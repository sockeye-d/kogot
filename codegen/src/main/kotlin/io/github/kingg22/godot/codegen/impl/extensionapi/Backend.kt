package io.github.kingg22.godot.codegen.impl.extensionapi

import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import java.nio.file.Path

/**
 * A **Backend** owns everything target-specific:
 * its [TypeResolver], its [CodeImplGenerator].
 *
 * The pipeline calls [generateAll] once per backend after the [Context] is built.
 *
 * New targets (Kotlin Native, Java FFM, Java JNI, …) implement this interface.
 */
interface Backend {
    val name: String

    val typeResolver: TypeResolver
    val codeImplGenerator: CodeImplGenerator

    /**
     * Generates all files for [api] into [outputDir].
     *
     * The [context] is the shared, immutable API context built before this call.
     *
     * Returns the list of paths written.
     */

    context(_: Context)
    fun generateAll(api: ExtensionApi, outputDir: Path): Sequence<Path> = codeImplGenerator.generate(api, outputDir)
}
