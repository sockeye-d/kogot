package io.github.kingg22.godot.codegen.models.extensionapi.domain

import io.github.kingg22.godot.codegen.models.extensionapi.BuiltinClass
import io.github.kingg22.godot.codegen.models.extensionapi.MethodArg

data class ResolvedBuiltinConstructor(
    val raw: BuiltinClass.Constructor,
    val ownerName: String,
    val runtimeFunctionName: String? = null,
) {
    val index: Int get() = raw.index
    val arguments: List<MethodArg> get() = raw.arguments
    val argumentTypes: List<String> get() = raw.arguments.map { it.type }
}
