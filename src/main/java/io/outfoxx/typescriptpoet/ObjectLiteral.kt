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
 * An object literal expression: `{ name: n, send: (m: string): void => { ... } }`.
 *
 * Members are properties, shorthand, or getters. Built through [CodeBlock.objectLiteral] and
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
  internal sealed class Member(val name: String) {

    /** `name: value`. */
    class Property(name: String, val value: CodeBlock) : Member(name)

    /** `name` on its own, where the value is a binding of the same name. */
    class Shorthand(name: String) : Member(name)

    /** `get name() { ... }`, whose body runs on each access rather than once, here. */
    class Getter(name: String, val body: CodeBlock) : Member(name)
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
    val fits = !inline.contains('\n') &&
      codeWriter.currentColumn + inline.length + 4 <= codeWriter.printWidth
    if (fits) {
      codeWriter.emit("{ ")
      emitMembers(codeWriter, separator = ", ")
      codeWriter.emit(" }")
      return
    }

    codeWriter.emit("{\n")
    codeWriter.indent()
    members.forEach { member ->
      emitMember(codeWriter, member)
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

    /** Adds a shorthand member: `{ name }`, where the value is a binding of the same name. */
    fun addShorthand(name: String) = apply {
      members += Member.Shorthand(name)
    }

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
}
