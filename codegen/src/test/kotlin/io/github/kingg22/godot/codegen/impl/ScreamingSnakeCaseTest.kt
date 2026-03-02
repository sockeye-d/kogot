package io.github.kingg22.godot.codegen.impl

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

class ScreamingSnakeCaseTest {
    @Test
    fun `test conversion cases`() {
        assertAll(
            testCases.map { (input, expected) ->
                val result = input.toScreamingSnakeCase()
                Executable { assertEquals(expected, result, "Input: $input") }
            },
        )
    }
}

private val testCases = mapOf(
    "InlineAlignment" to "INLINE_ALIGNMENT",
    "camelCase" to "CAMEL_CASE",
    "User1Login" to "USER_1_LOGIN",
    "HTTPResponseCode" to "HTTP_RESPONSE_CODE",
    "simple" to "SIMPLE",
    "Already_Snake" to "ALREADY_SNAKE",
    "A" to "A",
    "" to "",
    "   " to "",
    "My99Variables" to "MY_99_VARIABLES",
    "XMLParser" to "XML_PARSER",
)
