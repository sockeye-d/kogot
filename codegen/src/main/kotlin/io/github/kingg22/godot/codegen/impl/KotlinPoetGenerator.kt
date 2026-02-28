package io.github.kingg22.godot.codegen.impl

import io.github.kingg22.godot.codegen.impl.extensionapi.Backend
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.stubs.KotlinStubBackend
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import io.github.kingg22.godot.codegen.models.gextensioninterface.GDExtensionInterface
import java.nio.file.Path

class KotlinPoetGenerator(packageName: String, private val backend: Backend = KotlinStubBackend(packageName)) {
    private val interfaceGenerator = GDExtensionInterfaceGenerator(packageName)

    fun generate(api: GDExtensionInterface, outputDir: Path): List<Path> = interfaceGenerator.generate(api, outputDir)

    fun generate(api: ExtensionApi, outputDir: Path): List<Path> = context(Context.buildFromApi(api)) {
        backend.generateAll(api, outputDir)
    }
}
