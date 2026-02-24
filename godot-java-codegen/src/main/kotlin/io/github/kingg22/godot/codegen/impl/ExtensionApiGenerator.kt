package io.github.kingg22.godot.codegen.impl

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import io.github.kingg22.godot.codegen.models.extensionapi.ApiEnum
import io.github.kingg22.godot.codegen.models.extensionapi.BuiltinClass
import io.github.kingg22.godot.codegen.models.extensionapi.BuiltinEnum
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import io.github.kingg22.godot.codegen.models.extensionapi.GodotClass
import io.github.kingg22.godot.codegen.models.extensionapi.NativeStructure
import java.nio.file.Path

class ExtensionApiGenerator(private val packageName: String) {
    fun generate(api: ExtensionApi, outputDir: Path): List<Path> {
        val size = api.globalEnums.size + api.builtinClasses.size + api.classes.size + api.nativeStructures.size

        val (nestedEnum, globalEnums) = api.globalEnums.partition { it.name.contains(".") }

        if (nestedEnum.size > 2) {
            System.err.println(
                "WARNING: Nested enums (${nestedEnum.size}) " +
                    nestedEnum.joinToString(prefix = "[", postfix = "]") { it.name },
            )
        }

        val enums = globalEnums.asSequence().map { enumDef ->
            val enumSpec = generateEnum(enumDef)
            createFile(enumSpec, enumDef.name).writeTo(outputDir)
        }

        val variantClass = generateVariant(nestedEnum).writeTo(outputDir)

        val builtinClasses = api.builtinClasses.asSequence().map { clazz ->
            generateBuiltinClass(clazz).writeTo(outputDir)
        }

        val classes = api.classes.asSequence().map { clazz ->
            generateClass(clazz).writeTo(outputDir)
        }

        val nativeStructures = api.nativeStructures.asSequence().map { ns ->
            generateNativeStructure(ns).writeTo(outputDir)
        }

        return ArrayList<Path>(size).apply {
            add(variantClass)
            addAll(enums + builtinClasses + classes + nativeStructures)
        }
    }

    private fun createFile(type: TypeSpec, fileName: String): FileSpec = FileSpec
        .builder(packageName, fileName)
        .commonConfiguration()
        .addType(type)
        .build()

    private fun generateEnum(enumDef: ApiEnum): TypeSpec {
        val typeBuilder = TypeSpec.enumBuilder(enumDef.name)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("value", LONG)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("value", LONG)
                    .initializer("value")
                    .build(),
            )

        enumDef.values.forEach { value ->
            val enumConst = TypeSpec.anonymousClassBuilder()
                .addSuperclassConstructorParameter("%L", value.value)
                .build()
            typeBuilder.addEnumConstant(sanitizeEnumConstant(value.name), enumConst)
        }
        return typeBuilder.build()
    }

    private fun generateBuiltinClass(cls: BuiltinClass): FileSpec {
        val typeBuilder = TypeSpec.classBuilder(cls.name)
            .addModifiers(KModifier.OPEN)

        // Métodos
        cls.methods.forEach { method ->
            val methodName = safeIdentifier(method.name)
            val methodReturnType = method.returnType?.let { typeNameFor(packageName, it) } ?: UNIT
            val funSpec = FunSpec.builder(methodName)
                .addModifiers(KModifier.OPEN)
                .addParameters(methodArgsToParameters(packageName, method.arguments))
                .returns(methodReturnType)
                .apply {
                    addKdocForBitfield(this, method.returnType, "@return")
                }
                .addStatement("TODO()")
                .accidentalOverride(methodName, methodReturnType)
                .build()
            typeBuilder.addFunction(funSpec)
        }

        fun BuiltinEnum.asApiEnum() = ApiEnum(name = name, isBitfield = false, values = values)

        // Enums anidados (aquí vive Variant.Type, etc.)
        cls.enums.forEach { enumDef ->
            typeBuilder.addType(generateEnum(enumDef.asApiEnum()))
        }

        return createFile(typeBuilder.build(), cls.name)
    }

    private fun generateClass(cls: GodotClass): FileSpec {
        val typeBuilder = TypeSpec.classBuilder(cls.name)
            .addModifiers(KModifier.ABSTRACT)

        val parent = cls.inherits?.takeIf { it.isNotBlank() }
        if (parent != null) {
            typeBuilder.superclass(typeNameFor(packageName, parent))
        }

        cls.methods.forEach { method ->
            val methodName = safeIdentifier(method.name)
            val methodReturnType = methodReturnTypeName(packageName, method.returnValue)
            val funSpec = FunSpec.builder(methodName)
                .addModifiers(KModifier.OPEN)
                .addParameters(methodArgsToParameters(packageName, method.arguments))
                .returns(methodReturnType)
                .apply {
                    addKdocForBitfield(this, method.returnValue?.type, "@return")
                }
                .addStatement("TODO()")
                .accidentalOverride(methodName, methodReturnType)
                .build()
            typeBuilder.addFunction(funSpec)
        }

        val enumSpecs = cls.enums.map { enumDef -> generateEnum(enumDef) }

        typeBuilder.addTypes(enumSpecs)

        return createFile(typeBuilder.build(), cls.name)
    }

    private fun generateNativeStructure(ns: NativeStructure): FileSpec {
        val typeBuilder = TypeSpec.classBuilder(ns.name).addModifiers(KModifier.OPEN)
        return createFile(typeBuilder.build(), ns.name)
    }

    /** enums internos vendrán de globalEnums Variant. */
    private fun generateVariant(enums: List<ApiEnum>): FileSpec {
        val typeBuilder = TypeSpec.classBuilder("Variant").addModifiers(KModifier.OPEN)
            .addTypes(enums.map { generateEnum(it.copy(name = it.name.substringAfterLast("."))) })
        return createFile(typeBuilder.build(), "Variant")
    }

    private fun FunSpec.Builder.accidentalOverride(funName: String, returnType: TypeName): FunSpec.Builder =
        if (funName == "wait" && returnType == UNIT) {
            System.err.println("WARNING: modifying wait() to avoid conflicts with JVM Object method")
            this
                .addKdoc(
                    "Generated Note: Original name was `wait`, renamed to avoid conflicts with JVM [java.lang.Object] method.",
                )
                .build()
                .toBuilder("await")
        } else {
            this
        }
}
