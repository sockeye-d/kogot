package io.github.kingg22.godot.api

@RequiresOptIn(
    message = "This API is experimental and may change or removed in the future. The binding can be broken anytime.",
    level = ERROR,
)
@Retention(BINARY)
@Target(CLASS, FUNCTION, PROPERTY)
@MustBeDocumented
public annotation class ExperimentalGodotApi(val reason: String = "")
