package io.github.kingg22.godot.codegen.impl.extensionapi.native.impl

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.native.*
import io.github.kingg22.godot.codegen.impl.renameGodotClass

/**
 * Native implementation generator for the `Variant` sealed class.
 *
 * The standard [io.github.kingg22.godot.codegen.impl.extensionapi.native.generators.NativeVariantGenerator]
 * generates a pure-API sealed class that has no memory
 * backing — just wrapper subclasses holding Kotlin values. This class augments it with a proper
 * GDExtension memory lifecycle, so each `Variant` instance owns a Godot Variant allocation.
 *
 * ## What gets added to the sealed class
 *
 * ```kotlin
 * sealed class Variant private constructor(
 *     private val storage: CPointer<ByteVar>,
 * ) : AutoCloseable {
 *     private var closed: Boolean = false
 *
 *     internal val rawPtr: COpaquePointer
 *         get() = storage
 *
 *     override fun close() {
 *         if (!closed) {
 *             VariantBinding.instance.destroyRaw(rawPtr)
 *             freeBuiltinStorage(storage)
 *             closed = true
 *         }
 *     }
 * }
 * ```
 *
 * ## NIL becomes a `class`
 *
 * The base generator produces `NIL` as an `object`, but an `object` cannot hold per-instance
 * native storage. With this generator, `NIL` becomes a regular `class` that allocates a Godot
 * Variant and initialises it via `VariantBinding.instance.newNilRaw(rawPtr)`.
 *
 * ## Subclass constructor bodies
 *
 * Each typed subclass calls `super(allocateBuiltinStorage(variantSize))` and then populates the
 * Godot Variant through the appropriate GDExtension path:
 *
 * | Subclass  | GDExtension path                                                        |
 * |-----------|-------------------------------------------------------------------------|
 * | `NIL`     | `variant_new_nil`                                                       |
 * | `BOOL`    | `variant_construct(BOOL, rawPtr, [allocGdBool(value)], 1, null)`        |
 * | `INT`     | `variant_construct(INT, rawPtr, [LongVar(value).ptr], 1, null)`         |
 * | `FLOAT`   | `variant_construct(FLOAT, rawPtr, [DoubleVar(value).ptr], 1, null)`     |
 * | STRING … PACKED_VECTOR4_ARRAY | `variant_construct(TYPE, rawPtr, `value.rawPtr`, 1, null)` |
 * | `OBJECT`  | deferred — `TODO()`                                                     |
 *
 * ## Variant size
 *
 * `Variant` is NOT a `BuiltinClass` in the extension API JSON, so
 * `context.findResolvedBuiltinClass("Variant")` returns `null`.
 * The size is read directly from `context.extensionApi.builtinClassSizes`.
 */
class VariantImplGen {
    private lateinit var implPackageRegistry: ImplementationPackageRegistry

    /** Must be called before any generate* method. */
    fun initialize(implRegistry: ImplementationPackageRegistry) {
        implPackageRegistry = implRegistry
    }

    // ── Sealed class augmentation ─────────────────────────────────────────────

    /**
     * Augments [classBuilder] (which already has `SEALED` modifier) with:
     * - private primary constructor `(storage: CPointer<ByteVar>)`
     * - `storage` property
     * - `closed` guard
     * - `rawPtr` property
     * - `AutoCloseable` superinterface
     * - `close()` implementation
     *
     * Also caches the Variant size for use in [buildNilSubclass] and [buildSubclassConstructorBody].
     */
    context(context: Context)
    fun configureVariantClass(classBuilder: TypeSpec.Builder) {
        cachedVariantSize = resolveVariantSize(context)

        val storageType = C_POINTER.parameterizedBy(BYTE_VAR)
        val storageProp = PropertySpec
            .builder("storage", storageType, KModifier.PRIVATE)
            .initializer("storage")
            .build()

        classBuilder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addModifiers(KModifier.PRIVATE)
                .addParameter(ParameterSpec.builder("storage", storageType).build())
                .build(),
        )
        classBuilder.addProperty(storageProp)
        classBuilder.addProperty(
            PropertySpec.builder("closed", BOOLEAN, KModifier.PRIVATE)
                .mutable(true)
                .initializer("false")
                .build(),
        )
        classBuilder.addProperty(
            PropertySpec.builder("rawPtr", COPAQUE_POINTER, KModifier.INTERNAL)
                .getter(FunSpec.getterBuilder().addStatement("return %N", storageProp).build())
                .build(),
        )

        classBuilder.addSuperinterface(ClassName("kotlin", "AutoCloseable"))

        val variantBinding = implPackageRegistry.classNameForOrDefault("VariantBinding")
        val freeBuiltinStorage = implPackageRegistry.memberNameForOrDefault("freeBuiltinStorage")

        classBuilder.addFunction(
            FunSpec.builder("close")
                .addModifiers(KModifier.OVERRIDE)
                .addCode(
                    CodeBlock.builder()
                        .beginControlFlow("if (!closed)")
                        .addStatement("%T.instance.destroyRaw(rawPtr)", variantBinding)
                        .addStatement("%M(%N)", freeBuiltinStorage, "storage")
                        .addStatement("closed = true")
                        .endControlFlow()
                        .build(),
                )
                .build(),
        )
    }

    // ── NIL subclass ──────────────────────────────────────────────────────────

    /**
     * Builds the `NIL` subclass as a `class` (not an `object`).
     *
     * ```kotlin
     * class NIL : Variant(allocateBuiltinStorage(<size>)) {
     *     init {
     *         VariantBinding.instance.newNilRaw(rawPtr)
     *     }
     * }
     * ```
     *
     * Must be called after [configureVariantClass].
     */
    fun buildNilSubclass(variantClassName: ClassName): TypeSpec.Builder {
        val variantSize = requireCachedSize()
        val allocStorage = implPackageRegistry.memberNameForOrDefault("allocateBuiltinStorage")
        val variantBinding = implPackageRegistry.classNameForOrDefault("VariantBinding")

        return TypeSpec.classBuilder("NIL")
            .superclass(variantClassName)
            .addSuperclassConstructorParameter(
                CodeBlock.of("%M(%L)", allocStorage, variantSize),
            )
            .addInitializerBlock(
                CodeBlock.builder()
                    .addStatement("%T.instance.newNilRaw(rawPtr)", variantBinding)
                    .build(),
            )
    }

    // ── Typed subclass constructors ───────────────────────────────────────────

    /**
     * Augments a typed subclass builder's constructor with:
     * 1. A `callSuperConstructor(allocateBuiltinStorage(variantSize))` delegation.
     * 2. Returns a [CodeBlock] to use as the constructor's `init` block (or body).
     *
     * Returns `null` for [subclassName]s that are not yet supported (e.g. `OBJECT`), so the
     * caller can fall back to `TODO()`.
     *
     * @param subclassName   The screaming-snake subclass name, e.g. `"BOOL"`, `"STRING"`.
     * @param godotTypeName  The PascalCase Godot type name, e.g. `"Bool"`, `"String"`.
     * @return The body block to use as the constructor's `init` block and the super call
     */
    context(context: Context)
    fun buildSubclassConstructorBody(subclassName: String, godotTypeName: String): Pair<CodeBlock?, CodeBlock> {
        val variantSize = requireCachedSize()
        val allocStorage = implPackageRegistry.memberNameForOrDefault("allocateBuiltinStorage")
        val variantBinding = implPackageRegistry.classNameForOrDefault("VariantBinding")
        val allocConstTypePtrArray = implPackageRegistry.memberNameForOrDefault("allocConstTypePtrArray")
        val allocGdBool = implPackageRegistry.memberNameForOrDefault("allocGdBool")

        val bodyCode = when (subclassName) {
            // ── Primitives ────────────────────────────────────────────────────

            "BOOL" -> CodeBlock.builder()
                .beginControlFlow("%M", memScoped)
                .addStatement("val boolVar = %M(value)", allocGdBool)
                .addStatement(
                    "%T.instance.constructRaw(%L, rawPtr, %M(boolVar), 1, null)",
                    variantBinding,
                    "GDEXTENSION_VARIANT_TYPE_BOOL",
                    allocConstTypePtrArray,
                )
                .endControlFlow()
                .build()

            "INT" -> CodeBlock.builder()
                .beginControlFlow("%M", memScoped)
                .addStatement(
                    "val intVar = %M<%T>().also { it.%M = value.toLong() }",
                    cinteropAlloc,
                    LONG_VAR,
                    cinteropValue,
                )
                .addStatement(
                    "%T.instance.constructRaw(%L, rawPtr, %M(intVar.%M.%M()), 1, null)",
                    variantBinding,
                    "GDEXTENSION_VARIANT_TYPE_INT",
                    allocConstTypePtrArray,
                    cinteropPtr,
                    cinteropReinterpret,
                )
                .endControlFlow()
                .build()

            "FLOAT" -> CodeBlock.builder()
                .beginControlFlow("%M", memScoped)
                .addStatement(
                    "val floatVar = %M<%T>().also { it.%M = value.toDouble() }",
                    cinteropAlloc,
                    DOUBLE_VAR,
                    cinteropValue,
                )
                .addStatement(
                    "%T.instance.constructRaw(%L, rawPtr, %M(floatVar.%M.%M()), 1, null)",
                    variantBinding,
                    "GDEXTENSION_VARIANT_TYPE_FLOAT",
                    allocConstTypePtrArray,
                    cinteropPtr,
                    cinteropReinterpret,
                )
                .endControlFlow()
                .build()

            // ── GodotObject — deferred ────────────────────────────────────────
            "OBJECT" -> null

            // ── All builtin class subclasses (STRING, VECTOR2, …, PACKED_*) ─
            else -> {
                // Verify the resolved Kotlin type exposes rawPtr (i.e. it's a builtin class).
                // Types that DON'T have rawPtr: primitives already handled above, enums, etc.
                val resolvedGodotName = godotTypeName.renameGodotClass()
                val isKnownBuiltin =
                    context.isBuiltin(resolvedGodotName) || context.isBuiltin(godotTypeName)

                if (!isKnownBuiltin) return null to CodeBlock.of("%M(%L)", allocStorage, variantSize)

                val variantTypeConst = "GDEXTENSION_VARIANT_TYPE_$subclassName"
                CodeBlock.builder()
                    .beginControlFlow("%M", memScoped)
                    .addStatement(
                        "%T.instance.constructRaw(%L, rawPtr, %M(value.rawPtr), 1, null)",
                        variantBinding,
                        variantTypeConst,
                        allocConstTypePtrArray,
                    )
                    .endControlFlow()
                    .build()
            }
        }

        // Wire constructor → super(allocateBuiltinStorage(variantSize))
        return bodyCode to CodeBlock.of("%M(%L)", allocStorage, variantSize)
    }

    // ── Variant size ──────────────────────────────────────────────────────────

    /**
     * Looks up the Variant size from `builtinClassSizes` in the extension API JSON.
     *
     * `Variant` is present in `builtin_class_sizes` but NOT in `builtin_classes`,
     * so `context.findResolvedBuiltinClass("Variant")` always returns null.
     */
    private fun resolveVariantSize(context: Context): Int {
        val buildConfigName = context.model.buildConfiguration.jsonName
        return context.extensionApi.builtinClassSizes
            .firstOrNull { it.buildConfiguration == buildConfigName }
            ?.sizes
            ?.firstOrNull { it.name == "Variant" }
            ?.size
            ?: error("Cannot find Variant size for build config '$buildConfigName'")
    }

    private fun requireCachedSize(): Int {
        check(cachedVariantSize > 0) {
            "VariantImplGen: configureVariantClass() must be called before building subclasses"
        }
        return cachedVariantSize
    }

    // State set by configureVariantClass so subclass helpers don't need to re-resolve it
    private var cachedVariantSize: Int = -1
}
