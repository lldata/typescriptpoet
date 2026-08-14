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
package dk.lldata.typescriptpoet.dsl

import dk.lldata.typescriptpoet.ClassSpec
import dk.lldata.typescriptpoet.CodeBlock
import dk.lldata.typescriptpoet.DecoratorSpec
import dk.lldata.typescriptpoet.EnumSpec
import dk.lldata.typescriptpoet.ExportSpec
import dk.lldata.typescriptpoet.FileSpec
import dk.lldata.typescriptpoet.FunctionSpec
import dk.lldata.typescriptpoet.InterfaceSpec
import dk.lldata.typescriptpoet.Modifier
import dk.lldata.typescriptpoet.ModuleSpec
import dk.lldata.typescriptpoet.ObjectLiteral
import dk.lldata.typescriptpoet.ParameterSpec
import dk.lldata.typescriptpoet.PropertySpec
import dk.lldata.typescriptpoet.SymbolSpec
import dk.lldata.typescriptpoet.TypeAliasSpec
import dk.lldata.typescriptpoet.TypeName
import dk.lldata.typescriptpoet.TypeScriptPoetDsl

/**
 * A Kotlin surface over the builders, named after the TypeScript it emits.
 *
 * Nothing here is a new capability. Every lambda's receiver *is* the builder it configures, so
 * the whole builder API stays in scope inside a block; this file supplies only the part that
 * is pure noise -- creating a child, building it, adding it to its parent -- under the name of
 * the construct being written.
 *
 * ```kotlin
 * file("greeter") {
 *   clazz("Greeter", EXPORT) {
 *     property("name", TypeName.STRING, PRIVATE, READONLY)
 *     constructor { parameter("name", TypeName.STRING) }
 *     function("greet", PUBLIC) {
 *       returns(TypeName.STRING)
 *       addStatement("return %P", "Hello \${this.name}")
 *     }
 *   }
 * }
 * ```
 *
 * `class` and `interface` are Kotlin hard keywords, so those two are `clazz` and `interfaze`
 * rather than backticked. Every other TypeScript keyword here -- `type`, `namespace`, `enum`,
 * `constructor`, `function`, `static`, `extends`, `implements` -- is soft and reads bare.
 *
 * Every declaration is `@JvmSynthetic`: this layer does not exist for Java, which cannot write
 * a receiver lambda and has the builders instead.
 */

internal typealias FileBlock = @TypeScriptPoetDsl FileSpec.Builder.() -> Unit
internal typealias ClassBlock = @TypeScriptPoetDsl ClassSpec.Builder.() -> Unit
internal typealias InterfaceBlock = @TypeScriptPoetDsl InterfaceSpec.Builder.() -> Unit
internal typealias EnumBlock = @TypeScriptPoetDsl EnumSpec.Builder.() -> Unit
internal typealias ModuleBlock = @TypeScriptPoetDsl ModuleSpec.Builder.() -> Unit
internal typealias FunctionBlock = @TypeScriptPoetDsl FunctionSpec.Builder.() -> Unit
internal typealias PropertyBlock = @TypeScriptPoetDsl PropertySpec.Builder.() -> Unit
internal typealias ParameterBlock = @TypeScriptPoetDsl ParameterSpec.Builder.() -> Unit
internal typealias AliasBlock = @TypeScriptPoetDsl TypeAliasSpec.Builder.() -> Unit
internal typealias DecoratorBlock = @TypeScriptPoetDsl DecoratorSpec.Builder.() -> Unit
internal typealias ObjectBlock = @TypeScriptPoetDsl ObjectLiteral.Builder.() -> Unit

// ---- Entry points ---------------------------------------------------------------------------

/** The file itself. */
@JvmSynthetic
inline fun file(name: String, block: FileBlock): FileSpec = FileSpec.builder(name).apply(block).build()

/** `{ name: value }` */
@JvmSynthetic
inline fun obj(block: ObjectBlock): ObjectLiteral = CodeBlock.objectLiteral().apply(block).build()

/** `(message: string): void => { … }` */
@JvmSynthetic
inline fun arrow(name: String = "arrow", block: FunctionBlock): FunctionSpec =
  FunctionSpec.builder(name).arrow().apply(block).build()

// ---- Declarations, in a file or a namespace --------------------------------------------------

/** `class Name { … }` */
@JvmSynthetic
inline fun FileSpec.Builder.clazz(name: String, vararg modifiers: Modifier, block: ClassBlock) =
  addClass(ClassSpec.builder(name).addModifiers(*modifiers).apply(block).build())

/** `interface Name { … }` */
@JvmSynthetic
inline fun FileSpec.Builder.interfaze(name: String, vararg modifiers: Modifier, block: InterfaceBlock) =
  addInterface(InterfaceSpec.builder(name).addModifiers(*modifiers).apply(block).build())

/** `enum Name { … }` */
@JvmSynthetic
inline fun FileSpec.Builder.enum(name: String, vararg modifiers: Modifier, block: EnumBlock) =
  addEnum(EnumSpec.builder(name).addModifiers(*modifiers).apply(block).build())

/** `function name() { … }` */
@JvmSynthetic
inline fun FileSpec.Builder.function(name: String, vararg modifiers: Modifier, block: FunctionBlock) =
  addFunction(FunctionSpec.builder(name).addModifiers(*modifiers).apply(block).build())

/** `namespace Name { … }` */
@JvmSynthetic
inline fun FileSpec.Builder.namespace(name: String, vararg modifiers: Modifier, block: ModuleBlock) =
  addModule(ModuleSpec.builder(name).apply { modifiers.forEach(::addModifier) }.apply(block).build())

/** `type Name = T;` */
@JvmSynthetic
inline fun FileSpec.Builder.type(name: String, of: TypeName, vararg modifiers: Modifier, block: AliasBlock = {}) =
  addTypeAlias(TypeAliasSpec.builder(name, of).addModifiers(*modifiers).apply(block).build())

/** `const name: T = …;` — needs one of CONST, LET or VAR. */
@JvmSynthetic
inline fun FileSpec.Builder.property(
  name: String,
  type: TypeName,
  vararg modifiers: Modifier,
  block: PropertyBlock = {},
) = addProperty(PropertySpec.builder(name, type).addModifiers(*modifiers).apply(block).build())

/** `export … ;` */
@JvmSynthetic
fun FileSpec.Builder.export(exportSpec: ExportSpec) = addExport(exportSpec)

/** `class Name { … }` */
@JvmSynthetic
inline fun ModuleSpec.Builder.clazz(name: String, vararg modifiers: Modifier, block: ClassBlock) =
  addClass(ClassSpec.builder(name).addModifiers(*modifiers).apply(block).build())

/** `interface Name { … }` */
@JvmSynthetic
inline fun ModuleSpec.Builder.interfaze(name: String, vararg modifiers: Modifier, block: InterfaceBlock) =
  addInterface(InterfaceSpec.builder(name).addModifiers(*modifiers).apply(block).build())

/** `enum Name { … }` */
@JvmSynthetic
inline fun ModuleSpec.Builder.enum(name: String, vararg modifiers: Modifier, block: EnumBlock) =
  addEnum(EnumSpec.builder(name).addModifiers(*modifiers).apply(block).build())

/** `function name() { … }` */
@JvmSynthetic
inline fun ModuleSpec.Builder.function(name: String, vararg modifiers: Modifier, block: FunctionBlock) =
  addFunction(FunctionSpec.builder(name).addModifiers(*modifiers).apply(block).build())

/** `namespace Inner { … }` */
@JvmSynthetic
inline fun ModuleSpec.Builder.namespace(name: String, block: ModuleBlock) =
  addModule(ModuleSpec.builder(name).apply(block).build())

/** `type Name = T;` */
@JvmSynthetic
inline fun ModuleSpec.Builder.type(name: String, of: TypeName, block: AliasBlock = {}) =
  addTypeAlias(TypeAliasSpec.builder(name, of).apply(block).build())

/** `const name: T = …;` — needs one of CONST, LET or VAR. */
@JvmSynthetic
inline fun ModuleSpec.Builder.property(
  name: String,
  type: TypeName,
  vararg modifiers: Modifier,
  block: PropertyBlock = {},
) = addProperty(PropertySpec.builder(name, type).addModifiers(*modifiers).apply(block).build())

/** `// text` */
@JvmSynthetic
fun FileSpec.Builder.comment(format: String, vararg args: Any) = addComment(format, *args)

/** Code taken verbatim. */
@JvmSynthetic
fun FileSpec.Builder.code(format: String, vararg args: Any?) = addCode(CodeBlock.of(format, *args))

/** Code taken verbatim. */
@JvmSynthetic
fun ModuleSpec.Builder.code(format: String, vararg args: Any?) = addCode(CodeBlock.of(format, *args))

/** `function f(v: string): string;` per signature, then one implementation. */
@JvmSynthetic
inline fun FileSpec.Builder.overloads(name: String, vararg signatures: FunctionBlock, implementation: FunctionBlock) {
  val specs = signatures.map { FunctionSpec.builder(name).apply(it).build() }
  FunctionSpec.overloads(specs, FunctionSpec.builder(name).apply(implementation).build())
    .forEach { addFunction(it) }
}
