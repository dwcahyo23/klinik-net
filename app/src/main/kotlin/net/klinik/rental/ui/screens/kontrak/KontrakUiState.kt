package net.klinik.rental.ui.screens.kontrak

import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.Renter
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
 */
data class ContractRow(
    val contract: Contract,
    val renterName: String,
    val vehiclePlate: String,
    /** `formatRupiah(contract.dailyRate)`, computed once per emission. */
    val rateText: String,
    /** `formatTanggalBulan(contract.startDate)`, e.g. `23 Okt`. Carries the month. */
    val dateText: String,
)

/**
 * The whole screen in one value.
 *
 * Built with `combine` + `stateIn` + `distinctUntilChanged()` over three Room flows
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
 */
data class KontrakUiState(
    val rows: List<ContractRow> = emptyList(),
    val form: ContractFormState? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
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
 */
fun Contract.toRow(
    renterNames: Map<String, String>,
    vehiclePlates: Map<String, String>,
): ContractRow = ContractRow(
    contract = this,
    renterName = renterNames[renterId] ?: renterId,
    vehiclePlate = vehiclePlates[vehicleId] ?: vehicleId,
    // `formatRupiah` and `formatTanggalBulan` here and nowhere else: money and dates
    // each have exactly one text path, so a figure cannot look different on two
    // screens. The month form, not the weekday form — see `ContractRow.dateText`.
    rateText = formatRupiah(dailyRate),
    dateText = formatTanggalBulan(startDate),
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
