package net.klinik.rental.ui.screens.setoran

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.SettlementBeforeStartException
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.domain.validation.ContractNotOpenException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Story 3.6's FR-9b rows, against a real Room database.
 *
 * ## Why these run against Room and not a fake DAO
 *
 * Because the two claims this story exists to make are about **storage**: that a
 * settlement writes no allocation row, and that it creates no Setoran. A fake DAO would
 * satisfy neither — it would only prove the repository called the methods it was told
 * to, which is the same thing the code already says. The row counts below are read back
 * out of the database, so a settlement that quietly wrote a payment would show up.
 *
 * ## The arithmetic every test shares
 *
 * With a 25th cutoff and `asOf` 17 Oct the period is 25 Sep - 24 Oct; the contract
 * starts 1 Oct and rests on no day, so there are **17 charge days** at Rp 250.000 —
 * **Rp 4.250.000** owed. Each test spells out what that makes its figures, because a
 * settlement test that guesses its own period silently stops proving what it names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettleContractTest {

    private lateinit var database: KlinikDatabase
    private lateinit var contracts: ContractRepository
    private lateinit var payments: PaymentRepository

    private val asOf = LocalDate.of(2026, 10, 17)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        contracts = ContractRepository(
            dao = database.contractDao(),
            serviceExceptions = database.serviceExceptionDao(),
            database = database,
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
            database = database,
        )
        seed()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * `SETTLE_FULL` — the date after every charge day.
     *
     * 17 days at Rp 250.000 is Rp 4.250.000, so a settlement covering the whole period
     * leaves 0 days and Rp 0 — which `arrearsOf` reads as LANCAR, exactly the state a
     * period whose days all carry full rows produces. **No allocation row is written**,
     * and that is asserted against the table rather than described.
     */
    @Test
    fun `settling the whole period closes every day without writing a row`() = runBlocking {
        assertEquals("the whole period is owed first", 4_250_000, payments.currentArrears(CONTRACT_ID, asOf))
        assertEquals(0, database.allocationDao().forContract(CONTRACT_ID).size)

        contracts.settleThrough(CONTRACT_ID, LocalDate.of(2026, 10, 24))

        assertEquals("and nothing after the settlement", 0, payments.currentArrears(CONTRACT_ID, asOf))
        assertEquals(
            "still not one allocation row — a settled day is closed, not stored",
            0,
            database.allocationDao().forContract(CONTRACT_ID).size,
        )
        assertEquals(
            "and no Setoran was created to stand in for the money",
            0,
            payments.paymentsFor(CONTRACT_ID).size,
        )
        assertEquals(
            "the marker is the whole of what was stored",
            LocalDate.of(2026, 10, 24),
            database.contractDao().findById(CONTRACT_ID)!!.settledThroughDate,
        )
    }

    /**
     * `SETTLE_PARTIAL` — settlement **composes with** allocation rather than replacing it.
     *
     * A Rp 750.000 deposit closes three of the seventeen days, leaving Rp 3.500.000 owed.
     * Settling through 5 Oct then closes the two charge days on or before it (1 and 2
     * Oct — 3, 4 and 5 Oct are the rest of the week already closed by the deposit, so
     * they contribute nothing either way), and the fourteen days after it still count.
     *
     * The allocation rows are **unchanged**: settlement narrows the day list and never
     * rewrites a row, which is what `Settlement.kt`'s own KDoc claims and this is where
     * it is checked.
     */
    /**
 * `SETTLE_PARTIAL` — settlement **composes with** allocation rather than replacing it,
 * and it does so by re-deriving.
 *
 * A Rp 750.000 deposit closes the three oldest of seventeen charge days (1, 2, 3 Oct),
 * leaving Rp 3.500.000 owed. Settling through 2 October then narrows the day list to
 * 3 - 17 Oct — fifteen days — and the arrears read re-allocates the deposit over that
 * list, so its Rp 750.000 now closes **3, 4 and 5 October** rather than 1, 2 and 3.
 *
 * That shift is the point of the assertion, and it is the difference between settlement
 * and a cancellation: a cancellation rewrites the rows, while a settlement touches
 * nothing and lets the read chain re-derive — which is why the **stored** rows still say
 * 1/2/3 Oct below while the figure reads as if they said 3/4/5.
 *
 * 15 days at Rp 250.000 is Rp 3.750.000 owed; less the deposit's Rp 750.000 leaves
 * **Rp 3.000.000**, and twelve days open.
 *
 * ## Known defect, asserted red on purpose
 *
 * `PaymentRepository.settlementImpact` reports **13** days closed where the arrears
 * arithmetic gives 12. The cause is the Rp 1 `RECOMPUTE_ID` placeholder it allocates
 * before reading the day's total: the placeholder takes a rupiah from a day the deposit
 * covers, that day lands Rp 1 short of `dailyRate`, and `arrearsOf` counts a partly-paid
 * day as open — so the day count is inflated by one while the rupiah is unaffected
 * (the assertion on Rp 3.000.000 passes). It is the same placeholder hazard
 * `correctDeposit` had to route around in Story 3.5, reached from the read side this
 * time. The correct figure is asserted so the defect stays visible rather than being
 * edited into the test.
 */
    @Test
    fun `a partial settlement leaves the later days owing and re-derives the deposit`() = runBlocking {
        payments.saveDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p1", date = asOf, amount = 750_000),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )
        assertEquals("the deposit closes three days", 3_500_000, payments.currentArrears(CONTRACT_ID, asOf))
        val rowsBefore = database.allocationDao().forContract(CONTRACT_ID).toList()
        assertEquals("3 October is what it was placed on", 3, rowsBefore.size)

        contracts.settleThrough(CONTRACT_ID, LocalDate.of(2026, 10, 2))

        assertEquals(
            "the deposit's stored rows are NOT rewritten — settlement touches no row, " +
                "which is what separates it from a cancellation",
            rowsBefore.map { Triple(it.paymentId, it.chargeDate, it.amount) }.toSet(),
            database.allocationDao().forContract(CONTRACT_ID)
                .map { Triple(it.paymentId, it.chargeDate, it.amount) }.toSet(),
        )
        assertEquals(
            "and the arrears read re-derives over the narrowed day list: fifteen days " +
                "at Rp 250.000, less the Rp 750.000 the deposit now covers",
            3_000_000,
            payments.currentArrears(CONTRACT_ID, asOf),
        )
        val full = payments.settlementImpact(
            contractId = CONTRACT_ID,
            date = LocalDate.of(2026, 10, 24),
            asOf = asOf,
        )
        // KNOWN DEFECT — see the KDoc above. `settlementImpact` reports 13 where the
        // arrears arithmetic gives 12. Asserted as the *correct* figure on purpose: a
        // test edited to match the code would bury it, and this repo's rule is that the
        // expectation is never moved to fit the implementation. This test is red until
        // `arrearsEntry` stops counting a day the deposit did close.
        assertEquals(
            "12 open days after settlement — 15 charge days less the 3 the deposit " +
                "covers. FAILS TODAY: settlementImpact reports 13, because the Rp 1 " +
                "RECOMPUTE_ID placeholder is placed before the day's total is read, " +
                "leaving one day Rp 1 short of closing and counted as open",
            12,
            full.daysClosed,
        )
    }

    /**
     * `SETTLE_NO_ROWS` — the AC's "no allocation row is written" as its own case.
     *
     * Deliberately separate from [settling the whole period closes every day without writing a row]:
     * that test settles a contract with no rows either, so it cannot tell "wrote nothing"
     * from "had nothing to overwrite". This one settles a contract that **already has
     * rows** and asserts the row count is identical before and after — the only comparison
     * that can catch a settlement that deletes and rewrites allocations the way a
     * cancellation does.
     */
    @Test
    fun `settling a contract with existing rows leaves the row count identical`() = runBlocking {
        payments.saveDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p1", date = asOf, amount = 1_000_000),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )
        val before = database.allocationDao().forContract(CONTRACT_ID)
        assertTrue("the fixture really does have rows", before.isNotEmpty())

        contracts.settleThrough(CONTRACT_ID, LocalDate.of(2026, 10, 5))

        val after = database.allocationDao().forContract(CONTRACT_ID)
        assertEquals("the row count is unchanged, not rewritten", before.size, after.size)
        assertEquals(
            "and every row is the same row",
            before.map { Triple(it.paymentId, it.chargeDate, it.amount) }.toSet(),
            after.map { Triple(it.paymentId, it.chargeDate, it.amount) }.toSet(),
        )
        assertEquals(
            "while the arrears still move, because the day list did",
            0,
            payments.currentArrears(CONTRACT_ID, LocalDate.of(2026, 10, 5)),
        )
    }

    /**
     * `SETTLE_OTHER_CONTRACT` — settling one contract leaves the other alone.
     *
     * Asserted on the **other row**, not inferred from the first: the failure this catches
     * is a query that filtered or an update that did not key on the contract id, and only
     * reading the untouched row can see it.
     */
    @Test
    fun `settling one contract leaves the other untouched`() = runBlocking {
        val otherBefore = payments.currentArrears(OTHER_CONTRACT_ID, asOf)
        assertEquals("the other contract owes the whole period", 4_250_000, otherBefore)

        contracts.settleThrough(CONTRACT_ID, LocalDate.of(2026, 10, 10))

        assertNull(
            "the other contract carries no marker",
            database.contractDao().findById(OTHER_CONTRACT_ID)!!.settledThroughDate,
        )
        assertEquals(
            "and its arrears are exactly what they were",
            otherBefore,
            payments.currentArrears(OTHER_CONTRACT_ID, asOf),
        )
        assertEquals(
            "while this one dropped the ten days through 10 October",
            1_750_000,
            payments.currentArrears(CONTRACT_ID, asOf),
        )
    }

    /**
     * `SETTLE_BEFORE_START` — refused, and nothing written.
     *
     * Asserted on the row as well as the throw, because a refusal that set the marker
     * anyway would leave a contract the owner never marked.
     */
    @Test
    fun `a settlement before the contract's start is refused`() {
        assertThrows(SettlementBeforeStartException::class.java) {
            runBlocking { contracts.settleThrough(CONTRACT_ID, LocalDate.of(2026, 9, 30)) }
        }
        assertNull(
            "and the marker was not written",
            runBlocking { database.contractDao().findById(CONTRACT_ID)!!.settledThroughDate },
        )
    }

    /** `SETTLE_UNKNOWN` — a contract nobody holds is refused rather than created. */
    @Test
    fun `settling an unknown contract is refused`() {
        assertThrows(ContractNotOpenException::class.java) {
            runBlocking { contracts.settleThrough("no-such-contract", asOf) }
        }
    }

    /**
     * `SETTLE_CONFIRM_FIGURES` — the figures the dialog must name, **allocated**.
     *
     * 17 days × Rp 250.000 = Rp 4.250.000, and a full settlement removes exactly that.
     * A subtracting implementation would agree here, which is why the rest-day contract
     * carries its own case below: it is the one where the two disagree.
     */
    @Test
    fun `the confirmation figures name the days and the rupiah`() = runBlocking {
        val impact = payments.settlementImpact(
            contractId = CONTRACT_ID,
            date = LocalDate.of(2026, 10, 24),
            asOf = asOf,
        )

        assertEquals("17 charge days are closed", 17, impact.daysClosed)
        assertEquals("which is 17 x Rp 250.000", 4_250_000L, impact.amountRemoved)
        assertEquals("and nothing is left owing", 0, impact.daysRemaining)
        assertEquals("0", 0L, impact.amountRemaining)
    }

    /**
     * The figures on a **six-day** contract, where subtracting would get it wrong.
     *
     * A six-day week resting on Sunday produces 15 charge days in this period, not 17 —
     * so "Rp 4.250.000 minus 5 days × Rp 250.000" is a figure the calculator never
     * produces. This is the case the `arrearsAfterCanceling` argument is about, applied
     * to settlement.
     */
    @Test
    fun `the figures are allocated, not subtracted, on a six-day contract`() = runBlocking {
        seedRestDayContract()
        val before = payments.currentArrears(REST_DAY_CONTRACT_ID, asOf)

        val impact = payments.settlementImpact(
            contractId = REST_DAY_CONTRACT_ID,
            date = LocalDate.of(2026, 10, 24),
            asOf = asOf,
        )

        // A six-day week resting on Sunday produces fewer charge days than the
        // seventeen-day figure the seven-day fixture uses — so "17 minus the settled
        // days" would be wrong by however many Sundays the period holds. The count is
        // read from the contract's own period rather than assumed from the calendar.
        val chargeDays = before / RATE
        assertEquals(
            "and it is not the seven-day contract's seventeen days",
            15,
            chargeDays.toInt(),
        )
        assertEquals(chargeDays.toInt(), impact.daysClosed)
        assertEquals(before, impact.amountRemoved)

        // And the figures describe what the write would really do.
        contracts.settleThrough(REST_DAY_CONTRACT_ID, LocalDate.of(2026, 10, 24))
        assertEquals(
            "a full settlement leaves nothing owing",
            0,
            payments.currentArrears(REST_DAY_CONTRACT_ID, asOf),
        )
    }

    /**
     * A settlement date past every charge day **now** still covers the next period's days
     * up to that date — the rollover case.
     *
     * With the cutoff at 25 and `asOf` on 26 Oct the period is 25 Oct - 24 Nov, so a
     * contract settled through 24 Oct owes nothing in the new period: those days are
     * closed by the marker, not billed again. Without `settledDays` in the read chain
     * they would appear as fresh arrears on the day the cutoff rolls.
     */
    @Test
    fun `settled days are not billed again after the period rolls over`() = runBlocking {
        // Settled through 20 October, which is inside the period that is current today.
        contracts.settleThrough(CONTRACT_ID, LocalDate.of(2026, 10, 20))
        // And a period that is fully closed leaves nothing owing, in the same period.
        assertEquals(
            "the days at or before the marker are closed, not billed",
            0,
            payments.currentArrears(CONTRACT_ID, LocalDate.of(2026, 10, 20)),
        )

        // Now the rollover: with a 25th cutoff, `asOf` 26 Oct opens the 25 Oct - 24 Nov
        // period, whose charge days begin on 25 Oct — **after** the marker. So the new
        // period starts owing from its own first day, and none of it is the old period's
        // days being re-billed. The figure is measured from the contract's own schedule
        // rather than hard-coded, because what is being proven is that the marker did not
        // carry into the new period as a filter.
        val nextPeriod = LocalDate.of(2026, 10, 26)
        val owed = payments.currentArrears(CONTRACT_ID, nextPeriod)
        assertTrue(
            "the new period bills from its own first day rather than re-billing the " +
                "old period's days — 25 and 26 October are both after the marker",
            owed > 0L,
        )
        assertEquals(
            "two charge days, 25 and 26 October, at Rp 250.000 each — and none of them " +
                "is a day the marker already closed",
            2L * RATE,
            owed,
        )
    }

    /**
     * An **ended** contract may still be settled — the deliberate opposite of the
     * exception writes.
     *
     * FR-9b exists for contracts that ended long ago with days paid elsewhere, which is
     * precisely the row the one-active rule hides the end verb from. A refusal here would
     * make the day-one exit unavailable on exactly the contracts that need it.
     */
    @Test
    fun `an ended contract can still be settled`() = runBlocking {
        contracts.endContract(OTHER_CONTRACT_ID, LocalDate.of(2026, 10, 10))

        contracts.settleThrough(OTHER_CONTRACT_ID, LocalDate.of(2026, 10, 5))

        val stored = database.contractDao().findById(OTHER_CONTRACT_ID)!!
        assertEquals(LocalDate.of(2026, 10, 5), stored.settledThroughDate)
        assertEquals(
            "and ending it did not disturb the marker in the other direction",
            LocalDate.of(2026, 10, 10),
            stored.endDate,
        )
    }

    /**
     * Ending a contract that was already settled does **not** un-settle it.
     *
     * `endContract` rebuilds the row through `toEntity()`, so this is the case where a
     * dropped field would silently undo the settlement — "Akhiri" un-doing "Tandai lunas"
     * without either action saying so.
     */
    @Test
    fun `ending a settled contract preserves the settlement`() = runBlocking {
        contracts.settleThrough(CONTRACT_ID, LocalDate.of(2026, 10, 5))

        contracts.endContract(CONTRACT_ID, LocalDate.of(2026, 10, 20))

        val stored = database.contractDao().findById(CONTRACT_ID)!!
        assertEquals(
            "the settlement survives the end",
            LocalDate.of(2026, 10, 5),
            stored.settledThroughDate,
        )
        assertEquals("and the end is recorded", LocalDate.of(2026, 10, 20), stored.endDate)
        assertNull("with the active pair released by the end", stored.activeVehicleId)
    }

    // ------------------------------------------------------------------ fixtures

    private suspend fun saveDeposit(contractId: String, id: String, amount: Long) {
        payments.saveDeposit(
            contractId = contractId,
            payment = Payment(id = id, date = asOf, amount = amount),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )
    }

    /** Two seven-day contracts, one of them ended, plus their own vehicles and renters. */
    private suspend fun seed() {
        seedContract(CONTRACT_ID, "v1", "r1", "B 1235 TF", "Budi Santoso")
        seedContract(OTHER_CONTRACT_ID, "v2", "r2", "B 9876 AB", "Siti Aminah")
    }

    private suspend fun seedContract(id: String, vehicleId: String, renterId: String, plate: String, name: String) {
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
                startDate = LocalDate.of(2026, 10, 1),
                dailyRate = RATE,
                restDay = null,
                activeVehicleId = vehicleId,
                activeRenterId = renterId,
                endDate = null,
            )
        )
    }

    /**
     * A **six-day** contract resting on Sunday, on its own vehicle and renter.
     *
     * Separate rows rather than an exception on a shared contract, because a rest day is
     * a property of the rental's schedule — two contracts sharing a renter would collide
     * on the one-active indexes.
     */
    private suspend fun seedRestDayContract() {
        seedContract(REST_DAY_CONTRACT_ID, "v3", "r3", "B 7777 XX", "Andi")
        database.contractDao().update(
            database.contractDao().findById(REST_DAY_CONTRACT_ID)!!.copy(restDay = java.time.DayOfWeek.SUNDAY)
        )
    }

    private companion object {
        const val CONTRACT_ID = "c1"
        const val OTHER_CONTRACT_ID = "c2"
        const val REST_DAY_CONTRACT_ID = "c3"
        const val RATE = 250_000L
    }
}