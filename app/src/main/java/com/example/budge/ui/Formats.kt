package com.example.budge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import com.example.budge.data.prefs.Currencies
import com.example.budge.model.Amount
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Formatting for money and dates, shared by every screen.
 *
 * The screens each used to carry private copies of these helpers, which drifted apart,
 * and list rows rebuilt their formatters on every pass.
 */

// ---------------------------------------------------------------------------
// Money
// ---------------------------------------------------------------------------

// DecimalFormat is not thread-safe, so each thread keeps one instance instead of
// allocating a new formatter for every formatted value.
private val moneyFormat: ThreadLocal<DecimalFormat> =
    ThreadLocal.withInitial { DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)) }

/**
 * Formats integer cents as `SYMBOL1,234.56`.
 *
 * Grouping and the decimal separator are pinned to US conventions, so a number reads
 * the same on every device. The value is handed to [DecimalFormat] as a [BigDecimal]:
 * taking it through a `Double` first would undo the point of storing whole cents.
 */
fun formatMoney(
    amountCents: Long,
    symbol: String = Currencies.DOLLAR,
): String {
    // Kotlin sees ThreadLocal.get() as nullable; withInitial never yields null.
    val formatter = moneyFormat.get()!!
    // Every token the picker offers is a currency sign, so it attaches directly to the
    // number: "¥1,234.56". (A token stored by an older build is normalised to one of the
    // current ones at startup; see BudgeApplication.)
    return "$symbol${formatter.format(BigDecimal.valueOf(amountCents, 2))}"
}

/**
 * Parses user-typed text into a positive amount in cents, or null when the text is not
 * a usable amount: empty, malformed, zero, negative, longer than a 64-bit integer, or
 * above the 32-bit amount ceiling ([Amount.MAX_CENTS]).
 *
 * Rejecting past the ceiling is what keeps a mistyped figure out of the database
 * entirely, instead of letting it wrap somewhere later.
 */
fun parseAmountToCents(text: String): Long? =
    try {
        BigDecimal(normalizeDecimalSeparator(text))
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .takeIf { it.signum() > 0 }
            ?.longValueExact()
            ?.takeIf { Amount.isValid(it) }
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

/** The characters a keyboard may emit for the decimal point, and a pasted amount may carry. */
private const val DECIMAL_SEPARATORS = ".,"

/**
 * Rewrites a typed amount so that everything downstream sees `.` as the decimal point.
 *
 * The **last** separator in the text is taken as the decimal one and every other
 * separator is dropped, which is the only reading that works for both conventions at
 * once: `1,234.56` and `1.234,56` are the same number. Taking the comma seriously is not
 * cosmetic — a German, French, Spanish, Italian, Portuguese or Russian keyboard emits
 * `,` from its decimal key, and dropping it (which is what this used to do) saved `12,50`
 * as `1250.00`, a hundredfold error in the one field that must never be wrong.
 *
 * A lone separator with three digits after it is read as a decimal point, so `1,234`
 * means 1.234 rather than 1234. Those two readings cannot be told apart, and reading a
 * pasted amount as its nearest unit is a smaller error than multiplying it by a thousand.
 */
private fun normalizeDecimalSeparator(text: String): String {
    val trimmed = text.trim()
    // A leading minus is kept so the parser can still refuse a negative amount. Dropping
    // it here would turn "-5" into a perfectly valid 5.00.
    val sign = if (trimmed.startsWith('-')) "-" else ""
    val kept = trimmed.filter { it.isDigit() || it in DECIMAL_SEPARATORS }
    val index = kept.indexOfLast { it in DECIMAL_SEPARATORS }
    if (index < 0) return sign + kept
    return sign + kept.take(index).filter { it.isDigit() } + "." + kept.drop(index + 1).filter { it.isDigit() }
}

/**
 * Restricts raw field input to digits, one decimal separator and at most two fraction
 * digits, so the user cannot type a value the parser rejects.
 *
 * The separator is normalized to `.` as it is typed, whichever one the keyboard sent, so
 * the field shows the value the app will store.
 */
fun sanitizeAmountInput(input: String): String {
    val normalized = normalizeDecimalSeparator(input)
    val dotIndex = normalized.indexOf('.')
    if (dotIndex < 0) return normalized
    val sign = if (normalized.startsWith("-")) "-" else ""
    val whole = normalized.substring(0, dotIndex).filter { it.isDigit() }
    val fraction = normalized.substring(dotIndex + 1).take(2)
    return "$sign$whole.$fraction"
}

/**
 * Picks a text style for a money figure by its length.
 *
 * The summary cards put two figures side by side, so a large total has to step down a
 * size instead of wrapping or being cut off. The thresholds count characters of the
 * formatted string, since that is what has to fit.
 */
@Composable
fun amountTextStyle(text: String): TextStyle =
    when {
        text.length <= 12 -> MaterialTheme.typography.headlineSmall
        text.length <= 18 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.bodyMedium
    }

/** Renders stored cents back into an editable field value such as "12.34". */
fun centsToEditableAmount(amountCents: Long): String = BigDecimal.valueOf(amountCents, 2).toPlainString()

/** First character of a name, kept whole so a surrogate pair is never split. */
fun String.initialChar(): String = if (isEmpty()) "" else substring(0, offsetByCodePoints(0, 1))

// ---------------------------------------------------------------------------
// Release notes
// ---------------------------------------------------------------------------

private val HEADING = Regex("""^\s{0,3}#{1,6}\s+""")
private val BULLET = Regex("""^\s{0,3}(?:[-*+]|\d+[.)])\s+""")
private val HORIZONTAL_RULE = Regex("""^\s{0,3}(?:-{3,}|\*{3,}|_{3,})\s*$""")
private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
private val CODE_SPAN = Regex("""`([^`]*)`""")
private val BOLD = Regex("""\*\*([^*]+)\*\*|__([^_]+)__""")
private val ITALIC = Regex("""\*([^*\n]+)\*|_([^_\n]+)_""")

/**
 * Renders a GitHub release body as the plain text the update dialog can show.
 *
 * The API returns Markdown, and the dialog is a `Text`: the first published notes carried
 * four `##` headings, eight `**` pairs and ten backticks, all of which the user would have
 * read as markup instead of as a message. Only the constructs release notes actually use
 * are handled, and anything unrecognized is left alone rather than guessed at.
 */
fun releaseNotesText(markdown: String): String {
    val text =
        markdown
            .lineSequence()
            .map { line ->
                val trimmed = line.trimEnd()
                when {
                    HORIZONTAL_RULE.matches(trimmed) -> ""
                    else ->
                        trimmed
                            .replace(HEADING, "")
                            .replace(BULLET, "• ")
                            .replace(LINK) { it.groupValues[1] }
                            .replace(CODE_SPAN) { it.groupValues[1] }
                            .replace(BOLD) { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }
                            .replace(ITALIC) { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }
                }
            }
            .joinToString("\n")

    return text.replace(Regex("""\n{3,}"""), "\n\n").trim()
}

// ---------------------------------------------------------------------------
// Date picker <-> timestamp conversion
//
// Material 3's date picker identifies a day by the epoch millis of that day's
// UTC midnight, while the app stores the user's local instant. Passing a local
// timestamp straight in — or reading the result back with the local zone —
// shifts the day by one for anyone east of UTC in the early morning, or west of
// it in the evening. The helpers below are the only sanctioned conversions.
// ---------------------------------------------------------------------------

/** The value the date picker expects for [date]: that day's UTC midnight. */
fun datePickerMillisFor(date: LocalDate): Long =
    date
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

/** The calendar date a date-picker cell represents (picker values are UTC-normalized). */
fun localDateFromPickerMillis(pickerMillis: Long): LocalDate =
    Instant
        .ofEpochMilli(pickerMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()

/**
 * Applies a newly picked date to an existing [timestamp], keeping the time of
 * day already chosen. Used by the "Date" button, which changes only the date.
 */
fun applyDateToTimestamp(
    pickerMillis: Long,
    timestamp: Long,
    zone: ZoneId,
): Long {
    val date = localDateFromPickerMillis(pickerMillis)
    val time = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime()
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

/** Builds the wall-clock instant for [hour]:[minute] on the day [timestamp] falls on. */
fun applyTimeToTimestamp(
    hour: Int,
    minute: Int,
    timestamp: Long,
    zone: ZoneId,
): Long {
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    return date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

// ---------------------------------------------------------------------------
// Localized date/time text
// ---------------------------------------------------------------------------

/** Chinese and Japanese write dates year-first with unit characters ("2026年8月11日"). */
private fun cjkStyle(locale: Locale): Boolean = locale.language == "zh" || locale.language == "ja"

/** Formatter cached per locale: the pattern lookup is the expensive part, not the format call. */
@Composable
private fun pattern(pattern: String): DateTimeFormatter {
    val locale = LocalAppLocale.current
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}

/** CLDR date formatter for the app locale, cached like [pattern]. */
@Composable
private fun localizedDate(style: FormatStyle): DateTimeFormatter {
    val locale = LocalAppLocale.current
    return remember(style, locale) { DateTimeFormatter.ofLocalizedDate(style).withLocale(locale) }
}

/** Formats a month for a screen title: "2026年8月" / "August 2026". */
@Composable
fun formatMonthYear(yearMonth: YearMonth): String =
    if (cjkStyle(LocalAppLocale.current)) {
        "${yearMonth.year}年${yearMonth.monthValue}月"
    } else {
        yearMonth.format(pattern("MMMM yyyy"))
    }

/** Formats a day-list header: "8月11日 周二" / "Aug 11, Tue". */
@Composable
fun formatDayHeader(date: LocalDate): String =
    date.format(pattern(if (cjkStyle(LocalAppLocale.current)) "M月d日 EEE" else "MMM d, EEE"))

/**
 * Formats the date button on the entry screen.
 *
 * The locale's own CLDR format is used, so each language gets its usual order and month
 * name: "Aug 11, 2026", "2026年8月11日", "11 août 2026", "11 авг. 2026 г.".
 */
@Composable
fun formatShortDate(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(localizedDate(FormatStyle.MEDIUM))
}

/** Formats a 24-hour wall-clock time such as "09:05". */
@Composable
fun formatClockTime(timestamp: Long): String {
    val time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
    return time.format(pattern("HH:mm"))
}

/** Formats the yearly stats title: "2026年" / "2026". */
@Composable
fun formatYear(date: LocalDate): String =
    if (cjkStyle(LocalAppLocale.current)) {
        "${date.year}年"
    } else {
        date.format(pattern("yyyy"))
    }

/** Formats the daily stats title: "2026年8月11日" / "11 August 2026". */
@Composable
fun formatDayMonthYear(date: LocalDate): String =
    if (cjkStyle(LocalAppLocale.current)) {
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    } else {
        // `MMMM` is the format (not standalone) style, which is what Russian and
        // other inflected languages need in a phrase like "11 августа 2026".
        date.format(pattern("d MMMM yyyy"))
    }
