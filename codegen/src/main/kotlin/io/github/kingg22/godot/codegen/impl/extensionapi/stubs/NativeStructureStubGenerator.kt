package io.github.kingg22.godot.codegen.impl.extensionapi.stubs

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import io.github.kingg22.godot.codegen.impl.commonConfiguration
import io.github.kingg22.godot.codegen.models.extensionapi.NativeStructure

/**
 * Generates an `open class` stub for a Godot native structure.
 */
class NativeStructureStubGenerator(private val packageName: String) {
    fun generate(ns: NativeStructure): FileSpec {
        val typeBuilder = TypeSpec.classBuilder(ns.name).addModifiers(KModifier.OPEN)
        return FileSpec
            .builder(packageName, ns.name)
            .commonConfiguration()
            .addType(typeBuilder.build())
            .build()
    }
}
