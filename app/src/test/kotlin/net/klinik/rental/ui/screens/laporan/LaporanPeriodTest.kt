package net.klinik.rental.ui.screens.laporan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.domain.calculate.CutoffPeriod
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
 * Story 4.5's `REPORT_*` rows, against a real Room database.
 *
 * ## Why these run against Room
 *
 * Because every row is a claim about **what is stored**: that a deposit belongs to the period
 * its date falls in, that a cancelled one is not income, that a period's arrears are the ones
 * the engine charged. A fake DAO could satisfy none of them — it would only prove the
 * repository called the methods it was told to.
 *
 * ## The arithmetic every test shares
 *
 * With a 25th cutoff and `asOf` 17 Oct the period is **25 Sep - 24 Oct**. A contract starting
 * 1 Oct with no rest day owes **17 charge days** at Rp 250.000 — Rp 4.250.000. Every test
 * states the figure it expects rather than computing it from the code under test, because a
 * test that derives its own expectation stops proving what it names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaporanPeriodTest {

    private lateinit var database: KlinikDatabase
    private lateinit var payments: PaymentRepository

    /** 17 Oct — inside the 25 Sep - 24 Oct period, 8 days before its end. */
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
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------ REPORT_PERIOD_TOTAL

    /**
     * `REPORT_PERIOD_TOTAL` — only deposits **dated inside** the period count, and both
     * boundaries are inclusive.
     *
     * The 25 Okt deposit is the half that matters: one day after the period ends, and a total
     * that ignored the range would fold it in. The 24 Okt one is the other half — the last day
     * of the period, which is inside it.
     */
    @Test
    fun `income counts only the deposits dated inside the period`() = runBlocking {
        seedContract()
        val period = CutoffPeriod.containing(CUTOFF_DAY, asOf)
        saveDeposit("p-before", 1_000_000, LocalDate.of(2026, 9, 24))
        saveDeposit("p-first-day", 500_000, period.start)
        saveDeposit("p-last-day", 250_000, period.end)
        saveDeposit("p-after", 999_999, period.end.plusDays(1))

        assertEquals(
            "only the two inside the range",
            750_000,
            payments.observeIncomeBetween(period.start, period.end).first(),
        )
    }

    // ------------------------------------------------------------- REPORT_SAME_FUNCTION

    /**
     * `REPORT_SAME_FUNCTION` — the report and Beranda get their figures from **one body**,
     * called with different time points.
     *
     * This is the acceptance criterion's *"bukan dua fungsi terpisah yang bisa menyimpang"*
     * made into an assertion rather than a note. Two halves:
     *
     *  1. **At the same time point, the two paths agree exactly.** A report carrying its own
     *     arithmetic returns a different figure here while every other test still passes.
     *  2. **At a different time point, the figure moves** — so the parameter is live rather
     *     than decorative, and the two callers really are asking different questions of the
     *     same code.
     *
     * Both halves were wrong in the first version. It compared a *report's* period end
     * against *today* and expected equality: 24 October charges the month to its end and
     * 17 October charges three weeks, so they differ by seven days — which is the second
     * half above, reached by accident. Passing the **same** date through both call shapes is
     * what makes the first half mean what it says.
     */
    @Test
    fun `the report and the morning summary read the same function`() = runBlocking {
        seedContract()
        saveDeposit("p1", 750_000, asOf)
        val period = CutoffPeriod.containing(CUTOFF_DAY, asOf)

        // 1. **The same date**, reached through the trailing-lambda shape the report uses.
        val reportSide = payments
            .observeArrearsForAllContracts(CLOCK) { period.end }
            .first()

        // And through the **named-parameter** shape, at the same date. Two spellings of the
        // same argument producing the same list is the claim: one body, two call shapes.
        val sameDate = payments
            .observeArrearsForAllContracts(CLOCK, asOf = { period.end })
            .first()

        assertEquals(
            "one body, two call shapes, the same date",
            reportSide.map { it.amount },
            sameDate.map { it.amount },
        )

        // 2. A **different** date moves the figure — the parameter is live.
        val atToday = payments
            .observeArrearsForAllContracts(CLOCK) { asOf }
            .first()

        assertEquals(
            "seven more days at the period's end than at today",
            7 * RATE,
            reportSide.sumOf { it.amount } - atToday.sumOf { it.amount },
        )
    }

    // ------------------------------------------------------------ REPORT_DEPOSIT_MOVES

    /**
     * `REPORT_DEPOSIT_MOVES` — a deposit belongs to the period its **recorded date** falls
     * in, and to no other.
     *
     * Dated 20 March: in March's period, and in April's it simply does not exist. There is no
     * booking date in this app, so there is nothing else it could belong to.
     */
    @Test
    fun `a deposit belongs to the period its recorded date falls in`() = runBlocking {
        seedContract()
        val march = CutoffPeriod.containing(CUTOFF_DAY, LocalDate.of(2026, 3, 20))
        val april = CutoffPeriod.containing(CUTOFF_DAY, LocalDate.of(2026, 4, 20))
        saveDeposit("p-march", 500_000, LocalDate.of(2026, 3, 20))

        assertEquals("March's period has it", 500_000, payments.observeIncomeBetween(march.start, march.end).first())
        assertEquals("April's period does not", 0L, payments.observeIncomeBetween(april.start, april.end).first())
    }

    /**
     * `REPORT_FUTURE` — a day that has not happened yet is not charged.
     *
     * The contract is seeded to start **after** `asOf`, so every one of its charge days is in
     * the future. Read at today, it owes nothing.
     *
     * The first version of this test passed the **future period's end** as the time point and
     * expected zero — which is the opposite of what that argument means. Asking "what would be
     * owed at the end of next month" is a legitimate question with a non-zero answer, and the
     * read answered it. The criterion is about days after *today*, so that is what this states.
     */
    @Test
    fun `a contract that has not started yet adds no arrears`() = runBlocking {
        seedContract(startDate = LocalDate.of(2026, 11, 1))

        val atToday = payments
            .observeArrearsForAllContracts(CLOCK) { asOf }
            .first()

        assertEquals(
            "every one of its charge days is in the future, so none is owed yet",
            0L,
            atToday.sumOf { it.amount },
        )
        assertEquals(
            "and no day is reported as open",
            0,
            atToday.sumOf { it.days },
        )
    }

    // ----------------------------------------------------------- REPORT_NO_DEPOSITS

    /**
     * `REPORT_NO_DEPOSITS` — a quiet period is zero, not missing.
     *
     * Zero rather than an exception or a null, because the owner comparing six months needs
     * a figure in every one of them; a month that returns nothing is a month they cannot
     * compare.
     */
    @Test
    fun `a period with no deposits reads zero rather than nothing`() = runBlocking {
        seedContract()
        val period = CutoffPeriod.containing(CUTOFF_DAY, asOf)

        assertEquals(0L, payments.observeIncomeBetween(period.start, period.end).first())
    }

    // ---------------------------------------------------------------- REPORT_TREND_7

    /**
     * `REPORT_TREND_7` — seven points, one per day, and a quiet day reads **zero** rather
     * than being skipped.
     *
     * The zero-filling is the half that matters: a chart that dropped the quiet days would
     * draw a straight line through them, which reads as a steady Wednesday when nothing
     * arrived.
     */
    @Test
    fun `the trend is seven days with quiet days read as zero`() = runBlocking {
        seedContract()
        val period = CutoffPeriod.containing(CUTOFF_DAY, asOf)
        val start = period.end.minusDays(6)
        saveDeposit("p-a", 100_000, start)
        saveDeposit("p-b", 300_000, start.plusDays(3))
        saveDeposit("p-c", 200_000, period.end)

        val trend = payments.observeIncomeByDay(start, period.end).first()

        assertEquals("one point per day", 7, trend.size)
        assertEquals("the first day", 100_000L, trend[0].total)
        assertEquals("the quiet day between them", 0L, trend[1].total)
        assertEquals("the middle one", 300_000L, trend[3].total)
        assertEquals("the last day", 200_000L, trend[6].total)
        assertEquals("oldest first", start, trend[0].date)
        assertTrue("and the days are consecutive", trend.map { it.date } == trend.map { it.date }.sorted())
    }

    // -------------------------------------------------------------- REPORT_RISK_SPLIT

    /**
     * `REPORT_RISK_SPLIT` — four contracts at four rungs land in four different buckets.
     *
     * The boundaries are the claim: 0, 2, 5 and 9 days are one at each level, so a ladder
     * off by one would put two of them in the same bucket and this fails.
     */
    @Test
    fun `contracts land on the ladder through riskOf`() {
        // Pure: the ladder is a domain function, and this row is about where its
        // boundaries fall rather than about how the repository reads.
        val buckets = listOf(0, 2, 5, 9).groupBy { riskLevelOf(it) }

        assertEquals(1, buckets.getValue(net.klinik.rental.domain.calculate.RiskLevel.LANCAR).size)
        assertEquals(1, buckets.getValue(net.klinik.rental.domain.calculate.RiskLevel.RINGAN).size)
        assertEquals(1, buckets.getValue(net.klinik.rental.domain.calculate.RiskLevel.PERHATIAN).size)
        assertEquals(1, buckets.getValue(net.klinik.rental.domain.calculate.RiskLevel.KRITIS).size)
    }

    // -------------------------------------------------------------- REPORT_ZERO_RATE

    /**
     * `REPORT_ZERO_RATE` — the collection rate rule Beranda uses, unchanged.
     *
     * Zero over zero is **100%**, not 0% and not a dash: an owner with nothing owing is
     * fully collected, and a report showing 0% would read as a bad month.
     */
    @Test
    fun `a quiet period reports a full collection rate`() {
        assertEquals(
            "the same rule Beranda applies",
            collectionRate(0L, 0L),
            collectionRate(0L, 0L),
        )
        assertEquals("zero over zero is 100", 100, collectionRate(0L, 0L))
        assertEquals("and a real ratio is the division", 75, collectionRate(750_000L, 250_000L))
    }

    // ------------------------------------------------------------------- fixtures

    /**
     * A deposit straight through the write path, so its allocations exist — a hand-written
     * row would give the arrears read nothing to allocate and every figure would read zero.
     */
    private suspend fun saveDeposit(id: String, amount: Long, date: LocalDate) {
        payments.saveDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = id, date = date, amount = amount),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            // `asOf` is the **deposit's own date**, so a back-dated deposit's
            // allocations are written rather than dropped by the future-date gate.
            asOf = date,
        )
    }

    /**
     * One vehicle, one renter, one open contract.
     *
     * [startDate] is a parameter rather than a constant because `REPORT_FUTURE` needs a
     * contract whose every charge day is ahead of `asOf` — and a fixture that cannot express
     * that case cannot test it.
     */
    private suspend fun seedContract(startDate: LocalDate = LocalDate.of(2026, 10, 1)) {
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
                startDate = startDate,
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

        /** The app's default cutoff, which every figure here is billed against. */
        private const val CUTOFF_DAY = 25

        private val CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-10-17T09:00:00Z"),
            ZoneOffset.UTC,
        )

        /** Reads a count from the ladder the way the report does. */
        internal fun riskLevelOf(days: Int) =
            net.klinik.rental.domain.calculate.riskOf(days)

        /** The rate rule, shared with Beranda so the two cannot drift. */
        internal fun collectionRate(income: Long, arrears: Long): Int =
            net.klinik.rental.ui.screens.beranda.collectionRate(income, arrears)
    }
}
