package net.klinik.rental.ui.screens.kontrak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Lucide
import net.klinik.rental.R
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnNumPad
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.format.formatTanggalBulan
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The open-contract form, as a Material3 `ModalBottomSheet`.
 *
 * ## Why a sheet and not a `KnModal`
 *
 * Two pickers, a date, a rate with its numpad, and eight schedule chips is exactly
 * the "many inputs or a tall keyboard" case the epic context names: on a tablet a
 * centred dialog covers the numpad it exists to serve, and the owner has to dismiss
 * the thing he opened in order to reach the input. A sheet grows from the bottom
 * edge, so the numpad stays above the keyboard area and reachable at every height.
 * `VehicleFormSheet` and `RenterFormSheet` make the same choice.
 *
 * ## Why the rate is a numpad and not a text field
 *
 * The epic context: "Nominal entry uses the on-screen numpad, never the system
 * keyboard — it is used while standing and holding something, so large touch targets
 * beat typing speed." The rate is the one field on this form that is money, so it is
 * the one that gets the numpad. `KnNumPad` re-syncs from `value` through a
 * `LaunchedEffect`, so [ContractFormState.rateDigits] must hold exactly what the pad
 * emits — a grouped `"Rp 250.000"` would be normalised to something else and the
 * owner's digits discarded.
 *
 * ## What this form deliberately does not have
 *
 * No end-date field (Story 2.7 ends a contract), no settled-through-date (Epic 3),
 * and no `workSchedule` string beside the rest day — two encodings of one fact can
 * disagree. The schedule is the 6/7-day chip pair plus, for six days, the rest-day
 * chip row.
 *
 * Service exceptions are **not** here either, and that is a boundary rather than an
 * omission: a service exception is a *day the contract is not charged*, and it has
 * to be marked against a specific stored row on a contract the owner has already
 * opened. `ExceptionFormSheet` is that surface — mounted over the contract list by
 * `KontrakScreen` when the owner taps a row — and putting an editor for the contract's
 * *compensation* days on the form for *opening* it would offer a control for a
 * contract that does not exist yet.
 *
 * ## Why the sheet renders its own failure rather than leaving it to the screen
 *
 * `KontrakScreen` renders `state.errorMessage` in its own composition, but a
 * `ModalBottomSheet` is a **separate window** layered above the screen's. An
 * unexpected write failure therefore set a banner *beneath* the sheet: the write
 * failed, the sheet stayed open, and the owner saw nothing at all. So the sheet takes
 * [errorMessage] and draws it inside itself, where it is on top of everything the
 * owner is looking at.
 *
 * @param state the form's own state, held by the ViewModel so a rotation cannot
 *   lose half-typed input.
 * @param errorMessage the last failure that has no field of its own — an unexpected
 *   write error. Drawn as a banner inside the sheet, above the buttons. Field-level
 *   refusals travel on [state] instead, because those belong on the field that caused
 *   them.
 * @param onDismissRequest the sheet's scrim tap and back gesture. Both are wired: a
 *   sheet with no way out except its own buttons is a trap.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContractFormSheet(
    state: ContractFormState,
    renterOptions: List<Renter>,
    vehicleOptions: List<Vehicle>,
    onDismissRequest: () -> Unit,
    onChange: ((ContractFormState) -> ContractFormState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    val spacing = MaterialTheme.knSpacing
    // `skipPartiallyExpanded` so a second tap on the top-bar action does not find
    // the sheet at half height.
    //
    // The content's own height bound is `SheetMaxHeight` below — a fixed dp, not a
    // fraction, because `ModalBottomSheet` reports its window in dp and a "half the
    // screen" rule would have to re-derive the screen height to say anything.
    // `ModalBottomSheet` already constrains itself to the window; the fixed bound
    // stops the tallest content from pushing Save off the top of a short landscape
    // tablet.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.testTag(TAG_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                .heightIn(max = SheetMaxHeight),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            ContractPicker(
                label = stringResource(R.string.kontrak_field_renter),
                // A fixed literal, and the *only* tag applied to this node — see
                // `ContractPicker`'s KDoc on why tags are no longer derived from the
                // label and why a second `testTag` here would hide this one.
                tag = TAG_FIELD_RENTER,
                value = state.renterName,
                hint = stringResource(R.string.kontrak_picker_renter_hint),
                emptyMessage = stringResource(R.string.kontrak_picker_empty_renter),
                title = stringResource(R.string.kontrak_picker_renter_title),
                options = renterOptions,
                rowTitle = { it.name },
                // The phone number, because two renters can share a first name and
                // the number is the second thing the owner reads on the renter
                // screen. `Renter` guarantees it is non-blank.
                rowSubtitle = { it.phone },
                rowKey = { it.id },
                // The whole renter, not just the id: the selection writes the
                // renter's *name* beside their id so the field can show it without
                // a second lookup. `selectRenter` is the pure helper that does it.
                onSelect = { renter -> onChange { it.selectRenter(renter) } },
                error = state.renterError,
            )

            ContractPicker(
                label = stringResource(R.string.kontrak_field_vehicle),
                tag = TAG_FIELD_VEHICLE,
                value = state.vehiclePlate,
                hint = stringResource(R.string.kontrak_picker_vehicle_hint),
                // Its own copy, naming what is missing and where to add it — one
                // sentence for both pickers would tell the owner neither.
                emptyMessage = stringResource(R.string.kontrak_picker_empty_vehicle),
                title = stringResource(R.string.kontrak_picker_vehicle_title),
                options = vehicleOptions,
                rowTitle = { it.plate },
                // Brand and model, so two cars whose plates differ by one character
                // are tellable apart without reading the plate twice.
                rowSubtitle = { "${it.brand} ${it.model}" },
                rowKey = { it.id },
                // The whole vehicle: `selectVehicle` prefills the rate from this
                // vehicle's stored rate and overwrites whatever digits were there,
                // because they belonged to the previously selected car.
                onSelect = { vehicle -> onChange { it.selectVehicle(vehicle) } },
                error = state.vehicleError,
            )

            StartDateField(
                date = state.startDate,
                error = state.dateError,
                onPick = { date -> onChange { it.selectStartDate(date) } },
            )

            KnField(
                label = stringResource(R.string.kontrak_field_rate),
                value = state.rateDigits,
                onValueChange = { value ->
                    // Echoed straight back: `KnNumPad` normalises whatever it is
                    // given and re-syncs from `value`, so a caller that clamped or
                    // reformatted would see its own digits discarded.
                    onChange { it.copy(rateDigits = value, rateError = null) }
                },
                hint = stringResource(R.string.kontrak_rate_hint),
                error = state.rateError,
                // A static affix, not an input: the digits come from the numpad.
                leading = stringResource(R.string.kontrak_rate_affix),
                enabled = false,
                modifier = Modifier.testTag(TAG_FIELD_RATE),
            )

            // The numpad itself. `onSave` here only *commits the rate digits* — it
            // does not submit the form. A single Save key cannot mean two things,
            // and "I finished the rate" is a different intent from "I finished the
            // whole contract".
            KnNumPad(
                value = state.rateDigits,
                onValueChange = { digits ->
                    onChange { it.copy(rateDigits = digits, rateError = null) }
                },
                onSave = { amount ->
                    onChange { it.copy(rateDigits = amount.toString(), rateError = null) }
                },
                modifier = Modifier.testTag(TAG_NUMPAD),
            )

            SchedulePicker(
                sixDays = state.sixDays,
                restDay = state.restDay,
                onSelectSchedule = { six -> onChange { it.withSchedule(six) } },
                onSelectRestDay = { day -> onChange { it.selectRestDay(day) } },
            )

            // The failure that has no field of its own, drawn *inside* the sheet —
            // see the class KDoc on why the screen's banner cannot show it. Placed
            // directly above the buttons so it is the last thing read before Save,
            // and toned `Danger` to match the screen's own error banner.
            errorMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_SHEET_ERROR),
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.End),
                maxItemsInEachRow = 2,
            ) {
                KnButton(
                    label = stringResource(R.string.kontrak_cancel),
                    onClick = onDismissRequest,
                    variant = KnButtonVariant.Ghost,
                    modifier = Modifier.testTag(TAG_CANCEL),
                )
                KnButton(
                    label = stringResource(R.string.kontrak_save),
                    onClick = onSave,
                    variant = KnButtonVariant.Primary,
                    // `canSave` is the button's source, which is what its own KDoc
                    // claims. Leaving it unwired would make the property dead code
                    // and let a Save tap on an incomplete form through to the
                    // repository, where the refusal is a field error rather than a
                    // disabled button — the owner would learn what was missing only
                    // after tapping.
                    enabled = state.canSave,
                    modifier = Modifier.testTag(TAG_SAVE),
                )
            }
        }
    }
}

/**
 * The start-date field, and the Material3 date dialog it opens.
 *
 * ## Why Material3's `DatePickerDialog` and not a hand-built one
 *
 * The date is the one input on this form where a wrong value is invisible — `3` and
 * `30` look alike in a field, and a month is off by one in every hand-rolled
 * calendar. Material3's picker is already on the classpath, it is the platform's own
 * calendar with the device's locale and its month lengths, and building a second one
 * would be a second place for an off-by-one to live.
 *
 * AR-16 bans Material *Icons*, not Material components: `DatePickerDialog` is a
 * component, and it renders through the app's own `MaterialTheme`, so its colours
 * and type come from the tokens like everything else.
 *
 * ## Why the field is disabled
 *
 * The date arrives from the dialog, never from typing. `KnField(enabled = false)`
 * with a trailing affordance is the same shape as the two pickers — a value the
 * owner chooses rather than types — and it means the display format is produced by
 * [formatTanggalBulan] in exactly one place rather than by whatever the owner typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateField(
    date: LocalDate?,
    error: String?,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val hint = stringResource(R.string.kontrak_date_hint)

    KnField(
        label = stringResource(R.string.kontrak_field_start_date),
        // `""` rather than a default date: an untouched field has to be refusable
        // on its own field, and a pre-filled value the owner never looked at is the
        // prototype's hidden default wearing a label.
        value = date?.let { formatTanggalBulan(it) }.orEmpty(),
        onValueChange = {},
        placeholder = hint,
        error = error,
        enabled = false,
        trailingIcon = Lucide.CalendarDays,
        trailingContentDescription = hint,
        onTrailingIconClick = { open = true },
        modifier = modifier.testTag(TAG_FIELD_START_DATE),
    )

    if (open) {
        // Seeded from the current selection so reopening the dialog lands on the
        // date the owner already chose rather than snapping back to today.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // `selectedDateMillis` is UTC midnight for the picked
                        // calendar day, so it is read back through the same UTC
                        // offset it was written with. Converting through the device
                        // zone here would move the date by a day for anyone west of
                        // Greenwich — the classic off-by-one this whole component
                        // exists to avoid.
                        pickerState.selectedDateMillis?.let { millis ->
                            onPick(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        open = false
                    },
                    modifier = Modifier.testTag(TAG_DATE_CONFIRM),
                ) {
                    Text(stringResource(R.string.kontrak_date_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { open = false },
                    modifier = Modifier.testTag(TAG_DATE_DISMISS),
                ) {
                    Text(stringResource(R.string.kontrak_date_dismiss))
                }
            },
            modifier = Modifier.testTag(TAG_DATE_DIALOG),
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * The 6/7-day pair, and the rest-day row that appears only for six days.
 *
 * ## Why chips and not a `SegmentedButton` or a `RadioButton`
 *
 * `KendaraanScreen`'s filter row already establishes `KnChip`s in a `FlowRow` as
 * this app's pattern for a small mutually-exclusive set, and a chip carries the
 * `selected` state into the semantics tree so a screen reader can say which one is
 * active. `KnChip` also clears the 48dp floor on its own, which neither Material's
 * segmented button nor a bare radio row does at these sizes.
 *
 * ## Why the rest-day row is hidden for seven days
 *
 * `null` rest day *is* the seven-day week. Leaving the chips visible while seven
 * days is selected would offer a choice that the seven-day schedule cannot
 * represent — the same contradiction the epic context's `workSchedule` +
 * `offDay` pair was rejected for. So the row is *unmounted*, not merely disabled:
 * a disabled chip row still says "there is a day off here", which would be false.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchedulePicker(
    sixDays: Boolean,
    restDay: DayOfWeek?,
    onSelectSchedule: (Boolean) -> Unit,
    onSelectRestDay: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.kontrak_schedule_label),
            style = MaterialTheme.knTypography.label,
            color = MaterialTheme.knColors.textSecondary,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            KnChip(
                text = stringResource(R.string.kontrak_schedule_six),
                selected = sixDays,
                onClick = { onSelectSchedule(true) },
                modifier = Modifier.testTag(TAG_SCHEDULE_SIX),
            )
            KnChip(
                text = stringResource(R.string.kontrak_schedule_seven),
                selected = !sixDays,
                onClick = { onSelectSchedule(false) },
                modifier = Modifier.testTag(TAG_SCHEDULE_SEVEN),
            )
        }

        // Only for a six-day week — see the KDoc above on why this is unmounted
        // rather than disabled.
        if (sixDays) {
            Text(
                text = stringResource(R.string.kontrak_rest_day_label),
                style = MaterialTheme.knTypography.label,
                color = MaterialTheme.knColors.textSecondary,
                modifier = Modifier.padding(top = spacing.sm),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_REST_DAY_ROW),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                REST_DAY_ORDER.forEach { day ->
                    // Exactly one is selected, structurally: `restDay` holds one
                    // value, so there is no way to express two selections and no
                    // validation to forget. Tapping the selected day is a no-op
                    // rather than a deselect — a six-day week with no rest day is
                    // not a schedule this app can represent.
                    KnChip(
                        text = day.label(),
                        selected = day == restDay,
                        onClick = { onSelectRestDay(day) },
                        modifier = Modifier.testTag(restDayTag(day)),
                    )
                }
            }
            Text(
                text = stringResource(R.string.kontrak_rest_day_hint),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textTertiary,
            )
        }
    }
}

/**
 * A `LocalDate` as UTC midnight in epoch millis.
 *
 * The inverse of the read-back in [StartDateField], and deliberately both in UTC:
 * `DatePickerState.selectedDateMillis` is documented as UTC midnight for the chosen
 * calendar day, so writing and reading through the device zone would shift the date
 * by a day for anyone whose offset is not zero.
 */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The rest-day chip's tag, derived from the enum name so the two cannot drift. */
internal fun restDayTag(day: DayOfWeek): String = "kontrak-rest-day-${day.name}"

/**
 * A bound on the sheet's content.
 *
 * `ModalBottomSheet` already constrains itself to the window; this stops two
 * pickers, a date, a rate, a 66dp-key numpad and eight chips from growing past a
 * tall tablet and pushing the Save button off the top. The sheet's own scroll
 * container handles the rest.
 */
private val SheetMaxHeight = 900.dp

internal const val TAG_SHEET = "contract-form-sheet"
internal const val TAG_FIELD_RENTER = "contract-field-renter"
internal const val TAG_FIELD_VEHICLE = "contract-field-vehicle"
internal const val TAG_FIELD_START_DATE = "contract-field-start-date"
internal const val TAG_FIELD_RATE = "contract-field-rate"
internal const val TAG_NUMPAD = "contract-numpad"
internal const val TAG_SCHEDULE_SIX = "contract-schedule-six"
internal const val TAG_SCHEDULE_SEVEN = "contract-schedule-seven"
internal const val TAG_REST_DAY_ROW = "contract-rest-day-row"
internal const val TAG_DATE_DIALOG = "contract-date-dialog"
internal const val TAG_DATE_CONFIRM = "contract-date-confirm"
internal const val TAG_DATE_DISMISS = "contract-date-dismiss"
internal const val TAG_SAVE = "contract-save"
internal const val TAG_CANCEL = "contract-cancel"
internal const val TAG_SHEET_ERROR = "contract-sheet-error"
