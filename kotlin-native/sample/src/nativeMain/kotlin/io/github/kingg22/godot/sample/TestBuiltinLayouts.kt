package io.github.kingg22.godot.sample

import io.github.kingg22.godot.api.builtin.Color
import io.github.kingg22.godot.api.builtin.Vector2i
import io.github.kingg22.godot.api.builtin.Vector3
import io.github.kingg22.godot.api.builtin.asVariantString
import io.github.kingg22.godot.api.utils.GD
import io.github.kingg22.godot.internal.binding.getFloat
import io.github.kingg22.godot.internal.binding.getInt
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.reinterpret

/**
 * Runtime layout verification.
 *
 * Allocates each builtin with nativeHeap using the codegen-resolved size+align,
 * then writes and reads back known field values via member offsets.
 * If alignment is wrong, the read-back values will be garbage or Godot will crash.
 *
 * Run with: godot --headless --path <project>
 * Exit code 0 = all tests passed, 1 = failures.
 */
fun testBuiltinLayouts(): Boolean {
    var passed = true

    // Vector3: float_64 -> size=24, align=4 (float fields)
    // We write x=1.0, y=2.0, z=3.0 and read them back via member offsets
    fun testVector3(): Boolean {
        val expectedX = 1.0f
        val expectedY = 2.0f
        val expectedZ = 3.0f
        val v = Vector3(expectedX.toDouble(), expectedY.toDouble(), expectedZ.toDouble())
        val base = v.rawPtr.reinterpret<ByteVar>()
        // Member offsets from JSON: x=0, y=4, z=8 (float_32) or x=0, y=8, z=16 (double_64)
        // The codegen embeds the correct offsets per build config
        val x = getFloat(base, Vector3.OFFSET_X)
        val y = getFloat(base, Vector3.OFFSET_Y)
        val z = getFloat(base, Vector3.OFFSET_Z)
        val ok = x == expectedX && v.x == expectedX && y == expectedY && v.y == expectedY && z == expectedZ &&
            v.z == expectedZ
        val expectedNewX = 5.0f
        v.x = expectedNewX
        if (!ok) GD.print("FAIL testVector3: expected (1f,2f,3f) got ($x,$y,$z)".asVariantString())
        if (v.x != expectedNewX) GD.print("FAIL testVector3: expected (5f) got ${v.x}".asVariantString())
        return ok
    }

    // Vector2i: int32 fields, align=4 always
    fun testVector2i(): Boolean {
        val expectedX = 10
        val expectedY = 20
        val v = Vector2i(expectedX.toLong(), expectedY.toLong())
        val base = v.rawPtr.reinterpret<ByteVar>()
        val x = getInt(base, Vector2i.OFFSET_X)
        val y = getInt(base, Vector2i.OFFSET_Y)
        val ok = x == expectedX && y == expectedY && v.x == expectedX && v.y == expectedY
        if (!ok) GD.print("FAIL testVector2i: expected (10,20) got ($x,$y)".asVariantString())
        return ok
    }

    // Color: always float, align=4
    fun testColor(): Boolean {
        val expectedR = 0.5f
        val expectedG = 0.25f
        val expectedB = 0.75f
        val expectedA = 1.0f
        val c = Color(expectedR.toDouble(), expectedG.toDouble(), expectedB.toDouble(), expectedA.toDouble())
        val base = c.rawPtr.reinterpret<ByteVar>()
        val r = getFloat(base, Color.OFFSET_R)
        val g = getFloat(base, Color.OFFSET_G)
        val b = getFloat(base, Color.OFFSET_B)
        val a = getFloat(base, Color.OFFSET_A)
        val ok = r == expectedR && g == expectedG && b == expectedB && a == expectedA && c.r == expectedR &&
            c.g == expectedG && c.b == expectedB && c.a == expectedA
        if (!ok) GD.print("FAIL testColor: expected (0.5f, 0.25f, 0.75f, 1.0f) got ($r,$g,$b,$a)".asVariantString())
        GD.print(
            "Color r8 = ${c.r8}, g8 = ${c.g8}, b8 = ${c.b8}, a8 = ${c.a8}, h = ${c.h}, s = ${c.s}, v = ${c.v}, okHslH = ${c.okHslH}, okHslL = ${c.okHslL}, okHslS = ${c.okHslS}"
                .asVariantString(),
        )
        return ok
    }

    passed = passed and testVector3()
    passed = passed and testVector2i()
    passed = passed and testColor()

    GD.print(
        if (passed) {
            "testBuiltinLayouts: ALL PASSED".asVariantString()
        } else {
            "testBuiltinLayouts: FAILURES DETECTED".asVariantString()
        },
    )
    return passed
}
