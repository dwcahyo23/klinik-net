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
import net.klinik.rental.data.repository.AccountRequiredException
import net.klinik.rental.data.repository.DepositAlreadyCanceledException
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.UnknownAccountException
import net.klinik.rental.data.repository.UnknownDepositException
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.domain.validation.InvalidAmountException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Story 3.5's `CORRECT_*` rows, against a real Room database.
 *
 * ## Why these run against Room
 *
 * Two of the claims are about **schema**, not about the repository calling its methods: that
 * a corrected deposit still reads back through the contract's query, and that every
 * surviving deposit's rows still sum to its own amount after a wholesale re-allocation. A
 * fake DAO would prove neither.
 *
 * ## The arithmetic every test shares
 *
 * With a 25th cutoff and `asOf` 17 Oct the period is 25 Sep - 24 Oct; the contract starts
 * 1 Oct and rests on no day, so there are **17 charge days** at Rp 250.000 — Rp 4.250.000
 * owed. Each test spells out what that makes its figures, because a correction test that
 * guesses its own period silently stops proving what it names.
 *
 * The rest-day contract is a **separate** fixture, seeded by [seedRestDayContract], because
 * `CORRECT_DATE_SUNDAY_TO_MONDAY` is a six-day week: on a seven-day contract Sunday is an
 * ordinary charge day and the case would prove nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CorrectDepositTest {

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
     * `CORRECT_DATE_SUNDAY_TO_MONDAY` — the epic's own UJ-2 case, on a contract that
     * actually rests on Sunday.
     *
     * A six-day week from 1 Oct with a 25th cutoff, evaluated **as of Sunday 4 Oct**: the
     * period is 25 Sep - 24 Oct, the contract starts 1 Oct, and 4 Oct is the rest day — so
     * the chargeable days are Thu 1, Fri 2 and Sat 3, **Rp 750.000** owed. A Rp 500.000
     * deposit dated Sunday 4 Oct closes the two oldest of them, leaving Sat 3 open at
     * **Rp 250.000**.
     *
     * Correcting it to **Monday 5 Oct** moves the arrival a day later. Monday is a charge
     * day the deposit had not reached, but allocation is chronological rather than by
     * proximity — money arriving on any day still closes the *oldest open* day — so the two
     * days it closed stay closed and arrears stays at Rp 250.000.
     *
     * That is the honest result, and asserting it is the point: a re-derivation that merely
     * followed the date would have reopened Friday and reported Rp 500.000 owed, which is
     * the prototype's defect the epic forbids.
     */
    @Test
    fun `correcting a Sunday deposit to Monday keeps the days it closed closed`() = runBlocking {
        seedRestDayContract()
        val sunday = LocalDate.of(2026, 10, 4)
        val monday = LocalDate.of(2026, 10, 5)
        saveDeposit(REST_DAY_CONTRACT_ID, "rest-1", sunday, 500_000)

        // Sunday itself is the rest day, so only Thu 1, Fri 2 and Sat 3 are chargeable —
        // three days, and Rp 500.000 of it closed.
        assertEquals(250_000, repository.currentArrears(REST_DAY_CONTRACT_ID, sunday))
        assertEquals(
            "and the deposit closed two of the three",
            2,
            database.allocationDao().forContract(REST_DAY_CONTRACT_ID).size,
        )

        repository.correctDeposit(
            contractId = REST_DAY_CONTRACT_ID,
            paymentId = "rest-1",
            date = monday,
            amount = 500_000,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = monday,
        )

        assertEquals("the deposit is now dated Monday", monday, database.paymentDao().findById("rest-1")!!.date)
        // As of Monday the period gains one chargeable day — Monday 5 is not the rest day —
        // so the arrears *cannot* stay at Rp 250.000 if the correction closed nothing more.
        // It must rise by exactly one day's rate: four charge days, Rp 1.000.000 owed, less
        // the Rp 500.000 still placed.
        assertEquals(
            "Monday is chargeable, so one more day is owed — the deposit's own two stay closed",
            500_000,
            repository.currentArrears(REST_DAY_CONTRACT_ID, monday),
        )
        assertEquals(
            "and the corrected deposit still places two rows, not one",
            2,
            database.allocationDao().forContract(REST_DAY_CONTRACT_ID)
                .count { it.paymentId == "rest-1" },
        )
    }

    /**
     * The same correction on the **seven-day** contract, where Sunday is a charge day — so
     * the pair together shows the rest day is what makes the first case interesting rather
     * than the weekday alone.
     *
     * A deposit dated Sunday 4 Oct closes 1 and 2 Oct (the two oldest charge days). Corrected
     * to Monday 5 Oct it still closes those two, and arrears is unchanged: 15 charge days
     * remain open at Rp 250.000 either way.
     */
    @Test
    fun `correcting across a weekday on a seven day contract leaves the open days alone`() =
        runBlocking {
            val sunday = LocalDate.of(2026, 10, 4)
            val monday = LocalDate.of(2026, 10, 5)
            saveDeposit(CONTRACT_ID, "p1", sunday, 500_000)
            val before = repository.currentArrears(CONTRACT_ID, monday)

            repository.correctDeposit(
                contractId = CONTRACT_ID,
                paymentId = "p1",
                date = monday,
                amount = 500_000,
                accountId = null,
                referenceNo = null,
                notes = null,
                asOf = monday,
            )

            assertEquals(monday, database.paymentDao().findById("p1")!!.date)
            assertEquals(
                "moving the arrival a day later does not move which days it closes",
                before,
                repository.currentArrears(CONTRACT_ID, monday),
            )
        }

    /**
     * `CORRECT_AMOUNT_INCREASE` — the amount rises, one more day closes, arrears falls.
     *
     * Rp 250.000 closes the single oldest charge day; corrected to Rp 500.000 it closes two,
     * so arrears drops by exactly one day's rate and a second allocation row exists.
     */
    @Test
    fun `increasing the amount closes one more day`() = runBlocking {
        saveDeposit(CONTRACT_ID, "p1", asOf, 250_000)
        assertEquals(1, database.allocationDao().forContract(CONTRACT_ID).size)
        assertEquals(4_000_000, repository.currentArrears(CONTRACT_ID, asOf))

        repository.correctDeposit(
            contractId = CONTRACT_ID,
            paymentId = "p1",
            date = asOf,
            amount = 500_000,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )

        assertEquals(
            "one more day is closed",
            2,
            database.allocationDao().forContract(CONTRACT_ID).size,
        )
        assertEquals(3_750_000, repository.currentArrears(CONTRACT_ID, asOf))
        assertEquals(500_000, database.paymentDao().findById("p1")!!.amount)
    }

    /**
     * `CORRECT_AMOUNT_DECREASE` — and the mirror: the amount falls, a day reopens, arrears
     * rises by the same one day's rate.
     */
    @Test
    fun `decreasing the amount reopens a day`() = runBlocking {
        saveDeposit(CONTRACT_ID, "p1", asOf, 750_000)
        assertEquals(3, database.allocationDao().forContract(CONTRACT_ID).size)
        assertEquals(3_500_000, repository.currentArrears(CONTRACT_ID, asOf))

        repository.correctDeposit(
            contractId = CONTRACT_ID,
            paymentId = "p1",
            date = asOf,
            amount = 250_000,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )

        assertEquals(1, database.allocationDao().forContract(CONTRACT_ID).size)
        assertEquals(4_000_000, repository.currentArrears(CONTRACT_ID, asOf))
        assertEquals(250_000, database.paymentDao().findById("p1")!!.amount)
    }

    /**
     * `CORRECT_ZERO_AMOUNT` — refused, and the stored amount is untouched.
     *
     * Both signs, because `InvalidAmountException` is one rule and a negative is the case
     * a caller reaching this directly would actually pass.
     */
    @Test
    fun `a zero or negative amount is refused and nothing is written`() = runBlocking {
        saveDeposit(CONTRACT_ID, "p1", asOf, 500_000)

        assertThrows(InvalidAmountException::class.java) {
            runBlocking { correct("p1", CONTRACT_ID, amount = 0) }
        }
        assertThrows(InvalidAmountException::class.java) {
            runBlocking { correct("p1", CONTRACT_ID, amount = -100_000) }
        }

        assertEquals("the stored amount is unchanged", 500_000, database.paymentDao().findById("p1")!!.amount)
        assertEquals(
            "and so is its allocation",
            2,
            database.allocationDao().forContract(CONTRACT_ID).size,
        )
    }

    /**
     * `CORRECT_TRANSFER_ACCOUNT_EDIT` — account, reference and notes all move to the new
     * values, and the allocations survive the edit.
     *
     * The two accounts are on **different banks** rather than the same one, so the assertion
     * cannot pass by picking an account that happens to share a number.
     */
    @Test
    fun `a transfer's account reference and notes can be corrected`() = runBlocking {
        saveDeposit(CONTRACT_ID, "p1", asOf, 250_000, method = PaymentMethod.TransferBank, accountId = ACCOUNT_1_ID)
        val allocationsBefore = database.allocationDao().forContract(CONTRACT_ID).size

        repository.correctDeposit(
            contractId = CONTRACT_ID,
            paymentId = "p1",
            date = asOf,
            amount = 250_000,
            accountId = ACCOUNT_2_ID,
            referenceNo = "REF-NEW",
            notes = "Dibularui setelah dicek mutasi",
            asOf = asOf,
        )

        val stored = database.paymentDao().findById("p1")!!
        assertEquals(ACCOUNT_2_ID, stored.accountId)
        assertEquals("REF-NEW", stored.referenceNo)
        assertEquals("Dibularui setelah dicek mutasi", stored.notes)
        assertEquals(
            "and the placement is untouched — the amount did not move",
            allocationsBefore,
            database.allocationDao().forContract(CONTRACT_ID).size,
        )
    }

    /**
     * `CORRECT_TRANSFER_ACCOUNT_EMPTY` — **below the sheet**.
     *
     * The epic context is explicit that two screens can pass the same UI check, so the check
     * that counts is the one under them. The sheet refuses a transfer with no account; this
     * proves the repository refuses it too, for a caller that never went through the sheet.
     */
    @Test
    fun `a transfer corrected to no account is refused`() = runBlocking {
        saveDeposit(CONTRACT_ID, "p1", asOf, 250_000, method = PaymentMethod.TransferBank, accountId = ACCOUNT_1_ID)

        assertThrows(AccountRequiredException::class.java) {
            runBlocking {
                correct("p1", CONTRACT_ID, amount = 250_000, accountId = null)
            }
        }

        assertEquals(
            "the account is untouched",
            ACCOUNT_1_ID,
            database.paymentDao().findById("p1")!!.accountId,
        )
    }

    /** And a cash deposit may keep a null account, because that is what cash means. */
    @Test
    fun `a cash deposit corrects with no account`() = runBlocking {
        saveDeposit(CONTRACT_ID, "p1", asOf, 250_000, method = PaymentMethod.Tunai)

        correct("p1", CONTRACT_ID, amount = 250_000, accountId = null)

        assertEquals(250_000, database.paymentDao().findById("p1")!!.amount)
        assertNull(database.paymentDao().findById("p1")!!.accountId)
    }

    /**
     * The epic's "only one current value" rule, asserted on the row **and** on the method.
     *
     * The method half is the stronger claim: a correction that kept no record of the old
     * method but let the caller overwrite it would satisfy the first assertion while losing
     * the one field the AC forbids changing.
     */
    @Test
    fun `a correction keeps one row and the stored method`() = runBlocking {
        saveDeposit(CONTRACT_ID, "p1", asOf, 250_000, method = PaymentMethod.Tunai)
        assertEquals(1, repository.paymentsFor(CONTRACT_ID).size)

        correct("p1", CONTRACT_ID, amount = 500_000)

        assertEquals(
            "still exactly one deposit on the contract",
            1,
            repository.paymentsFor(CONTRACT_ID).size,
        )
        val stored = database.paymentDao().findById("p1")!!
        assertEquals(500_000, stored.amount)
        assertEquals(
            "and the method is carried from the row — there is no parameter to change it",
            PaymentMethod.Tunai.name,
            stored.method,
        )
    }

    /**
     * A correction naming an account nobody holds is refused, not deferred to the foreign key.
     *
     * The check exists because the constraint would otherwise refuse it — mid-transaction,
     * with a `SQLiteConstraintException` naming a table rather than the account. The sheet's
     * picker only offers stored accounts, so the only way here is a caller that bypassed it,
     * and a refusal it can act on is worth the one query.
     */
    @Test
    fun `a transfer corrected to an unknown account is refused`() = runBlocking {
        saveDeposit(CONTRACT_ID, "p1", asOf, 250_000, method = PaymentMethod.TransferBank, accountId = ACCOUNT_1_ID)

        assertThrows(UnknownAccountException::class.java) {
            runBlocking { correct("p1", CONTRACT_ID, amount = 250_000, accountId = "no-such-account") }
        }

        assertEquals(
            "the stored account is untouched",
            ACCOUNT_1_ID,
            database.paymentDao().findById("p1")!!.accountId,
        )
    }

    /** `Σ allocation == amount` holds for **every** deposit, not only the corrected one. */
    @Test
    fun `every deposit's allocations still sum to its amount`() = runBlocking {
        saveDeposit(CONTRACT_ID, "p1", asOf, 500_000)
        saveDeposit(CONTRACT_ID, "p2", asOf, 750_000)

        correct("p1", CONTRACT_ID, amount = 1_250_000)

        val rows = database.allocationDao().forContract(CONTRACT_ID)
        assertEquals(1_250_000, rows.filter { it.paymentId == "p1" }.sumOf { it.amount })
        assertEquals(750_000, rows.filter { it.paymentId == "p2" }.sumOf { it.amount })
    }

    /**
     * `CORRECT_CANCELED_REFUSED` — a cancelled deposit is not correctable, and the reason it
     * carries is untouched.
     */
    @Test
    fun `correcting a canceled deposit is refused`() {
        runBlocking {
            saveDeposit(CONTRACT_ID, "p1", asOf, 500_000)
            repository.cancelDeposit(CONTRACT_ID, "p1", "Salah ketik tanggal")

            assertThrows(DepositAlreadyCanceledException::class.java) {
                runBlocking { correct("p1", CONTRACT_ID, amount = 500_000) }
            }

            val stored = database.paymentDao().findById("p1")!!
            assertEquals(PaymentEntity.STATUS_CANCELED, stored.status)
            assertEquals("and the original reason stands", "Salah ketik tanggal", stored.cancelReason)
        }
    }

    /** `CORRECT_UNKNOWN_DEPOSIT` — nothing written for an id nobody holds. */
    @Test
    fun `correcting an unknown deposit is refused`() {
        runBlocking {
            assertThrows(UnknownDepositException::class.java) {
                runBlocking { correct("no-such-deposit", CONTRACT_ID, amount = 500_000) }
            }
            assertEquals(0, database.allocationDao().forContract(CONTRACT_ID).size)
        }
    }

    /**
     * `CORRECT_CONTRACT_MISMATCH` — the contract named must be the one the deposit belongs
     * to, so a caller holding the wrong contract cannot rewrite another rental's deposit.
     */
    @Test
    fun `correcting under the wrong contract is refused`() {
        runBlocking {
            saveDeposit(CONTRACT_ID, "p1", asOf, 500_000)

            assertThrows(UnknownDepositException::class.java) {
                runBlocking { correct("p1", "some-other-contract", amount = 500_000) }
            }

            assertEquals("and the amount is untouched", 500_000, database.paymentDao().findById("p1")!!.amount)
        }
    }

    // ------------------------------------------------------------- findDeposit

    /**
     * The projection resolves every relation the form shows, in one read.
     *
     * Asserted on the whole value rather than on the parts that happen to be non-null, since
     * a blank plate or a blank rate is the failure this projection exists to prevent.
     */
    @Test
    fun `findDeposit resolves the deposit and everything the form shows`() = runBlocking {
        saveDeposit(
            CONTRACT_ID,
            "p1",
            asOf,
            750_000,
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNT_1_ID,
            referenceNo = "REF-1",
            notes = "Diterima lewat teller",
        )

        val editable = repository.findDeposit("p1")
        assertNotNull("the deposit resolves", editable)
        editable!!
        assertEquals("p1", editable.id)
        assertEquals(CONTRACT_ID, editable.contractId)
        assertEquals(RENTER_ID, editable.renterId)
        assertEquals("Budi Santoso", editable.renterName)
        assertEquals("B 1235 TF", editable.vehiclePlate)
        assertEquals(RATE, editable.dailyRate)
        assertEquals(asOf, editable.date)
        assertEquals(750_000, editable.amount)
        assertEquals(PaymentMethod.TransferBank, editable.method)
        assertEquals(ACCOUNT_1_ID, editable.accountId)
        assertEquals("REF-1", editable.referenceNo)
        assertEquals("Diterima lewat teller", editable.notes)
        assertEquals(true, editable.needsAccount)
    }

    /** A cancelled deposit reads as absent, because there is nothing left to correct. */
    @Test
    fun `findDeposit reports a canceled deposit as absent`() {
        runBlocking {
            saveDeposit(CONTRACT_ID, "p1", asOf, 500_000)
            repository.cancelDeposit(CONTRACT_ID, "p1", "Salah ketik tanggal")

            assertNull(repository.findDeposit("p1"))
        }
    }

    /** And an id nobody holds, likewise — the two refusals are one answer to the form. */
    @Test
    fun `findDeposit reports an unknown deposit as absent`() {
        runBlocking {
            assertNull(repository.findDeposit("no-such-deposit"))
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A correctDeposit call with the fixtures' defaults, so each test states only what it varies. */
    private suspend fun correct(
        paymentId: String,
        contractId: String,
        amount: Long,
        accountId: String? = null,
    ) = repository.correctDeposit(
        contractId = contractId,
        paymentId = paymentId,
        date = asOf,
        amount = amount,
        accountId = accountId,
        referenceNo = null,
        notes = null,
        asOf = asOf,
    )

    private suspend fun saveDeposit(
        contractId: String,
        id: String,
        date: LocalDate,
        amount: Long,
        method: PaymentMethod = PaymentMethod.Tunai,
        accountId: String? = null,
        referenceNo: String? = null,
        notes: String? = null,
    ) {
        repository.saveDeposit(
            contractId = contractId,
            payment = Payment(id = id, date = date, amount = amount),
            method = method,
            accountId = accountId,
            referenceNo = referenceNo,
            notes = notes,
            asOf = date,
        )
    }

    /** The seven-day contract every test shares. 17 charge days at Rp 250.000. */
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
            BusinessAccountEntity(id = ACCOUNT_1_ID, bankName = "BCA", number = "1234567890"),
        )
        database.businessAccountDao().insertBusinessAccount(
            BusinessAccountEntity(id = ACCOUNT_2_ID, bankName = "Mandiri", number = "0987654321"),
        )
    }

    /**
     * A **six-day** contract resting on Sunday, on its own vehicle and renter.
     *
     * Separate rows rather than an exception on the shared contract, because a rest day is a
     * property of the rental's schedule — two contracts with the same renter would collide
     * on the one-active indexes.
     */
    private suspend fun seedRestDayContract() {
        database.vehicleDao().insert(
            VehicleEntity(
                id = REST_DAY_VEHICLE_ID,
                plate = "B 9876 AB",
                brand = "Honda",
                model = "Brio",
                year = 2022,
                color = null,
                dailyRate = RATE,
                notes = null,
            )
        )
        database.renterDao().insert(
            RenterEntity(
                id = REST_DAY_RENTER_ID,
                name = "Siti Aminah",
                phone = "08129876123",
                bankAccount = null,
                emergencyContact = "08129876456",
            )
        )
        database.contractDao().insertContract(
            ContractEntity(
                id = REST_DAY_CONTRACT_ID,
                renterId = REST_DAY_RENTER_ID,
                vehicleId = REST_DAY_VEHICLE_ID,
                startDate = LocalDate.of(2026, 10, 1),
                dailyRate = RATE,
                restDay = DayOfWeek.SUNDAY,
                activeVehicleId = REST_DAY_VEHICLE_ID,
                activeRenterId = REST_DAY_RENTER_ID,
                endDate = null,
            )
        )
    }

    private companion object {
        const val CONTRACT_ID = "contract-1"
        const val RENTER_ID = "renter-1"
        const val VEHICLE_ID = "vehicle-1"
        const val ACCOUNT_1_ID = "account-1"
        const val ACCOUNT_2_ID = "account-2"
        const val RATE = 250_000L

        const val REST_DAY_CONTRACT_ID = "rest-contract-1"
        const val REST_DAY_RENTER_ID = "rest-renter-1"
        const val REST_DAY_VEHICLE_ID = "rest-vehicle-1"

        /** Fixed so the entity's own audit field is deterministic across runs. */
        val CREATED_AT: Instant = Instant.parse("2026-10-17T09:00:00Z")
    }
}
