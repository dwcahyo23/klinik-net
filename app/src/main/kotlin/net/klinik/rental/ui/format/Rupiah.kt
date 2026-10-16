package net.klinik.rental.ui.format

import java.time.DayOfWeek
import java.time.LocalDate

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
 * Indonesian short date from a [LocalDate], **month form**: `23 Okt`, `1 Des`.
 *
 * ## Why this is the form the contract list uses
 *
 * The list's job is telling one rental apart from another, and a weekday alone
 * cannot: two rentals begun on the 23rd of different months both render `23 Jum`,
 * which is not a date the owner can act on. The month is the part that
 * distinguishes them, so the row's date carries it.
 *
 * ## Why it has a name of its own rather than overloading `formatTanggalHari`
 *
 * `formatTanggalHari` already names the *month* form, and the weekday form has its own
 * name ([formatHariTanggal]). An earlier version of this function was called
 * `formatTanggalHari(LocalDate)` while composing the **weekday** form, which made one
 * name produce two different strings — `formatTanggalHari(23, 9)` rendered `23 Okt`
 * and `formatTanggalHari(sameDate)` rendered `23 Jum`. Two conventions under one name
 * is how a caller ends up with the format it did not ask for.
 *
 * ## Why the two conventions have different entry points
 *
 * Each convention is reachable from the input that actually holds it, and from only
 * one shape — so there is no second spelling of either format to call by mistake:
 *
 *  - the **weekday** form is reachable from a [LocalDate]
 *    ([formatHariTanggal]) and from a [DayOfWeek] ([shortLabel]), because a weekday
 *    is what a rest-day chip and the row's `Libur Min` badge actually hold
 *  - the **month** form is reachable from a [LocalDate] (this function) and from
 *    decomposed ints ([formatTanggalHari]), because a calendar date is what a stored
 *    contract holds
 *
 * ## Why the decomposition happens here
 *
 * [formatTanggalHari]'s two-argument form takes decomposed `Int`s, so every caller
 * holding a real date had to reach for `date.dayOfMonth` and `date.monthValue - 1`.
 * That is where the off-by-one lives — the month list is zero-based and `Month` is
 * one-based — and a call site that got it wrong would render a plausible but wrong
 * month. So the decomposition happens once, here, next to the table it indexes.
 *
 * @throws IllegalArgumentException never — a real `LocalDate` always carries a day
 *   of month in 1..31 and a month, so the delegated call is in range by
 *   construction. The delegated function still checks, which is what makes that
 *   claim hold rather than being assumed.
 */
fun formatTanggalBulan(date: LocalDate): String =
    formatTanggalHari(date.dayOfMonth, date.monthValue - 1)

/**
 * Indonesian short date from a [LocalDate], **weekday form**: `23 Sen`, `24 Sel`.
 *
 * ## Why this is a `formatHariTanggal` overload and not a `formatTanggalHari` one
 *
 * The two names carry the two formats, and this function produces the *weekday* one —
 * so it belongs under the weekday name. An earlier version was called
 * `formatTanggalHari(LocalDate)` while composing this same weekday form, which meant
 * `formatTanggalHari` produced `23 Okt` for one input shape and `23 Jum` for another.
 * One name, two formats, and no way to tell which a call site would get.
 *
 * The overload is legitimate here for the opposite reason: `formatHariTanggal(Int)` and
 * `formatHariTanggal(LocalDate)` render the *same* format from the two shapes a caller
 * might hold. The shape differs; the output does not.
 *
 * ## Which form the contract list uses
 *
 * Not this one — [formatTanggalBulan] is, because the list's job is telling one rental
 * from another and a weekday cannot: two rentals begun on the 23rd of different months
 * both render `23 Jum`. This form is for a caller that wants the weekday *of* a real
 * date, which is a different question from "which date is this".
 *
 * Note this is `day + weekday` (`23 Sen`), not the bare abbreviation [shortLabel]
 * returns (`Sen`): a date needs its day of month, while a rest-day chip and the
 * `Libur Min` badge are naming a weekday and nothing else.
 *
 * @throws IllegalArgumentException never — a real `LocalDate` always carries a
 *   `DayOfWeek`, and the conversion below is always in 0..6. The delegated functions
 *   still check, which is what makes that claim hold rather than being assumed.
 */
fun formatHariTanggal(date: LocalDate): String =
    "${date.dayOfMonth} ${date.dayOfWeek.shortLabel()}"

/**
 * Three-letter Indonesian weekday abbreviations, `Sen`..`Min` — the `23 Sen`
 * form from the epic context.
 *
 * ## The index convention, stated correctly
 *
 * Indexed **Monday is 1, Sunday is 0** — that is `DayOfWeek.value % 7`, where
 * `value` is ISO-8601's 1..7. It is *not* indexed by `DayOfWeek.ordinal`, whose
 * Monday is 0 and Sunday is 6; the two differ by one and a caller that mixed them
 * would get a plausible neighbouring day rather than an error. Every caller
 * converts through `value % 7` — see [DayOfWeek.shortLabel], which is the
 * conversion's single definition.
 *
 * @throws IllegalArgumentException on an out-of-range index.
 */
fun formatHariTanggal(dayOfWeekIndex: Int): String {
    require(dayOfWeekIndex in HARI_SINGKAT.indices) {
        "dayOfWeekIndex out of range: $dayOfWeekIndex"
    }
    return HARI_SINGKAT[dayOfWeekIndex]
}

/**
 * The one source of Indonesian weekday abbreviations in the app.
 *
 * ## Why this is derived rather than duplicated as string resources
 *
 * The rest-day chips and the contract row's `Libur Min` badge both render these
 * abbreviations, and so does every date this module formats. Keeping a second copy
 * in `strings.xml` would mean two lists of the same seven words, and the day the
 * two disagreed the chip and the date beside it would name different days.
 *
 * So the abbreviation for a [DayOfWeek] comes from the same table
 * [formatHariTanggal] indexes, through the same `value % 7` conversion — one
 * source, and no second spelling to drift.
 */
fun DayOfWeek.shortLabel(): String = formatHariTanggal(value % 7)

/** `Sen`, `Sel`, `Rab`, `Kam`, `Jum`, `Sab`, `Min`, indexed by `DayOfWeek`. */
private val HARI_SINGKAT = listOf(
    "Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab",
)

/**
 * Grouped digits without the `Rp` prefix — for the numpad's in-progress display,
 * where the prefix is a separate styled span.
 */
fun formatRupiahDigits(amount: Long): String = formatRupiah(amount, withPrefix = false)
