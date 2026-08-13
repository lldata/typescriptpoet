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
package io.outfoxx.typescriptpoet.test;

import io.outfoxx.typescriptpoet.ClassSpec;
import io.outfoxx.typescriptpoet.ExportSpec;
import io.outfoxx.typescriptpoet.FileSpec;
import io.outfoxx.typescriptpoet.FunctionSpec;
import io.outfoxx.typescriptpoet.Modifier;
import io.outfoxx.typescriptpoet.PropertySpec;
import io.outfoxx.typescriptpoet.TypeAliasSpec;
import io.outfoxx.typescriptpoet.TypeName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The README calls this "a Kotlin and Java API", and until now nothing verified the Java half.
 *
 * <p>This is written deliberately in plain Java: every call here must work without touching
 * {@code Companion}, without named or default arguments, and without Kotlin-only syntax. If a
 * factory loses its {@code @JvmStatic} or a defaulted parameter loses its
 * {@code @JvmOverloads}, this stops compiling.
 */
@DisplayName("Java Interop Test")
class JavaInteropTest {

  @Test
  @DisplayName("The 2.0 API is usable from Java without Companion")
  void testApiIsUsableFromJava() throws IOException {
    // @JvmField constants, reached as plain static fields rather than getSTRING().
    TypeName string = TypeName.STRING;
    TypeName number = TypeName.NUMBER;

    TypeName person = TypeName.implicit("Person");
    TypeName.TypeVariable t = TypeName.typeVariable("T");
    TypeName.TypeVariable k = TypeName.typeVariable("K");

    // Type operators.
    TypeName keys = TypeName.keyOf(person);
    TypeName access = TypeName.indexedAccess(person, k);
    TypeName names = TypeName.readOnly(TypeName.arrayShorthandType(string));

    // Tuple members, using the @JvmOverloads ladder rather than named arguments.
    TypeName pair = TypeName.tupleType(
      Arrays.asList(
        TypeName.tupleMember(string, "first"),
        TypeName.tupleMember(number, "second", true)
      )
    );

    // Computed types.
    TypeName unwrap = TypeName.conditionalType(
      t, TypeName.arrayShorthandType(TypeName.infer("U")), TypeName.typeVariable("U"), TypeName.NEVER
    );
    TypeName mutable = TypeName.mappedType("K", keys, access);
    TypeName template = TypeName.templateLiteralType("get", k);

    // Overload groups.
    List<FunctionSpec> overloads = FunctionSpec.overloads(
      Arrays.asList(
        FunctionSpec.builder("parse").addParameter("value", string).returns(string).build()
      ),
      FunctionSpec.builder("parse")
        .addParameter("value", string)
        .returns(string)
        .addStatement("return value")
        .build()
    );

    ClassSpec widget = ClassSpec.builder("Widget")
      .addModifiers(Modifier.EXPORT)
      .addProperty(PropertySpec.builder("id", string).definiteAssignment().build())
      .addFunctions(overloads)
      .build();

    FileSpec file = FileSpec.builder("widget")
      .addTypeAlias(TypeAliasSpec.builder("Keys", keys).build())
      .addTypeAlias(TypeAliasSpec.builder("Names", names).build())
      .addTypeAlias(TypeAliasSpec.builder("Pair", pair).build())
      .addTypeAlias(TypeAliasSpec.builder("Unwrap", unwrap).addTypeVariable(t).build())
      .addTypeAlias(TypeAliasSpec.builder("Mutable", mutable).addTypeVariable(t).build())
      .addTypeAlias(TypeAliasSpec.builder("Template", template).addTypeVariable(k).build())
      .addClass(widget)
      .addExport(ExportSpec.named(Arrays.asList(ExportSpec.name("Widget", "Gadget"))))
      .build();

    StringWriter out = new StringWriter();
    file.writeTo(out);

    assertThat(
      out.toString(),
      equalTo(
        "\n" +
        "type Keys = keyof Person;\n" +
        "\n" +
        "type Names = readonly string[];\n" +
        "\n" +
        "type Pair = [first: string, second?: number];\n" +
        "\n" +
        "type Unwrap<T> = T extends (infer U)[] ? U : never;\n" +
        "\n" +
        "type Mutable<T> = { [K in keyof Person]: Person[K] };\n" +
        "\n" +
        "type Template<K> = `get${K}`;\n" +
        "\n" +
        "export class Widget {\n" +
        "\n" +
        "  id!: string;\n" +
        "\n" +
        "  parse(value: string): string;\n" +
        "\n" +
        "  parse(value: string): string {\n" +
        "    return value;\n" +
        "  }\n" +
        "\n" +
        "}\n" +
        "\n" +
        "export {Widget as Gadget};\n"
      )
    );
  }
}
