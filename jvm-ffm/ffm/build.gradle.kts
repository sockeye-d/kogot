plugins {
    // Apply the java conventions plugin from build-logic.
    id("buildlogic.java-library-conventions")
    id("buildlogic.java-null-check")
    id("buildlogic.java-styles-conventions") // Don't apply styles because it's not working with jextract output
}

val jextract by tasks.registering(Exec::class) {
    group = "codegen"
    description = "Run jextract to generate bindings"

    // Params
    val targetPackage = "io.github.kingg22.godot.internal.ffm"
    val headerClassName = "FFMUtils"

    // 1. Forzar Linux x64
    doFirst {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        if (!(os.contains("linux") && (arch == "amd64" || arch == "x86_64"))) {
            throw GradleException("jextract task only supports Linux x64. Detected: $os $arch")
        }
    }

    // ⚙️ Configurables
    val jextractBinary = layout.projectDirectory.file("tools/jextract-25/bin/jextract").asFile
    val argsFile = layout.projectDirectory.file("scripts/v4_6_1/godot_includes.txt").asFile
    val headerPath = rootProject.layout.projectDirectory.file("godot-version/v4_6_1/gdextension_interface.h").asFile
    val outputDir = layout.buildDirectory.dir("generated/jextract").get().asFile
    require(jextractBinary.exists() && argsFile.exists() && headerPath.exists()) {
        "jextract binary, args file, or header not found"
    }

    inputs.files(argsFile, headerPath)
    outputs.dir(outputDir)

    // 🧹 Limpiar output previo
    doFirst {
        outputDir.deleteRecursively()
        outputDir.mkdirs()
    }

    executable = jextractBinary.absolutePath

    args(
        "@${argsFile.absolutePath}",
        "--target-package",
        targetPackage,
        "--header-class-name",
        headerClassName,
        "--output",
        outputDir.absolutePath,
        headerPath.absolutePath,
    )

    // 🗑️ Eliminar archivos específicos tras generar
    doLast {
        val targetPackageAsPath = targetPackage.replace(".", "/")
        val file1 = outputDir.resolve(targetPackageAsPath).resolve("$headerClassName.java")
        val file2 = outputDir.resolve(targetPackageAsPath).resolve($$"$${headerClassName}$shared.java")
        require(file1.exists() && file2.exists()) { "Generated files not found: $file1, $file2" }

        file1.delete()
        file2.delete()
    }
}

sourceSets {
    main {
        java.srcDir(jextract.map { it.outputs.files })
    }
}

tasks.compileJava {
    dependsOn(jextract)
}

tasks.spotlessJava {
    dependsOn(jextract)
}
