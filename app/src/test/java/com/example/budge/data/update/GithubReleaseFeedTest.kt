package com.example.budge.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for reading the `releases.atom` feed, the fallback when the API cannot be read.
 *
 * The feed is the one place the app reads XML from the network, so besides the happy path
 * these cover what it must *refuse*: a link that is not a release, a tag that is not a
 * version, and a document that tries to pull in something from outside itself.
 */
class GithubReleaseFeedTest {
    @Test
    fun `reads the published release out of the real feed`() {
        val releases = parseGithubFeed(fixture())

        val release = releases.single()
        assertEquals("0.1.0-alpha.1", release.version.toString())
        assertEquals(ReleaseChannel.ALPHA, release.version.channel)
        assertEquals("0.1.0-alpha.1", release.title)
        assertEquals(
            "https://github.com/${UpdateConfig.GITHUB_REPOSITORY}/releases/tag/v0.1.0-alpha.1",
            release.pageUrl,
        )
        // The feed carries notes as rendered HTML, which the dialog is not written for.
        assertNull(release.notes)
    }

    @Test
    fun `ignores a link that is not a release page`() {
        val releases = parseGithubFeed(feed(link = "https://github.com/stephen2754/budge/releases"))

        assertTrue(releases.isEmpty())
    }

    @Test
    fun `ignores a tag that is not a version`() {
        val releases = parseGithubFeed(feed(link = "https://github.com/stephen2754/budge/releases/tag/nightly"))

        assertTrue(releases.isEmpty())
    }

    @Test
    fun `a doctype in the feed cannot pull anything in`() {
        val hostile =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE feed [ <!ENTITY secret SYSTEM "file:///etc/passwd"> ]>
            <feed>
              <entry>
                <title>&secret;</title>
                <link rel="alternate" type="text/html" href="https://github.com/stephen2754/budge/releases/tag/v9.9.9"/>
              </entry>
            </feed>
            """.trimIndent()

        val parsed = runCatching { parseGithubFeed(hostile) }.getOrNull()

        // The JDK refuses the document outright. A parser that accepts the DOCTYPE must
        // still hand back nothing from it; reading the file is the one outcome that is
        // never allowed.
        assertTrue(
            "an entity from a remote document was expanded into the app",
            parsed == null || parsed.all { it.title?.contains("root:") != true },
        )
    }

    private fun feed(link: String): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <id>tag:github.com,2008:Repository/1/v9.9.9</id>
            <link rel="alternate" type="text/html" href="$link"/>
            <title>9.9.9</title>
          </entry>
        </feed>
        """.trimIndent()

    private fun fixture(): String =
        checkNotNull(GithubReleaseFeedTest::class.java.getResourceAsStream(FIXTURE)) {
            "$FIXTURE is missing from the test resources"
        }.bufferedReader().use { it.readText() }

    private companion object {
        const val FIXTURE = "/releases-feed-0.1.0-alpha.1.atom"
    }
}
