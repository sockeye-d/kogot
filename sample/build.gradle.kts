plugins {
    id("buildlogic.java-library-conventions")
    id("buildlogic.java-styles-conventions")
    id("buildlogic.java-null-check")
    id("buildlogic.jvm-godot-registry")
    id("buildlogic.godot-export-conventions")
}

dependencies {
    implementation(projects.godotJavaBridge)
}
