package net.klinik.rental.ui

import net.klinik.rental.ui.components.NUMPAD_MAX_DIGITS
import net.klinik.rental.ui.components.NumPadSaveResult
import net.klinik.rental.ui.components.appendDigit
import net.klinik.rental.ui.components.appendZeroPad
import net.klinik.rental.ui.components.deleteLastDigit
import net.klinik.rental.ui.components.numPadAmount
import net.klinik.rental.ui.components.parseNumPadSave
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The numpad's two load-bearing acceptance criteria, asserted on the pure
 * functions rather than through the composable.
 *
 * Putting these rules in [net.klinik.rental.ui.components.NumPadLogic] instead
 * of inside the composable is what makes them checkable at all: a rule embedded
 * in a UI can only be tested by rendering and clicking, and this project has no
 * instrumentation run in its verification command.
 */
class NumPadLogicTest {

    // ---- NUMPAD_ENTRY: the 12-digit cap ----

    @Test
    fun `the thirteenth digit is ignored`() {
        var digits = ""
        repeat(NUMPAD_MAX_DIGITS) { i ->
            digits = appendDigit(digits, '7')
            assertEquals("${i + 1} digits after $i taps", i + 1, digits.length)
        }
        // The 13th tap.
        digits = appendDigit(digits, '7')
        assertEquals("display must still hold 12 digits", NUMPAD_MAX_DIGITS, digits.length)
    }

    @Test
    fun `the cap holds for a realistic daily-rate amount`() {
        // Rp 123.456.789 — nine digits, comfortably inside the cap.
        var digits = ""
        "123456789".forEach { digits = appendDigit(digits, it) }
        assertEquals("123456789", digits)

        // Three more digits reach exactly 12.
        digits = appendDigit(digits, '0')
        assertEquals("1234567890", digits)
        digits = appendDigit(digits, '1')
        assertEquals("12345678901", digits)
        digits = appendDigit(digits, '2')
        assertEquals("123456789012", digits)
        assertEquals(NUMPAD_MAX_DIGITS, digits.length)

        // The 13th is dropped.
        digits = appendDigit(digits, '3')
        assertEquals("123456789012", digits)
    }

    /**
     * The cap counts *significant* digits, because leading zeros are stripped on
     * entry. Twelve zeros are not "twelve digits" — the display never shows a
     * padded number, so an all-zero run has to stay at one digit or the owner
     * would be counting spaces to find out how far along they are.
     */
    @Test
    fun `leading zeros do not consume the cap`() {
        var digits = ""
        repeat(30) { digits = appendDigit(digits, '0') }
        assertEquals("all-zero input stays one digit", "0", digits)

        digits = appendDigit(digits, '7')
        assertEquals("a real digit after zeros still fits", "7", digits)
    }

    @Test
    fun `a leading zero is not padded onto the display`() {
        assertEquals("5", appendDigit("", '5'))
        // Typing 0 first then 5 reads as 5, not 05.
        assertEquals("5", appendDigit(appendDigit("", '0'), '5'))
        assertEquals("0", appendDigit("", '0'))
    }

    @Test
    fun `deleting and retyping walks back through the cap`() {
        var digits = "123456789012"
        digits = deleteLastDigit(digits)
        assertEquals("12345678901", digits)
        digits = appendDigit(digits, '3')
        assertEquals("123456789013", digits)
    }

    @Test
    fun `deleting an empty display stays empty`() {
        assertEquals("", deleteLastDigit(""))
    }

    @Test
    fun `the zero pad key respects the same cap`() {
        var digits = appendDigit("", '1')
        digits = appendZeroPad(digits)
        assertEquals("1000", digits)
        assertEquals(4, digits.length)
    }

    @Test
    fun `the zero pad key cannot overshoot the cap`() {
        var digits = "12345678901"
        digits = appendZeroPad(digits)
        assertEquals(NUMPAD_MAX_DIGITS, digits.length)
    }

    @Test
    fun `the zero pad key on a full display is a no-op`() {
        val full = "123456789012"
        assertEquals(full, appendZeroPad(full))
    }

    // ---- NUMPAD_SAVE_ZERO: reject loudly, never silently default ----

    @Test
    fun `a positive amount is accepted with its value`() {
        assertEquals(
            NumPadSaveResult.Accepted(250_000L),
            parseNumPadSave("250000"),
        )
    }

    @Test
    fun `zero is rejected`() {
        assertEquals(NumPadSaveResult.Rejected, parseNumPadSave("0"))
        assertEquals(NumPadSaveResult.Rejected, parseNumPadSave("000"))
    }

    @Test
    fun `an empty display is rejected rather than treated as zero`() {
        assertEquals(NumPadSaveResult.Rejected, parseNumPadSave(""))
    }

    @Test
    fun `a value that cannot parse is rejected`() {
        assertEquals(NumPadSaveResult.Rejected, parseNumPadSave("abc"))
    }

    /**
     * The type has no "accepted with a fallback" case at all, which is the
     * point: a screen cannot write `when (r) { Accepted -> ..., else -> 0 }` and
     * quietly accept a zero rate.
     */
    @Test
    fun `there is no accepted-but-adjusted outcome`() {
        val result = parseNumPadSave("50000")
        val accepted = result as NumPadSaveResult.Accepted
        assertEquals(50_000L, accepted.amount)
    }

    // ---- the display value ----

    @Test
    fun `the displayed amount is zero for empty input`() {
        assertEquals(0L, numPadAmount(""))
    }

    @Test
    fun `the displayed amount matches the digits`() {
        assertEquals(1_250_000L, numPadAmount("1250000"))
    }
}