package net.klinik.rental.ui.screens.penyewa

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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.theme.knSpacing

/**
 * The renter form, as a Material3 `ModalBottomSheet`.
 *
 * ## Why a sheet and not a `KnModal`
 *
 * Four fields plus a tall keyboard is the "many inputs or a tall keyboard" case
 * the epic context names: on a tablet a centred dialog covers the input the
 * owner opened it to reach. A sheet grows from the bottom edge, so the fields
 * stay above the keyboard area and reachable at every height.
 *
 * `ModalBottomSheet` is already on the classpath, so this uses the native
 * component rather than adding a second sheet to the design system — the design
 * system has a *modal* and this is deliberately not one. `VehicleFormSheet` makes
 * the same choice.
 *
 * ## Why there is no numpad
 *
 * The vehicle form's numpad exists for the daily rate: the epic context requires
 * nominal entry on the numpad, and money is the one field typed standing up.
 * There is no money on this form — the phone number and the account number are
 * strings, not amounts — so a numpad would be a control with no amount to
 * enter. `KnNumPad` is deliberately absent.
 *
 * ## Why there is no checkbox and no KTP field
 *
 * No checkbox: a boolean toggle would be an invented field. No KTP or address:
 * PRD §6.1 puts both out of scope, and a field that is merely hidden is one
 * refactor away from collecting personal data the app has no reason to hold.
 * There are exactly four fields, and the AC says exactly four.
 *
 * @param state the form's own state, held by the ViewModel so a rotation cannot
 *   lose half-typed input.
 * @param onDismissRequest the sheet's scrim tap and back gesture. Both are wired:
 *   a sheet with no way out except its own buttons is a trap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenterFormSheet(
    state: RenterFormState,
    onDismissRequest: () -> Unit,
    onChange: ((RenterFormState) -> RenterFormState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    // Half the screen rather than a fixed dp, and scrollable: four fields plus a
    // keyboard is taller than a landscape tablet. `skipPartiallyExpanded` so a
    // second tap on the add button does not find the sheet at half height.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        // **No `heightIn` here** — capping the sheet's own modifier sizes its window to
        // the cap and leaves it floating above the bottom edge. The cap belongs on the
        // content column, which wraps up to it.
        modifier = modifier.testTag(TAG_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SheetMaxHeight)
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
            KnField(
                label = stringResource(R.string.penyewa_field_name),
                value = state.name,
                onValueChange = { value ->
                    // The error clears on the next keystroke, not before: an
                    // error that vanished the moment the owner started fixing it
                    // would leave them unsure whether it was resolved.
                    onChange { it.copy(name = value, nameError = null) }
                },
                hint = stringResource(R.string.penyewa_name_hint),
                error = state.nameError,
                modifier = Modifier.testTag(TAG_FIELD_NAME),
            )

            // Phone and emergency contact on one wrapped row where the width
            // allows: they are two short values the owner reads as a pair, and
            // stacking them would push Save below the fold on a short screen.
            // `FlowRow` so a narrow phone wraps rather than squeezing two fields
            // into unreadable columns.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                maxItemsInEachRow = 2,
            ) {
                KnField(
                    label = stringResource(R.string.penyewa_field_phone),
                    value = state.phone,
                    onValueChange = { value ->
                        onChange { it.copy(phone = value, phoneError = null) }
                    },
                    // A phone keyboard, not `Number`/`Decimal`: the field is a
                    // number the owner dials, so `KeyboardType.Phone` is the
                    // keypad that has the digits on it.
                    keyboardType = KeyboardType.Phone,
                    // The refusal lands on the field that caused it, and it is
                    // cleared as soon as the owner types again — `KnField` hides
                    // a null error entirely, so a message that stayed would sit
                    // under a field the owner has already fixed.
                    error = state.phoneError,
                    modifier = Modifier
                        .testTag(TAG_FIELD_PHONE)
                        .weight(1f),
                )
                KnField(
                    label = stringResource(R.string.penyewa_field_emergency),
                    value = state.emergencyContact,
                    onValueChange = { value ->
                        onChange { it.copy(emergencyContact = value, emergencyError = null) }
                    },
                    keyboardType = KeyboardType.Phone,
                    error = state.emergencyError,
                    modifier = Modifier
                        .testTag(TAG_FIELD_EMERGENCY)
                        .weight(1f),
                )
            }

            // The one optional field. Labelled "(opsional)" in the resource
            // rather than inferred from an empty field — a field with no
            // optionality marker reads as required, and plenty of renters are
            // paid in cash.
            KnField(
                label = stringResource(R.string.penyewa_field_bank_account),
                value = state.bankAccount,
                onValueChange = { value -> onChange { it.copy(bankAccount = value) } },
                hint = stringResource(R.string.penyewa_bank_account_hint),
                modifier = Modifier.testTag(TAG_FIELD_BANK_ACCOUNT),
            )

            AvatarPicker(
                selected = state.avatar,
                onSelect = { key -> onChange { it.copy(avatar = key) } },
            )
            } // end of the scrolling column

            // Pinned **outside** the scroll container: as its last child the buttons
            // scrolled away with the form and sat below the cap.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.End),
                maxItemsInEachRow = 2,
            ) {
                KnButton(
                    label = stringResource(R.string.penyewa_cancel),
                    onClick = onDismissRequest,
                    variant = KnButtonVariant.Ghost,
                    modifier = Modifier.testTag(TAG_CANCEL),
                )
                KnButton(
                    label = stringResource(R.string.penyewa_save),
                    onClick = onSave,
                    variant = KnButtonVariant.Primary,
                    modifier = Modifier.testTag(TAG_SAVE),
                )
            }
        }
    }
}

/**
 * A bound on the sheet's content.
 *
 * `ModalBottomSheet` already constrains itself to the window; this stops four
 * fields plus two buttons from growing past a short landscape tablet and pushing
 * the Save button off the top. The sheet's own scroll container handles the rest.
 */
private val SheetMaxHeight = 720.dp

internal const val TAG_SHEET = "renter-form-sheet"
internal const val TAG_FIELD_NAME = "renter-field-name"
internal const val TAG_FIELD_PHONE = "renter-field-phone"
internal const val TAG_FIELD_EMERGENCY = "renter-field-emergency"
internal const val TAG_FIELD_BANK_ACCOUNT = "renter-field-bank-account"
internal const val TAG_FIELD_AVATAR = "renter-field-avatar"
internal const val TAG_AVATAR_GRID = "renter-avatar-grid"
internal const val TAG_SAVE = "renter-save"
internal const val TAG_CANCEL = "renter-cancel"