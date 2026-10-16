package net.klinik.rental.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Construction rules for a `ServiceException`: both ids and the reason are
 * non-blank.
 *
 * The interesting property is *where* the refusal happens — on construction, so an
 * invalid exception cannot exist in memory, let alone reach the database. That is
 * the same line [ContractTest] and [VehicleTest] hold for the ids and the rate, and
 * it is the half of the rule that needs no Android SDK.
 *
 * The *duplicate per `(contractId, date)`* rule cannot be asserted here at all: it
 * is a `UNIQUE` index on `SERVICE_EXCEPTION`, and only a real database can refuse
 * it. That half is `ContractRepositoryTest`'s `EXCEPTION_DUPLICATE_INDEX` row,
 * which deliberately bypasses the pre-check to prove it.
 */
class ServiceExceptionTest {

    @Test
    fun `a valid exception constructs`() {
        val exception = exception()
        assertEquals("exception-1", exception.id)
        assertEquals("contract-1", exception.contractId)
        assertEquals(LocalDate.of(2026, 10, 14), exception.date)
        assertEquals("Ganti ban, mobil di bengkel", exception.reason)
    }

    // ---------------------------------------------------------------- the ids

    /** A blank id is a primary key that collides with every other blank id. */
    @Test
    fun `a blank id is refused`() {
        assertThrows(IllegalArgumentException::class.java) { exception(id = "") }
        assertThrows(IllegalArgumentException::class.java) { exception(id = "   ") }
    }

    /** A blank `contractId` is a foreign key pointing at nothing in particular. */
    @Test
    fun `a blank contract id is refused`() {
        assertThrows(IllegalArgumentException::class.java) { exception(contractId = "") }
        assertThrows(IllegalArgumentException::class.java) { exception(contractId = "   ") }
    }

    // -------------------------------------------------------------- the reason

    /**
     * A blank reason is refused, because the acceptance criterion requires the
     * exception to be displayed *with its reason* — a row with a blank one has
     * nothing to display, and nothing in this story edits a stored reason, so the
     * gap would be permanent.
     */
    @Test
    fun `a blank reason is refused`() {
        assertThrows(IllegalArgumentException::class.java) { exception(reason = "") }
        assertThrows(IllegalArgumentException::class.java) { exception(reason = "   ") }
    }

    /**
     * And the reason is kept verbatim, not trimmed or normalised away.
     *
     * It is owner-visible text in their own words, so the only rule is that it is
     * not blank; a `trim()` on the way in would make what is stored differ from
     * what was typed, and the stored string is what the sheet reads back.
     */
    @Test
    fun `the reason is stored verbatim`() {
        assertEquals("  Ganti ban  ", exception(reason = "  Ganti ban  ").reason)
    }

    // -------------------------------------------------- out of scope, forever

    /**
     * No `status`. "Is this contract still open?" is asked through
     * `Contract.isOpen`, and a copy of that fact stored here is a second source of
     * truth that drifts the moment a contract ends without anyone updating the
     * exception rows — and the drift would be *silent*, because nothing reads it.
     */
    @Test
    fun `the model exposes no stored status field`() {
        val declared = ServiceException::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("status must be derived, never stored", declared.contains("status"))
    }

    /**
     * No `periodIndex` and no `offset` — the two encodings a "does not roll into
     * the next period" row could have been given instead of an absolute date.
     *
     * This is the assertion that holds AC3 in place. An offset-based row would
     * pass every other test in this file and every behavioural test too, right up
     * until a month rolled and the stored offset silently re-applied to whichever
     * day landed at that index — marking a different day than the owner chose. The
     * class has four fields and they are named above; this states that the other
     * two shapes are not here.
     */
    @Test
    fun `the model stores an absolute date and no period offset`() {
        val declared = ServiceException::class.java.declaredFields.map { it.name }.toSet()
        assertFalse(
            "a period index would re-apply to a different day next period",
            declared.contains("periodIndex"),
        )
        assertFalse(
            "a day offset would re-apply to a different day next period",
            declared.contains("offset"),
        )
    }

    /**
     * And no `vehicleId`: exceptions are **per Contract, never per Vehicle**.
     *
     * One vehicle can have several contracts with different exceptions, so a
     * vehicle column here would be a second owner for the same row — and two
     * encodings of "who does this exception belong to" can disagree.
     */
    @Test
    fun `the exception belongs to a contract and not to a vehicle`() {
        val declared = ServiceException::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("the owner column is `contractId` alone", declared.contains("vehicleId"))
        assertTrue("and it is there", declared.contains("contractId"))
    }

    // ------------------------------------------------------------------ helper

    private fun exception(
        id: String = "exception-1",
        contractId: String = "contract-1",
        date: LocalDate = LocalDate.of(2026, 10, 14),
        reason: String = "Ganti ban, mobil di bengkel",
    ) = ServiceException(
        id = id,
        contractId = contractId,
        date = date,
        reason = reason,
    )
}
