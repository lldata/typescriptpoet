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

import dk.lldata.typescriptpoet.SymbolSpec
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SymbolSpec Tests")
class SymbolSpecTests {

  @Test
  @DisplayName("Parsing implicitly defined (non-imported) symbols")
  fun testParsingImplicit() {
    val parsed = SymbolSpec.from("Some.Symbol.Depth")
    assertThat(parsed.value, emits("Some.Symbol.Depth"))
  }

  @Test
  @DisplayName("Parsing named import: exported symbol implied by module path")
  fun testParsingImplicitImportNamed() {
    val parsed = SymbolSpec.from("@rxjs/Observable")
    assertThat(parsed, instanceOf(SymbolSpec.ImportsName::class.java))

    val sym = parsed as SymbolSpec.ImportsName
    assertThat(sym.value, emits("Observable"))
    assertThat(sym.source, emits("rxjs/Observable"))
  }

  @Test
  @DisplayName("Parsing named import: exported symbol implied by generated module path")
  fun testParsingImplicitImportNamedGeneratedModule() {
    val parsed = SymbolSpec.from("@!Api")
    assertThat(parsed, instanceOf(SymbolSpec.ImportsName::class.java))

    val sym = parsed as SymbolSpec.ImportsName
    assertThat(sym.value, emits("Api"))
    assertThat(sym.source, emits("!Api"))
  }

  @Test
  @DisplayName("Parsing named import: exported symbol explicit, source relative to current dir")
  fun testParsingExplicitImportNamedSourceCurrentDirectory() {
    val parsed = SymbolSpec.from("BackendService@./some/local/source/file")
    assertThat(parsed, instanceOf(SymbolSpec.ImportsName::class.java))

    val sym = parsed as SymbolSpec.ImportsName
    assertThat(sym.value, emits("BackendService"))
    assertThat(sym.source, emits("./some/local/source/file"))
  }

  @Test
  @DisplayName("Parsing named import: exported symbol explicit, source relative to parent dir")
  fun testParsingImplicitImportNamedSourceParentDirectory() {
    val parsed = SymbolSpec.from("BackendService@../some/local/source/file")
    assertThat(parsed, instanceOf(SymbolSpec.ImportsName::class.java))

    val sym = parsed as SymbolSpec.ImportsName
    assertThat(sym.value, emits("BackendService"))
    assertThat(sym.source, emits("../some/local/source/file"))
  }

  @Test
  @DisplayName("Parsing named import: exported symbol explicit, source is implied module")
  fun testParsingExplicitImportNamed() {
    val parsed = SymbolSpec.from("SomeOtherSymbolDepth@rxjs/Observable")
    assertThat(parsed, instanceOf(SymbolSpec.ImportsName::class.java))

    val sym = parsed as SymbolSpec.ImportsName
    assertThat(sym.value, emits("SomeOtherSymbolDepth"))
    assertThat(sym.source, emits("rxjs/Observable"))
  }

  @Test
  @DisplayName("Parsing all import: exported symbol implied by module path")
  fun testParsingImplicitImportAll() {
    val parsed = SymbolSpec.from("*rxjs/Observable")
    assertThat(parsed, instanceOf(SymbolSpec.ImportsAll::class.java))

    val sym = parsed as SymbolSpec.ImportsAll
    assertThat(sym.value, emits("Observable"))
    assertThat(sym.source, emits("rxjs/Observable"))
  }

  @Test
  @DisplayName("Parsing all import: exported symbol explicit, source is implied module")
  fun testParsingExplicitImportAll() {
    val parsed = SymbolSpec.from("SomeOther*rxjs/Observable")
    assertThat(parsed, instanceOf(SymbolSpec.ImportsAll::class.java))

    val sym = parsed as SymbolSpec.ImportsAll
    assertThat(sym.value, emits("SomeOther"))
    assertThat(sym.source, emits("rxjs/Observable"))
  }

  @Test
  @DisplayName("Parsing side effect import: exported symbol made available as side effect of import")
  fun testParsingSymbolViaSideEffect() {
    val parsed = SymbolSpec.from("describe+mocha")
    assertThat(parsed, instanceOf(SymbolSpec.SideEffect::class.java))

    val sym = parsed as SymbolSpec.SideEffect
    assertThat(sym.value, emits("describe"))
    assertThat(sym.source, emits("mocha"))
  }

  @Test
  @DisplayName("Parsing augmentation import: exported symbol implied by module path")
  fun testParsingImplicitAugmentationWithAssociatedSymbol() {
    val parsed = SymbolSpec.from("+rxjs/add/operator/toPromise#Observable")
    assertThat(parsed, instanceOf(SymbolSpec.Augmented::class.java))

    val sym = parsed as SymbolSpec.Augmented
    assertThat(sym.value, emits("toPromise"))
    assertThat(sym.source, emits("rxjs/add/operator/toPromise"))
    assertThat(sym.augmented, emits("Observable"))
  }

  @Test
  @DisplayName("Parsing augmentation import: exported symbol explicit")
  fun testParsingExplicitAugmentationWithAssociatedSymbol() {
    val parsed = SymbolSpec.from("SomeSymbol+rxjs/add/operator/toPromise#Observable")
    assertThat(parsed, instanceOf(SymbolSpec.Augmented::class.java))

    val sym = parsed as SymbolSpec.Augmented
    assertThat(sym.value, emits("SomeSymbol"))
    assertThat(sym.source, emits("rxjs/add/operator/toPromise"))
    assertThat(sym.augmented, emits("Observable"))
  }
}
