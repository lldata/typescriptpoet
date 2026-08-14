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

import java.io.Closeable
import java.util.Stack

/**
 * Converts a [FileSpec] to a string suitable to both human- and tsc-consumption. This honors
 * imports, indentation, and deferred variable names.
 */
private const val PRINT_WIDTH = 80

/** The `, ` between two type variables, and the `<` and `>` around the list. */
private const val SEPARATOR_WIDTH = 2
private const val BRACKETS_WIDTH = 2

internal class CodeWriter(
  out: Appendable,
  private val indent: String = "  ",
  val renamedSymbols: Map<SymbolSpec, String> = emptyMap(),
) : Closeable {

  private val out = LineWrapper(out, indent, PRINT_WIDTH)

  /** The column the next character would be written at, for measure-then-break decisions. */
  val currentColumn: Int get() = out.column

  /** The column at which a line is considered too long. */
  val printWidth: Int get() = PRINT_WIDTH

  /**
   * How much of the current line is still to be written after the value being emitted.
   *
   * A value that lays itself out -- an object literal, a call's argument list, an inline
   * object type -- fits only if the whole *line* fits, so it has to count what comes after it
   * as well as itself. Without this, `{ method: "POST", path, body }` measures as fitting and
   * the `, config, options);` that follows it takes the line past the print width.
   *
   * [emitCode] works this out from the format parts still to come; a list that emits its own
   * elements sets it around each of them with [withTrailingWidth].
   */
  var trailingWidth: Int = 0
    private set

  /** Runs [block] with [trailingWidth] set to [width], restoring it afterwards. */
  fun <T> withTrailingWidth(width: Int, block: () -> T): T {
    val previous = trailingWidth
    trailingWidth = width
    try {
      return block()
    } finally {
      trailingWidth = previous
    }
  }

  /** The column a line one level deeper than the current one starts at. */
  val nextIndentColumn: Int get() = (indentLevel + 1) * indent.length
  private var indentLevel = 0

  private var tsDoc = false
  private var comment = false
  private var trailingNewline = false
  private var referencedSymbols = mutableSetOf<SymbolSpec>()
  private val scope = Stack<String>()

  inline fun <reified S : SymbolSpec> referencedSymbols() = referencedSymbols.filterIsInstance<S>().toImmutableSet()

  fun currentScope(): List<String> = scope.toImmutableList()

  fun pushScope(name: String) {
    scope.push(name)
  }

  fun popScope() {
    scope.pop()
  }

  /**
   * Whether a statement is being written. The first line of a statement is indented normally and
   * subsequent wrapped lines are double-indented.
   */
  private var inStatement = false

  /**
   * Whether that double indent has been applied. It goes on lazily, at the statement's first
   * newline, so a statement that never wraps never pays for it -- which means whether it is
   * owed cannot be inferred from the line count. [emitLiteral] emits a value that brings its
   * own layout with the statement suspended, so a statement can reach its end having written
   * several lines and no hanging indent at all.
   */
  private var statementIndented = false

  fun indent(levels: Int = 1) = apply {
    indentLevel += levels
  }

  fun unindent(levels: Int = 1) = apply {
    require(indentLevel - levels >= 0) { "cannot unindent $levels from $indentLevel" }
    indentLevel -= levels
  }

  /**
   * Emits a brace-delimited body: `{`, whatever [body] writes, indented, and `}`. An empty one
   * collapses to `{}`.
   *
   * Prettier writes `{}` for every empty body there is -- a class, an interface, a namespace,
   * an enum, a function, a method, a constructor, an arrow. That is not a rare shape in
   * generated code: a constructor whose parameters are all parameter properties has nothing
   * left to do. One place for the rule rather than one per construct, which is how the six
   * copies of the two-line form came to exist.
   *
   * A body-less *declaration* is a different thing and is not this: an abstract member or a
   * signature emits `m(): void;`, with no braces at all.
   *
   * @param endsLine whether the closing brace ends the line. An arrow function's body sits in
   *   expression position, so the statement around it owns the terminator.
   */
  inline fun emitBody(isEmpty: Boolean, endsLine: Boolean = true, body: () -> Unit) {
    if (isEmpty) {
      emit(if (endsLine) "{}\n" else "{}")
      return
    }
    emit("{\n")
    indent()
    body()
    unindent()
    emit(if (endsLine) "}\n" else "}")
  }

  fun emitComment(codeBlock: CodeBlock) {
    trailingNewline = true // Force the '//' prefix for the comment.
    comment = true
    try {
      emitCode(codeBlock)
      emit("\n")
    } finally {
      comment = false
    }
  }

  fun emitTSDoc(tsDocCodeBlock: CodeBlock) {
    if (tsDocCodeBlock.isEmpty()) return

    emit("/**\n")
    tsDoc = true
    try {
      emitCode(tsDocCodeBlock)
    } finally {
      tsDoc = false
    }
    emit(" */\n")
  }

  fun emitDecorators(decorators: List<DecoratorSpec>, inline: Boolean) {
    for (decoratorSpec in decorators) {
      decoratorSpec.emit(this)
      emitCode(if (inline) "%W" else "\n")
    }
  }

  /**
   * Emits `modifiers` in the standard order. Modifiers in `implicitModifiers` will not
   * be emitted.
   */
  fun emitModifiers(modifiers: Set<Modifier>, implicitModifiers: Set<Modifier> = emptySet()) {
    if (modifiers.isEmpty()) return
    for (modifier in modifiers.inEmitOrder()) {
      if (implicitModifiers.contains(modifier)) continue
      emit(modifier.keyword)
      emit(" ")
    }
  }

  /**
   * Emit type variables with their bounds.
   *
   * This should only be used when declaring type variables; everywhere else bounds are omitted.
   *
   * @param trailingWidth how much of what follows the `>` has to fit on the same line. Only
   *   what cannot break itself counts: a parameter list can, so a function passes the width of
   *   the `(` alone and lets its parameters break on their own, while a class's `extends`
   *   clause cannot and so is counted in full.
   */
  @JvmOverloads
  fun emitTypeVariables(typeVariables: List<TypeName.TypeVariable>, trailingWidth: Int = 0) {
    if (typeVariables.isEmpty()) return

    // Measure, then break, as for parameters and unions: keep the list on one line if the
    // declaration fits, otherwise one variable per line with a trailing comma and the `>`
    // back at the declaration's own indent, where whatever follows continues.
    val inlineWidth = typeVariables.sumOf { measure { emitTypeVariable(it) }.length } +
      SEPARATOR_WIDTH * (typeVariables.size - 1) + BRACKETS_WIDTH
    if (currentColumn + inlineWidth + trailingWidth <= printWidth) {
      emit("<")
      typeVariables.forEachIndexed { index, typeVariable ->
        if (index > 0) emit(", ")
        emitTypeVariable(typeVariable)
      }
      emit(">")
      return
    }

    emit("<\n")
    indent()
    typeVariables.forEach {
      emitTypeVariable(it)
      emit(",\n")
    }
    unindent()
    emit(">")
  }

  /** One type variable with its bounds: `const T extends A & keyof B`. */
  private fun emitTypeVariable(typeVariable: TypeName.TypeVariable) {
    // `const` and variance are declaration-site only; TypeVariable.emit deliberately
    // omits them so use sites stay bare.
    if (typeVariable.isConst) {
      emit("const ")
    }
    typeVariable.variance?.let { emit("${it.keyword} ") }
    emit(typeVariable.name)
    if (typeVariable.bounds.isEmpty()) return

    emit(" extends ")
    typeVariable.bounds.forEachIndexed { index, bound ->
      if (index > 0) emit(" ${bound.combiner.symbol} ")
      bound.modifier?.let { emit("${it.keyword} ") }
      // Through %T, so the writer emits the bound and collects its import. This used to
      // interpolate the rendered type into the format string, which put the right text in the
      // file and never told the import collector about it -- `X extends Base` with no
      // `import { Base }` anywhere.
      emitCode(CodeBlock.of("%T", bound.type))
    }
  }

  fun emitSymbol(symbolSpec: SymbolSpec) {
    if (symbolSpec.isTopLevelSymbol) {
      referencedSymbols.add(symbolSpec)
      emit(renamedSymbols[symbolSpec] ?: symbolSpec.value)
    } else {
      val topLevel = symbolSpec.topLevel()
      referencedSymbols.add(topLevel)
      emit(renamedSymbols[topLevel] ?: topLevel.value)
      emit(".")
      emit(symbolSpec.value.split(".").drop(1).joinToString("."))
    }
  }

  /**
   * Renders [block] to a string without writing anything, for a measure-then-break decision.
   *
   * The measuring writer carries this one's scope and renames, so what is measured is the text
   * that would actually be written: an import renamed around a collision, or a name made
   * relative to the namespace it sits in, is not the length it has anywhere else.
   */
  fun measure(block: CodeWriter.() -> Unit): String {
    val rendered = StringBuilder()
    CodeWriter(rendered, indent, renamedSymbols).use { writer ->
      currentScope().forEach(writer::pushScope)
      writer.block()
    }
    return rendered.toString()
  }

  fun emitCode(s: String) = emitCode(CodeBlock.of(s))

  fun emitCode(codeBlock: CodeBlock) = apply {
    var a = 0
    val partIterator = codeBlock.formatParts.listIterator()
    while (partIterator.hasNext()) {
      when (val part = partIterator.next()) {
        // %L and %T are the two placeholders whose argument may decide its own layout, so
        // they are the two that need to know how much of the line is already spoken for.
        "%L" -> {
          val arg = codeBlock.args[a++]
          withTrailingWidth(trailingWidthAfter(codeBlock, partIterator.nextIndex())) {
            emitLiteral(arg)
          }
        }

        "%N" -> emit(codeBlock.args[a++] as String)

        "%S" -> emitString(codeBlock.args[a++] as String?)

        "%P" -> emitStringTemplate(codeBlock.args[a++] as String?)

        "%T" -> {
          val arg = codeBlock.args[a++] as TypeName
          withTrailingWidth(trailingWidthAfter(codeBlock, partIterator.nextIndex())) {
            emitTypeName(arg)
          }
        }

        "%Q" -> emitSymbol(codeBlock.args[a++] as SymbolSpec)

        "%%" -> emit("%")

        "%>" -> indent()

        "%<" -> unindent()

        "%[" -> beginStatement()

        "%]" -> endStatement()

        "%W" -> emitWrappingSpace()

        else -> {
          // Handle deferred type.
          emit(part)
        }
      }
    }
  }

  /**
   * How much of the current line [codeBlock] still has to write from [partIndex] on.
   *
   * Only the plain text counts. The walk stops at the first newline, which ends the line and
   * with it anything the enclosing block would have added; and at the first placeholder that
   * takes an argument, whose width is not known without rendering it. Stopping there
   * *under*-estimates, which can only leave a value inline that a fuller measure would have
   * broken -- never break one that would have fitted.
   */
  private fun trailingWidthAfter(codeBlock: CodeBlock, partIndex: Int): Int {
    var width = 0
    for (index in partIndex until codeBlock.formatParts.size) {
      when (val part = codeBlock.formatParts[index]) {
        "%>", "%<", "%[", "%]" -> Unit

        // A wrapping space is a space until something decides otherwise; a doubled percent
        // is one character.
        "%W", "%%" -> width++

        "%L", "%N", "%S", "%P", "%T", "%Q" -> return width

        else -> {
          val newline = part.indexOf('\n')
          if (newline != -1) return width + newline
          width += part.length
        }
      }
    }
    // The block ended without a newline, so whatever encloses it continues this line.
    return width + trailingWidth
  }

  private fun beginStatement() {
    check(!inStatement) { "statement enter %[ followed by statement enter %[" }

    inStatement = true
    statementIndented = false
  }

  private fun endStatement() {
    check(inStatement) { "statement exit %] has no matching statement enter %[" }

    if (statementIndented) {
      unindent(2) // End a multi-line statement. Decrease the indentation level.
    }

    inStatement = false
    statementIndented = false
  }

  private fun emitWrappingSpace() = apply {
    out.wrappingSpace(indentLevel + 2)
  }

  private fun emitTypeName(typeName: TypeName) {
    typeName.emit(this)
  }

  private fun emitString(string: String?) {
    // Emit null as a literal null: no quotes.
    emit(
      if (string != null) {
        stringLiteralWithQuotes(string, (0 until (indentLevel + 1)).joinToString("") { indent })
      } else {
        "null"
      },
    )
  }

  private fun emitStringTemplate(string: String?) {
    // Emit null as a literal null: no quotes.
    emit(
      if (string != null) {
        stringTemplateLiteralWithBackticks(string, (0 until (indentLevel + 1)).joinToString("") { indent })
      } else {
        "null"
      },
    )
  }

  /**
   * Renders a `%L` argument.
   *
   * This is the only place that decides how a literal argument is rendered; [CodeBlock] keeps
   * the argument as it was given. Everything with an arm here is emitted against live writer
   * state -- the current indent and column, the scope stack, the rename map, and the set of
   * symbols to import -- which is what a value cannot get once it has been flattened to text.
   */
  private fun emitLiteral(o: Any?) {
    when (o) {
      is ClassSpec -> selfLaidOut { o.emit(this) }
      is InterfaceSpec -> selfLaidOut { o.emit(this) }
      is EnumSpec -> selfLaidOut { o.emit(this) }
      is DecoratorSpec -> o.emit(this)
      is FunctionSpec -> selfLaidOut { o.emit(this, null, emptySet()) }
      is ObjectLiteral -> selfLaidOut { o.emit(this) }
      is CallExpression -> selfLaidOut { o.emit(this) }
      is TypeName -> emitTypeName(o)
      is SymbolSpec -> emitSymbol(o)
      is CodeBlock -> emitCode(o)
      else -> emit(o.toString())
    }
  }

  /**
   * Emits a value that decides its own layout, outside any enclosing statement.
   *
   * The `%[ %]` hanging indent exists for an expression too long for the line: its
   * continuation lines are pushed out two levels so they read as continuations. A block-shaped
   * value -- a class, an arrow function, an object literal -- is not a wrapped expression. Its
   * lines are its own structure, already indented relative to where it starts, and giving them
   * the hanging indent as well puts the body and its closing brace two levels too deep.
   *
   * Suspending the statement rather than unindenting keeps the paired markers balanced: the
   * indent is owed only if it was applied, which [statementIndented] records rather than
   * infers.
   */
  private inline fun selfLaidOut(emit: () -> Unit) {
    val wasInStatement = inStatement
    inStatement = false
    try {
      emit()
    } finally {
      inStatement = wasInStatement
    }
  }

  /**
   * Emits `s` with indentation as required. It's important that all code that writes to
   * [CodeWriter.out] does it through here, since we emit indentation lazily in order to avoid
   * unnecessary trailing whitespace.
   */
  // Indentation and line-wrapping interact per character; flattening it splits that logic.
  @Suppress("NestedBlockDepth")
  fun emit(s: String) = apply {
    var first = true
    for (line in s.split('\n')) {
      // Emit a newline character. Make sure blank lines in KDoc & comments look good.
      if (!first) {
        if ((tsDoc || comment) && trailingNewline) {
          emitIndentation()
          out.append(if (tsDoc) " *" else "//")
        }
        out.append("\n")
        trailingNewline = true
        if (inStatement && !statementIndented) {
          indent(2) // Begin multiple-line statement. Increase the indentation level.
          statementIndented = true
        }
      }

      first = false
      if (line.isEmpty()) continue // Don't indent empty lines.

      // Emit indentation and comment prefix if necessary.
      if (trailingNewline) {
        emitIndentation()
        if (tsDoc) {
          out.append(" * ")
        } else if (comment) {
          out.append("// ")
        }
      }

      out.append(line)
      trailingNewline = false
    }
  }

  private fun emitIndentation() {
    for (j in 0 until indentLevel) {
      out.append(indent)
    }
  }

  override fun close() {
    out.close()
  }
}

internal inline fun buildCodeString(builderAction: CodeWriter.() -> Unit): String {
  val stringBuilder = StringBuilder()
  CodeWriter(stringBuilder).use {
    it.builderAction()
  }
  return stringBuilder.toString()
}

/**
 * Emits one member of a file or module, dispatching on its kind.
 *
 * Shared by [FileSpec] and [ModuleSpec], which carried the same `when` separately. They had
 * already drifted: the module copy had no `ExportSpec` arm, so an export inside a namespace
 * hit the `else` and threw.
 */
internal fun CodeWriter.emitMember(member: Any) {
  when (member) {
    is ModuleSpec -> member.emit(this)
    is InterfaceSpec -> member.emit(this)
    is ClassSpec -> member.emit(this)
    is EnumSpec -> member.emit(this)
    is FunctionSpec -> member.emit(this, null, setOf(Modifier.PUBLIC))
    is PropertySpec -> member.emit(this, setOf(Modifier.PUBLIC), asStatement = true)
    is TypeAliasSpec -> member.emit(this)
    is ExportSpec -> member.emit(this)
    is CodeBlock -> emitCode(member)
    else -> throw AssertionError("unhandled member type ${member.javaClass.name}")
  }
}
