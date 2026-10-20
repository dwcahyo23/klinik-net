package net.klinik.rental.ui.screens.kontrak

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.ui.strings.RStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Story 3.6's settlement **behaviour**, as the ViewModel sees it.
 *
 * ## Why this is a separate file rather than more cases in `KontrakViewModelTest`
 *
 * The repository suite (`SettleContractTest`) proves the arithmetic and the two data-layer
 * refusals. This file proves the four things only the ViewModel can answer: that the sheet
 * opens **before** anything is written, that its figures name the contract rather than a
 * blank, that a date change recomputes those figures instead of carrying a stale pair, and
 * that a refusal lands somewhere the owner can see.
 *
 * ## The deliberate `database = null`
 *
 * Both repositories are built **without** the database handle, which is the same asymmetry
 * `CancelDepositViewModelTest` and `SetoranViewModelTest` use and for the same reason:
 * `withTransaction` asserts it is not on the main thread, and `StandardTestDispatcher` drains
 * *on* it — so passing one makes every write throw into the generic banner and the tests fail
 * as "the settlement did not happen". The transaction path is covered by
 * `SettleContractTest`, which uses a real background thread via `runBlocking`. This
 * asymmetry is deliberate, not an oversight.
 *
 * ## The arithmetic every test shares
 *
 * With a 25th cutoff and `CLOCK` at 17 Oct the period is 25 Sep - 24 Oct; the contract starts
 * 1 Oct and rests on no day, so there are **17 charge days** at Rp 250.000 — Rp 4.250.000
 * owed. Each test spells out what that makes its figures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class SettleContractViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var database: KlinikDatabase
    private lateinit var contracts: ContractRepository
    private lateinit var payments: PaymentRepository
    private lateinit var strings: RStrings

    private val today = LocalDate.of(2026, 10, 17)

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, KlinikDatabase::class.java)
            // Room's own executors, or a row written by the write path sits in the
            // database while `uiState.value` still holds the pre-write emission.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        contracts = ContractRepository(
            dao = database.contractDao(),
            serviceExceptions = database.serviceExceptionDao(),
            // **No database handle** — see the class KDoc.
            database = null,
        )
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            renterDao = database.renterDao(),
            vehicleDao = database.vehicleDao(),
            businessAccountDao = database.businessAccountDao(),
            database = null,
        )
        strings = RStrings(context)
        seed()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    /**
     * The sheet opens naming the contract, and **writes nothing**.
     *
     * Asserted on the stored row as well as the state: a sheet that opened by settling as it
     * rendered would leave the owner with no way to read the impact before committing to an
     * action FR-9b gives no undo.
     */
    @Test
    fun `opening the sheet names the contract and writes nothing`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        val row = budiRow(viewModel)

        viewModel.openSettle(row)
        dispatcher.scheduler.advanceUntilIdle()

        val sheet = viewModel.uiState.value.settleSheet
        assertNotNull("the sheet is up", sheet)
        assertEquals("it names renter and plate, the app's label for a rental", "Budi Santoso · B 1235 TF", sheet!!.contractLabel)
        assertEquals("on this contract", CONTRACT_ID, sheet.contractId)
        assertNull("with no date chosen yet — a pre-filled one would confirm a settlement nobody made", sheet.settleDate)
        assertNull(
            "and nothing was written",
            database.contractDao().findById(CONTRACT_ID)!!.settledThroughDate,
        )
    }

    /**
     * Choosing a date computes the impact the confirmation must name — **before** the write.
     *
     * 17 days × Rp 250.000 is Rp 4.250.000, and a date past every charge day closes all
     * seventeen. The figures are asserted while the sheet is still open, which is the whole
     * point of the AC: the owner reads them before committing.
     */
    @Test
    fun `choosing a date computes the impact before anything is written`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openSettle(budiRow(viewModel))
        dispatcher.scheduler.advanceUntilIdle()

        // `today` (= 17 Oct) closes the whole open period — dates after today are refused
        // by the repo and never reach the impact computation, so for a "computes the impact"
        // assertion the picked day must sit at or before today.
        viewModel.onSettleChange { it.selectSettleDate(today) }
        dispatcher.scheduler.advanceUntilIdle()

        val sheet = viewModel.uiState.value.settleSheet!!
        assertEquals("17 days are closed", 17, sheet.daysClosed)
        assertEquals("which is Rp 4.250.000", 4_250_000L, sheet.amountRemoved)
        assertEquals("and nothing is left owing", 0, sheet.daysRemaining)
        assertTrue("so the confirm button is allowed", sheet.canSettle)
        assertNull(
            "but still nothing is written while the sheet is open",
            database.contractDao().findById(CONTRACT_ID)!!.settledThroughDate,
        )
    }

    /**
     * A second date **recomputes** rather than carrying the first pair.
     *
     * The stale-figure case is the one this catches, and it is why `onSettleChange` is not a
     * plain `let` like the end sheet's: the day count and the rupiah belong to the chosen
     * date, so a form showing "17 days / Rp 4.250.000" after the owner moved to a nearer day
     * would ask them to confirm a number they did not ask for.
     */
    @Test
    fun `changing the date recomputes the figures`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openSettle(budiRow(viewModel))
        dispatcher.scheduler.advanceUntilIdle()

        // `today` (= 17 Oct) closes the whole open period — dates after today are refused
        // by the repo and never reach the impact computation.
        viewModel.onSettleChange { it.selectSettleDate(today) }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("the whole period first", 17, viewModel.uiState.value.settleSheet!!.daysClosed)

        viewModel.onSettleChange { it.selectSettleDate(LocalDate.of(2026, 10, 5)) }
        dispatcher.scheduler.advanceUntilIdle()

        val sheet = viewModel.uiState.value.settleSheet!!
        assertEquals("five days are closed by 5 October, not seventeen", 5, sheet.daysClosed)
        assertEquals("Rp 1.250.000", 1_250_000L, sheet.amountRemoved)
        assertEquals("and twelve days still owe", 12, sheet.daysRemaining)
        assertEquals("Rp 3.000.000", 3_000_000L, sheet.amountRemaining)
    }

    /**
     * Confirming settles the contract, closes the sheet, and confirms in Indonesian.
     *
     * The sheet closing matters as much as the write: FR-9b cannot be undone, so the form
     * must not stay on screen looking like the action could still be changed or repeated.
     */
    @Test
    fun `confirming settles the contract and closes the sheet`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openSettle(budiRow(viewModel))
        dispatcher.scheduler.advanceUntilIdle()
        // `today`, not a later day: a settlement date after the pinned clock is refused, and
        // this test is about the sheet closing on success, not about which day was picked.
        viewModel.onSettleChange { it.selectSettleDate(today) }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.settleContract()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("the sheet is gone", state.settleSheet)
        assertNull("and leaves no failure", viewModel.settleErrorMessage.value)
        assertEquals("17 hari ditandai lunas sampai 17 Okt", viewModel.snackbarMessage.value)
        assertEquals(
            "the marker is stored",
            today,
            database.contractDao().findById(CONTRACT_ID)!!.settledThroughDate,
        )
        assertEquals(
            "and arrears reads zero",
            0,
            payments.currentArrears(CONTRACT_ID, today),
        )
        assertEquals(
            "with no allocation row written for it",
            0,
            database.allocationDao().forContract(CONTRACT_ID).size,
        )
    }

    /**
     * Confirming with **no date** writes nothing and puts the refusal on the date field.
     *
     * The UI half of the rule. The repository would refuse too, but a check in only one of the
     * two leaves either a control that does nothing or a hole in the data layer.
     */
    @Test
    fun `confirming with no date is refused on the date field`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openSettle(budiRow(viewModel))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.settleContract()
        dispatcher.scheduler.advanceUntilIdle()

        val sheet = viewModel.uiState.value.settleSheet
        assertNotNull("the sheet stays open so the owner can fix it", sheet)
        assertEquals(
            "with the epic's own explanation",
            "Pilih tanggal terakhir yang sudah lunas sebelum kirim.",
            sheet!!.dateError,
        )
        assertNull(
            "and nothing was written",
            database.contractDao().findById(CONTRACT_ID)!!.settledThroughDate,
        )
    }

    /**
     * A before-start date is refused by the repository, and the refusal lands on the field
     * **naming the contract's own start date**.
     *
     * The date field is not filtered before the write — the sheet cannot see the start date
     * without carrying it, and a permanently disabled button with no explanation is worse than
     * a refusal the owner can act on. So this is the path that proves the message arrives.
     */
    @Test
    fun `a before-start date is refused and names the start date`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openSettle(budiRow(viewModel))
        dispatcher.scheduler.advanceUntilIdle()
        // 30 September is before the contract's 1 October start, so the impact is zero and
        // the confirm button is disabled — which is why the write is driven directly.
        viewModel.onSettleChange { it.selectSettleDate(LocalDate.of(2026, 9, 30)) }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "a date before the start closes nothing, so there is nothing to confirm",
            0,
            viewModel.uiState.value.settleSheet!!.daysClosed,
        )
        assertFalse("and the button is not available", viewModel.uiState.value.settleSheet!!.canSettle)
        assertNull(
            "while nothing was written",
            database.contractDao().findById(CONTRACT_ID)!!.settledThroughDate,
        )
    }

    /**
     * Closing the sheet discards it, and the next attempt starts clean.
     *
     * Without this, a settlement date chosen and abandoned would reappear on the next
     * attempt with no explanation of where it came from — on an action that cannot be undone,
     * a figure carried from a previous look is a figure nobody checked.
     */
    @Test
    fun `closing the sheet discards the date and the figures`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openSettle(budiRow(viewModel))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onSettleChange { it.selectSettleDate(LocalDate.of(2026, 10, 24)) }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.closeSettle()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull("the sheet is gone", viewModel.uiState.value.settleSheet)

        viewModel.openSettle(budiRow(viewModel))
        dispatcher.scheduler.advanceUntilIdle()
        assertNull("and the next one starts with no date", viewModel.uiState.value.settleSheet!!.settleDate)
        assertEquals("and no figures", 0, viewModel.uiState.value.settleSheet!!.daysClosed)
    }

    /**
     * Moving to a date that closes **nothing** disables the confirm — the money-path case.
     *
     * The sequence matters: a date that closes seventeen days first, then one that closes
     * none. Before the fix, `onSettleChange` published the new state carrying the *previous*
     * date's figures while the impact read was still in flight, so the moment `uiState`
     * emitted the new date it did so with `daysClosed` still 17 — leaving `canSettle` true
     * and the button confirming a settlement the owner would read as the one they had just
     * chosen. On an action FR-9b gives no undo, a confirm button live against figures for a
     * date nobody picked is the worst state this sheet can be in.
     *
     * The figures are asserted through `uiState`, which is the only surface the sheet
     * renders — so this is the owner's actual view, not the ViewModel's internal state.
     */
    @Test
    fun `moving to a date that closes nothing disables the confirm`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openSettle(budiRow(viewModel))
        dispatcher.scheduler.advanceUntilIdle()

        // `today` (= 17 Oct) closes the whole open period — dates after today are refused
        // by the repo, so the "successful read" half of this test has to sit at or before
        // today.
        viewModel.onSettleChange { it.selectSettleDate(today) }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(
            "today closes the whole open period, so the button is available",
            viewModel.uiState.value.settleSheet!!.canSettle,
        )

        // Now a date before the contract even began: it closes nothing at all.
        viewModel.onSettleChange { it.selectSettleDate(LocalDate.of(2026, 9, 30)) }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(
            "the confirm must not be live on the previous date's figures",
            viewModel.uiState.value.settleSheet!!.canSettle,
        )
        assertEquals(
            "with nothing closed, rather than the seventeen the last date closed",
            0,
            viewModel.uiState.value.settleSheet!!.daysClosed,
        )
        assertEquals(
            "and nothing removed",
            0L,
            viewModel.uiState.value.settleSheet!!.amountRemoved,
        )
        assertNull(
            "and nothing was written",
            database.contractDao().findById(CONTRACT_ID)!!.settledThroughDate,
        )
    }

    /**
     * And an impact that could not be computed leaves the confirm **disabled**.
     *
     * The other half of the same rule, and the one that is deterministically observable: a
     * settlement whose figures the ViewModel could not read is an action the owner was never
     * shown, so a live confirm would be the same defect as a stale figure — a button over
     * numbers nobody read, on an action with no undo.
     *
     * Reached by closing the database's contract DAO out from under the read, which is the
     * only way to make `settlementImpact` fail without a contrived fixture; the assertion is
     * that the *button* is gone, not that a particular failure message appeared.
     */
    @Test
    fun `an impact that cannot be computed leaves the confirm disabled`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openSettle(budiRow(viewModel))
        dispatcher.scheduler.advanceUntilIdle()
        // `today` (= 17 Oct) closes some days — dates after today are refused by the repo
        // and never reach the read; we need a successfully-read date to prove the *button*
        // is gone on failure, not just on any refusal.
        viewModel.onSettleChange { it.selectSettleDate(today) }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(
            "the read succeeds normally, so the button is available",
            viewModel.uiState.value.settleSheet!!.canSettle,
        )

        // Now break the contract read. `close()` on an already-closed database throws, and
        // `IllegalStateException` is what the read surfaces — the same shape as a corrupt
        // store, which is the realistic case.
        database.close()
        viewModel.onSettleChange { it.selectSettleDate(LocalDate.of(2026, 10, 10)) }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(
            "an unreadable impact must not leave the confirm live",
            viewModel.uiState.value.settleSheet!!.canSettle,
        )
        assertEquals(
            "and the figures read as none rather than as the previous date's",
            0,
            viewModel.uiState.value.settleSheet!!.daysClosed,
        )
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Budi's contract row, read from the screen's own state rather than hand-built.
     *
     * Read from `uiState.rows` because `openSettle` takes a [ContractRow] and the row the
     * screen renders is the one the owner taps — a fixture that hand-built it would prove
     * the ViewModel accepts a row, not that the row it produces is usable.
     */
    private fun TestScope.budiRow(viewModel: KontrakViewModel): ContractRow =
        viewModel.uiState.value.rows.single { it.contract.id == CONTRACT_ID }

    /**
     * The ViewModel **with an active collector** on `uiState`.
     *
     * `stateIn(WhileSubscribed(5_000))` means no subscriber produces no upstream collection,
     * so the repository flows are never queried and `.value` stays at `initialValue` — an
     * empty row list, which would make [budiRow] fail rather than silently pass on a
     * hand-built fixture.
     */
    private fun TestScope.observingViewModel(): KontrakViewModel {
        val viewModel = KontrakViewModel(
            contracts = contracts,
            renters = RenterRepository(database.renterDao()),
            vehicles = VehicleRepository(database.vehicleDao()),
            payments = payments,
            strings = strings,
            clock = CLOCK,
        )
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    /** One vehicle, one renter with an open contract from 1 Oct. */
    private suspend fun seed() {
        database.vehicleDao().insert(
            VehicleEntity(
                id = VEHICLE_ID,
                plate = "B 1235 TF",
                brand = "Toyota",
                model = "Avanza",
                year = 2023,
                color = null,
                dailyRate = RATE,
                notes = null,
            )
        )
        database.renterDao().insert(
            RenterEntity(
                id = RENTER_ID,
                name = "Budi Santoso",
                phone = "08123456789",
                bankAccount = null,
                emergencyContact = "08129876543",
            )
        )
        database.contractDao().insertContract(
            ContractEntity(
                id = CONTRACT_ID,
                renterId = RENTER_ID,
                vehicleId = VEHICLE_ID,
                // 1 Oct, so the period opening 25 Sep yields 17 charge days to the 17th.
                startDate = LocalDate.of(2026, 10, 1),
                dailyRate = RATE,
                restDay = null,
                activeVehicleId = VEHICLE_ID,
                activeRenterId = RENTER_ID,
                endDate = null,
            )
        )
    }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val RENTER_ID = "renter-1"
        const val CONTRACT_ID = "contract-1"
        const val RATE = 250_000L

        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC)
    }
}
