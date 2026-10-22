package net.klinik.rental.ui.screens.kontrak

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.ContractDao
import net.klinik.rental.data.db.dao.ServiceExceptionDao
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.ServiceExceptionEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.ServiceException
import net.klinik.rental.domain.validation.ContractAlreadyActiveException
import net.klinik.rental.domain.validation.ContractEndBeforeStartException
import net.klinik.rental.domain.validation.ContractEndInFutureException
import net.klinik.rental.domain.validation.ContractNotOpenException
import net.klinik.rental.domain.validation.DuplicateServiceExceptionDateException
import net.klinik.rental.domain.validation.InvalidAmountException
import net.klinik.rental.domain.validation.ServiceExceptionBeforeContractStartException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        repository = ContractRepository(
            database.contractDao(),
            database.serviceExceptionDao(),
            database,
        )
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
     * flow `MobilViewModel` combines into `VehicleStatus.Tersewa` — so this is
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
        val racing = ContractRepository(blinded, database.serviceExceptionDao(), database)

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
        val racing = ContractRepository(blinded, database.serviceExceptionDao(), database)

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

    // ----------------------------------------------- the EXCEPTION_* matrix rows

    /**
     * `EXCEPTION_ADD` — the happy path, read back through the flow.
     *
     * A `Flow` rather than a one-shot read, for the same reason the contract tests
     * use one: it is what the sheet subscribes to, and it is what makes the AC's "no
     * manual refresh" a structural fact rather than a promise — Room's tracker
     * re-emits with no signal from anywhere in the app.
     */
    @Test
    fun `an exception is stored and observed on its contract`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))

        repository.addServiceException(
            ServiceException(
                id = EXCEPTION_ID,
                contractId = CONTRACT_ID,
                date = EXCEPTION_DAY,
                reason = "Ganti ban, mobil di bengkel",
            )
        )

        val stored = repository.observeServiceExceptions().first()
        assertEquals(1, stored.size)
        assertEquals(EXCEPTION_ID, stored.single().id)
        assertEquals(CONTRACT_ID, stored.single().contractId)
        assertEquals(EXCEPTION_DAY, stored.single().date)
        assertEquals("Ganti ban, mobil di bengkel", stored.single().reason)
    }

    /**
     * `EXCEPTION_DUPLICATE` — the same date twice on one contract is refused, nothing
     * is written, and the refusal names the **stored** date.
     *
     * The message naming the stored row is the part worth pinning: the owner has to
     * learn *which* day is already marked, and the submitted date happens to equal it
     * here — which is exactly why a test that only checked the exception *type* would
     * pass an implementation that carried the wrong value.
     */
    @Test
    fun `a duplicate date on one contract is refused and names the stored date`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))
        repository.addServiceException(exception(id = EXCEPTION_ID, date = EXCEPTION_DAY))

        val error = assertThrows(DuplicateServiceExceptionDateException::class.java) {
            runBlocking { repository.addServiceException(exception(id = "other", date = EXCEPTION_DAY)) }
        }

        assertEquals(EXCEPTION_DAY, error.date)
        assertEquals(CONTRACT_ID, error.contractId)
        assertEquals("the row count must not move", 1, repository.observeServiceExceptions().first().size)
    }

    /**
     * `EXCEPTION_DUPLICATE_INDEX` — the **index** refuses, and the refusal arrives as
     * the duplicate exception rather than a raw `SQLiteConstraintException`.
     *
     * Without the wrapper below, `insertServiceExceptionEntity`'s catch is dead code:
     * a pre-check that can see the row always refuses first. So the pre-check is
     * blinded exactly once and the write goes through to the real `UNIQUE` index —
     * which is the AC's own claim ("the refusal arrives as the duplicate exception
     * naming the stored date, not as a raw `SQLiteConstraintException`").
     */
    @Test
    fun `the index refusal is translated when the pre-check cannot see the row`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))
        repository.addServiceException(exception(id = EXCEPTION_ID, date = EXCEPTION_DAY))

        val blinded = BlindPreCheckExceptionDao(database.serviceExceptionDao())
        val racing = ContractRepository(database.contractDao(), blinded, database)

        val error = assertThrows(DuplicateServiceExceptionDateException::class.java) {
            runBlocking { racing.addServiceException(exception(id = "other", date = EXCEPTION_DAY)) }
        }

        assertEquals("names the stored date", EXCEPTION_DAY, error.date)
        assertEquals("the index must hold the row count at one", 1, repository.observeServiceExceptions().first().size)
    }

    /**
     * The re-read's **untouched** branch: a `SQLiteConstraintException` for which no
     * row is found is rethrown, not relabelled as a duplicate.
     *
     * The load-bearing case is a **primary-key** clash on `id` while the date is free.
     * Relabelling that as "this date is already marked" would tell the owner a date is
     * taken when it is not — and the re-read is what distinguishes the two, so it has
     * to be exercised on its own rather than only through the duplicate path.
     */
    @Test
    fun `a constraint failure with no stored row is rethrown untouched`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))
        repository.addServiceException(exception(id = EXCEPTION_ID, date = EXCEPTION_DAY))

        // Same *id*, different date: the date index is free, so the only thing that
        // can refuse is the primary key.
        val error = assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                repository.addServiceException(
                    exception(id = EXCEPTION_ID, date = EXCEPTION_DAY.plusDays(1))
                )
            }
        }

        assertTrue(
            "the raw SQLite failure must reach the caller, unlabelled: ${error::class.java.name}",
            error is android.database.sqlite.SQLiteConstraintException,
        )
        assertEquals(
            "and only the first row exists",
            1,
            repository.observeServiceExceptions().first().size,
        )
    }

    /**
     * `EXCEPTION_BEFORE_START` — a date before the contract's start is refused, and
     * the refusal names the start date.
     *
     * The reasoning is arithmetic, not policy: `chargeDaysIn` filters
     * `it >= contractStart` *before* it reaches the exception filter, so a row before
     * the start could never stop a charge. Storing it would be an inert row that looks
     * like a working one.
     */
    @Test
    fun `a date before the contract start is refused and names the start date`() = runBlocking {
        seed()
        val start = LocalDate.of(2026, 10, 1)
        repository.open(contract(id = CONTRACT_ID, startDate = start))

        val error = assertThrows(ServiceExceptionBeforeContractStartException::class.java) {
            runBlocking {
                repository.addServiceException(
                    exception(
                        id = EXCEPTION_ID,
                        date = start.minusDays(1),
                    )
                )
            }
        }

        assertEquals("the message names the start date", start, error.startDate)
        assertEquals("nothing is written", 0, repository.observeServiceExceptions().first().size)
    }

    /**
     * `EXCEPTION_ENDED_ADD` — an ended contract refuses the write.
     *
     * Written against the **repository**, not the UI: the AC is explicit that the
     * read-only rule is not a UI check, so a test that only asserted a disabled button
     * would leave the actual guarantee unverified. `isReadOnly` in the sheet is a
     * rendering of this fact, and it can drift.
     */
    @Test
    fun `an ended contract refuses a new exception`() = runBlocking {
        seed()
        database.contractDao().insertContract(endedEntity(id = CONTRACT_ID))

        assertThrows(ContractNotOpenException::class.java) {
            runBlocking {
                repository.addServiceException(
                    exception(id = EXCEPTION_ID, date = LocalDate.of(2026, 9, 15))
                )
            }
        }

        assertEquals("nothing is written", 0, repository.observeServiceExceptions().first().size)
    }

    /**
     * `EXCEPTION_ENDED_REMOVE` — and the same refusal on the way out. The row stays.
     *
     * One exception type covers both directions on purpose: "this contract has ended"
     * is one fact, and two classes would let one of the two paths forget to check it.
     */
    @Test
    fun `an ended contract refuses removing an exception and the row stays`() = runBlocking {
        seed()
        // The row is written while the contract is still open, then the contract ends:
        // an ended contract could not have had one added, so this is the only way to
        // reach the state the matrix row describes.
        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2026, 9, 1)))
        repository.addServiceException(
            exception(id = EXCEPTION_ID, date = LocalDate.of(2026, 9, 15))
        )
        database.contractDao().update(
            endedEntity(id = CONTRACT_ID, startDate = LocalDate.of(2026, 9, 1))
        )

        assertThrows(ContractNotOpenException::class.java) {
            runBlocking { repository.removeServiceException(EXCEPTION_ID, CONTRACT_ID) }
        }

        assertEquals(
            "the row must survive a refused removal",
            1,
            repository.observeServiceExceptions().first().size,
        )
    }

    /**
     * `EXCEPTION_PER_CONTRACT` — the same date on two contracts of **one vehicle** is
     * two rows, because exceptions are keyed on the contract.
     *
     * Two constraints interleave here and the ordering is the whole test. Only one
     * contract per vehicle can be open at a time (Story 2.5), and a *write* on an
     * ended contract is refused (this story) — so the only sequence that produces two
     * exceptions on one vehicle is: open the first, mark a day on it, end it, open the
     * second, mark the same day on that. Written any other way, one of the two
     * refusals fires first and the test would be asserting the wrong rule.
     *
     * The second date is 14 Oct and the first is 1 Sep: both are the same day-of-month
     * minus nothing, which is what makes a per-vehicle key observable as "only one row
     * survived".
     */
    @Test
    fun `the same date on two contracts of one vehicle is two rows`() = runBlocking {
        seedTwoRenters()

        // First contract: open, then marked while it is open.
        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2026, 9, 1)))
        repository.addServiceException(
            exception(id = "e1", date = LocalDate.of(2026, 9, 14))
        )

        // End it through the DAO, which frees the vehicle slot.
        database.contractDao().update(
            endedEntity(
                id = CONTRACT_ID,
                startDate = LocalDate.of(2026, 9, 1),
                endDate = LocalDate.of(2026, 9, 30),
            )
        )

        // Second contract on the same vehicle, then the same day marked on it.
        repository.open(
            contract(
                id = CONTRACT_ID_2,
                startDate = LocalDate.of(2026, 10, 1),
                renterId = RENTER_ID_2,
            )
        )
        repository.addServiceException(
            exception(id = "e2", contractId = CONTRACT_ID_2, date = LocalDate.of(2026, 10, 14))
        )

        val stored = repository.observeServiceExceptions().first()
        assertEquals("one row per contract", 2, stored.size)
        assertEquals(
            setOf(CONTRACT_ID, CONTRACT_ID_2),
            stored.map { it.contractId }.toSet(),
        )
        assertEquals(
            "both carry day 14 of their own month, which a per-vehicle key could not hold",
            setOf(14),
            stored.map { it.date.dayOfMonth }.toSet(),
        )
    }

    /**
     * `EXCEPTION_REMOVE` — the row is gone and the flow re-emits on its own.
     *
     * Also pins the **scoping** the delete predicate buys, using a *real* second
     * contract rather than a made-up id: `requireOpenContract` refuses an id nobody
     * holds, so a fabricated id would raise before the delete ran and this would test
     * the guard instead of the predicate. The right row id paired with the wrong
     * contract removes nothing — which is what keeps a delete from being aimed at any
     * contract the caller happens to hold a row id for.
     */
    @Test
    fun `removing an exception takes the row away and leaves other contracts alone`() = runBlocking {
        seedTwoRenters()
        // A second **open** contract on a second vehicle. Open, because
        // `requireOpenContract` refuses both an unknown id and an ended one — so a
        // fabricated id or an ended contract would raise before the delete ran, and
        // this would end up testing the guard rather than the predicate. The row under
        // test belongs to the *first* contract.
        database.vehicleDao().insert(vehicleEntity(id = VEHICLE_ID_2, plate = "B 9999 TF"))
        repository.open(contract(id = CONTRACT_ID))
        repository.open(
            contract(id = CONTRACT_ID_2, vehicleId = VEHICLE_ID_2, renterId = RENTER_ID_2)
        )
        repository.addServiceException(exception(id = EXCEPTION_ID, date = EXCEPTION_DAY))

        // The right id, the wrong contract: a no-op, not a delete.
        repository.removeServiceException(EXCEPTION_ID, CONTRACT_ID_2)
        assertEquals(
            "a delete must be scoped to its own contract",
            1,
            repository.observeServiceExceptions().first().size,
        )

        repository.removeServiceException(EXCEPTION_ID, CONTRACT_ID)
        assertEquals(
            "the flow re-emits with the row gone, no manual refresh",
            0,
            repository.observeServiceExceptions().first().size,
        )
    }

    // --------------------------------------------------- the END_* matrix rows

    /**
     * `END_ATOMIC` — the AC's AD-4 clause, and the test that carries the story.
     *
     * "Status Kontrak, Tunggakan, dan status Kendaraan tidak boleh separuh berubah" is
     * enforced here by observing the row **once** and asserting all three facts in that
     * single read. A separate observation per fact would pass an implementation that
     * wrote them in two statements — which is precisely the half-change AD-4 forbids,
     * and precisely the state a reader cannot detect afterwards.
     */
    @Test
    fun `ending sets the end date and frees both slots in one observation`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))

        repository.endContract(CONTRACT_ID, END_DAY, asOf = END_DAY)

        // ONE read, three assertions. This is the whole claim.
        val row = requireNotNull(database.contractDao().findById(CONTRACT_ID))
        assertEquals("the end date is stored verbatim", END_DAY, row.endDate)
        assertNull(
            "the vehicle's active slot is freed",
            row.activeVehicleId,
        )
        assertNull(
            "and the renter's, in the same write — not a second statement",
            row.activeRenterId,
        )
        assertFalse("and the row now reads as ended", row.endDate == null)
    }

    /**
     * And the vehicle reads `tersedia` with **no** sync action — asserted through the
     * same derived query every other screen uses, not through a status column.
     *
     * There is no refresh method anywhere in this test, and none to call, because
     * `VehicleDao.hasOpenContract` and `observeRented` both ask `endDate IS NULL`. The
     * AC's "tidak ada tombol sinkronisasi manual" is satisfied by *not having* a second
     * status that could need synchronising.
     */
    @Test
    fun `the vehicle reads available after the contract ends, with no sync call`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))
        assertTrue(
            "precondition: an open contract means a rented vehicle",
            database.vehicleDao().hasOpenContract(VEHICLE_ID),
        )

        repository.endContract(CONTRACT_ID, END_DAY, asOf = END_DAY)

        assertFalse(
            "the same derived query now answers no",
            database.vehicleDao().hasOpenContract(VEHICLE_ID),
        )
        assertTrue(
            "and the rented list is empty — re-emitted by Room, not pushed",
            database.vehicleDao().observeRented().first().isEmpty(),
        )
        // And the fleet list still holds the vehicle: it became *available*, not absent.
        assertEquals(1, database.vehicleDao().observeAll().first().size)
    }

    /**
     * `END_BEFORE_START` — refused, the message names the contract's own start date,
     * and **the row is still open**.
     *
     * The last clause is the one that matters: a refusal that wrote `endDate` anyway, or
     * that freed the slots before checking, would leave the owner's car available because
     * of a rejected action.
     */
    @Test
    fun `an end date before the contract start is refused and the row stays open`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2026, 10, 1)))
        val endDate = LocalDate.of(2026, 9, 30)

        val error = assertThrows(ContractEndBeforeStartException::class.java) {
            runBlocking { repository.endContract(CONTRACT_ID, endDate, asOf = END_DAY) }
        }

        assertEquals("names the contract's own start date", LocalDate.of(2026, 10, 1), error.startDate)
        val row = requireNotNull(database.contractDao().findById(CONTRACT_ID))
        assertNull("nothing was written", row.endDate)
        assertEquals("and the vehicle is still held", VEHICLE_ID, row.activeVehicleId)
    }

    /**
     * `END_ALREADY_ENDED` — a second end is refused and the **first** end date stands.
     *
     * The "unchanged" half matters: an implementation that assigned the new date before
     * checking would report a refusal and still overwrite the owner's figure.
     */
    @Test
    fun `an already-ended contract refuses a second end and keeps its first date`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))
        repository.endContract(CONTRACT_ID, END_DAY, asOf = END_DAY)

        assertThrows(ContractNotOpenException::class.java) {
            runBlocking { repository.endContract(CONTRACT_ID, LocalDate.of(2026, 10, 25), asOf = END_DAY) }
        }

        assertEquals(
            "the original end date is untouched",
            END_DAY,
            database.contractDao().findById(CONTRACT_ID)!!.endDate,
        )
    }

    /**
     * `END_ON_END` — ending on the start date is a real rental, so it is accepted.
     *
     * The degenerate case, and the one that settles the inclusive rule from the other
     * direction: a same-day rental charged for exactly one day is a coherent thing, and
     * an implementation that clamped `endDate` to "at least the start" would pass this
     * while still refusing the day *before*, which the next test covers.
     */
    @Test
    fun `a contract can end on its start date`() = runBlocking {
        val start = LocalDate.of(2026, 10, 1)
        seed()
        repository.open(contract(id = CONTRACT_ID, startDate = start))

        repository.endContract(CONTRACT_ID, start, asOf = END_DAY)

        assertEquals(start, database.contractDao().findById(CONTRACT_ID)!!.endDate)
    }

    /**
     * `END_OK_BEFORE_TODAY` — an end date in the **past** is stored verbatim.
     *
     * A contract that ended before this app existed is real, and refusing a past date
     * would make the owner's own history unrecordable — which is the same reasoning
     * `open` accepts a start date in the past for (FR-7).
     */
    @Test
    fun `an end date in the past is stored verbatim`() = runBlocking {
        val past = LocalDate.of(2025, 3, 15)
        seed()
        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2025, 3, 1)))

        repository.endContract(CONTRACT_ID, past, asOf = END_DAY)

        assertEquals(past, database.contractDao().findById(CONTRACT_ID)!!.endDate)
    }

    /**
     * `END_FREES_SLOTS` — the same vehicle **and** the same renter can be rented again.
     *
     * Both halves in one test, because they are released by the same write and an
     * implementation that cleared only one would still pass a test of either alone. This
     * is what SQLite treating `NULL`s as distinct in a unique index buys, and it is why
     * no `DROP INDEX` or rebalancing step exists anywhere in this story.
     */
    @Test
    fun `ending frees both the vehicle and the renter for a new contract`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))
        repository.endContract(CONTRACT_ID, END_DAY, asOf = END_DAY)

        // Same vehicle, same renter, both formerly held. Neither index may refuse.
        repository.open(contract(id = CONTRACT_ID_2, startDate = LocalDate.of(2026, 11, 1)))

        val second = requireNotNull(database.contractDao().findById(CONTRACT_ID_2))
        assertNull("the new contract starts open", second.endDate)
        assertEquals("and holds the vehicle slot", VEHICLE_ID, second.activeVehicleId)
        assertEquals(2, repository.observeContracts().first().size)
    }

    /**
     * `END_PRESERVES_EXCEPTIONS` — the compensation history survives, and stays read-only.
     *
     * Both halves: the rows are still there *and* the write that would change them is
     * refused. An ended contract whose exceptions quietly became editable, or quietly
     * disappeared, would corrupt the record of what was not billed — and the FK is
     * `RESTRICT`, so ending itself cannot have taken them.
     */
    @Test
    fun `ending preserves the exceptions and they stay read-only`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))
        repository.addServiceException(exception(id = EXCEPTION_ID, date = EXCEPTION_DAY))
        repository.addServiceException(exception(id = "e2", date = LocalDate.of(2026, 10, 16)))

        repository.endContract(CONTRACT_ID, END_DAY, asOf = END_DAY)

        val stored = repository.observeServiceExceptions().first()
        assertEquals("every exception row survives", 2, stored.size)
        assertEquals(
            "with its reasons intact",
            setOf("Ganti ban, mobil di bengkel", "Ganti ban, mobil di bengkel"),
            stored.map { it.reason }.toSet(),
        )
        assertThrows(ContractNotOpenException::class.java) {
            runBlocking {
                repository.addServiceException(
                    exception(id = "e3", date = LocalDate.of(2026, 10, 21))
                )
            }
        }
        assertThrows(ContractNotOpenException::class.java) {
            runBlocking { repository.removeServiceException(EXCEPTION_ID, CONTRACT_ID) }
        }
        assertEquals("and neither refusal removed anything", 2, repository.observeServiceExceptions().first().size)
    }

    /**
     * `END_LIST_ORDER` — an ended contract stays in the observed list, in its place.
     *
     * The row is neither deleted nor filtered out. This is the storage half of "kontrak
     * yang sudah berakhir tetap dihitung tunggakannya": whatever reads arrears later has
     * to find the row, and a list that dropped it would make that impossible while every
     * screen here still looked correct.
     */
    @Test
    fun `an ended contract stays in the observed list`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2026, 9, 1)))
        repository.endContract(CONTRACT_ID, LocalDate.of(2026, 9, 20), asOf = END_DAY)
        repository.open(contract(id = CONTRACT_ID_2, startDate = LocalDate.of(2026, 10, 1)))

        val observed = repository.observeContracts().first()
        assertEquals("both contracts are still observed", 2, observed.size)
        // Named by contract id rather than by sort order: `observeContracts` orders
        // descending by start date, and a test that hard-codes "first is the ended one"
        // would break the moment the order changed without anything about the story
        // having changed.
        assertEquals(
            "the ended one is marked as ended rather than absent",
            true,
            observed.single { it.id == CONTRACT_ID }.endDate != null,
        )
        assertEquals(
            "and the new one is still open",
            null,
            observed.single { it.id == CONTRACT_ID_2 }.endDate,
        )
    }

    /**
     * `END_PRESERVES_SURPLUS` — ending touches **exactly one row**.
     *
     * There is no `ALLOCATION` table yet, so the strong version of this claim — counting
     * allocation rows before and after — is not writable today. What *is* testable, and
     * what actually makes the surplus safe, is asserted here: the write is a single row
     * update on `contract`, and no `ON DELETE CASCADE` on that table could reach a money
     * record even if a delete were tried.
     *
     * The row count is read back through the DAO rather than asserted from the returned
     * value, so a future implementation that also wrote an allocation or credit row
     * would be caught here rather than discovered as a wrong bill.
     *
     * Story 3.6 owns the stronger version — it is where the allocation table arrives.
     */
    @Test
    fun `ending writes one row and nothing else`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))

        repository.endContract(CONTRACT_ID, END_DAY, asOf = END_DAY)

        // Every other table this story could plausibly have written to.
        assertEquals("the vehicle is untouched", 1, database.vehicleDao().observeAll().first().size)
        assertEquals("the renter is untouched", 1, database.renterDao().observeAll().first().size)
        assertEquals("the exceptions are untouched", 0, repository.observeServiceExceptions().first().size)
        assertEquals(
            "the contract row is the only one that changed, and it is still one row",
            1,
            database.contractDao().allContracts().first().size,
        )
        // And the contract table has no cascade that could reach another table. The
        // direction matters and is the whole point: `PRAGMA foreign_key_list(contract)`
        // lists the tables `contract` *points at* (`renter`, `vehicle`), which constrains
        // what it may reference. What a write to `contract` could cascade into is the
        // tables that point *at* it — and `service_exception` is the only one, declaring
        // `onDelete = RESTRICT`, so even a delete could not take the history.
        val cascadesOut = database.openHelper.writableDatabase
            .query("PRAGMA foreign_key_list(`service_exception`)").use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor else null }
                    .mapNotNull { row ->
                        val onDelete = row.getString(row.getColumnIndexOrThrow("on_delete"))
                        if (onDelete == "CASCADE") row.getString(row.getColumnIndexOrThrow("table")) else null
                    }
                    .toList()
            }
        assertTrue(
            "nothing cascades out of service_exception either, so an end cannot reach a money row: $cascadesOut",
            cascadesOut.isEmpty(),
        )
    }

    /**
     * `FUTURE` — an end date one day after today is refused, and **the row is untouched**.
     *
     * This is the row that closes the Story 2.7 deferral. `Contract.isOpen` is
     * `endDate == null`, so writing *any* date ends the contract on the spot: a date one
     * day out would free the car today and stop the charges today, for a rental that is
     * still running. The refusal is the whole fix — storing the date and keeping the car
     * occupied until it arrives would need `isOpen` to take a date.
     *
     * The three assertions are the AC's own: the contract stays open, the car still reads
     * rented, and the message names the day it compared against.
     */
    @Test
    fun `an end date after today is refused and the row stays open`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))
        val tomorrow = END_DAY.plusDays(1)

        val error = assertThrows(ContractEndInFutureException::class.java) {
            runBlocking { repository.endContract(CONTRACT_ID, tomorrow, asOf = END_DAY) }
        }

        assertEquals("names the day it compared against", END_DAY, error.asOf)
        assertEquals("and the contract it refused", CONTRACT_ID, error.contractId)
        val row = requireNotNull(database.contractDao().findById(CONTRACT_ID))
        assertNull("nothing was written", row.endDate)
        assertEquals("and the vehicle is still held", VEHICLE_ID, row.activeVehicleId)
        assertEquals("and the renter's slot too", RENTER_ID, row.activeRenterId)
        assertTrue(
            "so the derived query still answers rented",
            database.vehicleDao().hasOpenContract(VEHICLE_ID),
        )
    }

    /**
     * `FUTURE_FAR` — six weeks out is refused by the same guard, not a range check.
     *
     * One day and six weeks must produce the same refusal: a rule that only caught "the
     * near future" would still free a car for a rental with weeks to run.
     */
    @Test
    fun `an end date six weeks out is refused the same way`() = runBlocking {
        seed()
        repository.open(contract(id = CONTRACT_ID))
        val farFuture = END_DAY.plusWeeks(6)

        val error = assertThrows(ContractEndInFutureException::class.java) {
            runBlocking { repository.endContract(CONTRACT_ID, farFuture, asOf = END_DAY) }
        }

        assertEquals(END_DAY, error.asOf)
        assertNull(
            "and the row is still open",
            database.contractDao().findById(CONTRACT_ID)!!.endDate,
        )
        assertTrue(database.vehicleDao().hasOpenContract(VEHICLE_ID))
    }

    /**
     * `ORDER` — a date that is **both** in the future and before the start reports the
     * start-date refusal first.
     *
     * The start-date message names a figure the owner can act on; "too far ahead" would be
     * a second complaint about the same field, and one of the two has to win. The start
     * date wins because it is the constraint the owner cannot change — the end date simply
     * has to be on or after it — while "not after today" is the softer of the two.
     *
     * Reachable only when a contract's start date is itself in the future, which the app
     * allows: a start date in the past is accepted, and nothing forbids a future one.
     */
    @Test
    fun `a future date before the start reports the start-date refusal first`() = runBlocking {
        seed()
        val start = END_DAY.plusDays(10)
        repository.open(contract(id = CONTRACT_ID, startDate = start))

        val error = assertThrows(ContractEndBeforeStartException::class.java) {
            runBlocking { repository.endContract(CONTRACT_ID, END_DAY.plusDays(5), asOf = END_DAY) }
        }

        assertEquals("the start date, not today", start, error.startDate)
        assertNull(
            "and nothing was written",
            database.contractDao().findById(CONTRACT_ID)!!.endDate,
        )
    }

    /**
     * `OPEN_ENDED` — an `open` call whose `Contract` is already ended is refused, and
     * nothing is written.
     *
     * `Contract.isOpen` is `endDate == null`, and `toEntity()` derives both active columns
     * from it — so writing an ended contract through `open` would insert a row with both
     * slots NULL and the two `UNIQUE` active indexes would never see it. That is the
     * structural failure mode the indexes exist to prevent, reached through the wrong
     * entry point. No caller in the app reaches this today (`endContract` is the only
     * method that holds an ended `Contract`), so the test constructs one directly via the
     * domain factory, which is exactly the path a future caller would take.
     *
     * The success half is asserted in the same test: an `isOpen == true` contract still
     * reaches the write, so a regression that broke both halves cannot pass.
     */
    @Test
    fun `an open call whose contract is already ended is refused and nothing is written`() = runBlocking {
        seed()
        val ended = Contract(
            id = CONTRACT_ID,
            renterId = RENTER_ID,
            vehicleId = VEHICLE_ID,
            startDate = LocalDate.of(2026, 10, 1),
            dailyRate = 250_000L,
            restDay = DayOfWeek.SUNDAY,
            endDate = LocalDate.of(2026, 10, 15),
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.open(ended) }
        }

        val row = database.contractDao().findById(CONTRACT_ID)
        assertNull("the refused row never landed", row)

        // The success half: an open contract still takes the original path.
        repository.open(contract(id = CONTRACT_ID))
        val written = requireNotNull(database.contractDao().findById(CONTRACT_ID))
        assertEquals("written through the guarded path", VEHICLE_ID, written.activeVehicleId)
        assertEquals("with the renter slot set", RENTER_ID, written.activeRenterId)
    }

    /**
     * A contract that has not started yet has **no** end date it can accept.
     *
     * `open()` permits a future start date, so this is reachable through the form. Under
     * `isOpen = endDate == null` such a contract already holds its car from the moment it
     * is written, so "end it before it begins" has no date satisfying both
     * `endDate >= startDate` and `endDate <= asOf`. Both sides of that impossibility are
     * asserted here, because each produces a different — and each a truthful — message.
     *
     * Pinned rather than left as a comment: if a later change makes `open()` refuse a
     * future start date, this test is where that decision becomes visible.
     */
    @Test
    fun `a contract that has not started refuses every end date`() = runBlocking {
        seed()
        val start = END_DAY.plusDays(10)
        repository.open(contract(id = CONTRACT_ID, startDate = start))

        // Below the start: the start-date message names the day to wait for.
        val before = assertThrows(ContractEndBeforeStartException::class.java) {
            runBlocking { repository.endContract(CONTRACT_ID, END_DAY, asOf = END_DAY) }
        }
        assertEquals("the start date it cannot reach", start, before.startDate)

        // On the start or later: still ahead of today, so the future guard answers — and
        // it names today, which is the other half of why no date works.
        val onStart = assertThrows(ContractEndInFutureException::class.java) {
            runBlocking { repository.endContract(CONTRACT_ID, start, asOf = END_DAY) }
        }
        assertEquals("today, the day it is measured against", END_DAY, onStart.asOf)

        assertNull(
            "and nothing was written either time",
            database.contractDao().findById(CONTRACT_ID)!!.endDate,
        )
        assertTrue(
            "so the car stays held for the rental that has not begun",
            database.vehicleDao().hasOpenContract(VEHICLE_ID),
        )
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

    /**
     * [seed] plus a second renter, for the one exception test that needs two contracts
     * on one vehicle — which needs two renters, because `UNIQUE(renter.name)` and the
     * one-active-per-renter rule both stand in the way of reusing the first.
     */
    private suspend fun seedTwoRenters() {
        seed()
        database.renterDao().insert(
            RenterEntity(
                id = RENTER_ID_2,
                name = RENTER_NAME_2,
                phone = "08123456788",
                bankAccount = null,
                emergencyContact = "08129876544",
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
        renterId: String = RENTER_ID,
    ) = Contract(
        id = id,
        renterId = renterId,
        vehicleId = vehicleId,
        startDate = startDate,
        dailyRate = rate,
        restDay = restDay,
        endDate = null,
    )

    /** A raw open row, bypassing the repository and the domain model. */
    private fun openEntity(id: String, renterId: String = RENTER_ID) = ContractEntity(
        id = id,
        renterId = renterId,
        vehicleId = VEHICLE_ID,
        startDate = LocalDate.of(2026, 10, 1),
        dailyRate = 250_000L,
        restDay = null,
        activeVehicleId = VEHICLE_ID,
        activeRenterId = renterId,
        endDate = null,
    )

    /** A raw ended row: both active columns `NULL`, which is what frees the slots. */
    private fun endedEntity(
        id: String,
        startDate: LocalDate = LocalDate.of(2026, 9, 1),
        endDate: LocalDate = LocalDate.of(2026, 9, 10),
        renterId: String = RENTER_ID,
    ) = ContractEntity(
        id = id,
        renterId = renterId,
        vehicleId = VEHICLE_ID,
        startDate = startDate,
        dailyRate = 250_000L,
        restDay = null,
        activeVehicleId = null,
        activeRenterId = null,
        endDate = endDate,
    )

    /**
     * A domain service exception, ready to store.
     *
     * [date] defaults to a Wednesday inside the fixture contract's period, so the
     * rest day is never in play and a failure can only be about the exception.
     */
    private fun exception(
        id: String,
        date: LocalDate = EXCEPTION_DAY,
        contractId: String = CONTRACT_ID,
        reason: String = "Ganti ban, mobil di bengkel",
    ) = ServiceException(
        id = id,
        contractId = contractId,
        date = date,
        reason = reason,
    )

    /**
     * The real exception DAO with **only its duplicate pre-check blinded, and only
     * once**.
     *
     * The same shape and the same reason as [BlindPreCheckDao]: `addServiceException`
     * asks `findByContractAndDate` twice — once as the pre-check, again inside the
     * catch to find out *who* holds the `(contractId, date)` slot. Blinding it outright
     * would blind both, so the catch would find nothing, rethrow the raw
     * `SQLiteConstraintException`, and the test would assert the opposite of what it
     * claims. `null` on the first call and the real answer afterwards reproduces the
     * race exactly.
     */
    private class BlindPreCheckExceptionDao(
        private val delegate: ServiceExceptionDao,
    ) : ServiceExceptionDao by delegate {
        private val preCheckDone = java.util.concurrent.atomic.AtomicBoolean(false)

        /** `null` for the pre-check; the real row for the catch block's re-read. */
        override suspend fun findByContractAndDate(
            contractId: String,
            date: LocalDate,
        ): ServiceExceptionEntity? =
            if (preCheckDone.compareAndSet(false, true)) {
                null
            } else {
                delegate.findByContractAndDate(contractId, date)
            }
    }

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
        const val CONTRACT_ID_2 = "contract-2"
        const val RENTER_ID_2 = "renter-2"
        const val RENTER_NAME_2 = "Siti Aminah"
        const val VEHICLE_ID_2 = "vehicle-2"
        const val EXCEPTION_ID = "exception-1"

        /** A Wednesday inside the fixture contract's October period. */
        val EXCEPTION_DAY: LocalDate = LocalDate.of(2026, 10, 14)

        /**
         * An arbitrary pivot inside the fixture contract's period, and a Wednesday.
         *
         * **Not "today."** Nothing in this file has a clock — the repository takes `asOf` as
         * a parameter precisely so it has none — so this constant plays the role of whatever
         * day a case needs and is passed as `asOf` wherever a case needs one. Its own value
         * (20 Oct 2026) carries no meaning; moving it would not change what any of these
         * tests proves.
         */
        val END_DAY: LocalDate = LocalDate.of(2026, 10, 20)

        /** A canonical UUID string — 8-4-4-4-12 hex digits. */
        val UUID_PATTERN =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    }
}
