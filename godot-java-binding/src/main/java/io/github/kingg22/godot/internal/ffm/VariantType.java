package io.github.kingg22.godot.internal.ffm;

public final class VariantType {

    /* VARIANT TYPES */

    public static final short NIL = 0;

    /*  atomic types */
    public static final short BOOL = 1;
    public static final short INT = 2;
    public static final short FLOAT = 3;
    public static final short STRING = 4;

    /* math types */
    public static final short VECTOR2 = 5;
    public static final short VECTOR2I = 6;
    public static final short RECT2 = 7;
    public static final short RECT2I = 8;
    public static final short VECTOR3 = 9;
    public static final short VECTOR3I = 10;
    public static final short TRANSFORM2D = 11;
    public static final short VECTOR4 = 12;
    public static final short VECTOR4I = 13;
    public static final short PLANE = 14;
    public static final short QUATERNION = 15;
    public static final short AABB = 16;
    public static final short BASIS = 17;
    public static final short TRANSFORM3D = 18;
    public static final short PROJECTION = 19;

    /* misc types */
    public static final short COLOR = 20;
    public static final short STRING_NAME = 21;
    public static final short NODE_PATH = 22;
    public static final short RID = 23;
    public static final short OBJECT = 24;
    public static final short CALLABLE = 25;
    public static final short SIGNAL = 26;
    public static final short DICTIONARY = 27;
    public static final short ARRAY = 28;

    /* typed arrays */
    public static final short PACKED_BYTE_ARRAY = 29;
    public static final short PACKED_INT32_ARRAY = 30;
    public static final short PACKED_INT64_ARRAY = 31;
    public static final short PACKED_FLOAT32_ARRAY = 32;
    public static final short PACKED_FLOAT64_ARRAY = 33;
    public static final short PACKED_STRING_ARRAY = 34;
    public static final short PACKED_VECTOR2_ARRAY = 35;
    public static final short PACKED_VECTOR3_ARRAY = 36;
    public static final short PACKED_COLOR_ARRAY = 37;
    public static final short PACKED_VECTOR4_ARRAY = 38;

    public static final short VARIANT_MAX = 39;

    private VariantType() {
        throw new UnsupportedOperationException("Utility class");
    }
}
