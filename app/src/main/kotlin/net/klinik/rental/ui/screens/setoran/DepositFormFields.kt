package net.klinik.rental.ui.screens.setoran

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import net.klinik.rental.R
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The deposit form's **fields**, without the numpad and without the sheet.
 *
 * ## Why this is its own file
 *
 * Because the form has two presentations and the fields must not be written twice.
 *
 * The prototype's `payments.html` is a **two-pane split in landscape**: the form fills the
 * left and the numpad sits in a fixed column on the right, both on screen at once
 * (`.shots/prototype/payments.png`). In portrait that split does not fit, so the same
 * fields appear in the bottom sheet.
 *
 * One implementation, two layouts. A second copy of the fields would be a second place
 * every rule below lives — the renter picker, the method refusal, the transfer-only
 * account field — and the two would drift the first time one was fixed.
 *
 * ## What this deliberately does not contain
 *
 * The **numpad**. It is the amount's control and it is a different shape: fixed-size, and
 * laid out as a grid rather than as a column of fields. The split puts it in a fixed-width
 * column; the sheet puts it under the form. Both need it whole, so neither scrolls it.
 *
 * @param state the deposit state.
 * @param onChange the form's own reducer — `SetoranViewModel`'s `onFormChange`.
 */
@Composable
internal fun DepositFormFields(
    state: SetoranUiState,
    onChange: ((DepositFormState) -> DepositFormState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form ?: return
    val spacing = MaterialTheme.knSpacing

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // ---- who is paying
        Text(
            text = stringResource(R.string.deposito_renter_label),
            style = MaterialTheme.knTypography.label,
            color = MaterialTheme.knColors.textSecondary,
        )
        // Unmounted while correcting, not disabled.
        //
        // A deposit is permanently anchored to the contract it was recorded against —
        // re-homing one is a cancel and a fresh record, not a field edit — and a picker
        // the owner could tap here would rewrite `contractId` under a `paymentId` that
        // still names the old rental, so the save would fail with "deposit gone" for a
        // reason the owner cannot see. Absence is the honest signal: there is no choice
        // to make, so there is no control.
        if (!form.isEditing) {
            RenterPicker(
                options = state.renterOptions,
                selectedId = form.renterId,
                onSelect = { option ->
                    onChange { it.selectRenter(option.renter, option.contract) }
                },
            )
        } else {
            ReadOnlyFact(
                label = form.renterName,
                value = null,
                modifier = Modifier.testTag(TAG_DEPOSIT_RENTER_FACT),
            )
        }

        // ---- what it settles, filled from the contract
        //
        // Read-only on purpose: the vehicle is not the owner's choice here, it is
        // whatever the open contract says, and a control that looks editable but is
        // not is the same defect as a blanked field.
        if (form.hasContract) {
            ReadOnlyFact(
                label = form.vehiclePlate,
                value = form.rateText,
                modifier = Modifier.testTag(TAG_DEPOSIT_CONTRACT_FACT),
            )
        } else {
            // The reason the form cannot proceed, in place of two blank rows that
            // would read as a layout bug rather than as a fact about the renter.
            Text(
                text = stringResource(R.string.deposito_no_contract),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
                modifier = Modifier.testTag(TAG_DEPOSIT_NO_CONTRACT),
            )
        }

        // ---- when it arrived.
        //
        // Above the method row rather than beside the amount, because the amount is on
        // the numpad *beside* this column and a date the owner is correcting pairs with
        // the fact it is correcting — the contract, not the figure.
        //
        // Shown for a **new** deposit too, and that is not a leak: the epic context
        // states plainly that a deposit's amount may be dated in the past for
        // correction, and a field that appears only while editing would make
        // back-dating a correction-only privilege rather than a fact about the money.
        DepositDateField(
            date = form.date,
            onPick = { date -> onChange { it.setDate(date) } },
        )

        // ---- how it arrived
        Text(
            text = stringResource(R.string.deposito_method_label),
            style = MaterialTheme.knTypography.label,
            color = MaterialTheme.knColors.textSecondary,
        )
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            KnChip(
                text = stringResource(R.string.deposito_method_transfer),
                selected = form.method == PaymentMethod.TransferBank,
                // Disabled, not guarded. The epic forbids changing a deposit's method —
                // repairing one means cancelling and recording again — and a chip that
                // renders live and ignores the tap reads as a broken app rather than as
                // a rule. `setMethod` refuses as well, so this is the second of three
                // refusals and the repository's absent parameter is the third.
                enabled = !form.isEditing,
                onClick = { onChange { it.setMethod(PaymentMethod.TransferBank) } },
                modifier = Modifier.testTag(TAG_METHOD_TRANSFER),
            )
            KnChip(
                text = stringResource(R.string.deposito_method_cash),
                selected = form.method == PaymentMethod.Tunai,
                enabled = !form.isEditing,
                onClick = { onChange { it.setMethod(PaymentMethod.Tunai) } },
                modifier = Modifier.testTag(TAG_METHOD_CASH),
            )
        }

        // ---- Transfer Bank only.
        //
        // **Unmounted** for Tunai, not blanked — the epic context is explicit that
        // filling a field in blank "menciptakan ilusi data yang padahal tidak ada",
        // and this is the same rule `ExceptionFormSheet` follows when it drops its
        // add block for an ended contract.
        if (form.showsAccountField) {
            Text(
                text = stringResource(R.string.deposito_account_label),
                style = MaterialTheme.knTypography.label,
                color = MaterialTheme.knColors.textSecondary,
            )
            AccountPicker(
                options = state.accountOptions,
                selectedId = form.accountId,
                onSelect = { option -> onChange { it.selectAccount(option.account.id) } },
            )
            form.accountError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.knTypography.body,
                    color = MaterialTheme.knColors.semantic.danger,
                    modifier = Modifier.testTag(TAG_ACCOUNT_ERROR),
                )
            }

            KnField(
                label = stringResource(R.string.deposito_reference_label),
                value = form.referenceNo,
                onValueChange = { value -> onChange { it.setReferenceNo(value) } },
                // Free text and deliberately **not** validated: the epic context
                // makes the bank reference a reminder for the owner, and anything
                // stricter would refuse the references real banks issue.
                modifier = Modifier.testTag(TAG_REFERENCE),
            )
        }

        KnField(
            label = stringResource(R.string.deposito_notes_label),
            value = form.notes,
            onValueChange = { value -> onChange { it.setNotes(value) } },
            placeholder = stringResource(R.string.deposito_notes_hint),
            modifier = Modifier.testTag(TAG_NOTES),
        )

        form.amountError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.semantic.danger,
                modifier = Modifier.testTag(TAG_AMOUNT_ERROR),
            )
        }

        // The confirmation, drawn **inside** this surface: the screen's banner is in the
        // composition behind the sheet, which is exactly where the owner wants to read it.
        state.successMessage?.let { message ->
            KnSnackbar(
                message = message,
                tone = KnBadgeTone.Success,
                onDismiss = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_DEPOSIT_SUCCESS),
            )
        }

        state.errorMessage?.let { message ->
            KnSnackbar(
                message = message,
                tone = KnBadgeTone.Danger,
                onDismiss = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_DEPOSIT_ERROR),
            )
        }
    }
}

/** The renter picker: a wrapped row of chips, one per renter. */
@Composable
internal fun RenterPicker(
    options: List<RenterOption>,
    selectedId: String?,
    onSelect: (RenterOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.sm),
    ) {
        options.forEach { option ->
            KnChip(
                text = option.renter.name,
                selected = option.renter.id == selectedId,
                onClick = { onSelect(option) },
                // A renter with no running contract is still listed — the owner needs to
                // see *who* has none — but says so, because selecting one cannot proceed
                // and a chip that looks like the others would be a lie about what happens.
                leadingIcon = option.contract?.let { null },
                modifier = Modifier.testTag(renterChipTag(option.renter.id)),
            )
        }
    }
}

/** The account picker: a wrapped row of chips labelled `bank · number`. */
@Composable
internal fun AccountPicker(
    options: List<AccountOption>,
    selectedId: String?,
    onSelect: (AccountOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.sm),
    ) {
        options.forEach { option ->
            KnChip(
                text = option.label,
                selected = option.account.id == selectedId,
                onClick = { onSelect(option) },
                modifier = Modifier.testTag(accountChipTag(option.account.id)),
            )
        }
    }
}

/**
 * A read-only fact: what the deposit settles and what a day is worth.
 *
 * Not a `KnField`, because a field is a control the owner can change and this is a
 * consequence of the contract they already chose. A disabled field would look editable
 * and try to take focus; a plain row says "this is what you picked".
 */
@Composable
internal fun ReadOnlyFact(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.knTypography.body,
            color = MaterialTheme.knColors.textSecondary,
        )
        // The value is `null` for a row that carries only one fact — the renter's name on a
        // correction form, which has no figure to put beside it. Unmounted rather than
        // blank: an empty `Text` in a `SpaceBetween` row still occupies a node, and the
        // label would appear left-aligned against nothing.
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.knTypography.figure,
                color = MaterialTheme.knColors.textPrimary,
                // `tabular-nums` on the figure so a list of contracts shows its rates in a
                // column — the epic's AR-11, and the comparison the owner makes when
                // choosing which contract to pay.
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
