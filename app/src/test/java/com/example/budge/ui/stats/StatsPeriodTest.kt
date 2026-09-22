package com.example.budge.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tests for the period anchor.
 *
 * The requirement these pin down: the Day, Month and Year views are three views of
 * one date. Picking 3 December in the day view and switching to the month view must
 * land on December — it used to keep whatever month had been selected before, because
 * the month and the date were separate fields.
 */
class StatsPeriodTest {
    @Test
    fun `the month view is always the month of the anchor date`() {
        val state = StatsUiState(currentDate = LocalDate.of(2026, 12, 3))

        assertEquals(YearMonth.of(2026, 12), state.currentMonth)
    }

    @Test
    fun `there is no way to set a month that disagrees with the date`() {
        // The invariant is structural: currentMonth has no backing field, it is derived.
        val december = StatsUiState(currentDate = LocalDate.of(2026, 12, 3))
        val january = StatsUiState(currentDate = LocalDate.of(2026, 1, 3))

        assertEquals(YearMonth.of(2026, 12), december.currentMonth)
        assertEquals(YearMonth.of(2026, 1), january.currentMonth)
        assertEquals(12, december.currentDate.monthValue)
        assertEquals(2026, december.currentDate.year)
    }

    @Test
    fun `the year view reads the year of the same anchor`() {
        val state = StatsUiState(period = StatsPeriod.YEARLY, currentDate = LocalDate.of(2026, 12, 3))

        assertEquals(2026, state.currentDate.year)
        assertEquals(YearMonth.of(2026, 12), state.currentMonth)
    }

    @Test
    fun `navigating a month moves the whole anchor, not just the month`() {
        val anchor = LocalDate.of(2026, 12, 3)

        assertEquals(LocalDate.of(2026, 11, 3), anchor.shiftedBy(StatsPeriod.MONTHLY, -1))
        assertEquals(LocalDate.of(2027, 1, 3), anchor.shiftedBy(StatsPeriod.MONTHLY, 1))
        // December -> January crosses the year boundary with it.
        assertEquals(YearMonth.of(2027, 1), YearMonth.from(anchor.shiftedBy(StatsPeriod.MONTHLY, 1)))
    }

    @Test
    fun `navigating a year or a day keeps the other parts`() {
        val anchor = LocalDate.of(2026, 12, 3)

        assertEquals(LocalDate.of(2025, 12, 3), anchor.shiftedBy(StatsPeriod.YEARLY, -1))
        assertEquals(LocalDate.of(2027, 12, 3), anchor.shiftedBy(StatsPeriod.YEARLY, 1))
        assertEquals(LocalDate.of(2026, 12, 2), anchor.shiftedBy(StatsPeriod.DAILY, -1))
        assertEquals(LocalDate.of(2026, 12, 4), anchor.shiftedBy(StatsPeriod.DAILY, 1))
    }

    @Test
    fun `month navigation clamps at the end of a short month`() {
        // java.time semantics, documented on the view model: 31 March minus a month is
        // 28 February, and stepping a day across a month end rolls the month too.
        assertEquals(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31).shiftedBy(StatsPeriod.MONTHLY, -1))
        assertEquals(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 28).shiftedBy(StatsPeriod.DAILY, 1))
        assertEquals(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31).shiftedBy(StatsPeriod.DAILY, 1))
    }

    @Test
    fun `only day, month and year are offered`() {
        // The week period was removed: the selector shows 年 / 月 / 日 only.
        assertEquals(listOf(StatsPeriod.YEARLY, StatsPeriod.MONTHLY, StatsPeriod.DAILY), StatsPeriod.entries.toList())
    }
}
