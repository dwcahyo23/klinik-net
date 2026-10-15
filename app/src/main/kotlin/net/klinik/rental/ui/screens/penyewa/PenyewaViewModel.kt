package net.klinik.rental.ui.screens.penyewa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.validation.BlankRenterFieldException
import net.klinik.rental.domain.validation.DuplicateNameException
import net.klinik.rental.ui.strings.RStrings

/**
 * Owns the renter screen's state.
 *
 * ## No manual invalidation signal
 *
 * The list comes from `repository.observeRenters()`, which is Room's own
 * invalidation flow. A renter saved by the form re-emits there and the screen
 * updates — no refresh button, no `MutableStateFlow<Long>` of "something
 * changed". The epic context is explicit that a second mechanism can only ever
 * disagree with Room's, and the AC for "visible without a manual refresh" is
 * satisfied by *not having* a refresh path at all.
 *
 * ## The three flows that are combined
 *
 * Room's renters, the search query, plus the form state. All are combined into
 * one [PenyewaUiState] and `distinctUntilChanged()`d, so a query change that does
 * not alter the result set does not recompose the list.
 *
 * ## Why this screen is not mounted
 *
 * It is not reachable from `MainActivity`. UX-DR13 asks for master-detail in
 * landscape, but AR-18 makes `WindowSizeClass` an Activity decision and no
 * `NavHost` exists in `app/src/main` yet — so this ships fully tested and
 * unreachable, and Story 2.8 wires it. See the story's design note.
 */
class PenyewaViewModel(
    private val repository: RenterRepository,
    private val strings: RStrings,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val form = MutableStateFlow<RenterFormState?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    /**
     * Whether a write is in flight, so a double tap on Save cannot enqueue two.
     *
     * An `AtomicBoolean` rather than a `MutableStateFlow<Boolean>`: this is not
     * rendered, so putting it in the state would recompose the screen on a value
     * nothing draws.
     */
    private val saving = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * The screen state.
     *
     * Nesting two `combine` calls rather than one over four flows, for the same
     * reason `KendaraanViewModel` nests: the typed overloads stop at five flows,
     * and nesting keeps every source typed rather than reaching for the `vararg`
     * overload, which would hand the lambda an `Array<Any?>`.
     */
    val uiState: StateFlow<PenyewaUiState> = combine(
        repository.observeRenters(),
        query,
    ) { renters, currentQuery ->
        ListSnapshot(renters.map { RenterRow(it) }, currentQuery)
    }
        .combine(combine(form, errorMessage) { currentForm, error -> FormSnapshot(currentForm, error) }) { list, formSnapshot ->
            PenyewaUiState(
                rows = list.rows,
                query = list.query,
                form = formSnapshot.form,
                errorMessage = formSnapshot.error,
            )
        }
        // Distinct *after* the projection, so a Room emission that produces an
        // identical list does not push a new state downstream and recompose
        // every row for no visible change.
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            // `WhileSubscribed(5_000)` keeps the combine alive across a rotation
            // instead of re-running Room's query on every configuration change.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PenyewaUiState(),
        )

    /** A transient confirmation. Success does not change screen state. */
    val snackbarMessage: StateFlow<String?> = message

    /**
     * What the data half of the combine produces.
     *
     * A named type rather than a `Pair`, because `Pair` would make the second
     * read `second` at the call site — and a silently swapped row list and query
     * produces a screen that looks fine and searches wrongly.
     */
    private data class ListSnapshot(
        val rows: List<RenterRow>,
        val query: String,
    )

    /** The interaction half: the open form and the last refusal's message. */
    private data class FormSnapshot(
        val form: RenterFormState?,
        val error: String?,
    )

    // ------------------------------------------------------------------ input

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    // ------------------------------------------------------------------- form

    fun openAddForm() {
        errorMessage.value = null
        form.value = RenterFormState()
    }

    /**
     * Opens the form on an existing renter.
     *
     * The stored `bankAccount` arrives as a `String` rather than a `String?`:
     * a `null` renders as nothing on `KnField`, and `orEmpty()` is what turns
     * the nullable stored value back into the form's own representation — so an
     * edit cannot silently blank a renter who *does* have an account.
     */
    fun openEditForm(renter: Renter) {
        errorMessage.value = null
        form.value = RenterFormState(
            id = renter.id,
            name = renter.name,
            phone = renter.phone,
            bankAccount = renter.bankAccount.orEmpty(),
            emergencyContact = renter.emergencyContact,
        )
    }

    fun closeForm() {
        // Clearing the error too: the sheet it belonged to is gone, and leaving
        // the message behind would put a refusal on the list of a form the owner
        // has already dismissed.
        errorMessage.value = null
        form.value = null
    }

    fun onFormChange(transform: (RenterFormState) -> RenterFormState) {
        form.value = form.value?.let(transform)
    }

    /**
     * Saves the form, translating each typed exception into its Indonesian copy.
     *
     * ## Where the name rule is enforced, twice
     *
     * 1. Here — a blank name is refused before a `Renter` exists, so the message
     *    lands on the field rather than as a generic refusal
     * 2. In `RenterRepository` — so a second screen cannot pass a check this one
     *    has
     *
     * and uniqueness is enforced a third time by the database's `UNIQUE` index.
     * Step 1 is a courtesy, not the rule.
     *
     * `ifBlank { null }` is what makes the bank account the *optional* field it
     * is documented to be: a blank string is stored as `null` rather than as an
     * empty account number that looks like a real one.
     *
     * On success the form closes and the list updates on its own — Room re-emits
     * and `combine` rebuilds the state. There is no manual "now refresh".
     */
    fun saveForm() {
        // A second tap while the first write is still in flight would enqueue a
        // second `save` for the same renter. It would then be reported as a
        // duplicate of its own row and would reopen a form that had already
        // closed, so the guard is here rather than in the UI: the button's
        // `enabled` is a courtesy, this is the rule.
        if (!saving.compareAndSet(false, true)) return

        val current = form.value
        if (current == null) {
            saving.set(false)
            return
        }

        val renter = try {
            Renter(
                id = current.id ?: repository.newRenterId(),
                // Trimmed here for the form's own representation; the repository
                // trims again before it writes, so the unique key is normalised
                // for every caller and not only for this one.
                name = current.name.trim(),
                phone = current.phone.trim(),
                bankAccount = current.bankAccount.trim().ifBlank { null },
                emergencyContact = current.emergencyContact.trim(),
            )
        } catch (e: BlankRenterFieldException) {
            // Routed by the field the exception names, rather than one catch per
            // field, so adding a required field later means adding a branch here
            // and nothing else.
            val message = when (e.field) {
                "phone" -> strings.penyewaErrorBlankPhone()
                "emergencyContact" -> strings.penyewaErrorBlankEmergency()
                else -> strings.penyewaErrorBlankName()
            }
            form.value = current.copy(
                nameError = message.takeIf { e.field == "name" },
                phoneError = message.takeIf { e.field == "phone" },
                emergencyError = message.takeIf { e.field == "emergencyContact" },
            )
            saving.set(false)
            return
        }

        viewModelScope.launch {
            try {
                repository.save(renter)
                form.value = null
                errorMessage.value = null
                message.value = strings.penyewaSaved(renter.name)
            } catch (e: DuplicateNameException) {
                // Re-read the form rather than writing `current` back: the owner
                // may have kept typing while Room was doing the write, and a
                // stale `copy` would silently discard those keystrokes.
                form.value = form.value?.copy(
                    nameError = strings.penyewaErrorDuplicateName(e.name),
                )
            } catch (e: IllegalArgumentException) {
                form.value = form.value?.copy(nameError = strings.penyewaErrorBlankName())
            } catch (e: CancellationException) {
                // Re-thrown before the generic handler. `CancellationException`
                // extends `IllegalStateException`, so a cleared ViewModel would
                // otherwise show "Gagal menyimpan. Coba lagi." for a write that
                // was merely cancelled, and structured concurrency would be
                // quietly broken for every caller of this coroutine.
                throw e
            } catch (e: Exception) {
                // The write failed for a reason this screen does not model. It is
                // reported rather than swallowed: the epic context is explicit
                // that a failed write must fail loudly and never look like a
                // fresh account.
                errorMessage.value = strings.penyewaErrorUnexpected()
            } finally {
                saving.set(false)
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun consumeError() {
        errorMessage.value = null
    }
}