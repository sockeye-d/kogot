package io.github.kingg22.godot.sample

import io.github.kingg22.godot.api.builtin.asStringName
import io.github.kingg22.godot.api.singleton.ClassDB
import io.github.kingg22.godot.api.utils.GD
import io.github.kingg22.godot.api.utils.print
import io.github.kingg22.godot.internal.binding.BindingProcAddressHolder
import io.github.kingg22.godot.internal.binding.ClassDBBinding
import io.github.kingg22.godot.internal.binding.ObjectBinding
import io.github.kingg22.godot.internal.binding.toGdBool
import io.github.kingg22.godot.internal.ffi.*
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.staticCFunction

private lateinit var globalLibrary: GDExtensionClassLibraryPtr

val notificationFunc: GDExtensionClassNotification2 = staticCFunction { instancePtr, notification, reversed ->
    println("Calling notification func")
    val _ = instancePtr
    val _ = notification
    val _ = reversed
    //instancePtr?.asStableRef<GodotObject>()?.get()?.notification(notification, reversed.toBoolean())
}

val createInstanceFunc: GDExtensionClassCreateInstance2 = staticCFunction { _, _ ->
    println("Create instance func called")
    val base = ClassDB.instance.instantiate("Node".asStringName()).asObject().rawPtr
    println("Base constructed")
    val self = CustomClass(base)
    println("Wrapper constructed")
    val selfRef = StableRef.create(self)
    println("Ref created")
    val selfPtr = selfRef.asCPointer()
    println("setInstance")
    ObjectBinding.instance.setInstanceRaw(
        base,
        "CustomClass".asStringName().rawPtr,
        selfPtr
    )
    memScoped {
        println("setInstanceBinding")
        ObjectBinding.instance.setInstanceBindingRaw(
            pO = base,
            pToken = globalLibrary,
            pBinding = selfPtr,
            pCallbacks = cValue<GDExtensionInstanceBindingCallbacks> {
                println("Making callbacks")
                create_callback = null
                free_callback = null
                reference_callback = null
            }.ptr
        )
    }
    println("after setInstanceBinding")
    base
}

val freeInstanceFunc: GDExtensionClassFreeInstance = staticCFunction { _, ptr ->
    if (ptr != null) {
        println("Freeing $ptr")
        ptr.asStableRef<Any>().dispose()
    } else {
        println("Ptr was null??????")
    }
}

@Suppress("unused") // Invoked by Godot
@CName("godot_kotlin_init")
fun godotKotlinInit(
    pGetProcAddress: GDExtensionInterfaceGetProcAddress,
    pLibrary: GDExtensionClassLibraryPtr,
    initialization: CPointer<GDExtensionInitialization>,
): GDExtensionBool {
    BindingProcAddressHolder.initialize(pGetProcAddress, pLibrary)

    val initialization = initialization.pointed
    initialization.initialize = staticCFunction(::initialize)
    initialization.deinitialize = staticCFunction(::deinitialize)
    initialization.userdata = null
    initialization.minimum_initialization_level = GDEXTENSION_INITIALIZATION_SCENE

    globalLibrary = pLibrary

    return GDExtensionBool.TRUE
}

private fun initialize(userdata: COpaquePointer?, level: GDExtensionInitializationLevel) {
    when (level) {
        GDEXTENSION_INITIALIZATION_CORE -> println("✓ CORE initialized")

        GDEXTENSION_INITIALIZATION_SERVERS -> println("✓ SERVERS initialized")

        GDEXTENSION_INITIALIZATION_SCENE -> {
            println("✓ SCENE initialized")
            val info = cValue<GDExtensionClassCreationInfo5> {
                is_virtual = false.toGdBool()
                is_abstract = false.toGdBool()
                is_exposed = true.toGdBool()
                set_func = null
                get_func = null
                get_property_list_func = null
                free_property_list_func = null
                property_can_revert_func = null
                property_get_revert_func = null
                validate_property_func = null
                notification_func = notificationFunc
                to_string_func = null
                reference_func = null
                unreference_func = null
                recreate_instance_func = null
                get_virtual_func = null
                get_virtual_call_data_func = null
                call_virtual_with_data_func = null
                class_userdata = null
                create_instance_func = createInstanceFunc
                free_instance_func = freeInstanceFunc
            }
            memScoped {
                ClassDBBinding.instance.registerExtensionClass5Raw(
                    globalLibrary,
                    "CustomClass".asStringName().rawPtr,
                    "Node".asStringName().rawPtr,
                    info.ptr,
                )
            }
            println("Hi")
        }

        GDExtensionInitializationLevel.GDEXTENSION_INITIALIZATION_EDITOR -> GD.print("✓ EDITOR initialized. Hello from Kotlin Native")

        // False positive https://youtrack.jetbrains.com/issue/KT-77521
        else -> println("Unexpected $level, userdata: $userdata")
    }
}

private fun deinitialize(userdata: COpaquePointer?, level: GDExtensionInitializationLevel) {
    println("✗ DEINITIALIZE LEVEL = $level, userdata: $userdata")
}
