package io.github.kingg22.godot.codegen.impl.extensionapi.knative.impl

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.PRIMITIVE_NUMERIC_TYPES
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.generators.BodyGenerator
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.generators.addKdocIfPresent
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.generators.experimentalApiAnnotation
import io.github.kingg22.godot.codegen.impl.safeIdentifier
import io.github.kingg22.godot.codegen.impl.withExceptionContext
import io.github.kingg22.godot.codegen.models.extensionapi.EngineClass
import io.github.kingg22.godot.codegen.models.extensionapi.domain.ResolvedEngineClass

// ===============================
// Unified Property Generation
// ===============================
class EnginePropertyImplGen(private val typeResolver: TypeResolver, private val body: EngineMethodImplGen) {

    /**
     * Estrategias de generación de properties en Godot:
     *
     * 1. DIRECT:
     *    Getter/Setter existen como métodos reales en la clase.
     *
     * 2. DELEGATED:
     *    Getter/Setter existen en la clase padre (no en methods locales).
     *
     * 3. INDEXED:
     *    Property usa un método compartido con índice (ej: get_anchor(Side)).
     *
     * 4. FALLBACK:
     *    No existen métodos reales → generar TODO / acceso inseguro.
     *
     * Nota:
     * El JSON de Godot puede referenciar métodos inexistentes (_get/_set),
     * por lo que SIEMPRE se debe verificar contra methods reales.
     */
    context(_: Context)
    fun generateProperties(
        cls: ResolvedEngineClass,
        methods: List<EngineClass.ClassMethod>,
        classBuilder: TypeSpec.Builder,
    ): List<EngineClass.ClassMethod> {
        val usedMethodNames = mutableSetOf<String>()

        cls.raw.properties.forEach { property ->
            withExceptionContext({ "Error generating property '${property.name}'" }) {
                val resolved = resolvePropertyAccessors(property, methods, cls)

                if (resolved.strategy == PropertyStrategy.FALLBACK &&
                    resolved.getter == null &&
                    resolved.setter == null
                ) {
                    return@withExceptionContext
                }

                val spec = buildProperty(property, resolved, cls)
                classBuilder.addProperty(spec)

                if (resolved.strategy == PropertyStrategy.DIRECT && property.index == null) {
                    resolved.getter?.name?.let { usedMethodNames.add(it) }
                    resolved.setter?.name?.let { usedMethodNames.add(it) }
                }
            }
        }

        return methods.filterNot { it.name in usedMethodNames }
    }

    // ===============================
    // Resolution Phase
    // ===============================

    private enum class PropertyStrategy { DIRECT, DELEGATED, INDEXED, FALLBACK, }

    private data class ResolvedProperty(
        val getter: EngineClass.ClassMethod?,
        val setter: EngineClass.ClassMethod?,
        val strategy: PropertyStrategy,
    )

    context(_: Context)
    private fun resolvePropertyAccessors(
        property: EngineClass.ClassProperty,
        methods: List<EngineClass.ClassMethod>,
        engineClass: ResolvedEngineClass,
    ): ResolvedProperty {
        fun findMethod(name: String?): Pair<EngineClass.ClassMethod?, Boolean> {
            if (name == null) return null to false

            // 1. Exact match
            methods.find { it.name == name }?.let { return it to true }

            // 2. Search in parent classes
            engineClass.allMethods
                .find { it.name == name && !it.isStatic }
                ?.let { return it to false }

            return null to false
        }

        val (getter, isLocalGetter) = findMethod(property.getter)
        val (setter) = findMethod(property.setter)

        // INDEXED strategy
        if (property.index != null && getter != null) {
            val setter = setter ?: findMethod(property.setter?.removePrefix("_")).first
            return ResolvedProperty(getter, setter, PropertyStrategy.INDEXED)
        }

        // DIRECT strategy
        if (getter != null && isLocalGetter && (property.setter == null || (setter != null))) {
            return ResolvedProperty(getter, setter, PropertyStrategy.DIRECT)
        }

        // DELEGATED strategy (found only in parent)
        if (getter != null && !isLocalGetter) {
            val setter = setter ?: findMethod(property.setter?.removePrefix("_")).first
            return ResolvedProperty(getter, setter, PropertyStrategy.DELEGATED)
        }

        // FALLBACK
        println(
            buildString {
                append("WARNING: Fallback generation for property ")
                append(engineClass.name)
                append(".")
                append(property.name)
                append(", ")
                append("getter (expected: ")
                append(property.getter)
                append("): ")
                append(getter?.name)
                if (property.setter != null) {
                    append(", setter (expected: ")
                    append(property.setter)
                    append("): ")
                    append(setter?.name)
                }
            },
        )
        return ResolvedProperty(getter, setter, PropertyStrategy.FALLBACK)
    }

    // ===============================
    // Build Phase
    // ===============================

    context(context: Context)
    private fun buildProperty(
        property: EngineClass.ClassProperty,
        resolved: ResolvedProperty,
        engineClass: ResolvedEngineClass,
    ): PropertySpec {
        val className = engineClass.name
        val kotlinName = safeIdentifier(property.name)

        val propertyType = resolved.getter?.returnValue?.let {
            typeResolver.resolve(it)
        } ?: resolved.setter?.arguments?.lastOrNull()?.let {
            typeResolver.resolve(it)
        } ?: typeResolver.resolve(property.type)

        val builder = PropertySpec
            .builder(kotlinName, propertyType)
            .mutable(property.setter != null)
            .experimentalApiAnnotation(className, property.name)
            .addKdocIfPresent(property)

        when (resolved.strategy) {
            PropertyStrategy.DIRECT -> buildDirect(property, resolved, builder, engineClass)
            PropertyStrategy.DELEGATED -> buildDelegated(property, resolved, builder, engineClass)
            PropertyStrategy.INDEXED -> buildIndexed(property, resolved, builder, engineClass)
            PropertyStrategy.FALLBACK -> buildFallback(property, resolved, builder, engineClass)
        }

        return builder.build()
    }

    // ===============================
    // Strategy Implementations
    // ===============================

    context(_: Context)
    private fun buildDirect(
        property: EngineClass.ClassProperty,
        resolved: ResolvedProperty,
        builder: PropertySpec.Builder,
        engineClass: ResolvedEngineClass,
    ) {
        val getter = resolved.getter!!

        builder.getter(
            FunSpec
                .getterBuilder()
                .addCode(body.buildPropertyGetterBody(getter, engineClass))
                .build(),
        )

        if (property.setter == null) return

        val setter = resolved.setter ?: run {
            builder.setter(
                buildFallbackGetter(
                    "value",
                    typeResolver.resolve(property.type),
                    engineClass.name,
                    property,
                    resolved.strategy,
                ),
            )
            return
        }

        check(setter.arguments.size == 1) {
            "Setter ${setter.name} must have exactly one argument, got ${setter.arguments.size}"
        }

        val lastArgument = setter.arguments.last()

        builder.setter(
            FunSpec
                .setterBuilder()
                .addParameter(safeIdentifier(lastArgument.name), typeResolver.resolve(lastArgument.type))
                .addCode(body.buildPropertySetterBody(setter, engineClass))
                .build(),
        )
    }

    context(_: Context)
    private fun buildDelegated(
        property: EngineClass.ClassProperty,
        resolved: ResolvedProperty,
        builder: PropertySpec.Builder,
        engineClass: ResolvedEngineClass,
    ) {
        val getter = resolved.getter!!

        builder.getter(
            FunSpec
                .getterBuilder()
                .addModifiers(KModifier.INLINE)
                .addStatement("return %N()", safeIdentifier(getter.name))
                .build(),
        )

        val setter = resolved.setter ?: run {
            if (property.setter != null) {
                builder.setter(
                    buildFallbackGetter(
                        "value",
                        typeResolver.resolve(property.type),
                        engineClass.name,
                        property,
                        resolved.strategy,
                    ),
                )
            }
            return
        }

        check(setter.arguments.size == 1) {
            "Setter ${setter.name} must have exactly one argument, got ${setter.arguments.size}"
        }

        val setterArg = setter.arguments.last()

        builder.setter(
            FunSpec
                .setterBuilder()
                .addModifiers(KModifier.INLINE)
                .addParameter(safeIdentifier(setterArg.name), typeResolver.resolve(setterArg.type))
                .addStatement("%N(%N)", safeIdentifier(setter.name), safeIdentifier(setterArg.name))
                .build(),
        )
    }

    context(context: Context)
    private fun buildIndexed(
        property: EngineClass.ClassProperty,
        resolved: ResolvedProperty,
        builder: PropertySpec.Builder,
        engineClass: ResolvedEngineClass,
    ) {
        val getter = resolved.getter!!

        val enumConstant = resolveIndexedPropertyConstant(getter, property.index!!)

        builder.getter(
            FunSpec
                .getterBuilder()
                .addModifiers(KModifier.INLINE)
                .addStatement("return %N(%L)", safeIdentifier(getter.name), enumConstant)
                .build(),
        )

        if (property.setter == null) return

        val setter = resolved.setter ?: run {
            builder.setter(
                buildFallbackGetter(
                    "value",
                    typeResolver.resolve(property.type),
                    engineClass.name,
                    property,
                    resolved.strategy,
                ),
            )
            return
        }

        val setterConstant = resolveIndexedPropertyConstant(setter, property.index)
        check(setter.arguments.size >= 2) {
            "Setter ${setter.name} must have at least two arguments, got ${setter.arguments.size}"
        }
        val lastArgument = setter.arguments.last()

        builder.setter(
            FunSpec
                .setterBuilder()
                .addModifiers(KModifier.INLINE)
                .addParameter(safeIdentifier(lastArgument.name), typeResolver.resolve(lastArgument.type))
                .addStatement(
                    "%N(%L, %N)",
                    safeIdentifier(setter.name),
                    setterConstant,
                    safeIdentifier(lastArgument.name),
                )
                .build(),
        )
    }

    context(_: Context)
    private fun buildFallback(
        property: EngineClass.ClassProperty,
        resolved: ResolvedProperty,
        builder: PropertySpec.Builder,
        engineClass: ResolvedEngineClass,
    ) {
        val className = engineClass.name

        builder.getter(
            BodyGenerator.todoGetter("Unknown getter for $className.${property.name} (expected ${property.getter})"),
        )

        if (property.setter == null) return

        builder.setter(
            buildFallbackGetter(
                "value",
                typeResolver.resolve(property.type),
                className,
                property,
                resolved.strategy,
            ),
        )
    }

    private fun buildFallbackGetter(
        paramName: String,
        paramType: TypeName,
        className: String,
        property: EngineClass.ClassProperty,
        strategy: PropertyStrategy,
    ) = FunSpec
        .setterBuilder()
        .addParameter(paramName, paramType)
        .addCode(
            BodyGenerator.todoBody(
                "Unknown setter for $className.${property.name} (expected ${property.setter}). Strategy: $strategy",
            ),
        )
        .build()

    /**
     * Resuelve el índice numérico a una referencia de enum constant.
     * @param method Método getter/setter indexado
     * @param indexValue Valor numérico del índice
     * @return String del constant qualified (ej. "Flags.DISABLE_FOG") o el valor raw si no se encuentra
     */
    context(context: Context)
    private fun resolveIndexedPropertyConstant(method: EngineClass.ClassMethod, indexValue: Int): CodeBlock {
        check(method.arguments.isNotEmpty()) {
            "Indexed property getter/setter must have at least one argument, got ${method.arguments.size}"
        }
        val firstArg = method.arguments.first()

        // Fast path, the first argument is a primitive type
        if (firstArg.type in PRIMITIVE_NUMERIC_TYPES) {
            return CodeBlock.of("%L", indexValue)
        }

        val enumTypeStr = firstArg.type.removePrefix("enum::")
        var className: String? = null
        val enumName = if (enumTypeStr.contains(".")) {
            className = enumTypeStr.substringBeforeLast(".")
            enumTypeStr.substringAfterLast(".")
        } else {
            enumTypeStr
        }

        // Use resolveConstantUnambiguous here: the index of an indexed property is a raw integer
        // passed verbatim to the getter/setter, so an alias collision is worth logging — it doesn't
        // affect runtime correctness (all aliases have the same Long value) but it makes the choice
        // of emitted name explicit and reviewable during generation.
        val constantName = context.resolveEnumConstantUnambiguous(
            parentClass = className,
            enumName = enumName,
            value = indexValue.toLong(),
            context = "indexed property, method '${method.name}', index $indexValue",
        ) ?: error("Enum constant not found: $enumTypeStr.$indexValue, resolved from $className.$enumName")

        val enumTypeName = typeResolver.resolve(firstArg.type)
        return CodeBlock.of("%T.%L", enumTypeName, constantName)
    }
}
