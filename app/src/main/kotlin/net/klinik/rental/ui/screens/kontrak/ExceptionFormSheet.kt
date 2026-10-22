package net.klinik.rental.ui.screens.kontrak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wrench
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnIconButton
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.format.pickerMillisToDate
import net.klinik.rental.ui.format.toPickerMillis
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import java.time.LocalDate

/**
 * The open contract's service-exception editor, as one Material3 `ModalBottomSheet`.
 *
 * ## Why one sheet and not a list sheet with a nested add sheet
 *
 * Two `ModalBottomSheet` windows over one state is the defect Story 2.8's review found
 * as its only `high`, and nesting one sheet's window inside another's is the same
 * shape. So this is a **single** window carrying three blocks: the add block on top,
 * the stored rows below, and — for the one destructive action — a `KnModal`.
 *
 * A `KnModal` is a platform `Dialog`, which is a different window *class* with its own
 * single purpose and no shared state, and the precedent for a dialog inside a sheet is
 * already on this screen: `ContractFormSheet`'s `StartDateField` opens a
 * `DatePickerDialog` from inside its own sheet. So this sheet also opens a
 * `DatePickerDialog` from inside itself, and the impact dialog is the same argument
 * applied to a decision rather than a value.
 *
 * ## Why a read-only contract *unmounts* the add block and the remove controls
 *
 * A greyed-out "Tandai" on a contract that cannot take one still claims it can, and a
 * disabled trash icon on a row that cannot be edited says the same thing more quietly.
 * The epic's rule is that a finished contract's record of what was not billed is
 * settled history. So when `isReadOnly` is true the block and every remove control
 * are **absent**, not disabled — and the rows still render, because reading them is
 * the one thing an ended contract's exceptions are for.
 *
 * The repository refuses the same write independently. The UI half and the data half
 * are separate on purpose: this one is a courtesy that makes the sheet honest, and
 * that one is the rule.
 *
 * ## Why the sheet renders its own failure rather than leaving it to the screen
 *
 * A `ModalBottomSheet` is a **separate window** layered above the screen's, so a
 * banner composed in `KontrakScreen`'s own window would be *beneath* this sheet and
 * invisible. `KontrakScreen` therefore suppresses its banner while this is up and
 * hands the message down here, where it is on top of everything the owner is looking
 * at. The same is true of the confirmation: the screen's snackbar cannot be seen while
 * a sheet is over it, so this sheet's own successful write surfaces through the
 * screen's `snackbarMessage` **after** it closes — which is why a dismissal mid-write
 * has to leave that confirmation intact.
 *
 * ## Why the date picker is deliberately unbounded
 *
 * The friendlier alternative is a Material3 `SelectableDates` greying out everything
 * before the contract's `startDate`, and it costs a `LocalDate`↔epoch-millis bridge —
 * which is exactly the UTC conversion whose two existing copies are already a
 * documented off-by-one trap (`ContractFormSheet.kt:336-340` and `:468`). A third copy
 * is worse than a refusal. So the picker offers every day, [canAddException] gates
 * only what is *missing*, and `ContractRepository.addServiceException`'s
 * before-start refusal lands on the date field naming the start date. The owner
 * learns the rule from a sentence rather than from an option that silently vanished,
 * and the guarantee still holds for a caller that never came through the UI.
 *
 * ## Why there is no search bar and no filter on this list
 *
 * No acceptance criterion asks for either, and `KnSearchBar`'s IME bug
 * (`spec-2-4:224`) comes with the bar. The list is twenty-odd rows at most.
 *
 * @param state the sheet's own state, held by the ViewModel so a rotation cannot lose
 *   a half-typed reason or drop a confirmation the owner had opened.
 * @param rows the stored exceptions of this contract, already filtered and formatted
 *   by the ViewModel — see [KontrakUiState.exceptionRows] for why they are derived
 *   rather than held here.
 * @param errorMessage the last failure that has no field of its own. Drawn as a banner
 *   *inside* the sheet, for the window reason above.
 * @param onClose the sheet's scrim tap and back gesture. Both are wired: a sheet with
 *   no way out except its own buttons is a trap.
 * @param onRequestRemove opens the impact dialog for one row. The row's id is passed
 *   rather than the row, because the id is what the delete is keyed on and the sheet
 *   is the only place that knows which row the owner tapped.
 * @param onConfirmRemove the confirmed half of the remove.
 * @param onCancelRemove the dialog's dismissals — scrim tap, back gesture and the
 *   Ghost button, which are the same decision and therefore share one handler.
 * @param successMessage the confirmation for a write that landed, drawn **inside** the
 *   sheet. It has to be here for the same reason [errorMessage] is: the screen's own
 *   snackbar lives in the window *beneath* this one, so a confirmation drawn there
 *   after an exception is marked would stay invisible until the owner dismissed the
 *   sheet — which is precisely at the moment they want to read it.
 * @param onDismissSuccess dismisses that confirmation. It is the screen's own
 *   `onSnackbarDismiss`, because it is the *same* message: one confirmation, two
 *   windows it can be drawn in, one setter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExceptionFormSheet(
    state: ExceptionFormState,
    rows: List<ServiceExceptionRow>,
    onClose: () -> Unit,
    onChange: ((ExceptionFormState) -> ExceptionFormState) -> Unit,
    onAdd: () -> Unit,
    onRequestRemove: (String) -> Unit,
    onConfirmRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    /**
     * Dismisses [errorMessage].
     *
     * Its own handler rather than aliased to [onDismissSuccess]: the two clear
     * different messages, and sharing a setter is how one message is cleared through
     * the other's channel while staying on screen. Not defaulted to `{}` — the same
     * "unreachable banner, nothing at the call site says so" failure `KontrakScreen`'s
     * `onErrorDismiss` documents.
     */
    onDismissError: () -> Unit = {},
    successMessage: String? = null,
    onDismissSuccess: () -> Unit = {},
) {
    val spacing = MaterialTheme.knSpacing

    // `skipPartiallyExpanded`, and the same fixed content bound as the open-contract
    // sheet — see `ContractFormSheet` on both counts.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        modifier = modifier
            .testTag(TAG_EXCEPTION_SHEET)
            // The ceiling belongs on the sheet, not the scrolling content — on the content it
            // became the scroll viewport and the action row below it was unreachable.
            // See `ContractFormSheet`'s sheet for the full explanation.
            .heightIn(max = net.klinik.rental.ui.components.SheetMaxHeight),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.kontrak_exception_title, state.contractLabel),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_EXCEPTION_TITLE),
            )

            // The add block, unmounted entirely for a read-only contract — see the
            // class KDoc. Nothing here is disabled; it is absent.
            if (!state.isReadOnly) {
                Text(
                    text = stringResource(R.string.kontrak_exception_add_label),
                    style = MaterialTheme.knTypography.label,
                    color = MaterialTheme.knColors.textSecondary,
                )

                ExceptionDateField(
                    date = state.addDate,
                    error = state.dateError,
                    onPick = { date -> onChange { it.selectExceptionDate(date) } },
                )

                KnField(
                    label = stringResource(R.string.kontrak_exception_field_reason),
                    value = state.addReason,
                    onValueChange = { reason -> onChange { it.setExceptionReason(reason) } },
                    placeholder = stringResource(R.string.kontrak_exception_reason_hint),
                    error = state.reasonError,
                    // Multi-line with a **bound**. `KnField` had `singleLine` and no
                    // way to say "up to three lines", and an unbounded field inside a
                    // `verticalScroll` sheet grows the sheet without limit — a layout
                    // defect rather than a preference. Three is enough for
                    // "ganti ban, mobil di bengkel" and keeps the Add button on screen.
                    singleLine = false,
                    maxLines = REASON_MAX_LINES,
                    modifier = Modifier.testTag(TAG_EXCEPTION_REASON),
                )

                KnButton(
                    label = stringResource(R.string.kontrak_exception_add),
                    onClick = onAdd,
                    icon = Lucide.Wrench,
                    variant = KnButtonVariant.Primary,
                    // `canAddException` is the button's source, and it is what makes
                    // "a blank reason leaves Add disabled rather than storing an
                    // exception whose keterangan is empty" true at the surface rather
                    // than only in the repository's `require`.
                    enabled = state.canAddException,
                    modifier = Modifier.testTag(TAG_EXCEPTION_ADD),
                )
            }

            Text(
                text = stringResource(R.string.kontrak_exception_list_label),
                style = MaterialTheme.knTypography.label,
                color = MaterialTheme.knColors.textSecondary,
                modifier = Modifier.padding(top = spacing.sm),
            )

            if (rows.isEmpty()) {
                // Says what to do, per `epic-2-context.md:60` — and for a read-only
                // contract the instruction above it is gone, so this is the only thing
                // that could have told the owner why the list is empty and empty it.
                KnEmptyState(
                    title = stringResource(R.string.kontrak_exception_empty_title),
                    body = stringResource(R.string.kontrak_exception_empty_body),
                    icon = Lucide.Wrench,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_EXCEPTION_EMPTY),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    rows.forEach { row ->
                        // Keyed on the exception's own id, not its position: an
                        // unkeyed composition in a loop is keyed by slot, so a row
                        // removed under a write would migrate the next row's state
                        // onto a different day.
                        key(row.exception.id) {
                            ExceptionRow(
                                row = row,
                                // The same flag that unmounts the add block above,
                                // for the same reason. A disabled remove on an ended
                                // contract still says the day can be un-marked, and
                                // the repository refuses it anyway — so a greyed-out
                                // control is a claim the data layer will not honour.
                                isReadOnly = state.isReadOnly,
                                onRequestRemove = { onRequestRemove(row.exception.id) },
                            )
                        }
                    }
                }
            }

            // The confirmation for a write that landed, drawn *inside* the sheet for
            // the same window reason as the failure below. It sits above the failure so
            // the two are never on the same pixels: `KontrakScreen` gives the error the
            // precedence its own banner gives it, and the same rule holds here — the
            // failure is the one that has to be read.
            successMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Success,
                    onDismiss = onDismissSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_EXCEPTION_SUCCESS),
                )
            }

            // The failure that has no field of its own, drawn *inside* the sheet —
            // see the class KDoc on why the screen's banner cannot show it.
            errorMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Danger,
                    onDismiss = onDismissError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_EXCEPTION_ERROR),
                )
            }
        }

            // The sheet's way out, **pinned** below the scrolling list.
            //
            // It is the only control that closes a sheet whose list can be any length, so
            // leaving it at the end of the scroll made it unreachable on a long list — the
            // owner could add an exception and then have no way back to the contract.
            KnButton(
                label = stringResource(R.string.kontrak_cancel),
                onClick = onClose,
                variant = KnButtonVariant.Ghost,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.sm)
                    .navigationBarsPadding()
                    .testTag(TAG_EXCEPTION_CLOSE),
            )
        }
    }

    // The impact dialog, mounted from `state.pendingRemovalId` so its visibility is
    // state rather than a `remember`. That is what makes a rotation mid-confirmation
    // keep the dialog the owner had opened, and what lets a test assert "the dialog is
    // up because the ViewModel says so".
    //
    // Its confirm is `Primary` and its dismiss `Ghost` — `KnModal` has no
    // danger-confirm parameter, and forking that component for one sentence is not
    // worth it: the dialog names the impact in its body, which is what makes the
    // decision legible.
    //
    // Removing an exception is the middle confirmation tier (`epic-2-context.md:62`):
    // it changes numbers — the day becomes billable again — so it gets a dialog naming
    // the impact. It is deliberately **not** the third tier's mandatory reason: that
    // belongs to Setoran cancellation in Epic 3, and honouring it here would mean
    // inventing a `cancelReason` column no acceptance criterion names.
    val pendingId = state.pendingRemovalId
    val pendingRow = rows.firstOrNull { it.exception.id == pendingId }
    KnModal(
        visible = pendingId != null,
        onDismissRequest = onCancelRemove,
        title = stringResource(R.string.kontrak_exception_remove_title),
        confirmLabel = stringResource(R.string.kontrak_exception_remove_confirm),
        onConfirm = onConfirmRemove,
        dismissLabel = stringResource(R.string.kontrak_exception_remove_dismiss),
        onDismiss = onCancelRemove,
        modifier = Modifier.testTag(TAG_EXCEPTION_REMOVE_DIALOG),
    ) {
        Text(
            text = stringResource(
                R.string.kontrak_exception_remove_body,
                // The pending row's own formatted date, read out of the list rather
                // than out of the add form — the owner tapped *this* row, and the
                // add form may hold a half-typed day that is not it. The fallback is
                // unreachable in practice (the flag is only ever set from a row that
                // is on screen) and says so rather than naming an empty date.
                pendingRow?.dateText.orEmpty(),
            ),
            style = MaterialTheme.knTypography.body,
            color = MaterialTheme.knColors.textPrimary,
        )
    }
}

/**
 * One stored exception: its day, its reason, and — for an open contract — the
 * control that removes it.
 *
 * A `KnListRow`, the shape every list in this app reuses, with `KnAvatar` at its
 * default 44dp in the leading slot (which is hard-boxed to 44dp, so a larger element
 * would overflow it rather than fill it).
 */
@Composable
private fun ExceptionRow(
    row: ServiceExceptionRow,
    isReadOnly: Boolean,
    onRequestRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KnListRow(
        title = row.dateText,
        subtitle = row.exception.reason,
        modifier = modifier.testTag(exceptionRowTag(row.exception.id)),
        leading = { KnAvatar(name = row.dateText) },
        trailing = if (isReadOnly) {
            // **Unmounted**, not disabled, and for the reason the class KDoc gives: a
            // greyed-out control on a contract that cannot take one still claims it can.
            // The `trailing` slot is nullable for exactly this — `KnListRow` draws
            // nothing in it rather than drawing a dead control.
            null
        } else {
            {
                // `KnIconButton` clears the 48dp floor on its own, and this is the
                // first bare icon button this screen has had, so it is covered by the
                // touch-floor walk in `KontrakScreenTest`.
                KnIconButton(
                    icon = Lucide.CalendarDays,
                    // Names the date, not just "a button exists": the owner has to know
                    // *which* row this removes, and the spoken label is the only thing
                    // that tells them when they cannot see the row.
                    contentDescription = stringResource(
                        R.string.kontrak_exception_remove_description,
                        row.dateText,
                    ),
                    onClick = onRequestRemove,
                    modifier = Modifier.testTag(exceptionRemoveTag(row.exception.id)),
                )
            }
        },
    )
}

/**
 * The exception's date field, and the Material3 date dialog it opens.
 *
 * ## Why Material3's `DatePickerDialog` and not a hand-built one
 *
 * The same reasoning `ContractFormSheet`'s `StartDateField` gives, and the same
 * precedent: the date is the one input where a wrong value is invisible — `3` and
 * `30` look alike in a field, and a month is off by one in every hand-rolled calendar.
 * AR-16 bans Material *Icons*, not Material components; this renders through the
 * app's own `MaterialTheme`.
 *
 * ## Why no `SelectableDates` is bound
 *
 * See the class KDoc: bounding the picker needs a `LocalDate`↔epoch-millis bridge
 * that is exactly the UTC conversion this app already holds two documented copies of,
 * and a third copy of that trap is worse than a refusal with a sentence attached.
 *
 * ## Why the field is disabled
 *
 * The date arrives from the dialog, never from typing, and a disabled `KnField` with
 * a trailing affordance is the shape the two contract pickers already use — a value
 * the owner chooses rather than types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExceptionDateField(
    date: LocalDate?,
    error: String?,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    ContractDateField(
        date = date,
        label = stringResource(R.string.kontrak_exception_field_date),
        error = error,
        onPick = onPick,
        placeholder = stringResource(R.string.kontrak_exception_date_hint),
        modifier = modifier.testTag(TAG_EXCEPTION_DATE),
    )
}

/**
 * The shared date field behind **both** contract sheets, and the Material3 dialog it
 * opens.
 *
 * ## Why this is one function and not two
 *
 * It was `private` here as `ExceptionDateField` until Story 2.7 needed the same shape
 * for an end date — and that is exactly the moment the UTC round-trip below became a
 * problem rather than a detail. `ContractFormSheet`'s `StartDateField` holds the third
 * copy of it, `private` to that file, so this app has three implementations of "a
 * `LocalDate` through a Material3 picker" and two of them are invisible from here. The
 * conversion is documented as a trap at each site because each was written as if it were
 * the only one; a fourth copy would be the first one written with that knowledge and
 * still not sharing it.
 *
 * The honest fix is to make it shared, and the smallest shared version is this: both
 * sheets are the same question — *pick one day* — differing only in label, error and
 * tag. `StartDateField` joins on a later story that needs to touch it, or earlier if a
 * third caller appears; for now two call sites in one file is not the moment to widen the
 * surface, but it *is* the moment to stop copying.
 *
 * ## Why Material3's `DatePickerDialog` and not a hand-built one
 *
 * The same reasoning `ContractFormSheet`'s `StartDateField` gives: the date is the one
 * input where a wrong value is invisible — `3` and `30` look alike in a field, and a
 * month is off by one in every hand-rolled calendar. AR-16 bans Material *Icons*, not
 * Material components; this renders through the app's own `MaterialTheme`.
 *
 * ## Why no `SelectableDates` is bound
 *
 * Bounding the picker needs a `LocalDate`↔epoch-millis bridge — which is the very
 * conversion below — so a bound here would be a third copy of the code it is trying to
 * avoid. Instead the picker offers every day and the **repository** refuses an impossible
 * one with a sentence naming the contract's own start date. The owner learns the rule from
 * a message rather than from an option that silently vanished, and the guarantee still
 * holds for a caller that never came through the UI.
 *
 * **And do not add `max(today)` here.** This function is shared by three fields whose
 * valid ranges differ, and the two date-level rules are both about *reading* a stored
 * fact rather than about the calendar: an **end** date after today is refused because
 * `isOpen` is `endDate == null`, so any stored date ends the contract on the spot — but a
 * **start** date in the future is perfectly legal (`open` accepts it, and a rental that
 * has not begun yet is a real thing to record). A `max(today)` here would silently make
 * future-start contracts unrecordable through the one form that can record them, and no
 * test in the start-date path would notice, because the sheet would simply refuse a date
 * the app is supposed to accept.
 *
 * ## Why the field is disabled
 *
 * The date arrives from the dialog, never from typing, and a disabled `KnField` with
 * a trailing affordance is the shape the contract pickers already use — a value the
 * owner chooses rather than types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContractDateField(
    date: LocalDate?,
    label: String,
    error: String?,
    onPick: (LocalDate) -> Unit,
    /**
     * What the field says while it is empty.
     *
     * Not optional in practice: both callers pass one, because a disabled field showing
     * nothing at all is indistinguishable from a broken one — and because the *point* of
     * leaving the end date empty is that the owner must choose it, so the field has to
     * say what is being chosen.
     */
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    KnField(
        label = label,
        // `""` rather than a default date, for the reason the contract form's start
        // date is: an untouched field has to be refusable on its own field, and a
        // pre-filled value the owner never looked at is the prototype's hidden default
        // wearing a label.
        value = date?.let { it.toString() }.orEmpty(),
        onValueChange = {},
        placeholder = placeholder,
        error = error,
        enabled = false,
        trailingIcon = Lucide.CalendarDays,
        trailingContentDescription = label,
        onTrailingIconClick = { open = true },
        modifier = modifier,
    )

    if (open) {
        // Seeded from the current selection so reopening the dialog lands on the date
        // the owner already chose rather than snapping back to today.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // The UTC round-trip lives in `ui.format`'s `toPickerMillis` /
                        // `pickerMillisToDate` — see those on why the zone matters, and why three
                        // sheets share one conversion rather than owning one each.
                        pickerMillisToDate(pickerState.selectedDateMillis)?.let(onPick)
                        open = false
                    },
                    modifier = Modifier.testTag(TAG_CONTRACT_DATE_CONFIRM),
                ) {
                    Text(stringResource(R.string.kontrak_date_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { open = false },
                    modifier = Modifier.testTag(TAG_CONTRACT_DATE_DISMISS),
                ) {
                    Text(stringResource(R.string.kontrak_date_dismiss))
                }
            },
            modifier = Modifier.testTag(TAG_CONTRACT_DATE_DIALOG),
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * The write half of the picker bridge, as a local alias.
 *
 * Delegates to [net.klinik.rental.ui.format.toPickerMillis] so this sheet and the two others
 * cannot disagree about the zone. No logic of its own.
 */
private fun LocalDate.toUtcMillis(): Long = toPickerMillis()

// ------------------------------------------------------------ ending a contract

/**
 * The end-contract sheet: one date and one button.
 *
 * ## Why this sheet is so much smaller than [ExceptionFormSheet]
 *
 * Because the exception sheet carries stored rows and this one carries none. Its whole
 * surface is "the day the car came back", and everything else either belongs to the
 * repository or does not exist yet.
 *
 * ## Why there is no impact dialog
 *
 * UX-DR11's middle tier — a dialog naming the impact — is for actions that *change
 * numbers*, and ending a contract changes none: the end date is the owner's own figure,
 * and the charge days after it simply stop being owed. The third tier, a mandatory
 * reason, is for deleting data, and this deletes nothing — the row stays, its exceptions
 * stay, and its history stays readable.
 *
 * So a dialog here would ask the owner to acknowledge a consequence they are in the
 * middle of typing. The one thing they *could* be wrong about is the date, and the date
 * is the only field.
 *
 * ## Why its own banners rather than the screen's
 *
 * The same window reason [ExceptionFormSheet] gives: this is a separate window layered
 * over the screen, so a snackbar rendered in the screen's composition is invisible while
 * it is up. Both the confirmation and the failure are drawn **here**, where the owner can
 * read them.
 *
 * @param state the sheet's own state, held by the ViewModel so a rotation cannot lose a
 *   half-chosen date.
 * @param errorMessage the last failure with no field of its own — an already-ended
 *   contract, or a write this screen does not model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndContractSheet(
    state: EndContractFormState,
    onClose: () -> Unit,
    onChange: ((EndContractFormState) -> EndContractFormState) -> Unit,
    onConfirmEnd: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    successMessage: String? = null,
    onDismissSuccess: () -> Unit = {},
) {
    val spacing = MaterialTheme.knSpacing

    // `skipPartiallyExpanded` and the fixed content bound, both for the reasons
    // `ExceptionFormSheet` and `ContractFormSheet` give. This sheet has less content
    // than either, so the bound is not load-bearing here — it is kept so a future field
    // cannot silently push the button off a short landscape tablet.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        modifier = modifier
            .testTag(TAG_END_SHEET)
            // On the sheet, not the content — see the exception sheet above.
            .heightIn(max = net.klinik.rental.ui.components.SheetMaxHeight),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.kontrak_end_title, state.contractLabel),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_END_TITLE),
            )

            ContractDateField(
                date = state.endDate,
                label = stringResource(R.string.kontrak_end_field_date),
                error = state.dateError,
                onPick = { date -> onChange { it.selectEndDate(date) } },
                // Says the field is waiting on a choice, and — the reason this hint
                // exists at all — that the end date itself is still charged. An empty
                // date field with no hint invites picking the day the car came back and
                // expecting that day not to be billed.
                placeholder = stringResource(R.string.kontrak_end_date_hint),
                modifier = Modifier.testTag(TAG_END_DATE),
            )

            // The confirmation, inside this window for the same reason the failure
            // below is — and above it, so a message and a failure are never on the same
            // pixels. `KontrakScreen` gives the error precedence on the screen; the same
            // rule holds here.
            successMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Success,
                    onDismiss = onDismissSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_END_SUCCESS),
                )
            }

            errorMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Danger,
                    onDismiss = onDismissError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_END_ERROR),
                )
            }
        }

            // The two decisions, **pinned**. Ending a contract is the one write in this app
            // that cannot be undone from the screen, so its button must not be something the
            // owner has to scroll to find.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.sm)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.End),
                maxItemsInEachRow = 2,
            ) {
                KnButton(
                    label = stringResource(R.string.kontrak_cancel),
                    onClick = onClose,
                    variant = KnButtonVariant.Ghost,
                    modifier = Modifier.testTag(TAG_END_CLOSE),
                )
                KnButton(
                    label = stringResource(R.string.kontrak_end_confirm),
                    onClick = onConfirmEnd,
                    icon = Lucide.CircleCheck,
                    variant = KnButtonVariant.Primary,
                    // `canEndContract` and nothing more: a date before the contract's start
                    // is *not* filtered out here, because the sheet cannot see the start
                    // date without carrying it, and a permanently disabled button with no
                    // explanation is worse than a refusal that names the start date.
                    enabled = state.canEndContract,
                    modifier = Modifier.testTag(TAG_END_CONFIRM),
                )
            }
        }
    }
}

internal const val TAG_END_SHEET = "kontrak-end-sheet"
internal const val TAG_END_TITLE = "kontrak-end-title"
internal const val TAG_END_DATE = "kontrak-end-date"
internal const val TAG_END_CONFIRM = "kontrak-end-confirm"
internal const val TAG_END_CLOSE = "kontrak-end-close"
internal const val TAG_END_ERROR = "kontrak-end-error"
internal const val TAG_END_SUCCESS = "kontrak-end-success"

/**
 * A bound on the sheet's content, and the row/remove tags keyed on the exception id.
 *
 * Derived from the id rather than from the row's position, so two rows cannot be
 * addressed by the same tag after a delete and a test asserting "that row's remove
 * control" cannot silently resolve to a different row's.
 */
internal fun exceptionRowTag(id: String): String = "kontrak-exception-row-$id"

/** The remove control's own tag, so it is addressable apart from its row. */
internal fun exceptionRemoveTag(id: String): String = "kontrak-exception-remove-$id"

/**
 * How many lines the reason field grows to before it scrolls.
 *
 * Three, because that is what the longest realistic reason needs — "ganti ban, mobil
 * di bengkel" is two — and a fourth would push the Add button and the stored rows
 * below the fold of a 760dp-tall portrait tablet.
 */
private const val REASON_MAX_LINES = 3

internal const val TAG_EXCEPTION_SHEET = "kontrak-exception-sheet"
internal const val TAG_EXCEPTION_TITLE = "kontrak-exception-title"
internal const val TAG_EXCEPTION_DATE = "kontrak-exception-date"
internal const val TAG_EXCEPTION_REASON = "kontrak-exception-reason"
internal const val TAG_EXCEPTION_ADD = "kontrak-exception-add"
internal const val TAG_EXCEPTION_CLOSE = "kontrak-exception-close"
internal const val TAG_EXCEPTION_EMPTY = "kontrak-exception-empty"
internal const val TAG_EXCEPTION_ERROR = "kontrak-exception-error"
internal const val TAG_EXCEPTION_SUCCESS = "kontrak-exception-success"
internal const val TAG_CONTRACT_DATE_DIALOG = "kontrak-date-dialog"
internal const val TAG_CONTRACT_DATE_CONFIRM = "kontrak-date-confirm"
internal const val TAG_CONTRACT_DATE_DISMISS = "kontrak-date-dismiss"
internal const val TAG_EXCEPTION_REMOVE_DIALOG = "kontrak-exception-remove-dialog"

