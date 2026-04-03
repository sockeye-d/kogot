package io.github.kingg22.godot.sample

import io.github.kingg22.godot.api.builtin.asVariantString
import io.github.kingg22.godot.api.core.Node
import io.github.kingg22.godot.api.utils.GD
import kotlinx.cinterop.COpaquePointer

class CustomClass(nativePtr: COpaquePointer) : Node(nativePtr) {
    override fun _ready() {
        GD.print("hi".asVariantString())
    }
}
