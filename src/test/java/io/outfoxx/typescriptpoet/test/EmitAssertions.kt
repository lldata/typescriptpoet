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
package io.outfoxx.typescriptpoet.test

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

/**
 * Asserts what code was emitted, ignoring how it was laid out.
 *
 * Most tests here are about the code: that a property is `readonly`, that a union is
 * parenthesised, that an overload signature has no body. Layout is incidental to them, and
 * asserting it means every formatting change reads as dozens of behaviour failures -- which
 * is exactly what happened when the emitter moved to Prettier's defaults.
 *
 * Layout is not therefore unchecked. It is checked in one place that is about layout:
 * [KitchenSinkTests] compares a whole generated file against a golden file and hands it to
 * `prettier --check`, and the tests in FormattingTests assert specific layout rules. Use
 * [assertEmitsExactly] for a case where the layout *is* the point.
 */
fun assertEmits(actual: String, expected: String) =
  assertThat(normaliseWhitespace(actual), equalTo(normaliseWhitespace(expected)))

/** Asserts the emitted text exactly, layout included. For tests whose subject is layout. */
fun assertEmitsExactly(actual: String, expected: String) = assertThat(actual, equalTo(expected))

/**
 * Collapses runs of whitespace to a single space.
 *
 * Safe for TypeScript, which is whitespace-insensitive outside string and template literals,
 * and both sides are normalised identically -- so a literal's internal spacing still has to
 * agree.
 */
private fun normaliseWhitespace(source: String) = source.replace(Regex("\\s+"), " ").trim()
