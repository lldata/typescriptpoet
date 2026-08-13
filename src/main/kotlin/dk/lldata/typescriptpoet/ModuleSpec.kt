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
package dk.lldata.typescriptpoet

/** A generated `module` declaration. */
class ModuleSpec
private constructor(builder: Builder) : Taggable(builder.tags.toImmutableMap()) {

  enum class Kind(val keyword: String) {
    MODULE("module"),
    NAMESPACE("namespace"),
  }

  val name = builder.name
  val tsDoc = builder.tsDoc.build()
  val modifiers = builder.modifiers.toImmutableList()
  val members = builder.members.toImmutableList()
  val kind = builder.kind

  internal fun emit(codeWriter: CodeWriter) {
    codeWriter.pushScope(name)
    try {
      if (tsDoc.isNotEmpty()) {
        codeWriter.emitComment(tsDoc)
      }

      if (modifiers.isNotEmpty()) {
        codeWriter.emitCode(
          CodeBlock.of("%L ", modifiers.toSet().inEmitOrder().joinToString(" ") { it.keyword }),
        )
      }
      codeWriter.emitCode(CodeBlock.of("${kind.keyword} %L ", name))
      codeWriter.emitBody(members.isEmpty()) {
        members.forEachIndexed { index, member ->
          if (index > 0) codeWriter.emit("\n")
          codeWriter.emitMember(member)
        }
      }
    } finally {
      codeWriter.popScope()
    }
  }

  /** Whether the module declares no members. */
  fun isEmpty(): Boolean = members.isEmpty()

  /** Whether the module declares any members. */
  fun isNotEmpty(): Boolean = !isEmpty()

  override fun toString() = buildCodeString { emit(this) }

  /** A builder pre-populated with this spec, for deriving a modified copy. */
  fun toBuilder(): Builder {
    val builder = Builder(name, kind)
    builder.tsDoc.add(tsDoc)
    builder.modifiers += modifiers
    builder.members.addAll(this.members)
    return builder
  }

  open class Builder
  internal constructor(internal val name: String, internal val kind: Kind = Kind.NAMESPACE) :
    Taggable.Builder<Builder>() {

    internal val tsDoc = CodeBlock.builder()
    internal val modifiers = mutableSetOf<Modifier>()
    internal val members = mutableListOf<Any>()

    private fun checkMemberModifiers(modifiers: Set<Modifier>) {
      requireNoneOf(
        modifiers,
        Modifier.PUBLIC,
        Modifier.PROTECTED,
        Modifier.PRIVATE,
        Modifier.READONLY,
        Modifier.GET,
        Modifier.SET,
        Modifier.STATIC,
      )
    }

    /** Adds TSDoc above the module. */
    fun addTSDoc(format: String, vararg args: Any) = apply {
      tsDoc.add(format, *args)
    }

    /** Adds TSDoc above the module. */
    fun addTSDoc(block: CodeBlock) = apply {
      tsDoc.add(block)
    }

    /** Adds a modifier: `export namespace Shapes { }`. Only one of EXPORT or DECLARE. */
    fun addModifier(modifier: Modifier) = apply {
      requireNoneOrOneOf(
        modifiers + modifier,
        Modifier.EXPORT,
        Modifier.DECLARE,
      )
      modifiers += modifier
    }

    /** Nests a module: `namespace Outer { namespace Inner { } }`. */
    fun addModule(moduleSpec: ModuleSpec) = apply {
      members += moduleSpec
    }

    /** Adds a class to the module body. */
    fun addClass(classSpec: ClassSpec) = apply {
      checkMemberModifiers(classSpec.modifiers)
      members += classSpec
    }

    /** Adds an interface to the module body. */
    fun addInterface(ifaceSpec: InterfaceSpec) = apply {
      checkMemberModifiers(ifaceSpec.modifiers)
      members += ifaceSpec
    }

    /** Adds an enum to the module body. */
    fun addEnum(enumSpec: EnumSpec) = apply {
      checkMemberModifiers(enumSpec.modifiers)
      members += enumSpec
    }

    /** Adds a class, interface, enum or type alias, dispatching on its kind. */
    fun addType(typeSpec: AnyTypeSpec) = apply {
      when (typeSpec) {
        is EnumSpec -> addEnum(typeSpec)
        is InterfaceSpec -> addInterface(typeSpec)
        is ClassSpec -> addClass(typeSpec)
        is TypeAliasSpec -> addTypeAlias(typeSpec)
      }
    }

    /** Adds a function to the module body. Constructors and decorators are not allowed. */
    fun addFunction(functionSpec: FunctionSpec) = apply {
      require(!functionSpec.isConstructor) { "cannot add ${functionSpec.name} to module $name" }
      require(functionSpec.decorators.isEmpty()) { "decorators on module functions are not allowed" }
      checkMemberModifiers(functionSpec.modifiers)
      members += functionSpec
    }

    /** Adds a variable: `const VERSION: string = '1';`. Needs one of CONST, LET or VAR. */
    fun addProperty(propertySpec: PropertySpec) = apply {
      requireExactlyOneOf(
        propertySpec.modifiers,
        Modifier.CONST,
        Modifier.LET,
        Modifier.VAR,
      )
      require(propertySpec.decorators.isEmpty()) { "decorators on file properties are not allowed" }
      checkMemberModifiers(propertySpec.modifiers)
      members += propertySpec
    }

    /** Adds a type alias to the module body. */
    fun addTypeAlias(typeAliasSpec: TypeAliasSpec) = apply {
      members += typeAliasSpec
    }

    /** Adds arbitrary code to the module body. */
    fun addCode(codeBlock: CodeBlock) = apply {
      members += codeBlock
    }

    /** Whether the module declares no members. */
    fun isEmpty(): Boolean = members.isEmpty()

    /** Whether the module declares any members. */
    fun isNotEmpty(): Boolean = !isEmpty()

    /** Builds the module. */
    fun build() = ModuleSpec(this)
  }

  companion object {

    /** A namespace or module: `namespace Shapes { }`. */
    @JvmStatic
    @JvmOverloads
    fun builder(name: String, kind: Kind = Kind.NAMESPACE) = Builder(name, kind)

    /** A namespace or module: `namespace Shapes { }`. */
    @JvmStatic
    @JvmOverloads
    fun builder(name: TypeName, kind: Kind = Kind.NAMESPACE) = builder("$name", kind)
  }
}
