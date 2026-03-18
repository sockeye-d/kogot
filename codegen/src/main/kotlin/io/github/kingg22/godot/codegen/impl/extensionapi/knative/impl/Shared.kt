package io.github.kingg22.godot.codegen.impl.extensionapi.knative.impl

import com.squareup.kotlinpoet.*
import io.github.kingg22.godot.codegen.models.extensionapi.domain.ResolvedBuiltinLayout

fun buildLayoutConstants(layout: ResolvedBuiltinLayout): List<PropertySpec> =
    layout.memberOffsets.map { (member, offset) ->
        PropertySpec
            .builder("OFFSET_${member.uppercase()}", INT, KModifier.CONST)
            .initializer("%L", offset)
            .addKdoc(
                "Byte offset of member `%L` for build configuration `%L`.",
                member,
                layout.buildConfiguration.jsonName,
            )
            .build()
    }

// ── meta → StructMemory function name ────────────────────────────
fun metaToGetFun(meta: String): String? = when (meta.lowercase()) {
    "float" -> "getFloat"
    "double" -> "getDouble"
    "int32" -> "getInt"
    "int64" -> "getLong"
    "uint32" -> "getUInt"
    "uint64" -> "getULong"
    "int8" -> "getByte"
    "uint8" -> "getUByte"
    "int16" -> "getShort"
    "uint16" -> "getUShort"
    else -> null // compound type (e.g. "Vector2") — caller falls back to TODO
}

fun metaToSetFun(meta: String): String? = when (meta.lowercase()) {
    "float" -> "setFloat"
    "double" -> "setDouble"
    "int32" -> "setInt"
    "int64" -> "setLong"
    "uint32" -> "setUInt"
    "uint64" -> "setULong"
    "int8" -> "setByte"
    "uint8" -> "setUByte"
    "int16" -> "setShort"
    "uint16" -> "setUShort"
    else -> null
}

/**
 * Maps a member_offsets [meta] to the StructMemory function name and the Kotlin type
 * that corresponds to the physical storage.
 *
 * This mapping is INDEPENDENT of build configuration — meta="float" always means
 * C float (32-bit), never real_t. The build-config-dependent widening (Float→Double
 * in float_64) is handled separately via [storageToPropertyConv].
 */
fun metaToStorageInfo(meta: String, type: String): Pair<String, TypeName>? {
    check(type == "set" || type == "get")
    return when (meta.lowercase()) {
        "float" -> "${type}Float" to FLOAT
        "double" -> "${type}Double" to DOUBLE
        "int32" -> "${type}Int" to INT
        "int64" -> "${type}Long" to LONG
        "uint32" -> "${type}UInt" to U_INT
        "uint64" -> "${type}ULong" to U_LONG
        "int8" -> "${type}Byte" to BYTE
        "uint8" -> "${type}UByte" to U_BYTE
        "int16" -> "${type}Short" to SHORT
        "uint16" -> "${type}UShort" to U_SHORT
        else -> null // compound (Vector2, Vector3, …) — caller falls back to todoGetter
    }
}

/** Widening conversion from physical storage type to API property type. */
fun storageToPropertyConv(storage: TypeName, property: TypeName): String? = when {
    storage == FLOAT && property == DOUBLE -> ".toDouble()"
    storage == DOUBLE && property == FLOAT -> ".toFloat()"
    storage == INT && property == LONG -> ".toLong()"
    storage == LONG && property == INT -> ".toInt()"
    storage == U_INT && property == U_LONG -> ".toULong()"
    storage == U_LONG && property == U_INT -> ".toUInt()"
    else -> null
}

/** Narrowing conversion from API property type to physical storage type (setter path). */
fun propertyToStorageConv(property: TypeName, storage: TypeName): String? = storageToPropertyConv(property, storage)
