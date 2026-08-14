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
@file:Suppress("UnusedParameter")

package dk.lldata.typescriptpoet.dsl

import dk.lldata.typescriptpoet.ClassSpec
import dk.lldata.typescriptpoet.FileSpec
import dk.lldata.typescriptpoet.FunctionSpec
import dk.lldata.typescriptpoet.InterfaceSpec
import dk.lldata.typescriptpoet.ModuleSpec

// `null` is a type in TypeScript and a literal in Kotlin, so `property("x", null)` is a thing a
// TypeScript author will write. On its own the compiler answers "Null cannot be a value of a
// non-null type 'TypeName'", which is true and unhelpful -- it does not say the type is here
// under the name null_.
//
// Each overload below exists only to carry that message. They take Nothing?, so they are chosen
// only for a literal null; they are ERROR-deprecated, so choosing one is a compile error and the
// body never runs.

private const val HINT = "TypeScript's `null` type is `null_` here. Kotlin's `null` is not a type."

private fun unreachable(): Nothing = throw AssertionError("rejected at compile time")

@Deprecated(HINT, ReplaceWith("property(name, null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun ClassSpec.Builder.property(name: String, type: Nothing?): Nothing = unreachable()

@Deprecated(HINT, ReplaceWith("property(name, null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun InterfaceSpec.Builder.property(name: String, type: Nothing?): Nothing = unreachable()

@Deprecated(HINT, ReplaceWith("property(name, null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun FileSpec.Builder.property(name: String, type: Nothing?): Nothing = unreachable()

@Deprecated(HINT, ReplaceWith("property(name, null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun ModuleSpec.Builder.property(name: String, type: Nothing?): Nothing = unreachable()

@Deprecated(HINT, ReplaceWith("optional(name, null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun InterfaceSpec.Builder.optional(name: String, type: Nothing?): Nothing = unreachable()

@Deprecated(HINT, ReplaceWith("parameter(name, null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun FunctionSpec.Builder.parameter(name: String, type: Nothing?): Nothing = unreachable()

@Deprecated(HINT, ReplaceWith("rest(name, null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun FunctionSpec.Builder.rest(name: String, type: Nothing?): Nothing = unreachable()

@Deprecated(HINT, ReplaceWith("returns(null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun FunctionSpec.Builder.returns(type: Nothing?): Nothing = unreachable()

@Deprecated(HINT, ReplaceWith("type(name, null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun FileSpec.Builder.type(name: String, of: Nothing?): Nothing = unreachable()

@Deprecated(HINT, ReplaceWith("type(name, null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun ModuleSpec.Builder.type(name: String, of: Nothing?): Nothing = unreachable()

@Deprecated(HINT, ReplaceWith("extends(null_)"), DeprecationLevel.ERROR)
@JvmSynthetic
fun ClassSpec.Builder.extends(type: Nothing?): Nothing = unreachable()
