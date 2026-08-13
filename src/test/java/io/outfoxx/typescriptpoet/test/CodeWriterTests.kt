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
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.TypeName.Companion.STRING
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
      CoreMatchers.equalTo(
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
    // reached the import collector and `import { X } from 'x';` went missing.
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
      CoreMatchers.equalTo(
        """
            import {X} from 'x';


            function test(): string {
              return new X();
            }

        """.trimIndent(),
      ),
    )
  }
}
