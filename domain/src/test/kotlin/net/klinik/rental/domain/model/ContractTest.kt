package net.klinik.rental.domain.model

import net.klinik.rental.domain.validation.InvalidAmountException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Construction rules for a `Contract`: the ids are non-blank, the rate is
 * positive, and an end date cannot precede the start.
 *
 * The interesting property is *where* the refusal happens — on construction, so an
 * invalid contract cannot exist in memory, let alone reach the database. That is
 * the same line [VehicleTest] and [RenterTest] hold for the plate and the name, and
 * it is the half of the rule that needs no Android SDK.
 *
 * The *one active contract per vehicle and per renter* rule cannot be asserted
 * here at all: it is a pair of `UNIQUE` indexes, and only a real database can
 * refuse it. That half is `ContractRepositoryTest` in the app module, and its
 * `INDEX_REFUSES_SECOND` row deliberately bypasses every pre-check to prove it.
 */
class ContractTest {

    @Test
    fun `a valid contract constructs`() {
        val contract = contract()
        assertEquals("contract-1", contract.id)
        assertEquals("renter-1", contract.renterId)
        assertEquals("vehicle-1", contract.vehicleId)
        assertEquals(250_000L, contract.dailyRate)
        assertEquals(DayOfWeek.SUNDAY, contract.restDay)
    }

    // ---------------------------------------------------------------- the ids

    /**
     * A blank id is a primary key that collides with every other blank id, and a
     * blank foreign key points at nothing. All three are refused at construction so
     * the caller hears about the id rather than about a constraint three layers
     * down.
     */
    @Test
    fun `a blank id is refused`() {
        assertThrows(IllegalArgumentException::class.java) { contract(id = "") }
        assertThrows(IllegalArgumentException::class.java) { contract(id = "   ") }
    }

    @Test
    fun `a blank renter id is refused`() {
        assertThrows(IllegalArgumentException::class.java) { contract(renterId = "") }
        assertThrows(IllegalArgumentException::class.java) { contract(renterId = "   ") }
    }

    @Test
    fun `a blank vehicle id is refused`() {
        assertThrows(IllegalArgumentException::class.java) { contract(vehicleId = "") }
        assertThrows(IllegalArgumentException::class.java) { contract(vehicleId = "   ") }
    }

    // --------------------------------------------------------------- the rate

    /**
     * NFR-6 and AD-2: money is rejected where it enters the domain and never
     * defaulted. A zero-rate contract would bill nothing while occupying the car,
     * and it is exactly what the prototype's `parseInt(x) || 250000` produced for
     * an unparseable rate.
     */
    @Test
    fun `a rate of zero is refused`() {
        val error = assertThrows(InvalidAmountException::class.java) { contract(rate = 0L) }
        assertEquals(0L, error.amount)
    }

    @Test
    fun `a negative rate is refused`() {
        assertThrows(InvalidAmountException::class.java) { contract(rate = -1L) }
    }

    /**
     * The rate is `Long` rupiah end to end and no value is ever replaced by a
     * default — including the smallest legal amount, which a "falsy" check would
     * have swallowed.
     */
    @Test
    fun `no rate is ever replaced by a default`() {
        assertEquals(1L, contract(rate = 1L).dailyRate)
        assertEquals(13_000_000L, contract(rate = 13_000_000L).dailyRate)
        assertEquals(Long.MAX_VALUE, contract(rate = Long.MAX_VALUE).dailyRate)
    }

    // ------------------------------------------------------------ the schedule

    /**
     * `null` rest day *is* the seven-day week — not a missing value.
     *
     * This is the whole reason the model carries one field instead of the epic
     * context's `workSchedule` string plus `offDay` integer: `"7days"` with
     * `offDay = 3` would have no defined meaning, and two encodings of one fact can
     * disagree.
     */
    @Test
    fun `a null rest day is a seven day week`() {
        assertEquals(null, contract(restDay = null).restDay)
    }

    @Test
    fun `each day of the week can be the rest day`() {
        DayOfWeek.entries.forEach { day ->
            assertEquals(day, contract(restDay = day).restDay)
        }
    }

    // ---------------------------------------------------------- the date order

    @Test
    fun `an end date before the start is refused`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            contract(
                startDate = LocalDate.of(2026, 10, 10),
                endDate = LocalDate.of(2026, 10, 5),
            )
        }
        assertTrue(error.message!!.contains("must not be before"))
    }

    /** An end equal to the start is a one-day rental, and is real. */
    @Test
    fun `an end date on the start date is allowed`() {
        val day = LocalDate.of(2026, 10, 10)
        assertEquals(day, contract(startDate = day, endDate = day).endDate)
    }

    /**
     * A start date in the past is accepted — contracts that began before the app
     * existed are real, and refusing them would make the owner's own history
     * unrecordable (FR-7).
     */
    @Test
    fun `a start date in the past is accepted verbatim`() {
        val past = LocalDate.of(2020, 1, 15)
        assertEquals(past, contract(startDate = past).startDate)
    }

    // --------------------------------------------------------------- isOpen

    /**
     * The truth table for "open". `endDate IS NULL` *is* the open marker, so this
     * is a derivation and not a stored flag that could disagree with the date it
     * claims to mirror.
     */
    @Test
    fun `a contract with no end date is open`() {
        assertTrue(contract(endDate = null).isOpen)
    }

    @Test
    fun `a contract with an end date is not open`() {
        assertFalse(contract(endDate = LocalDate.of(2026, 10, 5)).isOpen)
    }

    // -------------------------------------------------- FR-9b, since Story 3.6

    /**
     * `settledThroughDate` is **declared** now — Story 3.6's FR-9b marker — and this
     * test used to assert the opposite.
     *
     * It is kept, inverted, rather than deleted, because the assertion was load-bearing
     * when it was written: Story 2.5 added the column "just in case" with no writer, and
     * a settlement claim the app cannot support is worse than a missing one. Story 3.6
     * supplies the writer, so the field is now earned — and the test that watched for it
     * is exactly the one that should notice if a fourth story adds another unwritten
     * column beside it.
     *
     * Asserted on the declared field so the failure names the field, not a boolean.
     */
    @Test
    fun `the model declares a settlement marker and defaults it to none`() {
        val declared = Contract::class.java.declaredFields.map { it.name.lowercase() }.toSet()
        assertTrue(
            "FR-9b's marker arrived with Story 3.6's writer",
            declared.contains("settledthroughdate"),
        )
        // And unset on every contract the app has written before it — a fresh contract
        // is never silently pre-settled.
        assertNull(Contract("c", "r", "v", LocalDate.of(2026, 10, 1), 250_000).settledThroughDate)
    }

    /**
     * And a settlement is independent of [Contract.endDate] — a contract can both end
     * mid-period and have its earlier days settled offline.
     *
     * The pair is kept apart deliberately; merging them would make one action overwrite
     * the other, so this pins the distinction the data layer relies on when it carries
     * `settledThroughDate` through `endContract`.
     */
    @Test
    fun `a settlement and an end date are independent`() {
        val both = contract(
            endDate = LocalDate.of(2026, 10, 20),
            settledThroughDate = LocalDate.of(2026, 10, 10),
        )
        assertEquals(LocalDate.of(2026, 10, 10), both.settledThroughDate)
        assertEquals(LocalDate.of(2026, 10, 20), both.endDate)
        assertFalse(both.isOpen)
    }

    /**
     * And no `status`, for the same reason [VehicleTest] gives: tersewa and
     * tersedia are derived from an open contract, so a stored status here would be
     * a second source of truth that drifts the moment the contract ends.
     */
    @Test
    fun `the model exposes no stored status field`() {
        val declared = Contract::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("status must be derived, never stored", declared.contains("status"))
    }

    /**
     * And no second encoding of the schedule. `workSchedule` and `offDay` are the
     * epic context's ERD spelling of a fact `restDay` already carries, and two
     * encodings of one fact can disagree.
     */
    @Test
    fun `the schedule is one field and not two`() {
        val declared = Contract::class.java.declaredFields.map { it.name }.toSet()
        assertFalse(declared.contains("workSchedule"))
        assertFalse(declared.contains("offDay"))
    }

    // ------------------------------------------------------------------ helper

    private fun contract(
        id: String = "contract-1",
        renterId: String = "renter-1",
        vehicleId: String = "vehicle-1",
        startDate: LocalDate = LocalDate.of(2026, 10, 1),
        rate: Long = 250_000L,
        restDay: DayOfWeek? = DayOfWeek.SUNDAY,
        endDate: LocalDate? = null,
        settledThroughDate: LocalDate? = null,
    ) = Contract(
        id = id,
        renterId = renterId,
        vehicleId = vehicleId,
        startDate = startDate,
        dailyRate = rate,
        restDay = restDay,
        endDate = endDate,
        settledThroughDate = settledThroughDate,
    )
}
