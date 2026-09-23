package com.example.budge.data.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the two-step read: the API first, the release feed when the API cannot be
 * read.
 *
 * The API is metered at sixty anonymous requests an hour for everyone sharing an address,
 * and the feed is not metered at all, so the case that matters most here is the one the
 * app was first reported to fail on: a refused API on a network where the release page
 * opens perfectly well in a browser.
 */
class ReleaseFetchFallbackTest {
    private val apiUrl = UpdateConfig.releasesUrl()
    private val feedUrl = UpdateConfig.releasesFeedUrl()

    @Test
    fun `the API is read first and the feed is left alone when it answers`() =
        runBlocking {
            val http = FakeHttp(mapOf(apiUrl to HttpResult.Answered(200, API_BODY)))

            val fetched = GithubReleaseSource(http).releases(UpdateConfig.GITHUB_REPOSITORY)

            assertEquals(listOf(apiUrl), http.requested)
            val release = (fetched as ReleaseFetch.Success).releases.single()
            assertEquals("0.1.0-alpha.1", release.version.toString())
            assertEquals("What changed", release.notes)
        }

    @Test
    fun `a rate-limited API falls back to the feed`() =
        runBlocking {
            val http =
                FakeHttp(
                    mapOf(
                        apiUrl to HttpResult.Answered(403, ""),
                        feedUrl to HttpResult.Answered(200, FEED_BODY),
                    ),
                )

            val fetched = GithubReleaseSource(http).releases(UpdateConfig.GITHUB_REPOSITORY)

            assertEquals(listOf(apiUrl, feedUrl), http.requested)
            val release = (fetched as ReleaseFetch.Success).releases.single()
            assertEquals("0.1.0-alpha.1", release.version.toString())
            assertTrue(release.pageUrl.endsWith("/releases/tag/v0.1.0-alpha.1"))
        }

    @Test
    fun `a timed-out API falls back to the feed`() =
        runBlocking {
            val http =
                FakeHttp(
                    mapOf(
                        apiUrl to HttpResult.Failed(UpdateFailure.TIMEOUT),
                        feedUrl to HttpResult.Answered(200, FEED_BODY),
                    ),
                )

            val fetched = GithubReleaseSource(http).releases(UpdateConfig.GITHUB_REPOSITORY)

            assertTrue(fetched is ReleaseFetch.Success)
        }

    @Test
    fun `an API answer that is not a release list falls back to the feed`() =
        runBlocking {
            // What a captive portal or a block page looks like: 200, and not the JSON.
            val http =
                FakeHttp(
                    mapOf(
                        apiUrl to HttpResult.Answered(200, "<html><body>Sign in to continue</body></html>"),
                        feedUrl to HttpResult.Answered(200, FEED_BODY),
                    ),
                )

            val fetched = GithubReleaseSource(http).releases(UpdateConfig.GITHUB_REPOSITORY)

            assertTrue(fetched is ReleaseFetch.Success)
        }

    @Test
    fun `when neither can be read, the API's answer is the one reported`() =
        runBlocking {
            val http =
                FakeHttp(
                    mapOf(
                        apiUrl to HttpResult.Answered(403, ""),
                        feedUrl to HttpResult.Failed(UpdateFailure.NETWORK),
                    ),
                )

            val fetched = GithubReleaseSource(http).releases(UpdateConfig.GITHUB_REPOSITORY)

            assertEquals(ReleaseFetch.Failure(UpdateFailure.RATE_LIMITED, 403), fetched)
        }

    @Test
    fun `an empty feed does not turn a failed check into 'no releases'`() =
        runBlocking {
            val http =
                FakeHttp(
                    mapOf(
                        apiUrl to HttpResult.Answered(403, ""),
                        feedUrl to HttpResult.Answered(200, EMPTY_FEED),
                    ),
                )

            val fetched = GithubReleaseSource(http).releases(UpdateConfig.GITHUB_REPOSITORY)

            assertEquals(ReleaseFetch.Failure(UpdateFailure.RATE_LIMITED, 403), fetched)
        }

    @Test
    fun `a repository that is not there is reported as missing`() =
        runBlocking {
            val http =
                FakeHttp(
                    mapOf(
                        apiUrl to HttpResult.Answered(404, ""),
                        feedUrl to HttpResult.Answered(404, ""),
                    ),
                )

            val fetched = GithubReleaseSource(http).releases(UpdateConfig.GITHUB_REPOSITORY)

            assertEquals(ReleaseFetch.Failure(UpdateFailure.NOT_FOUND, 404), fetched)
        }

    @Test
    fun `an unusable answer from both is reported as unreadable, not as no network`() =
        runBlocking {
            val http =
                FakeHttp(
                    mapOf(
                        apiUrl to HttpResult.Answered(200, "not json"),
                        feedUrl to HttpResult.Answered(200, "not xml either"),
                    ),
                )

            val fetched = GithubReleaseSource(http).releases(UpdateConfig.GITHUB_REPOSITORY)

            assertEquals(ReleaseFetch.Failure(UpdateFailure.PARSE), fetched)
        }

    /** An [HttpGet] that answers from a table and remembers what it was asked for. */
    private class FakeHttp(
        private val answers: Map<String, HttpResult>,
    ) : HttpGet {
        val requested = mutableListOf<String>()

        override fun get(url: String): HttpResult {
            requested += url
            return answers[url] ?: HttpResult.Failed(UpdateFailure.NETWORK)
        }
    }

    private companion object {
        val API_BODY =
            """
            [
              {
                "tag_name": "v0.1.0-alpha.1",
                "name": "0.1.0-alpha.1",
                "body": "What changed",
                "html_url": "https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.1",
                "draft": false,
                "prerelease": true
              }
            ]
        """.trimIndent()

        val FEED_BODY =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>tag:github.com,2008:Repository/1/v0.1.0-alpha.1</id>
                <link rel="alternate" type="text/html" href="https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.1"/>
                <title>0.1.0-alpha.1</title>
              </entry>
            </feed>
        """.trimIndent()

        val EMPTY_FEED =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"></feed>
        """.trimIndent()
    }
}
