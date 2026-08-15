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
package dk.lldata.typescriptpoet.dsl

import dk.lldata.typescriptpoet.CodeBlock
import dk.lldata.typescriptpoet.TypeName

// Composing types, written the way TypeScript composes them. Both operators flatten, so
// `a or b or c` is the same Union as `unionType(a, b, c)` rather than one nested in another.

private fun TypeName.choices(): List<TypeName> = if (this is TypeName.Union) typeChoices else listOf(this)

private fun TypeName.requirements(): List<TypeName> =
  if (this is TypeName.Intersection) typeRequirements else listOf(this)

/** `A | B` */
@JvmSynthetic
infix fun TypeName.or(other: TypeName): TypeName = TypeName.unionType(*(choices() + other.choices()).toTypedArray())

/** `A & B` */
@JvmSynthetic
infix fun TypeName.and(other: TypeName): TypeName =
  TypeName.intersectionType(*(requirements() + other.requirements()).toTypedArray())

/** `A | B | C`, from a computed list. */
@JvmSynthetic
fun union(types: Iterable<TypeName>): TypeName = TypeName.unionType(*types.toList().toTypedArray())

/** `"MARKETPLACE"`, a string literal type. */
@JvmSynthetic
fun literal(value: String): TypeName = TypeName.literal(value)

/** `{ label: string; hint?: string }` */
@JvmSynthetic
fun anonymous(vararg members: TypeName.Anonymous.Member): TypeName = TypeName.anonymousType(members.toList())

/**
 * `label: string`, a member of an anonymous type.
 *
 * [tsDoc] documents this member alone, which is the thing an interface cannot do without
 * being declared. A documented member puts the whole type on several lines. [readonly] is
 * the other thing an interface property can do that this form otherwise could not.
 */
@JvmSynthetic
fun member(
  name: String,
  type: TypeName,
  tsDoc: CodeBlock = CodeBlock.empty(),
  readonly: Boolean = false,
): TypeName.Anonymous.Member = TypeName.Anonymous.Member(name, type, false, tsDoc, readonly)

/** `hint?: string`, an optional member of an anonymous type. */
@JvmSynthetic
fun optionalMember(
  name: String,
  type: TypeName,
  tsDoc: CodeBlock = CodeBlock.empty(),
  readonly: Boolean = false,
): TypeName.Anonymous.Member = TypeName.Anonymous.Member(name, type, true, tsDoc, readonly)
