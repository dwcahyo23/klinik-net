package net.klinik.rental.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The lock's storage and its crypto, against a real database.
 *
 * ## Why the iteration count is low here and high in production
 *
 * Every test below that sets a PIN hashes twice (PIN + recovery code) and every verification
 * hashes once. At the production 120,000 iterations that is roughly a second per test on a build
 * machine, for a suite that runs twenty of them. So the tests pass a low count and
 * `the production iteration count clears the documented floor` asserts the **default** — which is
 * the only thing that makes the discount safe: lowering the default to speed up a test fails a
 * test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PinLockRepositoryTest {

    private lateinit var database: KlinikDatabase
    private lateinit var lock: PinLockRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        lock = lockAt(FIXED_NOW)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** A repository at [now], with a cheap iteration count — see the class KDoc. */
    private fun lockAt(now: Instant, random: SecureRandom = SecureRandom()) = PinLockRepository(
        dao = database.settingDao(),
        clock = Clock.fixed(now, ZoneOffset.UTC),
        database = database,
        iterations = 1_000,
        random = random,
    )

    // ------------------------------------------------------------------ enabling

    /**
     * Enabling returns a recovery code of the PRD's length, and stores **no readable copy**.
     *
     * Both halves matter. The length is what the entropy argument rests on; the absence is NFR-5.
     * A code stored in plaintext to be re-shown later would put the escape hatch in the same
     * database the lock protects.
     */
    @Test
    fun `enabling returns a 20 character code and stores no readable copy`() = runBlocking {
        val code = lock.newRecoveryCode()
        lock.enable("1234", code)

        assertEquals(PinLockRepository.RECOVERY_LENGTH, code.length)
        assertTrue(
            "the alphabet is the one the entropy figure assumes",
            code.all { it in PinLockRepository.RECOVERY_ALPHABET },
        )
        assertTrue("the lock is on", lock.isEnabled())

        val stored = database.settingDao().all().map { it.value }
        assertFalse("the code itself is nowhere", stored.any { it == code })
        assertFalse("nor is the PIN", stored.any { it == "1234" })
    }

    /**
     * Generating a code writes **nothing** — the cancel path.
     *
     * This is the property the split signature exists for. A single `enable()` that generated and
     * wrote would leave a live lock behind when the owner dismissed the code without confirming,
     * and rolling it back with [disable] would destroy an existing PIN when the owner was
     * *changing* one.
     */
    @Test
    fun `generating a recovery code writes nothing`() = runBlocking {
        val code = lock.newRecoveryCode()

        assertEquals(PinLockRepository.RECOVERY_LENGTH, code.length)
        assertFalse("the lock is still off", lock.isEnabled())
        assertTrue(
            "and the settings table is untouched",
            database.settingDao().all().isEmpty(),
        )
    }

    /** The PIN and the code are hashed, not stored — and their salts are distinct. */
    @Test
    fun `the pin and the code are hashed with different salts`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())

        val pinHash = database.settingDao().findValue(PinLockRepository.KEY_PIN_HASH)
        val pinSalt = database.settingDao().findValue(PinLockRepository.KEY_PIN_SALT)
        val codeHash = database.settingDao().findValue(PinLockRepository.KEY_RECOVERY_HASH)
        val codeSalt = database.settingDao().findValue(PinLockRepository.KEY_RECOVERY_SALT)

        assertNotNull(pinHash)
        assertNotNull(pinSalt)
        assertNotNull(codeHash)
        assertNotNull(codeSalt)
        assertNotEquals("one salt serving both would let one table serve both", pinSalt, codeSalt)
        // 256 bits of PBKDF2 output is 64 hex characters; anything shorter is a truncated hash.
        assertEquals(64, pinHash!!.length)
        assertEquals(64, codeHash!!.length)
    }

    /** Two locks on two databases produce different hashes for the same PIN — the salt is doing work. */
    @Test
    fun `the same pin hashes differently on two installations`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())
        val first = database.settingDao().findValue(PinLockRepository.KEY_PIN_HASH)

        // A second database with its own salt.
        val other = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        try {
            val otherLock = PinLockRepository(
                dao = other.settingDao(),
                clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
                database = other,
                iterations = 1_000,
            )
            otherLock.enable("1234", otherLock.newRecoveryCode())
            assertNotEquals(
                "a shared hash would mean one rainbow table serves every device",
                first,
                other.settingDao().findValue(PinLockRepository.KEY_PIN_HASH),
            )
        } finally {
            other.close()
        }
    }

    /** A PIN that is not four digits is refused at the door. */
    @Test
    fun `a malformed pin is refused`() {
        listOf("", "123", "12345", "12a4", "12 4").forEach { candidate ->
            assertFalse("\"$candidate\" is not a PIN", PinLockRepository.isValidPin(candidate))
            val thrown = runCatching { runBlocking { lock.enable(candidate, lock.newRecoveryCode()) } }
            assertTrue("\"$candidate\" must be refused", thrown.isFailure)
        }
        assertTrue("1234 is", PinLockRepository.isValidPin("1234"))
    }

    // ------------------------------------------------------------------ verifying

    @Test
    fun `the right pin verifies and the wrong one does not`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())

        assertTrue(lock.verifyPin("1234"))
        assertFalse(lock.verifyPin("4321"))
    }

    /**
     * Five consecutive failures lock input, and the sixth is refused **without comparing**.
     *
     * The sixth is the interesting one: a lockout that still hashed would spend a second per tap
     * on a refusal the owner cannot act on, and — worse — would let a correct PIN be rejected for
     * a reason the screen has to explain anyway.
     */
    @Test
    fun `five failures lock input and the deadline is set`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())

        repeat(PinLockRepository.MAX_ATTEMPTS) { assertFalse(lock.verifyPin("0000")) }

        assertEquals(PinLockRepository.MAX_ATTEMPTS, lock.failedAttempts())
        assertEquals(
            "the deadline is now plus the lockout window",
            FIXED_NOW.plus(PinLockRepository.LOCKOUT_DURATION),
            lock.lockedOutUntil(),
        )
        assertFalse("even the right PIN is refused while locked out", lock.verifyPin("1234"))
    }

    /**
     * A successful unlock clears the counter.
     *
     * Without this, four wrong taps on Monday and one on Friday would lock the owner out — the
     * count has to be *consecutive*, which is what the AC says and what this asserts.
     */
    @Test
    fun `a success clears the failure count`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())

        repeat(PinLockRepository.MAX_ATTEMPTS - 1) { assertFalse(lock.verifyPin("0000")) }
        assertEquals(PinLockRepository.MAX_ATTEMPTS - 1, lock.failedAttempts())

        assertTrue(lock.verifyPin("1234"))
        assertEquals("the counter resets", 0, lock.failedAttempts())
        assertNull("and so does the deadline", lock.lockedOutUntil())
    }

    /**
     * The lockout **expires**: after the window, the right PIN works again.
     *
     * A deadline that never lifted would be a permanent lock with no way in but the recovery code,
     * which is not what "hitung mundur" means.
     */
    @Test
    fun `the lockout lifts when the window passes`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())
        repeat(PinLockRepository.MAX_ATTEMPTS) { lock.verifyPin("0000") }

        val after = lockAt(FIXED_NOW.plus(PinLockRepository.LOCKOUT_DURATION).plusSeconds(1))

        assertNull("the deadline has passed", after.lockedOutUntil())
        assertTrue("and the right PIN is accepted again", after.verifyPin("1234"))
    }

    /**
     * The attempt count survives a **new repository over the same database** — the process-death
     * case, which is the whole reason the counter is persisted rather than held in memory.
     */
    @Test
    fun `the failure count survives a restart`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())
        repeat(3) { lock.verifyPin("0000") }

        // A fresh instance over the same database is what a cold start builds.
        val restarted = lockAt(FIXED_NOW)

        assertEquals(3, restarted.failedAttempts())
    }

    // ------------------------------------------------------------------ recovery

    @Test
    fun `the recovery code verifies and a wrong one does not`() = runBlocking {
        val code = lock.newRecoveryCode()
        lock.enable("1234", code)

        assertTrue(lock.verifyRecoveryCode(code))
        assertFalse(lock.verifyRecoveryCode("WRONGCODE0000000000"))
    }

    /**
     * Resetting with the code issues a **new** code, and the old one stops working.
     *
     * The AC's *"kode pemulihan baru dibuat, kode lama tidak berlaku"*, asserted on both sides:
     * the new code verifies and the old one does not. A reset that kept the old code would leave a
     * second key in the world, which is the thing a reset exists to revoke.
     */
    @Test
    fun `resetting issues a new code and revokes the old one`() = runBlocking {
        val original = lock.newRecoveryCode()
        lock.enable("1234", original)

        val replacement = lock.resetWithRecoveryCode(original, "5678")

        assertNotNull("the reset succeeds", replacement)
        assertNotEquals("and the code changes", original, replacement)
        assertTrue("the new PIN works", lock.verifyPin("5678"))
        assertFalse("the old PIN does not", lock.verifyPin("1234"))
        assertTrue("the new code works", lock.verifyRecoveryCode(replacement!!))
        assertFalse("and the old code is revoked", lock.verifyRecoveryCode(original))
    }

    /** A wrong recovery code resets nothing at all. */
    @Test
    fun `a wrong recovery code leaves the pin untouched`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())

        assertNull(lock.resetWithRecoveryCode("NOTTHECODE000000000", "5678"))

        assertTrue("the original PIN still works", lock.verifyPin("1234"))
        assertFalse(lock.verifyPin("5678"))
    }

    /**
     * The recovery code is **not** rate-limited, deliberately.
     *
     * A lockout on the escape hatch would strand an owner who mistyped their own code, and 119
     * bits do not need the protection a 4-digit PIN does. Asserted because the absence is a
     * decision, and a decision nobody tests is a decision the next change reverses.
     */
    @Test
    fun `wrong recovery codes do not lock the pin out`() = runBlocking {
        val code = lock.newRecoveryCode()
        lock.enable("1234", code)

        repeat(PinLockRepository.MAX_ATTEMPTS * 2) {
            lock.verifyRecoveryCode("NOTTHECODE000000000")
        }

        assertNull("no lockout", lock.lockedOutUntil())
        assertEquals("and no attempts counted", 0, lock.failedAttempts())
        assertTrue("the code still works", lock.verifyRecoveryCode(code))
    }

    // ------------------------------------------------------------------ disabling

    /** Disabling removes both secrets and both salts. */
    @Test
    fun `disabling removes every secret`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())
        lock.disable()

        assertFalse(lock.isEnabled())
        assertNull(database.settingDao().findValue(PinLockRepository.KEY_PIN_HASH))
        assertNull(database.settingDao().findValue(PinLockRepository.KEY_PIN_SALT))
        assertNull(database.settingDao().findValue(PinLockRepository.KEY_RECOVERY_HASH))
        assertNull(database.settingDao().findValue(PinLockRepository.KEY_RECOVERY_SALT))
    }

    /** The auto-lock duration survives a disable — it is a preference, not a secret. */
    @Test
    fun `disabling keeps the auto lock preference`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode(), autoLockMinutes = 15)
        lock.disable()

        assertEquals(15, lock.autoLockMinutes())
    }

    // ------------------------------------------------------------------ auto-lock

    @Test
    fun `an unenabled lock never locks`() = runBlocking {
        assertFalse(lock.shouldLock(coldStart = true))
        assertFalse(lock.shouldLock(coldStart = false))
    }

    /**
     * A **cold start** always locks when the PIN is on.
     *
     * The case this covers is the one the timestamp cannot: a process killed in the foreground
     * never ran `onStop`, so there is nothing to compare against. The app cannot know what
     * happened, so it locks.
     */
    @Test
    fun `a cold start locks even with no background timestamp`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode())

        assertNull("no timestamp was ever recorded", lock.backgroundedAt())
        assertTrue(lock.shouldLock(coldStart = true))
    }

    /** Under the duration, the owner comes back to an unlocked app. */
    @Test
    fun `returning under the duration does not lock`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode(), autoLockMinutes = 5)
        lock.recordBackgrounded()

        val twoMinutesLater = lockAt(FIXED_NOW.plus(Duration.ofMinutes(2)))

        assertFalse(twoMinutesLater.shouldLock(coldStart = false))
    }

    /** Past the duration, it does. */
    @Test
    fun `returning past the duration locks`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode(), autoLockMinutes = 5)
        lock.recordBackgrounded()

        val sixMinutesLater = lockAt(FIXED_NOW.plus(Duration.ofMinutes(6)))

        assertTrue(sixMinutesLater.shouldLock(coldStart = false))
    }

    /** Exactly at the duration counts as past it — the boundary belongs to the lock, not the owner. */
    @Test
    fun `the duration boundary locks`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode(), autoLockMinutes = 5)
        lock.recordBackgrounded()

        val exactly = lockAt(FIXED_NOW.plus(Duration.ofMinutes(5)))

        assertTrue(exactly.shouldLock(coldStart = false))
    }

    /**
     * The timestamp survives a restart, which is the AC's *"bukan counter yang reset"*.
     *
     * A repository built over the same database at a later instant is exactly what a restarted
     * process sees, and it must still decide correctly.
     */
    @Test
    fun `the background timestamp survives a restart`() = runBlocking {
        lock.enable("1234", lock.newRecoveryCode(), autoLockMinutes = 5)
        lock.recordBackgrounded()

        val restarted = lockAt(FIXED_NOW.plus(Duration.ofMinutes(10)))

        assertEquals(FIXED_NOW, restarted.backgroundedAt())
        assertTrue("and the elapsed time is measured from it", restarted.shouldLock(coldStart = false))
    }

    // ------------------------------------------------------------------ the floor

    /**
     * The production iteration count clears the documented floor.
     *
     * The one test that makes the low count above safe: tests may hash cheaply, but the **default**
     * may not be lowered to match.
     */
    @Test
    fun `the production iteration count clears the documented floor`() {
        assertTrue(
            "DEFAULT_ITERATIONS ${PinLockRepository.DEFAULT_ITERATIONS} must be at least " +
                PinLockRepository.MINIMUM_ITERATIONS,
            PinLockRepository.DEFAULT_ITERATIONS >= PinLockRepository.MINIMUM_ITERATIONS,
        )
    }

    /** And the auto-lock choices are the four FR-17 names. */
    @Test
    fun `the auto lock choices are the four the PRD names`() {
        assertEquals(setOf(1, 5, 15, 30), PinLockRepository.AUTO_LOCK_CHOICES)
        assertEquals(5, PinLockRepository.DEFAULT_AUTO_LOCK_MINUTES)
    }

    private companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-10-19T08:00:00Z")
    }
}