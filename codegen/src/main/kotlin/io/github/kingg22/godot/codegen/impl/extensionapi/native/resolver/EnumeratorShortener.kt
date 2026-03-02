package io.github.kingg22.godot.codegen.impl.extensionapi.native.resolver

object EnumeratorShortener {

    /**
     * @param godotClassName Clase contenedora (null si es global).
     * @param godotEnumName Nombre del enum (ya mapeado/renombrado).
     * @param enumerators Lista de nombres crudos de los enumeradores (ej: ["KEY_NONE", "KEY_ESCAPE"]).
     */
    fun shortenEnumeratorNames(
        godotClassName: String?,
        godotEnumName: String,
        enumerators: List<String>,
    ): List<String> {
        // 1. Excepciones hardcodeadas
        val hardcodedPrefixes = reduceHardcodedPrefix(godotClassName, godotEnumName)
        if (hardcodedPrefixes != null) {
            return enumerators.map { tryStripPrefixes(it, hardcodedPrefixes) }
        }

        if (enumerators.size <= 1) {
            return enumerators
        }

        // 2. Buscar prefijo común mediante heurística
        val original = enumerators[0]
        var (longestPrefix, pos) = enumeratorPrefix(original, original.length) ?: return enumerators

        // Comparar con el resto de enumeradores
        for (i in 1 until enumerators.size) {
            val e = enumerators[i]
            while (!e.startsWith(longestPrefix)) {
                // Acortar el prefijo hasta encontrar coincidencia
                val nextPrefix = enumeratorPrefix(original, pos - 1)
                if (nextPrefix != null) {
                    longestPrefix = nextPrefix.first
                    pos = nextPrefix.second
                } else {
                    // No hay prefijo común
                    pos = 0
                    break
                }
            }
            if (pos == 0) break
        }

        // 3. Aplicar recorte con validaciones finales
        val lastIndex = enumerators.size - 1
        return enumerators.mapIndexed { i, e ->
            // Caso especial: constantes MAX/COUNT al final del enum
            if (i == lastIndex && (e.endsWith("_MAX") || e.endsWith("_COUNT"))) {
                return@mapIndexed "MAX"
            }

            if (pos == 0) return@mapIndexed e

            var localPos = pos
            // Si el resultado empieza por un número, retrocedemos una posición del prefijo
            // para que sea un identificador válido (ej: SOURCE_3D -> 3D es inválido, SOURCE_3D es válido)
            while (startsWithInvalidChar(e.substring(localPos))) {
                if (localPos <= 0) break
                val prevUnderscore = e.lastIndexOf('_', localPos - 2)
                localPos = if (prevUnderscore != -1) prevUnderscore + 1 else 0
            }

            e.substring(localPos)
        }
    }

    private fun reduceHardcodedPrefix(className: String?, enumName: String): Array<String>? = when {
        className == null && enumName == "Key" -> arrayOf("KEY_")

        className == null && enumName == "Error" -> arrayOf("ERR_")

        (className == "RenderingServer" || className == "Mesh") &&
            enumName == "ArrayFormat" -> arrayOf("ARRAY_FORMAT_", "ARRAY_")

        className == "AudioServer" && enumName == "SpeakerMode" -> arrayOf("SPEAKER_MODE_", "SPEAKER_")

        className == "ENetConnection" && enumName == "HostStatistic" -> arrayOf("HOST_")

        className == null && enumName == "MethodFlags" -> arrayOf("METHOD_FLAG_", "METHOD_FLAGS_")

        className == null && enumName == "KeyModifierMask" -> arrayOf("KEY_MASK_", "KEY_")

        className == "RenderingDevice" && enumName == "StorageBufferUsage" -> arrayOf("STORAGE_BUFFER_USAGE_")

        enumName == "PathfindingAlgorithm" -> arrayOf("PATHFINDING_ALGORITHM_")

        else -> null
    }

    private fun tryStripPrefixes(enumerator: String, prefixes: Array<String>): String {
        for (prefix in prefixes) {
            val stripped = enumerator.removePrefix(prefix)
            if (!startsWithInvalidChar(stripped)) {
                return stripped
            }
        }
        return enumerator
    }

    /** Busca el prefijo terminado en guion bajo más largo posible hasta [maxPos]. */
    private fun enumeratorPrefix(s: String, maxPos: Int): Pair<String, Int>? {
        if (maxPos <= 0) return null
        val sub = s.substring(0, maxPos)
        val lastUnderscore = sub.lastIndexOf('_')
        if (lastUnderscore == -1) return null

        val pos = lastUnderscore + 1
        return s.substring(0, pos) to pos
    }

    // En Kotlin los identificadores no pueden empezar con dígitos
    private fun startsWithInvalidChar(s: String): Boolean {
        if (s.isEmpty()) return true
        return s[0].isDigit()
    }
}
