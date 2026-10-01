package com.example.budge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringReader

/**
 * Tests for the bounded read used on both paths that take in text from outside the app.
 *
 * The point of the limit is that the app's memory is not whatever the input happens to be:
 * reading a picked backup or a response body whole can be an [OutOfMemoryError], which is an
 * `Error` and so escapes every `catch (Exception)` on those paths.
 */
class BoundedReadTest {
    @Test
    fun `reads input that fits`() {
        val text = "x".repeat(1000)

        assertEquals(text, StringReader(text).readAtMost(limit = 1000))
    }

    @Test
    fun `an empty reader is an empty string, not a refusal`() {
        assertEquals("", StringReader("").readAtMost(limit = 10))
    }

    @Test
    fun `refuses input one character past the limit`() {
        assertNull(StringReader("x".repeat(1001)).readAtMost(limit = 1000))
    }

    @Test
    fun `refuses rather than truncates, even when the excess is far past the limit`() {
        // A truncated document is not a partial answer: half a backup would decode as a
        // ledger that is missing records, which is worse than refusing it.
        assertNull(StringReader("x".repeat(64)).readAtMost(limit = 8))
    }

    @Test
    fun `reads in chunks rather than assuming one read returns everything`() {
        // Readers are allowed to return one character at a time, which is what a slow or
        // filtered stream actually does. The limit is on the total, so this still fits.
        val oneByteAtATime =
            object : java.io.Reader() {
                private var index = 0
                private val text = "y".repeat(64)

                override fun read(
                    cbuf: CharArray,
                    off: Int,
                    len: Int,
                ): Int {
                    if (index >= text.length) return -1
                    cbuf[off] = text[index++]
                    return 1
                }

                override fun close() = Unit
            }

        assertEquals("y".repeat(64), oneByteAtATime.readAtMost(limit = 64))
    }
}
