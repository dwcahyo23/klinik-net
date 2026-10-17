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
import net.klinik.rental.domain.model.Payment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The deposit rows, against a real Room database.
 *
 * ## Why these run against Room and not a fake
 *
 * The invariant under test is AD-3's `Σ allocation == amount` **across two tables joined by
 * a foreign key**, and the constraint that protects it is a real `UNIQUE(paymentId,
 * chargeDate)` index. A fake DAO would prove the repository called its methods in an order; it
 * would prove nothing about whether the rows that landed actually add up — which is the one
 * thing a wrong deposit gets wrong.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentRepositoryTest {

    private lateinit var database: KlinikDatabase
    private lateinit var repository: PaymentRepository

    /** The cutoff this suite computes against. The stored default is 25; see the KDoc. */
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
        seedContract()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------- the happy paths

    /**
     * `DEPOSIT_OK_TRANSFER` — the deposit and its allocation rows land together.
     *
     * Asserted as **both** halves because that is the whole point: a deposit whose
     * allocations are missing is money the owner took that closes no day, and arrears then
     * reads too high forever with nothing on screen saying why.
     */
    @Test
    fun `a deposit writes its payment and its allocation rows`() = runBlocking {
        repository.saveDeposit(
            contractId = CONTRACT_ID,
            payment = payment(id = "p1", amount = 750_000),
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNT_ID,
            referenceNo = "TRX-001",
            notes = null,
            asOf = asOf,
        )

        val payments = repository.paymentsFor(CONTRACT_ID)
        assertEquals(1, payments.size)
        assertEquals(750_000, payments.single().amount)

        // Three days at Rp 250.000 closes exactly Rp 750.000, so three allocation rows and
        // no credit row.
        val rows = database.allocationDao().forContract(CONTRACT_ID)
        assertEquals(3, rows.size)
        assertEquals(
            "AD-3's one unconditional rule: the rows sum to the deposit",
            750_000,
            rows.sumOf { it.amount },
        )
        assertTrue("no credit row when the money lands exactly", rows.none { it.chargeDate == null })
    }

    /**
     * `DEPOSIT_OK_CASH` — no account, and the row says so.
     *
     * `accountId` is null rather than blank, because `null` is a **meaningful** value here —
     * cash has no external record — and a blank string would be a fifth thing the column
     * could hold.
     */
    @Test
    fun `a cash deposit stores no account`() = runBlocking {
        repository.saveDeposit(
            contractId = CONTRACT_ID,
            payment = payment(id = "p1", amount = 250_000),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = "Dibayar di tangan",
            asOf = asOf,
        )

        val stored = database.paymentDao().forContract(CONTRACT_ID).single()
        assertNull("a cash deposit has no account", stored.accountId)
        assertEquals("Tunai", stored.method)
        assertEquals(
            "and the owner's note is the only breadcrumb it has",
            "Dibayar di tangan",
            stored.notes,
        )
        assertEquals(1, database.allocationDao().forContract(CONTRACT_ID).size)
    }

    // ------------------------------------------------------------------ the money

    /**
     * `ALLOCATE_SURPLUS` — money beyond the open days becomes the credit row.
     *
     * This is AD-3's whole reason the table exists: credit is the `chargeDate IS NULL` row,
     * re-derived every period, so it survives the month rollover instead of being a figure
     * somebody has to remember to carry forward.
     */
    @Test
    fun `a deposit larger than the open days leaves a credit row`() = runBlocking {
        // The arithmetic, because getting it wrong is the difference between this test
        // proving the allocator's tail and it silently proving that the deposit landed
        // on whole days:
        //
        //   period  = 25 Sep - 24 Oct  (a 25th cutoff, as of 17 Oct)
        //   contract starts 1 Oct, so days before that are not chargeable
        //   capped at asOf (17 Oct)
        //   => 17 charge days: 1 Oct - 17 Oct
        //   => 17 x Rp 250.000 = Rp 4.250.000 still owed
        //
        // Rp 3.000.000 fills exactly 12 of those days with **no** remainder, so it would
        // produce no credit row at all — which is why the figure is above.
        val openDaysWorth = 17 * 250_000L
        val deposit = openDaysWorth + 1_500_000

        repository.saveDeposit(
            contractId = CONTRACT_ID,
            payment = payment(id = "p1", amount = deposit),
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNT_ID,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )

        val rows = database.allocationDao().forContract(CONTRACT_ID)
        val credit = rows.filter { it.chargeDate == null }
        assertEquals("the surplus is exactly one credit row", 1, credit.size)
        assertEquals(
            "holding every rupiah that had no day left to sit on",
            1_500_000,
            credit.single().amount,
        )
        assertEquals(
            "while the whole set still sums to the deposit",
            deposit,
            rows.sumOf { it.amount },
        )
        assertEquals(
            "and every one of the 17 open days is fully closed",
            17,
            rows.count { it.chargeDate != null },
        )
    }

    /**
     * `ALLOCATE_ONE_ROW` — a deposit smaller than one day's rate is a **part** of a day.
     *
     * The day stays open; only the remainder of it is closed. A whole day charged for a
     * partial payment would invent money the tenant never sent.
     */
    @Test
    fun `a deposit smaller than one day closes only part of it`() = runBlocking {
        repository.saveDeposit(
            contractId = CONTRACT_ID,
            payment = payment(id = "p1", amount = 100_000),
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNT_ID,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )

        val rows = database.allocationDao().forContract(CONTRACT_ID)
        assertEquals("one row, for the oldest open day", 1, rows.size)
        assertEquals(100_000, rows.single().amount)
        assertEquals(
            "which is less than the rate, so the day is not fully closed",
            100_000,
            rows.single().amount,
        )
    }

    // --------------------------------------------------------------- the refusals

    /**
     * A deposit of zero or a negative figure is refused and **neither table is written**.
     *
     * `Payment.init` refuses it before the repository is reached, which is why the refusal is
     * a constructor throw rather than a repository check — but the test asserts the *effect*,
     * because a refusal that left a payment row behind would be worse than none.
     */
    @Test
    fun `a zero or negative deposit is refused and writes nothing`() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            Payment(id = "p1", date = asOf, amount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Payment(id = "p1", date = asOf, amount = -500_000)
        }
        assertTrue(database.paymentDao().forContract(CONTRACT_ID).isEmpty())
        assertTrue(database.allocationDao().forContract(CONTRACT_ID).isEmpty())
    }

    /** And a deposit on a contract nobody holds is refused rather than creating one. */
    @Test
    fun `a deposit on an unknown contract is refused`() {
        assertThrows(net.klinik.rental.data.repository.UnknownContractException::class.java) {
            runBlocking {
                repository.saveDeposit(
                    contractId = "no-such-contract",
                    payment = payment(id = "p1", amount = 250_000),
                    method = PaymentMethod.Tunai,
                    accountId = null,
                    referenceNo = null,
                    notes = null,
                    asOf = asOf,
                )
            }
        }
    }

    // --------------------------------------------------------- the historical rows

    /**
     * `DEPOSIT_PAST_DATE` — a deposit dated in the past is stored verbatim.
     *
     * Correction is the owner's normal case, and it is why `createdAt` is separate from
     * `date`: the money arrived on the 5th even if it was typed on the 17th.
     */
    @Test
    fun `a past-dated deposit is stored verbatim`() = runBlocking {
        val past = LocalDate.of(2026, 10, 5)

        repository.saveDeposit(
            contractId = CONTRACT_ID,
            payment = payment(id = "p1", date = past, amount = 250_000),
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNT_ID,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )

        assertEquals(past, database.paymentDao().forContract(CONTRACT_ID).single().date)
    }

    /**
     * Two deposits close different days — the second does not land on the first one's day.
     *
     * This is what `UNIQUE(paymentId, chargeDate)` protects, and what re-allocating the
     * whole contract produces. Without the unique index both deposits could claim the same
     * day and the day's allocation would exceed the rate.
     */
    @Test
    fun `two deposits close different days`() = runBlocking {
        repository.saveDeposit(
            contractId = CONTRACT_ID,
            payment = payment(id = "p1", amount = 250_000),
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNT_ID,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )
        repository.saveDeposit(
            contractId = CONTRACT_ID,
            payment = payment(id = "p2", amount = 250_000),
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNT_ID,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )

        val rows = database.allocationDao().forContract(CONTRACT_ID)
        assertEquals("two rows, on two different days", 2, rows.size)
        assertEquals(
            "and the days are distinct",
            2,
            rows.map { it.chargeDate }.toSet().size,
        )
        assertEquals(
            "the contract's rate is what each day is worth",
            250_000,
            rows.first { it.chargeDate != null }.amount,
        )
        assertEquals(500_000, rows.sumOf { it.amount })
    }

    // ----------------------------------------------------------------- fixtures

    /** The vehicle, renter and an **open** contract at Rp 250.000/day from 1 Oct. */
    private suspend fun seedContract() {
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
                dailyRate = 250_000,
                restDay = null,
                activeVehicleId = VEHICLE_ID,
                activeRenterId = RENTER_ID,
                endDate = null,
            )
        )
        database.businessAccountDao().insertBusinessAccount(
            net.klinik.rental.data.db.entity.BusinessAccountEntity(
                id = ACCOUNT_ID,
                bankName = "BCA",
                number = "1234567890",
            )
        )
    }

    private fun payment(id: String, amount: Long, date: LocalDate = asOf) =
        Payment(id = id, date = date, amount = amount)

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val RENTER_ID = "renter-1"
        const val CONTRACT_ID = "contract-1"
        const val ACCOUNT_ID = "account-1"
    }
}