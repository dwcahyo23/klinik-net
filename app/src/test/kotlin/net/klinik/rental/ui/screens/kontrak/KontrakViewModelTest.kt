package net.klinik.rental.ui.screens.kontrak

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.ContractDao
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.ui.strings.RStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The end-to-end open path: the ViewModel, the repository, Room's invalidation
 * tracker and `combine`, against a real database.
 *
 * ## Why this exists when the layers below are already tested
 *
 * `ContractRepositoryTest` proves the write and the refusals. `ContractFormTest`
 * proves the form's own rules. This file proves the two are wired to each other —
 * the claims that are individually true and jointly untested:
 *
 *  - an opened contract appears in `uiState.rows` **with no manual refresh**,
 *    because Room's invalidation tracker re-emits and nothing in the app signals
 *  - the list joins the renter's name and the vehicle's plate in Kotlin, with no
 *    SQL JOIN and no second reader of either table
 *  - the copy for a refusal comes from `RStrings`, so it is Indonesian and names the
 *    blocking plate or person
 *  - a second Save while one is in flight is ignored
 *
 * ## Why `Dispatchers.Main` is swapped, and why the repository is built without a database
 *
 * `viewModelScope` runs on `Dispatchers.Main`, which does not exist on the JVM
 * without Robolectric's main looper being driven. A `StandardTestDispatcher` is
 * installed instead so `launch` bodies are queued and drained deterministically by
 * `runTest`, which is what makes these assertions about *after the write settled*
 * rather than about *eventually*.
 *
 * ## Why the repository is built over the DAO alone
 *
 * `ContractRepository.inTransaction` calls `RoomDatabase.withTransaction` when it has
 * a database handle, and that call asserts it is *not* on the main thread —
 * `assertNotMainThread` — because a transaction is a blocking call that would lock
 * the UI. Under Robolectric, `StandardTestDispatcher` drains on the main thread, so
 * passing the database here would make every `open` throw before it wrote anything,
 * and the assertions below would all read an empty list.
 *
 * Leaving the handle out is not a weakening of the test: `inTransaction` falls back
 * to running the block directly, and the transaction is *not* the rule — the two
 * `UNIQUE` indexes are, and they hold whatever the caller does. What is not covered
 * here is the isolation of the pre-checks, which is `ContractRepositoryTest`'s to
 * assert: it passes the database and drives `runBlocking` off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KontrakViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var database: KlinikDatabase
    private lateinit var contracts: ContractRepository
    private lateinit var renters: RenterRepository
    private lateinit var vehicles: VehicleRepository
    private lateinit var strings: RStrings

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, KlinikDatabase::class.java)
            // Room runs queries on its own executor, which `advanceUntilIdle()` does
            // not drain — so a row written by `open` would sit in the database while
            // `uiState.value` still held the pre-write emission, and every assertion
            // below would read an empty list. Pointing both executors at the test
            // dispatcher puts Room's work on the same virtual clock the assertions
            // advance, which is what makes "after the write settled" mean settled
            // rather than "eventually".
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        // No database handle — see the class KDoc.
        contracts = ContractRepository(database.contractDao())
        renters = RenterRepository(database.renterDao())
        vehicles = VehicleRepository(database.vehicleDao())
        strings = RStrings(context)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // -------------------------------------------------- the write reaches the list

    /**
     * The AC's central claim: a contract opened from the form is visible in the list
     * with no manual refresh.
     *
     * There is no refresh call anywhere in this test — no method to call even if
     * there were one — because the guarantee is satisfied by *not having* a refresh
     * path at all. Room's invalidation tracker re-emits, `combine` rebuilds, and the
     * row appears.
     */
    @Test
    fun `an opened contract appears in the list with no manual refresh`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        openAndSave(viewModel)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.rows.size)
        assertEquals("B 1235 TF", state.rows.single().vehiclePlate)
        assertEquals(1, state.contractCount)
    }

    /**
     * A successful save closes the form and confirms in Indonesian, naming the
     * vehicle so the confirmation is recognisable.
     */
    @Test
    fun `a successful save closes the form and confirms in Indonesian`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull("the form must be open before saving", viewModel.uiState.value.form)

        openAndSave(viewModel)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("the form must close on success", viewModel.uiState.value.form)
        assertEquals("Kontrak B 1235 TF tersimpan", viewModel.snackbarMessage.value)
    }

    /**
     * The stored contract carries the frozen rate and the schedule the form was set
     * to — read back through the raw column, bypassing every mapping.
     */
    @Test
    fun `the stored row carries the rate and the schedule from the form`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        openAndSave(viewModel, rateDigits = "310000", restDay = DayOfWeek.WEDNESDAY)
        dispatcher.scheduler.advanceUntilIdle()

        val stored = database.contractDao().findById(viewModel.uiState.value.rows.single().contract.id)
        assertNotNull(stored)
        assertEquals(310_000L, stored!!.dailyRate)
        assertEquals(DayOfWeek.WEDNESDAY, stored.restDay)
        assertNull("the contract is open", stored.endDate)
        assertEquals(
            "the active column is what the one-active index reads",
            stored.vehicleId,
            stored.activeVehicleId,
        )
    }

    // ------------------------------------------------------------ the name join

    /**
     * The list names the renter and the plate, joined in Kotlin from the flows the
     * ViewModel already combines.
     *
     * ## Why this is the test for "no SQL JOIN"
     *
     * `ContractRow` carries both names, and they can only come from the two observed
     * flows — `Contract` holds ids and nothing else. So a row that renders the right
     * name and plate *is* the proof that the join happened in Kotlin, without
     * asserting anything about the query text. A JOIN would have needed a row POJO
     * and a second reader of `renter` and `vehicle`, which is the two-readers problem
     * AD-5 exists to prevent.
     *
     * The two pre-rendered strings are asserted too, so the screen assembles neither
     * money nor a date.
     */
    @Test
    fun `the list joins the renter name and the plate in Kotlin`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        openAndSave(viewModel)
        dispatcher.scheduler.advanceUntilIdle()

        val row = viewModel.uiState.value.rows.single()
        assertEquals("Budi Santoso", row.renterName)
        assertEquals("B 1235 TF", row.vehiclePlate)
        assertEquals("Rp 250.000", row.rateText)
        // `1 Okt` — the **month** form. 1 October 2026 is a Thursday, so the weekday
        // form would render `1 Kam`; the list carries the month because two rentals
        // begun on the 1st of different months must not collide.
        assertEquals("1 Okt", row.dateText)
    }

    /**
     * The join is *live*: renaming a renter re-emits the contract list with the new
     * name, without the contract row being touched.
     *
     * This is the property the Kotlin join buys over a stored name — the row always
     * reads the current renter, so a corrected spelling appears everywhere at once.
     * A denormalised name column on the contract would keep showing the old one.
     */
    @Test
    fun `renaming a renter re-emits the contract list`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        openAndSave(viewModel)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Budi Santoso", viewModel.uiState.value.rows.single().renterName)

        renters.save(renter().copy(name = "Budi Santoso Jr"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the list must read the renter's current name, not a copy of it",
            "Budi Santoso Jr",
            viewModel.uiState.value.rows.single().renterName,
        )
    }

    /**
     * A contract whose renter or vehicle is not in the observed flows still renders,
     * falling back to the stored id rather than blanking the row.
     *
     * ## Why this is asserted on the pure projection and not through the ViewModel
     *
     * The foreign keys make the situation unreachable in production, and the
     * `combine` cannot be driven into it: `KontrakViewModel` always observes all
     * three tables, so a contract row that exists always has its parents. Reaching it
     * through the ViewModel would mean deleting a parent, which the foreign key
     * refuses — so the fallback is exercised where it lives, as
     * `Contract.toRow` with an empty map.
     *
     * It is worth asserting at all because the alternative to a fallback is a crash
     * on a screen the owner is reading, and because a `?:` is exactly the kind of
     * thing that silently becomes `!!` the first time someone "simplifies" it.
     */
    @Test
    fun `a contract whose names are absent falls back to the stored ids`() {
        val contract = net.klinik.rental.domain.model.Contract(
            id = "c1",
            renterId = "renter-1",
            vehicleId = "vehicle-1",
            startDate = LocalDate.of(2026, 10, 1),
            dailyRate = 250_000L,
        )

        val row = contract.toRow(renterNames = emptyMap(), vehiclePlates = emptyMap())

        assertEquals("renter-1", row.renterName)
        assertEquals("vehicle-1", row.vehiclePlate)
        // The two rendered fields are still produced, so the row is not half-built.
        assertEquals("Rp 250.000", row.rateText)
        assertEquals("1 Okt", row.dateText)
    }

    /**
     * And with the maps present, the names win — the other half of the same branch.
     */
    @Test
    fun `a contract with its names present uses them`() {
        val contract = net.klinik.rental.domain.model.Contract(
            id = "c1",
            renterId = "renter-1",
            vehicleId = "vehicle-1",
            startDate = LocalDate.of(2026, 10, 1),
            dailyRate = 250_000L,
        )

        val row = contract.toRow(
            renterNames = mapOf("renter-1" to "Budi Santoso"),
            vehiclePlates = mapOf("vehicle-1" to "B 1235 TF"),
        )

        assertEquals("Budi Santoso", row.renterName)
        assertEquals("B 1235 TF", row.vehiclePlate)
    }

    // ------------------------------------------------------------ the refusals

    /**
     * OPEN_VEHICLE_BUSY through the ViewModel: the refusal names the *plate* and
     * lands on the vehicle field, and the sheet stays open.
     *
     * A field-level message is the right surface because the sheet stays open — the
     * owner is still looking at the form, so the form is where the reason belongs.
     */
    @Test
    fun `a busy vehicle is refused on the vehicle field naming the plate`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        openAndSave(viewModel)
        dispatcher.scheduler.advanceUntilIdle()

        // The form closed on success, so reopen it and try the same car again.
        viewModel.openAddForm()
        viewModel.onFormChange {
            it.selectRenter(renter())
                .selectVehicle(vehicle())
                .selectStartDate(LocalDate.of(2026, 10, 1))
        }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull("the sheet must stay open on a refused save", form)
        assertEquals(
            "B 1235 TF masih tersewa. Akhiri kontraknya dulu sebelum membuka yang baru.",
            form!!.vehicleError,
        )
        assertNull("the renter must not be blamed", form.renterError)
        assertNull(
            "the screen-level error must stay empty for a field fault",
            viewModel.uiState.value.errorMessage,
        )
        assertEquals(1, viewModel.uiState.value.rows.size)
    }

    /**
     * OPEN_RENTER_BUSY: the refusal names the *person* and lands on the renter field.
     *
     * A second vehicle is seeded so the vehicle slot is free and the renter's is the
     * only one that can refuse — without it the vehicle check would fire first and
     * this test would pass while proving nothing about the renter half.
     */
    @Test
    fun `a busy renter is refused on the renter field naming the name`() = runTest(dispatcher) {
        seed()
        database.vehicleDao().insert(vehicleEntity(id = "vehicle-2", plate = "B 9876 AB"))
        val viewModel = observingViewModel()

        openAndSave(viewModel)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openAddForm()
        viewModel.onFormChange {
            it.selectRenter(renter())
                .selectVehicle(vehicle(id = "vehicle-2", plate = "B 9876 AB"))
                .selectStartDate(LocalDate.of(2026, 10, 1))
        }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull("the sheet must stay open on a refused save", form)
        assertEquals(
            "Budi Santoso masih menyewa mobil lain. Akhiri kontraknya dulu sebelum membuka yang baru.",
            form!!.renterError,
        )
        assertEquals(1, viewModel.uiState.value.rows.size)
    }

    /**
     * OPEN_NO_SELECTION: Save with nothing picked writes nothing, and the message
     * lands on the renter — the first field on the sheet.
     */
    @Test
    fun `Save with nothing selected is refused on the renter field`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull("the sheet must stay open on a refused save", form)
        assertEquals(
            "Wajib diisi. Pilih penyewa lalu kirim ulang.",
            form!!.renterError,
        )
        assertEquals(0, contracts.observeContracts().first().size)
    }

    /** And with a renter but no vehicle, the message moves to the vehicle field. */
    @Test
    fun `Save without a vehicle is refused on the vehicle field`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(renter()) }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull(form)
        assertEquals(
            "Wajib diisi. Pilih mobil lalu kirim ulang.",
            form!!.vehicleError,
        )
        assertNull("the renter was picked and must not be blamed", form.renterError)
        assertEquals(0, contracts.observeContracts().first().size)
    }

    /** And with both picked but no date, the message lands on the date field. */
    @Test
    fun `Save without a date is refused on the date field`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(renter()).selectVehicle(vehicle()) }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull(form)
        assertEquals(
            "Wajib diisi. Pilih tanggal mulai lalu kirim ulang.",
            form!!.dateError,
        )
        assertEquals(0, contracts.observeContracts().first().size)
    }

    /**
     * OPEN_RATE_ZERO: a rate of zero is refused on the rate field and nothing is
     * written.
     */
    @Test
    fun `Save with a zero rate is refused on the rate field`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        openAndSave(viewModel, rateDigits = "0")
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull("the sheet must stay open on a refused save", form)
        assertEquals(
            "Tarif harian harus lebih dari nol. Ketik angka di keypad lalu simpan.",
            form!!.rateError,
        )
        assertEquals(0, contracts.observeContracts().first().size)
    }

    /**
     * A second tap on Save while the first write is in flight is ignored.
     *
     * Without the guard the second `open` is enqueued for the same vehicle, the first
     * lands, and the second is then refused by the one-active index — so the owner
     * would be told their own brand-new contract made the car busy. That is a
     * confusing refusal about work that just succeeded, which is why the guard is in
     * the ViewModel rather than only in the button's `enabled`.
     */
    @Test
    fun `a second save while one is in flight is ignored`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        viewModel.onFormChange {
            it.selectRenter(renter())
                .selectVehicle(vehicle())
                .selectStartDate(LocalDate.of(2026, 10, 1))
        }
        viewModel.saveForm()
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "one contract per tap, not two rows",
            1,
            contracts.observeContracts().first().size,
        )
        assertNull("the form closes once, on the write that landed", viewModel.uiState.value.form)
        assertNull(
            "the second tap must not surface a refusal about the car it just rented",
            viewModel.uiState.value.errorMessage,
        )
    }

    /**
     * Dismissing the sheet drops a screen-level failure message with it.
     *
     * `closeForm()` clears `errorMessage` so a refusal raised while the sheet was up
     * does not survive onto the contract list, where it would read as a current
     * failure with no sheet left to explain it. Delete that one line and no other
     * test in this file fails: the field-level refusals above assert on the sheet's
     * own field slots, not this screen-level message, and the double-tap test's next
     * line calls `openAddForm()`, which clears it anyway.
     *
     * The message is produced the way it really appears — an unmodelled write
     * failure through a DAO whose insert refuses — rather than injected, so the
     * assertion covers the whole path from a real refusal to the cleared banner.
     */
    @Test
    fun `dismissing the sheet drops a screen level refusal`() = runTest(dispatcher) {
        seed()
        val viewModel = observingViewModel(dao = FailingInsertDao(database.contractDao()))

        openAndSave(viewModel)
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(
            "an unmodelled write failure must surface as a screen-level refusal",
            viewModel.uiState.value.errorMessage,
        )

        viewModel.closeForm()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(
            "a dismissed sheet must not leave its failure on the contract list",
            viewModel.uiState.value.errorMessage,
        )
    }

    /**
     * A form the owner dismissed and reopened during a write is **not** closed under
     * them when the write lands.
     *
     * ## Why this is the mirror of the catch branches
     *
     * Room's write is a suspension point and the owner keeps interacting across it —
     * the fact the catch branches already act on by re-reading `form.value` instead of
     * writing the stale `current` back. The success path had the mirror-image bug: it
     * cleared `form.value` unconditionally, so a form reopened while the write was in
     * flight was closed under the owner and the contract they had started typing
     * vanished with no explanation.
     *
     * The write is held open by a delegating DAO whose `insertContract` suspends until
     * released, which is the only way to observe the interleaving deterministically:
     * the test dismisses and reopens the form *inside* that window.
     */
    @Test
    fun `a form reopened during a write is not closed under the owner`() = runTest(dispatcher) {
        seed()
        val gate = CompletableDeferred<Unit>()
        val viewModel = observingViewModel(
            // The pre-checks answer honestly; only the insert is held, so the write
            // reaches the same path a real one would and suspends there.
            dao = GatedInsertDao(database.contractDao(), gate),
        )

        openAndSave(viewModel)
        // Run until the write is parked inside `insertContract`. `advanceUntilIdle`
        // stops there rather than hanging, because the gate *suspends* the write's
        // coroutine instead of blocking the thread — see `GatedInsertDao`.
        dispatcher.scheduler.advanceUntilIdle()

        // Dismiss and reopen the form the way the owner would, while the write is
        // still in flight.
        viewModel.closeForm()
        viewModel.openAddForm()
        dispatcher.scheduler.advanceUntilIdle()
        val reopened = viewModel.uiState.value.form
        assertNotNull("the owner's reopened form must exist", reopened)

        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(
            "a reopened form must not be closed by the earlier write landing",
            viewModel.uiState.value.form,
        )
        assertEquals(
            "and it must be the owner's own form, not a new one",
            reopened,
            viewModel.uiState.value.form,
        )
        // The write did land — this is not asserting that the save failed.
        assertEquals(1, contracts.observeContracts().first().size)
    }

    // -------------------------------------------------------- the picker options

    /**
     * The pickers are fed from the observed renter and vehicle flows, not from the
     * contracts' own names.
     *
     * A picker fed from the contract rows could only ever offer renters and vehicles
     * that *already* have a contract — which is the opposite of what a form for
     * opening a *new* one needs, and would make the screen unusable from empty.
     *
     * The two option flows are subscribed alongside `uiState`, for the same
     * `WhileSubscribed` reason the helper's KDoc gives: with no collector they are
     * never queried and `.value` stays at `initialValue`.
     */
    @Test
    fun `the pickers offer every stored renter and vehicle`() = runTest(dispatcher) {
        seed()
        database.vehicleDao().insert(vehicleEntity(id = "vehicle-2", plate = "B 9876 AB"))
        val viewModel = observingViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        // Nothing has been rented yet, and both options are already offered.
        assertEquals(0, viewModel.uiState.value.rows.size)
        assertEquals(1, viewModel.renterOptions.value.size)
        assertEquals(2, viewModel.vehicleOptions.value.size)
        assertEquals("B 1235 TF", viewModel.vehicleOptions.value.first().plate)
    }

    // ------------------------------------------------------------------ helper

    /**
     * Builds the ViewModel **with an active collector** on `uiState` and on the two
     * picker flows.
     *
     * ## Why this helper exists at all
     *
     * `uiState` is `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000))`,
     * and so are `renterOptions` and `vehicleOptions`. That sharing policy is the
     * right one for a screen: it keeps the Room queries alive across a rotation but
     * stops them when nothing is on screen.
     *
     * The cost is that with no subscriber there is no upstream collection, so the
     * Room `Flow`s are never queried and `.value` stays at `initialValue` — an empty
     * `KontrakUiState`, or an empty option list — no matter how many rows the
     * database holds. Reading `.value` without subscribing therefore cannot observe
     * anything. All three are subscribed here so a test can read whichever it is
     * about. `backgroundScope` is cancelled when the test body finishes, so the
     * collectors cannot outlive the test or leak into the next one.
     *
     * @param dao the contract DAO to build the repository over. Defaults to the real
     *   one; a test that needs to hold a write open passes a delegating wrapper — see
     *   `GatedInsertDao`.
     */
    private fun TestScope.observingViewModel(
        dao: ContractDao = database.contractDao(),
    ): KontrakViewModel {
        val viewModel = KontrakViewModel(
            ContractRepository(dao),
            renters,
            vehicles,
            strings,
        )
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        backgroundScope.launch(dispatcher) { viewModel.renterOptions.collect { } }
        backgroundScope.launch(dispatcher) { viewModel.vehicleOptions.collect { } }
        return viewModel
    }

    /**
     * A `ContractDao` that delegates everything but **suspends inside `insertContract`**
     * until [gate] completes.
     *
     * ## Why a delegating DAO rather than a fake
     *
     * The interleaving this exists to create — the owner interacting while the write
     * is in flight — happens *between* the insert and the success path, which is a
     * single suspension point inside one coroutine. Nothing in the ViewModel or the
     * repository offers a seam there, and a fake that reimplemented the write would
     * prove nothing about the real one.
     *
     * So this is a wrapper, not a fake: every method forwards to the real DAO, so the
     * schema, the indexes and the constraint translations behave exactly as they do in
     * production. Only the *timing* of the insert is controlled, which is the one thing
     * the test needs to pin.
     *
     * ## Why it suspends rather than blocks
     *
     * The test drives everything on a single-threaded `StandardTestDispatcher`, so a
     * blocking wait (`CountDownLatch.await`) would block the very thread
     * `advanceUntilIdle` needs to run the test's own interactions — the test would hang
     * and the interleaving could never be created. A `CompletableDeferred` *suspends*
     * the write's coroutine instead, leaving the thread free: `advanceUntilIdle` runs
     * up to the suspension, the test interacts, and completing the gate resumes the
     * write.
     */
    private class GatedInsertDao(
        private val delegate: ContractDao,
        private val gate: CompletableDeferred<Unit>,
    ) : ContractDao by delegate {
        override suspend fun insertContract(contract: ContractEntity) {
            gate.await()
            delegate.insertContract(contract)
        }
    }

    /**
     * A DAO whose insert refuses with something the screen does not model.
     *
     * `SQLiteConstraintException` is the realistic case — a primary-key clash or a
     * foreign-key refusal, which `ContractRepository.insertEntity` deliberately
     * rethrows rather than relabelling as "that car is busy". Everything else
     * delegates, so the pre-checks and the schema behave normally; only the write
     * fails. That is what produces the screen-level `errorMessage` rather than a
     * field refusal, which is the state `closeForm()` has to clear.
     */
    private class FailingInsertDao(
        private val delegate: ContractDao,
    ) : ContractDao by delegate {
        override suspend fun insertContract(contract: ContractEntity) {
            throw android.database.sqlite.SQLiteConstraintException(
                "test: a constraint failure the screen does not model",
            )
        }
    }

    /**
     * Opens the form, picks the seeded renter and vehicle, sets a date and rate, and
     * saves.
     *
     * `openAddForm` is inside because every caller wants the form open, and a caller
     * that forgot it would see `saveForm` return early — a silent no-op that looks
     * like a passing test.
     */
    private fun openAndSave(
        viewModel: KontrakViewModel,
        rateDigits: String? = null,
        restDay: DayOfWeek = DayOfWeek.SUNDAY,
    ) {
        viewModel.openAddForm()
        viewModel.onFormChange { current ->
            val picked = current
                .selectRenter(renter())
                .selectVehicle(vehicle())
                .selectStartDate(LocalDate.of(2026, 10, 1))
                .selectRestDay(restDay)
            if (rateDigits == null) picked else picked.copy(rateDigits = rateDigits)
        }
        viewModel.saveForm()
    }

    /** The vehicle and the renter every fixture contract names. */
    private suspend fun seed() {
        database.vehicleDao().insert(vehicleEntity())
        database.renterDao().insert(renterEntity())
    }

    private fun vehicleEntity(id: String = "vehicle-1", plate: String = "B 1235 TF") = VehicleEntity(
        id = id,
        plate = plate,
        brand = "Toyota",
        model = "Avanza",
        year = 2023,
        color = "Hitam",
        dailyRate = 250_000L,
        notes = null,
    )

    private fun renterEntity() = RenterEntity(
        id = "renter-1",
        name = "Budi Santoso",
        phone = "08123456789",
        bankAccount = null,
        emergencyContact = "08129876543",
    )

    private fun renter() = net.klinik.rental.domain.model.Renter(
        id = "renter-1",
        name = "Budi Santoso",
        phone = "08123456789",
        bankAccount = null,
        emergencyContact = "08129876543",
    )

    private fun vehicle(id: String = "vehicle-1", plate: String = "B 1235 TF") =
        net.klinik.rental.domain.model.Vehicle(
            id = id,
            plate = plate,
            brand = "Toyota",
            model = "Avanza",
            year = 2023,
            dailyRate = 250_000L,
        )
}
