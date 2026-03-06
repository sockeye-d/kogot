package io.github.kingg22.godot.codegen

import io.github.kingg22.godot.codegen.impl.GeneratorBackend
import io.github.kingg22.godot.codegen.impl.KotlinPoetGenerator
import io.github.kingg22.godot.codegen.models.extensionapi.ExtensionApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.measureTime

// error codes
private const val SUCCESS = 0
private const val FAILURE = 1
private const val OPTION_ERROR = 2
private const val INPUT_ERROR = 3
private const val FATAL_ERROR = 5
private const val OUTPUT_ERROR = 6

private val logger: Logger = Logger.DEFAULT

private fun printOptionError(message: String?) {
    logger.err(message ?: "Unknown error")
    logger.info("Try --help for more information.")
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

    if (optionSet.has("-h")) {
        return printHelp(SUCCESS)
    }

    if (!optionSet.has("--input-extension")) {
        logger.err("Missing input extension file file, must specify one of them.")
        return INPUT_ERROR
    }

    val extensionFile = optionSet.valueOf("--input-extension")?.let { Path(it) }
    val outputDir = optionSet.valueOf("--output")?.let { Path(it).createDirectories() }
        ?: error("Missing output directory")
    val packageName = optionSet.valueOf("--package").orEmpty()
    val backend = optionSet.valueOf("--backend").orEmpty()

    if (!outputDir.exists() || !outputDir.isDirectory()) {
        logger.err("directory.not.found", outputDir)
        return OUTPUT_ERROR
    }

    if (extensionFile != null && !extensionFile.exists()) {
        logger.err("file.not.found", extensionFile)
        return INPUT_ERROR
    }

    if (extensionFile == null) {
        logger.err("Unexpected. Missing input extension file, must specify one of them.")
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

    println("---Generator Extension API files--- Backend: $backendEnum")

    var firstPathParent: String? = null
    var generatedFilesCount = 0
    var time: Duration? = null

    try {
        time = measureTime {
            val json = Json
            val generator = KotlinPoetGenerator(packageName, backendEnum)
            val extensionApi = json.decodeFromStream<ExtensionApi>(extensionFile.inputStream())

            // Creamos un executor que usa Virtual Threads
            Executors.newVirtualThreadPerTaskExecutor().use { executor: ExecutorService ->
                val fileSpecSequence = generator.generate(extensionApi)
                val futures = mutableListOf<Future<*>>()

                // El hilo principal recorre la secuencia (Lazy)
                for (fileSpec in fileSpecSequence) {
                    // El hilo principal solo envía la tarea, no espera.
                    // El 'writeTo' ocurre DENTRO del Virtual Thread.
                    val future = executor.submit {
                        val path = fileSpec.writeTo(outputDir)

                        // Actualización segura del primer path para el log
                        if (firstPathParent == null) firstPathParent = path.parent.toString()
                    }
                    futures.add(future)
                }

                // Esperamos resultados mientras se escriben de forma paralela
                futures.forEach { it.get() }
                generatedFilesCount = futures.size
            }
        }
    } catch (e: Exception) {
        logger.fatal(e, "file.read.error", extensionFile)
        return FATAL_ERROR
    } finally {
        println("---Total generated: $generatedFilesCount in $time to => $firstPathParent ---")
    }

    return if (logger.hasErrors()) FAILURE else SUCCESS
}

fun main(args: Array<String>) {
    exitProcess(run(args))
}
