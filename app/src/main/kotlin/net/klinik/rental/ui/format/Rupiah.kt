package net.klinik.rental.ui.format

/**
 * The one place money becomes text (AR-11).
 *
 * Every rupiah figure in the app comes from here. No screen assembles
 * `"Rp " + value` or pads with zeros by hand — that is how two screens end up
 * showing the same amount two different ways, and how a renter notices.
 *
 * Rules, from `shared.css`'s `formatRupiah` usage and the story's edge-case
 * matrix:
 *  - whole rupiah, grouped in threes with a dot: `250000` -> `Rp 250.000`
 *  - no decimal part, ever: rupiah has no circulating subunit, and showing
 *    `Rp 250.000,00` implies a precision the domain does not have
 *  - `0` -> `Rp 0`, never `Rp 0,00` and never an empty string
 *  - never throws: `Long.MIN_VALUE` is handled, not overflowed
 *
 * Money is `Long` rupiah everywhere. A `Double` would introduce binary
 * rounding on values like `1234.56` that this app has no way to represent
 * correctly, so the type rules the mistake out at the signature.
 *
 * @param amount whole rupiah. A negative value renders as `-Rp 250.000` — the
 *   sign leads, because a trailing minus on a grouped figure reads as a
 *   separator artefact rather than as a debt.
 * @param withPrefix when false, returns just the grouped digits. Used by the
 *   numpad, which renders `Rp` as a separate, differently-styled span.
 */
fun formatRupiah(amount: Long, withPrefix: Boolean = true): String {
    val negative = amount < 0
    // Long.MIN_VALUE has no positive counterpart, so negate in the unsigned
    // domain rather than overflowing back to itself.
    val magnitude = if (negative) {
        amount.toULong().let { if (it == 0UL) 0UL else (0UL - it) }
    } else {
        amount.toULong()
    }

    val digits = magnitude.toString()
    val grouped = buildString(digits.length + digits.length / 3 + 1) {
        if (negative) append('-')
        if (withPrefix) append("Rp ")
        val firstGroup = digits.length % 3
        if (firstGroup != 0) {
            append(digits, 0, firstGroup)
        }
        var i = if (firstGroup == 0) 0 else firstGroup
        while (i < digits.length) {
            if (i != 0) append('.')
            append(digits, i, i + 3)
            i += 3
        }
    }
    return grouped
}

/**
 * Indonesian short date: `23 Jan`, `1 Des` — the epic context's "Voice" rule,
 * dates written in full rupiah-short form.
 *
 * ## On `23 Sen` / `24 Sel`
 *
 * The epic context's example writes dates as `23 Sen`, `24 Sel`. Those are
 * **weekday** abbreviations (Senin, Selasa), not months — `Sen` is not a month
 * in Indonesian, and no month list contains it. This function formats the
 * month, because that is what a billing date needs: a charge falls on a
 * calendar day, and the owner reads it against the calendar rather than
 * against a weekday. A weekday alone would not distinguish 23 Sen from 30 Sen.
 *
 * So the two conventions are deliberately kept apart rather than merged into
 * one ambiguous format:
 *  - month form, this function: `23 Jan`, `1 Des`
 *  - weekday form, [formatHariTanggal]: `Sen`, `Sel`
 *
 * ## Why both arguments are range-checked
 *
 * The epic context forbids hidden defaults: "an out-of-range month silently
 * returns the bare day" is exactly the shape it prohibits, because a caller
 * passing a bad index would get `23` back and never learn the date was
 * malformed. Both arguments therefore throw on an impossible value rather than
 * degrading.
 *
 * @param dayOfMonth 1..31.
 * @param monthIndex 0..11, matching [java.time.Month] ordinals.
 * @throws IllegalArgumentException when either value is out of range.
 */
fun formatTanggalHari(
    dayOfMonth: Int,
    monthIndex: Int,
): String {
    require(dayOfMonth in 1..31) { "dayOfMonth out of range: $dayOfMonth" }
    require(monthIndex in BULAN_SINGKAT.indices) { "monthIndex out of range: $monthIndex" }
    return "$dayOfMonth ${BULAN_SINGKAT[monthIndex]}"
}

/** Three-letter Indonesian month names, `Jan`..`Des`. Zero-based. */
private val BULAN_SINGKAT = listOf(
    "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
    "Jul", "Agu", "Sep", "Okt", "Nov", "Des",
)

/**
 * Three-letter Indonesian weekday abbreviations, `Sen`..`Min` — the `23 Sen`
 * form from the epic context.
 *
 * Zero-based on [java.time.DayOfWeek] ordinals, so Monday is 1.
 *
 * @throws IllegalArgumentException on an out-of-range index.
 */
fun formatHariTanggal(dayOfWeekIndex: Int): String {
    require(dayOfWeekIndex in HARI_SINGKAT.indices) {
        "dayOfWeekIndex out of range: $dayOfWeekIndex"
    }
    return HARI_SINGKAT[dayOfWeekIndex]
}

/** `Sen`, `Sel`, `Rab`, `Kam`, `Jum`, `Sab`, `Min`, indexed by `DayOfWeek`. */
private val HARI_SINGKAT = listOf(
    "Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab",
)

/**
 * Grouped digits without the `Rp` prefix — for the numpad's in-progress display,
 * where the prefix is a separate styled span.
 */
fun formatRupiahDigits(amount: Long): String = formatRupiah(amount, withPrefix = false)
