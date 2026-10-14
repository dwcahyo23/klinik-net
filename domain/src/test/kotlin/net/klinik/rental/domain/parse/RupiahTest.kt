package net.klinik.rental.domain.parse

import net.klinik.rental.domain.validation.InvalidAmountException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The `PARSE_RATE` rows of Story 2.2's I/O matrix, plus the boundary cases the
 * matrix implies.
 *
 * Runs in the pure-JVM module: `./gradlew :domain:test` needs no Android SDK,
 * which is what makes NFR-6's "the single parse path" cheap to hold to.
 */
class RupiahTest {

    // ---------------------------------------------------------- PARSE_RATE

    /** The one row that returns a value. Everything else returns null. */
    @Test
    fun `a plain whole-rupiah amount parses`() {
        assertEquals(250_000L, parseRupiahInput("250000"))
    }

    @Test
    fun `zero parses to nothing because zero is not a rate`() {
        assertNull(parseRupiahInput("0"))
    }

    @Test
    fun `a negative amount is refused`() {
        assertNull(parseRupiahInput("-1"))
    }

    @Test
    fun `letters are refused`() {
        assertNull(parseRupiahInput("abc"))
    }

    @Test
    fun `an empty string is refused`() {
        assertNull(parseRupiahInput(""))
    }

    /**
     * A dot means the caller is thinking in sen or thousands. `250.000` is 250
     * thousand rupiah, not two-and-a-half, so accepting it would silently
     * reinterpret the owner's figure.
     */
    @Test
    fun `a decimal point is refused rather than truncated`() {
        assertNull(parseRupiahInput("2.5"))
    }

    /** One rupiah is the floor — the smallest amount the system can express. */
    @Test
    fun `one rupiah is the smallest amount that parses`() {
        assertEquals(1L, parseRupiahInput("1"))
    }

    /**
     * The prototype's `parseInt(x) || 250000` turned every one of these into
     * Rp 250.000. Asserted as a set so the specific row that regresses names
     * itself.
     */
    @Test
    fun `no bad input silently becomes a fallback figure`() {
        listOf("0", "-1", "abc", "", "2.5", "  ", "250000,-", "Rp 250000")
            .forEach { input ->
                assertNull("'$input' must not parse to a value", parseRupiahInput(input))
            }
    }

    // ------------------------------------------------ tolerated separators

    /**
     * Spaces are grouping, not value. An edit form restoring stored text and a
     * paste of `250 000` must both produce the same number as `250000`.
     */
    @Test
    fun `spaces between digits are treated as grouping`() {
        assertEquals(250_000L, parseRupiahInput("250 000"))
        assertEquals(250_000L, parseRupiahInput("  250   000  "))
    }

    /** A leading or trailing space is whitespace, not an error. */
    @Test
    fun `surrounding whitespace does not change the value`() {
        assertEquals(250_000L, parseRupiahInput("  250000  "))
    }

    /** A non-breaking space from a soft keyboard is still whitespace. */
    @Test
    fun `a non-breaking space is whitespace`() {
        assertEquals(250_000L, parseRupiahInput("250 000"))
    }

    /**
     * Overflow must refuse rather than throw. The numpad caps at 12 digits, but
     * an edit form can restore anything already stored, and a caller has no
     * reason to expect `NumberFormatException` from a function documented to
     * return `Long?`.
     */
    @Test
    fun `more digits than a Long holds is refused, not thrown`() {
        assertNull(parseRupiahInput("9".repeat(25)))
        assertNull(parseRupiahInput("99999999999999999999"))
    }

    /** Leading zeros are digits; they carry no value and must not refuse. */
    @Test
    fun `leading zeros are accepted as the value they represent`() {
        assertEquals(250_000L, parseRupiahInput("000250000"))
        assertNull(parseRupiahInput("000"))
    }

    // -------------------------------------------------- requirePositiveRate

    @Test
    fun `requirePositiveRate returns a valid amount unchanged`() {
        assertEquals(250_000L, requirePositiveRate(250_000L))
    }

    @Test
    fun `requirePositiveRate rejects zero`() {
        assertThrows(InvalidAmountException::class.java) { requirePositiveRate(0L) }
    }

    @Test
    fun `requirePositiveRate rejects a negative amount`() {
        assertThrows(InvalidAmountException::class.java) { requirePositiveRate(-1L) }
    }

    /**
     * The two halves of NFR-6 have to agree, or a screen can pass one check and
     * be refused by the other: `parseRupiahInput` returning null and
     * `requirePositiveRate` throwing are two expressions of the same bound, so
     * everything the parser accepts must survive the validator.
     */
    @Test
    fun `the parser and the validator agree on the boundary`() {
        listOf("1", "250000", "999999999999").forEach { input ->
            val parsed = parseRupiahInput(input)
            assertEquals("'$input' must satisfy both halves", parsed, requirePositiveRate(parsed!!))
        }
        // And both refuse the same thing: "0" parses to null precisely because
        // `requirePositiveRate(0)` throws.
        assertNull(parseRupiahInput("0"))
        assertThrows(InvalidAmountException::class.java) { requirePositiveRate(0L) }
    }
}