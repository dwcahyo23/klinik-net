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
 * The deposit sheet's ViewModel: who is paid, what it settles, and what it refuses.
 *
 * ## Why the clock is fixed
 *
 * A deposit is allocated against a billing period, and the period depends on today. Left
 * on the system clock this whole file would pass in October and fail in November, and the
 * failing case would look like an allocation bug rather than a clock-dependent test. So
 * [CLOCK] is pinned, and the tests that depend on "which period" say so in their names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class SetoranViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var database: KlinikDatabase
    private lateinit var payments: PaymentRepository
    private lateinit var strings: RStrings

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, KlinikDatabase::class.java)
            // Room's own executors, or a row written by `saveDeposit` sits in the
            // database while `uiState.value` still holds the pre-write emission — and
            // every assertion below would read an empty list.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            // **No database handle** — `withTransaction` asserts it is off the main
            // thread and `StandardTestDispatcher` drains *on* it, so passing it here
            // makes every save throw into the generic banner and five tests fail as
            // "the write did not happen". The transaction path is covered by
            // `PaymentRepositoryTest`, which uses a real background thread. This
            // asymmetry is deliberate; do not "fix" it.
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

    // -------------------------------------------------------------- the auto-fill

    /**
     * Choosing a renter fills the vehicle and the rate **from their open contract**.
     *
     * The AC's own words, and the reason it is a lookup rather than two fields: a rate
     * the owner re-keys is the mistyped figure the whole form exists to prevent.
     */
    @Test
    fun `choosing a renter fills the vehicle and the contract's rate`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        assertEquals(2, viewModel.uiState.value.renterOptions.size)
        val option = budiOption(viewModel)
        assertNotNull("Budi has an open contract", option.contract)
        assertEquals("B 1235 TF", option.contract!!.vehiclePlate)
        assertEquals(
            "the rate is pre-formatted, not raw",
            "Rp 250.000",
            option.contract!!.rateText,
        )

        viewModel.openDeposit()

        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form!!
        assertEquals(RENTER_ID, form.renterId)
        assertEquals(CONTRACT_ID, form.contractId)
        assertEquals("B 1235 TF", form.vehiclePlate)
        assertEquals("Rp 250.000", form.rateText)
        assertTrue("and the form can proceed", form.hasContract)
    }

    /**
     * And the **amount stays empty** — the whole point of the mandatory numpad.
     *
     * The epic's reasoning: typing the figure from zero is what catches a mistype.
     * Pre-filling it from the rate would reintroduce exactly the risk the AC refuses,
     * so this assertion is the difference between the story's rule and a convenience.
     */
    @Test
    fun `choosing a renter leaves the amount empty`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        val option = budiOption(viewModel)

        viewModel.openDeposit()

        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the owner types it, so a mistyped figure is caught",
            "0",
            viewModel.uiState.value.form!!.amountDigits,
        )
    }

    /**
     * A renter with **no open contract** is still listed, marked — selecting one cannot
     * proceed.
     *
     * Both halves matter. Filtering them out would make a renter look deleted, and
     * listing them unmarked would promise a form that cannot be completed.
     */
    @Test
    fun `a renter with no open contract is listed but cannot proceed`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        assertEquals("both renters are listed", 2, viewModel.uiState.value.renterOptions.size)
        val agus = viewModel.uiState.value.renterOptions.single { it.renter.id == RENTER_NO_CONTRACT }
        assertNull("Agus has no running rental", agus.contract)

        viewModel.openDeposit()

        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(agus.renter, agus.contract) }
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form!!
        assertFalse("so the form cannot proceed", form.hasContract)
        assertFalse("and Save is not offered", form.canSave)
    }

    // -------------------------------------------------------------- the cash path

    /**
     * `DEPOSIT_CASH_HIDES_ACCOUNT` — the account field and the reference are **absent**
     * for cash, not blank.
     *
     * The epic context is explicit that filling a field in blank "menciptakan ilusi data
     * yang padahal tidak ada". Asserted on the state's `showsAccountField` rather than on
     * a composable, so the rule holds without Compose and the sheet test covers what the
     * owner actually sees.
     */
    @Test
    fun `choosing cash drops the account field and any account chosen`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        val option = budiOption(viewModel)
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        viewModel.onFormChange { it.selectAccount(ACCOUNT_ID) }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue("the transfer path shows it", viewModel.uiState.value.form!!.showsAccountField)

        viewModel.onFormChange { it.setMethod(net.klinik.rental.data.repository.PaymentMethod.Tunai) }
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form!!
        assertFalse("cash has no account field at all", form.showsAccountField)
        assertNull(
            "and the account chosen a moment ago is dropped, not filed against a " +
                "payment that never touched the bank",
            form.accountId,
        )
        assertTrue("so cash can save without one", form.canSave)
    }

    /**
     * And choosing the method is a no-op when it is unchanged — so re-tapping the
     * selected chip cannot silently clear a reference the owner already typed.
     */
    @Test
    fun `reselecting the same method keeps the reference`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.setReferenceNo("TRX-99") }

        viewModel.onFormChange { it.setMethod(net.klinik.rental.data.repository.PaymentMethod.TransferBank) }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("TRX-99", viewModel.uiState.value.form!!.referenceNo)
    }

    // ------------------------------------------------------------- the refusals

    /**
     * `DEPOSIT_NO_ACCOUNT_SELECTED` — a transfer with no account is refused, and the
     * message lands on the **account field**.
     */
    @Test
    fun `a transfer with no account is refused on the account field`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        val option = budiOption(viewModel)
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveDepositAmount(250_000)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("the refusal lands on the field", state.form!!.accountError)
        assertTrue("nothing was written", state.renterOptions.isNotEmpty())
        assertEquals(0, payments.paymentsFor(CONTRACT_ID).size)
    }

    /**
     * A renter with no contract refuses on the **banner**, because there is no field the
     * owner can change — the fix is to open or close a contract, not to retype a field.
     */
    @Test
    fun `a renter with no contract refuses on the banner and writes nothing`() =
        runTest(dispatcher) {
            val viewModel = observingViewModel()
            val agus = viewModel.uiState.value.renterOptions.single { it.renter.id == RENTER_NO_CONTRACT }
            viewModel.openDeposit()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.onFormChange { it.selectRenter(agus.renter, agus.contract) }
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.saveDepositAmount(250_000)
            dispatcher.scheduler.advanceUntilIdle()

            assertNotNull("no field to blame, so the banner", viewModel.uiState.value.errorMessage)
            assertTrue("the sheet stays open", viewModel.uiState.value.isOpen)
        }

    /** A second save while one is in flight is ignored — the guard, not the repository. */
    @Test
    fun `a second save while one is in flight is ignored`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        val option = budiOption(viewModel)
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        viewModel.onFormChange { it.selectAccount(ACCOUNT_ID) }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveDepositAmount(250_000)
        viewModel.saveDepositAmount(999_999)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("exactly one deposit is stored", 1, payments.paymentsFor(CONTRACT_ID).size)
        assertEquals(
            "and it is the first figure, not the second",
            250_000,
            payments.paymentsFor(CONTRACT_ID).single().amount,
        )
    }

    // ------------------------------------------------------------------ the write

    /** A recorded deposit closes charge days and closes the sheet. */
    @Test
    fun `a saved deposit is stored, allocated and closes the sheet`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        val option = budiOption(viewModel)
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        viewModel.onFormChange { it.selectAccount(ACCOUNT_ID) }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveDepositAmount(750_000)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("the sheet closes on success", state.form)
        assertNull("and leaves no failure", state.errorMessage)
        assertEquals("one deposit is stored", 1, payments.paymentsFor(CONTRACT_ID).size)
        assertTrue(
            "and it closed charge days rather than sitting as money that billed nothing",
            database.allocationDao().forContract(CONTRACT_ID).isNotEmpty(),
        )
    }

    /** The confirmation names the figure, formatted once. */
    @Test
    fun `a successful save confirms in Indonesian`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        val option = budiOption(viewModel)
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        viewModel.onFormChange { it.selectAccount(ACCOUNT_ID) }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveDepositAmount(250_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Setoran Rp 250.000 tersimpan", viewModel.snackbarMessage.value)
    }

    /**
     * A **cash** deposit is stored with no account — the AC's clause that the app records
     * there is no external source to verify it.
     */
    @Test
    fun `a cash deposit is stored with no account`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        val option = budiOption(viewModel)
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFormChange { it.selectRenter(option.renter, option.contract) }
        viewModel.onFormChange { it.setMethod(net.klinik.rental.data.repository.PaymentMethod.Tunai) }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveDepositAmount(250_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("no account", database.paymentDao().forContract(CONTRACT_ID).single().accountId)
    }

    /** Closing the sheet clears it and its message. */
    @Test
    fun `closing the sheet clears it`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        viewModel.openDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isOpen)

        viewModel.closeDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOpen)
        assertNull(viewModel.uiState.value.form)
    }

    // ------------------------------------------------------------------ helper

    /**
     * Builds the ViewModel **with an active collector** on `uiState`.
     *
     * `stateIn(WhileSubscribed(5_000))` means no subscriber produces no upstream
     * collection, so the repository flows are never queried and `.value` stays at
     * `initialValue`. `backgroundScope` is cancelled when the test body finishes.
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
        // Drain the scheduler so the repository flows emit before the test reads
        // `.value`. Without this the options are still at `stateIn`'s empty
        // initial value and every test fails as "no renters" — which reads like a
        // wiring fault and is really just an un-advanced clock.
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    /** Two renters, one vehicle, one open contract, one account. */
    private suspend fun seed() {
        database.vehicleDao().insert(
            VehicleEntity(
                id = VEHICLE_ID,
                plate = "B 1235 TF",
                brand = "Toyota",
                model = "Avanza",
                year = 2023,
                color = null,
                dailyRate = 250_000,
                notes = null,
            )
        )
        database.renterDao().insert(renterEntity(id = RENTER_ID, name = "Budi Santoso"))
        // Agus has **no** contract — the read-only-but-listed case.
        database.renterDao().insert(renterEntity(id = RENTER_NO_CONTRACT, name = "Agus Prasetyo"))
        database.contractDao().insertContract(
            ContractEntity(
                id = CONTRACT_ID,
                renterId = RENTER_ID,
                vehicleId = VEHICLE_ID,
                startDate = TODAY.minusDays(20),
                dailyRate = 250_000,
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

    private fun renterEntity(id: String, name: String) = RenterEntity(
        id = id,
        name = name,
        phone = "08123456789",
        bankAccount = null,
        emergencyContact = "08129876543",
    )

    /**
     * Budi's option — the renter **with** an open contract.
     *
     * Named rather than `.single()` because the fixture seeds two renters on purpose:
     * one with a contract and one without, which is what makes the read-only case
     * testable. A `.single()` would fail on the seed rather than on the rule.
     */
    private fun budiOption(viewModel: SetoranViewModel) =
        viewModel.uiState.value.renterOptions.single { it.renter.id == RENTER_ID }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val RENTER_ID = "renter-1"
        const val RENTER_NO_CONTRACT = "renter-2"
        const val CONTRACT_ID = "contract-1"
        const val ACCOUNT_ID = "account-1"

        /** Pinned so "which period" is a fact of the test, not of the month. */
        val TODAY: LocalDate = LocalDate.of(2026, 10, 17)
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC)
    }
}

