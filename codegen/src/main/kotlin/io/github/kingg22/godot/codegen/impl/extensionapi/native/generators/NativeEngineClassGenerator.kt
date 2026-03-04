package io.github.kingg22.godot.codegen.impl.extensionapi.native.generators

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.kingg22.godot.codegen.impl.createFile
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver
import io.github.kingg22.godot.codegen.impl.renameGodotClass
import io.github.kingg22.godot.codegen.impl.safeIdentifier
import io.github.kingg22.godot.codegen.impl.withExceptionContext
import io.github.kingg22.godot.codegen.models.extensionapi.GodotClass

class NativeEngineClassGenerator(
    private val typeResolver: TypeResolver,
    private val body: BodyGenerator,
    private val methodGen: NativeMethodGenerator,
    private val enumGenerator: NativeEnumGenerator,
) {
    companion object {
        val lazyMethod = MemberName("kotlin", "lazy")
        val lazyMode = ClassName("kotlin", "LazyThreadSafetyMode")
    }

    context(context: Context)
    fun generateFile(cls: GodotClass): FileSpec {
        val packageName = context.packageForOrDefault(cls.name)
        val spec = generateSpec(cls)
        return createFile(spec, spec.name!!, packageName)
    }

    context(context: Context)
    fun generateSpec(cls: GodotClass): TypeSpec {
        val classNameStr = cls.name.renameGodotClass()
        val packageName = context.packageForOrDefault(cls.name)
        val className = ClassName(packageName, classNameStr)
        val isSingleton = context.isSingleton(cls)
        val classBuilder = buildBaseClass(cls, className, isSingleton)

        // Separar métodos por tipo
        val (staticMethods, instanceMethods) = cls.methods.partition { it.isStatic }

        // Properties (Generadas vía getter/setter del JSON)
        cls.properties.forEach { property ->
            if (property.type.contains(",")) {
                println("WARNING: Multiple types in property ${cls.name}.${property.name}: ${property.type}")
                return@forEach
            }

            val memberType = typeResolver.resolve(property.type)

            val propBuilder = PropertySpec
                .builder(safeIdentifier(property.name), memberType)
                .mutable(property.setter != null)

            // property.description?.takeIf { it.isNotBlank() }?.let { propBuilder.addKdoc(it) }

            propBuilder.getter(
                FunSpec
                    .getterBuilder()
                    .addCode(body.todoBody())
                    .build(),
            )

            if (property.setter != null) {
                propBuilder.setter(
                    FunSpec
                        .setterBuilder()
                        .addParameter("value", memberType)
                        .addCode(body.todoBody())
                        .build(),
                )
            }

            classBuilder.addProperty(propBuilder.build())
        }

        // Virtual Methods = Open, Non-Virtual: Final
        instanceMethods.forEach {
            val modifiers = Array(if (it.isVirtual) 1 else 0) { KModifier.OPEN }
            classBuilder.addFunction(methodGen.buildMethod(it, *modifiers))
        }

        // Companion Object para Statics y Singleton Instance
        val companionBuilder = TypeSpec.companionObjectBuilder()

        if (isSingleton) {
            companionBuilder.addSingletonInstance(className)
        }

        cls.constants.forEach { enumConstant ->
            withExceptionContext({ "Error generating class constant '${enumConstant.name}'" }) {
                companionBuilder.addProperty(
                    PropertySpec
                        .builder(enumConstant.name, LONG, KModifier.CONST)
                        .initializer("%L", enumConstant.value)
                        .apply { enumConstant.description?.let { addKdoc("%S", it) } }
                        .build(),
                )
            }
        }

        staticMethods.forEach {
            companionBuilder.addFunction(methodGen.buildMethod(it))
        }

        if (isSingleton || cls.constants.isNotEmpty() || staticMethods.isNotEmpty()) {
            classBuilder.addType(companionBuilder.build())
        }

        cls.enums.forEach { enum ->
            if (context.isSpecializedClass(enum.name)) return@forEach
            classBuilder.addType(enumGenerator.generateSpec(enum))
        }

        return classBuilder.build()
    }

    context(_: Context)
    private fun buildBaseClass(cls: GodotClass, className: ClassName, isSingleton: Boolean): TypeSpec.Builder {
        val builder = TypeSpec.classBuilder(className)
        val constructorSpec = FunSpec.constructorBuilder()

        // Modificadores de clase
        when {
            !cls.isInstantiable && !isSingleton -> builder.addModifiers(KModifier.ABSTRACT)

            isSingleton -> {
                // Final class, internal constructor
                if (cls.name == "PhysicsServer2D" || cls.name == "PhysicsServer3D") {
                    builder.addAnnotation(API_STATUS_NON_EXTENSIBLE)
                    builder.addModifiers(KModifier.OPEN)
                    constructorSpec.addModifiers(KModifier.INTERNAL)
                } else {
                    builder.addModifiers(KModifier.FINAL)
                    constructorSpec.addModifiers(KModifier.PRIVATE)
                }
            }

            else -> builder.addModifiers(KModifier.OPEN)
        }

        // Herencia
        cls.inherits?.let { superClassType ->
            builder.superclass(typeResolver.resolve(superClassType))
        }

        builder.primaryConstructor(constructorSpec.build())

        cls.description?.takeIf { it.isNotBlank() }?.let {
            builder.addKdoc("%S", it.replace("*/", "").replace("/*", ""))
        }

        return builder
    }

    /** @receiver Companion Builder */
    private fun TypeSpec.Builder.addSingletonInstance(className: ClassName) = apply {
        addProperty(
            PropertySpec
                .builder("instance", className)
                .delegate(
                    CodeBlock
                        .builder()
                        .beginControlFlow("%M(%T.NONE)", lazyMethod, lazyMode)
                        .addStatement("%T()", className)
                        .endControlFlow()
                        .build(),
                )
                .build(),
        )
    }
}
