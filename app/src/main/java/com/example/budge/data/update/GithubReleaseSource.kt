package com.example.budge.data.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the published releases of a repository. */
interface ReleaseSource {
    suspend fun releases(repository: String): ReleaseFetch
}

/** Either the releases that were read, or why they could not be. */
sealed interface ReleaseFetch {
    data class Success(
        val releases: List<RemoteRelease>,
    ) : ReleaseFetch

    data class Failure(
        val failure: UpdateFailure,
    ) : ReleaseFetch
}

/**
 * Reads releases from the GitHub REST API over `HttpURLConnection`.
 *
 * One read-only GET is not worth an HTTP client dependency, and keeping it in a single
 * small class is easier to audit. The request carries no ledger data and no identifier,
 * which is what the licence screen promises.
 */
@Singleton
class GithubReleaseSource
    @Inject
    constructor() : ReleaseSource {
        override suspend fun releases(repository: String): ReleaseFetch =
            withContext(Dispatchers.IO) {
                val connection =
                    try {
                        (URL(UpdateConfig.releasesUrl(repository)).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = TIMEOUT_MILLIS
                            readTimeout = TIMEOUT_MILLIS
                            setRequestProperty("Accept", "application/vnd.github+json")
                            setRequestProperty("User-Agent", USER_AGENT)
                        }
                    } catch (_: Exception) {
                        return@withContext ReleaseFetch.Failure(UpdateFailure.NETWORK)
                    }

                try {
                    val code = connection.responseCode
                    val failure = failureForStatus(code)
                    if (failure != null) return@withContext ReleaseFetch.Failure(failure)
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    ReleaseFetch.Success(parseGithubReleases(body))
                } catch (_: Exception) {
                    ReleaseFetch.Failure(UpdateFailure.NETWORK)
                } finally {
                    connection.disconnect()
                }
            }

        private companion object {
            const val TIMEOUT_MILLIS = 10_000
            // GitHub asks every API client to identify itself.
            const val USER_AGENT = "Budge-Android"
        }
    }

/**
 * The failure a response code stands for, or null when the response is usable.
 *
 * 404 is separated from the rest because it has a fixable cause the user can act on:
 * either the repository does not exist, or it is still private.
 */
internal fun failureForStatus(code: Int): UpdateFailure? =
    when {
        code in 200..299 -> null
        code == 404 -> UpdateFailure.NOT_FOUND
        else -> UpdateFailure.HTTP
    }

/**
 * The subset of a GitHub release the app reads.
 *
 * `@SerializedName` for the same reason as the backup document: R8 renames these fields
 * in release builds and Gson maps JSON by field name.
 */
private data class GithubReleaseDto(
    @SerializedName("tag_name") val tagName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("draft") val draft: Boolean = false,
    @SerializedName("prerelease") val prerelease: Boolean = false,
)

private val gson = Gson()

/**
 * Parses a `GET /repos/{owner}/{repo}/releases` response.
 *
 * Drafts are skipped, since they are not published, and so is a release whose tag does
 * not parse as a version; guessing a version number is worse than ignoring the release.
 * A release GitHub marks as a pre-release whose tag has no suffix counts as a beta: it
 * is certainly not stable.
 *
 * Throws if the body is not JSON at all. The caller maps that to [UpdateFailure.PARSE].
 */
internal fun parseGithubReleases(json: String): List<RemoteRelease> {
    val dtos = gson.fromJson(json, Array<GithubReleaseDto>::class.java) ?: return emptyList()
    return dtos.mapNotNull { dto ->
        if (dto.draft) return@mapNotNull null
        val parsed = AppVersion.parse(dto.tagName) ?: return@mapNotNull null
        val version =
            if (dto.prerelease && parsed.channel == ReleaseChannel.STABLE) {
                parsed.copy(channel = ReleaseChannel.BETA)
            } else {
                parsed
            }
        val pageUrl = dto.htmlUrl ?: return@mapNotNull null
        RemoteRelease(
            version = version,
            title = dto.name ?: dto.tagName,
            notes = dto.body?.takeIf { it.isNotBlank() },
            pageUrl = pageUrl,
        )
    }
}
