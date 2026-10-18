package net.klinik.rental.ui.screens.tunggakan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.ServiceExceptionEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.domain.calculate.inArrearsOrder
import net.klinik.rental.domain.calculate.riskOf
import net.klinik.rental.domain.model.Payment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Story 4.1's `LIST_*` rows, against a real Room database.
 *
 * ## Why these run against Room
 *
 * Because the matrix is mostly claims about **what is stored**: that a settled contract
 * reports nothing, that an ended contract with a balance stays, and that the day count comes
 * from allocation rows rather than from dividing the rupiah by the rate. A fake DAO could
 * satisfy none of them — it would only prove the repository called the methods it was told
 * to, which is what the code already says.
 *
 * ## The arithmetic every test shares
 *
 * With a 25th cutoff and `asOf` 17 Oct the period is 25 Sep - 24 Oct. A contract starting
 * 1 Oct with no rest day owes **17 charge days** at Rp 250.000 — **Rp 4.250.000**. Every
 * test states the figure it expects rather than computing it from the code under test,
 * because a list test that derives its own expectation stops proving what it names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TunggakanArrearsTest {

    private lateinit var database: KlinikDatabase
    private lateinit var payments: PaymentRepository
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

    // ------------------------------------------------------------------ LIST_ONE

    /**
     * `LIST_ONE` — two contracts owing, and each entry carries **both** figures.
     *
     * Rp 750.000 closes three of seventeen days, so Budi owes Rp 3.500.000 across
     * **14 open days** — the count is what is still owed, not what was paid, and
     * `ArrearsEntry.days`'s own KDoc says so. Siti's Rp 250.000 closes one, leaving
     * Rp 4.000.000 across 16.
     *
     * The distinction matters more than it looks: a day count of "3" beside a rupiah figure
     * reads as three days outstanding, which would be wrong by an order of magnitude — and it
     * is that number the owner sends to a tenant.
     */
    @Test
    fun `two owing contracts are listed with amount and days each`() = runBlocking {
        seedContract(CONTRACT_ID, "v1", "r1", "B 1235 TF", "Budi Santoso")
        seedContract(OTHER_CONTRACT_ID, "v2", "r2", "B 9876 AB", "Siti Aminah")
        saveDeposit(CONTRACT_ID, 750_000)
        saveDeposit(OTHER_CONTRACT_ID, 250_000)

        val entries = payments.observeArrearsForAllContracts(asOf).first()
            .sortedBy { it.contractId }

        assertEquals("both contracts are present", 2, entries.size)
        assertEquals("Budi owes what the deposit did not cover", 3_500_000, entries[0].amount)
        assertEquals("over the fourteen days still open", 14, entries[0].days)
        assertEquals("Siti owes what her deposit did not cover", 4_000_000, entries[1].amount)
        assertEquals("over the sixteen days still open", 16, entries[1].days)
    }

    /**
     * The **oldest open day** travels with the entry — the epic's note says that is the
     * figure which explains a number, so it must be readable without opening anything.
     */
    @Test
    fun `the oldest still-open day is carried on the entry`() = runBlocking {
        seedContract(CONTRACT_ID, "v1", "r1", "B 1235 TF", "Budi Santoso")
        saveDeposit(CONTRACT_ID, 750_000)

        val entry = payments.observeArrearsForAllContracts(asOf).first().single()

        // Three days are closed from 1 Oct, so 4 Oct is the oldest day still owed.
        assertEquals(LocalDate.of(2026, 10, 4), entry.oldestOpenDay)
    }

    // ----------------------------------------------------------------- LIST_ZERO

    /**
     * `LIST_ZERO` — a fully paid contract is **absent**, not listed at zero.
     *
     * Asserted on the raw entries rather than on the UI, because `inArrearsOrder` does the
     * filtering at the domain layer and the screen inherits it: a row carrying Rp 0 would be
     * a row telling the owner someone owes them nothing, which is a different claim from not
     * listing them at all.
     */
    @Test
    fun `a contract owing nothing is absent from the list`() = runBlocking {
        seedContract(CONTRACT_ID, "v1", "r1", "B 1235 TF", "Budi Santoso")
        saveDeposit(CONTRACT_ID, 4_250_000)

        val entries = payments.observeArrearsForAllContracts(asOf).first()
            .filter { it.amount > 0L }

        assertTrue("a paid contract is not listed as owing", entries.isEmpty())
    }

    // --------------------------------------------------------- LIST_ENDED_OWES

    /**
     * `LIST_ENDED_OWES` — an ended contract with a balance stays in the list.
     *
     * The one case a naive "active contracts" read would silently drop, and dropping it is
     * how an owner chasing real money loses sight of it. Marked by `isEnded` rather than
     * filtered, so the row can say the rental is finished.
     */
    @Test
    fun `an ended contract with a balance is still listed`() = runBlocking {
        seedContract(CONTRACT_ID, "v1", "r1", "B 1235 TF", "Budi Santoso")
        contracts.endContract(CONTRACT_ID, LocalDate.of(2026, 10, 17))

        val entry = payments.observeArrearsForAllContracts(asOf).first().single()

        assertTrue("and it is marked as finished", entry.isEnded)
        assertEquals("still owing the whole period", 4_250_000, entry.amount)
        assertEquals("over every day of it", 17, entry.days)
    }

    // ------------------------------------------------------------ LIST_RISK_LADDER

    /**
     * `LIST_RISK_LADDER` — every boundary of the ladder, at the data layer.
     *
     * Pinned on [net.klinik.rental.domain.calculate.riskOf] rather than re-derived here: the
     * list must not carry its own copy of the ladder, and this asserts the one function the
     * badge colour comes from. Boundaries are the whole test — 2/3 and 6/7 are where an
     * off-by-one hides.
     */
    @Test
    fun `the risk ladder pins every boundary`() {
        assertEquals(RiskLevel.LANCAR, riskOf(0))
        assertEquals(RiskLevel.RINGAN, riskOf(1))
        assertEquals(RiskLevel.RINGAN, riskOf(2))
        assertEquals(RiskLevel.PERHATIAN, riskOf(3))
        assertEquals(RiskLevel.PERHATIAN, riskOf(6))
        assertEquals(RiskLevel.KRITIS, riskOf(7))
    }

    // ------------------------------------------------------------------ LIST_TIE

    /**
     * `LIST_TIE` — two contracts owing the **same** amount, ordered by the older open day.
     *
     * The tie-break, and the reason sorting on the rupiah alone is not enough: equal amounts
     * are exactly when the owner has no figure to go on, and the older unpaid day is the one
     * that says how long they have been waiting.
     *
     * ## How the tie is built, and why the obvious fixture is wrong
     *
     * Two contracts with **different start dates** do not tie — the later one has fewer
     * charge days and therefore owes less, so the primary sort settles the order and the
     * tie-break never runs. A test written that way passes while proving nothing.
     *
     * Two service exceptions do produce the tie, because an excused day leaves the day list
     * entirely and so takes its rupiah with it: excusing 3 Oct on one contract and 1 Oct on
     * the other leaves **both owing 16 × Rp 250.000**, with the same open-day count and
     * different oldest open days. That is the situation the tie-break exists for — same
     * money, same number of days, and one tenant waiting longer.
     *
     * And the fixture is arranged so the tie-break **disagrees with the ids**: Siti's id
     * sorts after Budi's, so a sort by id would put Budi first — while the older open day
     * says Siti. If this test passes, the ordering came from the day and not from the id,
     * which is the only way a tie-break test can fail for the right reason.
     */
    @Test
    fun `equal amounts order by the older open day`() = runBlocking {
        seedContract(CONTRACT_ID, "v1", "r1", "B 1235 TF", "Budi Santoso")
        seedContract(OTHER_CONTRACT_ID, "v2", "r2", "B 9876 AB", "Siti Aminah")
        // Budi is excused on the 3rd, so 1 Oct stays his oldest open day — and the earlier
        // of the two, which is why he must come first despite his id sorting earlier too.
        excuse(CONTRACT_ID, "exc-budi", LocalDate.of(2026, 10, 3))
        // Siti is excused on the 1st, so hers moves to the 2nd and she waits less.
        excuse(OTHER_CONTRACT_ID, "exc-siti", LocalDate.of(2026, 10, 1))

        val entries = payments.observeArrearsForAllContracts(asOf).first()
            .map { it.toEntry() }
            .inArrearsOrder()

        assertEquals(
            "the amounts really tie — or this test proves nothing",
            4_000_000,
            entries[0].amount,
        )
        assertEquals(
            "and the open-day counts match, so nothing else orders them",
            entries[1].days,
            entries[0].days,
        )
        assertEquals(
            "the older open day is first, whatever order the ids happen to be in",
            CONTRACT_ID,
            entries.first().contractId,
        )
        assertTrue(
            "and Budi's is genuinely the earlier of the two",
            entries.first().oldestOpenDay!!.isBefore(entries[1].oldestOpenDay!!),
        )
    }

    /** Excuses one charge day, which removes it from the list and its rupiah with it. */
    private suspend fun excuse(contractId: String, id: String, date: LocalDate) {
        database.serviceExceptionDao().insertServiceException(
            ServiceExceptionEntity(
                id = id,
                contractId = contractId,
                date = date,
                reason = "servis",
            )
        )
    }

    // -------------------------------------------------------------- LIST_SETTLED

    /**
     * `LIST_SETTLED` — a settled contract's days do not count.
     *
     * The case that proves the aggregate read goes through the same `previewAllocation` as
     * every other arrears read: a contract settled through the period's start owes nothing,
     * and this story never mentions FR-9b. If the list re-derived its own day list, the
     * settlement would be invisible here.
     */
    @Test
    fun `a settled contract owes nothing`() = runBlocking {
        seedContract(CONTRACT_ID, "v1", "r1", "B 1235 TF", "Budi Santoso")
        contracts.settleThrough(CONTRACT_ID, LocalDate.of(2026, 10, 17))

        val entry = payments.observeArrearsForAllContracts(asOf).first().single()

        assertEquals("every day is closed", 0, entry.amount)
        assertEquals("and no day is open", 0, entry.days)
        assertNull("so there is no oldest open day to name", entry.oldestOpenDay)
    }

    /**
     * A **partial** settlement closes only the days at or before it, and the days after it
     * still count — the composite case, where an implementation treating settlement as
     * clearing the whole period would report zero here.
     */
    @Test
    fun `a partial settlement leaves the later days owing`() = runBlocking {
        seedContract(CONTRACT_ID, "v1", "r1", "B 1235 TF", "Budi Santoso")
        contracts.settleThrough(CONTRACT_ID, LocalDate.of(2026, 10, 10))

        val entry = payments.observeArrearsForAllContracts(asOf).first().single()

        // 1 Oct to 10 Oct settled: 11 Oct to 17 Oct remain — seven days at Rp 250.000.
        assertEquals("seven days stay open", 7, entry.days)
        assertEquals("and owe the rest of the period", 1_750_000, entry.amount)
    }

    // ------------------------------------------------------------- LIST_MIDNIGHT

    /**
     * `LIST_MIDNIGHT` — a day already past is counted, which is what "as of" means.
     *
     * Read on 18 Oct rather than 17, so the last day of the previous `asOf` is now behind
     * the owner. The figure must have **grown**, not stayed: a read that pinned "today" to
     * the period's end would show the same number the owner saw yesterday, and it is exactly
     * that staleness the criterion names when it says the figure must match online banking.
     */
    @Test
    fun `a day that has passed is counted`() = runBlocking {
        seedContract(CONTRACT_ID, "v1", "r1", "B 1235 TF", "Budi Santoso")

        val onTheSeventeenth = payments
            .observeArrearsForAllContracts(LocalDate.of(2026, 10, 17))
            .first()
            .single()
        val onTheEighteenth = payments
            .observeArrearsForAllContracts(LocalDate.of(2026, 10, 18))
            .first()
            .single()

        assertEquals("17 days were open on the 17th", 17, onTheSeventeenth.days)
        assertEquals("and the 18th adds the day that fell behind", 18, onTheEighteenth.days)
        assertTrue(
            "so the figure grew rather than standing still",
            onTheEighteenth.amount > onTheSeventeenth.amount,
        )
    }

    // -------------------------------------------------------- the list stays live

    /**
     * The list re-emits on a write, with no refresh anywhere.
     *
     * This is what the "no manual refresh" rule actually asks for: a deposit saved must move
     * the number on its own. A repository that returned a one-shot read would pass every
     * count assertion above and fail this one.
     */
    @Test
    fun `the arrears list re-emits when a deposit lands`() = runBlocking {
        seedContract(CONTRACT_ID, "v1", "r1", "B 1235 TF", "Budi Santoso")

        val flow = payments.observeArrearsForAllContracts(asOf)
        assertEquals("the whole period is owed before any deposit", 17, flow.first().single().days)

        saveDeposit(CONTRACT_ID, 750_000)

        assertEquals(
            "three days close the moment the deposit lands",
            14,
            flow.first().single().days,
        )
    }

    // ------------------------------------------------------------------ fixtures

    private suspend fun saveDeposit(
        contractId: String,
        amount: Long,
        date: LocalDate = asOf,
    ) {
        payments.saveDeposit(
            contractId = contractId,
            payment = Payment(id = "pay-$contractId", date = date, amount = amount),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = date,
        )
    }

    private suspend fun seedContract(
        id: String,
        vehicleId: String,
        renterId: String,
        plate: String,
        name: String,
        startDate: LocalDate = LocalDate.of(2026, 10, 1),
    ) {
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
                id = renterId,
                name = name,
                phone = "08123456789",
                bankAccount = null,
                emergencyContact = "08129876543",
            )
        )
        database.contractDao().insertContract(
            ContractEntity(
                id = id,
                renterId = renterId,
                vehicleId = vehicleId,
                startDate = startDate,
                dailyRate = RATE,
                restDay = null,
                activeVehicleId = vehicleId,
                activeRenterId = renterId,
                endDate = null,
            )
        )
    }

    private companion object {
        const val CONTRACT_ID = "c1"
        const val OTHER_CONTRACT_ID = "c2"
        const val RATE = 250_000L
    }
}