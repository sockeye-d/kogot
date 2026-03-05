package io.github.kingg22.godot.codegen.impl.extensionapi.stubs

import com.squareup.kotlinpoet.FileSpec
import io.github.kingg22.godot.codegen.impl.extensionapi.CodeImplGenerator
import io.github.kingg22.godot.codegen.impl.extensionapi.Context
import io.github.kingg22.godot.codegen.impl.extensionapi.TypeResolver
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi

/**
 * Generates Kotlin stubs for the full Godot extension API.
 *
 * Delegates each entity type to a focused sub-generator, keeping this class
 * as an orchestrator only (no generation logic lives here).
 */
class KotlinStubGenerator(override val typeResolver: TypeResolver, private val packageName: String) :
    CodeImplGenerator.StubGenerator {
    private val enumGen = EnumStubGenerator(packageName)
    private val classGen = ClassStubGenerator(packageName, typeResolver)
    private val builtinGen = BuiltinStubGenerator(packageName, typeResolver, enumGen)
    private val nativeGen = NativeStructureStubGenerator(packageName)
    private val utilityGen = UtilityFunctionStubGenerator(typeResolver)
    private val variantGen = VariantStubGenerator(enumGen, typeResolver)

    init {
        println("WARNING: Kotlin stub generator doesn't generateFile functional bodies, all throw TODO()")
    }

    context(_: Context)
    override fun generate(api: ExtensionApi): Sequence<FileSpec> = sequence {
        val builtinClassesPaths = api.builtinClasses.asSequence()
            .mapNotNull { builtinGen.generate(it) }
        yieldAll(builtinClassesPaths)

        val godotClassesPaths = api.classes.asSequence()
            .map { classGen.generate(it) }
        yieldAll(godotClassesPaths)

        yieldAll(utilityGen.generate(api.utilityFunctions))

        val nativeStructuresPaths = api.nativeStructures.asSequence()
            .map { nativeGen.generate(it) }
        yieldAll(nativeStructuresPaths)

        val (nestedEnums, globalEnums) = api.globalEnums.partition { it.name.contains(".") }

        if (nestedEnums.size > 2) {
            System.err.println(
                "WARNING: Nested enums (${nestedEnums.size}) [${nestedEnums.joinToString(postfix = "]") { it.name }}",
            )
        }

        val globalEnumsPaths = globalEnums.asSequence()
            .map { enumGen.generateFile(it) }
        yieldAll(globalEnumsPaths)

        yield(variantGen.generate(nestedEnums).toBuilder(packageName).build())

        if (api.globalConstants.isNotEmpty()) {
            System.err.println(
                "WARNING: Global constants not supported yet. Found: [${api.globalConstants.joinToString()}]",
            )
        }
    }
}
