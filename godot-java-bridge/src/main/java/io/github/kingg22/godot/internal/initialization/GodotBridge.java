package io.github.kingg22.godot.internal.initialization;

import org.jspecify.annotations.NullMarked;

/**
 * Main bridge class between Godot and Java
 * This class will be called from the C GDExtension
 */
@SuppressWarnings("unused") // invoked with JNI
@NullMarked
final class GodotBridge {
    private static boolean initialized = false;

    /**
     * Called from C when GDExtension initializes
     */
    public static void initialize(final long getProcAddressPointer, final long libraryPointer) {
        System.out.println("[Java] GodotBridge.initialize() called");

        if (initialized) {
            System.out.println("[Java] Already initialized, skipping...");
            return;
        }

        try {
            // Initialize your game systems here
            System.out.println("[Java] Setting up game systems...");

            // Example: Load configurations
            loadConfigurations();

            // Example: Initialize managers
            initializeManagers();

            initialized = true;
            System.out.println("[Java] Initialization complete!");
        } catch (Exception e) {
            System.err.println("[Java] Error during initialization:");
            e.printStackTrace();
        }
    }

    /**
     * Called from C when GDExtension is being unloaded
     */
    public static void shutdown() {
        System.out.println("[Java] GodotBridge.shutdown() called");

        if (!initialized) {
            return;
        }

        try {
            // Cleanup your resources here
            System.out.println("[Java] Cleaning up resources...");

            initialized = false;
            System.out.println("[Java] Shutdown complete!");

        } catch (Exception e) {
            System.err.println("[Java] Error during shutdown:");
            e.printStackTrace();
        }
    }

    /**
     * Example: Method that can be called from Godot via JNI
     */
    public static String processGameLogic(String input) {
        System.out.println("[Java] Processing: " + input);
        // Your game logic here
        return "Processed: " + input;
    }

    /**
     * Example method for loading configurations
     */
    private static void loadConfigurations() {
        System.out.println("[Java] Loading configurations...");
        // Load your config files, databases, etc.
    }

    /**
     * Example method for initializing managers
     */
    private static void initializeManagers() {
        System.out.println("[Java] Initializing managers...");
        // Initialize your game managers (audio, network, etc.)
    }

    /**
     * Example: Get JVM information
     */
    public static void printJVMInfo() {
        Runtime runtime = Runtime.getRuntime();
        System.out.println("[Java] JVM Info:");
        System.out.println("  Max Memory: " + (runtime.maxMemory() / 1024 / 1024) + " MB");
        System.out.println("  Total Memory: " + (runtime.totalMemory() / 1024 / 1024) + " MB");
        System.out.println("  Free Memory: " + (runtime.freeMemory() / 1024 / 1024) + " MB");
        System.out.println("  Java Version: " + System.getProperty("java.version"));
    }
}
