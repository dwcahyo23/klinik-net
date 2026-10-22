package net.klinik.rental.ui.screens.setoran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.klinik.rental.data.repository.AccountRequiredException
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.DepositAlreadyCanceledException
import net.klinik.rental.data.repository.DepositHistoryRepository
import net.klinik.rental.data.repository.MissingDepositRelationException
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.UnknownDepositException
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.domain.validation.InvalidAmountException
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.strings.RStrings
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The deposit sheet: who paid, how much, and how it arrived.
 *
 * ## Why this ViewModel exists separately from the contract screen's
 *
 * The sheet has no destination of its own — it is mounted by the shell's FAB over
 * whichever screen the owner was on — so it needs no route and no entry in `Screens`. What
 * it *does* need is three repositories the contract ViewModel does not hold (renters,
 * vehicles, accounts) plus the payment one, and a lifetime that outlives the rotation of
 * whichever screen is behind it. A ViewModel is the cheapest thing that survives a
 * configuration change.
 *
 * ## Why [today] is a [Clock] and not `LocalDate.now()`
 *
 * Because a deposit is dated relative to the billing period, and a period depends on
 * "today". Pinning it to the system clock makes the allocation untestable — a test that
 * passes in October fails in November. The epic context requires the single `Clock`
 * abstraction, and this is the first caller that has one.
 */
class SetoranViewModel(
    private val payments: PaymentRepository,
    private val renterRepository: RenterRepository,
    private val vehicleRepository: VehicleRepository,
    private val accountRepository: BusinessAccountRepository,
    private val contractRepository: ContractRepository,
    private val strings: RStrings,
    private val clock: Clock,
    /**
     * The history read. Nullable **only** so the existing tests build a ViewModel over the
     * repositories they already have; production passes it, and [openHistory] refuses
     * without it rather than showing an empty list that would read as "this contract has
     * never been paid".
     */
    private val historyRepository: DepositHistoryRepository? = null,
) : ViewModel() {

    /** The open form, or `null` when the sheet is closed. */
    private val form = MutableStateFlow<DepositFormState?>(null)

    /**
     * The contract's deposit history, or `null` when none is open.
     *
     * Held here beside [form] rather than in [KontrakViewModel] because the two verbs this
     * unlocks — [requestCancel] and [openEditDeposit] — live here, and their tests, their
     * refusals and their dialogs are all here. A history read by one ViewModel and acted on
     * by another is a state that has to be kept in step across two objects.
     */
    private val history = MutableStateFlow<DepositHistoryUi?>(null)

    /** The last banner-level refusal. A field refusal lands on its field instead. */
    private val errorMessage = MutableStateFlow<String?>(null)

    private val message = MutableStateFlow<String?>(null)

    /**
     * Whether a write is in flight, so a double tap cannot record the same deposit twice.
     *
     * An `AtomicBoolean`, not a rendered flag, for the reason its three siblings use one:
     * this is not drawn, so putting it in the state would recompose the sheet on a value
     * nothing reads.
     */
    private val saving = AtomicBoolean(false)

    /**
     * How many times an edit form has been asked for, so a stale read can tell it lost.
     *
     * Not a `Boolean` because two opens can race: a dismissal between them must cancel the
     * one before it as well, and a flag that a second open resets would let the first
     * through. A counter has no such blind spot — every close and every new open moves it,
     * so only the most recent request may write.
     *
     * Plain `Int` and not an `AtomicInteger`: every mutation and read happens on the
     * ViewModel's single dispatcher thread, which is what `viewModelScope.launch` gives.
     */
    private var openRequests = 0

    /**
     * The pickers' options, read once and shared.
     *
     * Every renter is listed whether or not they have an open contract — the owner needs
     * to see *who* has none, and a filtered list makes a renter look deleted.
     */
    private data class Options(
        val renters: List<RenterOption>,
        val accounts: List<AccountOption>,
    )

    /** The options the pickers read. Shared, so one subscription feeds both. */
    private val options: StateFlow<Options> = combine(
        renterRepository.observeRenters(),
        contractRepository.observeContracts(),
        combine(
            vehicleRepository.observeVehicles(),
            accountRepository.observeAccounts(),
        ) { vehicles, accounts -> vehicles.associate { it.id to it.plate } to accounts },
    ) { renters, contracts, (plates, accounts) ->
        // The contract a renter pays into is the one **currently running** — `isOpen`,
        // which is `endDate == null`. A finished contract's deposits are history, and a
        // deposit dated today belongs to the rental that is still going.
        val openByRenter = contracts
            .filter { it.isOpen }
            .associateBy { it.renterId }
        Options(
            renters = renters.map { renter ->
                val contract = openByRenter[renter.id]
                RenterOption(
                    renter = renter,
                    contract = contract?.let {
                        DepositContract(
                            contractId = it.id,
                            vehiclePlate = plates[it.vehicleId] ?: it.vehicleId,
                            // From the **contract**, never the vehicle: the rate was
                            // frozen when the rental opened, and reading the vehicle
                            // would reprice a running contract the moment the fleet
                            // list changed.
                            rateText = formatRupiah(it.dailyRate),
                        )
                    },
                )
            },
            accounts = accounts.map { account ->
                // `bank · number`, because two accounts at one bank are legitimate and
                // the owner must be able to tell them apart — which is why the number is
                // in the label rather than only the bank.
                AccountOption(
                    account = account,
                    label = "${account.bankName} · ${account.number}",
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Options(emptyList(), emptyList()),
    )

    /**
     * The dialog's state, held separately from the form.
     *
     * A separate flow rather than a field on [form] so that *closing the form* and
     * *dismissing the dialog* are different acts. A preview stored on the form would be
     * discarded by code that only meant to close the sheet, and the owner would lose the
     * dialog they were reading.
     *
     * Named `previewState` so it cannot be confused with the repository's
     * `DepositPreview` or with this class's `previewDeposit` — three things in one class
     * whose whole job is to agree with each other.
     */
    private val previewState = MutableStateFlow<DepositPreviewUi?>(null)

    /**
     * The cancellation dialog's state — Story 3.4's.
     *
     * A separate flow from [form] and [previewState] so that three windows have three
     * lifetimes. They are genuinely different decisions: the form is the deposit being
     * composed, the preview is a question about it, and the cancellation is a question
     * about something already stored. Collapsing them would mean closing the sheet also
     * dismissed a dialog, and a reason typed for one would survive into another.
     */
    private val cancelState = MutableStateFlow<CancelDepositUi?>(null)

    // --------------------------------------------------- the Setoran screen

    /**
     * The screen's selected tab.
     *
     * Held as its own flow rather than a field on [screenState] for the same reason every
     * other selection in this app is: the tab survives a history re-emission, and folding
     * it into a `combine` output it does not depend on would recompose the tab row every
     * time a deposit landed anywhere.
     */
    private val selectedTab = MutableStateFlow(SetoranTab.Input)

    /**
     * Bumped by [consumeScreenError] to ask the history read to try again — the same
     * re-subscribe-on-dismiss shape the arrears screen's `arrearsRetry` uses. A counter,
     * because two dismissals are two requests and a boolean would make the second a no-op.
     */
    private val screenRetry = MutableStateFlow(0)

    /** What the screen's history read has produced, tagged so "not yet read" is a value. */
    private sealed interface ScreenHistorySource {
        /** Not yet read. Carries no rows, so the screen cannot draw an empty list from it. */
        data object Loading : ScreenHistorySource

        /** Read, possibly to no deposits. */
        data class Loaded(
            val rows: List<net.klinik.rental.data.repository.DepositHistoryRow>,
        ) : ScreenHistorySource

        /** The read failed, and the list must not be believed. */
        data object Failed : ScreenHistorySource
    }

    /**
     * The screen's history source, with its failure carried as a **value**.
     *
     * The same three-step shape the arrears screen's source takes, for the same reasons:
     * `onStart` carries Loading so the first empty Room emission cannot be read as "no
     * deposits anywhere"; a `catch` that re-throws would terminate the sharing coroutine
     * under `stateIn` and freeze the screen on its last value; and re-subscribing on
     * dismiss is one deliberate recovery, not a spin.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val screenHistorySource: Flow<ScreenHistorySource> =
        screenRetry.flatMapLatest { _ ->
            val repository = historyRepository
                ?: return@flatMapLatest kotlinx.coroutines.flow.flow {
                    emit(ScreenHistorySource.Failed)
                }
            repository.observeAll()
                .map<
                    List<net.klinik.rental.data.repository.DepositHistoryRow>,
                    ScreenHistorySource,
                    > { rows -> ScreenHistorySource.Loaded(rows) }
                .onStart { emit(ScreenHistorySource.Loading) }
                .catch { emit(ScreenHistorySource.Failed) }
        }

    /**
     * The history search box, as typed, and the status chip, as chosen.
     *
     * Both are `MutableStateFlow`s for the same reason [selectedTab] is: they are the owner's
     * input, and they must survive the history re-reading underneath them. Folding them into
     * the read would reset the search box every time a deposit landed.
     */
    private val historyQuery = MutableStateFlow("")
    private val historyFilter = MutableStateFlow<HistoryFilter?>(null)

    /**
     * The Setoran screen's state — the two-tab destination's own read.
     *
     * ## Why the summary is derived **here**, from the same emission as the rows
     *
     * Because a second counting query would be a second subscription and a second answer
     * to "how many deposits are there" — figures that can drift from the list above them.
     * total/active/cancelled are counts over the loaded rows, and received-today comes
     * from `observeTodaysIncome`'s own flow, which is the read Beranda uses. The summary
     * counts **all** rows, not [SetoranScreenState.visibleRows]: it answers "what came in",
     * and a search box narrowing the table must not rewrite the totals above it.
     *
     * ## Why the narrowing happens here and not in the composable
     *
     * Because "what the table shows" is a decision about the data, and a screen that filtered
     * its own list would have to be handed all four matching fields to redo the match. The
     * rule is the one every other filter in this app follows — the ViewModel answers, the
     * screen draws.
     */
    val screenState: StateFlow<SetoranScreenState> = combine(
        // Nested, because `combine` has a five-argument overload and this reads six
        // sources. The inner pair keeps the sixth one typed rather than arriving as
        // `Any?` through the vararg form.
        combine(selectedTab, form) { tab, currentForm -> tab to currentForm },
        screenHistorySource,
        payments.observeTodaysIncome(clock),
        historyQuery,
        historyFilter,
    ) { tabAndForm, source, receivedToday, query, filter ->
        val (tab, currentForm) = tabAndForm
        val allRows = when (source) {
            // Nothing rendered while loading or failed: an empty list here would be the
            // claim "no deposits anywhere", made without having looked.
            ScreenHistorySource.Loading, is ScreenHistorySource.Failed -> emptyList()
            is ScreenHistorySource.Loaded -> source.rows.map { row -> toScreenRow(row) }
        }
        SetoranScreenState(
            selectedTab = tab,
            // The Input tab shows the form inline — the prototype's `payments.html` has the
            // form and the numpad in the tab, not behind a sheet. The shell reads this to
            // avoid mounting the sheet over the form it is already drawing.
            isShowingForm = tab == SetoranTab.Input && currentForm != null,
            rows = allRows,
            historyQuery = query,
            historyFilter = filter,
            visibleRows = allRows.filter { row ->
                matchesFilter(row, filter) && matchesQuery(row, query)
            },
            // Derived from the same emission the rows travel in — one subscription.
            summary = when (source) {
                ScreenHistorySource.Loading, is ScreenHistorySource.Failed -> null
                is ScreenHistorySource.Loaded -> DepositSummaryUi(
                    total = source.rows.size,
                    active = source.rows.count { !it.isCanceled },
                    cancelled = source.rows.count { it.isCanceled },
                    receivedTodayText = formatRupiah(receivedToday),
                )
            },
            errorMessage = when (source) {
                is ScreenHistorySource.Failed -> strings.depositoErrorUnexpected()
                else -> null
            },
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SetoranScreenState(),
        )

    /**
     * The three status chips' own rule.
     *
     * "Aktif" is *reported* rather than *not cancelled alone*, because the chip and the badge on
     * the row must agree: the table draws a green badge only for an active row, and a filter
     * that called a partial deposit something else would leave a row visible under "Aktif" with
     * a badge saying otherwise. Cancelled is its own chip, so it is excluded from [Aktif].
     */
    private fun matchesFilter(row: DepositHistoryRowUi, filter: HistoryFilter?): Boolean =
        when (filter) {
            null -> true
            HistoryFilter.Aktif -> !row.isPartial && !row.isCanceled
            HistoryFilter.Sebagian -> row.isPartial && !row.isCanceled
            HistoryFilter.Dibatalkan -> row.isCanceled
        }

    /**
     * The search box's rule — `renderHistory`'s own match, over the same four fields.
     *
     * Renter, plate, reference and bank. The plate is the second half of the row's own
     * `"renterName · plate"` label, which is why the label is what gets searched rather than a
     * field that does not exist on the row. Case-insensitive, and an empty box matches
     * everything — including a box holding only spaces, because a search for " " is not a
     * search.
     */
    private fun matchesQuery(row: DepositHistoryRowUi, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return listOfNotNull(
            row.renterName,
            row.contractLabel,
            row.referenceNo,
            row.accountText,
            row.methodText,
        ).any { it.contains(q, ignoreCase = true) }
    }

    /** Selects a tab. The two are the screen's whole chrome, so no guard is needed. */
    fun selectTab(tab: SetoranTab) {
        selectedTab.value = tab
    }

    /** Records what the owner typed into the history search box. */
    fun setHistoryQuery(query: String) {
        historyQuery.value = query
    }

    /** Clears the history search box. Its own function so "clear" is not a typed empty string. */
    fun clearHistoryQuery() {
        historyQuery.value = ""
    }

    /** Selects a status chip. `null` is "all", which is the default. */
    fun selectHistoryFilter(filter: HistoryFilter?) {
        historyFilter.value = filter
    }

    /** Dismisses the screen's failure banner by asking the read to try again. */
    fun consumeScreenError() {
        screenRetry.value = screenRetry.value + 1
    }

    /**
     * One repository row, formatted for the screen — the same projection
     * `openHistory` builds for the per-contract block, with the three per-row
     * identity fields the cross-contract list exists to show.
     */
    private suspend fun toScreenRow(
        row: net.klinik.rental.data.repository.DepositHistoryRow,
    ): DepositHistoryRowUi {
        val identity = resolveContractLabel(row.contractId)
        val accounts = accountRepository.observeAccounts().first()
            .associate { it.id to "${it.bankName} · ${it.number}" }
        return DepositHistoryRowUi(
            paymentId = row.id,
            contractId = row.contractId,
            renterName = identity.renterName,
            contractLabel = identity.label,
            amountText = formatRupiah(row.amount),
            dateText = strings.formatExceptionDate(row.date),
            methodText = if (row.method == PaymentMethod.TransferBank) {
                strings.depositoMethodTransfer()
            } else {
                strings.depositoMethodCash()
            },
            accountText = row.accountId?.let { accounts[it] },
            referenceNo = row.referenceNo,
            notes = row.notes,
            // From the row's own derivation, not a flag passed from the screen.
            isEditable = row.isEditable,
            isCanceled = row.isCanceled,
            cancelReasonText = row.cancelReason,
            // A cancelled deposit is never "partial": it cancelled nothing, so being short of
            // a day's rate is not a fact about it. A zero rate means the contract was not in
            // the snapshot — treated as "not partial" rather than as "everything is partial",
            // because the second would put the badge on every row of a deleted contract.
            isPartial = !row.isCanceled && identity.dailyRate > 0L && row.amount < identity.dailyRate,
        )
    }

    /**
     * Resolves a contract id to its renter's name, `renterName · plate` label, and daily rate.
     *
     * A **Kotlin join**, not a SQL one — the same rule the contract and arrears screens
     * follow: one reader per table, and the join is over static display facts a stale
     * name cannot make wrong. Falls back to the id, which is a better outcome than a row
     * that shows nothing at all.
     *
     * The rate rides along because the row's "Sebagian" badge is `amount < dailyRate`, and
     * reading the contract twice — once for the label, once for the rate — would be two
     * snapshots of the same table that a write between them could make disagree. A zero rate
     * means the contract was not found, which the badge reads as "not partial".
     */
    private suspend fun resolveContractLabel(contractId: String): ContractIdentity {
        val contracts = contractRepository.observeContracts().first()
        val contract = contracts.firstOrNull { it.id == contractId }
            ?: return ContractIdentity(contractId, contractId, dailyRate = 0L)
        val renterName = renterRepository.observeRenters().first()
            .firstOrNull { it.id == contract.renterId }?.name ?: contract.renterId
        val plate = vehicleRepository.observeVehicles().first()
            .firstOrNull { it.id == contract.vehicleId }?.plate ?: contract.vehicleId
        return ContractIdentity(renterName, "$renterName · $plate", contract.dailyRate)
    }

    /** What [resolveContractLabel] answers — the three facts a cross-contract row needs. */
    private data class ContractIdentity(
        val renterName: String,
        val label: String,
        val dailyRate: Long,
    )


    private data class OptionsSnapshot(
        val options: Options,
        val message: String?,
    )

    /** The screen state the sheet reads. */
    val uiState: StateFlow<SetoranUiState> = combine(
        form,
        combine(errorMessage, previewState, message) { error, currentPreview, currentMessage ->
            SheetSnapshot(error, currentPreview, currentMessage)
        },
        cancelState,
        combine(options, message) { currentOptions, currentMessage ->
            OptionsSnapshot(currentOptions, currentMessage)
        },
        history,
    ) { currentForm, sheetSnapshot, currentCancel, optionsSnapshot, currentHistory ->
        SetoranUiState(
            form = currentForm,
            errorMessage = sheetSnapshot.error,
            renterOptions = optionsSnapshot.options.renters,
            accountOptions = optionsSnapshot.options.accounts,
            successMessage = sheetSnapshot.message,
            preview = sheetSnapshot.preview,
            cancel = currentCancel,
            history = currentHistory,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SetoranUiState(isLoading = true),
        )

    /** The transient confirmation, for the sheet's own banner. */
    val snackbarMessage: StateFlow<String?> = message

    private data class SheetSnapshot(
        val error: String?,
        val preview: DepositPreviewUi?,
        val message: String?,
    )

    // ------------------------------------------------------------------- form

    /**
     * Opens one contract's deposit history — the surface that mounts Stories 3.4 and 3.5.
     *
     * ## Why this reads rather than computes
     *
     * Because the rows are facts the owner recorded, not a question about money. No
     * arrears figure appears here, and the day count a deposit closed is deliberately
     * absent too: putting that beside the contract's arrears would show two different
     * numbers under one heading, and the owner cannot tell which is which.
     *
     * ## Why cancelled rows are listed rather than filtered
     *
     * Because Story 3.4 chose a status flag over a delete precisely so this trail could be
     * read later, and a history that hid the cancellation would undo that decision one
     * screen later. The owner's own reason for cancelling is the most useful column on
     * the row — it is the answer to *"why did Budi's arrears jump on the 17th"*.
     *
     * @param contractId the contract whose deposits to list.
     * @param renterName for the block's title, matching the exception sheet's label shape.
     * @param vehiclePlate likewise.
     */
    fun openHistory(contractId: String, renterName: String, vehiclePlate: String) {
        errorMessage.value = null
        message.value = null
        val repository = historyRepository
        if (repository == null) {
            // Rather than an empty list: "this contract has never been paid" and "this
            // ViewModel cannot read history" are different claims, and only one of them
            // is true in production.
            errorMessage.value = strings.depositoErrorUnexpected()
            return
        }
        // Replaces any edit still reading — see `openDeposit`'s note on the same token.
        openRequests++

        history.value = DepositHistoryUi(
            contractId = contractId,
            contractLabel = "$renterName · $vehiclePlate",
            renterName = renterName,
            // Set empty, then filled by the read below. A block that appears with no rows
            // draws its empty state for one frame on every open, which is the
            // "empty data that looks like a new account" failure Story 2.8 is about.
            rows = emptyList(),
            errorMessage = null,
        )

        val request = openRequests
        viewModelScope.launch {
            try {
                val accounts = accountRepository.observeAccounts().first()
                    .associate { it.id to "${it.bankName} · ${it.number}" }
                val rows = repository.forContract(contractId).map { row ->
                    DepositHistoryRowUi(
                        paymentId = row.id,
                        // The block's own values, which on this surface are the row's too:
                        // every deposit here belongs to the contract `openHistory` was
                        // asked for. Carried per row because the block's cancel callback
                        // reads the row, not the block.
                        contractId = contractId,
                        renterName = renterName,
                        contractLabel = "$renterName · $vehiclePlate",
                        amountText = formatRupiah(row.amount),
                        dateText = strings.formatExceptionDate(row.date),
                        methodText = if (row.method == PaymentMethod.TransferBank) {
                            strings.depositoMethodTransfer()
                        } else {
                            strings.depositoMethodCash()
                        },
                        accountText = row.accountId?.let { accounts[it] },
                        referenceNo = row.referenceNo,
                        notes = row.notes,
                        // From the row's own derivation, not a flag passed from the screen:
                        // a verb offered on a row the repository would refuse renders live
                        // and ignores the tap.
                        isEditable = row.isEditable,
                        isCanceled = row.isCanceled,
                        cancelReasonText = row.cancelReason,
                    )
                }
                if (request != openRequests) return@launch
                history.value = history.value?.copy(rows = rows)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Reported, and the block keeps its error rather than its empty rows: a
                // failed read shown as "no deposits" tells the owner this contract was
                // never paid, which is the one claim it must never make without having
                // looked.
                if (request == openRequests) {
                    history.value = history.value?.copy(
                        errorMessage = strings.depositoErrorUnexpected(),
                    )
                }
            }
        }
    }

    /** Closes the history block. */
    fun closeHistory() {
        errorMessage.value = null
        history.value = null
    }

    /**
     * Dismisses the history block's own failure banner.
     *
     * Its own handler rather than aliased to [consumeError]: the two clear different
     * messages, and sharing a setter is how one message is cleared through the other's
     * channel while staying on screen — the trap every other banner pair in this app
     * records. It clears the banner **inside** the block and leaves the rows alone, so a
     * dismissed failure does not also read as an emptied contract.
     */
    fun consumeHistoryError() {
        history.value = history.value?.copy(errorMessage = null)
    }

    /**
     * Opens the sheet on a **blank** form — including a blank amount.
     *
     * The empty amount is the story's whole point rather than an omission: the owner is
     * required to type the figure, because typing it from zero is what catches a mistype.
     * The rate is filled for them precisely so they *cannot* be reading it off the screen
     * and re-keying it.
     *
     * The date is today rather than a blank field: a deposit arrives today unless the owner
     * is correcting, and the correction's date comes from the picker rather than from a
     * default.
     *
     * Seeded from [clock] rather than `LocalDate.now()` so the date the form proposes and
     * the `asOf` the allocation runs against are the same instant — two sources of "today"
     * is how a deposit written at 23:59 lands in the wrong billing period.
     */
    fun openDeposit() {
        errorMessage.value = null
        message.value = null
        // Cancels any edit still reading, for the same reason `closeDeposit` does: this form
        // is replacing it, and a correction that landed after this would overwrite the sheet
        // the owner just opened for a new deposit.
        openRequests++
        form.value = DepositFormState(date = LocalDate.now(clock))
    }

    /**
     * Opens the sheet to correct [paymentId] — Story 3.5's.
     *
     * ## Why this reads rather than being handed a deposit
     *
     * Because the form is populated from the **stored** row, not from whatever the caller
     * last saw. A stale caller — a history list emitting before the owner's edit landed — must
     * not be able to write back values the database has moved on from, and a correction is
     * exactly the write that would overwrite them.
     *
     * ## Why a refusal opens no form at all
     *
     * Because a correction form with a blank plate, a blank rate or a blank renter is a form
     * the owner cannot check what they are correcting. `PaymentRepository.findDeposit`
     * resolves all of them or returns nothing, so the two outcomes here are a complete form
     * and a message — never a half-built one.
     *
     * @param paymentId the stored deposit to correct.
     */
    fun openEditDeposit(paymentId: String) {
        errorMessage.value = null
        message.value = null
        previewState.value = null
        cancelState.value = null
        // Bumped **before** the read, and checked after it. The read below suspends, and
        // the owner can dismiss the sheet across that suspension — without this token the
        // launch would write the form afterwards and the sheet would spring open again
        // after being told to go. `closeDeposit` is what invalidates it.
        val request = ++openRequests
        form.value = null

        viewModelScope.launch {
            try {
                val stored = payments.findDeposit(paymentId)
                if (request != openRequests) return@launch
                if (stored == null) {
                    // Its own copy rather than `depositoErrorUnexpected`, which reads
                    // "Gagal menyimpan setoran" — a failed *save*, about an open that has
                    // not saved anything. An owner who tapped a row that no longer exists
                    // needs to know the deposit is gone, not that writing failed.
                    errorMessage.value = strings.depositoErrorNotFound()
                    return@launch
                }
                form.value = DepositFormState(
                    paymentId = stored.id,
                    renterId = stored.renterId,
                    renterName = stored.renterName,
                    contractId = stored.contractId,
                    vehiclePlate = stored.vehiclePlate,
                    // Formatted here rather than by the repository: `formatRupiah` is in
                    // `ui.format`, and the epic context pins the dependency direction as
                    // domain → data → ui. This is the same path the options flow below takes.
                    rateText = formatRupiah(stored.dailyRate),
                    date = stored.date,
                    amountDigits = stored.amount.toString(),
                    method = stored.method,
                    accountId = stored.accountId,
                    referenceNo = stored.referenceNo.orEmpty(),
                    notes = stored.notes.orEmpty(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingDepositRelationException) {
                // The relations are foreign-key protected, so this is a state the database
                // forbids rather than one a routine correction reaches. Said plainly
                // anyway: a refusal the owner can read beats a blank form.
                errorMessage.value = strings.depositoErrorNotFound()
            } catch (e: Exception) {
                errorMessage.value = strings.depositoErrorUnexpected()
            }
        }
    }

    fun onFormChange(transform: (DepositFormState) -> DepositFormState) {
        form.value = form.value?.let(transform)
        // Any edit invalidates a preview already on screen. Not tidiness: the figures
        // would then describe a deposit the owner is no longer composing, and the AC's
        // "pratinjau wajib" would be a figure about the wrong transaction.
        previewState.value = null
    }

    // ------------------------------------------------------------- the preview

    /**
     * Computes the mandatory preview for the deposit currently on the pad.
     *
     * Called by `KnNumPad`'s Save **instead of** writing, which is what makes the
     * preview unskippable: the pad has no other exit, and the dialog is the only way
     * forward.
     *
     * The completeness refusals are the same ones `saveDepositAmount` applies, and they
     * stay here rather than in the write — because reaching this point with an incomplete
     * form means the pad offered a Save the sheet could not honour, and the message has
     * to land on the field the owner still has to fix.
     */
    fun previewDeposit(amount: Long) {
        val current = form.value ?: return
        // A correction writes directly and has no preview to compute — UX-DR11's
        // "correcting a field is not a destructive act". Branched **before** the saving
        // lock so [saveCorrection] takes it itself: taking it here and handing it on
        // would be the same lock acquired twice, which always fails.
        if (current.isEditing) {
            saveCorrection(amount)
            return
        }

        if (!saving.compareAndSet(false, true)) return
        val contractId = current.contractId
        if (contractId == null) {
            errorMessage.value = strings.depositoErrorNoContract()
            saving.set(false)
            return
        }
        if (current.showsAccountField && current.accountId == null) {
            form.value = current.copy(accountError = strings.depositoErrorAccountRequired())
            saving.set(false)
            return
        }

        val today = LocalDate.now(clock)
        viewModelScope.launch {
            try {
                val result = payments.previewDeposit(
                    contractId = contractId,
                    payment = Payment(
                        // A throwaway id: the preview allocates against it and writes
                        // nothing, and the real id is minted at confirm time. Minting
                        // one here would mean the id in the dialog and the id stored
                        // could differ.
                        id = PREVIEW_ID,
                        // The **form's** date, not the clock's — the same value
                        // `saveDepositAmount` writes. Reading the clock here instead
                        // would let a back-dated new deposit be previewed on one day
                        // and stored on another, which is exactly the "preview cannot
                        // disagree with the save" property Story 3.3 exists to hold.
                        date = current.date,
                        amount = amount,
                    ),
                    asOf = today,
                )
                errorMessage.value = null
                previewState.value = result.toUi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Said plainly, and **no save is offered**: the epic context is explicit
                // that a preview showing the wrong thing is worse than no preview.
                previewState.value = null
                errorMessage.value = strings.depositoErrorPreviewFailed()
            } finally {
                saving.set(false)
            }
        }
    }

    /**
     * The dialog's dismiss — "Cek dulu", not "Batal".
     *
     * Discards the preview and **saves nothing**, which is the half that makes the
     * preview mandatory rather than advisory: a dismiss that saved would be a
     * confirmation the owner could wave through, and a preview that can be waved
     * through has taught them nothing.
     *
     * The form is deliberately left open with its amount: nothing was lost by looking.
     */
    fun dismissPreview() {
        previewState.value = null
    }

    /**
     * Writes the deposit the preview described.
     *
     * ## Why it refuses when no preview is up
     *
     * Because this is where the mandatory rule becomes structural rather than a comment.
     * The button is wired to the dialog's confirm, so a caller that skipped
     * [previewDeposit] gets a refusal instead of a silent write — and a future screen
     * that mounts a Save straight to this method cannot bypass the preview by wiring
     * around the sheet.
     */
    fun confirmDeposit() {
        val current = form.value
        if (current == null || previewState.value == null) return

        // The amount comes from the form, not from the preview: the preview carries no
        // amount, and re-reading the digits here is what would let a figure typed
        // between preview and confirm go in unshown.
        val amount = current.amountDigits.toLongOrNull()
        if (amount == null || amount <= 0L) {
            previewState.value = null
            form.value = current.copy(amountError = strings.depositoErrorAmountZero())
            return
        }
        saveDepositAmount(amount)
    }
    /**
     * Records the deposit with the amount `KnNumPad`'s Save handed over.
     *
     * A `Long` parameter rather than reading `amountDigits` here, because the pad has
     * **already refused zero** by the time this is called: its refusal is a red flash,
     * an *event* that changes no state and cannot be derived from the digits. Taking
     * the figure from the pad means one keystroke produces one answer — a version that
     * re-parsed the digits would have to answer the same key twice, once as a flash and
     * once as a refusal.
     *
     * So the amount is not re-checked here. `Payment.init` still refuses a non-positive
     * figure at the data layer, which is where the guarantee belongs; a caller that
     * reached this with `0` gets the domain refusal rather than a silent write.
     */
    fun saveDepositAmount(amount: Long) {
        if (!saving.compareAndSet(false, true)) return

        val current = form.value
        if (current == null) {
            saving.set(false)
            return
        }
        if (current.isEditing) {
            // The mirror of `saveCorrection`'s guard. Both write paths are reachable from the
            // sheet's numpad, and a form carrying a `paymentId` that reached the *new-deposit*
            // path would mint a fresh id and record a **second** deposit against the same
            // renter — the one outcome this story's "one current value" rule forbids, and one
            // no sheet state makes visible.
            saving.set(false)
            saveCorrection(amount)
            return
        }
        val contractId = current.contractId
        if (contractId == null) {
            // No contract, so nothing to bill. Not a field refusal: there is no control
            // the owner can change, because the renter has no running rental — which is
            // why the sheet says so in place of two blank rows.
            errorMessage.value = strings.depositoErrorNoContract()
            saving.set(false)
            return
        }
        if (current.showsAccountField && current.accountId == null) {
            form.value = current.copy(accountError = strings.depositoErrorAccountRequired())
            saving.set(false)
            return
        }

        val today = LocalDate.now(clock)
        val accountId = if (current.showsAccountField) current.accountId else null

        viewModelScope.launch {
            try {
                payments.saveDeposit(
                    contractId = contractId,
                    payment = Payment(
                        id = payments.newPaymentId(),
                        date = current.date,
                        amount = amount,
                    ),
                    method = current.method,
                    accountId = accountId,
                    // A cash deposit has no bank reference; a transfer's is the one
                    // field that makes it checkable against a mutation later.
                    referenceNo = current.referenceNo.trim().ifBlank { null },
                    // Kept on a transfer too, because an owner may want to note
                    // something — but on cash it is the **only** breadcrumb there is.
                    notes = current.notes.trim().ifBlank { null },
                    asOf = today,
                )
                // Conditional, for the reason the other screens' closes are: the write is
                // a suspension point and the owner keeps interacting across it.
                val live = form.value
                if (live != null && live === current) form.value = null
                // The dialog goes with the sheet: leaving it up over a closed form would
                // be a confirm button whose write has nowhere to go.
                previewState.value = null
                errorMessage.value = null
                message.value = strings.depositoSaved(formatRupiah(amount))
            } catch (e: net.klinik.rental.data.repository.UnknownContractException) {
                form.value = form.value?.copy(
                    contractId = null,
                    vehiclePlate = "",
                    rateText = "",
                )
                errorMessage.value = strings.depositoErrorNoContract()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Reported, never swallowed: a deposit the owner took and believes
                // they recorded is the worst outcome this app has.
                errorMessage.value = strings.depositoErrorUnexpected()
            } finally {
                saving.set(false)
            }
        }
    }

    /**
     * Writes a correction to the deposit the form is correcting.
     *
     * ## Why this saves with no dialog in between
     *
     * UX-DR11: correcting a field is not a destructive act — the row survives, its history
     * position survives, and nothing is destroyed — so a confirmation would be the
     * "over-confirmation that teaches people to stop reading" the epic context warns about.
     * The *preview* is the same rule one step further: it exists to catch an amount typed
     * wrong the first time, and an amount being edited has already been seen.
     *
     * ## Why the method is never sent
     *
     * Because it cannot be changed, and [DepositFormState.setMethod] refuses before the
     * sheet's chips are even tappable. `PaymentRepository.correctDeposit` has no parameter to
     * receive it, so the third refusal is structural — the one a caller cannot route around.
     *
     * @param amount the figure `KnNumPad`'s Save handed over, on the same terms as
     *   [saveDepositAmount]: the pad has already refused zero, and this does not re-parse.
     */
    fun saveCorrection(amount: Long) {
        if (!saving.compareAndSet(false, true)) return

        val current = form.value
        val paymentId = current?.paymentId
        if (current == null || paymentId == null) {
            // No stored deposit behind the form, so there is nothing to correct. The
            // un-skippable counterpart to [confirmDeposit]'s: a caller that reached the
            // write without an edit open gets a refusal rather than an insert.
            saving.set(false)
            return
        }
        val contractId = current.contractId
        if (contractId == null) {
            // Said rather than swallowed. A silent return here would leave the owner tapping
            // Save on a form whose renter has no rental — and the sheet says exactly that
            // in place of the picker, so the sentence they already read is the right one.
            errorMessage.value = strings.depositoErrorNoContract()
            saving.set(false)
            return
        }
        if (current.showsAccountField && current.accountId == null) {
            form.value = current.copy(accountError = strings.depositoErrorAccountRequired())
            saving.set(false)
            return
        }

        val today = LocalDate.now(clock)
        val accountId = if (current.showsAccountField) current.accountId else null

        viewModelScope.launch {
            try {
                payments.correctDeposit(
                    contractId = contractId,
                    paymentId = paymentId,
                    // The form's date, not the clock: dating a deposit back to Sunday is the
                    // correction this story exists for.
                    date = current.date,
                    amount = amount,
                    accountId = accountId,
                    referenceNo = current.referenceNo.trim().ifBlank { null },
                    notes = current.notes.trim().ifBlank { null },
                    asOf = today,
                )
                val live = form.value
                if (live != null && live === current) form.value = null
                previewState.value = null
                errorMessage.value = null
                // Distinct from `depositoSaved`: the two announce different outcomes of the
                // same gesture, and a snackbar saying "tersimpan" after a correction would
                // imply a second deposit was recorded.
                message.value = strings.depositoCorrected(formatRupiah(amount))
            } catch (e: AccountRequiredException) {
                // The repository's half of the transfer-account rule. The sheet already
                // refuses it on the field, so reaching here means a caller bypassed the
                // sheet — and the message still has to land somewhere the owner sees.
                refuseOnField(current) { it.copy(accountError = strings.depositoErrorAccountRequired()) }
            } catch (e: DepositAlreadyCanceledException) {
                errorMessage.value = strings.depositoErrorAlreadyCanceled()
            } catch (e: UnknownDepositException) {
                errorMessage.value = strings.depositoErrorNotFound()
            } catch (e: InvalidAmountException) {
                refuseOnField(current) { it.copy(amountError = strings.depositoErrorAmountZero()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Reported, never swallowed. A correction the owner believes landed when it
                // did not leaves arrears on a figure they no longer trust.
                errorMessage.value = strings.depositoErrorUnexpected()
            } finally {
                saving.set(false)
            }
        }
    }

    /**
     * Puts a refusal back on the form the write was made from — and only if it is still there.
     *
     * The `live === current` guard [saveDepositAmount]'s close uses, applied to the refusal
     * rather than the success. Without it a write that fails after the owner dismissed or
     * changed the form would resurrect a sheet that is no longer there, or land an error on
     * fields belonging to a deposit they are now looking at.
     */
    private fun refuseOnField(
        current: DepositFormState,
        refusal: (DepositFormState) -> DepositFormState,
    ) {
        val live = form.value
        if (live != null && live === current) form.value = refusal(current)
    }

    /** A closed form takes every dialog with it. See the close in [saveDepositAmount]. */
    fun closeDeposit() {
        errorMessage.value = null
        previewState.value = null
        cancelState.value = null
        // Cancels any edit open still reading — see [openRequests]. Without this the launch
        // would open the sheet again after the owner dismissed it.
        openRequests++
        form.value = null
    }

    fun consumeMessage() {
        message.value = null
    }

    fun consumeError() {
        errorMessage.value = null
    }

    // --------------------------------------------------------- cancelling one

    /**
     * Opens the cancellation dialog for [paymentId], with its impact already computed.
     *
     * ## Why the figure is computed *before* the dialog opens
     *
     * Because a dialog that opens with a blank line and fills it in a moment later is
     * one the owner can read past. And the figure is `arrearsAfterCanceling` — allocated
     * **without** the deposit rather than the current arrears minus its amount — so it is
     * exactly what the write will produce. That is the property Story 3.3 exists to
     * protect, and `CancelDepositTest` proves it at the data layer with a case where the
     * two answers differ by a day.
     *
     * ## Why the caller passes the contract and renter
     *
     * Because this used to read both from [form] — which meant a cancellation was only
     * reachable from inside the deposit sheet, and Story 3.4 shipped unreachable for
     * exactly that reason. The **deposit history** mounts this verb from outside any
     * form, so the contract is now a parameter and the sheet is merely one caller.
     *
     * Taken as a parameter rather than looked up, because a deposit row already carries
     * its contract and looking it up would be a second read of a fact the row holds.
     *
     * @param contractId the contract the deposit belongs to, from the row.
     * @param renterName for the impact sentence — *"Tunggakan Pak Budi akan kembali menjadi…"* —
     *   names the person, and a row knows it without the sheet being open.
     */
    fun requestCancel(contractId: String, renterName: String, paymentId: String) {
        errorMessage.value = null
        viewModelScope.launch {
            try {
                val arrearsAfter = payments.arrearsAfterCanceling(
                    contractId = contractId,
                    paymentId = paymentId,
                    asOf = LocalDate.now(clock),
                )
                val amount = payments.paymentsFor(contractId)
                    .firstOrNull { it.id == paymentId }
                    ?.amount
                    ?: 0L
                val amountLabel = formatRupiah(amount)
                cancelState.value = CancelDepositUi(
                    paymentId = paymentId,
                    contractId = contractId,
                    amountLabel = amountLabel,
                    title = strings.depositoCancelTitle(amountLabel),
                    // The renter's own name, from the form the sheet already holds: a
                    // cancellation is about *this person's* arrears going back up.
                    impactLine = strings.depositoCancelImpact(
                        renterName = renterName,
                        arrearsAfter = formatRupiah(arrearsAfter),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Said plainly rather than opening a dialog that promises a figure this
                // screen cannot produce — a warning the owner cannot check is worse than
                // no warning.
                errorMessage.value = strings.depositoErrorPreviewFailed()
            }
        }
    }

    /**
     * Carries a reason edit to the dialog.
     *
     * Its own handler rather than folded into [onFormChange], because the reason belongs
     * to the *cancellation* and not to the deposit: a form carrying both would let an
     * edit to one clear the other's error, and the two are never written together.
     */
    fun onCancelChange(transform: (CancelDepositUi) -> CancelDepositUi) {
        cancelState.value = cancelState.value?.let(transform)
    }

    /**
     * The dialog's dismiss.
     *
     * Discards the reason rather than keeping it: the deposit is untouched, and a reason
     * held for a cancellation the owner walked away from would reappear on the next
     * attempt with no explanation of where it came from.
     */
    fun dismissCancel() {
        cancelState.value = null
    }

    /**
     * Cancels the deposit the dialog names.
     *
     * ## Why the blank reason is refused here *and* in the repository
     *
     * Because the two refusals answer different audiences. The UI's is for the owner, who
     * needs the field to say why; the repository's is the rule, and it is the one a caller
     * bypassing the sheet would meet. A check in only one of the two leaves either a
     * control that does nothing or a hole in the data layer.
     */
    fun cancelDeposit() {
        if (!saving.compareAndSet(false, true)) return

        val current = cancelState.value
        if (current == null) {
            saving.set(false)
            return
        }
        if (current.reason.isBlank()) {
            cancelState.value = current.copy(
                reasonError = strings.depositoErrorCancelReasonRequired(),
            )
            saving.set(false)
            return
        }

        viewModelScope.launch {
            try {
                payments.cancelDeposit(
                    contractId = current.contractId,
                    paymentId = current.paymentId,
                    reason = current.reason,
                )
                // The dialog goes, and so does any preview. The **sheet stays**, which is
                // the one difference from `confirmDeposit`: cancelling undoes one deposit
                // rather than submitting the form, and the owner who is recording a second
                // payment this month should not lose the renter and amount they picked for
                // it. Dismissing the whole sheet on a *cancel* would discard typing nobody
                // asked to discard.
                cancelState.value = null
                previewState.value = null
                errorMessage.value = null
                message.value = strings.depositoCanceled(
                    // The figure the dialog named, carried on the state rather than
                    // parsed back out of the title — see `CancelDepositUi.amountLabel`.
                    amount = current.amountLabel,
                )
            } catch (e: net.klinik.rental.data.repository.BlankCancelReasonException) {
                cancelState.value = cancelState.value?.copy(
                    reasonError = strings.depositoErrorCancelReasonRequired(),
                )
            } catch (e: net.klinik.rental.data.repository.DepositAlreadyCanceledException) {
                cancelState.value = null
                errorMessage.value = strings.depositoErrorAlreadyCanceled()
            } catch (e: net.klinik.rental.data.repository.UnknownDepositException) {
                cancelState.value = null
                errorMessage.value = strings.depositoErrorPreviewFailed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Reported, never swallowed. A cancellation the owner believes happened
                // when it did not leaves arrears understated — the failure that costs them
                // real money.
                errorMessage.value = strings.depositoErrorUnexpected()
            } finally {
                saving.set(false)
            }
        }
    }

    /**
     * The repository's figures, rendered into UX-DR10's exact sentences.
     *
     * ## Why the credit line is `null` rather than `Kredit: Rp 0`
     *
     * A line that always appears stops being read. The epic context says credit must never
     * appear as a *mystery number*, and a zero credit is not a mystery — it is nothing
     * happening, shown in the position where something would have been.
     *
     * ## Why the two "closes" shapes rather than one with a fragment
     *
     * The epic's format has two different sentences, and bolting an optional remainder
     * onto the shorter one leaves a dangling separator when nothing remains to say.
     */
    private fun net.klinik.rental.data.repository.DepositPreview.toUi() = DepositPreviewUi(
        arrearsLine = strings.depositoPreviewArrears(
            before = formatRupiah(arrearsBefore),
            after = formatRupiah(arrearsAfter),
        ),
        closesLine = if (daysRemaining > 0) {
            strings.depositoPreviewCloses(
                daysClosed = daysClosed,
                daysRemaining = daysRemaining,
                remainingAmount = formatRupiah(arrearsAfter),
            )
        } else {
            strings.depositoPreviewClosesOnly(daysClosed)
        },
        creditLine = if (hasCredit) {
            strings.depositoPreviewCredit(formatRupiah(credit))
        } else {
            null
        },
    )

    private companion object {
        /**
         * The id the preview allocates against, and never stores.
         *
         * A placeholder rather than a real id because the preview writes nothing, and
         * minting a real one here would leave the id in the dialog and the id stored able
         * to differ — a preview that described a different transaction than the one saved.
         */
        const val PREVIEW_ID = "preview"
    }
}

