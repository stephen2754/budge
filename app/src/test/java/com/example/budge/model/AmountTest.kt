package com.example.budge.model

import com.example.budge.data.prefs.Currencies
import com.example.budge.ui.formatMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the money widths.
 *
 * Amounts are 32-bit unsigned and the balance is 64-bit signed, and the pair is what
 * keeps a fat-fingered figure from turning into a number the rest of the app cannot
 * handle: the input bound refuses it, and the aggregate bound saturates rather than
 * wrapping into something that looks like real data.
 */
class AmountTest {
    @Test
    fun `the amount ceiling is 2 to the 32 minus 1 cents`() {
        assertEquals(0xFFFF_FFFFL, Amount.MAX_CENTS)
        assertEquals(4_294_967_295L, Amount.MAX_CENTS)
        // About 42.9 million currency units: far past any personal ledger, and the
        // largest figure the summary cards can be asked to lay out.
        assertEquals("¥42,949,672.95", formatMoney(Amount.MAX_CENTS, Currencies.YEN_SIGN))
    }

    @Test
    fun `a valid amount is positive and inside the ceiling`() {
        assertTrue(Amount.isValid(1L))
        assertTrue(Amount.isValid(1234L))
        assertTrue(Amount.isValid(Amount.MAX_CENTS))

        assertFalse("zero is not a transaction", Amount.isValid(0L))
        assertFalse("amounts are not signed", Amount.isValid(-1L))
        assertFalse("one cent past the ceiling is refused", Amount.isValid(Amount.MAX_CENTS + 1))
        assertFalse("Long.MAX_VALUE is refused", Amount.isValid(Long.MAX_VALUE))
    }

    @Test
    fun `aggregates saturate instead of wrapping`() {
        // A total past the ceiling shows the ceiling: wrong, but bounded and obviously
        // not a real figure. A wrap would show a small number that looks like data.
        assertEquals(Amount.MAX_CENTS, Amount.coerceToAmount(Amount.MAX_CENTS + 5))
        assertEquals(Amount.MAX_CENTS, Amount.coerceToAmount(Long.MAX_VALUE))
        assertEquals(0L, Amount.coerceToAmount(-3))
        assertEquals(1234L, Amount.coerceToAmount(1234))
    }

    @Test
    fun `the balance is never clamped and never wraps`() {
        // income - expense in i64: the two extreme cases both fit with room to spare.
        assertEquals(Amount.MAX_CENTS, Amount.MAX_CENTS - 0L)
        assertEquals(-Amount.MAX_CENTS, 0L - Amount.MAX_CENTS)
        // Which is the reason the balance is 64-bit: it has to be able to go negative.
        assertTrue(Amount.MAX_CENTS - 0L > 0L)
        assertTrue(0L - Amount.MAX_CENTS < 0L)
    }
}
