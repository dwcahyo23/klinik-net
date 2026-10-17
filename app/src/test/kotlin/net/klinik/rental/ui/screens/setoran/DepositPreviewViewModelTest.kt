package net.klinik.rental.ui.screens.setoran

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
import net.klinik.rental.data.repository.BusinessAccountRepository
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
 * Story 3.3's preview **behaviour**, as the ViewModel sees it.
 *
 * ## Why this is a separate file rather than more cases in `SetoranViewModelTest`
 *
 * `DepositPreviewTest` proves the *repository* cannot disagree with itself — one pure
 * function behind both the preview and the write. This file proves what the **owner** can
 * do about that: that the dialog appears before anything lands, that dismissing it saves
 * nothing, and that confirming it with no dialog up saves nothing either.
 *
 * Those are UI-state claims rather than arithmetic ones, and keeping them apart means a
 * failure in either names which layer broke rather than pointing at a file with both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class DepositPreviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var database: KlinikDatabase
    private lateinit var payments: PaymentRepository
    private lateinit var strings: RStrings

    /**
     * Pinned, because the figures the assertions quote depend on it: with a 25th cutoff
     * and this date the period is 25 Sep - 24 Oct, and a contract from 1 Oct gives
     * **17 charge days** at Rp 250.000 — Rp 4.250.000 owed. The arithmetic is spelled out
     * per test rather than left here, because a preview test that guesses its own period
     * silently stops proving what it names.
     */
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
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            // **No database handle.** `withTransaction` asserts it is off the main thread,
            // and `StandardTestDispatcher` drains *on* it — so passing it here makes every
            // write throw into the generic banner and the tests fail as "the deposit did
            // not happen". The transaction path is covered by `PaymentRepositoryTest`,
            // which uses a real background thread. This asymmetry is deliberate.
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
     * Previewing computes the dialog and writes **nothing**.
     *
     * Asserted on both tables, because a preview that wrote an allocation row and not a
     * payment row would look inert in most lists while still being a write — and the whole
     * point of the dialog is that nothing has landed yet.
     */
    @Test
    fun `previewing writes nothing and shows the dialog`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.completeFor(ACCOUNT_ID, "500000")

        viewModel.previewDeposit(500_000)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("the dialog is up", state.isPreviewing)
        assertEquals("no deposit in the table", 0, payments.paymentsFor(CONTRACT_ID).size)
        assertEquals("and no allocation", 0, database.allocationDao().forContract(CONTRACT_ID).size)
    }

    /** The dialog reads UX-DR10's exact sentences. */
    @Test
    fun `the preview reads the epic's format`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.completeFor(ACCOUNT_ID, "1000000")

        viewModel.previewDeposit(1_000_000)
        dispatcher.scheduler.advanceUntilIdle()

        val preview = viewModel.uiState.value.preview!!
        assertEquals("the arrears line", "Tunggakan: Rp 4.250.000 → Rp 3.250.000", preview.arrearsLine)
        assertEquals(
            "and the closes line, with days remaining",
            "Menutup: 4 hari · Sisa: 13 hari (Rp 3.250.000)",
            preview.closesLine,
        )
        assertNull("with no credit line at all, because there is no credit", preview.creditLine)
    }

    /**
     * And a credit appears **with** its explanation — the AC forbids a mystery number.
     *
     * A deposit larger than the whole period: every day closes and the tail is credit.
     */
    @Test
    fun `a credit appears with its explanation`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.completeFor(ACCOUNT_ID, "4500000")

        viewModel.previewDeposit(4_250_000 + 250_000)
        dispatcher.scheduler.advanceUntilIdle()

        val preview = viewModel.uiState.value.preview!!
        assertEquals("every day closes", "Menutup: 17 hari", preview.closesLine)
        assertEquals(
            "and the credit names itself and explains itself",
            "Kredit: Rp 250.000 — dipakai otomatis untuk periode berikutnya",
            preview.creditLine,
        )
    }

    /** The dialog's dismiss writes nothing and leaves the form open. */
    @Test
    fun `dismissing the preview saves nothing and keeps the form`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.completeFor(ACCOUNT_ID, "500000")

        viewModel.previewDeposit(500_000)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.dismissPreview()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("the dialog is gone", state.isPreviewing)
        assertTrue("but the sheet is still open — 'Cek dulu' loses nothing", state.isOpen)
        assertEquals("and nothing was written", 0, payments.paymentsFor(CONTRACT_ID).size)
    }

    /**
     * Confirming writes the deposit and closes **both** windows.
     *
     * The dialog going with the sheet matters: a confirm button left up over a closed
     * sheet would be a figure the owner could still act on with nothing behind it.
     */
    @Test
    fun `confirming the preview writes the deposit and closes both windows`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.completeFor(ACCOUNT_ID, "750000")

        viewModel.previewDeposit(750_000)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.confirmDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("one deposit is stored", 1, payments.paymentsFor(CONTRACT_ID).size)
        assertEquals(
            "for the amount the dialog showed",
            750_000,
            payments.paymentsFor(CONTRACT_ID).single().amount,
        )
        assertFalse("the dialog is gone", state.isPreviewing)
        assertFalse("and so is the sheet", state.isOpen)
    }

    /**
     * The preview is **un-skippable**: confirming with no dialog up writes nothing.
     *
     * This is where UX-DR10's *"pratinjau wajib, bukan opsional"* becomes structural
     * rather than a wiring convention — a caller reaching `confirmDeposit` without a
     * preview gets a refusal, not a silent save.
     */
    @Test
    fun `confirming without a preview writes nothing`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.completeFor(ACCOUNT_ID, "750000")

        viewModel.confirmDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("nothing was written", 0, payments.paymentsFor(CONTRACT_ID).size)
        assertTrue("and the sheet is still open", viewModel.uiState.value.isOpen)
    }

    /**
     * Editing the form invalidates a dialog already up.
     *
     * Not tidiness: the figures would then describe a deposit the owner is no longer
     * composing, and confirming would save something the preview never showed.
     */
    @Test
    fun `editing the form dismisses the preview`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.completeFor(ACCOUNT_ID, "500000")

        viewModel.previewDeposit(500_000)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPreviewing)

        viewModel.onFormChange { it.copy(amountDigits = "999999") }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(
            "a dialog about a different figure must not survive an edit",
            viewModel.uiState.value.isPreviewing,
        )
    }

    /**
     * A preview on a transfer with no account is refused **before** the dialog opens.
     *
     * The same rule the pad's Save applies in Story 3.2, kept in place — the dialog
     * showing figures for a deposit that could not be saved would be the worst outcome
     * of the story.
     */
    @Test
    fun `a transfer with no account never reaches the dialog`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.completeFor(accountId = null, amount = "500000")

        viewModel.previewDeposit(500_000)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("no dialog", state.isPreviewing)
        assertNotNull("the refusal lands on the field", state.form!!.accountError)
        assertEquals("and nothing was written", 0, payments.paymentsFor(CONTRACT_ID).size)
    }

    // ------------------------------------------------------------------ helpers

    /** Selects Budi, the account, and the digits — the three things a form needs. */
    private fun SetoranViewModel.completeFor(accountId: String?, amount: String) {
        val option = uiState.value.renterOptions.single { it.renter.id == RENTER_ID }
        onFormChange { it.selectRenter(option.renter, option.contract) }
        accountId?.let { id -> onFormChange { it.selectAccount(id) } }
        onFormChange { it.copy(amountDigits = amount) }
    }

    /**
     * The ViewModel **with an active collector** on `uiState`.
     *
     * `stateIn(WhileSubscribed(5_000))` means no subscriber produces no upstream
     * collection, so the repository flows are never queried and `.value` stays at
     * `initialValue` — which reads as "no renters" rather than as an un-advanced clock.
     * The `advanceUntilIdle` is what makes the difference; see the KDoc.
     */
    private fun TestScope.observingViewModel(): SetoranViewModel {
        val viewModel = SetoranViewModel(
            payments = payments,
            renterRepository = RenterRepository(database.renterDao()),
            vehicleRepository = VehicleRepository(database.vehicleDao()),
            accountRepository = BusinessAccountRepository(database.businessAccountDao()),
            contractRepository = ContractRepository(
                database.contractDao(),
                database.serviceExceptionDao(),
            ),
            strings = strings,
            clock = CLOCK,
        )
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    /** One vehicle, one renter with an open contract, and one account. */
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
        database.businessAccountDao().insertBusinessAccount(
            net.klinik.rental.data.db.entity.BusinessAccountEntity(
                id = ACCOUNT_ID,
                bankName = "BCA",
                number = "1234567890",
            )
        )
    }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val RENTER_ID = "renter-1"
        const val CONTRACT_ID = "contract-1"
        const val ACCOUNT_ID = "account-1"
        const val RATE = 250_000L

        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC)
    }
}