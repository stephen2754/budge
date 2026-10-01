package com.example.budge.data

import java.io.Reader

/**
 * Reads at most [limit] characters, or null when there are more.
 *
 * The two places in this app that take in text from outside it — a backup file the user
 * picked and a response body from the update check — both need the same thing: an upper
 * bound on what one read may cost. Reading a foreign input whole makes the app's memory
 * whatever that input happens to be, and an [OutOfMemoryError] is an `Error`, so it escapes
 * every `catch (Exception)` on those paths and takes the process down.
 *
 * Null rather than a truncated string: half a backup and half a JSON document are not
 * partial answers, they are wrong ones, and every caller here treats unreadable input as a
 * failure it reports.
 */
internal fun Reader.readAtMost(limit: Int): String? {
    val text = StringBuilder()
    val buffer = CharArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) return text.toString()
        if (text.length + read > limit) return null
        text.append(buffer, 0, read)
    }
}
