package io.github.kingg22.godot.codegen.impl.extensionapi.native

import io.github.kingg22.godot.codegen.impl.extensionapi.CodeImplGenerator
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import java.nio.file.Path

/**
 * Generates Kotlin Native implementation bodies (cinterop / GDExtension bindings).
 *
 * This is a placeholder — fill in as you implement each entity type.
 */
class KotlinNativeImplGenerator(override val typeResolver: TypeResolver, private val packageName: String) :
    CodeImplGenerator.ImplGenerator {

    context(context: Context)
    override fun generate(api: ExtensionApi, outputDir: Path): List<Path> {
        // TODO: generate cinterop-based implementations for classes, builtins, utility functions
        return emptyList()
    }
}
