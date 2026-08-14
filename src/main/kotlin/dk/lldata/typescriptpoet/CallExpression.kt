/*
 * Copyright 2017 Outfox, Inc.
 * Copyright 2026 LL Data ApS
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

/**
 * A call expression: `apiRequest<Listing>({ method: "POST", path }, config, options)`.
 *
 * Built through [CodeBlock.call] and used as a `%L` argument, as [ObjectLiteral] is, because a
 * call is an expression rather than a declaration:
 *
 * ```kotlin
 * val request = CodeBlock.call(apiRequest)
 *   .addTypeArgument(returns)
 *   .addArgument(body)
 *   .addArgument("config")
 *   .addArgument("options")
 *   .build()
 *
 * FunctionSpec.builder("get").addStatement("return %L", request).build()
 * ```
 *
 * The point of the type is the argument list. Written as a format string --
 * `addStatement("return %T(%L, config, options)", …)` -- the `, config, options)` is opaque
 * text, and a writer cannot break a line at a boundary it cannot see. Keeping the arguments
 * as a list is what lets them be measured and broken, the same way a parameter list is:
 *
 * ```typescript
 * return apiRequest<CompleteRegistrationResponse>(
 *   { method: "POST", path, body },
 *   config,
 *   options,
 * );
 * ```
 */
class CallExpression
internal constructor(
  internal val callee: CodeBlock,
  internal val typeArguments: List<TypeName>,
  internal val arguments: List<CodeBlock>,
  internal val isNew: Boolean,
) {

  internal fun emit(codeWriter: CodeWriter) {
    if (isNew) codeWriter.emit("new ")
    codeWriter.emitCode(callee)
    emitTypeArguments(codeWriter)

    // The column the `(` lands on, which is where the argument list has to fit from.
    val column = codeWriter.currentColumn
    if (arguments.isEmpty()) {
      codeWriter.emit("()")
      return
    }

    // Measure, then break, as a parameter list does, and all-or-nothing for the same reason:
    // breaking only the arguments that overflow is what produces `apiRequest({\n … \n}, config,
    // options)`, where the one argument that could lay itself out did and the rest stayed put.
    val rendered = arguments.map { argument -> codeWriter.measure { emitCode(argument) } }
    val inlineWidth = rendered.sumOf { it.length } + SEPARATOR_WIDTH * (arguments.size - 1)
    // An argument that spans lines of its own -- an arrow with a body -- breaks the list
    // however short it measures, since the line it sits on has already ended.
    val fits = rendered.none { it.contains('\n') } &&
      column + inlineWidth + PARENS_WIDTH + codeWriter.trailingWidth <= codeWriter.printWidth

    if (fits) {
      emitInline(codeWriter, rendered)
    } else {
      emitOnePerLine(codeWriter)
    }
  }

  private fun emitTypeArguments(codeWriter: CodeWriter) {
    if (typeArguments.isEmpty()) return

    // Type arguments to a call stay on one line. Prettier breaks the argument list long
    // before it breaks these, and a call generic enough to need it is not a shape this
    // library is trying to win at.
    codeWriter.emit("<")
    typeArguments.forEachIndexed { index, typeArgument ->
      if (index > 0) codeWriter.emit(", ")
      codeWriter.emitCode(CodeBlock.of("%T", typeArgument))
    }
    codeWriter.emit(">")
  }

  private fun emitInline(codeWriter: CodeWriter, rendered: List<String>) {
    codeWriter.emit("(")
    arguments.forEachIndexed { index, argument ->
      if (index > 0) codeWriter.emit(SEPARATOR)
      // What still has to fit after this argument: the ones after it and their separators,
      // the closing paren, and whatever follows the call itself.
      val after = rendered.drop(index + 1).sumOf { it.length } +
        SEPARATOR_WIDTH * (arguments.size - 1 - index) + 1 + codeWriter.trailingWidth
      codeWriter.withTrailingWidth(after) { codeWriter.emitCode(argument) }
    }
    codeWriter.emit(")")
  }

  private fun emitOnePerLine(codeWriter: CodeWriter) {
    codeWriter.emit("(\n")
    codeWriter.indent()
    arguments.forEach { argument ->
      // The comma is all that follows an argument on its own line.
      codeWriter.withTrailingWidth(1) { codeWriter.emitCode(argument) }
      codeWriter.emit(",\n")
    }
    codeWriter.unindent()
    codeWriter.emit(")")
  }

  override fun toString() = buildCodeString { emit(this) }

  /** Builds a [CallExpression]. Obtained from [CodeBlock.call] or [CodeBlock.newInstance]. */
  class Builder internal constructor(private val callee: CodeBlock, private val isNew: Boolean) {

    private val typeArguments = mutableListOf<TypeName>()
    private val arguments = mutableListOf<CodeBlock>()

    /** Adds an explicit type argument: the `Listing` in `apiRequest<Listing>(…)`. */
    fun addTypeArgument(typeArgument: TypeName) = apply {
      typeArguments += typeArgument
    }

    /** Adds one argument. */
    fun addArgument(argument: CodeBlock) = apply {
      arguments += argument
    }

    /** Adds one argument, written as a format string. */
    fun addArgument(format: String, vararg args: Any?) = addArgument(CodeBlock.of(format, *args))

    /** Adds an object literal argument: `apiRequest({ method: "POST" })`. */
    fun addArgument(argument: ObjectLiteral) = addArgument(CodeBlock.of("%L", argument))

    /** Adds a function argument: `map((x) => x * 2)`. */
    fun addArgument(argument: FunctionSpec) = addArgument(CodeBlock.of("%L", argument))

    /** Adds a nested call argument: `outer(inner(x))`. */
    fun addArgument(argument: CallExpression) = addArgument(CodeBlock.of("%L", argument))

    fun build() = CallExpression(
      callee,
      typeArguments.toImmutableList(),
      arguments.toImmutableList(),
      isNew,
    )
  }

  private companion object {

    const val SEPARATOR = ", "
    const val SEPARATOR_WIDTH = SEPARATOR.length

    /** The `(` and `)` around the list. */
    const val PARENS_WIDTH = 2
  }
}
