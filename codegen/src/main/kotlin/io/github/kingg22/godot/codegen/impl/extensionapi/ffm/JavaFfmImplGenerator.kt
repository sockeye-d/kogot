package io.github.kingg22.godot.codegen.impl.extensionapi.ffm

import io.github.kingg22.godot.codegen.impl.extensionapi.CodeImplGenerator
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import java.nio.file.Path

/** Generates Java FFM API implementation bodies (Java FFM bindings, MemorySegment, Arena). */
class JavaFfmImplGenerator(override val typeResolver: TypeResolver, private val packageName: String) :
    CodeImplGenerator.ImplGenerator {

    context(context: Context)
    override fun generate(api: ExtensionApi, outputDir: Path): Sequence<Path> {
        // TODO: generate Java FFM for classes, builtins, utility functions
        return sequenceOf()
    }
}
