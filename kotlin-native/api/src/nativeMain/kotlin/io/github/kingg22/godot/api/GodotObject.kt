package io.github.kingg22.godot.api

import kotlinx.cinterop.CPointed
import kotlinx.cinterop.NativePtr

public class GodotObject(rawPtr: NativePtr) : CPointed(rawPtr)
