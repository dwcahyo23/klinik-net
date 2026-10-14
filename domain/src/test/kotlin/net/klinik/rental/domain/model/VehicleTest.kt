package net.klinik.rental.domain.model

import net.klinik.rental.domain.validation.InvalidAmountException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Construction refuses a blank plate and a rate of `0` — the two rules that
 * make a `Vehicle` row safe to write.
 *
 * The interesting property is *where* the refusal happens: on construction, so
 * an invalid vehicle cannot exist in memory, let alone reach the database. This
 * mirrors `PaymentTest`/`AllocationEngineTest`, which hold the same line for
 * deposits.
 */
class VehicleTest {

    @Test
    fun `a valid vehicle constructs`() {
        val vehicle = vehicle(plate = "B 1235 TF", rate = 250_000L)
        assertEquals("B 1235 TF", vehicle.plate)
        assertEquals(250_000L, vehicle.dailyRate)
    }

    // ------------------------------------------------------------- the plate

    @Test
    fun `a blank plate is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            vehicle(plate = "", rate = 250_000L)
        }
    }

    @Test
    fun `a whitespace-only plate is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            vehicle(plate = "   ", rate = 250_000L)
        }
    }

    /**
     * The model stores the *normalised* plate rather than normalising on read.
     * A constructor that accepted `"b 1235 tf"` would let a row be written whose
     * text differs from the value its unique index is built on — two spellings
     * of one car that the database would then treat as distinct.
     */
    @Test
    fun `an un-normalised plate is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            vehicle(plate = "b 1235 tf", rate = 250_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            vehicle(plate = "  B 1235 TF  ", rate = 250_000L)
        }
    }

    // ----------------------------------------------------------- the money

    @Test
    fun `a rate of zero is refused`() {
        assertThrows(InvalidAmountException::class.java) {
            vehicle(plate = "B 1235 TF", rate = 0L)
        }
    }

    @Test
    fun `a negative rate is refused`() {
        assertThrows(InvalidAmountException::class.java) {
            vehicle(plate = "B 1235 TF", rate = -1L)
        }
    }

    /**
     * The prototype's `dailyRate: parseInt(x) || 250000` is the exact opposite:
     * a zero or an unparseable value became a plausible-looking Rp 250.000. There
     * is no rate that constructs as anything other than the rate given.
     */
    @Test
    fun `no rate is ever replaced by a default`() {
        assertEquals(1L, vehicle(plate = "B 1 AA", rate = 1L).dailyRate)
        assertEquals(13_000_000L, vehicle(plate = "B 2 AA", rate = 13_000_000L).dailyRate)
    }

    /** The rate is `Long` end to end, so it cannot hold a concatenated string. */
    @Test
    fun `the rate is a Long and survives the round trip`() {
        val rate = 250_000L
        assertEquals(rate, vehicle(plate = "B 1235 TF", rate = rate).dailyRate)
    }

    // -------------------------------------------------- there is no status

    /**
     * `status` and `currentRenterId` are the prototype's two display
     * conventions, and both drift. Their absence is a design constraint, so it
     * is asserted: a `Vehicle` with a stored status is a Vehicle whose status
     * can disagree with the contracts that actually exist.
     */
    @Test
    fun `the model exposes no stored status field`() {
        val declared = Vehicle::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(
            "status must be derived, never stored",
            false,
            declared.any { it == "status" || it == "currentRenterId" },
        )
        assertEquals(false, declared.contains("currentRenterId"))
    }

    private fun vehicle(plate: String, rate: Long) = Vehicle(
        id = "vehicle-1",
        plate = plate,
        brand = "Toyota",
        model = "Avanza",
        year = 2023,
        dailyRate = rate,
    )
}