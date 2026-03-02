package io.github.kingg22.godot.codegen.impl.extensionapi.native

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.kingg22.godot.codegen.impl.commonConfiguration
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver
import io.github.kingg22.godot.codegen.impl.renameGodotClass
import io.github.kingg22.godot.codegen.impl.snakeCaseToCamelCase
import io.github.kingg22.godot.codegen.impl.withExceptionContext
import io.github.kingg22.godot.codegen.models.extensionapi.EnumDescriptor

/** Generates the sealed `Variant` class without nested enums (native backend emits them top-level). */
class NativeVariantGenerator(private val typeResolver: TypeResolver) {
    context(context: Context)
    fun generateSpec(nestedEnums: List<EnumDescriptor>): TypeSpec {
        val variantTypes = nestedEnums.find { it.name == "Variant.Type" }

        return withExceptionContext({ "Generating Variant class, nested enums count: ${nestedEnums.size}" }) {
            val variantClassName = ClassName(context.packageForOrDefault("Variant"), "Variant")

            val typeBuilder = TypeSpec
                .classBuilder(variantClassName)
                .addModifiers(KModifier.SEALED)

            variantTypes?.values?.forEach { variantType ->
                val enumValueName = variantType.name // "TYPE_PACKED_BYTE_ARRAY"
                val subclassName = enumValueName.removePrefix("TYPE_")

                if (subclassName == "MAX") return@forEach

                if (subclassName == "NIL") {
                    typeBuilder.addType(
                        TypeSpec
                            .objectBuilder("NIL")
                            .superclass(variantClassName)
                            .apply { variantType.description?.let { addKdoc(it) } }
                            .build(),
                    )
                    return@forEach
                }

                val godotTypeName = subclassName
                    .lowercase()
                    .snakeCaseToCamelCase()
                    .replaceFirstChar { it.uppercaseChar() }

                val valueType = typeResolver.resolve(godotTypeName.renameGodotClass())

                val variantTypeSpec = TypeSpec
                    .classBuilder(subclassName)
                    .superclass(variantClassName)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("value", valueType)
                            .build(),
                    )
                    .addProperty(
                        PropertySpec
                            .builder("value", valueType)
                            .initializer("value")
                            .build(),
                    )
                    .apply { variantType.description?.let { addKdoc(it) } }

                typeBuilder.addType(variantTypeSpec.build())
            }

            typeBuilder.build()
        }
    }

    context(context: Context)
    fun generate(nestedEnums: List<EnumDescriptor>): FileSpec = FileSpec
        .builder(context.packageForOrDefault("Variant"), "Variant")
        .commonConfiguration()
        .addType(generateSpec(nestedEnums))
        .build()
}
