package net.klinik.rental.data.backup

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
import net.klinik.rental.data.db.entity.ServiceExceptionEntity
import net.klinik.rental.data.db.entity.SettingEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
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

/**
 * The export read, against a real database.
 *
 * ## Why a real Room database and not a hand-built `BackupDocument`
 *
 * Because the claims are about **what is in the tables**, not about what a writer does with a
 * list: that all eight readers reach the database at all, that six `Flow.first()` calls and two
 * new `all()` queries see every row, and that the entity's own `init` guards do not reject what
 * a real write produced. A fake DAO could satisfy none of them — and the two DAOs that had no
 * whole-table reader until this story are exactly the ones a fake would have hidden.
 *
 * ## What is proved by mutation here rather than by review
 *
 * The PIN-hash test inserts a `pinHash` row that **nothing in this app writes** — Story 5.3 has
 * not been built. It is here so that the deny-list is proved by the current code rather than by
 * the memory of whoever writes that story.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {

    private lateinit var database: KlinikDatabase
    private lateinit var payments: PaymentRepository
    private lateinit var backups: BackupRepository

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
        backups = BackupRepository(
            vehicleDao = database.vehicleDao(),
            renterDao = database.renterDao(),
            contractDao = database.contractDao(),
            serviceExceptionDao = database.serviceExceptionDao(),
            businessAccountDao = database.businessAccountDao(),
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            settingDao = database.settingDao(),
            clock = CLOCK,
        )
        seed()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * All eight tables reach the file, including the three the acceptance criterion does not
     * name.
     *
     * `service_exception` is the owner's own "hari tanpa tagihan" marks and `allocation` is
     * Story 1.3's per-day ledger; both are their data and neither is regenerable once the device
     * is gone. The criterion's five are a floor.
     */
    @Test
    fun `every table reaches the file`() = runBlocking {
        val json = backups.build().json

        listOf(
            "vehicles",
            "renters",
            "contracts",
            "serviceExceptions",
            "businessAccounts",
            "payments",
            "allocations",
            "settings",
        ).forEach { assertTrue("\"$it\" is missing", json.contains("\"$it\":")) }

        assertTrue("and the seeded vehicle is in there", json.contains(VEHICLE_ID))
        assertTrue("as is the renter", json.contains(RENTER_ID))
        assertTrue("and the contract", json.contains(CONTRACT_ID))
        assertTrue("and the account", json.contains(ACCOUNT_ID))
        assertTrue("and the business name from settings", json.contains("Klinik Net"))
    }

    /** A deposit's allocations are copied as stored — not re-derived by the exporter. */
    @Test
    fun `allocations are exported verbatim`() = runBlocking {
        saveDeposit("p1", 250_000, LocalDate.of(2026, 10, 5))

        val json = backups.build().json

        assertTrue("allocation rows are present", json.contains("\"allocations\": ["))
        // Every allocation carries its payment's id, so the file's rows can be traced back to
        // the deposit that placed them without re-deriving anything.
        assertTrue("and the payment id they belong to", json.contains("\"paymentId\": \"p1\""))
    }

    /**
     * A cancelled deposit is exported with the owner's reason.
     *
     * Story 3.4 kept the row precisely so it could be read later; a file that carried the status
     * without the reason would keep the fact and lose the only part of it that answers "why did
     * Budi's arrears jump on the 17th".
     */
    @Test
    fun `a cancelled deposit keeps its reason in the file`() = runBlocking {
        saveDeposit("p1", 250_000, LocalDate.of(2026, 10, 5))
        payments.cancelDeposit(CONTRACT_ID, "p1", "salah tanggal")

        val json = backups.build().json

        assertTrue(json.contains("\"status\": \"canceled\""))
        assertTrue(json.contains("\"cancelReason\": \"salah tanggal\""))
    }

    /**
     * `pinHash` never reaches the file, and every other setting still does.
     *
     * The row is inserted by this test and written by **nothing** in the app — Story 5.3 has not
     * been built. That is the point: the exclusion is proved now, by the deny-list, rather than
     * remembered later by a different person editing a different file.
     */
    @Test
    fun `the pin hash is excluded while other settings are not`() = runBlocking {
        database.settingDao().putSetting(SettingEntity(key = "pinHash", value = "secret-hash"))
        database.settingDao().putSetting(SettingEntity(key = "recoveryHash", value = "other-hash"))

        val json = backups.build().json

        assertFalse("the PIN hash is absent", json.contains("pinHash"))
        assertFalse("the recovery hash is absent", json.contains("recoveryHash"))
        assertFalse("and neither value leaked", json.contains("secret-hash"))
        assertTrue("while the business name still exports", json.contains("businessName"))
    }

    /**
     * The export changes nothing.
     *
     * The criterion is explicit — *"data di aplikasi tetap utuh; ekspor tidak menghapus apa
     * pun"* — and this is the assertion that would catch a future edit that gave the exporter a
     * write path.
     */
    @Test
    fun `exporting leaves every table unchanged`() = runBlocking {
        saveDeposit("p1", 250_000, LocalDate.of(2026, 10, 5))
        val before = database.allocationDao().all()
        val paymentsBefore = database.paymentDao().observeAllForHistory().first()
        val vehiclesBefore = database.vehicleDao().observeAll().first()

        backups.build()

        assertEquals("allocations", before, database.allocationDao().all())
        assertEquals("payments", paymentsBefore, database.paymentDao().observeAllForHistory().first())
        assertEquals("vehicles", vehiclesBefore, database.vehicleDao().observeAll().first())
    }

    /** The filename carries the export date, so two backups can be told apart. */
    @Test
    fun `the filename carries the export date`() {
        assertEquals(
            "klinik-net-backup-2026-10-19.json",
            backups.filenameFor(LocalDate.of(2026, 10, 19)),
        )
        assertTrue(
            "and two different days are two different names",
            backups.filenameFor(LocalDate.of(2026, 10, 19)) !=
                backups.filenameFor(LocalDate.of(2026, 10, 20)),
        )
    }

    /**
     * A database an owner barely used still produces a complete file.
     *
     * The seed writes the four tables a business has on day one and never touches `payment` or
     * `allocation` — so this is the shape of a real first backup: some tables full, some empty,
     * **none missing**. Story 5.2 has to be able to tell "this backup has no deposits" from "the
     * field is not there", and only one of those is a fact about the owner's business.
     */
    @Test
    fun `an untouched database still produces a complete file`() = runBlocking {
        val json = backups.build().json

        assertTrue("a populated table is written in full", json.contains(VEHICLE_ID))
        assertTrue("an untouched one is empty, not absent", json.contains("\"payments\": []"))
        assertTrue(json.contains("\"allocations\": []"))
        assertTrue(json.contains("\"serviceExceptions\": ["))
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun seed() {
        database.vehicleDao().insert(vehicle())
        database.renterDao().insert(renter())
        database.contractDao().insertContract(contract())
        database.businessAccountDao().insertBusinessAccount(
            BusinessAccountEntity(id = ACCOUNT_ID, bankName = "BCA", number = "1234567890"),
        )
        database.serviceExceptionDao().insertServiceException(
            ServiceExceptionEntity(
                id = "e1",
                contractId = CONTRACT_ID,
                date = LocalDate.of(2026, 10, 7),
                reason = "banjir",
            ),
        )
        database.settingDao().putSetting(SettingEntity(key = "businessName", value = "Klinik Net"))
    }

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

    private fun vehicle() = VehicleEntity(
        id = VEHICLE_ID,
        plate = "B 1235 TF",
        brand = "Toyota",
        model = "Avanza",
        year = 2023,
        color = null,
        dailyRate = 250_000,
        notes = null,
        isServis = false,
    )

    private fun renter() = RenterEntity(
        id = RENTER_ID,
        name = "Budi Santoso",
        phone = "08123456789",
        bankAccount = null,
        emergencyContact = "08129876543",
    )

    private fun contract() = ContractEntity(
        id = CONTRACT_ID,
        renterId = RENTER_ID,
        vehicleId = VEHICLE_ID,
        startDate = LocalDate.of(2026, 10, 1),
        dailyRate = 250_000,
        restDay = null,
        activeVehicleId = VEHICLE_ID,
        activeRenterId = RENTER_ID,
        endDate = null,
        settledThroughDate = null,
    )

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val RENTER_ID = "renter-1"
        const val CONTRACT_ID = "contract-1"
        const val ACCOUNT_ID = "account-1"

        /** AD-8 in force: "today" is injected, never read from the system clock. */
        val CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-10-19T08:00:00Z"),
            ZoneOffset.UTC,
        )
    }
}