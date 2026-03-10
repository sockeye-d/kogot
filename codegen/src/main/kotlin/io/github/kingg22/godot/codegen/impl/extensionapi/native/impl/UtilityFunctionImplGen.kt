package io.github.kingg22.godot.codegen.impl.extensionapi.native.impl

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.joinToCode
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.native.*
import io.github.kingg22.godot.codegen.impl.extensionapi.native.generators.BodyGenerator
import io.github.kingg22.godot.codegen.impl.safeIdentifier
import io.github.kingg22.godot.codegen.models.extensionapi.MethodArg
import io.github.kingg22.godot.codegen.models.extensionapi.UtilityFunction

/**
 * Generates lazy-loaded function pointer properties and actual invocation bodies
 * for Godot API utility functions (the `GD` object).
 *
 * Each utility function gets:
 * 1. A private `lazy(PUBLICATION)` property that loads the `GDExtensionPtrUtilityFunction`
 *    via `VariantBinding.instance.getPtrUtilityFunctionRaw`, using a temporary `StringName`.
 * 2. A function body that invokes the loaded pointer, packing arguments appropriately.
 *
 * ## Argument mapping
 * - Godot primitive `float` / `double` → `alloc<DoubleVar>()`, assign, pass `.ptr.reinterpret()`
 * - Godot `int`                        → `alloc<LongVar>()`, assign, pass `.ptr.reinterpret()`
 * - Godot `bool`                       → `allocGdBool(arg)`, pass directly
 * - Builtin class (has `rawPtr`)       → pass `arg.rawPtr` directly
 *
 * ## Return type mapping
 * - `void` (null return type) → pass `null` as `r_return`
 * - `float` / `double`       → `alloc<DoubleVar>()`, read `.value` after invoke
 * - `int`                    → `alloc<LongVar>()`, read `.value` after invoke
 * - `bool`                   → `allocGdBool()`, read via `readGdBool()` after invoke
 */
class UtilityFunctionImplGen(private val delegate: BodyGenerator) {
    private lateinit var implPackageRegistry: ImplementationPackageRegistry

    fun initialize(implRegistry: ImplementationPackageRegistry) {
        implPackageRegistry = implRegistry
    }

    // ── Property: lazy GDExtensionPtrUtilityFunction ──────────────────────────

    context(context: Context)
    fun buildFunctionPointerProperty(fn: UtilityFunction): PropertySpec {
        val ptrUtilityFunctionType = implPackageRegistry.classNameForOrDefault("GDExtensionPtrUtilityFunction")
        val variantBindingClass = implPackageRegistry.classNameForOrDefault("VariantBinding")
        val stringNameClass = context.classNameForOrDefault("StringName")

        val bodyCode = CodeBlock
            .builder()
            .beginControlFlow("%T(%S).use { name ->", stringNameClass, fn.name)
            .addStatement(
                "%T.instance",
                variantBindingClass,
            )
            .indent()
            .addStatement(".getPtrUtilityFunctionRaw(name.rawPtr, %LL)", fn.hash)
            .addStatement("?: error(%S)", "Missing utility function '${fn.name}'")
            .unindent()
            .endControlFlow()
            .build()

        return PropertySpec
            .builder(functionPointerName(fn), ptrUtilityFunctionType, KModifier.PRIVATE)
            .delegate(
                CodeBlock
                    .builder()
                    .beginControlFlow("%M(PUBLICATION)", lazyMethod)
                    .add(bodyCode)
                    .endControlFlow()
                    .build(),
            )
            .build()
    }

    // ── Body Implementation ───────────────────────────────────────────────────

    context(context: Context)
    fun buildFunctionBody(fn: UtilityFunction): CodeBlock {
        val allocConstTypePtrArray = implPackageRegistry.memberNameForOrDefault("allocConstTypePtrArray")
        val propName = functionPointerName(fn)

        if (fn.isVararg) {
            return buildVarargBody(fn, propName, allocConstTypePtrArray)
        }

        return buildFixedArgsBody(fn, propName, allocConstTypePtrArray)
    }

    private fun buildVarargBody(fn: UtilityFunction, propName: String, allocConstTypePtrArray: MemberName): CodeBlock {
        // ... (Mantenido igual por ahora ya que requiere infraestructura de Variant)
        return delegate.todoBody()
    }

    context(ctx: Context)
    private fun buildFixedArgsBody(
        fn: UtilityFunction,
        propName: String,
        allocConstTypePtrArray: MemberName,
    ): CodeBlock {
        if (!fn.arguments.all { isMarshallable(it) }) return delegate.todoBody()

        val returnType = fn.returnType
        if (returnType != null && !isReturnMarshallable(returnType)) return delegate.todoBody()

        return CodeBlock.builder().apply {
            if (returnType != null) add("return ")
            beginControlFlow("%M", memScoped)

            // 1. Alloc return buffer
            if (returnType != null) add(buildReturnAlloc(returnType))

            // 2. Alloc arguments (Asignación manual, no also)
            fn.arguments.forEach { arg -> add(buildArgAlloc(arg)) }

            // 3. Invoke logic
            val argExpressions = fn.arguments.joinToCode(", ") { arg -> argPointerExpression(arg) }
            val retExpression = when (returnType) {
                null -> CodeBlock.of("null")
                "bool" -> CodeBlock.of("retPtr")
                else -> CodeBlock.of("retPtr.%M.%M()", cinteropPtr, cinteropReinterpret)
            }

            if (fn.arguments.isEmpty()) {
                addStatement("%N.%M(%L, null, 0)", propName, cinteropInvoke, retExpression)
            } else {
                addStatement(
                    "%N.%M(%L, %M(%L), %L)",
                    propName,
                    cinteropInvoke,
                    retExpression,
                    allocConstTypePtrArray,
                    argExpressions,
                    fn.arguments.size,
                )
            }

            // 4. Read return
            if (returnType != null) add(buildReturnRead(returnType))

            endControlFlow()
        }.build()
    }

    // ── Marshalling Helpers ───────────────────────────────────────────────────

    context(ctx: Context)
    private fun isMarshallable(arg: MethodArg): Boolean = when {
        arg.type == "Variant" -> false
        arg.type.startsWith("enum::") || arg.type.startsWith("bitfield::") -> false
        isGodotPrimitive(arg.type) || ctx.isBuiltin(arg.type) -> true
        else -> false
    }

    private fun isReturnMarshallable(returnType: String): Boolean = when {
        returnType == "Variant" -> false
        isGodotPrimitive(returnType) -> true
        else -> false // Builtins require ptr constructor
    }

    private fun isGodotPrimitive(type: String): Boolean = type == "float" || type == "int" || type == "bool"

    private fun buildArgAlloc(arg: MethodArg): CodeBlock {
        val name = safeIdentifier(arg.name)
        val builder = CodeBlock.builder()
        when (arg.type) {
            "float", "double", "int" -> {
                val cType = when (arg.type) {
                    "float" -> FLOAT_VAR
                    "double" -> DOUBLE_VAR
                    "int" -> INT_VAR
                    else -> error("Invalid type: ${arg.type}")
                }
                builder.addStatement("val %LVar = %M<%T>()", name, cinteropAlloc, cType)
                builder.addStatement("%LVar.%M = %N", name, cinteropValue, name)
            }

            "bool" -> {
                builder.addStatement(
                    "val %LVar = %M(%N)",
                    name,
                    implPackageRegistry.memberNameForOrDefault("allocGdBool"),
                    name,
                )
            }
        }
        return builder.build()
    }

    context(ctx: Context)
    private fun argPointerExpression(arg: MethodArg): CodeBlock {
        val name = safeIdentifier(arg.name)
        return when {
            isGodotPrimitive(arg.type) -> CodeBlock.of("%LVar.%M.%M()", name, cinteropPtr, cinteropReinterpret)
            ctx.isBuiltin(arg.type) -> CodeBlock.of("%N.rawPtr", name)
            else -> error("Invalid type: ${arg.type}")
        }
    }

    private fun buildReturnAlloc(returnType: String): CodeBlock {
        val builder = CodeBlock.builder()
        when (returnType) {
            "float" -> builder.addStatement("val retPtr = %M<%T>()", cinteropAlloc, FLOAT_VAR)

            "double" -> builder.addStatement("val retPtr = %M<%T>()", cinteropAlloc, DOUBLE_VAR)

            "int" -> builder.addStatement("val retPtr = %M<%T>()", cinteropAlloc, INT_VAR)

            "bool" -> builder.addStatement(
                "val retPtr = %M()",
                implPackageRegistry.memberNameForOrDefault("allocGdBool"),
            )
        }
        return builder.build()
    }

    private fun buildReturnRead(returnType: String): CodeBlock = when (returnType) {
        "float", "double", "int" -> CodeBlock.builder().addStatement("retPtr.%M", cinteropValue).build()

        "bool" -> CodeBlock.builder().addStatement(
            "retPtr.%M()",
            implPackageRegistry.memberNameForOrDefault("readGdBool"),
        ).build()

        else -> CodeBlock.of("")
    }

    private fun functionPointerName(fn: UtilityFunction) = safeIdentifier(fn.name) + "Fn"
}
