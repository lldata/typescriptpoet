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

import dk.lldata.typescriptpoet.CodeBlock
import dk.lldata.typescriptpoet.CodeWriter
import dk.lldata.typescriptpoet.FileSpec
import dk.lldata.typescriptpoet.FunctionSpec
import dk.lldata.typescriptpoet.TypeName
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.StringWriter

/**
 * Layout is the subject here, so these assert it exactly.
 *
 * The shape under test throughout is the one that made the argument list worth modelling: a
 * call taking a structured first argument and two short ones after it.
 */
@DisplayName("Call Expression Tests")
class CallExpressionTests {

  private fun emit(fn: FunctionSpec): String {
    val out = StringWriter()
    fn.emit(CodeWriter(out), null, setOf())
    return out.toString()
  }

  private val apiRequest = TypeName.namedImport("apiRequest", "./runtime")

  /** `{ method: "POST", path, body }`, the argument that can lay itself out. */
  private fun request(method: String) = CodeBlock.objectLiteral()
    .addProperty("method", "%S", method)
    .addShorthand("path")
    .addShorthand("body")
    .build()

  @Test
  @DisplayName("Keeps a call that fits on one line")
  fun testShortCallInline() {
    val call = CodeBlock.call("send")
      .addArgument("a")
      .addArgument("b")
      .build()

    assertThat(call.toString(), equalTo("send(a, b)"))
  }

  @Test
  @DisplayName("Emits an empty argument list as ()")
  fun testNoArguments() {
    assertThat(CodeBlock.call("reset").build().toString(), equalTo("reset()"))
  }

  @Test
  @DisplayName("Emits type arguments before the argument list")
  fun testTypeArguments() {
    val call = CodeBlock.call("cast")
      .addTypeArgument(TypeName.STRING)
      .addTypeArgument(TypeName.NUMBER)
      .addArgument("value")
      .build()

    assertThat(call.toString(), equalTo("cast<string, number>(value)"))
  }

  @Test
  @DisplayName("Emits a constructor call")
  fun testNewInstance() {
    val call = CodeBlock.newInstance(TypeName.mapType(TypeName.STRING, TypeName.NUMBER))
      .build()

    assertThat(call.toString(), equalTo("new Map<string, number>()"))
  }

  @Test
  @DisplayName("Breaks every argument onto its own line, not just the one that can break itself")
  fun testLongCallBreaksAllArguments() {
    val call = CodeBlock.call(apiRequest)
      .addTypeArgument(TypeName.implicit("CompleteRegistrationResponse"))
      .addArgument(request("POST"))
      .addArgument("config")
      .addArgument("options")
      .build()

    val fn = FunctionSpec.builder("post")
      .addStatement("return %L", call)
      .build()

    assertEmitsExactly(
      emit(fn),
      """
      |function post() {
      |  return apiRequest<CompleteRegistrationResponse>(
      |    { method: "POST", path, body },
      |    config,
      |    options,
      |  );
      |}
      |
      """.trimMargin(),
    )
  }

  @Test
  @DisplayName("Breaks a structured argument again when it does not fit at the deeper indent")
  fun testArgumentBreaksInsideABrokenCall() {
    val query = CodeBlock.objectLiteral()
      .addProperty("latitude", "%L", "args?.latitude")
      .addProperty("longitude", "%L", "args?.longitude")
      .addProperty("radiusInMetres", "%L", "args?.radiusInMetres")
      .build()
    val call = CodeBlock.call(apiRequest)
      .addTypeArgument(TypeName.implicit("LocationReverseOkResponse"))
      .addArgument(
        CodeBlock.objectLiteral()
          .addProperty("method", "%S", "GET")
          .addShorthand("path")
          .addProperty("query", query)
          .build(),
      )
      .addArgument("config")
      .addArgument("options")
      .build()

    val fn = FunctionSpec.builder("get")
      .addStatement("return %L", call)
      .build()

    assertEmitsExactly(
      emit(fn),
      """
      |function get() {
      |  return apiRequest<LocationReverseOkResponse>(
      |    {
      |      method: "GET",
      |      path,
      |      query: {
      |        latitude: args?.latitude,
      |        longitude: args?.longitude,
      |        radiusInMetres: args?.radiusInMetres,
      |      },
      |    },
      |    config,
      |    options,
      |  );
      |}
      |
      """.trimMargin(),
    )
  }

  @Test
  @DisplayName("Breaks a call whose argument spans lines however short it measures")
  fun testMultiLineArgumentForcesTheBreak() {
    val callback = FunctionSpec.builder("cb")
      .arrow()
      .addParameter("e", TypeName.implicit("Event"))
      .addStatement("log(e)")
      .build()
    val call = CodeBlock.call("on")
      .addArgument("%S", "click")
      .addArgument(callback)
      .build()

    val fn = FunctionSpec.builder("listen")
      .addStatement("%L", call)
      .build()

    assertEmitsExactly(
      emit(fn),
      """
      |function listen() {
      |  on(
      |    "click",
      |    (e: Event) => {
      |      log(e);
      |    },
      |  );
      |}
      |
      """.trimMargin(),
    )
  }

  @Test
  @DisplayName("Collects the callee's import")
  fun testCalleeIsImported() {
    val call = CodeBlock.call(apiRequest).addArgument("config").build()
    val file = FileSpec.builder("client")
      .addFunction(FunctionSpec.builder("go").addStatement("return %L", call).build())
      .build()

    val out = StringWriter()
    file.writeTo(out)
    assertThat(out.toString().lines().first(), equalTo("""import { apiRequest } from "./runtime";"""))
  }

  @Test
  @DisplayName("Counts what follows the call when deciding whether it fits")
  fun testTrailingTextCountsTowardsTheWidth() {
    // The call alone is 71 columns at this indent and would fit; `.then(unwrap);` after it
    // takes the line to 85, so the arguments break.
    val call = CodeBlock.call("requestSomethingWithAModeratelyLongName")
      .addArgument("configuration")
      .addArgument("options")
      .build()

    val fn = FunctionSpec.builder("go")
      .addStatement("return %L.then(unwrap)", call)
      .build()

    assertEmitsExactly(
      emit(fn),
      """
      |function go() {
      |  return requestSomethingWithAModeratelyLongName(
      |    configuration,
      |    options,
      |  ).then(unwrap);
      |}
      |
      """.trimMargin(),
    )
  }
}
