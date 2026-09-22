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
    fun `refuses anything that is not a version`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.0"))
        assertNull(AppVersion.parse("1.0.0.0"))
        assertNull(AppVersion.parse("1.0.0-rc.1"))
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
