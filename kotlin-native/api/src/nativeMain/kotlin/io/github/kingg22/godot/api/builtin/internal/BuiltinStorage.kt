package io.github.kingg22.godot.api.builtin.internal

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap

internal typealias BuiltinStorage = CPointer<ByteVar>

internal fun allocateBuiltinStorage(size: Int): BuiltinStorage = nativeHeap.allocArray(size)

internal fun freeBuiltinStorage(storage: BuiltinStorage) {
    nativeHeap.free(storage.rawValue)
}
