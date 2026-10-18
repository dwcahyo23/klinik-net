package net.klinik.rental.ui.lock

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.asExecutor
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.repository.PinLockRepository
import net.klinik.rental.ui.strings.RStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The lock screen's state machine.
 *
 * ## Why the repository is built **without** a database handle here
 *
 * `withTransaction` asserts it is off the main thread, and these tests run on
 * `StandardTestDispatcher`'s thread. Every ViewModel test in this repo takes the same shape for
 * the same reason; the transaction paths are covered by `PinLockRepositoryTest` under
 * `runBlocking`. The cost is that a test here cannot prove atomicity — it proves the flow.
 *
 * ## Why the PIN is never read from the state
 *
 * Because it is never there. `PinLockUiState` carries `enteredDigits` and nothing else, so a test
 * that wanted the digits would have to read a private field — which is the point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PinLockViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: KlinikDatabase
    private lateinit var lock: PinLockRepository
    private lateinit var strings: RStrings
    private var unlocked = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        )
            // Room runs its queries on its own executor, which `advanceUntilIdle()` does not
            // drain — so a `findValue` issued by `verifyPin` would sit in flight on a real
            // background thread while the assertion read a state that had not been updated yet.
            // Pointing both executors at the test dispatcher puts Room's work on the same virtual
            // clock. Every ViewModel test in this repo that touches Room does this; the first
            // version of this file did not, and every coroutine assertion failed as though the
            // ViewModel were unwired.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        lock = PinLockRepository(
            dao = database.settingDao(),
            clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            // **No database handle** — see the class KDoc.
            iterations = 1_000,
        )
        strings = RStrings(ApplicationProvider.getApplicationContext())
        unlocked = 0
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): PinLockViewModel {
        val vm = PinLockViewModel(lock = lock, strings = strings, onUnlocked = { unlocked += 1 })
        // Drain the constructor's first reads so `.value` is not the initial state.
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    // ------------------------------------------------------------------ entry

    /** Four digits auto-submit, and a correct PIN unlocks. */
    @Test
    fun `the correct pin unlocks after four digits`() = runTest(dispatcher) {
        val code = lock.newRecoveryCode()
        lock.enable("1234", code)
        val vm = viewModel()

        "1234".forEach { vm.onDigit(it) }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("unlocked once", 1, unlocked)
        assertEquals("and the dots are cleared", 0, vm.uiState.value.enteredDigits)
        assertNull(vm.uiState.value.errorMessage)
    }

    /**
     * A wrong PIN shows the message, clears the dots, and bumps the shake key.
     *
     * The shake key is asserted because it is what the animation keys on: a screen that showed the
     * message but did not shake would satisfy "an error is displayed" and fail the AC's *"ada
     * getaran dan Shake 420ms"*.
     */
    @Test
    fun `a wrong pin shows the message and bumps the shake key`() = runTest(dispatcher) {
        lock.enable("1234", lock.newRecoveryCode())
        val vm = viewModel()

        "0000".forEach { vm.onDigit(it) }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("not unlocked", 0, unlocked)
        assertEquals("PIN salah, coba lagi", vm.uiState.value.errorMessage)
        assertEquals("the dots clear for the next attempt", 0, vm.uiState.value.enteredDigits)
        assertEquals("and the shake fires", 1, vm.uiState.value.shakeKey)
    }

    /** A second wrong attempt shakes again, even though the message text is unchanged. */
    @Test
    fun `a second wrong attempt shakes again`() = runTest(dispatcher) {
        lock.enable("1234", lock.newRecoveryCode())
        val vm = viewModel()

        "0000".forEach { vm.onDigit(it) }
        dispatcher.scheduler.advanceUntilIdle()
        val first = vm.uiState.value.shakeKey

        "1111".forEach { vm.onDigit(it) }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("the key moved again", vm.uiState.value.shakeKey > first)
    }

    /** Backspace removes a digit and never below zero. */
    @Test
    fun `backspace removes a digit`() = runTest(dispatcher) {
        lock.enable("1234", lock.newRecoveryCode())
        val vm = viewModel()

        vm.onDigit('1')
        vm.onDigit('2')
        assertEquals(2, vm.uiState.value.enteredDigits)

        vm.onDelete()
        assertEquals(1, vm.uiState.value.enteredDigits)

        vm.onDelete()
        vm.onDelete()
        assertEquals("never below zero", 0, vm.uiState.value.enteredDigits)
    }

    /**
     * Five failures lock input, and the countdown appears.
     *
     * The sixth tap is **ignored** — not queued, not compared — because a lockout that still
     * accepted digits would let the owner complete a PIN they cannot submit.
     */
    @Test
    fun `five failures show the countdown and refuse further input`() = runTest(dispatcher) {
        lock.enable("1234", lock.newRecoveryCode())
        val vm = viewModel()

        repeat(PinLockRepository.MAX_ATTEMPTS) {
            "0000".forEach { digit -> vm.onDigit(digit) }
            dispatcher.scheduler.advanceUntilIdle()
        }

        assertNotNull("the countdown is showing", vm.uiState.value.lockedOutSeconds)
        assertNull("and the error line is replaced by it", vm.uiState.value.errorMessage)
        // A tap while locked out does not even fill a dot.
        vm.onDigit('1')
        assertEquals(0, vm.uiState.value.enteredDigits)
    }

    /**
     * The countdown **clears itself** when the deadline passes, with no tap.
     *
     * The loop re-reads the persisted deadline each tick rather than counting down locally, so a
     * lockout that expired while the screen sat there lifts without the owner touching anything.
     */
    @Test
    fun `the countdown clears when the lockout expires`() = runTest(dispatcher) {
        lock.enable("1234", lock.newRecoveryCode())
        repeat(PinLockRepository.MAX_ATTEMPTS) { lock.verifyPin("0000") }

        // A ViewModel whose clock is past the deadline.
        val after = PinLockRepository(
            dao = database.settingDao(),
            clock = Clock.fixed(
                FIXED_NOW.plus(PinLockRepository.LOCKOUT_DURATION).plusSeconds(1),
                ZoneOffset.UTC,
            ),
            iterations = 1_000,
        )
        val vm = PinLockViewModel(lock = after, strings = strings, onUnlocked = { unlocked += 1 })
        dispatcher.scheduler.advanceUntilIdle()

        // The tick is the UI's cadence, so the test drives it the way the screen would.
        vm.refreshLockout()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("the countdown is gone", vm.uiState.value.lockedOutSeconds)

        "1234".forEach { vm.onDigit(it) }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("and the right PIN gets in", 1, unlocked)
    }

    // ---------------------------------------------------------------- recovery

    /** The recovery flow walks code → new PIN → new code. */
    @Test
    fun `recovery issues a new code and unlocks`() = runTest(dispatcher) {
        val original = lock.newRecoveryCode()
        lock.enable("1234", original)
        val vm = viewModel()

        vm.openRecovery()
        assertEquals(RecoveryStep.EnterCode, vm.uiState.value.recovery?.step)

        vm.onRecoveryCodeChange(original)
        vm.submitRecoveryCode()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(RecoveryStep.NewPin, vm.uiState.value.recovery?.step)

        vm.onNewPinChange("5678")
        vm.submitNewPin()
        dispatcher.scheduler.advanceUntilIdle()

        val recovery = vm.uiState.value.recovery
        assertEquals(RecoveryStep.ShowNewCode, recovery?.step)
        assertNotNull("a new code is issued", recovery?.issuedCode)
        assertTrue(
            "and it is not the old one",
            recovery?.issuedCode != original,
        )

        vm.confirmNewCodeWritten()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("the owner is let in", 1, unlocked)
        assertNull("and the code is dropped from state", vm.uiState.value.recovery)
    }

    /** A wrong code stays on the first step with a message and unlocks nothing. */
    @Test
    fun `a wrong recovery code stays on the code step`() = runTest(dispatcher) {
        lock.enable("1234", lock.newRecoveryCode())
        val vm = viewModel()

        vm.openRecovery()
        vm.onRecoveryCodeChange("NOTTHECODE000000000")
        vm.submitRecoveryCode()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(RecoveryStep.EnterCode, vm.uiState.value.recovery?.step)
        assertEquals("Kode pemulihan salah, coba lagi.", vm.uiState.value.recovery?.errorMessage)
        assertEquals("and nothing unlocked", 0, unlocked)
    }

    /** A new PIN that is not four digits is refused before anything is written. */
    @Test
    fun `a malformed new pin is refused`() = runTest(dispatcher) {
        val original = lock.newRecoveryCode()
        lock.enable("1234", original)
        val vm = viewModel()

        vm.openRecovery()
        vm.onRecoveryCodeChange(original)
        vm.submitRecoveryCode()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onNewPinChange("12")
        vm.submitNewPin()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(RecoveryStep.NewPin, vm.uiState.value.recovery?.step)
        assertEquals("PIN harus tepat 4 angka.", vm.uiState.value.recovery?.errorMessage)
        assertTrue("the old PIN still works", lock.verifyPin("1234"))
    }

    private companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-10-19T08:00:00Z")
    }
}