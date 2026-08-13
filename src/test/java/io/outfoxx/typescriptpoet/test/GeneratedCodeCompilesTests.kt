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
import io.outfoxx.typescriptpoet.EnumSpec
import io.outfoxx.typescriptpoet.ExportSpec
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.InterfaceSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.ParameterSpec
import io.outfoxx.typescriptpoet.PropertySpec
import io.outfoxx.typescriptpoet.TypeAliasSpec
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.TypeName.Mapped
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

/**
 * Type-checks generated output with the real TypeScript compiler.
 *
 * The rest of the suite asserts exact strings, which proves the emitter is stable but not
 * that what it emits is legal TypeScript. This closes that gap for the constructs added in
 * 2.0.0 -- precedence, modifier order and the newer type syntax are all things a string
 * comparison will happily let you get wrong.
 *
 * Skipped, not failed, when `npx` is unavailable, so the build does not depend on a Node
 * toolchain being present.
 */
@DisplayName("Generated Code Compiles Tests")
class GeneratedCodeCompilesTests {

  @Test
  @DisplayName("tsc accepts a file using every construct added in 2.0.0")
  fun testKitchenSinkTypeChecks(@TempDir dir: Path) {
    assumeTrue(npxAvailable(), "npx is not available; skipping the tsc type-check")

    val source = kitchenSink()
    val file = dir.resolve("kitchen-sink.ts")
    file.writeText(source)

    val result = run(
      listOf(
        "npx", "-y", "-p", "typescript@5", "tsc",
        "--noEmit", "--strict", "--target", "ES2022", "--lib", "ES2022,DOM",
        file.toString(),
      ),
      timeoutSeconds = 300,
    )

    assertThat(
      "tsc rejected the generated source:\n\n$source\n\n${result.second}",
      result.first,
      equalTo(0),
    )
  }

  private fun kitchenSink(): String {
    val t = TypeName.typeVariable("T")
    val k = TypeName.typeVariable("K")
    val person = TypeName.implicit("Person")

    val file = FileSpec.builder("kitchen-sink")

    // An object type to hang the type-level constructs off.
    file.addInterface(
      InterfaceSpec.builder("Person")
        .addModifiers(Modifier.EXPORT)
        .addProperty("name", TypeName.STRING)
        .addProperty("age", TypeName.NUMBER)
        .build(),
    )

    // keyof / indexed access / array shorthand / readonly / labelled tuples.
    file.addTypeAlias(TypeAliasSpec.builder("PersonKey", TypeName.keyOf(person)).build())
    file.addTypeAlias(
      TypeAliasSpec.builder("Name", TypeName.indexedAccess(person, TypeName.implicit("\"name\""))).build(),
    )
    file.addTypeAlias(
      TypeAliasSpec.builder("Names", TypeName.readOnly(TypeName.arrayShorthandType(TypeName.STRING))).build(),
    )
    file.addTypeAlias(
      TypeAliasSpec.builder(
        "Pair",
        TypeName.readOnly(
          TypeName.tupleType(
            listOf(
              TypeName.tupleMember(TypeName.STRING, label = "first"),
              TypeName.tupleMember(TypeName.NUMBER, label = "second", optional = true),
            ),
          ),
        ),
      ).build(),
    )
    // Precedence: a union under keyof and under the array shorthand must be parenthesised.
    file.addTypeAlias(
      TypeAliasSpec.builder(
        "UnionKeys",
        TypeName.keyOf(TypeName.unionType(person, TypeName.implicit("{ extra: boolean }"))),
      ).build(),
    )
    file.addTypeAlias(
      TypeAliasSpec.builder(
        "Mixed",
        TypeName.arrayShorthandType(TypeName.unionType(TypeName.STRING, TypeName.NUMBER)),
      ).build(),
    )

    // Conditional with infer, mapped with `as` remapping and a template literal.
    file.addTypeAlias(
      TypeAliasSpec.builder(
        "Unwrap",
        TypeName.conditionalType(
          t,
          TypeName.parameterizedType(TypeName.ARRAY, TypeName.infer("U")),
          TypeName.typeVariable("U"),
          TypeName.NEVER,
        ),
      ).addTypeVariable(t).build(),
    )
    file.addTypeAlias(
      TypeAliasSpec.builder(
        "Mutable",
        TypeName.mappedType(
          keyName = "K",
          constraint = TypeName.keyOf(t),
          valueType = TypeName.indexedAccess(t, k),
          readonly = Mapped.Change.REMOVE,
        ),
      ).addTypeVariable(t).build(),
    )
    file.addTypeAlias(
      TypeAliasSpec.builder(
        "Getters",
        TypeName.mappedType(
          keyName = "K",
          constraint = TypeName.keyOf(t),
          valueType = TypeName.indexedAccess(t, k),
          asClause = TypeName.templateLiteralType(
            "get",
            TypeName.parameterizedType(
              TypeName.implicit("Capitalize"),
              TypeName.parameterizedType(TypeName.implicit("Extract"), k, TypeName.STRING),
            ),
          ),
        ),
      ).addTypeVariable(t).build(),
    )

    // Generic function type, construct signature, variance and const type parameters.
    file.addTypeAlias(
      TypeAliasSpec.builder(
        "Identity",
        TypeName.genericLambda(
          typeVariables = listOf(TypeName.typeVariable("V")),
          parameters = mapOf("value" to TypeName.typeVariable("V")),
          returnType = TypeName.typeVariable("V"),
        ),
      ).build(),
    )
    file.addTypeAlias(
      TypeAliasSpec.builder(
        "Factory",
        TypeName.constructorType(mapOf("name" to TypeName.STRING), person),
      ).build(),
    )
    file.addTypeAlias(
      TypeAliasSpec.builder(
        "Producer",
        TypeName.lambda(returnType = TypeName.typeVariable("O")),
      ).addTypeVariable(
        TypeName.typeVariable("O", variance = TypeName.TypeVariable.Variance.OUT),
      ).build(),
    )

    // const enum.
    file.addEnum(
      EnumSpec.builder("Direction")
        .addModifiers(Modifier.CONST)
        .addConstant("Up", CodeBlock.of("1"))
        .addConstant("Down", CodeBlock.of("2"))
        .build(),
    )

    // Type predicate, assertion signature, this parameter, async, generators, rest,
    // destructuring, overloads.
    file.addFunction(
      FunctionSpec.builder("isPerson")
        .addParameter("value", TypeName.implicit("unknown"))
        .returnsIs("value", person)
        .addStatement("return typeof value === 'object' && value !== null && 'name' in value")
        .build(),
    )
    file.addFunction(
      FunctionSpec.builder("assertPerson")
        .addParameter("value", TypeName.implicit("unknown"))
        .returnsAsserts("value", person)
        .addStatement("if (!isPerson(value)) throw new Error('not a person')")
        .build(),
    )
    file.addFunction(
      FunctionSpec.builder("describe")
        .thisParameter(person)
        .returns(TypeName.STRING)
        .addStatement("return this.name")
        .build(),
    )
    file.addFunction(
      FunctionSpec.builder("load")
        .addModifiers(Modifier.ASYNC)
        .returns(TypeName.parameterizedType(TypeName.PROMISE, TypeName.STRING))
        .addStatement("return 'loaded'")
        .build(),
    )
    file.addFunction(
      FunctionSpec.builder("counter")
        .generator()
        .returns(TypeName.parameterizedType(TypeName.GENERATOR, TypeName.NUMBER))
        .addStatement("yield 1")
        .build(),
    )
    file.addFunction(
      FunctionSpec.builder("log")
        .restParameter("args", TypeName.arrayShorthandType(TypeName.STRING))
        .addStatement("console.log(...args)")
        .build(),
    )
    file.addFunction(
      FunctionSpec.builder("configure")
        .addParameter(
          ParameterSpec.builder("options", TypeName.implicit("{ host: string; port: number }"))
            .destructure(
              ParameterSpec.objectPattern(
                ParameterSpec.binding("host"),
                ParameterSpec.binding("port", "listenPort"),
              ),
            )
            .build(),
        )
        .returns(TypeName.STRING)
        .addStatement("return `\${host}:\${listenPort}`")
        .build(),
    )
    FunctionSpec.overloads(
      signatures = listOf(
        FunctionSpec.builder("parse").addParameter("value", TypeName.STRING).returns(TypeName.STRING).build(),
        FunctionSpec.builder("parse").addParameter("value", TypeName.NUMBER).returns(TypeName.NUMBER).build(),
      ),
      implementation = FunctionSpec.builder("parse")
        .addParameter("value", TypeName.implicit("string | number"))
        .returns(TypeName.implicit("string | number"))
        .addStatement("return value")
        .build(),
    ).forEach { file.addFunction(it) }

    // Declared ahead of Registry, whose static block assigns it.
    file.addProperty(
      PropertySpec.builder("registered", TypeName.BOOLEAN)
        .addModifiers(Modifier.LET)
        .initializer("false")
        .build(),
    )

    // Class members: #private, definite assignment, static block, index signature,
    // accessor, override, generator method, arrow property.
    file.addClass(
      ClassSpec.builder("Base")
        .addFunction(
          FunctionSpec.builder("greet").returns(TypeName.STRING).addStatement("return 'hi'").build(),
        )
        .build(),
    )
    file.addClass(
      ClassSpec.builder("Registry")
        .superClass(TypeName.implicit("Base"))
        .addProperty(PropertySpec.builder("#secret", TypeName.STRING).initializer("'s'").build())
        .addProperty(PropertySpec.builder("later", TypeName.STRING).definiteAssignment().build())
        .addProperty(
          PropertySpec.builder("count", TypeName.NUMBER)
            .addModifiers(Modifier.ACCESSOR)
            .initializer("0")
            .build(),
        )
        .addProperty(
          PropertySpec.builder(
            "handler",
            TypeName.lambda("event" to TypeName.STRING, returnType = TypeName.VOID),
          ).initializer(
            "%L",
            FunctionSpec.builder("handler")
              .arrow()
              .addParameter("event", TypeName.STRING)
              .returns(TypeName.VOID)
              .addStatement("console.log(event)")
              .build(),
          ).build(),
        )
        .addIndexable(
          FunctionSpec.indexableBuilder()
            .addParameter("key", TypeName.STRING)
            .returns(TypeName.implicit("unknown"))
            .build(),
        )
        .addStaticBlock("registered = true;\n")
        .addFunction(
          FunctionSpec.builder("greet")
            .addModifiers(Modifier.OVERRIDE)
            .returns(TypeName.STRING)
            .addStatement("return this.#secret")
            .build(),
        )
        .addFunction(
          FunctionSpec.builder("items")
            .generator()
            .returns(TypeName.parameterizedType(TypeName.GENERATOR, TypeName.NUMBER))
            .addStatement("yield 1")
            .build(),
        )
        .build(),
    )

    file.addExport(ExportSpec.named(listOf(ExportSpec.name("Registry", "Reg"))))

    val out = StringWriter()
    file.build().writeTo(out)
    return out.toString()
  }

  private fun npxAvailable(): Boolean =
    runCatching { run(listOf("npx", "--version"), timeoutSeconds = 60).first == 0 }.getOrDefault(false)

  private fun run(command: List<String>, timeoutSeconds: Long): Pair<Int, String> {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      return -1 to "timed out after ${timeoutSeconds}s"
    }
    return process.exitValue() to output
  }
}
