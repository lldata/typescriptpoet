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

import kotlin.math.min

/**
 * A destructuring parameter pattern: `{ a, b: renamed }` or `[first, second]`.
 */
data class Destructure
internal constructor(val kind: Kind, val bindings: List<Binding>) {

  enum class Kind(val open: String, val close: String) {

    OBJECT("{ ", " }"),
    ARRAY("[", "]"),
  }

  /** One bound name, optionally renamed (`b: renamed`, object patterns only). */
  data class Binding(val name: String, val alias: String? = null)

  override fun toString(): String = bindings.joinToString(", ", prefix = kind.open, postfix = kind.close) { binding ->
    if (binding.alias != null) "${binding.name}: ${binding.alias}" else binding.name
  }
}

class ParameterSpec private constructor(builder: Builder) : Taggable(builder.tags.toImmutableMap()) {

  val name = builder.name
  val destructure = builder.destructure
  val optional = builder.optional
  val decorators = builder.decorators.toImmutableList()
  val modifiers = builder.modifiers.toImmutableSet()
  val type = builder.type
  val defaultValue = builder.defaultValue

  internal fun emit(
    codeWriter: CodeWriter,
    includeType: Boolean = true,
    isRest: Boolean = false,
    optionalAllowed: Boolean = false,
  ) {
    codeWriter.emitDecorators(decorators, true)
    codeWriter.emitModifiers(modifiers)
    if (isRest) {
      codeWriter.emitCode("...")
    }
    // A destructuring pattern stands in for the name; `name` is then only bookkeeping.
    codeWriter.emitCode(CodeBlock.of("%L", destructure?.toString() ?: name))
    if (includeType) {
      if (optional && optionalAllowed) {
        codeWriter.emitCode("?")
      }
      codeWriter.emitCode(CodeBlock.of(": %T", type))
      if (optional && !optionalAllowed) {
        codeWriter.emitCode(" | undefined")
      }
    }
    emitDefaultValue(codeWriter)
  }

  internal fun emitDefaultValue(codeWriter: CodeWriter) {
    if (defaultValue != null) {
      codeWriter.emitCode(CodeBlock.of(" = %[%L%]", defaultValue))
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false
    if (javaClass != other.javaClass) return false
    return toString() == other.toString()
  }

  override fun hashCode() = toString().hashCode()

  override fun toString() = buildCodeString { emit(this) }

  /** A builder pre-populated with this spec, optionally renamed or retyped. */
  @JvmOverloads
  fun toBuilder(name: String = this.name, type: TypeName = this.type): Builder {
    val builder = Builder(name, type, optional)
    builder.decorators += decorators
    builder.modifiers += modifiers
    builder.defaultValue = defaultValue
    return builder
  }

  class Builder internal constructor(
    internal val name: String,
    internal val type: TypeName,
    internal var optional: Boolean,
  ) : Taggable.Builder<Builder>() {

    internal val decorators = mutableListOf<DecoratorSpec>()
    internal val modifiers = mutableListOf<Modifier>()
    internal var defaultValue: CodeBlock? = null
    internal var destructure: Destructure? = null

    /** Adds parameter decorators: `greet(@inject name: string)`. */
    fun addDecorators(decoratorSpecs: Iterable<DecoratorSpec>) = apply {
      decorators += decoratorSpecs
    }

    /** Adds a parameter decorator: `greet(@inject name: string)`. */
    fun addDecorator(decoratorSpec: DecoratorSpec) = apply {
      decorators += decoratorSpec
    }

    /** Adds a parameter decorator by symbol: `greet(@inject name: string)`. */
    fun addDecorator(decorator: SymbolSpec) = apply {
      decorators += DecoratorSpec.builder(decorator).build()
    }

    /** Adds modifiers, making it a constructor property: `constructor(private name: string)`. */
    fun addModifiers(vararg modifiers: Modifier) = apply {
      this.modifiers += modifiers
    }

    /** Adds modifiers, making it a constructor property: `constructor(private name: string)`. */
    fun addModifiers(modifiers: Iterable<Modifier>) = apply {
      this.modifiers += modifiers
    }

    /** Marks the parameter optional: `greet(name?: string)`. */
    fun optional(optional: Boolean) = apply {
      this.optional = optional
    }

    /** Sets the default value: `greet(name: string = 'world')`. */
    fun defaultValue(format: String, vararg args: Any?) = defaultValue(
      CodeBlock.of(format, *args),
    )

    /** Sets the default value: `greet(name: string = 'world')`. May be set only once. */
    fun defaultValue(codeBlock: CodeBlock) = apply {
      check(this.defaultValue == null) { "initializer was already set" }
      this.defaultValue = codeBlock
    }

    /**
     * Replaces the parameter name with a destructuring pattern
     * (e.g. `{ a, b }: Options`).
     *
     * The builder's `name` is kept for lookup by [FunctionSpec.parameter] but is not emitted.
     */
    fun destructure(destructure: Destructure) = apply {
      this.destructure = destructure
    }

    fun build() = ParameterSpec(this)
  }

  companion object {

    /** A parameter: `name: string`. */
    @JvmStatic
    @JvmOverloads
    fun builder(name: String, type: TypeName, optional: Boolean = false, vararg modifiers: Modifier): Builder {
      require(name.isName) { "not a valid name: $name" }
      return Builder(name, type, optional).addModifiers(*modifiers)
    }

    /**
     * The `this` pseudo-parameter (e.g. `function f(this: Window, x: number)`).
     *
     * Bypasses the name check, since `this` is a reserved word everywhere else.
     */
    internal fun thisParameter(type: TypeName): ParameterSpec = Builder("this", type, false).build()

    /** An object destructuring pattern (e.g. `{ a, b: renamed }`). */
    @JvmStatic
    fun objectPattern(vararg bindings: Destructure.Binding): Destructure =
      Destructure(Destructure.Kind.OBJECT, bindings.toList())

    /** An array destructuring pattern (e.g. `[first, second]`). */
    @JvmStatic
    fun arrayPattern(vararg names: String): Destructure =
      Destructure(Destructure.Kind.ARRAY, names.map { Destructure.Binding(it) })

    /** One binding in an object pattern, optionally renamed. */
    @JvmStatic
    @JvmOverloads
    fun binding(name: String, alias: String? = null): Destructure.Binding = Destructure.Binding(name, alias)
  }
}

internal fun List<ParameterSpec>.emit(
  codeWriter: CodeWriter,
  enclosed: Boolean = true,
  rest: ParameterSpec? = null,
  constructorProperties: Map<String, PropertySpec> = emptyMap(),
  emitBlock: (ParameterSpec, Boolean, Boolean) -> Unit =
    { param, isRest, optionalAllowed ->
      param.emit(codeWriter, optionalAllowed = optionalAllowed, isRest = isRest)
    },
) = with(codeWriter) {
  val params = this@emit + if (rest != null) listOf(rest) else emptyList()
  if (enclosed) emit("(")
  if (size < 5 && all { constructorProperties[it.name]?.decorators?.isEmpty() ?: it.decorators.isEmpty() }) {
    params.forEachIndexed { index, parameter ->
      val optionalAllowed = subList(min(index + 1, size), size).all { it.optional }
      if (index > 0) emitCode(",%W")
      emitBlock(parameter, rest === parameter, optionalAllowed)
    }
  } else {
    emit("\n")
    indent(2)
    params.forEachIndexed { index, parameter ->
      val optionalAllowed = subList(min(index + 1, size), size).all { it.optional }
      if (index > 0) emit(",\n")
      emitBlock(parameter, rest === parameter, optionalAllowed)
    }
    unindent(2)
    emit("\n")
  }
  if (enclosed) emit(")")
}
