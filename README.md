TypeScriptPoet
==========

[![CI](https://github.com/lldata/typescriptpoet/actions/workflows/ci.yml/badge.svg)](https://github.com/lldata/typescriptpoet/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.outfoxx/typescriptpoet?label=Maven%20Central)][dl]
[![License](https://img.shields.io/github/license/outfoxx/typescriptpoet?color=blue)](LICENSE.txt)
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

And this is the code to generate it with TypeScriptPoet:

```kotlin
val observable = TypeName.standard("@rxjs/Observable")

val greeter = ClassSpec.builder("Greeter")
  .addModifiers(Modifier.EXPORT)
  .constructor(
    FunctionSpec.constructorBuilder()
      .addParameter("name", TypeName.STRING, false, Modifier.PRIVATE)
      .build()
  )
  .addFunction(
    FunctionSpec.builder("greet")
      .returns(TypeName.parameterizedType(observable, TypeName.STRING))
      .addStatement("return %T.of(`Hello \${this.name}`)", observable)
      .build()
  )
  .build()

val file = FileSpec.builder("Greeter")
  .addClass(greeter)
  .build()

val out = StringWriter()
file.writeTo(out)
```

The [KDoc][kdoc] catalogs the complete TypeScriptPoet API, which is inspired by [JavaPoet][javapoet].


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
  <groupId>io.outfoxx</groupId>
  <artifactId>typescriptpoet</artifactId>
  <version>2.0.0</version>
</dependency>
```

or Gradle:

```kotlin
implementation("io.outfoxx:typescriptpoet:2.0.0")
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


 [dl]: https://central.sonatype.com/artifact/io.outfoxx/typescriptpoet
 [kdoc]: https://outfoxx.github.io/typescriptpoet/2.0.0/index.html
 [javapoet]: https://github.com/square/javapoet/
