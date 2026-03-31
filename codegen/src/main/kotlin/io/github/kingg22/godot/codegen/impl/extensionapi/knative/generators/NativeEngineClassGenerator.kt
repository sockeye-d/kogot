package io.github.kingg22.godot.codegen.impl.extensionapi.knative.generators

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.kingg22.godot.codegen.impl.createFile
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.PRIMITIVE_NUMERIC_TYPES
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.impl.EngineClassImplGen
import io.github.kingg22.godot.codegen.impl.extensionapi.knative.impl.EngineMethodImplGen
import io.github.kingg22.godot.codegen.impl.renameGodotClass
import io.github.kingg22.godot.codegen.impl.safeIdentifier
import io.github.kingg22.godot.codegen.impl.withExceptionContext
import io.github.kingg22.godot.codegen.models.extensionapi.EngineClass
import io.github.kingg22.godot.codegen.models.extensionapi.domain.ResolvedEngineClass

class NativeEngineClassGenerator(
    private val typeResolver: TypeResolver,
    private val body: EngineMethodImplGen,
    private val methodGen: NativeMethodGenerator,
    private val enumGenerator: NativeEnumGenerator,
    private val engineClassImplGen: EngineClassImplGen,
) {

    context(context: Context)
    fun generateFile(cls: ResolvedEngineClass): FileSpec {
        val packageName = context.packageForOrDefault(cls.name)
        val spec = generateSpec(cls)
        return createFile(spec, spec.name!!, packageName) {
            addProperties(body.buildTopLevelFptrProperties(cls))
        }
    }

    context(context: Context)
    fun generateSpec(cls: ResolvedEngineClass): TypeSpec {
        val raw = cls.raw
        withExceptionContext({ "Generating class '${cls.name}'" }) {
            val classNameStr = cls.name.renameGodotClass()
            val packageName = context.packageForOrDefault(cls.name)
            val className = ClassName(packageName, classNameStr)
            val isSingleton = cls.isSingleton
            val classBuilder = buildBaseClass(cls, className, isSingleton)

            // Companion Object para Statics y Singleton Instance
            val companionBuilder = TypeSpec.companionObjectBuilder()

            engineClassImplGen.configureConstructor(cls, classBuilder, companionBuilder, className)

            val (staticMethods, instanceMethods) = raw.methods.partition { it.isStatic }

            // Sintetizar properties desde métodos getter/setter
            val standaloneMethods = generateProperties(cls, instanceMethods, classBuilder)

            // Métodos standalone (no forman parte de una property)
            standaloneMethods.forEach { method ->
                val methodSpec = methodGen.buildMethod(
                    method = method,
                    className = cls.name,
                    codeBody = body.buildMethodBody(method, cls.name),
                ) {
                    if (method.isVirtual) addModifiers(KModifier.OPEN)

                    if ((method.name == "get" && method.arguments.size == 1) ||
                        (method.name == "set" && method.arguments.size == 2)
                    ) {
                        addModifiers(KModifier.OPERATOR)
                    }
                }

                classBuilder.addFunction(methodSpec)
            }

            raw.constants.forEach { constant ->
                withExceptionContext({ "Error generating class constant '${constant.name}'" }) {
                    companionBuilder.addProperty(
                        PropertySpec
                            .builder(constant.name, LONG, KModifier.CONST)
                            .initializer("%L", constant.value)
                            .addKdocIfPresent(constant)
                            .experimentalApiAnnotation(cls.name, constant.name)
                            .build(),
                    )
                }
            }

            staticMethods.forEach { method ->
                companionBuilder.addFunction(
                    methodGen.buildMethod(method, cls.name, codeBody = body.buildMethodBody(method, cls.name)),
                )
            }

            if (isSingleton || raw.constants.isNotEmpty() || staticMethods.isNotEmpty()) {
                classBuilder.addType(companionBuilder.build())
            }

            cls.enums.forEach { enum ->
                if (context.isSpecializedClass(enum.shortName)) return@forEach
                val enumSpec = enumGenerator.generateSpec(enum)
                classBuilder.addType(enumSpec)
                if (enum.shortName != enumSpec.name && enum.shortName.all { it.isUpperCase() }) {
                    classBuilder.addTypeAlias(
                        TypeAliasSpec
                            .builder(enum.shortName, className.nestedClass(enumSpec.name!!))
                            .build(),
                    )
                }
            }

            return classBuilder.build()
        }
    }

    /**
     * Genera properties Kotlin desde cls.properties.
     *
     * @return Set de nombres de métodos no usados
     */
    context(_: Context)
    private fun generateProperties(
        cls: ResolvedEngineClass,
        methods: List<EngineClass.ClassMethod>,
        classBuilder: TypeSpec.Builder,
    ): List<EngineClass.ClassMethod> {
        val usedMethodNames = mutableSetOf<String>()

        cls.raw.properties.forEach { property ->
            withExceptionContext({ "Error generating property '${property.name}'" }) {
                val propertySpec = synthesizeProperty(property, methods, cls)
                classBuilder.addProperty(propertySpec)

                // Marcar getter/setter como usados solo si son properties standalone (sin delegación a methods)
                if (property.index == null) {
                    usedMethodNames.add(property.getter)
                    property.setter?.let { usedMethodNames.add(it) }
                }
            }
        }

        return methods.filterNot { it.name in usedMethodNames }
    }

    context(context: Context)
    private fun synthesizeProperty(
        property: EngineClass.ClassProperty,
        methods: List<EngineClass.ClassMethod>,
        engineClass: ResolvedEngineClass,
    ): PropertySpec {
        val className = engineClass.name
        val safeName = safeIdentifier(property.name)

        // Buscar el método getter para obtener el tipo exacto
        val setterMethod = property.setter?.let { setter ->
            methods.find { it.name == setter }
                ?: engineClass.methods.find { it.name == setter }?.takeUnless { it.isStatic }
                    ?.let { return generateDelegatedProperty(property) }
        }

        val getterMethod = methods.find { it.name == property.getter }
            ?: engineClass.methods.find { it.name == property.getter }?.takeUnless { it.isStatic }
                ?.let { return generateDelegatedProperty(property) }

        // Fallback generation when missing the method
        if (getterMethod == null || (property.setter != null && setterMethod == null)) {
            // FIXME: enable with logger.debug/verbose
            println(
                buildString {
                    append("WARNING: Fallback generation for property ")
                    append(className)
                    append(".")
                    append(property.name)
                    append(", ")

                    append("getter (expected: ")
                    append(property.getter)
                    append("): ")
                    append(getterMethod?.name)

                    if (property.setter != null) {
                        append(", setter (expected: ")
                        append(property.setter)
                        append("): ")
                        append(setterMethod?.name)
                    }
                },
            )
            return generateProperty(property, engineClass, getterMethod, setterMethod)
        }

        // TypeResolver maneja el trato especial de type + meta
        val propertyType = if (getterMethod.returnValue != null) {
            typeResolver.resolve(getterMethod.returnValue)
        } else if (setterMethod != null && setterMethod.arguments.isNotEmpty()) {
            typeResolver.resolve(setterMethod.arguments.last())
        } else {
            typeResolver.resolve(property.type)
        }

        val propBuilder = PropertySpec
            .builder(safeName, propertyType)
            .mutable(property.setter != null)
            .experimentalApiAnnotation(className, property.name)
            .addKdocIfPresent(property)

        if (property.name != safeName) {
            propBuilder.addKdoc("\n\nOriginal name: `%S`", property.name)
        }

        if (property.type.contains(",")) {
            val (includedTypes, excludedTypes) = PropertyTypes(property.type)
            propBuilder.addKdoc("\n\nAccepts: %L", includedTypes.joinToString())
            if (excludedTypes.isNotEmpty()) {
                propBuilder.addKdoc("\n\nExcludes: %L", excludedTypes.joinToString())
            }
        }

        // Getter
        if (property.index != null) {
            // Buscar el tipo del parámetro del getter para saber qué enum usar
            val enumConstant = resolveIndexedPropertyConstant(
                method = getterMethod,
                indexValue = property.index,
            )

            propBuilder.getter(
                FunSpec
                    .getterBuilder()
                    .addStatement("return %N(%L)", safeIdentifier(property.getter), enumConstant)
                    .build(),
            )
        } else {
            propBuilder.getter(
                FunSpec
                    .getterBuilder()
                    .addCode(body.buildPropertyGetterBody(getterMethod, engineClass))
                    .build(),
            )
        }

        // Setter
        if (property.setter == null || setterMethod == null) return propBuilder.build()

        if (property.index != null) {
            val enumConstant = resolveIndexedPropertyConstant(
                method = setterMethod,
                indexValue = property.index,
            )

            propBuilder.setter(
                FunSpec
                    .setterBuilder()
                    .addParameter("value", propertyType)
                    .addStatement("%N(%L, value)", safeIdentifier(property.setter), enumConstant)
                    .build(),
            )
        } else {
            check(setterMethod.arguments.size == 1) {
                "Property setter must have exactly one argument, got ${setterMethod.arguments.size} on $className.${property.name}"
            }

            propBuilder.setter(
                FunSpec
                    .setterBuilder()
                    .addParameter(safeIdentifier(setterMethod.arguments.first().name), propertyType)
                    .addCode(body.buildPropertySetterBody(setterMethod, engineClass))
                    .build(),
            )
        }

        return propBuilder.build()
    }

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
            return CodeBlock.of(indexValue.toString())
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

    context(_: Context)
    private fun generateProperty(
        property: EngineClass.ClassProperty,
        engineClass: ResolvedEngineClass,
        getter: EngineClass.ClassMethod?,
        setter: EngineClass.ClassMethod?,
    ): PropertySpec {
        if (property.type.contains(',')) error("Multi-type properties are not supported by generate property yet")
        val className = engineClass.name

        val propertyType = if (getter != null && getter.returnValue != null) {
            typeResolver.resolve(getter.returnValue)
        } else if (setter != null && setter.arguments.isNotEmpty()) {
            typeResolver.resolve(setter.arguments.last())
        } else {
            typeResolver.resolve(property.type)
        }

        val kotlinName = safeIdentifier(property.name)

        val propBuilder = PropertySpec
            .builder(kotlinName, propertyType)
            .mutable(property.setter != null)
            .experimentalApiAnnotation(className, property.name)
            .addKdocIfPresent(property)

        if (property.name != kotlinName) {
            propBuilder.addKdoc("\n\nOriginal name: `%S`", property.name)
        }

        // Getter
        if (property.index != null && getter != null) {
            // Buscar el tipo del parámetro del getter para saber qué enum usar
            val enumConstant = resolveIndexedPropertyConstant(
                method = getter,
                indexValue = property.index,
            )

            propBuilder.getter(
                FunSpec
                    .getterBuilder()
                    .addStatement("return %N(%L)", safeIdentifier(property.getter), enumConstant)
                    .build(),
            )
        } else {
            propBuilder.getter(
                FunSpec
                    .getterBuilder()
                    .addCode(
                        getter?.let {
                            body.buildPropertyGetterBody(
                                getter,
                                engineClass,
                            )
                        } ?: BodyGenerator.todoBody(
                            "Unknown getter method for property $className.${property.name}, method: ${property.getter}",
                        ),
                    )
                    .build(),
            )
        }

        if (property.setter == null) return propBuilder.build()

        if (setter == null) {
            propBuilder.setter(
                FunSpec
                    .setterBuilder()
                    .addParameter("value", propertyType)
                    .addCode(
                        BodyGenerator.todoBody(
                            "Unknown setter method for property $className.${property.name}, method: ${property.setter}",
                        ),
                    )
                    .build(),
            )
            return propBuilder.build()
        }

        if (property.index != null) {
            val enumConstant = resolveIndexedPropertyConstant(
                method = setter,
                indexValue = property.index,
            )

            propBuilder.setter(
                FunSpec
                    .setterBuilder()
                    .addParameter("value", propertyType)
                    .addStatement("%N(%L, value)", safeIdentifier(property.setter), enumConstant)
                    .build(),
            )
        } else {
            check(setter.arguments.size == 1) {
                "Property setter must have exactly one argument, got ${setter.arguments.size} on $className.${property.name}"
            }

            val setterArg = setter.arguments.first()
            val setterType = typeResolver.resolve(setterArg.type)

            propBuilder.setter(
                FunSpec
                    .setterBuilder()
                    .addParameter(safeIdentifier(setterArg.name), setterType)
                    .addCode(body.buildPropertySetterBody(setter, engineClass))
                    .build(),
            )
        }

        return propBuilder.build()
    }

    // Change to data class when Flexible constructor is available https://youtrack.jetbrains.com/issue/KT-81919
    private class PropertyTypes {
        val includedTypes: List<String>
        val excludedTypes: List<String>

        constructor(multiType: String) {
            val parts = multiType.split(",").map { it.trim() }

            includedTypes = parts.filter { !it.startsWith("-") }
            excludedTypes = parts.filter { it.startsWith("-") }.map { it.removePrefix("-") }
        }

        operator fun component1() = includedTypes
        operator fun component2() = excludedTypes
    }

    /**
     * Genera una propiedad que delega su implementación a la clase padre.
     * Útil cuando la propiedad está definida en el JSON, pero los métodos getter/setter
     * pertenecen a una clase base y no están disponibles como FPtrs locales.
     */
    context(_: Context)
    private fun generateDelegatedProperty(property: EngineClass.ClassProperty): PropertySpec {
        val safeName = safeIdentifier(property.name)
        val propertyType = typeResolver.resolve(property.type)
        val isMutable = property.setter != null

        val propBuilder = PropertySpec
            .builder(safeName, propertyType)
            // .addModifiers(KModifier.OVERRIDE) // Marcamos como override ya que existe en el padre
            .mutable(isMutable)

        propBuilder.getter(
            FunSpec
                .getterBuilder()
                .addStatement("return super.%N", safeName)
                .build(),
        )

        if (isMutable) {
            propBuilder.setter(
                FunSpec
                    .setterBuilder()
                    .addParameter("value", propertyType)
                    .addStatement("super.%N = value", safeName)
                    .build(),
            )
        }

        return propBuilder.build()
    }

    context(_: Context)
    private fun buildBaseClass(cls: ResolvedEngineClass, className: ClassName, isSingleton: Boolean): TypeSpec.Builder {
        val builder = TypeSpec
            .classBuilder(className)
            .experimentalApiAnnotation(cls.name)

        // Modificadores de clase
        when {
            !cls.isInstantiable && !isSingleton -> builder.addModifiers(KModifier.ABSTRACT)

            isSingleton -> {
                // Final class, internal constructor
                if (cls.isSingletonExtensible) {
                    builder.addAnnotation(API_STATUS_NON_EXTENSIBLE)
                    builder.addModifiers(KModifier.OPEN)
                }
            }

            else -> builder.addModifiers(KModifier.OPEN)
        }

        // Herencia
        cls.raw.inherits?.let { superClassType ->
            builder.superclass(typeResolver.resolve(superClassType))
        }

        return builder.addKdocIfPresent(cls.raw)
    }
}
