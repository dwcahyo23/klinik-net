package net.klinik.rental.ui.screens.pengaturan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The business-account form, as a Material3 `ModalBottomSheet`.
 *
 * ## Why a sheet and not a `KnModal`
 *
 * Two fields plus a keyboard is still the "many inputs or a tall keyboard" case the epic
 * context names: on a tablet a centred dialog covers the input the owner opened it to reach.
 * A sheet grows from the bottom edge, so the fields stay above the keyboard area and
 * reachable at every height. `VehicleFormSheet` and `RenterFormSheet` make the same choice.
 *
 * ## Why there is no numpad
 *
 * `KnNumPad` exists for **money** — the epic context requires nominal entry on the numpad
 * because money is typed standing up while holding something. An account number is not an
 * amount: it is an identifier the owner copies from a statement or pastes from a banking app,
 * and a numpad would make pasting impossible while making the digits no easier to read. So
 * the field takes the system keyboard, exactly as the renter's phone number does.
 *
 * ## Why there is no delete control
 *
 * Story 3.1's acceptance criterion says an account a deposit uses cannot be deleted, enforced
 * in the data layer. The table that would make an account "in use" is Story 3.2's and does
 * not exist, so a delete here would be a control whose guard is vacuously true — it would
 * refuse nothing while looking complete. `BusinessAccountDao`'s KDoc has the full argument.
 * The control and the refusal land together, in 3.2.
 *
 * ## Why the title differs between add and edit
 *
 * An edit that kept the "Tambah rekening" heading would read as a create, and the owner would
 * reasonably expect a second account rather than a changed one. `state.isEditing` picks the
 * word; there is no second code path.
 *
 * @param state the form's own state, held by the ViewModel so a rotation cannot lose
 *   half-typed input.
 * @param errorMessage the last failure with no field of its own, drawn inside the sheet
 *   because the screen's banner is in a different window.
 * @param onDismissRequest the sheet's scrim tap and back gesture. Both are wired: a sheet
 *   with no way out except its own buttons is a trap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormSheet(
    state: AccountFormState,
    onDismissRequest: () -> Unit,
    onChange: ((AccountFormState) -> AccountFormState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    successMessage: String? = null,
    onDismissSuccess: () -> Unit = {},
) {
    val spacing = MaterialTheme.knSpacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        // **No `heightIn` here** — capping the sheet's own modifier sizes its window to
        // the cap and leaves it floating above the bottom edge. The cap belongs on the
        // content column, which wraps up to it.
        modifier = modifier.testTag(TAG_ACCOUNT_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = AccountSheetMaxHeight)
                // The keyboard. djustResize shrinks the window; this keeps the sheet's action
                // row above the IME rather than under it.
                .imePadding(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(
                    if (state.isEditing) {
                        R.string.pengaturan_edit_title
                    } else {
                        R.string.pengaturan_add_title
                    },
                ),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_ACCOUNT_TITLE),
            )

            KnField(
                label = stringResource(R.string.pengaturan_field_bank),
                value = state.bankName,
                // The error clears on the next keystroke, not before: an error that vanished
                // the moment the owner started fixing it would leave them unsure whether it
                // was resolved. `setBankName` is the pure helper that does it, so the rule
                // is testable without Compose.
                onValueChange = { value -> onChange { it.setBankName(value) } },
                hint = stringResource(R.string.pengaturan_bank_hint),
                error = state.bankError,
                modifier = Modifier.testTag(TAG_FIELD_BANK),
            )

            KnField(
                label = stringResource(R.string.pengaturan_field_number),
                value = state.number,
                onValueChange = { value -> onChange { it.setNumber(value) } },
                hint = stringResource(R.string.pengaturan_number_hint),
                error = state.numberError,
                // `Number` rather than `Phone`: the value is digits, and the numeric keypad is
                // the one that puts them on the first layer. `Phone` would offer `+`, `*` and
                // `#`, none of which an account number can contain.
                keyboardType = KeyboardType.Number,
                modifier = Modifier.testTag(TAG_FIELD_NUMBER),
            )

            // The confirmation for a write that landed, drawn *inside* the sheet for the same
            // window reason as the failure below. Above it, so the two are never on the same
            // pixels: the failure is the one that has to be read.
            successMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Success,
                    onDismiss = onDismissSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_ACCOUNT_SUCCESS),
                )
            }

            // The failure that has no field of its own — a write this screen does not model.
            // The two typed refusals both land on a field, so this is the only banner case.
            errorMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Danger,
                    onDismiss = onDismissError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_ACCOUNT_ERROR),
                )
            }

            } // end of the scrolling column

            // Pinned outside it: inside, the buttons scrolled with the form and sat below
            // the cap.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.End),
                maxItemsInEachRow = 2,
            ) {
                KnButton(
                    label = stringResource(R.string.pengaturan_cancel),
                    onClick = onDismissRequest,
                    variant = KnButtonVariant.Ghost,
                    modifier = Modifier.testTag(TAG_ACCOUNT_CANCEL),
                )
                KnButton(
                    label = stringResource(R.string.pengaturan_save),
                    onClick = onSave,
                    variant = KnButtonVariant.Primary,
                    // `canSaveAccount` is completeness only — the number's *format* is not part
                    // of it, deliberately. A malformed number leaves Save enabled and produces a
                    // refusal naming the format, which is actionable; a permanently disabled
                    // button beside a filled field would leave the owner with no explanation.
                    enabled = state.canSaveAccount,
                    modifier = Modifier.testTag(TAG_ACCOUNT_SAVE),
                )
            }
        }
        }
    }

/**
 * A bound on the sheet's content.
 *
 * `ModalBottomSheet` already constrains itself to the window; this stops two fields plus two
 * buttons from growing past a short landscape tablet. The sheet's own scroll container handles
 * the rest. The figure is smaller than the renter form's because this form has half its fields.
 */
private val AccountSheetMaxHeight = 520.dp

internal const val TAG_ACCOUNT_SHEET = "pengaturan-account-sheet"
internal const val TAG_ACCOUNT_TITLE = "pengaturan-account-title"
internal const val TAG_FIELD_BANK = "pengaturan-field-bank"
internal const val TAG_FIELD_NUMBER = "pengaturan-field-number"
internal const val TAG_ACCOUNT_SAVE = "pengaturan-account-save"
internal const val TAG_ACCOUNT_CANCEL = "pengaturan-account-cancel"
internal const val TAG_ACCOUNT_ERROR = "pengaturan-account-error"
internal const val TAG_ACCOUNT_SUCCESS = "pengaturan-account-success"
