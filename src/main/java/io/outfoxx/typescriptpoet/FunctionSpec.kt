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

/**
 * A type-predicate return type: `x is Y`, `asserts x`, or `asserts x is Y`.
 *
 * These are return-position-only and depend on a parameter name, so they are not a
 * [TypeName] -- a predicate cannot appear anywhere a type can.
 */
data class TypePredicate
internal constructor(val parameterName: String, val type: TypeName?, val asserts: Boolean)

/** A generated function declaration. */
class FunctionSpec
private constructor(builder: Builder) : Taggable(builder.tags.toImmutableMap()) {

  val name = builder.name
  val tsDoc = builder.tsDoc.build()
  val decorators = builder.decorators.toImmutableList()
  val modifiers = builder.modifiers.toImmutableSet()
  val typeVariables = builder.typeVariables.toImmutableList()
  val returnType = builder.returnType
  val typePredicate = builder.typePredicate
  val thisParameterType = builder.thisParameterType
  val parameters = builder.parameters.toImmutableList()
  val restParameter = builder.restParameter
  val isGenerator = builder.isGenerator
  val isSignatureOnly = builder.isSignatureOnly
  val isArrow = builder.isArrow
  val body = builder.body.build()

  init {
    require(body.isEmpty() || Modifier.ABSTRACT !in builder.modifiers) {
      "abstract function ${builder.name} cannot have code"
    }
    require(body.isEmpty() || !builder.isSignatureOnly) {
      "signature-only function ${builder.name} cannot have code"
    }
  }

  fun abstract(): FunctionSpec = builder(name)
    .addModifiers(Modifier.ABSTRACT)
    .addTypeVariables(typeVariables)
    .addParameters(parameters)
    .build()

  internal fun parameter(name: String) = parameters.firstOrNull { it.name == name }

  internal fun emit(codeWriter: CodeWriter, enclosingName: String?, implicitModifiers: Set<Modifier>) {
    codeWriter.emitTSDoc(tsDoc)
    codeWriter.emitDecorators(decorators, false)
    codeWriter.emitModifiers(modifiers, implicitModifiers)

    emitSignature(codeWriter, enclosingName)

    val isEmptyConstructor = isConstructor && body.isEmpty()
    // Call and index signatures are declarations, never definitions: they have no body in any
    // context. Interfaces got this for free by forcing ABSTRACT onto their members, but a
    // class index signature is concrete and would otherwise be emitted with an empty body.
    if (Modifier.ABSTRACT in modifiers || isEmptyConstructor || isCallable || isIndexable || isSignatureOnly) {
      codeWriter.emit(";\n")
      return
    }

    if (isArrow) {
      // An arrow function is an expression, so it neither starts on its own line nor ends
      // with one; the surrounding statement owns the terminator.
      codeWriter.emit(" => {\n")
      codeWriter.indent()
      codeWriter.emitCode(body)
      codeWriter.unindent()
      codeWriter.emit("}")
      return
    }

    codeWriter.emit(" {\n")
    codeWriter.indent()
    codeWriter.emitCode(body)
    codeWriter.unindent()
    codeWriter.emit("}\n")
  }

  private fun emitSignature(codeWriter: CodeWriter, enclosingName: String?) {
    when {
      isConstructor -> codeWriter.emitCode("constructor")

      isCallable -> codeWriter.emitCode("")

      isIndexable -> codeWriter.emitCode("[")

      isArrow -> codeWriter.emitCode("")

      else -> {
        if (enclosingName == null) {
          codeWriter.emit("function")
          codeWriter.emit(if (isGenerator) "* " else " ")
        } else if (isGenerator) {
          // On a method the star binds to the name, not to a `function` keyword.
          codeWriter.emit("*")
        }
        codeWriter.emitCode(CodeBlock.of("%L", name))
      }
    }

    if (typeVariables.isNotEmpty()) {
      codeWriter.emitTypeVariables(typeVariables)
    }

    val allParameters =
      thisParameterType?.let { listOf(ParameterSpec.thisParameter(it)) + parameters } ?: parameters

    allParameters.emit(
      codeWriter,
      enclosed = !isIndexable,
      rest = restParameter,
    ) { param, isRest, optionalAllowed ->
      param.emit(codeWriter, isRest = isRest, optionalAllowed = optionalAllowed)
    }

    if (isIndexable) {
      codeWriter.emitCode("]")
    }

    // A type predicate replaces the return type entirely.
    if (typePredicate != null) {
      codeWriter.emitCode(": ")
      if (typePredicate.asserts) {
        codeWriter.emitCode("asserts ")
      }
      codeWriter.emitCode(typePredicate.parameterName)
      typePredicate.type?.let { codeWriter.emitCode(CodeBlock.of(" is %T", it)) }
      return
    }

    // An explicitly requested `void` is emitted. Leaving `returns()` uncalled is how you ask
    // for no return type at all.
    if (returnType != null) {
      codeWriter.emitCode(CodeBlock.of(": %T", returnType))
    }
  }

  val isConstructor get() = name.isConstructor
  val isAccessor get() = modifiers.contains(Modifier.GET) || modifiers.contains(Modifier.SET)
  val isCallable get() = name.isCallable
  val isIndexable get() = name.isIndexable

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false
    if (javaClass != other.javaClass) return false
    return toString() == other.toString()
  }

  override fun hashCode() = toString().hashCode()

  override fun toString() = buildCodeString { emit(this, null, emptySet()) }

  fun toBuilder(): Builder {
    val builder = Builder(name)
    builder.tsDoc.add(tsDoc)
    builder.decorators += decorators
    builder.modifiers += modifiers
    builder.typeVariables += typeVariables
    builder.returnType = returnType
    builder.typePredicate = typePredicate
    builder.thisParameterType = thisParameterType
    builder.parameters += parameters
    builder.restParameter = restParameter
    builder.isGenerator = isGenerator
    builder.isSignatureOnly = isSignatureOnly
    builder.isArrow = isArrow
    builder.body.add(body)
    return builder
  }

  class Builder internal constructor(internal val name: String) : Taggable.Builder<Builder>() {

    internal val tsDoc = CodeBlock.builder()
    internal val decorators = mutableListOf<DecoratorSpec>()
    internal val modifiers = mutableSetOf<Modifier>()
    internal val typeVariables = mutableListOf<TypeName.TypeVariable>()
    internal var returnType: TypeName? = null
    internal var typePredicate: TypePredicate? = null
    internal var thisParameterType: TypeName? = null
    internal val parameters = mutableListOf<ParameterSpec>()
    internal var restParameter: ParameterSpec? = null
    internal var isGenerator = false
    internal var isSignatureOnly = false
    internal var isArrow = false
    internal val body = CodeBlock.builder()

    init {
      require(name.isConstructor || name.isName) {
        "not a valid name: $name"
      }
    }

    fun addTSDoc(format: String, vararg args: Any) = apply {
      tsDoc.add(format, *args)
    }

    fun addTSDoc(block: CodeBlock) = apply {
      tsDoc.add(block)
    }

    fun addDecorators(decoratorSpecs: Iterable<DecoratorSpec>) = apply {
      this.decorators += decoratorSpecs
    }

    fun addDecorator(decoratorSpec: DecoratorSpec) = apply {
      decorators += decoratorSpec
    }

    fun addModifiers(vararg modifiers: Modifier) = apply {
      this.modifiers += modifiers
    }

    fun addModifiers(modifiers: Iterable<Modifier>) = apply {
      this.modifiers += modifiers
    }

    /**
     * Marks this as a generator function, emitting `function*` or `*method()`.
     *
     * Combine with [Modifier.ASYNC] for `async function*`.
     */
    @JvmOverloads
    fun generator(value: Boolean = true) = apply {
      this.isGenerator = value
    }

    /**
     * Emits this as an arrow function expression (e.g. `(x: number): string => { ... }`).
     *
     * Arrow functions are expressions, so the result is used as an initializer or a code
     * block argument rather than declared on its own; the name is kept only for lookup.
     */
    @JvmOverloads
    fun arrow(value: Boolean = true) = apply {
      this.isArrow = value
    }

    /**
     * Emits this as a declaration only -- a signature terminated with `;` and no body.
     *
     * This is how overload signatures are written: several signature-only specs sharing a
     * name, followed by one implementation that has a body. See [FunctionSpec.overloads].
     */
    @JvmOverloads
    fun signatureOnly(value: Boolean = true) = apply {
      this.isSignatureOnly = value
    }

    fun addTypeVariables(typeVariables: Iterable<TypeName.TypeVariable>) = apply {
      this.typeVariables += typeVariables
    }

    fun addTypeVariable(typeVariable: TypeName.TypeVariable) = apply {
      typeVariables += typeVariable
    }

    fun returns(returnType: TypeName) = apply {
      check(!name.isConstructor) { "$name cannot have a return type" }
      this.returnType = returnType
    }

    /**
     * Declares a type-predicate return type (e.g. `value is string`).
     *
     * @param parameterName Name of the parameter being narrowed
     * @param type Type it is narrowed to
     */
    fun returnsIs(parameterName: String, type: TypeName) = apply {
      check(!name.isConstructor) { "$name cannot have a return type" }
      this.typePredicate = TypePredicate(parameterName, type, asserts = false)
    }

    /**
     * Declares an assertion signature (e.g. `asserts value` or `asserts value is string`).
     *
     * @param parameterName Name of the parameter being asserted about
     * @param type Type it is asserted to be, or null for a bare `asserts value`
     */
    @JvmOverloads
    fun returnsAsserts(parameterName: String, type: TypeName? = null) = apply {
      check(!name.isConstructor) { "$name cannot have a return type" }
      this.typePredicate = TypePredicate(parameterName, type, asserts = true)
    }

    /**
     * Declares the `this` pseudo-parameter (e.g. `function f(this: Window, x: number)`).
     *
     * It is erased at call sites and exists only to type the receiver, so it is not an
     * ordinary parameter and cannot be optional, rest, or decorated.
     */
    fun thisParameter(type: TypeName) = apply {
      this.thisParameterType = type
    }

    fun addParameters(parameterSpecs: Iterable<ParameterSpec>) = apply {
      for (parameterSpec in parameterSpecs) {
        addParameter(parameterSpec)
      }
    }

    fun addParameter(parameterSpec: ParameterSpec) = apply {
      parameters += parameterSpec
    }

    @JvmOverloads
    fun addParameter(
      name: String,
      type: TypeName,
      optional: Boolean = false,
      defaultValue: CodeBlock,
      vararg modifiers: Modifier,
    ) = addParameter(
      ParameterSpec.builder(
        name,
        type,
        optional,
        *modifiers,
      ).defaultValue(defaultValue).build(),
    )

    @JvmOverloads
    fun addParameter(name: String, type: TypeName, optional: Boolean = false, vararg modifiers: Modifier) =
      addParameter(ParameterSpec.builder(name, type, optional, *modifiers).build())

    fun restParameter(name: String, type: TypeName) = restParameter(ParameterSpec.builder(name, type).build())

    fun restParameter(parameterSpec: ParameterSpec) = apply {
      this.restParameter = parameterSpec
    }

    fun addCode(format: String, vararg args: Any) = apply {
      modifiers -= Modifier.ABSTRACT
      body.add(format, *args)
    }

    fun addNamedCode(format: String, args: Map<String, *>) = apply {
      modifiers -= Modifier.ABSTRACT
      body.addNamed(format, args)
    }

    fun addCode(codeBlock: CodeBlock) = apply {
      modifiers -= Modifier.ABSTRACT
      body.add(codeBlock)
    }

    fun addComment(format: String, vararg args: Any) = apply {
      body.add("// " + format + "\n", *args)
    }

    /**
     * @param controlFlow the control flow construct and its code, such as "if (foo == 5)".
     * * Shouldn't contain braces or newline characters.
     */
    fun beginControlFlow(controlFlow: String, vararg args: Any) = apply {
      modifiers -= Modifier.ABSTRACT
      body.beginControlFlow(controlFlow, *args)
    }

    /**
     * @param controlFlow the control flow construct and its code, such as "else if (foo == 10)".
     * *     Shouldn't contain braces or newline characters.
     */
    fun nextControlFlow(controlFlow: String, vararg args: Any) = apply {
      modifiers -= Modifier.ABSTRACT
      body.nextControlFlow(controlFlow, *args)
    }

    fun endControlFlow() = apply {
      modifiers -= Modifier.ABSTRACT
      body.endControlFlow()
    }

    fun addStatement(format: String, vararg args: Any) = apply {
      modifiers -= Modifier.ABSTRACT
      body.addStatement(format, *args)
    }

    fun build() = FunctionSpec(this)
  }

  companion object {

    private const val CONSTRUCTOR = "constructor()"
    private const val CALLABLE = "callable()"
    private const val INDEXABLE = "indexable()"

    private val String.isConstructor get() = this == CONSTRUCTOR
    private val String.isCallable get() = this == CALLABLE
    private val String.isIndexable get() = this == INDEXABLE

    /**
     * A free function or method.
     *
     * Constructors, call signatures and index signatures are internally discriminated by
     * name, so those three names are refused here: reaching them through this builder would
     * silently produce a different kind of member than the caller asked for. Use
     * [constructorBuilder], [callableBuilder] or [indexableBuilder] instead.
     */
    @JvmStatic
    fun builder(name: String): Builder {
      require(!name.isConstructor && !name.isCallable && !name.isIndexable) {
        "'$name' is reserved; use ${
          when (name) {
            CONSTRUCTOR -> "constructorBuilder()"
            CALLABLE -> "callableBuilder()"
            else -> "indexableBuilder()"
          }
        } instead"
      }
      return Builder(name)
    }

    /**
     * Builds an overload group: N signatures followed by the implementation.
     *
     * TypeScript writes overloads as several body-less signatures immediately followed by a
     * single implementation, all sharing a name. This validates that shape -- same name
     * throughout, implementation last -- and marks the signatures body-less, so callers pass
     * the result straight to `addFunctions`.
     *
     * @param signatures The overload signatures, in the order they should be declared
     * @param implementation The single implementation, which carries the body
     */
    @JvmStatic
    fun overloads(signatures: List<FunctionSpec>, implementation: FunctionSpec): List<FunctionSpec> {
      require(signatures.isNotEmpty()) { "an overload group needs at least one signature" }
      val mismatched = signatures.map { it.name }.filter { it != implementation.name }
      require(mismatched.isEmpty()) {
        "every overload signature must share the implementation's name " +
          "'${implementation.name}', but found $mismatched"
      }
      return signatures.map { it.toBuilder().signatureOnly().build() } + implementation
    }

    @JvmStatic
    fun constructorBuilder() = Builder(
      CONSTRUCTOR,
    )

    @JvmStatic
    fun callableBuilder() = Builder(
      CALLABLE,
    )

    @JvmStatic
    fun indexableBuilder() = Builder(
      INDEXABLE,
    )
  }
}
