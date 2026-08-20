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
 * An object literal expression: `{ name: n, send: (m: string): void => { ... } }`.
 *
 * Members are properties, shorthand, getters, or spreads. Built through [CodeBlock.objectLiteral] and
 * used as a `%L` argument, because an object literal is an expression rather than a
 * declaration:
 *
 * ```kotlin
 * val obj = CodeBlock.objectLiteral()
 *   .addProperty("name", CodeBlock.of("n"))
 *   .addGetter("child", "return %L(%P, config)", "childNode", "\${path}/child")
 *   .build()
 *
 * FunctionSpec.builder("create").addStatement("return %L", obj).build()
 * ```
 *
 * The members stay structured until the file is written rather than being rendered to text
 * up front, which is what lets the layout be decided against the print width. Building the
 * same thing as a hand-formatted [CodeBlock] cannot do that: the text is already committed
 * by the time the writer sees it.
 */
class ObjectLiteral
internal constructor(internal val members: List<Member>) {

  /** One member of the literal. */
  internal sealed class Member {

    /** `name: value`. */
    class Property(val name: String, val value: CodeBlock) : Member()

    /** `name` on its own, where the value is a binding of the same name. */
    class Shorthand(val name: String) : Member()

    /** `get name() { ... }`, whose body runs on each access rather than once, here. */
    class Getter(val name: String, val body: CodeBlock) : Member()

    /** `...value`, spreading another object's members in. There is no key to carry. */
    class Spread(val value: CodeBlock) : Member()
  }

  internal fun emit(codeWriter: CodeWriter) {
    if (members.isEmpty()) {
      codeWriter.emit("{}")
      return
    }

    // Measure, then break, as elsewhere: keep the literal on one line if the whole thing
    // fits, otherwise put every member on its own line with a trailing comma. Measuring
    // through the writer rather than in isolation, so that a member whose rendered length
    // depends on where it is written -- a renamed import, a name relative to its namespace --
    // is measured at the length it will have.
    val inline = codeWriter.measure { emitMembers(this, separator = ", ") }
    // A member that spans lines -- an arrow function with a body, a nested literal that
    // broke -- forces the whole literal to break, however short the text measures.
    //
    // The trailing width is the rest of the line after the literal, which the literal does
    // not write and cannot fit without: `{ method: "POST", path, body }` measures at 30 and
    // is still too wide for a line that goes on to say `, config, options);`.
    val fits = !inline.contains('\n') &&
      codeWriter.currentColumn + inline.length + BRACES_WIDTH + codeWriter.trailingWidth <=
      codeWriter.printWidth
    if (fits) {
      codeWriter.emit("{ ")
      emitMembers(codeWriter, separator = ", ")
      codeWriter.emit(" }")
      return
    }

    codeWriter.emit("{\n")
    codeWriter.indent()
    members.forEach { member ->
      // The comma is all that follows a member on its own line.
      codeWriter.withTrailingWidth(1) { emitMember(codeWriter, member) }
      codeWriter.emit(",\n")
    }
    codeWriter.unindent()
    codeWriter.emit("}")
  }

  private fun emitMembers(codeWriter: CodeWriter, separator: String) {
    members.forEachIndexed { index, member ->
      if (index > 0) codeWriter.emit(separator)
      emitMember(codeWriter, member)
    }
  }

  private fun emitMember(codeWriter: CodeWriter, member: Member) {
    when (member) {
      is Member.Shorthand -> codeWriter.emit(member.name)

      is Member.Property -> {
        codeWriter.emit(member.name)
        codeWriter.emit(": ")
        codeWriter.emitCode(member.value)
      }

      is Member.Spread -> {
        codeWriter.emit("...")
        codeWriter.emitCode(member.value)
      }

      // Written like an arrow function body: the member owns its braces but not the comma
      // after them, which the literal adds. A getter body is a statement list rather than an
      // expression, so it always spans lines -- which is what makes a literal holding one
      // break, by the same rule any multi-line member goes by.
      is Member.Getter -> {
        codeWriter.emit("get ")
        codeWriter.emit(member.name)
        codeWriter.emit("() {\n")
        codeWriter.indent()
        codeWriter.emitCode(member.body)
        // A body that didn't end its last line, so the brace doesn't land on the end of it.
        if (codeWriter.currentColumn > 0) codeWriter.emit("\n")
        codeWriter.unindent()
        codeWriter.emit("}")
      }
    }
  }

  override fun toString() = buildCodeString { emit(this) }

  /** Builds an [ObjectLiteral]. Obtained from [CodeBlock.objectLiteral]. */
  class Builder internal constructor() {

    private val members = mutableListOf<Member>()

    /** Adds `name: value`. */
    fun addProperty(name: String, value: CodeBlock) = apply {
      members += Member.Property(name, value)
    }

    /** Adds `name: value`, with the value written as a format string. */
    fun addProperty(name: String, format: String, vararg args: Any?) = addProperty(name, CodeBlock.of(format, *args))

    // Spelling `addProperty("get", "%L", arrow(…))` to put a spec in a member is pure
    // ceremony: the format string carries no information the argument does not. These are
    // the kinds a member value is in practice.

    /** Adds `name: (…) => …`, a function-valued member. */
    fun addProperty(name: String, value: FunctionSpec) = addProperty(name, CodeBlock.of("%L", value))

    /** Adds `name: { … }`, a nested object literal. */
    fun addProperty(name: String, value: ObjectLiteral) = addProperty(name, CodeBlock.of("%L", value))

    /** Adds `name: f(…)`, a call-valued member. */
    fun addProperty(name: String, value: CallExpression) = addProperty(name, CodeBlock.of("%L", value))

    /** Adds a shorthand member: `{ name }`, where the value is a binding of the same name. */
    fun addShorthand(name: String) = apply {
      members += Member.Shorthand(name)
    }

    /**
     * Adds a spread member: `{ ...value }`. [value] is written through the ordinary machinery,
     * so a `%T` inside it imports rather than only spelling correctly.
     */
    fun addSpread(value: CodeBlock) = apply {
      members += Member.Spread(value)
    }

    /** Adds a spread of a binding named here rather than imported: `{ ...defaults }`. */
    fun addSpread(name: String) = addSpread(CodeBlock.of("%L", name))

    /** Adds a spread of an imported symbol, collecting it into the file's imports. */
    fun addSpread(name: TypeName) = addSpread(CodeBlock.of("%T", name))

    /**
     * Adds a getter: `get name() { ... }`.
     *
     * [body] is a statement list, as a function body is -- build it with
     * [CodeBlock.Builder.addStatement], which writes the `;` and the newline.
     *
     * The distinction from [addProperty] is when the value is computed. A property is
     * evaluated once, where the literal is built; a getter runs on each access, which is what
     * a tree of nodes reached on demand is made of. Rewriting such a getter as a property
     * would construct the whole tree whenever any of it is touched.
     */
    fun addGetter(name: String, body: CodeBlock) = apply {
      members += Member.Getter(name, body)
    }

    /** Adds a getter whose body is one statement, terminated here: `get name() { return x; }`. */
    fun addGetter(name: String, format: String, vararg args: Any?) =
      addGetter(name, CodeBlock.builder().addStatement(format, *args).build())

    fun build() = ObjectLiteral(members.toImmutableList())
  }

  private companion object {

    /** The `{ ` and ` }` around an inline literal. */
    const val BRACES_WIDTH = 4
  }
}
