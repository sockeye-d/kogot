package io.github.kingg22.godot.internal.bridge;

import io.github.kingg22.godot.api.GodotClass;
import io.github.kingg22.godot.api.GodotRegistry;

import java.util.function.Supplier;

/** Bridge adapter from public registry API to ClassDB. */
public final class GodotRegistryImpl implements GodotRegistry {
    private final ClassDBBridge classDB;

    public GodotRegistryImpl(final ClassDBBridge classDB) {
        this.classDB = classDB;
    }

    @Override
    public void register(
            final String className, final String parentClassName, final Supplier<? extends GodotClass> factory) {
        classDB.registerClass(new ClassDBBridge.ClassDefinition(className, parentClassName, factory));
    }
}
