package net.klinik.rental.ui.screens.setoran

import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.domain.model.BusinessAccount
import net.klinik.rental.domain.model.Renter

/**
 * The deposit form's own state, held by the ViewModel.
 *
 * ## Why the amount is a `String` and not a `Long`
 *
 * Because it is **what the owner has typed**, and half-typed is a real state: the numpad
 * renders "1500" while the owner is still on the third digit. Parsing on every keystroke
 * would refuse "0" as invalid and flash on the way to "1500000". The `Long` appears exactly
 * once — when `toPayment` builds the domain value — and `KnNumPad`'s own Save already refuses
 * zero with its flash, so the button's `onSave` is the one path that hands over a figure.
 *
 * ## Why the renter and the vehicle are both here, and why the rate is not
 *
 * The AC says the vehicle *and its daily rate* are filled from the active contract. So the
 * state carries which contract is being paid — not a free choice of vehicle, because a
 * deposit belongs to the rental it settles. The renter is chosen because the owner thinks in
 * people; the contract follows from them. Storing the vehicle separately would let the two
 * disagree.
 *
 * The rate is **pre-formatted text**, not a `Long`. Every other row in this app hands the
 * ViewModel's formatting to the composable rather than letting it call `formatRupiah`
 * itself, and a rate the owner must re-type is the mistake this whole form exists to
 * prevent.
 */
data class DepositFormState(
    val renterId: String? = null,
    val renterName: String = "",
    /**
     * The renter's open contract, once it has been resolved.
     *
     * `null` while no contract matches, which is the "there is nothing to bill" state the
     * form refuses to proceed from rather than guessing at a rate.
     */
    val contractId: String? = null,
    val vehiclePlate: String = "",
    /** `formatRupiah(contract.dailyRate)` — see the class KDoc. */
    val rateText: String = "",
    /** What the owner has typed on the numpad. See the class KDoc. */
    val amountDigits: String = "0",
    val method: PaymentMethod = PaymentMethod.TransferBank,
    val accountId: String? = null,
    val referenceNo: String = "",
    val notes: String = "",
    val accountError: String? = null,
    val amountError: String? = null,
) {
    /** Whether a renter's open contract has been found, and what is being paid. */
    val hasContract: Boolean get() = contractId != null

    /**
     * Whether the account field is on screen **at all**.
     *
     * `method == Tunai` and the field is **absent**, not blank. The epic context is explicit
     * that filling a field in blank "menciptakan ilusi data yang padahal tidak ada" — and the
     * same rule the exception sheet follows when it unmounts its add block for an ended
     * contract. A greyed-out field would be worse: it would keep its label and its error row
     * while being impossible to complete.
     */
    val showsAccountField: Boolean get() = method == PaymentMethod.TransferBank

    /**
     * Whether Save is allowed.
     *
     * Completeness only — **not** whether the amount is positive. `KnNumPad`'s Save already
     * refuses zero and flashes, and that refusal is a *better* one than a disabled button
     * beside a field showing `0`: the owner pressed a control and got an answer, rather than
     * being left wondering why nothing happens. So this only guards the two things a
     * disabled button would genuinely prevent.
     */
    val canSave: Boolean
        get() = hasContract && (!showsAccountField || accountId != null)
}

/**
 * Applies a payment method, clearing the account with it.
 *
 * Switching to Tunai **drops the selected account**, rather than keeping it hidden. The
 * reverse — choosing a bank, switching to cash, and saving with the account silently
 * retained — would file a cash payment against a bank it never touched, which is exactly
 * the "no external source can verify this" case the method exists to distinguish.
 */
fun DepositFormState.setMethod(method: PaymentMethod): DepositFormState = if (method == this.method) {
    this
} else {
    copy(
        method = method,
        accountId = null,
        accountError = null,
        referenceNo = "",
    )
}

/** Applies a renter, clearing the errors on the fields the new contract replaces. */
fun DepositFormState.selectRenter(renter: Renter?, contract: DepositContract?): DepositFormState {
    if (renter == null) {
        return copy(
            renterId = null,
            renterName = "",
            contractId = null,
            vehiclePlate = "",
            rateText = "",
            // Choosing nobody is not a refusal, so nothing is cleared: the amount the owner
            // already typed is theirs, and a renter is chosen before an amount in practice.
            accountError = null,
        )
    }
    return copy(
        renterId = renter.id,
        renterName = renter.name,
        contractId = contract?.contractId,
        vehiclePlate = contract?.vehiclePlate.orEmpty(),
        rateText = contract?.rateText.orEmpty(),
    )
}

/** Applies an account choice, clearing its own error — the choice *is* the fix. */
fun DepositFormState.selectAccount(accountId: String?): DepositFormState = copy(
    accountId = accountId,
    accountError = null,
)

/** Applies a bank reference. Free text, cleared of nothing — it is a reminder. */
fun DepositFormState.setReferenceNo(referenceNo: String): DepositFormState =
    copy(referenceNo = referenceNo)

/** Applies the owner's own note — for cash, the only breadcrumb there is. */
fun DepositFormState.setNotes(notes: String): DepositFormState = copy(notes = notes)

/** The renter's open contract, projected for the form. */
data class DepositContract(
    val contractId: String,
    val vehiclePlate: String,
    /** `formatRupiah(contract.dailyRate)`. */
    val rateText: String,
)

/** One selectable renter in the sheet's picker. */
data class RenterOption(
    val renter: Renter,
    /**
     * Whether this renter has an open contract, and therefore can take a deposit.
     *
     * Carried rather than filtered out of the list: a renter with no contract must still be
     * visible, because the owner needs to see *who* has none rather than wonder where they
     * went. Selecting one simply cannot proceed.
     */
    val contract: DepositContract?,
)

/** One selectable business account in the sheet's picker. */
data class AccountOption(
    val account: BusinessAccount,
    /** `BCA · 1234567890`, so the owner can tell two accounts at one bank apart. */
    val label: String,
)

/**
 * The whole sheet in one value.
 *
 * @param isLoading true while the pickers have not emitted yet. The default is `false`
 *   for hand-built fixtures; the ViewModel's `stateIn` initial value passes `true`. The
 *   sheet shows skeletons rather than an empty picker for the same reason every other
 *   screen does: an empty list before the first read is indistinguishable from a renter
 *   who has nobody.
 * @param successMessage the confirmation for a deposit that landed, drawn **inside** the
 *   sheet — the screen's banner lives in a different window and would be invisible while
 *   this is up.
 */
data class SetoranUiState(
    val form: DepositFormState? = null,
    val renterOptions: List<RenterOption> = emptyList(),
    val accountOptions: List<AccountOption> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isLoading: Boolean = false,
) {
    /** The sheet is up. */
    val isOpen: Boolean get() = form != null
}