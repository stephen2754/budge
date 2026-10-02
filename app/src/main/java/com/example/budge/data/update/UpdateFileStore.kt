package com.example.budge.data.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Where a downloaded update lives between arriving and being installed.
 *
 * Two names per version, deliberately: a download writes a `.part` file that is never
 * installed, and only a file that passed its hash check is renamed to the name the installer
 * is given. Two writers can therefore never share one file — which is what made a retry after
 * a cancelled download produce a mixture that failed its own checksum check and reported a
 * tampered download that was not tampered with.
 *
 * An interface so the update flow can be tested on the JVM without a device, and so the one
 * directory that holds an executable file is described in one short class.
 */
interface UpdateFileStore {
    /** Scratch file for [version]. A `.part` is never handed to the installer. */
    fun scratch(version: AppVersion): File

    /** Where a verified download of [version] waits for the installer. */
    fun verified(version: AppVersion): File

    /**
     * Removes scratch files and any download that is not newer than [running].
     *
     * The file name carries the version, which is what makes this safe to run at startup:
     * while the installer is reading a download, the app it belongs to is still the *old*
     * build, so that file is newer than the running version and is kept. Once the install has
     * happened the app runs as that version, and the next start removes the file it no longer
     * needs. A `.part` is removed unconditionally — it is not resumable and not installable.
     */
    fun clearStale(running: AppVersion)
}

/**
 * The real store: app-private internal storage, so nothing needs a permission and nothing
 * else on the device can read what is waiting there.
 */
class InternalUpdateFileStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UpdateFileStore {
        override fun scratch(version: AppVersion): File = File(directory(), "$PREFIX$version$PART_SUFFIX")

        override fun verified(version: AppVersion): File = File(directory(), "$PREFIX$version$READY_SUFFIX")

        override fun clearStale(running: AppVersion) {
            val files = runCatching { directory().listFiles() }.getOrNull() ?: return
            files.forEach { file ->
                val name = file.name
                val version =
                    name
                        .removePrefix(PREFIX)
                        .removeSuffix(PART_SUFFIX)
                        .removeSuffix(READY_SUFFIX)
                        .let(AppVersion::parse)
                val stale = name.endsWith(PART_SUFFIX) || version == null || version <= running
                if (stale) runCatching { file.delete() }
            }
        }

        private fun directory(): File = File(context.filesDir, DIRECTORY_NAME)

        private companion object {
            const val DIRECTORY_NAME = "updates"

            const val PREFIX = "budge-"

            const val PART_SUFFIX = ".apk.part"

            const val READY_SUFFIX = ".apk"
        }
    }
