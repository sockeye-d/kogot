package io.github.kingg22.godot.internal;

import io.github.kingg22.godot.internal.ffm.GDExtensionInterfaceGetProcAddress;
import io.github.kingg22.godot.internal.ffm.GDExtensionInterfacePrintWarning;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/// This is a holder of C pointer reinterpreted to Java
public final class GodotPrintTest {
    public static void init(final MemorySegment getProcAddress) {
        System.out.println("[Java] Testing Print of Godot...");
        var arena = Arena.ofAuto();
        var printWarningFunPtr =
                GDExtensionInterfaceGetProcAddress.invoke(getProcAddress, arena.allocateFrom("print_warning"));
        GDExtensionInterfacePrintWarning.invoke(
                printWarningFunPtr,
                arena.allocateFrom("Hello World from Java with FFM - Panama"),
                arena.allocateFrom("Nothing"),
                arena.allocateFrom("Nothing"),
                0,
                (byte) 1);
    }
}
