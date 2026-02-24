package io.github.kingg22.godot.codegen.models.extensionapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GodotClass(
    val name: String,
    @SerialName("is_refcounted") val isRefcounted: Boolean,
    @SerialName("is_instantiable") val isInstantiable: Boolean,
    val inherits: String? = null,
    @SerialName("api_type") val apiType: String,
    val constants: List<EnumConstant> = emptyList(),
    val enums: List<ApiEnum> = emptyList(),
    val methods: List<ClassMethod> = emptyList(),
    val properties: List<ClassProperty> = emptyList(),
    val signals: List<Signal> = emptyList(),
) {
    @Serializable
    class ClassMethod(
        val name: String,
        @SerialName("is_const") val isConst: Boolean,
        @SerialName("is_vararg") val isVararg: Boolean,
        @SerialName("is_static") val isStatic: Boolean,
        @SerialName("is_virtual") val isVirtual: Boolean,
        @SerialName("is_required") val isRequired: Boolean? = null,
        val hash: Long? = null,
        @SerialName("hash_compatibility") val hashCompatibility: List<Long> = emptyList(),
        @SerialName("return_value") val returnValue: MethodReturn? = null,
        val arguments: List<MethodArg> = emptyList(),
    )

    @Serializable
    class ClassProperty(
        val type: String,
        val name: String,
        val setter: String? = null,
        val getter: String? = null,
        val index: Int? = null,
    )
}
