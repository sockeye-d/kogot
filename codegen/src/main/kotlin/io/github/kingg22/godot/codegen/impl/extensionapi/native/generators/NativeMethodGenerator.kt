package io.github.kingg22.godot.codegen.impl.extensionapi.native.generators

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.UNIT
import io.github.kingg22.godot.codegen.impl.addKdocForBitfield
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver
import io.github.kingg22.godot.codegen.impl.safeIdentifier
import io.github.kingg22.godot.codegen.models.extensionapi.MethodArg

/**
 * Shared method/parameter generation logic used by both builtin class
 * generators and engine class generators.
 *
 * Knows nothing about class structure, operators, or statics —
 * those are concerns of the specific generators.
 *
 * @param body provides CodeBlock bodies (stub TODO() or real cinterop calls)
 */
class NativeMethodGenerator(private val typeResolver: TypeResolver, private val body: BodyGenerator) {

    /**
     * Builds a [FunSpec] from raw method data.
     *
     * @param name           Godot method name (will be passed through [safeIdentifier])
     * @param returnType     Godot return type string, null → Unit
     * @param isVararg       whether the method accepts trailing vararg Variant args
     * @param arguments      fixed argument list
     * @param extraModifiers additional [KModifier]s (e.g., OVERRIDE, OPERATOR)
     */
    context(context: Context)
    fun buildMethod(
        name: String,
        returnType: String?,
        isVararg: Boolean,
        arguments: List<MethodArg>,
        extraModifiers: List<KModifier> = emptyList(),
    ): FunSpec {
        val returnTypeName = returnType?.let { typeResolver.resolve(it) } ?: UNIT
        val builder = FunSpec
            .builder(safeIdentifier(name))
            .addModifiers(extraModifiers)
            .returns(returnTypeName)
            .addCode(body.todoBody())
            .addKdocForBitfield(returnType)

        // Fixed args always come first
        arguments.forEach { arg ->
            require(!isVararg || safeIdentifier(arg.name) != "args") {
                "Vararg method '$name' has a fixed arg named 'args' — rename it to avoid clash"
            }
            builder.addParameter(buildParameter(arg))
        }

        // Trailing vararg only after all fixed args
        if (isVararg) {
            builder.addParameter(
                ParameterSpec
                    .builder("args", context.classNameFor("Variant"), KModifier.VARARG)
                    .build(),
            )
        }

        return builder.build()
    }

    /**
     * Builds a [ParameterSpec] for a single [MethodArg].
     *
     * Default values from Godot JSON are emitted as `TODO()` —
     * the impl layer replaces them with actual expressions.
     */
    context(_: Context)
    fun buildParameter(arg: MethodArg): ParameterSpec {
        val type = typeResolver.resolve(arg)
        val paramBuilder = ParameterSpec.builder(safeIdentifier(arg.name), type)
        arg.defaultValue?.let { _ ->
            paramBuilder.defaultValue(body.todoDefaultValueParam())
        }
        return paramBuilder.build()
    }
}
