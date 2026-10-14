package net.klinik.rental.ui

import net.klinik.rental.ui.format.formatHariTanggal
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.format.formatRupiahDigits
import net.klinik.rental.ui.format.formatTanggalHari
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

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