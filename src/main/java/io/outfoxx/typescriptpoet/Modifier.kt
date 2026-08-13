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

/** Available declaration modifiers. */
enum class Modifier {

  EXPORT,
  PUBLIC,
  PROTECTED,
  PRIVATE,
  READONLY,
  GET,
  SET,
  STATIC,
  ABSTRACT,
  DECLARE,
  CONST,
  LET,
  VAR,
  ;

  val keyword: String
    get() = name.lowercase()
}

/**
 * The order modifiers are emitted in, which is TypeScript's grammar order.
 *
 * This is deliberately independent of the enum's declaration order. Emission used to iterate
 * an `EnumSet`, which always yields declaration order, so the generated output silently
 * depended on how the constants above happen to be arranged -- appending a constant could
 * change generated code, and correcting the output meant physically moving a constant.
 */
private val MODIFIER_EMIT_ORDER = listOf(
  Modifier.EXPORT,
  Modifier.DECLARE,
  Modifier.PUBLIC,
  Modifier.PROTECTED,
  Modifier.PRIVATE,
  Modifier.STATIC,
  Modifier.ABSTRACT,
  Modifier.READONLY,
  Modifier.GET,
  Modifier.SET,
  Modifier.CONST,
  Modifier.LET,
  Modifier.VAR,
)

private val MODIFIER_EMIT_INDEX =
  MODIFIER_EMIT_ORDER.withIndex().associate { (index, modifier) -> modifier to index }

/**
 * Sorts modifiers into the order TypeScript requires them to be written in.
 *
 * Fails rather than guessing when a modifier has no defined position, so that adding a
 * constant to [Modifier] without placing it here is a loud error instead of a silent
 * mis-ordering of generated code.
 */
internal fun Set<Modifier>.inEmitOrder(): List<Modifier> {
  val unplaced = filterNot { MODIFIER_EMIT_INDEX.containsKey(it) }
  require(unplaced.isEmpty()) {
    "no emit order defined for $unplaced; add them to MODIFIER_EMIT_ORDER in Modifier.kt"
  }
  return sortedBy { MODIFIER_EMIT_INDEX.getValue(it) }
}
