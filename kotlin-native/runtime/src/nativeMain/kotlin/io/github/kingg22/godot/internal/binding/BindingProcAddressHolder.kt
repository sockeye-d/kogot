package io.github.kingg22.godot.internal.binding

import io.github.kingg22.godot.internal.ffi.GDExtensionClassLibraryPtr
import io.github.kingg22.godot.internal.ffi.GDExtensionInterfaceGetProcAddress

class BindingProcAddressHolder private constructor(
    val getProcAddress: GDExtensionInterfaceGetProcAddress,
    val library: GDExtensionClassLibraryPtr,
) {
    companion object {
        fun initialize(getProcAddress: GDExtensionInterfaceGetProcAddress, libraryPtr: GDExtensionClassLibraryPtr) {
            instance = BindingProcAddressHolder(getProcAddress, libraryPtr)
        }
    }
}

private var instance: BindingProcAddressHolder? = null

val bindingProcAddressHolder: BindingProcAddressHolder
    get() = instance ?: error("GDExtension getProcAddress holder is not initialized. Call initialize() first.")
