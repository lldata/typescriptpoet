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

import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.CodeWriter
import io.outfoxx.typescriptpoet.DecoratorSpec
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.ParameterSpec
import io.outfoxx.typescriptpoet.PropertySpec
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.tag
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.StringWriter

/**
 * Covers public API that had no test at all.
 *
 * `TypeName.Intersection` sat at 0% instruction coverage despite being a documented factory,
 * as did the reified `tag` extensions, and both `FileSpec.get` overloads were barely
 * touched. These are the parts most likely to break unnoticed.
 */
@DisplayName("Uncovered API Tests")
class UncoveredApiTests {

  @Test
  @DisplayName("Generates intersection types")
  fun testIntersectionType() {
    val intersection = TypeName.intersectionType(
      TypeName.implicit("Person"),
      TypeName.implicit("Serializable"),
      TypeName.implicit("Loggable"),
    )

    assertThat(intersection.toString(), equalTo("Person & Serializable & Loggable"))
  }

  @Test
  @DisplayName("Parenthesises an intersection used as an operand")
  fun testIntersectionAsOperand() {
    val intersection = TypeName.intersectionType(
      TypeName.implicit("Person"),
      TypeName.implicit("Serializable"),
    )

    assertThat(
      TypeName.arrayShorthandType(intersection).toString(),
      equalTo("(Person & Serializable)[]"),
    )
    assertThat(
      TypeName.keyOf(intersection).toString(),
      equalTo("keyof (Person & Serializable)"),
    )
  }

  @Test
  @DisplayName("A single-element intersection needs no parentheses")
  fun testSingleElementIntersection() {
    val intersection = TypeName.intersectionType(TypeName.implicit("Person"))

    assertThat(TypeName.arrayShorthandType(intersection).toString(), equalTo("Person[]"))
  }

  @Test
  @DisplayName("Tags round-trip through every builder that supports them")
  fun testTagExtensions() {
    assertThat(ClassSpec.builder("A").tag(1).build().tag<Int>(), equalTo(1))
    assertThat(FunctionSpec.builder("a").tag("f").build().tag<String>(), equalTo("f"))
    assertThat(FileSpec.builder("a").tag(2).build().tag<Int>(), equalTo(2))
    assertThat(ModuleSpec.builder("a").tag(3).build().tag<Int>(), equalTo(3))
    assertThat(
      ParameterSpec.builder("a", TypeName.STRING).tag(4).build().tag<Int>(),
      equalTo(4),
    )
    assertThat(
      PropertySpec.builder("a", TypeName.STRING).tag(5).build().tag<Int>(),
      equalTo(5),
    )
    assertThat(DecoratorSpec.builder("a").tag(6).build().tag<Int>(), equalTo(6))
  }

  @Test
  @DisplayName("An absent tag reads back as null")
  fun testMissingTagIsNull() {
    assertThat(ClassSpec.builder("A").build().tag<String>(), nullValue())
  }

  @Test
  @DisplayName("FileSpec.get derives a module path from a type")
  fun testFileSpecGetFromType() {
    val file = FileSpec.get(ClassSpec.builder("Greeter").build())

    val out = StringWriter()
    file.writeTo(out)

    assertThat(out.toString(), equalTo("\nclass Greeter {\n}\n"))
  }

  @Test
  @DisplayName("FileSpec.get lifts a module's members to the file")
  fun testFileSpecGetFromModule() {
    val module = ModuleSpec.builder("Shapes")
      .addClass(ClassSpec.builder("Circle").build())
      .build()

    val file = FileSpec.get(module)

    val out = StringWriter()
    file.writeTo(out)

    // The members are lifted out of the namespace, not nested inside it.
    assertThat(out.toString(), equalTo("\nclass Circle {\n}\n"))
  }

  @Test
  @DisplayName("Generates a namespace and a module")
  fun testModuleKinds() {
    val namespace = ModuleSpec.builder("Shapes")
      .addModifier(Modifier.EXPORT)
      .addClass(ClassSpec.builder("Circle").build())
      .build()

    val out = StringWriter()
    namespace.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      equalTo(
        """
            export namespace Shapes {

              class Circle {
              }

            }

        """.trimIndent(),
      ),
    )

    val module = ModuleSpec.builder("Shapes", ModuleSpec.Kind.MODULE).build()

    val moduleOut = StringWriter()
    module.emit(CodeWriter(moduleOut))

    assertThat(moduleOut.toString(), equalTo("module Shapes {\n}\n"))
  }

  @Test
  @DisplayName("Rejects a module member carrying a class-member modifier")
  fun testModuleRejectsMemberModifiers() {
    val error = runCatching {
      ModuleSpec.builder("Shapes")
        .addClass(ClassSpec.builder("Circle").addModifiers(Modifier.STATIC).build())
    }.exceptionOrNull()

    assertThat(error is IllegalArgumentException, equalTo(true))
  }

  @Test
  @DisplayName("Rejects more than one of export and declare on a module")
  fun testModuleRejectsConflictingModifiers() {
    val error = runCatching {
      ModuleSpec.builder("Shapes")
        .addModifier(Modifier.EXPORT)
        .addModifier(Modifier.DECLARE)
    }.exceptionOrNull()

    assertThat(error is IllegalArgumentException, equalTo(true))
  }

  @Test
  @DisplayName("Rejects the internal sentinel names from the public builder")
  fun testSentinelNamesAreRejected() {
    // FunctionSpec discriminates constructors, call signatures and index signatures by
    // comparing the name against these literals. Reaching them through the ordinary builder
    // would silently produce a different kind of member than the caller asked for.
    for (sentinel in listOf("constructor()", "callable()", "indexable()")) {
      val error = runCatching { FunctionSpec.builder(sentinel) }.exceptionOrNull()
      assertThat("'$sentinel' should be rejected", error is IllegalArgumentException, equalTo(true))
    }
  }
}
