package io.github.kingg22.godot

import io.github.kingg22.godot.internal.api.GDExtensionBool
import io.github.kingg22.godot.internal.api.GDExtensionClassLibraryPtr
import io.github.kingg22.godot.internal.api.GDExtensionInitialization
import io.github.kingg22.godot.internal.api.GDExtensionInitializationLevel
import io.github.kingg22.godot.internal.api.GDExtensionInterfaceGetProcAddress
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.pointed
import kotlinx.cinterop.staticCFunction

public lateinit var getProcAddress: GDExtensionInterfaceGetProcAddress
public lateinit var library: GDExtensionClassLibraryPtr

@CName("godot_kotlin_init")
public fun main(
    pGetProcAddress: GDExtensionInterfaceGetProcAddress?,
    pLibrary: GDExtensionClassLibraryPtr?,
    initialization: CPointer<GDExtensionInitialization>?,
): GDExtensionBool {
    println("=== Godot Kotlin Native Initialization ===")

    requireNotNull(pGetProcAddress) { "pGetProcAddress must not be null" }
    requireNotNull(pLibrary) { "pLibrary must not be null" }
    requireNotNull(initialization) { "initialization must not be null" }

    getProcAddress = pGetProcAddress
    library = pLibrary
    val initialization = initialization.pointed
    initialization.initialize = staticCFunction(::initialize)
    initialization.deinitialize = staticCFunction(::deinitialize)
    initialization.userdata = null
    initialization.minimum_initialization_level = GDEXTENSION_INITIALIZATION_SCENE

    return 1u
}

public fun initialize(userdata: COpaquePointer?, level: GDExtensionInitializationLevel) {
    println("INITIALIZE LEVEL = $level 😈")
}

public fun deinitialize(userdata: COpaquePointer?, level: GDExtensionInitializationLevel) {
    println("DEINITIALIZE LEVEL = $level 💀")
}
