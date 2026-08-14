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
package dk.lldata.typescriptpoet.test;

import dk.lldata.typescriptpoet.ClassSpec;
import dk.lldata.typescriptpoet.FileSpec;
import dk.lldata.typescriptpoet.FunctionSpec;
import dk.lldata.typescriptpoet.Modifier;
import dk.lldata.typescriptpoet.TypeName;

import java.io.IOException;
import java.io.StringWriter;

/** The Java half of the README example, kept honest by {@code ReadmeExampleTests}. */
final class ReadmeExampleJava {

  private ReadmeExampleJava() {
  }

  static String emit() throws IOException {
    // Declared as Standard, not TypeName: parameterizedType needs the precise type,
    // which Kotlin infers and Java must be told.
    TypeName.Standard observable = TypeName.standard("@rxjs/Observable");

    FileSpec file = FileSpec.builder("Greeter")
        .addClass(
            ClassSpec.builder("Greeter")
                .addModifiers(Modifier.EXPORT)
                .constructor(
                    FunctionSpec.constructorBuilder()
                        .addParameter("name", TypeName.STRING, false, Modifier.PRIVATE)
                        .build())
                .addFunction(
                    FunctionSpec.builder("greet")
                        .returns(TypeName.parameterizedType(observable, TypeName.STRING))
                        .addStatement("return %T.of(`Hello ${this.name}`)", observable)
                        .build())
                .build())
        .build();

    StringWriter out = new StringWriter();
    file.writeTo(out);
    return out.toString();
  }
}
