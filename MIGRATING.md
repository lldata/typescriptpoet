Migrating from 1.x to 2.0.0
===========================

Most code needs no changes. The breaks below are listed with what to do about each.

Requirements
------------

The published jar targets **Java 8 bytecode** and **Kotlin 2.0** metadata, so consumers need
JDK 8+ and a Kotlin 2.0+ compiler. The declared `kotlin-stdlib` floor is 2.0.21; Gradle
resolves to the highest version any participant asks for, so this does not hold you back.

Building TypeScriptPoet itself needs JDK 17, which the Gradle toolchain provisions for you.


Changed output
--------------

These change what is generated without changing any API you call. Regenerate and diff.

**`returns(TypeName.VOID)` now emits `: void`.**
It used to be silently discarded, leaving no return type at all. If you want no return type,
do not call `returns()`.

**Modifiers are emitted in TypeScript's grammar order.**
Order used to follow the `Modifier` enum's declaration order, which produced `readonly static`
and `readonly abstract`; both are rejected by `tsc`. They are now `static readonly` and
`abstract readonly`.

**Emitted formatting now aims at Prettier's defaults.**
Double quotes rather than single, brace spacing in `import { A }`, `;` rather than `,` between
type-literal members, trailing commas, no blank lines immediately inside `{ }`, and an
80-column width. If you assert on generated output, regenerate your expectations; if you run
Prettier over generated files, this removes most of the churn rather than adding it. It is
best effort rather than a guarantee -- what to do about the remaining difference, whether that
is excluding generated files from your formatter, running Prettier over them, or leaving them
as they are, is yours to choose.

**Rest parameters emit `...args` rather than `... args`.**
Cosmetic; the old form was valid but not idiomatic.

**Consecutive plain members no longer have a blank line between them.**
An interface or class used to separate every pair of members, including two plain fields, so
a six-field DTO interface was eleven lines. Prettier keeps blank lines but never inserts
them, so nothing required these. Members carrying a comment, a decorator, an initializer or a
body still get one.

**A value that lays itself out now counts the rest of the line.**
An object literal or inline object type used to measure only itself, so
`f({ method: "POST", path, body }, config, options);` came out past the print width. Some
things that used to stay on one line now break. Inline object types break at all for the
first time -- they were previously emitted on one line however long they were.

**A concise arrow body that does not fit now moves to the next line whole.**
It used to stay on the `=>` line and break inside itself, which took apart a call that would
have fitted once it had a line of its own. An object literal body is unchanged: it still hugs
the arrow as `=> ({`.

**A lone parameter whose type is an inline object keeps its parens on the signature line,**
letting the type break instead: `listWidgets(args?: {` … `}): Promise<T> {`. This is what
Prettier does with an options bag.

**Types referenced only inside a nested `CodeBlock` are now imported.**
Passing a `CodeBlock` as a `%L` argument used to flatten it to a string, discarding its
format parts, so a `%T` inside it never reached the import collector. If you were adding
those imports manually as a workaround, remove the workaround or you will get duplicates.

**The reserved-word list is TypeScript's, not Kotlin's.**
It previously held Kotlin's keywords verbatim. Names like `function`, `const`, `let`, `enum`,
`export`, `import`, `new`, `delete`, `void`, `await` and `yield` are now rejected as
identifiers — they were never valid in the output. Kotlin-only words such as `fun`, `val`,
`object`, `when` and `typealias` are now accepted, as they always should have been.


Source-incompatible changes
---------------------------

**`TypeName.Tuple.memberTypes` is now `members`.**
Tuple elements can be labelled, optional or rest, so each element is a `Tuple.Member` rather
than a bare `TypeName`. `TypeName.tupleType(vararg TypeName)` is unchanged, and `memberTypes`
survives as a derived read-only property, so most call sites need nothing.

**`TypeName` gained subclasses.**
It is a sealed class, so an exhaustive `when` over it in your own code needs new branches or
an `else`. The additions are `Operator`, `IndexedAccess`, `ArrayShorthand`, `Conditional`,
`Mapped` and `TemplateLiteral`.

**`SymbolSpec` gained `ImportsDefault`,** with `=` as its sigil in the symbol mini-DSL. If
you pass hand-written spec strings containing `=`, check they still parse as intended; `=`
was not previously a legal character in that position.

**`TypeName.Anonymous.Member` gained a `tsDoc` parameter.**
Constructing one with three positional arguments is unchanged -- the parameter is defaulted,
and `@JvmOverloads` keeps the three-argument form for Java too. Only `copy()`'s signature and
a four-component destructuring are affected.

**`copy()` on `TypeName` and `SymbolSpec` subclasses is no longer public.**
They are annotated `@ConsistentCopyVisibility`, matching their non-public constructors. Build
them through their factories, which is how they were always meant to be constructed.


Fixed, previously unusable
--------------------------

**`FileSpec.addProperty` used to throw for every input.** It required exactly one of
`CONST`/`LET`/`VAR` and then ran a check that forbade all three. Top-level properties now
work.

**`FileSpec.addEnum` rejected `CONST`,** so `const enum` could not be added to a file.

**Class index signatures emitted a body,** producing `[key: string]: any { }`. Call and index
signatures are declarations in every context and never take a body.


New in 2.0.0
------------

Types: `keyof`, `typeof`, indexed access (`T[K]`), the array shorthand (`T[]`), `readonly`
arrays and tuples, labelled/optional/rest tuple elements, conditional types with `infer`,
mapped types with `as` remapping and `+`/`-` modifiers, template literal types, generic
function types, construct signatures including `abstract new`, `unique symbol`, variance
annotations and `const` type parameters.

Declarations: `async`, generator functions and methods, arrow functions, overload groups,
`this` parameters, type predicates and assertion signatures, `override`, `accessor`,
`#private` members, class static initializer blocks, class index signatures, `const enum`,
definite assignment (`!`), and destructuring parameters.

Modules: default imports, `import type` and `export type`, re-exports (`export * from`,
`export * as ns from`, `export { a, b as c } from`), standalone export lists,
`export default`, and `export =`.

Expressions: `CodeBlock.objectLiteral()`, and `CodeBlock.call()`/`newInstance()`, which keep an
object's members and a call's arguments structured until the file is written so both can be
measured against the print width and broken; `ObjectLiteral.Builder.addSpread()` for
`{ ...value }`; and `FunctionSpec.Builder.expressionBody()` for a concise arrow body.

Elsewhere in the API: `literal()` for literal types, `promiseType()` and `recordType()`,
`anonymousMember()` for an inline object type member that can be `readonly` and carry a TSDoc
comment of its own, `PropertySpec.builder()` without a `TypeName` so a `const`'s type can be
left to inference, and a public `TypePredicate` that `lambda()` and `genericLambda()` accept in
place of a return type.
