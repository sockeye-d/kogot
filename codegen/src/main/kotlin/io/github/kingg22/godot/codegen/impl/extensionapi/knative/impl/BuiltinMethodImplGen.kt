package io.github.kingg22.godot.codegen.impl.extensionapi.knative.impl

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.joinToCode
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.*
import io.github.kingg22.godot.codegen.impl.renameGodotClass
import io.github.kingg22.godot.codegen.impl.safeIdentifier
import io.github.kingg22.godot.codegen.models.extensionapi.BuiltinClass
import io.github.kingg22.godot.codegen.models.extensionapi.MethodArg

/**
 * Generates lazy-loaded fptr properties and invocation bodies for builtin class methods.
 *
 * Mirrors [UtilityFunctionImplGen] but targets `GDExtensionPtrBuiltInMethod`:
 *   `(p_base, p_args, r_return, p_argument_count) → Unit`
 *
 * - **Static methods**: `p_base = null`
 * - **Instance methods**: `p_base = rawPtr`
 *
 * The fptr is loaded via `VariantBinding.instance.getPtrBuiltinMethodRaw(variantType, name, hash)`.
 * Properties are emitted as top-level `private val` lazy delegates in the class file.
 */
class BuiltinMethodImplGen {
    private lateinit var implPackageRegistry: ImplementationPackageRegistry

    fun initialize(implRegistry: ImplementationPackageRegistry) {
        implPackageRegistry = implRegistry
    }

    // ── Top-level lazy fptr property ──────────────────────────────────────────

    context(context: Context)
    fun buildMethodFptrProperty(
        method: BuiltinClass.BuiltinMethod,
        variantType: String,
        className: String,
    ): PropertySpec {
        val ptrType = implPackageRegistry.classNameForOrDefault("GDExtensionPtrBuiltInMethod")
        val variantBinding = implPackageRegistry.classNameForOrDefault("VariantBinding")
        val stringNameClass = context.classNameForOrDefault("StringName")

        val body = buildLazyBlock {
            beginControlFlow("%T(%S).use { name ->", stringNameClass, method.name)
                .addStatement(
                    "%T.instance.getPtrBuiltinMethodRaw(%N, name.rawPtr, %LL)",
                    variantBinding,
                    variantType,
                    method.hash,
                )
                .withIndent {
                    addStatement(
                        "?: error(%S)",
                        "Missing builtin method '$className.${method.name}' hash: ${method.hash}",
                    )
                }
            endControlFlow()
        }

        return PropertySpec
            .builder(methodFptrName(className, method), ptrType, KModifier.PRIVATE)
            .delegate(body)
            .build()
    }

    // ── Method body ───────────────────────────────────────────────────────────

    context(context: Context)
    fun buildMethodBody(method: BuiltinClass.BuiltinMethod, className: String): CodeBlock {
        val propName = methodFptrName(className, method)
        if ((className == "Callable" || className == "Signal") && (method.name == "get_object")) {
            return CodeBlock.of("return TODO(%S)", "Object return type is not yet supported")
        }
        if ((className == "Dictionary" && method.name == "has") || (className == "Array" && method.name == "set") || (
                className == "Dictionary" && (
                    method.name == "erase" ||
                        method.name == "get" ||
                        method.name == "get_or_add" ||
                        method.name == "set"
                    )
                )
        ) {
            return CodeBlock.of("return TODO(%S)", "Generic types are not yet supported for this method")
        }
        return buildFixedArgsBody(method, propName)
    }

    context(ctx: Context)
    private fun buildFixedArgsBody(method: BuiltinClass.BuiltinMethod, propName: String): CodeBlock = buildCodeBlock {
        val returnType = method.returnType
        if (returnType != null && returnType != "void") add("return ")

        beginControlFlow("%M", memScoped)

        // 1. Alloc return buffer
        if (returnType != null && returnType != "void") add(buildReturnAlloc(returnType))

        // 2. Alloc primitive args
        method.arguments.forEach { arg -> add(buildArgAlloc(arg)) }

        // 3. Build p_base expression
        val pBase = if (method.isStatic) "null" else "rawPtr"

        // 4. Build r_return expression
        val rReturn = when {
            returnType == null || returnType == "void" -> "null"

            returnType == "bool" -> "retPtr"

            isGodotPrimitive(returnType) -> "retPtr.%M"

            // .ptr
            ctx.isBuiltin(returnType) -> "retPtr.rawPtr"

            ctx.findEngineClass(returnType) != null -> "retPtr.%M.%M()"

            // .ptr.reinterpret()
            else -> "null"
        }

        // 4. Invoke
        val argExpressions = method.arguments.joinToCode("") { arg -> argPointerExpression(arg) }

        if (method.arguments.isEmpty()) {
            if (rReturn == "retPtr.%M" || rReturn == "retPtr.%M.%M()") {
                addStatement(
                    "%N.%M(%L, null, retPtr.%M, 0)",
                    propName,
                    cinteropInvoke,
                    pBase,
                    cinteropPtr,
                )
            } else {
                addStatement("%N.%M(%L, null, %L, 0)", propName, cinteropInvoke, pBase, rReturn)
            }
        } else {
            val allocConstTypePtrArray = implPackageRegistry.memberNameForOrDefault("allocConstTypePtrArray")

            addStatement("%N.%M(", propName, cinteropInvoke)

            withIndent {
                addStatement("%L,", pBase)
                addStatement("%M(", allocConstTypePtrArray)
                withIndent { add(argExpressions) }
                addStatement("),")

                if (rReturn == "retPtr.%M" || rReturn == "retPtr.%M.%M()") {
                    addStatement("retPtr.%M,", cinteropPtr)
                } else {
                    addStatement("%L,", rReturn)
                }

                addStatement("%L,", method.arguments.size)
            }
            addStatement(")")
        }

        // 5. Read return
        if (returnType != null && returnType != "void") add(buildReturnRead(returnType))

        endControlFlow()
    }

    private fun buildArgAlloc(arg: MethodArg): CodeBlock = buildCodeBlock {
        val name = safeIdentifier(arg.name)
        when (arg.type) {
            "float", "double" -> {
                addStatement("val %LVar = %M<%T>()", name, cinteropAlloc, DOUBLE_VAR)
                addStatement("%LVar.%M = %N", name, cinteropValue, name)
            }

            "int" -> {
                addStatement("val %LVar = %M<%T>()", name, cinteropAlloc, LONG_VAR)
                addStatement("%LVar.%M = %N", name, cinteropValue, name)
            }

            "bool" -> {
                addStatement(
                    "val %LVar = %M(%N)",
                    name,
                    implPackageRegistry.memberNameForOrDefault("allocGdBool"),
                    name,
                )
            }
            // builtin classes and everything else — rawPtr passed directly, no alloc needed
        }
    }

    context(ctx: Context)
    private fun argPointerExpression(arg: MethodArg): CodeBlock {
        val name = safeIdentifier(arg.name)
        return when {
            arg.type == "float" || arg.type == "double" || arg.type == "int" -> CodeBlock.ofStatement(
                "%LVar.%M,",
                name,
                cinteropPtr,
            )

            arg.type == "bool" -> CodeBlock.ofStatement("%LVar,", name)

            ctx.isBuiltin(arg.type) || arg.type == "Variant" -> CodeBlock.ofStatement("%N.rawPtr,", name)

            arg.type.startsWith("enum::") -> CodeBlock.ofStatement(
                "%M<%T>().also·{ it.%M = %N.value }.%M,",
                cinteropAlloc,
                LONG_VAR,
                cinteropValue,
                name,
                cinteropPtr,
            )

            else -> CodeBlock.ofStatement("%N.rawPtr,", name)
        }
    }

    context(ctx: Context)
    private fun buildReturnAlloc(returnType: String): CodeBlock = buildCodeBlock {
        when {
            returnType == "float" || returnType == "double" ->
                addStatement("val retPtr = %M<%T>()", cinteropAlloc, DOUBLE_VAR)

            returnType == "int" ->
                addStatement("val retPtr = %M<%T>()", cinteropAlloc, LONG_VAR)

            returnType == "bool" ->
                addStatement(
                    "val retPtr = %M()",
                    implPackageRegistry.memberNameForOrDefault("allocGdBool"),
                )

            ctx.isBuiltin(returnType) ->
                addStatement(
                    "val retPtr = %T()",
                    ctx.classNameForOrDefault(returnType.renameGodotClass()),
                )

            ctx.findEngineClass(returnType) != null ->
                addStatement("val retPtr = %M<%T>()", cinteropAlloc, C_OPAQUE_POINTER_VAR)

            else -> error("Unknown return type for builtin method: '$returnType'")
        }
    }

    context(ctx: Context)
    private fun buildReturnRead(returnType: String): CodeBlock = when {
        returnType == "float" || returnType == "double" || returnType == "int" ->
            CodeBlock.ofStatement("retPtr.%M", cinteropValue)

        returnType == "bool" ->
            CodeBlock.ofStatement(
                "retPtr.%M()",
                implPackageRegistry.memberNameForOrDefault("readGdBool"),
            )

        ctx.isBuiltin(returnType) -> CodeBlock.ofStatement("retPtr")

        ctx.findEngineClass(returnType) != null -> CodeBlock.ofStatement(
            "retPtr.value?.let { %T(it) }",
            ctx.classNameForOrDefault(returnType.renameGodotClass()),
        )

        else -> CodeBlock.ofStatement("retPtr")
    }

    private fun isGodotPrimitive(type: String) = type == "float" || type == "double" || type == "int" || type == "bool"

    fun methodFptrName(className: String, method: BuiltinClass.BuiltinMethod): String =
        "method${className}${safeIdentifier(method.name).replaceFirstChar(Char::uppercase)}_${method.hash.toULong()}_Fn"
}
