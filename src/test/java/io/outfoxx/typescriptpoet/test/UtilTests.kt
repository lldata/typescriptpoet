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

import io.outfoxx.typescriptpoet.dropCommon
import io.outfoxx.typescriptpoet.isKeyword
import io.outfoxx.typescriptpoet.isName
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class UtilTests {

  @Test
  fun testDropCommon() {
    val full = listOf("a", "b", "c", "d")

    assertThat(
      full.dropCommon(listOf("a", "b")),
      equalTo(listOf("c", "d")),
    )

    assertThat(
      full.dropCommon(listOf("a")),
      equalTo(listOf("b", "c", "d")),
    )

    assertThat(
      full.dropCommon(listOf("a", "b", "c", "d")),
      equalTo(listOf()),
    )

    assertThat(
      full.dropCommon(listOf("A", "b")),
      equalTo(listOf("a", "b", "c", "d")),
    )

    assertThat(
      full.dropCommon(listOf("a", "b", "c", "d", "e")),
      equalTo(listOf("a", "b", "c", "d")),
    )
  }

  @Test
  fun testReservedWordsAreRejectedAsNames() {
    // Upstream outfoxx/typescriptpoet#23: the list used to be Kotlin's reserved words.
    val reservedWords = listOf(
      "function", "const", "let", "enum", "export", "import", "new", "delete", "void",
      "class", "extends", "instanceof", "switch", "default", "await", "yield", "static",
      "private", "public", "implements",
    )
    for (reserved in reservedWords) {
      assertThat("'$reserved' should be reserved", reserved.isKeyword, equalTo(true))
    }
  }

  @Test
  fun testKotlinOnlyWordsAreAllowedAsNames() {
    // These are Kotlin keywords but ordinary TypeScript identifiers.
    for (allowed in listOf("fun", "val", "object", "when", "typealias", "is", "as")) {
      assertThat("'$allowed' should not be reserved", allowed.isKeyword, equalTo(false))
    }
  }

  @Test
  fun testContextualKeywordsAreAllowedAsNames() {
    // TypeScript's contextual keywords are legal identifiers and must not be refused.
    val contextualKeywords = listOf(
      "type", "get", "set", "from", "of", "async", "declare", "namespace", "readonly",
      "any", "string", "number", "unknown", "never", "satisfies", "infer", "keyof",
    )
    for (allowed in contextualKeywords) {
      assertThat("'$allowed' should not be reserved", allowed.isKeyword, equalTo(false))
    }
  }

  @Test
  fun testDottedNamesAreCheckedPerPart() {
    assertThat("Foo.Bar".isName, equalTo(true))
    assertThat("Foo.class".isName, equalTo(false))
    assertThat("class.Foo".isName, equalTo(false))
  }
}
