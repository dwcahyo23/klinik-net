package net.klinik.rental.ui.screens.setoran

import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.domain.model.BusinessAccount
import net.klinik.rental.domain.model.Renter
import java.time.LocalDate

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
    /**
     * The stored deposit this form is correcting, or `null` for a new one.
     *
     * Non-null is the whole of "editing" — see [isEditing]. It is the id rather than a
     * boolean because a boolean is a second thing that can disagree with the row: a form
     * that says it is editing while carrying no id cannot be written back, and one that
     * carries an id while saying it is new would insert a second deposit.
     */
    val paymentId: String? = null,
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
    /**
     * When the money arrived, as a billing fact.
     *
     * Carried by the form rather than read from the clock at save time, and that is what
     * makes the picker work: an owner dating a deposit back to Sunday needs the save to use
     * *that* Sunday. `openDeposit` seeds it with the ViewModel's clock, so "today" on a new
     * form is the same today the allocation uses.
     */
    val date: LocalDate,
    /** What the owner has typed on the numpad. See the class KDoc. */
    val amountDigits: String = "0",
    val method: PaymentMethod = PaymentMethod.TransferBank,
    val accountId: String? = null,
    val referenceNo: String = "",
    val notes: String = "",
    val accountError: String? = null,
    val amountError: String? = null,
) {
    /**
     * Whether this form corrects a stored deposit rather than recording a new one.
     *
     * The single source of "editing" for the whole sheet: the title, the method chips' lock
     * and `SetoranViewModel`'s choice of write path all read it. A separate flag would be a
     * second thing that can disagree with the id — which is what makes
     * `correcting a cash deposit as a transfer` expressible.
     */
    val isEditing: Boolean get() = paymentId != null

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
 *
 * ## Why a correction refuses here, before the chips are even drawn
 *
 * The epic's rule is that payment method **cannot** be corrected — repairing a method means
 * cancelling and recording again. This is the second of two refusals, and the earlier one:
 * the sheet draws its chips disabled, so an owner who never finds the disabled state still
 * cannot change the value. The repository's absence of a `method` parameter is the third,
 * and the only one a caller cannot route around.
 */
fun DepositFormState.setMethod(method: PaymentMethod): DepositFormState =
    if (isEditing || method == this.method) {
        this
    } else {
        copy(
            method = method,
            accountId = null,
            accountError = null,
            referenceNo = "",
        )
    }

/**
 * Applies a date correction.
 *
 * A bare `copy` rather than a transform that also clears something: the date has no error
 * of its own in this story — an impossible date is refused by the calendar itself — so a
 * second state field beside it would be a field nothing ever assigns.
 */
fun DepositFormState.setDate(date: LocalDate): DepositFormState = copy(date = date)

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
 * @param preview the mandatory preview, or `null` when none is up.
 *
 *   **Non-null is what makes saving possible, not a decoration.** The ViewModel's
 *   `confirmDeposit` refuses when this is `null`, so the preview cannot be skipped by any
 *   path — not by a dismissed dialog, not by a caller reaching the sheet directly, and not
 *   by a future screen that forgets to mount it. That is what UX-DR10's *"pratinjau wajib,
 *   bukan opsional"* means in code rather than in a comment.
 */
data class SetoranUiState(
    val form: DepositFormState? = null,
    val renterOptions: List<RenterOption> = emptyList(),
    val accountOptions: List<AccountOption> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val preview: DepositPreviewUi? = null,
    val cancel: CancelDepositUi? = null,
    /**
     * The contract's deposit history, or `null` when no history is open.
     *
     * **`null` rather than an empty list**, because "no history open" and "a contract with
     * no deposits" are different states and this sheet can draw a wrong one from
     * mistaking them: the first shows nothing at all, the second shows an invitation to
     * record one. Room's first emission is an empty list, so an empty list alone cannot
     * tell them apart.
     */
    val history: DepositHistoryUi? = null,
    val isLoading: Boolean = false,
) {
    /** The sheet is up. */
    val isOpen: Boolean get() = form != null

    /** The preview dialog is up. Separate from [isOpen]: both windows are open at once. */
    val isPreviewing: Boolean get() = preview != null

    /** The cancellation dialog is up — a third window, and the only one with a field. */
    val isCanceling: Boolean get() = cancel != null

    /** The history block is up — the surface that mounts Stories 3.4 and 3.5. */
    val isShowingHistory: Boolean get() = history != null
}

/**
 * One contract's deposit history, **already formatted**.
 *
 * ## Why this type carries no rupiah figure and no date
 *
 * Because the row is a *record of what the owner recorded*, not an answer to a question
 * about money. The amount is formatted once here for the same reason the preview's strings
 * are: a rupiah figure assembled in two places is a rupiah figure that can differ in two
 * places. What the row deliberately does **not** carry is an arrears figure — the days a
 * deposit closed are not the contract's arrears, and a row showing both would put two
 * different numbers next to each other under one heading.
 *
 * @property renterName carried so the cancellation verb can name the person without the
 *   caller re-reading it. Story 3.4's impact sentence is *"Tunggakan Pak Budi akan kembali
 *   menjadi…"* — it is about a person, so the row that starts it knows the person.
 */
data class DepositHistoryRowUi(
    val paymentId: String,
    /**
     * The contract the deposit belongs to, and who rents it — **per row**.
     *
     * Carried on the row rather than held at block level because the same rows now serve
     * two surfaces: the per-contract block in the contract sheet (where every row's values
     * equal the block's) and the Setoran screen's cross-contract history (where they do
     * not). A cancel callback that read block-level state would open the cancellation
     * dialog for the wrong contract in that second surface — exactly the wrong-target
     * failure the wiring test was written to catch.
     */
    val contractId: String,
    val renterName: String,
    /** `renterName · plate`, the label this app names a rental with. */
    val contractLabel: String,
    /** `250.000` as `formatRupiah` spells it, including the `Rp` prefix. */
    val amountText: String,
    /** `14 Okt` — the app's own date form, not the ISO column. */
    val dateText: String,
    val methodText: String,
    /** `BCA · 1234567890`, or `null` for cash. */
    val accountText: String?,
    val referenceNo: String?,
    val notes: String?,
    /**
     * Whether **either** verb may be offered.
     *
     * Carried rather than recomputed in the composable, because a row that offers a verb
     * the repository would refuse renders live and ignores the tap — the defect this repo
     * has shipped three times. `DepositHistoryRow.isEditable` is where the decision is made.
     */
    val isEditable: Boolean,
    /**
     * Whether this deposit was cancelled — the fact the status column and the filter chips read.
     *
     * Separate from [isEditable], which is `false` for a cancelled row **and** would be `false`
     * for any future non-editable state this app has not built. The filter asks "was it
     * cancelled", so it reads the fact rather than inferring it from a verb's availability.
     */
    val isCanceled: Boolean,
    /** The owner's own reason for cancelling, shown only when cancelled. */
    val cancelReasonText: String?,
    /**
     * Paid, but short of the contract's daily rate — `payments.html`'s "Sebagian" badge.
     *
     * Derived from `amount < contract.dailyRate`, which the ViewModel reads as a **snapshot**:
     * the rate is frozen onto the contract at open time, so a later price change cannot make a
     * historical deposit retroactively partial. A cancelled deposit is never partial — it
     * cancelled nothing, so "less than a day" is not a fact about it.
     */
    val isPartial: Boolean = false,
)

/**
 * The history block's state: the contract, the rows, and whether anything has gone wrong.
 *
 * @param rows empty **and** [contractId] non-null is the "this contract has no deposits"
 *   state, which is an invitation to record one rather than an absence.
 */
data class DepositHistoryUi(
    val contractId: String,
    /** `renterName · plate`, the label the exception sheet's title uses. */
    val contractLabel: String,
    val renterName: String,
    val rows: List<DepositHistoryRowUi> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * The preview, **already formatted**, for a dialog that renders text and never figures.
 *
 * ## Why the strings live here and not in the composable
 *
 * Because the alternative is a dialog that calls `formatRupiah` per recomposition, and a
 * money figure assembled in two places is a money figure that can differ in two places. The
 * ViewModel builds these once, with the same `formatRupiah` every other surface uses.
 *
 * ## Why there is no per-day date anywhere in this type
 *
 * The acceptance criterion forbids it — *"tidak ada tanggal per-hari"* — and the reason is
 * the owner's actual question: they want to know whether the figure *looks right*, and a
 * table of dates is something they have to verify rather than something they can read. The
 * per-day audit still exists, in the deposit history; this type deliberately has no field
 * to put a date in, so the ledger cannot creep back as a "helpful" extra.
 *
 * @property creditLine the explanation-carrying sentence, or `null` when there is no
 *   credit. `null` rather than a "Kredit: Rp 0" line, because a zero credit is not news
 *   and a line that always appears stops being read. See `DepositPreview.hasCredit`.
 */
data class DepositPreviewUi(
    /** `Tunggakan: Rp 750.000 → Rp 250.000` — the arrow is the whole point. */
    val arrearsLine: String,
    /** `Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)`, or just `Menutup: 3 hari`. */
    val closesLine: String,
    /** `Kredit: Rp 250.000 — dipakai otomatis untuk periode berikutnya`, or `null`. */
    val creditLine: String?,
)

/**
 * The cancellation dialog's state — Story 3.4's.
 *
 * ## Why the impact is a **sentence**, not two figures
 *
 * The epic's copy is *"Tunggakan Pak Budi akan kembali menjadi Rp 750.000."* — the arrears
 * returning to a figure, in words, naming the renter. Two numbers with an arrow would be
 * the *preview*'s shape, and reusing it here would make two dialogs look alike while doing
 * different things: one says what a deposit will do, the other says what undoing it costs.
 * The first invites a decision; the second warns of a consequence, and they should read
 * differently for exactly that reason.
 *
 * @property reason the owner's explanation, or `""` untouched. Blank is the AC's refusal
 *   case, so this is a field the owner must fill rather than an optional note.
 */
data class CancelDepositUi(
    val paymentId: String,
    val contractId: String,
    /** `Batalkan setoran Rp 250.000?` — the title, naming what is being undone. */
    val title: String,
    /** `Tunggakan Pak Budi akan kembali menjadi Rp 750.000.` */
    val impactLine: String,
    /**
     * The deposit's amount, already formatted.
     *
     * Carried **beside** [title] rather than parsed back out of it. The confirmation
     * needs this figure, and recovering it with `substringAfter("setoran ")` would make
     * an Indonesian sentence the source of a money value — so a copy edit, a translator,
     * or a different word order would silently change what the owner is told was
     * refunded.
     */
    val amountLabel: String,
    val reason: String = "",
    val reasonError: String? = null,
)

/**
 * Applies a reason edit, clearing its own error.
 *
 * Cleared because the edit **is** the fix — an error that survived it would accuse the
 * owner of a field they are in the middle of filling in, which is the rule every other
 * field in this sheet follows.
 */
fun CancelDepositUi.setReason(reason: String): CancelDepositUi = copy(
    reason = reason,
    reasonError = null,
)

/**
 * Whether the cancellation may proceed.
 *
 * **Completeness only.** The rule itself is enforced twice — here for the button, and in
 * the repository for the data — so this is a reflection of the rule rather than its only
 * enforcement. A disabled control beside an empty field is the epic's own "wajib diisi";
 * the refusal then reads as a rule rather than as an app that stopped working.
 */
val CancelDepositUi.canCancel: Boolean get() = reason.isNotBlank()

// ------------------------------------------------------- the Setoran screen

/** The Setoran screen's two tabs, in the order the rail's own screen shows them. */
enum class SetoranTab {
    /**
     * The deposit input tab. Its action opens the **existing** deposit sheet — no second
     * form, no second numpad, no second preview.
     */
    Input,

    /**
     * The cross-contract history tab: every deposit on every contract, newest first.
     */
    Riwayat,
}

/**
 * The cross-contract summary strip — four plain figures, no chart, no KPI hierarchy.
 *
 * ## Why `total`/`active`/`cancelled` are **counts derived from the same rows**
 *
 * Because the acceptance criterion's rule is one subscription: figures derived here are
 * figures that can never disagree with the list above them. A second counting query would
 * be a second subscription and a second answer to "how many deposits are there".
 *
 * `receivedTodayText` is the one figure that does **not** come from the rows — it counts
 * confirmed money that landed today, which cancelled rows are excluded from by
 * construction. It comes from `observeTodaysIncome`'s own flow, the same read Beranda
 * uses, so the two screens cannot spell today's income differently.
 */
data class DepositSummaryUi(
    /** How many deposits are listed — cancelled ones included. */
    val total: Int,
    /** How many are still `confirmed`. */
    val active: Int,
    /** How many have been cancelled. */
    val cancelled: Int,
    /** Today's confirmed income, already formatted — from `observeTodaysIncome`. */
    val receivedTodayText: String,
)

/**
 * The Setoran screen's own state, distinct from the deposit **sheet's** [SetoranUiState].
 *
 * ## Why a separate type rather than a field on the sheet's state
 *
 * Because the sheet is a window the shell mounts over whichever screen shows, and this
 * screen is a destination with its own lifetime. Folding the screen's state into the
 * sheet's would make the shell collect and re-emit it on every sheet interaction — and
 * the sheet's `isLoading` semantics ("the pickers have not emitted") would silently
 * acquire a second meaning about the screen.
 *
 * ## Why there is no `isFailed` here
 *
 * Because a failed read **is** carried — on [historyUi.errorMessage] — but the summary
 * strip deliberately has no error of its own: its figures are derived from the same
 * emission the rows travel in, so a read that failed delivers neither and the history
 * block's banner is the one surface that says so.
 */
data class SetoranScreenState(
    /** The selected tab. Input first, which is where the screen opens. */
    val selectedTab: SetoranTab = SetoranTab.Input,
    /**
     * Whether the Input tab is showing the deposit form **inline**, rather than behind a
     * sheet.
     *
     * The prototype's `payments.html` puts the form in the tab itself — the form on the
     * left, the numpad on the right — so on that screen the form is already on the page.
     * The shell reads this to avoid mounting the sheet over it, which would be the same
     * form twice.
     */
    val isShowingForm: Boolean = false,
    /**
     * The cross-contract rows, newest first — already formatted, the same shape the
     * per-contract block draws.
     */
    val rows: List<DepositHistoryRowUi> = emptyList(),
    /**
     * The search box's raw text, exactly as typed.
     *
     * Raw, and matched case-insensitively against renter, plate, reference and bank — the four
     * things `payments.html`'s own `renderHistory` searches. Kept as typed rather than
     * lowercased so the caret does not jump when the owner types a capital.
     */
    val historyQuery: String = "",
    /** The status the owner narrowed to, or `null` for all. */
    val historyFilter: HistoryFilter? = null,
    /** What the table draws: [rows] narrowed by [historyQuery] and [historyFilter]. */
    val visibleRows: List<DepositHistoryRowUi> = emptyList(),
    /** The four summary figures, derived from [rows] plus `observeTodaysIncome`. */
    val summary: DepositSummaryUi? = null,
    /**
     * The history read's own failure banner, or `null`.
     *
     * A failed read must not be drawn as an empty list: "no deposits anywhere" is a claim
     * about money this screen must not make without having looked — the same rule every
     * history surface here follows.
     */
    val errorMessage: String? = null,
) {
    /** Whether the empty state applies: looked, found nothing, nothing failed. */
    val isEmpty: Boolean get() = rows.isEmpty() && errorMessage == null

    /**
     * Whether the owner narrowed the list to nothing — the "no match" case, which is a
     * different sentence from [isEmpty]: "nothing came in" is a claim about the money, and
     * "no row matches" is a fact about the search box.
     */
    val isFilteredToNothing: Boolean get() =
        visibleRows.isEmpty() && rows.isNotEmpty() && errorMessage == null
}

/**
 * The three filters the prototype's history chips offer — `payments.html`'s
 * `[data-hfilter]`.
 *
 * `null` means "all". A separate enum rather than three booleans, for the reason every other
 * filter in this app is one: three booleans can describe five states, four of which do not
 * exist.
 */
enum class HistoryFilter { Aktif, Sebagian, Dibatalkan }
