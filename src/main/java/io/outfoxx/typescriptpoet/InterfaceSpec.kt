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

/** A generated `interface` declaration. */
class InterfaceSpec
private constructor(builder: Builder) : TypeSpec<InterfaceSpec, InterfaceSpec.Builder>(builder) {

  override val name = builder.name
  val tsDoc = builder.tsDoc.build()
  val modifiers = builder.modifiers.toImmutableSet()
  val typeVariables = builder.typeVariables.toImmutableList()
  val superInterfaces = builder.superInterfaces.toImmutableList()
  val propertySpecs = builder.propertySpecs.toImmutableList()
  val functionSpecs = builder.functionSpecs.toImmutableList()
  val indexableSpecs = builder.indexableSpecs.toImmutableList()
  val callable = builder.callable

  override fun emit(codeWriter: CodeWriter) {
    var wroteMember = false
    fun separate() {
      if (wroteMember) codeWriter.emit("\n")
      wroteMember = true
    }

    codeWriter.emitTSDoc(tsDoc)
    codeWriter.emitModifiers(modifiers, setOf())
    codeWriter.emit("interface")
    codeWriter.emitCode(CodeBlock.of(" %L", name))
    val superClasses = superInterfaces.map { CodeBlock.of("%T", it) }.let {
      if (it.isNotEmpty()) it.joinToCode(prefix = " extends ") else CodeBlock.empty()
    }

    // As for a class: the `extends` clause cannot break, so all of it counts, plus the ` {`.
    val trailing = codeWriter.measure { emitCode(superClasses) }.length + 2
    codeWriter.emitTypeVariables(typeVariables, trailingWidth = trailing)

    codeWriter.emitCode(superClasses)

    codeWriter.emit(" ")
    codeWriter.emitBody(hasNoBody) {
      emitMembers(codeWriter, ::separate)
    }
  }

  // The body's members, in the order an interface writes them.
  private fun emitMembers(codeWriter: CodeWriter, separate: () -> Unit) {
    // Callable
    callable?.let {
      separate()
      it.emit(codeWriter, null, setOf(Modifier.ABSTRACT))
    }

    // Properties.
    for (propertySpec in propertySpecs) {
      separate()
      propertySpec.emit(
        codeWriter,
        setOf(Modifier.PUBLIC),
        asStatement = true,
        compactOptionalAllowed = true,
      )
    }

    // Indexables
    for (funSpec in indexableSpecs) {
      separate()
      funSpec.emit(codeWriter, null, setOf(Modifier.PUBLIC, Modifier.ABSTRACT))
    }

    // Functions.
    for (funSpec in functionSpecs) {
      if (funSpec.isConstructor) continue
      separate()
      funSpec.emit(codeWriter, name, setOf(Modifier.PUBLIC, Modifier.ABSTRACT))
    }
  }

  private val hasNoBody: Boolean
    get() {
      return propertySpecs.isEmpty() && functionSpecs.isEmpty() && indexableSpecs.isEmpty() && callable == null
    }

  /** A builder pre-populated with this spec, for deriving a modified copy. */
  fun toBuilder(): Builder {
    val builder = Builder(name)
    builder.tsDoc.add(tsDoc)
    builder.modifiers += modifiers
    builder.typeVariables += typeVariables
    builder.superInterfaces += superInterfaces
    builder.propertySpecs += propertySpecs
    builder.functionSpecs += functionSpecs
    builder.indexableSpecs += indexableSpecs
    builder.callable = callable
    return builder
  }

  class Builder(name: String) : TypeSpec.Builder<InterfaceSpec, Builder>(name) {

    internal val tsDoc = CodeBlock.builder()
    internal val modifiers = mutableListOf<Modifier>()
    internal val typeVariables = mutableListOf<TypeName.TypeVariable>()
    internal val superInterfaces = mutableListOf<TypeName>()
    internal val propertySpecs = mutableListOf<PropertySpec>()
    internal val functionSpecs = mutableListOf<FunctionSpec>()
    internal val indexableSpecs = mutableListOf<FunctionSpec>()
    internal var callable: FunctionSpec? = null

    /** Adds TSDoc above the interface. */
    fun addTSDoc(format: String, vararg args: Any) = apply {
      tsDoc.add(format, *args)
    }

    /** Adds TSDoc above the interface. */
    fun addTSDoc(block: CodeBlock) = apply {
      tsDoc.add(block)
    }

    /** Adds modifiers: `export interface Person { }`. */
    fun addModifiers(vararg modifiers: Modifier) = apply {
      this.modifiers += modifiers
    }

    /** Adds type parameters: `interface Box<T> { }`. */
    fun addTypeVariables(typeVariables: Iterable<TypeName.TypeVariable>) = apply {
      this.typeVariables += typeVariables
    }

    /** Adds a type parameter: `interface Box<T> { }`. */
    fun addTypeVariable(typeVariable: TypeName.TypeVariable) = apply {
      typeVariables += typeVariable
    }

    /** Adds an extended interface: `interface Admin extends Person { }`. */
    fun addSuperInterface(superClass: TypeName) = apply {
      this.superInterfaces.add(superClass)
    }

    /** Adds members: `name: string;`. Initializers and decorators are not allowed. */
    fun addProperties(propertySpecs: Iterable<PropertySpec>) = apply {
      propertySpecs.forEach { addProperty(it) }
    }

    /** Adds a member: `readonly name?: string;`. Initializers and decorators are not allowed. */
    fun addProperty(propertySpec: PropertySpec) = apply {
      require(propertySpec.decorators.isEmpty()) { "Interface properties cannot have decorators" }
      require(propertySpec.initializer == null) { "Interface properties cannot have initializers" }
      propertySpecs += propertySpec
    }

    /** Adds a member: `readonly name?: string;`. */
    @JvmOverloads
    fun addProperty(name: String, type: TypeName, optional: Boolean = false, vararg modifiers: Modifier) =
      addProperty(PropertySpec.builder(name, type, optional, *modifiers).build())

    /** Adds method signatures: `greet(): string;`. */
    fun addFunctions(functionSpecs: Iterable<FunctionSpec>) = apply {
      functionSpecs.forEach { addFunction(it) }
    }

    /** Adds a method signature: `greet(): string;`. Emitted body-less. */
    fun addFunction(functionSpec: FunctionSpec) = apply {
      require(functionSpec.modifiers.contains(Modifier.ABSTRACT)) { "Interface methods must be abstract" }
      require(functionSpec.body.isEmpty()) { "Interface methods cannot have code" }
      require(!functionSpec.isConstructor) { "Interfaces cannot have a constructor" }
      require(functionSpec.decorators.isEmpty()) { "Interface functions cannot have decorators" }
      this.functionSpecs += functionSpec
    }

    /** Adds index signatures: `[key: string]: unknown;`. */
    fun addIndexables(indexableSpecs: Iterable<FunctionSpec>) = apply {
      indexableSpecs.forEach { addIndexable(it) }
    }

    /** Adds an index signature: `[key: string]: unknown;`. Must be ABSTRACT. */
    fun addIndexable(functionSpec: FunctionSpec) = apply {
      require(functionSpec.modifiers.contains(Modifier.ABSTRACT)) { "Indexables must be ABSTRACT" }
      this.indexableSpecs += functionSpec
    }

    /** Sets the call signature: `(value: string): number;`. */
    fun callable(callable: FunctionSpec?) = apply {
      if (callable != null) {
        require(callable.isCallable) {
          "expected a callable signature but was ${callable.name}; use FunctionSpec.callableBuilder when building"
        }
        require(callable.modifiers == setOf(Modifier.ABSTRACT)) { "Callable must be ABSTRACT and nothing else" }
      }
      this.callable = callable
    }

    override fun build(): InterfaceSpec = InterfaceSpec(this)
  }

  companion object {

    /** An interface: `interface Person { }`. */
    @JvmStatic
    fun builder(name: String) = Builder(name)

    /** An interface: `interface Person { }`. */
    @JvmStatic
    fun builder(name: TypeName) = Builder("$name")

    /** An interface derived from a class's public members. */
    @JvmStatic
    fun builder(classSpec: ClassSpec): Builder {
      val builder = Builder(classSpec.name)
        .addModifiers(*classSpec.modifiers.toTypedArray())
        .addProperties(classSpec.propertySpecs)
      builder.functionSpecs.forEach { builder.addFunction(it.abstract()) }
      return builder
    }
  }
}
