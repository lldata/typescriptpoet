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

// The member half of the DSL: what goes inside a class, an interface, a function, an enum or an
// object literal. See Declarations.kt for the file- and namespace-level half.

// ---- Class members ---------------------------------------------------------------------------

/** `name: T = …;` */
@JvmSynthetic
inline fun ClassSpec.Builder.property(
  name: String,
  type: TypeName,
  vararg modifiers: Modifier,
  block: PropertyBlock = {},
) = addProperty(PropertySpec.builder(name, type).addModifiers(*modifiers).apply(block).build())

/** `name(): T { … }` */
@JvmSynthetic
inline fun ClassSpec.Builder.function(name: String, vararg modifiers: Modifier, block: FunctionBlock) =
  addFunction(FunctionSpec.builder(name).addModifiers(*modifiers).apply(block).build())

/** `constructor(…) { … }` — parameters may be promoted to properties. */
@JvmSynthetic
inline fun ClassSpec.Builder.constructor(block: FunctionBlock) =
  constructor(FunctionSpec.constructorBuilder().apply(block).build())

/** `[key: string]: T;` */
@JvmSynthetic
inline fun ClassSpec.Builder.index(vararg modifiers: Modifier, block: FunctionBlock) =
  addIndexable(FunctionSpec.indexableBuilder().addModifiers(*modifiers).apply(block).build())

/** `static { … }` */
@JvmSynthetic
fun ClassSpec.Builder.static(format: String, vararg args: Any?) = addStaticBlock(CodeBlock.of(format, *args))

/** `extends Base` */
@JvmSynthetic
fun ClassSpec.Builder.extends(type: TypeName) = superClass(type)

/** `implements A, B` */
@JvmSynthetic
fun ClassSpec.Builder.implements(vararg types: TypeName) = apply { types.forEach(::addMixin) }

// Variance and `const` are named arguments after the bounds vararg, so one function covers
// `typeParam("C", bound(X))`, `typeParam("O", variance = OUT)` and `typeParam("C", const = true)`.

/** `<T extends Base>` */
@JvmSynthetic
fun ClassSpec.Builder.typeParam(
  name: String,
  vararg bounds: TypeName.TypeVariable.Bound,
  variance: TypeName.TypeVariable.Variance? = null,
  const: Boolean = false,
) = addTypeVariable(TypeName.typeVariable(name, variance, const, bounds.toList()))

/** `<T>`, reusing a variable declared elsewhere. */
@JvmSynthetic
fun ClassSpec.Builder.typeParam(variable: TypeName.TypeVariable) = addTypeVariable(variable)

/** A TSDoc comment above the class. */
@JvmSynthetic
fun ClassSpec.Builder.tsDoc(format: String, vararg args: Any) = addTSDoc(format, *args)

// ---- Interface members -----------------------------------------------------------------------

/** `name: T;` */
@JvmSynthetic
inline fun InterfaceSpec.Builder.property(
  name: String,
  type: TypeName,
  vararg modifiers: Modifier,
  block: PropertyBlock = {},
) = addProperty(PropertySpec.builder(name, type).addModifiers(*modifiers).apply(block).build())

/** `name?: T;` */
@JvmSynthetic
fun InterfaceSpec.Builder.optional(name: String, type: TypeName) = addProperty(name, type, true)

/** `name(): T;` */
@JvmSynthetic
inline fun InterfaceSpec.Builder.function(name: String, vararg modifiers: Modifier, block: FunctionBlock) =
  addFunction(FunctionSpec.builder(name).addModifiers(*modifiers).apply(block).build())

/** `[key: string]: T;` */
@JvmSynthetic
inline fun InterfaceSpec.Builder.index(vararg modifiers: Modifier, block: FunctionBlock) =
  addIndexable(FunctionSpec.indexableBuilder().addModifiers(*modifiers).apply(block).build())

/** `(value: string): number;` — a call signature, which only an interface has. */
@JvmSynthetic
inline fun InterfaceSpec.Builder.call(vararg modifiers: Modifier, block: FunctionBlock) =
  callable(FunctionSpec.callableBuilder().addModifiers(*modifiers).apply(block).build())

/** `extends A, B` */
@JvmSynthetic
fun InterfaceSpec.Builder.extends(vararg types: TypeName) = apply { types.forEach(::addSuperInterface) }

/** `<T extends Base>` */
@JvmSynthetic
fun InterfaceSpec.Builder.typeParam(
  name: String,
  vararg bounds: TypeName.TypeVariable.Bound,
  variance: TypeName.TypeVariable.Variance? = null,
  const: Boolean = false,
) = addTypeVariable(TypeName.typeVariable(name, variance, const, bounds.toList()))

/** `<T>`, reusing a variable declared elsewhere. */
@JvmSynthetic
fun InterfaceSpec.Builder.typeParam(variable: TypeName.TypeVariable) = addTypeVariable(variable)

/** A TSDoc comment above the interface. */
@JvmSynthetic
fun InterfaceSpec.Builder.tsDoc(format: String, vararg args: Any) = addTSDoc(format, *args)

// ---- Function members ------------------------------------------------------------------------

/** `name: T` */
@JvmSynthetic
inline fun FunctionSpec.Builder.parameter(
  name: String,
  type: TypeName,
  vararg modifiers: Modifier,
  optional: Boolean = false,
  block: ParameterBlock = {},
) = addParameter(ParameterSpec.builder(name, type, optional, *modifiers).apply(block).build())

/** `...name: T[]` */
@JvmSynthetic
inline fun FunctionSpec.Builder.rest(name: String, type: TypeName, block: ParameterBlock = {}) =
  restParameter(ParameterSpec.builder(name, type).apply(block).build())

/** `<T extends Base>` */
@JvmSynthetic
fun FunctionSpec.Builder.typeParam(
  name: String,
  vararg bounds: TypeName.TypeVariable.Bound,
  variance: TypeName.TypeVariable.Variance? = null,
  const: Boolean = false,
) = addTypeVariable(TypeName.typeVariable(name, variance, const, bounds.toList()))

/** `<T>`, reusing a variable declared elsewhere. */
@JvmSynthetic
fun FunctionSpec.Builder.typeParam(variable: TypeName.TypeVariable) = addTypeVariable(variable)

/** A TSDoc comment above the function. */
@JvmSynthetic
fun FunctionSpec.Builder.tsDoc(format: String, vararg args: Any) = addTSDoc(format, *args)

/** `<T extends Base>` */
@JvmSynthetic
fun TypeAliasSpec.Builder.typeParam(
  name: String,
  vararg bounds: TypeName.TypeVariable.Bound,
  variance: TypeName.TypeVariable.Variance? = null,
  const: Boolean = false,
) = addTypeVariable(TypeName.typeVariable(name, variance, const, bounds.toList()))

/** `<T>`, reusing a variable declared elsewhere. */
@JvmSynthetic
fun TypeAliasSpec.Builder.typeParam(variable: TypeName.TypeVariable) = addTypeVariable(variable)

// ---- Decorators, in the positions they appear -------------------------------------------------

/** `@name` */
@JvmSynthetic
inline fun ClassSpec.Builder.decorator(name: SymbolSpec, block: DecoratorBlock = {}) =
  addDecorator(DecoratorSpec.builder(name).apply(block).build())

/** `@name` */
@JvmSynthetic
inline fun FunctionSpec.Builder.decorator(name: SymbolSpec, block: DecoratorBlock = {}) =
  addDecorator(DecoratorSpec.builder(name).apply(block).build())

/** `@name` */
@JvmSynthetic
inline fun PropertySpec.Builder.decorator(name: SymbolSpec, block: DecoratorBlock = {}) =
  addDecorator(DecoratorSpec.builder(name).apply(block).build())

/** `@name` */
@JvmSynthetic
inline fun ParameterSpec.Builder.decorator(name: SymbolSpec, block: DecoratorBlock = {}) =
  addDecorator(DecoratorSpec.builder(name).apply(block).build())

// ---- Enum and object literal members ----------------------------------------------------------

/** `Name = "value",` */
@JvmSynthetic
fun EnumSpec.Builder.constant(name: String, format: String, vararg args: Any?) =
  addConstant(name, CodeBlock.of(format, *args))

/** `name: value` */
@JvmSynthetic
fun ObjectLiteral.Builder.property(name: String, format: String, vararg args: Any?) = addProperty(name, format, *args)

/** `name` */
@JvmSynthetic
fun ObjectLiteral.Builder.shorthand(name: String) = addShorthand(name)

/** `get name() { … }` */
@JvmSynthetic
fun ObjectLiteral.Builder.getter(name: String, format: String, vararg args: Any?) = addGetter(name, format, *args)

/** `@dec("x")` */
@JvmSynthetic
fun DecoratorSpec.Builder.argument(format: String, vararg args: Any?) = addParameter(null, format, *args)

/** A decorator argument with its name emitted as a comment before the value. */
@JvmSynthetic
fun DecoratorSpec.Builder.labelled(label: String, format: String, vararg args: Any?) =
  addParameter(label, format, *args)
