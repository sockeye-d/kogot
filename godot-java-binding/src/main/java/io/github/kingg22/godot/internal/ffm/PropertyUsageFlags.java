package io.github.kingg22.godot.internal.ffm;

public final class PropertyUsageFlags {
    public static final short NONE = 0;
    public static final short STORAGE = 2;
    public static final short EDITOR = 4;
    public static final short INTERNAL = 8;
    public static final short CHECKABLE = 16;
    public static final short CHECKED = 32;
    public static final short GROUP = 64;
    public static final short CATEGORY = 128;
    public static final short SUBGROUP = 256;
    public static final short CLASS_IS_BITFIELD = 512;
    public static final short NO_INSTANCE_STATE = 1024;
    public static final short RESTART_IF_CHANGED = 2048;
    public static final short SCRIPT_VARIABLE = 4096;
    public static final short STORE_IF_NULL = 8192;
    public static final short UPDATE_ALL_IF_MODIFIED = 16384;
    public static final int SCRIPT_DEFAULT_VALUE = 32768;
    public static final int CLASS_IS_ENUM = 65536;
    public static final int NIL_IS_VARIANT = 131072;
    public static final int ARRAY = 262144;
    public static final int ALWAYS_DUPLICATE = 524288;
    public static final int NEVER_DUPLICATE = 1048576;
    public static final int HIGH_END_GFX = 2097152;
    public static final int NODE_PATH_FROM_SCENE_ROOT = 4194304;
    public static final int RESOURCE_NOT_PERSISTENT = 8388608;
    public static final int KEYING_INCREMENTS = 16777216;
    public static final int DEFERRED_SET_RESOURCE = 33554432;
    public static final int EDITOR_INSTANTIATE_OBJECT = 67108864;
    public static final int EDITOR_BASIC_SETTING = 134217728;
    public static final int READ_ONLY = 268435456;
    public static final int SECRET = 536870912;
    public static final short DEFAULT = STORAGE | EDITOR;
    public static final short NO_EDITOR = STORAGE;

    private PropertyUsageFlags() {
        throw new UnsupportedOperationException("Utility class");
    }
}
