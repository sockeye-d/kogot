package io.github.kingg22.godot.codegen.impl.extensionapi

import com.squareup.kotlinpoet.TypeName
import io.github.kingg22.godot.codegen.models.extensionapi.TypeMetaHolder

/**
 * Resolves a Godot type reference to the [TypeName] used in generated code for a specific backend.
 *
 * Each backend (Kotlin Native, Java FFM, Java JNI, …) provides its own implementation,
 * since the same Godot type may map to different platform types.
 *
 * Example mappings differ per backend:
 * ```
 * Godot "int*"  → Java FFM: MemorySegment(FFMUtils.C_INT) | Kotlin Native: CPointer<Long>
 * ```
 */
interface TypeResolver {

    /**
     * Resolves a raw Godot type string (e.g. `"int"`, `"Node"`, `"typedarray::Vector2"`)
     * to a KotlinPoet [TypeName] suitable for the target backend.
     */
    fun resolve(godotType: String): TypeName

    /**
     * Resolves a [TypeMetaHolder] (type + optional meta hint).
     *
     * The default implementation defers meta handling to subclasses:
     * if [TypeMetaHolder.isRequired] or meta is null, resolves the base type;
     * otherwise applies backend-specific meta mapping.
     */
    fun resolve(holder: TypeMetaHolder): TypeName {
        if (holder.meta == null || holder.isRequired()) return resolve(holder.type)
        return runCatching { resolve(holder.meta!!) }.getOrDefault(resolve(holder.type))
    }
}
