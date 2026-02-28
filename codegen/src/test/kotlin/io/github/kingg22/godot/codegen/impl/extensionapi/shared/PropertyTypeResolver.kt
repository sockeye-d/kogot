package io.github.kingg22.godot.codegen.impl.extensionapi.shared

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

class PropertyTypeResolver {
    private val parser = PropertyTypeParser

    @Test
    fun `when pass param types, then return expected types`() {
        assertAll(
            TYPES_EXPECTED.map { (godotType, expected) ->
                val parsed = parser.extractCodegenType(godotType)
                Executable { assertEquals(expected, parsed) }
            },
        )
    }
}

private val TYPES_EXPECTED = mapOf(
    "BaseMaterial3D,ShaderMaterial" to "BaseMaterial3D",
    "Mesh,-PlaneMesh,-PointMesh,-QuadMesh,-RibbonTrailMesh" to "Mesh",
    "typedarray::24/17:CompositorEffect" to "typedarray::CompositorEffect",
    "typeddictionary::Color;Color" to "Dictionary", // FIXME can be Map<Color, Color> or typed class
    "typedarray::27/0:" to "typedarray::Variant",
    "GradientTexture1D" to "GradientTexture1D",
)
