package io.github.kingg22.godot.internal;

import io.github.kingg22.godot.internal.ffm.GDExtensionInterfaceGetProcAddress;
import io.github.kingg22.godot.internal.ffm.GDExtensionInterfacePrintWarning;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/// This is a holder of C pointer reinterpreted to Java
public final class GodotPrintTest {
    static MemorySegment getProcAddress;
    static MemorySegment printWarningFunPtr;

    public static void init(final long getProcAddressPointer) {
        if (getProcAddress != null) return;
        System.out.println("[Java] Initializing GodotRuntime...");
        getProcAddress = MemorySegment.ofAddress(getProcAddressPointer)
            .reinterpret(ValueLayout.ADDRESS.byteSize(), Arena.global(), null);
        var arena = Arena.ofAuto();
        printWarningFunPtr = GDExtensionInterfaceGetProcAddress
            .invoke(getProcAddress, arena.allocateFrom("print_warning"));
        GDExtensionInterfacePrintWarning.invoke(
            printWarningFunPtr,
            arena.allocateFrom("Hello World from Java with FFM - Panama"),
            arena.allocateFrom("Nothing"),
            arena.allocateFrom("Nothing"),
            0,
            (byte) 1
        );
    }
}
