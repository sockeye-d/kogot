package io.github.kingg22.godot.codegen.impl.extensionapi.native.generators

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.kingg22.godot.codegen.impl.createFile
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.renameGodotClass
import io.github.kingg22.godot.codegen.impl.sanitizeTypeName
import io.github.kingg22.godot.codegen.impl.toScreamingSnakeCase
import io.github.kingg22.godot.codegen.impl.withExceptionContext
import io.github.kingg22.godot.codegen.models.extensionapi.EnumDescriptor

/**
 * Generates top-level enums for Kotlin/Native.
 * Nested enums (Parent.Enum) are emitted as a top-level `ParentEnum` class in the Parent package.
 */
class NativeEnumGenerator {

    context(context: Context)
    fun generateFile(descriptor: EnumDescriptor): FileSpec {
        val spec = generateSpec(descriptor)
        return createFile(spec, spec.name!!, packageFor(descriptor.name))
    }

    fun generateSpec(descriptor: EnumDescriptor): TypeSpec {
        withExceptionContext({ "Generating enum '${descriptor.name}', values count: ${descriptor.values.size}" }) {
            val enumName = enumTypeName(descriptor.name)

            val typeBuilder = TypeSpec
                .enumBuilder(enumName)
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
            descriptor.description?.takeIf { it.isNotBlank() }?.let { typeBuilder.addKdoc("%S", it) }

            // TODO KeyModifierMask special prefix cases
            val enumNameScreaming = when (descriptor.name) {
                "Error" -> "ERR_"
                "MethodFlags" -> "METHOD_FLAG_"
                "PropertyUsageFlags" -> "PROPERTY_USAGE_"
                "VariantOperator" -> "OP_"
                "VariantType" -> "TYPE_"
                "Vector2Axis", "Vector2iAxis", "Vector3Axis", "Vector3iAxis", "Vector4Axis", "Vector4iAxis" -> "AXIS_"
                else -> descriptor.name.toScreamingSnakeCase()
            }

            descriptor.values.forEach { enumConstant ->
                withExceptionContext({ "Error generating enum constant '${enumConstant.name}'" }) {
                    var enumEntry = enumConstant.name.removePrefix(enumNameScreaming)
                    while (enumEntry.isNotBlank() && enumEntry.startsWith("_")) {
                        enumEntry = enumEntry.substring(1)
                    }
                    enumEntry = enumEntry.ifBlank {
                        println("WARNING: Enum constant '${enumConstant.name}' has no prefix, using raw name")
                        enumConstant.name
                    }

                    if (enumEntry.first().isDigit()) {
                        println(
                            "WARNING: Enum constant '${enumConstant.name}' starts with digit, restoring original name",
                        )
                        enumEntry = enumConstant.name
                    }

                    typeBuilder.addEnumConstant(
                        sanitizeTypeName(enumEntry),
                        TypeSpec
                            .anonymousClassBuilder()
                            .addSuperclassConstructorParameter("%L", enumConstant.value)
                            .apply { enumConstant.description?.let { addKdoc("%S", it) } }
                            .build(),
                    )
                }
            }

            return typeBuilder.build()
        }
    }

    private fun enumTypeName(rawName: String): String {
        if (!rawName.contains('.')) return sanitizeTypeName(rawName.renameGodotClass())
        val parent = rawName.substringBeforeLast('.')
        val enumName = rawName.substringAfterLast('.')
        val parentName = sanitizeTypeName(parent.renameGodotClass())
        val nestedName = sanitizeTypeName(enumName.renameGodotClass())
        return parentName + nestedName
    }

    context(context: Context)
    private fun packageFor(rawName: String): String {
        if (!rawName.contains('.')) return context.packageForOrDefault(rawName)
        val parent = rawName.substringBeforeLast('.')
        return context.packageForOrDefault(parent)
    }
}
