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
    fun `reads the published releases out of the real feed`() {
        val releases = parseGithubFeed(fixture())

        assertEquals(listOf("0.1.0-alpha.2", "0.1.0-alpha.1"), releases.map { it.version.toString() })
        assertEquals(listOf(ReleaseChannel.ALPHA, ReleaseChannel.ALPHA), releases.map { it.version.channel })
        assertEquals("0.1.0-alpha.2", releases.first().title)
        assertEquals(
            "https://github.com/${UpdateConfig.GITHUB_REPOSITORY}/releases/tag/v0.1.0-alpha.2",
            releases.first().pageUrl,
        )
        // The feed carries notes as rendered HTML, which the dialog is not written for.
        assertNull(releases.first().notes)
    }

    @Test
    fun `the real feed offers the newer release to the build it replaces`() {
        // This is the upgrade path a build in the field takes when the API is refused and
        // the feed is what answers, which is what happened the first time this check was
        // reported broken.
        val installed = AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 1)

        val offered = selectUpdate(installed, parseGithubFeed(fixture()))

        assertEquals("0.1.0-alpha.2", offered?.version?.toString())
    }

    @Test
    fun `the real feed does not offer a newer version to the build that is already on it`() {
        val installed = AppVersion(0, 1, 0, ReleaseChannel.ALPHA, 2)

        assertNull(selectUpdate(installed, parseGithubFeed(fixture())))
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
        const val FIXTURE = "/releases-feed-0.1.0-alpha.2.atom"
    }
}
