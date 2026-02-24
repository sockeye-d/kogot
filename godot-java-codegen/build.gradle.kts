plugins {
    id("buildlogic.kotlin-library-conventions")
    id("buildlogic.kotlin-styles-conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("com.squareup:kotlinpoet:2.2.0")
}

tasks.register<JavaExec>("generateGodotApi") {
    group = "codegen"
    description = "Generate Godot Kotlin API wrappers from extension_api.json"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.kingg22.godot.codegen.GenerateGodotApiKt")

    val inputInterfaceJson = project.rootProject
        .layout
        .projectDirectory
        .file("godot-java-binding/raw/v4_6_1/gdextension_interface.json")

    val inputExtensionApiJson = project.rootProject
        .layout
        .projectDirectory
        .file("godot-java-binding/raw/v4_6_1/extension_api.json")

    val outputDir = project.rootProject
        .layout
        .projectDirectory
        .dir("godot-java-api/build/generated/sources/godotApi")

    args(
        "--input-interface",
        inputInterfaceJson.asFile.absolutePath,
        "--input-extension",
        inputExtensionApiJson.asFile.absolutePath,
        "--output",
        outputDir.asFile.absolutePath,
        "--package",
        "io.github.kingg22.godot.api",
    )
}
