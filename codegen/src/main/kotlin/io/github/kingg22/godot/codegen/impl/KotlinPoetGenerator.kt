package io.github.kingg22.godot.codegen.impl

import io.github.kingg22.godot.codegen.impl.extensionapi.Backend
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.ffm.JavaFfmBackend
import io.github.kingg22.godot.codegen.impl.extensionapi.native.KotlinNativeBackend
import io.github.kingg22.godot.codegen.impl.extensionapi.stubs.KotlinStubBackend
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import io.github.kingg22.godot.codegen.models.gextensioninterface.GDExtensionInterface
import java.nio.file.Path

class KotlinPoetGenerator(packageName: String, private val backend: Backend) {
    private val interfaceGenerator = GDExtensionInterfaceGenerator(packageName)

    constructor(packageName: String, backend: GeneratorBackend) : this(
        packageName,
        when (backend) {
            GeneratorBackend.JAVA_FFM -> JavaFfmBackend(packageName)
            GeneratorBackend.STUBS -> KotlinStubBackend(packageName)
            GeneratorBackend.KOTLIN_NATIVE -> KotlinNativeBackend(packageName)
        },
    )

    fun generate(api: GDExtensionInterface, outputDir: Path): List<Path> = interfaceGenerator.generate(api, outputDir)

    fun generate(api: ExtensionApi, outputDir: Path): Sequence<Path> = context(Context.buildFromApi(api)) {
        backend.generateAll(api, outputDir)
    }
}
