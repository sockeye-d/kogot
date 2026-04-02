# Contributing

> [!NOTE]
> The project is still in development. This is not ready for anything yet.

1. Clone the repository.
2. Run `./gradlew assemble` to compile the project.

## Testing the sample code
1. After compiling the project, copy the `kotlin-native/sample/build/bin` folder to `mi-juego-prueba/addons/gdkotlin-native`
2. Run the `mi-juego-prueba` project with [Godot v4.6.1](https://godotengine.org/download/archive/)
- Currently only tested on Linux. I hope to test on Windows and macOS soon.
- You can change the code of the sample project, but only API calls can be performed. Custom code is not supported yet.
- If you have problems with the Godot project, delete the `.uid` file of `addons/gdkotlin-native`.

## Resources
- [Java 25 SE – Foreign Function and Memory API](https://docs.oracle.com/en/java/javase/25/core/foreign-function-and-memory-api.html)
- [Kotlin/Native Guide](https://kotlinlang.org/docs/native-overview.html)
- More info in the _docs_ folder.
