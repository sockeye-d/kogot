package io.github.kingg22.godot.codegen.impl.extensionapi

import io.github.kingg22.godot.codegen.impl.extensionapi.native.resolver.EnumConstantResolver
import io.github.kingg22.godot.codegen.impl.extensionapi.stubs.StubsPackageRegistry
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import io.github.kingg22.godot.codegen.models.extensionapi.Header
import io.github.kingg22.godot.codegen.models.extensionapi.domain.GodotVersion

/** Special context for test */
@Suppress("ktlint:standard:function-naming", "TestFunctionName")
fun EmptyContext(
    packageRegistry: PackageRegistry = StubsPackageRegistry(""),
    tree: InheritanceTree = InheritanceTree(),
    version: GodotVersion = GodotVersion(0, 0, 0, 0, "test", "test", 0, "Test version"),
): Context = Context(
    extensionApi = ExtensionApi(header = Header(0, 0, 0, "test", "test", "Test version", "single")),
    builtinTypes = emptySet(),
    singletons = emptySet(),
    classes = emptySet(),
    globalEnumsTypes = emptySet(),
    nativeStructureTypes = emptySet(),
    enumConstantResolver = EnumConstantResolver.empty(),
    experimentalTypesRegistry = ExperimentalTypesRegistry.empty,
    inheritanceTree = tree,
    godotVersion = version,
    packageRegistry = packageRegistry,
    precision = "single",
)
