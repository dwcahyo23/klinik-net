package net.klinik.rental.ui

import net.klinik.rental.ui.format.formatHariTanggal
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.format.formatRupiahDigits
import net.klinik.rental.ui.format.formatTanggalBulan
import net.klinik.rental.ui.format.formatTanggalBulanTahun
import net.klinik.rental.ui.format.formatTanggalHari
import net.klinik.rental.ui.format.shortLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The money-formatting edge cases from the story's I/O matrix, plus the
 * boundaries that are easy to break and hard to notice.
 *
 * Money is `Long` rupiah. Every case here is one that would be visually wrong
 * rather than an exception, because a formatter that throws on `Long.MIN_VALUE`
 * takes down a screen while one that prints `Rp 9.223.372.036.854.775.808`
 * with a missing minus sign quietly misreports a balance.
 */
class RupiahFormatTest {

    // ---- FORMAT_MONEY: the two matrix rows ----

    @Test
    fun `250000 rupiah is grouped in threes`() {
        assertEquals("Rp 250.000", formatRupiah(250_000L))
    }

    @Test
    fun `1234567890 rupiah is grouped in threes with no decimals`() {
        assertEquals("Rp 1.234.567.890", formatRupiah(1_234_567_890L))
    }

    // ---- boundaries ----

    @Test
    fun `zero is Rp 0 and never Rp 0,00`() {
        assertEquals("Rp 0", formatRupiah(0L))
    }

    /**
     * Rupiah has no circulating subunit, so the output never carries a decimal
     * part. The Indonesian decimal comma is the character to watch for; the dot
     * is the thousands separator and is expected.
     */
    @Test
    fun `no input ever produces a decimal comma`() {
        val inputs = longArrayOf(
            0L, 1L, 9L, 10L, 99L, 100L, 999L, 1_000L, 999_999L, 1_000_000L,
            1_234_567L, 9_876_543_210L, Long.MAX_VALUE,
        )
        inputs.forEach { out ->
            val formatted = formatRupiah(out)
            assertFalse("'$formatted' must not contain a decimal comma", formatted.contains(','))
            assertTrue(
                "'$formatted' must start with 'Rp ' or '-Rp '",
                formatted.startsWith("Rp ") || formatted.startsWith("-Rp "),
            )
            // Matches what the test name promises. Asserting only `Rp ` here
            // would fail a correct negative result, and — worse — a regression
            // that dropped the sign would also fail it for the wrong reason,
            // hiding a real bug behind an assertion written for the other case.
            if (out < 0) {
                assertTrue(
                    "a negative amount must lead with its sign, got '$formatted'",
                    formatted.startsWith("-Rp "),
                )
            }
        }
    }

    @Test
    fun `the dot appears only as a thousands separator`() {
        // Every group after the first is exactly three digits, so no dot is ever
        // followed by anything other than a digit.
        val formatted = formatRupiah(1_234_567_890L).removePrefix("Rp ")
        formatted.split(".").drop(1).forEach { group ->
            assertEquals(3, group.length)
            assertTrue(group.all { it.isDigit() })
        }
    }

    @Test
    fun `each group is exactly three digits except the first`() {
        val formatted = formatRupiah(1_234_567_890L).removePrefix("Rp ")
        val groups = formatted.split(".")
        assertEquals(listOf("1", "234", "567", "890"), groups)
    }

    @Test
    fun `values below one thousand are not padded`() {
        assertEquals("Rp 7", formatRupiah(7L))
        assertEquals("Rp 42", formatRupiah(42L))
        assertEquals("Rp 420", formatRupiah(420L))
        assertEquals("Rp 1000".replace("1000", "1.000"), formatRupiah(1_000L))
    }

    @Test
    fun `a negative amount keeps its sign ahead of the prefix`() {
        // The sign leads: a trailing minus on a grouped figure reads as a
        // separator artefact rather than as a debt.
        assertEquals("-Rp 250.000", formatRupiah(-250_000L))
    }

    /**
     * `Long.MIN_VALUE` has no positive counterpart, so negating it overflows
     * back to itself. Naive `abs()` therefore silently drops the minus sign and
     * reports a positive balance for a negative debt — the worst failure this
     * function could have.
     */
    @Test
    fun `Long MIN_VALUE keeps its sign instead of overflowing`() {
        val formatted = formatRupiah(Long.MIN_VALUE)
        assertTrue("expected a leading minus, got '$formatted'", formatted.startsWith("-Rp "))
        assertEquals(
            "-Rp 9.223.372.036.854.775.808",
            formatted,
        )
    }

    @Test
    fun `Long MAX_VALUE formats without throwing`() {
        assertEquals("Rp 9.223.372.036.854.775.807", formatRupiah(Long.MAX_VALUE))
    }

    /** "Never throws" is an explicit matrix promise, so it is asserted, not assumed. */
    @Test
    fun `formatting never throws across a wide sweep`() {
        var v = Long.MIN_VALUE
        repeat(2_000) {
            formatRupiah(v)
            v += (Long.MAX_VALUE / 2_000)
        }
        formatRupiah(Long.MIN_VALUE)
        formatRupiah(Long.MAX_VALUE)
    }

    @Test
    fun `the prefix can be omitted for the numpad display`() {
        assertEquals("250.000", formatRupiahDigits(250_000L))
        assertEquals("0", formatRupiahDigits(0L))
    }

    @Test
    fun `formatting is idempotent in shape across magnitudes`() {
        // A cheap guard against a group-separator that drifts with magnitude.
        assertEquals("Rp 100", formatRupiah(100L))
        assertEquals("Rp 1.000", formatRupiah(1_000L))
        assertEquals("Rp 10.000", formatRupiah(10_000L))
        assertEquals("Rp 100.000", formatRupiah(100_000L))
        assertEquals("Rp 1.000.000", formatRupiah(1_000_000L))
    }

    // ---- the short date form the app speaks ----

    /**
     * The epic context writes dates as `23 Sen` / `24 Sel`, which are **weekday**
     * abbreviations, and separately expects a month somewhere. The two
     * conventions are kept as separate functions rather than merged, so this
     * asserts each on its own terms.
     */
    @Test
    fun `dates render as day plus a three letter Indonesian month`() {
        assertEquals("23 Jan", formatTanggalHari(23, 0))
        assertEquals("1 Des", formatTanggalHari(1, 11))
        assertEquals("9 Sep", formatTanggalHari(9, 8))
        assertEquals("31 Agu", formatTanggalHari(31, 7))
        assertEquals("5 Mei", formatTanggalHari(5, 4))
    }

    /** `Sen`/`Sel` are weekdays, zero-based on `java.time.DayOfWeek` ordinals. */
    @Test
    fun `weekdays render as the epic context's Sen and Sel`() {
        assertEquals("Sen", formatHariTanggal(1))
        assertEquals("Sel", formatHariTanggal(2))
        assertEquals("Min", formatHariTanggal(0))
        assertEquals("Sab", formatHariTanggal(6))
    }

    // ---- the LocalDate entry points, added by Story 2.5 ----

    /**
     * The contract list's date is the **month** form and carries the month.
     *
     * ## Why the month is the thing under test
     *
     * The list's job is telling one rental from another. A weekday alone cannot: two
     * rentals begun on the 23rd of different months both render `23 Jum`, which is
     * not a date the owner can act on. So `formatTanggalBulan` composes the month
     * form, and the pair below — same day, different months — is the case a weekday
     * format collapses into one string.
     */
    @Test
    fun `a LocalDate renders as day plus the Indonesian month`() {
        assertEquals("1 Okt", formatTanggalBulan(LocalDate.of(2026, 10, 1)))
        assertEquals("23 Okt", formatTanggalBulan(LocalDate.of(2026, 10, 23)))
        assertEquals("1 Des", formatTanggalBulan(LocalDate.of(2026, 12, 1)))
        assertEquals("9 Sep", formatTanggalBulan(LocalDate.of(2026, 9, 9)))
        assertEquals("31 Agu", formatTanggalBulan(LocalDate.of(2026, 8, 31)))
        assertEquals("5 Mei", formatTanggalBulan(LocalDate.of(2026, 5, 5)))
    }

    /**
     * And two rentals begun on the same day of different months are distinguishable —
     * the property the weekday form could not provide, and the reason the list uses
     * this one.
     */
    @Test
    fun `the same day of different months renders differently`() {
        val august = formatTanggalBulan(LocalDate.of(2026, 8, 23))
        val october = formatTanggalBulan(LocalDate.of(2026, 10, 23))

        assertEquals("23 Agu", august)
        assertEquals("23 Okt", october)
        assertFalse(
            "two rentals begun on the 23rd of different months must not collide",
            august == october,
        )
    }

    /**
     * January is the boundary of the month decomposition: the list is zero-based and
     * `Month` is one-based, so `monthValue - 1` is the only conversion, and an
     * off-by-one there would render every date one month early — or throw on
     * December, which indexes past the end.
     */
    @Test
    fun `January and December are the boundaries of the month decomposition`() {
        assertEquals("1 Jan", formatTanggalBulan(LocalDate.of(2026, 1, 1)))
        assertEquals("31 Des", formatTanggalBulan(LocalDate.of(2026, 12, 31)))
    }

    /**
     * The `LocalDate` weekday form — the renamed function, under the weekday name
     * rather than the month one.
     *
     * ## Why the `% 7` is the thing under test
     *
     * `formatHariTanggal` is indexed Monday-is-1 / Sunday-is-0, which is
     * `DayOfWeek.value % 7` — `value` is ISO-8601's 1..7 with `SUNDAY` as 7. Sunday is
     * therefore the one day the two numbering schemes disagree about, and a conversion
     * that forgot the wrap would index 7 into a seven-element list and throw. Every day
     * of a week is asserted, so a wrong conversion cannot pass by landing on a day that
     * happens to be right.
     */
    @Test
    fun `a LocalDate renders as day plus the Indonesian weekday`() {
        // 2026-10-01 is a Thursday.
        assertEquals("1 Kam", formatHariTanggal(LocalDate.of(2026, 10, 1)))
        assertEquals("2 Jum", formatHariTanggal(LocalDate.of(2026, 10, 2)))
        assertEquals("3 Sab", formatHariTanggal(LocalDate.of(2026, 10, 3)))
        assertEquals("4 Min", formatHariTanggal(LocalDate.of(2026, 10, 4)))
        assertEquals("5 Sen", formatHariTanggal(LocalDate.of(2026, 10, 5)))
        assertEquals("6 Sel", formatHariTanggal(LocalDate.of(2026, 10, 6)))
        assertEquals("7 Rab", formatHariTanggal(LocalDate.of(2026, 10, 7)))
    }

    /**
     * Sunday is the boundary of the two numbering schemes, so it is asserted against
     * a date far from the others as well — a conversion that wrapped the wrong way
     * could pass one week's worth of dates by accident.
     */
    @Test
    fun `Sunday is the day the two numbering schemes disagree about`() {
        // 2020-01-05 and 2026-10-04 are both Sundays.
        assertEquals("5 Min", formatHariTanggal(LocalDate.of(2020, 1, 5)))
        assertEquals("4 Min", formatHariTanggal(LocalDate.of(2026, 10, 4)))
    }

    /**
     * And the two `LocalDate` forms render **different** formats under different names
     * — the defect that made one name produce two strings.
     *
     * The same date is put through both, and the two outputs are asserted to differ.
     * A rename that accidentally left the weekday form under the month name, or the
     * reverse, fails here.
     */
    @Test
    fun `the two LocalDate forms differ and carry different information`() {
        val date = LocalDate.of(2026, 10, 23)

        assertEquals("23 Okt", formatTanggalBulan(date))
        assertEquals("23 Jum", formatHariTanggal(date))
        assertFalse(
            "the month form and the weekday form must not render the same string",
            formatTanggalBulan(date) == formatHariTanggal(date),
        )
    }

    /**
     * The weekday abbreviation for a `DayOfWeek`, which is what the rest-day chips and
     * the `Libur Min` badge render.
     */
    @Test
    fun `a DayOfWeek renders as its Indonesian abbreviation`() {
        assertEquals("Sen", DayOfWeek.MONDAY.shortLabel())
        assertEquals("Sel", DayOfWeek.TUESDAY.shortLabel())
        assertEquals("Rab", DayOfWeek.WEDNESDAY.shortLabel())
        assertEquals("Kam", DayOfWeek.THURSDAY.shortLabel())
        assertEquals("Jum", DayOfWeek.FRIDAY.shortLabel())
        assertEquals("Sab", DayOfWeek.SATURDAY.shortLabel())
        assertEquals("Min", DayOfWeek.SUNDAY.shortLabel())
    }

    /**
     * And the abbreviation agrees with the int-indexed table for every day — the
     * `value % 7` conversion is the only thing that could make them disagree.
     */
    @Test
    fun `the abbreviation agrees with the indexed table for every day`() {
        DayOfWeek.entries.forEach { day ->
            assertEquals(
                "shortLabel must be the same table, indexed the same way, for $day",
                formatHariTanggal(day.value % 7),
                day.shortLabel(),
            )
        }
    }

    /**
     * The year-qualified month form, which exists because the end-date refusal names the
     * day the app compares against while the field above it renders `date.toString()`.
     *
     * ## Why the year is the thing under test
     *
     * The refusal is only actionable if its figure matches the year-qualified date
     * sitting directly above it in the same sheet. `23 Okt` beside `2027-10-23` tells
     * the owner nothing about *which* October the app means — which is the whole reason
     * the refusal carries the day. So the year is asserted, and asserted on two dates
     * seven months apart, so a formatter that dropped it cannot pass on one.
     *
     * And it composes rather than re-derives: the `day + month` half must be exactly
     * what [formatTanggalBulan] produces, so a date can never be spelled two ways.
     */
    @Test
    fun `the year qualified form carries the year the day plus month form omits`() {
        assertEquals("17 Okt 2026", formatTanggalBulanTahun(LocalDate.of(2026, 10, 17)))
        assertEquals("23 Okt 2027", formatTanggalBulanTahun(LocalDate.of(2027, 10, 23)))
        assertEquals("1 Jan 2026", formatTanggalBulanTahun(LocalDate.of(2026, 1, 1)))
    }

    /**
     * The year is the only difference from its sibling, so the two must agree on
     * everything else — otherwise a caller could not tell which of the two a string
     * came from.
     */
    @Test
    fun `the year qualified form and the day plus month form agree except on the year`() {
        val date = LocalDate.of(2027, 10, 23)

        assertEquals(
            "the day and month half must be the sibling's output, not a second derivation",
            formatTanggalBulan(date),
            formatTanggalBulanTahun(date).removeSuffix(" ${date.year}"),
        )
    }

    /**
     * No `LocalDate` can produce an out-of-range index, so neither entry point throws.
     */
    @Test
    fun `every day of a year renders without throwing`() {
        var date = LocalDate.of(2026, 1, 1)
        while (date.year == 2026) {
            formatTanggalBulan(date)
            formatHariTanggal(date)
            date.dayOfWeek.shortLabel()
            date = date.plusDays(1)
        }
    }

    /**
     * No hidden defaults. An out-of-range month used to come back as the bare
     * day, which is the "silently degrade rather than fail" shape the epic
     * context prohibits — a caller with a malformed date would render `23` and
     * never learn why.
     */
    @Test
    fun `an out of range date throws rather than degrading`() {
        assertThrows(IllegalArgumentException::class.java) {
            formatTanggalHari(23, 12)
        }
        assertThrows(IllegalArgumentException::class.java) {
            formatTanggalHari(23, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            formatTanggalHari(0, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            formatTanggalHari(32, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            formatHariTanggal(7)
        }
    }
}