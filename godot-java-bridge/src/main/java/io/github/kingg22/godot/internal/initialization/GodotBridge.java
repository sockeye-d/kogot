package io.github.kingg22.godot.internal.initialization;

import io.github.kingg22.godot.internal.annotations.Initializer;
import io.github.kingg22.godot.internal.bridge.BridgeContext;
import io.github.kingg22.godot.internal.ffm.GDExtensionInitializationLevel;

/** Entry point invoked by the GDExtension C bootstrap. */
@SuppressWarnings("unused") // invoked from native
final class GodotBridge {

    private GodotBridge() {
        throw new UnsupportedOperationException("Internal native bridge class, cannot be instantiated");
    }

    private static boolean runtimeInitialized = false;

    /** Called once from C when the runtime reaches SCENE and JVM startup succeeded. */
    @Initializer
    public static void initialize(final long getProcAddressPointer, final long libraryPointer) {
        System.out.println("[Java] GodotBridge.initialize() called");

        if (runtimeInitialized) {
            System.out.println("[Java] Runtime already initialized, skipping bridge bootstrap");
            return;
        }

        BridgeContext.initialize(getProcAddressPointer, libraryPointer);
        runtimeInitialized = true;

        printJVMInfo();
        System.out.println("[Java] Bridge runtime initialized");
    }

    /** Called from C for each level in initialization order. */
    public static void onInitializationLevel(final short level) {
        switch (level) {
            case GDExtensionInitializationLevel.GDEXTENSION_INITIALIZATION_CORE ->
                System.out.println("[Java] Init level CORE");
            case GDExtensionInitializationLevel.GDEXTENSION_INITIALIZATION_SERVERS ->
                System.out.println("[Java] Init level SERVERS");
            case GDExtensionInitializationLevel.GDEXTENSION_INITIALIZATION_SCENE -> {
                System.out.println("[Java] Init level SCENE");
                if (!runtimeInitialized) {
                    System.err.println("[Java] SCENE level reached without runtime bootstrap");
                }
            }
            case GDExtensionInitializationLevel.GDEXTENSION_INITIALIZATION_EDITOR ->
                System.out.println("[Java] Init level EDITOR");
            default -> System.err.println("[Java] Unknown init level: " + level);
        }
    }

    /** Called from C for each level in deinitialization order. */
    public static void onDeinitializationLevel(final short level) {
        switch (level) {
            case GDExtensionInitializationLevel.GDEXTENSION_INITIALIZATION_EDITOR ->
                System.out.println("[Java] Deinit level EDITOR");
            case GDExtensionInitializationLevel.GDEXTENSION_INITIALIZATION_SCENE -> {
                System.out.println("[Java] Deinit level SCENE");
                BridgeContext.shutdown();
                runtimeInitialized = false;
            }
            case GDExtensionInitializationLevel.GDEXTENSION_INITIALIZATION_SERVERS ->
                System.out.println("[Java] Deinit level SERVERS");
            case GDExtensionInitializationLevel.GDEXTENSION_INITIALIZATION_CORE ->
                System.out.println("[Java] Deinit level CORE");
            default -> System.err.println("[Java] Unknown deinit level: " + level);
        }
    }

    /** Called from C when JVM is going down. */
    public static void shutdown() {
        System.out.println("[Java] GodotBridge.shutdown() called");

        BridgeContext.shutdown();
        runtimeInitialized = false;

        System.out.println("[Java] Shutdown complete");
    }

    private static void printJVMInfo() {
        final var runtime = Runtime.getRuntime();
        System.out.println("[Java] JVM Info:");
        System.out.println("  Max Memory: " + (runtime.maxMemory() / 1024 / 1024) + " MB");
        System.out.println("  Total Memory: " + (runtime.totalMemory() / 1024 / 1024) + " MB");
        System.out.println("  Free Memory: " + (runtime.freeMemory() / 1024 / 1024) + " MB");
        System.out.println("  Java Version: " + System.getProperty("java.version"));
    }
}
