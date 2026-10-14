package net.klinik.rental.ui.screens.kendaraan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.domain.validation.VehicleHasActiveContractException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The `DELETE_VEHICLE` matrix row: a vehicle with a contract whose `endDate IS
 * NULL` survives a delete and raises `VehicleHasActiveContractException`.
 *
 * ## Why this test exists with no delete button on the screen
 *
 * The story's AC asks that deleting a rented vehicle be *refused*, and never asks
 * for a delete control. The affordance is deliberately deferred — the mandatory
 * deletion reason has nowhere to go yet, and the prototype's dialog collected one
 * and dropped it while promising an audit trail that did not exist. So the guard
 * ships with no UI attached, which means it is this test — not a tap on a button —
 * that holds the guarantee.
 *
 * That makes this row more load-bearing than a normal UI test: if it is deleted,
 * skipped, or weakened to "returns false", the refusal quietly stops existing
 * and nothing on screen would reveal it.
 *
 * ## Why a real database
 *
 * The refusal is enforced by `onDelete = ForeignKey.RESTRICT` in the schema. A
 * fake DAO would have to re-implement that rule by hand, and a hand-written
 * implementation of the rule under test proves nothing about the real one.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleRepositoryDeleteTest {

    private lateinit var database: KlinikDatabase
    private lateinit var repository: VehicleRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        repository = VehicleRepository(database.vehicleDao(), database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** The matrix row, in full: refuses, names the vehicle, and removes nothing. */
    @Test
    fun `a vehicle with an open contract cannot be deleted`() = runBlocking {
        repository.save(vehicle("B 1235 TF", id = "first"))
        database.vehicleDao().insertContract(
            ContractEntity(
                id = "contract-1",
                vehicleId = "first",
                startDate = LocalDate.of(2026, 10, 1),
                // `null` *is* "still running".
                endDate = null,
            ),
        )

        val error = assertThrows(VehicleHasActiveContractException::class.java) {
            runBlocking { repository.delete("first") }
        }

        // The exception names a plate the owner can see in the list, so the
        // message is actionable rather than a bare refusal.
        assertEquals("B 1235 TF", error.plate)
        assertTrue(error.message!!.contains("B 1235 TF"))

        // The row is still there. "Refused" has to mean *untouched*, not
        // "removed and complained about afterwards".
        val survivor = database.vehicleDao().findById("first")
        assertNotNull("the vehicle row must survive a refused delete", survivor)
        assertEquals("B 1235 TF", survivor!!.plate)
        assertEquals(250_000L, survivor.dailyRate)
        assertEquals(1, repository.observeVehicles().first().size)
    }

    /**
     * A *finished* contract also blocks a delete, and is reported as its own
     * fact rather than as "still rented".
     *
     * `onDelete = ForeignKey.RESTRICT` is deliberately broad: it refuses while
     * any contract row references the vehicle. That is the safe direction to be
     * wrong in — whether a finished contract should permit a delete is Story
     * 2.7's question, since it is the story that ends contracts and owns what
     * happens to the allocations pointing at the vehicle.
     *
     * The exception is asserted as [IllegalStateException] rather than
     * [VehicleHasActiveContractException] because saying "masih tersewa" about a
     * rental that ended on the 5th would be a message wrong in exactly the way a
     * stored status column goes wrong. The row is still untouched either way.
     */
    @Test
    fun `a vehicle with a closed contract is still protected by the foreign key`() = runBlocking {
        repository.save(vehicle("B 1235 TF", id = "first"))
        database.vehicleDao().insertContract(
            ContractEntity(
                id = "contract-1",
                vehicleId = "first",
                startDate = LocalDate.of(2026, 10, 1),
                endDate = LocalDate.of(2026, 10, 5),
            ),
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.delete("first") }
        }

        // Refused means untouched, not "removed and complained about after".
        assertNotNull(database.vehicleDao().findById("first"))
        assertEquals(1, repository.observeVehicles().first().size)
    }

    /** An unrented vehicle is deletable — the guard is not a blanket refusal. */
    @Test
    fun `an unrented vehicle can be deleted`() = runBlocking {
        repository.save(vehicle("B 1235 TF", id = "first"))
        assertEquals(1, repository.observeVehicles().first().size)

        repository.delete("first")

        assertEquals(null, database.vehicleDao().findById("first"))
        assertTrue(repository.observeVehicles().first().isEmpty())
    }

    /**
     * One rented vehicle does not block another. A guard keyed on "any contract
     * exists anywhere" would fail here, and the failure would look like the app
     * silently refusing ordinary work.
     */
    @Test
    fun `a rented vehicle does not block deleting a different vehicle`() = runBlocking {
        repository.save(vehicle("B 1235 TF", id = "rented"))
        repository.save(vehicle("B 9876 AB", id = "free"))
        database.vehicleDao().insertContract(
            ContractEntity(
                id = "contract-1",
                vehicleId = "rented",
                startDate = LocalDate.of(2026, 10, 1),
                endDate = null,
            ),
        )

        repository.delete("free")

        assertNotNull(database.vehicleDao().findById("rented"))
        assertEquals(null, database.vehicleDao().findById("free"))
    }

    /**
     * Deleting an id that is not stored is a no-op, not a crash: the repository
     * is callable from a list action that raced a removal, and throwing
     * `VehicleHasActiveContractException` there would name a vehicle the owner
     * never had.
     */
    @Test
    fun `deleting an unknown id is a no-op`() = runBlocking {
        repository.delete("no-such-vehicle")
        assertTrue(repository.observeVehicles().first().isEmpty())
    }

    /**
     * The foreign key is the backstop, independent of the repository's explicit
     * check.
     *
     * This writes a raw `DELETE` through the DAO, bypassing
     * `repository.delete` and therefore bypassing the `hasOpenContract` check. If
     * `onDelete = RESTRICT` were `CASCADE`, the row *and* its contract would
     * vanish silently and this assertion would fail — which is exactly the data
     * loss the schema choice exists to prevent.
     */
    @Test
    fun `the foreign key refuses a delete even when the repository check is bypassed`() = runBlocking {
        repository.save(vehicle("B 1235 TF", id = "first"))
        database.vehicleDao().insertContract(
            ContractEntity(
                id = "contract-1",
                vehicleId = "first",
                startDate = LocalDate.of(2026, 10, 1),
                endDate = null,
            ),
        )

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { database.vehicleDao().deleteById("first") }
        }

        // Both rows survive: no cascade, no silent contract loss.
        assertNotNull(database.vehicleDao().findById("first"))
    }

    // ------------------------------------------------------------------ helpers

    private fun vehicle(plate: String, id: String) = Vehicle(
        id = id,
        plate = plate,
        brand = "Toyota",
        model = "Agya",
        year = 2023,
        color = "Hitam",
        dailyRate = 250_000L,
    )
}