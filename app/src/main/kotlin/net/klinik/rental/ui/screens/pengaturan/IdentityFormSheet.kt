package net.klinik.rental.ui.screens.pengaturan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
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
 * The business-identity form, as a Material3 `ModalBottomSheet`.
 *
 * ## Why a sheet and not a `KnModal`
 *
 * Three free-text fields and the system keyboard is the case `AccountFormSheet` names: on a
 * tablet a centred dialog covers the inputs the owner opened it to reach. A sheet grows from
 * the bottom edge, so the fields stay reachable at every height.
 *
 * ## Why there is no numpad, and why the phone field takes the phone keyboard
 *
 * `KnNumPad` exists for **money** — the epic context requires nominal entry on the numpad
 * because money is typed standing up while holding something. A phone number is not an amount:
 * it is something the owner copies from a card, a website or a banking app, and a numpad would
 * make pasting impossible while making the digits no easier to read. So `Phone` rather than
 * `Number`, because the one character `Number` refuses is the leading `+` — which is how half
 * the country's phone numbers are written down, and refusing it here would refuse a real number.
 *
 * ## Why the name is the only field with an error row
 *
 * Because it is the only field with a rule. The address and phone are free text the owner types
 * for outsiders, and `canSaveIdentity` does not gate on them — a business with no address is a
 * real business, not a half-filled form.
 *
 * @param state the form's own state, held by the ViewModel so a rotation cannot lose a
 *   half-typed business name.
 * @param errorMessage the last failure with no field of its own, drawn inside the sheet because
 *   the screen's banner is in a different window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityFormSheet(
    state: IdentityFormState,
    onDismissRequest: () -> Unit,
    onChange: ((IdentityFormState) -> IdentityFormState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The failure and confirmation, and their dismissals.
     *
     * **Required, not defaulted** — and that is not an accident of ordering. `SettleContractSheet`
     * in Story 3.6 shipped `onDismissError = {}` and `onDismissSuccess = {}`, so both of its
     * banners drew a close glyph that did nothing; the sheet test passed them through the
     * defaults, so the gap was never exercised. The same trap `ExceptionFormSheet`'s KDoc
     * forbids and `Screens.Pengaturan`'s own rule exists to close.
     */
    errorMessage: String?,
    onDismissError: () -> Unit,
    successMessage: String?,
    onDismissSuccess: () -> Unit,
) {
    val spacing = MaterialTheme.knSpacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
            .testTag(TAG_IDENTITY_SHEET)
            // The cap belongs on the **sheet**: on the content column it became the scroll
            // viewport, so the action row below it was laid out and never reachable.
            .heightIn(max = IdentitySheetMaxHeight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .heightIn(max = IdentitySheetMaxHeight),
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
                text = stringResource(R.string.pengaturan_identity_title),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_IDENTITY_TITLE),
            )

            KnField(
                label = stringResource(R.string.pengaturan_identity_field_name),
                value = state.name,
                // The error clears on the next keystroke, not before: an error that vanished
                // the moment the owner started fixing it would leave them unsure whether it was
                // resolved. `setIdentityName` is the pure helper that does it, so the rule is
                // testable without Compose.
                onValueChange = { value -> onChange { it.setIdentityName(value) } },
                hint = stringResource(R.string.pengaturan_identity_name_hint),
                error = state.nameError,
                modifier = Modifier.testTag(TAG_IDENTITY_FIELD_NAME),
            )

            KnField(
                label = stringResource(R.string.pengaturan_identity_field_address),
                value = state.address,
                onValueChange = { value -> onChange { it.setIdentityAddress(value) } },
                hint = stringResource(R.string.pengaturan_identity_address_hint),
                // No `error` slot, and deliberately so: `IdentityFormState` has no address
                // error because there is no rule to refuse one with. An always-null error
                // parameter would be a claim that this field can fail when it cannot.
                modifier = Modifier.testTag(TAG_IDENTITY_FIELD_ADDRESS),
            )

            KnField(
                label = stringResource(R.string.pengaturan_identity_field_phone),
                value = state.phone,
                onValueChange = { value -> onChange { it.setIdentityPhone(value) } },
                hint = stringResource(R.string.pengaturan_identity_phone_hint),
                // `Phone` rather than `Number`: `Number` refuses the leading `+`, and a written
                // phone number is exactly where the owner reaches for it.
                keyboardType = KeyboardType.Phone,
                modifier = Modifier.testTag(TAG_IDENTITY_FIELD_PHONE),
            )

            // Both messages into the sheet's own window, and the failure above the success:
            // two `BottomCenter` bars drawn independently stack on the same pixels, and the
            // failure is the one that has to be read.
            successMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Success,
                    onDismiss = onDismissSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_IDENTITY_SUCCESS),
                )
            }

            errorMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Danger,
                    onDismiss = onDismissError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_IDENTITY_ERROR),
                )
            }

            } // end of the scrolling column

            // Pinned outside it: as its last child the buttons scrolled with the form and
            // sat below the cap.
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
                    modifier = Modifier.testTag(TAG_IDENTITY_CANCEL),
                )
                KnButton(
                    label = stringResource(R.string.pengaturan_save),
                    onClick = onSave,
                    variant = KnButtonVariant.Primary,
                    // The name alone — see `canSaveIdentity` on why the address and phone are
                    // not part of it.
                    enabled = state.canSaveIdentity,
                    modifier = Modifier.testTag(TAG_IDENTITY_SAVE),
                )
            }
        }
        }
    }

/**
 * A bound on the sheet's content.
 *
 * Larger than `AccountFormSheet`'s 520dp because this form has three fields to its two, and
 * the third is a phone number that reaches for a taller keyboard.
 */
private val IdentitySheetMaxHeight = 620.dp

internal const val TAG_IDENTITY_SHEET = "pengaturan-identity-sheet"
internal const val TAG_IDENTITY_TITLE = "pengaturan-identity-title"
internal const val TAG_IDENTITY_FIELD_NAME = "pengaturan-identity-field-name"
internal const val TAG_IDENTITY_FIELD_ADDRESS = "pengaturan-identity-field-address"
internal const val TAG_IDENTITY_FIELD_PHONE = "pengaturan-identity-field-phone"
internal const val TAG_IDENTITY_SAVE = "pengaturan-identity-save"
internal const val TAG_IDENTITY_CANCEL = "pengaturan-identity-cancel"
internal const val TAG_IDENTITY_ERROR = "pengaturan-identity-error"
internal const val TAG_IDENTITY_SUCCESS = "pengaturan-identity-success"
