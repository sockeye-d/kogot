package io.github.kingg22.godot.codegen.impl.extensionapi.stubs

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import io.github.kingg22.godot.codegen.impl.commonConfiguration
import io.github.kingg22.godot.codegen.impl.withExceptionContext
import io.github.kingg22.godot.codegen.models.extensionapi.EnumDescriptor

/**
 * Generates the sealed `Variant` class with nested enums from Godot's `Variant.*` global enums.
 */
class VariantStubGenerator(private val packageName: String, private val enumGen: EnumStubGenerator) {
    fun generate(nestedEnums: List<EnumDescriptor>): FileSpec =
        withExceptionContext({ "Generating Variant class, nested enums count: ${nestedEnums.size}" }) {
            val typeBuilder = TypeSpec
                .classBuilder("Variant")
                .addModifiers(KModifier.SEALED)
                .addTypes(
                    nestedEnums.map {
                        withExceptionContext({ "Error generating nested enum '${it.name}'" }) {
                            enumGen.generateSpec(it.copy(name = it.name.substringAfterLast(".")))
                        }
                    },
                )

            FileSpec
                .builder(packageName, "Variant")
                .commonConfiguration()
                .addType(typeBuilder.build())
                .build()
        }
}
