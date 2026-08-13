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
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.TypeName
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

    val greeter = ClassSpec.builder("Greeter")
      .addModifiers(Modifier.EXPORT)
      .constructor(
        FunctionSpec.constructorBuilder()
          .addParameter("name", TypeName.STRING, false, Modifier.PRIVATE)
          .build(),
      )
      .addFunction(
        FunctionSpec.builder("greet")
          .returns(TypeName.parameterizedType(observable, TypeName.STRING))
          .addStatement("return %T.of(`Hello \${this.name}`)", observable)
          .build(),
      )
      .build()

    val file = FileSpec.builder("Greeter")
      .addClass(greeter)
      .build()

    val out = StringWriter()
    file.writeTo(out)

    assertThat(
      out.toString(),
      emits(
        """
            import { Observable } from "rxjs/Observable";


            export class Greeter {

              constructor(private name: string) {
              }

              greet(): Observable<string> {
                return Observable.of(`Hello ${'$'}{this.name}`);
              }

            }

        """.trimIndent(),
      ),
    )
  }
}
