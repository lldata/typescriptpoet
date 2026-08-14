TypeScriptPoet
==========

[![CI](https://github.com/lldata/typescriptpoet/actions/workflows/ci.yml/badge.svg)](https://github.com/lldata/typescriptpoet/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/dk.lldata/typescriptpoet?label=Maven%20Central)][dl]
[![License](https://img.shields.io/github/license/lldata/typescriptpoet?color=blue)](LICENSE.txt)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-8%2B-437291?logo=openjdk&logoColor=white)](#download)
[![TypeScript](https://img.shields.io/badge/emits-TypeScript%205.x-3178C6?logo=typescript&logoColor=white)](#typescript-support)

`TypeScriptPoet` is a Kotlin and Java API for generating `.ts` source files.

Source file generation can be useful when doing things such as annotation processing or interacting
with metadata files (e.g., database schemas, protocol formats). By generating code, you eliminate
the need to write boilerplate while also keeping a single source of truth for the metadata.


### Example

Here's a `Greeter` file:

```typescript
import {Observable} from 'rxjs/Observable';


export class Greeter {

  constructor(private name: string) {
  }

  greet(): Observable<string> {
    return Observable.of(`Hello ${this.name}`);
  }

}
```

And this is the code to generate it. In Kotlin, through the DSL:

```kotlin
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
```

The names are TypeScript's own — `clazz`, `interfaze`, `type`, `namespace`, `constructor`,
`function`, `export`, `private`, `string` — because the thing being written is TypeScript.
Two are misspelled and three carry a trailing underscore only where TypeScript's word is a
Kotlin keyword: `clazz`, `interfaze`, and `null_`, `object_`, `var_`.

In Java, through the builders:

```java
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
```

The builders are the whole API; the Kotlin DSL is a layer over them and is `@JvmSynthetic`, so
Java sees only the builders and never the DSL.

That example is compiled and asserted by [`ReadmeExampleTests`][readme-test], so it cannot
drift from what the library actually emits.

For everything else, the integration test is the worked reference. The same file is built
three ways, and all three are asserted to emit it byte for byte:
[`KitchenSinkDsl.kt`][sink-dsl] with the Kotlin DSL, [`KitchenSink.kt`][sink-src] with the
Kotlin builders, and [`KitchenSinkJava.java`][sink-java] with the builders from Java.
[`kitchen-sink.ts`][sink-out] is the output, type-checked with the real `tsc` on every build.

**TypeScript 5.0 or newer**, and the same file is type-checked against both 5 and 7 on every
build. 5.0 is the floor because that is what the newest construct here needs — a `const` type
parameter; emit only older constructs and older compilers are fine. Checking 7 as well says
the output has not been left behind, not that you have to be on it: TypeScript 7 is the
native compiler rather than a language release, so it accepts the same file 5 does.

Emitted code tries to do a decent job of looking like it has already been through Prettier
with default settings — double quotes, trailing commas, brace spacing, an 80-column width,
and constructs that break onto several lines when they do not fit. Everything the library
lays out itself is measured against that width and against what the rest of the line still
has to hold: parameter lists, call arguments, object literals, inline object types, unions
and type parameter lists all stay on one line if they fit, and break one entry per line if
they do not.

**That is best effort, not a promise.** Prettier lays out a whole document and reconsiders;
this library decides as it writes, and it does not implement Prettier's heuristics for which
argument to hug or where a break reads best. An unusual shape can still come out differently.
Nor is the width guaranteed everywhere: a statement given as a format string is text the
writer cannot break, so `addStatement("return f(%L, a, b)", …)` can only give way where a
structured value sits — which is what [`CodeBlock.call`][call] exists for, and why reaching
for it is worth it in a call that might not fit.

What to do about the difference is yours to choose, and the library takes no position: exclude
generated files from your formatter, run the output through Prettier, or leave it as it stands.

[call]: src/main/kotlin/dk/lldata/typescriptpoet/CallExpression.kt

The [KDoc][kdoc] catalogs the complete API, which is inspired by [JavaPoet][javapoet]. Each
entry shows the TypeScript it emits.


What's new in 2.0.0
-------------------

2.0.0 is the first release since 2021 and rebuilds what the library can express. The full
list is in the [changelog](CHANGELOG.md); the upgrade path, including every breaking change,
is in [MIGRATING.md](MIGRATING.md).

**There is a Kotlin DSL.** Declarations, members and types are written under the name of the
TypeScript they emit — `clazz`, `interfaze`, `type`, `namespace`, `function`, `constructor`,
`property`, `parameter` — with statements in a `body { }` that accepts only statements. It is
extension functions over the same builders, not a second API: the builder stays in scope inside
every block, and every declaration is `@JvmSynthetic`, so Java sees the builders and never the
DSL. On the kitchen sink it is a third fewer lines of code and three `.build()` calls instead of
ninety-eight.

**The type system caught up with TypeScript 5.** Conditional types with `infer`, mapped types
with `as` remapping and `+`/`-` modifiers, template literal types, `keyof`, `typeof`, indexed
access, the `readonly` array shorthand, labelled and rest tuple elements, variance annotations
and `const` type parameters. Previously `keyof` existed only as a type-parameter bound and the
rest could not be written at all.

**Declarations gained what modern TypeScript uses:** `async`, generators, arrow functions,
overload groups, type predicates and assertion signatures, `override`, `accessor`, `#private`
members, static initializer blocks, `const enum`, definite assignment, and destructuring
parameters.

**Modules gained the rest of the import and export forms:** default imports, `import type` and
`export type`, re-exports, standalone export lists, `export default` and `export =`.

**Expressions are structured, not spelled.** `CodeBlock.objectLiteral` and `CodeBlock.call`
keep an object's members and a call's arguments as lists until the file is written, so both
can be measured and broken. A call written as a format string cannot be: `f(%L, a, b)` has no
argument boundaries in it, so the arguments after the `%L` stay glued to whatever the `%L`
laid out. There is a concise arrow body too — `(x) => x * 2` rather than
`(x) => { return x * 2; }` — and an inline object type whose members can each carry a doc
comment, which is the one thing an interface cannot do without being declared.

**Operand precedence is handled.** A union used under `keyof` or `[]` is parenthesised, so
`keyof (A | B)` means what you asked for rather than silently re-associating.

**It ships lighter.** Guava and kotlin-reflect are gone -- both were declared but never used --
leaving no runtime dependencies beyond the Kotlin standard library.

**Several long-standing bugs are fixed,** including types referenced inside a nested
`CodeBlock` not being imported, a reserved-word list that was Kotlin's rather than
TypeScript's, modifiers emitted in an order `tsc` rejects, and `FileSpec.addProperty` throwing
for every input.


TypeScript support
------------------

TypeScriptPoet covers the TypeScript 5.x surface. A generated file exercising every construct
below is type-checked with the real `tsc` as part of the test suite.

**Types** — unions, intersections, tuples (including labelled, optional and rest elements),
object literal types, `keyof`, `typeof`, indexed access (`T[K]`), the array shorthand (`T[]`),
`readonly` arrays and tuples, conditional types with `infer`, mapped types with `as` remapping
and `+`/`-` modifiers, template literal types, generic function types, construct signatures
including `abstract new`, `unique symbol`, variance annotations (`in`/`out`) and `const` type
parameters.

Operand precedence is handled: a union used under `keyof` or the array shorthand is
parenthesised, so `keyof (A | B)` and `(A | B)[]` mean what you asked for rather than
re-associating.

**Declarations** — classes, interfaces, enums (including `const enum`), type aliases, modules
and namespaces, decorators, `async`, generator functions and methods, arrow functions,
overload groups, `this` parameters, type predicates (`x is Y`) and assertion signatures
(`asserts x is Y`), `override`, `accessor`, `#private` members, static initializer blocks,
index signatures, definite assignment (`!`), and destructuring parameters.

**Modules** — named, namespace, default and side-effect imports, `import type` and
`export type`, re-exports (`export * from`, `export * as ns from`, `export { a, b as c } from`),
standalone export lists, `export default`, and `export =`.


Download
--------

Download [the latest .jar][dl] or depend via Maven:

```xml
<dependency>
  <groupId>dk.lldata</groupId>
  <artifactId>typescriptpoet</artifactId>
  <version>2.0.0-alpha4</version>
</dependency>
```

or Gradle:

```kotlin
implementation("dk.lldata:typescriptpoet:2.0.0-alpha4")
```

The library has no runtime dependencies beyond the Kotlin standard library. It targets Java 8
bytecode and Kotlin 2.0 metadata, so it is usable from JDK 8+ and any Kotlin 2.0+ toolchain.

Upgrading from 1.x? See [MIGRATING.md](MIGRATING.md).


Building
--------

```bash
./gradlew build
```

Requires JDK 17, which the Gradle toolchain provisions automatically.

`build` runs the full gate: tests, ktlint and license headers via Spotless, detekt, public
ABI compatibility, and a coverage floor.


License
-------

    Copyright 2017 Outfox, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


 [readme-test]: src/test/kotlin/dk/lldata/typescriptpoet/test/ReadmeExampleTests.kt
 [sink-src]: src/test/kotlin/dk/lldata/typescriptpoet/test/KitchenSink.kt
 [sink-dsl]: src/test/kotlin/dk/lldata/typescriptpoet/test/KitchenSinkDsl.kt
 [sink-java]: src/test/java/dk/lldata/typescriptpoet/test/KitchenSinkJava.java
 [sink-out]: src/test/resources/kitchen-sink.ts
 [dl]: https://central.sonatype.com/artifact/dk.lldata/typescriptpoet
 [kdoc]: https://lldata.github.io/typescriptpoet/2.0.0/index.html
 [javapoet]: https://github.com/square/javapoet/
