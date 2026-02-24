package io.github.kingg22.godot.codegen.models.extensionapi

import kotlinx.serialization.Serializable

@Serializable
class BuiltinEnum(val name: String, val values: List<EnumConstant>)
