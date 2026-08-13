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
import io.outfoxx.typescriptpoet.ExportSpec
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.PropertySpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.StringWriter

@DisplayName("Module Export Tests")
class ModuleExportTests {

  private fun emit(file: FileSpec): String {
    val out = StringWriter()
    file.writeTo(out)
    return out.toString()
  }

  @Test
  @DisplayName("Generates a default import")
  fun testDefaultImport() {
    val file = FileSpec.builder("Test")
      .addProperty(
        PropertySpec.builder("engine", TypeName.defaultImport("Engine", "templates"))
          .addModifiers(Modifier.CONST)
          .build(),
      )
      .build()

    assertThat(emit(file), emits("import Engine from \"templates\";\n\n\nconst engine: Engine;\n"))
  }

  @Test
  @DisplayName("Parses a default import from the symbol mini-DSL")
  fun testDefaultImportFromSpec() {
    val symbol = SymbolSpec.from("Engine=templates")

    assertThat(symbol is SymbolSpec.ImportsDefault, equalTo(true))
    assertThat((symbol as SymbolSpec.ImportsDefault).source, emits("templates"))
  }

  @Test
  @DisplayName("Generates a type-only default import")
  fun testTypeOnlyDefaultImport() {
    val file = FileSpec.builder("Test")
      .addProperty(
        PropertySpec.builder("engine", TypeName.defaultImport("Engine", "templates", typeOnly = true))
          .addModifiers(Modifier.CONST)
          .build(),
      )
      .build()

    assertThat(
      emit(file),
      emits("import type Engine from \"templates\";\n\n\nconst engine: Engine;\n"),
    )
  }

  @Test
  @DisplayName("Uses the inline type form for a type-only named import")
  fun testTypeOnlyNamedImport() {
    // Value and type imports from one module stay on a single statement.
    val file = FileSpec.builder("Test")
      .addProperty(
        PropertySpec.builder("a", TypeName.namedImport("Alpha", "lib", typeOnly = true))
          .addModifiers(Modifier.CONST)
          .build(),
      )
      .addProperty(
        PropertySpec.builder("b", TypeName.namedImport("Beta", "lib"))
          .addModifiers(Modifier.CONST)
          .build(),
      )
      .build()

    assertThat(
      emit(file),
      equalTo(
        "import { Beta, type Alpha } from \"lib\";\n\nconst a: Alpha;\n\nconst b: Beta;\n",
      ),
    )
  }

  @Test
  @DisplayName("Generates re-exports")
  fun testReExports() {
    val file = FileSpec.builder("Test")
      .addExport(ExportSpec.all("./alpha"))
      .addExport(ExportSpec.allAs("beta", "./beta"))
      .addExport(
        ExportSpec.named(
          listOf(ExportSpec.name("Gamma"), ExportSpec.name("Delta", "Renamed")),
          from = "./gamma",
        ),
      )
      .addExport(ExportSpec.named(listOf(ExportSpec.name("Epsilon")), from = "./eps", typeOnly = true))
      .build()

    assertThat(
      emit(file),
      emits(
        """

            export * from "./alpha";

            export * as beta from "./beta";

            export { Gamma, Delta as Renamed } from "./gamma";

            export type { Epsilon } from "./eps";

        """.trimIndent(),
      ),
    )
  }

  @Test
  @DisplayName("Generates a standalone export list")
  fun testExportList() {
    val file = FileSpec.builder("Test")
      .addExport(ExportSpec.named(listOf(ExportSpec.name("a"), ExportSpec.name("b", "c"))))
      .build()

    assertThat(emit(file), emits("\nexport { a, b as c };\n"))
  }

  @Test
  @DisplayName("Generates export default for an expression")
  fun testExportDefaultExpression() {
    val file = FileSpec.builder("Test")
      .addExport(ExportSpec.default("createEngine()"))
      .build()

    assertThat(emit(file), emits("\nexport default createEngine();\n"))
  }

  @Test
  @DisplayName("Generates export default on a declaration")
  fun testExportDefaultDeclaration() {
    val file = FileSpec.builder("Test")
      .addClass(
        ClassSpec.builder("Engine")
          .addModifiers(Modifier.EXPORT, Modifier.DEFAULT)
          .build(),
      )
      .build()

    assertThat(emit(file), emits("\nexport default class Engine {}\n"))
  }

  @Test
  @DisplayName("Generates export =")
  fun testExportEquals() {
    val file = FileSpec.builder("Test")
      .addExport(ExportSpec.exportEquals("Engine"))
      .build()

    assertThat(emit(file), emits("\nexport = Engine;\n"))
  }

  @Test
  @DisplayName("Rejects export = alongside any other export")
  fun testExportEqualsIsExclusive() {
    val error = runCatching {
      FileSpec.builder("Test")
        .addExport(ExportSpec.all("./alpha"))
        .addExport(ExportSpec.exportEquals("Engine"))
    }.exceptionOrNull()

    assertThat(error is IllegalArgumentException, equalTo(true))
  }

  @Test
  @DisplayName("Rejects a second default export")
  fun testSingleDefaultExport() {
    val error = runCatching {
      FileSpec.builder("Test")
        .addExport(ExportSpec.default("a"))
        .addExport(ExportSpec.default("b"))
    }.exceptionOrNull()

    assertThat(error is IllegalArgumentException, equalTo(true))
  }
}
