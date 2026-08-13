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
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.inEmitOrder
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.StringWriter

/**
 * Emitted modifier order is TypeScript's grammar order, and must not depend on the
 * declaration order of the [Modifier] enum or on the order the caller added them in.
 */
@DisplayName("Modifier Ordering Tests")
class ModifierOrderingTests {

  @Test
  @DisplayName("Sorts a full modifier set into TypeScript's grammar order")
  fun testFullSetSortsToGrammarOrder() {
    val everything = setOf(
      Modifier.VAR,
      Modifier.SET,
      Modifier.READONLY,
      Modifier.ABSTRACT,
      Modifier.STATIC,
      Modifier.PRIVATE,
      Modifier.DECLARE,
      Modifier.EXPORT,
    )

    assertThat(
      everything.inEmitOrder(),
      equalTo(
        listOf(
          Modifier.EXPORT,
          Modifier.DECLARE,
          Modifier.PRIVATE,
          Modifier.STATIC,
          Modifier.ABSTRACT,
          Modifier.READONLY,
          Modifier.SET,
          Modifier.VAR,
        ),
      ),
    )
  }

  @Test
  @DisplayName("Ignores the order the caller supplied modifiers in")
  fun testCallerOrderIsIrrelevant() {
    val forwards = setOf(Modifier.PUBLIC, Modifier.STATIC, Modifier.READONLY)
    val backwards = setOf(Modifier.READONLY, Modifier.STATIC, Modifier.PUBLIC)

    assertThat(backwards.inEmitOrder(), equalTo(forwards.inEmitOrder()))
  }

  @Test
  @DisplayName("Generates `static` before `readonly` on a property")
  fun testStaticPrecedesReadonly() {
    // Upstream outfoxx/typescriptpoet#16: `readonly static` is rejected by tsc.
    val testClass = ClassSpec.builder("Test")
      .addProperty(
        "value",
        TypeName.NUMBER,
        false,
        Modifier.PRIVATE,
        Modifier.STATIC,
        Modifier.READONLY,
      )
      .build()

    val out = StringWriter()
    testClass.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      emits(
        """
            class Test {

              private static readonly value: number;

            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Generates `abstract` before `readonly` on a property")
  fun testAbstractPrecedesReadonly() {
    // tsc: "'abstract' modifier must precede 'readonly' modifier".
    val testClass = ClassSpec.builder("Test")
      .addModifiers(Modifier.ABSTRACT)
      .addProperty(
        "value",
        TypeName.NUMBER,
        false,
        Modifier.PROTECTED,
        Modifier.ABSTRACT,
        Modifier.READONLY,
      )
      .build()

    val out = StringWriter()
    testClass.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      emits(
        """
            abstract class Test {

              protected abstract readonly value: number;

            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Generates `static` before `get` on an accessor")
  fun testStaticPrecedesGet() {
    val testClass = ClassSpec.builder("Test")
      .addFunction(
        FunctionSpec.builder("value")
          .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.GET)
          .returns(TypeName.NUMBER)
          .addStatement("return 5")
          .build(),
      )
      .build()

    val out = StringWriter()
    testClass.emit(CodeWriter(out))

    // `public` is implicit on a class member and is deliberately suppressed; ordering the
    // remaining two is the point of this case.
    assertThat(
      out.toString(),
      emits(
        """
            class Test {

              static get value(): number {
                return 5;
              }

            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Generates `export` before `declare` on a class")
  fun testExportPrecedesDeclare() {
    val testClass = ClassSpec.builder("Test")
      .addModifiers(Modifier.DECLARE, Modifier.EXPORT)
      .build()

    val out = StringWriter()
    testClass.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      emits(
        """
            export declare class Test {
            }

        """.trimIndent(),
      ),
    )
  }
}
