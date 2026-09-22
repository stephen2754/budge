package com.example.budge.model

/**
 * How wide each money value is allowed to get.
 *
 * Amounts are unsigned 32-bit cents: 0 to 4,294,967,295, or about 42.9 million units.
 * That is far more than a personal ledger needs, and it is what keeps a mistyped entry
 * from becoming a number that breaks a total, a layout or an arithmetic step further
 * down. Anything above the ceiling is refused rather than truncated (`isValid`), and a
 * sum that somehow exceeds it saturates (`coerceToAmount`).
 *
 * The balance is a signed 64-bit value instead. It is a difference rather than an
 * amount: it has to be able to go negative, and subtracting one 32-bit figure from
 * another must not wrap.
 *
 * SQLite has no unsigned integer type, so the column is an `INTEGER` that Room maps to
 * `Long`. The range above is the part that makes a value u32, and there is no schema
 * change behind any of this.
 */
object Amount {
    /** 2^32 - 1 cents: the largest amount the app accepts, stores or displays. */
    const val MAX_CENTS: Long = 0xFFFF_FFFFL

    /** True when [cents] can be stored as an amount: at least one cent, at most [MAX_CENTS]. */
    fun isValid(cents: Long): Boolean = cents in 1..MAX_CENTS

    /**
     * Clamps a total into the amount range.
     *
     * Saturation beats wrapping here. A total pinned at the ceiling is obviously not a
     * real figure; a wrapped one looks like a small, plausible number.
     */
    fun coerceToAmount(cents: Long): Long = cents.coerceIn(0L, MAX_CENTS)
}
