package io.github.kingg22.godot.codegen.impl.extensionapi

import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import java.nio.file.Path

interface CodeImplGenerator {
    val typeResolver: TypeResolver

    context(context: Context)
    fun generate(api: ExtensionApi, outputDir: Path): List<Path>

    /**
     * Generates the platform-specific implementation bodies for a specific [Backend].
     *
     * Each backend produces its own impl (cinterop calls, JNI bindings, FFM MemorySegment, …).
     */
    interface ImplGenerator : CodeImplGenerator

    /**
     * Generates API stubs (interfaces / abstract classes / open classes with `TODO()` bodies)
     * for a specific [Backend].
     *
     * Stubs declare the shape of the Godot API without any platform-specific implementation.
     */
    interface StubGenerator : CodeImplGenerator
}
