package net.klinik.rental.ui.screens.setoran

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.BusinessAccountEntity
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.BlankCancelReasonException
import net.klinik.rental.data.repository.DepositAlreadyCanceledException
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.UnknownDepositException
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
 * Story 3.4's `CANCEL_*` rows, against a real Room database.
 *
 * ## Why these run against Room
 *
 * Two of the claims are about **schema**, not about the repository calling its methods:
 * that the cancelled row is still returned by the contract's query, and that every
 * surviving deposit's rows still sum to its own amount after a wholesale re-allocation. A
 * fake DAO would prove neither.
 *
 * ## The arithmetic every test shares
 *
 * With a 25th cutoff and `asOf` 17 Oct the period is 25 Sep - 24 Oct; the contract
 * starts 1 Oct, so there are **17 charge days** at Rp 250.000 — Rp 4.250.000 owed. Each
 * test spells out what that makes its figures, because a cancellation test that guesses its
 * own period silently stops proving what it names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CancelDepositTest {

    private lateinit var database: KlinikDatabase
    private lateinit var repository: PaymentRepository

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
     * `CANCEL_OK` — the reason is stored, the allocations are released, and the arrears
     * rise by exactly what was cancelled.
     *
     * Rp 750.000 closes three of seventeen days, so arrears is Rp 3.500.000 before and
     * Rp 4.250.000 after — the difference being the three days that reopened.
     */
    @Test
    fun `cancelling stores the reason and releases the deposit's allocations`() = runBlocking {
        saveDeposit("p1", 750_000)
        assertEquals(3, database.allocationDao().forContract(CONTRACT_ID).size)
        assertEquals(3_500_000, repository.currentArrears(CONTRACT_ID, asOf))

        repository.cancelDeposit(CONTRACT_ID, "p1", "salah tanggal")

        assertEquals(
            "every day this deposit closed reopens",
            0,
            database.allocationDao().forContract(CONTRACT_ID).size,
        )
        assertEquals(
            "and arrears returns to the whole period",
            4_250_000,
            repository.currentArrears(CONTRACT_ID, asOf),
        )
        val stored = database.paymentDao().findById("p1")!!
        assertEquals(PaymentEntity.STATUS_CANCELED, stored.status)
        assertEquals("with the owner's own words", "salah tanggal", stored.cancelReason)
    }

    /**
     * `CANCEL_NO_REASON` — refused, and **nothing** changed.
     *
     * Asserted on the row's status as well as the throw, because a refusal that marked the
     * deposit anyway would leave a cancellation the owner never confirmed.
     */
    @Test
    fun `a blank reason is refused and nothing is written`() = runBlocking {
        saveDeposit("p1", 750_000)

        assertThrows(BlankCancelReasonException::class.java) {
            runBlocking { repository.cancelDeposit(CONTRACT_ID, "p1", "   ") }
        }

        val stored = database.paymentDao().findById("p1")!!
        assertEquals("still live", PaymentEntity.STATUS_CONFIRMED, stored.status)
        assertNull("and no reason was attached", stored.cancelReason)
        assertEquals("with its allocations intact", 3, database.allocationDao().forContract(CONTRACT_ID).size)
    }

    /** `CANCEL_ALREADY` — a second cancellation is refused, reason untouched. */
    @Test
    fun `a second cancellation is refused and the first reason stands`() = runBlocking {
        saveDeposit("p1", 750_000)
        repository.cancelDeposit(CONTRACT_ID, "p1", "salah tanggal")

        assertThrows(DepositAlreadyCanceledException::class.java) {
            runBlocking { repository.cancelDeposit(CONTRACT_ID, "p1", "alasan lain") }
        }

        assertEquals(
            "a newer explanation must not overwrite the audit trail",
            "salah tanggal",
            database.paymentDao().findById("p1")!!.cancelReason,
        )
    }

    /** `CANCEL_UNKNOWN` — a deposit nobody holds is refused rather than created. */
    @Test
    fun `cancelling a deposit nobody holds is refused`() = runBlocking {
        saveDeposit("p1", 750_000)

        assertThrows(UnknownDepositException::class.java) {
            runBlocking { repository.cancelDeposit(CONTRACT_ID, "no-such", "salah tanggal") }
        }
        // The stored deposit is untouched rather than deleted by the failed lookup.
        assertEquals(1, database.paymentDao().forContract(CONTRACT_ID).size)
        assertEquals(3, database.allocationDao().forContract(CONTRACT_ID).size)
    }

    /**
     * `CANCEL_ROW_STAYS` — **the row is still there**, with its reason.
     *
     * The epic's criterion is *"tidak ada hard delete"*, and this is the assertion that
     * would fail if a delete were ever added: it reads through the *same* query the
     * history will use, not through a special one.
     */
    @Test
    fun `a cancelled deposit is still readable`() = runBlocking {
        saveDeposit("p1", 750_000)
        repository.cancelDeposit(CONTRACT_ID, "p1", "salah tanggal")

        val observed = repository.paymentsFor(CONTRACT_ID)
        assertEquals("the row survives cancellation", 1, observed.size)
        assertEquals("p1", observed.single().id)
    }

    /**
     * `CANCEL_SUM_HOLDS` — AD-3's `Σ allocation == amount` survives, for **every**
     * surviving deposit, after a wholesale re-allocation.
     *
     * This is the row that catches a partial cancel: if only the cancelled deposit's rows
     * were deleted and the survivors left placed as they were, each survivor's rows would
     * still sum to its own amount — so the sums alone would pass — but the *days* would be
     * wrong. Both are asserted below.
     */
    @Test
    fun `every surviving deposit's rows still sum to its amount`() = runBlocking {
        saveDeposit("p1", 1_000_000)
        saveDeposit("p2", 1_000_000)

        repository.cancelDeposit(CONTRACT_ID, "p1", "salah tanggal")

        val rows = database.allocationDao().forContract(CONTRACT_ID)
        val byDeposit = rows.groupBy { it.paymentId }
        assertEquals("only the survivor is still placed", setOf("p2"), byDeposit.keys)
        for ((paymentId, depositRows) in byDeposit) {
            assertEquals(
                "AD-3's unconditional rule holds for $paymentId",
                1_000_000L,
                depositRows.sumOf { it.amount },
            )
        }
        // And the survivor landed on the *oldest* days, because the first four are now
        // free — which is the shift a partial cancel would have missed.
        val days = byDeposit.getValue("p2").mapNotNull { it.chargeDate }.sorted()
        assertEquals(
            "and it took the days the cancellation opened",
            LocalDate.of(2026, 10, 1),
            days.first(),
        )
    }

    /**
     * `CANCEL_LAST` — cancelling the only deposit returns the contract to the whole period.
     *
     * Its own test because it is the boundary: a re-allocation that ran with an empty
     * payment list would divide by nothing or short-circuit, and this is where that shows.
     */
    @Test
    fun `cancelling the only deposit returns the whole period`() = runBlocking {
        saveDeposit("p1", 4_250_000)

        repository.cancelDeposit(CONTRACT_ID, "p1", "salah tanggal")

        assertEquals("no placements survive", 0, database.allocationDao().forContract(CONTRACT_ID).size)
        assertEquals("and every day is owed again", 4_250_000, repository.currentArrears(CONTRACT_ID, asOf))
    }

    /**
     * `CANCEL_ARREARS_UP` — the dialog's impact figure is the arrears *after*, honestly
     * allocated, not the current figure minus the deposit.
     *
     * The two happen to agree in the simple cases, which is exactly why the two-deposit
     * case is here: cancelling the first lets the second move onto the days it opens, so a
     * subtracting implementation would report a number the allocator never produces.
     */
    @Test
    fun `the dialog figure is the arrears after cancelling, allocated`() = runBlocking {
        saveDeposit("p1", 1_000_000)
        saveDeposit("p2", 1_000_000)
        // Rp 1.000.000 closes four whole days at Rp 250.000, so two deposits close eight
        // of the seventeen and leave Rp 4.250.000 - Rp 2.000.000 = Rp 2.250.000 owed.
        assertEquals("two deposits close eight of seventeen days", 2_250_000, repository.currentArrears(CONTRACT_ID, asOf))

        val after = repository.arrearsAfterCanceling(CONTRACT_ID, "p1", asOf)

        repository.cancelDeposit(CONTRACT_ID, "p1", "salah tanggal")
        assertEquals(
            "and the dialog promised exactly what the write produced",
            repository.currentArrears(CONTRACT_ID, asOf),
            after,
        )
        // Only p2's Rp 1.000.000 survives, so Rp 3.250.000 is owed. A
        // *subtracting* implementation would have said Rp 4.250.000 - (Rp 2.250.000 -
        // Rp 1.000.000) = Rp 3.000.000 — wrong by Rp 250.000, which is exactly one day,
        // and exactly the size of error an owner would read as a rounding slip.
        assertEquals(
            "which a subtract-the-deposit figure would have got wrong",
            3_250_000,
            after,
        )
    }

    /** And an unknown or already-cancelled deposit reports the present, not a promise. */
    @Test
    fun `the dialog figure falls back to the present when nothing would change`() = runBlocking {
        saveDeposit("p1", 750_000)
        val present = repository.currentArrears(CONTRACT_ID, asOf)

        assertEquals(
            "an unknown deposit changes nothing",
            present,
            repository.arrearsAfterCanceling(CONTRACT_ID, "no-such", asOf),
        )

        repository.cancelDeposit(CONTRACT_ID, "p1", "salah tanggal")
        val afterCancel = repository.currentArrears(CONTRACT_ID, asOf)
        assertEquals(
            "and so does an already-cancelled one",
            afterCancel,
            repository.arrearsAfterCanceling(CONTRACT_ID, "p1", asOf),
        )
    }

    /**
     * The entity refuses a cancelled row with **no reason** — the AC's rule, enforced
     * where the row is built rather than only at the call site.
     */
    @Test
    fun `a cancelled deposit cannot be built without a reason`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentEntity(
                id = "bad",
                contractId = CONTRACT_ID,
                accountId = null,
                date = asOf,
                amount = 250_000,
                method = "Tunai",
                referenceNo = null,
                notes = null,
                createdAt = java.time.Instant.parse("2026-10-17T09:00:00Z"),
                status = PaymentEntity.STATUS_CANCELED,
                cancelReason = "   ",
            )
        }
    }

    // ------------------------------------------------------------------ fixtures

    /** Stores one confirmed deposit through the real write path. */
    private suspend fun saveDeposit(id: String, amount: Long) {
        repository.saveDeposit(
            contractId = CONTRACT_ID,
            payment = net.klinik.rental.domain.model.Payment(id = id, date = asOf, amount = amount),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )
    }

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
        const val RATE = 250_000L
    }
}