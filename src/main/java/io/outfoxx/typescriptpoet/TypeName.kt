/*
 * Copyright 2017 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.outfoxx.typescriptpoet

import io.outfoxx.typescriptpoet.TypeName.TypeVariable.Bound
import io.outfoxx.typescriptpoet.TypeName.TypeVariable.Bound.Combiner
import io.outfoxx.typescriptpoet.TypeName.TypeVariable.Bound.Combiner.UNION

/**
 * Name of any possible type that can be referenced
 *
 */
sealed class TypeName {

  internal abstract fun emit(codeWriter: CodeWriter)

  /**
   * Whether this type binds loosely enough that it must be parenthesised when it appears as
   * the operand of a tighter-binding construct.
   *
   * `keyof A | B` parses as `(keyof A) | B`, and `A | B[]` as `A | (B[])`, so a union used as
   * the operand of `keyof` or `[]` has to be wrapped to mean what the caller asked for.
   */
  internal open val bindsLoosely: Boolean get() = false

  /** Emits this type, parenthesised if it would otherwise re-associate. See [bindsLoosely]. */
  internal fun emitAsOperand(codeWriter: CodeWriter) {
    if (bindsLoosely) {
      codeWriter.emit("(")
      emit(codeWriter)
      codeWriter.emit(")")
    } else {
      emit(codeWriter)
    }
  }

  @ConsistentCopyVisibility
  data class Standard
  internal constructor(val base: SymbolSpec) : TypeName() {

    /** A type nested inside this one: `Outer.Inner`. */
    fun nested(name: String) = Standard(base.nested(name))

    /** The enclosing type of `Outer.Inner`, or null if this is top level. */
    fun enclosingTypeName() = base.enclosing()?.let { Standard(it) }

    /** The outermost type of `Outer.Inner.Deep`, i.e. `Outer`. */
    fun topLevelTypeName() = Standard(base.topLevel())

    /** The last segment of `Outer.Inner`, i.e. `Inner`. */
    fun simpleName() = base.value.split(".").last()

    /** The segments of `Outer.Inner.Deep` after the first, i.e. `[Inner, Deep]`. */
    fun simpleNames(): List<String> {
      val names = base.value.split(".")
      return names.subList(1, names.size)
    }

    val isTopLevelTypeName: Boolean get() = base.isTopLevelSymbol

    /** Applies type arguments: `Array<string>`. */
    fun parameterized(vararg typeArgs: TypeName) = parameterizedType(this, *typeArgs)

    override fun emit(codeWriter: CodeWriter) {
      val fullPath = base.value.split(".")
      val relativePath = fullPath.dropCommon(codeWriter.currentScope())
      val relativeName =
        if (relativePath.isNotEmpty()) {
          relativePath.joinToString(".")
        } else {
          fullPath.last()
        }

      if (relativeName == base.value) {
        codeWriter.emitSymbol(base)
      } else {
        codeWriter.emitSymbol(SymbolSpec.implicit(relativeName))
      }
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  @ConsistentCopyVisibility
  data class Parameterized
  internal constructor(val rawType: Standard, val typeArgs: List<TypeName>) : TypeName() {

    override fun emit(codeWriter: CodeWriter) {
      rawType.emit(codeWriter)
      codeWriter.emit("<")
      typeArgs.forEachIndexed { idx, typeArg ->
        typeArg.emit(codeWriter)

        if (idx < typeArgs.size - 1) {
          codeWriter.emit(", ")
        }
      }
      codeWriter.emit(">")
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  @ConsistentCopyVisibility
  data class TypeVariable
  internal constructor(
    val name: String,
    val bounds: List<Bound>,
    val variance: Variance? = null,
    val isConst: Boolean = false,
    val isInfer: Boolean = false,
  ) : TypeName() {

    /** Declaration-site variance annotation (e.g. `<in T>`, `<out T>`, `<in out T>`). */
    enum class Variance(val keyword: String) {

      IN("in"),
      OUT("out"),
      IN_OUT("in out"),
    }

    data class Bound(val type: TypeName, val combiner: Combiner = UNION, val modifier: Modifier?) {

      enum class Combiner(val symbol: String) {

        UNION("|"),
        INTERSECT("&"),
      }

      enum class Modifier(val keyword: String) {

        KEY_OF("keyof"),
      }
    }

    // `infer U[]` parses as `infer (U[])`, so an infer variable used as the operand of a
    // tighter construct has to be parenthesised: `(infer U)[]`. A plain type variable is a
    // single token and never needs it.
    override val bindsLoosely: Boolean get() = isInfer

    override fun emit(codeWriter: CodeWriter) {
      // `infer T` is a use-site form; variance, `const` and bounds only appear at the
      // declaration site, which goes through CodeWriter.emitTypeVariables.
      if (isInfer) {
        codeWriter.emit("infer ")
      }
      codeWriter.emit(name)
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  /**
   * A prefix type operator: `keyof T`, `typeof x`, `readonly T[]`, `unique symbol`.
   */
  @ConsistentCopyVisibility
  data class Operator
  internal constructor(val operator: Kind, val operand: TypeName) : TypeName() {

    enum class Kind(val keyword: String) {

      KEY_OF("keyof"),
      TYPE_OF("typeof"),
      READONLY("readonly"),
      UNIQUE("unique"),
    }

    override fun emit(codeWriter: CodeWriter) {
      codeWriter.emit(operator.keyword)
      codeWriter.emit(" ")
      operand.emitAsOperand(codeWriter)
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  /** An indexed access type: `T[K]`. */
  @ConsistentCopyVisibility
  data class IndexedAccess
  internal constructor(val objectType: TypeName, val indexType: TypeName) : TypeName() {

    override fun emit(codeWriter: CodeWriter) {
      objectType.emitAsOperand(codeWriter)
      codeWriter.emit("[")
      indexType.emit(codeWriter)
      codeWriter.emit("]")
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  /**
   * The array shorthand, `T[]`.
   *
   * Distinct from [arrayType], which emits `Array<T>`. Only the shorthand can carry
   * `readonly`: `readonly T[]` is valid where `readonly Array<T>` is not.
   */
  @ConsistentCopyVisibility
  data class ArrayShorthand
  internal constructor(val elementType: TypeName) : TypeName() {

    override fun emit(codeWriter: CodeWriter) {
      elementType.emitAsOperand(codeWriter)
      codeWriter.emit("[]")
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  /**
   * A conditional type: `T extends U ? X : Y`.
   *
   * `infer` variables are placed inside [extendsType], via [TypeName.infer].
   */
  @ConsistentCopyVisibility
  data class Conditional
  internal constructor(
    val checkType: TypeName,
    val extendsType: TypeName,
    val trueType: TypeName,
    val falseType: TypeName,
  ) : TypeName() {

    override val bindsLoosely: Boolean get() = true

    override fun emit(codeWriter: CodeWriter) {
      checkType.emitAsOperand(codeWriter)
      codeWriter.emit(" extends ")
      extendsType.emitAsOperand(codeWriter)
      codeWriter.emit(" ? ")
      trueType.emit(codeWriter)
      codeWriter.emit(" : ")
      // The false branch is the standard place to chain, and `A ? B : C ? D : E` already
      // associates to the right, so no parentheses are needed here.
      falseType.emit(codeWriter)
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  /**
   * A mapped type: `{ [K in Keys]: T }`, with optional `as` key remapping and `+`/`-`
   * `readonly` and `?` modifiers.
   */
  @ConsistentCopyVisibility
  data class Mapped
  internal constructor(
    val keyName: String,
    val constraint: TypeName,
    val valueType: TypeName,
    val asClause: TypeName? = null,
    val readonly: Change? = null,
    val optional: Change? = null,
  ) : TypeName() {

    /**
     * Whether a mapped type adds, removes, or simply states a `readonly`/`?` modifier.
     *
     * [KEEP] emits the bare modifier, which for a homomorphic mapped type means "leave as
     * found"; [ADD] and [REMOVE] emit `+` and `-`. Absent (null) emits nothing at all, which
     * is different from [KEEP].
     */
    enum class Change(val prefix: String) {

      KEEP(""),
      ADD("+"),
      REMOVE("-"),
    }

    override fun emit(codeWriter: CodeWriter) {
      // Measure, then break: Prettier keeps a mapped type on one line only if it fits, and
      // otherwise puts the single member on its own line between the braces.
      val body = buildCodeString { emitBody(this) }
      val fitsOnOneLine = codeWriter.currentColumn + body.length + 5 <= codeWriter.printWidth

      if (fitsOnOneLine) {
        codeWriter.emit("{ ")
        emitBody(codeWriter)
        codeWriter.emit(" }")
      } else {
        codeWriter.emit("{\n")
        codeWriter.indent()
        emitBody(codeWriter)
        codeWriter.emit(";\n")
        codeWriter.unindent()
        codeWriter.emit("}")
      }
    }

    /** The `[K in Keys]: V` between the braces, without them or any layout around it. */
    private fun emitBody(codeWriter: CodeWriter) {
      readonly?.let { codeWriter.emit("${it.prefix}readonly ") }
      codeWriter.emit("[")
      codeWriter.emit(keyName)
      codeWriter.emit(" in ")
      constraint.emit(codeWriter)
      asClause?.let {
        codeWriter.emit(" as ")
        it.emit(codeWriter)
      }
      codeWriter.emit("]")
      optional?.let { codeWriter.emit("${it.prefix}?") }
      codeWriter.emit(": ")
      valueType.emit(codeWriter)
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  /**
   * A template literal type: `` `on${Capitalize<T>}` ``.
   *
   * [parts] alternates freely between [String] literals and [TypeName] interpolations.
   */
  @ConsistentCopyVisibility
  data class TemplateLiteral
  internal constructor(val parts: List<Any>) : TypeName() {

    override fun emit(codeWriter: CodeWriter) {
      codeWriter.emit("`")
      parts.forEach { part ->
        when (part) {
          is TypeName -> {
            codeWriter.emit("\${")
            part.emit(codeWriter)
            codeWriter.emit("}")
          }

          else -> codeWriter.emit(part.toString())
        }
      }
      codeWriter.emit("`")
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  @ConsistentCopyVisibility
  data class Anonymous
  internal constructor(val members: List<Member>) : TypeName() {

    data class Member(val name: String, val type: TypeName, val optional: Boolean)

    override fun emit(codeWriter: CodeWriter) {
      codeWriter.emit("{ ")
      members.forEachIndexed { idx, member ->
        codeWriter.emit(member.name)
        if (member.optional) {
          codeWriter.emit("?")
        }
        codeWriter.emit(": ")
        member.type.emit(codeWriter)

        if (idx != members.size - 1) {
          codeWriter.emit("; ")
        }
      }
      codeWriter.emit(" }")
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  @ConsistentCopyVisibility
  data class Tuple
  internal constructor(val members: List<Member>) : TypeName() {

    /**
     * One tuple element, optionally labelled, optional, or a rest element:
     * `[a: string, b?: number, ...rest: boolean[]]`.
     */
    data class Member(
      val type: TypeName,
      val label: String? = null,
      val optional: Boolean = false,
      val rest: Boolean = false,
    )

    /** The element types, discarding labels. Convenience for the common unlabelled case. */
    val memberTypes: List<TypeName> get() = members.map { it.type }

    override fun emit(codeWriter: CodeWriter) {
      codeWriter.emit("[")
      members.forEachIndexed { idx, member ->
        if (member.rest) {
          codeWriter.emit("...")
        }
        if (member.label != null) {
          codeWriter.emit(member.label)
          if (member.optional) {
            codeWriter.emit("?")
          }
          codeWriter.emit(": ")
          member.type.emit(codeWriter)
        } else {
          member.type.emit(codeWriter)
          if (member.optional) {
            codeWriter.emit("?")
          }
        }

        if (idx != members.size - 1) {
          codeWriter.emit(", ")
        }
      }
      codeWriter.emit("]")
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  @ConsistentCopyVisibility
  data class Intersection
  internal constructor(val typeRequirements: List<TypeName>) : TypeName() {

    override val bindsLoosely: Boolean get() = typeRequirements.size > 1

    override fun emit(codeWriter: CodeWriter) {
      typeRequirements.forEachIndexed { idx, typeRequirement ->
        typeRequirement.emit(codeWriter)

        if (idx != typeRequirements.size - 1) {
          codeWriter.emit(" & ")
        }
      }
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  @ConsistentCopyVisibility
  data class Union
  internal constructor(val typeChoices: List<TypeName>) : TypeName() {

    override val bindsLoosely: Boolean get() = typeChoices.size > 1

    override fun emit(codeWriter: CodeWriter) {
      typeChoices.forEachIndexed { idx, typeChoice ->
        typeChoice.emit(codeWriter)

        if (idx != typeChoices.size - 1) {
          codeWriter.emit(" | ")
        }
      }
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  @ConsistentCopyVisibility
  data class Lambda
  internal constructor(
    val parameters: Map<String, TypeName> = emptyMap(),
    val returnType: TypeName = VOID,
    val typeVariables: List<TypeVariable> = emptyList(),
    val constructable: Boolean = false,
    val abstract: Boolean = false,
  ) : TypeName() {

    override val bindsLoosely: Boolean get() = true

    override fun emit(codeWriter: CodeWriter) {
      if (abstract) {
        codeWriter.emit("abstract ")
      }
      if (constructable) {
        codeWriter.emit("new ")
      }
      codeWriter.emitTypeVariables(typeVariables)
      codeWriter.emit("(")
      parameters.entries.forEachIndexed { idx, entry ->
        val (name, type) = entry

        codeWriter.emit(name)
        codeWriter.emit(": ")
        type.emit(codeWriter)

        if (idx != parameters.size - 1) {
          codeWriter.emit(", ")
        }
      }
      codeWriter.emit(") => ")
      returnType.emit(codeWriter)
    }

    override fun toString() = buildCodeString { emit(this) }
  }

  companion object {

    @JvmField val NULL = implicit("null")

    @JvmField val UNDEFINED = implicit("undefined")

    @JvmField val NEVER = implicit("never")

    @JvmField val VOID = implicit("void")

    @JvmField val ANY = implicit("any")

    @JvmField val BOOLEAN = implicit("boolean")

    @JvmField val NUMBER = implicit("number")

    @JvmField val BIGINT = implicit("bigint")

    @JvmField val STRING = implicit("string")

    @JvmField val OBJECT = implicit("object")

    @JvmField val SYMBOL = implicit("symbol")

    @JvmField val BOOLEAN_CLASS = implicit("Boolean")

    @JvmField val NUMBER_CLASS = implicit("Number")

    @JvmField val BIGINT_CLASS = implicit("BigInt")

    @JvmField val STRING_CLASS = implicit("String")

    @JvmField val OBJECT_CLASS = implicit("Object")

    @JvmField val SYMBOL_CLASS = implicit("Symbol")

    @JvmField val FUNCTION = implicit("Function")

    @JvmField val ERROR = implicit("Error")

    @JvmField val REGEXP = implicit("RegExp")

    @JvmField val MATH = implicit("Math")

    @JvmField val DATE = implicit("Date")

    @JvmField val SET = implicit("Set")

    @JvmField val WEAK_SET = implicit("WeakSet")

    @JvmField val MAP = implicit("Map")

    @JvmField val WEAK_MAP = implicit("WeakMap")

    @JvmField val ARRAY = implicit("Array")

    @JvmField val INT8_ARRAY = implicit("Int8Array")

    @JvmField val UINT8_ARRAY = implicit("Uint8Array")

    @JvmField val UINT8_CLAMPED_ARRAY = implicit("Uint8ClampedArray")

    @JvmField val INT16_ARRAY = implicit("Int16Array")

    @JvmField val UINT16_ARRAY = implicit("Uint16Array")

    @JvmField val INT32_ARRAY = implicit("Int32Array")

    @JvmField val UINT32_ARRAY = implicit("Uint32Array")

    @JvmField val FLOAT32_ARRAY = implicit("Float32Array")

    @JvmField val FLOAT64_ARRAY = implicit("Float64Array")

    @JvmField val BIG_INT64_ARRAY = implicit("BigInt64Array")

    @JvmField val BIG_UINT64_ARRAY = implicit("BigUint64Array")

    @JvmField val ARRAY_BUFFER = implicit("ArrayBuffer")

    @JvmField val SHARED_ARRAY_BUFFER = implicit("SharedArrayBuffer")

    @JvmField val ATOMICS = implicit("Atomics")

    @JvmField val DATA_VIEW = implicit("DataView")

    @JvmField val JSON = implicit("JSON")

    @JvmField val PROMISE = implicit("Promise")

    @JvmField val GENERATOR = implicit("Generator")

    /**
     * Any class/enum/primitive/etc type name
     *
     * @param exportedName The name of the symbol exported from module `from`
     * @param from The module the that `exportedName` is exported from
     */
    @JvmStatic
    @JvmOverloads
    fun namedImport(exportedName: String, from: String, typeOnly: Boolean = false): Standard =
      Standard(SymbolSpec.importsName(exportedName, from, typeOnly))

    /**
     * A type imported as a module's default export
     *
     * @param localName Name to bind the default export to
     * @param from The module it is imported from
     * @param typeOnly Whether to emit `import type`
     */
    @JvmStatic
    @JvmOverloads
    fun defaultImport(localName: String, from: String, typeOnly: Boolean = false): Standard =
      Standard(SymbolSpec.importsDefault(localName, from, typeOnly))

    /**
     * Any class/enum/primitive/etc type name
     *
     * @param name Name for the implicit type, will be symbolized
     */
    @JvmStatic
    fun implicit(name: String): Standard = Standard(SymbolSpec.implicit(name))

    /**
     * Any class/enum/primitive/etc type name
     *
     * @param symbolSpec Serialized symbol spec
     */
    @JvmStatic
    fun standard(symbolSpec: String): Standard = Standard(SymbolSpec.from(symbolSpec))

    /**
     * Any class/enum/primitive/etc type name
     *
     * @param symbolSpec Symbol spec
     */
    @JvmStatic
    fun standard(symbolSpec: SymbolSpec): Standard = Standard(symbolSpec)

    /**
     * Type name for the generic Array type
     *
     * @param elementType Element type of the array
     * @return Type name of the new array type
     */
    @JvmStatic
    fun arrayType(elementType: TypeName): TypeName = parameterizedType(
      ARRAY,
      elementType,
    )

    /**
     * Type name for the generic Set type
     *
     * @param elementType Element type of the set
     * @return Type name of the new set type
     */
    @JvmStatic
    fun setType(elementType: TypeName): TypeName = parameterizedType(
      SET,
      elementType,
    )

    /**
     * Type name for the generic Map type
     *
     * @param keyType Key type of the map
     * @param valueType Value type of the map
     * @return Type name of the new map type
     */
    @JvmStatic
    fun mapType(keyType: TypeName, valueType: TypeName): TypeName = parameterizedType(
      MAP,
      keyType,
      valueType,
    )

    /**
     * A string literal type (e.g. `"active"`).
     *
     * Takes the *unquoted* value and spells it, so the quoting and escaping are the
     * library's job rather than the caller's. That is the point: passing pre-quoted text to
     * [implicit] emits a file that does not parse the moment the value contains a quote or a
     * backslash, and does so silently, which matters most when the value comes from data
     * rather than from source.
     *
     * Escaped by the same routine as `%S`, so a literal type and a string constant with the
     * same value are spelled identically.
     */
    @JvmStatic
    fun literal(value: String): Standard = implicit(stringLiteralWithQuotes(value, ""))

    /** A numeric literal type (e.g. `42`). */
    @JvmStatic
    fun literal(value: Int): Standard = implicit(value.toString())

    /** A numeric literal type (e.g. `42`). */
    @JvmStatic
    fun literal(value: Long): Standard = implicit(value.toString())

    /** A boolean literal type (`true` or `false`). */
    @JvmStatic
    fun literal(value: Boolean): Standard = implicit(value.toString())

    /**
     * The `Record<K, V>` utility type (e.g. `Record<string, number>`)
     *
     * Distinct from [mapType], which spells `Map<K, V>`. `Map` is a runtime class, accessed
     * with `.get(k)`, and is not what `JSON.parse` produces; `Record` describes a plain
     * object accessed with `obj[k]`, which is usually what generated code modelling
     * JSON-shaped data wants.
     *
     * @param keyType Key type of the record
     * @param valueType Value type of the record
     * @return Type name of the new record type
     */
    @JvmStatic
    fun recordType(keyType: TypeName, valueType: TypeName): Parameterized = parameterizedType(
      RECORD,
      keyType,
      valueType,
    )

    /**
     * Parameterized type that represents a concrete
     * usage of a generic type
     *
     * @param rawType Generic type to invoke with arguments
     * @param typeArgs Names of the provided type arguments
     * @return Type name of the new parameterized type
     */
    @JvmStatic
    fun parameterizedType(rawType: Standard, vararg typeArgs: TypeName): Parameterized =
      Parameterized(rawType, typeArgs.toList())

    /**
     * Type variable represents a single variable type in a
     * generic type or function.
     *
     * @param name The name of the variable as it will be used in the definition
     * @param bounds Bound constraints that will be required during instantiation
     * @return Type name of the new type variable
     */
    @JvmStatic
    fun typeVariable(name: String, vararg bounds: Bound): TypeVariable = TypeVariable(name, bounds.toList())

    /**
     * Type variable with a declaration-site variance annotation and/or `const` modifier
     * (e.g. `<const T>`, `<out T>`, `<in out T extends Base>`).
     *
     * @param name The name of the variable as it will be used in the definition
     * @param variance Variance annotation, or null for none
     * @param const Whether the type parameter is a `const` type parameter
     * @param bounds Bound constraints that will be required during instantiation
     */
    @JvmStatic
    @JvmOverloads
    fun typeVariable(
      name: String,
      variance: TypeVariable.Variance? = null,
      const: Boolean = false,
      bounds: List<Bound> = emptyList(),
    ): TypeVariable = TypeVariable(name, bounds, variance, const)

    /**
     * An `infer` type variable, for use inside the `extends` clause of a conditional type
     * (e.g. the `U` in `T extends Array<infer U> ? U : never`).
     *
     * @param name The name being inferred
     */
    @JvmStatic
    fun infer(name: String): TypeVariable = TypeVariable(name, emptyList(), isInfer = true)

    /**
     * Factory for type variable bounds
     */
    @JvmStatic
    @JvmOverloads
    fun bound(type: TypeName, combiner: Combiner = UNION, modifier: Bound.Modifier? = null): Bound =
      Bound(type, combiner, modifier)

    /**
     * Factory for type variable bounds
     */
    @JvmStatic
    @JvmOverloads
    fun unionBound(type: TypeName, keyOf: Boolean = false): Bound =
      bound(type, UNION, if (keyOf) Bound.Modifier.KEY_OF else null)

    /**
     * Factory for type variable bounds
     */
    @JvmStatic
    @JvmOverloads
    fun intersectBound(type: TypeName, keyOf: Boolean = false): Bound =
      bound(type, Combiner.INTERSECT, if (keyOf) Bound.Modifier.KEY_OF else null)

    /**
     * Anonymous type name (e.g. `{ length: number, name: string }`)
     *
     * @param members Member pairs to define the anonymous type
     * @return Type name representing the anonymous type
     */
    @JvmStatic
    fun anonymousType(members: List<Anonymous.Member>): Anonymous = Anonymous(members)

    /**
     * Anonymous type name (e.g. `{ length?: number, name: string }`)
     *
     * @param members Member pairs to define the anonymous type (all properties are required)
     * @return Type name representing the anonymous type
     */
    @JvmStatic
    fun anonymousType(vararg members: Pair<String, TypeName>): Anonymous =
      anonymousType(members.map { Anonymous.Member(it.first, it.second, false) })

    /**
     * Tuple type name (e.g. `[number, boolean, string]`}
     *
     * @param memberTypes Each argument represents a distinct member type
     * @return Type name representing the tuple type
     */
    @JvmStatic
    fun tupleType(vararg memberTypes: TypeName): Tuple = Tuple(memberTypes.map { Tuple.Member(it) })

    /**
     * Tuple type name with labelled, optional or rest elements
     * (e.g. `[a: string, b?: number, ...rest: boolean[]]`)
     *
     * @param members Each argument represents a distinct member
     * @return Type name representing the tuple type
     */
    @JvmStatic
    fun tupleType(members: List<Tuple.Member>): Tuple = Tuple(members)

    /**
     * A single tuple element.
     *
     * @param type Element type
     * @param label Optional element label (e.g. the `a` in `[a: string]`)
     * @param optional Whether the element is optional (e.g. `[b?: number]`)
     * @param rest Whether the element is a rest element (e.g. `[...rest: boolean[]]`)
     */
    @JvmStatic
    @JvmOverloads
    fun tupleMember(
      type: TypeName,
      label: String? = null,
      optional: Boolean = false,
      rest: Boolean = false,
    ): Tuple.Member = Tuple.Member(type, label, optional, rest)

    /**
     * The `keyof` operator (e.g. `keyof Person`)
     *
     * @param type Type whose keys are taken
     */
    @JvmStatic
    fun keyOf(type: TypeName): Operator = Operator(Operator.Kind.KEY_OF, type)

    /**
     * A `typeof` type query (e.g. `typeof config`)
     *
     * This queries the type of a *value*, so it takes the value's symbol and the value is
     * imported like any other reference.
     *
     * @param symbol Symbol of the value being queried
     */
    @JvmStatic
    fun typeOf(symbol: SymbolSpec): Operator = Operator(Operator.Kind.TYPE_OF, Standard(symbol))

    /**
     * A `typeof` type query (e.g. `typeof config`)
     *
     * @param type Type name of the value being queried
     */
    @JvmStatic
    fun typeOf(type: Standard): Operator = Operator(Operator.Kind.TYPE_OF, type)

    /**
     * The `readonly` type operator, valid on array shorthands and tuples
     * (e.g. `readonly string[]`, `readonly [number, string]`)
     *
     * @param type Array shorthand or tuple type to mark readonly
     */
    @JvmStatic
    fun readOnly(type: TypeName): Operator = Operator(Operator.Kind.READONLY, type)

    /**
     * An indexed access type (e.g. `Person["name"]`)
     *
     * @param objectType Type being indexed
     * @param indexType Index
     */
    @JvmStatic
    fun indexedAccess(objectType: TypeName, indexType: TypeName): IndexedAccess = IndexedAccess(objectType, indexType)

    /**
     * The array shorthand (e.g. `string[]`)
     *
     * Unlike [arrayType], which emits `Array<T>`, this form can carry `readonly`.
     *
     * @param elementType Element type of the array
     */
    @JvmStatic
    fun arrayShorthandType(elementType: TypeName): ArrayShorthand = ArrayShorthand(elementType)

    /**
     * Intersection type name (e.g. `Person & Serializable & Loggable`)
     *
     * @param typeRequirements Requirements of the intersection as individual type names
     * @return Type name representing the intersection type
     */
    @JvmStatic
    fun intersectionType(vararg typeRequirements: TypeName): Intersection = Intersection(typeRequirements.toList())

    /**
     * Union type name (e.g. `int | number | any`)
     *
     * @param typeChoices All possible choices allowed in the union
     * @return Type name representing the union type
     */
    @JvmStatic
    fun unionType(vararg typeChoices: TypeName): Union = Union(typeChoices.toList())

    /** Returns a lambda type with `returnType` and parameters of listed in `parameters`. */
    @JvmStatic
    @JvmOverloads
    fun lambda(parameters: Map<String, TypeName> = emptyMap(), returnType: TypeName) = Lambda(parameters, returnType)

    /** Returns a lambda type with `returnType` and parameters of listed in `parameters`. */
    @JvmStatic
    fun lambda(vararg parameters: Pair<String, TypeName> = emptyArray(), returnType: TypeName) =
      Lambda(parameters.toMap(), returnType)

    /**
     * A generic function type (e.g. `<T>(value: T) => T`).
     *
     * @param typeVariables Type parameters of the function type
     * @param parameters Parameters of the function type
     * @param returnType Return type of the function type
     */
    @JvmStatic
    @JvmOverloads
    fun genericLambda(
      typeVariables: List<TypeVariable>,
      parameters: Map<String, TypeName> = emptyMap(),
      returnType: TypeName,
    ) = Lambda(parameters, returnType, typeVariables)

    /**
     * A construct signature type (e.g. `new (value: string) => Widget`).
     *
     * @param parameters Parameters of the constructor
     * @param returnType Type the constructor produces
     * @param typeVariables Type parameters of the construct signature
     * @param abstract Whether this is an `abstract new (...) => T` type
     */
    @JvmStatic
    @JvmOverloads
    fun constructorType(
      parameters: Map<String, TypeName> = emptyMap(),
      returnType: TypeName,
      typeVariables: List<TypeVariable> = emptyList(),
      abstract: Boolean = false,
    ) = Lambda(parameters, returnType, typeVariables, constructable = true, abstract = abstract)

    /**
     * The `Record` utility type, for use with [recordType].
     *
     * Deliberately not among the constants above: those are runtime globals that can be
     * referenced in a value position, and `Record` exists only at the type level.
     */
    @JvmField
    val RECORD: Standard = implicit("Record")

    /** `unique symbol`, as used for well-known symbol declarations. */
    @JvmField
    val UNIQUE_SYMBOL: Operator = Operator(Operator.Kind.UNIQUE, SYMBOL)

    /**
     * A conditional type (e.g. `T extends string ? A : B`)
     *
     * @param checkType Type being tested
     * @param extendsType Type it is tested against; may contain [infer] variables
     * @param trueType Result when the test holds
     * @param falseType Result when it does not
     */
    @JvmStatic
    fun conditionalType(
      checkType: TypeName,
      extendsType: TypeName,
      trueType: TypeName,
      falseType: TypeName,
    ): Conditional = Conditional(checkType, extendsType, trueType, falseType)

    /**
     * A mapped type (e.g. `{ readonly [K in keyof T]?: T[K] }`)
     *
     * @param keyName Name of the key variable (the `K` in `[K in Keys]`)
     * @param constraint Type the key ranges over
     * @param valueType Type each key maps to
     * @param asClause Optional key remapping (the `as` in `[K in Keys as ...]`)
     * @param readonly Whether to add, remove or state `readonly`; null emits nothing
     * @param optional Whether to add, remove or state `?`; null emits nothing
     */
    @JvmStatic
    @JvmOverloads
    fun mappedType(
      keyName: String,
      constraint: TypeName,
      valueType: TypeName,
      asClause: TypeName? = null,
      readonly: Mapped.Change? = null,
      optional: Mapped.Change? = null,
    ): Mapped = Mapped(keyName, constraint, valueType, asClause, readonly, optional)

    /**
     * A template literal type (e.g. `` `get${Capitalize<K>}` ``)
     *
     * @param parts Alternating [String] literals and [TypeName] interpolations
     */
    @JvmStatic
    fun templateLiteralType(vararg parts: Any): TemplateLiteral {
      val invalid = parts.filterNot { it is String || it is TypeName }
      require(invalid.isEmpty()) {
        "template literal parts must be String or TypeName, but found $invalid"
      }
      return TemplateLiteral(parts.toList())
    }
  }
}
