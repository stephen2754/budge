package com.example.budge.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
     * written so far and the total the server announced, which is **-1** when it did not say
     * — the caller treats anything that is not positive as "unknown".
     *
     * Must be cancellable: the caller stops a download by cancelling the coroutine, and a
     * loop that never observes cancellation would keep going after the user asked it to stop.
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
                        // The address redirects to whichever host GitHub serves assets from,
                        // so the host that actually answers is the one to check: the original
                        // URL being github.com says nothing about where the bytes came from.
                        if (!isGithubHost(connection.url.host)) return@runCatching false
                        val total = connection.contentLengthLong
                        // A response does not get to decide how much of this device it fills.
                        // The size is refused when it is announced, and the loop stops when it
                        // was not announced at all.
                        if (total > MAX_APK_BYTES) return@runCatching false
                        target.parentFile?.mkdirs()
                        onProgress(0L, total)
                        connection.inputStream.use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(BUFFER_BYTES)
                                var written = 0L
                                while (true) {
                                    // `read` blocks on a socket and `cancel()` cannot
                                    // interrupt it, so cancellation has to be observed here or
                                    // a cancelled download runs to completion — paying for the
                                    // whole file after the user asked it to stop.
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    written += read
                                    if (written > MAX_APK_BYTES) return@runCatching false
                                    output.write(buffer, 0, read)
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

            /**
             * Sixty-four megabytes. The APK is under two; a response that claims or delivers
             * more than this is not an update, and filling internal storage is not something a
             * server gets to ask for.
             */
            const val MAX_APK_BYTES = 64L * 1024 * 1024

            /** Generous: an APK is a couple of megabytes and mobile data is slow. */
            const val TIMEOUT_MILLIS = 30_000

            const val BUFFER_BYTES = 64 * 1024

            const val USER_AGENT = "Budge-Android"
        }
    }

/**
 * Whether [host] is one of GitHub's, including the subdomain its asset CDN uses.
 *
 * A suffix check rather than an exact match, and it requires the dot before the domain so
 * that a host like `notgithub.com` cannot pass.
 */
private fun isGithubHost(host: String?): Boolean =
    host != null && GITHUB_HOST_SUFFIXES.any { suffix -> host == suffix || host.endsWith(".$suffix") }

private val GITHUB_HOST_SUFFIXES = listOf("github.com", "githubusercontent.com")
