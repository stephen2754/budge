package com.example.budge.ui

import com.example.budge.data.prefs.Currencies
import com.example.budge.model.Amount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Regression tests for the display/conversion helpers.
 *
 * Everything here is a plain JVM test: the functions under test are deliberately
 * free of Android and Compose so they can be checked without a device.
 */
class FormatsTest {
    // -----------------------------------------------------------------------
    // Money
    // -----------------------------------------------------------------------

    @Test
    fun `formats cents with symbol and US grouping`() {
        assertEquals("$0.00", formatMoney(0))
        assertEquals("$0.10", formatMoney(10))
        assertEquals("$12.34", formatMoney(1234))
        assertEquals("$1,234.56", formatMoney(123456))
        assertEquals("¥-12.34", formatMoney(-1234, "¥"))
    }

    @Test
    fun `attaches the currency sign to the amount`() {
        // Amounts carry the sign only: no "CNY"/"JPY" text on a figure the user reads
        // all day. Which currency the ¥ is belongs in the Settings list.
        assertEquals("$1,234.56", formatMoney(123456))
        assertEquals("€1,234.56", formatMoney(123456, Currencies.EURO))
        assertEquals("₽1,234.56", formatMoney(123456, Currencies.RUBLE))
        assertEquals("£1,234.56", formatMoney(123456, Currencies.POUND))
        assertEquals("¥1,234.56", formatMoney(123456, Currencies.YEN_SIGN))
    }

    @Test
    fun `keeps every cent of a value a Double could not represent`() {
        // 2^53 + 1 cents. Routing the amount through `cents / 100.0` — which the
        // per-screen formatters used to do — loses the last cent and prints .92.
        // This is the guard for the "integer cents end to end" rule.
        assertEquals("$90,071,992,547,409.93", formatMoney(9_007_199_254_740_993L))
    }

    @Test
    fun `parses typed amounts into positive cents`() {
        assertEquals(1234L, parseAmountToCents("12.34"))
        assertEquals(1200L, parseAmountToCents("12"))
        assertEquals(5L, parseAmountToCents(" 0.05 "))
        // Half-up rounding on the third decimal.
        assertEquals(1235L, parseAmountToCents("12.345"))
    }

    @Test
    fun `accepts the largest 32-bit amount and refuses anything above it`() {
        // The ceiling is what stops a mistyped figure ever reaching the database.
        assertEquals(Amount.MAX_CENTS, parseAmountToCents("42949672.95"))
        assertNull(parseAmountToCents("42949672.96"))
        assertNull(parseAmountToCents("99999999999999999999"))
        assertEquals("¥42,949,672.95", formatMoney(Amount.MAX_CENTS, Currencies.YEN_SIGN))
    }

    @Test
    fun `the balance keeps the full signed 64-bit range`() {
        // Every amount is inside u32, but income - expense is i64 and must not wrap.
        assertEquals(-4_294_967_295L, 0L - Amount.MAX_CENTS)
        assertEquals("¥-42,949,672.95", formatMoney(0L - Amount.MAX_CENTS, Currencies.YEN_SIGN))
    }

    @Test
    fun `rejects amounts that are not usable money`() {
        assertNull(parseAmountToCents(""))
        assertNull(parseAmountToCents("abc"))
        assertNull(parseAmountToCents("."))
        assertNull(parseAmountToCents("0"))
        assertNull(parseAmountToCents("0.00"))
        assertNull(parseAmountToCents("-5"))
        // Overflow must fail rather than wrap around into a negative amount.
        assertNull(parseAmountToCents("99999999999999999999"))
    }

    @Test
    fun `sanitizes typing to one decimal point and two places`() {
        assertEquals("12.34", sanitizeAmountInput("12.345"))
        assertEquals("1.23", sanitizeAmountInput("1.2.3"))
        assertEquals("12", sanitizeAmountInput("a1b2"))
        assertEquals("1.", sanitizeAmountInput("1."))
        assertEquals("", sanitizeAmountInput("abc"))
    }

    @Test
    fun `renders stored cents back into a two-place editable value`() {
        // The old `(cents / 100.0).toString()` produced "12.0" for 1200 and "0.1"
        // for 10, so reopening a transaction showed a malformed amount.
        assertEquals("12.00", centsToEditableAmount(1200))
        assertEquals("0.10", centsToEditableAmount(10))
        assertEquals("12.34", centsToEditableAmount(1234))
    }

    @Test
    fun `initial character never splits a surrogate pair`() {
        assertEquals("餐", "餐饮".initialChar())
        assertEquals("A", "Apple".initialChar())
        assertEquals("", "".initialChar())
        // take(1) would return a lone high surrogate here and render as a tofu box.
        val emoji = "\uD83D\uDE00 snack"
        assertEquals("\uD83D\uDE00", emoji.initialChar())
        assertEquals(2, emoji.initialChar().length)
    }

    // -----------------------------------------------------------------------
    // Date picker conversion
    // -----------------------------------------------------------------------

    @Test
    fun `picker millis are the UTC midnight of the given local date`() {
        val date = LocalDate.of(2026, 8, 11)
        assertEquals(
            Instant.parse("2026-08-11T00:00:00Z").toEpochMilli(),
            datePickerMillisFor(date),
        )
        assertEquals(date, localDateFromPickerMillis(datePickerMillisFor(date)))
    }

    /**
     * The bug this guards against: the picker was handed the raw local instant
     * instead of that day's UTC midnight, so east of UTC in the early morning (and
     * west of UTC in the evening) it highlighted the neighbouring day and confirming
     * without touching anything moved the transaction by a day.
     */
    @Test
    fun `confirming the same date leaves the instant untouched in every zone`() {
        val zones = listOf("Asia/Shanghai", "America/New_York", "Europe/Berlin", "UTC")
        // 07:00 and 21:00 local: the hours where the UTC date differs from the
        // local date in a positive and in a negative offset respectively.
        val hours = listOf(0, 7, 12, 21, 23)

        for (zoneId in zones) {
            val zone = ZoneId.of(zoneId)
            for (hour in hours) {
                val timestamp =
                    ZonedDateTime.of(2026, 8, 11, hour, 30, 0, 0, zone)
                        .toInstant()
                        .toEpochMilli()
                val localDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
                val pickerMillis = datePickerMillisFor(localDate)

                assertEquals(
                    "re-confirming $localDate at ${hour}:30 in $zoneId must not move the instant",
                    timestamp,
                    applyDateToTimestamp(pickerMillis, timestamp, zone),
                )
            }
        }
    }

    @Test
    fun `shows why the conversion is needed at all`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val timestamp =
            ZonedDateTime.of(2026, 8, 11, 7, 0, 0, 0, zone)
                .toInstant()
                .toEpochMilli()

        // Feeding the raw instant in would have selected the 10th, because the
        // picker reads the *UTC* calendar date of whatever it is given.
        val naiveUtcDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("UTC")).toLocalDate()
        assertNotEquals(LocalDate.of(2026, 8, 11), naiveUtcDate)
        assertEquals(LocalDate.of(2026, 8, 10), naiveUtcDate)

        // The converted value selects the right day.
        val localDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 11), localDateFromPickerMillis(datePickerMillisFor(localDate)))
    }

    @Test
    fun `applies a picked date while keeping the chosen time of day`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val timestamp =
            ZonedDateTime.of(2026, 8, 11, 21, 45, 0, 0, zone)
                .toInstant()
                .toEpochMilli()

        val picked = LocalDate.of(2026, 7, 1)
        val result = applyDateToTimestamp(datePickerMillisFor(picked), timestamp, zone)
        val resultTime = Instant.ofEpochMilli(result).atZone(zone)

        assertEquals(picked, resultTime.toLocalDate())
        assertEquals(21, resultTime.hour)
        assertEquals(45, resultTime.minute)
    }

    @Test
    fun `applies a picked time to the day already selected`() {
        val zone = ZoneId.of("Europe/Berlin")
        val timestamp =
            ZonedDateTime.of(2026, 8, 11, 21, 45, 0, 0, zone)
                .toInstant()
                .toEpochMilli()

        val result = applyTimeToTimestamp(hour = 9, minute = 5, timestamp = timestamp, zone = zone)
        val resultTime = Instant.ofEpochMilli(result).atZone(zone)

        assertEquals(LocalDate.of(2026, 8, 11), resultTime.toLocalDate())
        assertEquals(9, resultTime.hour)
        assertEquals(5, resultTime.minute)
    }
}
