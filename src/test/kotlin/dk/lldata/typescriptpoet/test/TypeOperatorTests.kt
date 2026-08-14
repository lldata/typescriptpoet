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

import dk.lldata.typescriptpoet.ClassSpec
import dk.lldata.typescriptpoet.CodeWriter
import dk.lldata.typescriptpoet.FunctionSpec
import dk.lldata.typescriptpoet.TypeName
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.StringWriter

@DisplayName("Type Operator Tests")
class TypeOperatorTests {

  private val person = TypeName.implicit("Person")

  @Test
  @DisplayName("Generates keyof")
  fun testKeyOf() {
    assertThat(TypeName.keyOf(person).toString(), emits("keyof Person"))
  }

  @Test
  @DisplayName("Parenthesises a union under keyof")
  fun testKeyOfUnionIsParenthesised() {
    // `keyof A | B` parses as `(keyof A) | B`, which is not what was asked for.
    val union = TypeName.unionType(person, TypeName.STRING)

    assertThat(TypeName.keyOf(union).toString(), emits("keyof (Person | string)"))
  }

  @Test
  @DisplayName("Generates typeof")
  fun testTypeOf() {
    assertThat(TypeName.typeOf(TypeName.implicit("config")).toString(), emits("typeof config"))
  }

  @Test
  @DisplayName("Generates indexed access")
  fun testIndexedAccess() {
    val access = TypeName.indexedAccess(person, TypeName.implicit("\"name\""))

    assertThat(access.toString(), emits("""Person["name"]"""))
  }

  @Test
  @DisplayName("Generates indexed access with keyof as the index")
  fun testIndexedAccessWithKeyOf() {
    val access = TypeName.indexedAccess(person, TypeName.keyOf(person))

    assertThat(access.toString(), emits("Person[keyof Person]"))
  }

  @Test
  @DisplayName("Generates the array shorthand")
  fun testArrayShorthand() {
    assertThat(TypeName.arrayShorthandType(TypeName.STRING).toString(), emits("string[]"))
  }

  @Test
  @DisplayName("Parenthesises a union under the array shorthand")
  fun testArrayShorthandOfUnion() {
    // `A | B[]` parses as `A | (B[])`.
    val union = TypeName.unionType(TypeName.STRING, TypeName.NUMBER)

    assertThat(
      TypeName.arrayShorthandType(union).toString(),
      emits("(string | number)[]"),
    )
  }

  @Test
  @DisplayName("Generates readonly arrays and tuples")
  fun testReadOnly() {
    assertThat(
      TypeName.readOnly(TypeName.arrayShorthandType(TypeName.STRING)).toString(),
      emits("readonly string[]"),
    )
    assertThat(
      TypeName.readOnly(TypeName.tupleType(TypeName.NUMBER, TypeName.STRING)).toString(),
      emits("readonly [number, string]"),
    )
  }

  @Test
  @DisplayName("Generates unique symbol")
  fun testUniqueSymbol() {
    assertThat(TypeName.UNIQUE_SYMBOL.toString(), emits("unique symbol"))
  }

  @Test
  @DisplayName("Generates labelled, optional and rest tuple elements")
  fun testTupleMembers() {
    val tuple = TypeName.tupleType(
      listOf(
        TypeName.tupleMember(TypeName.STRING, label = "a"),
        TypeName.tupleMember(TypeName.NUMBER, label = "b", optional = true),
        TypeName.tupleMember(
          TypeName.arrayShorthandType(TypeName.BOOLEAN),
          label = "rest",
          rest = true,
        ),
      ),
    )

    assertThat(tuple.toString(), emits("[a: string, b?: number, ...rest: boolean[]]"))
  }

  @Test
  @DisplayName("Generates an unlabelled optional tuple element")
  fun testUnlabelledOptionalTupleMember() {
    val tuple = TypeName.tupleType(
      listOf(
        TypeName.tupleMember(TypeName.STRING),
        TypeName.tupleMember(TypeName.NUMBER, optional = true),
      ),
    )

    assertThat(tuple.toString(), emits("[string, number?]"))
  }

  @Test
  @DisplayName("Generates a generic function type")
  fun testGenericLambda() {
    val lambda = TypeName.genericLambda(
      typeVariables = listOf(TypeName.typeVariable("T")),
      parameters = mapOf("value" to TypeName.typeVariable("T")),
      returnType = TypeName.typeVariable("T"),
    )

    assertThat(lambda.toString(), emits("<T>(value: T) => T"))
  }

  @Test
  @DisplayName("Generates construct signature types")
  fun testConstructorType() {
    assertThat(
      TypeName.constructorType(mapOf("value" to TypeName.STRING), person).toString(),
      emits("new (value: string) => Person"),
    )
    assertThat(
      TypeName.constructorType(mapOf("value" to TypeName.STRING), person, abstract = true).toString(),
      emits("abstract new (value: string) => Person"),
    )
  }

  @Test
  @DisplayName("Emits infer only at the use site")
  fun testInfer() {
    assertThat(TypeName.infer("U").toString(), emits("infer U"))
  }

  @Test
  @DisplayName("Emits a bare type variable at the use site")
  fun testTypeVariableUseSiteIsBare() {
    // Variance and `const` belong to the declaration, so they must not leak into references.
    val typeVar = TypeName.typeVariable(
      "T",
      variance = TypeName.TypeVariable.Variance.OUT,
      const = true,
    )

    assertThat(typeVar.toString(), emits("T"))
  }

  @Test
  @DisplayName("Generates an index signature on a class")
  fun testClassIndexSignature() {
    val testClass = ClassSpec.builder("Test")
      .addIndexable(
        FunctionSpec.indexableBuilder()
          .addParameter("key", TypeName.STRING)
          .returns(TypeName.ANY)
          .build(),
      )
      .build()

    val out = StringWriter()
    testClass.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      emits(
        """
            class Test {

              [key: string]: any;

            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Generates const and variance annotations at the declaration site")
  fun testTypeVariableDeclarationSite() {
    val testClass = ClassSpec.builder("Test")
      .addTypeVariable(TypeName.typeVariable("T", variance = TypeName.TypeVariable.Variance.OUT))
      .addTypeVariable(TypeName.typeVariable("U", const = true))
      .build()

    val out = StringWriter()
    testClass.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      emits(
        """
            class Test<out T, const U> {}

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Generates Record, distinct from Map")
  fun testRecordType() {
    // Issue #1: Map is a runtime class accessed with .get(k) and is not what JSON.parse
    // produces, so generated code modelling JSON-shaped data needs Record.
    assertThat(
      TypeName.recordType(TypeName.STRING, TypeName.NUMBER).toString(),
      equalTo("Record<string, number>"),
    )
    assertThat(
      TypeName.mapType(TypeName.STRING, TypeName.NUMBER).toString(),
      equalTo("Map<string, number>"),
    )
  }

  @Test
  @DisplayName("Generates literal types, escaping string values")
  fun testLiteralTypes() {
    // Issue #2: the only route was implicit() with the caller writing the quotes, which
    // emits an unparseable file the moment the value contains a quote or a backslash.
    assertThat(TypeName.literal("a").toString(), equalTo("\"a\""))
    assertThat(TypeName.literal(42).toString(), equalTo("42"))
    assertThat(TypeName.literal(true).toString(), equalTo("true"))

    val union = TypeName.unionType(TypeName.literal("a"), TypeName.literal("say \"hi\""))

    assertThat(union.toString(), equalTo("\"a\" | \"say \\\"hi\\\"\""))
  }
}
