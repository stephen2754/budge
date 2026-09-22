package com.example.budge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budge.BuildConfig
import com.example.budge.data.update.AppVersion
import com.example.budge.data.update.ReleaseChannel
import com.example.budge.data.update.ReleaseFetch
import com.example.budge.data.update.ReleaseNotes
import com.example.budge.data.update.ReleaseSource
import com.example.budge.data.update.UpdateConfig
import com.example.budge.data.update.UpdateStatus
import com.example.budge.data.update.releaseHistoryFor
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
                _status.value =
                    when (val fetched = releaseSource.releases(UpdateConfig.GITHUB_REPOSITORY)) {
                        is ReleaseFetch.Failure -> UpdateStatus.Unreachable(fetched.failure)
                        is ReleaseFetch.Success -> when {
                            // Nothing published at all is not the same as "nothing newer".
                            fetched.releases.isEmpty() -> UpdateStatus.NoReleases
                            else -> {
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
