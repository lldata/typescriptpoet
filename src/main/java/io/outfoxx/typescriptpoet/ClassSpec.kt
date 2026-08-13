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

import io.outfoxx.typescriptpoet.CodeBlock.Companion.joinToCode

/** A generated `class` declaration. */
class ClassSpec
private constructor(builder: Builder) : TypeSpec<ClassSpec, ClassSpec.Builder>(builder) {

  override val name = builder.name
  val tsDoc = builder.tsDoc.build()
  val decorators = builder.decorators.toImmutableList()
  val modifiers = builder.modifiers.toImmutableSet()
  val typeVariables = builder.typeVariables.toImmutableList()
  val superClass = builder.superClass
  val mixins = builder.mixins.toImmutableList()
  val propertySpecs = builder.propertySpecs.toImmutableList()
  val constructor = builder.constructor
  val functionSpecs = builder.functionSpecs.toImmutableList()
  val indexableSpecs = builder.indexableSpecs.toImmutableList()
  val staticBlocks = builder.staticBlocks.toImmutableList()
  val useConstructorPropertiesAutomatically = builder.useConstructorPropertiesAutomatically

  override fun emit(codeWriter: CodeWriter) {
    val constructorProperties: Map<String, PropertySpec> =
      if (useConstructorPropertiesAutomatically) {
        constructorProperties()
      } else {
        emptyMap()
      }

    codeWriter.emitTSDoc(tsDoc)
    codeWriter.emitDecorators(decorators, false)
    codeWriter.emitModifiers(modifiers, setOf(Modifier.PUBLIC))
    codeWriter.emit("class")
    codeWriter.emitCode(CodeBlock.of(" %L", name))
    codeWriter.emitTypeVariables(typeVariables)

    val superClass = if (superClass != null) CodeBlock.of("extends %T", superClass) else CodeBlock.empty()
    val mixins = mixins.map { CodeBlock.of("%T", it) }.let {
      if (it.isNotEmpty()) it.joinToCode(prefix = "implements ") else CodeBlock.empty()
    }

    val parents = (listOf(superClass) + mixins).filter { it.isNotEmpty() }
    if (parents.any { it.isNotEmpty() }) {
      codeWriter.emitCode(parents.joinToCode(separator = " ", prefix = " "))
    }

    codeWriter.emit(" {\n")
    codeWriter.indent()

    // Non-static properties.
    for (propertySpec in propertySpecs) {
      if (constructorProperties.containsKey(propertySpec.name) && !propertySpec.modifiers.contains(Modifier.STATIC)) {
        continue
      }
      codeWriter.emit("\n")
      propertySpec.emit(
        codeWriter,
        setOf(Modifier.PUBLIC),
        asStatement = true,
        compactOptionalAllowed = !useConstructorPropertiesAutomatically,
      )
    }

    // Index signatures.
    for (funSpec in indexableSpecs) {
      codeWriter.emit("\n")
      funSpec.emit(codeWriter, null, setOf(Modifier.PUBLIC))
    }

    emitConstructor(codeWriter, constructorProperties)

    // Static initializer blocks.
    for (block in staticBlocks) {
      codeWriter.emit("\n")
      codeWriter.emit("static {\n")
      codeWriter.indent()
      codeWriter.emitCode(block)
      codeWriter.unindent()
      codeWriter.emit("}\n")
    }

    // Constructors.
    for (funSpec in functionSpecs) {
      if (!funSpec.isConstructor) continue
      codeWriter.emit("\n")
      funSpec.emit(codeWriter, name, setOf(Modifier.PUBLIC))
    }

    // Functions (static and non-static).
    for (funSpec in functionSpecs) {
      if (funSpec.isConstructor) continue
      codeWriter.emit("\n")
      funSpec.emit(codeWriter, name, setOf(Modifier.PUBLIC))
    }

    codeWriter.unindent()

    if (!hasNoBody) {
      codeWriter.emit("\n")
    }
    codeWriter.emit("}\n")
  }

  /**
   * Emits the constructor, replacing property declarations with parameter properties where
   * the two describe the same thing. See [constructorProperties].
   */
  private fun emitConstructor(codeWriter: CodeWriter, constructorProperties: Map<String, PropertySpec>) {
    val constructor = constructor ?: return

    codeWriter.emit("\n")

    if (constructor.decorators.isNotEmpty()) {
      codeWriter.emit(" ")
      codeWriter.emitDecorators(constructor.decorators, false)
      codeWriter.emit("\n")
    }

    if (constructor.modifiers.isNotEmpty()) {
      codeWriter.emitModifiers(constructor.modifiers)
    }

    codeWriter.emit("constructor")

    constructor.parameters.emit(
      codeWriter,
      rest = constructor.restParameter,
      constructorProperties = constructorProperties,
    ) { param, isRest, optionalAllowed ->
      val promoted = if (isRest) null else constructorProperties[param.name]
      if (promoted != null) {
        emitParameterProperty(codeWriter, promoted, param)
      } else {
        param.emit(
          codeWriter,
          isRest = isRest,
          optionalAllowed = optionalAllowed && !useConstructorPropertiesAutomatically,
        )
      }
    }

    codeWriter.emit(" {\n")
    codeWriter.indent()
    codeWriter.emitCode(constructor.body)
    codeWriter.unindent()
    codeWriter.emit("}\n")
  }

  /**
   * Emits a property promoted into the constructor signature: `constructor(private name: string)`.
   *
   * TypeScript only treats a parameter as a property declaration when it carries an
   * accessibility or `readonly` modifier, so one is added when the property has none.
   */
  private fun emitParameterProperty(codeWriter: CodeWriter, property: PropertySpec, param: ParameterSpec) {
    val declaresProperty = property.modifiers.any { modifier ->
      modifier.isOneOf(Modifier.PUBLIC, Modifier.PRIVATE, Modifier.PROTECTED, Modifier.READONLY)
    }
    val declaration =
      if (declaresProperty) property else property.toBuilder().addModifiers(Modifier.PUBLIC).build()

    declaration.emit(codeWriter, setOf(), compactOptionalAllowed = false, withInitializer = false)
    param.emitDefaultValue(codeWriter)
  }

  /** Returns the properties that can be declared inline as constructor parameters. */
  private fun constructorProperties(): Map<String, PropertySpec> =
    propertySpecs.filter { it.name == it.initializer?.toString() }.associate { it.name to it }

  private val hasNoBody: Boolean
    get() {
      if (propertySpecs.isNotEmpty()) {
        val constructorProperties = constructorProperties()
        propertySpecs
          .filterNot { constructorProperties.containsKey(it.name) }
          .forEach { _ -> return false }
      }
      return constructor == null && functionSpecs.isEmpty() && indexableSpecs.isEmpty() &&
        staticBlocks.isEmpty()
    }

  /** A builder pre-populated with this spec, for deriving a modified copy. */
  fun toBuilder(): Builder {
    val builder = Builder(name)
    builder.tsDoc.add(tsDoc)
    builder.decorators += decorators
    builder.modifiers += modifiers
    builder.typeVariables += typeVariables
    builder.superClass = superClass
    builder.mixins += mixins
    builder.propertySpecs += propertySpecs
    builder.constructor = constructor
    builder.functionSpecs += functionSpecs
    builder.indexableSpecs += indexableSpecs
    builder.staticBlocks += staticBlocks
    return builder
  }

  class Builder(name: String) : TypeSpec.Builder<ClassSpec, Builder>(name) {

    internal val tsDoc = CodeBlock.builder()
    internal val decorators = mutableListOf<DecoratorSpec>()
    internal val modifiers = mutableListOf<Modifier>()
    internal val typeVariables = mutableListOf<TypeName.TypeVariable>()
    internal var superClass: TypeName? = null
    internal val mixins = mutableListOf<TypeName>()
    internal val propertySpecs = mutableListOf<PropertySpec>()
    internal var constructor: FunctionSpec? = null
    internal val functionSpecs = mutableListOf<FunctionSpec>()
    internal val indexableSpecs = mutableListOf<FunctionSpec>()
    internal val staticBlocks = mutableListOf<CodeBlock>()
    internal var useConstructorPropertiesAutomatically = true

    /** Adds TSDoc above the class. */
    fun addTSDoc(format: String, vararg args: Any) = apply {
      tsDoc.add(format, *args)
    }

    /** Adds TSDoc above the class. */
    fun addTSDoc(block: CodeBlock) = apply {
      tsDoc.add(block)
    }

    /** Adds decorators above the class: `@sealed`. */
    fun addDecorators(decoratorSpecs: Iterable<DecoratorSpec>) = apply {
      decorators += decoratorSpecs
    }

    /** Adds a decorator above the class: `@sealed`. */
    fun addDecorator(decoratorSpec: DecoratorSpec) = apply {
      decorators += decoratorSpec
    }

    /** Adds modifiers: `export abstract class Widget { }`. */
    fun addModifiers(vararg modifiers: Modifier) = apply {
      this.modifiers += modifiers
    }

    /** Adds type parameters: `class Widget<T> { }`. */
    fun addTypeVariables(typeVariables: Iterable<TypeName.TypeVariable>) = apply {
      this.typeVariables += typeVariables
    }

    /** Adds a type parameter: `class Widget<T> { }`. */
    fun addTypeVariable(typeVariable: TypeName.TypeVariable) = apply {
      typeVariables += typeVariable
    }

    /** Sets the superclass: `class Widget extends Base { }`. May be set only once. */
    fun superClass(superClass: TypeName) = apply {
      check(this.superClass == null) { "superclass already set to ${this.superClass}" }
      this.superClass = superClass
    }

    /** Adds implemented interfaces: `class Widget implements A, B { }`. */
    fun addMixins(mixins: Iterable<TypeName>) = apply {
      this.mixins += mixins
    }

    /** Adds an implemented interface: `class Widget implements Serializable { }`. */
    fun addMixin(mixin: TypeName) = apply {
      mixins += mixin
    }

    /** Sets the constructor: `constructor(name: string) { }`. */
    fun constructor(constructor: FunctionSpec?) = apply {
      if (constructor != null) {
        require(constructor.isConstructor) {
          "expected a constructor but was ${constructor.name}; use FunctionSpec.constructorBuilder when building"
        }
      }
      this.constructor = constructor
    }

    /** Adds properties: `name: string;`. */
    fun addProperties(propertySpecs: Iterable<PropertySpec>) = apply {
      this.propertySpecs += propertySpecs
    }

    /** Adds a property: `name: string;`. */
    fun addProperty(propertySpec: PropertySpec) = apply {
      propertySpecs += propertySpec
    }

    /** Adds a property: `private readonly name?: string;`. */
    @JvmOverloads
    fun addProperty(name: String, type: TypeName, optional: Boolean = false, vararg modifiers: Modifier) =
      addProperty(PropertySpec.builder(name, type, optional, *modifiers).build())

    /** Adds methods. Pass a [FunctionSpec.overloads] group to add an overload set. */
    fun addFunctions(functionSpecs: Iterable<FunctionSpec>) = apply {
      functionSpecs.forEach { addFunction(it) }
    }

    /** Adds a method: `greet(): string { }`. */
    fun addFunction(functionSpec: FunctionSpec) = apply {
      require(!functionSpec.isConstructor) { "Use the 'constructor' method for the constructor" }
      this.functionSpecs += functionSpec
    }

    /** Adds index signatures: `[key: string]: unknown;`. */
    fun addIndexables(indexableSpecs: Iterable<FunctionSpec>) = apply {
      indexableSpecs.forEach { addIndexable(it) }
    }

    /**
     * Adds an index signature (e.g. `[key: string]: unknown;`).
     *
     * Unlike an interface's, a class index signature is a concrete declaration, so ABSTRACT
     * is rejected rather than required.
     */
    fun addIndexable(functionSpec: FunctionSpec) = apply {
      require(functionSpec.isIndexable) {
        "expected an index signature but was ${functionSpec.name}; " +
          "use FunctionSpec.indexableBuilder when building"
      }
      require(!functionSpec.modifiers.contains(Modifier.ABSTRACT)) {
        "Class indexables must not be ABSTRACT"
      }
      this.indexableSpecs += functionSpec
    }

    /** Adds a `static { ... }` initializer block. */
    fun addStaticBlock(codeBlock: CodeBlock) = apply {
      this.staticBlocks += codeBlock
    }

    /** Adds a `static { ... }` initializer block. */
    fun addStaticBlock(format: String, vararg args: Any?) = addStaticBlock(CodeBlock.of(format, *args))

    @JvmOverloads
    fun allowUsingConstructorPropertiesAutomatically(value: Boolean = true) = apply {
      this.useConstructorPropertiesAutomatically = value
    }

    override fun build(): ClassSpec {
      val isAbstract = modifiers.contains(Modifier.ABSTRACT)
      for (functionSpec in functionSpecs) {
        require(isAbstract || !functionSpec.modifiers.contains(Modifier.ABSTRACT)) {
          "non-abstract type $name cannot declare abstract function ${functionSpec.name}"
        }
      }

      return ClassSpec(this)
    }
  }

  companion object {

    /** A class: `class Widget { }`. */
    @JvmStatic
    fun builder(name: String) = Builder(name)

    /** A class: `class Widget { }`. */
    @JvmStatic
    fun builder(name: TypeName) = Builder("$name")
  }
}
