package net.klinik.rental.ui.screens.kontrak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The pure `ExceptionFormState` rules, with no Compose, no device and no database.
 *
 * ## Why this is a separate file rather than more cases in `ExceptionFormSheetTest`
 *
 * Every function here is a top-level `copy()` over a data class, and all of them are
 * observable through the sheet's rendering. Testing them through the sheet costs a
 * Robolectric device and a `waitForIdle()` per assertion, and it cannot distinguish
 * "the rule is wrong" from "the sheet did not wire it". So the rules are pinned here,
 * where a failure points at a function, and the sheet test covers the wiring.
 *
 * The rendering rules themselves — what is *absent* for a read-only contract, and that
 * Add is disabled rather than merely refused — live in `ExceptionFormSheetTest`, where
 * "absent" is something you can actually observe.
 */
class ExceptionFormTest {

    // ------------------------------------------------- the missing-field order

    /**
     * The date is checked first, so a form with neither answered is refused on the
     * field the owner sees first.
     *
     * The order is the order the fields appear on the sheet, and it is the *only* thing
     * that distinguishes the two branches — a form missing both returns `Date` either
     * way, so this assertion is what pins the order rather than the existence of both.
     */
    @Test
    fun `a form missing both is missing its date first`() {
        assertEquals(ExceptionField.Date, sheet().firstMissingExceptionField())
    }

    /** A date alone still leaves the reason, and only the reason. */
    @Test
    fun `a date alone leaves the reason missing`() {
        assertEquals(
            ExceptionField.Reason,
            sheet().selectExceptionDate(MARKED_DAY).firstMissingExceptionField(),
        )
    }

    /** Both answered, and the form is complete. */
    @Test
    fun `a complete form is missing nothing`() {
        val complete = sheet().selectExceptionDate(MARKED_DAY).setExceptionReason("Ganti ban")
        assertNull(complete.firstMissingExceptionField())
    }

    /**
     * A **blank** reason counts as missing, and whitespace counts as blank.
     *
     * `" "` rather than `""` on purpose: `ServiceException` refuses `isNotBlank()`, so a
     * reason of spaces would fail at the data layer with a message about the field the
     * owner never knew was wrong. `isBlank` is the same test the domain model applies,
     * which is what makes the two halves one rule rather than two.
     */
    @Test
    fun `a blank or whitespace-only reason is still missing`() {
        val dated = sheet().selectExceptionDate(MARKED_DAY)
        assertEquals(ExceptionField.Reason, dated.setExceptionReason("").firstMissingExceptionField())
        assertEquals(ExceptionField.Reason, dated.setExceptionReason("   ").firstMissingExceptionField())
        assertFalse(dated.setExceptionReason("   ").canAddException)
    }

    // ---------------------------------------------------------- error clearing

    /**
     * Choosing a date clears **its own** error and only its own.
     *
     * The date selection *is* the fix, so an error that survived it would accuse the
     * owner of a field they have already filled in. The reason's error is untouched
     * because the reason is still blank — clearing it too would erase a real refusal.
     */
    @Test
    fun `choosing a date clears the date error and keeps the reason error`() {
        val refused = sheet().copy(dateError = "Tanggal itu sudah ditandai", reasonError = "Alasan wajib")
        val afterDate = refused.selectExceptionDate(MARKED_DAY)

        assertNull(afterDate.dateError)
        assertEquals("Alasan wajib", afterDate.reasonError)
    }

    /** And symmetrically for the reason. */
    @Test
    fun `typing a reason clears the reason error and keeps the date error`() {
        val refused = sheet().copy(dateError = "Tanggal itu sudah ditandai", reasonError = "Alasan wajib")
        val afterReason = refused.setExceptionReason("Ganti ban")

        assertNull(afterReason.reasonError)
        assertEquals("Tanggal itu sudah ditandai", afterReason.dateError)
    }

    // ------------------------------------------------------------- canAdd

    /**
     * `canAddException` is `false` for a read-only sheet **even when the form is
     * complete**.
     *
     * Both halves matter, and the second is the one an implementation gets wrong for
     * free: `canAddException` is read in the state where the button is not composed at
     * all, so a version that only checked completeness would answer `true` there and
     * be "correct" for a control nobody can see.
     */
    @Test
    fun `a read-only sheet can never add`() {
        val complete = sheet(readOnly = true)
            .selectExceptionDate(MARKED_DAY)
            .setExceptionReason("Ganti ban")

        assertNull("the form itself is complete", complete.firstMissingExceptionField())
        assertFalse("and a read-only sheet still refuses", complete.canAddException)
    }

    /** The plain complete-form case, so the test above has something to be different from. */
    @Test
    fun `a complete open sheet can add`() {
        assertTrue(
            sheet().selectExceptionDate(MARKED_DAY).setExceptionReason("Ganti ban")
                .canAddException,
        )
    }

    // ------------------------------------------------- toServiceException

    /**
     * The id is **passed in**, not generated — which is what keeps this function pure
     * and lets a test pin the mapping.
     */
    @Test
    fun `a complete form builds the exception with the id it was given`() {
        val exception = sheet().selectExceptionDate(MARKED_DAY)
            .setExceptionReason("Ganti ban")
            .toServiceException("exception-1")

        assertEquals("exception-1", exception!!.id)
        assertEquals(CONTRACT_ID, exception.contractId)
        assertEquals(MARKED_DAY, exception.date)
        assertEquals("Ganti ban", exception.reason)
    }

    /**
     * An incomplete form builds `null` rather than throwing.
     *
     * `null` is the whole point of the KDoc's reasoning: an incomplete form is a state
     * the caller already knows how to report — `firstMissingExceptionField` names it —
     * so a throw here would collapse "you have not picked a day" into the same path as
     * a genuinely unexpected failure, and the owner would read the generic message.
     */
    @Test
    fun `an incomplete form builds nothing`() {
        assertNull("no date at all", sheet().toServiceException("exception-1"))
        assertNull(
            "a date but no reason",
            sheet().selectExceptionDate(MARKED_DAY).toServiceException("exception-1"),
        )
        assertNull(
            "a whitespace-only reason is still no reason",
            sheet().selectExceptionDate(MARKED_DAY).setExceptionReason("  ")
                .toServiceException("exception-1"),
        )
    }

    /**
     * The reason is stored **verbatim**, not trimmed.
     *
     * It is owner-visible text in their own words, and what is stored is what the sheet
     * reads back on the next emission. Trimming here would mean the row shows a
     * different string from the one the owner just typed, with nothing saying so.
     */
    @Test
    fun `the reason is stored verbatim`() {
        val exception = sheet().selectExceptionDate(MARKED_DAY)
            .setExceptionReason("  ganti ban, mobil di bengkel  ")
            .toServiceException("exception-1")

        assertEquals("  ganti ban, mobil di bengkel  ", exception!!.reason)
    }

    /**
     * A read-only sheet still **builds** an exception — read-only governs the write, not
     * the projection.
     *
     * Pinned because the two rules are independent and it is easy to conflate them: the
     * sheet refuses to *offer* the add, and the repository refuses the write. Neither
     * is this function's business, and a version that returned `null` for a read-only
     * sheet would hide the real refusal behind an unrelated one.
     */
    @Test
    fun `a read-only sheet still projects an exception`() {
        val exception = sheet(readOnly = true)
            .selectExceptionDate(MARKED_DAY)
            .setExceptionReason("Ganti ban")
            .toServiceException("exception-1")

        assertEquals(MARKED_DAY, exception!!.date)
    }

    // ------------------------------------------------------------------ fixture

    private fun sheet(readOnly: Boolean = false) = ExceptionFormState(
        contractId = CONTRACT_ID,
        contractLabel = "Budi Santoso · B 1235 TF",
        isReadOnly = readOnly,
    )

    private companion object {
        const val CONTRACT_ID = "c1"
        val MARKED_DAY: LocalDate = LocalDate.of(2026, 10, 14)
    }
}