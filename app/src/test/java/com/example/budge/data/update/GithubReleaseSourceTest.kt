package com.example.budge.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Tests for reading the GitHub releases payload.
 *
 * The mapping decides which releases the app will ever consider, so the interesting
 * cases are the ones that must be *skipped* — a draft is not published, and a tag that is
 * not a version must be ignored rather than guessed at.
 */
class GithubReleaseSourceTest {
    @Test
    fun `reads tags, notes and page urls`() {
        val json =
            """
            [
              {
                "tag_name": "v1.1.0",
                "name": "1.1.0",
                "body": "What changed",
                "html_url": "https://example.invalid/releases/v1.1.0",
                "draft": false,
                "prerelease": false
              },
              {
                "tag_name": "v1.2.0-beta.1",
                "name": "1.2.0 beta 1",
                "body": "Try it",
                "html_url": "https://example.invalid/releases/v1.2.0-beta.1",
                "draft": false,
                "prerelease": true
              }
            ]
            """.trimIndent()

        val releases = parseGithubReleases(json)

        assertEquals(listOf("1.1.0", "1.2.0-beta.1"), releases.map { it.version.toString() })
        assertEquals("What changed", releases[0].notes)
        assertEquals("Try it", releases[1].notes)
        assertEquals("https://example.invalid/releases/v1.1.0", releases[0].pageUrl)
    }

    @Test
    fun `skips drafts, unparseable tags and entries without a page`() {
        val json =
            """
            [
              { "tag_name": "v9.9.9", "html_url": "https://example.invalid/draft", "draft": true },
              { "tag_name": "nightly", "html_url": "https://example.invalid/nightly" },
              { "tag_name": "v1.0.0" },
              { "tag_name": "v1.0.1", "html_url": "https://example.invalid/releases/v1.0.1" }
            ]
            """.trimIndent()

        val releases = parseGithubReleases(json)

        assertEquals(listOf("1.0.1"), releases.map { it.version.toString() })
    }

    @Test
    fun `treats a prerelease flag without a suffix as a beta, never as stable`() {
        // GitHub's `prerelease` flag is the only signal here, and "not stable" is the
        // safe reading: it keeps such a release away from stable builds.
        val json =
            """
            [
              { "tag_name": "v1.1.0", "html_url": "https://example.invalid/1", "prerelease": true }
            ]
            """.trimIndent()

        val releases = parseGithubReleases(json)

        assertEquals(ReleaseChannel.BETA, releases.single().version.channel)
    }

    @Test
    fun `an empty release list parses to nothing`() {
        assertEquals(emptyList<RemoteRelease>(), parseGithubReleases("[]"))
    }

    @Test
    fun `a blank body is reported as no notes rather than empty text`() {
        val json = """[{ "tag_name": "v1.0.0", "body": "   ", "html_url": "https://example.invalid/1" }]"""

        assertTrue(parseGithubReleases(json).single().notes == null)
    }

    @Test
    fun `maps response codes to the failure they mean`() {
        assertNull("200 is a usable response", failureForStatus(200))
        assertNull(failureForStatus(204))
        // 404 is worth separating: it is either "no such repository" or "still private",
        // and both are things the user can fix, unlike a wait.
        assertEquals(UpdateFailure.NOT_FOUND, failureForStatus(404))
        // 429 says "too many requests" by definition. A 403 does not: GitHub sends it
        // both for the anonymous quota and for a repository it will not serve, so only
        // the response's own header turns it into a rate limit.
        assertEquals(UpdateFailure.RATE_LIMITED, failureForStatus(429))
        assertEquals(UpdateFailure.HTTP, failureForStatus(403))
        assertEquals(UpdateFailure.RATE_LIMITED, failureForStatus(403, rateLimitExhausted = true))
        assertEquals(UpdateFailure.HTTP, failureForStatus(500))
        assertEquals(UpdateFailure.HTTP, failureForStatus(302))
    }

    @Test
    fun `only the source's own header makes a 403 a rate limit`() {
        // The header GitHub sends on the 403 that means the hourly allowance is gone.
        assertTrue(rateLimitExhausted(403, "0"))
        assertFalse(rateLimitExhausted(403, "59"))
        assertFalse("a missing header is not a rate limit", rateLimitExhausted(403, null))
        assertFalse("404 is never a rate limit", rateLimitExhausted(404, "0"))
    }

    @Test
    fun `tells a timeout apart from never reaching the host`() {
        assertEquals(UpdateFailure.TIMEOUT, failureFor(SocketTimeoutException("read timed out")))
        assertEquals(UpdateFailure.NETWORK, failureFor(UnknownHostException("api.github.com")))
        assertEquals(UpdateFailure.NETWORK, failureFor(ConnectException("connection refused")))
    }

    @Test
    fun `the repository slug is configured for release`() {
        // A configured source is what arms the check; while it is the placeholder the
        // app makes no network call at all.
        assertTrue(UpdateConfig.isConfigured)
        assertTrue(UpdateConfig.GITHUB_REPOSITORY.matches(Regex("[^/]+/[^/]+")))
        assertTrue(UpdateConfig.releasesUrl("owner/name").endsWith("/repos/owner/name/releases?per_page=30"))
    }
}
