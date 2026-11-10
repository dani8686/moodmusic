package com.daniel.mood

/** Pure filename helpers shared by SAF folder export, ZIP export and their tests. */
object FileNames {
    private val illegal = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

    fun safeName(value: String): String {
        val cleaned = value.map { c -> if (c in illegal || c.code < 32) '_' else c }
            .joinToString("")
            .trim()
            .trimEnd('.', ' ')
        return cleaned.ifBlank { "audio" }
    }

    fun split(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && name.length - dot <= 6) {
            name.substring(0, dot) to name.substring(dot)
        } else name to ""
    }

    fun uniqueName(requested: String, existing: Set<String>): String {
        val clean = safeName(requested)
        if (clean !in existing) return clean
        val (base, extension) = split(clean)
        var number = 2
        while ("${base}_$number$extension" in existing) number++
        return "${base}_$number$extension"
    }

    fun uniqueZipName(requested: String, used: MutableSet<String>): String {
        val clean = safeName(requested)
        if (used.add(clean)) return clean
        val (base, extension) = split(clean)
        var number = 2
        while (!used.add("${base}_$number$extension")) number++
        return "${base}_$number$extension"
    }

    fun extensionForMime(mime: String?): String = when (mime?.lowercase()) {
        "audio/mpeg", "audio/mp3" -> ".mp3"
        "audio/mp4", "audio/m4a", "audio/x-m4a" -> ".m4a"
        "audio/flac", "audio/x-flac" -> ".flac"
        "audio/ogg", "application/ogg" -> ".ogg"
        "audio/wav", "audio/x-wav" -> ".wav"
        "audio/aac", "audio/aac-adts" -> ".aac"
        "audio/x-ms-wma" -> ".wma"
        "audio/opus" -> ".opus"
        else -> ".audio"
    }

    fun withExtension(name: String, mime: String?): String {
        val clean = safeName(name)
        val (_, extension) = split(clean)
        return if (extension.isNotEmpty()) clean else clean + extensionForMime(mime)
    }
}
