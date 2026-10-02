package com.example.budge.data.update

import java.io.File
import java.security.MessageDigest

/**
 * The SHA-256 of [file] as lowercase hex, or null when it cannot be read.
 */
fun sha256(file: File): String? =
    runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }.getOrNull()

/**
 * Whether [actual] is the hash [published] names.
 *
 * Every way of not knowing counts as a mismatch, not as a pass. A download whose hash cannot
 * be read, or that was published as some other algorithm, is not installed: the point of
 * checking is that a file which arrived over the network is the file that was published, and
 * "the check could not be performed" is not that.
 */
fun matchesSha256(
    published: String?,
    actual: String?,
): Boolean {
    if (actual.isNullOrBlank()) return false
    val expected =
        published
            ?.substringAfter("sha256:", missingDelimiterValue = "")
            ?.trim()
            ?.lowercase()
    return !expected.isNullOrBlank() && expected == actual.lowercase()
}
