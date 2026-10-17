package net.klinik.rental.domain.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The account-number normaliser.
 *
 * ## Why this file is mostly about what is *accepted*
 *
 * The tempting way to write this test is a list of rejected inputs, because refusing bad
 * input feels like the interesting half. It is the wrong emphasis here: the acceptance
 * criterion is that validation is format-only and that *nothing changes when the bank's
 * records disagree*, so the failure this file exists to catch is an implementation that
 * became **too strict** — one that refuses a real account because it expected a particular
 * length, prefix or checksum. So the accepted cases carry the weight, and the length bound
 * is tested from both sides so a future tightening shows up as a failure rather than as a
 * refused account on someone's device.
 */
class AccountNumberTest {

    // ------------------------------------------------------------ the separators

    /**
     * The three spellings of one account all normalise to one value.
     *
     * This is the claim the whole function exists for. The owner's banking app writes
     * `1234 5678`, their statement writes `1234-5678`, their bank's website writes
     * `12345678` — and if these stayed distinct, the same account would appear in their
     * list three times with no way to tell which row is current.
     */
    @Test
    fun `spaces, dashes and neither all produce one value`() {
        val expected = "12345678"
        assertEquals(expected, normalizeAccountNumber("1234 5678"))
        assertEquals(expected, normalizeAccountNumber("1234-5678"))
        assertEquals(expected, normalizeAccountNumber("12345678"))
    }

    /**
     * Mixed and repeated separators collapse too.
     *
     * A paste from a statement can carry `1234 - 5678` or `12-34-56-78`, and the owner
     * has no control over which. Every one of them is the same account.
     */
    @Test
    fun `mixed and repeated separators collapse`() {
        val expected = "12345678"
        assertEquals(expected, normalizeAccountNumber("1234 - 5678"))
        assertEquals(expected, normalizeAccountNumber("12-34-56-78"))
        assertEquals(expected, normalizeAccountNumber("12 34  56   78"))
        assertEquals(expected, normalizeAccountNumber("  1234 5678  "))
    }

    /**
     * A non-breaking space is stripped as well as an ordinary one.
     *
     * Android soft keyboards produce non-breaking spaces by accident, and a banking app's
     * paste can carry one. `trim()` does not remove one in the middle of a string, so an
     * implementation that only handled `' '` would store a number with an invisible
     * character in it — and the owner would see two identical-looking accounts.
     */
    @Test
    fun `a non-breaking space is stripped like an ordinary one`() {
        assertEquals("12345678", normalizeAccountNumber("1234 5678"))
    }

    // -------------------------------------------------------------- the refusals

    /** Blank and whitespace-only have no normalised form. */
    @Test
    fun `blank input is refused`() {
        assertNull(normalizeAccountNumber(""))
        assertNull(normalizeAccountNumber("   "))
        assertNull(normalizeAccountNumber("---"))
        assertNull(normalizeAccountNumber("   - -  "))
    }

    /**
     * A letter is a **refusal**, not something to strip.
     *
     * The alternative — filtering to digits and storing the rest — would record an account
     * the owner did not type. `12ab34` is not an account number; it is a paste of the wrong
     * thing, and the owner has to be told rather than have half of it kept.
     */
    @Test
    fun `anything that is not a digit is refused rather than stripped`() {
        assertNull(normalizeAccountNumber("12ab34"))
        assertNull(normalizeAccountNumber("1234#5678"))
        assertNull(normalizeAccountNumber("12.34.56"))
        assertNull(normalizeAccountNumber("1234/5678"))
    }

    /**
     * A decimal figure is refused, not truncated.
     *
     * `12.34` is what a *balance* looks like, and a paste that grabbed the balance instead
     * of the account number must fail loudly. Storing `1234` would be an account the owner
     * never chose, and it would look plausible.
     */
    @Test
    fun `a decimal figure is refused`() {
        assertNull(normalizeAccountNumber("1234.56"))
        assertNull(normalizeAccountNumber("1,234,567"))
    }

    // --------------------------------------------------------- the length bound

    /**
     * The bound is generous, and this is the test that keeps it that way.
     *
     * A 16-digit account is real and must be accepted. The bound exists as a *paste guard*
     * — see the function's KDoc — and a future reader who tightens it into a per-bank rule
     * breaks the acceptance criterion that validation is format-only. This assertion is
     * the tripwire.
     */
    @Test
    fun `a long but plausible account number is accepted`() {
        val sixteen = "1234567890123456"
        assertEquals(sixteen, normalizeAccountNumber(sixteen))
        // And a 30-digit one, which no bank issues but which is still within the guard.
        val thirty = "1".repeat(30)
        assertEquals(thirty, normalizeAccountNumber(thirty))
    }

    /**
     * And past the bound it is refused — the case the bound exists for.
     *
     * A paste of a whole statement line carries a date, a reference number and an amount
     * alongside the account. Without the bound that lands silently as a very long "account
     * number", and the owner sees a row that looks like data.
     */
    @Test
    fun `a paste longer than the bound is refused`() {
        assertNull(normalizeAccountNumber("1".repeat(MAX_ACCOUNT_NUMBER_DIGITS + 1)))
        assertNull(normalizeAccountNumber("2026-10-17 REF 998877 1234567890 5000000"))
    }

    /** The bound itself is inclusive, so the boundary is not off by one. */
    @Test
    fun `the bound itself is accepted`() {
        val atBound = "1".repeat(MAX_ACCOUNT_NUMBER_DIGITS)
        assertEquals(atBound, normalizeAccountNumber(atBound))
    }
}
