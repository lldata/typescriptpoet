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
import dk.lldata.typescriptpoet.FileSpec
import dk.lldata.typescriptpoet.FunctionSpec
import dk.lldata.typescriptpoet.TypeName
import dk.lldata.typescriptpoet.dsl.body
import dk.lldata.typescriptpoet.dsl.clazz
import dk.lldata.typescriptpoet.dsl.constructor
import dk.lldata.typescriptpoet.dsl.export
import dk.lldata.typescriptpoet.dsl.file
import dk.lldata.typescriptpoet.dsl.function
import dk.lldata.typescriptpoet.dsl.parameter
import dk.lldata.typescriptpoet.dsl.private
import dk.lldata.typescriptpoet.dsl.string
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.StringWriter

/**
 * The example in README.md, kept honest.
 *
 * The 1.x README drifted: its sample called `TypeName.importedType(...)`, which had been
 * renamed to `namedImport`, so the headline example did not compile against the library it
 * documented. Asserting it here means that cannot happen again silently.
 */
@DisplayName("README Example Tests")
class ReadmeExampleTests {

  @Test
  @DisplayName("The README example generates what the README says it does")
  fun testReadmeExample() {
    val observable = TypeName.standard("@rxjs/Observable")

    val file = file("Greeter") {
      clazz("Greeter", export) {
        constructor {
          parameter("name", string, private)
        }
        function("greet") {
          returns(TypeName.parameterizedType(observable, string))
          body {
            statement("return %T.of(`Hello \${this.name}`)", observable)
          }
        }
      }
    }

    val out = StringWriter()
    file.writeTo(out)

    assertThat(out.toString(), emits(EXPECTED))
  }

  @Test
  @DisplayName("The Java README example generates the same file")
  fun testReadmeExampleJava() {
    assertThat(ReadmeExampleJava.emit(), emits(EXPECTED))
  }

  private companion object {
    val EXPECTED = """
        import { Observable } from "rxjs/Observable";


        export class Greeter {

          constructor(private name: string) {}

          greet(): Observable<string> {
            return Observable.of(`Hello ${'$'}{this.name}`);
          }

        }

    """.trimIndent()
  }
}
