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

import dk.lldata.typescriptpoet.CallExpression
import dk.lldata.typescriptpoet.FunctionSpec
import dk.lldata.typescriptpoet.ObjectLiteral
import dk.lldata.typescriptpoet.TypeName

// What goes inside an expression rather than a declaration: an object literal's members and a
// call's arguments. Both are lists the writer measures and breaks, which is the whole reason
// they are built rather than spelled -- see Declarations.kt for the entry points that make
// them, and Members.kt for the declaration half of the DSL.

// ---- Object literal members --------------------------------------------------------------

/** `name: value` */
@JvmSynthetic
fun ObjectLiteral.Builder.property(name: String, format: String, vararg args: Any?) = addProperty(name, format, *args)

/** `name: (…) => …` */
@JvmSynthetic
fun ObjectLiteral.Builder.property(name: String, value: FunctionSpec) = addProperty(name, value)

/** `name: { … }` */
@JvmSynthetic
fun ObjectLiteral.Builder.property(name: String, value: ObjectLiteral) = addProperty(name, value)

/** `name: f(…)` */
@JvmSynthetic
fun ObjectLiteral.Builder.property(name: String, value: CallExpression) = addProperty(name, value)

/** `name` */
@JvmSynthetic
fun ObjectLiteral.Builder.shorthand(name: String) = addShorthand(name)

/** `get name() { … }` */
@JvmSynthetic
fun ObjectLiteral.Builder.getter(name: String, format: String, vararg args: Any?) = addGetter(name, format, *args)

// ---- Call arguments ------------------------------------------------------------------------

/** `f<T>(…)` */
@JvmSynthetic
fun CallExpression.Builder.typeArgument(type: TypeName) = addTypeArgument(type)

/** One argument. */
@JvmSynthetic
fun CallExpression.Builder.argument(format: String, vararg args: Any?) = addArgument(format, *args)

/** An object literal argument: `f({ … })` */
@JvmSynthetic
fun CallExpression.Builder.argument(value: ObjectLiteral) = addArgument(value)

/** A function argument: `map((x) => x * 2)` */
@JvmSynthetic
fun CallExpression.Builder.argument(value: FunctionSpec) = addArgument(value)

/** A nested call argument: `outer(inner(x))` */
@JvmSynthetic
fun CallExpression.Builder.argument(value: CallExpression) = addArgument(value)
