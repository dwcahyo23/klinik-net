package net.klinik.rental.ui.screens.setoran

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Lucide
import net.klinik.rental.R
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.components.KnNumPad
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.format.formatTanggalBulan
import net.klinik.rental.ui.format.pickerMillisToDate
import net.klinik.rental.ui.format.toPickerMillis
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import java.time.LocalDate

/**
 * The deposit sheet: who paid, how much, and how it arrived.
 *
 * ## Why a sheet and not a dialog — and why this story names the reason
 *
 * UX-DR18: a numpad needs vertical room, and on a tablet the virtual keyboard fills
 * most of the screen, so a centred dialog covers the numpad it exists to serve. A
 * `ModalBottomSheet` grows from the bottom edge and keeps the pad reachable at every
 * height. `ContractFormSheet` and `ExceptionFormSheet` make the same choice for the same
 * reason; this sheet has the strongest case of the three because the numpad is fixed-size
 * and cannot be made to fit in a smaller box.
 *
 * ## Why the fields are shared, not copied
 *
 * Because the same form has **two** presentations: this sheet in portrait, and the
 * prototype's two-pane split in landscape (`payments.html`, where the form is on the
 * left and the numpad in a fixed column on the right). The fields live in
 * [DepositFormFields] and both presentations call it — one implementation, two layouts.
 *
 * ## Why the numpad is not optional, and not skippable
 *
 * The epic's own reason: typing from zero forces the owner to *remember* a figure the app
 * already holds, and what is lost is not speed but the possibility of a mistype. So the
 * amount is never pre-filled and there is no other way to enter it — which is why this
 * composable has no `KnField` for the amount, only the pad.
 *
 * ## Why the sheet scrolls
 *
 * The content is a renter picker, two read-only rows, a method toggle, a conditional pair
 * of fields and a 4x3 grid — taller than a landscape tablet. The scroll belongs to the
 * **fields**, and the pad below is pinned: a pad that scrolls under a finger is a pad the
 * owner cannot hit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositSheet(
    state: SetoranUiState,
    onClose: () -> Unit,
    onChange: ((DepositFormState) -> DepositFormState) -> Unit,
    onSave: (Long) -> Unit,
    onConfirmPreview: () -> Unit,
    onDismissPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    val form = state.form ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        // **No `heightIn` here.** Capping the sheet's own modifier sizes its window to the
        // cap and leaves it floating above the bottom edge with the scrim showing under
        // it. `ModalBottomSheet` already bounds itself to the window and anchors to the
        // bottom; the cap belongs on the content column below.
        modifier = modifier.testTag(TAG_DEPOSIT_SHEET),
    ) {
        Column(
            // The bound, on the **outer** column.
            //
            // It has to be here, and it has to be a *bound* rather than a height: the
            // scrolling column below is weighted, and a weighted child is measured
            // against what is **left over** in its parent. A parent that wraps its
            // content leaves nothing over, so the form collapsed to zero height — the
            // defect `DepositSheetTest`'s "the sheet's form has a height" test caught.
            //
            // `fillMaxHeight` before the bound, so the parent takes the **full** height
            // the sheet offers and the weight has something to divide. A parent that
            // only wraps its content would leave the weighted child nothing.
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .heightIn(max = DepositSheetMaxHeight),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The **only** scroll container in the sheet.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                // `weight(1f, fill = false)`: the form takes its **natural** height, up
                // to what the action row below leaves. `fill = true` would make it fill
                // the whole sheet, so a short form would open a sheet of mostly
                // whitespace.
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // The title changes because the sheet's whole job does: a correction
            // updates a stored row in place, while "Catat Setoran" promises a new one.
            // An owner who cannot tell the two apart cannot tell whether tapping Save
            // will record a second deposit.
            Text(
                text = stringResource(
                    if (form.isEditing) R.string.deposito_edit_title else R.string.deposito_title,
                ),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_DEPOSIT_TITLE),
            )

            DepositFormFields(
                state = state,
                onChange = onChange,
            )
        }

            // ---- the action row, **pinned** below the scrolling form.
            //
            // Both of these used to sit at the end of the scrolling column, which is the
            // same defect in a different place: the pad's own SIMPAN key and the escape
            // hatch below it were laid out past the cap and could not be scrolled to, so a
            // sheet the owner had opened could not be filled in at all.
            //
            // The pad is the sheet's **primary** control — it is how the amount is entered
            // and `SIMPAN` on it is the way forward — so it stays whole and visible rather
            // than scrolling away under a finger.
            //
            // `onSave` goes to the handler that **computes the preview**, not to the one
            // that writes. That is the whole reason the preview is mandatory: the pad
            // has no other exit, so the dialog is the only way forward. The pad's own
            // zero/negative refusal — a red flash, an *event* that changes no state and
            // cannot be derived from the digits — happens before either, which is why
            // the sheet does not pre-check the amount itself.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                KnNumPad(
                    value = form.amountDigits,
                    onValueChange = { digits -> onChange { it.copy(amountDigits = digits) } },
                    onSave = onSave,
                    modifier = Modifier.testTag(TAG_NUMPAD),
                )

                KnButton(
                    label = stringResource(R.string.deposito_cancel),
                    onClick = onClose,
                    variant = KnButtonVariant.Ghost,
                    modifier = Modifier.testTag(TAG_DEPOSIT_CANCEL),
                )
            }
        }
    }

    // The mandatory preview, as a dialog over the sheet.
    //
    // Mounted **outside** the `ModalBottomSheet` and after it, so it composes in a
    // separate window above rather than inside the sheet's own column — a `KnModal`
    // nested in a `ModalBottomSheet`'s content would be two windows in one composable,
    // which is the shape Story 2.8's review found as its only `high`.
    DepositPreviewDialog(
        state = state,
        onConfirmPreview = onConfirmPreview,
        onDismissPreview = onDismissPreview,
    )
}

/**
 * The deposit's mandatory preview, as a dialog.
 *
 * ## Why this is its own composable
 *
 * Because the form has **two** presentations — the bottom sheet and the prototype's
 * two-pane split — and the preview is the step that actually **writes** the deposit.
 *
 * When the preview lived only inside the sheet, the split had no way to save: the pad's
 * `SIMPAN` computed a preview that nothing displayed, and the write behind it was gated
 * on the preview's confirm. The owner could enter an amount in the split and never
 * record it — a control that reaches nothing, which is the defect this repo keeps
 * shipping.
 *
 * So the dialog is mounted by **both** presentations, and the write path is the same
 * one either way.
 *
 * ## Why the dialog is mounted outside its host
 *
 * Because a `KnModal` nested inside a `ModalBottomSheet`'s content would be two windows
 * in one composable — the shape Story 2.8's review found as its only `high`. It is a
 * separate window above the sheet, or above the page in the split.
 *
 * Both handlers are **required**, not defaulted. A default would be a confirm that
 * silently does nothing, and the owner would tap "Ya, simpan" and watch nothing
 * happen with a deposit they believe they took.
 */
@Composable
internal fun DepositPreviewDialog(
    state: SetoranUiState,
    onConfirmPreview: () -> Unit,
    onDismissPreview: () -> Unit,
) {
    state.preview?.let { preview ->
        KnModal(
            visible = true,
            onDismissRequest = onDismissPreview,
            title = stringResource(R.string.deposito_preview_title),
            confirmLabel = stringResource(R.string.deposito_preview_confirm),
            onConfirm = onConfirmPreview,
            // "Cek dulu" rather than "Batal": nothing is cancelled. The form is still
            // open with its amount, and a label saying "cancel" tells the owner they lost
            // their work — which is the wrong story, and the reason they would press
            // confirm instead of actually checking.
            dismissLabel = stringResource(R.string.deposito_preview_dismiss),
            onDismiss = onDismissPreview,
            modifier = Modifier.testTag(TAG_PREVIEW),
        ) {
            // Three lines, no per-day dates. The epic context is explicit that the
            // ledger lives in the deposit history and not here, and the format below is
            // its own sentence for each shape.
            PreviewLine(preview.arrearsLine, Modifier.testTag(TAG_PREVIEW_ARREARS))
            PreviewLine(preview.closesLine, Modifier.testTag(TAG_PREVIEW_CLOSES))
            // Null rather than an empty line, so a deposit that left no credit shows no
            // `Kredit:` line at all — a line that is always present stops being read.
            preview.creditLine?.let { PreviewLine(it, Modifier.testTag(TAG_PREVIEW_CREDIT)) }
        }
    }
}

/** One preview line. A `Text`, because these are sentences rather than figures. */
@Composable
private fun PreviewLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.knTypography.body,
        color = MaterialTheme.knColors.textPrimary,
        modifier = modifier,
    )
}


/**
 * A bound on the sheet's content.
 *
 * `ModalBottomSheet` already constrains itself to the window; this stops the numpad's
 * bottom row running off the top of a short landscape tablet. 760dp rather than the
 * other sheets' 520–720 because this one carries a 4x3 grid.
 */
private val DepositSheetMaxHeight = 760.dp

/** The renter chip's tag, keyed on the renter id so two chips cannot collide. */
internal fun renterChipTag(renterId: String): String = "deposito-renter-$renterId"

/** The dropdown's trigger, so a test can open it without matching its label. */
internal const val renterPickerTag = "deposito-renter-picker"

/** The account chip's tag, keyed on the account id. */
internal fun accountChipTag(accountId: String): String = "deposito-account-$accountId"

internal const val TAG_DEPOSIT_SHEET = "deposito-sheet"
internal const val TAG_DEPOSIT_TITLE = "deposito-title"
internal const val TAG_DEPOSIT_CONTRACT_FACT = "deposito-contract-fact"
internal const val TAG_DEPOSIT_NO_CONTRACT = "deposito-no-contract"
internal const val TAG_METHOD_TRANSFER = "deposito-method-transfer"
internal const val TAG_METHOD_CASH = "deposito-method-cash"
internal const val TAG_ACCOUNT_ERROR = "deposito-account-error"
internal const val TAG_AMOUNT_ERROR = "deposito-amount-error"
internal const val TAG_REFERENCE = "deposito-reference"
internal const val TAG_NOTES = "deposito-notes"
internal const val TAG_NUMPAD = "deposito-numpad"
internal const val TAG_DEPOSIT_CANCEL = "deposito-cancel"
internal const val TAG_PREVIEW = "deposito-preview"
internal const val TAG_PREVIEW_ARREARS = "deposito-preview-arrears"
internal const val TAG_PREVIEW_CLOSES = "deposito-preview-closes"
internal const val TAG_PREVIEW_CREDIT = "deposito-preview-credit"
internal const val TAG_DEPOSIT_ERROR = "deposito-error"
internal const val TAG_DEPOSIT_SUCCESS = "deposito-success"
internal const val TAG_DEPOSIT_FIELD_DATE = "deposito-field-date"
internal const val TAG_DEPOSIT_RENTER_FACT = "deposito-renter-fact"
internal const val TAG_DEPOSIT_DATE_DIALOG = "deposito-date-dialog"
internal const val TAG_DEPOSIT_DATE_CONFIRM = "deposito-date-confirm"
internal const val TAG_DEPOSIT_DATE_DISMISS = "deposito-date-dismiss"

/**
 * The arrival-date field, and the Material3 calendar it opens.
 *
 * ## Why Material3's `DatePickerDialog` and not a hand-built one
 *
 * Because this is the one input where a wrong value is invisible — `3` and `30` look alike
 * in a field, and a month is off by one in every hand-rolled calendar. `ContractFormSheet`'s
 * start-date field makes the same choice for the same reason, and reusing the shape is what
 * keeps the two sheets behaving identically.
 *
 * AR-16 bans Material *icons*, not Material components: `DatePickerDialog` renders through
 * this app's own `MaterialTheme`, so its colours and type come from the tokens.
 *
 * ## Why the field is disabled and carries a calendar affordance
 *
 * Because the date arrives from the dialog, never from typing — so `formatTanggalBulan`
 * produces the display format in exactly one place, and the affordance is what the owner
 * taps. Same shape as `ContractFormSheet.StartDateField`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DepositDateField(
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val hint = stringResource(R.string.deposito_date_hint)

    KnField(
        label = stringResource(R.string.deposito_field_date),
        value = formatTanggalBulan(date),
        onValueChange = {},
        placeholder = hint,
        enabled = false,
        trailingIcon = Lucide.CalendarDays,
        trailingContentDescription = hint,
        onTrailingIconClick = { open = true },
        modifier = modifier.testTag(TAG_DEPOSIT_FIELD_DATE),
    )

    if (open) {
        // Seeded from the current selection, so reopening lands on the day the owner
        // already chose rather than snapping back to today.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toUtcMillis(),
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
                    modifier = Modifier.testTag(TAG_DEPOSIT_DATE_CONFIRM),
                ) {
                    Text(stringResource(R.string.deposito_date_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { open = false },
                    modifier = Modifier.testTag(TAG_DEPOSIT_DATE_DISMISS),
                ) {
                    Text(stringResource(R.string.deposito_date_dismiss))
                }
            },
            modifier = Modifier.testTag(TAG_DEPOSIT_DATE_DIALOG),
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * The write half of the picker bridge, as a local alias.
 *
 * Delegates to [net.klinik.rental.ui.format.toPickerMillis] so this sheet and the two contract
 * sheets cannot disagree about the zone. No logic of its own.
 */
private fun LocalDate.toUtcMillis(): Long = toPickerMillis()