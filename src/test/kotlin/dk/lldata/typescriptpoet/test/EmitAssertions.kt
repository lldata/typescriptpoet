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
package dk.lldata.typescriptpoet.test

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.TypeSafeMatcher

/**
 * Asserts what code was emitted, ignoring how it was laid out.
 *
 * Most tests here are about the code: that a property is `readonly`, that a union is
 * parenthesised, that an overload signature has no body. Layout is incidental to them, and
 * asserting it means every formatting change reads as dozens of behaviour failures -- which
 * is exactly what happened when the emitter moved to Prettier's defaults.
 *
 * Layout is not therefore unchecked. [KitchenSinkTests] compares a whole generated file
 * against a golden file and hands it to `prettier --check`, and the tests whose subject is a
 * layout rule assert it exactly -- how a union alias wraps, how an object literal breaks, how
 * a statement indents. Use [assertEmitsExactly] for a case where the layout *is* the point.
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

/**
 * Matches emitted code ignoring layout, for use with `assertThat`.
 *
 * A drop-in replacement for `equalTo` at assertion sites whose subject is the code rather
 * than its formatting, which is most of them.
 */
fun emits(expected: String): Matcher<String> = object : TypeSafeMatcher<String>() {

  override fun matchesSafely(item: String) = normaliseWhitespace(item) == normaliseWhitespace(expected)

  override fun describeTo(description: Description) {
    description.appendText("emits (ignoring whitespace)\n").appendText(expected)
  }

  override fun describeMismatchSafely(item: String, description: Description) {
    description.appendText("was\n").appendText(item)
  }
}
