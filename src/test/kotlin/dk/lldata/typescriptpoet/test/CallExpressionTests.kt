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
import dk.lldata.typescriptpoet.ObjectLiteral
import dk.lldata.typescriptpoet.ParameterSpec
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

  /** The arrow a leaf of a generated client is made of: `post: (…) => apiRequest(…)`. */
  private fun leaf(returns: TypeName, request: ObjectLiteral, vararg parameters: ParameterSpec) =
    FunctionSpec.builder("post")
      .arrow()
      .apply { parameters.forEach { addParameter(it) } }
      .returns(TypeName.promiseType(returns))
      .expressionBody(
        "%L",
        CodeBlock.call(apiRequest)
          .addTypeArgument(returns)
          .addArgument(request)
          .addArgument("config")
          .addArgument("options")
          .build(),
      )
      .build()

  @Test
  @DisplayName("Moves a body that does not fit onto its own line, leaving the call intact")
  fun testExpressionBodyBreaksAfterTheArrow() {
    val obj = CodeBlock.objectLiteral()
      .addProperty(
        "post",
        leaf(
          TypeName.VOID,
          CodeBlock.objectLiteral().addProperty("method", "%S", "POST").addShorthand("path").build(),
          ParameterSpec.builder("options", TypeName.namedImport("RequestOptions", "./runtime"), true).build(),
        ),
      )
      .build()

    val fn = FunctionSpec.builder("node").addStatement("return %L", obj).build()

    // The call fits once it has a line of its own, so it stays whole rather than exploding.
    assertEmitsExactly(
      emit(fn),
      """
      |function node() {
      |  return {
      |    post: (options?: RequestOptions): Promise<void> =>
      |      apiRequest<void>({ method: "POST", path }, config, options),
      |  };
      |}
      |
      """.trimMargin(),
    )
  }

  @Test
  @DisplayName("Breaks the call too when its own line is still not enough")
  fun testExpressionBodyBreaksAgainWhenItStillDoesNotFit() {
    val obj = CodeBlock.objectLiteral()
      .addProperty(
        "post",
        leaf(
          TypeName.implicit("CompleteRegistrationResponse"),
          CodeBlock.objectLiteral()
            .addProperty("method", "%S", "POST")
            .addShorthand("path")
            .addShorthand("body")
            .build(),
          ParameterSpec.builder("body", TypeName.implicit("CompleteRegistrationRequest"), false).build(),
          ParameterSpec.builder("options", TypeName.namedImport("RequestOptions", "./runtime"), true).build(),
        ),
      )
      .build()

    val fn = FunctionSpec.builder("node").addStatement("return %L", obj).build()

    assertEmitsExactly(
      emit(fn),
      """
      |function node() {
      |  return {
      |    post: (
      |      body: CompleteRegistrationRequest,
      |      options?: RequestOptions,
      |    ): Promise<CompleteRegistrationResponse> =>
      |      apiRequest<CompleteRegistrationResponse>(
      |        { method: "POST", path, body },
      |        config,
      |        options,
      |      ),
      |  };
      |}
      |
      """.trimMargin(),
    )
  }

  @Test
  @DisplayName("An object literal body hugs the arrow rather than moving down")
  fun testObjectLiteralBodyHugs() {
    val arrow = FunctionSpec.builder("make")
      .arrow()
      .addParameter("identifier", TypeName.STRING)
      .addParameter("displayName", TypeName.STRING)
      .expressionBody(
        "%L",
        CodeBlock.objectLiteral()
          .addShorthand("identifier")
          .addShorthand("displayName")
          .addProperty("createdAt", "%L", "Date.now()")
          .build(),
      )
      .build()

    val fn = FunctionSpec.builder("factory").addStatement("return %L", arrow).build()

    assertEmitsExactly(
      emit(fn),
      """
      |function factory() {
      |  return (identifier: string, displayName: string) => ({
      |    identifier,
      |    displayName,
      |    createdAt: Date.now(),
      |  });
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
