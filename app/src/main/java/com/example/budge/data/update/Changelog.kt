package com.example.budge.data.update

import androidx.annotation.StringRes
import com.example.budge.R

/**
 * A published release, as far as the app needs to know about it.
 *
 * [notesRes] is a **one-line summary**, not the full release notes: the dialog exists to
 * say what changed at a glance. It is also the one string in the app that is not
 * translated into all nine languages — it is written in English and Chinese only, and
 * every other locale falls back to the English text, which is what Android does for a
 * string a locale does not define. The full detail per release lives in `CHANGELOG.md`.
 */
data class ReleaseNotes(
    val version: AppVersion,
    @StringRes val notesRes: Int,
)

/**
 * The release history this build carries.
 *
 * Compiled into the app rather than fetched, so it works offline and cannot be changed
 * under the user by a server. Publishing a version means adding an entry here plus its
 * `release_notes_*` summary in `values/` and `values-zh/`. A unit test fails if the
 * entry and the installed version disagree.
 */
private val RELEASES: List<ReleaseNotes> =
    listOf(
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.BETA, 6), R.string.release_notes_0_1_0_beta_6),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.BETA, 5), R.string.release_notes_0_1_0_beta_5),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.BETA, 4), R.string.release_notes_0_1_0_beta_4),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.BETA, 3), R.string.release_notes_0_1_0_beta_3),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.BETA, 2), R.string.release_notes_0_1_0_beta_2),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.BETA, 1), R.string.release_notes_0_1_0_beta_1),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 9), R.string.release_notes_0_1_0_alpha_9),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 8), R.string.release_notes_0_1_0_alpha_8),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 7), R.string.release_notes_0_1_0_alpha_7),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 6), R.string.release_notes_0_1_0_alpha_6),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 5), R.string.release_notes_0_1_0_alpha_5),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 4), R.string.release_notes_0_1_0_alpha_4),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 3), R.string.release_notes_0_1_0_alpha_3),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 2), R.string.release_notes_0_1_0_alpha_2),
        ReleaseNotes(AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 1), R.string.release_notes_0_1_0_alpha_1),
    )

/**
 * The releases a build on [channel] may see, newest first.
 *
 * Stable sees stable, beta sees beta and stable, alpha sees everything. Filtering the
 * history the same way the update check filters candidates means the two can never
 * disagree.
 */
fun releaseHistoryFor(channel: ReleaseChannel): List<ReleaseNotes> = filterHistory(RELEASES, channel)

/**
 * The alpha releases a build on [channel] may look at when it asks to, newest first.
 *
 * Betas only. An alpha build already has them in its history, and a stable build is not on
 * the test programme at all — the button that offers this must not exist there, so the rule
 * lives here rather than at the call site.
 */
fun alphasFor(channel: ReleaseChannel): List<ReleaseNotes> =
    if (channel == ReleaseChannel.BETA) {
        filterHistory(RELEASES, ReleaseChannel.ALPHA).filter { it.version.channel == ReleaseChannel.ALPHA }
    } else {
        emptyList()
    }

/** The filtering rule itself, separated from the shipped list so it can be tested. */
internal fun filterHistory(
    releases: List<ReleaseNotes>,
    channel: ReleaseChannel,
): List<ReleaseNotes> =
    releases
        .filter { channel.accepts(it.version.channel) }
        .sortedByDescending { it.version }
