package io.github.kingg22.godot.internal.ffm;

public final class PropertyHint {
    public static final short NONE = 0;
    public static final short RANGE = 1;
    public static final short ENUM = 2;
    public static final short ENUM_SUGGESTION = 3;
    public static final short EXP_EASING = 4;
    public static final short LINK = 5;
    public static final short FLAGS = 6;
    public static final short LAYERS_2D_RENDER = 7;
    public static final short LAYERS_2D_PHYSICS = 8;
    public static final short LAYERS_2D_NAVIGATION = 9;
    public static final short LAYERS_3D_RENDER = 10;
    public static final short LAYERS_3D_PHYSICS = 11;
    public static final short LAYERS_3D_NAVIGATION = 12;
    public static final short LAYERS_AVOIDANCE = 37;
    public static final short FILE = 13;
    public static final short DIR = 14;
    public static final short GLOBAL_FILE = 15;
    public static final short GLOBAL_DIR = 16;
    public static final short RESOURCE_TYPE = 17;
    public static final short MULTILINE_TEXT = 18;
    public static final short EXPRESSION = 19;
    public static final short PLACEHOLDER_TEXT = 20;
    public static final short COLOR_NO_ALPHA = 21;
    public static final short OBJECT_ID = 22;
    public static final short TYPE_STRING = 23;
    public static final short NODE_PATH_TO_EDITED_NODE = 24;
    public static final short OBJECT_TOO_BIG = 25;
    public static final short NODE_PATH_VALID_TYPES = 26;
    public static final short SAVE_FILE = 27;
    public static final short GLOBAL_SAVE_FILE = 28;
    public static final short INT_IS_OBJECTID = 29;
    public static final short INT_IS_POINTER = 30;
    public static final short ARRAY_TYPE = 31;
    public static final short DICTIONARY_TYPE = 38;
    public static final short LOCALE_ID = 32;
    public static final short LOCALIZABLE_STRING = 33;
    public static final short NODE_TYPE = 34;
    public static final short HIDE_QUATERNION_EDIT = 35;
    public static final short PASSWORD = 36;
    public static final short TOOL_BUTTON = 39;
    public static final short ONESHOT = 40;
    public static final short GROUP_ENABLE = 42;
    public static final short INPUT_NAME = 43;
    public static final short FILE_PATH = 44;
    public static final short MAX = 45;

    private PropertyHint() {
        throw new UnsupportedOperationException("Utility class");
    }
}
