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
import dk.lldata.typescriptpoet.CodeBlock
import dk.lldata.typescriptpoet.CodeWriter
import dk.lldata.typescriptpoet.FileSpec
import dk.lldata.typescriptpoet.FunctionSpec
import dk.lldata.typescriptpoet.ModuleSpec
import dk.lldata.typescriptpoet.TypeName
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
  @DisplayName("Takes a getter as a member")
  fun testGetterMember() {
    val obj = CodeBlock.objectLiteral()
      .addGetter("read", "return %L(%P, config)", "messagesReadNode", "\${path}/read")
      .build()

    assertEmitsExactly(
      emit(FunctionSpec.builder("create").addStatement("return %L", obj).build()),
      """
          function create() {
            return {
              get read() {
                return messagesReadNode(`${'$'}{path}/read`, config);
              },
            };
          }

      """.trimIndent(),
    )
  }

  @Test
  @DisplayName("Builds a lazily-reached node without hand-formatting")
  fun testLazyNodeTree() {
    // The shape #9 was filed for: a factory returning a node whose children are reached on
    // access. The getter is the member that makes it lazy -- as a property it would build the
    // whole tree whenever any of it was touched -- and every type in here arrives through a
    // getter body, an arrow member or a nested literal, which is where the imports used to be
    // dropped.
    val requestOptions = TypeName.namedImport("RequestOptions", "./request")
    val apiRequest = TypeName.namedImport("apiRequest", "./request")
    val thread = TypeName.namedImport("Thread", "./types")

    val getArrow = FunctionSpec.builder("get")
      .arrow()
      .addParameter("options", requestOptions, optional = true)
      .addStatement(
        "return %T<%T[]>(%L, options)",
        apiRequest,
        thread,
        CodeBlock.objectLiteral()
          .addProperty("method", "%S", "GET")
          .addShorthand("path")
          .build(),
      )
      .build()

    val obj = CodeBlock.objectLiteral()
      .addGetter("read", "return %L(%P, config)", "messagesReadNode", "\${path}/read")
      .addProperty("get", "%L", getArrow)
      .build()

    val fn = FunctionSpec.builder("messagesNode")
      .addParameter("path", TypeName.STRING)
      .addParameter("config", TypeName.namedImport("ApiConfig", "./config"))
      .addStatement("return %L", obj)
      .build()

    assertEmitsExactly(
      buildString { FileSpec.builder("messages").addFunction(fn).build().writeTo(this) },
      """
          import { ApiConfig } from "./config";
          import { RequestOptions, apiRequest } from "./request";
          import { Thread } from "./types";

          function messagesNode(path: string, config: ApiConfig) {
            return {
              get read() {
                return messagesReadNode(`${'$'}{path}/read`, config);
              },
              get: (options?: RequestOptions) => {
                return apiRequest<Thread[]>({ method: "GET", path }, options);
              },
            };
          }

      """.trimIndent(),
    )
  }

  @Test
  @DisplayName("Breaks a literal holding a getter however short it measures")
  fun testGetterForcesBreak() {
    // A getter body is a statement list, so it always spans lines, so the literal does too --
    // no separate rule, just the one a multi-line member already goes by.
    val obj = CodeBlock.objectLiteral()
      .addShorthand("id")
      .addGetter("n", "return 1")
      .build()

    assertEmitsExactly(
      emit(FunctionSpec.builder("create").addStatement("return %L", obj).build()),
      """
          function create() {
            return {
              id,
              get n() {
                return 1;
              },
            };
          }

      """.trimIndent(),
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

  @Test
  @DisplayName("Counts the rest of the statement when deciding whether the literal fits")
  fun testTrailingTextCountsTowardsTheWidth() {
    val obj = CodeBlock.objectLiteral()
      .addProperty("method", "%S", "POST")
      .addShorthand("path")
      .addShorthand("body")
      .build()

    // The literal measures 30 wide and would fit at this column on its own. It is what comes
    // after it on the line that does not, and the literal is the only thing here that can
    // give way.
    val fn = FunctionSpec.builder("marketplaceMessagesThreadIdReactionsMessageIdNode")
      .addStatement("return apiRequest<DmMessageDto>(%L, config, options)", obj)
      .build()

    assertEmitsExactly(
      emit(fn),
      """
      |function marketplaceMessagesThreadIdReactionsMessageIdNode() {
      |  return apiRequest<DmMessageDto>({
      |    method: "POST",
      |    path,
      |    body,
      |  }, config, options);
      |}
      |
      """.trimMargin(),
    )
  }

  @Test
  @DisplayName("Spreads a bare binding")
  fun testSpreadShorthand() {
    val obj = CodeBlock.objectLiteral()
      .addSpread("defaults")
      .addProperty("extra", "%S", "x")
      .build()

    assertThat(obj.toString(), equalTo("{ ...defaults, extra: \"x\" }"))
  }

  @Test
  @DisplayName("Collects an import for a spread of an imported symbol")
  fun testSpreadKeepsImports() {
    // #45: addShorthand smuggled the spread through, but a TypeName interpolated into a
    // String is just text by the time the writer sees it -- the import never made it in and
    // the file did not compile. addSpread carries the TypeName through %T instead.
    val defaults = TypeName.namedImport("defaults", "./defaults")
    val obj = CodeBlock.objectLiteral()
      .addSpread(defaults)
      .addProperty("extra", "%S", "x")
      .build()

    val file = FileSpec.builder("test")
      .addFunction(FunctionSpec.builder("make").addCode("return %L;\n", obj).build())
      .build()

    assertThat(
      buildString { file.writeTo(this) },
      emits(
        """
            import { defaults } from "./defaults";


            function make() {
              return { ...defaults, extra: "x" };
            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Takes a spec as a member value without a %L to carry it")
  fun testSpecValuedMembers() {
    val getter = FunctionSpec.builder("get")
      .arrow()
      .addParameter("id", TypeName.STRING)
      .expressionBody("load(id)")
      .build()
    val obj = CodeBlock.objectLiteral()
      .addProperty("get", getter)
      .addProperty("meta", CodeBlock.objectLiteral().addShorthand("version").build())
      .addProperty("now", CodeBlock.call("Date.now").build())
      .build()

    assertThat(
      obj.toString(),
      equalTo("{ get: (id: string) => load(id), meta: { version }, now: Date.now() }"),
    )
  }
}
