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
 * Built through [CodeBlock.objectLiteral] and used as a `%L` argument, because an object
 * literal is an expression rather than a declaration:
 *
 * ```kotlin
 * val obj = CodeBlock.objectLiteral()
 *   .addProperty("name", CodeBlock.of("n"))
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

  /** One member: `name: value`, or `name` on its own when shorthand. */
  internal data class Member(val name: String, val value: CodeBlock?)

  internal fun emit(codeWriter: CodeWriter) {
    if (members.isEmpty()) {
      codeWriter.emit("{}")
      return
    }

    // Measure, then break, as elsewhere: keep the literal on one line if the whole thing
    // fits, otherwise put every member on its own line with a trailing comma.
    val inline = buildCodeString { emitMembers(this, separator = ", ") }
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
    codeWriter.emit(member.name)
    member.value?.let {
      codeWriter.emit(": ")
      codeWriter.emitCode(it)
    }
  }

  override fun toString() = buildCodeString { emit(this) }

  /** Builds an [ObjectLiteral]. Obtained from [CodeBlock.objectLiteral]. */
  class Builder internal constructor() {

    private val members = mutableListOf<Member>()

    /** Adds `name: value`. */
    fun addProperty(name: String, value: CodeBlock) = apply {
      members += Member(name, value)
    }

    /** Adds `name: value`, with the value written as a format string. */
    fun addProperty(name: String, format: String, vararg args: Any?) = addProperty(name, CodeBlock.of(format, *args))

    /** Adds a shorthand member: `{ name }`, where the value is a binding of the same name. */
    fun addShorthand(name: String) = apply {
      members += Member(name, null)
    }

    fun build() = ObjectLiteral(members.toImmutableList())
  }
}
