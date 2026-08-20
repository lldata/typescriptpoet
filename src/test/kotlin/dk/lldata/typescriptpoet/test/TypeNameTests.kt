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

import dk.lldata.typescriptpoet.CodeBlock
import dk.lldata.typescriptpoet.CodeWriter
import dk.lldata.typescriptpoet.FunctionSpec
import dk.lldata.typescriptpoet.SymbolSpec
import dk.lldata.typescriptpoet.TypeName
import dk.lldata.typescriptpoet.TypeName.Anonymous.Member
import dk.lldata.typescriptpoet.TypeName.Companion.BOOLEAN
import dk.lldata.typescriptpoet.TypeName.Companion.DATE
import dk.lldata.typescriptpoet.TypeName.Companion.NUMBER
import dk.lldata.typescriptpoet.TypeName.Companion.STRING
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@DisplayName("TypeName Tests")
class TypeNameTests {

  private fun emit(fn: FunctionSpec): String {
    val out = java.io.StringWriter()
    fn.emit(CodeWriter(out), null, setOf())
    return out.toString()
  }

  @Test
  @DisplayName("Parsing nested type import only imports root symbol while referencing fully nested import")
  fun testParsingNestedImport() {
    val typeName = TypeName.namedImport("This", "!Api").nested("Is.Nested")

    val symbol = typeName.base as? SymbolSpec.ImportsName ?: fail("ImportsName symbol expected")
    assertThat(symbol.value, emits("This.Is.Nested"))
    assertThat(symbol.source, emits("!Api"))
  }

  @Test
  @DisplayName("Rejects a blank implicit type name")
  fun testRejectsBlankImplicit() {
    val error = runCatching { TypeName.implicit("") }.exceptionOrNull()

    assertThat(error is IllegalArgumentException, equalTo(true))

    val blankError = runCatching { TypeName.implicit("   ") }.exceptionOrNull()

    assertThat(blankError is IllegalArgumentException, equalTo(true))
  }

  @Test
  @DisplayName("Anonymous type names produce valid syntax")
  fun testAnonymousNameGen() {
    val typeName = TypeName.anonymousType("a" to STRING, "b" to NUMBER, "C" to BOOLEAN)

    assertThat(
      typeName.members,
      hasItems(
        Member("a", STRING, false),
        Member("b", NUMBER, false),
        Member("C", BOOLEAN, false),
      ),
    )
    assertThat(typeName.toString(), emits("{ a: string; b: number; C: boolean }"))

    val typeName2 = TypeName.anonymousType(
      arrayListOf(
        Member("a", NUMBER, true),
        Member("B", STRING, false),
        Member("c", DATE, true),
      ),
    )

    assertThat(
      typeName2.members,
      hasItems(
        Member("a", NUMBER, true),
        Member("B", STRING, false),
        Member("c", DATE, true),
      ),
    )
    assertThat(typeName2.toString(), emits("{ a?: number; B: string; c?: Date }"))
  }

  @Test
  @DisplayName("Promise type name")
  fun testPromiseType() {
    assertThat(TypeName.promiseType(STRING).toString(), emits("Promise<string>"))
    assertThat(TypeName.promiseType(TypeName.VOID).toString(), emits("Promise<void>"))
  }

  @Test
  @DisplayName("Empty anonymous type")
  fun testEmptyAnonymousType() {
    assertThat(TypeName.anonymousType(emptyList()).toString(), equalTo("{}"))
  }

  @Test
  @DisplayName("Breaks an anonymous type that does not fit, one member per line")
  fun testAnonymousTypeBreaksOnWidth() {
    val args = TypeName.anonymousType(
      TypeName.anonymousMember("limit", NUMBER, optional = true),
      TypeName.anonymousMember("cursor", STRING, optional = true),
      TypeName.anonymousMember("includeArchived", TypeName.BOOLEAN, optional = true),
      TypeName.anonymousMember("sortDirection", STRING, optional = true),
    )
    val fn = FunctionSpec.builder("list")
      .addParameter("args", args, optional = true)
      .build()

    assertEmitsExactly(
      emit(fn),
      """
      |function list(args?: {
      |  limit?: number;
      |  cursor?: string;
      |  includeArchived?: boolean;
      |  sortDirection?: string;
      |}) {}
      |
      """.trimMargin(),
    )
  }

  @Test
  @DisplayName("A documented member breaks the anonymous type however short it is")
  fun testDocumentedAnonymousMemberBreaks() {
    val args = TypeName.anonymousType(
      TypeName.anonymousMember(
        "limit",
        NUMBER,
        optional = true,
        tsDoc = CodeBlock.of("Server default: %L.\n", 20),
      ),
    )
    val fn = FunctionSpec.builder("list")
      .addParameter("args", args, optional = true)
      .build()

    assertEmitsExactly(
      emit(fn),
      """
      |function list(args?: {
      |  /**
      |   * Server default: 20.
      |   */
      |  limit?: number;
      |}) {}
      |
      """.trimMargin(),
    )
  }

  @Test
  @DisplayName("An anonymous type member can be readonly")
  fun testReadonlyAnonymousMember() {
    val typeName = TypeName.anonymousType(
      TypeName.anonymousMember("id", STRING, readonly = true),
      TypeName.anonymousMember("label", STRING),
    )

    assertThat(typeName.toString(), emits("{ readonly id: string; label: string }"))
  }
}
