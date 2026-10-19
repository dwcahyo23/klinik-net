package net.klinik.rental.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.BusinessAccountEntity
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.domain.validation.InvalidCutoffDayException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Story 5.4's write path, against a real database.
 *
 * ## What only a real database can prove
 *
 * The two claims that matter most are about **what is not written**: that every `allocation` row
 * is byte-identical after a cutoff change, and that the change is exactly one settings row. A fake
 * DAO could be written to satisfy either without the production code being right.
 *
 * The preview's arithmetic is the third: two arrears reads per contract over the same deposits,
 * which needs the real allocator, the real `chargeDaysIn` and the real `settledDays` to mean
 * anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CutoffChangeTest {

    private lateinit var database: KlinikDatabase
    private lateinit var payments: PaymentRepository

    private val asOf = LocalDate.of(2026, 3, 24)

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
        seed()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------------- reads

    /** Nothing stored reads as the default, through the same path every computation uses. */
    @Test
    fun `an unset cutoff reads as the default`() = runBlocking {
        assertEquals(PaymentRepository.DEFAULT_CUTOFF_DAY, payments.cutoffDay())
    }

    @Test
    fun `a stored cutoff reads back`() = runBlocking {
        payments.setCutoffDay(15)

        assertEquals(15, payments.cutoffDay())
    }

    // ---------------------------------------------------------------- preview

    /**
     * The preview reports a **count of contracts that moved** and the rupiah totals.
     *
     * The contract here has an open period and no deposits, so moving the cutoff from 25 to 15
     * changes which days are in the period — and therefore the arrears figure. The assertion is
     * on the shape rather than an exact rupiah figure, because the exact figure depends on the
     * charge-day schedule the fixture builds; `CUTOFF_MOVES_THE_FIGURE` is what matters.
     */
    @Test
    fun `the preview reports the change before anything is written`() = runBlocking {
        val preview = payments.previewCutoffChange(15, asOf)

        assertEquals(25, preview.oldCutoffDay)
        assertEquals(15, preview.newCutoffDay)
        assertEquals(
            "the one open contract's figure moves",
            1,
            preview.changedContractCount,
        )
        assertNotEquals(
            "and the totals differ",
            preview.totalBefore,
            preview.totalAfter,
        )
        assertEquals(preview.totalAfter - preview.totalBefore, preview.totalDelta)
    }

    /**
     * **The preview writes nothing.**
     *
     * The property that makes it a preview. Asserted on the stored cutoff *and* on the allocation
     * table, because a preview that computed by writing and rolling back would satisfy the first
     * and could still have touched the second.
     */
    @Test
    fun `the preview writes nothing`() = runBlocking {
        val cutoffBefore = payments.cutoffDay()
        val allocationsBefore = database.allocationDao().all()

        payments.previewCutoffChange(15, asOf)

        assertEquals("the cutoff is unchanged", cutoffBefore, payments.cutoffDay())
        assertEquals("and no allocation row moved", allocationsBefore, database.allocationDao().all())
    }

    /**
     * A database with **no contracts** previews as a no-op rather than failing.
     *
     * A fresh install whose owner wants to set their billing day before typing anything is the
     * normal first-run case, and a preview that refused would be a setting they cannot reach.
     */
    @Test
    fun `an empty database previews as a no-op`() = runBlocking {
        // A **separate** empty database rather than `clearAllTables()`: that call asserts it is
        // off the main thread, and `runBlocking` on the test thread is the main thread. A second
        // in-memory database is also closer to the case — a fresh install, not a wiped one.
        val empty = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        try {
            val preview = PaymentRepository(
                paymentDao = empty.paymentDao(),
                allocationDao = empty.allocationDao(),
                contractDao = empty.contractDao(),
                exceptionDao = empty.serviceExceptionDao(),
                settingDao = empty.settingDao(),
                database = empty,
            ).previewCutoffChange(15, asOf)

            assertEquals(0, preview.changedContractCount)
            assertEquals(0L, preview.totalDelta)
            assertTrue("and it says so", preview.isNoOp)
        } finally {
            empty.close()
        }
    }

    /**
     * Picking the day already in force is a no-op preview.
     *
     * The confirm is still allowed — "nothing changes" is not a refusal — but the dialog has to
     * be able to say it, which is what `isNoOp` is for.
     */
    @Test
    fun `picking the stored day previews as a no-op`() = runBlocking {
        val preview = payments.previewCutoffChange(25, asOf)

        assertEquals(0, preview.changedContractCount)
        assertEquals(0L, preview.totalDelta)
        assertTrue(preview.isNoOp)
    }

    /** A day outside 1..28 is refused before anything is read or written. */
    @Test
    fun `an out of range day is refused`() = runBlocking {
        listOf(0, 29, 31, -1).forEach { day ->
            val preview = runCatching { payments.previewCutoffChange(day, asOf) }
            assertTrue("preview of $day must be refused", preview.isFailure)
            assertTrue(
                "and with the domain's own exception",
                preview.exceptionOrNull() is InvalidCutoffDayException,
            )

            val write = runCatching { payments.setCutoffDay(day) }
            assertTrue("writing $day must be refused", write.isFailure)
        }

        assertEquals(
            "and the stored value never moved",
            PaymentRepository.DEFAULT_CUTOFF_DAY,
            payments.cutoffDay(),
        )
    }

    // ------------------------------------------------------------------ write

    /**
     * **The AC's central claim: no allocation row is deleted or rewritten.**
     *
     * Every row compared field for field. This is the assertion that would catch a future change
     * that "helpfully" re-derived the ledger after a cutoff shift — which is exactly the mistake
     * Story 1.5 exists to prevent, and which would be invisible in the arrears figures because
     * re-derivation produces the same numbers today and different ones after the next change.
     */
    @Test
    fun `changing the cutoff touches no allocation row`() = runBlocking {
        saveDeposit("p1", 250_000, LocalDate.of(2026, 3, 20))
        val before = database.allocationDao().all()
        assertTrue("the fixture must actually have rows", before.isNotEmpty())

        payments.setCutoffDay(15)

        assertEquals(
            "every allocation row is byte-identical",
            before,
            database.allocationDao().all(),
        )
    }

    /**
     * And it writes **one** row.
     *
     * The settings table holds the business name from the seed; after the change it holds exactly
     * one more entry, and the rest are untouched. A write that also cleared a cache row would fail
     * this, which is the point: the AC's "satu transaksi" is about the change being one act.
     */
    @Test
    fun `changing the cutoff writes exactly one settings row`() = runBlocking {
        val before = database.settingDao().all().associate { it.key to it.value }

        payments.setCutoffDay(15)

        val after = database.settingDao().all().associate { it.key to it.value }
        assertEquals("one key added", before.size + 1, after.size)
        assertEquals("15", after[PaymentRepository.KEY_CUTOFF_DAY])
        before.forEach { (key, value) ->
            assertEquals("$key is untouched", value, after[key])
        }
    }

    /**
     * **Every arrears read after the write uses the new day, with no recomputation step.**
     *
     * The AC's *"diubah ke semua Tunggakan dihitung ulang"*. There is no recalculation call
     * anywhere in this test — the second read simply returns a different figure, because
     * `previewAllocation` reads the cutoff from storage on each call.
     */
    @Test
    fun `arrears reflect the new cutoff immediately after the write`() = runBlocking {
        val before = database.contractDao().observeAll().first().first()
        val arrearsBefore = arrearsOfContract(before.id)

        payments.setCutoffDay(15)

        val arrearsAfter = arrearsOfContract(before.id)
        assertNotEquals(
            "the figure moved without any recomputation call",
            arrearsBefore,
            arrearsAfter,
        )
    }

    /**
     * And the **preview agreed with what the write produced**.
     *
     * The property that makes the preview worth showing: the numbers the owner approved are the
     * numbers they get. A preview computed from a different read than the write is a preview that
     * lies, and this is the assertion that would catch it.
     */
    @Test
    fun `the preview's after total matches the real read after the write`() = runBlocking {
        saveDeposit("p1", 250_000, LocalDate.of(2026, 3, 20))

        val preview = payments.previewCutoffChange(15, asOf)
        payments.setCutoffDay(15)

        assertEquals(
            "the figure the owner approved is the figure they get",
            preview.totalAfter,
            arrearsOfContract(CONTRACT_ID),
        )
    }

    // ------------------------------------------------------------------ helpers

    /** One contract's arrears through the same public read the screens use. */
    private suspend fun arrearsOfContract(contractId: String): Long =
        payments.observeArrearsForAllContracts(CLOCK, asOf = { asOf })
            .first()
            .first { it.contractId == contractId }
            .amount

    private suspend fun saveDeposit(id: String, amount: Long, date: LocalDate) {
        payments.saveDeposit(
            contractId = CONTRACT_ID,
            payment = Payment(id = id, date = date, amount = amount),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = asOf,
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
                // A period that both cutoffs can see: start well before 25 Feb, so the 25 and the
                // 15 periods differ in which days they contain rather than in whether the
                // contract exists yet.
                startDate = LocalDate.of(2026, 1, 1),
                dailyRate = 250_000,
                restDay = null,
                activeVehicleId = VEHICLE_ID,
                activeRenterId = RENTER_ID,
                endDate = null,
                settledThroughDate = null,
            ),
        )
        database.businessAccountDao().insertBusinessAccount(
            BusinessAccountEntity(id = "a1", bankName = "BCA", number = "1234567890"),
        )
        database.settingDao().putSetting(
            net.klinik.rental.data.db.entity.SettingEntity(
                key = "businessName",
                value = "Klinik Net",
            ),
        )
    }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val RENTER_ID = "renter-1"
        const val CONTRACT_ID = "contract-1"

        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-03-24T09:00:00Z"), ZoneOffset.UTC)
    }
}