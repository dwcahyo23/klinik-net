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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.ServiceException
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.domain.validation.ContractAlreadyActiveException
import net.klinik.rental.domain.validation.ContractNotOpenException
import net.klinik.rental.domain.validation.DuplicateServiceExceptionDateException
import net.klinik.rental.domain.validation.InvalidAmountException
import net.klinik.rental.domain.validation.ServiceExceptionBeforeContractStartException
import net.klinik.rental.ui.strings.RStrings
import java.util.concurrent.atomic.AtomicBoolean

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
 * ## Why this screen is reachable
 *
 * Story 2.8 built the shell: `AppNavGraph` routes to this screen from
 * `Destination.Kontrak`. `PenyewaScreen` was reachable the same way, a story
 * earlier than the two before it shipped unreachable.
 *
 * ## Why the exception writes have their own guard
 *
 * [saving] guards the open-contract form and [savingException] guards the exception
 * sheet. **Two `AtomicBoolean`s, not one**: sharing them would let saving a contract
 * block saving an exception, which is the wrong direction — the two sheets cannot be
 * open at once, but the owner can open one, start a write, and open the other, and a
 * cross-blocked write would be refused with nothing on screen to explain it.
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
     * The open contract's exception editor, and the row its impact dialog is
     * waiting on. ViewModel state rather than `remember`, for the reason
     * [ExceptionFormState.pendingRemovalId] gives: a rotation mid-confirmation must
     * not silently drop a dialog the owner had already opened.
     */
    private val exceptionSheet = MutableStateFlow<ExceptionFormState?>(null)
    private val exceptionError = MutableStateFlow<String?>(null)

    /**
     * Whether a write is in flight, so a double tap on Save cannot enqueue two.
     *
     * An `AtomicBoolean` rather than a `MutableStateFlow<Boolean>`: this is not
     * rendered, so putting it in the state would recompose the screen on a value
     * nothing draws. `PenyewaViewModel` uses the same guard for the same reason,
     * and it matters more here — a second open would be refused by the one-active
     * index and would report the contract's own vehicle as busy.
     */
    private val saving = AtomicBoolean(false)

    /**
     * The same guard for the exception sheet's writes, kept **separate** from
     * [saving] — see the class KDoc on why sharing them is the wrong direction.
     */
    private val savingException = AtomicBoolean(false)

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
     * The stored service exceptions, read **once** and shared, for the same reason
     * as [renterRows].
     *
     * Read **once** and shared, for the same reason as [renterRows] — the sheet's
     * rows, the contract list's count badges and [uiState] all need the same list,
     * and three subscriptions to one Room query is three reads that can land on
     * opposite sides of a write.
     */
    private val exceptionRows: Flow<List<ServiceException>> =
        contracts.observeServiceExceptions()

    /**
     * Contracts plus "has the read happened yet", as one flow.
     *
     * A derived flag rather than a `MutableStateFlow<Boolean>`: a flag set in the
     * constructor and cleared on the first emission is a second thing to keep in
     * step with the subscription, and a screen whose flag disagrees with its list
     * is exactly what the flag exists to prevent. Wrapping the *same*
     * `observeContracts()` call the list reads means one subscription, one query.
     */
    private fun contractsWithLoadFlag(): Flow<Pair<List<Contract>, Boolean>> =
        contracts.observeContracts()
            .map { it to true }
            .onStart { emit(emptyList<Contract>() to false) }

    /**
     * The screen state.
     *
     * ## Why the `combine` calls nest
     *
     * The typed overloads stop at five flows, and this screen now has **eight**
     * sources: contracts, renters, vehicles, service exceptions, the open form, the
     * error message, the exception sheet, and the exception sheet's own failure
     * message. Nesting keeps every source typed rather than reaching for the `vararg`
     * overload, which would hand the lambda an `Array<Any?>` and push the inference
     * problem into the body. `KendaraanViewModel` and `PenyewaViewModel` nest for
     * exactly this reason.
     *
     * `distinctUntilChanged()` is applied *after* the projection, so a Room
     * emission that produces an identical list does not push a new state downstream
     * and recompose every row for no visible change.
     */
    val uiState: StateFlow<KontrakUiState> = combine(
        contractsWithLoadFlag(),
        renterRows,
        vehicleRows,
        exceptionRows,
    ) { (contractRows, hasEmitted), renters, vehicles, exceptions ->
        // The joins, in Kotlin rather than in SQL — see the class KDoc. `associate`
        // once per emission rather than per row, so the whole projection is linear in
        // the number of contracts and of exceptions.
        val renterNames = renters.associate { it.id to it.name }
        val vehiclePlates = vehicles.associate { it.id to it.plate }
        // One pass to count, rather than a filter per row: the contract list is
        // walked once and every row reads its number from this map.
        val exceptionCounts = exceptions.groupingBy { it.contractId }.eachCount()
        JoinSnapshot(
            rows = contractRows.map { it.toRow(renterNames, vehiclePlates, exceptionCounts) },
            isLoading = !hasEmitted,
            exceptions = exceptions,
        )
    }
        .combine(combine(form, errorMessage) { currentForm, error -> FormSnapshot(currentForm, error) }) { join, formSnapshot ->
            ListSnapshot(
                rows = join.rows,
                isLoading = join.isLoading,
                exceptions = join.exceptions,
                form = formSnapshot.form,
                error = formSnapshot.error,
            )
        }
        .combine(
            combine(exceptionSheet, exceptionError) { sheet, error -> ExceptionSnapshot(sheet, error) },
        ) { list, exceptionSnapshot ->
            KontrakUiState(
                rows = list.rows,
                form = list.form,
                errorMessage = list.error,
                isLoading = list.isLoading,
                exceptionSheet = exceptionSnapshot.sheet,
                // The rows are **derived**, filtered to the open contract. With no
                // sheet up they stay empty rather than carrying every contract's
                // exceptions in the screen's state for a list nobody is drawing —
                // and it means the rows cannot go stale, because there is no copy to
                // be stale: Room re-emits and `combine` rebuilds.
                exceptionRows = exceptionSnapshot.sheet
                    ?.let { sheetState ->
                        list.exceptions
                            .filter { it.contractId == sheetState.contractId }
                            .map { it.toRow() }
                    }
                    .orEmpty(),
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            // `WhileSubscribed(5_000)` keeps the combines alive across a rotation
            // instead of re-running Room's four queries on every configuration
            // change.
            started = SharingStarted.WhileSubscribed(5_000),
            // `isLoading = true`: before the first emission there is genuinely
            // nothing to show, and the field's `false` default would render
            // "Belum ada Kontrak" — an invitation to open a contract the owner has
            // already opened — for the frame before the read lands.
            initialValue = KontrakUiState(isLoading = true),
        )

    /** What the data half of the combine produces. */
    private data class JoinSnapshot(
        val rows: List<ContractRow>,
        val isLoading: Boolean,
        /**
         * Every stored exception, kept raw here so the layer above can filter to
         * the open contract.
         *
         * Carried rather than projected in this layer because the projection needs
         * the sheet's `contractId`, which is interaction state and is joined in a
         * later layer — the same reason `FormSnapshot` exists below.
         */
        val exceptions: List<ServiceException>,
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

    /**
     * The exception sheet's **own** failure message.
     *
     * Separate from `KontrakUiState.errorMessage` because the sheet is a different
     * **window**: a snackbar rendered in the screen's composition is behind the sheet
     * and would be invisible while the sheet is up, which is exactly the AC's
     * "the failure renders inside the sheet" clause. So it is its own flow rather than
     * a field on the combined state — a field would force the screen and the sheet to
     * compete for one slot, and the screen's banner is suppressed anyway while the
     * sheet is mounted.
     *
     * Not `stateIn`'d and not collected inside `uiState`, deliberately: it is not part
     * of the screen's structural state, and folding it in would recompose every
     * consumer of `uiState` when a message appears.
     */
    val exceptionErrorMessage: StateFlow<String?> = exceptionError

    /** The interaction half: the open form and the last refusal's message. */
    private data class FormSnapshot(
        val form: ContractFormState?,
        val error: String?,
    )

    /**
     * The second layer's carrier, so the screen's own composition is not duplicated.
     *
     * A plain container rather than `KontrakUiState` itself, for the same reason
     * [JoinSnapshot] and [FormSnapshot] exist: `combine`'s lambda is where the *next*
     * state is built, and naming the two halves lets the intermediate value say what
     * it is instead of carrying fields that are about to be re-decided.
     */
    private data class ListSnapshot(
        val rows: List<ContractRow>,
        val isLoading: Boolean,
        val exceptions: List<ServiceException>,
        val form: ContractFormState?,
        val error: String?,
    )

    /**
     * The exception sheet's own two inputs.
     *
     * Separate from [ListSnapshot] because they are interaction state rather than
     * data, and joining them in the same layer would put a `MutableStateFlow` on the
     * same side of the `combine` as a Room `Flow` — which is how a form edit ends up
     * waiting on a database emission.
     */
    private data class ExceptionSnapshot(
        val sheet: ExceptionFormState?,
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

    // ------------------------------------------------------- service exceptions

    /**
     * Opens the exception sheet on the contract behind [row].
     *
     * The label is resolved **here**, from the flows this class already observes, and
     * stored as a string on the sheet's state — the same reason `ContractFormState`
     * holds `renterName` and `vehiclePlate` beside their ids. A sheet that looked
     * either half up again would be a second reader of `renter` and `vehicle`.
     *
     * `isReadOnly` is `contract.isOpen`, the derivation every "is this still
     * editable?" question asks — there is no stored flag and no `status` column.
     *
     * The sheet's form opens **empty**: a pre-filled date would be a default the
     * owner never chose, and a pre-filled reason would be inventing text in their
     * name. `ContractFormState.startDate` refuses for the same reason.
     */
    fun openExceptions(row: ContractRow) {
        exceptionError.value = null
        exceptionSheet.value = ExceptionFormState(
            contractId = row.contract.id,
            contractLabel = "${row.renterName} · ${row.vehiclePlate}",
            isReadOnly = !row.contract.isOpen,
        )
    }

    /**
     * Closes the sheet.
     *
     * The sheet's own failure message goes with it, for the reason `closeForm` clears
     * `errorMessage`: a refusal raised while the sheet was up must not survive onto
     * the contract list, where it would read as a current failure with nothing left
     * to explain it.
     */
    fun closeExceptions() {
        exceptionError.value = null
        exceptionSheet.value = null
    }

    /** Applies a transform to the sheet's state, if the sheet is open. */
    fun onExceptionChange(transform: (ExceptionFormState) -> ExceptionFormState) {
        exceptionSheet.value = exceptionSheet.value?.let(transform)
    }

    /**
     * Marks the sheet's day as not billed.
     *
     * ## Where each rule is enforced
     *
     * 1. Here — the missing-field refusals land on their own fields, so the owner
     *    sees the reason while the sheet is still open
     * 2. In `ContractRepository.addServiceException` — so a second screen cannot pass
     *    a check this one has
     * 3. In the schema — the `UNIQUE(contractId, date)` index is the duplicate rule
     *    itself, and it is the only thing that holds when two callers race past 1
     *    and 2
     *
     * Step 1 is a courtesy, not the rule.
     *
     * On success the add fields are cleared and the list updates on its own — Room
     * re-emits and `combine` rebuilds the rows. There is no manual "now refresh",
     * and no row is written into the sheet's state, so there is nothing to write back.
     */
    fun addException() {
        // The same double-tap guard as `saveForm`, and a **separate** flag — see the
        // class KDoc. Without it a second Add is enqueued for the same day, the first
        // lands, and the second is refused by the unique index: the owner is told
        // about a duplicate of the row they had just created.
        if (!savingException.compareAndSet(false, true)) return

        val current = exceptionSheet.value
        if (current == null) {
            savingException.set(false)
            return
        }

        // A read-only sheet refuses here as well as in the repository. The sheet's
        // add block is *unmounted* in that state, so this branch is unreachable
        // through the UI — and it is kept because the repository refuses anyway, and
        // a caller holding this ViewModel must not be able to write around the UI
        // half of the rule. Two halves, one fact, both asked.
        if (current.isReadOnly) {
            exceptionSheet.value = current.copy(dateError = strings.kontrakErrorExceptionEnded())
            savingException.set(false)
            return
        }

        val missing = current.firstMissingExceptionField()
        if (missing != null) {
            exceptionSheet.value = current.withExceptionRefusal(missing)
            savingException.set(false)
            return
        }

        val exception = current.toServiceException(contracts.newServiceExceptionId())
        if (exception == null) {
            // `firstMissingExceptionField` above returned null, so this is the two
            // functions disagreeing — the fail-loud direction is to refuse rather
            // than write an exception built from a field that was not there.
            exceptionSheet.value = current.withExceptionRefusal(ExceptionField.Reason)
            savingException.set(false)
            return
        }

        // The instance that was submitted, captured before the write. The success
        // path below clears *this* form's fields and no other — see its comment.
        val submitted = exceptionSheet.value

        viewModelScope.launch {
            try {
                contracts.addServiceException(exception)
                // Conditional, for the reason `saveForm`'s close is: Room's write is
                // a suspension point and the owner keeps interacting across it. A sheet
                // the owner dismissed and reopened while the write was in flight is a
                // different instance and is left alone — clearing unconditionally
                // would wipe the reason they had started typing into it.
                //
                // `!= null` as well as `=== submitted`: the identity check alone is a
                // nullable comparison, and the sheet can legitimately be *gone* by now,
                // in which case there is nothing to clear and nothing to write.
                val live = exceptionSheet.value
                if (live != null && live === submitted) {
                    exceptionSheet.value = live.copy(addDate = null, addReason = "")
                }
                exceptionError.value = null
                message.value = strings.kontrakExceptionAdded(
                    strings.formatExceptionDate(exception.date),
                )
            } catch (e: DuplicateServiceExceptionDateException) {
                // Re-read the sheet rather than writing `current` back: the owner may
                // have kept interacting while Room was doing the write, and a stale
                // `copy` would silently discard that.
                writeExceptionRefusal(ExceptionField.Date, strings.kontrakErrorExceptionDuplicate(e.date))
            } catch (e: ServiceExceptionBeforeContractStartException) {
                writeExceptionRefusal(
                    ExceptionField.Date,
                    strings.kontrakErrorExceptionBeforeStart(e.startDate),
                )
            } catch (e: ContractNotOpenException) {
                // This one has no field of its own: it is about the contract, and the
                // sheet's whole add block is about to go away because the contract has
                // ended. It lands on the sheet's own banner for the same reason the
                // screen's cannot show it — a sheet is a separate window.
                val live = exceptionSheet.value
                if (live != null) {
                    exceptionSheet.value = live.copy(
                        isReadOnly = true,
                        pendingRemovalId = null,
                    )
                }
                exceptionError.value = strings.kontrakErrorExceptionEnded()
            } catch (e: CancellationException) {
                // Re-thrown before the generic handler. `CancellationException`
                // extends `IllegalStateException`, so a cleared ViewModel would
                // otherwise show a failure message for a write that was merely
                // cancelled, and structured concurrency would be quietly broken for
                // every caller of this coroutine.
                throw e
            } catch (e: Exception) {
                // The write failed for a reason this screen does not model. It is
                // reported rather than swallowed: the epic context is explicit that a
                // failed write must fail loudly and never look like a fresh account.
                exceptionError.value = strings.kontrakErrorExceptionUnexpected()
            } finally {
                savingException.set(false)
            }
        }
    }

    /**
     * Opens the impact dialog for one row.
     *
     * The "which row" flag is ViewModel state rather than a `remember`, so a
     * rotation in the middle of a confirmation does not silently drop a dialog the
     * owner had opened — and the dialog's visibility is state rather than a
     * composable's own memory, which is what lets the sheet test assert "the dialog
     * is up because the ViewModel says so".
     */
    fun requestRemoveException(id: String) {
        val live = exceptionSheet.value ?: return
        exceptionSheet.value = live.copy(pendingRemovalId = id)
    }

    /**
     * The confirmed half of the remove.
     *
     * A no-op when no row is pending, so a double tap on the dialog's confirm does
     * not enqueue two deletes — the second would delete nothing and report nothing,
     * which is harmless but not free, and a sheet that reported a success twice would
     * read as a bug to the owner.
     */
    fun confirmRemoveException() {
        val current = exceptionSheet.value
        val pendingId = current?.pendingRemovalId ?: return

        if (!savingException.compareAndSet(false, true)) return

        val contractId = current.contractId
        // The pending row, read **before** the write. By the time the delete lands
        // the row is gone from Room's flow and the date would be unrecoverable, and
        // the confirmation is the last place the owner can be told *which* day
        // stopped being free.
        //
        // Read from `uiState` rather than from a second query, because that is the
        // very list the sheet is drawing — the owner confirmed the dialog about *that*
        // row, so the confirmation names that row's date rather than a fresh lookup's
        // idea of it. A read after the write would be one emission behind.
        val pendingDate = uiState.value.exceptionRows
            .firstOrNull { it.exception.id == pendingId }
            ?.exception
            ?.date
        exceptionSheet.value = current.copy(pendingRemovalId = null)

        viewModelScope.launch {
            try {
                contracts.removeServiceException(pendingId, contractId)
                exceptionError.value = null
                message.value = strings.kontrakExceptionRemoved(pendingDate)
            } catch (e: ContractNotOpenException) {
                val live = exceptionSheet.value
                if (live != null) {
                    exceptionSheet.value = live.copy(
                        isReadOnly = true,
                        pendingRemovalId = null,
                    )
                }
                exceptionError.value = strings.kontrakErrorExceptionEnded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exceptionError.value = strings.kontrakErrorExceptionUnexpected()
            } finally {
                savingException.set(false)
            }
        }
    }

    /** The dismissed dialog alone changes nothing but the pending flag. */
    fun cancelRemoveException() {
        exceptionSheet.value = exceptionSheet.value?.copy(pendingRemovalId = null)
    }

    /**
     * Clears the sheet's own failure banner.
     *
     * Its own handler rather than an alias of [consumeError]: the two dismiss
     * different messages, and aliasing them is how one message ends up cleared
     * through the other's setter while staying on screen.
     */
    fun consumeExceptionError() {
        exceptionError.value = null
    }

    /**
     * Writes a refusal onto one of the sheet's fields, re-reading the live state.
     *
     * The re-read is the same rule the open-form path follows: Room's write is a
     * suspension point, the owner keeps interacting across it, and a stale `copy`
     * written back would discard whatever they typed in the meantime. When the sheet
     * is gone there is no field to write to, so the message falls back to the
     * sheet's own banner channel rather than being dropped — a failed write the
     * owner never learns about is the outcome the epic context forbids.
     */
    private fun writeExceptionRefusal(field: ExceptionField, message: String) {
        val live = exceptionSheet.value
        if (live != null) {
            exceptionSheet.value = live.withExceptionRefusal(field, message)
        } else {
            exceptionError.value = message
        }
    }

    /**
     * Puts a refusal's message on the field it belongs to.
     *
     * One `when` over [ExceptionField] rather than two call sites, so adding a field
     * later means adding a branch here and nowhere else — the same routing
     * [withRefusal] does for the open-contract form.
     */
    private fun ExceptionFormState.withExceptionRefusal(
        field: ExceptionField,
        message: String? = null,
    ): ExceptionFormState = when (field) {
        ExceptionField.Date -> copy(
            dateError = message ?: strings.kontrakErrorExceptionBlankDate(),
        )

        ExceptionField.Reason -> copy(
            reasonError = message ?: strings.kontrakErrorExceptionBlankReason(),
        )
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
