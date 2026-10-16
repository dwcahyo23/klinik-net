package net.klinik.rental.ui.screens.kontrak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.domain.validation.ContractAlreadyActiveException
import net.klinik.rental.domain.validation.InvalidAmountException
import net.klinik.rental.ui.strings.RStrings

/**
 * Owns the contract screen's state.
 *
 * ## No manual invalidation signal
 *
 * The list comes from `repository.observeContracts()`, which is Room's own
 * invalidation flow. A contract opened by the form re-emits there and the screen
 * updates — no refresh button, no `MutableStateFlow<Long>` of "something changed".
 * The epic context is explicit that a second mechanism can only ever disagree with
 * Room's, and the AC for "visible without a manual refresh" is satisfied by *not
 * having* a refresh path at all.
 *
 * ## Why the names are joined here and not in SQL
 *
 * The list shows the renter's name and the vehicle's plate. Both flows are already
 * needed: the *form* has to offer the renter and vehicle pickers, so
 * `observeRenters()` and `observeVehicles()` are combined regardless. A `@Query`
 * with two JOINs would add a second way to read renters and vehicles next to the
 * existing ones — the two-readers problem AD-5 exists to prevent — and would need
 * a row POJO to carry it. `KendaraanViewModel` already combines several flows in
 * Kotlin, and this is that pattern with one more source.
 *
 * A contract whose renter or vehicle is somehow absent from the observed flows
 * still renders: the plate and the name fall back to the stored id rather than
 * blanking the row. The foreign keys make that unreachable in practice, and a row
 * that showed nothing at all would be a worse outcome than one naming an id.
 *
 * ## Why this screen is not mounted
 *
 * It is not reachable from `MainActivity`, which composes only `KendaraanScreen`.
 * UX-DR13 asks for master-detail in landscape and AR-18 makes `WindowSizeClass` an
 * Activity decision, and no `NavHost` exists in `app/src/main` yet — so this ships
 * fully tested and unreachable, and Story 2.8 wires it. `PenyewaScreen` carries the
 * same deferral for the same reason.
 */
class KontrakViewModel(
    private val contracts: ContractRepository,
    private val renters: RenterRepository,
    private val vehicles: VehicleRepository,
    private val strings: RStrings,
) : ViewModel() {

    private val form = MutableStateFlow<ContractFormState?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    /**
     * Whether a write is in flight, so a double tap on Save cannot enqueue two.
     *
     * An `AtomicBoolean` rather than a `MutableStateFlow<Boolean>`: this is not
     * rendered, so putting it in the state would recompose the screen on a value
     * nothing draws. `PenyewaViewModel` uses the same guard for the same reason,
     * and it matters more here — a second open would be refused by the one-active
     * index and would report the contract's own vehicle as busy.
     */
    private val saving = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * The renters and vehicles, read **once** and shared by everything below.
     *
     * Both the list join and the two pickers need these, and each used to call
     * `observeRenters()`/`observeVehicles()` for itself — four subscriptions to two
     * Room queries. Two reasons that mattered. First, two observers means two reads
     * that can land on opposite sides of a write, and this class's own KDoc argues
     * against "a second way to read renters and vehicles", which is exactly what a
     * second subscription is. Second, the pickers' separate `stateIn` starts at
     * `initialValue = emptyList()`, so for one frame after the sheet mounts the
     * picker rendered "Belum ada penyewa. Tambahkan penyewa dulu di layar Penyewa."
     * about renters already stored — sending the owner to do something they had
     * already done.
     *
     * Cold and shared: every collector below subscribes to this one flow, so they
     * see the *same* emission rather than their own read.
     */
    private val renterRows: Flow<List<Renter>> = renters.observeRenters()

    private val vehicleRows: Flow<List<Vehicle>> = vehicles.observeVehicles()

    /**
     * The screen state.
     *
     * ## Why the three `combine` calls nest
     *
     * The typed overloads stop at five flows, and this screen has five sources —
     * contracts, renters, vehicles, the form and the error message. Nesting keeps
     * every source typed rather than reaching for the `vararg` overload, which would
     * hand the lambda an `Array<Any?>` and push the inference problem into the body.
     * `KendaraanViewModel` and `PenyewaViewModel` nest for exactly this reason.
     *
     * `distinctUntilChanged()` is applied *after* the projection, so a Room
     * emission that produces an identical list does not push a new state downstream
     * and recompose every row for no visible change.
     */
    val uiState: StateFlow<KontrakUiState> = combine(
        contracts.observeContracts(),
        renterRows,
        vehicleRows,
    ) { contractRows, renters, vehicles ->
        // The join, in Kotlin rather than in SQL — see the class KDoc. `associate`
        // once per emission rather than per row, so the whole projection is linear in
        // the number of contracts.
        val renterNames = renters.associate { it.id to it.name }
        val vehiclePlates = vehicles.associate { it.id to it.plate }
        contractRows.map { it.toRow(renterNames, vehiclePlates) }
    }
        .combine(combine(form, errorMessage) { currentForm, error -> FormSnapshot(currentForm, error) }) { rows, formSnapshot ->
            KontrakUiState(
                rows = rows,
                form = formSnapshot.form,
                errorMessage = formSnapshot.error,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            // `WhileSubscribed(5_000)` keeps the combines alive across a rotation
            // instead of re-running Room's three queries on every configuration
            // change.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = KontrakUiState(),
        )

    /**
     * The renters the picker offers.
     *
     * Exposed as its own `StateFlow` rather than read out of [uiState], because the
     * picker needs the full renter *models* and `KontrakUiState` deliberately carries
     * only the names the list draws. Re-deriving them from the state would mean the
     * picker could only offer renters that some contract already names — the opposite
     * of what a picker for opening a *new* contract needs.
     *
     * Read from [renterRows], the same flow the join above uses, so this is a
     * second *collector* rather than a second *subscription* to Room. See that
     * field for why the duplicate mattered.
     */
    val renterOptions: StateFlow<List<Renter>> = renterRows
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** The vehicles the picker offers, for the same reason as [renterOptions]. */
    val vehicleOptions: StateFlow<List<Vehicle>> = vehicleRows
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** A transient confirmation. Success does not change screen state. */
    val snackbarMessage: StateFlow<String?> = message

    /** The interaction half: the open form and the last refusal's message. */
    private data class FormSnapshot(
        val form: ContractFormState?,
        val error: String?,
    )

    // ------------------------------------------------------------------- form

    /**
     * Opens the form on a new contract.
     *
     * The start date opens empty rather than at today. A visible default would be
     * the prototype's hidden one wearing a label — the owner would Save without
     * reading it — and the AC for the form names the start date as one of the
     * things it shows and the owner fills in, so an untouched field is refused with
     * a message on that field rather than quietly written as today.
     *
     * The schedule opens on six days with Minggu as the rest day: six days is the
     * common case for this fleet, and the AC says the rest-day picker defaults to
     * Minggu.
     */
    fun openAddForm() {
        errorMessage.value = null
        form.value = ContractFormState()
    }

    fun closeForm() {
        // Clearing the error too: the sheet it belonged to is gone, and leaving the
        // message behind would put a refusal on the list of a form the owner has
        // already dismissed.
        errorMessage.value = null
        form.value = null
    }

    fun onFormChange(transform: (ContractFormState) -> ContractFormState) {
        form.value = form.value?.let(transform)
    }

    /**
     * Saves the form, translating each typed exception into its Indonesian copy.
     *
     * ## Where each rule is enforced
     *
     * 1. Here — the four missing-field refusals land on their own fields, so the
     *    owner sees the reason while the sheet is still open
     * 2. In `ContractRepository.open` — so a second screen cannot pass a check this
     *    one has
     * 3. In the schema — the two `UNIQUE` indexes are the one-active rule itself,
     *    and they are the only thing that holds when two callers race past 1 and 2
     *
     * Step 1 is a courtesy, not the rule.
     *
     * On success the form closes and the list updates on its own — Room re-emits and
     * `combine` rebuilds the state. There is no manual "now refresh".
     */
    fun saveForm() {
        // A second tap while the first write is still in flight would enqueue a
        // second `open` for the same vehicle. The first would land, the second
        // would be refused by the one-active index, and the owner would be told
        // their own brand-new contract made the car busy. So the guard is here
        // rather than in the UI: the button's `enabled` is a courtesy, this is the
        // rule.
        if (!saving.compareAndSet(false, true)) return

        val current = form.value
        if (current == null) {
            saving.set(false)
            return
        }

        // The refusals that need no database, in the order the fields appear on the
        // sheet. `firstMissingField` is the same function that gates Save and that
        // `toContract` reads, so the button, this path and the projection cannot
        // disagree about what is missing.
        val missing = current.firstMissingField()
        if (missing != null) {
            form.value = current.withRefusal(missing)
            saving.set(false)
            return
        }

        val contract = try {
            current.toContract(contracts.newContractId())
        } catch (e: InvalidAmountException) {
            form.value = current.withRefusal(ContractField.Rate)
            saving.set(false)
            return
        } catch (e: IllegalArgumentException) {
            // A blank id. Not reachable from this form — the ids come from the
            // pickers — so this is reported rather than guessed at.
            errorMessage.value = strings.kontrakErrorUnexpected()
            saving.set(false)
            return
        }

        if (contract == null) {
            // `firstMissingField` above returned null, so this is the two functions
            // disagreeing — the fail-loud direction is to refuse rather than to write
            // a contract built from a field that was not there.
            form.value = current.withRefusal(ContractField.Rate)
            saving.set(false)
            return
        }

        // The instance that was submitted, captured before the write. The success
        // path below closes *this* form and no other — see its comment.
        val submitted = form.value

        viewModelScope.launch {
            try {
                contracts.open(contract)
                // ## Why the close is conditional
                //
                // Room's write is a suspension point, and the owner keeps interacting
                // across it — which is the same fact the catch branches below already
                // act on by re-reading `form.value` instead of writing the stale
                // `current` back. Clearing unconditionally had the mirror-image bug:
                // a form the owner dismissed and reopened while the write was in
                // flight was closed under them, and the contract they had started
                // typing vanished with no explanation.
                //
                // So the form is closed only when it is still the one that was
                // submitted. A reopened form is a different instance and is left
                // alone; its own Save will report the vehicle as busy, which is the
                // truth — the contract that just landed is holding the slot.
                if (form.value === submitted) {
                    form.value = null
                }
                errorMessage.value = null
                message.value = strings.kontrakSaved(current.vehiclePlate)
            } catch (e: ContractAlreadyActiveException) {
                // Re-read the form rather than writing `current` back: the owner may
                // have kept interacting while Room was doing the write, and a stale
                // `copy` would silently discard that.
                val refusal = when (e.side) {
                    ContractAlreadyActiveException.Side.Vehicle -> ContractField.Vehicle
                    ContractAlreadyActiveException.Side.Renter -> ContractField.Renter
                }
                // The refusal lands on the field **if the sheet is still up**. If the
                // owner dismissed it during the write there is no field to write to,
                // and the `?:` would drop the message entirely — a failed write the
                // owner never learns about. So a form-less refusal falls back to the
                // screen's own channel, which the sheet also renders.
                val current = form.value
                if (current != null) {
                    form.value = current.withRefusal(refusal, e.blockingName)
                } else {
                    errorMessage.value = when (e.side) {
                        ContractAlreadyActiveException.Side.Vehicle ->
                            strings.kontrakErrorVehicleBusy(e.blockingName)
                        ContractAlreadyActiveException.Side.Renter ->
                            strings.kontrakErrorRenterBusy(e.blockingName)
                    }
                }
            } catch (e: InvalidAmountException) {
                form.value = form.value?.withRefusal(ContractField.Rate)
            } catch (e: CancellationException) {
                // Re-thrown before the generic handler. `CancellationException`
                // extends `IllegalStateException`, so a cleared ViewModel would
                // otherwise show "Gagal menyimpan. Coba lagi." for a write that was
                // merely cancelled, and structured concurrency would be quietly
                // broken for every caller of this coroutine.
                throw e
            } catch (e: Exception) {
                // The write failed for a reason this screen does not model. It is
                // reported rather than swallowed: the epic context is explicit that
                // a failed write must fail loudly and never look like a fresh
                // account.
                errorMessage.value = strings.kontrakErrorUnexpected()
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

    /**
     * Puts a refusal's message on the field it belongs to.
     *
     * One `when` over [ContractField] rather than four call sites, so adding a
     * field later means adding a branch here and nothing else — the same routing
     * `PenyewaViewModel` does over `BlankRenterFieldException.field`.
     *
     * @param blockingName when the refusal names the entity holding the slot, that
     *   name; otherwise the field's own generic wording.
     */
    private fun ContractFormState.withRefusal(
        field: ContractField,
        blockingName: String? = null,
    ): ContractFormState = when (field) {
        ContractField.Renter -> copy(
            renterError = blockingName
                ?.let(strings::kontrakErrorRenterBusy)
                ?: strings.kontrakErrorBlankRenter(),
        )

        ContractField.Vehicle -> copy(
            vehicleError = blockingName
                ?.let(strings::kontrakErrorVehicleBusy)
                ?: strings.kontrakErrorBlankVehicle(),
        )

        ContractField.StartDate -> copy(dateError = strings.kontrakErrorBlankDate())

        ContractField.Rate -> copy(rateError = strings.kontrakErrorRate())

        ContractField.RestDay -> copy(
            // No slot of its own: `restDay` cannot be blank while six days is
            // selected — `withSchedule` guarantees it and `selectRestDay` only ever
            // sets it — so this branch is unreachable from the form. It is handled
            // rather than thrown because a `when` over an enum must be exhaustive,
            // and the rate slot is where a schedule problem would be least
            // misleading: the schedule and the rate are the two things the owner
            // sets on this form.
            rateError = strings.kontrakErrorRate(),
        )
    }
}
