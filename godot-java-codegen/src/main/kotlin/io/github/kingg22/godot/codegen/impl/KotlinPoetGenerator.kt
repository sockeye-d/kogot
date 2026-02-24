package io.github.kingg22.godot.codegen.impl

import com.squareup.kotlinpoet.*
import io.github.kingg22.godot.codegen.models.Arguments
import io.github.kingg22.godot.codegen.models.Deprecated
import io.github.kingg22.godot.codegen.models.GDExtensionInterface
import io.github.kingg22.godot.codegen.models.Interface
import io.github.kingg22.godot.codegen.models.Types
import io.github.kingg22.godot.codegen.models.ValueType
import java.nio.file.Path

// https://kotlinlang.org/docs/reference/keyword-reference.html
private val kotlinKeywords = setOf(
    // Hard keywords
    "as",
    "break",
    "class",
    "continue",
    "do",
    "else",
    "false",
    "for",
    "fun",
    "if",
    "in",
    "interface",
    "is",
    "null",
    "object",
    "package",
    "return",
    "super",
    "this",
    "throw",
    "true",
    "try",
    "typealias",
    "typeof",
    "val",
    "var",
    "when",
    "while",

    // Soft keywords
    "by",
    "catch",
    "constructor",
    "delegate",
    "dynamic",
    "field",
    "file",
    "finally",
    "get",
    "import",
    "init",
    "param",
    "property",
    "receiver",
    "set",
    "setparam",
    "where",

    // Modifier keywords
    "actual",
    "abstract",
    "annotation",
    "companion",
    "const",
    "crossinline",
    "data",
    "enum",
    "expect",
    "external",
    "final",
    "infix",
    "inline",
    "inner",
    "internal",
    "lateinit",
    "noinline",
    "open",
    "operator",
    "out",
    "override",
    "private",
    "protected",
    "public",
    "reified",
    "sealed",
    "suspend",
    "tailrec",
    "value",
    "vararg",

    // These aren't keywords anymore but still break some code if unescaped. https://youtrack.jetbrains.com/issue/KT-52315
    "header",
    "impl",

    // Other reserved keywords
    "yield",
)

private val nameRegex = Regex("[^A-Za-z0-9_]")
private val K_DEPRECATED = ClassName("kotlin", "Deprecated")
private val K_REPLACE_WITH = ClassName("kotlin", "ReplaceWith")

class KotlinPoetGenerator(private val packageName: String) {
    fun generate(api: GDExtensionInterface, outputDir: Path): List<Path> = api.types.map { type ->
        val file = when (type) {
            is Types.EnumType -> generateEnum(type)
            is Types.HandleType -> generateHandle(type)
            is Types.AliasType -> generateAlias(type)
            is Types.StructType -> generateStruct(type)
            is Types.FunctionType -> generateFunctionType(type)
        }
        file.writeTo(outputDir)
    }.plusElement(generateInterface(api.interfaces).writeTo(outputDir))

    private fun generateEnum(type: Types.EnumType): FileSpec {
        val typeBuilder = TypeSpec.enumBuilder(type.name)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("value", INT)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("value", INT)
                    .initializer("value")
                    .build(),
            )

        type.values.forEach { value ->
            val enumConst = TypeSpec.anonymousClassBuilder()
                .addSuperclassConstructorParameter("%L", value.value)
                .apply {
                    val kdoc = buildKdoc(value.description, emptyList(), emptyList(), null)
                    if (kdoc.isNotBlank()) {
                        addKdoc("%L\n", kdoc)
                    }
                }
                .build()
            typeBuilder.addEnumConstant(sanitizeEnumConstant(value.name), enumConst)
        }

        addCommonDocs(typeBuilder, type.description, emptyList(), type.deprecated)

        return FileSpec.builder(packageName, type.name)
            .addType(typeBuilder.build())
            .build()
    }

    private fun generateHandle(type: Types.HandleType): FileSpec {
        val typeBuilder = TypeSpec.classBuilder(type.name)
            .addModifiers(KModifier.ABSTRACT)

        val parent = type.parent?.takeIf { it.isNotBlank() }
        if (parent != null) {
            typeBuilder.superclass(typeNameFor(parent))
        }

        addCommonDocs(typeBuilder, type.description, emptyList(), type.deprecated)

        return FileSpec.builder(packageName, type.name)
            .addType(typeBuilder.build())
            .build()
    }

    private fun generateAlias(type: Types.AliasType): FileSpec {
        val typeAlias = TypeAliasSpec.builder(type.name, typeNameFor(type.type))
        addCommonDocs(typeAlias, type.description, emptyList(), type.deprecated)

        return FileSpec.builder(packageName, type.name)
            .addTypeAlias(typeAlias.build())
            .build()
    }

    private fun generateStruct(type: Types.StructType): FileSpec {
        val constructor = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(type.name)
            .addModifiers(KModifier.DATA)

        type.members.forEachIndexed { index, member ->
            val name = safeIdentifier(member.name.ifBlank { "member$index" })
            val typeName = typeNameFor(member.type)
            constructor.addParameter(name, typeName)
            typeBuilder.addProperty(
                PropertySpec.builder(name, typeName)
                    .initializer(name)
                    .build(),
            )
        }

        typeBuilder.primaryConstructor(constructor.build())

        addCommonDocs(typeBuilder, type.description, emptyList(), type.deprecated)

        return FileSpec.builder(packageName, type.name)
            .addType(typeBuilder.build())
            .build()
    }

    private fun generateFunctionType(type: Types.FunctionType): FileSpec {
        val typeBuilder = TypeSpec.classBuilder(type.name)
            .addModifiers(KModifier.ABSTRACT)

        val funSpec = FunSpec.builder("invoke")
            .addModifiers(KModifier.OPEN, KModifier.OPERATOR)
            .addParameters(argumentsToParameters(type.arguments))
            .returns(returnTypeName(type.returnValue))
            .addStatement("TODO()")
            .build()

        typeBuilder.addFunction(funSpec)
        addCommonDocs(typeBuilder, type.description, emptyList(), type.deprecated)

        return FileSpec.builder(packageName, type.name)
            .addType(typeBuilder.build())
            .build()
    }

    private fun generateInterface(interfaces: List<Interface>): FileSpec {
        val typeBuilder = TypeSpec.classBuilder("GDExtensionInterface")
            .addModifiers(KModifier.ABSTRACT)

        interfaces.forEachIndexed { index, iface ->
            val funName = safeIdentifier(iface.name.ifBlank { "function$index" })
            val funSpec = FunSpec.builder(funName)
                .addModifiers(KModifier.OPEN)
                .addParameters(argumentsToParameters(iface.arguments))
                .returns(returnTypeName(iface.returnValue))
                .apply {
                    val paramDocs = iface.arguments.mapIndexed { argIndex, arg ->
                        val name = argumentName(arg, argIndex)
                        name to arg.description
                    }
                    val kdoc = buildKdoc(
                        iface.description,
                        iface.see,
                        paramDocs,
                        iface.since,
                    )
                    if (kdoc.isNotBlank()) {
                        addKdoc("%L\n", kdoc)
                    }
                    iface.deprecated?.let { addAnnotation(deprecatedAnnotation(it)) }
                }
                .addStatement("TODO()")
                .build()
            typeBuilder.addFunction(funSpec)
        }

        return FileSpec.builder(packageName, "GDExtensionInterface")
            .addType(typeBuilder.build())
            .build()
    }

    private fun argumentsToParameters(arguments: List<Arguments>) = arguments.mapIndexed { index, arg ->
        ParameterSpec.builder(argumentName(arg, index), typeNameFor(arg.type)).build()
    }

    private fun argumentName(arg: Arguments, index: Int): String {
        val baseName = arg.name?.takeIf { it.isNotBlank() } ?: "arg$index"
        return safeIdentifier(baseName)
    }

    private fun returnTypeName(returnValue: ValueType?): TypeName = if (returnValue == null) {
        UNIT
    } else {
        typeNameFor(returnValue.type)
    }

    private fun typeNameFor(rawType: String): TypeName {
        var type = rawType.trim()
        type = type.removePrefix("const ").trim()
        while (type.endsWith("*")) {
            type = type.removeSuffix("*").trim()
        }

        val normalized = type.lowercase()
        return when (normalized) {
            "void" -> UNIT

            "bool", "boolean" -> BOOLEAN

            "char" -> CHAR

            "float" -> FLOAT

            "double" -> DOUBLE

            "string" -> STRING

            "int", "int32_t" -> INT

            "uint", "uint32_t" -> U_INT

            "short", "int16_t" -> SHORT

            "ushort", "uint16_t" -> U_SHORT

            "byte", "int8_t" -> BYTE

            "ubyte", "uint8_t" -> U_BYTE

            "long", "int64_t", "intptr_t" -> LONG

            "ulong", "uint64_t", "uintptr_t", "size_t" -> U_LONG

            else -> {
                if (type.contains('.')) {
                    ClassName.bestGuess(type)
                } else {
                    ClassName(packageName, type)
                }
            }
        }
    }

    private fun addCommonDocs(
        builder: TypeSpec.Builder,
        description: List<String>,
        see: List<String>,
        deprecated: Deprecated?,
    ) {
        val kdoc = buildKdoc(description, see, emptyList(), null)
        if (kdoc.isNotBlank()) {
            builder.addKdoc("%L\n", kdoc)
        }
        deprecated?.let { builder.addAnnotation(deprecatedAnnotation(it)) }
    }

    private fun addCommonDocs(
        builder: TypeAliasSpec.Builder,
        description: List<String>,
        see: List<String>,
        deprecated: Deprecated?,
    ) {
        val kdoc = buildKdoc(description, see, emptyList(), null)
        if (kdoc.isNotBlank()) {
            builder.addKdoc("%L\n", kdoc)
        }
        deprecated?.let { builder.addAnnotation(deprecatedAnnotation(it)) }
    }

    private fun buildKdoc(
        description: List<String>,
        see: List<String>,
        paramDocs: List<Pair<String, List<String>>>,
        since: String?,
    ): String {
        val lines = mutableListOf<String>()
        if (description.isNotEmpty()) {
            lines.addAll(description)
        }
        if (!since.isNullOrBlank()) {
            lines.add("@since $since")
        }
        paramDocs.forEach { (name, docs) ->
            val text = if (docs.isEmpty()) "" else " ${docs.joinToString(" ")}"
            lines.add("@param $name$text")
        }
        see.forEach { entry ->
            lines.add("@see $entry")
        }
        return lines.joinToString("\n")
    }

    private fun deprecatedAnnotation(deprecated: Deprecated): AnnotationSpec {
        val message = buildString {
            if (!deprecated.message.isNullOrBlank()) {
                append(deprecated.message)
                append(" (since ")
                append(deprecated.since)
                append(')')
            } else {
                append("Deprecated since ")
                append(deprecated.since)
            }
        }

        val builder = AnnotationSpec.builder(K_DEPRECATED)
            .addMember("message = %S", message)

        val replaceWith = deprecated.replaceWith
        if (!replaceWith.isNullOrBlank()) {
            builder.addMember("replaceWith = %T(%S)", K_REPLACE_WITH, replaceWith)
        }

        return builder.build()
    }

    private fun safeIdentifier(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return "_"
        val sanitized = trimmed.replace(nameRegex, "_")
        val fixed = if (sanitized.first().isDigit()) "_$sanitized" else sanitized
        return if (isKotlinKeyword(fixed)) "`$fixed`" else fixed
    }

    private fun sanitizeEnumConstant(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return "UNKNOWN"
        val sanitized = trimmed.replace(nameRegex, "_")
        val fixed = if (sanitized.first().isDigit()) "_$sanitized" else sanitized
        return if (isKotlinKeyword(fixed)) "${fixed}_" else fixed
    }

    private fun isKotlinKeyword(name: String): Boolean = name in kotlinKeywords
}
