package net.klinik.rental.ui.screens.tunggakan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.klinik.rental.data.repository.ContractArrears
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.strings.RStrings
import java.time.Clock
import java.time.LocalDate

/**
 * Owns the arrears list's state — Story 4.1.
 *
 * ## Why the list re-emits on any write, with no refresh anywhere
 *
 * Because the whole point of this screen is that the number is trustworthy. The source is
 * `PaymentRepository.observeArrearsForAllContracts`, which sits on Room's invalidation
 * tracker: a deposit saved, a contract ended or a contract settled all re-emit here and the
 * list moves with no signal of its own. A manual refresh button would be a second mechanism,
 * and the epic context is explicit that a second mechanism can only ever disagree with the
 * first — the owner would press it and wonder whether the number before or after was right.
 *
 * ## Why the arrears and the names are joined here rather than in SQL
 *
 * Because the **figures** come from one call per contract inside the repository, and this class
 * only adds the two *static* names a row displays — a person's name and a car's plate. A stale
 * name cannot make a wrong number look right, so joining them downstream is safe; joining the
 * **figures** downstream would not be, and `ContractArrears` is what stops that.
 *
 * Same argument `KontrakViewModel` gives for its own join, and the same reason this does not
 * add a `@Query` with two JOINs: that would put a second reader of `renter` and `vehicle` next
 * to the existing ones.
 *
 * ## Why the clock is handed to the repository rather than read here
 *
 * Because "today" must be read **per emission**, not once when this ViewModel was built. An
 * app left open across midnight would otherwise show yesterday's figure until the owner left
 * and came back, and nothing would say so. Passing the `Clock` down puts the read where the
 * emission happens; passing a `LocalDate` up freezes it at construction.
 */
class TunggakanViewModel(
    private val payments: PaymentRepository,
    renterRepository: RenterRepository,
    vehicleRepository: VehicleRepository,
    private val strings: RStrings,
    private val clock: Clock,
    /**
     * The business name a reminder quotes — Story 4.4's single source.
     *
     * Injected as a suspending read rather than a `BusinessIdentityRepository`, so this class
     * depends on the one value it needs instead of on a storage layer.
     *
     * **Required, not defaulted.** A default would let a construction site omit it and ship a
     * reminder that names no business — the "nullable-for-tests" shape that let Story 3.5 ship a
     * repository with three unwired DAOs while every test passed. Omitting it is now a compile
     * error at each of the three call sites.
     */
    private val businessName: suspend () -> String,
    /**
     * Overrides the arrears source. **For tests only.**
     *
     * A seam because `PaymentRepository` is a `final class` and cannot be stubbed, and the
     * failure path is precisely the half a hand-built state cannot exercise: the screen's
     * `isFailed` branch is easy to assert, and the route *to* it is what broke. Defaulted so
     * production never has to know this exists, and named `arrearsSource` rather than
     * something load-bearing-sounding so a caller reading the signature can tell it apart
     * from the constructor's real collaborators.
     */
    private val arrearsSourceOverride: (() -> Flow<List<ContractArrears>>)? = null,
) : ViewModel() {

    /** What the arrears source has produced, tagged so "not yet read" is a value. */
    private sealed interface Source {
        /**
         * Not yet read. Carries no entries, so the screen cannot draw a zero row from it.
         *
         * A sealed type rather than a nullable list: "nothing owed" and "not read yet" are
         * both an empty list at runtime and must not be the same branch, and this screen has
         * the strongest reason of any in the app — an empty *arrears* list is the claim
         * "nobody owes you anything".
         */
        data object Loading : Source

        /** Read, possibly to nothing owed. */
        data class Loaded(val entries: List<ContractArrears>) : Source

        /**
         * The read failed, and the list must not be believed.
         *
         * A member rather than a `Boolean` beside the rows, because the two claims are the
         * same claim: an empty list drawn after a failure is the app asserting that nobody
         * owes anything, which is the one thing it must never do without having looked.
         */
        data object Failed : Source
    }

    /**
     * The banner text a source carries, or `null`.
     *
     * On the sealed type rather than in the caller, so "which state carries a message" is
     * one question with one answer — a `when` over three members that the compiler can
     * check is exhaustive, rather than three separate `?.let` branches that could disagree.
     */
    private fun Source.bannerOrNull(strings: RStrings): String? =
        if (this is Source.Failed) strings.tunggakanError() else null

    /**
     * The four data reads plus the selection, as one value.
     *
     * Nested because the typed `combine` overloads stop at five flows and this screen now has
     * six once the risk chip is a source. `combine(…) { … }.combine(…)` keeps every parameter
     * typed; the `vararg` overload would hand the lambda an `Array<Any?>` and push the inference
     * problem into the body, which is what this class's KDoc already warns about.
     */
    private data class SourceSnapshot(
        val source: Source,
        val selection: String?,
        val renters: List<Renter>,
        val vehicles: List<Vehicle>,
    )

    /**
     * Which row the landscape detail pane is showing.
     *
     * Held as its own flow rather than folded into the projection, so reading it inside another
     * `combine`'s lambda cannot emit a state carrying the *previous* selection — the same
     * reason `KontrakViewModel` keeps each of its five sheets as a real source.
     */
    private val selectedId = MutableStateFlow<String?>(null)

    /** The risk chip on the top bar — `arrears.html:29-35`. */
    private val riskFilter = MutableStateFlow(RiskFilter.Semua)

    /**
     * Bumped by [consumeError] to ask the source to try again.
     *
     * A counter rather than a boolean because the same request twice must still re-subscribe
     * — two dismissals in a row are two requests, not one repeated one, and a `Boolean` would
     * make the second a no-op that leaves the banner up.
     */
    private val arrearsRetry = MutableStateFlow(0)

    /**
     * The reminder dialog, or `null` when it is closed — Story 4.2.
     *
     * Its own flow beside the rows, not a field on them: a message is about one row and has its
     * own lifetime, and closing it must not disturb the list or the selection. The same argument
     * `KontrakViewModel` gives for keeping each of its sheets as a real source.
     */
    private val reminder = MutableStateFlow<ReminderUi?>(null)

    /**
     * The contract whose reminder the owner last asked to open, or `null` once dismissed.
     *
     * A **request token**, not the dialog's own state: `openReminder` suspends while it reads the
     * business name, and `reminder.value` is still `null` during that first read — so checking
     * the dialog's state cannot distinguish "not yet built" from "dismissed". This records the
     * intent before the suspend and is cleared by [dismissReminder], which is what makes the
     * check meaningful.
     */
    private val reminderRequest = MutableStateFlow<String?>(null)

    /**
     * The arrears source, tagged and with its failures carried as a **value**.
     *
     * ## Why the failure is a `Source` and not a side channel
     *
     * Because a `MutableStateFlow` written inside `catch` never reaches the screen. The
     * `combine` below only re-emits when one of **its** inputs does, so a message written to
     * a flow it does not read changes nothing that anyone can see: the screen would keep
     * drawing its skeleton and the owner would read "no data" as "nothing owed" — which is
     * the exact confusion the banner exists to prevent, reached by adding a banner.
     *
     * So the failure *is* the value. `Source.Failed` is part of the same sealed hierarchy
     * the success path is, so it flows through the combine like any other emission and lands
     * in `uiState` by construction rather than by a handler somebody has to remember.
     *
     * ## Why `asOf` is not a parameter here
     *
     * It is read **inside** the repository, per emission. Passing `LocalDate.now(clock)` as
     * an argument froze it at construction, so an app left open across midnight showed
     * yesterday's figure until the owner left and returned — and nothing said so.
     */
    private val arrearsSource: Flow<Source> =
        arrearsRetry.flatMapLatest { _ ->
            (arrearsSourceOverride?.invoke() ?: payments.observeArrearsForAllContracts(clock))
                // `onStart` carries the *loading* value rather than branching the screen on
                // emptiness: Room's first emission is an empty list, so a screen that treated
                // that as "nothing owed" would show its empty state on every single load — the
                // one failure this screen can least afford, since it tells the owner nobody owes
                // them anything.
                .map<List<ContractArrears>, Source> { entries -> Source.Loaded(entries) }
                .onStart { emit(Source.Loading) }
                // The failure is a **value** on the same sealed hierarchy the rows travel, so it
                // reaches the screen through the same `combine` they do.
                //
                // ## Why `catch` that does not re-throw
                //
                // A `catch` that re-throws emits `Source.Failed` and *then* terminates the flow;
                // under `stateIn(WhileSubscribed)` that completes the sharing coroutine, so the
                // value it just emitted is never delivered and `uiState` freezes on its last
                // read — the screen sits on a skeleton forever, which is the very failure the
                // banner exists to prevent.
                //
                // ## Why not `retryWhen { _, _ -> true }`
                //
                // Because a source that fails immediately fails again immediately, and
                // re-subscribing in a tight loop spins the CPU while showing a banner — a worse
                // failure than the frozen skeleton it replaces, and one that would not stop until
                // the owner left the screen. Retrying on a timer is a refresh mechanism wearing
                // a retry's name.
                //
                // So the flow **completes normally** after the failure, and [arrearsRetry]
                // re-subscribes it: the owner dismisses the banner, the read is attempted again,
                // and it either comes back with real figures or fails again visibly. Recovery is
                // one deliberate act, not a spin.
                .catch { emit(Source.Failed) }
        }

    val uiState: StateFlow<TunggakanUiState> = combine(
        combine(
            arrearsSource,
            renterRepository.observeRenters(),
            vehicleRepository.observeVehicles(),
            selectedId,
        ) { source, renters, vehicles, selection -> SourceSnapshot(source, selection, renters, vehicles) },
        reminder,
        riskFilter,
    ) { snapshot, currentReminder, currentFilter ->
        TunggakanUiState(
            rows = when (val source = snapshot.source) {
                // Nothing rendered while loading, and **nothing rendered on failure**: the
                // banner beside an empty list would state "nobody owes you anything" and
                // then apologise for it, which is worse than saying nothing.
                Source.Loading, is Source.Failed -> emptyList()
                is Source.Loaded -> source.entries.toRows(
                    renterNames = snapshot.renters.associate { it.id to it.name },
                    // The phone joins the **same** lookup the name does, so the recipient
                    // arrives with the figures rather than from a second read — the rule the
                    // reminder's own KDoc gives.
                    renterPhones = snapshot.renters.associate { it.id to it.phone },
                    vehiclePlates = snapshot.vehicles.associate { it.id to it.plate },
                    strings = strings,
                )
            },
            riskFilter = currentFilter,
            // Carried through unchanged rather than re-derived, so a row that stops owing
            // leaves the pane showing its prompt instead of silently retargeting to a
            // different renter the moment a payment lands.
            selectedContractId = snapshot.selection,
            isLoading = snapshot.source is Source.Loading,
            isFailed = snapshot.source is Source.Failed,
            // **Derived from the source, not from a second channel.** The banner is a fact
            // about which `Source` this is, so reading it from `Source.Failed` keeps one
            // derivation; a `MutableStateFlow` written beside the flow was the original
            // defect, because a message in a flow the `combine` does not read reaches
            // nobody. A dismissal does not set this to null — it re-emits `Loaded`, which
            // drops the banner because the read no longer says it failed.
            errorMessage = snapshot.source.bannerOrNull(strings),
            reminder = currentReminder,
        )
    }
        // After the projection, so a Room emission that produces an identical list does not
        // recompose every row for no visible change.
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            // `WhileSubscribed(5_000)` keeps the combine alive across a rotation instead of
            // re-running the whole allocation pass on every configuration change — which
            // matters more here than on a list screen, because recomputing arrears for every
            // contract is the expensive part of this screen.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TunggakanUiState(isLoading = true),
        )

    /**
     * Selects one row for the landscape detail pane, or clears it with `null`.
     *
     * Tapping the **same** row again clears the selection rather than being a no-op: in
     * landscape the pane is beside the list rather than behind it, so the owner needs a way
     * to dismiss it without leaving the screen, and a row that cannot be un-tapped is a
     * detail pane that is stuck.
     */
    fun onSelect(contractId: String?) {
        selectedId.value = if (selectedId.value == contractId) null else contractId
    }

    /** The risk chip. A filter over the list, not a navigation. */
    fun onRiskFilterChange(value: RiskFilter) {
        riskFilter.value = value
    }

    /**
     * Dismisses the banner by asking the source to try again.
     *
     * Not a message setter, because the message is not held here: it is derived from
     * `Source.Failed`. A dismiss that cleared a field the screen does not read would leave
     * the banner up — the original defect in a second costume. Re-subscribing instead both
     * hides the banner and puts the screen back on real data, so a dismissal that succeeds
     * is one that also recovered.
     */
    fun consumeError() {
        arrearsRetry.value = arrearsRetry.value + 1
    }

    // --------------------------------------------------------------- the reminder

    /**
     * Opens the reminder dialog for [contractId], with the message already composed.
     *
     * ## What is snapshot-consistent here, and what is not — stated plainly
     *
     * **The figures are.** `amount`, `days` and the row's `figureText` come from one
     * `ContractArrears` emission in `PaymentRepository.observeArrearsForAllContracts`, and the
     * message quotes that string rather than re-deriving it.
     *
     * **The names, plate and phone are joined, not snapshotted.** They arrive from
     * `observeRenters()` and `observeVehicles()` — separate `combine` inputs — so a renter
     * renamed between two emissions can produce a row whose name is newer than its figure. That
     * is safe for the *message* because a name cannot make an amount wrong, but it is not the
     * single-snapshot property the acceptance criterion describes, and a reader who believed it
     * was would be trusting a sentence rather than the code. The criterion's own concern — one
     * figure, one moment — is satisfied by the figures; the labels are a join.
     *
     * ## Why the business name is read here
     *
     * Because it is not a figure. A business name changing mid-session cannot make the amount
     * wrong, and reading it at compose time means a name corrected in Settings appears in the
     * very next message without a restart.
     *
     * @throws IllegalArgumentException never — a blank name is reported as a refusal on the
     *   dialog rather than thrown, because this runs in a UI callback.
     */
    fun openReminder(contractId: String) {
        val row = uiState.value.rows.firstOrNull { it.contractId == contractId } ?: return

        // Recorded *before* the suspend, so the launch can tell "still wanted" from "dismissed
        // while I was reading". A request token rather than the dialog's own state, because the
        // dialog has not been built yet — the whole point of the read below.
        reminderRequest.value = contractId

        viewModelScope.launch {
            // A blank name means the business identity is not configured. Composing anyway would
            // send "ini pengingat dari ." to a tenant, so the dialog opens with a refusal
            // instead — a wiring or configuration problem must be visible to the owner, not
            // delivered to the person they bill.
            val business = businessName()

            // The owner can dismiss while the name read is in flight. Without this the launch
            // lands afterwards and re-opens a dialog they closed — the defect Story 3.5 fixed in
            // `saveCorrection`, in the same shape. Checked against the *request*, not against
            // `reminder.value`, which is still null on the first open.
            if (reminderRequest.value != contractId) return@launch

            if (business.isBlank()) {
                // No message to compose. The dialog still opens, carrying the refusal, so the
                // owner learns why Send is unavailable rather than finding a dead button.
                reminder.value = ReminderUi(
                    contractId = contractId,
                    phone = row.renterPhone,
                    text = "",
                    errorMessage = strings.tunggakanReminderNoIdentity(),
                )
                return@launch
            }

            reminder.value = ReminderUi(
                contractId = contractId,
                phone = row.renterPhone,
                text = ReminderMessage.build(
                    renter = row.renterName,
                    businessName = business,
                    plate = row.vehiclePlate,
                    figureText = row.figureText,
                    template = { renter, name, plate, figure ->
                        strings.tunggakanReminderMessage(renter, name, plate, figure)
                    },
                ),
            )
        }
    }

    /**
     * Carries an edit to the dialog, rebuilding the link with it.
     *
     * The rebuild happens in `ReminderUi.withText`, so there is no path by which the text
     * changes and the link does not — which is the criterion's whole requirement.
     */
    fun onReminderChange(text: String) {
        reminder.value = reminder.value?.withText(text)
    }

    /**
     * Refuses an empty message rather than copying or sending nothing.
     *
     * Kept because the dialog's own enablement reads it, and because a caller reaching this
     * directly gets the same answer `sendReminder` gives.
     */
    fun confirmReminderText(): Boolean {
        val current = reminder.value ?: return false
        if (!current.canSend) {
            reminder.value = current.copy(
                errorMessage = if (current.text.isBlank()) {
                    strings.tunggakanReminderEmpty()
                } else {
                    strings.tunggakanReminderNoPhone()
                },
            )
            return false
        }
        return true
    }

    /**
     * Records that the text was copied.
     *
     * The copy itself is the composable's — it needs a `Context` — and this only carries the
     * confirmation into the state, so the snackbar is drawn by the same `combine` everything
     * else is.
     *
     * ## Why an empty message is refused here too
     *
     * Because the disabled button is a courtesy, not the rule. The epic context is explicit that
     * two screens can pass the same UI check, so "Copy copies nothing" has to hold below the
     * control — the same argument `BlankIdentityNameException` makes one layer down.
     */
    fun onReminderCopied() {
        val current = reminder.value ?: return
        if (!current.canSend) {
            reminder.value = current.copy(errorMessage = strings.tunggakanReminderEmpty())
            return
        }
        reminder.value = current.copy(copiedMessage = strings.tunggakanReminderCopied())
    }

    /**
     * The link the owner asked to open, or `null` when there is nothing to send.
     *
     * A one-shot value rather than a callback, because opening a URL needs an `Intent` and a
     * `Context` — both composition-side. The screen observes this, launches, and calls
     * [onReminderLinkOpened] so the same link cannot be opened twice on one recomposition.
     *
     * Refuses an emptied message: the refusal lands on the dialog's field, and nothing opens.
     */
    private val _pendingLink = MutableStateFlow<String?>(null)
    val pendingLink: StateFlow<String?> = _pendingLink

    /**
     * Validates the message and hands its link to the screen.
     *
     * Two refusals, both landing on the dialog's field rather than throwing — a failure the
     * owner can act on belongs on the field they can fix:
     *
     *  - an emptied message;
     *  - a phone that cannot be addressed, which `ReminderUi.link` reports as `null`. Without
     *    this the Send button would appear to work and open nothing.
     */
    fun sendReminder() {
        val current = reminder.value ?: return
        if (!current.canSend) {
            val message = if (current.text.isBlank()) {
                strings.tunggakanReminderEmpty()
            } else {
                // The text is fine; the recipient is not. Naming the phone field's own problem
                // keeps the two refusals distinguishable, which matters because the fixes differ.
                strings.tunggakanReminderNoPhone()
            }
            reminder.value = current.copy(errorMessage = message)
            return
        }
        _pendingLink.value = current.link
    }

    /** The screen consumed [pendingLink] and launched it. */
    fun onReminderLinkOpened() {
        _pendingLink.value = null
    }

    /**
     * WhatsApp could not be opened — most likely it is not installed.
     *
     * Reported on the dialog rather than swallowed, because the owner's next move is the Copy
     * button beside the message and nothing would otherwise tell them it is there.
     */
    fun onReminderOpenFailed() {
        _pendingLink.value = null
        reminder.value = reminder.value?.copy(
            errorMessage = strings.tunggakanReminderOpenFailed(),
        )
    }
    /** Closes the dialog and its message with it. */
    fun dismissReminder() {
        // Cleared **before** the state, so a launch awaiting the business name sees the
        // cancellation rather than the dialog it is about to build.
        reminderRequest.value = null
        reminder.value = null
        _pendingLink.value = null
    }
}