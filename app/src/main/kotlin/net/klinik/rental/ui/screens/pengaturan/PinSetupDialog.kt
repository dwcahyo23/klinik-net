package net.klinik.rental.ui.screens.pengaturan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.components.KnPinGrid
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * Story 5.3's PIN setup: choose, confirm, then the recovery code shown exactly once.
 *
 * ## Why the PIN is entered twice
 *
 * Because a mistyped PIN on a lock with no way in is unrecoverable, and the recovery code is the
 * only route back. Two entries cost seconds; a wrong PIN costs the records.
 *
 * ## Why the third step has **no dismiss**
 *
 * Because the AC says the flow is not finished until the owner confirms they wrote the code down.
 * A "Batal" here would be a button that discards the only copy of the escape hatch after the
 * owner has already seen it — and the code cannot be shown again. The confirm is the only exit,
 * and it is what writes the lock.
 *
 * ## Why the digits never reach the state
 *
 * `PinSetupUi` carries `firstDigits`/`secondDigits` — counts, not values. A `UiState` is a
 * bundle-able object, and a PIN in one is a PIN in a saved-state dump.
 */
@Composable
fun PinSetupDialog(
    state: PinSetupUi,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onConfirmWritten: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnModal(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(
            when (state.step) {
                PinSetupStep.EnterPin -> R.string.pin_setup_title_enter
                PinSetupStep.ConfirmPin -> R.string.pin_setup_title_confirm
                PinSetupStep.ShowCode -> R.string.pin_setup_code_title
            },
        ),
        // The third step's only button is the confirmation; the first two have no confirm because
        // the grid submits at four digits, the same way the lock screen does.
        confirmLabel = if (state.step == PinSetupStep.ShowCode) {
            stringResource(R.string.pin_recovery_written)
        } else {
            null
        },
        onConfirm = if (state.step == PinSetupStep.ShowCode) onConfirmWritten else null,
        dismissLabel = if (state.step == PinSetupStep.ShowCode) {
            // Absent on the final step — see the KDoc. The code is shown once and this is the only
            // exit that keeps it.
            null
        } else {
            stringResource(R.string.pin_recovery_cancel)
        },
        onDismiss = if (state.step == PinSetupStep.ShowCode) null else onDismiss,
        modifier = modifier.testTag(TAG_PIN_SETUP_DIALOG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            when (state.step) {
                PinSetupStep.EnterPin, PinSetupStep.ConfirmPin -> {
                    Text(
                        text = stringResource(
                            if (state.step == PinSetupStep.EnterPin) {
                                R.string.pin_setup_body_enter
                            } else {
                                R.string.pin_setup_body_confirm
                            },
                        ),
                        style = MaterialTheme.knTypography.body,
                        color = MaterialTheme.knColors.textSecondary,
                    )
                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.knTypography.body,
                            color = MaterialTheme.knColors.errorText,
                            modifier = Modifier.testTag(TAG_PIN_SETUP_ERROR),
                        )
                    }
                    KnPinGrid(
                        enteredDigits = if (state.step == PinSetupStep.EnterPin) {
                            state.firstDigits
                        } else {
                            state.secondDigits
                        },
                        totalDigits = net.klinik.rental.data.repository.PinLockRepository.PIN_LENGTH,
                        onDigit = onDigit,
                        onDelete = onDelete,
                    )
                }

                PinSetupStep.ShowCode -> {
                    Text(
                        text = stringResource(R.string.pin_setup_code_body),
                        style = MaterialTheme.knTypography.body,
                        color = MaterialTheme.knColors.textSecondary,
                    )
                    SelectionContainer {
                        Text(
                            text = state.issuedCode.orEmpty(),
                            style = MaterialTheme.knTypography.title,
                            // Monospace so `I` and `l` are distinguishable at this size — the
                            // code is written down once, by hand or by copy.
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.knColors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TAG_PIN_SETUP_CODE),
                        )
                    }
                    KnBadge(
                        text = stringResource(R.string.pin_recovery_code_once),
                        tone = KnBadgeTone.Danger,
                    )
                }
            }
        }
    }
}

internal const val TAG_PIN_SETUP_DIALOG = "pengaturan-pin-setup-dialog"
internal const val TAG_PIN_SETUP_CODE = "pengaturan-pin-setup-code"
internal const val TAG_PIN_SETUP_ERROR = "pengaturan-pin-setup-error"