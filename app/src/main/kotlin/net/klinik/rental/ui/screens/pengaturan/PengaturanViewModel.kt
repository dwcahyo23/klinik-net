package net.klinik.rental.ui.screens.pengaturan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.klinik.rental.data.backup.BackupJsonReader
import net.klinik.rental.data.backup.BackupRepository
import net.klinik.rental.data.backup.BackupRestorer
import net.klinik.rental.data.backup.RestorePreview
import net.klinik.rental.data.backup.RestoreRejected
import net.klinik.rental.data.db.SCHEMA_VERSION
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.data.repository.BusinessIdentity
import net.klinik.rental.data.repository.BusinessIdentityRepository
import net.klinik.rental.data.repository.PinLockRepository
import net.klinik.rental.data.repository.BlankIdentityNameException
import net.klinik.rental.data.repository.CutoffChangePreview
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.domain.validation.InvalidCutoffDayException
import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.domain.model.BusinessAccount
import net.klinik.rental.domain.validation.InvalidAccountNumberException
import net.klinik.rental.ui.backup.BackupFileWriter
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.strings.RStrings
import java.time.Clock
import java.time.LocalDate

/**
 * The settings screen: the business accounts the owner receives transfers into.
 *
 * ## Why this screen exists at all in Story 3.1
 *
 * Story 3.2's deposit form selects an account when the payment method is a bank transfer,
 * and a form whose dropdown has no source list has to be built twice — once empty, once
 * populated. So the list exists first. See `epic-3-context.md`'s cross-story section.
 *
 * ## Why there is no delete anywhere in this class
 *
 * Story 3.1's acceptance criterion says an account a deposit uses cannot be deleted, and
 * that the rule is enforced in the data layer. The table that would make an account "in
 * use" — `PAYMENT` — is Story 3.2's and does not exist, so a delete here would carry a guard
 * whose condition is vacuously true. `BusinessAccountDao`'s KDoc has the full argument. The
 * short version: the guard lands with the foreign key, in 3.2.
 *
 * ## Why saving is one flow rather than add and edit methods
 *
 * `AccountFormState.id` is null while adding and set while editing, and the repository
 * decides insert-vs-update from the *stored* row. A separate `add` and `edit` here would be
 * two methods the screen has to choose between correctly, and the wrong choice inserts a
 * duplicate account for one bank.
 */
class PengaturanViewModel(
    private val repository: BusinessAccountRepository,
    private val identity: BusinessIdentityRepository,
    private val strings: RStrings,
    /**
     * Story 5.1's export, optional rather than required.
     *
     * Optional because this ViewModel is the *settings* screen's, not the backup's: an
     * exporter is handed in where it exists and absent where it does not, rather than every
     * caller of a settings screen having to build one. `exportTo` refuses in words when it is
     * absent — the same shape `SetoranViewModel.openHistory` uses for its nullable repository —
     * so the missing case is a **message**, not a silently dead button.
     */
    private val backups: BackupRepository? = null,
    /**
     * Story 5.2's restore, optional on the same argument as [backups].
     *
     * A separate parameter rather than folded into [backups] because the two have different
     * lifetimes and different failure surfaces: an export reads eight tables, a restore reads a
     * file *and* replaces eight tables, and the only object that can do the second is one holding
     * the database handle.
     */
    private val restorer: BackupRestorer? = null,
    /**
     * Story 5.3's lock, optional on the same argument.
     *
     * Optional so every existing test that builds a settings ViewModel without a lock keeps
     * compiling — and so the security card simply does not appear when there is no lock to
     * configure, rather than drawing a switch wired to nothing.
     */
    private val pinLock: PinLockRepository? = null,
    /**
     * Story 5.4's cutoff, and the one repository that computes arrears.
     *
     * Optional on the same argument as [pinLock]: a settings ViewModel built without a
     * payment repository simply has no cutoff card, rather than one whose button does nothing.
     */
    private val payments: PaymentRepository? = null,
    /**
     * The clock the preview computes "as of".
     *
     * Read from the caller rather than from the repository because the repository's own clock is
     * used for writes; a preview that silently used a different "today" than the arrears list
     * would show the owner a figure for a period they are not in.
     */
    private val clock: Clock? = null,
) : ViewModel() {

    /** The open form, or `null` when the sheet is closed. */
    private val form = MutableStateFlow<AccountFormState?>(null)

    /**
     * The business-identity form — Story 4.4's, beside the account one rather than inside it.
     *
     * A separate flow because it is a separate sheet with a separate lifetime: collapsing the
     * two would mean closing the account sheet also discarded a half-typed business name, which
     * is the defect `SetoranViewModel` keeps three flows apart to avoid.
     */
    private val identityForm = MutableStateFlow<IdentityFormState?>(null)

    /**
     * The identity sheet's **own** banner.
     *
     * Separate from [errorMessage] for the reason
     * `PengaturanUiState.identityErrorMessage` gives: two windows sharing one message slot is
     * how a dismissal clears another form's message, and how the account sheet's refusal ends
     * up drawn inside the identity window.
     */
    private val identityError = MutableStateFlow<String?>(null)

    /**
     * The stored identity, read once and refreshed after a save.
     *
     * A `StateFlow` rather than a `Flow` from the DAO because `settings` has **no** read
     * notification to observe: Room re-emits a flow for the tables its query names, and a
     * key/value read names one key, so a write to `businessPhone` would not re-emit a query for
     * `businessName`. Refreshing after the write is therefore the honest mechanism, and it is
     * also the only one that exists until this table has a `Flow` query of its own.
     */
    private val storedIdentity = MutableStateFlow<IdentityRow?>(null)

    /**
     * Why the stored identity could not be read, or `null`.
     *
     * A **failure**, distinct from [storedIdentity] being `null` for "not read yet" — the
     * distinction the loading state depends on. `PengaturanUiState.hasIdentity` reads the row,
     * so a failed read would otherwise look exactly like a fresh install and would let
     * `openIdentityForm` write the default over a real business name.
     */
    private val identityLoadError = MutableStateFlow<String?>(null)

    /** A transient confirmation. Success does not change screen state. */
    private val message = MutableStateFlow<String?>(null)

    /**
     * The last refusal that has no field of its own.
     *
     * Rendered by the screen as a banner. There is only one refusal that lands here — a
     * write that failed for a reason this screen does not model — because the two typed
     * refusals both name a field.
     */
    private val errorMessage = MutableStateFlow<String?>(null)

    /**
     * Whether a write is in flight, so a double tap on Save cannot enqueue two.
     *
     * An `AtomicBoolean` rather than a `MutableStateFlow<Boolean>`: this is not rendered, so
     * putting it in the state would recompose the screen on a value nothing draws. The same
     * guard `PenyewaViewModel` and `KendaraanViewModel` use for the same reason.
     */
    private val saving = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Whether Story 5.1's export is in flight.
     *
     * A **flow** rather than the `AtomicBoolean` [saving] is, because unlike a save this value is
     * rendered: the action button draws disabled while it is true. An `AtomicBoolean` would have
     * to be copied into the `combine` on every write, which is a second fact to keep in step —
     * and the screen's button enabled-state disagreeing with an export in progress is exactly
     * what the flag exists to prevent.
     *
     * It also serves as the double-tap guard, and does so without a CAS: the flag is set
     * **synchronously** before the coroutine is launched, so a second tap arriving before the
     * coroutine starts already sees `true`.
     */
    private val exporting = MutableStateFlow(false)

    /** A failed export. Its own slot so the screen's form banner cannot swallow it. */
    private val exportErrorMessage = MutableStateFlow<String?>(null)

    /**
     * The parsed file awaiting the owner's decision — the dialog's visibility **and** its content.
     *
     * Holding the parsed `Restore` rather than only a formatted preview is the property that makes
     * the preview honest: [confirmRestore] writes this exact object, so what the owner saw and what
     * lands in the database cannot be two different reads of the file.
     */
    private val pendingRestore = MutableStateFlow<BackupJsonReader.Restore?>(null)

    /** A restore in flight — rendered, so a second confirm cannot start a second replacement. */
    private val restoring = MutableStateFlow(false)

    private val restoreErrorMessage = MutableStateFlow<String?>(null)

    /**
     * Story 5.3's lock state, re-read after every write rather than derived from the flow.
     *
     * `null` until the first read lands, so the card cannot draw the switch in the wrong position
     * for a frame — see `PengaturanUiState.pin`.
     */
    private val pinSettings = MutableStateFlow<PinSettingsUi?>(null)

    /** The setup flow, or `null` when closed. */
    private val pinSetup = MutableStateFlow<PinSetupUi?>(null)

    /**
     * The digits typed so far in each setup step — **never** in the flow's state.
     *
     * Same rule as the lock screen's: a `UiState` is a bundle-able object, and a PIN in one is a
     * PIN in a log. The state carries only how many dots are filled.
     */
    private var setupPin = ""
    private var setupConfirm = ""

    /** Story 5.4's cutoff day, re-read after every write. `null` until the first read. */
    private val cutoffSettings = MutableStateFlow<CutoffSettingsUi?>(null)

    /** A refused cutoff change. Its own slot, so it cannot clear the form's message. */
    private val cutoffErrorMessage = MutableStateFlow<String?>(null)

    /**
     * The computed preview awaiting a decision, or `null`.
     *
     * The `CutoffChangePreview` itself rather than the formatted text, so [confirmCutoffChange]
     * writes the very figures the owner saw — a second preview on confirm could disagree with the
     * one that was approved.
     */
    private val pendingCutoffPreview = MutableStateFlow<CutoffChangePreview?>(null)

    /**
     * The accounts plus "has the read happened yet", as one flow.
     *
     * A derived flag rather than a separate `MutableStateFlow<Boolean>`: a flag set in the
     * constructor and cleared on the first emission is a second thing to keep in step with
     * the subscription, and a screen whose flag disagrees with its list is exactly what the
     * flag exists to prevent. Wrapping the *same* `observeAccounts()` call the list reads
     * means one subscription, one query.
     */
    private fun accountsWithLoadFlag(): Flow<Pair<List<BusinessAccount>, Boolean>> =
        repository.observeAccounts()
            .map { it to true }
            .onStart { emit(emptyList<BusinessAccount>() to false) }

    /**
     * The screen state.
     *
     * ## Why the sources are bundled by concern rather than passed one by one
     *
     * `combine` has typed overloads up to five sources and a sixth falls through to the `vararg`
     * one whose lambda takes an `Array<Any?>` — the untyped shape this class's own KDoc warns
     * about. Nesting pairs of them kept it typed until Story 5.4 added a seventh concern and the
     * nesting itself became a `Pair<Pair<A, B>, C>` this file could no longer read.
     *
     * So each concern bundles **once**, into a value class of its own, and the outer `combine`
     * takes four typed sources. The alternative — a ninth `combine` nesting level — is exactly the
     * kind of shape that survives until someone adds a field and the whole expression falls over.
     */
    val uiState: StateFlow<PengaturanUiState> = combine(
        accountsWithLoadFlag().map { (accounts, emitted) -> accounts to emitted },
        combine(form, errorMessage) { currentForm, error -> FormSnapshot(currentForm, error) },
        combine(identityForm, storedIdentity, identityError) { form, stored, error ->
            IdentitySnapshot(form, stored, error)
        },
        combine(
            combine(exporting, exportErrorMessage) { busy, failure -> ExportSnapshot(busy, failure) },
            combine(
                combine(pendingRestore, restoring, restoreErrorMessage) { pending, busy, failure ->
                    RestoreSnapshot(pending?.preview?.toUi(), busy, failure)
                },
                combine(pinSettings, pinSetup) { pin, setup -> PinSnapshot(pin, setup) },
            ) { restore, pin -> restore to pin },
            combine(cutoffSettings, pendingCutoffPreview, cutoffErrorMessage) { cut, pending, failure ->
                CutoffSnapshot(cut, pending?.toUi(), failure)
            },
        ) { export, restoreAndPin, cutoff ->
            DatalessTriple(export, restoreAndPin, cutoff)
        },
    ) { accountsAndEmitted, formSnapshot, identitySnapshot, extras ->
        val (accounts, hasEmitted) = accountsAndEmitted
        val (restoreSnapshot, pinSnapshot) = extras.restoreAndPin
        PengaturanUiState(
            // The projection to a row happens here, once per emission, so the screen renders
            // what it is handed and no composable formats a value.
            rows = accounts.map { it.toRow() },
            form = formSnapshot.form,
            identity = identitySnapshot.stored,
            identityForm = identitySnapshot.form,
            identityErrorMessage = identitySnapshot.error,
            // The confirmation is the same message the account save produces, read from the
            // one flow both share: success does not change screen state, so it is a
            // transient bar rather than a field either sheet owns.
            identitySuccessMessage = null,
            errorMessage = formSnapshot.error,
            isExporting = extras.export.busy,
            exportErrorMessage = extras.export.failure,
            restorePreview = restoreSnapshot.preview,
            isRestoring = restoreSnapshot.busy,
            restoreErrorMessage = restoreSnapshot.failure,
            cutoff = extras.cutoff.settings,
            cutoffPreview = extras.cutoff.preview,
            cutoffErrorMessage = extras.cutoff.failure,
            pin = pinSnapshot.settings,
            pinSetup = pinSnapshot.setup,
            isLoading = !hasEmitted,
        )
    }
        // Distinct *after* the projection, so an emission that produces an identical list
        // does not push a new state downstream and recompose every row for no visible change.
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            // `WhileSubscribed(5_000)` keeps the combine alive across a rotation instead of
            // re-running the query on every configuration change.
            started = SharingStarted.WhileSubscribed(5_000),
            // `isLoading = true`: before the first emission there is genuinely nothing to
            // show, and the field's `false` default would render "Belum ada rekening" — an
            // invitation to add an account the owner already added, and a screen that reads
            // like a fresh install — for the frame before the read lands.
            initialValue = PengaturanUiState(isLoading = true),
        )

    /** A transient confirmation. Success does not change screen state. */
    val snackbarMessage: StateFlow<String?> = message

    /** The interaction half: the open form and the last banner-level refusal. */
    private data class FormSnapshot(
        val form: AccountFormState?,
        val error: String?,
    )

    /**
     * The identity half of the screen, as one value.
     *
     * Nested rather than folded into [FormSnapshot] because the two forms are never open at
     * once and are never written together: pairing them would let a dismissal of one clear the
     * other's state, which is the defect `SetoranViewModel.onCancelChange` is kept apart from
     * `onFormChange` to prevent.
     */
    private data class IdentitySnapshot(
        val form: IdentityFormState?,
        val stored: IdentityRow?,
        val error: String?,
    )

    /**
     * Story 5.1's half of the screen, as one value.
     *
     * Nested for the reason [IdentitySnapshot] gives: an export in flight and a form being
     * typed are never both true, and pairing them would let closing a sheet clear the export's
     * failure — which is the defect the screen's banner suppression makes visible.
     */
    private data class ExportSnapshot(
        val busy: Boolean,
        val failure: String?,
    )

    /** Story 5.2's half: the preview (which is also the dialog's visibility) and its own failure. */
    private data class RestoreSnapshot(
        val preview: RestorePreviewUi?,
        val busy: Boolean,
        val failure: String?,
    )

    /** Story 5.3's half: the card's figures and the setup flow, which are read together. */
    private data class PinSnapshot(
        val settings: PinSettingsUi?,
        val setup: PinSetupUi?,
    )

    /** Story 5.4's half: the card's day, the pending preview, and its refusal. */
    private data class CutoffSnapshot(
        val settings: CutoffSettingsUi?,
        val preview: CutoffPreviewUi?,
        val failure: String?,
    )

    /**
     * Three sources where `combine` has no three-argument overload worth reaching for.
     *
     * A named class rather than a `Triple` because a `Triple` of snapshots reads as
     * `extras.third` at the use site, and this file has five of those. Named fields mean a
     * reordering of the arguments is a compile error rather than a silent swap.
     */
    private data class DatalessTriple(
        val export: ExportSnapshot,
        val restoreAndPin: Pair<RestoreSnapshot, PinSnapshot>,
        val cutoff: CutoffSnapshot,
    )

    /**
     * The repository's preview, formatted for the dialog.
     *
     * Every sentence here is the AC's own two numbers plus the period labels. The delta's sign is
     * stated in words rather than left to a `+`/`−` glyph: an owner reading "Rp 750.000" with no
     * direction cannot tell a rise from a fall, and a signed figure in a sentence is a sentence
     * that survives being read aloud.
     */
    private fun CutoffChangePreview.toUi() = CutoffPreviewUi(
        day = newCutoffDay,
        changedLine = when (changedContractCount) {
            0 -> strings.pengaturanCutoffNoContracts()
            1 -> strings.pengaturanCutoffOneContract()
            else -> strings.pengaturanCutoffManyContracts(changedContractCount)
        },
        deltaLine = when {
            totalDelta == 0L -> strings.pengaturanCutoffNoChange()
            totalDelta > 0 -> strings.pengaturanCutoffUp(formatRupiah(totalDelta))
            else -> strings.pengaturanCutoffDown(formatRupiah(-totalDelta))
        },
        periodLine = periodLineFor(oldCutoffDay, newCutoffDay),
    )

    init {
        // Read once on construction rather than per screen-open: the name is pre-filled into
        // the form from here, so a read on open would leave the field empty for the frame
        // between the tap and the answer.
        viewModelScope.launch {
            storedIdentity.value = identity.identity().toRow()
            identityLoadError.value = null
        }.invokeOnCompletion { cause ->
            if (cause != null) identityLoadError.value = strings.pengaturanErrorUnexpected()
        }

        // Story 5.3's card, read once on construction. Read here rather than per screen-open for
        // the same reason the identity is: the switch must show its real position on the first
        // frame, and a read on open would draw it off for one frame — a switch in the wrong
        // position is a switch the owner flips the wrong way.
        viewModelScope.launch {
            pinLock?.let { lock ->
                pinSettings.value = PinSettingsUi(
                    enabled = lock.isEnabled(),
                    autoLockMinutes = lock.autoLockMinutes(),
                )
            }
        }

        // Story 5.4's cutoff, read once for the same reason: the card must show the owner's
        // real day on the first frame, not the default followed by a correction.
        viewModelScope.launch {
            payments?.let { repository ->
                cutoffSettings.value = CutoffSettingsUi(day = repository.cutoffDay())
            }
        }
    }

    // ------------------------------------------------------------------- form

    /**
     * Opens the form on a **new** account.
     *
     * Both fields open empty rather than pre-filled from the most recent account. A default
     * bank name would be the prototype's hidden default wearing a label — the owner would
     * save without reading it — and the same rule kept the contract's start date and end date
     * empty. There is no "most recent" shortcut here for the same reason: an account is
     * added rarely enough that retyping is cheaper than a wrong row.
     */
    fun openAddForm() {
        errorMessage.value = null
        // Closes the identity form rather than stacking over it — the mirror of
        // [openIdentityForm]'s own guard. Only one of the two sheets may be up: two
        // `ModalBottomSheet` windows over one state is the defect Story 2.8's review found as
        // its only `high`, and the screen's comment states this as a property rather than a
        // hope. The modal blocks the owner from reaching the add button today, but a deep link
        // or a second caller would not be blocked, and a one-way guard would read as complete.
        identityForm.value = null
        form.value = AccountFormState()
    }

    /**
     * Opens the form on [row], pre-filled.
     *
     * Pre-filling is the opposite case to [openAddForm] and is correct here: an edit is *about*
     * the stored values, so showing them is the whole point. The id travels, which is what
     * makes the save an update rather than a second insert.
     */
    fun openEditForm(row: AccountRow) {
        errorMessage.value = null
        // The same one-sheet-at-a-time guard [openAddForm] carries, for the same reason.
        identityForm.value = null
        form.value = AccountFormState(
            id = row.account.id,
            bankName = row.account.bankName,
            number = row.account.number,
        )
    }

    /**
     * Closes the form, clearing its own banner with it.
     *
     * The banner goes too, for the reason `PenyewaViewModel.closeForm` clears `errorMessage`:
     * a refusal raised while the sheet was up must not survive onto the list, where it would
     * read as a current failure with nothing left to explain it.
     */
    fun closeForm() {
        errorMessage.value = null
        form.value = null
    }

    /** Applies a form edit. Pure and hoisted, so the transform is testable without Compose. */
    fun onFormChange(transform: (AccountFormState) -> AccountFormState) {
        form.value = form.value?.let(transform)
    }

    /**
     * Saves the form, translating each typed exception into its Indonesian copy.
     *
     * ## Where the rules are enforced, twice
     *
     * 1. Here — an incomplete form is refused before an account exists, so the message lands
     *    on the field rather than as a generic refusal
     * 2. In `BusinessAccountRepository` — so a second screen cannot pass a check this one has,
     *    and so a caller holding two strings and no model is still covered
     *
     * Step 1 is a courtesy, not the rule. The format refusal has no step 1 at all: the field
     * accepts what the owner types and the repository reports what is wrong with it, because
     * a disabled button beside a filled field leaves them with no explanation.
     *
     * On success the form closes and the list updates on its own — the database re-emits and
     * `combine` rebuilds the state. There is no manual "now refresh".
     */
    fun saveForm() {
        // A second tap while the first write is in flight would enqueue a second save for the
        // same account. The button's `enabled` is a courtesy; this is the rule.
        if (!saving.compareAndSet(false, true)) return

        val current = form.value
        if (current == null) {
            saving.set(false)
            return
        }

        // The courtesy half: refuse an incomplete form here so the message lands on the field
        // the owner is looking at, rather than coming back from the repository as a refusal
        // about an account they never got to build.
        val missing = current.firstMissingAccountField()
        if (missing != null) {
            form.value = current.withBlankRefusal(missing)
            saving.set(false)
            return
        }

        val account = current.toBusinessAccount(repository.newAccountId())
        if (account == null) {
            // `firstMissingAccountField` above returned null, so this is the two functions
            // disagreeing — the safe direction is to refuse rather than write an account built
            // from a field that was not there. It is not silent: the owner gets the banner,
            // because a disagreement here is a defect and must not read as a successful save.
            errorMessage.value = strings.pengaturanErrorUnexpected()
            saving.set(false)
            return
        }

        viewModelScope.launch {
            try {
                repository.save(account)
                // Conditional, for the reason `saveForm`'s close is in the other screens: the
                // write is a suspension point and the owner keeps interacting across it. A
                // sheet they dismissed and reopened while the write was in flight is a
                // different instance and is left alone.
                val live = form.value
                // Identity only. An `id` comparison would be wrong here: a new account's
                // form has a null id both before and after the write, so two *different*
                // forms would compare equal and a reopened sheet would be closed under the
                // owner. The reference is what distinguishes "the sheet I submitted" from
                // "a sheet opened since".
                if (live != null && live === current) form.value = null
                errorMessage.value = null
                message.value = strings.pengaturanAccountSaved(account.bankName)
            } catch (e: InvalidAccountNumberException) {
                val live = form.value ?: current
                form.value = live.copy(
                    numberError = strings.pengaturanErrorNumberFormat(),
                )
            } catch (e: CancellationException) {
                // Re-thrown before the generic handler. `CancellationException` extends
                // `IllegalStateException`, so a cleared ViewModel would otherwise show
                // "Gagal menyimpan" for a write that was merely cancelled.
                throw e
            } catch (e: Exception) {
                // The write failed for a reason this screen does not model. Reported rather
                // than swallowed: a failed write must fail loudly and never look like a
                // fresh account.
                errorMessage.value = strings.pengaturanErrorUnexpected()
            } finally {
                saving.set(false)
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    // ------------------------------------------------------------ Story 4.4

    /**
     * Opens the identity form, pre-filled with what is stored.
     *
     * **All three fields are seeded**, and that is the whole of this method. Pre-filling the
     * name alone would open the address and phone empty, and `toBusinessIdentity` turns an
     * empty field into `null` — which the repository reads as *clear it*. So an owner fixing a
     * typo in the name would see a success confirmation and lose the other two, on the one
     * action the UI cannot undo.
     *
     * The default name fills in only when nothing has been stored, because a fresh form has no
     * values to pre-fill and a blank business name would end up in every outgoing message.
     */
    fun openIdentityForm() {
        errorMessage.value = null
        // **Closes the account form rather than stacking over it.** Two `ModalBottomSheet`
        // windows over one state is the defect Story 2.8's review found as its only `high`:
        // two tap-blockers, two scrims, and one screen that scrolls under both. The owner who
        // taps the identity section while an account sheet is up gets the identity sheet —
        // and the account's half-typed bank name, which they would have to retype.
        form.value = null

        val stored = storedIdentity.value
        // A tap before the read lands would otherwise open the form on the **default** while
        // the stored identity is already in hand — and the owner's next save would write that
        // default over their real business name. So the form waits for the read rather than
        // racing it, and the section's own action is disabled until it has arrived.
        if (stored == null) {
            identityError.value = strings.pengaturanErrorUnexpected()
            return
        }

        identityForm.value = IdentityFormState(
            name = stored.name,
            address = stored.address.orEmpty(),
            phone = stored.phone.orEmpty(),
        )
    }

    /**
     * Closes the identity form, clearing its own banner with it.
     *
     * The account form's banner goes with it rather than being left behind: a refusal raised
     * while this sheet was up must not survive onto the list, where it would read as a current
     * failure about an account with nothing left to explain it.
     */
    fun dismissIdentity() {
        errorMessage.value = null
        identityError.value = null
        identityForm.value = null
    }

    /** The identity sheet's own banner channel. See `PengaturanUiState.identityErrorMessage`. */
    fun consumeIdentityError() {
        identityError.value = null
    }

    /** Applies an identity edit. Pure and hoisted, like [onFormChange]. */
    fun onIdentityChange(transform: (IdentityFormState) -> IdentityFormState) {
        identityForm.value = identityForm.value?.let(transform)
    }

    /**
     * Saves the identity as one block.
     *
     * ## Where the blank-name rule is enforced, twice
     *
     * 1. Here — so the message lands on the field the owner is looking at, rather than coming
     *    back from the repository as a refusal about an identity they never finished building
     * 2. In `BusinessIdentityRepository.save` — so a second screen, or a caller holding three
     *    strings and no model, cannot pass the check this one has
     *
     * Step 1 is a courtesy; the repository's is the rule. Neither is dead: the form holds
     * `String`s and is built independently of the model, so the check here runs first and
     * names a field, and the repository's runs for every other caller.
     *
     * ## Why the stored identity is re-read after the write
     *
     * Because the section's header reads it, and a header still showing the old name beside a
     * form that just saved a new one is a screen contradicting itself. `settings` has no read
     * notification to observe — see [storedIdentity] — so the re-read is the mechanism rather
     * than a workaround.
     */
    fun saveIdentity() {
        if (!saving.compareAndSet(false, true)) return

        val current = identityForm.value
        if (current == null) {
            saving.set(false)
            return
        }

        val identity = current.toBusinessIdentity()
        if (identity == null) {
            identityForm.value = current.copy(
                nameError = strings.pengaturanErrorBlankIdentityName(),
            )
            saving.set(false)
            return
        }

        viewModelScope.launch {
            try {
                this@PengaturanViewModel.identity.save(
                    identity.name,
                    identity.address,
                    identity.phone,
                )
                // Identity only, for the reason `saveForm`'s close carries: the reference is
                // what distinguishes "the sheet I submitted" from "a sheet opened since".
                val live = identityForm.value
                if (live != null && live === current) identityForm.value = null
                storedIdentity.value = this@PengaturanViewModel.identity.identity().toRow()
                errorMessage.value = null
                identityError.value = null
                message.value = strings.pengaturanIdentitySaved(identity.name)
            } catch (e: BlankIdentityNameException) {
                val live = identityForm.value ?: current
                identityForm.value = live.copy(
                    nameError = strings.pengaturanErrorBlankIdentityName(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // On the identity's **own** channel: routing this to the account screen's
                // banner would draw it inside the wrong window and clear it through the
                // wrong setter.
                identityError.value = strings.pengaturanErrorUnexpected()
            } finally {
                saving.set(false)
            }
        }
    }

    /** Projects the stored identity onto its row. The name is already resolved. */
    /**
     * Projects the stored identity onto its row.
     *
     * **All three fields travel.** A row carrying only the name would seed an edit with the
     * address and phone empty, and saving that writes `null` for both — which
     * `BusinessIdentityRepository.save` treats as *clear this field*. The owner corrects a typo
     * in the business name and loses the address and phone they entered weeks ago, with a
     * confirmation saying it saved. `IdentityRow`'s KDoc carries the full argument.
     */
    private fun BusinessIdentity.toRow() = IdentityRow(
        name = name,
        address = address,
        phone = phone,
    )

    fun consumeError() {
        errorMessage.value = null
    }

    // ------------------------------------------------------- the backup export

    /**
     * Writes the whole database to [uri], which the Storage Access Framework already granted.
     *
     * ## Why the picker is **not** launched from here
     *
     * Because a launcher needs an `ActivityResultLauncher`, and this class deliberately holds no
     * Android context of its own — `RStrings` is the only thing here that touches the framework,
     * and it does so to read a string. The screen's route owns the launcher and hands the granted
     * `Uri` back here, which keeps the export's two halves in the order they must happen and
     * leaves this class testable with a repository and no Robolectric activity at all.
     *
     * ## Why the file is written even when the owner has no accounts
     *
     * Because "no accounts" is a fact the backup must be able to carry: a fresh install whose
     * owner exports before typing anything still gets a valid, complete, empty file rather than
     * an error. Refusing would make the first backup — the one taken before anything can go
     * wrong — the one that fails.
     *
     * A `null` [backups] reports the failure in the UI rather than throwing: this method is
     * called from a coroutine whose caller is a `Uri` callback, and an exception crossing that
     * boundary takes the Activity with it.
     */
    fun exportTo(uri: Uri) {
        if (backups == null) {
            exportErrorMessage.value = strings.pengaturanErrorExportFailed()
            return
        }
        // Set synchronously, not inside the coroutine — see `exporting`'s KDoc.
        if (exporting.value) return
        exporting.value = true

        exportErrorMessage.value = null
        viewModelScope.launch {
            try {
                val document = backups.build()
                val written = BackupFileWriter.write(strings.contextRef, uri, document.json)
                if (written) {
                    // The screen's **shared** success slot, now with a third writer: an account
                    // save, an identity save and an export all land here, newest wins. That is
                    // right for the same reason they share one banner — two `BottomCenter`
                    // bars would stack on the same pixels — and the sequence that could lose a
                    // message is two successes within the life of one bar, where the owner has
                    // already seen both. The export's *failure* does not come here.
                    message.value = strings.pengaturanExportSaved(document.filename)
                } else {
                    exportErrorMessage.value = strings.pengaturanErrorExportFailed()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Said plainly rather than silently. A backup that half-wrote and reported
                // success is worse than one that failed loudly: the owner would stop taking
                // backups believing they had a current one.
                exportErrorMessage.value = strings.pengaturanErrorExportFailed()
            } finally {
                exporting.value = false
            }
        }
    }

    /** Dismisses the export's failure banner and re-arms the action. */
    fun consumeExportError() {
        exportErrorMessage.value = null
    }

    /**
     * Parses [json] and, if it is a file this build can restore, shows the preview.
     *
     * ## Why parsing happens here and not when the owner confirms
     *
     * Because the preview **is** the decision. A flow that confirmed first and parsed after would
     * be asking the owner to approve a file nobody had read, and the AC's whole point is that the
     * counts appear before anything is replaced.
     *
     * ## Why a refusal clears nothing
     *
     * Because a refused file has not touched the database — the parse is the only thing that ran.
     * The banner explains why, the owner keeps their data, and the dialog never appears.
     */
    fun onRestoreFileChosen(json: String?) {
        if (json == null) {
            // The file could not be opened at all. A different next action from "wrong file", so
            // a different sentence — the owner may be able to fix a permission and retry.
            restoreErrorMessage.value = strings.pengaturanRestoreUnreadable()
            return
        }
        if (restorer == null) {
            restoreErrorMessage.value = strings.pengaturanRestoreUnreadable()
            return
        }
        // The same guard `endContract` and `settleThrough` use, read from the ViewModel's clock
        // rather than from inside the reader: a backup written before those guards existed can
        // carry a future end date, and restoring it would free the car for a rental still running
        // with no in-app way back. `clock` is optional on this class, so an absent clock cannot
        // answer "is this date in the future" — and a reader that guessed would be worse than one
        // that refuses, so the file is reported unreadable rather than restored unchecked.
        val clock = clock ?: run {
            restoreErrorMessage.value = strings.pengaturanRestoreUnreadable()
            return
        }
        restoreErrorMessage.value = null
        try {
            pendingRestore.value = restorer.parse(json, asOf = LocalDate.now(clock))
        } catch (e: RestoreRejected) {
            // The reader's own reason, turned into a sentence by one `when`. A generic message
            // here would throw away the only information that helps: which of "wrong file",
            // "wrong version" and "corrupt file" this is.
            restoreErrorMessage.value = strings.pengaturanRestoreRefused(
                reason = e.reason,
                fileVersion = e.fileVersion,
                appVersion = SCHEMA_VERSION,
            )
        }
    }

    /** Closes the preview without writing anything. */
    fun dismissRestore() {
        pendingRestore.value = null
    }

    /**
     * Replaces the whole database with the file the owner just approved.
     *
     * ## Why the guard is set synchronously
     *
     * The same reason [exportTo]'s is: a second tap arriving before the coroutine starts would
     * otherwise see `false` and start a second replacement. Two restores racing over one database
     * is not a UI defect — it is two transactions clearing and writing the same tables.
     */
    fun confirmRestore() {
        val restore = pendingRestore.value ?: return
        val restorer = restorer ?: return
        if (restoring.value) return
        restoring.value = true

        viewModelScope.launch {
            try {
                restorer.apply(restore)
                // Cleared only on success: a failed restore must leave the dialog up with its
                // banner, so the owner can see the file they chose is still the one on screen.
                pendingRestore.value = null
                message.value = strings.pengaturanRestoreDone()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The transaction rolled back, so the old data is intact — and saying so is the
                // difference between "nothing happened" and "your records are gone".
                restoreErrorMessage.value = strings.pengaturanRestoreRefused(
                    reason = BackupJsonReader.REASON_BAD_ROW,
                    fileVersion = null,
                    appVersion = SCHEMA_VERSION,
                )
            } finally {
                restoring.value = false
            }
        }
    }

    /** Dismisses the restore's banner. */
    fun consumeRestoreError() {
        restoreErrorMessage.value = null
    }

    /**
     * The preview the dialog draws, or `null` when nothing is pending.
     *
     * Formatted here rather than in the dialog for the reason every `*Ui` type in this app gives:
     * a figure assembled in a composable is assembled per recomposition, and the counts line is
     * three numbers and three nouns in one sentence.
     */
    private fun RestorePreview.toUi(): RestorePreviewUi = RestorePreviewUi(
        countsLine = strings.pengaturanRestoreCounts(
            vehicles = vehicleCount,
            renters = renterCount,
            payments = paymentCount,
        ),
        periodLine = lastPaymentDate
            ?.let { strings.pengaturanRestorePeriod(strings.formatExceptionDate(it)) }
            // The file has no deposits. `maxOfOrNull` gave us `null` rather than an epoch date,
            // so the copy can say so instead of printing "1 Jan 1970" and looking broken.
            ?: strings.pengaturanRestorePeriodEmpty(),
    )

/**
     * Story 5.3's setup flow: choose a PIN, confirm it, then show the recovery code once.
     *
     * ## Why nothing is written until the code is confirmed
     *
     * Because the AC says the flow is not finished before that confirmation. A lock written at the
     * PIN step would leave the owner with a PIN they know and an escape hatch they dismissed —
     * which is the exact state the recovery code exists to prevent.
     *
     * ## Why the PIN is entered twice
     *
     * Because a mistyped PIN on a lock with no way in is unrecoverable, and the recovery code is
     * the only way out. Typing it twice costs two seconds; getting it wrong costs the records.
     */
    fun openPinSetup() {
        setupPin = ""
        setupConfirm = ""
        pinSetup.value = PinSetupUi(step = PinSetupStep.EnterPin)
    }

    /** Closes the setup flow. Nothing has been written, so there is nothing to undo. */
    fun dismissPinSetup() {
        setupPin = ""
        setupConfirm = ""
        pinSetup.value = null
    }

    /** One key of the setup grid. */
    fun onSetupDigit(digit: Char) {
        val current = pinSetup.value ?: return
        when (current.step) {
            PinSetupStep.EnterPin -> {
                if (setupPin.length >= PinLockRepository.PIN_LENGTH) return
                setupPin += digit
                if (setupPin.length == PinLockRepository.PIN_LENGTH) {
                    // Straight to the confirmation: there is no Enter key on the grid, and the
                    // second entry is the check.
                    pinSetup.value = current.copy(
                        step = PinSetupStep.ConfirmPin,
                        firstDigits = setupPin.length,
                        errorMessage = null,
                    )
                } else {
                    pinSetup.value = current.copy(firstDigits = setupPin.length)
                }
            }

            PinSetupStep.ConfirmPin -> {
                if (setupConfirm.length >= PinLockRepository.PIN_LENGTH) return
                setupConfirm += digit
                if (setupConfirm.length == PinLockRepository.PIN_LENGTH) {
                    confirmSetupPin()
                } else {
                    pinSetup.value = current.copy(secondDigits = setupConfirm.length)
                }
            }

            PinSetupStep.ShowCode -> Unit
        }
    }

    /** The setup grid's backspace, on whichever step is showing. */
    fun onSetupDelete() {
        val current = pinSetup.value ?: return
        when (current.step) {
            PinSetupStep.EnterPin -> {
                setupPin = setupPin.dropLast(1)
                pinSetup.value = current.copy(firstDigits = setupPin.length)
            }

            PinSetupStep.ConfirmPin -> {
                // Backing out of the confirmation returns to the first entry rather than editing
                // the second: the two must agree, and an edit to the second alone can only
                // produce a mismatch.
                if (setupConfirm.isEmpty()) {
                    setupPin = ""
                    pinSetup.value = current.copy(
                        step = PinSetupStep.EnterPin,
                        firstDigits = 0,
                        secondDigits = 0,
                    )
                } else {
                    setupConfirm = setupConfirm.dropLast(1)
                    pinSetup.value = current.copy(secondDigits = setupConfirm.length)
                }
            }

            PinSetupStep.ShowCode -> Unit
        }
    }

    /**
     * Compares the two entries, then generates the code **without writing anything**.
     *
     * The code is generated here and held in [PinSetupUi.issuedCode] until the owner confirms. The
     * write happens in [confirmPinCodeWritten], which is the AC's "flow is not finished" clause.
     */
    private fun confirmSetupPin() {
        val current = pinSetup.value ?: return
        if (setupPin != setupConfirm) {
            setupPin = ""
            setupConfirm = ""
            pinSetup.value = current.copy(
                step = PinSetupStep.EnterPin,
                firstDigits = 0,
                secondDigits = 0,
                errorMessage = strings.pinSetupMismatchBody(),
            )
            return
        }

        val lock = pinLock
        if (lock == null) {
            pinSetup.value = null
            return
        }
        // Generated, not written. `enable` is called only from the confirm below.
        pinSetup.value = current.copy(
            step = PinSetupStep.ShowCode,
            issuedCode = lock.newRecoveryCode(),
            errorMessage = null,
        )
    }

    /**
     * Writes the lock — the confirmation the AC requires.
     *
     * Only here does anything reach the database, and only here does the code become the stored
     * one. Backing out before this leaves no lock at all.
     */
    fun confirmPinCodeWritten() {
        val current = pinSetup.value ?: return
        val code = current.issuedCode ?: return
        val lock = pinLock ?: return
        val pin = setupPin

        viewModelScope.launch {
            try {
                lock.enable(pin, code)
                pinSettings.value = PinSettingsUi(
                    enabled = true,
                    autoLockMinutes = lock.autoLockMinutes(),
                )
                // The PIN and the code are dropped the moment they are written: the code is shown
                // once by contract, and a value left in a flow state can be re-rendered.
                setupPin = ""
                setupConfirm = ""
                pinSetup.value = null
                message.value = strings.pinEnabledMessage()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                pinSetup.value = current.copy(errorMessage = strings.pinErrorUnavailable())
            }
        }
    }

    /** Turns the lock off. The secrets go with it. */
    fun disablePin() {
        val lock = pinLock ?: return
        viewModelScope.launch {
            try {
                lock.disable()
                pinSettings.value = PinSettingsUi(
                    enabled = false,
                    autoLockMinutes = lock.autoLockMinutes(),
                )
                message.value = strings.pinDisabledMessage()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The card keeps showing what it showed: a switch that flips without the write
                // succeeding is a switch that lies about the lock.
            }
        }
    }

    /** Changes the auto-lock duration. */
    fun setAutoLockMinutes(minutes: Int) {
        val lock = pinLock ?: return
        viewModelScope.launch {
            try {
                lock.setAutoLockMinutes(minutes)
                pinSettings.value = pinSettings.value?.copy(autoLockMinutes = minutes)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Same as `disablePin`: the card keeps its value rather than showing a preference
                // that was not stored.
            }
        }
    }

    /**
     * Puts a blank-field refusal on the field it belongs to.
     *
     * ## Why there is no `catch (BlankAccountFieldException)` around the write
     *
     * Because it would be unreachable. `BusinessAccount.init` throws the typed exception, and
     * `toBusinessAccount` builds the model *before* the coroutine starts — and
     * `firstMissingAccountField` above has already refused an incomplete form. So a blank field
     * cannot reach the `try` block, and a catch there would be dead code that reads as a guard.
     *
     * That is the same argument this story applies to `BusinessAccountRepository.save`, and it is
     * worth being consistent about: dead validation is worse than none, because it looks like
     * protection while never running.
     */
    private fun AccountFormState.withBlankRefusal(field: AccountField): AccountFormState =
        when (field) {
            AccountField.BankName -> copy(bankError = strings.pengaturanErrorBlankBank())
            AccountField.Number -> copy(numberError = strings.pengaturanErrorBlankNumber())
        }

    // ------------------------------------------------------- Story 5.4: the cutoff

    /**
     * Story 5.4: picks a day, computes what it would change, and asks.
     *
     * ## Why there is **no** public function that writes the cutoff directly
     *
     * Because the AC makes the preview mandatory: *"pratinjau ini wajib, bukan opsional"*. The only
     * way to write the setting is [confirmCutoffChange], and the only way to reach that is a
     * pending preview. A `setCutoffDay` on this ViewModel would be a path from "pick a day" to
     * "written" that skips the question, and a reviewer would have to notice its existence.
     */
    fun requestCutoffChange(day: Int) {
        val repository = payments ?: return
        val clock = clock ?: return

        // Refused here rather than by the repository alone, so the card can say so on the
        // control the owner is holding. The repository's own check is the backstop, and
        // `CutoffChangeTest` asserts it fires for a direct call.
        if (day < CutoffPeriod.MIN_CUTOFF_DAY || day > CutoffPeriod.MAX_CUTOFF_DAY) {
            cutoffErrorMessage.value = strings.pengaturanCutoffInvalid()
            return
        }

        viewModelScope.launch {
            try {
                pendingCutoffPreview.value = repository.previewCutoffChange(
                    newCutoffDay = day,
                    asOf = LocalDate.now(clock),
                )
                cutoffErrorMessage.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: InvalidCutoffDayException) {
                cutoffErrorMessage.value = strings.pengaturanCutoffInvalid()
            } catch (_: Exception) {
                cutoffErrorMessage.value = strings.pengaturanErrorUnexpected()
            }
        }
    }

    /** Closes the preview without writing anything. */
    fun dismissCutoffPreview() {
        pendingCutoffPreview.value = null
    }

    /**
     * Writes the day whose consequences the owner just saw.
     *
     * One settings row. **No allocation row is touched**, and that is structural: the repository's
     * `setCutoffDay` has no statement that could reach the allocation table, and every arrears
     * read after this uses the new day because `previewAllocation` reads the cutoff from storage
     * on each call — so there is no recalculation step and nothing to trigger one.
     */
    fun confirmCutoffChange() {
        val preview = pendingCutoffPreview.value ?: return
        val repository = payments ?: return

        viewModelScope.launch {
            try {
                repository.setCutoffDay(preview.newCutoffDay)
                cutoffSettings.value = CutoffSettingsUi(day = preview.newCutoffDay)
                pendingCutoffPreview.value = null
                message.value = strings.pengaturanCutoffSaved(preview.newCutoffDay)
            } catch (e: CancellationException) {
                throw e
            } catch (_: InvalidCutoffDayException) {
                pendingCutoffPreview.value = null
                cutoffErrorMessage.value = strings.pengaturanCutoffInvalid()
            } catch (_: Exception) {
                // The card keeps the day it showed: a figure that flipped without the write
                // succeeding is a card lying about the owner's billing cycle.
                cutoffErrorMessage.value = strings.pengaturanErrorUnexpected()
            }
        }
    }

    /** Dismisses the cutoff's refusal. */
    fun consumeCutoffError() {
        cutoffErrorMessage.value = null
    }

    /**
     * The two period labels the shift produces, for the preview's `periodLine`.
     *
     * Built from [CutoffPeriod.containing] — the one function that owns "which period is this
     * cutoff's" — so the dialog's labels cannot be built by a second rule and disagree with the
     * periods the preview actually computed.
     */
    private fun periodLineFor(oldCutoffDay: Int, newCutoffDay: Int): String {
        val clock = clock ?: return ""
        val today = LocalDate.now(clock)
        return strings.pengaturanCutoffPeriods(
            CutoffPeriod.containing(oldCutoffDay, today).label,
            CutoffPeriod.containing(newCutoffDay, today).label,
        )
    }

    /**
     * Projects one account into its row.
     *
     * `numberText` is the stored value verbatim: an account number has no grouping convention
     * the owner asked for, and inventing one would put spaces into a string they may be
     * comparing character by character against a bank statement. The field exists anyway so a
     * future grouping has one place to live, and so this screen follows the same
     * "pre-formatted in the ViewModel" rule as every other row in the app.
     */
    private fun BusinessAccount.toRow() = AccountRow(
        account = this,
        numberText = number,
    )
}
