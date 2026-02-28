package io.github.kingg22.godot.codegen.impl.extensionapi.shared

/**
 * Parses property type hints from the Godot API.
 *
 * **WARNING: prefer to delegate to kotlin compiler infers or methods in getters/setters to get the property type**
 */
object PropertyTypeParser {
    /**
     * Extracts the actual codegen type from a property type hint string.
     *
     * Examples:
     * ```
     *   "typedarray::24/17:CompositorEffect"  → "typedarray::CompositorEffect"
     *   "typedarray::27/0:"                   → "typedarray::Variant" (empty = Variant)
     *   "typeddictionary::Color;Color"        → keep as-is for typed dict resolution
     *   "BaseMaterial3D,ShaderMaterial"       → "BaseMaterial3D" (first/base type)
     *   "Mesh,-PlaneMesh,-PointMesh"          → "Mesh" (first non-excluded type)
     *   "Texture2D,Texture3D"                 → "Variant" (no common base → opaque)
     * ```
     */
    fun extractCodegenType(rawPropertyType: String): String = when {
        rawPropertyType.startsWith("typedarray::") ->
            parseTypedArray(rawPropertyType)

        rawPropertyType.startsWith("typeddictionary::") ->
            parseTypedDictionary(rawPropertyType)

        rawPropertyType.contains(",") ->
            parseMultiType(rawPropertyType)

        else -> {
            // plain type, pass through
            println("WARNING: raw property type: '$rawPropertyType' going to be exported.")
            rawPropertyType
        }
    }

    /**
     * ```text
     * "typedarray::24/17:CompositorEffect" → "typedarray::CompositorEffect"
     * "typedarray::27/0:"                  → "typedarray::Variant"
     * ```
     */
    private fun parseTypedArray(type: String): String {
        val withoutPrefix = type.removePrefix("typedarray::")
        return if (withoutPrefix.contains(":")) {
            // has hint: "24/17:CompositorEffect"
            val className = withoutPrefix.substringAfterLast(":").ifBlank { "Variant" }
            "typedarray::$className"
        } else {
            type // "typedarray::CompositorEffect" already clean
        }
    }

    /**
     * ```text
     * "typeddictionary::Color;Color" → keep, resolver handles it
     * (or break into key/value for future typed dict support)
     * ```
     */
    private fun parseTypedDictionary(type: String): String {
        // For now, Dictionary<K, V> isn't generated with full type safety
        // Return opaque Dictionary until a typed dict resolver exists
        return "Dictionary"
    }

    /**
     * ```text
     * "BaseMaterial3D,ShaderMaterial"   → "BaseMaterial3D"
     * "Mesh,-PlaneMesh,-PointMesh"      → "Mesh"
     * "Texture2D,Texture3D"             → "Variant" (no common base)
     * ```
     */
    private fun parseMultiType(type: String): String {
        val parts = type.split(",")
        // Positive types (no - prefix) are candidates
        val positives = parts.filter { !it.startsWith("-") }
        // The first positive is always the base type in Godot's convention
        // Exclusions (-X) are subtypes that shouldn't be used, not relevant for codegen
        return positives.firstOrNull() ?: "Variant"
    }
}
