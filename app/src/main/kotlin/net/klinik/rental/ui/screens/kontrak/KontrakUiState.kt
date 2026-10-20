package net.klinik.rental.ui.screens.kontrak

import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.ServiceException
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.domain.parse.parseRupiahInput
import net.klinik.rental.ui.components.NUMPAD_MAX_DIGITS
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.format.formatTanggalBulan
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One row of the contract list: the contract plus the two names the owner reads
 * and the two strings already formatted for display.
 *
 * ## Why the names and the formatted strings are on the row
 *
 * The list must show the renter's name and the vehicle's plate, and both flows are
 * already observed by the ViewModel's `combine`. Resolving them there keeps the
 * screen stateless — it renders what it is handed — and keeps the join in one
 * place instead of in every row composable.
 *
 * The money and the date are pre-rendered for the same reason `KendaraanScreen`'s
 * rows carry `rateText`: `formatRupiah` and the date formatter are called once per
 * emission, in the ViewModel, so two screens cannot disagree about how a rate or a
 * date looks.
 *
 * @param renterName the renter's stored name. Resolved from the observed renter
 *   flow rather than joined in SQL — see `ContractRepository.observeContracts`.
 * @param vehiclePlate the vehicle's normalised plate, which is the string the
 *   fleet list shows.
 * @param dateText the start date as `23 Okt` — the **month** form, because the
 *   list's job is telling one rental from another and a weekday cannot: two
 *   rentals begun on the 23rd of different months both render `23 Jum`. See
 *   `formatTanggalBulan`.
 * @param exceptionCount how many days this contract is *not* charged. Carried on
 *   the row rather than read from a second list so the badge needs no lookup and
 *   is drawn on the first frame: the AC wants the owner to see which rentals carry
 *   compensation days *before* opening anything. Zero draws no badge at all — a
 *   "0" on every row would be a claim about data the owner did not ask for.
 * @param canEnd whether the row offers the end verb. `contract.isOpen`, carried as a
 *   field rather than read in the composable because *absence* is what the row must
 *   draw — an ended contract has no end affordance at all, not a disabled one, for the
 *   reason [ExceptionFormState] gives about its add block.
 *
 *   **Defaulted, and that default is a trap in two directions.** It is defaulted so the
 *   twenty-odd hand-built fixtures in this package's tests do not each have to restate
 *   it — and `copy()` does *not* re-evaluate a default, so a fixture that copies a row
 *   and gives the `contract` an `endDate` keeps the original's `canEnd` and draws an end
 *   verb on a contract that already ended. That is why [toRow] sets it explicitly, and
 *   why every test fixture that copies a row to make it ended sets it too.
 * @param endDateText the end date as `14 Okt`, or `null` while the contract runs. The
 *   row draws it in place of [dateText] once ended, so a finished rental says *when it
 *   finished* rather than only when it began — the owner reconciling a bill needs the
 *   end, and `null` is what distinguishes "still running" from "began and ended on the
 *   same day" without a second boolean.
 *
 *   Defaulted for the same reason [canEnd] is, with the same `copy()` caveat.
 */
data class ContractRow(
    val contract: Contract,
    val renterName: String,
    val vehiclePlate: String,
    /** `formatRupiah(contract.dailyRate)`, computed once per emission. */
    val rateText: String,
    /** `formatTanggalBulan(contract.startDate)`, e.g. `23 Okt`. Carries the month. */
    val dateText: String,
    val exceptionCount: Int = 0,
    val canEnd: Boolean = contract.isOpen,
    val endDateText: String? = null,
    /**
     * `formatTanggalBulan(settledThroughDate)`, or `null` when the contract carries no
     * settlement — Story 3.6's.
     *
     * `null` is what both hides the settlement verb and removes this line, which is why
     * it is one field rather than a flag plus a date: a contract that is not settled has
     * no day to show, so a separate boolean could only ever agree with the null and
     * disagree with itself.
     */
    val settledThroughText: String? = null,
)

/**
 * The whole screen in one value.
 *
 * Built with `combine` + `stateIn` + `distinctUntilChanged()` over four Room flows
 * (see `KontrakViewModel`).
 *
 * @param isLoading true while the contract flow has not emitted anything yet.
 *   See `KendaraanUiState.isLoading`: Room's first emission is an empty list, so
 *   a screen that branched on emptiness showed "Belum ada Kontrak" during every
 *   load. The default is `false` for hand-built fixtures; `KontrakViewModel`'s
 *   `stateIn` initial value passes `true`.
 *
 * There is deliberately no search query and no filter. No acceptance criterion
 * asks for either, and the search bar's IME bug (`spec-2-4:224` —
 * `onSearch = { (onSubmit ?: onClear ?: {})() }` clears the query when no
 * `onSubmit` is passed) would come with it. A filter row is a later story's, when
 * one is asked for.
 *
 * @param errorMessage Indonesian copy for the last refused write, or `null`.
 *   Already-resolved text rather than an exception, because the exception's whole
 *   purpose is to survive until here and pick its words. It is *rendered* by the
 *   screen as a snackbar.
 *
 * @param exceptionSheet the open-contract exception editor, or `null`. A sheet
 *   rather than a destination, mounted by `KontrakScreen` beside the open-form
 *   sheet — never by `KontrakRoute`, because two `ModalBottomSheet` windows over one
 *   state is the defect Story 2.8's review found as its only `high`.
 *
 * @param exceptionRows the *stored* exceptions of whichever contract the sheet is
 *   open for, or every contract when no sheet is up — the same list the contract
 *   rows' count badges are drawn from. Derived in the ViewModel's `combine` and
 *   **never** stored inside [exceptionSheet]: a snapshot inside the sheet state
 *   would make every write have to write it back, which is the manual invalidation
 *   signal `epic-2-context.md:44` forbids. Room's tracker re-emits, so a delete makes
 *   the row disappear on its own.
 *
 * @param endSheet the end-contract editor, or `null`. The third sheet, and the same
 *   rule as [exceptionSheet]: mounted by `KontrakScreen` beside its two siblings,
 *   never by `KontrakRoute`.
 */
data class KontrakUiState(
    val rows: List<ContractRow> = emptyList(),
    val form: ContractFormState? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val exceptionSheet: ExceptionFormState? = null,
    val exceptionRows: List<ServiceExceptionRow> = emptyList(),
    val endSheet: EndContractFormState? = null,
    /** The FR-9b settlement sheet, or `null`. The fourth, and the same mounting rule. */
    val settleSheet: SettleFormState? = null,
) {
    /**
     * Whether the empty state applies.
     *
     * One emptiness, not two: with no search bar there is no "no match" state to
     * tell apart from "nothing stored", and inventing one would be a second
     * message for a situation that cannot arise.
     */
    val isEmpty: Boolean get() = rows.isEmpty()

    /** The subtitle on the top bar: how many contracts are stored. */
    val contractCount: Int get() = rows.size
}

/**
 * The open-contract form's own state, held by the ViewModel.
 *
 * ## Why the two pickers hold an id *and* a display name
 *
 * The id is what gets written; the name is what the read-only field shows. Keeping
 * both means the field does not have to look the selection up again on every
 * recomposition, and it means a selection whose entity was removed from the
 * observed flows still renders as the thing the owner picked rather than blanking.
 *
 * ## Why `rateDigits` is a `String` and not a `Long`
 *
 * The rate field is fed by `KnNumPad`, which emits digit strings and re-syncs from
 * its `value`. Holding the digits means `parseRupiahInput` stays the only parse
 * path: the numpad emits digits, the parser turns them into a `Long` at save time,
 * and nothing between can substitute a default. `VehicleFormState` makes the same
 * choice for the same reason.
 *
 * @param renterId the selected renter, or `null` while nothing is picked.
 * @param vehicleId the selected vehicle, or `null` while nothing is picked.
 * @param startDate the day the rental began, or `null` while nothing is picked. A
 *   date in the past is accepted — contracts that began before the app existed are
 *   real (FR-7).
 * @param sixDays whether this is a six-day week. The two schedules the AC names are
 *   fully expressed by [restDay] (`null` = seven days), so this boolean is the
 *   *picker's* state rather than a second fact: it is what decides whether the rest
 *   day row is drawn, and [withSchedule] keeps it and [restDay] in step.
 * @param restDay the one day off per week. Non-null exactly when [sixDays] is true.
 */
data class ContractFormState(
    val renterId: String? = null,
    val renterName: String = "",
    val vehicleId: String? = null,
    val vehiclePlate: String = "",
    val startDate: LocalDate? = null,
    val rateDigits: String = "",
    val sixDays: Boolean = true,
    val restDay: DayOfWeek? = DEFAULT_REST_DAY,
    /**
     * Per-field messages, one slot per field that can be refused.
     *
     * Separate slots rather than a single `error`, because the refusal has to land
     * on the field that caused it: an owner looking at an empty vehicle picker
     * needs that picker to say why, not whichever field they happened to be
     * reading. `PenyewaFormState` draws the same distinction for the same reason.
     */
    val renterError: String? = null,
    val vehicleError: String? = null,
    val dateError: String? = null,
    val rateError: String? = null,
)

/** The one day off a six-day week starts on when the owner has not chosen one. */
val DEFAULT_REST_DAY: DayOfWeek = DayOfWeek.SUNDAY

// -------------------------------------------------------------- the name join

/**
 * Projects one contract into its row, resolving the two names from the observed
 * flows.
 *
 * ## Why this is a pure function and not inline in the ViewModel
 *
 * The join is the one piece of the ViewModel with a branch worth asserting — what
 * happens when a contract names a renter or a vehicle that is not in the observed
 * flows — and inside a `combine` that branch is unreachable from a test. Pulling it
 * out makes it assertable with two maps and no database, the same split
 * `PenyewaFilterTest` makes for the search predicate.
 *
 * ## Why the id is the fallback rather than a blank
 *
 * The two foreign keys make a missing name unreachable in production, so this is a
 * "cannot happen" branch — and the two ways to write one are a crash or a visible
 * placeholder. A crash on a screen the owner is reading is worse than a row naming
 * an id, and a blank would render as an empty row that looks like a rendering bug.
 * The id is at least the truth about what is stored.
 *
 * @param renterNames renter id -> stored name.
 * @param vehiclePlates vehicle id -> normalised plate.
 * @param exceptionCounts contract id -> how many days that contract is not charged.
 *   Grouped **once per emission** rather than per row, for the same reason the two
 *   maps are: a contract list is walked once and each row reads from a lookup that
 *   was built outside the loop. A row that counted exceptions by filtering the full
 *   list would be O(contracts x exceptions), and would still be a second reader of
 *   the same table from inside a projection that is meant to be pure.
 */
fun Contract.toRow(
    renterNames: Map<String, String>,
    vehiclePlates: Map<String, String>,
    exceptionCounts: Map<String, Int> = emptyMap(),
    // `null` when nothing has been settled. See [ContractRow.settledThroughText].
    settledThroughDate: LocalDate? = null,
): ContractRow = ContractRow(
    contract = this,
    renterName = renterNames[renterId] ?: renterId,
    vehiclePlate = vehiclePlates[vehicleId] ?: vehicleId,
    // `formatRupiah` and `formatTanggalBulan` here and nowhere else: money and dates
    // each have exactly one text path, so a figure cannot look different on two
    // screens. The month form, not the weekday form — see `ContractRow.dateText`.
    rateText = formatRupiah(dailyRate),
    dateText = formatTanggalBulan(startDate),
    exceptionCount = exceptionCounts[id] ?: 0,
    // Derived, not passed in: `isOpen` is the one definition of "still running" and
    // copying it from the caller would let a row disagree with the contract it holds.
    canEnd = isOpen,
    // `null` while running, so the row draws the start date alone; once ended, the same
    // formatter as every other date on this screen — a second spelling here is how a
    // reconciliation ends up comparing `14 Okt` against `14/10/2026`.
    endDateText = endDate?.let(::formatTanggalBulan),
    settledThroughText = settledThroughDate?.let(::formatTanggalBulan),
)

/**
 * Projects one stored exception into its row.
 *
 * The counterpart to [toRow], and the reason the sheet's list needs no lookup of
 * its own: the date is formatted once per emission, in the ViewModel, so the sheet
 * renders what it is handed and two screens cannot spell a date two ways.
 */
fun ServiceException.toRow(): ServiceExceptionRow = ServiceExceptionRow(
    exception = this,
    dateText = formatTanggalBulan(date),
)

// ------------------------------------------------------------ pure form edits

/**
 * Applies a renter selection.
 *
 * Pure and top-level so the `RENTER` picker's effect is testable without a device
 * and without a ViewModel, and so the ViewModel's handler is a one-line delegation
 * rather than a place logic can hide.
 */
fun ContractFormState.selectRenter(renter: Renter): ContractFormState = copy(
    renterId = renter.id,
    renterName = renter.name,
    // The error clears on selection rather than staying until Save is tapped
    // again: an error that survived the fix would accuse the owner of a field they
    // have already filled in.
    renterError = null,
)

/**
 * Applies a vehicle selection, prefilling the rate from that vehicle.
 *
 * ## Why the rate is prefilled and still editable
 *
 * The contract's rate is copied from the vehicle at open time and then frozen
 * (FR-7, AD-2), so the vehicle's rate is the sensible starting point and the owner
 * should not have to retype it. It stays editable because negotiated rates are
 * explicitly allowed — a renter who agreed a different price must be recordable,
 * and a locked field would make that impossible.
 *
 * Prefilling *overwrites* whatever digits were there. That is the right direction:
 * the digits came from the previous vehicle's rate, so keeping them would silently
 * carry one car's price onto another. The owner sees the new figure before saving,
 * which is what makes it a prefill rather than a substitution.
 */
fun ContractFormState.selectVehicle(vehicle: Vehicle): ContractFormState = copy(
    vehicleId = vehicle.id,
    vehiclePlate = vehicle.plate,
    // Clamped to what the numpad can hold. `Vehicle.dailyRate` is a `Long` and the
    // domain accepts anything positive, so a rate at or above Rp 1.000.000.000.000
    // is thirteen digits — and `KnNumPad` renders at most `NUMPAD_MAX_DIGITS`
    // (12). Seeding the unclamped value would leave the state holding thirteen
    // digits while the display shows twelve, and the first key tap would then
    // overwrite it with the pad's own truncated buffer: the owner types one digit
    // and the rate silently loses its leading digit. Truncating at the source keeps
    // the state and the display showing the same string, which is what makes the
    // value the owner read the value that gets stored.
    rateDigits = vehicle.dailyRate.toString().takeLast(NUMPAD_MAX_DIGITS),
    vehicleError = null,
    // The old rate belonged to the old vehicle; its refusal, if any, went with it.
    rateError = null,
)

/**
 * Switches between the six-day and seven-day schedules.
 *
 * ## Why the two are kept in step here
 *
 * `null` rest day *is* the seven-day week (see `Contract`), so the schedule picker
 * has no separate fact to store — but a `restDay` left set while seven days is
 * selected would be a second, contradicting encoding of the same fact, which is
 * exactly what the epic context's `workSchedule` + `offDay` pair was rejected for.
 * So switching to seven days clears the rest day, and switching back fills in the
 * default again: a six-day week always has a day off, and a picker that opened with
 * nothing selected would be a form the owner cannot complete by reading it.
 *
 * That does mean a day the owner picked is not remembered across a toggle to seven
 * days and back. It is a two-chip choice on one sheet with a named default, and the
 * alternative is a third field whose only job is to remember a value the form cannot
 * currently represent — hidden state that could disagree with the visible picker.
 */
fun ContractFormState.withSchedule(sixDays: Boolean): ContractFormState = copy(
    sixDays = sixDays,
    restDay = if (sixDays) restDay ?: DEFAULT_REST_DAY else null,
)

/**
 * Chooses the one day off.
 *
 * "Exactly one" is structural rather than enforced: a `DayOfWeek?` holds one value,
 * so there is no way to express two selections and no validation to forget. The
 * picker's selected state is `restDay == thisDay`, which is why tapping the
 * already-selected day is a no-op rather than a deselect — a six-day week without a
 * rest day is not a schedule this app can represent.
 */
fun ContractFormState.selectRestDay(day: DayOfWeek): ContractFormState =
    if (!sixDays) this else copy(restDay = day)

/**
 * Applies a start-date selection.
 *
 * No range check, deliberately: a date in the past is accepted, because contracts
 * that began before this app existed are real (FR-7). The only structurally
 * impossible date — one after the contract ends — cannot be picked here at all,
 * because this form has no end-date field; Story 2.7 owns ending a contract.
 */
fun ContractFormState.selectStartDate(date: LocalDate): ContractFormState = copy(
    startDate = date,
    dateError = null,
)

/** The rate as a `Long`, or `null` when the digits are not a positive amount. */
fun ContractFormState.parsedRate(): Long? = parseRupiahInput(rateDigits)

/**
 * The first field that is not yet answerable, or `null` when the form is complete.
 *
 * ## Why one function rather than a boolean per field
 *
 * Save is refused for four different reasons and each has to land on a different
 * field. Returning *which* one is missing keeps that decision in one place, so the
 * ViewModel's refusal path cannot drift from the check that gates the button — and
 * a test can assert the order as well as the outcome.
 *
 * The order is the order the fields appear on the sheet, top to bottom, so the
 * message lands on the first thing the owner would notice rather than on whichever
 * field a dictionary happened to yield.
 *
 * Pure and top-level, so the `OPEN_NO_SELECTION` and `OPEN_RATE_ZERO` matrix rows
 * are testable with no device, no database and no ViewModel.
 */
fun ContractFormState.firstMissingField(): ContractField? = when {
    renterId == null -> ContractField.Renter
    vehicleId == null -> ContractField.Vehicle
    startDate == null -> ContractField.StartDate
    parsedRate() == null -> ContractField.Rate
    sixDays && restDay == null -> ContractField.RestDay
    else -> null
}

/**
 * Whether Save is allowed.
 *
 * The button's `enabled` is a courtesy and this is its source; the *rule* is in
 * `ContractRepository.open` and, for the one-active half, in the schema's two
 * `UNIQUE` indexes. A screen that skipped this check would still be correct, only
 * slower to fail — which is the same relationship the plate pre-check has to the
 * plate index.
 */
val ContractFormState.canSave: Boolean get() = firstMissingField() == null

/**
 * Builds the contract this form describes, or `null` when the form is not complete.
 *
 * ## Why this is a pure function and not inline in the ViewModel
 *
 * The projection is the one place the form's five fields become the model's five
 * arguments, and a swapped pair there writes a contract against the wrong car or the
 * wrong person — a wrong row that looks exactly like a right one. Pulling it out
 * makes the mapping assertable with no database and no ViewModel, the same split
 * `PenyewaFilterTest` makes for the search predicate.
 *
 * ## Why `null` rather than throwing
 *
 * An incomplete form is a state the caller already knows how to report — it is what
 * [firstMissingField] names — so this returns `null` and lets the ViewModel put the
 * message on the field that caused it. Throwing would collapse "you have not picked a
 * car" into the same path as a genuinely unexpected failure, and the owner would see
 * "Gagal menyimpan. Coba lagi." instead of being told what to fill in.
 *
 * The rate is read through [parsedRate], so a zero or unparseable value is *absent*
 * here exactly as it is in [firstMissingField] — one definition of "a usable rate",
 * not two. [Contract]'s own constructor still refuses a bad rate, which is the
 * guarantee for a caller that never came through this function.
 *
 * The returned contract is always open (`endDate = null`): this story never opens one
 * that has already ended, and ending a contract is Story 2.7's.
 *
 * @param id the new contract's id, from `ContractRepository.newContractId()`. Passed
 *   in rather than generated here, so this stays pure and a test can pin it.
 */
fun ContractFormState.toContract(id: String): Contract? {
    if (firstMissingField() != null) return null
    val renterId = renterId ?: return null
    val vehicleId = vehicleId ?: return null
    val startDate = startDate ?: return null
    val rate = parsedRate() ?: return null

    return Contract(
        id = id,
        renterId = renterId,
        vehicleId = vehicleId,
        startDate = startDate,
        dailyRate = rate,
        // The schedule's two representations are reconciled here and nowhere else:
        // a seven-day week stores no rest day, whatever the picker last held.
        restDay = if (sixDays) restDay else null,
        endDate = null,
    )
}

/**
 * The fields a refusal can land on.
 *
 * An enum rather than a `String` field name, so the ViewModel's `when` over it is
 * exhaustive and a field added later fails the build rather than silently falling
 * into a default branch.
 */
enum class ContractField {
    Renter,
    Vehicle,
    StartDate,
    Rate,
    RestDay,
}

// ------------------------------------------------------------ Story 2.6: exceptions

/**
 * One stored exception, plus the date already formatted for display.
 *
 * `dateText` rather than the raw `LocalDate` for the same reason `ContractRow` has
 * one: `formatTanggalBulan` is called once per emission, in the ViewModel, so two
 * screens cannot disagree about how a date looks and no composable formats anything.
 *
 * The **month** form (`14 Okt`), not the weekday form. A weekday cannot tell two
 * exceptions apart — `14 Jum` and `14 Rab` are the only two things a weekday gives
 * you, and a list of marked days read against a bill is a list that has to be
 * unambiguous. See `formatTanggalBulan` and `Rupiah.kt:104-146`.
 */
data class ServiceExceptionRow(
    val exception: ServiceException,
    val dateText: String,
)

/**
 * The exception sheet's own state: **which** contract, whether it can be edited,
 * and the two add fields.
 *
 * ## Why it holds the form and never the rows
 *
 * The stored exceptions are derived in the ViewModel's `combine` from the observed
 * flow, exactly as the list's renter names and plates are derived in Kotlin rather
 * than joined in SQL. Snapping them into this state would make every write have to
 * write them back — a manual invalidation signal, which `epic-2-context.md:44`
 * forbids precisely because a second mechanism can only ever disagree with Room's.
 * A consequence worth stating: the sheet cannot be open before the read lands,
 * because it is reached from a row and rows come from a flow that has not emitted
 * while `isLoading` is true. So it needs no skeleton of its own, and the four-state
 * contract is discharged structurally rather than by a test.
 *
 * ## Why `isReadOnly` is state and not a lookup
 *
 * It answers "may this contract take an exception?", and the answer is
 * `contract.isOpen` — a derivation over `endDate`, not a stored flag. The UI half of
 * the rule *unmounts* the add block and every remove control when it is true, and
 * the data half refuses the write in `ContractRepository`; the two are separate on
 * purpose, because a screen check is a courtesy and this is the rule.
 *
 * @param contractLabel the title's text, pre-resolved by the ViewModel from the
 *   renter and plate flows. Held as a string because the sheet needs to name the
 *   rental in one heading and looking both halves up again in a composable would be
 *   a second reader of `renter` and `vehicle`.
 * @param addDate the day being marked, or `null` while nothing is picked. Empty
 *   rather than defaulted to today, for the reason `ContractFormState.startDate` is:
 *   a visible default is the prototype's hidden one wearing a label.
 * @param addReason the owner's own words, or `""` untouched.
 */
data class ExceptionFormState(
    val contractId: String,
    val contractLabel: String,
    val isReadOnly: Boolean = false,
    val addDate: LocalDate? = null,
    val addReason: String = "",
    /** One slot per field that can be refused, for the reason `ContractFormState` has. */
    val dateError: String? = null,
    val reasonError: String? = null,
    /**
     * The row awaiting the impact dialog's confirmation, or `null`.
     *
     * ViewModel state rather than a `remember` inside the sheet, so a rotation mid-
     * confirmation does not silently drop a dialog the owner had already opened. The
     * dialog's *visibility* is therefore state rather than a composable's memory,
     * which is what lets the sheet assert "the dialog is open because the ViewModel
     * says so" instead of asserting its own local flag.
     */
    val pendingRemovalId: String? = null,
)

/**
 * The fields an exception refusal can land on.
 *
 * **Not** an extension of [ContractField]: that enum is the *open-contract form's*
 * refusal set, and this sheet adds no field to that form. Making this one a subclass
 * would tie two independent forms' error slots together, so a field added to either
 * would silently appear in the other's `when` — which is the kind of shared namespace
 * that makes an exhaustive `when` a lie.
 */
enum class ExceptionField {
    Date,
    Reason,
}

/**
 * Applies a date selection to the exception form.
 *
 * Clears its own error because the selection **is** the fix, and an error that
 * survived it would accuse the owner of a field they have already filled in — the
 * same rule `selectRenter` and `selectStartDate` follow.
 */
fun ExceptionFormState.selectExceptionDate(date: LocalDate): ExceptionFormState = copy(
    addDate = date,
    dateError = null,
)

/**
 * Applies a reason edit, clearing its own error as the owner types.
 *
 * Pure and top-level so the refusal-clearing is testable with no device, no database
 * and no ViewModel, and so the ViewModel's handler is a one-line delegation rather
 * than a place logic can hide.
 */
fun ExceptionFormState.setExceptionReason(reason: String): ExceptionFormState = copy(
    addReason = reason,
    reasonError = null,
)

/**
 * The first field that is not yet answerable, or `null` when the form is complete.
 *
 * ## Why the reason is second
 *
 * The order is the order the fields appear on the sheet, top to bottom, so the
 * message lands on the first thing the owner would notice. It is deliberately *not*
 * `ContractFormState.firstMissingField`'s shape extended with the read-only case: a
 * read-only sheet has no add block at all, so asking which add field is missing is
 * asking about a form that is not on screen.
 */
fun ExceptionFormState.firstMissingExceptionField(): ExceptionField? = when {
    addDate == null -> ExceptionField.Date
    addReason.isBlank() -> ExceptionField.Reason
    else -> null
}

/**
 * Whether Add is allowed.
 *
 * A **blank** reason rather than a missing one: the reason field opens empty and is
 * typed into, so emptiness is the "not yet answered" state, exactly as the date's
 * `null` is. `ServiceException`'s own `require(reason.isNotBlank())` is the data-layer
 * half of the same rule — a caller holding a blank reason cannot construct one either.
 *
 * A read-only sheet answers `false` unconditionally, so `canAddException` is safe to
 * read even in the state where the button is not composed at all.
 */
val ExceptionFormState.canAddException: Boolean
    get() = !isReadOnly && firstMissingExceptionField() == null

/**
 * Builds the exception this form describes, or `null` when the form is incomplete.
 *
 * ## Why `null` rather than throwing
 *
 * An incomplete form is a state the caller already knows how to report — it is what
 * [firstMissingExceptionField] names — so this returns `null` and lets the ViewModel
 * put the message on the field that caused it. Throwing would collapse "you have not
 * picked a day" into the same path as a genuinely unexpected failure, and the owner
 * would read the generic message instead of being told what to fill in.
 *
 * @param id the new exception's id, from
 *   `ContractRepository.newServiceExceptionId()`. Passed in rather than generated
 *   here, so this stays pure and a test can pin it — the same choice
 *   `ContractFormState.toContract` makes.
 */
fun ExceptionFormState.toServiceException(id: String): ServiceException? {
    if (firstMissingExceptionField() != null) return null
    val date = addDate ?: return null
    return ServiceException(
        id = id,
        contractId = contractId,
        date = date,
        // Verbatim, not trimmed: the reason is owner-visible text in their own
        // words, and what is stored is what the sheet reads back.
        reason = addReason,
    )
}

// ---------------------------------------------------------- ending a contract

/**
 * The end-contract sheet's state, held by the ViewModel so a rotation cannot lose a
 * half-chosen date.
 *
 * ## Why this state is much smaller than [ExceptionFormState]
 *
 * Because the exception sheet has rows to display and this one has none. That sheet's
 * state exists only to hold the add form *while the stored rows are derived* in the
 * ViewModel's `combine`; here there is nothing to derive, so what is left is identity and
 * one field. An `isReadOnly` flag is absent for the same reason — an ended contract never
 * gets this far: `KontrakViewModel.openEndContract` is only reachable from a row that
 * carries the end verb, and the repository refuses a second end anyway.
 *
 * @param endDate the day the rental ended, or `null` untouched. A **past** date is
 *   accepted and is the common case — a contract that ended before this app existed is
 *   real, and refusing a past date would make the owner's own history unrecordable. A
 *   date **after today** is refused by the repository, not here: `isOpen` is
 *   `endDate == null`, so any stored date ends the contract on the spot and a future one
 *   would free the car today for a rental still running.
 * @param dateError the last refusal that landed on this field, or `null`.
 *   `null` means "validation has not run", which is what `KnField` renders as *no*
 *   error row rather than an empty one.
 */
data class EndContractFormState(
    val contractId: String,
    /** `renter · plate`, the same label the exception sheet's title uses. */
    val contractLabel: String,
    val endDate: LocalDate? = null,
    val dateError: String? = null,
)

/**
 * Applies an end date to the form, clearing its own error.
 *
 * The selection **is** the fix, so an error surviving it would accuse the owner of a
 * field they have already filled in — the same rule [selectExceptionDate] follows.
 */
fun EndContractFormState.selectEndDate(date: LocalDate): EndContractFormState = copy(
    endDate = date,
    dateError = null,
)

/**
 * Whether the end button is allowed.
 *
 * `endDate != null` and nothing else. A before-start date is *not* filtered out here:
 * the owner may be typing the wrong date into the wrong field, and the sheet cannot
 * see the contract's start date without carrying it — so the repository refuses it and
 * the message names the start date, which is the figure the owner needs in order to fix
 * it. Filtering it at the button would leave the owner with a permanently disabled
 * control and no explanation.
 *
 * A **future** date is not filtered here either, for the same reason and with the same
 * division of labour: the sheet does not hold "today" — the ViewModel does, in its clock —
 * and the refusal names the day it compared against, which is the one figure the owner
 * cannot otherwise see. A button that silently greyed out on a future date would tell them
 * nothing about why.
 */
val EndContractFormState.canEndContract: Boolean
    get() = endDate != null

// --------------------------------------------------------- Story 3.6: settlement

/**
 * The FR-9b sheet's state — "tandai lunas sampai tanggal".
 *
 * ## Why it mirrors [EndContractFormState] rather than extending it
 *
 * Because the two actions are opposites that would be mutually exclusive in one type:
 * ending a contract stops the billing, settling it declares past days already paid.
 * Merged, the state would carry a date that means *both* — the one ambiguity this app's
 * rules exist to prevent. Two small types are cheaper than one ambiguous field.
 *
 * ## Why it carries the impact up front
 *
 * @property daysClosed and @property amountRemoved are read **before** the sheet opens,
 *   because the AC requires the confirmation to name them and a dialog that fills itself
 *   in a moment after opening is one the owner reads past. They are whole figures, not
 *   pre-formatted strings, for the reason `DepositPreview` is: every rupiah is formatted
 *   once, in the ViewModel, through the same `formatRupiah`.
 *
 * @property settleDate the chosen day, or `null` untouched. A **past** date is the
 *   expected case — that is the whole point of FR-9b — so nothing here biases toward
 *   today.
 */
data class SettleFormState(
    val contractId: String,
    /** `renter · plate`, the same label the end and exception sheets use. */
    val contractLabel: String,
    val settleDate: LocalDate? = null,
    val dateError: String? = null,
    val daysClosed: Int = 0,
    val amountRemoved: Long = 0L,
    val daysRemaining: Int = 0,
    val amountRemaining: Long = 0L,
)

/**
 * Applies a settlement date, clearing its own error.
 *
 * The selection **is** the fix, so an error surviving it would accuse the owner of a
 * field they have already filled in — the same rule [selectEndDate] follows.
 */
fun SettleFormState.selectSettleDate(date: LocalDate): SettleFormState = copy(
    settleDate = date,
    dateError = null,
)

/**
 * Whether the confirm button is allowed.
 *
 * A date **and** something to close. The second half is the one that matters most:
 * FR-9b has no undo and no audit trail, so a settlement that would close no day is an
 * action with nothing behind it that the owner cannot reverse from inside the app. A
 * disabled button beside the field saying so is the honest control — the alternative is
 * a live button that silently writes a no-op.
 *
 * The before-start case is deliberately *not* filtered here, matching [canEndContract]:
 * the repository refuses it with a message that names the contract's start date, which
 * is what the owner needs in order to fix it.
 */
val SettleFormState.canSettle: Boolean
    get() = settleDate != null && daysClosed > 0
