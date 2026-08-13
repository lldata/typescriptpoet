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
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.CodeWriter
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.ModuleSpec
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

  private fun emit(module: ModuleSpec): String {
    val out = StringWriter()
    module.emit(CodeWriter(out))
    return out.toString()
  }

  private fun emit(cls: ClassSpec): String {
    val out = StringWriter()
    cls.emit(CodeWriter(out))
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

  @Test
  @DisplayName("Lays out a literal returned by addStatement at the statement's own indent")
  fun testAddStatement() {
    // addStatement is the API for `return { ... };` -- it is one statement, and it owns the
    // `;` and the newline. It wraps its content in %[ %], whose hanging indent is meant for an
    // expression too long for the line; applied to a literal that lays itself out it put the
    // members and the closing brace two levels too deep. Callers were told to write
    // addCode("return %L;\n", obj) instead, which hands back the terminator that #7 was filed
    // to stop them writing.
    val obj = CodeBlock.objectLiteral()
      .addProperty("alphaAlphaAlpha", "%S", "aaaaaaaaaaaaaaaaaaaa")
      .addProperty("betaBetaBeta", "%S", "bbbbbbbbbbbbbbbbbbbb")
      .addProperty("gammaGammaGamma", "%S", "cccccccccccccccccccc")
      .build()

    assertEmitsExactly(
      emit(FunctionSpec.builder("create").addStatement("return %L", obj).build()),
      """
          function create() {
            return {
              alphaAlphaAlpha: "aaaaaaaaaaaaaaaaaaaa",
              betaBetaBeta: "bbbbbbbbbbbbbbbbbbbb",
              gammaGammaGamma: "cccccccccccccccccccc",
            };
          }

      """.trimIndent(),
    )
  }

  @Test
  @DisplayName("Resumes the statement after a literal that broke")
  fun testStatementContinuesAfterLiteral() {
    // The delicate half: the statement is suspended for the literal and has to come back for
    // what follows, with the indent it was owed still balanced at %].
    val obj = CodeBlock.objectLiteral()
      .addProperty("alphaAlphaAlpha", "%S", "aaaaaaaaaaaaaaaaaaaa")
      .addProperty("betaBetaBeta", "%S", "bbbbbbbbbbbbbbbbbbbb")
      .addProperty("gammaGammaGamma", "%S", "cccccccccccccccccccc")
      .build()

    val fn = FunctionSpec.builder("create")
      .addStatement("return %L as %T", obj, TypeName.standard("Config"))
      .addStatement("return done")
      .build()

    assertEmitsExactly(
      emit(fn),
      """
          function create() {
            return {
              alphaAlphaAlpha: "aaaaaaaaaaaaaaaaaaaa",
              betaBetaBeta: "bbbbbbbbbbbbbbbbbbbb",
              gammaGammaGamma: "cccccccccccccccccccc",
            } as Config;
            return done;
          }

      """.trimIndent(),
    )
  }

  // The four tests below are all one defect: an ObjectLiteral handed to %L is flattened with
  // toString() when the CodeBlock is *built*, so the literal is rendered by a throwaway
  // CodeWriter that shares no state with the one writing the file. It has no imports to
  // collect into, no renames, no scope, and sits at column 0. Same shape as upstream #27,
  // which was fixed for a nested CodeBlock only -- see CodeWriterTests.

  @Test
  @DisplayName("Collects imports for types referenced inside a member value")
  fun testMemberValueKeepsImports() {
    val obj = CodeBlock.objectLiteral()
      .addProperty("stream", "new %T()", TypeName.namedImport("Observable", "rxjs/observable"))
      .build()

    val file = FileSpec.builder("test")
      .addFunction(FunctionSpec.builder("create").addCode("return %L;\n", obj).build())
      .build()

    assertThat(
      buildString { file.writeTo(this) },
      emits(
        """
            import { Observable } from "rxjs/observable";


            function create() {
              return { stream: new Observable() };
            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Renames a member value's type when its name collides with another import")
  fun testMemberValueHonoursRenames() {
    // The worse half of the same bug: this one compiles. Without the rename the literal's
    // `Thing` binds to the import from "./a", silently the wrong type.
    val obj = CodeBlock.objectLiteral()
      .addProperty("x", "new %T()", TypeName.namedImport("Thing", "./b"))
      .build()

    val file = FileSpec.builder("test")
      .addFunction(
        FunctionSpec.builder("create")
          .addStatement("const q = new %T()", TypeName.namedImport("Thing", "./a"))
          .addCode("return %L;\n", obj)
          .build(),
      )
      .build()

    assertThat(
      buildString { file.writeTo(this) },
      emits(
        """
            import { Thing } from "./a";
            import { Thing as Thing_ } from "./b";


            function create() {
              const q = new Thing();
              return { x: new Thing_() };
            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Resolves a member value's type against the enclosing scope")
  fun testMemberValueHonoursScope() {
    val obj = CodeBlock.objectLiteral()
      .addProperty("v", "%T.make()", TypeName.standard("Api.Inner.Thing"))
      .build()

    val module = ModuleSpec.builder("Api")
      .addFunction(FunctionSpec.builder("create").addCode("return %L;\n", obj).build())
      .build()

    assertThat(
      emit(module),
      emits(
        """
            namespace Api {

              function create() {
                return { v: Inner.Thing.make() };
              }

            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Measures a member at the length its name will actually have")
  fun testMeasuredAfterScopeResolution() {
    // Inside `namespace Api` these emit as `Inner.Thing`, and the literal fits on one line.
    // Measured as the `Api.Inner.Thing` they are spelled with, it does not, and the literal
    // broke over four lines to avoid an overflow that was never going to happen.
    val obj = CodeBlock.objectLiteral()
      .addProperty("firstValue", "%T.make()", TypeName.standard("Api.Inner.Thing"))
      .addProperty("secondValue", "%T.make()", TypeName.standard("Api.Inner.Other"))
      .build()

    val module = ModuleSpec.builder("Api")
      .addFunction(FunctionSpec.builder("create").addStatement("return %L", obj).build())
      .build()

    assertEmitsExactly(
      emit(module),
      """
          namespace Api {
            function create() {
              return { firstValue: Inner.Thing.make(), secondValue: Inner.Other.make() };
            }
          }

      """.trimIndent(),
    )
  }

  @Test
  @DisplayName("Measures the literal against the column it is emitted at")
  fun testMeasuredAtEmitColumn() {
    // Fits on one line from column 0 and does not from column 11, where it is actually
    // written. Measuring the wrong one overruns the print width.
    val obj = CodeBlock.objectLiteral()
      .addProperty("alpha", "%S", "aaaaaaaaaaaaaaaaaaaaaaaaaaaa")
      .addProperty("beta", "%S", "bbbbbbbbbbbbbbbbbbbb")
      .build()

    val cls = ClassSpec.builder("Factory")
      .addFunction(FunctionSpec.builder("create").addCode("return %L;\n", obj).build())
      .build()

    assertEmitsExactly(
      emit(cls),
      """
          class Factory {
            create() {
              return {
                alpha: "aaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                beta: "bbbbbbbbbbbbbbbbbbbb",
              };
            }
          }

      """.trimIndent(),
    )
  }
}
