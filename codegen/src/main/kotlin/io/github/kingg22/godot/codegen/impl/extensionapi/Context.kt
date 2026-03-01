package io.github.kingg22.godot.codegen.impl.extensionapi

import com.squareup.kotlinpoet.ClassName
import io.github.kingg22.godot.codegen.impl.extensionapi.PackageRegistry.Companion.resolveCoreSubpackage
import io.github.kingg22.godot.codegen.impl.renameGodotClass
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import io.github.kingg22.godot.codegen.models.extensionapi.GodotClass
import io.github.kingg22.godot.codegen.models.extensionapi.domain.GodotVersion

/**
 * Inmutable, built once from [ExtensionApi].
 *
 * Provides fast lookups about the Godot type system:
 * class hierarchy, singletons, builtins, native structures.
 *
 * No generation state lives here — this is pure query over the parsed API.
 */
class Context private constructor(
    private val builtinTypes: Set<String>,
    private val nativeStructureTypes: Set<String>,
    private val singletons: Set<String>,

    /**
     * Classes that are "final" in Godot: singletons and abstract engine classes with no virtual interface.
     *
     * TODO investigate if this is still true in 4.0
     */
    private val finalClasses: Set<String>,
    private val inheritanceTree: InheritanceTree,
    val godotVersion: GodotVersion,
    private val packageRegistry: PackageRegistry,
) {
    init {
        println("INFO: Context created to generate for Godot: $godotVersion")
    }

    // ── Type classification ───────────────────────────────────────────────────

    fun isBuiltin(godotName: String): Boolean = godotName in builtinTypes
    fun isNativeStructure(godotName: String): Boolean = godotName in nativeStructureTypes
    fun isSingleton(godotName: String): Boolean = godotName in singletons
    fun isSingleton(godotClass: GodotClass): Boolean = godotClass.name in singletons
    fun isFinal(godotName: String): Boolean = godotName in finalClasses

    // ── Hierarchy ─────────────────────────────────────────────────────────────

    /** Direct parent class, or null if [godotName] is a root (Object). */
    fun directBase(godotName: String): String? = inheritanceTree.directBase(godotName)

    /**
     * All base classes from nearest to furthest (Object), **excluding** [godotName] itself.
     *
     * Example: Node3D → [Node, Object]
     */
    fun allBases(godotName: String): List<String> = inheritanceTree.collectAllBases(godotName)

    /**
     * Returns true if [godotName] IS [baseName] or transitively inherits from it.
     *
     * Reflexive: `inherits("Node", "Node") == true`
     */
    fun inherits(godotName: String, baseName: String): Boolean = inheritanceTree.inherits(godotName, baseName)

    // ── Package registry ───────────────────────────────────────────────────────────────

    /**
     * Returns the package for [godotName], or null if not registered
     * (caller should treat it as a primitive / external type).
     */
    fun packageFor(godotName: String): String? = packageRegistry.packageFor(godotName)

    /**
     * Returns the [ClassName] for [godotName] using its registered package
     * and the [kotlinName] produced by [renameGodotClass].
     *
     * Throws if the type is not registered — forces all generated types
     * to be registered before code generation starts.
     */
    fun classNameFor(godotName: String, kotlinName: String = godotName.renameGodotClass()): ClassName =
        packageRegistry.classNameFor(godotName, kotlinName)

    companion object {
        fun buildFromApi(api: ExtensionApi, rootPackage: String): Context {
            // add Variant as builtin because is absent
            val builtinTypes = mutableSetOf("Variant")
            val nativeStructureTypes = mutableSetOf<String>()
            val singletons = mutableSetOf<String>()
            val finalClasses = mutableSetOf<String>()
            val tree = InheritanceTree()

            api.singletons.forEach { singleton ->
                singletons += singleton.name
                finalClasses += singleton.name
            }

            api.builtinClasses.forEach { builtin ->
                builtinTypes += builtin.name
            }

            api.nativeStructures.forEach { ns ->
                nativeStructureTypes += ns.name
            }

            api.classes.forEach { cls ->
                cls.inherits?.takeIf { it.isNotBlank() }?.let { base ->
                    tree.insert(derived = cls.name, base = base)
                }
            }

            check(builtinTypes.none { it in singletons }) {
                "Found a builtin type that is also a singleton: ${builtinTypes.intersect(singletons)}"
            }

            fun isSingleton(name: String) = name in singletons

            fun buildPackageRegistry(api: ExtensionApi): PackageRegistry {
                val map = mutableMapOf<String, String>()

                // Builtins
                api.builtinClasses.forEach { cls ->
                    map[cls.name] = "$rootPackage.api.builtin"
                }
                // Variant is injected manually in Context but not in builtinClasses
                map["Variant"] = "$rootPackage.api.builtin"

                // Engine classes
                api.classes.forEach { cls ->
                    val pkg = when {
                        isSingleton(cls.name) -> "$rootPackage.api.singleton"

                        // los 79 confiables
                        cls.apiType == "editor" -> "$rootPackage.api.editor"

                        else -> {
                            val sub = resolveCoreSubpackage(cls.name, tree)
                            val subPack = sub?.let { ".$sub" }.orEmpty()
                            "$rootPackage.api.core$subPack"
                        }
                    }
                    map[cls.name] = pkg
                }

                // Global enums
                api.globalEnums.forEach { enum ->
                    map[enum.name] = "$rootPackage.api.global"
                }

                // Native structures — manual impl, but types must be resolvable
                api.nativeStructures.forEach { ns ->
                    map[ns.name] = "$rootPackage.api.native"
                }

                // Utility functions → no types per se, but category packages
                // are registered separately if needed

                return PackageRegistry(map)
            }

            return Context(
                builtinTypes = builtinTypes,
                nativeStructureTypes = nativeStructureTypes,
                singletons = singletons,
                finalClasses = finalClasses,
                inheritanceTree = tree,
                godotVersion = GodotVersion(api.header),
                packageRegistry = buildPackageRegistry(api),
            )
        }
    }
}
