package net.klinik.rental.ui.screens.kontrak

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.ContractDao
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.validation.ContractAlreadyActiveException
import net.klinik.rental.domain.validation.InvalidAmountException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The `OPEN_*` matrix rows, against a real Room database.
 *
 * ## Why these are not fakes
 *
 * The one-active rule in this story is a **schema** guarantee — two `UNIQUE` indexes
 * over the contract's active columns — and a fake DAO cannot demonstrate it. A
 * hand-written fake that "checks for an active contract" would pass every test here
 * while the real constraints were missing, which is precisely the failure the
 * `INDEX_REFUSES_SECOND` row exists to catch. So these run against an in-memory Room
 * database built by the same annotation processor that builds the shipped one.
 *
 * `inMemoryDatabaseBuilder` is fine here because the tests assert *schema* behaviour,
 * not persistence across a process restart — that is `DatabaseMigrationTest`'s job,
 * and it needs a real file.
 *
 * ## Why Robolectric
 *
 * Room needs a `Context` and a real SQLite. Robolectric supplies both on the JVM, so
 * the AC is verified without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContractRepositoryTest {

    private lateinit var database: KlinikDatabase
    private lateinit var repository: ContractRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        )
            // Every assertion here is a suspend function driven by `runBlocking`, so
            // there is no main-thread query to forbid.
            .build()
        repository = ContractRepository(database.contractDao(), database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------- OPEN_CONTRACT: the happy path

    /**
     * The matrix row: a free vehicle, a free renter, and a contract that is stored
     * open and comes back out of the flow.
     *
     * Observed through the `Flow` rather than a one-shot read, because the flow is
     * what the screen subscribes to — this is also the AC's "visible with no manual
     * refresh": Room's invalidation tracker re-emits with no signal from anywhere in
     * the app.
     */
    @Test
    fun `an open contract is written and observed`() = runBlocking {
        seed()

        repository.open(contract())

        val stored = repository.observeContracts().first()
        assertEquals(1, stored.size)
        assertEquals(RENTER_ID, stored.single().renterId)
        assertEquals(VEHICLE_ID, stored.single().vehicleId)
        assertEquals(250_000L, stored.single().dailyRate)
        assertEquals(DayOfWeek.SUNDAY, stored.single().restDay)
        assertTrue("a contract with no end date is open", stored.single().isOpen)
    }

    /**
     * The AC's second half: the same vehicle now reads `Tersewa` on the fleet
     * screen.
     *
     * Asserted through `VehicleRepository.observeRentedPlates`, which is the exact
     * flow `KendaraanViewModel` combines into `VehicleStatus.Tersewa` — so this is
     * the derivation the fleet screen reads, not a re-implementation of it.
     */
    @Test
    fun `opening a contract makes the vehicle read as rented`() = runBlocking {
        seed()
        val fleet = net.klinik.rental.data.repository.VehicleRepository(database.vehicleDao())

        assertTrue("the vehicle starts free", fleet.observeRentedPlates().first().isEmpty())

        repository.open(contract())

        assertEquals(setOf(PLATE), fleet.observeRentedPlates().first())
    }

    /**
     * OPEN_WITH_PAST_START: a start date before today is accepted and stored
     * verbatim.
     *
     * Contracts that began before this app existed are real (FR-7), and refusing them
     * would make the owner's own history unrecordable. Asserted on the raw column as
     * well, because the stored representation is what every later query reads.
     */
    @Test
    fun `a start date in the past is stored verbatim`() = runBlocking {
        seed()
        val past = LocalDate.of(2020, 1, 15)

        repository.open(contract(startDate = past))

        assertEquals(past, repository.observeContracts().first().single().startDate)
        assertEquals(
            past.toString(),
            database.contractDao().findById(CONTRACT_ID)!!.startDate.toString(),
        )
    }

    /**
     * OPEN_NEGOTIATED_RATE: the contract's own rate is stored and the vehicle is
     * untouched.
     *
     * The AC names this explicitly — negotiated rates are allowed — and the second
     * half is the one that matters: a write path that "helpfully" synced the vehicle
     * to the agreed price would silently reprice the car for every future rental.
     */
    @Test
    fun `a negotiated rate is stored on the contract and the vehicle is untouched`() = runBlocking {
        seed()

        repository.open(contract(rate = 300_000L))

        assertEquals(300_000L, repository.observeContracts().first().single().dailyRate)
        assertEquals(
            "the vehicle's own rate must not be changed by the contract",
            VEHICLE_RATE,
            database.vehicleDao().findById(VEHICLE_ID)!!.dailyRate,
        )
    }

    /**
     * RATE_FROZEN: changing the vehicle's rate afterwards leaves the stored contract
     * rate unchanged.
     *
     * This is the whole reason the rate is copied in rather than read through at
     * billing time: a later price change on the fleet screen is about *future*
     * rentals, and applying it to a rental already agreed would reprice it behind the
     * owner's back.
     */
    @Test
    fun `a later vehicle rate change leaves the contract rate unchanged`() = runBlocking {
        seed()
        repository.open(contract(rate = 250_000L))

        // The owner raises the fleet price on the vehicle screen.
        database.vehicleDao().update(
            vehicleEntity().copy(dailyRate = 400_000L),
        )

        assertEquals(
            "the frozen contract rate must not follow the vehicle",
            250_000L,
            repository.observeContracts().first().single().dailyRate,
        )
    }

    // -------------------------------------------------- OPEN_VEHICLE_BUSY / RENTER_BUSY

    /**
     * The matrix row: a vehicle that already has an open contract is refused, nothing
     * is written, and the message names the plate.
     *
     * The plate is the *stored* one, read back from the vehicle row — not the id the
     * caller passed — so the message points at a car the owner can see in the fleet
     * list.
     */
    @Test
    fun `a vehicle that already has an open contract is refused and names the plate`() = runBlocking {
        seed()
        repository.open(contract(id = "first"))

        val error = assertThrows(ContractAlreadyActiveException::class.java) {
            runBlocking { repository.open(contract(id = "second")) }
        }

        assertEquals(ContractAlreadyActiveException.Side.Vehicle, error.side)
        assertEquals(PLATE, error.blockingName)
        assertTrue(error.message!!.contains(PLATE))

        // Nothing was written: still one row, and it is the first one.
        val stored = repository.observeContracts().first()
        assertEquals(1, stored.size)
        assertEquals("first", stored.single().id)
    }

    /**
     * The matrix row for the renter side: a person who already has an open contract
     * is refused, nothing is written, and the message names the *person*.
     *
     * A second vehicle is seeded so the refusal can only be the renter's — without
     * it the vehicle check would fire first and this test would pass while proving
     * nothing about the renter half.
     */
    @Test
    fun `a renter that already has an open contract is refused and names the name`() = runBlocking {
        seed()
        // A second car, so the vehicle slot is free and the renter's is the only one
        // that can refuse.
        database.vehicleDao().insert(vehicleEntity(id = "vehicle-2", plate = "B 9876 AB"))

        repository.open(contract(id = "first", vehicleId = "vehicle-2"))

        val error = assertThrows(ContractAlreadyActiveException::class.java) {
            runBlocking { repository.open(contract(id = "second", vehicleId = VEHICLE_ID)) }
        }

        assertEquals(ContractAlreadyActiveException.Side.Renter, error.side)
        assertEquals(RENTER_NAME, error.blockingName)
        assertTrue(error.message!!.contains(RENTER_NAME))

        assertEquals(1, repository.observeContracts().first().size)
    }

    /**
     * When *both* slots are busy, the vehicle is named — deterministically.
     *
     * The two pre-checks run in a fixed order so the refusal does not depend on which
     * query the executor happened to run first. Without that, the same input would
     * sometimes blame the car and sometimes the person, and a test could only assert
     * "one of the two".
     */
    @Test
    fun `the vehicle side is refused first when both are busy`() = runBlocking {
        seed()
        repository.open(contract(id = "first"))

        val error = assertThrows(ContractAlreadyActiveException::class.java) {
            runBlocking { repository.open(contract(id = "second")) }
        }

        assertEquals(ContractAlreadyActiveException.Side.Vehicle, error.side)
    }

    // ------------------------------------------------ INDEX_REFUSES_SECOND

    /**
     * The matrix row the schema exists for: the pre-check bypassed, two open
     * contracts written for one vehicle, the second refused by the unique index, and
     * the row count left at one.
     *
     * ## Why this bypasses the repository
     *
     * A pre-check is a courtesy: two callers can both pass it before either writes.
     * Only the index settles that, and the only way to demonstrate the index is to
     * write through the DAO directly — the repository would refuse at step 2 and the
     * constraint would never be exercised. So this is the one test in this file that
     * goes under the repository, and it is the one that proves the guarantee is the
     * schema rather than the check.
     */
    @Test
    fun `the unique index refuses a second open contract when the pre-check is bypassed`() = runBlocking {
        seed()

        database.contractDao().insertContract(openEntity(id = "first"))

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { database.contractDao().insertContract(openEntity(id = "second")) }
        }

        assertEquals(
            "the schema must hold the row count at one",
            1,
            repository.observeContracts().first().size,
        )
    }

    /**
     * And the repository translates the index's refusal into the *same* named
     * exception the pre-check raises, naming the plate.
     *
     * Without that translation a raced write would surface as a raw
     * `SQLiteConstraintException` — a message naming a constraint, in another
     * language, that the owner cannot act on. The translation is *verified* rather
     * than assumed: the catch block re-reads which slot is occupied, so a primary-key
     * clash or a foreign-key refusal is rethrown untouched instead of being
     * relabelled as "that car is busy".
     *
     * The refusal below comes from the pre-check, because a pre-check that can see
     * the row always wins single-threaded. That is why the translation itself is
     * exercised by `the insert path translates the index refusal when the pre-check
     * cannot see the row` below, and the index's own refusal by `the unique index
     * refuses a second open contract when the pre-check is bypassed`. This test proves
     * the pre-check's half, which is the common path.
     */
    @Test
    fun `an occupied slot is refused with the named exception`() = runBlocking {
        seed()
        database.contractDao().insertContract(openEntity(id = "first"))

        val error = assertThrows(ContractAlreadyActiveException::class.java) {
            runBlocking { repository.open(contract(id = "second")) }
        }

        assertEquals(ContractAlreadyActiveException.Side.Vehicle, error.side)
        assertEquals(PLATE, error.blockingName)
        assertEquals(1, repository.observeContracts().first().size)
    }

    /**
     * `INDEX_REFUSES_SECOND`'s **Error Handling** promise, exercised end to end: the
     * pre-check passes, the insert hits the index, and the repository translates the
     * refusal into `ContractAlreadyActiveException(Side.Vehicle, plate)`.
     *
     * ## Why this needs a delegating DAO
     *
     * The catch block is unreachable single-threaded: a pre-check that can see the
     * occupied row always refuses first, so `insertEntity` is never called. The one
     * test that does reach the index calls the DAO directly, which proves the schema
     * refuses but says nothing about the translation — leaving the matrix row's stated
     * Error Handling, and the re-read and rethrow branches beside it, executed by
     * nothing at all.
     *
     * So this wraps the real DAO and blinds only its two `findActiveBy*` lookups. Every
     * other method delegates, so the insert is the real one against the real schema and
     * the real indexes; only the pre-check's view is narrowed, which is exactly the
     * state a racing caller would find itself in. This is a *wrapper*, not a fake — a
     * fake that reimplemented the write would prove nothing about the real constraint.
     */
    @Test
    fun `the insert path translates the index refusal when the pre-check cannot see the row`() = runBlocking {
        seed()
        // The real row is written first, so the index genuinely refuses the second.
        database.contractDao().insertContract(openEntity(id = "first"))

        val blinded = BlindPreCheckDao(database.contractDao())
        val racing = ContractRepository(blinded, database)

        val error = assertThrows(ContractAlreadyActiveException::class.java) {
            runBlocking { racing.open(contract(id = "second")) }
        }

        assertEquals(ContractAlreadyActiveException.Side.Vehicle, error.side)
        assertEquals(
            "the message must name the plate read back from the row holding the slot",
            PLATE,
            error.blockingName,
        )
        assertEquals("the index must hold the row count at one", 1, repository.observeContracts().first().size)
    }

    /**
     * And the same for the **renter** side: a blinded pre-check, an insert refused by
     * `index_contract_activeRenterId`, translated to the renter's own sentence.
     *
     * Two reasons this is separate from the vehicle test. First, the two indexes are
     * independent — a migration that created only one would pass every other test in
     * this file. Second, the translation's re-read has to pick the *right* side: the
     * catch checks the vehicle first, so a renter-side refusal must fall through to the
     * renter lookup and name the person rather than the car.
     */
    @Test
    fun `the insert path translates the renter index refusal to the renter side`() = runBlocking {
        seed()
        // A second car, so the vehicle slot is free and only the renter's is occupied.
        database.vehicleDao().insert(vehicleEntity(id = "vehicle-2", plate = "B 9876 AB"))
        database.contractDao().insertContract(openEntity(id = "first"))

        val blinded = BlindPreCheckDao(database.contractDao())
        val racing = ContractRepository(blinded, database)

        val error = assertThrows(ContractAlreadyActiveException::class.java) {
            runBlocking { racing.open(contract(id = "second", vehicleId = "vehicle-2")) }
        }

        assertEquals(
            "a renter-side refusal must name the renter, not the car",
            ContractAlreadyActiveException.Side.Renter,
            error.side,
        )
        assertEquals(RENTER_NAME, error.blockingName)
        assertEquals(1, repository.observeContracts().first().size)
    }

    /**
     * The renter-side unique index exercised as a **guarantee**, at the column level.
     *
     * The vehicle side has this test; the renter side did not, which meant a typo that
     * dropped `unique` from `index_contract_activeRenterId` — in the entity or in
     * `MIGRATION_2_3` — passed every test in this file. The two indexes are the whole
     * one-active rule, so each is asserted where only the schema can answer.
     */
    @Test
    fun `the renter index refuses a second open contract when the pre-check is bypassed`() = runBlocking {
        seed()
        database.vehicleDao().insert(vehicleEntity(id = "vehicle-2", plate = "B 9876 AB"))

        database.contractDao().insertContract(openEntity(id = "first"))

        // Same renter, a different free vehicle: only the renter index can refuse.
        // Both the id *and* its active mirror move together — `ContractEntity.init`
        // requires them to agree for an open contract, so copying only `vehicleId`
        // would be refused by the entity rather than by the index.
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                database.contractDao().insertContract(
                    openEntity(id = "second").copy(
                        vehicleId = "vehicle-2",
                        activeVehicleId = "vehicle-2",
                    ),
                )
            }
        }

        assertEquals(
            "the schema must hold the row count at one",
            1,
            repository.observeContracts().first().size,
        )
    }

    // ------------------------------------------------------------ OPEN_RATE_ZERO

    /**
     * The matrix row: a rate of zero or negative is refused and nothing is written.
     *
     * `Contract`'s constructor refuses before the repository is reached, so the
     * `assertThrows` wraps the construction too. That is the point of the domain
     * check — an invalid contract cannot exist in memory, so it cannot be written by
     * any path.
     */
    @Test
    fun `a rate of zero is refused and nothing is written`() {
        val error = assertThrows(InvalidAmountException::class.java) {
            contract(rate = 0L)
        }
        assertEquals(0L, error.amount)
        assertEquals(0, runBlocking { repository.observeContracts().first() }.size)
    }

    @Test
    fun `a negative rate is refused and nothing is written`() {
        assertThrows(InvalidAmountException::class.java) {
            contract(rate = -1L)
        }
        assertEquals(0, runBlocking { repository.observeContracts().first() }.size)
    }

    /**
     * And a non-positive rate cannot reach the database by *any* path: the model
     * refuses it at construction, and the repository re-checks before it writes.
     *
     * The second half is unreachable given the first — the same shape
     * `VehicleRepository` has, and deliberately kept for the same reason: a caller
     * holding a rate and no `Contract` yet must still be refused. `copy` is used here
     * rather than a hand-built instance because it goes back through the primary
     * constructor, so the model's own check is what fires — which is the claim this
     * test makes.
     */
    @Test
    fun `a non-positive rate cannot be constructed into a contract at all`() {
        assertThrows(InvalidAmountException::class.java) {
            contract(rate = 250_000L).copy(dailyRate = 0L)
        }
        assertThrows(InvalidAmountException::class.java) {
            contract(rate = 250_000L).copy(dailyRate = -1L)
        }
        assertEquals(0, runBlocking { repository.observeContracts().first() }.size)
    }

    // -------------------------------------------------------------- the schedule

    /**
     * `restDay = null` is the seven-day week, and it round-trips as `null` rather
     * than as a sentinel.
     *
     * The epic context's `workSchedule` string plus `offDay` integer would have made
     * `"7days"` with `offDay = 3` representable, and it has no defined meaning. One
     * nullable column cannot express that contradiction.
     */
    @Test
    fun `a seven day week stores a null rest day`() = runBlocking {
        seed()

        repository.open(contract(restDay = null))

        assertNull(repository.observeContracts().first().single().restDay)
        assertNull(database.contractDao().findById(CONTRACT_ID)!!.restDay)
    }

    /** And a six-day week round-trips the day itself, through the converter. */
    @Test
    fun `a six day week stores its rest day`() = runBlocking {
        seed()

        repository.open(contract(restDay = DayOfWeek.WEDNESDAY))

        assertEquals(
            DayOfWeek.WEDNESDAY,
            repository.observeContracts().first().single().restDay,
        )
    }

    // ------------------------------------------------------------------- ids

    /**
     * `newContractId` hands out distinct ids, so two contracts opened in the same
     * breath cannot silently overwrite each other on the primary key.
     */
    @Test
    fun `new contract ids are distinct`() {
        val ids = (1..50).map { repository.newContractId() }
        assertEquals(50, ids.toSet().size)
        // UUID form, so the `uuid` column is honest about what it holds.
        assertTrue(ids.first().matches(UUID_PATTERN))
    }

    // -------------------------------------------------- the entity's own guards

    /**
     * An open row whose active columns disagree with `endDate` cannot even be
     * constructed.
     *
     * This is the structural half of the substitute for a partial index: the active
     * columns are a *copy* of the ids, and a copy can drift. `ContractEntity.init`
     * closes that by refusing any row where the two disagree, the same way
     * `VehicleEntity` refuses an un-normalised plate — so a raw `@Insert` cannot land
     * a row that frees a slot still occupied or occupies one that is not.
     */
    @Test
    fun `an open row with a null active vehicle cannot be constructed`() {
        assertThrows(IllegalArgumentException::class.java) {
            openEntity(id = "bad").copy(activeVehicleId = null)
        }
    }

    /** And an *ended* row that still holds the slot is refused too. */
    @Test
    fun `an ended row that still holds a slot cannot be constructed`() {
        assertThrows(IllegalArgumentException::class.java) {
            endedEntity(id = "bad").copy(activeVehicleId = VEHICLE_ID)
        }
        assertThrows(IllegalArgumentException::class.java) {
            endedEntity(id = "bad").copy(activeRenterId = RENTER_ID)
        }
    }

    /**
     * The end-date ordering rule holds at the entity as well as in the domain, so a
     * raw insert cannot land an ended contract that ended before it started.
     */
    @Test
    fun `an end date before the start is refused at the entity`() {
        assertThrows(IllegalArgumentException::class.java) {
            endedEntity(id = "bad").copy(endDate = LocalDate.of(2020, 1, 1))
        }
    }

    /**
     * And the entity refuses a zero rate, so a raw `@Insert` cannot land one either.
     */
    @Test
    fun `a zero rate cannot be constructed into an entity`() {
        val error = assertThrows(InvalidAmountException::class.java) {
            openEntity(id = "bad").copy(dailyRate = 0L)
        }
        assertEquals(0L, error.amount)
    }

    // ------------------------------------------------------- the DAO's own reads

    /**
     * `findById` returns the stored row, and `null` for an id nobody holds.
     *
     * Both DAO methods that no production path calls yet are exercised here rather
     * than left uncovered: `findById` and `update` exist for Story 2.7, which ends a
     * contract by reading it and writing it back, and an unexercised query is one
     * whose column list can drift from the entity without anything noticing. This
     * asserts the round trip through the real schema.
     */
    @Test
    fun `findById returns the stored row and null for an unknown id`() = runBlocking {
        seed()
        database.contractDao().insertContract(openEntity(id = "first"))

        val found = database.contractDao().findById("first")
        assertNotNull("the stored row must be readable by id", found)
        assertEquals("first", found!!.id)
        assertEquals(VEHICLE_ID, found.vehicleId)
        assertEquals(RENTER_ID, found.renterId)
        assertEquals(250_000L, found.dailyRate)
        // `openEntity` writes no rest day, so the converter round-trips `null` — which
        // is itself the seven-day week rather than a missing value.
        assertNull(found.restDay)

        assertNull("an id nobody holds must read as null", database.contractDao().findById("nothing"))
    }

    /**
     * `update` writes a row in place, matched on the primary key.
     *
     * This is the half Story 2.7 will use to set `endDate`, so the row it touches is
     * asserted to be exactly one — a broad write would pass "the contract changed"
     * while corrupting a neighbour. The active columns are cleared in the same update,
     * because that is what ending a contract *means*: the slots are freed and the
     * vehicle becomes rentable again.
     */
    @Test
    fun `update writes one row in place and frees the slots when it ends`() = runBlocking {
        seed()
        database.contractDao().insertContract(openEntity(id = "first"))
        database.contractDao().insertContract(endedEntity(id = "neighbour"))

        // Ending the first contract: an end date, and both active columns cleared —
        // the shape `ContractEntity.init` pins together.
        database.contractDao().update(
            openEntity(id = "first").copy(
                activeVehicleId = null,
                activeRenterId = null,
                endDate = LocalDate.of(2026, 11, 1),
            ),
        )

        val ended = database.contractDao().findById("first")
        assertNotNull(ended)
        assertNull("an ended contract must free the vehicle slot", ended!!.activeVehicleId)
        assertNull("and the renter slot", ended.activeRenterId)
        assertEquals(LocalDate.of(2026, 11, 1), ended.endDate)

        // The neighbour is untouched — the update matched one primary key.
        val neighbour = database.contractDao().findById("neighbour")
        assertNotNull(neighbour)
        assertNull("the neighbour must not have been rewritten", neighbour!!.activeVehicleId)

        // And the freed slot is genuinely usable: a new open contract can take it.
        database.contractDao().insertContract(openEntity(id = "third"))
        assertEquals(3, repository.observeContracts().first().size)
    }

    // -------------------------------------------------- many ended contracts

    /**
     * SQLite treats `NULL`s as distinct in a unique index, so any number of *ended*
     * contracts may share a vehicle. This is what makes the two plain `UNIQUE`
     * indexes behave like the partial index AR-12 asks for, and a column that was
     * `NOT NULL` — or a writer that filled the slot in for ended contracts — would
     * fail here by allowing the owner only one contract per car, ever.
     */
    @Test
    fun `many ended contracts can share one vehicle and one renter`() = runBlocking {
        seed()

        database.contractDao().insertContract(endedEntity(id = "first"))
        database.contractDao().insertContract(endedEntity(id = "second"))
        database.contractDao().insertContract(endedEntity(id = "third"))

        assertEquals(3, repository.observeContracts().first().size)

        // And the slot is still free for a new open contract.
        repository.open(contract(id = "fourth"))
        assertEquals(4, repository.observeContracts().first().size)
    }

    // ------------------------------------------------------------------ helpers

    /** The vehicle and the renter every fixture contract names. */
    private suspend fun seed() {
        database.vehicleDao().insert(vehicleEntity())
        database.renterDao().insert(
            RenterEntity(
                id = RENTER_ID,
                name = RENTER_NAME,
                phone = "08123456789",
                bankAccount = null,
                emergencyContact = "08129876543",
            ),
        )
    }

    private fun vehicleEntity(id: String = VEHICLE_ID, plate: String = PLATE) = VehicleEntity(
        id = id,
        plate = plate,
        brand = "Toyota",
        model = "Avanza",
        year = 2023,
        color = "Hitam",
        dailyRate = VEHICLE_RATE,
        notes = null,
    )

    /**
     * A domain contract, ready to open.
     *
     * Built through `Contract`'s constructor on purpose: that is the path the
     * ViewModel takes, so its checks are exercised rather than bypassed.
     */
    private fun contract(
        id: String = CONTRACT_ID,
        vehicleId: String = VEHICLE_ID,
        startDate: LocalDate = LocalDate.of(2026, 10, 1),
        rate: Long = 250_000L,
        restDay: DayOfWeek? = DayOfWeek.SUNDAY,
    ) = Contract(
        id = id,
        renterId = RENTER_ID,
        vehicleId = vehicleId,
        startDate = startDate,
        dailyRate = rate,
        restDay = restDay,
        endDate = null,
    )

    /** A raw open row, bypassing the repository and the domain model. */
    private fun openEntity(id: String) = ContractEntity(
        id = id,
        renterId = RENTER_ID,
        vehicleId = VEHICLE_ID,
        startDate = LocalDate.of(2026, 10, 1),
        dailyRate = 250_000L,
        restDay = null,
        activeVehicleId = VEHICLE_ID,
        activeRenterId = RENTER_ID,
        endDate = null,
    )

    /** A raw ended row: both active columns `NULL`, which is what frees the slots. */
    private fun endedEntity(id: String) = ContractEntity(
        id = id,
        renterId = RENTER_ID,
        vehicleId = VEHICLE_ID,
        startDate = LocalDate.of(2026, 9, 1),
        dailyRate = 250_000L,
        restDay = null,
        activeVehicleId = null,
        activeRenterId = null,
        endDate = LocalDate.of(2026, 9, 10),
    )

    /**
     * The real DAO with **only its two pre-check lookups blinded, and only once each**.
     *
     * ## Why a wrapper and not a fake
     *
     * The repository's constraint-translation branch is unreachable single-threaded: a
     * pre-check that can see the occupied row always refuses first, so `insertEntity`
     * is never entered. Reaching it needs a caller whose pre-check passes while the
     * slot is in fact taken — which is precisely the state two racing callers produce,
     * and the only way to produce it deterministically in a test.
     *
     * Everything except those two lookups delegates to the real DAO, so the insert
     * that follows is the real insert against the real schema and the real unique
     * indexes, and the `SQLiteConstraintException` the repository catches is the one
     * SQLite actually raised. A fake that reimplemented the write would prove nothing
     * about the constraint, which is the whole point of the test.
     *
     * ## Why each lookup is blinded exactly once
     *
     * `open` asks the same two questions twice: once as the pre-check, and again in the
     * catch block to find out *who* holds the slot. Blinding the method outright would
     * blind both, so the catch would find nothing, rethrow the raw exception, and the
     * test would assert the opposite of what it claims. Returning `null` only on the
     * first call reproduces the race exactly: the pre-check sees a free slot, the write
     * is refused by the index, and the re-read then sees the truth.
     */
    private class BlindPreCheckDao(
        private val delegate: ContractDao,
    ) : ContractDao by delegate {
        private val vehiclePreCheckDone = java.util.concurrent.atomic.AtomicBoolean(false)
        private val renterPreCheckDone = java.util.concurrent.atomic.AtomicBoolean(false)

        /** `null` for the pre-check; the real answer for the catch block's re-read. */
        override suspend fun findActiveByVehicleId(vehicleId: String): ContractEntity? =
            if (vehiclePreCheckDone.compareAndSet(false, true)) {
                null
            } else {
                delegate.findActiveByVehicleId(vehicleId)
            }

        /** The same, on the renter side. */
        override suspend fun findActiveByRenterId(renterId: String): ContractEntity? =
            if (renterPreCheckDone.compareAndSet(false, true)) {
                null
            } else {
                delegate.findActiveByRenterId(renterId)
            }
    }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val PLATE = "B 1235 TF"
        const val VEHICLE_RATE = 250_000L
        const val RENTER_ID = "renter-1"
        const val RENTER_NAME = "Budi Santoso"
        const val CONTRACT_ID = "contract-1"

        /** A canonical UUID string — 8-4-4-4-12 hex digits. */
        val UUID_PATTERN =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    }
}
