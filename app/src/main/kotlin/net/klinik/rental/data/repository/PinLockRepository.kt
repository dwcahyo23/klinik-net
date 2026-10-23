package net.klinik.rental.data.repository

import androidx.room.withTransaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.SettingDao
import net.klinik.rental.data.db.entity.SettingEntity

/**
 * Story 5.3's lock: the PIN, the recovery code, and the two clocks that decide when to ask.
 *
 * ## Why the whole lock is one class
 *
 * Because its parts are not separable. The PIN hash is meaningless without its salt; the attempt
 * counter is meaningless without the lockout deadline; the auto-lock duration is meaningless
 * without the background timestamp. Splitting them across repositories would put four things that
 * must be written together in four files, and the transaction that writes them is the only reason
 * the state is ever consistent.
 *
 * ## Why PBKDF2WithHmacSHA1 and nothing newer
 *
 * Because it is in the Android SDK with no dependency, and because bcrypt and Argon2 are not —
 * they need a native library. For a **4-digit PIN** that is the right trade: 10,000 combinations
 * are exhaustible offline whatever the hash, so the expensive ones would slow the owner's unlock
 * far more than they slow an attacker. The control that actually matters for low-entropy input is
 * [MAX_ATTEMPTS] and the countdown, which this class also owns. AD-7 says all of this and this
 * class implements it rather than re-arguing it.
 *
 * ## Why the recovery code is hashed at the same strength
 *
 * A cheaper hash for a higher-entropy secret would be defensible arithmetic and a bad rule: two
 * hash strengths in one file is two things to keep in step, and the code's ~119 bits do not need
 * the discount.
 *
 * ## Why the attempt counter and lockout deadline are **persisted**
 *
 * Because a counter in memory resets when the process dies, and force-quitting the app is the
 * first thing anyone trying to get in would do. Persisting them is what makes the limit real
 * rather than decorative — and it is the same reason the background timestamp is persisted.
 */
class PinLockRepository(
    private val dao: SettingDao,
    private val clock: Clock,
    private val database: KlinikDatabase? = null,
    /**
     * PBKDF2 iterations.
     *
     * A parameter with the production value as the default, because a suite of twenty PIN tests
     * that each hashed at [DEFAULT_ITERATIONS] would spend a minute doing nothing. `PinLockTest`
     * asserts the **default** is at least [MINIMUM_ITERATIONS], so lowering it to make tests fast
     * fails a test rather than shipping a weak hash.
     */
    private val iterations: Int = DEFAULT_ITERATIONS,
    /**
     * The randomness behind salts and the recovery code.
     *
     * Injected so a test can assert the alphabet and the length without depending on luck, and so
     * the code is reproducible in a way the production default is not.
     */
    private val random: SecureRandom = SecureRandom(),
) {

    // ------------------------------------------------------------------ state

    /**
     * Whether a PIN has been set and confirmed, seeding the first-run PIN on a fresh install.
     *
     * ## Why a fresh install is **locked**, not open
     *
     * Because the prototype ships that way — `data.js:11-12` carries `pinEnabled: true` and
     * `pinCode: "1234"` in its defaults, and `index.html:44` is the PIN screen, not an
     * optional panel. The owner put it plainly: *"default pin 1234 tapi ketika app pertama dibuka
     * tidak lock screen pin langsung by pass."*
     *
     * Absent used to read as "no PIN", which meant a fresh install opened straight onto the
     * dashboard — a books app that opens onto the books. That is not what the lock is for, and
     * it is not what the prototype does.
     *
     * ## Why the seed is idempotent
     *
     * Because it runs on **every** read. [seedFirstRunPin] writes only when no PIN has been set,
     * so the second call is a no-op — and an owner who later turns the lock **off** in settings
     * does not get a lock back the next time the Activity reads this.
     */
    suspend fun isEnabled(): Boolean {
        seedFirstRunPin()
        return dao.findValue(KEY_ENABLED) == "true"
    }

    /**
     * Writes the prototype's default PIN on an install that has never had one.
     *
     * ## Why `1234`, and what it is
     *
     * It is the prototype's own default and the owner's stated expectation, so it is what ships.
     * It is **not** a security claim: a 4-digit PIN is exhaustible in 10,000 tries whatever the
     * hash, and the PRD says so in terms (*"PIN adalah penghalang layar, bukan keamanan"*). The
     * attempt limit and the 30-second lockout are the control that matters, and both are here.
     *
     * ## Why a real hash, not the plaintext
     *
     * NFR-5 forbids the PIN being readable in storage, in logs, or in an export. So the seed goes
     * through [enable] — the same path the owner's own PIN takes — rather than writing `pinCode`.
     * The recovery code is generated and **not** stored, because there is nobody to write it down:
     * the owner is going to change this PIN in settings, and until they do the prototype's own
     * escape hatch is a fresh recovery code from `resetWithRecoveryCode`. Writing one down and
     * never showing it would be a secret with no reader.
     *
     * The owner changing the PIN in settings replaces the salts and the hash through [enable], so
     * this default stops being the key the moment they touch it.
     */
    private suspend fun seedFirstRunPin() {
        // **Two guards, and both are load-bearing.**
        //
        // `KEY_SEEDED` answers "has this install ever had its first-run PIN?" — it is written
        // once and never deleted, not by [disable] and not by a restore. Without it, turning
        // the lock off re-seeded the PIN on the very next read and the lock came back, which
        // is a control that undoes itself.
        //
        // `KEY_PIN_HASH` answers "does a PIN already exist?" — without it, an owner who set
        // their own PIN *before* any read had run would have it overwritten by the default.
        // That is the window `enable()` opens: it writes the hash, but the marker is only
        // written by this method, so the first `isEnabled()` after a fresh `enable()` would
        // otherwise seed straight over it.
        if (dao.findValue(KEY_SEEDED) != null) return
        if (dao.findValue(KEY_PIN_HASH) != null) {
            // A PIN predates the marker — this database was written by a build before the
            // marker existed. Record that it has been seeded so the default never lands on it.
            dao.putSetting(SettingEntity(KEY_SEEDED, "true"))
            return
        }
        enable(pin = FIRST_RUN_PIN, recoveryCode = newRecoveryCode())
        dao.putSetting(SettingEntity(KEY_SEEDED, "true"))
    }

    /** The auto-lock duration in minutes. Absent reads as [DEFAULT_AUTO_LOCK_MINUTES]. */
    suspend fun autoLockMinutes(): Int =
        dao.findInt(KEY_AUTO_LOCK_MINUTES, DEFAULT_AUTO_LOCK_MINUTES)

    /** When the app last went to the background, or `null` if it never has since the PIN was set. */
    suspend fun backgroundedAt(): Instant? =
        dao.findValue(KEY_BACKGROUNDED_AT)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** How many consecutive failures have happened since the last success. */
    suspend fun failedAttempts(): Int = dao.findInt(KEY_ATTEMPTS, 0)

    /**
     * When input becomes acceptable again, or `null` when it is acceptable now.
     *
     * Compared against the injected [clock] rather than the system one so the countdown is
     * testable — AD-8's rule, and the same reason every other "now" in this app comes from a
     * `Clock`.
     */
    suspend fun lockedOutUntil(): Instant? {
        val stored = dao.findValue(KEY_LOCKOUT_UNTIL)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return null
        return stored.takeIf { it.isAfter(Instant.now(clock)) }
    }

    /** Remaining lockout, or `null` when input is acceptable. */
    suspend fun lockoutRemaining(): Duration? =
        lockedOutUntil()?.let { Duration.between(Instant.now(clock), it) }

    // ------------------------------------------------------------------- setup

    /**
     * Enables the lock with a code the caller already generated, and returns nothing.
     *
     * ## Why generation and writing are separate calls
     *
     * Because the AC says the flow is not finished until the owner confirms they wrote the code
     * down. A single `enable(pin)` that generated *and* wrote would leave a live lock behind when
     * the owner dismissed the code without confirming — and rolling it back with [disable] would
     * destroy an existing PIN when the owner was *changing* one.
     *
     * So the flow is: [newRecoveryCode] (no writes), show it, confirm, then this. Backing out
     * before the confirm writes nothing at all, and the caller simply drops the code.
     *
     * The code is stored only as a hash and cannot be shown again — [verifyRecoveryCode] can only
     * answer yes or no.
     */
    suspend fun enable(
        pin: String,
        recoveryCode: String,
        autoLockMinutes: Int = DEFAULT_AUTO_LOCK_MINUTES,
    ) {
        require(isValidPin(pin)) { "a PIN must be exactly $PIN_LENGTH digits" }
        require(recoveryCode.length == RECOVERY_LENGTH) {
            "a recovery code must be $RECOVERY_LENGTH characters"
        }

        val pinSalt = newSalt()
        val recoverySalt = newSalt()

        inTransaction {
            dao.putSetting(SettingEntity(KEY_PIN_SALT, pinSalt))
            dao.putSetting(SettingEntity(KEY_PIN_HASH, hash(pin, pinSalt)))
            dao.putSetting(SettingEntity(KEY_RECOVERY_SALT, recoverySalt))
            dao.putSetting(SettingEntity(KEY_RECOVERY_HASH, hash(recoveryCode, recoverySalt)))
            dao.putSetting(SettingEntity(KEY_AUTO_LOCK_MINUTES, autoLockMinutes.toString()))
            dao.putSetting(SettingEntity(KEY_ENABLED, "true"))
            // A fresh lock starts with a clean slate: enabling a PIN is not a failed attempt, and
            // a stale lockout from a previous PIN would refuse input for a reason the owner
            // cannot connect to anything they did.
            dao.deleteSetting(KEY_ATTEMPTS)
            dao.deleteSetting(KEY_LOCKOUT_UNTIL)
            // No background timestamp yet: the owner is in the app right now, having just set the
            // PIN, and writing "now" here would make the first return trip compare against a
            // moment that is not a backgrounding.
            dao.deleteSetting(KEY_BACKGROUNDED_AT)
        }
    }

    /**
     * Replaces the PIN using the recovery code, and issues a **new** code.
     *
     * ## Why the old code stops working
     *
     * Because a code that survives its own use is a permanent second key. The AC is explicit:
     * *"kode pemulihan baru dibuat, kode lama tidak berlaku."* [enable] overwrites both salts and
     * both hashes, so the old code cannot verify against the new salt even if its hash were kept.
     *
     * @return the new recovery code, to be shown once, or `null` when [recoveryCode] is wrong.
     */
    suspend fun resetWithRecoveryCode(
        recoveryCode: String,
        newPin: String,
    ): String? {
        if (!verifyRecoveryCode(recoveryCode)) return null
        val newCode = newRecoveryCode()
        enable(newPin, newCode, autoLockMinutes())
        return newCode
    }

    /** Turns the lock off. The secrets go with it rather than lingering unused. */
    suspend fun disable() {
        inTransaction {
            dao.deleteSetting(KEY_PIN_HASH)
            dao.deleteSetting(KEY_PIN_SALT)
            dao.deleteSetting(KEY_RECOVERY_HASH)
            dao.deleteSetting(KEY_RECOVERY_SALT)
            dao.deleteSetting(KEY_ENABLED)
            dao.deleteSetting(KEY_ATTEMPTS)
            dao.deleteSetting(KEY_LOCKOUT_UNTIL)
            dao.deleteSetting(KEY_BACKGROUNDED_AT)
            // `autoLockMinutes` is deliberately **kept**: it is a preference, not a secret, and an
            // owner who turns the lock off and on again should not have to re-pick their duration.
        }
    }

    /** Changes the auto-lock duration. Kept when the lock is disabled — see [disable]. */
    suspend fun setAutoLockMinutes(minutes: Int) {
        require(minutes in AUTO_LOCK_CHOICES) { "$minutes is not one of $AUTO_LOCK_CHOICES" }
        dao.putSetting(SettingEntity(KEY_AUTO_LOCK_MINUTES, minutes.toString()))
    }

    // ------------------------------------------------------------------ verify

    /**
     * Whether [pin] is the stored PIN, applying the attempt limit.
     *
     * ## Why this both answers and records
     *
     * Because the counter and the answer are the same event. A separate `recordFailure()` would
     * let a caller verify without counting, and the one caller that forgot would be the one that
     * mattered.
     *
     * A **locked-out** call returns `false` without comparing, and does not increment — otherwise
     * the deadline would extend itself on every refused tap and the owner could never get back in.
     */
    suspend fun verifyPin(pin: String): Boolean {
        if (lockedOutUntil() != null) return false

        // The seed, before the hash is read: on a fresh install the lock screen is shown by
        // `shouldLock` (which seeded), but `verifyPin` is a public entry point and a caller that
        // reached it first on an unseeded database would compare against no hash and refuse every
        // PIN — including the right one.
        seedFirstRunPin()

        val salt = dao.findValue(KEY_PIN_SALT) ?: return false
        val stored = dao.findValue(KEY_PIN_HASH) ?: return false
        val matches = constantTimeEquals(stored, hash(pin, salt))

        if (matches) {
            clearAttempts()
        } else {
            recordFailure()
        }
        return matches
    }

    /**
     * Whether [code] is the stored recovery code.
     *
     * **Not** rate-limited, and that is deliberate: the code carries ~119 bits, so a lockout adds
     * nothing an attacker would notice, while a lockout on the *escape hatch* could strand an
     * owner who mistyped their own code twice. The PIN's limit exists because 4 digits are
     * guessable; this is not.
     */
    suspend fun verifyRecoveryCode(code: String): Boolean {
        val salt = dao.findValue(KEY_RECOVERY_SALT) ?: return false
        val stored = dao.findValue(KEY_RECOVERY_HASH) ?: return false
        return constantTimeEquals(stored, hash(code, salt))
    }

    /** Records a failure and, at [MAX_ATTEMPTS], sets the lockout deadline. */
    private suspend fun recordFailure() {
        val attempts = dao.findInt(KEY_ATTEMPTS, 0) + 1
        inTransaction {
            dao.putSetting(SettingEntity(KEY_ATTEMPTS, attempts.toString()))
            if (attempts >= MAX_ATTEMPTS) {
                // A fixed window from *this* failure, not an accumulating one: the deadline is
                // replaced rather than extended, so the owner who waits it out is let in on the
                // next attempt.
                dao.putSetting(
                    SettingEntity(
                        KEY_LOCKOUT_UNTIL,
                        Instant.now(clock).plus(LOCKOUT_DURATION).toString(),
                    ),
                )
            }
        }
    }

    private suspend fun clearAttempts() {
        inTransaction {
            dao.deleteSetting(KEY_ATTEMPTS)
            dao.deleteSetting(KEY_LOCKOUT_UNTIL)
        }
    }

    // -------------------------------------------------------------- lifecycle

    /**
     * Records that the app went to the background at the current instant.
     *
     * Called from `MainActivity.onStop`. Persisted rather than held in memory because the AC's
     * whole point is that the counter must survive process death: *"dihitung dari timestamp
     * absolut saat masuk background, bukan counter yang reset."*
     */
    suspend fun recordBackgrounded() {
        dao.putSetting(SettingEntity(KEY_BACKGROUNDED_AT, Instant.now(clock).toString()))
    }

    /**
     * Whether the app should be locked when it comes back to the foreground.
     *
     * ## Why `coldStart` is a parameter rather than something this can work out
     *
     * Because it is a fact about the **process**, and only the Activity knows it. The rule the
     * spec settles on: a process that has just started locks unconditionally when the PIN is on,
     * because the process being gone is the case where `onStop` may never have run — and the case
     * where the app cannot know what happened is the case where it must fail safe. Every later
     * return compares the persisted timestamp against the duration.
     */
    suspend fun shouldLock(coldStart: Boolean): Boolean {
        if (!isEnabled()) return false
        if (coldStart) return true

        val backgroundedAt = backgroundedAt() ?: return false
        val elapsed = Duration.between(backgroundedAt, Instant.now(clock))
        return elapsed >= Duration.ofMinutes(autoLockMinutes().toLong())
    }

    // ------------------------------------------------------------------ crypto

    /**
     * PBKDF2 over [value] with [salt], as lowercase hex.
     *
     * Hex rather than Base64 so the stored value is plain ASCII with no platform dependency and
     * no padding alphabet to get wrong — `java.util.Base64` is API 26 and this app's floor is 24,
     * and `android.util.Base64` would put a framework type in the data layer for no gain.
     */
    private fun hash(value: String, salt: String): String {
        val spec = PBEKeySpec(
            value.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            iterations,
            HASH_BITS,
        )
        val encoded = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        return encoded.joinToString(separator = "") { byte ->
            // `and 0xff` before `toString(16)`, or a byte above 127 renders as a negative int and
            // the hex string grows a minus sign.
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    /**
     * Compares two hashes without an early exit.
     *
     * `MessageDigest.isEqual` is the JDK's constant-time comparison. A `==` on the hex strings
     * would be correct and would leak how many leading characters matched, which is a timing
     * channel — small, but free to avoid.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    /** 128 bits of salt, hex-encoded. A per-setting salt is what stops one rainbow table serving both. */
    private fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    /**
     * A fresh recovery code, **not stored anywhere**.
     *
     * Public because the setup flow generates the code, shows it, and only writes it on the
     * owner's confirmation — see [enable]. A caller that generated and wrote in one step would
     * leave a live lock behind when the owner dismissed the code.
     */
    fun newRecoveryCode(): String = buildString(RECOVERY_LENGTH) {
        repeat(RECOVERY_LENGTH) {
            append(RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)])
        }
    }

    /** The same nullable-database shape every repository here uses. */
    private suspend fun inTransaction(block: suspend () -> Unit) {
        database?.withTransaction { block() } ?: block()
    }

    companion object {
        /** A PIN is exactly four digits. Not three, not six — FR-17 says four. */
        const val PIN_LENGTH = 4

        /** Five consecutive failures lock input. AD-7's number, not this story's choice. */
        const val MAX_ATTEMPTS = 5

        /**
         * How long input is refused after [MAX_ATTEMPTS].
         *
         * 30 seconds. Ten thousand combinations at five per half minute is about sixteen hours of
         * continuous tapping, which is long enough to make hand-guessing pointless and short
         * enough that an owner who fat-fingered five times is not locked out of their own books
         * for a coffee break.
         */
        val LOCKOUT_DURATION: Duration = Duration.ofSeconds(30)

        /** The auto-lock durations FR-17 names, in minutes. */
        val AUTO_LOCK_CHOICES: Set<Int> = setOf(1, 5, 15, 30)

        /** FR-17's default. */
        const val DEFAULT_AUTO_LOCK_MINUTES = 5

        /**
         * The PIN a **fresh install** ships with — the prototype's own default (`data.js:12`).
         *
         * A first-run convenience, not a secret: the owner changes it in settings, and a 4-digit
         * PIN is exhaustible in 10,000 tries whatever the hash. It is stored hashed, like every
         * other PIN — see [seedFirstRunPin].
         */
        const val FIRST_RUN_PIN = "1234"

        /** The recovery code's length. The PRD's minimum, which is also what it is generated at. */
        const val RECOVERY_LENGTH = 20

        /** The alphabet the PRD's entropy figure assumes: A–Z, a–z, 0–9. */
        const val RECOVERY_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        /**
         * Why the full alphanumeric set and not a reduced one.
         *
         * The PRD computes the entropy from it: *"20 karakter alphanumeric memberi ~119 bit
         * entropi"*. Dropping the ambiguous characters (`0/O`, `1/I/l`) would be kinder to read
         * and would quietly cost the entropy the whole recovery mechanism is justified by. The
         * code is written down once and copied, not typed from memory — and case **is**
         * significant, for the same reason: folding it would cut the alphabet and the entropy
         * with it.
         */

        /**
         * The production iteration count.
         *
         * 120,000 is roughly 100ms on a modern phone and a few hundred on a cheap tablet — slow
         * enough to matter for an offline attack on a 10,000-combination space, fast enough that
         * unlocking does not feel broken. [MINIMUM_ITERATIONS] is the floor a test enforces, so
         * this number cannot be quietly reduced to make a suite faster.
         */
        const val DEFAULT_ITERATIONS = 120_000

        /** The floor [DEFAULT_ITERATIONS] must clear. */
        const val MINIMUM_ITERATIONS = 100_000

        private const val ALGORITHM = "PBKDF2WithHmacSHA1"
        private const val HASH_BITS = 256
        private const val SALT_BYTES = 16

        /** Whether [pin] is a PIN this app can store: exactly [PIN_LENGTH] ASCII digits. */
        fun isValidPin(pin: String): Boolean =
            pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

        // The settings keys. Every one of them is in `BackupJson.NEVER_EXPORTED`, because a
        // restored `pinEnabled = true` without a hash is a lock nobody can open.
        const val KEY_ENABLED = "pinEnabled"
        const val KEY_PIN_HASH = "pinHash"
        const val KEY_PIN_SALT = "pinSalt"
        const val KEY_RECOVERY_HASH = "recoveryHash"
        const val KEY_RECOVERY_SALT = "recoverySalt"
        const val KEY_ATTEMPTS = "pinAttempts"
        const val KEY_LOCKOUT_UNTIL = "pinLockoutUntil"
        const val KEY_AUTO_LOCK_MINUTES = "autoLockMinutes"
        const val KEY_BACKGROUNDED_AT = "pinBackgroundedAt"

        /**
         * "This install has already had its first-run PIN."
         *
         * Deliberately **not** in `BackupJson.NEVER_EXPORTED`: it is not a secret, and a restore
         * onto a fresh device should not resurrect the default PIN there. See
         * [seedFirstRunPin] on why the marker exists at all.
         */
        const val KEY_SEEDED = "pinFirstRunSeeded"
    }
}