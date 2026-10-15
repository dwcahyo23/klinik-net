package net.klinik.rental.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Construction refuses a blank or whitespace-only name — the rule that makes a
 * `RENTER` row findable again.
 *
 * The interesting property is *where* the refusal happens: on construction, so
 * an unnamed renter cannot exist in memory, let alone reach the database. This
 * is the same line [VehicleTest] holds for the plate.
 *
 * The uniqueness half of the rule cannot be asserted here at all — it is a
 * `UNIQUE` index, and only a real database can refuse it. That half is
 * `RenterRepositoryTest` in the app module; this file is the pure-JVM half that
 * needs no Android SDK.
 */
class RenterTest {

    @Test
    fun `a valid renter constructs`() {
        val renter = renter(name = "Budi Santoso")
        assertEquals("Budi Santoso", renter.name)
        assertEquals("08123456789", renter.phone)
        assertEquals("08129876543", renter.emergencyContact)
    }

    // ------------------------------------------------------------- the name

    @Test
    fun `a blank name is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            renter(name = "")
        }
    }

    /**
     * Whitespace-only, not just empty.
     *
     * `"   "` is not a name an owner can search for again, and it is what an
     * untouched text field on a phone produces when the owner taps Save without
     * typing — so this is the *common* case of the blank-name row, not a
     * theoretical one.
     */
    @Test
    fun `a whitespace-only name is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            renter(name = "   ")
        }
    }

    /** The message names what it got, so a failure points at the writer. */
    @Test
    fun `the refusal message names the blank value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            renter(name = "   ")
        }
        assertTrue(error.message!!.contains("name must not be blank"))
    }

    /**
     * The name is *not* normalised — no uppercasing, no whitespace squeezing.
     *
     * A plate has one canonical form and is normalised so its unique index sees
     * one spelling of one car. A person's name does not: uppercasing would
     * degrade every label, list row and WhatsApp message built from it. So the
     * two spellings below are two distinct stored names, and uniqueness is
     * exact. See the design note in the story.
     */
    @Test
    fun `a name is stored exactly as entered`() {
        assertEquals("budi santoso", renter(name = "budi santoso").name)
        assertEquals("Budi Santoso", renter(name = "Budi Santoso").name)
        // Interior runs are the owner's typing, not a second spelling to fix.
        assertEquals("Budi  Santoso", renter(name = "Budi  Santoso").name)
    }

    // ------------------------------------------------------ optional account

    /**
     * `bankAccount` is the one nullable field: plenty of renters are paid in
     * cash, and forcing an account number would push the owner to invent one.
     */
    @Test
    fun `a blank bank account is allowed`() {
        assertNull(renter(name = "Budi Santoso", bankAccount = null).bankAccount)
        assertEquals(
            "BCA 1234567890",
            renter(name = "Budi Santoso", bankAccount = "BCA 1234567890").bankAccount,
        )
    }

    // ------------------------------------------------- out of scope, forever

    /**
     * `ktp` and `address` are absent by design, not by oversight.
     *
     * PRD §6.1 pushes both out of scope and the epic context restates it: the
     * app collects no national ID number and no address. Asserting the absence
     * is what makes that a structural constraint — a field added "just in case"
     * fails here instead of becoming personal data the app can never justify
     * deleting.
     */
    @Test
    fun `the model declares no ktp or address field`() {
        val declared = Renter::class.java.declaredFields.map { it.name.lowercase() }.toSet()
        assertTrue(
            "the model must not collect a national ID number",
            declared.none { it == "ktp" || it == "nik" },
        )
        assertTrue(
            "the model must not collect an address",
            declared.none { it == "address" || it == "alamat" },
        )
    }

    /**
     * And no stored status either. A renter is rented or not according to
     * whether an open `CONTRACT` names them, so a flag on the renter would be a
     * second source of truth that drifts the moment a contract ends.
     */
    @Test
    fun `the model exposes no stored status field`() {
        val declared = Renter::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(
            "status must be derived from contracts, never stored on the renter",
            false,
            declared.any { it == "status" || it == "isRented" || it == "currentVehicleId" },
        )
    }

    // ------------------------------------------------------------------ helper

    private fun renter(name: String, bankAccount: String? = "BCA 1234567890") = Renter(
        id = "renter-1",
        name = name,
        phone = "08123456789",
        bankAccount = bankAccount,
        emergencyContact = "08129876543",
    )
}