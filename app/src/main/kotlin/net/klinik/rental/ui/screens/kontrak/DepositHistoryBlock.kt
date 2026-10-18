package net.klinik.rental.ui.screens.kontrak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.screens.setoran.DepositHistoryRowUi
import net.klinik.rental.ui.screens.setoran.DepositHistoryUi
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * One contract's deposit history, and the two verbs that mount Epic 3's finished features.
 *
 * ## Why this block exists at all
 *
 * Because `SetoranViewModel.requestCancel` and `openEditDeposit` were completed, tested,
 * and **unreachable**: both read the contract from the deposit sheet's own form state, so
 * cancelling or correcting a deposit was only possible from inside a form that opened
 * blank. Epic 3 recorded both as deferrals pointing at a history screen, and this is it.
 *
 * The honest measure of this surface is small: it is a list with two buttons per row, and
 * the work behind them was done two stories earlier.
 *
 * ## Why both verbs are **absent** on a cancelled row, not greyed out
 *
 * Because `correctDeposit` refuses a cancelled deposit with `DepositAlreadyCanceledException`
 * and `cancelDeposit` refuses a second cancellation. A disabled button still says the action
 * is available; the repository says otherwise. `ExceptionFormSheet`'s remove verb takes the
 * same position for the same reason — *"a greyed-out control is a claim the data layer will not
 * honour"* — and `isEditable` on the row is where the decision is made, so a screen cannot
 * offer a verb the repository would refuse.
 *
 * ## Why no gesture
 *
 * Because the epic forbids it by name: a swipe that cancels money is a gesture that can be
 * fired by accident, and a cancellation needs a written reason. Both verbs are visible buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositHistoryBlock(
    state: DepositHistoryUi,
    onRequestCancel: (contractId: String, renterName: String, paymentId: String) -> Unit,
    onRequestEdit: (paymentId: String) -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_DEPOSIT_HISTORY),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = stringResource(R.string.kontrak_history_title),
            style = MaterialTheme.knTypography.label,
            color = MaterialTheme.knColors.textSecondary,
        )

        // The failure that has no row of its own. A failed read drawn as "no deposits"
        // tells the owner this contract was never paid, which is the one claim this app
        // must never make without having looked.
        state.errorMessage?.let { message ->
            KnSnackbar(
                message = message,
                tone = KnBadgeTone.Danger,
                onDismiss = onErrorDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_DEPOSIT_HISTORY_ERROR),
            )
        }

        if (state.rows.isEmpty() && state.errorMessage == null) {
            // An **invitation**, not an absence: the criterion says the empty state explains,
            // and "no deposits" without saying what to do reads as a contract nobody paid.
            KnEmptyState(
                title = stringResource(R.string.kontrak_history_empty_title),
                body = stringResource(R.string.kontrak_history_empty_body),
                modifier = Modifier.testTag(TAG_DEPOSIT_HISTORY_EMPTY),
            )
        } else {
            state.rows.forEach { row ->
                // Keyed on the deposit's id rather than its position, for the reason
                // `ExceptionFormSheet` keys on the exception's: an unkeyed composition in
                // a loop is keyed by slot, so a row removed by a cancellation would migrate
                // the next row's content onto a different deposit.
                key(row.paymentId) {
                    DepositHistoryRowContent(
                        row = row,
                        onRequestCancel = {
                            onRequestCancel(state.contractId, state.renterName, row.paymentId)
                        },
                        onRequestEdit = { onRequestEdit(row.paymentId) },
                    )
                }
            }
        }
    }
}

/**
 * One deposit: what it was, and what can be done to it now.
 *
 * Two rows rather than one column so the amount and the date read together — they are
 * what the owner is scanning for — with the method and account beneath as the detail
 * that answers "how did it arrive".
 */
@Composable
private fun DepositHistoryRowContent(
    row: DepositHistoryRowUi,
    onRequestCancel: () -> Unit,
    onRequestEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs)
            .testTag(depositHistoryRowTag(row.paymentId)),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.amountText,
                style = MaterialTheme.knTypography.figure,
                color = MaterialTheme.knColors.textPrimary,
                // `tabular-nums` so a column of amounts aligns, the same reason the
                // contract row's rate carries it.
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = row.dateText,
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.methodText,
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
            )
            row.accountText?.let { account ->
                Text(
                    text = account,
                    style = MaterialTheme.knTypography.body,
                    color = MaterialTheme.knColors.textTertiary,
                )
            }
            if (!row.isEditable) {
                // Marked, not merely un-actionable: a row the owner cannot act on still has
                // to say *why*, and the reason is the owner's own words below.
                KnBadge(
                    text = stringResource(R.string.kontrak_history_cancelled_badge),
                    tone = KnBadgeTone.Danger,
                )
            }
        }

        row.referenceNo?.let { reference ->
            Text(
                text = stringResource(R.string.kontrak_history_reference, reference),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textTertiary,
            )
        }

        // The owner's reason for cancelling, in their own words. This is the column that
        // answers "why did Budi's arrears jump on the 17th" — the whole reason Story 3.4
        // chose a status flag over a delete.
        row.cancelReasonText?.let { reason ->
            Text(
                text = stringResource(R.string.kontrak_history_cancel_reason, reason),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textTertiary,
                modifier = Modifier.testTag(TAG_DEPOSIT_HISTORY_REASON),
            )
        }

        // **Unmounted** for a cancelled row rather than disabled — see the class KDoc.
        if (row.isEditable) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                KnButton(
                    label = stringResource(R.string.kontrak_history_edit_action),
                    onClick = onRequestEdit,
                    variant = KnButtonVariant.Secondary,
                    modifier = Modifier.testTag(depositHistoryEditTag(row.paymentId)),
                )
                KnButton(
                    label = stringResource(R.string.kontrak_history_cancel_action),
                    onClick = onRequestCancel,
                    variant = KnButtonVariant.Danger,
                    modifier = Modifier.testTag(depositHistoryCancelTag(row.paymentId)),
                )
            }
        }
    }
}

/** The row's tag, keyed on the deposit id so two rows cannot collide. */
internal fun depositHistoryRowTag(paymentId: String): String = "kontrak-history-row-$paymentId"

/** The correct verb's tag, keyed on the deposit id. */
internal fun depositHistoryEditTag(paymentId: String): String = "kontrak-history-edit-$paymentId"

/** The cancel verb's tag, keyed on the deposit id. */
internal fun depositHistoryCancelTag(paymentId: String): String = "kontrak-history-cancel-$paymentId"

internal const val TAG_DEPOSIT_HISTORY = "kontrak-history"
internal const val TAG_DEPOSIT_HISTORY_EMPTY = "kontrak-history-empty"
internal const val TAG_DEPOSIT_HISTORY_ERROR = "kontrak-history-error"
internal const val TAG_DEPOSIT_HISTORY_REASON = "kontrak-history-reason"
