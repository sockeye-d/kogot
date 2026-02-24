package io.github.kingg22.godot.codegen.models.extensionapi

import kotlinx.serialization.Serializable

@Serializable
data class MethodReturn(val type: String, val meta: String? = null)
