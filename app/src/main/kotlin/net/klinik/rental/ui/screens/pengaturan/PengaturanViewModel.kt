package net.klinik.rental.ui.screens.pengaturan

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
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.domain.model.BusinessAccount
import net.klinik.rental.domain.validation.InvalidAccountNumberException
import net.klinik.rental.ui.strings.RStrings

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
    private val strings: RStrings,
) : ViewModel() {

    /** The open form, or `null` when the sheet is closed. */
    private val form = MutableStateFlow<AccountFormState?>(null)

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
     * Nesting two `combine` calls rather than one over three flows, for the same reason
     * `PenyewaViewModel` nests: it keeps every source typed rather than reaching for the
     * `vararg` overload, which would hand the lambda an `Array<Any?>`.
     */
    val uiState: StateFlow<PengaturanUiState> = combine(
        accountsWithLoadFlag(),
        combine(form, errorMessage) { currentForm, error -> FormSnapshot(currentForm, error) },
    ) { (accounts, hasEmitted), formSnapshot ->
        PengaturanUiState(
            // The projection to a row happens here, once per emission, so the screen renders
            // what it is handed and no composable formats a value.
            rows = accounts.map { it.toRow() },
            form = formSnapshot.form,
            errorMessage = formSnapshot.error,
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

    fun consumeError() {
        errorMessage.value = null
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
     *
     * One `when` over [AccountField] rather than two call sites, so adding a field later means
     * adding a branch here and nowhere else.
     */
    private fun AccountFormState.withBlankRefusal(field: AccountField): AccountFormState =
        when (field) {
            AccountField.BankName -> copy(bankError = strings.pengaturanErrorBlankBank())
            AccountField.Number -> copy(numberError = strings.pengaturanErrorBlankNumber())
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
