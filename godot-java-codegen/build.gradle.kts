plugins {
    id("buildlogic.kotlin-application-conventions")
    id("buildlogic.kotlin-styles-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinpoet)
}

application {
    mainClass.set("io.github.kingg22.godot.codegen.GenerateGodotApiKt")
}

val generateGodotExtensionApi = tasks.register<GenerateGodotTask>("generateGodotExtensionApi") {
    description = "Generate Godot Extension API wrappers"

    inputExtension.convention(
        rootProject.layout.projectDirectory
            .file("godot-java-binding/raw/v4_6_1/extension_api.json"),
    )

    outputDir.convention(
        rootProject.layout.projectDirectory
            .dir("godot-java-api/build/generated/sources/godotApi"),
    )

    packageName.convention("io.github.kingg22.godot.api")
}

val generateGDExtensionInterface = tasks.register<GenerateGodotTask>("generateGDExtensionInterface") {
    description = "Generate GDExtension interface bindings"

    inputInterface.convention(
        rootProject.layout.projectDirectory
            .file("godot-java-binding/raw/v4_6_1/gdextension_interface.json"),
    )

    outputDir.convention(
        rootProject.layout.projectDirectory
            .dir("godot-java-api/build/generated/sources/gdextensionInterface"),
    )

    packageName.convention("io.github.kingg22.godot.gdextension")
}
