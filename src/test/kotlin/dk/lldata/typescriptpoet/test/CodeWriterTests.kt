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
import dk.lldata.typescriptpoet.SymbolSpec
import dk.lldata.typescriptpoet.TypeName
import dk.lldata.typescriptpoet.TypeName.Companion.STRING
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.StringWriter

@DisplayName("CodeWriter Tests")
class CodeWriterTests {

  @Test
  fun `test long line wrapping`() {
    val testFunc = FunctionSpec.builder("test")
      .returns(STRING)
      .addStatement(
        "return X(aaaaa,%Wbbbbb,%Wccccc,%Wddddd,%Weeeee,%Wfffff,%Wggggg,%Whhhhh,%Wiiiii,%Wjjjjj,%Wkkkkk,%Wlllll,%Wmmmmm,%Wnnnnn,%Wooooo,%Wppppp,%Wqqqqq)",
      )
      .build()

    MatcherAssert.assertThat(
      testFunc.toString(),
      emits(
        """
            function test(): string {
              return X(aaaaa, bbbbb, ccccc, ddddd, eeeee, fffff, ggggg, hhhhh, iiiii, jjjjj, kkkkk, lllll,
                  mmmmm, nnnnn, ooooo, ppppp, qqqqq);
            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Collects imports for types referenced inside a nested CodeBlock")
  fun testNestedCodeBlockKeepsImports() {
    // Upstream outfoxx/typescriptpoet#27: a CodeBlock passed as a %L argument used to be
    // flattened with toString(), which discarded its format parts, so the %T inside it never
    // reached the import collector and `import {  X  } from \"x\";` went missing.
    val testFunc = FunctionSpec.builder("test")
      .returns(STRING)
      .addStatement("return %L", CodeBlock.of("new %T()", TypeName.namedImport("X", "x")))
      .build()

    val file = FileSpec.builder("Test")
      .addFunction(testFunc)
      .build()

    val out = StringWriter()
    file.writeTo(out)

    MatcherAssert.assertThat(
      out.toString(),
      emits(
        """
            import { X } from "x";


            function test(): string {
              return new X();
            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Collects imports for types referenced inside a %L spec argument")
  fun testSpecLiteralKeepsImports() {
    // The same flattening as above, one argument kind over: argToLiteral keeps a CodeBlock
    // but calls toString() on everything else, so a spec handed to %L is rendered by a
    // throwaway CodeWriter and its types never reach the import collector. CodeWriter has
    // arms for these -- ClassSpec, FunctionSpec and the rest -- that nothing could reach.
    val arrow = FunctionSpec.builder("send")
      .arrow()
      .addParameter("m", TypeName.namedImport("X", "x"))
      .build()

    val file = FileSpec.builder("Test")
      .addFunction(
        FunctionSpec.builder("test")
          .addCode("const f = %L;\n", arrow)
          .build(),
      )
      .build()

    val out = StringWriter()
    file.writeTo(out)

    MatcherAssert.assertThat(
      out.toString(),
      emits(
        """
            import { X } from "x";


            function test() {
              const f = (m: X) => {};
            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Collects imports for a type used as a type-variable bound")
  fun testBoundKeepsImports() {
    // Bounds were rendered by interpolating the type's text into the format string, which put
    // the right name in the file and never reached the import collector -- `X extends Base`
    // with no `import { Base }` anywhere, so the file did not compile. Same defect as the %L
    // flattening above, one place over.
    val file = FileSpec.builder("Test")
      .addClass(
        ClassSpec.builder("Holder")
          .addTypeVariable(TypeName.typeVariable("X", TypeName.bound(TypeName.namedImport("Base", "./base"))))
          .build(),
      )
      .build()

    val out = StringWriter()
    file.writeTo(out)

    MatcherAssert.assertThat(
      out.toString(),
      emits(
        """
            import { Base } from "./base";


            class Holder<X extends Base> {}

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Breaks a type variable list that does not fit, one variable per line")
  fun testTypeVariablesBreakOnWidth() {
    // What follows the `>` counts only when it cannot break itself. A parameter list can, so
    // it does not push the type variables over; a class's `extends` clause cannot, so it does.
    val fn = FunctionSpec.builder("query")
      .addTypeVariable(TypeName.typeVariable("TFirstParameter", TypeName.bound(TypeName.STRING)))
      .addTypeVariable(TypeName.typeVariable("TSecondParameter", TypeName.bound(TypeName.NUMBER)))
      .addTypeVariable(TypeName.typeVariable("TThirdParameter", TypeName.bound(TypeName.implicit("object"))))
      .addParameter("value", TypeName.typeVariable("TFirstParameter"))
      .returns(TypeName.VOID)
      .build()

    val out = StringWriter()
    fn.emit(CodeWriter(out), null, setOf())

    assertEmitsExactly(
      out.toString(),
      """
          function query<
            TFirstParameter extends string,
            TSecondParameter extends number,
            TThirdParameter extends object,
          >(value: TFirstParameter): void {}

      """.trimIndent(),
    )
  }

  @Test
  @DisplayName("Keeps a type variable list inline when only the parameters overflow")
  fun testTypeVariablesStayInlineWhenParametersBreak() {
    val fn = FunctionSpec.builder("select")
      .addTypeVariable(TypeName.typeVariable("TFirst", TypeName.bound(TypeName.STRING)))
      .addTypeVariable(TypeName.typeVariable("TSecond", TypeName.bound(TypeName.NUMBER)))
      .addParameter("firstArgument", TypeName.STRING)
      .addParameter("secondArgument", TypeName.NUMBER)
      .addParameter("thirdArgument", TypeName.BOOLEAN)
      .returns(TypeName.VOID)
      .build()

    val out = StringWriter()
    fn.emit(CodeWriter(out), null, setOf())

    assertEmitsExactly(
      out.toString(),
      """
          function select<TFirst extends string, TSecond extends number>(
            firstArgument: string,
            secondArgument: number,
            thirdArgument: boolean,
          ): void {}

      """.trimIndent(),
    )
  }

  @Test
  @DisplayName("Collects imports for a type or symbol passed directly to %L")
  fun testTypeAndSymbolLiteralKeepImports() {
    // %T and %Q are the placeholders for these, but nothing stopped a caller reaching for %L,
    // and it used to render them with toString(): a TypeName gave the right text and no
    // import, and a SymbolSpec -- a data class, with no toString of its own -- gave the
    // Kotlin dump `ImportsName(value=Y, source=y)`.
    val testFunc = FunctionSpec.builder("test")
      .addStatement("const a: %L = new %L()", TypeName.namedImport("X", "x"), SymbolSpec.from("Y@y"))
      .build()

    val file = FileSpec.builder("Test")
      .addFunction(testFunc)
      .build()

    val out = StringWriter()
    file.writeTo(out)

    MatcherAssert.assertThat(
      out.toString(),
      emits(
        """
            import { X } from "x";
            import { Y } from "y";


            function test() {
              const a: X = new Y();
            }

        """.trimIndent(),
      ),
    )
  }
}
