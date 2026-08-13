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
  rather than hand-formatted into a `CodeBlock`. Members are properties, shorthand, or
  getters — `addGetter` is what a lazily-reached value needs, since a property is computed
  where the object is built rather than where it is read.
  ([#9](https://github.com/lldata/typescriptpoet/issues/9))
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

**Modules**
- Default imports, with `=` as their sigil in the symbol mini-DSL.
- `import type` and `export type`, using the inline `type` form so value and type imports
  from one module stay on a single statement.
- Re-exports (`export * from`, `export * as ns from`, `export { a, b as c } from`),
  standalone export lists, `export default`, and `export =`.

**Formatting**
- Emitted output is Prettier-formatted with Prettier's default settings, verified by a
  `prettier --check` test: double quotes, brace spacing in import and export specifiers, `;`
  separators in type literals, trailing commas, no blank lines against braces, and an
  80-column print width. Generated files no longer churn the first time Prettier is run over
  a repository. **This changes emitted formatting for every 1.x user**; no API changed.
- Long parameter lists and mapped types break on width rather than at fixed wrap points,
  matching what Prettier does: measure the construct, and either keep it on one line or put
  every element on its own.

**Project**
- A golden-file integration test covering every construct once, type-checked with the real
  `tsc` and validated with `prettier --check`.
- Binary compatibility validation, a coverage floor, and ktlint plus license-header checks,
  all wired into `check`.

### Fixed

- `FileSpec.addProperty` threw for every input: it required exactly one of `CONST`/`LET`/`VAR`
  and then ran a check forbidding all three. Top-level properties were unreachable.
- `FileSpec.addEnum` rejected `CONST`, so `const enum` could not be added to a file.
- Types referenced only inside a nested `CodeBlock` were not imported, because passing a
  `CodeBlock` as a `%L` argument flattened it to a string and discarded its format parts.
  ([outfoxx/typescriptpoet#27](https://github.com/outfoxx/typescriptpoet/pull/27))
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

### Removed

- The Guava dependency, which was declared but never imported, and `kotlin-reflect`, which
  was never needed — `KClass` lives in `kotlin-stdlib`. The library now has no runtime
  dependencies beyond the Kotlin standard library.
  ([outfoxx/typescriptpoet#29](https://github.com/outfoxx/typescriptpoet/pull/29))


## 1.1.2 and earlier

See the [releases page](https://github.com/outfoxx/typescriptpoet/releases).
