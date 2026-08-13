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

/** A generated property declaration. */
class PropertySpec
private constructor(builder: Builder) : Taggable(builder.tags.toImmutableMap()) {

  val name = builder.name
  val type = builder.type
  val tsDoc = builder.tsDoc.build()
  val decorators = builder.decorators.toImmutableList()
  val modifiers = builder.modifiers.toImmutableSet()
  val initializer = builder.initializer
  val optional = builder.optional
  val definiteAssignment = builder.definiteAssignment

  init {
    require(!(optional && definiteAssignment)) {
      "property $name cannot be both optional and definitely assigned"
    }
    require(!(name.startsWith("#") && Modifier.PRIVATE in builder.modifiers)) {
      "property $name is already private by virtue of its name; drop Modifier.PRIVATE"
    }
  }

  internal fun emit(
    codeWriter: CodeWriter,
    implicitModifiers: Set<Modifier>,
    asStatement: Boolean = false,
    withInitializer: Boolean = true,
    compactOptionalAllowed: Boolean = false,
  ) {
    codeWriter.emitTSDoc(tsDoc)
    codeWriter.emitDecorators(decorators, false)
    codeWriter.emitModifiers(modifiers, implicitModifiers)
    codeWriter.emitCode(
      CodeBlock.of(
        "%L${suffix(compactOptionalAllowed)}: %T${if (optional && !compactOptionalAllowed) " | undefined" else ""}",
        name,
        type,
      ),
    )
    if (withInitializer && initializer != null) {
      codeWriter.emit(" = ")
      // The statement wrapper gives long expressions a hanging indent on continuation lines,
      // which is wrong for a block-shaped value such as an arrow function or object literal:
      // it would push the body and closing brace out by two extra levels. Those bring their
      // own indentation, so emit them unwrapped.
      if (initializer.toString().contains("\n")) {
        codeWriter.emitCode(CodeBlock.of("%L", initializer))
      } else {
        codeWriter.emitCode(CodeBlock.of("%[%L%]", initializer))
      }
    }
    if (asStatement) {
      codeWriter.emit(";\n")
    }
  }

  /** The `?` or `!` that follows the property name, if any. */
  private fun suffix(compactOptionalAllowed: Boolean) = when {
    optional && compactOptionalAllowed -> "?"
    definiteAssignment -> "!"
    else -> ""
  }

  override fun toString() = buildCodeString { emit(this, emptySet()) }

  /** A builder pre-populated with this spec, for deriving a modified copy. */
  fun toBuilder(): Builder {
    val bldr = Builder(name, type, optional)
      .addTSDoc(tsDoc)
      .addDecorators(decorators)
      .addModifiers(*modifiers.toTypedArray())
    initializer?.let { bldr.initializer(it) }
    bldr.definiteAssignment(definiteAssignment)

    return bldr
  }

  class Builder internal constructor(
    internal val name: String,
    internal val type: TypeName,
    internal var optional: Boolean,
  ) : Taggable.Builder<Builder>() {

    internal val tsDoc = CodeBlock.builder()
    internal val decorators = mutableListOf<DecoratorSpec>()
    internal val modifiers = mutableListOf<Modifier>()
    internal var initializer: CodeBlock? = null
    internal var definiteAssignment: Boolean = false

    /** Adds TSDoc above the property. */
    fun addTSDoc(format: String, vararg args: Any) = apply {
      tsDoc.add(format, *args)
    }

    /** Adds TSDoc above the property. */
    fun addTSDoc(block: CodeBlock) = apply {
      tsDoc.add(block)
    }

    /** Adds decorators above the property: `@observable`. */
    fun addDecorators(decoratorSpecs: Iterable<DecoratorSpec>) = apply {
      decorators += decoratorSpecs
    }

    /** Adds a decorator above the property: `@observable`. */
    fun addDecorator(decoratorSpec: DecoratorSpec) = apply {
      decorators += decoratorSpec
    }

    /** Adds modifiers: `private static readonly name: string;`. */
    fun addModifiers(vararg modifiers: Modifier) = apply {
      this.modifiers += modifiers
    }

    /** Marks the property optional: `name?: string;`. */
    fun optional(optional: Boolean) = apply {
      this.optional = optional
    }

    /** Sets the initializer: `name: string = 'world';`. */
    fun initializer(format: String, vararg args: Any?) = initializer(
      CodeBlock.of(format, *args),
    )

    /** Sets the initializer: `name: string = 'world';`. May be set only once. */
    fun initializer(codeBlock: CodeBlock) = apply {
      check(this.initializer == null) { "initializer was already set" }
      this.initializer = codeBlock
    }

    /**
     * Marks the property as definitely assigned, emitting `name!: Type`.
     *
     * Mutually exclusive with `optional`.
     */
    @JvmOverloads
    fun definiteAssignment(value: Boolean = true) = apply {
      this.definiteAssignment = value
    }

    fun build() = PropertySpec(this)
  }

  companion object {

    /** A property or top-level variable: `name: string;`. */
    @JvmStatic
    @JvmOverloads
    fun builder(name: String, type: TypeName, optional: Boolean = false, vararg modifiers: Modifier): Builder =
      Builder(name, type, optional).addModifiers(*modifiers)
  }
}
