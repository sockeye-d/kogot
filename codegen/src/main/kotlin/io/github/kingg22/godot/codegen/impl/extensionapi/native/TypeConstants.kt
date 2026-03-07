package io.github.kingg22.godot.codegen.impl.extensionapi.native

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

val COPAQUE_POINTER = ClassName("kotlinx.cinterop", "COpaquePointer")
val C_POINTER = ClassName("kotlinx.cinterop", "CPointer")
val C_STRUCT_VAR = ClassName("kotlinx.cinterop", "CStructVar")
val C_STRUCT_VAR_TYPE = ClassName("kotlinx.cinterop", "CStructVar", "Type")
val C_ARRAY_POINTER = ClassName("kotlinx.cinterop", "CArrayPointer")
val C_NATIVE_PTR = ClassName("kotlinx.cinterop", "NativePtr")
val C_FUNCTION = ClassName("kotlinx.cinterop", "CFunction")
val C_NAME_ANNOTATION = ClassName("kotlin.native", "CName")

// CVar types (used for pointer targets)
val BYTE_VAR = ClassName("kotlinx.cinterop", "ByteVar")
val SHORT_VAR = ClassName("kotlinx.cinterop", "ShortVar")
val INT_VAR = ClassName("kotlinx.cinterop", "IntVar")
val LONG_VAR = ClassName("kotlinx.cinterop", "LongVar")
val U_BYTE_VAR = ClassName("kotlinx.cinterop", "UByteVar")
val U_SHORT_VAR = ClassName("kotlinx.cinterop", "UShortVar")
val U_INT_VAR = ClassName("kotlinx.cinterop", "UIntVar")
val U_LONG_VAR = ClassName("kotlinx.cinterop", "ULongVar")
val FLOAT_VAR = ClassName("kotlinx.cinterop", "FloatVar")
val DOUBLE_VAR = ClassName("kotlinx.cinterop", "DoubleVar")

val LAZY_MODE = ClassName("kotlin", "LazyThreadSafetyMode")
val lazyMethod = MemberName("kotlin", "lazy")

val memScoped = MemberName("kotlinx.cinterop", "memScoped")
val cstr = MemberName("kotlinx.cinterop", "cstr", true)
val reinterpret = MemberName("kotlinx.cinterop", "reinterpret", true)

val PRIMITIVE_NUMERIC_TYPES = setOf(
    "int8_t", "int8",
    "short", "int16_t", "int16",
    "int", "int32_t", "int32",
    "long long", "int64_t", "int64", "long", "intptr_t",
    "float", "double",
)
