package net.klinik.rental.ui.lock

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
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The recovery flow, one step per window.
 *
 * ## Why three steps and not one form
 *
 * Because the AC's order is fixed: the code is checked, then the new PIN is chosen, then a **new**
 * code is displayed and confirmed. A single form would let an owner type a new PIN before
 * discovering their code was wrong — and the code check is the only thing authorising the change.
 *
 * ## Why the issued code is selectable and in a monospace font
 *
 * Because it is 20 characters of mixed case and digits, and the owner is going to copy it into a
 * password manager or write it on paper. `SelectionContainer` makes it copyable; the monospace
 * face makes `I` and `l` distinguishable at the size it is rendered. Both are small things that
 * decide whether the escape hatch survives being written down.
 *
 * ## Why the confirmation is a checkbox-like acknowledgement rather than a plain "Selesai"
 *
 * Because the AC says the flow is not finished until the owner confirms they wrote it down, and a
 * button labelled "Selesai" confirms nothing. The button's label states the promise it is asking
 * for, and it is the only way out of this step.
 */
@Composable
fun RecoveryDialog(
    state: RecoveryUi,
    onCodeChange: (String) -> Unit,
    onNewPinChange: (String) -> Unit,
    onSubmitCode: () -> Unit,
    onSubmitNewPin: () -> Unit,
    onConfirmWritten: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    // The dismiss label is absent on the final step: the code is shown once, and a "back" that
    // discarded it would leave the owner with a PIN they can forget and no way out. The confirm
    // is the only exit, which is exactly what the AC asks for.
    val dismissLabel = when (state.step) {
        RecoveryStep.ShowNewCode -> null
        else -> stringResource(R.string.pin_recovery_cancel)
    }

    KnModal(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(
            when (state.step) {
                RecoveryStep.EnterCode -> R.string.pin_recovery_title_code
                RecoveryStep.NewPin -> R.string.pin_recovery_title_new_pin
                RecoveryStep.ShowNewCode -> R.string.pin_recovery_title_new_code
            },
        ),
        confirmLabel = when (state.step) {
            RecoveryStep.EnterCode -> stringResource(R.string.pin_recovery_check)
            RecoveryStep.NewPin -> stringResource(R.string.pin_recovery_set_pin)
            RecoveryStep.ShowNewCode -> stringResource(R.string.pin_recovery_written)
        },
        onConfirm = when (state.step) {
            RecoveryStep.EnterCode -> onSubmitCode
            RecoveryStep.NewPin -> onSubmitNewPin
            RecoveryStep.ShowNewCode -> onConfirmWritten
        },
        dismissLabel = dismissLabel,
        onDismiss = if (dismissLabel == null) null else onDismiss,
        modifier = modifier.testTag(TAG_RECOVERY_DIALOG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            when (state.step) {
                RecoveryStep.EnterCode -> {
                    Text(
                        text = stringResource(R.string.pin_recovery_code_body),
                        style = MaterialTheme.knTypography.body,
                        color = MaterialTheme.knColors.textSecondary,
                    )
                    KnField(
                        label = stringResource(R.string.pin_recovery_code_field),
                        value = state.code,
                        onValueChange = onCodeChange,
                        error = state.errorMessage,
                        placeholder = stringResource(R.string.pin_recovery_code_hint),
                        modifier = Modifier.testTag(TAG_RECOVERY_CODE_FIELD),
                    )
                }

                RecoveryStep.NewPin -> {
                    Text(
                        text = stringResource(R.string.pin_recovery_new_pin_body),
                        style = MaterialTheme.knTypography.body,
                        color = MaterialTheme.knColors.textSecondary,
                    )
                    KnField(
                        label = stringResource(R.string.pin_new_field),
                        value = state.newPin,
                        onValueChange = onNewPinChange,
                        error = state.errorMessage,
                        placeholder = stringResource(R.string.pin_new_hint),
                        modifier = Modifier.testTag(TAG_RECOVERY_NEW_PIN_FIELD),
                    )
                }

                RecoveryStep.ShowNewCode -> {
                    Text(
                        text = stringResource(R.string.pin_recovery_new_code_body),
                        style = MaterialTheme.knTypography.body,
                        color = MaterialTheme.knColors.textSecondary,
                    )
                    SelectionContainer {
                        Text(
                            text = state.issuedCode.orEmpty(),
                            style = MaterialTheme.knTypography.title,
                            // Monospace so `I` and `l` are distinguishable at this size — the code
                            // is written down once, by hand or by copy.
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.knColors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TAG_RECOVERY_ISSUED_CODE),
                        )
                    }
                    // The same warning the setup flow carries, for the same reason: the code
                    // cannot be shown again, and the owner is the only copy.
                    KnBadge(
                        text = stringResource(R.string.pin_recovery_code_once),
                        tone = KnBadgeTone.Danger,
                    )
                }
            }
        }
    }
}

/**
 * The one-time display during **setup**, before the PIN is enabled.
 *
 * A separate composable from [RecoveryDialog]'s final step because the two are reached from
 * different places and have different exits: this one's confirm is what *writes* the lock, while
 * the recovery flow's confirm only closes a flow whose write already happened.
 */
@Composable
fun NewRecoveryCodeDialog(
    code: String,
    onConfirmWritten: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnModal(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.pin_setup_code_title),
        confirmLabel = stringResource(R.string.pin_recovery_written),
        onConfirm = onConfirmWritten,
        dismissLabel = stringResource(R.string.pin_recovery_cancel),
        onDismiss = onDismiss,
        modifier = modifier.testTag(TAG_SETUP_CODE_DIALOG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.pin_setup_code_body),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
            )
            SelectionContainer {
                Text(
                    text = code,
                    style = MaterialTheme.knTypography.title,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.knColors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_SETUP_ISSUED_CODE),
                )
            }
            KnBadge(
                text = stringResource(R.string.pin_recovery_code_once),
                tone = KnBadgeTone.Danger,
            )
        }
    }
}

/** A plain "PIN did not match" dialog for the setup flow's two entries. */
@Composable
fun PinMismatchDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnModal(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.pin_setup_mismatch_title),
        confirmLabel = stringResource(R.string.pin_setup_mismatch_retry),
        onConfirm = onDismiss,
        modifier = modifier.testTag(TAG_MISMATCH_DIALOG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.pin_setup_mismatch_body),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
            )
            KnButton(
                label = stringResource(R.string.pin_setup_mismatch_cancel),
                onClick = onDismiss,
                variant = KnButtonVariant.Ghost,
            )
        }
    }
}

internal const val TAG_RECOVERY_DIALOG = "pin-recovery-dialog"
internal const val TAG_RECOVERY_CODE_FIELD = "pin-recovery-code-field"
internal const val TAG_RECOVERY_NEW_PIN_FIELD = "pin-recovery-new-pin-field"
internal const val TAG_RECOVERY_ISSUED_CODE = "pin-recovery-issued-code"
internal const val TAG_SETUP_CODE_DIALOG = "pin-setup-code-dialog"
internal const val TAG_SETUP_ISSUED_CODE = "pin-setup-issued-code"
internal const val TAG_MISMATCH_DIALOG = "pin-setup-mismatch-dialog"