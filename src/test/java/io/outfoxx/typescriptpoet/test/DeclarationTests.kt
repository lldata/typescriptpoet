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
import io.outfoxx.typescriptpoet.EnumSpec
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.ParameterSpec
import io.outfoxx.typescriptpoet.PropertySpec
import io.outfoxx.typescriptpoet.TypeName
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.StringWriter

@DisplayName("Declaration Tests")
class DeclarationTests {

  private fun emit(fn: FunctionSpec): String {
    val out = StringWriter()
    fn.emit(CodeWriter(out), null, setOf())
    return out.toString()
  }

  private fun emit(cls: ClassSpec): String {
    val out = StringWriter()
    cls.emit(CodeWriter(out))
    return out.toString()
  }

  @Test
  @DisplayName("Generates an async function")
  fun testAsync() {
    val fn = FunctionSpec.builder("load")
      .addModifiers(Modifier.ASYNC)
      .returns(TypeName.parameterizedType(TypeName.PROMISE, TypeName.STRING))
      .build()

    assertThat(emit(fn), equalTo("async function load(): Promise<string> {\n}\n"))
  }

  @Test
  @DisplayName("Generates a generator function")
  fun testGenerator() {
    val fn = FunctionSpec.builder("items")
      .generator()
      .build()

    assertThat(emit(fn), equalTo("function* items() {\n}\n"))
  }

  @Test
  @DisplayName("Generates an async generator function")
  fun testAsyncGenerator() {
    val fn = FunctionSpec.builder("items")
      .addModifiers(Modifier.ASYNC)
      .generator()
      .build()

    assertThat(emit(fn), equalTo("async function* items() {\n}\n"))
  }

  @Test
  @DisplayName("Generates a generator method with the star on the name")
  fun testGeneratorMethod() {
    val testClass = ClassSpec.builder("Test")
      .addFunction(FunctionSpec.builder("items").generator().build())
      .build()

    assertThat(
      emit(testClass),
      equalTo("class Test {\n\n  *items() {\n  }\n\n}\n"),
    )
  }

  @Test
  @DisplayName("Generates a type predicate return type")
  fun testTypePredicate() {
    val fn = FunctionSpec.builder("isString")
      .addParameter("value", TypeName.implicit("unknown"))
      .returnsIs("value", TypeName.STRING)
      .build()

    assertThat(emit(fn), equalTo("function isString(value: unknown): value is string {\n}\n"))
  }

  @Test
  @DisplayName("Generates assertion signatures")
  fun testAssertionSignatures() {
    val withType = FunctionSpec.builder("assertString")
      .addParameter("value", TypeName.implicit("unknown"))
      .returnsAsserts("value", TypeName.STRING)
      .build()

    assertThat(
      emit(withType),
      equalTo("function assertString(value: unknown): asserts value is string {\n}\n"),
    )

    val bare = FunctionSpec.builder("assertDefined")
      .addParameter("value", TypeName.implicit("unknown"))
      .returnsAsserts("value")
      .build()

    assertThat(
      emit(bare),
      equalTo("function assertDefined(value: unknown): asserts value {\n}\n"),
    )
  }

  @Test
  @DisplayName("Generates a this parameter")
  fun testThisParameter() {
    val fn = FunctionSpec.builder("handle")
      .thisParameter(TypeName.implicit("Window"))
      .addParameter("event", TypeName.implicit("Event"))
      .build()

    assertThat(emit(fn), equalTo("function handle(this: Window, event: Event) {\n}\n"))
  }

  @Test
  @DisplayName("Generates override and accessor modifiers")
  fun testOverrideAndAccessor() {
    val testClass = ClassSpec.builder("Test")
      .addProperty(
        PropertySpec.builder("value", TypeName.NUMBER)
          .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.OVERRIDE, Modifier.ACCESSOR)
          .build(),
      )
      .build()

    assertThat(
      emit(testClass),
      equalTo("class Test {\n\n  static override accessor value: number;\n\n}\n"),
    )
  }

  @Test
  @DisplayName("Generates a private #field")
  fun testPrivateField() {
    val testClass = ClassSpec.builder("Test")
      .addProperty(PropertySpec.builder("#secret", TypeName.STRING).build())
      .build()

    assertThat(emit(testClass), equalTo("class Test {\n\n  #secret: string;\n\n}\n"))
  }

  @Test
  @DisplayName("Rejects a #field that also carries Modifier.PRIVATE")
  fun testPrivateFieldRejectsRedundantModifier() {
    val error = runCatching {
      PropertySpec.builder("#secret", TypeName.STRING).addModifiers(Modifier.PRIVATE).build()
    }.exceptionOrNull()

    assertThat(error is IllegalArgumentException, equalTo(true))
  }

  @Test
  @DisplayName("Generates a definite assignment assertion")
  fun testDefiniteAssignment() {
    val testClass = ClassSpec.builder("Test")
      .addProperty(
        PropertySpec.builder("value", TypeName.STRING).definiteAssignment().build(),
      )
      .build()

    assertThat(emit(testClass), equalTo("class Test {\n\n  value!: string;\n\n}\n"))
  }

  @Test
  @DisplayName("Rejects a property that is both optional and definitely assigned")
  fun testOptionalAndDefiniteAreExclusive() {
    val error = runCatching {
      PropertySpec.builder("value", TypeName.STRING, optional = true)
        .definiteAssignment()
        .build()
    }.exceptionOrNull()

    assertThat(error is IllegalArgumentException, equalTo(true))
  }

  @Test
  @DisplayName("Generates a static initializer block")
  fun testStaticBlock() {
    val testClass = ClassSpec.builder("Test")
      .addStaticBlock("registry.add(Test);\n")
      .build()

    assertThat(
      emit(testClass),
      equalTo("class Test {\n\n  static {\n    registry.add(Test);\n  }\n\n}\n"),
    )
  }

  @Test
  @DisplayName("Generates a const enum")
  fun testConstEnum() {
    val testEnum = EnumSpec.builder("Direction")
      .addModifiers(Modifier.EXPORT, Modifier.CONST)
      .addConstant("Up")
      .addConstant("Down")
      .build()

    val out = StringWriter()
    testEnum.emit(CodeWriter(out))

    assertThat(
      out.toString(),
      equalTo("export const enum Direction {\n  Up,\n  Down\n}\n"),
    )
  }

  @Test
  @DisplayName("Generates an overload group")
  fun testOverloads() {
    val overloads = FunctionSpec.overloads(
      signatures = listOf(
        FunctionSpec.builder("parse")
          .addParameter("value", TypeName.STRING)
          .returns(TypeName.STRING)
          .build(),
        FunctionSpec.builder("parse")
          .addParameter("value", TypeName.NUMBER)
          .returns(TypeName.NUMBER)
          .build(),
      ),
      implementation = FunctionSpec.builder("parse")
        .addParameter("value", TypeName.ANY)
        .returns(TypeName.ANY)
        .addStatement("return value")
        .build(),
    )

    val testClass = ClassSpec.builder("Test").addFunctions(overloads).build()

    assertThat(
      emit(testClass),
      equalTo(
        """
            class Test {

              parse(value: string): string;

              parse(value: number): number;

              parse(value: any): any {
                return value;
              }

            }

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Rejects an overload group whose signatures disagree on the name")
  fun testOverloadsRejectMismatchedNames() {
    val error = runCatching {
      FunctionSpec.overloads(
        signatures = listOf(FunctionSpec.builder("parse").build()),
        implementation = FunctionSpec.builder("format").build(),
      )
    }.exceptionOrNull()

    assertThat(error is IllegalArgumentException, equalTo(true))
  }

  @Test
  @DisplayName("Generates a destructured object parameter")
  fun testObjectDestructuring() {
    val fn = FunctionSpec.builder("configure")
      .addParameter(
        ParameterSpec.builder("options", TypeName.implicit("Options"))
          .destructure(
            ParameterSpec.objectPattern(
              ParameterSpec.binding("host"),
              ParameterSpec.binding("port", "listenPort"),
            ),
          )
          .build(),
      )
      .build()

    assertThat(
      emit(fn),
      equalTo("function configure({ host, port: listenPort }: Options) {\n}\n"),
    )
  }

  @Test
  @DisplayName("Generates a destructured array parameter")
  fun testArrayDestructuring() {
    val fn = FunctionSpec.builder("swap")
      .addParameter(
        ParameterSpec.builder("pair", TypeName.tupleType(TypeName.STRING, TypeName.STRING))
          .destructure(ParameterSpec.arrayPattern("first", "second"))
          .build(),
      )
      .build()

    assertThat(
      emit(fn),
      equalTo("function swap([first, second]: [string, string]) {\n}\n"),
    )
  }

  @Test
  @DisplayName("Generates a rest parameter without a space after the ellipsis")
  fun testRestParameterSpacing() {
    val fn = FunctionSpec.builder("log")
      .restParameter("args", TypeName.arrayShorthandType(TypeName.ANY))
      .build()

    assertThat(emit(fn), equalTo("function log(...args: any[]) {\n}\n"))
  }

  @Test
  @DisplayName("Generates an arrow function as a property initializer")
  fun testArrowFunction() {
    val arrow = FunctionSpec.builder("handler")
      .arrow()
      .addParameter("event", TypeName.implicit("Event"))
      .returns(TypeName.VOID)
      .addStatement("console.log(event)")
      .build()

    val testClass = ClassSpec.builder("Test")
      .addProperty(
        PropertySpec.builder(
          "handler",
          TypeName.lambda("event" to TypeName.implicit("Event"), returnType = TypeName.VOID),
        )
          .initializer("%L", arrow)
          .build(),
      )
      .build()

    assertThat(
      emit(testClass),
      equalTo(
        """
            class Test {

              handler: (event: Event) => void = (event: Event): void => {
                console.log(event);
              };

            }

        """.trimIndent(),
      ),
    )
  }
}
