package io.github.kingg22.godot.codegen.impl.extensionapi.native

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver

class KotlinNativeTypeResolver(private val packageName: String) : TypeResolver {
    override fun resolve(godotType: String): TypeName = ClassName(packageName, godotType)
}
