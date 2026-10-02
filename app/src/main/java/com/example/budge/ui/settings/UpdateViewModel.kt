package com.example.budge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budge.BuildConfig
import com.example.budge.data.update.ApkDownloader
import com.example.budge.data.update.ApkInstaller
import com.example.budge.data.update.UpdateFileStore
import com.example.budge.data.update.UpdateFailure
import com.example.budge.data.update.AppVersion
import com.example.budge.data.update.ReleaseChannel
import com.example.budge.data.update.ReleaseFetch
import com.example.budge.data.update.ReleaseNotes
import com.example.budge.data.update.ReleaseSource
import com.example.budge.data.update.RemoteRelease
import com.example.budge.data.update.UpdateConfig
import com.example.budge.data.update.UpdateStatus
import com.example.budge.data.update.alphasFor
import com.example.budge.data.update.matchesSha256
import com.example.budge.data.update.releaseHistoryFor
import com.example.budge.data.update.selectBetaOffer
import com.example.budge.data.update.selectUpdate
import com.example.budge.data.update.sha256
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The About section: which channel this build is on, what it may show, and the update
 * check.
 *
 * The channel is read from the installed `versionName`, so a build named
 * `1.1.0-beta.2` sees and installs beta and stable releases, while `1.0.0` sees stable
 * releases only. Nothing here is configurable at runtime — a build cannot promote
 * itself to a channel it was not released on.
 */
/**
 * What the in-app update download is doing.
 *
 * Kept apart from [UpdateStatus], which is about what the check found. A download outlives the
 * dialog it was started from, so closing that dialog with one running must not throw the
 * progress away — and a file that has not been checked is never [Ready].
 */
sealed interface UpdateDownload {
    data object Idle : UpdateDownload

    /** [total] is 0 when the server did not say how large the file is. */
    data class Downloading(
        val bytes: Long,
        val total: Long,
    ) : UpdateDownload

    data object Verifying : UpdateDownload

    data object Ready : UpdateDownload

    data object DownloadFailed : UpdateDownload

    data object VerificationFailed : UpdateDownload

    /** The verified file exists but the platform would not open its installer. */
    data object InstallFailed : UpdateDownload
}

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        private val releaseSource: ReleaseSource,
        private val apkDownloader: ApkDownloader,
        private val apkInstaller: ApkInstaller,
        private val fileStore: UpdateFileStore,
    ) : ViewModel() {
        /** The channel of the running build. */
        val channel: ReleaseChannel = AppVersion.channelOf(BuildConfig.VERSION_NAME)

        /**
         * The running build's version. Falls back to `0.0.0` when `versionName` is not a
         * version at all, which makes every published release look newer — the safe
         * direction for an update prompt.
         */
        val currentVersion: AppVersion = AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(0, 0, 0)

        /** Release history this channel is allowed to see, newest first. */
        val history: List<ReleaseNotes> = releaseHistoryFor(channel)

        /**
         * This build's own record in that history, for the version dialog.
         *
         * The dialog says what *this* build is; the list of earlier releases is one button
         * away, so the two never have to be read as the same thing.
         */
        val currentRelease: ReleaseNotes? = history.firstOrNull { it.version == currentVersion }

        /**
         * The alpha records this build may look at on request, newest first.
         *
         * Empty for anything but a beta build (see [alphasFor]), so the button that reveals
         * them cannot appear where it has nothing to reveal or is not allowed.
         */
        val alphas: List<ReleaseNotes> = alphasFor(channel)

        /** [history] with those alphas merged in, newest first, for the button that asks. */
        val historyWithAlphas: List<ReleaseNotes> = (history + alphas).sortedByDescending { it.version }

        private val _betaOffer = MutableStateFlow<RemoteRelease?>(null)

        /**
         * The newest beta this build could join, as of the last check.
         *
         * Only a stable build is ever offered one — a beta or an alpha is already on a test
         * channel — and it is re-decided on every check rather than remembered, so the button
         * disappears as soon as the stable release catches up with the betas. A failed check
         * leaves it empty: nothing was learned on that check, and a button promising the
         * newest beta would be guessing.
         */
        val betaOffer: StateFlow<RemoteRelease?> = _betaOffer.asStateFlow()

        private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
        val status: StateFlow<UpdateStatus> = _status.asStateFlow()

        private val _download = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
        val download: StateFlow<UpdateDownload> = _download.asStateFlow()

        private var downloadJob: Job? = null

        /** The scratch file the running download is writing, if there is one. */
        private var scratchFile: File? = null

        /** The verified file, and the version it belongs to, if the last download passed. */
        private var readyFile: File? = null

        private var readyVersion: AppVersion? = null

        /**
         * Downloads the offered release and checks it against the hash GitHub published.
         *
         * The bytes go to a `.part` file and only a file that passed its check is renamed to
         * the name the installer will be given. That separation is not tidiness: writing both
         * attempts to one name meant a retry after a cancelled download could interleave with
         * the abandoned writer and produce a mixture that failed its own checksum check —
         * telling the reader a download had been tampered with when it had not.
         */
        fun downloadUpdate() {
            val release = (_status.value as? UpdateStatus.Available)?.release ?: return
            val url = release.assetUrl ?: return
            if (_download.value is UpdateDownload.Downloading || _download.value is UpdateDownload.Verifying) return

            clearReady()
            val scratch = fileStore.scratch(release.version)
            scratchFile = scratch
            _download.value = UpdateDownload.Downloading(bytes = 0L, total = 0L)
            downloadJob =
                viewModelScope.launch {
                    val complete =
                        apkDownloader.download(url, scratch) { bytes, total ->
                            // The callback runs on the download's own thread and can arrive
                            // after the reader has cancelled, so it writes progress only while
                            // this download is still the one on screen. That also stops a late
                            // callback from pulling the state back out of Verifying.
                            if (_download.value is UpdateDownload.Downloading) {
                                _download.value = UpdateDownload.Downloading(bytes = bytes, total = total)
                            }
                        }
                    scratchFile = null
                    if (!complete) {
                        scratch.delete()
                        _download.value = UpdateDownload.DownloadFailed
                        return@launch
                    }
                    _download.value = UpdateDownload.Verifying
                    val actual = withContext(Dispatchers.IO) { sha256(scratch) }
                    if (!matchesSha256(release.digest, actual)) {
                        scratch.delete()
                        _download.value = UpdateDownload.VerificationFailed
                        return@launch
                    }
                    val ready = fileStore.verified(release.version)
                    if (!scratch.renameTo(ready)) {
                        // A rename that fails means the file could not be put where the
                        // installer is allowed to read it; that is a failed download, not a
                        // verified one.
                        scratch.delete()
                        _download.value = UpdateDownload.DownloadFailed
                        return@launch
                    }
                    readyFile = ready
                    readyVersion = release.version
                    _download.value = UpdateDownload.Ready
                }
        }

        /** Abandons a download in progress and removes whatever it had written. */
        fun cancelDownload() {
            downloadJob?.cancel()
            downloadJob = null
            // The scratch file is the one a cancelled download is writing — the verified file
            // is a different name, which is what keeps this from deleting something installable.
            scratchFile?.delete()
            scratchFile = null
            clearReady()
            _download.value = UpdateDownload.Idle
        }

        private fun clearReady() {
            readyFile?.delete()
            readyFile = null
            readyVersion = null
        }

        /** Whether the platform will let this app hand a file to the installer. */
        fun canInstallPackages(): Boolean = apkInstaller.canInstallPackages()

        /**
         * Hands the verified file to the system installer, which asks the user to confirm.
         *
         * The file and the ready state are deliberately kept. Starting an installer tells this
         * app nothing about what the user did with it — they may cancel, or the platform may
         * refuse — and throwing the verified download away on "an activity started" meant a
         * cancelled install cost another whole download. It is removed when the app next
         * starts as a version that no longer needs it, which is what cleans up after a
         * successful install.
         */
        fun installDownloaded() {
            val file = readyFile ?: return
            if (!apkInstaller.install(file)) {
                _download.value = UpdateDownload.InstallFailed
            }
        }

        /** Opens the per-app "install unknown apps" screen. */
        fun requestInstallPermission() = apkInstaller.requestInstallPermission()

        fun checkForUpdate() {
            if (_status.value == UpdateStatus.Checking) return
            if (!UpdateConfig.isConfigured) {
                _status.value = UpdateStatus.NotConfigured
                return
            }
            viewModelScope.launch {
                try {
                _status.value = UpdateStatus.Checking
                _betaOffer.value = null
                // A check re-decides which release to offer. A download in flight is abandoned
                // — the offer may change under it — but a file that is already verified and
                // ready to install is kept, because throwing it away would make the reader pay
                // for it twice for no reason.
                if (_download.value !is UpdateDownload.Ready) cancelDownload()
                _status.value =
                    when (val fetched = releaseSource.releases(UpdateConfig.GITHUB_REPOSITORY)) {
                        is ReleaseFetch.Failure -> UpdateStatus.Unreachable(fetched.failure, fetched.statusCode)
                        is ReleaseFetch.Success -> when {
                            // Nothing published at all is not the same as "nothing newer".
                            fetched.releases.isEmpty() -> UpdateStatus.NoReleases
                            else -> {
                                // Null for a beta or alpha build, and for a stable build
                                // that is already ahead of every beta.
                                _betaOffer.value = selectBetaOffer(currentVersion, fetched.releases)
                                val newer = selectUpdate(currentVersion, fetched.releases)
                                // A verified file is only useful while its release is still the
                                // one being offered; otherwise it would sit in storage with no
                                // way to reach it.
                                if (_download.value is UpdateDownload.Ready && readyVersion != newer?.version) {
                                    cancelDownload()
                                }
                                if (newer == null) {
                                    UpdateStatus.UpToDate(currentVersion)
                                } else {
                                    UpdateStatus.Available(currentVersion, newer)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A check that could not be completed says so; it must never read as "up to
                    // date", and it must not take the app down on the way.
                    _status.value = UpdateStatus.Unreachable(UpdateFailure.PARSE)
                }
            }
        }
    }
