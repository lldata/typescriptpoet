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

import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A TypeScript file containing top level objects like classes, objects, functions, properties, and type
 * aliases.
 *
 * Items are output in the following order:
 * - Comment
 * - Imports
 * - Members
 */
class FileSpec
private constructor(builder: Builder) : Taggable(builder.tags.toImmutableMap()) {

  val modulePath = builder.modulePath
  val comment = builder.comment.build()
  val members = builder.members.toList()
  val indent = builder.indent

  /** Writes the file, resolving imports relative to [directory]. */
  @Throws(IOException::class)
  @JvmOverloads
  fun writeTo(out: Appendable, directory: Path = Paths.get("/")) {
    // First pass: emit the entire file, just to collect the symbols we'll need to import.
    val importsCollector = CodeWriter(NullAppendable, indent)
    importsCollector.use { emit(it, directory) }

    val absPath = directory.resolve(modulePath).toAbsolutePath()

    val importedSymbols =
      importsCollector.referencedSymbols<SymbolSpec.Imported>()
        .filter {
          // Include only imports from other files
          when {
            it.source.startsWith("./") -> {
              val absImportPath = absPath.resolve(it.source).toAbsolutePath().normalize()
              absImportPath != absPath
            }

            it.source.startsWith("!") -> {
              val absImportPath = directory.resolve(it.source.removePrefix("!")).toAbsolutePath().normalize()
              absImportPath != absPath
            }

            else -> true
          }
        }
        .toSet()

    // Pass local type name & imports to name allocator to resolve collisions
    val topLevelNameAllocator = NameAllocator()

    // Allocate unique set of top level members
    members
      .filterIsInstance<AnyTypeSpec>()
      .map { it.name }
      .toSet()
      .forEach {
        topLevelNameAllocator.newName(it)
      }

    importedSymbols
      .forEach {
        topLevelNameAllocator.newName(it.value, it)
      }

    val renamedSymbols =
      topLevelNameAllocator.tagsToNames()
        .filterKeys { it is SymbolSpec }
        .mapKeys { it.key as SymbolSpec }
        .filter { it.key.value != it.value }

    // Second pass: write the code, taking advantage of the imports.
    CodeWriter(out, indent, renamedSymbols).use {
      emit(it, directory, importedSymbols)
    }
  }

  /** Writes this to `directory` as UTF-8 using the standard directory structure.  */
  @Throws(IOException::class)
  fun writeTo(directory: Path) {
    require(Files.notExists(directory) || Files.isDirectory(directory)) {
      "path $directory exists but is not a directory."
    }
    val outputPath = directory.resolve("$modulePath.ts")

    if (outputPath.parent != null) {
      Files.createDirectories(outputPath.parent)
    }

    OutputStreamWriter(Files.newOutputStream(outputPath), UTF_8).use { writeTo(it, directory) }
  }

  /** Writes this to `directory` as UTF-8 using the standard directory structure.  */
  @Throws(IOException::class)
  fun writeTo(directory: File) = writeTo(directory.toPath())

  private fun emit(
    codeWriter: CodeWriter,
    directory: Path = Paths.get("/"),
    imports: Set<SymbolSpec.Imported> = emptySet(),
  ) {
    if (comment.isNotEmpty()) {
      codeWriter.emitComment(comment)
    }

    if (imports.isNotEmpty()) {
      emitImports(codeWriter, directory, imports)
    }

    members.filterNot { it is ModuleSpec || it is CodeBlock }.forEach { member ->
      codeWriter.emit("\n")
      codeWriter.emitMember(member)
    }

    members.filterIsInstance<ModuleSpec>().forEach { member ->
      codeWriter.emit("\n")
      member.emit(codeWriter)
    }

    members.filterIsInstance<CodeBlock>().forEach { member ->
      codeWriter.emit("\n")
      codeWriter.emitCode(member)
    }
  }

  // Imports are grouped by module, then by kind, then by name.
  @Suppress("NestedBlockDepth")
  private fun emitImports(codeWriter: CodeWriter, directory: Path, imports: Set<SymbolSpec.Imported>) {
    val augmentImports = imports
      .filterIsInstance<SymbolSpec.Augmented>()
      .groupBy { it.augmented }

    val sideEffectImports = imports
      .filterIsInstance<SymbolSpec.SideEffect>()
      .groupBy { it.source }

    if (imports.isNotEmpty()) {
      imports
        // Augments are emitted next to the symbol they augment, and side-effect imports are
        // emitted separately below, so both are excluded here. This read `||`, which is
        // always true -- a symbol cannot be both -- so the filter did nothing.
        .filter { it !is SymbolSpec.Augmented && it !is SymbolSpec.SideEffect }
        .groupBy { FileModules.importPath(directory, modulePath, it.source) }
        .toSortedMap()
        .forEach { (sourceImportPath, imports) ->

          imports.filterIsInstance<SymbolSpec.ImportsDefault>().forEach { import ->
            // Default imports each get their own statement. Merging them onto a named-import
            // line (`import D, { a } from 'm'`) is legal but not attempted.
            val keyword = if (import.typeOnly) "import type" else "import"
            codeWriter.emitCode(
              CodeBlock.of("%[$keyword %L from \"%L\";\n%]", import.value, sourceImportPath),
            )
          }

          imports.filterIsInstance<SymbolSpec.ImportsAll>().forEach { import ->
            // Output star imports individually
            val keyword = if (import.typeOnly) "import type" else "import"
            codeWriter.emitCode(CodeBlock.of("%[$keyword * as %L from \"%L\";\n%]", import.value, sourceImportPath))
            // Output related augments
            augmentImports[import.value]?.forEach { augment ->
              codeWriter.emitCode(CodeBlock.of("%[import \"%L\";\n%]", augment.source))
            }
          }

          imports.filterIsInstance<SymbolSpec.ImportsName>()
            .map {
              // A type-only name inside an otherwise ordinary import uses the inline `type`
              // form, so value and type imports from one module stay on one statement.
              val prefix = if (it.typeOnly) "type " else ""
              val renamed = codeWriter.renamedSymbols[it] ?: return@map "$prefix${it.value}"
              "$prefix${it.value} as $renamed"
            }
            .toSortedSet()
            .let { names ->
              if (names.isEmpty()) return@let
              // Output named imports as a group
              codeWriter
                .emitCode("import { ")
                .indent()
                .emitCode(names.joinToString(", "))
                .unindent()
                .emitCode(CodeBlock.of(" } from \"%L\";\n", sourceImportPath))
              // Output related augments
              names.forEach { name ->
                augmentImports[name]?.forEach { augment ->
                  codeWriter.emitCode(CodeBlock.of("%[import \"%L\";\n%]", augment.source))
                }
              }
            }
        }

      sideEffectImports.forEach {
        codeWriter.emitCode(CodeBlock.of("%[import %S;\n%]", it.key))
      }
    }
  }

  /** Whether the file declares no members. */
  fun isEmpty(): Boolean = members.isEmpty()

  /** Whether the file declares any members. */
  fun isNotEmpty(): Boolean = !isEmpty()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false
    if (javaClass != other.javaClass) return false
    return toString() == other.toString()
  }

  override fun hashCode() = toString().hashCode()

  /**
   * The file as `writeTo` would write it, imports included.
   *
   * This used to call `emit` directly, which defaults `imports` to empty -- so it returned
   * output that looked complete and silently lacked the import block.
   */
  override fun toString() = buildString { writeTo(this) }

  /** A builder pre-populated with this spec, for deriving a modified copy. */
  fun toBuilder(): Builder {
    val builder = Builder(modulePath)
    builder.comment.add(comment)
    builder.members.addAll(this.members)
    builder.indent = indent
    return builder
  }

  class Builder internal constructor(internal val modulePath: String) : Taggable.Builder<Builder>() {

    init {
      require(!modulePath.endsWith(".ts")) { "File's modulePath should not include typescript extension" }
    }

    internal val comment = CodeBlock.builder()
    internal var indent = "  "
    internal val members = mutableListOf<Any>()

    private fun checkMemberModifiers(modifiers: Set<Modifier>) {
      requireNoneOf(
        modifiers,
        Modifier.PUBLIC,
        Modifier.PROTECTED,
        Modifier.PRIVATE,
        Modifier.READONLY,
        Modifier.GET,
        Modifier.SET,
        Modifier.STATIC,
        Modifier.CONST,
        Modifier.LET,
        Modifier.VAR,
      )
    }

    /**
     * Top-level properties are variable declarations, so unlike other members they *must*
     * carry exactly one of `const`, `let` or `var`. Checking them with
     * [checkMemberModifiers], which forbids all three, made every top-level property throw.
     */
    private fun checkPropertyModifiers(modifiers: Set<Modifier>) {
      requireExactlyOneOf(
        modifiers,
        Modifier.CONST,
        Modifier.LET,
        Modifier.VAR,
      )
      requireNoneOf(
        modifiers,
        Modifier.PUBLIC,
        Modifier.PROTECTED,
        Modifier.PRIVATE,
        Modifier.READONLY,
        Modifier.GET,
        Modifier.SET,
        Modifier.STATIC,
      )
    }

    /** Adds a comment at the top of the file, above the imports: `// Generated. Do not edit.`. */
    fun addComment(format: String, vararg args: Any) = apply {
      comment.add(format, *args)
    }

    /**
     * Adds a module-level `export` statement: a re-export, a standalone export list,
     * `export default <expr>`, or `export = <expr>`.
     */
    fun addExport(exportSpec: ExportSpec) = apply {
      if (exportSpec.kind == ExportSpec.Kind.EQUALS) {
        require(members.none { it is ExportSpec }) {
          "`export =` cannot be combined with any other export in the same file"
        }
      } else {
        require(members.none { it is ExportSpec && it.kind == ExportSpec.Kind.EQUALS }) {
          "`export =` cannot be combined with any other export in the same file"
        }
      }
      require(
        exportSpec.kind != ExportSpec.Kind.DEFAULT ||
          members.none { it is ExportSpec && it.kind == ExportSpec.Kind.DEFAULT },
      ) {
        "a module can have only one default export"
      }
      members += exportSpec
    }

    /** Adds a namespace or module: `namespace Shapes { }`. */
    fun addModule(moduleSpec: ModuleSpec) = apply {
      members += moduleSpec
    }

    /** Adds a class. Class-member modifiers are rejected at file scope. */
    fun addClass(classSpec: ClassSpec) = apply {
      checkMemberModifiers(classSpec.modifiers)
      members += classSpec
    }

    /** Adds an interface. */
    fun addInterface(ifaceSpec: InterfaceSpec) = apply {
      checkMemberModifiers(ifaceSpec.modifiers)
      members += ifaceSpec
    }

    /** Adds an enum: `export const enum Direction { }`. */
    fun addEnum(enumSpec: EnumSpec) = apply {
      // `const enum` is legal at file scope, so CONST is exempt here even though it is a
      // forbidden modifier for every other kind of member.
      requireNoneOf(
        enumSpec.modifiers,
        Modifier.PUBLIC,
        Modifier.PROTECTED,
        Modifier.PRIVATE,
        Modifier.READONLY,
        Modifier.GET,
        Modifier.SET,
        Modifier.STATIC,
        Modifier.LET,
        Modifier.VAR,
      )
      members += enumSpec
    }

    /** Adds a class, interface, enum or type alias, dispatching on its kind. */
    fun addType(typeSpec: AnyTypeSpec) = apply {
      when (typeSpec) {
        is EnumSpec -> addEnum(typeSpec)
        is InterfaceSpec -> addInterface(typeSpec)
        is ClassSpec -> addClass(typeSpec)
        is TypeAliasSpec -> addTypeAlias(typeSpec)
      }
    }

    /** Adds a function. Constructors and decorators are not allowed at file scope. */
    fun addFunction(functionSpec: FunctionSpec) = apply {
      require(!functionSpec.isConstructor) { "cannot add ${functionSpec.name} to file $modulePath" }
      require(functionSpec.decorators.isEmpty()) { "decorators on module functions are not allowed" }
      checkMemberModifiers(functionSpec.modifiers)
      members += functionSpec
    }

    /** Adds a variable: `const VERSION: string = '1';`. Needs one of CONST, LET or VAR. */
    fun addProperty(propertySpec: PropertySpec) = apply {
      require(propertySpec.decorators.isEmpty()) { "decorators on file properties are not allowed" }
      checkPropertyModifiers(propertySpec.modifiers)
      members += propertySpec
    }

    /** Adds a type alias: `type Id = string;`. */
    fun addTypeAlias(typeAliasSpec: TypeAliasSpec) = apply {
      members += typeAliasSpec
    }

    /** Adds arbitrary code, emitted after the declarations. */
    fun addCode(codeBlock: CodeBlock) = apply {
      members += codeBlock
    }

    /** Sets the indent string. Two spaces by default. */
    fun indent(indent: String) = apply {
      this.indent = indent
    }

    /** Whether the file declares no members. */
    fun isEmpty(): Boolean = members.isEmpty()

    /** Whether the file declares any members. */
    fun isNotEmpty(): Boolean = !isEmpty()

    /** Builds the file. */
    fun build() = FileSpec(this)
  }

  companion object {

    /** A file at [modulePath], without the `.ts` extension. */
    @JvmStatic
    fun builder(modulePath: String) = Builder(modulePath)

    /** A file holding a module's members, lifted out of the module itself. */
    @JvmStatic
    @JvmOverloads
    fun get(moduleSpec: ModuleSpec, modulePath: String = moduleSpec.name.replace('.', '/').lowercase()): FileSpec =
      builder(modulePath)
        .apply { members.addAll(moduleSpec.members.toMutableList()) }
        .build()

    /** A file holding a single type, at a path derived from the type's name. */
    @JvmStatic
    @JvmOverloads
    fun get(typeSpec: AnyTypeSpec, modulePath: String = typeSpec.name.replace('.', '/').lowercase()): FileSpec =
      builder(modulePath)
        .addType(typeSpec)
        .build()
  }
}
