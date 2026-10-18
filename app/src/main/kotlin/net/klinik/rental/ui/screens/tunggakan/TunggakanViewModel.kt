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
     * depends on the one value it needs instead of on a storage layer, and so a test can supply
     * a name without a database. `null` in a test that never opens the reminder dialog.
     */
    private val businessName: (suspend () -> String)? = null,
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
     * Which row the landscape detail pane is showing.
     *
     * Held as its own flow rather than folded into the projection, so reading it inside another
     * `combine`'s lambda cannot emit a state carrying the *previous* selection — the same
     * reason `KontrakViewModel` keeps each of its five sheets as a real source.
     */
    private val selectedId = MutableStateFlow<String?>(null)

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
        arrearsSource,
        renterRepository.observeRenters(),
        vehicleRepository.observeVehicles(),
        selectedId,
        reminder,
    ) { source, renters, vehicles, selection, currentReminder ->
        TunggakanUiState(
            rows = when (source) {
                // Nothing rendered while loading, and **nothing rendered on failure**: the
                // banner beside an empty list would state "nobody owes you anything" and
                // then apologise for it, which is worse than saying nothing.
                Source.Loading, is Source.Failed -> emptyList()
                is Source.Loaded -> source.entries.toRows(
                    renterNames = renters.associate { it.id to it.name },
                    // The phone joins the **same** lookup the name does, so the recipient
                    // arrives with the figures rather than from a second read — the rule the
                    // reminder's own KDoc gives.
                    renterPhones = renters.associate { it.id to it.phone },
                    vehiclePlates = vehicles.associate { it.id to it.plate },
                    strings = strings,
                )
            },
            // Carried through unchanged rather than re-derived, so a row that stops owing
            // leaves the pane showing its prompt instead of silently retargeting to a
            // different renter the moment a payment lands.
            selectedContractId = selection,
            isLoading = source is Source.Loading,
            isFailed = source is Source.Failed,
            // **Derived from the source, not from a second channel.** The banner is a fact
            // about which `Source` this is, so reading it from `Source.Failed` keeps one
            // derivation; a `MutableStateFlow` written beside the flow was the original
            // defect, because a message in a flow the `combine` does not read reaches
            // nobody. A dismissal does not set this to null — it re-emits `Loaded`, which
            // drops the banner because the read no longer says it failed.
            errorMessage = source.bannerOrNull(strings),
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
     * ## Why the figures come from the row and not from the repository
     *
     * Because the acceptance criterion's strongest clause is that a cancellation landing
     * between the list and the message must not produce a number from two different states. The
     * row already carries amount, days, names and phone from **one** emission, so the way to
     * guarantee that is to read nothing again. Re-querying here would reintroduce exactly the
     * window the clause forbids.
     *
     * ## Why the business name is read here, and why that is not the same problem
     *
     * Because it is not a figure. A business name changing mid-session cannot make the amount
     * wrong, and reading it at compose time means a name corrected in Settings appears in the
     * very next message without a restart. It is the one field that is allowed to be fresh.
     */
    fun openReminder(contractId: String) {
        val row = uiState.value.rows.firstOrNull { it.contractId == contractId } ?: return

        viewModelScope.launch {
            // A null seam means no identity is wired — a test that never opens this dialog. The
            // message still composes, with an empty business name, rather than throwing.
            val business = businessName?.invoke().orEmpty()
            val text = ReminderMessage.build(
                renter = row.renterName,
                businessName = business,
                plate = row.vehiclePlate,
                figureText = row.figureText,
                template = { renter, business, plate, figure ->
                    strings.tunggakanReminderMessage(renter, business, plate, figure)
                },
            )
            reminder.value = ReminderUi(
                contractId = contractId,
                phone = row.renterPhone,
                text = text,
                // Built from the same text the field shows, so the link and the field cannot
                // start out disagreeing either.
                link = ReminderMessage.whatsappLink(row.renterPhone, text),
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
     * Returns whether there was anything to act on, so the caller can decide between opening
     * the link and leaving the dialog up with its refusal.
     */
    fun confirmReminderText(): Boolean {
        val current = reminder.value ?: return false
        if (!current.canSend) {
            reminder.value = current.copy(errorMessage = strings.tunggakanReminderEmpty())
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
     */
    fun onReminderCopied() {
        reminder.value = reminder.value?.copy(
            copiedMessage = strings.tunggakanReminderCopied(),
        )
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
     * The refusal path is `confirmReminderText`, which writes the message onto the dialog rather
     * than throwing — a failure the owner can act on belongs on the field they can fix.
     */
    fun sendReminder() {
        if (!confirmReminderText()) return
        _pendingLink.value = reminder.value?.link
    }

    /** The screen consumed [pendingLink] and launched it. */
    fun onReminderLinkOpened() {
        _pendingLink.value = null
    }

    /** Closes the dialog and its message with it. */
    fun dismissReminder() {
        reminder.value = null
        _pendingLink.value = null
    }
}