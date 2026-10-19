package net.klinik.rental.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.security.SecureRandom
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
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.PinLockRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Payment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Epic 5's closing gate: the two rules that pull in opposite directions, held together.
 *
 * ## What this file is for
 *
 * FR-16 says a restore brings everything back. NFR-5 and AD-7 say the PIN hash never leaves the
 * device. Both are true only if the exclusion is **precisely the lock** — one key too few and every
 * backup carries a screen lock; one key too many and the owner restores a database that quietly
 * lost configuration they had to type again.
 *
 * Neither failure announces itself. That is what this file is for.
 *
 * ## Why it drives the production path rather than two repositories
 *
 * Because a suite that hand-assembles "the file" proves only that two hand-built objects agree.
 * Every test here goes `build()` → **the file's bytes** → `read()` → `apply()`, so a key the
 * writer and the reader spell differently is caught here rather than on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupLockInterplayTest {

    private lateinit var database: KlinikDatabase
    private lateinit var lock: PinLockRepository

    private val clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        lock = PinLockRepository(
            dao = database.settingDao(),
            clock = clock,
            database = database,
            // Cheap iterations so the suite is not a minute of hashing; the floor is asserted by
            // `PinLockRepositoryTest`, which is what makes the discount safe here.
            iterations = 1_000,
        )
        seedFullDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------------- round trip

    /**
     * `VERIFY_ROUNDTRIP` — every **business** table survives an export and restore of the file.
     *
     * Byte-for-byte on the entity's own `equals`, which for a data class is every field. A count
     * would pass with every value wrong, and this is the assertion that says the owner got their
     * records back.
     */
    @Test
    fun `every business table survives the round trip intact`() = runBlocking {
        val source = backupsOver(database)
        val file = source.build().json

        val restored = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        try {
            restorerOver(restored).apply(restorerOver(restored).parse(file))

            assertEquals(
                "vehicles",
                database.vehicleDao().observeAll().first(),
                restored.vehicleDao().observeAll().first(),
            )
            assertEquals(
                "renters",
                database.renterDao().observeAll().first(),
                restored.renterDao().observeAll().first(),
            )
            assertEquals(
                "contracts",
                database.contractDao().observeAll().first(),
                restored.contractDao().observeAll().first(),
            )
            assertEquals(
                "service exceptions",
                database.serviceExceptionDao().observeAll().first(),
                restored.serviceExceptionDao().observeAll().first(),
            )
            assertEquals(
                "business accounts",
                database.businessAccountDao().observeAll().first(),
                restored.businessAccountDao().observeAll().first(),
            )
            assertEquals(
                "payments",
                database.paymentDao().observeAllForHistory().first(),
                restored.paymentDao().observeAllForHistory().first(),
            )
            assertEquals(
                "allocations",
                database.allocationDao().all(),
                restored.allocationDao().all(),
            )
        } finally {
            restored.close()
        }
    }

    // ------------------------------------------------------------------ the leak

    /**
     * `VERIFY_NO_LEAK` — the file's **raw text** carries no lock key and no lock value.
     *
     * Read as bytes rather than as parsed settings, because the failure this guards is a hash
     * sitting under an *unexpected* key — and a check that walks the parsed `settings` array by
     * name would step straight over it. The only assertion that survives a refactor of the
     * writer's field names is "these strings are not in the file".
     */
    @Test
    fun `the exported file carries no lock key and no lock value`() = runBlocking {
        val code = lock.newRecoveryCode()
        lock.enable("1234", code)

        val file = backupsOver(database).build().json

        // The keys. Every one of the nine, asserted by name so a renamed setting cannot slip
        // past a check that only looked for the two original hashes.
        listOf(
            "pinHash",
            "recoveryHash",
            "pinSalt",
            "recoverySalt",
            "pinEnabled",
            "pinAttempts",
            "pinLockoutUntil",
            "autoLockMinutes",
            "pinBackgroundedAt",
        ).forEach { key ->
            assertFalse("\"$key\" must not be in the file", file.contains("\"$key\""))
        }

        // And the **values** — the real test. A hash written under a different key still leaks
        // even though its name is absent, so the strings themselves are what must not appear.
        val storedValues = database.settingDao().all()
            .filter { it.key.startsWith("pin") || it.key.startsWith("recovery") }
            .map { it.value }
        assertTrue("the fixture must actually have lock rows", storedValues.isNotEmpty())
        storedValues.forEach { value ->
            assertFalse(
                "a lock value leaked into the file: ${value.take(12)}…",
                file.contains(value),
            )
        }
        assertFalse("nor the PIN itself", file.contains("\"1234\""))
    }

    /**
     * The `NEVER_EXPORTED` list is **exactly** the set of keys a lock writes.
     *
     * Both halves of this story are pinned here rather than independently: a key added to the
     * list that is not a lock key would delete configuration from every backup, and a lock key
     * missing from it would leak. Asserting the set **equals** the lock's own key set is what keeps
     * them from drifting apart quietly — and this is the assertion that fails the moment a future
     * story adds a lock key without remembering the list.
     */
    @Test
    fun `the deny-list is exactly the set of keys a lock writes`() {
        val lockKeys = setOf(
            PinLockRepository.KEY_ENABLED,
            PinLockRepository.KEY_PIN_HASH,
            PinLockRepository.KEY_PIN_SALT,
            PinLockRepository.KEY_RECOVERY_HASH,
            PinLockRepository.KEY_RECOVERY_SALT,
            PinLockRepository.KEY_ATTEMPTS,
            PinLockRepository.KEY_LOCKOUT_UNTIL,
            PinLockRepository.KEY_AUTO_LOCK_MINUTES,
            PinLockRepository.KEY_BACKGROUNDED_AT,
        )

        assertEquals(
            "NEVER_EXPORTED must be exactly the lock's keys — no more, and no fewer",
            lockKeys,
            BackupJson.NEVER_EXPORTED,
        )
    }

    // ------------------------------------------------- the other half: no over-filter

    /**
     * `VERIFY_NO_OVERFILTER` — every **non-lock** setting survives.
     *
     * The quieter failure of the two. A filter that removed `businessName` alongside the PIN keys
     * would satisfy every leak assertion while costing the owner their business name on every
     * restore, and the app would restore and run and nobody would notice for weeks.
     */
    @Test
    fun `every non-lock setting survives the file`() = runBlocking {
        val file = backupsOver(database).build().json

        listOf("businessName", "cutoffDay").forEach { key ->
            assertTrue("\"$key\" must be in the file", file.contains("\"$key\""))
        }
        assertTrue("and its value", file.contains("Klinik Net"))
        assertTrue("as is the last export date", file.contains(BackupRepository.KEY_LAST_BACKUP_DATE))
    }

    // ---------------------------------------------------------------- unlocked

    /**
     * `VERIFY_UNLOCKED` — the restored app is **not locked**.
     *
     * A positive assertion, not the absence of a hash. `assertNull(settings["pinHash"])` and
     * `assertFalse(isEnabled())` are different claims, and only the second is the one the owner's
     * morning depends on: a restore that left a stale `pinEnabled = true` with no hash satisfies
     * the first and bricks the second.
     */
    @Test
    fun `the restored app is not locked`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())
        assertTrue("the fixture must start locked", lock.isEnabled())

        val file = backupsOver(database).build().json

        val restored = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        try {
            val restoredLock = PinLockRepository(
                dao = restored.settingDao(),
                clock = clock,
                database = restored,
                iterations = 1_000,
            )
            restorerOver(restored).apply(restorerOver(restored).parse(file))

            assertFalse(
                "the owner must get in — a lock with no hash is a lock nobody can open",
                restoredLock.isEnabled(),
            )
        } finally {
            restored.close()
        }
    }

    /**
     * `VERIFY_NEWLOCK` — a fresh lock on the restored database works, and issues a **new** code.
     *
     * The AC asks for this and it is the assertion that catches a restore leaving a stale hash
     * whose salt no longer matches: the new PIN would then be verified against the old salt and
     * silently refuse.
     */
    @Test
    fun `a fresh lock can be built on the restored database with a new recovery code`() = runBlocking {
        val originalCode = lock.newRecoveryCode()
        lock.enable("1234", originalCode)
        val file = backupsOver(database).build().json

        val restored = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        try {
            val restoredLock = PinLockRepository(
                dao = restored.settingDao(),
                clock = clock,
                database = restored,
                iterations = 1_000,
            )
            restorerOver(restored).apply(restorerOver(restored).parse(file))

            val newCode = restoredLock.newRecoveryCode()
            restoredLock.enable("5678", newCode)

            assertTrue("the lock is on", restoredLock.isEnabled())
            assertTrue("and the new PIN opens it", restoredLock.verifyPin("5678"))
            assertTrue("with the new code", restoredLock.verifyRecoveryCode(newCode))
            assertNotEquals(
                "and the code is not the original — a reset that kept it would be a second key",
                originalCode,
                newCode,
            )
        } finally {
            restored.close()
        }
    }

    // ----------------------------------------------------------------- auto-lock

    /**
     * `VERIFY_AUTOLOCK` — the lock survives a **restart**, because the timestamp is in the
     * database.
     *
     * The Activity's `onStop`/`onStart` pair cannot be driven from a JVM test, and it does not
     * need to be: what has to hold is that the timestamp is persisted and that `shouldLock`
     * reads it. Those two are the whole mechanism, and a counter held in memory would fail this
     * while passing every test that only checked the elapsed time in one process.
     */
    @Test
    fun `the auto-lock survives a restart`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode(), autoLockMinutes = 5)

        val later = Clock.fixed(
            Instant.parse("2026-10-19T08:10:00Z"),
            ZoneOffset.UTC,
        )
        val restartLock = PinLockRepository(
            dao = database.settingDao(),
            clock = later,
            database = database,
            iterations = 1_000,
        )
        restartLock.recordBackgrounded()

        // A *third* instance stands in for the restarted process: same database, later clock.
        val afterRestart = PinLockRepository(
            dao = database.settingDao(),
            clock = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC),
            database = database,
            iterations = 1_000,
        )

        assertTrue(
            "ten minutes after backgrounding, the app locks again",
            afterRestart.shouldLock(coldStart = false),
        )
        assertTrue(
            "and so does a cold start, because the process may have died without recording one",
            afterRestart.shouldLock(coldStart = true),
        )
    }

    // ---------------------------------------------------------------- versions

    /** `VERIFY_OLD_FILE` / `VERIFY_NEW_FILE` — a mismatched version is refused, both ways. */
    @Test
    fun `a file from another schema version is refused naming both versions`() = runBlocking {
        val restorer = restorerOver(database)
        val file = backupsOver(database).build().json
            .replace("\"schemaVersion\": $SCHEMA", "\"schemaVersion\": 99")

        val thrown = runCatching { restorer.parse(file) }.exceptionOrNull()

        assertTrue("it must be refused", thrown is RestoreRejected)
        assertEquals(BackupJsonReader.REASON_VERSION, (thrown as RestoreRejected).reason)
        assertEquals("and the file's own version is carried for the message", 99, thrown.fileVersion)
    }

    /**
     * The older direction is refused too, and — unlike the newer one — **the lock's own state
     * survives the refusal untouched**.
     *
     * The AC says an older file is restored "dengan migrasi bila perlu". No older file exists,
     * because Story 5.1 is the first thing in this app that ever wrote one. So what this pins is
     * the *interim* answer and, more importantly, that a refusal is **inert**: the owner's database
     * is exactly as it was, because the version is checked before any row is read. A reader that
     * parsed first and compared second would have cleared the database before discovering the
     * file was unusable — which is the one failure NFR-3 names.
     */
    @Test
    fun `an older file is refused without touching the existing data`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())
        val vehiclesBefore = database.vehicleDao().observeAll().first()
        val stillLockedBefore = lock.isEnabled()

        val restorer = restorerOver(database)
        val file = backupsOver(database).build().json
            .replace("\"schemaVersion\": $SCHEMA", "\"schemaVersion\": 7")

        val thrown = runCatching { restorer.parse(file) }.exceptionOrNull()

        assertTrue(
            "refused, because no older file exists yet — Story 5.1 wrote the first one, at v8",
            thrown is RestoreRejected,
        )
        assertEquals(7, (thrown as RestoreRejected).fileVersion)
        assertEquals(
            "and the refusal changed nothing",
            vehiclesBefore,
            database.vehicleDao().observeAll().first(),
        )
        assertTrue(
            "the owner's lock is exactly as it was",
            stillLockedBefore && lock.isEnabled(),
        )
        assertTrue(
            "and it still opens with the PIN it was set to",
            lock.verifyPin("1234"),
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun backupsOver(db: KlinikDatabase) = BackupRepository(
        vehicleDao = db.vehicleDao(),
        renterDao = db.renterDao(),
        contractDao = db.contractDao(),
        serviceExceptionDao = db.serviceExceptionDao(),
        businessAccountDao = db.businessAccountDao(),
        paymentDao = db.paymentDao(),
        allocationDao = db.allocationDao(),
        settingDao = db.settingDao(),
        clock = clock,
    )

    private fun restorerOver(db: KlinikDatabase) = BackupRestorer(
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
     * A business with everything in it: two contracts, deposits on one, a cancellation, an
     * exception, an account, and the settings the export must carry.
     *
     * A deposit with `PaymentRepository` rather than a raw row insert, because the allocations
     * are what the round trip has to reproduce and a hand-written allocation row would not be the
     * one a real deposit writes.
     */
    private suspend fun seedFullDatabase() {
        database.vehicleDao().insert(
            VehicleEntity(
                id = "v1",
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
                id = "r1",
                name = "Budi Santoso",
                phone = "08123456789",
                bankAccount = null,
                emergencyContact = "08129876543",
            ),
        )
        database.contractDao().insertContract(
            ContractEntity(
                id = "c1",
                renterId = "r1",
                vehicleId = "v1",
                startDate = LocalDate.of(2026, 10, 1),
                dailyRate = 250_000,
                restDay = null,
                activeVehicleId = "v1",
                activeRenterId = "r1",
                endDate = null,
                settledThroughDate = null,
            ),
        )
        database.businessAccountDao().insertBusinessAccount(
            BusinessAccountEntity(id = "a1", bankName = "BCA", number = "1234567890"),
        )
        database.serviceExceptionDao().insertServiceException(
            ServiceExceptionEntity(
                id = "e1",
                contractId = "c1",
                date = LocalDate.of(2026, 10, 7),
                reason = "banjir",
            ),
        )
        database.settingDao().putSetting(SettingEntity(key = "businessName", value = "Klinik Net"))
        database.settingDao().putSetting(SettingEntity(key = "cutoffDay", value = "25"))

        val payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            database = database,
        )
        payments.saveDeposit(
            contractId = "c1",
            payment = Payment(id = "p1", date = LocalDate.of(2026, 10, 5), amount = 500_000),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = LocalDate.of(2026, 10, 19),
        )
        payments.cancelDeposit("c1", "p1", "salah tanggal")
    }

    private companion object {
        const val SCHEMA = net.klinik.rental.data.db.SCHEMA_VERSION
    }
}