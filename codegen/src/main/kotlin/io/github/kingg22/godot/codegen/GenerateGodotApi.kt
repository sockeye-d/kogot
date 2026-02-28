package io.github.kingg22.godot.codegen

import io.github.kingg22.godot.codegen.impl.GeneratorBackend
import io.github.kingg22.godot.codegen.impl.KotlinPoetGenerator
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import io.github.kingg22.godot.codegen.models.gextensioninterface.GDExtensionInterface
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.IOException
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

// error codes
private const val SUCCESS = 0
private const val FAILURE = 1
private const val OPTION_ERROR = 2
private const val INPUT_ERROR = 3
private const val FATAL_ERROR = 5
private const val OUTPUT_ERROR = 6

private val logger: Logger = Logger.DEFAULT

private fun printOptionError(message: String?) {
    logger.err(
        """{0}\n\
        Usage: jextract <options> <header file>\n
        Use --help for a list of possible options
        """.trimIndent(),
        message,
    )
}

private fun printHelp(exitCode: Int): Int {
    logger.info("TODO make Help message")
    return exitCode
}

@OptIn(ExperimentalSerializationApi::class)
private fun run(args: Array<String>): Int {
    val args = try {
        CommandLine.parse(args.toMutableList())
    } catch (ioexp: IOException) {
        logger.fatal(ioexp, "argfile.read.error", ioexp)
        return OPTION_ERROR
    }

    val parser = OptionParser.builder {
        accepts("--input-interface", listOf("-ii", "--input-file-interface"), "help.input", true)
        accepts("--input-extension", listOf("-ie", "--input-file-extension"), "help.input", true)
        accepts("--backend", listOf("-b", "--backend"), "help.backend", true)

        accepts("--output", listOf("-o", "--output-dir"), "help.output", true)
        accepts("--package", listOf("-p"), "help.package", true)

        // optionals
        accepts("-h", listOf("-?", "--help"), "help.h", false)
        accepts("--version", "help.version", false)
    }

    val optionSet = try {
        parser.parse(args)
    } catch (oe: OptionParser.OptionException) {
        printOptionError(oe.message)
        return OPTION_ERROR
    }

    /*
        if (optionSet.has("--version")) {
            val version = JextractTool::class.java.getModule().getDescriptor().version()
            logger.info(
                "jextract.version",
                version.get(),
                System.getProperty("java.runtime.version"),
                LibClang.version(),
            )
            return SUCCESS
        }
     */

    if (optionSet.has("-h")) {
        return printHelp(SUCCESS)
    }

    if (!optionSet.has("--input-extension") && !optionSet.has("--input-interface")) {
        logger.err("Missing input extension file or input interface file, must specify one of them.")
        return INPUT_ERROR
    }

    val extensionFile = optionSet.valueOf("--input-extension")?.let { Path(it) }
    val interfaceFile = optionSet.valueOf("--input-interface")?.let { Path(it) }
    val outputDir = Path(optionSet.valueOf("--output")!!).createDirectories()
    val packageName = optionSet.valueOf("--package").orEmpty()
    val backend = optionSet.valueOf("--backend").orEmpty()

    if (!outputDir.exists() || !outputDir.isDirectory()) {
        logger.err("directory.not.found", outputDir)
        return OUTPUT_ERROR
    }

    if (interfaceFile != null && !interfaceFile.exists()) {
        logger.err("file.not.found", interfaceFile)
        return INPUT_ERROR
    }

    if (extensionFile != null && !extensionFile.exists()) {
        logger.err("file.not.found", interfaceFile)
        return INPUT_ERROR
    }

    if (extensionFile == null && interfaceFile == null) {
        logger.err("Unexpected. Missing input extension file or input interface file, must specify one of them.")
        return INPUT_ERROR
    }

    if (backend.isBlank()) {
        logger.err("Missing backend, must specify one of: ${GeneratorBackend.entries.joinToString { it.name }}")
        return INPUT_ERROR
    }

    val backendEnum = GeneratorBackend.entries.find { it.name.equals(backend, true) } ?: run {
        logger.err("Invalid backend: $backend, must be one of: ${GeneratorBackend.entries.joinToString { it.name }}")
        return INPUT_ERROR
    }

    val json = Json
    val generator = KotlinPoetGenerator(packageName, backendEnum)

    try {
        if (interfaceFile != null) {
            System.err.println(
                "WARNING: Using deprecated GDExtensionInterface No-OP generation, please replace usage with jextract for FFM or cinterop for Kotlin/Native",
            )
            val extensionInterface = json.decodeFromStream<GDExtensionInterface>(interfaceFile.inputStream())
            val paths = generator.generate(extensionInterface, outputDir)
            println("---Generated GDExtension Interface files---:")
            println("---Total: ${paths.size} => ${paths.firstOrNull()?.parent} ---")
        }
        if (extensionFile != null) {
            println()
            val extensionApi = json.decodeFromStream<ExtensionApi>(extensionFile.inputStream())
            val paths = generator.generate(extensionApi, outputDir)
            println("---Generated Extension API files---")
            println("---Total: ${paths.size} => ${paths.firstOrNull()?.parent} ---")
        }
    } catch (e: Exception) {
        logger.fatal(e, "file.read.error", interfaceFile)
        return FATAL_ERROR
    }

    return if (logger.hasErrors()) FAILURE else SUCCESS
}

fun main(args: Array<String>) {
    exitProcess(run(args))
}
