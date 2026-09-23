package com.example.budge.data.update

/**
 * Where published releases are read from.
 *
 * Nothing else has to change to point the app somewhere else: fill in `owner/name` (and
 * keep a non-GitHub reader behind the same [ReleaseSource] interface if one is ever
 * added). While the value is still [PLACEHOLDER] no request is made at all, and the
 * update row says so rather than reporting a version it never compared.
 */
object UpdateConfig {
    private const val PLACEHOLDER = "OWNER/REPO"

    /**
     * `owner/name` of the repository releases are published from.
     *
     * The placeholder stays as a named value because [isConfigured] compares against it:
     * a checkout that has not been pointed at a repository reports "not configured"
     * instead of firing a request that cannot succeed.
     */
    const val GITHUB_REPOSITORY = "stephen2754/budge"

    /** True once a real `owner/name` has been filled in. */
    val isConfigured: Boolean
        get() = GITHUB_REPOSITORY != PLACEHOLDER && REPOSITORY_PATTERN.matches(GITHUB_REPOSITORY)

    private val REPOSITORY_PATTERN = Regex("""^[^/\s]+/[^/\s]+$""")

    /** The GitHub releases endpoint for [repository]. */
    fun releasesUrl(repository: String = GITHUB_REPOSITORY): String =
        "https://api.github.com/repos/$repository/releases?per_page=30"

    /**
     * The release feed for [repository], served by `github.com` itself.
     *
     * The API is metered — 60 anonymous requests an hour for everyone sharing an address
     * — and it is the host most often interfered with on the networks this app is
     * installed on. The feed is not: it is the same host that serves the release page a
     * browser opens, with no quota. It is read as a fallback, not instead of the API,
     * because it cannot say that a release was flagged as a pre-release.
     */
    fun releasesFeedUrl(repository: String = GITHUB_REPOSITORY): String = "https://github.com/$repository/releases.atom"
}

/** A release found upstream, reduced to what the app needs. */
data class RemoteRelease(
    val version: AppVersion,
    val title: String?,
    val notes: String?,
    val pageUrl: String,
)

/**
 * Why a check could not be completed. Mapped to a localized sentence by the UI.
 *
 * These stay apart because they call for different things: nothing to retry when the
 * device is offline, a retry when the source is rate limiting, a corrected address after
 * a 404, and a bug report when the answer cannot be read. Collapsing them all into
 * "cannot connect" hides which one happened, which is what made the first report of a
 * failed check impossible to act on.
 */
enum class UpdateFailure {
    /** The host could not be reached at all. */
    NETWORK,

    /** The host was reached but did not answer in time. */
    TIMEOUT,

    /**
     * The source refused the request: too many anonymous requests from this address, or
     * from everyone sharing it. Waiting is the whole fix.
     */
    RATE_LIMITED,

    /** The API answered 404: no such repository, or it is not public. */
    NOT_FOUND,

    /** The API answered with something other than 2xx. */
    HTTP,

    /** The response was not the JSON or XML this app knows how to read. */
    PARSE,
}

/** The outcome of the most recent check. */
sealed interface UpdateStatus {
    /** Nothing has been checked in this process yet. */
    data object Idle : UpdateStatus

    data object Checking : UpdateStatus

    /** No source is configured, so nothing was requested and nothing is known. */
    data object NotConfigured : UpdateStatus

    /**
     * The source answered, and has published nothing at all.
     *
     * Kept apart from [UpToDate]: an empty repository says nothing about whether this
     * build is current, and calling that "up to date" would be a guess presented as a
     * fact.
     */
    data object NoReleases : UpdateStatus

    /** The installed build is the newest release its channel may install. */
    data class UpToDate(
        val current: AppVersion,
    ) : UpdateStatus

    /** A newer release the installed channel is allowed to install. */
    data class Available(
        val current: AppVersion,
        val release: RemoteRelease,
    ) : UpdateStatus

    /**
     * The check failed, so whether this build is current is unknown.
     *
     * There is no state meaning "could not check, therefore up to date", and there
     * should never be one.
     *
     * [statusCode] is set when a status code is what went wrong, so the row can name it
     * rather than saying only that something did.
     */
    data class Unreachable(
        val failure: UpdateFailure,
        val statusCode: Int? = null,
    ) : UpdateStatus
}

/**
 * The newest release [current] may install, or null if there is none.
 *
 * Only releases the installed channel accepts are considered, so a stable build is never
 * offered a beta and a beta build is never offered an alpha. Everything else falls out
 * of the version comparison, including a beta build being moved onto the stable release
 * it leads up to.
 */
fun selectUpdate(
    current: AppVersion,
    releases: List<RemoteRelease>,
): RemoteRelease? =
    releases
        .filter { current.channel.accepts(it.version.channel) && it.version > current }
        .maxByOrNull { it.version }
