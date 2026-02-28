package io.github.kingg22.godot.codegen.impl.extensionapi.ffm

import io.github.kingg22.godot.codegen.impl.extensionapi.Backend
import io.github.kingg22.godot.codegen.impl.extensionapi.CodeImplGenerator
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver

class JavaFfmBackend(
    packageName: String,
    override val typeResolver: TypeResolver = JavaFfmTypeResolver(packageName),
    override val codeImplGenerator: CodeImplGenerator = JavaFfmImplGenerator(typeResolver, packageName),
) : Backend {
    override val name: String get() = "java-ffm"
}
