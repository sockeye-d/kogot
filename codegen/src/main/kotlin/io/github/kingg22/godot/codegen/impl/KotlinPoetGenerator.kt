package io.github.kingg22.godot.codegen.impl

import io.github.kingg22.godot.codegen.impl.extensionapi.Backend
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.PackageRegistryFactory
import io.github.kingg22.godot.codegen.impl.extensionapi.ffm.JavaFfmBackend
import io.github.kingg22.godot.codegen.impl.extensionapi.ffm.JavaFfmPackageRegistry
import io.github.kingg22.godot.codegen.impl.extensionapi.native.KotlinNativeBackend
import io.github.kingg22.godot.codegen.impl.extensionapi.native.NativePackageRegistry
import io.github.kingg22.godot.codegen.impl.extensionapi.stubs.KotlinStubBackend
import io.github.kingg22.godot.codegen.impl.extensionapi.stubs.StubsPackageRegistry
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import io.github.kingg22.godot.codegen.models.gextensioninterface.GDExtensionInterface
import java.nio.file.Path

class KotlinPoetGenerator(
    private val packageName: String,
    private val backend: Backend,
    private val packageRegistryFactory: PackageRegistryFactory,
) {
    private val interfaceGenerator = GDExtensionInterfaceGenerator(packageName)

    constructor(packageName: String, backend: GeneratorBackend) : this(
        packageName,
        when (backend) {
            GeneratorBackend.STUBS -> KotlinStubBackend(packageName)
            GeneratorBackend.JAVA_FFM -> JavaFfmBackend(packageName)
            GeneratorBackend.KOTLIN_NATIVE -> KotlinNativeBackend()
        },
        when (backend) {
            GeneratorBackend.STUBS -> StubsPackageRegistry.factory
            GeneratorBackend.JAVA_FFM -> JavaFfmPackageRegistry.factory
            GeneratorBackend.KOTLIN_NATIVE -> NativePackageRegistry.factory
        },
    )

    fun generate(api: GDExtensionInterface, outputDir: Path): List<Path> = interfaceGenerator.generate(api, outputDir)

    fun generate(api: ExtensionApi, outputDir: Path): Sequence<Path> = context(
        Context.buildFromApi(api, packageName, packageRegistryFactory),
    ) {
        backend.generateAll(api, outputDir)
    }
}
