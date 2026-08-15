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

import dk.lldata.typescriptpoet.NameAllocator
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Name Allocator Tests")
class NameAllocatorTests {

  @Test
  @DisplayName("Replaces currency symbols, which Java allows in identifiers but TypeScript does not")
  fun testCurrencySymbolsAreSanitised() {
    // Issue #42: Character.isJavaIdentifierPart accepts currency symbols, so a suggestion like
    // "price€" was copied through unchanged into a .ts file that tsc then rejects.
    assertThat(NameAllocator().newName("price€"), equalTo("price_"))
    assertThat(NameAllocator().newName("€"), equalTo("_"))
    assertThat(NameAllocator().newName("£cost"), equalTo("_cost"))
  }

  @Test
  @DisplayName("Replaces ignorable control characters, which Java allows in identifiers but TypeScript does not")
  fun testIgnorableControlCharactersAreSanitised() {
    // Character.isJavaIdentifierPart also accepts ISO control characters and Unicode format
    // characters (e.g. soft hyphen, U+00AD), neither of which ECMAScript's IdentifierPart
    // includes.
    assertThat(NameAllocator().newName("a\u00ADb"), equalTo("a_b"))
    assertThat(NameAllocator().newName("a\u0007b"), equalTo("a_b"))
  }

  @Test
  @DisplayName("Preserves dollar signs and underscores")
  fun testDollarAndUnderscoreArePreserved() {
    assertThat(NameAllocator().newName("\$foo_bar"), equalTo("\$foo_bar"))
  }

  @Test
  @DisplayName("Preserves the zero-width joiner and non-joiner between other identifier characters")
  fun testZeroWidthJoinersArePreservedInContinuationPosition() {
    // ECMAScript's IdentifierPart explicitly includes U+200C/U+200D so combining scripts can
    // join characters that Unicode's ID_Continue alone would leave disconnected.
    assertThat(
      NameAllocator().newName("a\u200Cb\u200Dc"),
      equalTo("a\u200Cb\u200Dc"),
    )
  }

  @Test
  @DisplayName("Preserves non-ASCII letters, which are valid identifier characters in both languages")
  fun testNonAsciiLettersArePreserved() {
    assertThat(NameAllocator().newName("café"), equalTo("café"))
    assertThat(NameAllocator().newName("变量"), equalTo("变量"))
  }

  @Test
  @DisplayName("Prefixes a leading digit with an underscore")
  fun testLeadingDigitGetsUnderscorePrefix() {
    assertThat(NameAllocator().newName("1st"), equalTo("_1st"))
  }

  @Test
  @DisplayName("Replaces spaces and dashes with underscores")
  fun testSpacesAndDashesAreSanitised() {
    assertThat(NameAllocator().newName("string builder"), equalTo("string_builder"))
    assertThat(NameAllocator().newName("kebab-case"), equalTo("kebab_case"))
  }
}
