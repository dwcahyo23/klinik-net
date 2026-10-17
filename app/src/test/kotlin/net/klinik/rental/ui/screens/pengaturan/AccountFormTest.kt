package net.klinik.rental.ui.screens.pengaturan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure `AccountFormState` rules, with no Compose, no device and no database.
 *
 * Every function here is a top-level `copy()` over a data class, and all of them are
 * observable through the sheet's rendering. Testing them through the sheet costs a Robolectric
 * device per assertion and cannot distinguish "the rule is wrong" from "the sheet did not wire
 * it". So the rules are pinned here, where a failure points at a function, and the sheet test
 * covers the wiring.
 */
class AccountFormTest {

    // ------------------------------------------------- the missing-field order

    /**
     * The bank name is checked first, so a form with neither answered is refused on the field
     * the owner sees first.
     *
     * The order is the order the fields appear on the sheet, and it matches the repository's
     * own order — a form that refused the number while the bank was empty would report a fault
     * the owner has to fix second.
     */
    @Test
    fun `a form missing both is missing its bank name first`() {
        assertEquals(AccountField.BankName, form().firstMissingAccountField())
    }

    /** A bank name alone still leaves the number. */
    @Test
    fun `a bank name alone leaves the number missing`() {
        assertEquals(
            AccountField.Number,
            form().setBankName("BCA").firstMissingAccountField(),
        )
    }

    /** Both answered, and the form is complete. */
    @Test
    fun `a complete form is missing nothing`() {
        assertNull(form().setBankName("BCA").setNumber("1234567890").firstMissingAccountField())
    }

    /**
     * Whitespace counts as blank for both fields.
     *
     * The model refuses `isBlank()`, so a field of spaces would fail below the screen with a
     * message about a field the owner never knew was wrong. `isBlank` is the same test the
     * model applies, which is what makes the two halves one rule.
     */
    @Test
    fun `whitespace-only fields are still missing`() {
        assertEquals(AccountField.BankName, form().setBankName("   ").firstMissingAccountField())
        assertEquals(
            AccountField.Number,
            form().setBankName("BCA").setNumber("   ").firstMissingAccountField(),
        )
    }

    // ---------------------------------------------------------- error clearing

    /**
     * Editing a bank name clears **its own** error and only its own.
     *
     * The edit *is* the fix, so an error surviving it would accuse the owner of a field they
     * have already filled in. The number's error is untouched because the number is still
     * blank — clearing it too would erase a real refusal.
     */
    @Test
    fun `editing the bank name clears the bank error and keeps the number error`() {
        val refused = form().copy(bankError = "Isi nama banknya.", numberError = "Isi nomornya.")
        val after = refused.setBankName("BCA")

        assertNull(after.bankError)
        assertEquals("Isi nomornya.", after.numberError)
    }

    /** And symmetrically for the number. */
    @Test
    fun `editing the number clears the number error and keeps the bank error`() {
        val refused = form().copy(bankError = "Isi nama banknya.", numberError = "Isi nomornya.")
        val after = refused.setNumber("123")

        assertNull(after.numberError)
        assertEquals("Isi nama banknya.", after.bankError)
    }

    // -------------------------------------------------------------- canSave

    /**
     * `canSaveAccount` is completeness **only** — a malformed number leaves it `true`.
     *
     * This is the deliberate asymmetry worth pinning: a disabled button beside a filled field
     * would leave the owner with no explanation, while an enabled button that produces a
     * refusal naming the format is actionable. The repository is what refuses the format.
     */
    @Test
    fun `a filled but malformed number still allows save`() {
        val malformed = form().setBankName("BCA").setNumber("12ab34")

        assertTrue("completeness is the only rule here", malformed.canSaveAccount)
        assertNull("and the format is not this function's business", malformed.firstMissingAccountField())
    }

    /** And an incomplete form does not. */
    @Test
    fun `an incomplete form cannot save`() {
        assertFalse(form().canSaveAccount)
        assertFalse(form().setBankName("BCA").canSaveAccount)
    }

    // ------------------------------------------------------ toBusinessAccount

    /**
     * A complete form builds the account, and a **new** form takes the id it was given.
     *
     * The id is passed in rather than generated here, which keeps this function pure and lets
     * a test pin the mapping.
     */
    @Test
    fun `a complete new form builds the account with the id it was given`() {
        val account = form().setBankName("BCA").setNumber("1234567890")
            .toBusinessAccount("a-new")

        assertEquals("a-new", account!!.id)
        assertEquals("BCA", account.bankName)
        assertEquals("1234567890", account.number)
    }

    /**
     * An **edit** keeps its own id, and the passed-in one is ignored.
     *
     * This is what makes a save an update rather than a second insert: the id travels with the
     * form, so the repository finds the stored row and updates it. A form that took the fresh
     * id here would insert a duplicate account for one bank.
     */
    @Test
    fun `an edit form keeps its own id and ignores the generated one`() {
        val editing = AccountFormState(id = "a-existing", bankName = "BCA", number = "111")

        val account = editing.toBusinessAccount("a-generated")

        assertEquals("the stored id wins", "a-existing", account!!.id)
    }

    /** An incomplete form builds nothing rather than throwing. */
    @Test
    fun `an incomplete form builds nothing`() {
        assertNull(form().toBusinessAccount("a1"))
        assertNull(form().setBankName("BCA").toBusinessAccount("a1"))
    }

    /**
     * The values are **not trimmed** here.
     *
     * Trimming is the repository's job, on the way to storage. Doing it in both places means the
     * value the owner sees in the field and the value that is stored can differ in a way neither
     * can explain.
     */
    @Test
    fun `the form's values are passed through untrimmed`() {
        val account = form().setBankName("  BCA  ").setNumber(" 123 ").toBusinessAccount("a1")

        assertEquals("  BCA  ", account!!.bankName)
        assertEquals(" 123 ", account.number)
    }

    // ------------------------------------------------------------------ isEditing

    /**
     * `isEditing` is derived from the id, never stored.
     *
     * A second boolean would be able to disagree with the id, and the disagreement would show as
     * an edit form that inserts a duplicate — the same reasoning `RenterFormState.isEditing`
     * gives.
     */
    @Test
    fun `isEditing follows the id`() {
        assertFalse(form().isEditing)
        assertTrue(form().copy(id = "a1").isEditing)
    }

    private fun form() = AccountFormState()
}
