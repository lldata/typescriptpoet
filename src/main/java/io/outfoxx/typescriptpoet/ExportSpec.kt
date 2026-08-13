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
 * A module-level `export` statement that is not simply a modifier on a declaration.
 *
 * This covers re-exports (`export * from 'm'`, `export * as ns from 'm'`,
 * `export { a, b as c } from 'm'`), standalone export lists (`export { a, b }`), the default
 * export of an expression (`export default x`), and the CommonJS export assignment
 * (`export = x`).
 *
 * Marking a declaration itself exported is still done with [Modifier.EXPORT], and
 * `export default class Foo` with [Modifier.EXPORT] plus [Modifier.DEFAULT].
 */
class ExportSpec
internal constructor(
  internal val kind: Kind,
  internal val source: String? = null,
  internal val namespaceAlias: String? = null,
  internal val names: List<Name> = emptyList(),
  internal val typeOnly: Boolean = false,
  internal val expression: CodeBlock? = null,
) {

  enum class Kind {

    ALL,
    ALL_AS,
    NAMED,
    DEFAULT,
    EQUALS,
  }

  /** An exported name, optionally renamed on the way out (`a as b`). */
  data class Name(val name: String, val alias: String? = null) {

    override fun toString() = if (alias != null) "$name as $alias" else name
  }

  internal fun emit(codeWriter: CodeWriter) {
    when (kind) {
      Kind.ALL ->
        codeWriter.emitCode(CodeBlock.of("%[export ${typePrefix()}* from \"%L\";\n%]", source))

      Kind.ALL_AS ->
        codeWriter.emitCode(
          CodeBlock.of("%[export ${typePrefix()}* as %L from \"%L\";\n%]", namespaceAlias, source),
        )

      Kind.NAMED -> {
        val list = names.joinToString(", ")
        if (source != null) {
          codeWriter.emitCode(
            CodeBlock.of("%[export ${typePrefix()}{ %L } from \"%L\";\n%]", list, source),
          )
        } else {
          codeWriter.emitCode(CodeBlock.of("%[export ${typePrefix()}{ %L };\n%]", list))
        }
      }

      Kind.DEFAULT -> {
        codeWriter.emitCode("export default ")
        codeWriter.emitCode(expression!!)
        codeWriter.emit(";\n")
      }

      Kind.EQUALS -> {
        codeWriter.emitCode("export = ")
        codeWriter.emitCode(expression!!)
        codeWriter.emit(";\n")
      }
    }
  }

  private fun typePrefix() = if (typeOnly) "type " else ""

  override fun toString() = buildCodeString { emit(this) }

  companion object {

    /** `export * from 'module';` */
    @JvmStatic
    @JvmOverloads
    fun all(from: String, typeOnly: Boolean = false) = ExportSpec(Kind.ALL, source = from, typeOnly = typeOnly)

    /** `export * as namespace from 'module';` */
    @JvmStatic
    @JvmOverloads
    fun allAs(namespace: String, from: String, typeOnly: Boolean = false) =
      ExportSpec(Kind.ALL_AS, source = from, namespaceAlias = namespace, typeOnly = typeOnly)

    /**
     * `export { a, b as c } from 'module';`, or `export { a, b as c };` when [from] is null.
     */
    @JvmStatic
    @JvmOverloads
    fun named(names: List<Name>, from: String? = null, typeOnly: Boolean = false): ExportSpec {
      require(names.isNotEmpty()) { "an export list needs at least one name" }
      return ExportSpec(Kind.NAMED, source = from, names = names, typeOnly = typeOnly)
    }

    /** `export default <expression>;` */
    @JvmStatic
    fun default(expression: CodeBlock) = ExportSpec(Kind.DEFAULT, expression = expression)

    /** `export default <expression>;` */
    @JvmStatic
    fun default(format: String, vararg args: Any?) = default(CodeBlock.of(format, *args))

    /**
     * `export = <expression>;`
     *
     * The CommonJS export assignment. It cannot be combined with any ES `export` in the same
     * file, which [FileSpec] enforces.
     */
    @JvmStatic
    fun exportEquals(expression: CodeBlock) = ExportSpec(Kind.EQUALS, expression = expression)

    /** `export = <expression>;` */
    @JvmStatic
    fun exportEquals(format: String, vararg args: Any?) = exportEquals(CodeBlock.of(format, *args))

    /** One exported name, optionally renamed (`a as b`). */
    @JvmStatic
    @JvmOverloads
    fun name(name: String, alias: String? = null) = Name(name, alias)
  }
}
