package net.klinik.rental.ui.screens.pengaturan

import net.klinik.rental.domain.model.BusinessAccount

/**
 * One account, plus the number as the screen renders it.
 *
 * The number arrives **already formatted** by the ViewModel — the same rule every other
 * row in this app follows, so a figure cannot be assembled by hand in one composable and
 * differently in another. Here there is little formatting to do (an account number has no
 * grouping convention the owner asked for), but the field exists anyway: it is where a
 * future grouping would go, and it keeps the rule uniform rather than making this the one
 * screen that reads a domain value directly.
 *
 * There is deliberately **no** `isDefault`, `isActive` or `status` here either — see
 * `BusinessAccount`'s KDoc. A row is an account and nothing else.
 */
data class AccountRow(
    val account: BusinessAccount,
    /** The number as shown. Currently the stored value verbatim; see the class KDoc. */
    val numberText: String,
)

/**
 * The whole screen in one value.
 *
 * Built with `combine` + `stateIn` + `distinctUntilChanged()` over the account flow (see
 * `PengaturanViewModel`).
 *
 * @param isLoading true while the account flow has not emitted anything yet.
 *   See `KendaraanUiState.isLoading` for why this cannot be `rows.isEmpty()`: the database's
 *   first emission is an empty list, so during every load a screen that branched on
 *   emptiness showed "Belum ada rekening" — an invitation to add an account the owner
 *   already added, and, worse, a screen that reads like a fresh install. The default is
 *   `false` because a hand-built fixture already holds its rows; the ViewModel's `stateIn`
 *   initial value passes `true` explicitly.
 * @param errorMessage Indonesian copy for the last refused write, or `null`. Already-
 *   resolved text rather than an exception, because the exception's whole purpose is to
 *   survive until here and pick its words. Rendered by the screen as a banner.
 */
data class PengaturanUiState(
    val rows: List<AccountRow> = emptyList(),
    val form: AccountFormState? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
) {
    /**
     * Whether the empty state applies.
     *
     * One emptiness, not two: this screen has no search, so there is no "no match" state to
     * tell apart from "nothing stored". Inventing one would be a second message for a
     * situation that cannot arise.
     */
    val isEmpty: Boolean get() = rows.isEmpty()

    /** The subtitle on the top bar: how many accounts are stored. */
    val accountCount: Int get() = rows.size
}

/**
 * The account form's own state, held by the ViewModel.
 *
 * ## Why [id] is nullable rather than a separate `isEditing` flag
 *
 * A null id means "adding"; a non-null one means "editing that row". A second boolean would
 * be able to disagree with the id, and the disagreement would show as an edit form that
 * inserts a duplicate account — the same reasoning `RenterFormState.isEditing` gives, and
 * the same field shape.
 *
 * ## Why the fields are `String` and not typed values
 *
 * There is nothing to parse: a bank name is free text and a number is digits the owner
 * types. The number's normalisation happens in the repository, on the way to storage, so
 * the form holds exactly what the owner typed — which is what lets an invalid value stay on
 * screen for them to correct rather than being silently rewritten under their cursor.
 *
 * @param bankError the last refusal's message for the bank field, or `null`. `null` means
 *   "validation has not run" and renders no error row; a blank string would draw an empty one.
 * @param numberError the same, for the number field. Separate slots because the refusal has
 *   to land on the field that caused it — an owner looking at a malformed number needs that
 *   field to say why, not the one they happened to be reading.
 */
data class AccountFormState(
    val id: String? = null,
    val bankName: String = "",
    val number: String = "",
    val bankError: String? = null,
    val numberError: String? = null,
) {
    /** Whether this is an edit rather than a create. Derived, never stored. */
    val isEditing: Boolean get() = id != null
}

/**
 * Applies a bank-name edit, clearing its own error.
 *
 * Clears because the edit **is** the fix: an error that survived it would accuse the owner
 * of a field they are in the middle of filling in — the same rule every other form in this
 * app follows.
 */
fun AccountFormState.setBankName(bankName: String): AccountFormState = copy(
    bankName = bankName,
    bankError = null,
)

/**
 * Applies a number edit, clearing its own error. See [setBankName].
 *
 * ## Why there is no input cap here, despite the domain having a length bound
 *
 * The tempting addition is a `maxLength` on the field, matching
 * `normalizeAccountNumber`'s `MAX_ACCOUNT_NUMBER_DIGITS`. It would be wrong, and in the same
 * direction as clamping an end date: a cap **truncates a paste silently**, so the owner pastes
 * a statement line and gets a 40-digit prefix that looks like a plausible account number and is
 * not one. The repository's refusal is the better outcome — it names the problem and writes
 * nothing — which is why the bound lives there and not here.
 *
 * `KnField` has no `maxLength` parameter either, so a cap would have to be a truncating branch
 * in this function. That is exactly the silent rewrite this note exists to prevent.
 */
fun AccountFormState.setNumber(number: String): AccountFormState = copy(
    number = number,
    numberError = null,
)

/** The fields a blank-field refusal can land on. */
enum class AccountField {
    BankName,
    Number,
}

/**
 * The first required field that is not yet answered, or `null` when the form is complete.
 *
 * ## Why the bank name is checked first
 *
 * The order is the order the fields appear on the sheet, top to bottom, so the message lands
 * on the first thing the owner would notice. It also matches the repository's own check
 * order, which matters for the same reason: a form that refused the number while the bank
 * was empty would report a fault the owner has to fix second.
 *
 * ## Why this does **not** check the number's format
 *
 * Because a malformed number is not a *missing* field — it is a wrong value, with a
 * different message and a different fix. Keeping the two apart here is what lets
 * `canSaveAccount` mean "everything is present" while the format refusal arrives from the
 * repository with the typed exception that names it. Folding format into this function
 * would make "not yet filled in" and "filled in wrongly" one condition, and the owner would
 * see "wajib diisi" on a field that visibly contains text.
 */
fun AccountFormState.firstMissingAccountField(): AccountField? = when {
    bankName.isBlank() -> AccountField.BankName
    number.isBlank() -> AccountField.Number
    else -> null
}

/**
 * Whether Save is allowed.
 *
 * Completeness only — see [firstMissingAccountField] on why the number's format is not part
 * of it. A malformed number leaves Save enabled and produces a refusal naming the format,
 * which is the actionable message; a permanently disabled button beside a filled field would
 * leave the owner with no explanation at all.
 */
val AccountFormState.canSaveAccount: Boolean
    get() = firstMissingAccountField() == null

/**
 * Builds the account this form describes, or `null` when the form is incomplete.
 *
 * ## Why `null` rather than throwing
 *
 * An incomplete form is a state the caller already knows how to report — it is what
 * [firstMissingAccountField] names — so this returns `null` and lets the ViewModel put the
 * message on the field that caused it. Throwing would collapse "you have not filled this in"
 * into the same path as a genuinely unexpected failure.
 *
 * ## Why the values are not trimmed here
 *
 * Trimming is the repository's job, on the way to storage, and doing it in both places means
 * the value the owner sees in the field and the value that is stored can differ in a way
 * neither of them can explain. The bank name is trimmed at the repository; the number is
 * normalised there too.
 *
 * @param id the id to use. Passed in rather than generated here, so this stays pure and a
 *   test can pin it — the same choice `ContractFormState.toContract` makes.
 */
fun AccountFormState.toBusinessAccount(id: String): BusinessAccount? {
    if (firstMissingAccountField() != null) return null
    return BusinessAccount(
        id = this.id ?: id,
        bankName = bankName,
        number = number,
    )
}

