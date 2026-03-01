package io.github.kingg22.godot.codegen.impl.extensionapi

import com.squareup.kotlinpoet.ClassName
import io.github.kingg22.godot.codegen.impl.renameGodotClass

/**
 * Maps every Godot type name to the Kotlin package where it will be generated.
 *
 * Built once alongside [Context].
 *
 * Consulted by [TypeResolver] implementations and all code generators to produce correct [ClassName] references.
 *
 * The resolver handles types NOT registered here (primitives, void, etc.) directly without a package lookup.
 */
class PackageRegistry internal constructor(private val typeToPackage: Map<String, String>) {
    init {
        println("INFO: PackageRegistry created with ${typeToPackage.size} entries")
    }

    /**
     * Returns the package for [godotName], or null if not registered
     * (caller should treat it as a primitive / external type).
     */
    fun packageFor(godotName: String): String? = typeToPackage[godotName]

    /**
     * Returns the [ClassName] for [godotName] using its registered package
     * and the [kotlinName] produced by [renameGodotClass].
     *
     * Throws if the type is not registered — forces all generated types
     * to be registered before code generation starts.
     */
    fun classNameFor(godotName: String, kotlinName: String = godotName.renameGodotClass()): ClassName {
        val pkg = typeToPackage[godotName] ?: error("Type '$godotName' is not registered in PackageRegistry")
        return ClassName(pkg, kotlinName)
    }

    companion object {

        /**
         * Determines the core sub-package for a non-singleton, non-editor class.
         *
         * Strategy: walk up to the last ancestor before Object (depth-1 root).
         * That root name → sub-package name.
         */
        internal fun resolveCoreSubpackage(godotName: String, inheritanceTree: InheritanceTree): String? {
            val bases = inheritanceTree.collectAllBases(godotName)
            // bases = [DirectParent, …, RootBeforeObject, Object]
            // We want the root before "Object"
            val rootAncestor = bases.dropLastWhile { it == "Object" }.lastOrNull()
                ?: return null

            return when (rootAncestor) {
                "Node" -> "node"
                "Resource" -> "resource"
                "RefCounted" -> "refcounted"
                else -> null
            }
        }
    }
}
