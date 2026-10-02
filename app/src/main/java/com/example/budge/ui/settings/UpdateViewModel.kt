package com.example.budge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budge.BuildConfig
import com.example.budge.data.update.AppVersion
import com.example.budge.data.update.ReleaseChannel
import com.example.budge.data.update.ReleaseFetch
import com.example.budge.data.update.ReleaseNotes
import com.example.budge.data.update.ReleaseSource
import com.example.budge.data.update.RemoteRelease
import com.example.budge.data.update.selectBetaOffer
import com.example.budge.data.update.UpdateConfig
import com.example.budge.data.update.UpdateStatus
import com.example.budge.data.update.releaseHistoryFor
import com.example.budge.data.update.alphasFor
import com.example.budge.data.update.selectUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The About section: which channel this build is on, what it may show, and the update
 * check.
 *
 * The channel is read from the installed `versionName`, so a build named
 * `1.1.0-beta.2` sees and installs beta and stable releases, while `1.0.0` sees stable
 * releases only. Nothing here is configurable at runtime — a build cannot promote
 * itself to a channel it was not released on.
 */
@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        private val releaseSource: ReleaseSource,
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

        /**
         * Asks the release source whether a newer release exists for this channel.
         *
         * With no source configured nothing is requested and the state says so, rather
         * than reporting a version that was never compared.
         */
        fun checkForUpdate() {
            if (_status.value == UpdateStatus.Checking) return
            if (!UpdateConfig.isConfigured) {
                _status.value = UpdateStatus.NotConfigured
                return
            }
            viewModelScope.launch {
                _status.value = UpdateStatus.Checking
                _betaOffer.value = null
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
                                if (newer == null) {
                                    UpdateStatus.UpToDate(currentVersion)
                                } else {
                                    UpdateStatus.Available(currentVersion, newer)
                                }
                            }
                        }
                    }
            }
        }
    }
