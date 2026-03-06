plugins {
    id("buildlogic.kotlin-application-conventions")
    id("buildlogic.kotlin-styles-conventions")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-Xtype-enhancement-improvements-strict-mode",
            "-Xwhen-expressions=indy",
        )
        // The Kotlin Compiler adds intrinsic assertions which are only relevant
        // when the code is consumed by Java users. Therefore, we can turn this off
        // when code is being consumed by Kotlin users.
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinpoet)
}

application {
    mainClass.set("io.github.kingg22.godot.codegen.GenerateGodotApiKt")
}
