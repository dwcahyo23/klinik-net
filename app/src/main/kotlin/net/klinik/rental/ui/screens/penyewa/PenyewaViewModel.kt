package net.klinik.rental.ui.screens.penyewa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.domain.validation.BlankRenterFieldException
import net.klinik.rental.domain.validation.DuplicateNameException
import net.klinik.rental.ui.format.formatRupiah
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
 * ## Why this screen is reachable
 *
 * Story 2.8 built the shell: `AppNavGraph` routes to this screen from
 * `Destination.Penyewa`, and it mounts the same way `KendaraanScreen` does —
 * a sheet over the list, not a second destination.
 */
class PenyewaViewModel(
    private val repository: RenterRepository,
    private val strings: RStrings,
    /**
     * The contracts behind the detail pane.
     *
     * Optional so the existing tests — which build this ViewModel against a renter repository
     * alone — keep compiling. Absent, the pane simply shows no contract, which is the correct
     * rendering for a list of people none of whom is renting.
     */
    private val contracts: ContractRepository? = null,
    private val vehicles: VehicleRepository? = null,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val form = MutableStateFlow<RenterFormState?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    /** Which renter the pane shows; `null` falls back to the first visible row. */
    private val selectedRenterId = MutableStateFlow<String?>(null)

    /**
     * Whether a write is in flight, so a double tap on Save cannot enqueue two.
     *
     * An `AtomicBoolean` rather than a `MutableStateFlow<Boolean>`: this is not
     * rendered, so putting it in the state would recompose the screen on a value
     * nothing draws.
     */
    private val saving = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * The renters plus "has the read happened yet", as one flow.
     *
     * A derived flag rather than a `MutableStateFlow<Boolean>`: a flag set in the
     * constructor and cleared on the first emission is a second thing to keep in
     * step with the subscription, and a screen whose flag disagrees with its list
     * is exactly what the flag exists to prevent. Wrapping the *same*
     * `observeRenters()` call the list reads means one subscription, one query.
     */
    private fun rentersWithLoadFlag(): Flow<Pair<List<Renter>, Boolean>> =
        repository.observeRenters()
            .map { it to true }
            .onStart { emit(emptyList<Renter>() to false) }

    /**
     * The screen state.
     *
     * Nesting two `combine` calls rather than one over four flows, for the same
     * reason `KendaraanViewModel` nests: the typed overloads stop at five flows,
     * and nesting keeps every source typed rather than reaching for the `vararg`
     * overload, which would hand the lambda an `Array<Any?>`.
     */
    val uiState: StateFlow<PenyewaUiState> = combine(
        combine(
            rentersWithLoadFlag(),
            query,
            contracts?.observeContracts() ?: flowOf(emptyList()),
            vehicles?.observeVehicles() ?: flowOf(emptyList()),
        ) { (renters, hasEmitted), currentQuery, openContracts, vehicleList ->
            ListSnapshot(
                rows = renters.map { RenterRow(it) },
                query = currentQuery,
                isLoading = !hasEmitted,
                contracts = openContracts,
                vehiclesById = vehicleList.associateBy { it.id },
            )
        },
        form,
        errorMessage,
        selectedRenterId,
    ) { list, currentForm, error, selection ->
        val selected = list.rows.firstOrNull { it.renter.id == selection }
            ?: list.rows.firstOrNull()

        PenyewaUiState(
            rows = list.rows,
            query = list.query,
            form = currentForm,
            errorMessage = error,
            isLoading = list.isLoading,
            selectedRenterId = selected?.renter?.id,
            selectedContract = selected?.renter?.let { renter ->
                list.contractFactsFor(renter, strings)
            },
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
            // `isLoading = true`: before the first emission there is genuinely
            // nothing to show, and the field's `false` default would render
            // "Belum ada Penyewa" — an invitation to add a person the owner has
            // already added — for the frame before the read lands.
            initialValue = PenyewaUiState(isLoading = true),
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
        val isLoading: Boolean,
        val contracts: List<Contract>,
        val vehiclesById: Map<String, Vehicle>,
    ) {
        /**
         * `renters.html:248-279` — the active contract behind [renter].
         *
         * `strings` arrives as a parameter rather than being captured: a data class cannot hold a
         * reference to the outer class, and `RStrings` is what formats the date.
         */
        fun contractFactsFor(renter: Renter, strings: RStrings): RenterContractFacts? {
            val contract = contracts.firstOrNull {
                it.renterId == renter.id && it.endDate == null
            } ?: return null
            val vehicle = vehiclesById[contract.vehicleId] ?: return null
            return RenterContractFacts(
                vehiclePlate = vehicle.plate,
                vehicleText = "${vehicle.brand} ${vehicle.model} ${vehicle.year}",
                rateText = formatRupiah(contract.dailyRate),
                startText = strings.formatExceptionDate(contract.startDate),
                restDay = contract.restDay,
            )
        }
    }

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

    /**
     * Selects the renter whose detail the pane shows.
     *
     * Tapping the same row keeps it selected — the pane is the list's other half, not a thing the
     * owner dismisses (see `KendaraanViewModel.select`).
     */
    fun select(renterId: String) {
        selectedRenterId.value = renterId
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
            avatar = renter.avatar.orEmpty(),
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
                // Blank means "no emoji", so `KnAvatar` falls back to initials rather than
                // drawing an empty disc. Not trimmed to a single character: an emoji can be a
                // sequence (skin tone, zero-width joiner), and cutting it would leave a broken
                // glyph on every screen.
                avatar = current.avatar.trim().ifBlank { null },
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