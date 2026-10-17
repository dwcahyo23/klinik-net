package net.klinik.rental.ui.screens.setoran

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * Story 3.4's cancellation dialog: what is being undone, what it costs, and why.
 *
 * ## Why the reason field is **inside** the dialog and not on the sheet
 *
 * Because the reason is only ever read next to the impact. Writing it on the deposit
 * sheet would ask the owner to supply an explanation for something they have not yet been
 * shown the cost of, and the field would sit there long after the decision was made —
 * at which point it reads as a note rather than as the justification the audit needs.
 *
 * ## Why this is the highest confirmation tier the app has
 *
 * UX-DR11's third level — *"aksi yang menghapus data → dialog + alasan wajib"* — and the
 * only one with a mandatory field. The two below it are used by Story 2.7's contract end
 * and Story 2.6's exception removal, neither of which asks for writing. This one does,
 * because what is being destroyed is evidence.
 *
 * @param state the dialog's own state, held by the ViewModel so a rotation cannot lose a
 *   half-typed reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancelDepositDialog(
    state: CancelDepositUi,
    onChange: (CancelDepositUi) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnModal(
        // Driven by a non-null state rather than a `visible` flag, so the window cannot
        // be up with nothing in it — the invisible tap-blocker `KnModal` unmounts to
        // avoid.
        visible = true,
        onDismissRequest = onDismiss,
        title = state.title,
        // "Batalkan setoran", not "Batal" — the confirm and the dismiss differ by one
        // word and the owner is destroying an audit record, so the confirm spells out
        // which of the two buttons they are reaching for.
        confirmLabel = stringResource(R.string.deposito_cancel_confirm),
        onConfirm = onConfirm,
        // The AC's "alasan wajib diisi" on the control itself. The ViewModel refuses a
        // blank reason too, so this is the reflection of the rule rather than its only
        // enforcement — a disabled button beside a mandatory field reads as a rule, where a
        // button that does nothing reads as an app that stopped working.
        confirmEnabled = state.canCancel,
        dismissLabel = stringResource(R.string.deposito_cancel_dismiss),
        onDismiss = onDismiss,
        modifier = modifier.testTag(TAG_CANCEL_DIALOG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // The impact, before the field: the owner decides *whether* to cancel, and the
            // reason is the justification for a decision already taken.
            Text(
                text = state.impactLine,
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_CANCEL_IMPACT),
            )

            KnField(
                label = stringResource(R.string.deposito_cancel_reason_label),
                value = state.reason,
                // The error clears on the next keystroke rather than on confirm: an
                // error that vanished only after a failed press teaches the owner that
                // the field is fighting them.
                onValueChange = { value -> onChange(state.setReason(value)) },
                placeholder = stringResource(R.string.deposito_cancel_reason_hint),
                error = state.reasonError,
                // Multi-line with a bound, for the reason the deposit sheet's reason field
                // is: "the tenant's cousin handed it over, not the tenant" is one sentence
                // that does not fit a single line, and an unbounded field inside a dialog
                // grows the dialog without limit.
                singleLine = false,
                maxLines = CANCEL_REASON_MAX_LINES,
                modifier = Modifier
                    .padding(top = spacing.xs)
                    .testTag(TAG_CANCEL_REASON),
            )
        }
    }
}

/**
 * How many lines the reason grows to before it scrolls.
 *
 * Three, matching the deposit sheet's reason field — the same writing task in the same
 * app, and two different bounds for it would be one more thing to keep in step.
 */
private const val CANCEL_REASON_MAX_LINES = 3

internal const val TAG_CANCEL_DIALOG = "deposito-cancel-dialog"
internal const val TAG_CANCEL_IMPACT = "deposito-cancel-impact"
internal const val TAG_CANCEL_REASON = "deposito-cancel-reason"