package com.example.budge.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for checking a downloaded update before it is installed.
 *
 * This is the whole reason the app is allowed to install anything: a file that arrived over
 * the network is installed only when its SHA-256 is the one the release published, and every
 * way of *not knowing* has to come out as a refusal rather than as a pass.
 */
class VerifiedDownloadTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun fileWith(bytes: ByteArray): File = folder.newFile("download.apk").apply { writeBytes(bytes) }

    @Test
    fun `hashes a file the way sha256 does`() {
        // The well-known hash of "abc", so the helper is checked against something outside
        // this codebase rather than against itself.
        val file = fileWith("abc".toByteArray())

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256(file))
    }

    @Test
    fun `hashes an empty file`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256(fileWith(ByteArray(0))))
    }

    @Test
    fun `an unreadable file has no hash rather than an empty one`() {
        val missing = File(folder.root, "not-there.apk")

        assertNull(sha256(missing))
    }

    @Test
    fun `a matching hash passes, whatever case GitHub wrote it in`() {
        val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        assertTrue(matchesSha256("sha256:$hash", hash))
        assertTrue(matchesSha256("sha256:${hash.uppercase()}", hash))
    }

    @Test
    fun `a different hash fails`() {
        val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        assertFalse(matchesSha256("sha256:${"0".repeat(64)}", hash))
    }

    @Test
    fun `a download nobody published a hash for is refused, not waved through`() {
        val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        assertFalse("no digest at all", matchesSha256(null, hash))
        assertFalse("an empty digest", matchesSha256("", hash))
        assertFalse("a digest in another algorithm", matchesSha256("sha512:$hash", hash))
        assertFalse("a bare hash with no algorithm", matchesSha256(hash, hash))
    }

    @Test
    fun `a file that could not be hashed is refused`() {
        assertFalse(matchesSha256("sha256:abc", null))
    }
}
