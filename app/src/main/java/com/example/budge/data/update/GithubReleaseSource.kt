package com.example.budge.data.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

/** Reads the published releases of a repository. */
interface ReleaseSource {
    suspend fun releases(repository: String): ReleaseFetch
}

/** Either the releases that were read, or why they could not be. */
sealed interface ReleaseFetch {
    data class Success(
        val releases: List<RemoteRelease>,
    ) : ReleaseFetch

    /** [statusCode] is set when a status code is what went wrong. */
    data class Failure(
        val failure: UpdateFailure,
        val statusCode: Int? = null,
    ) : ReleaseFetch
}

/** One HTTP GET, behind an interface so the two-step read below can be tested offline. */
fun interface HttpGet {
    fun get(url: String): HttpResult
}

/** What one GET produced: an answer, or the reason there was none. */
sealed interface HttpResult {
    data class Answered(
        val code: Int,
        val body: String,
    ) : HttpResult

    data class Failed(
        val failure: UpdateFailure,
    ) : HttpResult
}

/**
 * [HttpGet] over `HttpURLConnection`.
 *
 * One read-only GET is not worth an HTTP client dependency, and keeping it in a single
 * small class is easier to audit. The request carries no ledger data and no identifier,
 * which is what the licence screen promises.
 */
@Singleton
class UrlConnectionGet
    @Inject
    constructor() : HttpGet {
        override fun get(url: String): HttpResult {
            val connection =
                try {
                    (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = TIMEOUT_MILLIS
                        readTimeout = TIMEOUT_MILLIS
                        setRequestProperty("Accept", ACCEPT)
                        setRequestProperty("User-Agent", USER_AGENT)
                    }
                } catch (e: Exception) {
                    return HttpResult.Failed(failureFor(e))
                }

            return try {
                val code = connection.responseCode
                val body = if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
                HttpResult.Answered(code, body)
            } catch (e: Exception) {
                HttpResult.Failed(failureFor(e))
            } finally {
                connection.disconnect()
            }
        }

        private companion object {
            /**
             * Long enough for a phone on a slow international route. The first version of
             * this used ten seconds, which a handshake plus a response on mobile data can
             * exceed, and a timeout was then reported as if the device were offline.
             */
            const val TIMEOUT_MILLIS = 20_000

            const val ACCEPT = "application/vnd.github+json, application/atom+xml"

            // GitHub asks every API client to identify itself.
            const val USER_AGENT = "Budge-Android"
        }
    }

/**
 * The failure an exception stands for.
 *
 * A timeout is kept apart from the rest: it means the host was reached and did not
 * answer, which is a different complaint from a device with no route to it at all.
 */
internal fun failureFor(e: Exception): UpdateFailure =
    if (e is SocketTimeoutException) UpdateFailure.TIMEOUT else UpdateFailure.NETWORK

/**
 * The failure a response code stands for, or null when the response is usable.
 *
 * 404 is separated because it has a fixable cause the user can act on — the repository
 * does not exist, or it is still private — and 403/429 because GitHub uses them for the
 * anonymous rate limit, which the user can only wait out.
 */
internal fun failureForStatus(code: Int): UpdateFailure? =
    when {
        code in 200..299 -> null
        code == 404 -> UpdateFailure.NOT_FOUND
        code == 403 || code == 429 -> UpdateFailure.RATE_LIMITED
        else -> UpdateFailure.HTTP
    }

/**
 * Reads releases, asking the API first and the release feed if the API cannot be read.
 *
 * The API is the richer source: it says whether a release was flagged as a pre-release
 * and carries the notes as Markdown. It is also metered — sixty anonymous requests an
 * hour for everyone behind one address — and it is the endpoint most often interfered
 * with on the networks this app is installed on. Neither fact is the user's problem to
 * diagnose, so a failed API read falls back to [UpdateConfig.releasesFeedUrl], which is
 * served by the same host as the release page in a browser and carries no quota. When
 * the feed has nothing either, the API's own failure is what gets reported: it is the
 * more specific answer of the two.
 */
@Singleton
class GithubReleaseSource
    @Inject
    constructor(
        private val http: HttpGet,
    ) : ReleaseSource {
        override suspend fun releases(repository: String): ReleaseFetch =
            withContext(Dispatchers.IO) {
                val apiFailure =
                    when (val api = read(UpdateConfig.releasesUrl(repository), ::parseGithubReleases)) {
                        is ReleaseFetch.Success -> return@withContext api
                        is ReleaseFetch.Failure -> api
                    }

                when (val feed = read(UpdateConfig.releasesFeedUrl(repository), ::parseGithubFeed)) {
                    // An empty feed after a failed API read tells us nothing: report why
                    // the API read failed rather than that nothing was ever published.
                    is ReleaseFetch.Success -> if (feed.releases.isEmpty()) apiFailure else feed
                    is ReleaseFetch.Failure -> apiFailure
                }
            }

        private fun read(
            url: String,
            parse: (String) -> List<RemoteRelease>,
        ): ReleaseFetch =
            when (val result = http.get(url)) {
                is HttpResult.Failed -> ReleaseFetch.Failure(result.failure)
                is HttpResult.Answered -> {
                    val failure = failureForStatus(result.code)
                    when {
                        failure != null -> ReleaseFetch.Failure(failure, result.code)
                        else ->
                            try {
                                ReleaseFetch.Success(parse(result.body))
                            } catch (_: Exception) {
                                // The answer arrived but was not what this app reads:
                                // a captive portal, a block page, a changed format.
                                ReleaseFetch.Failure(UpdateFailure.PARSE)
                            }
                    }
                }
            }
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

/** Where a release page lives, and therefore where the tag can be read from a feed link. */
private const val RELEASE_TAG_PATH = "/releases/tag/"

/**
 * Parses a `GET /repos/{owner}/{repo}/releases` response.
 *
 * Drafts are skipped, since they are not published, and so is a release whose tag does
 * not parse as a version; guessing a version number is worse than ignoring the release.
 * A release GitHub marks as a pre-release whose tag has no suffix counts as a beta: it
 * is certainly not stable.
 *
 * Throws if the body is not JSON at all, which the caller maps to
 * [UpdateFailure.PARSE].
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

/**
 * Parses a `releases.atom` feed.
 *
 * The feed lists the same releases the API does and leaves drafts out, but it names the
 * version only inside each entry's link, and it cannot say that a release was flagged as
 * a pre-release. The tag suffix is therefore the only channel signal it offers — which
 * is the signal this app treats as authoritative anyway. Notes are left out as well: the
 * feed carries them as rendered HTML, and the dialog they would appear in is written for
 * the Markdown the API returns.
 *
 * Throws if the body is not this XML, which the caller maps to [UpdateFailure.PARSE].
 */
internal fun parseGithubFeed(xml: String): List<RemoteRelease> {
    val builder =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // The body is remote input. Refuse a DOCTYPE where the parser allows it, keep
            // entity references unexpanded, and never resolve one, whatever the parser
            // supports: a feed must not make this app read a file or open a socket.
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }

    val entries = builder.parse(InputSource(StringReader(xml))).getElementsByTagName("entry")

    return (0 until entries.length).mapNotNull { index ->
        val entry = entries.item(index) as? Element ?: return@mapNotNull null
        var pageUrl: String? = null
        var title: String? = null

        val children = entry.childNodes
        for (child in 0 until children.length) {
            val node = children.item(child)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            when (node.nodeName) {
                "title" -> title = node.textContent
                "link" ->
                    node.attributes
                        ?.getNamedItem("href")
                        ?.nodeValue
                        ?.takeIf { it.contains(RELEASE_TAG_PATH) }
                        ?.let { pageUrl = it }
            }
        }

        val url = pageUrl ?: return@mapNotNull null
        val tag = url.substringAfterLast(RELEASE_TAG_PATH)
        val version = AppVersion.parse(tag) ?: return@mapNotNull null
        RemoteRelease(version = version, title = title ?: tag, notes = null, pageUrl = url)
    }
}
