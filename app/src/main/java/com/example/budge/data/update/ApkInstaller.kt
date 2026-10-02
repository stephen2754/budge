package com.example.budge.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * What this app is allowed to do about installing a package, and how it asks.
 *
 * An interface so the update flow can be tested without a device: everything here is a
 * platform call, and everything that decides *whether* to call it lives in the view model.
 */
interface ApkInstaller {
    /** False while this app has no permission to install packages — the user has to grant it. */
    fun canInstallPackages(): Boolean

    /** Hands the downloaded file to the system installer, which asks the user to confirm. */
    fun install(file: File): Boolean

    /** Opens the per-app "install unknown apps" screen so that permission can be granted. */
    fun requestInstallPermission()

    /** Deletes any downloaded update files, which are of no use once installed or refused. */
    fun clearDownloadedUpdates()
}

/**
 * The system's own installer, and the permission it needs.
 *
 * Installing an app is the one thing in this app the platform insists a *person* approves:
 * since Android 8 the app must hold `REQUEST_INSTALL_PACKAGES` and the user has to allow this
 * app to install unknown apps, and the installer then shows its own confirmation. Nothing
 * here bypasses that, and the signature check the installer performs on an upgrade is what
 * stops a file signed by anyone else from replacing this app at all.
 */
class AndroidApkInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ApkInstaller {
        override fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

        override fun install(file: File): Boolean {
            if (!file.exists()) return false
            val uri =
                runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
                }.getOrNull() ?: return false
            val intent =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { context.startActivity(intent) }.isSuccess
        }

        override fun requestInstallPermission() {
            val intent =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        override fun clearDownloadedUpdates() {
            runCatching { updateDirectory(context).deleteRecursively() }
        }

        private companion object {
            const val AUTHORITY_SUFFIX = ".updates"

            const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        }
    }

/**
 * Where a downloaded update is kept.
 *
 * App-private internal storage, so no permission is needed to write it and nothing else on
 * the device can read it; the installer is handed a content URI through the provider in the
 * manifest rather than a file path.
 */
fun updateDirectory(context: Context): File = File(context.filesDir, "updates")
