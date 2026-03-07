package io.github.kingg22.godot.codegen.impl.extensionapi.native.generators

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.kingg22.godot.codegen.impl.K_TODO
import io.github.kingg22.godot.codegen.impl.extensionapi.native.BYTE_VAR
import io.github.kingg22.godot.codegen.impl.extensionapi.native.COPAQUE_POINTER
import io.github.kingg22.godot.codegen.impl.extensionapi.native.C_POINTER
import io.github.kingg22.godot.codegen.models.extensionapi.BuiltinClass

private val STORAGE_BACKED_BUILTINS = setOf("String", "StringName", "NodePath")

// FIXME create a new RuntimePackageRegistry to handle those
private val builtinRuntimeObject = ClassName("io.github.kingg22.godot.runtime", "BuiltinRuntime")
private val godotStringClass = ClassName("io.github.kingg22.godot.api.builtin", "GodotString")
private val allocateBuiltinStorage = MemberName(
    "io.github.kingg22.godot.api.builtin.internal",
    "allocateBuiltinStorage",
)
private val freeBuiltinStorage = MemberName(
    "io.github.kingg22.godot.api.builtin.internal",
    "freeBuiltinStorage",
)

/** Responsible solely for generating function/property bodies. */
class BodyGenerator {
    fun todoBody(): CodeBlock = CodeBlock.of("%M()", K_TODO)

    fun todoGetter(): FunSpec = FunSpec
        .getterBuilder()
        .addCode(todoBody())
        .build()

    fun configureStorageBackedBuiltin(builtinClass: BuiltinClass, classBuilder: TypeSpec.Builder): TypeSpec.Builder =
        classBuilder.apply {
            if (builtinClass.name !in STORAGE_BACKED_BUILTINS) return@apply

            val storageType = C_POINTER.parameterizedBy(BYTE_VAR)
            val storageProperty = PropertySpec
                .builder("storage", storageType, KModifier.PRIVATE)
                .initializer("storage")
                .build()

            classBuilder.primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addParameter("storage", storageType)
                    .addModifiers(KModifier.PRIVATE)
                    .build(),
            )
            classBuilder.addProperty(storageProperty)
            classBuilder.addProperty(
                PropertySpec
                    .builder("closed", BOOLEAN, KModifier.PRIVATE)
                    .mutable(true)
                    .initializer("false")
                    .build(),
            )
            classBuilder.addProperty(
                PropertySpec
                    .builder("rawPtr", COPAQUE_POINTER, KModifier.INTERNAL)
                    .getter(
                        FunSpec
                            .getterBuilder()
                            .addStatement("return %N", storageProperty)
                            .build(),
                    )
                    .build(),
            )
        }

    fun buildCloseFunction(builtinClass: BuiltinClass): FunSpec {
        if (builtinClass.name !in STORAGE_BACKED_BUILTINS) {
            return FunSpec
                .builder("close")
                .addModifiers(KModifier.OVERRIDE)
                .addCode(todoBody())
                .build()
        }

        return FunSpec
            .builder("close")
            .addModifiers(KModifier.OVERRIDE)
            .addCode(
                CodeBlock.builder()
                    .beginControlFlow("if (!closed)")
                    .addStatement("%L", destroyCallFor(builtinClass.name))
                    .addStatement("%M(%N)", freeBuiltinStorage, "storage")
                    .addStatement("closed = true")
                    .endControlFlow()
                    .build(),
            )
            .build()
    }

    fun constructorBodyFor(
        builtinClass: BuiltinClass,
        ctor: BuiltinClass.Constructor,
        ctorBuilder: FunSpec.Builder,
    ): CodeBlock {
        if (builtinClass.name !in STORAGE_BACKED_BUILTINS) return todoBody()

        if (builtinClass.name in STORAGE_BACKED_BUILTINS) {
            ctorBuilder.callThisConstructor(
                CodeBlock.of("%M(%L)", allocateBuiltinStorage, builtinStorageSize(builtinClass.name)),
            )
        }

        val initCall = when (builtinClass.name) {
            "String" -> when (ctor.index) {
                0 -> CodeBlock.of("%T.initializeStringEmpty(rawPtr)", builtinRuntimeObject)
                1 -> CodeBlock.of("%T.initializeStringCopy(rawPtr, from.rawPtr)", builtinRuntimeObject)
                2 -> CodeBlock.of("%T.initializeStringFromStringName(rawPtr, from.rawPtr)", builtinRuntimeObject)
                3 -> CodeBlock.of("%T.initializeStringFromNodePath(rawPtr, from.rawPtr)", builtinRuntimeObject)
                else -> return todoBody()
            }

            "StringName" -> when (ctor.index) {
                0 -> CodeBlock.of("%T.initializeStringNameEmpty(rawPtr)", builtinRuntimeObject)
                1 -> CodeBlock.of("%T.initializeStringNameCopy(rawPtr, from.rawPtr)", builtinRuntimeObject)
                2 -> CodeBlock.of("%T.initializeStringNameFromString(rawPtr, from.rawPtr)", builtinRuntimeObject)
                else -> return todoBody()
            }

            "NodePath" -> when (ctor.index) {
                0 -> CodeBlock.of("%T.initializeNodePathEmpty(rawPtr)", builtinRuntimeObject)
                1 -> CodeBlock.of("%T.initializeNodePathCopy(rawPtr, from.rawPtr)", builtinRuntimeObject)
                2 -> CodeBlock.of("%T.initializeNodePathFromString(rawPtr, from.rawPtr)", builtinRuntimeObject)
                else -> return todoBody()
            }

            else -> return todoBody()
        }

        return CodeBlock
            .builder()
            .addStatement("%L", initCall)
            .build()
    }

    fun stringConstructorBodyFor(builtinClass: BuiltinClass, ctorBuilder: FunSpec.Builder): CodeBlock {
        if (builtinClass.name in STORAGE_BACKED_BUILTINS) {
            ctorBuilder.callThisConstructor(
                CodeBlock.of(
                    "%M(%L)",
                    allocateBuiltinStorage,
                    builtinStorageSize(builtinClass.name),
                ),
            )
        }

        return when (builtinClass.name) {
            "String" -> CodeBlock.builder()
                .addStatement("%T.initializeStringFromUtf8(rawPtr, value)", builtinRuntimeObject)
                .build()

            "StringName" -> CodeBlock.builder()
                .addStatement("%T.initializeStringNameFromUtf8(rawPtr, value)", builtinRuntimeObject)
                .build()

            "NodePath" -> CodeBlock.builder()
                .beginControlFlow("%T(value).use { godotString ->", godotStringClass)
                .addStatement("%T.initializeNodePathFromString(rawPtr, godotString.rawPtr)", builtinRuntimeObject)
                .endControlFlow()
                .build()

            else -> todoBody()
        }
    }

    fun destroyCallFor(className: String): CodeBlock = when (className) {
        "String" -> CodeBlock.of("%T.destroyString(rawPtr)", builtinRuntimeObject)
        "StringName" -> CodeBlock.of("%T.destroyStringName(rawPtr)", builtinRuntimeObject)
        "NodePath" -> CodeBlock.of("%T.destroyNodePath(rawPtr)", builtinRuntimeObject)
        else -> error("Missing destroy method for $className")
    }

    fun builtinStorageSize(className: String): Int = when (className) {
        "String" -> 8
        "StringName" -> 8
        "NodePath" -> 8
        else -> error("Missing storage size for $className")
    }
}
