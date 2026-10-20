package net.klinik.rental.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The restore's write half, against a real database.
 *
 * ## What only a real database can prove
 *
 * Every claim here is about constraints: that the insert order satisfies all five foreign keys,
 * that `clearAllTables()` inside `withTransaction` does not throw, that the entity guards accept
 * what the reader produced, and that a restored allocation still sums to its payment's amount.
 * A fake DAO would satisfy none of them — it would happily accept rows in any order and never
 * tell us the ordering was wrong.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestorerTest {

    private lateinit var database: KlinikDatabase
    private lateinit var restorer: BackupRestorer
    private lateinit var payments: PaymentRepository

    private val asOf = LocalDate.of(2026, 10, 17)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        restorer = restorerFor(database)
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

    /**
     * The whole file goes in: all eight tables, with the foreign keys satisfied by the order.
     *
     * A restore that threw a `SQLiteConstraintException` here would mean the order was wrong —
     * which is the one thing this test exists to catch, because the ordering is derived from the
     * schema rather than from anything the compiler checks.
     */
    @Test
    fun `a whole file restores into an empty database`() = runBlocking {
        restorer.apply(restorer.parse(FULL_FILE, AS_OF))

        assertEquals("vehicle", 1, database.vehicleDao().observeAll().let { read(it) }.size)
        assertEquals(1, read(database.renterDao().observeAll()).size)
        assertEquals(1, read(database.contractDao().observeAll()).size)
        assertEquals(1, read(database.serviceExceptionDao().observeAll()).size)
        assertEquals(1, read(database.businessAccountDao().observeAll()).size)
        assertEquals(1, read(database.paymentDao().observeAllForHistory()).size)
        assertEquals(1, database.allocationDao().all().size)
        assertEquals(1, database.settingDao().all().size)
    }

    /**
     * The restore **replaces** what was there, rather than merging into it.
     *
     * The criterion's "pemulihan adalah satu-satunya operasi yang menimpa" — a merge would leave
     * rows from both databases, and the totals would be a number nobody's business ever had.
     */
    @Test
    fun `a restore replaces the existing data rather than merging`() = runBlocking {
        seedDifferentBusiness()
        assertEquals(
            "the old business is there before the restore",
            "B 9999 ZZ",
            read(database.vehicleDao().observeAll()).single().plate,
        )

        restorer.apply(restorer.parse(FULL_FILE, AS_OF))

        val vehicles = read(database.vehicleDao().observeAll())
        assertEquals("only the file's vehicle survives", 1, vehicles.size)
        assertEquals("B 1235 TF", vehicles.first().plate)
        assertNull(
            "and the old renter is gone",
            database.renterDao().findById("old-renter"),
        )
    }

    /**
     * **`Σ allocation == amount` still holds after a restore.**
     *
     * Story 1.3's unconditional rule, and the reason allocations are copied rather than
     * re-derived: a restore that recomputed them against today's cutoff could produce a set that
     * sums correctly while placing the money on different days — and this assertion is the one
     * that would not catch that. The round-trip test is what catches *that*; this one catches a
     * restore that dropped a row.
     */
    @Test
    fun `a restored payment's allocations still sum to its amount`() = runBlocking {
        restorer.apply(restorer.parse(FULL_FILE, AS_OF))

        val allocations = database.allocationDao().forContract(CONTRACT_ID)
        val total = allocations.sumOf { it.amount }
        val amount = read(database.paymentDao().observeAllForHistory()).first().amount

        assertEquals("every rupiah of the deposit is still on a day", amount, total)
    }

    /**
     * A file carrying a `pinHash` leaves no `pinHash` in the database.
     *
     * The reader filters it, and this proves the filter reaches storage rather than only the
     * in-memory value the preview read. Story 5.3 will add the row; this is what stops it
     * arriving through a backup.
     */
    @Test
    fun `a pin hash in the file never reaches the database`() = runBlocking {
        val json = FULL_FILE.replace(
            """"settings": [{"key":"businessName","value":"Klinik Net"}]""",
            """"settings": [{"key":"businessName","value":"Klinik Net"},
                            {"key":"pinHash","value":"secret"},
                            {"key":"recoveryHash","value":"other"}]""",
        )

        restorer.apply(restorer.parse(json, AS_OF))

        val settings = database.settingDao().all().map { it.key }
        assertEquals("only the business name is stored", listOf("businessName"), settings)
        assertNull("and no lock-screen hash", database.settingDao().findValue("pinHash"))
    }

    /**
     * An **empty** file empties the database, rather than being refused.
     *
     * A fresh install's backup is eight empty arrays and restoring it is a legitimate thing to
     * do — "put this device back to how it was before I typed anything". The refusal path is the
     * *missing* key, which the reader test covers; the two must not be confused.
     */
    @Test
    fun `an empty file empties the database`() = runBlocking {
        seedDifferentBusiness()

        restorer.apply(restorer.parse(EMPTY_FILE, AS_OF))

        assertTrue(read(database.vehicleDao().observeAll()).isEmpty())
        assertTrue(read(database.renterDao().observeAll()).isEmpty())
        assertTrue(database.settingDao().all().isEmpty())
    }

    /**
     * A **cancelled deposit** arrives with its status and reason intact.
     *
     * Story 3.4 kept the row so the audit trail stays readable; a restore that dropped the
     * reason would keep the fact and lose the only part of it that answers "why did Budi's
     * arrears jump on the 17th".
     */
    @Test
    fun `a cancelled deposit arrives with its reason`() = runBlocking {
        val json = FULL_FILE.replace(
            """"status":"confirmed","cancelReason":null""",
            """"status":"canceled","cancelReason":"salah tanggal"""",
        )

        restorer.apply(restorer.parse(json, AS_OF))

        val stored = read(database.paymentDao().observeAllForHistory()).first()
        assertEquals("canceled", stored.status)
        assertEquals("salah tanggal", stored.cancelReason)
    }

    /**
     * A file whose contract row violates the entity's own rule is refused **before** anything is
     * deleted.
     *
     * This is the property that matters most: the version gate and the row guards run during
     * [BackupRestorer.parse], which is before [BackupRestorer.apply] touches the database. An
     * owner whose file is corrupt keeps the data they have.
     */
    @Test
    fun `a corrupt file leaves the existing data untouched`() = runBlocking {
        seedDifferentBusiness()
        val before = read(database.vehicleDao().observeAll()).map { it.id }.sorted()

        val corrupt = FULL_FILE.replace(""""activeVehicleId":"v1"""", """"activeVehicleId":"v9"""")
        val rejected = runCatching { restorer.parse(corrupt, AS_OF) }

        assertTrue("the file is refused", rejected.isFailure)
        assertEquals(
            "and the owner still has what they had",
            before,
            read(database.vehicleDao().observeAll()).map { it.id }.sorted(),
        )
    }

    /**
     * An ended contract restores with its active columns null — the other half of the entity's
     * guard, which a file written by the app always satisfies and a hand-edited one may not.
     */
    @Test
    fun `an ended contract restores with null active columns`() = runBlocking {
        val json = FULL_FILE.replace(
            """"endDate":null,"settledThroughDate":null""",
            """"endDate":"2026-10-20","settledThroughDate":null""",
        ).replace(
            """"activeVehicleId":"v1","activeRenterId":"r1"""",
            """"activeVehicleId":null,"activeRenterId":null""",
        )

        restorer.apply(restorer.parse(json, AS_OF))

        val contract = read(database.contractDao().observeAll()).first()
        assertNull(contract.activeVehicleId)
        assertNull(contract.activeRenterId)
        assertEquals(LocalDate.of(2026, 10, 20), contract.endDate)
    }

/**
     * A restore that fails **partway** leaves the old data intact.
     *
     * The KDoc claims one transaction makes "the old data" and "the new data" the only two
     * outcomes. This is the test that makes that a fact rather than an intention: the file's
     * second vehicle duplicates the first's plate, which the `UNIQUE` index refuses *after* the
     * first insert has already happened and after `clearAllTables()` has already emptied the
     * database. Without the transaction the owner would be left with an empty app holding one
     * vehicle — the "half-restored database" that reads as data loss.
     *
     * This is also the test that would fail if `withTransaction` were ever removed as
     * "unnecessary", which is exactly the kind of simplification a future reader might make.
     */
    @Test
    fun `a restore that fails partway rolls back to the old data`() = runBlocking {
        seedDifferentBusiness()

        val duplicatePlate = withDuplicatePlate()
        val restore = restorer.parse(duplicatePlate, AS_OF)

        val failure = runCatching { restorer.apply(restore) }

        assertTrue("the duplicate plate is refused", failure.isFailure)
        assertEquals(
            "and the old business is still exactly as it was",
            "B 9999 ZZ",
            read(database.vehicleDao().observeAll()).single().plate,
        )
        assertEquals(
            "the old renter too — the wipe rolled back with the failed insert",
            "Siti Aminah",
            read(database.renterDao().observeAll()).single().name,
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun restorerFor(db: KlinikDatabase) = BackupRestorer(
        database = db,
        vehicleDao = db.vehicleDao(),
        renterDao = db.renterDao(),
        contractDao = db.contractDao(),
        serviceExceptionDao = db.serviceExceptionDao(),
        businessAccountDao = db.businessAccountDao(),
        paymentDao = db.paymentDao(),
        allocationDao = db.allocationDao(),
        settingDao = db.settingDao(),
    )

    /**
     * One emission of a Room flow.
     *
     * A helper because every assertion here needs the same thing — the list as stored — and a
     * `first()` at each call site would put the same import in eight places.
     */
    private suspend fun <T> read(flow: Flow<T>): T = flow.first()

    /**
     * [FULL_FILE] with a **second** vehicle whose plate duplicates the first.
     *
     * Built by inserting a row rather than by string-replacing a formatted block: a multi-line
     * `replace` on the indented literal silently matches nothing, and a fixture that changes
     * nothing makes the test assert the opposite of what it claims — which is exactly what
     * happened the first time this test was written. The assertion below is the guard.
     */
    private fun withDuplicatePlate(): String {
        val json = FULL_FILE.replace(
            """"vehicles": [""",
            """"vehicles": [
                {"id":"v2","plate":"B 1235 TF","brand":"Honda","model":"Beat","year":2024,
                 "color":null,"dailyRate":100000,"notes":null,"isServis":false},""",
        )
        assertTrue(
            "the fixture must actually add the duplicate — a no-op replace would make this " +
                "test pass by restoring a perfectly valid file",
            json != FULL_FILE && json.contains("\"id\":\"v2\""),
        )
        return json
    }

    /** A business the restore must erase: a different vehicle, renter and contract. */
    private suspend fun seedDifferentBusiness() {
        database.vehicleDao().insert(
            VehicleEntity(
                id = "old-vehicle",
                plate = "B 9999 ZZ",
                brand = "Honda",
                model = "Beat",
                year = 2024,
                color = null,
                dailyRate = 100_000,
                notes = null,
                isServis = false,
            ),
        )
        database.renterDao().insert(
            RenterEntity(
                id = "old-renter",
                name = "Siti Aminah",
                phone = "081200000000",
                bankAccount = null,
                emergencyContact = "081200000001",
            ),
        )
        database.contractDao().insertContract(
            ContractEntity(
                id = "old-contract",
                renterId = "old-renter",
                vehicleId = "old-vehicle",
                startDate = LocalDate.of(2026, 1, 1),
                dailyRate = 100_000,
                restDay = null,
                activeVehicleId = "old-vehicle",
                activeRenterId = "old-renter",
                endDate = null,
                settledThroughDate = null,
            ),
        )
        database.settingDao().putSetting(SettingEntity(key = "businessName", value = "Old Name"))
    }

    private companion object {
        const val CONTRACT_ID = "c1"

    /** The day the reader compares a contract's dates against; after every fixture date. */
    val AS_OF: LocalDate = LocalDate.of(2026, 10, 31)

        /** One row in every table, with every foreign key satisfied. */
        val FULL_FILE = """
            {
              "schemaVersion": 8,
              "vehicles": [
                {"id":"v1","plate":"B 1235 TF","brand":"Toyota","model":"Avanza","year":2023,
                 "color":null,"dailyRate":250000,"notes":null,"isServis":false}
              ],
              "renters": [
                {"id":"r1","name":"Budi Santoso","phone":"08123456789","bankAccount":null,
                 "emergencyContact":"08129876543"}
              ],
              "contracts": [
                {"id":"c1","renterId":"r1","vehicleId":"v1","startDate":"2026-10-01",
                 "dailyRate":250000,"restDay":null,"activeVehicleId":"v1","activeRenterId":"r1",
                 "endDate":null,"settledThroughDate":null}
              ],
              "serviceExceptions": [
                {"id":"e1","contractId":"c1","date":"2026-10-07","reason":"banjir"}
              ],
              "businessAccounts": [
                {"id":"a1","bankName":"BCA","number":"1234567890"}
              ],
              "payments": [
                {"id":"p1","contractId":"c1","accountId":null,"date":"2026-10-05","amount":250000,
                 "method":"tunai","referenceNo":null,"notes":null,
                 "createdAt":"2026-10-05T09:00:00Z","status":"confirmed","cancelReason":null}
              ],
              "allocations": [
                {"id":"al1","paymentId":"p1","contractId":"c1","chargeDate":"2026-10-05",
                 "amount":250000}
              ],
              "settings": [{"key":"businessName","value":"Klinik Net"}]
            }
        """.trimIndent()

        val EMPTY_FILE = """
            {
              "schemaVersion": 8,
              "vehicles": [], "renters": [], "contracts": [], "serviceExceptions": [],
              "businessAccounts": [], "payments": [], "allocations": [], "settings": []
            }
        """.trimIndent()
    }
}