package io.github.kingg22.godot

import io.github.kingg22.godot.internal.api.GDExtensionBool
import io.github.kingg22.godot.internal.api.GDExtensionClassLibraryPtr
import io.github.kingg22.godot.internal.api.GDExtensionGodotVersion2
import io.github.kingg22.godot.internal.api.GDExtensionInitialization
import io.github.kingg22.godot.internal.api.GDExtensionInitializationLevel
import io.github.kingg22.godot.internal.api.GDExtensionInterfaceGetGodotVersion2
import io.github.kingg22.godot.internal.api.GDExtensionInterfaceGetProcAddress
import io.github.kingg22.godot.internal.api.GDExtensionInterfacePrintErrorWithMessage
import kotlinx.cinterop.*

public lateinit var getProcAddress: GDExtensionInterfaceGetProcAddress
public lateinit var library: GDExtensionClassLibraryPtr
public lateinit var godotPrintError: GDExtensionInterfacePrintErrorWithMessage
public lateinit var godotVersion2: GDExtensionGodotVersion2

@CName("godot_kotlin_init")
public fun main(
    pGetProcAddress: GDExtensionInterfaceGetProcAddress,
    pLibrary: GDExtensionClassLibraryPtr,
    initialization: CPointer<GDExtensionInitialization>,
): GDExtensionBool {
    println("=== Godot Kotlin Native Initialization ===")
    getProcAddress = pGetProcAddress
    library = pLibrary
    val initialization = initialization.pointed
    initialization.initialize = staticCFunction(::initialize)
    initialization.deinitialize = staticCFunction(::deinitialize)
    initialization.userdata = null
    initialization.minimum_initialization_level = GDEXTENSION_INITIALIZATION_SCENE

    return GDExtensionBool.TRUE
}

public fun initialize(userdata: COpaquePointer?, level: GDExtensionInitializationLevel) {
    println("INITIALIZE LEVEL = $level 😈")
    if (level >= GDEXTENSION_INITIALIZATION_EDITOR) {
        memScoped {
            godotPrintError = getProcAddress("print_error_with_message".cstr.ptr)
                ?.reinterpret()
                ?: return@memScoped
            val getGodotVersion2: GDExtensionInterfaceGetGodotVersion2 = getProcAddress("get_godot_version2".cstr.ptr)
                ?.reinterpret()
                ?: return@memScoped
            val struct = alloc<GDExtensionGodotVersion2>()
            getGodotVersion2(struct.ptr)
            godotPrintError(
                "godotPrintError(...)".cstr.ptr, /* description */
                "Hello World from Kotlin Native".cstr.ptr, /* message */
                "initialize(...)".cstr.ptr, /* function */
                "MainKt".cstr.ptr, /* file */
                59, /* line */
                GDExtensionBool.TRUE, /* editor_notify */
            )
            println("Godot Version: ${struct.string?.toKString()}")
        }
    }
}

public fun deinitialize(userdata: COpaquePointer?, level: GDExtensionInitializationLevel) {
    println("DEINITIALIZE LEVEL = $level 💀")
}
