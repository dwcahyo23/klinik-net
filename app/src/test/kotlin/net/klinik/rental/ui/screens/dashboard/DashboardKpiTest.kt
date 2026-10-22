package net.klinik.rental.ui.screens.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Payment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Story 4.3's `HOME_*` rows, against a real Room database.
 *
 * ## Why these run against Room
 *
 * Because two of the matrix rows are claims about **what is stored**: that a cancelled
 * deposit is not income, and that an ended contract's arrears still count. A fake DAO could
 * satisfy neither — it would only prove the repository called the methods it was told to.
 *
 * ## The arithmetic every test shares
 *
 * With a 25th cutoff and `asOf` 17 Oct the period is 25 Sep - 24 Oct. A contract starting
 * 1 Oct with no rest day owes **17 charge days** at Rp 250.000 — **Rp 4.250.000**. Every test
 * states the figure it expects rather than computing it from the code under test, because a
 * test that derives its own expectation stops proving what it names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardKpiTest {

    private lateinit var database: KlinikDatabase
    private lateinit var payments: PaymentRepository
    private lateinit var vehicles: VehicleRepository
    private lateinit var contracts: ContractRepository

    private val asOf = LocalDate.of(2026, 10, 17)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            database = database,
        )
        vehicles = VehicleRepository(database.vehicleDao(), database)
        contracts = ContractRepository(
            dao = database.contractDao(),
            serviceExceptions = database.serviceExceptionDao(),
            database = database,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------------ HOME_TODAY

    /**
     * `HOME_TODAY` — only today's confirmed deposits count, and an older one is excluded.
     *
     * The older deposit is the half that matters: a total that summed every deposit ever
     * recorded would look right on the first day of the app and wrong on the second.
     */
    @Test
    fun `only today's confirmed deposits count as income`() = runBlocking {
        seedContract(CONTRACT_ID)
        saveDeposit("p-today", 750_000, date = asOf)
        saveDeposit("p-old", 500_000, date = LocalDate.of(2026, 10, 10))

        assertEquals(
            "today's deposit only",
            750_000,
            payments.observeTodaysIncome(CLOCK).first(),
        )
    }

    /**
     * A **cancelled** deposit is not income.
     *
     * It is kept for its audit trail and it no longer counts against arrears, so counting it
     * here would put a figure on the morning screen that the rest of the app has already
     * agreed is void — and the two would disagree the moment a cancellation landed.
     */
    @Test
    fun `a cancelled deposit is not income`() = runBlocking {
        seedContract(CONTRACT_ID)
        saveDeposit("p1", 750_000, date = asOf)
        payments.cancelDeposit(CONTRACT_ID, "p1", "salah tanggal")

        assertEquals(
            "the cancelled deposit does not count",
            0,
            payments.observeTodaysIncome(CLOCK).first(),
        )
    }

    // -------------------------------------------------------------- HOME_ZERO_RATE

    /**
     * `HOME_ZERO_RATE` — a morning with nothing owed reads **100%**, not 0% and not a dash.
     *
     * The rate answers "how much of what was owed has been paid", and a morning with no
     * arrears has nothing outstanding — which is a full collection. Reading it as 0% would
     * tell the owner their tenants are behind when nobody owes anything.
     */
    @Test
    fun `the rate is 100 percent when there is nothing to collect`() {
        assertEquals(100, collectionRate(income = 0, arrears = 0))
    }

    // ------------------------------------------------------------------- HOME_RATE

    /**
     * `HOME_RATE` — the rate is a whole percentage, with no decimal.
     *
     * Rp 750.000 collected against Rp 250.000 outstanding is 75%.
     */
    @Test
    fun `the rate is a whole percentage of what was owed`() {
        assertEquals(75, collectionRate(income = 750_000, arrears = 250_000))
    }

    /**
     * And a ratio that does not divide evenly is **rounded down**, not up.
     *
     * Rp 100.000 of Rp 300.000 is 33.3%, and the tile shows 33%. Rounding up would report
     * more collected than actually was.
     */
    @Test
    fun `a rate that does not divide evenly rounds down`() {
        assertEquals(33, collectionRate(income = 100_000, arrears = 200_000))
    }

    // ------------------------------------------------------------ HOME_ENDED_OWES

    /**
     * `HOME_ENDED_OWES` — an ended contract's arrears still count in the total.
     *
     * The same population Story 4.1's list draws, so the morning screen and the list cannot
     * disagree about how much is owed.
     */
    @Test
    fun `an ended contract that still owes counts in the arrears total`() = runBlocking {
        seedContract(CONTRACT_ID)
        contracts.endContract(CONTRACT_ID, LocalDate.of(2026, 10, 17), asOf = LocalDate.of(2026, 10, 17))

        val entries = payments.observeArrearsForAllContracts(CLOCK).first()
        assertEquals("the ended contract is still listed", 1, entries.size)
        assertTrue("and it still owes", entries.single().amount > 0)
        assertTrue("and it is marked as ended", entries.single().isEnded)
    }

    // ------------------------------------------------------------ HOME_SERVIS_WINS

    /**
     * `HOME_SERVIS_WINS` — a vehicle in service *and* under an open contract counts once,
     * as Servis.
     *
     * That is Story 2.3's rule, not a re-derivation: `VehicleStatus.of` checks `Servis`
     * first because a car off the road is the fact the owner wants on a morning screen. The
     * open contract still bills either way, so nothing is lost by preferring it here.
     */
    @Test
    fun `a vehicle in service and under contract counts as servis`() = runBlocking {
        seedContract(CONTRACT_ID)
        // The manual service marker on the same vehicle the contract names.
        database.vehicleDao().update(
            database.vehicleDao().findById(VEHICLE_ID)!!.copy(isServis = true),
        )

        val split = splitFleet(
            vehicles.observeVehicles().first(),
            vehicles.observeRentedPlates().first(),
        )
        assertEquals("counted once, as servis", 1, split.service)
        assertEquals("and not as rented", 0, split.rented)
        assertEquals("and not as available", 0, split.available)
    }

    /**
     * And a vehicle with neither marker is **available** — the third bucket, so the three
     * counts add up to the fleet.
     */
    @Test
    fun `a vehicle with no marker and no contract is available`() = runBlocking {
        seedContract(CONTRACT_ID)
        database.vehicleDao().insert(
            VehicleEntity(
                id = "v-free",
                plate = "B 9999 XX",
                brand = "Toyota",
                model = "Avanza",
                year = 2023,
                color = null,
                dailyRate = RATE,
                notes = null,
            ),
        )

        val split = splitFleet(
            vehicles.observeVehicles().first(),
            vehicles.observeRentedPlates().first(),
        )
        assertEquals("one available", 1, split.available)
        assertEquals("one rented", 1, split.rented)
        assertEquals("none in service", 0, split.service)
    }

    // ----------------------------------------------------------------- HOME_UPDATES

    /**
     * `HOME_UPDATES` — the figure moves on a write, with no reload and no navigation.
     *
     * The flow is **the same subscription** throughout: a test that built a second flow
     * would prove nothing about the first one moving.
     */
    @Test
    fun `the income moves on a write with no refresh`() = runBlocking {
        seedContract(CONTRACT_ID)
        val flow = payments.observeTodaysIncome(CLOCK)
        assertEquals("nothing yet", 0, flow.first())

        saveDeposit("p1", 750_000, date = asOf)

        assertEquals(
            "and it moves on its own",
            750_000,
            flow.first(),
        )
    }

    /**
     * The clock is read **per emission**, so an app left open across midnight moves without
     * being re-opened.
     *
     * A query with the date bound in its parameters would freeze the date in the
     * subscription — Room re-runs it with the same bound value when the table changes — so
     * yesterday's income would be shown after midnight. That is the same defect Story 4.1's
     * review found in the arrears read, and the reason the query groups by date.
     */
    @Test
    fun `an app left open across midnight sees the new day`() = runBlocking {
        seedContract(CONTRACT_ID)
        saveDeposit("p-yesterday", 750_000, date = LocalDate.of(2026, 10, 16))
        val clock = MutableClock(LocalDate.of(2026, 10, 16))
        val flow = payments.observeTodaysIncome(clock)
        assertEquals("yesterday's deposit is today's income", 750_000, flow.first())

        clock.today = LocalDate.of(2026, 10, 17)
        assertEquals(
            "and it is not tomorrow's",
            0,
            flow.first(),
        )
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * A clock whose "today" can be moved, for the midnight case.
     *
     * `MutableClock` rather than two fixed clocks: the defect being pinned is a **frozen**
     * read, so the test must hold **one** flow and move the day underneath it.
     */
    private class MutableClock(var today: LocalDate) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.parse("${today}T09:00:00Z")
    }

    private suspend fun saveDeposit(id: String, amount: Long, date: LocalDate) {
        payments.saveDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = id, date = date, amount = amount),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = date,
        )
    }

    private suspend fun seedContract(id: String) {
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
                id = id,
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
    }

    private companion object {
        private const val CONTRACT_ID = "c1"
        private const val RENTER_ID = "r1"
        private const val VEHICLE_ID = "v1"
        private const val RATE = 250_000L

        private val CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-10-17T09:00:00Z"),
            ZoneOffset.UTC,
        )
    }
}
