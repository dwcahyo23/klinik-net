package net.klinik.rental.ui.screens.pengaturan

import net.klinik.rental.data.repository.BusinessIdentity
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
 *   See `MobilUiState.isLoading` for why this cannot be `rows.isEmpty()`: the database's
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
    /** The business identity as stored, or the default. Story 4.4's section header reads it. */
    val identity: IdentityRow? = null,
    val identityForm: IdentityFormState? = null,
    /**
     * The identity sheet's **own** failure and confirmation.
     *
     * Separate from [errorMessage], which is the account form's. Two windows sharing one
     * message slot is the defect `KontrakScreen` gives each of its three sheets a private slot
     * to avoid: the account's refusal would draw inside the identity window, and dismissing it
     * would clear a message belonging to the other form.
     */
    val identityErrorMessage: String? = null,
    val identitySuccessMessage: String? = null,
    val errorMessage: String? = null,
    /**
     * Story 5.1's export, in flight.
     *
     * A single boolean rather than a progress figure because there is nothing to report progress
     * *about*: the owner is looking at a system file picker, not at this app, and the work is
     * eight table reads plus one file write. What the flag prevents is a **second picker** — a
     * double tap would open two pickers, and the second one's destination would silently win
     * while the owner believed the first file had been written.
     */
    val isExporting: Boolean = false,
    /**
     * A failed export, or `null`.
     *
     * Its own field rather than the screen's [errorMessage], because a form is never open while
     * an export runs — and because the screen's banner suppresses itself while either sheet is
     * up, which would swallow an export failure the moment an account sheet appeared. The
     * failure is about money the owner believed was safe, so it has to be readable.
     */
    val exportErrorMessage: String? = null,
    /**
     * Story 5.2's preview, when a file has been parsed and the owner has not decided yet.
     *
     * Non-null is what makes the dialog visible, and it is the **parsed file** rather than a
     * summary — the confirm writes exactly what this value describes, so a preview computed from
     * one read and a restore from another cannot disagree.
     */
    val restorePreview: RestorePreviewUi? = null,
    /** A restore in flight. Rendered, so a second confirm cannot start a second replacement. */
    val isRestoring: Boolean = false,
    /**
     * A refused or failed restore, or `null`.
     *
     * Its own slot beside [exportErrorMessage] rather than shared with it: the two failures
     * arrive from different actions and carry different next steps, and a single slot would let
     * a dismissal of one clear the other's message.
     */
    val restoreErrorMessage: String? = null,
    /**
     * Story 5.3's lock state, or `null` before the first read.
     *
     * `null` rather than a defaulted `false`: the security card must not draw "PIN mati" for the
     * frame before the read lands, because a switch showing the wrong position is a switch the
     * owner flips the wrong way.
     */
    val pin: PinSettingsUi? = null,
    /** The PIN setup flow, or `null` when it is closed. */
    val pinSetup: PinSetupUi? = null,
    /**
     * Story 5.4's cutoff day, or `null` before the first read.
     *
     * `null` rather than a defaulted 25 for the same reason [pin] is: the card must not draw a day
     * the owner did not choose for the frame before the read lands.
     */
    val cutoff: CutoffSettingsUi? = null,
    /**
     * The mandatory preview, when the owner has picked a day and not decided yet.
     *
     * Non-null is what makes the dialog visible. It carries the **computed** preview, so the
     * confirm writes the day whose consequences the owner actually saw.
     */
    val cutoffPreview: CutoffPreviewUi? = null,
    /** A refused cutoff change, or `null`. Its own slot: a refusal is not a form error. */
    val cutoffErrorMessage: String? = null,
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

    /**
     * Whether the identity section's action should read "edit" rather than "add".
     *
     * Derived from whether the section already shows a name, not from a separate flag — the two
     * would be able to disagree, and a section offering to add an identity that is already
     * stored is the same defect an `isEditing` boolean causes elsewhere.
     */
    val hasIdentity: Boolean get() = identity != null
}

/**
 * The security card's figures, read once and re-read after every write.
 *
 * @property enabled whether a PIN is set.
 * @property autoLockMinutes the duration, one of `PinLockRepository.AUTO_LOCK_CHOICES`.
 */
data class PinSettingsUi(
    val enabled: Boolean,
    val autoLockMinutes: Int,
)

/**
 * The PIN setup flow's step.
 *
 * Three steps, and the third is the one the AC makes load-bearing: the code is shown **once** and
 * the flow is not finished until the owner confirms they wrote it down. Nothing is written to the
 * database before that confirmation, so backing out leaves the lock exactly as it was.
 *
 * @property firstDigits how many digits of the first entry are filled. The digits themselves never
 *   leave the ViewModel — a PIN in a `UiState` is a PIN in a saved-state bundle.
 * @property secondDigits likewise for the confirmation.
 * @property issuedCode the code, present only at [PinSetupStep.ShowCode]. Shown once, never stored.
 */
data class PinSetupUi(
    val step: PinSetupStep,
    val firstDigits: Int = 0,
    val secondDigits: Int = 0,
    val issuedCode: String? = null,
    val errorMessage: String? = null,
)

/** Which question the setup dialog is asking. */
enum class PinSetupStep {
    /** Choose the PIN. */
    EnterPin,

    /** Type it again — a mistyped PIN on a lock with no way in is unrecoverable. */
    ConfirmPin,

    /** The recovery code, shown once, with the confirmation that it was written down. */
    ShowCode,
}

/**
 * Story 5.4's cutoff card: the day in force.
 *
 * @property day 1–28, the value every arrears computation reads.
 */
data class CutoffSettingsUi(
    val day: Int,
)

/**
 * The cutoff change preview, already formatted.
 *
 * Built by the ViewModel for the reason every `*Ui` type here is: the sentence is three numbers
 * and two period labels, and a composable that assembled it would rebuild it on every
 * recomposition.
 *
 * @property day the day the owner picked — what [PengaturanViewModel.confirmCutoffChange] writes.
 * @property changedLine `2 kontrak berubah tunggakannya`, or the no-op sentence.
 * @property deltaLine `Tunggakan naik Rp 750.000`, or the no-change sentence.
 * @property periodLine `25 Feb – 24 Mar 2026 → 15 Mar – 14 Apr 2026`.
 */
data class CutoffPreviewUi(
    val day: Int,
    val changedLine: String,
    val deltaLine: String,
    val periodLine: String,
)

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

// ---------------------------------------------------------------- Story 4.4

/**
 * The identity the message quotes, as the screen shows it.
 *
 * Read from storage rather than compiled in — the prototype's three hardcoded copies of the
 * business name are exactly what this replaces, so the screen reads one default from one
 * constant rather than repeating it.
 *
 * ## Why all three fields and not just the name
 *
 * Because an edit **seeds the form from this row**, and a row carrying only the name would
 * open the address and phone empty — after which saving them writes `null`, which
 * `BusinessIdentityRepository.clearIfAbsent` treats as *clear this field*. That is silent data
 * loss on the one action the UI cannot undo: the owner corrects a typo in the name, taps save,
 * sees a confirmation, and the address and phone they entered weeks ago are gone.
 *
 * So the row carries exactly what the form needs to be pre-filled, and `null` here means
 * "never filled in" — which is a different act from "cleared", and is preserved as such all
 * the way to storage.
 */
data class IdentityRow(
    /** The business name as stored, or the default when nothing has been saved yet. */
    val name: String,
    /** The stored address, or `null` when the owner never filled one in. */
    val address: String? = null,
    /** The stored phone, or `null` when the owner never filled one in. */
    val phone: String? = null,
)

/**
 * The business-identity form's own state, held by the ViewModel.
 *
 * ## Why this is a second form and not three fields on the account form
 *
 * Because they are saved as **one block**: a name with no address is an identity the owner did
 * not enter, and the message would quote half of it. A second state beside `AccountFormState`
 * is therefore not a convenience — it is what makes "all three or none" expressible. One form
 * per sheet is also the rule every other screen in this app follows.
 *
 * ## Why the name opens pre-filled and the other two do not
 *
 * Because the name has a default the owner is *expected* to accept or replace — it is the
 * prototype's own, and a blank field would invite an empty business name in every outgoing
 * message. The address and phone have no default worth inventing: a blank one invites the owner
 * to type a placeholder, and `openAddForm`'s KDoc gives the same reason for leaving the account
 * bank field empty.
 *
 * That is the **fresh** form's rule. An **edit** opens all three from what is stored, because
 * the alternative is the silent data loss [IdentityRow]'s KDoc describes: the owner fixes one
 * field and the two they left alone are read as "cleared" and deleted.
 *
 * @param nameError the last refusal's message for the name field, or `null`. `null` means
 *   "validation has not run" and renders no error row.
 *
 * @param name **no default here, deliberately.** A `DEFAULT_NAME` default parameter would be a
 *   second home for the constant beside `BusinessIdentityRepository`'s — and the spec forbids
 *   exactly that, because a second copy is one that can drift: an owner would be shown a
 *   default on a fresh form that differs from the one a message falls back to. The value
 *   arrives from the ViewModel, which reads the single constant; `""` here means "constructed
 *   by hand", which is what a test fixture is.
 */
data class IdentityFormState(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val nameError: String? = null,
)

/**
 * Applies a name edit, clearing its own error.
 *
 * Clears because the edit **is** the fix — the same rule `setBankName` gives, and the reason
 * an error surviving a correction accuses the owner of a field they are mid-way through.
 */
fun IdentityFormState.setIdentityName(name: String): IdentityFormState = copy(
    name = name,
    nameError = null,
)

/** Applies an address edit. No error slot, because an address is never refused. */
fun IdentityFormState.setIdentityAddress(address: String): IdentityFormState = copy(
    address = address,
)

/** Applies a phone edit. No error slot, for the same reason. */
fun IdentityFormState.setIdentityPhone(phone: String): IdentityFormState = copy(
    phone = phone,
)

/**
 * Whether Save is allowed.
 *
 * The name alone, because it is the only field with a rule. The address and phone are free text
 * the owner types for outsiders, and a completeness gate over them would refuse a business with
 * no address — which is a real business, not a half-filled form.
 */
val IdentityFormState.canSaveIdentity: Boolean
    get() = name.isNotBlank()

/**
 * Builds the identity this form describes, or `null` when the name is blank.
 *
 * `null` rather than throwing, for the reason `toBusinessAccount` gives: an incomplete form is
 * a state the caller already knows how to report, so the message lands on the field instead of
 * arriving as an exception about an identity the owner never finished building.
 *
 * The address and phone stay **nullable** all the way down — a blank one is "not filled in", and
 * storing `""` would make the two indistinguishable to the next reader.
 */
fun IdentityFormState.toBusinessIdentity(): BusinessIdentity? {
    if (name.isBlank()) return null
    return BusinessIdentity(
        name = name.trim(),
        address = address.trim().takeIf { it.isNotBlank() },
        phone = phone.trim().takeIf { it.isNotBlank() },
    )
}

