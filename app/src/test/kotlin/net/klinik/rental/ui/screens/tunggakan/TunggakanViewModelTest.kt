package net.klinik.rental.ui.screens.tunggakan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.flow
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
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.ui.components.KnBadgeTone
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Story 4.1's ViewModel: the list's ordering, its selection, and its loading gate.
 *
 * ## Why this file exists and is not more cases in the screen test
 *
 * Because the screen test proves what is **drawn** and this one proves what the ViewModel
 * **decides** — the order, the selection's lifetime, and the rule that "not read yet" is not
 * the same branch as "nothing owed". A pure-function test over `ArrearsRow` could not see any
 * of those, because all three involve the combine.
 *
 * ## The clock
 *
 * Pinned. The arrears figure depends on which billing period "today" falls in, so a test that
 * used `LocalDate.now()` would pass in October and fail in November — and the `domain` module
 * has no Android block at all, which is what keeps that failure a build error rather than a
 * production surprise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class TunggakanViewModelTest {

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
            // Room's own query executor, or a row written by the write path sits in the
            // database while `uiState.value` still holds the pre-write emission. The
            // **transaction** executor is deliberately left on Room's own background pool:
            // no write in this file is a transaction, because `withTransaction` refuses the
            // main thread and `StandardTestDispatcher` drains *on* it.
            .setQueryExecutor(dispatcher.asExecutor())
            .build()
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            // **No database handle**, for the reason above. The transaction path is covered by
            // `TunggakanArrearsTest`, which runs on a real background thread — the asymmetry
            // is deliberate, exactly as `CancelDepositViewModelTest` records.
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
     * The list is **ordered**, not merely present.
     *
     * The AC's ordering is the domain's `inArrearsOrder`, and the point of this test is that
     * the ViewModel applies it rather than handing the screen an unsorted list for it to
     * sort — a second sort would be a second answer to "who do I contact first".
     */
    @Test
    fun `rows come out in arrears order, largest first`() = runTest(dispatcher) {
        saveDeposit(SMALL_CONTRACT_ID, 750_000)
        saveDeposit(LARGE_CONTRACT_ID, 250_000)
        val viewModel = observingViewModel()

        val rows = viewModel.uiState.value.rows
            // Every seeded contract owes the full period unless something is paid, so the
            // two under test are singled out here — an assertion over the whole list would
            // be asserting the fixture rather than the ordering.
            .filter { it.contractId == LARGE_CONTRACT_ID || it.contractId == SMALL_CONTRACT_ID }

        // `SMALL_CONTRACT_ID` is the *larger deposit* (Rp 750.000), which leaves the
        // *smaller* balance — Rp 4.250.000 − Rp 750.000 = Rp 3.500.000 — while
        // `LARGE_CONTRACT_ID` was paid Rp 250.000 and still owes Rp 4.000.000.
        //
        // Naming the fixture after the deposit rather than the arrears is deliberate: it
        // makes the ordering assertion disagree with the fixture's own names, so a sort that
        // ran on deposit size or on id instead of on the arrears figure would fail here
        // rather than pass for the right reason.
        assertEquals("both contracts under test are listed", 2, rows.size)
        assertEquals(
            "the larger arrears figure is first, whatever the deposit size was",
            LARGE_CONTRACT_ID,
            rows[0].contractId,
        )
        assertEquals("and the smaller arrears figure second", SMALL_CONTRACT_ID, rows[1].contractId)
    }

    /**
     * Every row carries the renter's **name** and the plate, not their ids.
     *
     * The join happens in the ViewModel from the observed flows, and the fallback to the id
     * is what a missing renter would get — asserted here so the names are known to come from
     * the flows rather than from the contract's own ids.
     */
    @Test
    fun `rows carry the joined names rather than ids`() = runTest(dispatcher) {
        saveDeposit(OWING_CONTRACT_ID, 250_000)
        val viewModel = observingViewModel()

        val row = viewModel.uiState.value.rows.first { it.contractId == OWING_CONTRACT_ID }

        assertEquals("Budi Santoso", row.renterName)
        assertEquals("B 1235 TF", row.vehiclePlate)
    }

    /**
     * A row's figure is the AC's shape, with **both** halves.
     *
     * Asserted on the composed string, because a row carrying the amount without the day
     * count — or the reverse — is precisely the defect the criterion names.
     */
    @Test
    fun `a row's figure carries the amount and the day count together`() = runTest(dispatcher) {
        saveDeposit(OWING_CONTRACT_ID, 750_000)
        val viewModel = observingViewModel()

        val row = viewModel.uiState.value.rows.first { it.contractId == OWING_CONTRACT_ID }

        // Rp 4.250.000 owed less Rp 750.000 paid, across the fourteen days still open.
        assertEquals("Rp 3.500.000 · 14 hari", row.figureText)
        assertEquals("and the badge reads the same count", "14 hari", row.daysLabel)
    }

    /**
     * The badge word follows the day count, through the domain's ladder.
     *
     * Seven open days is KRITIS; the boundary matters because an off-by-one between 6 and 7
     * would show the owner the wrong urgency on the only number they act on.
     */
    @Test
    fun `the badge word is derived from the day count`() = runTest(dispatcher) {
        saveDeposit(OWING_CONTRACT_ID, 750_000)
        val viewModel = observingViewModel()

        val row = viewModel.uiState.value.rows.first { it.contractId == OWING_CONTRACT_ID }

        assertEquals("14 open days is KRITIS", "Kritis", row.riskLabel)
        assertEquals("and the tone follows it", KnBadgeTone.Danger, row.tone)
    }

    /**
     * An ended contract stays **and** is marked, rather than being dropped or listed as open.
     *
     * `LIST_ENDED_OWES` seen from the ViewModel. Dropping it is how an owner chasing real
     * money loses sight of it, and listing it unmarked would claim the rental is running.
     *
     * This is the case `ContractDao.observeOpen()` would silently drop, and it is why the
     * list reads **every** contract rather than the open ones.
     */
    @Test
    fun `an ended contract with a balance stays and is marked finished`() = runTest(dispatcher) {
        saveDeposit(ENDED_CONTRACT_ID, 250_000)
        val viewModel = observingViewModel()
        val before = viewModel.uiState.value.rows.first { it.contractId == ENDED_CONTRACT_ID }

        // Ends mid-period, so its balance is untouched by the end: the charge days stop
        // being produced after it, and what the owner still owes is what it already owed.
        //
        // Ends the row directly rather than through `ContractRepository.endContract`,
        // because that one is a transaction and `withTransaction` refuses the main thread —
        // which is exactly why `payments` above takes the nullable database handle and a
        // write-path repository cannot be used from a `StandardTestDispatcher` test at all.
        // The three columns it would change are set together here, which is what its KDoc
        // says the transaction exists to guarantee.
        database.contractDao().update(
            database.contractDao().findById(ENDED_CONTRACT_ID)!!.copy(
                endDate = today,
                activeVehicleId = null,
                activeRenterId = null,
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        val after = viewModel.uiState.value.rows.first { it.contractId == ENDED_CONTRACT_ID }
        assertTrue("and it is marked as finished", after.isEnded)
        assertEquals("with the balance still owed", before.figureText, after.figureText)
    }

    /**
     * A paid contract is **absent** — not listed at zero.
     *
     * The claim a caller would make about "nothing owed" and the claim "nobody has paid
     * anything" are different, and this screen can only ever be read as one of them.
     */
    @Test
    fun `a contract owing nothing is absent rather than listed at zero`() = runTest(dispatcher) {
        saveDeposit(PAID_CONTRACT_ID, 4_250_000)
        saveDeposit(OWING_CONTRACT_ID, 250_000)
        val viewModel = observingViewModel()

        val ids = viewModel.uiState.value.rows.map { it.contractId }

        assertFalse("the paid contract is not on the list", ids.contains(PAID_CONTRACT_ID))
        assertTrue("but the owing one is", ids.contains(OWING_CONTRACT_ID))
    }

    /**
     * Selecting a row reaches the detail pane, and tapping it **again** clears the selection.
     *
     * The second half is not a convenience: the pane is *beside* the list rather than behind
     * it, so a row that cannot be un-tapped is a detail pane the owner cannot dismiss
     * without leaving the screen.
     */
    @Test
    fun `tapping a row selects it and tapping again clears the selection`() = runTest(dispatcher) {
        saveDeposit(OWING_CONTRACT_ID, 250_000)
        val viewModel = observingViewModel()

        viewModel.onSelect(OWING_CONTRACT_ID)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(OWING_CONTRACT_ID, viewModel.uiState.value.selectedRow?.contractId)

        viewModel.onSelect(OWING_CONTRACT_ID)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull("and the second tap dismisses it", viewModel.uiState.value.selectedRow)
    }

    /**
     * A row that stops owing takes its selection with it.
     *
     * The failure the selection field's KDoc rules out: a pane that silently retargets
     * itself would show one person's figure right after a payment landed on another.
     */
    @Test
    fun `a row that stops owing leaves the detail pane empty rather than retargeting`() =
        runTest(dispatcher) {
            saveDeposit(OWING_CONTRACT_ID, 250_000)
            val viewModel = observingViewModel()
            viewModel.onSelect(OWING_CONTRACT_ID)
            dispatcher.scheduler.advanceUntilIdle()
            assertNotNull("selected", viewModel.uiState.value.selectedRow)

            // Pay it off in full: the first deposit covers one day, this one covers the
            // rest, so between them the contract owes nothing and leaves the list.
            payments.saveDeposit(
                contractId = OWING_CONTRACT_ID,
                payment = Payment(id = "pay-off", date = today, amount = 4_250_000),
                method = PaymentMethod.Tunai,
                accountId = null,
                referenceNo = null,
                notes = null,
                asOf = today,
            )
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(
                "the row is gone from the list",
                viewModel.uiState.value.rows.any { it.contractId == OWING_CONTRACT_ID },
            )
            assertNull(
                "and the pane keeps its prompt rather than retargeting to another renter",
                viewModel.uiState.value.selectedRow,
            )
        }

    /**
     * The list re-emits when a deposit lands, with no refresh anywhere.
     *
     * What "no manual refresh" has to mean: the number moves on its own. A ViewModel that
     * read once would pass every count assertion above and fail exactly here.
     */
    @Test
    fun `a deposit moves the figure without any refresh signal`() = runTest(dispatcher) {
        saveDeposit(OWING_CONTRACT_ID, 250_000)
        val viewModel = observingViewModel()
        val before = viewModel.uiState.value.rows.first { it.contractId == OWING_CONTRACT_ID }
        dispatcher.scheduler.advanceUntilIdle()

        saveDeposit(OWING_CONTRACT_ID, 750_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "the figure moved on its own",
            viewModel.uiState.value.rows
                .first { it.contractId == OWING_CONTRACT_ID }
                .figureText != before.figureText,
        )
    }

    /**
     * A failed read reaches the screen as a **banner**, and draws no rows.
 *
 * ## Why this test exists at all
     *
     * Because the first version of this story set `errorMessage.value` inside `catch` and
     * **nothing carried it to `uiState`**: `errorMessage` was not one of the `combine`'s
     * inputs, so the write re-rendered nothing. The screen would have sat on a skeleton
     * forever — the owner unable to tell "no data" from "nothing owed", which is the one
     * distinction this screen exists to keep.
     *
     * The fix was to make the failure a **value** in the sealed hierarchy rather than a
     * side-channel write, so it flows through the same `combine` the rows do. This test is
     * what pins that, and it fails against the side-channel version.
     */
    @Test
    fun `a failed read reaches the screen as a banner rather than a silent skeleton`() =
        runTest(dispatcher) {
            val viewModel = failingViewModel()
            backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue("the failure is a state, not a side channel", state.isFailed)
            assertNotNull("and it carries the message the screen draws", state.errorMessage)
            assertEquals(
                "in Indonesian",
                "Gagal menghitung tunggakan. Coba lagi.",
                state.errorMessage,
            )
        }

    /**
     * And a failed read draws **no rows**, rather than an empty list.
     *
     * An empty arrears list is the claim "nobody owes you anything". A banner drawn over one
     * states that confidently and then apologises for it, which is worse than saying
     * nothing — so the two must not appear together.
     */
    @Test
    fun `a failed read draws no rows, so it cannot read as nobody owing`() =
        runTest(dispatcher) {
            val viewModel = failingViewModel()
            backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue("no rows", state.rows.isEmpty())
            assertFalse(
                "and not the empty state either — that is a claim, not a fallback",
                state.isEmpty,
            )
        }

    /**
     * Dismissing the banner is a **retry**, and a retry that recovers.
     *
     * The half that could not exist before: `consumeError` used to clear a message the
     * ViewModel did not read, so it changed nothing the owner could see. It now re-subscribes
     * the source, so dismissing either hides the banner by recovering or — if the read still
     * fails — brings the banner straight back. There is no state where the banner is gone and
     * no figures behind it.
     *
     * The seam returns a flow that fails once and then succeeds, so the recovery path is
     * exercised end to end rather than asserted.
     */
    @Test
    fun `dismissing the banner retries the read and recovers`() = runTest(dispatcher) {
        val attempts = AtomicInteger(0)
        val flaky = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
        )
        val viewModel = TunggakanViewModel(
            payments = flaky,
            renterRepository = RenterRepository(database.renterDao()),
            vehicleRepository = VehicleRepository(database.vehicleDao()),
            strings = strings,
            clock = CLOCK,
            arrearsSourceOverride = {
                flow {
                    if (attempts.getAndIncrement() == 0) {
                        throw IllegalStateException("the read failed")
                    }
                    emit(emptyList())
                }
            },
        )
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertTrue("the first attempt failed", failed.isFailed)
        assertNotNull("and said so", failed.errorMessage)

        viewModel.consumeError()
        dispatcher.scheduler.advanceUntilIdle()

        val recovered = viewModel.uiState.value
        assertFalse("the retry cleared the failure", recovered.isFailed)
        assertNull(
            "and the banner with it — never a dismissal that leaves a silent empty list",
            recovered.errorMessage,
        )
        assertEquals("the read really was attempted again", 2, attempts.get())
    }

    /**
     * A dismissal that does **not** recover brings the banner back rather than hiding a
     * failure the owner still cannot see.
     *
     * The opposite half of the retry: a screen that swallowed the dismissal would show an
     * empty arrears list with no explanation, which is the claim this whole screen exists to
     * avoid making.
     */
    @Test
    fun `a dismissal that cannot recover shows the banner again`() = runTest(dispatcher) {
        val viewModel = failingViewModel()
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFailed)

        viewModel.consumeError()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("still failing, so still said", state.isFailed)
        assertNotNull("and the message is back", state.errorMessage)
        assertTrue("with no rows claiming nobody owes", state.rows.isEmpty())
    }

    /**
     * A ViewModel over a repository whose arrears read **fails**.
     *
     * A hand-built `TunggakanUiState(isFailed = true)` would prove the screen's branch and
     * nothing about the path that reaches it — which is the half that was broken. So the
     * flow itself throws, and the test watches the state change because of it.
     *
     * `open` rather than `abstract`, because `PaymentRepository` is a final class and
     * Kotlin has no way to stub a method on it. Wrapping it is therefore the only seam
     * available, and it is an honest one: the test supplies the *behaviour* the ViewModel
     * reacts to rather than the reaction, which is the half under test.
     */
    private fun TestScope.failingViewModel(): TunggakanViewModel {
        val failing = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
        )
        return TunggakanViewModel(
            payments = failing,
            renterRepository = RenterRepository(database.renterDao()),
            vehicleRepository = VehicleRepository(database.vehicleDao()),
            strings = strings,
            clock = CLOCK,
            // The seam: a read that throws the instant it is collected. Everything the
            // ViewModel does on failure — the banner, the empty rows, the state — is
            // exercised against a flow that genuinely fails.
            arrearsSourceOverride = { flow { throw IllegalStateException("the read failed") } },
        )
    }

    // -------------------------------------------------------------------- helpers

    /**
     * The ViewModel **with an active collector** on `uiState`.
     *
     * `stateIn(WhileSubscribed(5_000))` produces no upstream collection without a subscriber,
     * so the repository flows would never be queried and `.value` would stay at the initial
     * value — which reads as "no rows" rather than as an un-advanced clock. Every test that
     * reads `uiState.value` needs this, which is why it is a helper and not a per-test block.
     */
    private fun TestScope.observingViewModel(): TunggakanViewModel {
        val viewModel = TunggakanViewModel(
            payments = payments,
            renterRepository = RenterRepository(database.renterDao()),
            vehicleRepository = VehicleRepository(database.vehicleDao()),
            strings = strings,
            clock = CLOCK,
        )
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private suspend fun saveDeposit(contractId: String, amount: Long) {
        payments.saveDeposit(
            contractId = contractId,
            payment = Payment(id = "pay-$contractId-$amount", date = today, amount = amount),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = today,
        )
    }

    private suspend fun seed() {
        listOf(
            Triple(OWING_CONTRACT_ID, "v1", "Budi Santoso") to "B 1235 TF",
            Triple(PAID_CONTRACT_ID, "v2", "Siti Aminah") to "B 9876 AB",
            Triple(LARGE_CONTRACT_ID, "v3", "Andi") to "B 5555 ZZ",
            Triple(SMALL_CONTRACT_ID, "v4", "Rina") to "B 4444 AA",
            Triple(ENDED_CONTRACT_ID, "v5", "Doni") to "B 3333 BB",
        ).forEach { (ids, plate) ->
            val (contractId, vehicleId, name) = ids
            database.vehicleDao().insert(
                VehicleEntity(
                    id = vehicleId,
                    plate = plate,
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
                    id = "r-$contractId",
                    name = name,
                    phone = "08123456789",
                    bankAccount = null,
                    emergencyContact = "08129876543",
                )
            )
            database.contractDao().insertContract(
                ContractEntity(
                    id = contractId,
                    renterId = "r-$contractId",
                    vehicleId = vehicleId,
                    startDate = LocalDate.of(2026, 10, 1),
                    dailyRate = RATE,
                    restDay = null,
                    activeVehicleId = vehicleId,
                    activeRenterId = "r-$contractId",
                    endDate = null,
                )
            )
        }
    }

    private companion object {
        const val OWING_CONTRACT_ID = "c-owing"
        const val PAID_CONTRACT_ID = "c-paid"
        const val LARGE_CONTRACT_ID = "c-large"
        const val SMALL_CONTRACT_ID = "c-small"
        const val ENDED_CONTRACT_ID = "c-ended"
        const val RATE = 250_000L

        /** The whole period owes Rp 4.250.000 at Rp 250.000 a day. */
        const val FULL_PERIOD = 4_250_000L

        val CLOCK = Clock.fixed(
            Instant.parse("2026-10-17T09:00:00Z"),
            ZoneOffset.UTC,
        )
    }
}
