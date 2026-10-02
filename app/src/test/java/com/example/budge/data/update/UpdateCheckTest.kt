package com.example.budge.data.update

import com.example.budge.BuildConfig
import com.example.budge.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for "which release should this build be told about".
 *
 * One rule governs both the update check and the release history, so the two can never
 * disagree: a build is never offered — or shown — a release from a channel it does not
 * accept.
 */
class UpdateCheckTest {
    private fun remote(
        version: String,
        page: String = "https://example.invalid/$version",
    ) = RemoteRelease(
        version = AppVersion.parse(version)!!,
        title = version,
        notes = null,
        pageUrl = page,
    )

    private val published =
        listOf(
            remote("1.0.0"),
            remote("1.1.0"),
            remote("1.2.0-beta.1"),
            remote("1.2.0-beta.2"),
            remote("2.0.0-alpha.1"),
        )

    @Test
    fun `a stable build is offered the newest stable release only`() {
        val current = AppVersion.parse("1.0.0")!!

        val update = selectUpdate(current, published)

        assertEquals("1.1.0", update?.version?.toString())
    }

    @Test
    fun `a stable build is never offered a beta or an alpha`() {
        val current = AppVersion.parse("1.1.0")!!

        // 1.2.0-beta.2 and 2.0.0-alpha.1 are the newest overall, and both are refused.
        assertNull(selectUpdate(current, published))
    }

    @Test
    fun `a beta build is offered the newest beta or stable, never an alpha`() {
        val current = AppVersion.parse("1.0.0-beta.1")!!

        val update = selectUpdate(current, published)

        assertEquals("1.2.0-beta.2", update?.version?.toString())
    }

    @Test
    fun `a beta build is moved onto the stable release that supersedes it`() {
        val current = AppVersion.parse("1.2.0-beta.2")!!

        // 1.1.0 (stable) is older than this beta, so it is not an update at all; 1.2.0 is
        // the release the beta leads up to, and a beta build accepts stable releases.
        val update = selectUpdate(current, published + remote("1.2.0"))

        assertEquals("1.2.0", update?.version?.toString())
    }

    @Test
    fun `a beta build is never pushed backwards onto an older stable release`() {
        val current = AppVersion.parse("1.2.0-beta.2")!!

        assertNull(selectUpdate(current, published))
    }

    @Test
    fun `an alpha build is offered the newest of anything`() {
        val current = AppVersion.parse("1.0.0-alpha.1")!!

        val update = selectUpdate(current, published)

        assertEquals("2.0.0-alpha.1", update?.version?.toString())
    }

    @Test
    fun `only a beta build may look at the alphas on request`() {
        assertTrue("a beta build may ask", alphasFor(ReleaseChannel.BETA).isNotEmpty())
        assertTrue("an alpha build already has them in its history", alphasFor(ReleaseChannel.ALPHA).isEmpty())
        assertTrue("a stable build is not on the test programme", alphasFor(ReleaseChannel.STABLE).isEmpty())
    }

    @Test
    fun `what a beta build may look at is alphas only, newest first`() {
        val alphas = alphasFor(ReleaseChannel.BETA)

        assertTrue(
            "revealing the alphas must not smuggle in another channel",
            alphas.all { it.version.channel == ReleaseChannel.ALPHA },
        )
        assertEquals(alphas.sortedByDescending { it.version }, alphas)
    }

    @Test
    fun `a stable build can be offered the newest beta worth joining`() {
        val offer =
            selectBetaOffer(
                AppVersion.parse("1.0.0")!!,
                listOf(remote("1.1.0-beta.1"), remote("1.2.0-beta.1"), remote("1.1.0-beta.4")),
            )

        assertEquals(AppVersion.parse("1.2.0-beta.1"), offer?.version)
    }

    @Test
    fun `joining the beta never means an alpha`() {
        val offer = selectBetaOffer(AppVersion.parse("1.0.0")!!, listOf(remote("2.0.0-alpha.1")))

        assertNull("an alpha is the development channel, not the test programme", offer)
    }

    @Test
    fun `a stable build ahead of every beta is offered nothing to join`() {
        val offer =
            selectBetaOffer(
                AppVersion.parse("1.3.0")!!,
                listOf(remote("1.2.0-beta.3"), remote("1.2.0-beta.9")),
            )

        assertNull(offer)
    }

    @Test
    fun `a build already on a test channel is offered nothing to join`() {
        val betas = listOf(remote("1.1.0-beta.1"))

        assertNull(selectBetaOffer(AppVersion.parse("1.0.0-beta.1")!!, betas))
        assertNull(selectBetaOffer(AppVersion.parse("1.0.0-alpha.1")!!, betas))
    }

    @Test
    fun `a stable release is never the beta to join`() {
        val offer = selectBetaOffer(AppVersion.parse("1.0.0")!!, listOf(remote("1.1.0")))

        assertNull(offer)
    }

    @Test
    fun `a build ahead of the release source is not offered a downgrade`() {
        val current = AppVersion.parse("3.0.0")!!

        assertNull(selectUpdate(current, published))
    }

    @Test
    fun `an empty release list offers nothing`() {
        assertNull(selectUpdate(AppVersion.parse("1.0.0")!!, emptyList()))
    }

    // -----------------------------------------------------------------------
    // Release history
    // -----------------------------------------------------------------------

    private val history =
        listOf(
            ReleaseNotes(AppVersion.parse("1.0.0")!!, R.string.release_notes_0_1_0_alpha_1),
            ReleaseNotes(AppVersion.parse("1.1.0-beta.1")!!, R.string.release_notes_0_1_0_alpha_1),
            ReleaseNotes(AppVersion.parse("2.0.0-alpha.1")!!, R.string.release_notes_0_1_0_alpha_1),
        )

    @Test
    fun `a stable build's history holds stable releases and nothing else`() {
        val visible = filterHistory(history, ReleaseChannel.STABLE)

        assertEquals(listOf("1.0.0"), visible.map { it.version.toString() })
    }

    @Test
    fun `a beta build's history adds betas but still hides alphas`() {
        val visible = filterHistory(history, ReleaseChannel.BETA)

        assertEquals(listOf("1.1.0-beta.1", "1.0.0"), visible.map { it.version.toString() })
    }

    @Test
    fun `an alpha build's history holds everything, newest first`() {
        val visible = filterHistory(history, ReleaseChannel.ALPHA)

        assertEquals(listOf("2.0.0-alpha.1", "1.1.0-beta.1", "1.0.0"), visible.map { it.version.toString() })
    }

    @Test
    fun `the shipped build can see its own release`() {
        // Guards the release routine: bumping versionName without adding the matching
        // entry here would leave the About row listing everything except the build the
        // user is running.
        val channel = AppVersion.channelOf(BuildConfig.VERSION_NAME)
        val current = AppVersion.parse(BuildConfig.VERSION_NAME)!!
        val history = releaseHistoryFor(channel)

        assertTrue("the history is empty for $channel", history.isNotEmpty())
        assertTrue("$current is missing from its own history", history.any { it.version == current })
    }

    @Test
    fun `a stable build is shown stable releases only`() {
        val visible = releaseHistoryFor(ReleaseChannel.STABLE)

        assertTrue(visible.all { it.version.channel == ReleaseChannel.STABLE })
    }
}
