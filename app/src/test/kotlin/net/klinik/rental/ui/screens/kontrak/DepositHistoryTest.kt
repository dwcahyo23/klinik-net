package net.klinik.rental.ui.screens.kontrak

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.BusinessAccountEntity
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.DepositHistoryRepository
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Story 3.4's stored rows, finally readable — against a real database.
 *
 * ## Why this file exists at all
 *
 * Because Story 3.4 chose a **status flag over a delete** so an audit trail could be read
 * later, and nothing ever read it. The decision was correct and the code was finished; what
 * was missing was a row with a button on it. These tests are the other half of that: they
 * prove the reason an owner typed when cancelling is actually reachable from the app.
 *
 * ## Why a real Room database
 *
 * Because the claims are about **schema**: that a cancelled row survives, that it carries
 * its reason, and that a cancellation written through the repository re-appears on the
 * next read. A fake DAO could satisfy none of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DepositHistoryTest {

    private lateinit var database: KlinikDatabase
    private lateinit var payments: PaymentRepository
    private lateinit var history: DepositHistoryRepository

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
        history = DepositHistoryRepository(database.paymentDao())
        seed()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Three deposits, **oldest first**.
     *
     * Ordered by the query rather than by a sort in the repository — and a second sort
     * would be a second answer to "which came first". The amounts differ so a wrong order
     * cannot pass: this asserts `p1 → p2 → p3` by amount, not by identity alone.
     */
    @Test
    fun `deposits are listed oldest first`() = runBlocking {
        saveDeposit("p1", 250_000, LocalDate.of(2026, 10, 5))
        saveDeposit("p2", 500_000, LocalDate.of(2026, 10, 12))
        saveDeposit("p3", 750_000, LocalDate.of(2026, 10, 15))

        val rows = history.forContract(CONTRACT_ID)

        assertEquals(3, rows.size)
        assertEquals(
            "oldest date first, and the amounts prove it",
            listOf(250_000L, 500_000L, 750_000L),
            rows.map { it.amount },
        )
        assertEquals(
            "every row carries the date it was recorded on",
            LocalDate.of(2026, 10, 5),
            rows.first().date,
        )
        assertEquals(
            "and the method it arrived by",
            PaymentMethod.Tunai,
            rows.first().method,
        )
    }

    /**
     * `HIST_CANCELLED_SHOWN` — a cancelled deposit is **still listed**, and carries the
     * owner's own reason.
     *
     * This is the whole point of Story 3.4's soft delete, asserted. Had the history filtered
     * on status it would have looked up the entity and quietly reintroduced the filter the
     * allocator's KDoc refuses — and the reason the owner typed would have been written
     * once and never read by anything.
     */
    @Test
    fun `a cancelled deposit is still listed with the owner's reason`() = runBlocking {
        saveDeposit("p1", 250_000, LocalDate.of(2026, 10, 5))
        saveDeposit("p2", 500_000, LocalDate.of(2026, 10, 12))
        payments.cancelDeposit(CONTRACT_ID, "p1", "salah tanggal")

        val rows = history.forContract(CONTRACT_ID)
        val cancelled = rows.first { it.id == "p1" }

        assertEquals("still listed", 2, rows.size)
        assertTrue("and marked cancelled", cancelled.isCanceled)
        assertEquals(
            "with the owner's own words, which is the column the trail exists for",
            "salah tanggal",
            cancelled.cancelReason,
        )
        assertFalse("which is why it is no longer editable", cancelled.isEditable)

        // And the survivor is untouched by the cancellation — Story 3.4's claim.
        val live = rows.first { it.id == "p2" }
        assertFalse(live.isCanceled)
        assertNull(live.cancelReason)
        assertTrue("and still editable", live.isEditable)
    }

    /** A contract with no deposits is an empty list, not a failure. */
    @Test
    fun `a contract with no deposits reads empty`() = runBlocking {
        assertTrue(history.forContract(CONTRACT_ID).isEmpty())
    }

    /**
     * `HIST_UPDATE` — a cancellation written **through the repository** appears on the next
     * read, with no reload and no refresh signal.
     *
     * The read is a one-shot query, so "it re-emits" is really "the next read is
     * correct" — and Room's invalidation tracker means the caller gets a fresh one. What
     * matters is that the write and the read agree, which is what this pins.
     */
    @Test
    fun `a cancellation written now is on the next read`() = runBlocking {
        saveDeposit("p1", 250_000, LocalDate.of(2026, 10, 5))
        assertFalse("confirmed before", history.forContract(CONTRACT_ID).first().isCanceled)

        payments.cancelDeposit(CONTRACT_ID, "p1", "salah nominal")

        val row = history.forContract(CONTRACT_ID).first { it.id == "p1" }
        assertTrue("cancelled on the next read", row.isCanceled)
        assertEquals("salah nominal", row.cancelReason)
        assertFalse("and no longer editable", row.isEditable)
    }

    /**
     * A transfer carries the account it arrived through, resolved to `bank · number`.
     *
     * Resolved here rather than from a second lookup: the row already knows the account
     * id, and the label is a presentation concern, so the read stays one row per deposit.
     */
    @Test
    fun `a transfer carries the account it arrived through`() = runBlocking {
        payments.saveDeposit(
            contractId = CONTRACT_ID,
            payment = net.klinik.rental.domain.model.Payment(
                id = "p1",
                date = LocalDate.of(2026, 10, 5),
                amount = 250_000,
            ),
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNT_ID,
            referenceNo = "TRX-9",
            notes = null,
            asOf = asOf,
        )

        val row = history.forContract(CONTRACT_ID).single()
        assertEquals(PaymentMethod.TransferBank, row.method)
        assertEquals(ACCOUNT_ID, row.accountId)
        assertEquals("TRX-9", row.referenceNo)
    }

    /** A cash deposit has no account, which is the design rather than missing data. */
    @Test
    fun `a cash deposit carries no account`() = runBlocking {
        saveDeposit("p1", 250_000, LocalDate.of(2026, 10, 5))

        val row = history.forContract(CONTRACT_ID).single()
        assertEquals(PaymentMethod.Tunai, row.method)
        assertNull("cash has no external source to verify it", row.accountId)
    }

    // ------------------------------------------------------------------ fixtures

    private suspend fun saveDeposit(id: String, amount: Long, date: LocalDate) {
        payments.saveDeposit(
            contractId = CONTRACT_ID,
            payment = net.klinik.rental.domain.model.Payment(id = id, date = date, amount = amount),
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
            BusinessAccountEntity(id = ACCOUNT_ID, bankName = "BCA", number = "1234567890"),
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
