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
package dk.lldata.typescriptpoet.test

import dk.lldata.typescriptpoet.CodeWriter
import dk.lldata.typescriptpoet.Modifier
import dk.lldata.typescriptpoet.TypeAliasSpec
import dk.lldata.typescriptpoet.TypeName
import dk.lldata.typescriptpoet.TypeName.Mapped
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.StringWriter

@DisplayName("Computed Type Tests")
class ComputedTypeTests {

  private val t = TypeName.typeVariable("T")
  private val k = TypeName.typeVariable("K")

  @Test
  @DisplayName("Generates a conditional type")
  fun testConditional() {
    val conditional = TypeName.conditionalType(
      checkType = t,
      extendsType = TypeName.STRING,
      trueType = TypeName.implicit("Yes"),
      falseType = TypeName.implicit("No"),
    )

    assertThat(conditional.toString(), emits("T extends string ? Yes : No"))
  }

  @Test
  @DisplayName("Generates a conditional type with infer")
  fun testConditionalWithInfer() {
    val conditional = TypeName.conditionalType(
      checkType = t,
      extendsType = TypeName.parameterizedType(TypeName.ARRAY, TypeName.infer("U")),
      trueType = TypeName.typeVariable("U"),
      falseType = TypeName.NEVER,
    )

    assertThat(conditional.toString(), emits("T extends Array<infer U> ? U : never"))
  }

  @Test
  @DisplayName("Chains conditional types to the right without parentheses")
  fun testNestedConditional() {
    val inner = TypeName.conditionalType(t, TypeName.NUMBER, TypeName.implicit("B"), TypeName.implicit("C"))
    val outer = TypeName.conditionalType(t, TypeName.STRING, TypeName.implicit("A"), inner)

    assertThat(
      outer.toString(),
      emits("T extends string ? A : T extends number ? B : C"),
    )
  }

  @Test
  @DisplayName("Generates a mapped type")
  fun testMapped() {
    val mapped = TypeName.mappedType(
      keyName = "K",
      constraint = TypeName.keyOf(t),
      valueType = TypeName.indexedAccess(t, k),
    )

    assertThat(mapped.toString(), emits("{ [K in keyof T]: T[K] }"))
  }

  @Test
  @DisplayName("Generates a mapped type with readonly and optional modifiers")
  fun testMappedWithModifiers() {
    val mapped = TypeName.mappedType(
      keyName = "K",
      constraint = TypeName.keyOf(t),
      valueType = TypeName.indexedAccess(t, k),
      readonly = Mapped.Change.KEEP,
      optional = Mapped.Change.KEEP,
    )

    assertThat(mapped.toString(), emits("{ readonly [K in keyof T]?: T[K] }"))
  }

  @Test
  @DisplayName("Generates a mapped type that removes modifiers")
  fun testMappedRemovingModifiers() {
    val mapped = TypeName.mappedType(
      keyName = "K",
      constraint = TypeName.keyOf(t),
      valueType = TypeName.indexedAccess(t, k),
      readonly = Mapped.Change.REMOVE,
      optional = Mapped.Change.REMOVE,
    )

    assertThat(mapped.toString(), emits("{ -readonly [K in keyof T]-?: T[K] }"))
  }

  @Test
  @DisplayName("Generates a mapped type with an as clause")
  fun testMappedWithAsClause() {
    val mapped = TypeName.mappedType(
      keyName = "K",
      constraint = TypeName.keyOf(t),
      valueType = TypeName.indexedAccess(t, k),
      asClause = TypeName.templateLiteralType(
        "get",
        TypeName.parameterizedType(TypeName.implicit("Capitalize"), k),
      ),
    )

    assertThat(
      mapped.toString(),
      emits("{ [K in keyof T as `get\${Capitalize<K>}`]: T[K] }"),
    )
  }

  @Test
  @DisplayName("Generates a template literal type")
  fun testTemplateLiteral() {
    val template = TypeName.templateLiteralType("on", k, "Changed")

    assertThat(template.toString(), emits("`on\${K}Changed`"))
  }

  @Test
  @DisplayName("Rejects template literal parts that are neither String nor TypeName")
  fun testTemplateLiteralRejectsOtherParts() {
    val error = runCatching { TypeName.templateLiteralType("on", 42) }.exceptionOrNull()

    assertThat(error is IllegalArgumentException, equalTo(true))
  }

  @Test
  @DisplayName("Uses computed types in a type alias")
  fun testTypeAliasWithComputedTypes() {
    // TypeAliasSpec is already `type Name<Vars> = %T`, so it inherits every new TypeName.
    val alias = TypeAliasSpec.builder(
      "Mutable",
      TypeName.mappedType(
        keyName = "K",
        constraint = TypeName.keyOf(t),
        valueType = TypeName.indexedAccess(t, k),
        readonly = Mapped.Change.REMOVE,
      ),
    )
      .addTypeVariable(t)
      .build()

    val out = StringWriter()
    alias.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      emits("type Mutable<T> = { -readonly [K in keyof T]: T[K] };\n"),
    )
  }

  @Test
  @DisplayName("Breaks a long union type alias, one choice per line with a leading pipe")
  fun testLongUnionAliasBreaks() {
    // Issue #5: unions rendered on one line at any length. Prettier breaks them here, and
    // only here -- not in member or parameter position.
    val alias = TypeAliasSpec.builder(
      "Long",
      TypeName.unionType(*(1..4).map { TypeName.literal("AAAAAAAAAAAAAAAAAA$it") }.toTypedArray()),
    ).build()

    val out = StringWriter()
    alias.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      equalTo(
        """
            type Long =
              | "AAAAAAAAAAAAAAAAAA1"
              | "AAAAAAAAAAAAAAAAAA2"
              | "AAAAAAAAAAAAAAAAAA3"
              | "AAAAAAAAAAAAAAAAAA4";

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Keeps a union on one indented line when only the declaration is too long")
  fun testUnionAliasBreaksAfterEqualsFirst() {
    // Issue #8: wrapping went straight to one choice per line. Prettier tries this rung
    // first -- break after the `=`, keep the union on one indented line -- and only splits
    // per choice when that is too wide as well. Here the declaration is 82 columns and the
    // indented union is 52, so the middle form is the one that fits.
    val alias = TypeAliasSpec.builder(
      "LocationLabelKind",
      TypeName.unionType(
        TypeName.literal("MARKETPLACE"),
        TypeName.literal("DISTRICT"),
        TypeName.literal("COUNTRY"),
        TypeName.literal("UNKNOWN"),
      ),
    ).addModifiers(Modifier.EXPORT).build()

    val out = StringWriter()
    alias.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      equalTo(
        """
            export type LocationLabelKind =
              "MARKETPLACE" | "DISTRICT" | "COUNTRY" | "UNKNOWN";

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Leaves a short union inline")
  fun testShortUnionAliasStaysInline() {
    val alias = TypeAliasSpec.builder(
      "Short",
      TypeName.unionType(TypeName.literal("a"), TypeName.literal("b")),
    ).build()

    val out = StringWriter()
    alias.emit(CodeWriter(out))

    assertThat(out.toString(), equalTo("type Short = \"a\" | \"b\";\n"))
  }

  @Test
  @DisplayName("Breaks a long intersection with a trailing ampersand")
  fun testLongIntersectionAliasBreaks() {
    // Prettier lays intersections out differently from unions: the first operand stays on
    // the `=` line and the separator trails.
    val alias = TypeAliasSpec.builder(
      "Inter",
      TypeName.intersectionType(
        TypeName.implicit("AAAAAAAAAAAAAAAAAAAAAAAA"),
        TypeName.implicit("BBBBBBBBBBBBBBBBBBBBBBBBBB"),
        TypeName.implicit("CCCCCCCCCCCCCCCCCCCCCCCCCC"),
      ),
    ).build()

    val out = StringWriter()
    alias.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      equalTo(
        """
            type Inter = AAAAAAAAAAAAAAAAAAAAAAAA &
              BBBBBBBBBBBBBBBBBBBBBBBBBB &
              CCCCCCCCCCCCCCCCCCCCCCCCCC;

        """.trimIndent(),
      ),
    )
  }
}
