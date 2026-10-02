package com.example.budge.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for version parsing, ordering and the channel rules.
 *
 * These decisions are the ones a user cannot see the shape of: whether a beta build is
 * offered a stable release, and — more importantly — that a stable build is never
 * offered a beta.
 */
class AppVersionTest {
    @Test
    fun `parses a plain, a v-prefixed and a channelled version`() {
        assertEquals(AppVersion(1, 0, 0), AppVersion.parse("1.0.0"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(AppVersion(1, 2, 3, ReleaseChannel.BETA, 2), AppVersion.parse("1.2.3-beta.2"))
        assertEquals(AppVersion(2, 0, 0, ReleaseChannel.ALPHA, 1), AppVersion.parse("v2.0.0-alpha.1"))
        // A missing channel number means the first one, not "no channel".
        assertEquals(AppVersion(1, 0, 0, ReleaseChannel.BETA, 0), AppVersion.parse("1.0.0-beta"))
    }

    @Test
    fun `reads a release candidate`() {
        assertEquals(AppVersion(1, 0, 0, ReleaseChannel.RC, 1), AppVersion.parse("1.0.0-rc.1"))
        assertEquals(AppVersion(1, 0, 0, ReleaseChannel.RC, 0), AppVersion.parse("v1.0.0-rc"))
        assertEquals(ReleaseChannel.RC, AppVersion.channelOf("0.1.0-rc.1"))
        assertEquals("1.0.0-rc.1", AppVersion(1, 0, 0, ReleaseChannel.RC, 1).toString())
    }

    @Test
    fun `a suffix this build does not know is still a version, read as a pre-release`() {
        // The failure this prevents: a release whose tag does not parse is dropped from the
        // list entirely, so every build older than the new suffix reports itself up to date
        // forever. That happened when -rc.1 was published, and it must not happen again.
        assertEquals(AppVersion(1, 0, 0, ReleaseChannel.BETA, 1), AppVersion.parse("1.0.0-preview.1"))
        assertEquals(AppVersion(1, 0, 0, ReleaseChannel.BETA, 0), AppVersion.parse("v1.0.0-preview"))
        assertEquals(ReleaseChannel.BETA, AppVersion.channelOf("2.0.0-next.3"))
        // Read as a pre-release means a stable build is never shown it...
        assertFalse(ReleaseChannel.STABLE.accepts(AppVersion.parse("1.0.0-preview.1")!!.channel))
        // ...and a beta build is, so the release is visible to somebody.
        assertTrue(ReleaseChannel.BETA.accepts(AppVersion.parse("1.0.0-preview.1")!!.channel))
    }

    @Test
    fun `refuses anything that is not a version`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.0"))
        assertNull(AppVersion.parse("1.0.0.0"))
        assertNull(AppVersion.parse("1.0.0-rc.1.2"))
        assertNull(AppVersion.parse("1.0.0-1"))
    }

    @Test
    fun `orders by number, then channel, then channel number`() {
        assertTrue(AppVersion.parse("1.0.1")!! > AppVersion.parse("1.0.0")!!)
        assertTrue(AppVersion.parse("1.1.0")!! > AppVersion.parse("1.0.9")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)

        // A pre-release is older than the release it leads up to: that is what lets a
        // beta user be moved onto the stable build by ordinary comparison.
        assertTrue(AppVersion.parse("1.0.0-alpha.1")!! < AppVersion.parse("1.0.0-beta.1")!!)
        assertTrue(AppVersion.parse("1.0.0-beta.9")!! < AppVersion.parse("1.0.0")!!)
        // The rung a release candidate occupies: above every beta, below the release it is a
        // candidate for. Without it a beta build could never be offered the candidate, and
        // the last step of a release would be untestable.
        assertTrue(AppVersion.parse("1.0.0-beta.9")!! < AppVersion.parse("1.0.0-rc.1")!!)
        assertTrue(AppVersion.parse("1.0.0-rc.1")!! < AppVersion.parse("1.0.0-rc.2")!!)
        assertTrue(AppVersion.parse("1.0.0-rc.2")!! < AppVersion.parse("1.0.0")!!)
        // The concrete step this project took: if alpha.9 did not sort below beta.1, an
        // alpha build would never be offered the beta that supersedes it.
        assertTrue(AppVersion.parse("0.1.0-alpha.9")!! < AppVersion.parse("0.1.0-beta.1")!!)
        assertTrue(AppVersion.parse("1.0.0-beta.1")!! < AppVersion.parse("1.0.0-beta.2")!!)
    }

    @Test
    fun `round-trips through its own text form`() {
        for (text in listOf("1.0.0", "1.2.3", "1.2.3-alpha.1", "1.2.3-beta.10")) {
            assertEquals(text, AppVersion.parse(text).toString())
        }
    }

    @Test
    fun `the installed channel is read from versionName`() {
        assertEquals(ReleaseChannel.STABLE, AppVersion.channelOf("1.0.0"))
        assertEquals(ReleaseChannel.BETA, AppVersion.channelOf("1.1.0-beta.2"))
        assertEquals(ReleaseChannel.ALPHA, AppVersion.channelOf("2.0.0-alpha.1"))
        // An unreadable version name is treated as stable, which is the safe direction:
        // stable is offered the fewest updates.
        assertEquals(ReleaseChannel.STABLE, AppVersion.channelOf("dev"))
        assertEquals(ReleaseChannel.STABLE, AppVersion.channelOf(null))
    }

    @Test
    fun `refuses a version whose numbers do not fit an int`() {
        // The pattern's digits are unbounded. Throwing here would take the whole update
        // check down and report the response as unreadable; skipping one tag is the
        // honest outcome, since parse() promises null for what it cannot read.
        assertNull(AppVersion.parse("v9999999999.0.0"))
        assertNull(AppVersion.parse("1.9999999999.0"))
        assertNull(AppVersion.parse("1.0.9999999999-alpha.1"))
    }

    @Test
    fun `a stable build sees only stable releases`() {
        assertTrue(ReleaseChannel.STABLE.accepts(ReleaseChannel.STABLE))
        assertFalse(ReleaseChannel.STABLE.accepts(ReleaseChannel.BETA))
        assertFalse(ReleaseChannel.STABLE.accepts(ReleaseChannel.ALPHA))
    }

    @Test
    fun `a beta build sees beta and stable but never alpha`() {
        assertTrue(ReleaseChannel.BETA.accepts(ReleaseChannel.STABLE))
        assertTrue(ReleaseChannel.BETA.accepts(ReleaseChannel.BETA))
        assertFalse(ReleaseChannel.BETA.accepts(ReleaseChannel.ALPHA))
    }

    @Test
    fun `an alpha build sees everything`() {
        assertTrue(ReleaseChannel.ALPHA.accepts(ReleaseChannel.STABLE))
        assertTrue(ReleaseChannel.ALPHA.accepts(ReleaseChannel.BETA))
        assertTrue(ReleaseChannel.ALPHA.accepts(ReleaseChannel.ALPHA))
    }
}
