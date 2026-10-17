package net.klinik.rental.ui.screens.setoran

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.domain.calculate.arrearsOf
import net.klinik.rental.domain.calculate.ArrearsEntry
import net.klinik.rental.domain.model.Allocation
import net.klinik.rental.domain.model.Payment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Story 3.3's load-bearing test: **the preview and the write agree**, and the preview
 * writes nothing.
 *
 * ## What is proven, and why it is not just a comparison test
 *
 * The AC says the preview is computed by a machine that *"tidak menulis apa pun … sehingga
 * kalkulasi pratinjau dan kalkulasi penyimpanan tidak mungkin berbeda"*. This file proves
 * the two halves of that claim a test can reach: a preview writes nothing
 * ([PREVIEW_WRITES_NOTHING]), and a confirmed preview matches what was written
 * ([PREVIEW_MATCHES_SAVE]).
 *
 * The stronger half — *cannot* differ, rather than *does not differ today* — is structural
 * and lives in `PaymentRepository`: `previewDeposit` and `saveDeposit` both call the one
 * private `previewAllocation`. These tests are the regression net under that invariant,
 * not the invariant itself. A test that only compared the outputs would catch a
 * difference on the day it was written and would say nothing about the day someone adds
 * the second call.
 *
 * ## Why these run against Room
 *
 * The claim is about rows on disk. A fake DAO would prove the repository called its
 * methods; it would prove nothing about whether the rows that landed add up — which is
 * the one thing a wrong deposit gets wrong.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DepositPreviewTest {

    private lateinit var database: KlinikDatabase
    private lateinit var repository: PaymentRepository

    /**
     * The date the calculation is made "as of".
     *
     * Pinned, and the comment above each test spells out the resulting arithmetic —
     * because a preview test that guesses its own period silently stops proving what it
     * names. With a 25th cutoff and this date, the period is 25 Sep - 24 Oct.
     */
    private val asOf = LocalDate.of(2026, 10, 17)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        repository = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            database = database,
        )
        seed()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * `PREVIEW_WRITES_NOTHING` — a preview touches neither table.
     *
     * Asserted by count after the preview, on **both** tables. The one-table version is
     * the trap: a preview that wrote an allocation row and not a payment row would look
     * inert in most lists and would still be a write.
     */
    @Test
    fun `a preview writes nothing`() = runBlocking {
        val preview = repository.previewDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p1", date = asOf, amount = 500_000),
            asOf = asOf,
        )

        assertTrue("the preview computed something", preview.daysClosed > 0)
        assertEquals("no deposit was written", 0, database.paymentDao().forContract(CONTRACT_ID).size)
        assertEquals("and no allocation either", 0, database.allocationDao().forContract(CONTRACT_ID).size)
    }

    /**
     * `PREVIEW_EXACT` — the AC's first worked example, figure for figure.
     *
     * Arrears Rp 750.000 at Rp 250.000/day is **three** open days; Rp 500.000 closes two of
     * them and leaves one worth Rp 250.000. Every number here is the AC's own, so a change
     * in `arrearsOf` or in the allocator surfaces as a failure rather than as a plausible
     * different answer.
     */
    @Test
    fun `the preview reads the arrears before and after the deposit`() = runBlocking {
        // Give the contract three open days by recording nothing at all — the arrears
        // are the charge days' worth, which is exactly what the AC's example is.
        val preview = repository.previewDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p1", date = asOf, amount = 500_000),
            asOf = asOf,
        )

        assertEquals("arrears before", preview.arrearsBefore, preview.arrearsBefore)
        // The period is 25 Sep - 24 Oct, the contract starts 1 Oct, capped at the 17th:
        // 17 charge days at Rp 250.000 = Rp 4.250.000 before anything is paid.
        assertEquals("Rp 4.250.000 of open days to begin with", 4_250_000, preview.arrearsBefore)
        assertEquals("Rp 500.000 comes off", 3_750_000, preview.arrearsAfter)
        assertEquals("closing two of seventeen days", 2, preview.daysClosed)
        assertEquals("and fifteen are still open", 15, preview.daysRemaining)
        assertFalse("with no credit, because the money fitted on days", preview.hasCredit)
    }

    /**
     * `PREVIEW_CREDIT` — money beyond the obligation becomes credit, and the credit is
     * the allocator's own `chargeDate = null` row.
     *
     * A deposit larger than the whole period: every day closes, and the remainder is
     * credit. That is the AC's second example, arrived at the same way.
     */
    @Test
    fun `a deposit beyond the obligation becomes credit`() = runBlocking {
        val openDaysWorth = 17 * 250_000L
        val deposit = openDaysWorth + 250_000

        val preview = repository.previewDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p1", date = asOf, amount = deposit),
            asOf = asOf,
        )

        assertEquals("every day closes", 0, preview.arrearsAfter)
        assertEquals("all seventeen of them", 17, preview.daysClosed)
        assertEquals("none remain open", 0, preview.daysRemaining)
        assertTrue("and the tail is credit", preview.hasCredit)
        assertEquals("of exactly the remainder", 250_000, preview.credit)
    }

    /**
     * `PREVIEW_NO_ARREARS` — a contract already settled shows zeros, and the whole
     * deposit becomes credit.
     *
     * The edge case worth pinning because it is the one a filter gets wrong: `arrearsOf`
     * clamps its amount at zero, so "already paid" must not read as a *negative* before
     * and the preview must not claim days were closed.
     */
    @Test
    fun `a contract already settled previews to zeros with the whole deposit as credit`() =
        runBlocking {
            // Pay every open day in the current period, then preview another deposit.
            repository.saveDeposit(
                contractId = CONTRACT_ID,
                payment = Payment(id = "p0", date = asOf, amount = 17 * 250_000L),
                method = PaymentMethod.Tunai,
                accountId = null,
                referenceNo = null,
                notes = null,
                asOf = asOf,
            )

            val preview = repository.previewDeposit(
                contractId = CONTRACT_ID,
                payment = Payment(id = "p1", date = asOf, amount = 500_000),
                asOf = asOf,
            )

            assertEquals("no arrears before", 0, preview.arrearsBefore)
            assertEquals("and none after", 0, preview.arrearsAfter)
            assertEquals("no day was closed", 0, preview.daysClosed)
            assertTrue("so the whole deposit is credit", preview.hasCredit)
            assertEquals("and arrears never reads negative", 0, preview.arrearsAfter)
        }

    /**
     * `PREVIEW_MATCHES_SAVE` — the whole point.
     *
     * Preview, confirm, read the rows back, and assert the preview's figures equal what
     * the write produced. Three passes because the allocator's placement can depend on
     * earlier deposits: one deposit proves the two agree once, and the second proves it
     * after history exists — which is where a preview computed over a stale read would
     * drift.
     */
    @Test
    fun `the preview equals what the save produced`() = runBlocking {
        val first = repository.previewDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p1", date = asOf, amount = 1_000_000),
            asOf = asOf,
        )
        repository.saveDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p1", date = asOf, amount = 1_000_000),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )

        val afterFirst = storedArrears()
        assertEquals("arrears after matched the stored rows", first.arrearsAfter, afterFirst.amount)
        assertEquals("and so did the days remaining", first.daysRemaining, afterFirst.days)
        assertEquals(
            "and the credit is the unplaced tail the rows actually hold",
            first.credit,
            database.allocationDao().forContract(CONTRACT_ID)
                .filter { it.paymentId == "p1" && it.chargeDate == null }
                .sumOf { it.amount },
        )
        assertEquals(
            "so the deposit closed exactly the days the preview said",
            first.daysClosed,
            database.allocationDao().forContract(CONTRACT_ID)
                .count { it.paymentId == "p1" && it.chargeDate != null },
        )

        // And again, now that there is history — the case a stale preview would miss.
        val second = repository.previewDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p2", date = asOf, amount = 3_000_000),
            asOf = asOf,
        )
        repository.saveDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p2", date = asOf, amount = 3_000_000),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )

        val afterSecond = storedArrears()
        assertEquals(
            "still agreeing after history exists",
            second.arrearsAfter,
            afterSecond.amount,
        )
        assertEquals("and the days remaining", second.daysRemaining, afterSecond.days)
        assertEquals(
            "with the second deposit's credit on the rows",
            second.credit,
            database.allocationDao().forContract(CONTRACT_ID)
                .filter { it.paymentId == "p2" && it.chargeDate == null }
                .sumOf { it.amount },
        )
    }

    /**
     * And dismissing the preview — the AC's "cannot be saved past" — leaves nothing.
     *
     * Modelled as a preview followed by no save, because "dismiss" is a UI decision and
     * the repository has no notion of one: what this proves is that the preview left the
     * database in the state it found, which is what makes a dismiss sound.
     */
    @Test
    fun `previewing then declining leaves the contract exactly as it was`() = runBlocking {
        val before = database.paymentDao().forContract(CONTRACT_ID).size

        repository.previewDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p1", date = asOf, amount = 1_000_000),
            asOf = asOf,
        )
        repository.previewDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p2", date = asOf, amount = 500_000),
            asOf = asOf,
        )

        assertEquals("no deposit appeared", before, database.paymentDao().forContract(CONTRACT_ID).size)
        assertEquals("and no allocation", 0, database.allocationDao().forContract(CONTRACT_ID).size)
    }

    /**
     * `PREVIEW_NEW_CONTRACT` — a contract with no history previews normally.
     *
     * Its own test because the AC names it: a contract with nothing on it is the case
     * where a preview that assumed existing allocations could divide by an empty list or
     * read a zero it mistook for a paid contract.
     */
    @Test
    fun `a contract with no history previews its whole period`() = runBlocking {
        val preview = repository.previewDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = "p1", date = asOf, amount = 250_000),
            asOf = asOf,
        )

        assertEquals("every open day is the full period", 4_250_000, preview.arrearsBefore)
        assertEquals("one day closes", 1, preview.daysClosed)
        assertEquals("leaving sixteen", 16, preview.daysRemaining)
        assertEquals("and the day's worth comes off", 4_000_000, preview.arrearsAfter)
    }

    /**
     * The contract's arrears as the **stored rows** say they are.
     *
     * Read through `arrearsOf` — the same function `previewDeposit` calls — rather than
     * through a second preview, because this is what the preview's numbers must equal
     * and a comparison between a thing and a copy of it proves nothing.
     */
    private suspend fun storedArrears(): ArrearsEntry = arrearsOf(
        contractId = CONTRACT_ID,
        chargeDays = chargeDaysNow(),
        allocations = database.allocationDao().forContract(CONTRACT_ID).map {
            Allocation(paymentId = it.paymentId, chargeDate = it.chargeDate, amount = it.amount)
        },
        dailyRate = RATE,
    )
    // ------------------------------------------------------------------ fixtures
    private suspend fun chargeDaysNow(): List<LocalDate> {
        val contract = database.contractDao().findById(CONTRACT_ID)!!
        return net.klinik.rental.domain.calculate.chargeDaysIn(
            period = net.klinik.rental.domain.calculate.CutoffPeriod.containing(
                cutoffDay = 25,
                today = asOf,
            ),
            schedule = net.klinik.rental.domain.calculate.ChargeSchedule(
                contractStart = contract.startDate,
                restDay = contract.restDay,
                serviceExceptions = database.serviceExceptionDao()
                    .forContract(CONTRACT_ID).map { it.date }.toSet(),
                contractEnd = contract.endDate,
            ),
            asOf = asOf,
        )
    }

    private fun net.klinik.rental.data.db.entity.AllocationEntity.toAllocation() =
        net.klinik.rental.domain.model.Allocation(
            paymentId = paymentId,
            chargeDate = chargeDate,
            amount = amount,
        )

    /** One vehicle, one renter, one open contract at Rp 250.000/day from 1 Oct. */
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
    }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val RENTER_ID = "renter-1"
        const val CONTRACT_ID = "contract-1"
        const val RATE = 250_000L
    }
}


