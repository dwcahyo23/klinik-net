package net.klinik.rental.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.klinik.rental.data.repository.PinLockRepository
import net.klinik.rental.ui.strings.RStrings

/**
 * What the lock screen draws.
 *
 * @property enteredDigits how many of [totalDigits] are filled. The digits themselves are **not**
 *   here — they live in a private field of the ViewModel and never reach a `StateFlow`, a saved
 *   state, or a log. A PIN in a `UiState` is a PIN in a bundle dump.
 * @property errorMessage the refusal, or `null`.
 * @property lockedOutSeconds the countdown, or `null` when input is acceptable.
 * @property shakeKey bumped on each **wrong** submission, so the screen's animation re-fires even
 *   when the message text is unchanged.
 * @property recovery the recovery flow's step, or `null` when it is closed.
 */
data class PinLockUiState(
    val enteredDigits: Int = 0,
    val totalDigits: Int = PinLockRepository.PIN_LENGTH,
    val errorMessage: String? = null,
    val lockedOutSeconds: Int? = null,
    val shakeKey: Int = 0,
    val recovery: RecoveryUi? = null,
)

/**
 * The recovery flow: code, then a new PIN, then the new code shown once.
 *
 * Three steps rather than one form because the AC's order is fixed — the code is checked, then the
 * new PIN is set, then a **new** code is displayed and must be confirmed. A single form would let
 * an owner type a new PIN before discovering their code was wrong.
 */
data class RecoveryUi(
    val step: RecoveryStep,
    val code: String = "",
    val newPin: String = "",
    val errorMessage: String? = null,
    /** The **new** code, present only at [RecoveryStep.ShowNewCode]. Shown once, never stored. */
    val issuedCode: String? = null,
)

/** Which question the recovery dialog is asking. */
enum class RecoveryStep {
    /** Enter the code that was written down when the PIN was set. */
    EnterCode,

    /** Choose a new PIN. */
    NewPin,

    /** The replacement code, shown exactly once, with the confirmation that it was written down. */
    ShowNewCode,
}

/**
 * The lock screen's state machine.
 *
 * ## Why the entered digits are a private field and not part of the state
 *
 * Because a `UiState` is the kind of object that ends up in a saved-state bundle, a Compose
 * snapshot, a crash report's "state at state of failure" dump. A four-digit PIN in any of those is
 * a PIN in a log, which NFR-5 forbids by name. The screen only ever needs to know **how many**
 * digits are entered — `KnPinGrid`'s own KDoc makes the same argument for the same reason — so
 * that is all the state carries.
 *
 * ## Why the countdown is a loop and not a stored deadline
 *
 * Because the screen has to redraw once a second. The **deadline** lives in the repository and is
 * persisted; this loop only renders the remaining time, and re-reads the deadline each tick so a
 * lockout that expired while the screen was up clears itself without a tap.
 *
 * @param onUnlocked invoked when the PIN was accepted. **Required** — a defaulted lambda here is
 *   a lock that accepts the right PIN and never lets the owner in, which is the "renders and is
 *   wired to nothing" defect with the worst possible consequence.
 */
class PinLockViewModel(
    private val lock: PinLockRepository,
    private val strings: RStrings,
    private val onUnlocked: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinLockUiState())
    val uiState: StateFlow<PinLockUiState> = _uiState.asStateFlow()

    /**
     * The digits entered so far — **never** in [uiState].
     *
     * See the class KDoc. Cleared on every submission, correct or not: a correct PIN has no reason
     * to linger, and a wrong one must not stay on screen for the next attempt to inherit.
     */
    private var entered: String = ""

    private var verifying = false
    private var shakeCounter = 0

    init {
        // Read the deadline once at construction: a screen opened *during* a lockout — the owner
        // came back from another app — must show the countdown immediately rather than after the
        // first tick.
        refreshLockout()
    }

    // ---------------------------------------------------------------- unlocking

    /** One key from the grid. */
    fun onDigit(digit: Char) {
        if (verifying) return
        if (_uiState.value.lockedOutSeconds != null) return
        if (_uiState.value.recovery != null) return
        if (entered.length >= PinLockRepository.PIN_LENGTH) return

        entered += digit
        _uiState.update { it.copy(enteredDigits = entered.length, errorMessage = null) }

        // Auto-submit at full length: there is no Enter key on the grid, and the AC's flow is
        // four digits then the answer. Waiting for a button that does not exist would be a lock
        // nobody can open.
        if (entered.length == PinLockRepository.PIN_LENGTH) submit()
    }

    /** The grid's backspace. */
    fun onDelete() {
        if (verifying) return
        entered = entered.dropLast(1)
        _uiState.update { it.copy(enteredDigits = entered.length, errorMessage = null) }
    }

    /**
     * Checks the entered PIN.
     *
     * ## Why the error path shakes and the success path does not
     *
     * Because the shake means "that was wrong". Firing it on success would be a screen telling the
     * owner they failed at the moment they got in — and the UX doc is explicit that it fires
     * *"hanya saat benar-benar salah"*.
     */
    private fun submit() {
        val candidate = entered
        verifying = true
        viewModelScope.launch {
            try {
                val ok = lock.verifyPin(candidate)
                entered = ""
                if (ok) {
                    _uiState.update {
                        it.copy(enteredDigits = 0, errorMessage = null, lockedOutSeconds = null)
                    }
                    onUnlocked()
                } else {
                    shakeCounter += 1
                    _uiState.update {
                        it.copy(
                            enteredDigits = 0,
                            errorMessage = strings.pinErrorWrong(),
                            shakeKey = shakeCounter,
                        )
                    }
                    // A failure may have crossed the attempt limit, so the countdown starts now
                    // rather than on the next tick.
                    refreshLockout()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed read must not be drawn as a wrong PIN: the owner would keep retrying a
                // correct code. It says the check could not run, which is a different next action.
                entered = ""
                _uiState.update {
                    it.copy(enteredDigits = 0, errorMessage = strings.pinErrorUnavailable())
                }
            } finally {
                verifying = false
            }
        }
    }

    /**
     * Re-reads the persisted deadline and renders the remaining seconds.
     *
     * ## Why this is a **function the UI calls**, not a coroutine that ticks itself
     *
     * Because "how often to redraw" is a UI concern, and putting the `delay` loop in the
     * ViewModel makes it a coroutine that never completes: with a fixed injected `Clock` the
     * remaining time never shrinks, so the loop re-arms forever and any `advanceUntilIdle` in a
     * test hangs. The first version of this class did exactly that and the suite timed out.
     *
     * The lock screen owns a `LaunchedEffect` that calls this once a second **only while a
     * countdown is showing**, so the cadence lives where the redraw happens and the ViewModel
     * stays a pure state machine a test can drive by hand.
     */
    fun refreshLockout() {
        viewModelScope.launch {
            try {
                val remaining = lock.lockoutRemaining()
                val seconds = remaining?.seconds?.toInt()?.takeIf { it > 0 }
                _uiState.update {
                    it.copy(
                        lockedOutSeconds = seconds,
                        // The countdown replaces the error line while it runs — one slot, because
                        // "wrong PIN" and "wait 24s" are the same question with two answers.
                        errorMessage = if (seconds != null) null else it.errorMessage,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing to say: the countdown is cosmetic and a failed read leaves the previous
                // value on screen rather than clearing a lockout the owner is waiting out.
            }
        }
    }

    // ---------------------------------------------------------------- recovery

    /** Opens the recovery flow on its first step. */
    fun openRecovery() {
        _uiState.update { it.copy(recovery = RecoveryUi(step = RecoveryStep.EnterCode)) }
    }

    /** Closes the recovery flow and returns to PIN entry. */
    fun dismissRecovery() {
        _uiState.update { it.copy(recovery = null) }
    }

    /** Carries an edit into the recovery step's field. */
    fun onRecoveryCodeChange(code: String) {
        _uiState.update { state ->
            state.copy(recovery = state.recovery?.copy(code = code, errorMessage = null))
        }
    }

    /** Carries an edit into the new-PIN field. */
    fun onNewPinChange(pin: String) {
        _uiState.update { state ->
            state.copy(recovery = state.recovery?.copy(newPin = pin, errorMessage = null))
        }
    }

    /**
     * Checks the recovery code and moves to the new-PIN step.
     *
     * A **wrong** code stays on this step with a message. It does not lock out — see
     * `PinLockRepository.verifyRecoveryCode`'s KDoc for why the escape hatch is not rate-limited.
     */
    fun submitRecoveryCode() {
        val recovery = _uiState.value.recovery ?: return
        viewModelScope.launch {
            try {
                if (lock.verifyRecoveryCode(recovery.code.trim())) {
                    _uiState.update {
                        it.copy(recovery = it.recovery?.copy(step = RecoveryStep.NewPin))
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            recovery = it.recovery?.copy(
                                errorMessage = strings.pinErrorWrongCode(),
                            ),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(recovery = it.recovery?.copy(errorMessage = strings.pinErrorUnavailable()))
                }
            }
        }
    }

    /**
     * Sets the new PIN, issues a **new** recovery code, and shows it once.
     *
     * The code is generated here and written by `resetWithRecoveryCode` in the same call, because
     * the reset has already been authorised by the old code — unlike the setup flow, there is no
     * "cancel" that must leave the previous state intact. Cancelling *this* step would leave the
     * old PIN working, which is what the owner expects from backing out.
     */
    fun submitNewPin() {
        val recovery = _uiState.value.recovery ?: return
        if (!PinLockRepository.isValidPin(recovery.newPin)) {
            _uiState.update {
                it.copy(recovery = it.recovery?.copy(errorMessage = strings.pinErrorBadNewPin()))
            }
            return
        }

        viewModelScope.launch {
            try {
                val newCode = lock.resetWithRecoveryCode(
                    recoveryCode = recovery.code.trim(),
                    newPin = recovery.newPin,
                )
                if (newCode == null) {
                    // The code stopped verifying between the two steps — a second device, or a
                    // concurrent reset. Back to the first step rather than pretending it worked.
                    _uiState.update {
                        it.copy(
                            recovery = RecoveryUi(
                                step = RecoveryStep.EnterCode,
                                errorMessage = strings.pinErrorWrongCode(),
                            ),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            recovery = it.recovery?.copy(
                                step = RecoveryStep.ShowNewCode,
                                issuedCode = newCode,
                            ),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(recovery = it.recovery?.copy(errorMessage = strings.pinErrorUnavailable()))
                }
            }
        }
    }

    /**
     * The owner confirms they wrote the new code down.
     *
     * The flow is not finished before this — the AC says so — and finishing means unlocking, since
     * the owner has just proved they hold the recovery code and chosen a PIN they know.
     */
    fun confirmNewCodeWritten() {
        val recovery = _uiState.value.recovery ?: return
        if (recovery.step != RecoveryStep.ShowNewCode) return

        // The code is dropped from state the moment it is confirmed: it is shown once by contract,
        // and a value left in a StateFlow is a value that can be re-rendered.
        _uiState.update { it.copy(recovery = null, errorMessage = null, shakeKey = it.shakeKey) }
        onUnlocked()
    }

    /** Clears a message when the owner dismisses it. */
    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

}