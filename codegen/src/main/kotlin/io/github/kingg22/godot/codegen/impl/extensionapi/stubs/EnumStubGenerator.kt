package io.github.kingg22.godot.codegen.impl.extensionapi.stubs

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.kingg22.godot.codegen.impl.commonConfiguration
import io.github.kingg22.godot.codegen.impl.renameGodotClass
import io.github.kingg22.godot.codegen.impl.sanitizeTypeName
import io.github.kingg22.godot.codegen.models.extensionapi.EnumDescriptor

/**
 * Generates a Kotlin `enum class` from a Godot [io.github.kingg22.godot.codegen.models.extensionapi.EnumDescriptor].
 */
class EnumStubGenerator(private val packageName: String) {

    fun generateFile(enumDef: EnumDescriptor): FileSpec {
        val spec = generateSpec(enumDef)
        return FileSpec
            .builder(packageName, enumDef.name.renameGodotClass())
            .commonConfiguration()
            .addType(spec)
            .build()
    }

    fun generateSpec(enumDef: EnumDescriptor): TypeSpec {
        val typeBuilder = TypeSpec.enumBuilder(enumDef.name.renameGodotClass())
            .primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addParameter("value", LONG)
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder("value", LONG)
                    .initializer("value")
                    .build(),
            )

        enumDef.values.forEach { value ->
            typeBuilder.addEnumConstant(
                sanitizeTypeName(value.name),
                TypeSpec
                    .anonymousClassBuilder()
                    .addSuperclassConstructorParameter("%L", value.value)
                    .build(),
            )
        }
        return typeBuilder.build()
    }
}
