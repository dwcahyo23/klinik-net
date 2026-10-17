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
import net.klinik.rental.data.db.entity.BusinessAccountEntity
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Payment
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
 * Story 3.5's correction behaviour, as the ViewModel sees it.
 *
 * ## What this file proves that the repository's own tests cannot
 *
 * Five things only the ViewModel can answer: that opening an edit populates the form from
 * the **stored** row rather than from a caller, that the method lock reaches the screen,
 * that a correction saves with no dialog in between, that a refusal lands somewhere the
 * owner can read it, and — the one this story's earlier pass got wrong — that a **new**
 * deposit still stores today's date now that the form carries one.
 *
 * ## The arithmetic every test shares
 *
 * With a 25th cutoff and `CLOCK` at 17 Oct the period is 25 Sep - 24 Oct; the contract
 * starts 1 Oct and rests on no day, so there are **17 charge days** at Rp 250.000 —
 * Rp 4.250.000 owed. Each test spells out what that makes its figures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class CorrectDepositViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var database: KlinikDatabase
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
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            // `findDeposit` reads all three relations itself, so it needs them — and that is
            // the half of the wiring the form's completeness depends on.
            renterDao = database.renterDao(),
            vehicleDao = database.vehicleDao(),
            businessAccountDao = database.businessAccountDao(),
            // **No database handle.** `withTransaction` asserts it is off the main thread,
            // and `StandardTestDispatcher` drains *on* it — so passing it here makes every
            // write throw into the generic banner. The transaction path is covered by
            // `CorrectDepositTest`, which uses a real background thread. This asymmetry is
            // deliberate.
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
     * Opening an edit fills every field from the stored row.
     *
     * The renter's name and the vehicle's plate are asserted here because those are the two
     * the ViewModel previously resolved itself, through three separate flow reads that could
     * each come back empty. A form carrying a blank plate is a form the owner cannot check
     * what they are correcting.
     */
    @Test
    fun `opening an edit fills the form from the stored deposit`() = runTest(dispatcher) {
        saveDeposit("p1", 500_000)
        val viewModel = observingViewModel()

        viewModel.openEditDeposit("p1")
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull("the sheet is open", form)
        assertTrue("and it is correcting, not recording", form!!.isEditing)
        assertEquals("p1", form.paymentId)
        assertEquals("500000", form.amountDigits)
        assertEquals("Budi Santoso", form.renterName)
        assertEquals(RENTER_ID, form.renterId)
        assertEquals(CONTRACT_ID, form.contractId)
        assertEquals("B 1235 TF", form.vehiclePlate)
        assertEquals("Rp 250.000", form.rateText)
        assertEquals(today, form.date)
        assertEquals(PaymentMethod.Tunai, form.method)
    }

    /** A transfer opens carrying its own account, reference and notes. */
    @Test
    fun `opening a transfer edit carries its account reference and notes`() = runTest(dispatcher) {
        saveDeposit(
            "p1",
            500_000,
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNT_1_ID,
            referenceNo = "REF-7788",
            notes = "Teller Slideshow",
        )
        val viewModel = observingViewModel()

        viewModel.openEditDeposit("p1")
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form!!
        assertEquals(PaymentMethod.TransferBank, form.method)
        assertEquals(ACCOUNT_1_ID, form.accountId)
        assertEquals("REF-7788", form.referenceNo)
        assertEquals("Teller Slideshow", form.notes)
        assertTrue("and the account field is on screen", form.showsAccountField)
    }

    /**
     * The method cannot be changed, at the state level.
     *
     * The sheet's own lock is asserted in `DepositSheetTest`; this is the half underneath
     * it, and the two are separate because either alone would pass — a state that refuses
     * with no locked chip, or a locked chip over a state that does not.
     */
    @Test
    fun `the payment method cannot be changed while correcting`() = runTest(dispatcher) {
        saveDeposit("p1", 500_000, method = PaymentMethod.Tunai)
        val viewModel = observingViewModel()
        viewModel.openEditDeposit("p1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFormChange { it.setMethod(PaymentMethod.TransferBank) }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the method is unchanged",
            PaymentMethod.Tunai,
            viewModel.uiState.value.form!!.method,
        )
    }

    /**
     * A date edit reaches the form through the sheet's own `onChange`.
     *
     * Asserted on the transform the sheet uses rather than on a dedicated handler, because a
     * date carrying a *second* setter beside `onChange` is the second mechanism that can
     * disagree with the first — and a test that only called the dedicated setter would not
     * see that.
     */
    @Test
    fun `a date edit reaches the form through onChange`() = runTest(dispatcher) {
        saveDeposit("p1", 500_000)
        val viewModel = observingViewModel()
        viewModel.openEditDeposit("p1")
        dispatcher.scheduler.advanceUntilIdle()

        val picked = LocalDate.of(2026, 10, 5)
        viewModel.onFormChange { it.setDate(picked) }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(picked, viewModel.uiState.value.form!!.date)
    }

    /**
     * `CORRECT_NO_CONFIRMATION` — the pad's Save writes directly, with no preview dialog.
     *
     * Asserted on both halves: the write happened, and no dialog is up. Either alone would
     * pass — a sheet that saved but *also* raised the preview would break the owner with a
     * second, pointless confirmation.
     */
    @Test
    fun `correcting saves directly with no preview dialog`() = runTest(dispatcher) {
        saveDeposit("p1", 250_000)
        val viewModel = observingViewModel()
        viewModel.openEditDeposit("p1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.previewDeposit(500_000)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("no preview dialog is up", state.isPreviewing)
        assertNull("and the sheet closes on success", state.form)
        assertEquals("Setoran Rp 500.000 diperbarui", state.successMessage)
        assertNull("leaving no failure behind", state.errorMessage)

        val stored = database.paymentDao().findById("p1")!!
        assertEquals("the amount is corrected in place", 500_000, stored.amount)
        assertEquals("and no second deposit was recorded", 1, payments.paymentsFor(CONTRACT_ID).size)
    }

    /**
     * A correction is told apart from a save by its wording.
     *
     * Pinned because the two are the same gesture with different outcomes, and a snackbar
     * reading "tersimpan" after a correction would tell the owner a new deposit was recorded
     * when the one they corrected is the one that changed.
     */
    @Test
    fun `the correction is confirmed with its own wording`() = runTest(dispatcher) {
        saveDeposit("p1", 250_000)
        val viewModel = observingViewModel()
        viewModel.openEditDeposit("p1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.previewDeposit(250_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Setoran Rp 250.000 diperbarui", viewModel.snackbarMessage.value)
    }

    /** The corrected date is the one written — not the clock's date, which is what the picker exists to override. */
    @Test
    fun `the corrected date is what gets stored`() = runTest(dispatcher) {
        saveDeposit("p1", 250_000)
        val viewModel = observingViewModel()
        viewModel.openEditDeposit("p1")
        dispatcher.scheduler.advanceUntilIdle()

        val backdated = LocalDate.of(2026, 10, 5)
        viewModel.onFormChange { it.setDate(backdated) }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.previewDeposit(250_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(backdated, database.paymentDao().findById("p1")!!.date)
    }

    /**
     * A **new** deposit still stores today's date.
     *
     * The regression guard for this story's one change to the new-deposit path: the form now
     * carries a `date`, so the save reads `current.date` rather than the clock. That is what
     * makes the picker work, and it is also the shape that would quietly let a new deposit
     * carry a stale date if `openDeposit` stopped seeding it.
     */
    @Test
    fun `a new deposit still stores today`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        val option = viewModel.uiState.value.renterOptions.single { it.renter.id == RENTER_ID }
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        viewModel.onFormChange { it.selectAccount(ACCOUNT_1_ID) }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveDepositAmount(250_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(today, payments.paymentsFor(CONTRACT_ID).single().date)
    }

    /** The date field is on a **new** deposit too, seeded to today — the back-dating a correction needs. */
    @Test
    fun `a new deposit opens on today's date`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.form!!.date)
    }

    /** Clearing a transfer's account is refused on the account field, and nothing is written. */
    @Test
    fun `clearing a transfer's account is refused on the field`() = runTest(dispatcher) {
        saveDeposit(
            "p1",
            500_000,
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNT_1_ID,
        )
        val viewModel = observingViewModel()
        viewModel.openEditDeposit("p1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFormChange { it.selectAccount(null) }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.saveCorrection(500_000)
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull("the form stays open so the owner can re-pick", form)
        assertEquals(
            "and the refusal lands on the account field",
            "Pilih rekening usaha yang menerima transfer ini.",
            form!!.accountError,
        )
        assertEquals(
            "nothing was written",
            ACCOUNT_1_ID,
            database.paymentDao().findById("p1")!!.accountId,
        )
    }

    /** A blank amount is refused, and the deposit keeps the amount it had. */
    @Test
    fun `correcting to a blank amount is refused on the amount field`() = runTest(dispatcher) {
        saveDeposit("p1", 250_000)
        val viewModel = observingViewModel()
        viewModel.openEditDeposit("p1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveCorrection(0)
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull(form)
        assertNotNull("the amount field carries the refusal", form!!.amountError)
        assertEquals("and the stored amount is untouched", 250_000, database.paymentDao().findById("p1")!!.amount)
    }

    /** `CORRECT_CANCELED_REFUSED`, seen from the UI: no form opens, and the banner says why. */
    @Test
    fun `opening a canceled deposit for correction opens no form`() = runTest(dispatcher) {
        saveDeposit("p1", 250_000)
        payments.cancelDeposit(CONTRACT_ID, "p1", "Salah ketik tanggal")
        val viewModel = observingViewModel()

        viewModel.openEditDeposit("p1")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("no form is opened", viewModel.uiState.value.form)
        assertNotNull("and the refusal is on the banner", viewModel.uiState.value.errorMessage)
    }

    /**
     * `CORRECT_UNKNOWN_DEPOSIT`, and the copy is asserted — not merely asserted present.
     *
     * "Gagal menyimpan setoran" would be wrong here in a way an owner can act on: it says a
     * write failed when the truth is that the row is gone. So the test pins the sentence.
     */
    @Test
    fun `opening an unknown deposit says it is gone, not that saving failed`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openEditDeposit("no-such-deposit")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("no form is opened", viewModel.uiState.value.form)
        assertEquals(
            "and the message names what is actually wrong",
            "Setoran ini sudah tidak ada, jadi tidak bisa dikoreksi.",
            viewModel.uiState.value.errorMessage,
        )
    }

    /** Saving with no edit open writes nothing — the un-skippable counterpart to the preview's. */
    @Test
    fun `saving a correction with no edit open writes nothing`() = runTest(dispatcher) {
        saveDeposit("p1", 250_000)
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveCorrection(999_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the stored deposit is untouched",
            250_000,
            database.paymentDao().findById("p1")!!.amount,
        )
    }

    /**
     * A **new** deposit is stored on the date the picker shows — not on the clock's date.
     *
     * The regression guard for this story's change to the new-deposit path, and the half the
     * earlier "still stores today" test could not see: that one asserted a value
     * `openDeposit` had already seeded, so it passed whether the save read the form or the
     * clock. This one moves the date first, so a save still reading `today` stores the wrong
     * day and fails.
     */
    @Test
    fun `a new deposit is stored on the date the picker set`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        val option = viewModel.uiState.value.renterOptions.single { it.renter.id == RENTER_ID }
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        viewModel.onFormChange { it.selectAccount(ACCOUNT_1_ID) }
        dispatcher.scheduler.advanceUntilIdle()

        val backdated = LocalDate.of(2026, 10, 5)
        viewModel.onFormChange { it.setDate(backdated) }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.saveDepositAmount(250_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the back-dated day is what lands on disk",
            backdated,
            payments.paymentsFor(CONTRACT_ID).single().date,
        )
    }

    /**
     * The preview and the save agree on the date — the property Story 3.3 exists to protect.
     *
     * Back-dated new deposit, previewed and then confirmed. Both read the form's date now, so
     * the arrears the dialog showed is the arrears the write produced; a preview still on
     * `today` would show one figure and the save would produce another, and the owner would
     * have trusted the wrong one.
     */
    @Test
    fun `the preview and the save agree on a back-dated new deposit`() = runTest(dispatcher) {
        val backdated = LocalDate.of(2026, 10, 5)
        // What the dialog would promise: the repository's own preview, on the form's date.
        // `DepositPreviewUi` carries formatted lines rather than figures, so the comparison
        // is against the number behind them — otherwise this would assert two strings that
        // are the same builder output whatever date the preview used.
        val promised = payments.previewDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "preview-1", date = backdated, amount = 750_000),
            asOf = today,
        )

        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        val option = viewModel.uiState.value.renterOptions.single { it.renter.id == RENTER_ID }
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        viewModel.onFormChange { it.selectAccount(ACCOUNT_1_ID) }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.setDate(backdated) }
        // `confirmDeposit` re-reads the digits rather than trusting the preview's amount —
        // a figure typed between preview and confirm must not go in unshown — so the pad's
        // own value has to be set for the confirm to have anything to write.
        viewModel.onFormChange { it.copy(amountDigits = "750000") }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.previewDeposit(750_000)
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull("the mandatory preview is up", viewModel.uiState.value.preview)
        viewModel.confirmDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the arrears the dialog named is the arrears the write produced",
            promised.arrearsAfter,
            payments.currentArrears(CONTRACT_ID, today),
        )
    }

    /** Opening a new deposit while an edit is still reading replaces it, not the other way round. */
    @Test
    fun `opening a new deposit while an edit is reading wins`() = runTest(dispatcher) {
        saveDeposit("p1", 250_000)
        val viewModel = observingViewModel()

        viewModel.openEditDeposit("p1")
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull(form)
        assertNull("and it is a new deposit, not the edit that was still reading", form!!.paymentId)
    }

    /**
     * Closing the sheet while the open is still reading leaves it closed.
     *
     * The launch reads before it writes, so without this the sheet would spring open again
     * after the owner dismissed it — the "control that appears after you told it to go"
     * shape, and the reason every close in this class re-checks the form it started with.
     */
    @Test
    fun `closing the sheet while an edit is opening wins`() = runTest(dispatcher) {
        saveDeposit("p1", 250_000)
        val viewModel = observingViewModel()

        viewModel.openEditDeposit("p1")
        viewModel.closeDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("the sheet stays closed", viewModel.uiState.value.form)
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A ViewModel with an active collector on `uiState`.
     *
     * `stateIn(WhileSubscribed(5_000))` means no subscriber produces no upstream
     * collection, so the repository flows are never queried and `.value` stays at its
     * initial value — which reads as "no renters" rather than as an un-advanced clock.
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
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private suspend fun saveDeposit(
        id: String,
        amount: Long,
        method: PaymentMethod = PaymentMethod.Tunai,
        accountId: String? = null,
        referenceNo: String? = null,
        notes: String? = null,
    ) {
        payments.saveDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = id, date = today, amount = amount),
            method = method,
            accountId = accountId,
            referenceNo = referenceNo,
            notes = notes,
            asOf = today,
        )
    }

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
                startDate = LocalDate.of(2026, 10, 1),
                dailyRate = RATE,
                restDay = null,
                activeVehicleId = VEHICLE_ID,
                activeRenterId = RENTER_ID,
                endDate = null,
            )
        )
        database.businessAccountDao().insertBusinessAccount(
            BusinessAccountEntity(id = ACCOUNT_1_ID, bankName = "BCA", number = "1234567890"),
        )
    }

    private companion object {
        const val CONTRACT_ID = "contract-1"
        const val RENTER_ID = "renter-1"
        const val VEHICLE_ID = "vehicle-1"
        const val ACCOUNT_1_ID = "account-1"
        const val RATE = 250_000L

        val CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-10-17T09:00:00Z"),
            ZoneOffset.UTC,
        )
    }
}
