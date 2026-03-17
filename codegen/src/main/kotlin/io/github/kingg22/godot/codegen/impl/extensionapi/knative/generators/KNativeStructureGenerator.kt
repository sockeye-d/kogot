package io.github.kingg22.godot.codegen.impl.extensionapi.knative.generators

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.kingg22.godot.codegen.impl.K_SUPPRESS
import io.github.kingg22.godot.codegen.impl.createFile
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.*
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.impl.ImplementationPackageRegistry
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.resolver.NativeStructureParser.NativeStructureField
import io.github.kingg22.godot.codegen.impl.renameGodotClass
import io.github.kingg22.godot.codegen.impl.safeIdentifier
import io.github.kingg22.godot.codegen.impl.sanitizeTypeName
import io.github.kingg22.godot.codegen.models.extensionapi.domain.ResolvedNativeStructure

class KNativeStructureGenerator(private val typeResolver: TypeResolver, private val body: BodyGenerator) {
    private lateinit var implPackageRegistry: ImplementationPackageRegistry

    private data class FieldLayout(val name: String, val offset: Int, val size: Int, val align: Int)
    private data class StructLayout(val size: Int, val align: Int, val fields: List<FieldLayout>)

    fun initialize(implementationPackageRegistry: ImplementationPackageRegistry) {
        this.implPackageRegistry = implementationPackageRegistry
    }

    context(context: Context)
    fun generateFile(ns: ResolvedNativeStructure): FileSpec? {
        val spec = generateSpec(ns) ?: return null
        return createFile(spec, spec.name!!, context.packageForOrDefault(spec.name!!))
    }

    context(_: Context)
    fun generateSpec(ns: ResolvedNativeStructure): TypeSpec? {
        val fields = ns.fields

        // Determinar si la estructura puede ser CStructVar pura
        val canBeCStructVar = canGenerateAsCStructVar(fields)

        return if (canBeCStructVar) {
            // ESTRATEGIA 1: CStructVar (compatible con cinterop)
            if (ns.name == "ObjectID" || ns.name == "AudioFrame") return null
            error(
                "Generating CStructVar for ${ns.name} with fields: ${fields.joinToString { it.name }}, " +
                    "must be defined in header file",
            )
        } else {
            generateOpaqueWrapper(ns, fields)
        }
    }

    // ESTRATEGIA 2: Wrapper Opaco (para estructuras con tipos de API)
    context(context: Context)
    private fun generateOpaqueWrapper(ns: ResolvedNativeStructure, fields: List<NativeStructureField>): TypeSpec {
        val className = sanitizeTypeName(ns.name.renameGodotClass())
        val layout = calculateStructLayout(context, ns.name, fields)

        return TypeSpec
            .classBuilder(className)
            .experimentalApiAnnotation(ns.name)
            .addKdoc(
                "Native structure wrapper\n\n⚠️ This structure contains references to generated API types.",
            )
            .primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("handle", COPAQUE_POINTER)
                    .build(),
            )
            .superclass(C_STRUCT_VAR)
            .addSuperclassConstructorParameter("handle.rawValue")
            .addProperty(
                PropertySpec
                    .builder("storage", C_POINTER.parameterizedBy(BYTE_VAR), KModifier.PRIVATE)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement(
                                "return %M(rawPtr) ?: error(%S)",
                                INTERPRET_C_POINTER,
                                "Null raw pointer for native struct storage",
                            )
                            .build(),
                    )
                    .build(),
            )
            .addProperties(
                // Generar properties con TODO() como antes
                fields.map { field ->
                    val type = typeResolver.resolve(field.type)
                    val resolvedType = if (field.arraySize != null) {
                        context
                            .classNameForOrDefault("Array", "GodotArray", typedClass = true)
                            .parameterizedBy(type)
                    } else {
                        type
                    }
                    val fieldLayout = layout.fields.first { it.name == field.name }
                    val kotlinName = safeIdentifier(field.name)
                    val isImplemented =
                        field.arraySize == null && isLayoutImplementablePrimitiveOrPointer(field.type, resolvedType)

                    val getter = if (isImplemented) {
                        generateOffsetGetter(field, resolvedType, fieldLayout.offset)
                    } else {
                        body.todoGetter()
                    }

                    val setter = if (isImplemented) {
                        generateOffsetSetter(field, resolvedType, fieldLayout.offset)
                    } else {
                        FunSpec
                            .setterBuilder()
                            .addParameter("value", resolvedType)
                            .addCode(body.todoBody())
                            .build()
                    }

                    PropertySpec
                        .builder(kotlinName, resolvedType)
                        .mutable(true)
                        .getter(getter)
                        .setter(setter)
                        .apply {
                            addKdoc("Original name: `%S`", field.name)
                            if (field.arraySize != null) {
                                addKdoc("\n\nArray size: %L, type: `%T`", field.arraySize, type)
                            } else {
                                addKdoc("\n\nOriginal type: `%S`", field.type)
                            }
                            addKdoc(
                                "\n\nLayout: offset=%L, size=%L, align=%L",
                                fieldLayout.offset,
                                fieldLayout.size,
                                fieldLayout.align,
                            )
                        }
                        .build()
                },
            )
            .addType(
                TypeSpec
                    .companionObjectBuilder()
                    .superclass(C_STRUCT_VAR_TYPE)
                    .addSuperclassConstructorParameter("size = %L, align = %L", layout.size, layout.align)
                    .addAnnotation(
                        AnnotationSpec
                            .builder(K_SUPPRESS)
                            .addMember("%S", "DEPRECATION")
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    // HELPERS
    context(_: Context)
    private fun canGenerateAsCStructVar(fields: List<NativeStructureField>): Boolean = fields.all { field ->
        val type = field.type.removePrefix("const ").trim().removeSuffix("*").trim()

        // Solo permitir primitivos, punteros opacos y tipos nativos conocidos
        isPrimitiveCType(type) ||
            isOpaquePointer(type) ||
            isKnownNativeType(type)
    }

    private fun isPrimitiveCType(type: String): Boolean = type.lowercase() in setOf(
        "int8_t", "uint8_t", "int16_t", "uint16_t",
        "int32_t", "uint32_t", "int64_t", "uint64_t",
        "float", "double", "bool",
        "char", "short", "int", "long",
        "size_t", "intptr_t", "uintptr_t",
    )

    private fun isOpaquePointer(type: String): Boolean = type == "void" ||
        (type.startsWith("GDExtension") && type.endsWith("Ptr")) ||
        type.endsWith("*")

    context(context: Context)
    private fun isKnownNativeType(type: String): Boolean {
        // Tipos que sabemos que son seguros para CStructVar
        // (otros structs nativos que ya fueron generados)
        return context.nativeStructureTypes.contains(type) ||
            context.extensionInterface?.types?.any { it.name == type } == true ||
            context.extensionInterface?.interfaces?.any { it.name == type || it.legacyTypeName == type } == true
    }

    private fun calculateStructLayout(
        context: Context,
        structName: String,
        fields: List<NativeStructureField>,
    ): StructLayout {
        fun alignUp(value: Int, align: Int): Int {
            if (align <= 1) return value
            val rem = value % align
            return if (rem == 0) value else value + (align - rem)
        }

        fun pointerLayout(): Pair<Int, Int> = 8 to 8 // Kotlin/Native targets for Godot are 64-bit today.

        fun primitiveLayout(type: String): Pair<Int, Int>? = when (type) {
            "bool" -> 1 to 1
            "char" -> 1 to 1
            "int8_t" -> 1 to 1
            "uint8_t" -> 1 to 1
            "int16_t" -> 2 to 2
            "uint16_t" -> 2 to 2
            "short" -> 2 to 2
            "int32_t" -> 4 to 4
            "uint32_t" -> 4 to 4
            "int" -> 4 to 4
            "float" -> 4 to 4
            "int64_t" -> 8 to 8
            "uint64_t" -> 8 to 8
            "double" -> 8 to 8
            "real_t" -> 8 to 8
            "ObjectID" -> 8 to 8
            else -> null
        }

        fun lookupLayout(type: String): Pair<Int, Int> {
            val clean = type.removePrefix("const ").trim()
            if (clean.endsWith("*")) return pointerLayout()

            primitiveLayout(clean)?.let { return it }

            if (clean.startsWith("enum::") || clean.startsWith("bitfield::")) {
                return 8 to 8
            }

            // Builtins: exact name in extension_api.json (e.g. "Vector2", "RID", "StringName")
            context.model.builtins.firstOrNull { it.name == clean }?.layout?.let { layout ->
                val size = layout.size
                val align = when {
                    size <= 1 -> 1
                    size <= 2 -> 2
                    size <= 4 -> 4
                    else -> 8
                }
                return size to align
            }

            // Nested native structures (value fields).
            context.model.nativeStructures.firstOrNull { it.name == clean }?.let { nested ->
                val nestedFields = nested.fields
                val nestedLayout = calculateStructLayout(context, nested.name, nestedFields)
                return nestedLayout.size to nestedLayout.align
            }

            // Fallback: treat as pointer to keep generator running (but warn loudly).
            println("WARNING: Unknown native struct field type '$clean' in $structName; treating as pointer.")
            return pointerLayout()
        }

        var offset = 0
        var structAlign = 1
        val out = ArrayList<FieldLayout>(fields.size)

        for (f in fields) {
            val clean = f.type.removePrefix("const ").trim()
            val (elemSize, fieldAlign) = lookupLayout(clean)

            val fieldSize = if (f.arraySize != null) elemSize * f.arraySize else elemSize
            structAlign = maxOf(structAlign, fieldAlign)

            offset = alignUp(offset, fieldAlign)
            out += FieldLayout(name = f.name, offset = offset, size = fieldSize, align = fieldAlign)
            offset += fieldSize
        }

        val size = alignUp(offset, structAlign)
        return StructLayout(size = size, align = structAlign, fields = out)
    }

    private fun isLayoutImplementablePrimitiveOrPointer(rawType: String, kotlinType: TypeName): Boolean {
        val clean = rawType.removePrefix("const ").trim()
        if (clean.endsWith("*")) return kotlinType == COPAQUE_POINTER

        return when (clean) {
            "bool", "char",
            "int8_t", "uint8_t",
            "int16_t", "uint16_t",
            "int32_t", "uint32_t",
            "int", "float",
            "int64_t", "uint64_t",
            "double", "real_t",
            "ObjectID",
            -> kotlinType in setOf(BOOLEAN, BYTE, U_BYTE, SHORT, U_SHORT, INT, U_INT, LONG, U_LONG, FLOAT, DOUBLE)

            else -> kotlinType in setOf(BOOLEAN, BYTE, U_BYTE, SHORT, U_SHORT, INT, U_INT, LONG, U_LONG, FLOAT, DOUBLE)
        }
    }

    private fun generateOffsetGetter(field: NativeStructureField, resolvedType: TypeName, offsetBytes: Int): FunSpec {
        val clean = field.type.removePrefix("const ").trim()
        val funName = when {
            clean.endsWith("*") || resolvedType == COPAQUE_POINTER -> "getPointer"
            resolvedType == BOOLEAN -> "getBoolean"
            resolvedType == BYTE -> "getByte"
            resolvedType == U_BYTE -> "getUByte"
            resolvedType == SHORT -> "getShort"
            resolvedType == U_SHORT -> "getUShort"
            resolvedType == INT -> "getInt"
            resolvedType == U_INT -> "getUInt"
            resolvedType == LONG -> "getLong"
            resolvedType == U_LONG -> "getULong"
            resolvedType == FLOAT -> "getFloat"
            resolvedType == DOUBLE -> "getDouble"
            else -> null
        } ?: return body.todoGetter()

        return FunSpec
            .getterBuilder()
            .addStatement("return %M(storage, %L)", MemberName(implPackageRegistry.rootPackage, funName), offsetBytes)
            .build()
    }

    private fun generateOffsetSetter(field: NativeStructureField, resolvedType: TypeName, offsetBytes: Int): FunSpec {
        val clean = field.type.removePrefix("const ").trim()
        val funName = when {
            clean.endsWith("*") || resolvedType == COPAQUE_POINTER -> "setPointer"
            resolvedType == BOOLEAN -> "setBoolean"
            resolvedType == BYTE -> "setByte"
            resolvedType == U_BYTE -> "setUByte"
            resolvedType == SHORT -> "setShort"
            resolvedType == U_SHORT -> "setUShort"
            resolvedType == INT -> "setInt"
            resolvedType == U_INT -> "setUInt"
            resolvedType == LONG -> "setLong"
            resolvedType == U_LONG -> "setULong"
            resolvedType == FLOAT -> "setFloat"
            resolvedType == DOUBLE -> "setDouble"
            else -> null
        } ?: return FunSpec
            .setterBuilder()
            .addParameter("value", resolvedType)
            .addCode(body.todoBody())
            .build()

        return FunSpec
            .setterBuilder()
            .addParameter("value", resolvedType)
            .addStatement("%M(storage, %L, value)", MemberName(implPackageRegistry.rootPackage, funName), offsetBytes)
            .build()
    }
}
