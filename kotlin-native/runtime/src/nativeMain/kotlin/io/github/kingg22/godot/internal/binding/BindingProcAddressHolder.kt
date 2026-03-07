package io.github.kingg22.godot.internal.binding

import io.github.kingg22.godot.internal.ffi.GDExtensionClassLibraryPtr
import io.github.kingg22.godot.internal.ffi.GDExtensionInterfaceGetProcAddress

internal class BindingProcAddressHolder private constructor(
    val getProcAddress: GDExtensionInterfaceGetProcAddress,
    val library: GDExtensionClassLibraryPtr,
) {
    companion object {
        @Suppress("NOTHING_TO_INLINE")
        inline fun initialize(
            getProcAddress: GDExtensionInterfaceGetProcAddress,
            libraryPtr: GDExtensionClassLibraryPtr,
        ) {
            instance = BindingProcAddressHolder(getProcAddress, libraryPtr)
        }
    }
}

private var instance: BindingProcAddressHolder? = null

internal val bindingProcAddressHolder: BindingProcAddressHolder
    get() = instance ?: error("GDExtension getProcAddress holder is not initialized. Call initialize() first.")
