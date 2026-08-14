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

import dk.lldata.typescriptpoet.Modifier

// TypeScript's modifiers, lowercase as TypeScript writes them, to pair with the type names in
// Body.kt: `function("greet", public, override)` rather than `Modifier.PUBLIC, Modifier.OVERRIDE`.
//
// `var` is the one Kotlin hard keyword here, so it is var_ -- a trailing underscore rather than
// a leading one, because `var` then still completes to it in an IDE, and because a leading
// underscore means a private backing field in Kotlin and Java.

/** `export class …` */
@JvmField val export: Modifier = Modifier.EXPORT

/** `declare namespace …` */
@JvmField val declare: Modifier = Modifier.DECLARE

/** `export default class …` */
@JvmField val default: Modifier = Modifier.DEFAULT

/** `public greet()` */
@JvmField val public: Modifier = Modifier.PUBLIC

/** `private engine` */
@JvmField val private: Modifier = Modifier.PRIVATE

/** `protected describe()` */
@JvmField val protected: Modifier = Modifier.PROTECTED

/** `readonly id` */
@JvmField val readonly: Modifier = Modifier.READONLY

/** `static create()` */
@JvmField val static: Modifier = Modifier.STATIC

/** `abstract class …` */
@JvmField val abstract: Modifier = Modifier.ABSTRACT

/** `async load()` */
@JvmField val async: Modifier = Modifier.ASYNC

/** `override greet()` */
@JvmField val override: Modifier = Modifier.OVERRIDE

/** `accessor count` */
@JvmField val accessor: Modifier = Modifier.ACCESSOR

/** `get size()` */
@JvmField val get: Modifier = Modifier.GET

/** `set size(value)` */
@JvmField val set: Modifier = Modifier.SET

/** `const VERSION = …` */
@JvmField val const: Modifier = Modifier.CONST

/** `let logger: Logger` */
@JvmField val let: Modifier = Modifier.LET

/** `var legacy: string` */
@JvmField val var_: Modifier = Modifier.VAR
