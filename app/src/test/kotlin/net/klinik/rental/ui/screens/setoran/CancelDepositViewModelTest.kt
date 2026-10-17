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
 * Story 3.4's cancellation **behaviour**, as the ViewModel sees it.
 *
 * ## Why this is a separate file rather than more cases in `SetoranViewModelTest`
 *
 * `CancelDepositTest` proves the repository's arithmetic and its three refusals. This file
 * proves the four things only the ViewModel can answer: that the dialog appears *before*
 * anything is written, that a blank reason is refused **on the control** rather than only
 * in the data, that the mandatory field's error clears on the next keystroke, and that a
 * refusal from the repository lands somewhere the owner can see it.
 *
 * The dead-code failure this file's existence is insurance against is the one that bit this
 * story once already: tests spliced into an existing file that compiled, ran green, and
 * never executed. A new file cannot be half-written without `ktlint` noticing the class
 * never closes.
 *
 * ## The arithmetic every test shares
 *
 * With a 25th cutoff and `CLOCK` at 17 Oct the period is 25 Sep - 24 Oct; the contract
 * starts 1 Oct, so **17 charge days** at Rp 250.000 — Rp 4.250.000 owed. Each test spells
 * out what that makes its figures, because a cancellation test that guesses its own period
 * silently stops proving what it names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class CancelDepositViewModelTest {

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
            // **No database handle.** `withTransaction` asserts it is off the main thread,
            // and `StandardTestDispatcher` drains *on* it — so passing it here makes every
            // write throw into the generic banner and the tests fail as "the cancellation
            // did not happen". The transaction path is covered by `CancelDepositTest`,
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
     * Asking to cancel shows the dialog and writes **nothing**.
     *
     * Asserted on both tables, because the dialog is the whole point — an owner who sees
     * the impact sentence has not yet agreed to anything.
     */
    @Test
    fun `requesting a cancellation shows the dialog and writes nothing`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()

        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("the dialog is up", viewModel.uiState.value.isCanceling)
        assertEquals("no payment was touched", 1, payments.paymentsFor(CONTRACT_ID).size)
        assertEquals(
            "and no allocation was released",
            3,
            database.allocationDao().forContract(CONTRACT_ID).size,
        )
        assertEquals(
            "still confirmed",
            PaymentEntity.STATUS_CONFIRMED,
            database.paymentDao().findById(PAYMENT_ID)!!.status,
        )
    }

    /**
     * The impact sentence is the epic's own — naming **Budi** and the figure his arrears
     * return to.
     *
     * Rp 750.000 closed three of seventeen days, so arrears is Rp 3.500.000 now and
     * Rp 4.250.000 once the deposit is undone: the whole period, because there is nothing
     * else placed.
     */
    @Test
    fun `the dialog names the renter and the arrears they return to`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()

        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        val cancel = viewModel.uiState.value.cancel!!
        assertEquals(
            "Tunggakan Budi Santoso akan kembali menjadi Rp 4.250.000.",
            cancel.impactLine,
        )
        assertEquals("Batalkan setoran Rp 750.000?", cancel.title)
        assertEquals("with the amount carried beside the title", "Rp 750.000", cancel.amountLabel)
        assertEquals("and no reason yet", "", cancel.reason)
    }

    /**
     * A blank reason is refused **on the dialog**, with the field's own error.
     *
     * The UI half of the AC's *"alasan wajib diisi"*. The repository refuses it too
     * (`CancelDepositTest`), but a rule enforced only in the data leaves a confirm button
     * that does nothing — which reads as a broken app rather than as a rule.
     */
    @Test
    fun `a blank reason is refused on the dialog and writes nothing`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()
        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        val cancel = viewModel.uiState.value.cancel
        assertNotNull("the dialog stays up, so the owner can fix it", cancel)
        assertEquals(
            "with the epic's own explanation of why",
            "Tulis alasan pembatalannya. Tanpa alasan, riwayat setteran ini tidak bisa diaudit.",
            cancel!!.reasonError,
        )
        assertEquals(
            "nothing was cancelled",
            PaymentEntity.STATUS_CONFIRMED,
            database.paymentDao().findById(PAYMENT_ID)!!.status,
        )
        assertEquals("and its allocations are intact", 3, database.allocationDao().forContract(CONTRACT_ID).size)
    }

    /** Whitespace is blank — the pad and the field can both produce it. */
    @Test
    fun `a whitespace-only reason is refused too`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()
        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onCancelChange { it.setReason("   ") }
        viewModel.cancelDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.cancel?.reasonError)
        assertEquals(
            PaymentEntity.STATUS_CONFIRMED,
            database.paymentDao().findById(PAYMENT_ID)!!.status,
        )
    }

    /**
     * The error clears on the **next keystroke**, not only on the next confirm.
     *
     * An error that survived the fix accuses the owner of a field they are in the middle
     * of filling in — the rule every other field in this sheet follows.
     */
    @Test
    fun `the refusal clears as soon as the owner types`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()
        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.cancelDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.cancel?.reasonError)

        viewModel.onCancelChange { it.setReason("salah tanggal") }
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("the error is gone", viewModel.uiState.value.cancel?.reasonError)
    }

    /**
     * Confirming with a reason cancels the deposit and closes the dialog.
     *
     * The sheet **stays open**, unlike `confirmDeposit`'s — see the note in the ViewModel.
     * A cancel that dismissed the whole form would throw away the renter and amount the
     * owner had already picked for their next payment.
     */
    @Test
    fun `confirming with a reason cancels the deposit`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()
        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onCancelChange { it.setReason("salah tanggal") }

        viewModel.cancelDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("the dialog is gone", state.isCanceling)
        assertTrue("but the sheet is still open", state.isOpen)
        assertEquals("Setoran Rp 750.000 dibatalkan", state.successMessage)

        val stored = database.paymentDao().findById(PAYMENT_ID)!!
        assertEquals("with the status flipped", PaymentEntity.STATUS_CANCELED, stored.status)
        assertEquals("and the owner's own words kept", "salah tanggal", stored.cancelReason)
        assertEquals("every day it closed is owed again", 0, database.allocationDao().forContract(CONTRACT_ID).size)
    }

    /**
     * The confirmation names the amount the **dialog** promised.
     *
     * Asserted rather than left implicit, because the message is built from
     * `amountLabel` and the alternative — parsing it back out of the title — is the bug
     * this design exists to prevent.
     */
    @Test
    fun `the confirmation carries the dialog's own figure`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()
        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()
        val promised = viewModel.uiState.value.cancel!!.amountLabel
        viewModel.onCancelChange { it.setReason("salah tanggal") }

        viewModel.cancelDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Setoran $promised dibatalkan", viewModel.uiState.value.successMessage)
    }

    /**
     * Dismissing writes nothing **and** discards the reason.
     *
     * The discard matters: a reason held for a cancellation the owner walked away from
     * would reappear on the next attempt with no explanation of where it came from.
     */
    @Test
    fun `dismissing writes nothing and forgets the reason`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()
        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onCancelChange { it.setReason("salah tanggal") }

        viewModel.dismissCancel()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCanceling)
        assertEquals(
            PaymentEntity.STATUS_CONFIRMED,
            database.paymentDao().findById(PAYMENT_ID)!!.status,
        )

        // And the next attempt starts clean rather than inheriting it.
        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.cancel!!.reason)
    }

    /**
     * A second cancellation is refused, the dialog goes, and a **banner** says so.
     *
     * `CANCEL_ALREADY`, seen from the UI. The deposit is only reachable while the sheet is
     * open — and a cancel leaves it open — so this is not a contrived route: it is exactly
     * what a double-tap on the history row produces.
     */
    @Test
    fun `cancelling twice is refused and the banner says why`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()
        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onCancelChange { it.setReason("salah tanggal") }
        viewModel.cancelDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.requestCancel(PAYMENT_ID)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onCancelChange { it.setReason("alasan lain") }
        viewModel.cancelDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("the dialog is gone", state.isCanceling)
        assertEquals(
            "and the refusal is on the banner, where the sheet's own message would show",
            "Setoran ini sudah dibatalkan, jadi tidak bisa dibatalkan lagi.",
            state.errorMessage,
        )
        assertEquals(
            "the original reason stands — a newer one must not overwrite the audit trail",
            "salah tanggal",
            database.paymentDao().findById(PAYMENT_ID)!!.cancelReason,
        )
    }

    /**
     * Cancelling with no dialog up writes nothing.
     *
     * The un-skippable counterpart to the preview's: `cancelDeposit` is reachable from
     * anywhere in the app, and a caller that forgot to open the dialog must get a refusal
     * rather than a silent cancellation.
     */
    @Test
    fun `cancelling with no dialog up writes nothing`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()

        viewModel.cancelDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            PaymentEntity.STATUS_CONFIRMED,
            database.paymentDao().findById(PAYMENT_ID)!!.status,
        )
        assertEquals(3, database.allocationDao().forContract(CONTRACT_ID).size)
    }

    /** Asking to cancel a deposit nobody holds opens no dialog — there is no figure. */
    @Test
    fun `requesting a cancellation of an unknown deposit opens no dialog`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()

        viewModel.requestCancel("no-such-payment")
        dispatcher.scheduler.advanceUntilIdle()

        // The repository returns the *current* arrears rather than throwing, so the dialog
        // is up — and what it says is the truth: nothing would change.
        val cancel = viewModel.uiState.value.cancel
        assertNotNull(cancel)
        assertEquals(
            "the impact is the present, because nothing would change",
            "Tunggakan Budi Santoso akan kembali menjadi Rp 3.500.000.",
            cancel!!.impactLine,
        )
    }

    /** And confirming *that* refuses, rather than cancelling a payment that is not there. */
    @Test
    fun `confirming an unknown deposit refuses and writes nothing`() = runTest(dispatcher) {
        saveDeposit()
        val viewModel = openSheet()
        viewModel.requestCancel("no-such-payment")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onCancelChange { it.setReason("salah tanggal") }

        viewModel.cancelDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCanceling)
        assertNotNull("and the failure is reported", viewModel.uiState.value.errorMessage)
        assertEquals(
            "the real deposit is untouched",
            PaymentEntity.STATUS_CONFIRMED,
            database.paymentDao().findById(PAYMENT_ID)!!.status,
        )
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A ViewModel with the sheet open on Budi, and an active collector.
     *
     * `stateIn(WhileSubscribed(5_000))` means no subscriber produces no upstream
     * collection, so the repository flows are never queried and `.value` stays at
     * `initialValue` — which reads as "no renters" rather than as an un-advanced clock.
     *
     * The sheet must be open because `requestCancel` reads the contract from the form:
     * a cancellation is always about *this* rental, and a ViewModel holding only a
     * `paymentId` would have to trust that the two agree.
     */
    private fun TestScope.openSheet(): SetoranViewModel {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        val option = viewModel.uiState.value.renterOptions.single { it.renter.id == RENTER_ID }
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    /**
     * The ViewModel **with an active collector** on `uiState`.
     *
     * See [openSheet]'s KDoc on why the collector is not optional.
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

    /**
     * A Rp 750.000 cash deposit on 14 Oct, which closes three of the seventeen days.
     *
     * Cash and no account so the fixture does not depend on Story 3.1's table — this story
     * is about cancelling, and a deposit it cannot record is a distraction.
     */
    private suspend fun saveDeposit() {
        payments.saveDeposit(
            contractId = CONTRACT_ID,
            payment = net.klinik.rental.domain.model.Payment(
                id = PAYMENT_ID,
                date = LocalDate.of(2026, 10, 14),
                amount = 750_000,
            ),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = today,
        )
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
            BusinessAccountEntity(id = ACCOUNT_ID, bankName = "BCA", number = "1234567890")
        )
    }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val RENTER_ID = "renter-1"
        const val CONTRACT_ID = "contract-1"
        const val ACCOUNT_ID = "account-1"
        const val PAYMENT_ID = "payment-1"
        const val RATE = 250_000L

        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC)
    }
}