package io.github.kingg22.godot.codegen.impl.extensionapi.stubs

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import io.github.kingg22.godot.codegen.impl.commonConfiguration
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver
import io.github.kingg22.godot.codegen.impl.withExceptionContext
import io.github.kingg22.godot.codegen.models.extensionapi.UtilityFunction

/**
 * Generates the `GD` object containing Godot utility functions.
 */
class UtilityFunctionStubGenerator(private val packageName: String, private val typeResolver: TypeResolver) {
    private val methodGen = MethodStubGenerator(packageName, typeResolver)

    fun generate(functions: List<UtilityFunction>): FileSpec = withExceptionContext({
        "Generating utility functions, count: ${functions.size}"
    }) {
        val typeBuilder = TypeSpec
            .objectBuilder("GD")
            .addKdoc("Utility functions for Godot API.")

        functions.forEach { fn ->
            withExceptionContext({ "Error generating utility function '${fn.name}'" }) {
                val returnType = fn.returnType?.let { typeResolver.resolve(it) } ?: UNIT
                val funSpec = methodGen.generate(
                    name = fn.name,
                    returnType = returnType,
                    returnTypeString = fn.returnType,
                    isOpen = false,
                    arguments = fn.arguments,
                ).addKdoc("Category: %L", fn.category).build()
                typeBuilder.addFunction(funSpec)
            }
        }

        FileSpec
            .builder(packageName, "GD")
            .commonConfiguration()
            .addType(typeBuilder.build())
            .build()
    }
}
