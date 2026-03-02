package io.github.kingg22.godot.codegen.impl.extensionapi.ffm

import io.github.kingg22.godot.codegen.impl.extensionapi.PackageRegistry
import io.github.kingg22.godot.codegen.impl.extensionapi.PackageRegistryFactory
import io.github.kingg22.godot.codegen.impl.extensionapi.stubs.StubsPackageRegistry

class JavaFfmPackageRegistry(pkg: String) : PackageRegistry by StubsPackageRegistry(pkg) {
    companion object {
        val factory: PackageRegistryFactory = { pkg, _ -> JavaFfmPackageRegistry(pkg) }
    }
}
