package io.github.kingg22.godot.codegen.models.extensionapi.domain

import io.github.kingg22.godot.codegen.models.extensionapi.NativeStructure

data class ResolvedNativeStructure(val raw: NativeStructure) {
    val name: String get() = raw.name
    val shortName: String get() = raw.name.substringAfterLast('.')
}
