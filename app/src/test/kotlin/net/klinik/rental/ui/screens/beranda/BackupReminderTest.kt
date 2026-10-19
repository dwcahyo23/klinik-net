package net.klinik.rental.ui.screens.beranda

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.klinik.rental.data.backup.BackupRepository
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.SettingEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.ui.strings.RStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Story 5.5's reminder, against a real database.
 *
 * ## Why the matrix is run through the **real** repository rather than a stub
 *
 * Because the three facts it compares — the last export date, the deposit count and today's date
 * — are each written by a different piece of code. A reminder driven by a hand-built flow would
 * pass while the export's write and the reminder's read disagreed about the key name, which is
 * the one thing that could actually happen.
 *
 * ## Why the clock is the interesting input
 *
 * Because two of the three cases are about **time**: exported today means silent, exported
 * yesterday means a reminder naming yesterday, and the nudge hour means silence until 09:00. A
 * fixed clock is what makes those three distinguishable without waiting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupReminderTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: KlinikDatabase
    private lateinit var backups: BackupRepository
    private lateinit var strings: RStrings

    /** 2026-10-19, 10:00 local — after the default 09:00 nudge hour. */
    private val now = Instant.parse("2026-10-19T10:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        )
            // Both executors on the test dispatcher — Room's own executor is not drained by
            // `advanceUntilIdle`, and every assertion here reads the result of a Room write.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        strings = RStrings(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    /** A repository at [instant], so "today" and "this hour" are both settable. */
    private fun backupsAt(instant: Instant): BackupRepository = BackupRepository(
        vehicleDao = database.vehicleDao(),
        renterDao = database.renterDao(),
        contractDao = database.contractDao(),
        serviceExceptionDao = database.serviceExceptionDao(),
        businessAccountDao = database.businessAccountDao(),
        paymentDao = database.paymentDao(),
        allocationDao = database.allocationDao(),
        settingDao = database.settingDao(),
        clock = Clock.fixed(instant, ZoneOffset.UTC),
    )

    /**
     * One deposit on one contract — enough to say the owner has something to lose.
     *
     * A deposit, not a vehicle: the reminder's whole claim is that money is at stake, and a fleet
     * the owner could re-add in a minute is not what the epic exists to protect.
     *
     * Suspending rather than blocking, and called through `runTest` so Room's own dispatcher
     * runs it off the main thread — `assertNotMainThread` is a hard failure, not a warning, and
     * seeding from the test thread is how six of these eight tests failed the first time.
     */
    private suspend fun seedDeposit() {
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
                isServis = false,
            ),
        )
        database.renterDao().insert(
            RenterEntity(
                id = RENTER_ID,
                name = "Budi Santoso",
                phone = "08123456789",
                bankAccount = null,
                emergencyContact = "08129876543",
            ),
        )
        database.contractDao().insertContract(
            ContractEntity(
                id = CONTRACT_ID,
                renterId = RENTER_ID,
                vehicleId = VEHICLE_ID,
                startDate = LocalDate.of(2026, 10, 1),
                dailyRate = 250_000,
                restDay = null,
                activeVehicleId = VEHICLE_ID,
                activeRenterId = RENTER_ID,
                endDate = null,
                settledThroughDate = null,
            ),
        )
        PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            // **No database handle.** `withTransaction` asserts it is off the main thread, and
            // `runTest`'s body runs on the main one — so passing it makes every seeded write
            // throw `IllegalStateException`. This is the same asymmetry every ViewModel test in
            // this repo accepts; the transaction path is covered by the repository suites that
            // use `runBlocking` on a real background thread.
            database = null,
        ).saveDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p1", date = LocalDate.of(2026, 10, 5), amount = 250_000),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = LocalDate.of(2026, 10, 19),
        )
    }

    /** Builds the ViewModel over [instant] and lets its flows settle. */
    private fun TestScopeScope.viewModelAt(instant: Instant): BerandaViewModel {
        val vm = BerandaViewModel(
            payments = PaymentRepository(
                paymentDao = database.paymentDao(),
                allocationDao = database.allocationDao(),
                contractDao = database.contractDao(),
                exceptionDao = database.serviceExceptionDao(),
                settingDao = database.settingDao(),
                // No handle, for the reason on `seedDeposit`.
                database = null,
            ),
            vehicleRepository = VehicleRepository(database.vehicleDao(), database),
            strings = strings,
            clock = Clock.fixed(instant, ZoneOffset.UTC),
            backups = backupsAt(instant),
            nudgeHour = 9,
        )
        // **`stateIn(WhileSubscribed)` collects nothing until something subscribes**, and a
        // cold `flow { }` like `reminderSource` never starts at all without one. Reading
        // `.value` therefore returns `stateIn`'s initial value — which is exactly what the
        // three positive cases saw: a screen with no reminder because nothing ever ran.
        backgroundScope.launch(dispatcher) { vm.uiState.collect { } }
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    /** Lets a write made after construction reach the collected flows. */
    private fun settle() {
        dispatcher.scheduler.advanceUntilIdle()
    }

    /** A tiny alias so the helper above reads as one call. */
    private typealias TestScopeScope = kotlinx.coroutines.test.TestScope

    // ------------------------------------------------------------- the matrix

    /**
     * `BACKUP_NUDGE` — deposits exist, nothing has ever been exported, and the hour has passed.
     *
     * The AC's first row, and the only one where the banner must appear.
     */
    @Test
    fun `a reminder appears when there are deposits and no export today`() = runTest(dispatcher) {
        seedDeposit()
        val vm = viewModelAt(now)

        val reminder = vm.uiState.value.backupReminder
        assertNotNull("the banner must appear", reminder)
        assertEquals(
            "and it states the fact",
            "Belum ada cadangan hari ini. Data Anda hanya ada di perangkat ini.",
            reminder!!.body,
        )
    }

    /** `BACKUP_FIRST` — never exported, so **no date line**, not a line reading "never". */
    @Test
    fun `a first run reminder carries no last-export line`() = runTest(dispatcher) {
        seedDeposit()
        val vm = viewModelAt(now)

        assertNull(
            "the AC says 'kalau ada' — a first run has no date to name",
            vm.uiState.value.backupReminder?.lastExportLine,
        )
    }

    /** `BACKUP_DONE` — exported today, so the reminder is silent. */
    @Test
    fun `no reminder after today's export`() = runTest(dispatcher) {
        seedDeposit()
        backupsAt(now).build()
        dispatcher.scheduler.advanceUntilIdle()

        val vm = viewModelAt(now)

        assertNull("today's export silences it", vm.uiState.value.backupReminder)
    }

    /** `BACKUP_YESTERDAY` — and it names yesterday, because the AC says it must. */
    @Test
    fun `a reminder after yesterday's export names yesterday`() = runTest(dispatcher) {
        seedDeposit()
        backupsAt(now.minusSeconds(24 * 60 * 60)).build()
        dispatcher.scheduler.advanceUntilIdle()

        val reminder = viewModelAt(now).uiState.value.backupReminder

        assertNotNull("yesterday's export is not today's", reminder)
        assertNotNull("and the line names it", reminder!!.lastExportLine)
        assertEquals(
            "19 Okt is the exported date — the app's own date form, not ISO",
            "Ekspor terakhir: 18 Okt",
            reminder.lastExportLine,
        )
    }

    /**
     * `BACKUP_EMPTY` — a fresh install with no deposits is **not** nagged.
     *
     * A reminder about backing up nothing is noise, and the first-run owner would meet it on the
     * morning they have not yet typed a deposit — which is the exact moment it would be least
     * credible.
     */
    @Test
    fun `no reminder on a fresh install with no deposits`() = runTest(dispatcher) {
        val vm = viewModelAt(now)

        assertNull("nothing to lose, nothing to say", vm.uiState.value.backupReminder)
    }

    /**
     * `BACKUP_UNDATED` — before the nudge hour, the banner stays silent.
     *
     * A reminder at 07:00 for an owner who backs up at close of business is noise, and the hour is
     * therefore a setting rather than a constant.
     */
    @Test
    fun `no reminder before the nudge hour`() = runTest(dispatcher) {
        seedDeposit()
        dispatcher.scheduler.advanceUntilIdle()
        val earlyMorning = Instant.parse("2026-10-19T07:00:00Z")

        assertNull("07:00 is before the 09:00 nudge", viewModelAt(earlyMorning).uiState.value.backupReminder)
    }

    /**
     * The reminder **appears** the moment the owner records their first deposit elsewhere.
     *
     * This is why the source is a `Flow` rather than a count read at construction: the owner adds
     * a deposit on the Setoran screen, comes back, and the banner must be there — without
     * reopening the app.
     */
    @Test
    fun `the reminder appears once the owner records their first deposit`() = runTest(dispatcher) {
        val vm = viewModelAt(now)
        assertNull("nothing to lose yet", vm.uiState.value.backupReminder)

        seedDeposit()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(
            "and now something is at stake",
            vm.uiState.value.backupReminder,
        )
    }

    // ------------------------------------------------------------ the tap's end

    /**
     * The tap reaches the ViewModel.
     *
     * A dialog appearing proves the button was pressed; it does not prove the callback chain is
     * intact, and that chain ends here. `BerandaScreenTest` covers the dialog, this covers the
     * wiring.
     */
    @Test
    fun `tapping the reminder reaches the view model`() = runTest(dispatcher) {
        seedDeposit()
        val vm = viewModelAt(now)

        assertEquals("nothing yet", 0, vm.reminderTaps)
        vm.onBackupReminderTapped()

        assertEquals("and one tap after", 1, vm.reminderTaps)
    }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val RENTER_ID = "renter-1"
        const val CONTRACT_ID = "contract-1"
    }
}