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
package dk.lldata.typescriptpoet.dsl

import dk.lldata.typescriptpoet.CodeBlock
import dk.lldata.typescriptpoet.FunctionSpec
import dk.lldata.typescriptpoet.TypeName
import dk.lldata.typescriptpoet.TypeScriptPoetDsl

/**
 * The statements inside a function, separated from its signature.
 *
 * This receiver offers only statements, so a `parameter` cannot be written inside a body and a
 * `statement` cannot be written beside a signature. Nothing defers: omitting [body] emits `{}`.
 */
@TypeScriptPoetDsl
class Body
@PublishedApi internal constructor(@PublishedApi internal val builder: FunctionSpec.Builder) {

  /** `return name;` */
  fun statement(format: String, vararg args: Any) = builder.addStatement(format, *args)

  /** `if (x > 0) {` */
  fun begin(controlFlow: String, vararg args: Any) = builder.beginControlFlow(controlFlow, *args)

  /** `} else if (x < 0) {` */
  fun next(controlFlow: String, vararg args: Any) = builder.nextControlFlow(controlFlow, *args)

  /** `}` */
  fun end() = builder.endControlFlow()

  /** Code taken verbatim. */
  fun code(format: String, vararg args: Any?) = builder.addCode(CodeBlock.of(format, *args))

  /** `// text` */
  fun comment(format: String, vararg args: Any) = builder.addComment(format, *args)
}

/** The `{ … }` after the signature. */
@JvmSynthetic
inline fun FunctionSpec.Builder.body(block: Body.() -> Unit) = apply { Body(this).block() }

// ---- TypeScript's primitive names ------------------------------------------------------------
//
// All twelve of them, lowercase as TypeScript writes them. The case is not cosmetic: `string`
// is the primitive and `String` is the wrapper object type, which the library keeps apart as
// STRING and STRING_CLASS. The uppercase built-ins -- Array, Promise, Map, Date -- are not
// aliased here: they are uppercase in TypeScript too, and `Array` as a Kotlin value would
// shadow kotlin.Array.
//
// `object` and `null` are Kotlin hard keywords, so those two carry a trailing underscore. It is
// trailing rather than leading so that typing `null` still completes to it, and because a
// leading underscore means a private backing field in Kotlin and Java.

/** `string` */
@JvmField
val string: TypeName = TypeName.STRING

/** `number` */
@JvmField
val number: TypeName = TypeName.NUMBER

/** `boolean` */
@JvmField
val boolean: TypeName = TypeName.BOOLEAN

/** `any` */
@JvmField
val any: TypeName = TypeName.ANY

/** `void` */
@JvmField
val void: TypeName = TypeName.VOID

/** `never` */
@JvmField
val never: TypeName = TypeName.NEVER

/** `undefined` */
@JvmField
val undefined: TypeName = TypeName.UNDEFINED

/** `unknown` */
@JvmField
val unknown: TypeName = TypeName.implicit("unknown")

/** `bigint` */
@JvmField
val bigint: TypeName = TypeName.BIGINT

/** `symbol` */
@JvmField
val symbol: TypeName = TypeName.SYMBOL

/** `object` */
@JvmField
val object_: TypeName = TypeName.OBJECT

/** `null` */
@JvmField
val null_: TypeName = TypeName.NULL
