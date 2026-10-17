package net.klinik.rental.ui.screens.setoran

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.klinik.rental.R
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnNumPad
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

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
 * of fields and a 4x3 grid — taller than a landscape tablet. `heightIn` plus
 * `verticalScroll` keeps every part reachable instead of pushing the numpad's bottom row
 * off the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositSheet(
    state: SetoranUiState,
    onClose: () -> Unit,
    onChange: ((DepositFormState) -> DepositFormState) -> Unit,
    onSave: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    val form = state.form ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        modifier = modifier.testTag(TAG_DEPOSIT_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                // A bound, not a height: the content is short for a cash deposit with
                // two options and tall for a transfer with several accounts, and the
                // sheet takes what it needs up to the point where the grid would run off
                // a short screen.
                .heightIn(max = DepositSheetMaxHeight),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.deposito_title),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_DEPOSIT_TITLE),
            )

            // ---- who is paying
            Text(
                text = stringResource(R.string.deposito_renter_label),
                style = MaterialTheme.knTypography.label,
                color = MaterialTheme.knColors.textSecondary,
            )
            RenterPicker(
                options = state.renterOptions,
                selectedId = form.renterId,
                onSelect = { option ->
                    onChange { it.selectRenter(option.renter, option.contract) }
                },
            )

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
                // would read as a layout bug rather than a fact about the renter.
                Text(
                    text = stringResource(R.string.deposito_no_contract),
                    style = MaterialTheme.knTypography.body,
                    color = MaterialTheme.knColors.textSecondary,
                    modifier = Modifier.testTag(TAG_DEPOSIT_NO_CONTRACT),
                )
            }

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
                    onClick = { onChange { it.setMethod(PaymentMethod.TransferBank) } },
                    modifier = Modifier.testTag(TAG_METHOD_TRANSFER),
                )
                KnChip(
                    text = stringResource(R.string.deposito_method_cash),
                    selected = form.method == PaymentMethod.Tunai,
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

            // The confirmation, drawn **inside** this window: the screen's banner is in
            // the composition behind the sheet, which is exactly where the owner wants to
            // read it.
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

            // ---- the numpad.
            //
            // `onSave` goes straight to the handler, so the pad's own zero/negative
            // refusal — a red flash, which is an *event* and cannot be derived from the
            // digits — happens before the ViewModel is asked to write anything. That is
            // why the sheet does not pre-check the amount itself: the pad already does,
            // and a second check would produce two different answers for one keystroke.
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

/** The renter picker: a wrapped row of chips, one per renter. */
@Composable
private fun RenterPicker(
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
private fun AccountPicker(
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
private fun ReadOnlyFact(
    label: String,
    value: String,
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
        Text(
            text = value,
            style = MaterialTheme.knTypography.figure,
            color = MaterialTheme.knColors.textPrimary,
            // `tabular-nums` on the figure so a list of contracts shows its rates in a
            // column — the epic's AR-11, and the comparison the owner makes when
            // choosing which contract to pay.
            fontFamily = FontFamily.Monospace,
        )
    }
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
internal const val TAG_DEPOSIT_ERROR = "deposito-error"
internal const val TAG_DEPOSIT_SUCCESS = "deposito-success"