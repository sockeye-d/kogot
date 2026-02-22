import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// Puedes cambiar esto o leerlo desde gradle.properties
val toolchainVersion: Provider<Int> = providers
    .gradleProperty("jvmToolchainVersion")
    .map(String::toInt)
    .orElse(24)

val javaTargetVersion: Provider<Int> = providers
    .gradleProperty("javaTargetVersion")
    .map(String::toInt)

// ---------- JAVA ----------
plugins.withId("java") {
    extensions.configure(JavaPluginExtension::class.java) {
        if (javaTargetVersion.isPresent) {
            val javaTarget = JavaVersion.toVersion(javaTargetVersion.get())
            sourceCompatibility = javaTarget
            targetCompatibility = javaTarget
        } else {
            toolchain {
                languageVersion.set(
                    JavaLanguageVersion.of(toolchainVersion.get()),
                )
            }
        }
    }
}

plugins.withId("java-library") {
    extensions.configure(JavaPluginExtension::class.java) {
        if (javaTargetVersion.isPresent) {
            val javaTarget = JavaVersion.toVersion(javaTargetVersion.get())
            sourceCompatibility = javaTarget
            targetCompatibility = javaTarget
        } else {
            toolchain {
                languageVersion.set(
                    JavaLanguageVersion.of(toolchainVersion.get()),
                )
            }
        }
    }
}

// ---------- KOTLIN JVM ----------
plugins.withId("org.jetbrains.kotlin.jvm") {
    extensions.configure(KotlinJvmProjectExtension::class.java) {
        if (javaTargetVersion.isPresent) {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(javaTargetVersion.get().toString()))
            }
        } else {
            jvmToolchain(toolchainVersion.get())
        }
    }
}

afterEvaluate {
    logger.lifecycle("JVM versions to use: ${toolchainVersion.get()}, with target: ${javaTargetVersion.orNull}")
}
