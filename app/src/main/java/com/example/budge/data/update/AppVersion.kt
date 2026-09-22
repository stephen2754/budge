package com.example.budge.data.update

/**
 * A release stream, and the rule for what a build on it may see and install.
 *
 * | installed | offered |
 * | --- | --- |
 * | `STABLE` | stable releases |
 * | `BETA` | beta and stable, never alpha |
 * | `ALPHA` | everything, alpha included |
 *
 * The release history uses the same rule, so it cannot show a version the update check
 * would refuse.
 */
enum class ReleaseChannel {
    ALPHA,
    BETA,
    STABLE,
    ;

    /** True when a build on this channel may be offered [candidate]. */
    fun accepts(candidate: ReleaseChannel): Boolean =
        when (this) {
            ALPHA -> true
            BETA -> candidate != ALPHA
            STABLE -> candidate == STABLE
        }

    /** Ordering used when comparing two versions of the same number: alpha < beta < stable. */
    internal val rank: Int
        get() =
            when (this) {
                ALPHA -> 0
                BETA -> 1
                STABLE -> 2
            }
}

/**
 * A version: `major.minor.patch`, with an optional `-alpha.N` or `-beta.N` suffix.
 *
 * Ordering is semantic: `1.0.0-alpha.2 < 1.0.0-beta.1 < 1.0.0 < 1.0.1`. A pre-release
 * therefore sorts below the release it leads up to, which is what lets a beta user be
 * moved onto the stable build without a special case.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val channel: ReleaseChannel = ReleaseChannel.STABLE,
    val channelNumber: Int = 0,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val numbers = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (numbers != 0) return numbers
        val byChannel = channel.rank.compareTo(other.channel.rank)
        if (byChannel != 0) return byChannel
        return channelNumber.compareTo(other.channelNumber)
    }

    /** The bare version number, without the channel suffix: `1.0.0`. */
    val number: String
        get() = "$major.$minor.$patch"

    /** The full version string: `1.0.0-beta.2`, or `1.0.0` on the stable channel. */
    override fun toString(): String =
        when (channel) {
            ReleaseChannel.STABLE -> number
            ReleaseChannel.ALPHA -> "$number-alpha.$channelNumber"
            ReleaseChannel.BETA -> "$number-beta.$channelNumber"
        }

    companion object {
        // Tolerates a leading "v" and a missing channel number ("1.0.0-beta" == beta.0).
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.?(\d+)?)?$""", RegexOption.IGNORE_CASE)

        /**
         * Parses `v1.2.3`, `1.2.3`, `1.2.3-beta.2` or `1.2.3-alpha`. Returns null for
         * anything else.
         *
         * Both sides of every comparison come through here: the installed `versionName`
         * and each release tag. A tag that does not parse is skipped, never guessed at.
         */
        fun parse(raw: String?): AppVersion? {
            val match = PATTERN.matchEntire(raw?.trim() ?: return null) ?: return null
            val (major, minor, patch, suffix, number) = match.destructured
            val channel =
                when (suffix.lowercase()) {
                    "alpha" -> ReleaseChannel.ALPHA
                    "beta" -> ReleaseChannel.BETA
                    else -> ReleaseChannel.STABLE
                }
            return AppVersion(
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toInt(),
                channel = channel,
                channelNumber = number.toIntOrNull() ?: 0,
            )
        }

        /**
         * The channel an installed build belongs to, read from its version name.
         * `1.1.0-beta.2` is a beta build; a name with no suffix is stable.
         *
         * An unparseable name counts as stable. That is the cautious direction, since a
         * stable build is offered the fewest updates.
         */
        fun channelOf(versionName: String?): ReleaseChannel = parse(versionName)?.channel ?: ReleaseChannel.STABLE
    }
}
