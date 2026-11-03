package dev.anujyadav.harmoniq.services.globalsearch.download

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object FileNames {
    // "Coldplay - Yellow.m4a", 2 -> "Coldplay - Yellow (2).m4a"
    fun withCollisionSuffix(fileName: String, attempt: Int): String {
        if (attempt <= 1) return fileName
        val dot = fileName.lastIndexOf('.')
        return when {
            dot <= 0 -> "$fileName ($attempt)"
            else -> "${fileName.substring(0, dot)} ($attempt)${fileName.substring(dot)}"
        }
    }

    fun withExtension(fileName: String, extension: String): String {
        val dot = fileName.lastIndexOf('.')
        val base = if (dot <= 0) fileName else fileName.substring(0, dot)
        return "$base.$extension"
    }

    fun extensionOf(fileName: String) = fileName.substringAfterLast('.', "").lowercase()
}

object Sha256 {
    private const val BUFFER_BYTES = 64 * 1024

    fun of(file: File): String = file.inputStream().use { of(it) }

    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
