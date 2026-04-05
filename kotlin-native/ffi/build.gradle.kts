import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("buildlogic.kotlin-multiplatform-conventions")
    id("buildlogic.kotlin-styles-conventions")
}

kotlin {
    explicitApi()

    compilerOptions {
        optIn.addAll(
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlin.experimental.ExperimentalNativeApi",
        )
    }

    applyDefaultHierarchyTemplate()

    // linux
    linuxX64 { configureGodotInterop() }
    // mingwX64 { configureGodotInterop() }
}

fun KotlinNativeTarget.configureGodotInterop() {
    compilations.getByName("main").cinterops.create("godot") {
        packageName = "io.github.kingg22.godot.internal.ffi"
        defFile(layout.projectDirectory.file("nativeInterop/cinterop/godot.def"))
        includeDirs.allHeaders(rootProject.layout.projectDirectory.file("godot-version/v4_6_2/"))
    }
}
