package com.example.budge.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/**
 * Downloads a release asset into [target], reporting how far it has got.
 *
 * An interface so the update flow can be driven in tests without a network, and so the one
 * place that writes an executable file is a single short class.
 */
interface ApkDownloader {
    /**
     * Returns true only when the whole file arrived. [onProgress] is called with the bytes
     * written so far and the total the server announced, which is 0 when it did not say.
     */
    suspend fun download(
        url: String,
        target: File,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): Boolean
}

/**
 * The real download: one HTTPS GET, streamed straight to disk.
 *
 * The bytes never become a String or a ByteArray — the file is the only copy — and the
 * connection is held to https on GitHub's own host, because the address comes from a
 * response and this app is about to install what it finds there. What the address redirects
 * to is GitHub's business: TLS validates the host it actually reaches.
 */
class UrlConnectionApkDownloader
    @Inject
    constructor() : ApkDownloader {
        override suspend fun download(
            url: String,
            target: File,
            onProgress: (bytes: Long, total: Long) -> Unit,
        ): Boolean =
            withContext(Dispatchers.IO) {
                if (!url.startsWith(GITHUB_PREFIX)) return@withContext false
                runCatching {
                    val connection =
                        (URL(url).openConnection() as HttpsURLConnection).apply {
                            connectTimeout = TIMEOUT_MILLIS
                            readTimeout = TIMEOUT_MILLIS
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", USER_AGENT)
                            setRequestProperty("Accept", "application/octet-stream")
                        }
                    try {
                        if (connection.responseCode !in 200..299) return@runCatching false
                        val total = connection.contentLengthLong
                        target.parentFile?.mkdirs()
                        onProgress(0L, total)
                        connection.inputStream.use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(BUFFER_BYTES)
                                var written = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    written += read
                                    onProgress(written, total)
                                }
                            }
                        }
                        true
                    } finally {
                        connection.disconnect()
                    }
                }.getOrDefault(false)
            }

        private companion object {
            const val GITHUB_PREFIX = "https://github.com/"

            /** Generous: an APK is a couple of megabytes and mobile data is slow. */
            const val TIMEOUT_MILLIS = 30_000

            const val BUFFER_BYTES = 64 * 1024

            const val USER_AGENT = "Budge-Android"
        }
    }
