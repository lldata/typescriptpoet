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
package dk.lldata.typescriptpoet

/** A generated function or class decorator declaration. */
class DecoratorSpec
internal constructor(builder: Builder) : Taggable(builder.tags.toImmutableMap()) {

  val name = builder.name
  val parameters = builder.parameters
  val factory = builder.factory

  internal fun emit(codeWriter: CodeWriter) {
    codeWriter.emitCode(CodeBlock.of("@%Q", name))

    if (parameters.isNotEmpty()) {
      codeWriter.emitCode("(")

      parameters.forEachIndexed { index, (first, second) ->
        if (index > 0 && index < parameters.size) {
          codeWriter.emitCode(",%W")
        }
        if (first != null) {
          codeWriter.emit("/* $first */ ")
        }
        codeWriter.emitCode(second)
      }

      codeWriter.emitCode(")")
    } else if (factory == true) {
      // This branch is the empty-parameter case, so the elvis fallback that used to be here
      // -- `factory ?: parameters.isNotEmpty()` -- could only ever evaluate to false.
      codeWriter.emit("()")
    }
  }

  override fun toString() = buildCodeString { emit(this) }

  /** A builder pre-populated with this spec, for deriving a modified copy. */
  fun toBuilder(): Builder {
    val builder = Builder(name)
    builder.parameters += parameters
    builder.factory = factory
    return builder
  }

  class Builder
  internal constructor(val name: SymbolSpec) : Taggable.Builder<Builder>() {

    internal val parameters = mutableListOf<Pair<String?, CodeBlock>>()
    internal var factory: Boolean? = null

    /** Emits empty parentheses when there are no parameters: `@sealed()` rather than `@sealed`. */
    fun asFactory() = apply {
      this.factory = true
    }

    /** Adds an argument: `@inject(Engine)`. A [name] is emitted as an inline block comment before it. */
    @JvmOverloads
    fun addParameter(name: String? = null, format: String, vararg args: Any?) = apply {
      parameters += name to CodeBlock.of(format, *args)
    }

    /** Adds an argument: `@inject(Engine)`. */
    @JvmOverloads
    fun addParameter(name: String? = null, codeBlock: CodeBlock) = apply {
      parameters += name to codeBlock
    }

    /** Builds the decorator. */
    fun build(): DecoratorSpec = DecoratorSpec(this)
  }

  companion object {

    /** A decorator: `@sealed`. The name is parsed as a symbol spec, so it can carry an import. */
    @JvmStatic
    fun builder(name: String): Builder = Builder(SymbolSpec.from(name))

    /** A decorator: `@sealed`, imported from [name]'s module. */
    @JvmStatic
    fun builder(name: SymbolSpec): Builder = Builder(name)
  }
}
