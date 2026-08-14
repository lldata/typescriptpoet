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
package dk.lldata.typescriptpoet

/**
 * Marks the builders as the receivers of a scoped DSL, so that inside one builder's block the
 * enclosing builder's members are not silently in scope. Without it,
 *
 * ```kotlin
 * function("m") {
 *   property("leaked", TypeName.STRING)   // resolves to the enclosing class, and compiles
 * }
 * ```
 *
 * is accepted and puts the property somewhere the reader did not ask for.
 *
 * It lives here, next to the builders it annotates, rather than in `dsl` -- [DslMarker] keys
 * on the receiver's class, so the annotation has to be applied to these builders, and a core
 * type must not depend on the optional layer built on top of it. Nothing else about it is
 * public API: it carries no members and has no effect on Java, which cannot write a receiver
 * lambda in the first place.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class TypeScriptPoetDsl
