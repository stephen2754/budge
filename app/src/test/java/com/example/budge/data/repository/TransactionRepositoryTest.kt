package com.example.budge.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Tests for the month-to-range conversion that every monthly query and every
 * "does this transaction belong to this month" decision is built on.
 *
 * The range is half-open, `[start, end)`, which is what keeps two adjacent months
 * from both claiming a transaction recorded exactly at midnight on the first.
 */
class TransactionRepositoryTest {
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun startOf(
        year: Int,
        month: Int,
    ): Long = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `covers the whole month as a half-open range`() {
        val (start, end) = TransactionRepository.yearMonthToRange(YearMonth.of(2026, 8))

        assertEquals(startOf(2026, 8), start)
        assertEquals(startOf(2026, 9), end)
        assertTrue(start < end)
        assertEquals(31, daysBetween(start, end))
    }

    @Test
    fun `december rolls over into the next year`() {
        val (start, end) = TransactionRepository.yearMonthToRange(YearMonth.of(2026, 12))

        assertEquals(startOf(2026, 12), start)
        assertEquals(startOf(2027, 1), end)
        assertEquals(31, daysBetween(start, end))
    }

    @Test
    fun `handles a leap february`() {
        val (start, end) = TransactionRepository.yearMonthToRange(YearMonth.of(2028, 2))

        assertEquals(startOf(2028, 2), start)
        assertEquals(startOf(2028, 3), end)
        assertEquals(29, daysBetween(start, end))
    }

    @Test
    fun `adjacent months meet exactly and never overlap`() {
        val august = TransactionRepository.yearMonthToRange(YearMonth.of(2026, 8))
        val september = TransactionRepository.yearMonthToRange(YearMonth.of(2026, 9))

        // August's exclusive end is September's inclusive start: an instant exactly
        // at the boundary belongs to September only.
        assertEquals(august.second, september.first)
    }

    @Test
    fun `the boundary instant belongs to the later month`() {
        val august = TransactionRepository.yearMonthToRange(YearMonth.of(2026, 8))
        val september = TransactionRepository.yearMonthToRange(YearMonth.of(2026, 9))
        val midnight = startOf(2026, 9)

        assertTrue("August must exclude its end", midnight < august.first || midnight >= august.second)
        assertTrue("September must include its start", midnight >= september.first && midnight < september.second)
    }

    private fun daysBetween(
        startMillis: Long,
        endMillis: Long,
    ): Long =
        ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(startMillis).atZone(zone),
            Instant.ofEpochMilli(endMillis).atZone(zone),
        )
}
