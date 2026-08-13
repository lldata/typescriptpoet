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

import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.CodeWriter
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.TypeName
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.StringWriter

@DisplayName("Object Literal Tests")
class ObjectLiteralTests {

  private fun emit(fn: FunctionSpec): String {
    val out = StringWriter()
    fn.emit(CodeWriter(out), null, setOf())
    return out.toString()
  }

  @Test
  @DisplayName("Keeps a short object literal on one line")
  fun testShortLiteralInline() {
    val obj = CodeBlock.objectLiteral()
      .addProperty("name", CodeBlock.of("n"))
      .addShorthand("id")
      .build()

    assertThat(obj.toString(), equalTo("{ name: n, id }"))
  }

  @Test
  @DisplayName("Generates an empty object literal")
  fun testEmptyLiteral() {
    assertThat(CodeBlock.objectLiteral().build().toString(), equalTo("{}"))
  }

  @Test
  @DisplayName("Breaks a long object literal, one member per line with a trailing comma")
  fun testLongLiteralBreaks() {
    val obj = CodeBlock.objectLiteral()
      .addProperty("alphaAlphaAlpha", "%S", "aaaaaaaaaaaaaaaaaaaa")
      .addProperty("betaBetaBeta", "%S", "bbbbbbbbbbbbbbbbbbbb")
      .addProperty("gammaGammaGamma", "%S", "cccccccccccccccccccc")
      .build()

    val fn = FunctionSpec.builder("create")
      .addCode("return %L;\n", obj)
      .build()

    assertThat(
      emit(fn),
      equalTo(
        """
            function create() {
              return {
                alphaAlphaAlpha: "aaaaaaaaaaaaaaaaaaaa",
                betaBetaBeta: "bbbbbbbbbbbbbbbbbbbb",
                gammaGammaGamma: "cccccccccccccccccccc",
              };
            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Takes an arrow function as a member value")
  fun testArrowMember() {
    val arrow = FunctionSpec.builder("send")
      .arrow()
      .addParameter("message", TypeName.STRING)
      .returns(TypeName.VOID)
      .addStatement("console.log(message)")
      .build()

    val obj = CodeBlock.objectLiteral()
      .addProperty("send", CodeBlock.of("%L", arrow))
      .build()

    val fn = FunctionSpec.builder("create").addCode("return %L;\n", obj).build()

    assertThat(
      emit(fn),
      equalTo(
        """
            function create() {
              return {
                send: (message: string): void => {
                  console.log(message);
                },
              };
            }

        """.trimIndent(),
      ),
    )
  }
}
