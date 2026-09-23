package com.example.budge.data.update

import com.example.budge.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update check, run over a response captured from the live endpoint.
 *
 * `app/src/test/resources/github-releases-0.1.0-alpha.1.json` is the body of
 * `GET https://api.github.com/repos/stephen2754/budge/releases?per_page=30`, exactly as
 * GitHub returned it when the first release was published, and it is kept verbatim. The
 * rest of this package tests the rules against payloads written to fit them; this file
 * exists because the shape of a GitHub response is not the app's to decide. If the field
 * names drift, or the release goes up with a tag the parser cannot read, every other test
 * here still passes while the real check quietly finds nothing.
 */
class PublishedReleaseTest {
    private val releases = parseGithubReleases(fixture())

    @Test
    fun `reads the published release out of the real payload`() {
        assertEquals(1, releases.size)

        val release = releases.single()
        assertEquals("0.1.0-alpha.1", release.version.toString())
        assertEquals(ReleaseChannel.ALPHA, release.version.channel)
        assertEquals("0.1.0-alpha.1", release.title)
        assertNotNull(release.notes)
        assertTrue(release.notes!!.isNotBlank())
    }

    @Test
    fun `the release page it points at belongs to the configured repository`() {
        val release = releases.single()

        assertTrue(
            "expected a release page under ${UpdateConfig.GITHUB_REPOSITORY}, was ${release.pageUrl}",
            release.pageUrl.startsWith("https://github.com/${UpdateConfig.GITHUB_REPOSITORY}/releases/"),
        )
        assertEquals(
            "https://api.github.com/repos/${UpdateConfig.GITHUB_REPOSITORY}/releases?per_page=30",
            UpdateConfig.releasesUrl(),
        )
        assertTrue("the app must be pointed at a real repository", UpdateConfig.isConfigured)
    }

    @Test
    fun `a shipped build is offered nothing newer than the release in this payload`() {
        val installed = AppVersion.parse(BuildConfig.VERSION_NAME)
        assertNotNull("the installed versionName must be a version this app can read", installed)

        // Not "unknown": the endpoint answered, and nothing in the answer is newer than
        // the build that shipped. The fixture is the first release, so this keeps holding
        // as the version moves on — what it catches is a build shipped older than the
        // release it was cut from.
        assertNull(selectUpdate(installed!!, releases))
    }

    @Test
    fun `an older build on the same channel is offered the published release`() {
        val older = AppVersion(0, 0, 9, ReleaseChannel.ALPHA, 1)

        assertEquals(releases.single(), selectUpdate(older, releases))
    }

    @Test
    fun `a stable build is not offered an alpha, however new`() {
        val stable = AppVersion(0, 0, 1)

        assertFalse(stable.channel.accepts(releases.single().version.channel))
        assertNull(selectUpdate(stable, releases))
    }

    private companion object {
        const val FIXTURE = "/github-releases-0.1.0-alpha.1.json"

        fun fixture(): String =
            checkNotNull(PublishedReleaseTest::class.java.getResourceAsStream(FIXTURE)) {
                "$FIXTURE is missing from the test resources"
            }.bufferedReader().use { it.readText() }
    }
}
