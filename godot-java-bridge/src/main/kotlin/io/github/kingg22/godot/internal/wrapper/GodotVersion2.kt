package io.github.kingg22.godot.internal.wrapper

import io.github.kingg22.godot.internal.ffm.GDExtensionGodotVersion2
import java.lang.foreign.MemorySegment

/**
 * ```c++
 * struct {
 *     uint32_t major;
 *     uint32_t minor;
 *     uint32_t patch;
 *     uint32_t hex;
 *     const char *status;
 *     const char *build;
 *     const char *hash;
 *     uint64_t timestamp;
 *     const char *string;
 * }
 * ```
 *
 * @param hex Full version encoded as hexadecimal with one byte (2 hex digits) per number
 * (e.g., for "3.1.12" it would be 0x03010C)
 * @param status e.g. "stable", "beta", "rc1", "rc2"
 * @param build e.g. "custom_build"
 * @param hash Full Git commit hash.
 * @param timestamp Git commit date UNIX timestamp in seconds, or 0 if unavailable.
 * @param string e.g. "Godot v3.1.4.stable.official.mono"
 * @see io.github.kingg22.godot.internal.ffm.GDExtensionInterfaceGetGodotVersion2
 */
@JvmRecord
@ConsistentCopyVisibility
data class GodotVersion2 private constructor(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val hex: Int,
    val status: String,
    val build: String,
    val hash: String,
    val timestamp: Long,
    val string: String,
) {
    override fun toString(): String = string

    constructor(struct: MemorySegment) : this(
        major = GDExtensionGodotVersion2.major(struct),
        minor = GDExtensionGodotVersion2.minor(struct),
        patch = GDExtensionGodotVersion2.patch(struct),
        hex = GDExtensionGodotVersion2.hex(struct),
        status = GDExtensionGodotVersion2.status(struct).getString(0),
        build = GDExtensionGodotVersion2.build(struct).getString(0),
        hash = GDExtensionGodotVersion2.hash(struct).getString(0),
        timestamp = GDExtensionGodotVersion2.timestamp(struct),
        string = GDExtensionGodotVersion2.string(struct).getString(0),
    )
}
