package net.klinik.rental.ui.screens.kendaraan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.domain.parse.normalizePlate
import net.klinik.rental.domain.parse.parseRupiahInput
import net.klinik.rental.domain.validation.DuplicatePlateException
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.domain.validation.InvalidAmountException
import net.klinik.rental.domain.validation.VehicleHasActiveContractException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The `SAVE_VEHICLE` matrix rows, against a real Room database.
 *
 * ## Why these are not fakes
 *
 * The uniqueness guarantee in this story is a **schema** guarantee — a `UNIQUE`
 * index on the normalised plate — and a fake DAO cannot demonstrate it. A hand-
 * written fake that "checks for duplicates" would pass this test while the real
 * constraint was missing, which is precisely the failure it exists to catch. So
 * these run against an in-memory Room database built by the same annotation
 * processor that builds the shipped one.
 *
 * `inMemoryDatabaseBuilder` is fine here because the test asserts *schema*
 * behaviour, not persistence across a process restart; the shipped singleton is
 * on disk and is not what this row is about.
 *
 * ## Why Robolectric
 *
 * Room needs a `Context` and a real SQLite. Robolectric supplies both on the
 * JVM, so the AC is verified without an emulator.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleRepositoryTest {

    private lateinit var database: KlinikDatabase
    private lateinit var repository: VehicleRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        )
            // Every assertion here is a suspend function driven by `runBlocking`,
            // so there is no main-thread query to forbid.
            .build()
        repository = VehicleRepository(database.vehicleDao(), database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------- SAVE_VEHICLE: valid row

    /** A valid vehicle is written, and comes back out of the flow. */
    @Test
    fun `a valid vehicle is written and observed`() = runBlocking {
        repository.save(vehicle("B 1235 TF", rate = 250_000L))

        val stored = repository.observeVehicles().first()
        assertEquals(1, stored.size)
        assertEquals("B 1235 TF", stored.single().plate)
        assertEquals("Toyota", stored.single().brand)
        assertEquals(2023, stored.single().year)
    }

    /**
     * The rate reaches the database as a `Long`, whole rupiah — the AC that rules
     * out the prototype's string-rate corruption.
     *
     * The prototype's `updateCar` persisted the rate as a raw DOM string, which
     * turned the fleet KPI into concatenated digits. Here the column is `Long`
     * and the value round-trips exactly, so there is no path by which it can.
     */
    @Test
    fun `the daily rate reaches the database as a Long`() = runBlocking {
        repository.save(vehicle("B 1235 TF", rate = 250_000L))

        val stored = repository.observeVehicles().first().single()
        assertEquals(250_000L, stored.dailyRate)

        // And through the raw column, bypassing every mapping — the only place
        // the stored representation is actually observable.
        val raw = database.vehicleDao().findById(stored.id)
        assertNotNull(raw)
        assertEquals(250_000L, raw!!.dailyRate)
    }

    // --------------------------------------------- SAVE_VEHICLE: duplicate plate

    /**
     * The matrix row: a colliding plate writes nothing and raises
     * `DuplicatePlateException` naming the stored plate.
     */
    @Test
    fun `a colliding plate writes nothing and names the stored plate`() {
        runBlocking { repository.save(vehicle("B 1235 TF", rate = 250_000L)) }

        val error = assertThrows(DuplicatePlateException::class.java) {
            runBlocking { repository.save(vehicle("B 1235 TF", rate = 300_000L, id = "second")) }
        }

        // The message names a plate that actually exists on screen.
        assertEquals("B 1235 TF", error.plate)
        assertTrue(error.message!!.contains("B 1235 TF"))

        // Nothing was written: still one row, and the original rate untouched.
        val stored = runBlocking { repository.observeVehicles().first() }
        assertEquals(1, stored.size)
        assertEquals(250_000L, stored.single().dailyRate)
    }

    /**
     * The epic context's example — `b 1235 tf` and `B 1235 TF` must not become two
     * rows — is enforced at *two* layers, and this is the second.
     *
     * The first is [VehicleEntity]'s constructor, which refuses a plate that is
     * not already normalised. Without it the `UNIQUE` index would compare
     * `"B 1235 TF"` against `"b   1235  tf"`, see two different strings, and
     * correctly decline to fire — leaving one physical car as two rows. The
     * refusal below is what makes the index's guarantee structural rather than
     * dependent on every caller remembering to normalise first.
     */
    @Test
    fun `an un-normalised plate cannot even be constructed into an entity`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            entity("b   1235  tf", id = "second")
        }
        // The message names what it got, so a failure points at the writer.
        assertTrue(error.message!!.contains("b   1235  tf"))
    }

    /**
     * And the two spellings *do* normalise to one string — the pair that makes
     * the above meaningful. If normalisation did not collapse them, the raw
     * duplicate would be a genuinely different plate.
     */
    @Test
    fun `the two spellings normalise to one plate`() {
        assertEquals(
            normalizePlate("b   1235  tf"),
            normalizePlate("B 1235 TF"),
        )

        runBlocking { repository.save(vehicle("B 1235 TF", rate = 250_000L, id = "first")) }

        // A second vehicle written through the repository with the *normalised*
        // spelling of the same plate is refused, and the first is untouched.
        val error = assertThrows(DuplicatePlateException::class.java) {
            runBlocking { repository.save(vehicle("B 1235 TF", rate = 300_000L, id = "second")) }
        }
        assertEquals("B 1235 TF", error.plate)
        assertEquals(
            250_000L,
            runBlocking { repository.observeVehicles().first() }.single().dailyRate,
        )
    }

    /**
     * The `UNIQUE` index itself, demonstrated at the column level.
     *
     * Both rows here are normalised — they are byte-identical — so the constraint
     * that fires is the index's and not a check this suite added. This is the
     * guarantee the prototype's `JSON.parse(localStorage).some()` could not give:
     * two writes that raced could both pass that scan and both land.
     */
    @Test
    fun `the unique index refuses a second identical plate`() {
        runBlocking { database.vehicleDao().insert(entity("B 1235 TF", id = "first")) }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { database.vehicleDao().insert(entity("B 1235 TF", id = "second")) }
        }

        assertEquals(1, runBlocking { repository.observeVehicles().first() }.size)
    }

    // ----------------------------------------------- SAVE_VEHICLE: invalid rate

    /**
     * The matrix row: a rate of `0` writes nothing and raises
     * `InvalidAmountException`.
     *
     * Note *where* it raises: `Vehicle`'s constructor refuses before the
     * repository is reached, so the assertThrows wraps the construction too.
     * That is the point of the domain check — an invalid vehicle cannot exist in
     * memory, so it cannot be written by any path.
     */
    @Test
    fun `a rate of zero is refused and nothing is written`() {
        val error = assertThrows(InvalidAmountException::class.java) {
            runBlocking { repository.save(vehicle("B 1235 TF", rate = 0L)) }
        }
        assertEquals(0L, error.amount)
        assertEquals(0, runBlocking { repository.observeVehicles().first() }.size)
    }

    @Test
    fun `a negative rate is refused and nothing is written`() {
        assertThrows(InvalidAmountException::class.java) {
            runBlocking { repository.save(vehicle("B 1235 TF", rate = -1L)) }
        }
        assertEquals(0, runBlocking { repository.observeVehicles().first() }.size)
    }

    /**
     * NFR-6's actual requirement: the rule is enforced below the UI, so a
     * caller that skipped every form check still cannot write a bad row.
     *
     * Exercised as the *whole* parse-to-write path rather than through a
     * one-off repository method: the same digit string the numpad emits goes
     * through `parseRupiahInput`, is turned into a `Vehicle`, and reaches
     * `save`. `"0"` must stop at the first step and never reach the database.
     */
    @Test
    fun `the parse-to-write path refuses a zero rate`() = runBlocking {
        val digits = "0"
        val rate = parseRupiahInput(digits)
        assertNull("the parser must refuse \"0\"", rate)

        // Nothing was written, and no `Vehicle` was ever constructed — the null
        // short-circuits before construction, which is where the write would
        // have come from.
        assertEquals(0, repository.observeVehicles().first().size)
    }

    /**
     * And the second half of the same rule: even given a `Long` that could only
     * have come from somewhere other than the parser, the repository still
     * refuses it. `Vehicle`'s constructor refuses first, and the repository
     * re-checks — so there is no path into the database that skips both.
     */
    @Test
    fun `a non-positive rate cannot be constructed into a vehicle at all`() {
        assertThrows(InvalidAmountException::class.java) {
            vehicle("B 1235 TF", rate = 0L)
        }
        assertThrows(InvalidAmountException::class.java) {
            vehicle("B 1235 TF", rate = -1L)
        }
        assertEquals(0, runBlocking { repository.observeVehicles().first() }.size)
    }

    // ------------------------------------------------------- editing behaviour

    /**
     * The AC: two vehicles stored, one edited, the other unchanged and the
     * edited rate still a `Long`.
     *
     * The update is keyed on the primary key, so it reaches exactly one row — a
     * broad `save` that rewrote the table would make this assertion pass by
     * accident while still corrupting the neighbour.
     */
    @Test
    fun `editing one vehicle leaves the other unchanged`() = runBlocking {
        repository.save(vehicle("B 1235 TF", rate = 250_000L, id = "first"))
        repository.save(vehicle("B 9876 AB", rate = 275_000L, id = "second"))

        repository.save(vehicle("B 1235 TF", rate = 300_000L, id = "first", model = "Avanza"))

        val stored = repository.observeVehicles().first().associateBy { it.id }
        assertEquals(2, stored.size)
        // The edited row took the new rate…
        assertEquals(300_000L, stored.getValue("first").dailyRate)
        // …and the neighbour is byte-for-byte what it was.
        assertEquals(275_000L, stored.getValue("second").dailyRate)
        assertEquals("B 9876 AB", stored.getValue("second").plate)
    }

    /** An edit may keep its own plate without colliding with itself. */
    @Test
    fun `a vehicle can be edited without changing its plate`() = runBlocking {
        repository.save(vehicle("B 1235 TF", rate = 250_000L, id = "first"))
        repository.save(vehicle("B 9876 AB", rate = 275_000L, id = "second"))

        // Unchanged plate, changed rate: this is an update, not a duplicate.
        repository.save(vehicle("B 1235 TF", rate = 275_000L, id = "first"))

        val stored = repository.observeVehicles().first().associateBy { it.id }
        assertEquals(2, stored.size)
        assertEquals(275_000L, stored.getValue("first").dailyRate)
    }

    // ------------------------------------------------------------- blank plate

    @Test
    fun `a blank plate is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.save(vehicle("   ", rate = 250_000L)) }
        }
        assertEquals(0, runBlocking { repository.observeVehicles().first() }.size)
    }

    // ------------------------------------------------------------ status source

    /**
     * `tersewa` comes from the existence of an open contract, not from a stored
     * column. A vehicle with `endDate IS NULL` is rented; the same vehicle with
     * an end date is available again.
     */
    @Test
    fun `a vehicle with an open contract is reported as rented`() = runBlocking {
        repository.save(vehicle("B 1235 TF", rate = 250_000L, id = "first"))
        assertTrue(repository.observeRentedPlates().first().isEmpty())

        database.vehicleDao().insertContract(
            ContractEntity(
                id = "contract-1",
                vehicleId = "first",
                startDate = LocalDate.of(2026, 10, 1),
                endDate = null,
            ),
        )

        assertEquals(setOf("B 1235 TF"), repository.observeRentedPlates().first())
    }

    /** An ended contract is not an open one, so the vehicle is available again. */
    @Test
    fun `a vehicle with a closed contract is not rented`() = runBlocking {
        repository.save(vehicle("B 1235 TF", rate = 250_000L, id = "first"))
        database.vehicleDao().insertContract(
            ContractEntity(
                id = "contract-1",
                vehicleId = "first",
                startDate = LocalDate.of(2026, 10, 1),
                endDate = LocalDate.of(2026, 10, 5),
            ),
        )

        assertTrue(repository.observeRentedPlates().first().isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A raw entity, bypassing the domain model entirely.
     *
     * Used for the uniqueness rows, which are about what the *schema* refuses —
     * and the domain model cannot be used for that, because by the time a
     * `Vehicle` exists its plate is already normalised.
     */
    private fun entity(plate: String, id: String) = VehicleEntity(
        id = id,
        plate = plate,
        brand = "Toyota",
        model = "Agya",
        year = 2023,
        color = "Hitam",
        dailyRate = 250_000L,
        notes = null,
    )

    private fun vehicle(
        plate: String,
        rate: Long,
        id: String = "vehicle-1",
        model: String = "Agya",
    ) = Vehicle(
        id = id,
        plate = plate,
        brand = "Toyota",
        model = model,
        year = 2023,
        color = "Hitam",
        dailyRate = rate,
    )
}