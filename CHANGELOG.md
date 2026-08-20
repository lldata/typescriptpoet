Changelog
=========

All notable changes to this project are documented here. This project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [Unreleased] — 2.0.0

The first release since 2021. See [MIGRATING.md](MIGRATING.md) for the upgrade path.

### Added

**Type system**
- `keyof`, `typeof` and the `readonly` type operator, composable anywhere a type is accepted.
  `keyof` previously existed only as a type-parameter bound modifier.
- Indexed access types (`T[K]`) and the array shorthand (`T[]`), the latter distinct from
  `arrayType()`'s `Array<T>` because only the shorthand can carry `readonly`.
- Conditional types with `infer`, mapped types with `as` key remapping and `+`/`-`
  `readonly`/`?` modifiers, and template literal types.
- Labelled, optional and rest tuple elements.
- `CodeBlock.objectLiteral()`, building an object literal expression whose members stay
  structured until the file is written, so the layout is decided against the print width
  rather than hand-formatted into a `CodeBlock`. Members are properties, shorthand, getters, or
  spreads — `addGetter` is what a lazily-reached value needs, since a property is computed
  where the object is built rather than where it is read.
  ([#9](https://github.com/lldata/typescriptpoet/issues/9))
- `ObjectLiteral.Builder.addSpread()`, spelling `{ ...value }` through the same structured
  machinery as every other member. Before this, the only way to write a spread at all was to
  smuggle it through `addShorthand`'s bare `String`, which renders whatever was passed through
  `toString()` before the writer ever sees it — a `TypeName` given that way loses its import
  the same way #10 described for other members, so a spread of an imported symbol emitted a
  name nothing had imported.
  ([#45](https://github.com/lldata/typescriptpoet/issues/45))
- `CodeBlock.call()` and `CodeBlock.newInstance()`, building a call expression whose argument
  list stays structured until the file is written. A call spelled as a format string —
  `addStatement("return %T(%L, config, options)", …)` — has no argument boundaries in it, so
  the writer could not break it: the one argument that could lay itself out did, and the rest
  stayed glued to its closing brace. Kept as a list, the arguments break together, one per
  line, as a parameter list does.
  ([#13](https://github.com/lldata/typescriptpoet/issues/13))
- `FunctionSpec.Builder.expressionBody()`, giving an arrow a concise body: `(x) => x * 2`
  rather than `(x) => { return x * 2; }`. An object literal is parenthesised, since `=> {`
  would otherwise open a block.
- `TypeName.promiseType()`, alongside `arrayType`/`setType`/`mapType`/`recordType`.
  `PROMISE.parameterized(t)` said the same thing but was not where anyone looked.
- `TypeName.anonymousMember()`, so a member of an inline object type can be optional, `readonly`,
  and carry a TSDoc comment of its own. The comment is the reason to use an inline type rather
  than an interface: it says what omitting the member means, which optionality alone does not.
  Without `readonly`, that TSDoc-carrying form was a strictly weaker way to spell a shape an
  interface property could already mark read-only.
  ([#16](https://github.com/lldata/typescriptpoet/issues/16))
- `ObjectLiteral.Builder.addProperty()` overloads taking a `FunctionSpec`, `ObjectLiteral` or
  `CallExpression` directly. `addProperty("get", "%L", arrow(…))` still works; the `"%L"`
  carried no information the argument did not.
- `literal()`, for literal types: `literal("a")` emits `"a"`, escaped, and there are
  numeric and boolean overloads. Previously the only route was `implicit()` with the caller
  writing the quotes, which emitted an unparseable file if the value contained one.
- `recordType()`, emitting `Record<K, V>`. `mapType()` spells `Map<K, V>`, which is a runtime
  class accessed with `.get(k)` and not what `JSON.parse` produces.
- Generic function types, construct signatures including `abstract new`, and `unique symbol`.
- Variance annotations (`in`/`out`) and `const` type parameters.
- Operand precedence: a union or intersection used under `keyof`, `[]` or `[K]` is
  parenthesised, so the emitted type means what was asked for.

**Declarations**
- `async`, generator functions and methods, and arrow functions.
- Overload groups: N signatures plus one implementation, validated to share a name.
- `this` parameters, type predicates (`x is Y`) and assertion signatures (`asserts x is Y`).
- `override` and `accessor` modifiers, `#private` members, class static initializer blocks,
  and class index signatures.
- `const enum`, definite assignment (`!`), and destructuring parameters.
- `PropertySpec.builder(name, modifiers…)`, a property or top-level variable with no
  `TypeName`, emitting no annotation: `const api = createApi();` rather than a type invented
  purely to have something to write. For an object literal, inference is often better typing
  than anything a generator could name.
  ([#48](https://github.com/lldata/typescriptpoet/issues/48))

**Modules**
- Default imports, with `=` as their sigil in the symbol mini-DSL.
- `import type` and `export type`, using the inline `type` form so value and type imports
  from one module stay on a single statement.
- Re-exports (`export * from`, `export * as ns from`, `export { a, b as c } from`),
  standalone export lists, `export default`, and `export =`.

**Kotlin DSL**
- A `dsl` package naming each construct after the TypeScript it emits — `clazz`, `interfaze`,
  `type`, `namespace`, `enum`, `function`, `constructor`, `property`, `parameter`, `index`,
  `call`, `extends`, `implements`, `static`, `decorator`, `overloads` — over the same builders,
  which stay the whole API. Every declaration is `@JvmSynthetic`, so Java sees the builders and
  never the DSL.
- Statements live in a `body { }` whose receiver offers only statements, so a `parameter` cannot
  be written inside a body and a `statement` cannot be written beside a signature.
- TypeScript's twelve primitive types and seventeen modifiers as lowercase values — `string`,
  `number`, `public`, `readonly` — with a trailing underscore only where the word is a Kotlin
  keyword: `null_`, `object_`, `var_`.
- `or` and `and` for unions and intersections, `union(…)` for a computed list, and
  `anonymous(member(…), optionalMember(…))` for object literal types.
- Passing Kotlin's `null` where TypeScript's `null` type was meant is a compile error naming
  `null_`, rather than the compiler's generic "Null cannot be a value of a non-null type".

**Formatting**
- Emitted output aims to look like it has already been through Prettier with default
  settings: double quotes, brace spacing in import and export specifiers, `;` separators in
  type literals, trailing commas, no blank lines against braces, and an 80-column print
  width. A `prettier --check` test over the golden file keeps that honest for the constructs
  the library emits, so generated files should no longer churn the first time Prettier is run
  over a repository. It is an aim rather than a guarantee — Prettier decides breaks by laying
  out a whole document, this emitter decides them as it writes, and an unusual shape can
  still differ. Run Prettier over the output if you need it exact.
  **This changes emitted formatting for every 1.x user**; no API changed.
- Long parameter lists and mapped types break on width rather than at fixed wrap points,
  matching what Prettier does: measure the construct, and either keep it on one line or put
  every element on its own.
- A type parameter list breaks on width like a parameter list, one variable per line with a
  trailing comma and the `>` back at the declaration's indent. What follows the `>` counts
  only when it cannot break itself: a parameter list can, a class's `extends` clause cannot.
  ([#11](https://github.com/lldata/typescriptpoet/issues/11))
- An empty body is emitted as `{}` rather than an opening brace, a newline and a closing one —
  for a class, interface, namespace, enum, function, method, constructor and arrow alike. A
  constructor whose parameters are all parameter properties is the common case.
  ([#12](https://github.com/lldata/typescriptpoet/issues/12))
- A union type alias too long for one line breaks after the `=` and keeps the union on a
  single indented line, and only splits one choice per line when that is too wide as well —
  the intermediate form Prettier tries first, which is often what a union of literals needs
  once its `export type Name = ` prefix is counted.
  ([#8](https://github.com/lldata/typescriptpoet/issues/8))

**Project**
- A golden-file integration test covering every construct once, type-checked with the real
  `tsc` — against TypeScript 5 and 7, the floor the emitted constructs need and the current
  major — and validated with `prettier --check`. The same file is built three ways — Kotlin
  DSL, Kotlin builders, Java builders — each asserted to emit it byte for byte, so the Java
  half of "a Kotlin and Java API" is tested at full size.
- Binary compatibility validation, a coverage floor, and ktlint plus license-header checks,
  all wired into `check`.

### Fixed

- `NameAllocator` sanitised a name suggestion with `Character.isJavaIdentifierStart`/`Part`,
  which is more permissive than a TypeScript identifier in both directions: it accepts every
  currency symbol and several ignorable control and format characters that ECMAScript's
  `IdentifierStart`/`IdentifierPart` do not, so a suggestion like `price€` was copied through
  unchanged into a `.ts` file that `tsc` then rejects — a failure that landed on whoever
  generated the file, not on whoever wrote the suggestion. It now approximates Unicode's
  `ID_Start`/`ID_Continue` from letter and mark categories, plus the `$`/`_` and zero-width
  joiner/non-joiner ECMAScript adds on top, all from `Character` APIs old enough for the
  Java 8 floor. ([#42](https://github.com/lldata/typescriptpoet/issues/42))
- A named-import group reached the line wrapper as one pre-joined string —
  `names.joinToString(", ")` — with no space to break at, so `import { … } from "…";` was
  emitted on one line however wide it got: three names past a moderately deep relative
  specifier was enough to reach 92 columns. It now measures the whole statement and, like every
  other list in this emitter, breaks all-or-nothing — one name per line with a trailing comma —
  rather than wrapping only the names that overflow, which is what keeps the result a fixed
  point under Prettier. ([#15](https://github.com/lldata/typescriptpoet/issues/15))
- A concise arrow body that did not fit stayed on the `=>` line and broke inside itself, so a
  call that would have fitted on a line of its own came apart into one argument per line. It
  now breaks after the `=>` and takes the whole body down one level, which is both what
  Prettier does and what keeps the call intact. An object literal body still hugs the arrow —
  `=> ({` and then its members — since moving `({` down on its own reads worse.
  ([#14](https://github.com/lldata/typescriptpoet/issues/14))
- A value that lays itself out — an object literal, an inline object type — measured only
  itself and not the rest of the line, so `return f({ method: "POST", path, body }, config,
  options);` came out at 83 columns against a print width of 80. Whatever the enclosing
  format string still has to write is now counted.
  ([#13](https://github.com/lldata/typescriptpoet/issues/13))
- `TypeName.Anonymous` was emitted on one line unconditionally, which made an inline object
  type the one construct that could not be laid out. It now measures and breaks like an
  object literal.
- An interface or class put a blank line between every pair of members, including two plain
  fields. Prettier keeps blank lines but never inserts them, so the gaps were the library's
  own and roughly doubled the height of every DTO interface. Members with a comment, a
  decorator, an initializer or a body still get one.
- A lone parameter whose type is an inline object now keeps its parens on the signature line
  and lets the type break instead, which is what Prettier does with an options bag.
- `FileSpec.addProperty` threw for every input: it required exactly one of `CONST`/`LET`/`VAR`
  and then ran a check forbidding all three. Top-level properties were unreachable.
- `FileSpec.addEnum` rejected `CONST`, so `const enum` could not be added to a file.
- Types referenced only inside a nested `CodeBlock` were not imported, because passing a
  `CodeBlock` as a `%L` argument flattened it to a string and discarded its format parts.
  ([outfoxx/typescriptpoet#27](https://github.com/outfoxx/typescriptpoet/pull/27))
- A type used as a type-variable bound was not imported. The bound was rendered by
  interpolating the type's text into a format string, so the name reached the file and never
  reached the import collector: `class Holder<X extends Base>` with no `import { Base }`
  anywhere, which does not compile.
  ([#11](https://github.com/lldata/typescriptpoet/issues/11))
- Every other kind of `%L` argument was still flattened that way. A spec, an object literal,
  a `TypeName` or a `SymbolSpec` was rendered when the block was built, by a throwaway writer
  with no imports to collect into, no rename map, no scope and no column — so its types were
  not imported, a colliding name was not renamed, a name inside a namespace was not made
  relative to it, and a value that lays out against the print width measured from column 0
  rather than from where it was written. `%L` arguments now reach the writer as they were
  given. ([#10](https://github.com/lldata/typescriptpoet/issues/10))
- A value that brings its own layout — a class, an arrow function, an object literal — no
  longer takes the hanging indent of an enclosing statement, which is meant for the
  continuation lines of an over-long expression and put such a value's body and closing brace
  two levels too deep. `addStatement("return %L", objectLiteral)` now lays the literal out at
  the statement's own indent, so the terminator and newline no longer have to be hand-written
  with `addCode`. ([#10](https://github.com/lldata/typescriptpoet/issues/10))
- `returns(TypeName.VOID)` was silently discarded, so an explicit `: void` could not be
  emitted. ([outfoxx/typescriptpoet#24](https://github.com/outfoxx/typescriptpoet/issues/24))
- The reserved-word list was Kotlin's, not TypeScript's, so `NameAllocator` could emit a
  bare `function` or `const` as an identifier.
  ([outfoxx/typescriptpoet#23](https://github.com/outfoxx/typescriptpoet/issues/23))
- Modifiers were emitted in `Modifier` enum declaration order, producing `readonly static`
  and `readonly abstract`; `tsc` rejects both.
  ([outfoxx/typescriptpoet#16](https://github.com/outfoxx/typescriptpoet/pull/16))
- `FileSpec.emitImports` filtered with `!is Augmented || !is SideEffect`, which is always
  true, so the filter did nothing.
- `isName` split on `"\\."` using the literal-delimiter overload, so it never split and its
  per-part check silently degraded to a whole-string one.
- Class index signatures were emitted with a body: `[key: string]: any { }`.
- Rest parameters emitted `... args` with a stray space.
- Block-shaped property initializers picked up the statement wrapper's hanging indent.
- `TypeName.implicit("")` produced a type with an empty name rather than refusing it, so
  `PropertySpec.builder("api", TypeName.implicit(""), false, Modifier.CONST)` emitted
  `export const api:  = createApi();` — a dangling colon and a doubled space, silently, until
  `tsc` saw it. `implicit()` now requires a non-blank name.
  ([#48](https://github.com/lldata/typescriptpoet/issues/48))

### Changed

- **Breaking.** Targets Java 8 bytecode and Kotlin 2.0 metadata, with a `kotlin-stdlib` floor
  of 2.0.21. Consumers need JDK 8+ and a Kotlin 2.0+ compiler.
- **Breaking.** `TypeName.Tuple.memberTypes` is now `members`, holding `Tuple.Member`.
  `memberTypes` remains as a derived property.
- **Breaking.** `TypeName` and `SymbolSpec` gained subclasses; exhaustive `when` expressions
  over them need updating.
- **Breaking.** `copy()` on `TypeName` and `SymbolSpec` subclasses is no longer public,
  matching their non-public constructors.
- `@JvmField` on the `TypeName` constants, so Java callers write `TypeName.STRING`.
  ([outfoxx/typescriptpoet#19](https://github.com/outfoxx/typescriptpoet/pull/19))
- Builds on Gradle 9.7 with Kotlin 2.4.10 and a JDK 17 toolchain.
- The published POM spells its SCM coordinates as `scm:git:https://…` and
  `scm:git:ssh://…`. The previous values omitted the provider segment, so tools that read
  the POM to find the sources — IDEs, release plugins, provenance scanners — could not parse
  them.
- The README's licence notice carries the LL Data ApS copyright line alongside Outfox's,
  matching `NOTICE.txt` and the header Spotless applies to every source file.
- The README carries a coverage badge, fed by a shields.io endpoint that CI writes to the
  `gh-pages` branch on each push to `main`. GitHub Pages is now serving that branch, which
  also means the Dokka documentation every release has been publishing there is readable
  for the first time, at <https://lldata.github.io/typescriptpoet/>.
- Unreleased commits can be depended on through [JitPack](https://jitpack.io/#lldata/typescriptpoet),
  so a project using this library can test a fix before a version is cut rather than after,
  and report back while the fix is still cheap to change. The README says how; the group is
  `com.github.lldata` there rather than `dk.lldata`, because JitPack builds from the
  repository. Publishing snapshots to Maven Central was tried first and removed: one publish
  costs about 11 MB, almost all of it the Dokka javadoc artifact, against an 80 MB monthly
  limit and a ceiling of seven releases a month — so a snapshot per merge would have spent
  the budget the actual releases need. Pinning a commit is also the better answer for a bug
  report, since it does not move under the person testing it.
- The Maven Central `-javadoc` artifact is now a single redirect page pointing at
  <https://lldata.github.io/typescriptpoet/>, instead of a full copy of the Dokka HTML site.
  Central requires the artifact to exist, not to be useful, and nobody reads documentation out
  of a jar; the previous 11 MB copy spent most of the 80 MB monthly publishing budget on
  something identical to what is already on `gh-pages`. `gh-pages` itself is unaffected — it
  still gets the complete site, unabridged, on every release.
  ([#21](https://github.com/lldata/typescriptpoet/issues/21))
- Qodana's inspections run clean, so the count means something again. Seven imports in the DSL
  that nothing referenced are gone. The eleven "unused receiver parameter" reports were the
  deliberate shape of the `null` hints in `NullHint.kt` — those overloads exist to be refused at
  compile time, and the receiver is what picks the right builder for the message — so they are
  suppressed next to the parameter warning already suppressed there for the same reason.
  ([#55](https://github.com/lldata/typescriptpoet/pull/55))

### Removed

- The Guava dependency, which was declared but never imported, and `kotlin-reflect`, which
  was never needed — `KClass` lives in `kotlin-stdlib`. The library now has no runtime
  dependencies beyond the Kotlin standard library.
  ([outfoxx/typescriptpoet#29](https://github.com/outfoxx/typescriptpoet/pull/29))


## 1.1.2 and earlier

See the [releases page](https://github.com/outfoxx/typescriptpoet/releases).
