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

import io.outfoxx.typescriptpoet.CodeWriter
import io.outfoxx.typescriptpoet.TypeAliasSpec
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.TypeName.Mapped
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

    assertThat(conditional.toString(), equalTo("T extends string ? Yes : No"))
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

    assertThat(conditional.toString(), equalTo("T extends Array<infer U> ? U : never"))
  }

  @Test
  @DisplayName("Chains conditional types to the right without parentheses")
  fun testNestedConditional() {
    val inner = TypeName.conditionalType(t, TypeName.NUMBER, TypeName.implicit("B"), TypeName.implicit("C"))
    val outer = TypeName.conditionalType(t, TypeName.STRING, TypeName.implicit("A"), inner)

    assertThat(
      outer.toString(),
      equalTo("T extends string ? A : T extends number ? B : C"),
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

    assertThat(mapped.toString(), equalTo("{ [K in keyof T]: T[K] }"))
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

    assertThat(mapped.toString(), equalTo("{ readonly [K in keyof T]?: T[K] }"))
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

    assertThat(mapped.toString(), equalTo("{ -readonly [K in keyof T]-?: T[K] }"))
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
      equalTo("{ [K in keyof T as `get\${Capitalize<K>}`]: T[K] }"),
    )
  }

  @Test
  @DisplayName("Generates a template literal type")
  fun testTemplateLiteral() {
    val template = TypeName.templateLiteralType("on", k, "Changed")

    assertThat(template.toString(), equalTo("`on\${K}Changed`"))
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
      equalTo("type Mutable<T> = { -readonly [K in keyof T]: T[K] };\n"),
    )
  }
}
