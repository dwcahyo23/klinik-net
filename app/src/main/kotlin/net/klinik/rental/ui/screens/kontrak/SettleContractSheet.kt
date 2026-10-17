package net.klinik.rental.ui.screens.kontrak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.format.formatTanggalBulan
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The FR-9b sheet: "tandai lunas sampai [tanggal]" — Story 3.6.
 *
 * ## Why a sheet and not a dialog over the contract
 *
 * Same reason [EndContractSheet] is one: it is a second window layered over the screen,
 * so the screen's own banners are invisible while it is up, and it carries its own pair.
 * It is a sibling of that sheet rather than a mode of it — the two actions are
 * opposites, and one sheet that could do both would carry a date meaning two things.
 *
 * ## Why it shows the figures *above* the field
 *
 * Because they are the consequence of the date, and the date is empty on open. The owner
 * picks a day, the figures appear, and the confirm button unlocks only once there is
 * something to close. Reading it the other way round — a live button that may write a
 * no-op — would be worse than a disabled one, because FR-9b has no undo and no audit
 * trail: the owner cannot discover afterwards that they settled a date that closed
 * nothing.
 *
 * ## Why the remaining figures are shown too
 *
 * The AC asks only for what is removed, but a confirmation that names only a removal
 * cannot distinguish a correct settlement from one that wiped the whole period. The
 * remainder line costs one row and turns "how much" into an answerable question.
 *
 * @param state the sheet's own state, held by the ViewModel so a rotation cannot lose a
 *   chosen date or the figures computed from it.
 * @param errorMessage the last failure with no field of its own — a contract nobody
 *   holds, or a write this screen does not model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleContractSheet(
    state: SettleFormState,
    onClose: () -> Unit,
    onChange: ((SettleFormState) -> SettleFormState) -> Unit,
    onConfirmSettle: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    successMessage: String? = null,
    onDismissSuccess: () -> Unit = {},
) {
    val spacing = MaterialTheme.knSpacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        modifier = modifier.testTag(TAG_SETTLE_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                // Not load-bearing for one field and two buttons — kept for the reason
                // `EndContractSheet` keeps its bound: so a future line cannot push the
                // confirm off a short landscape tablet.
                .heightIn(max = SettleSheetMaxHeight),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(
                    R.string.kontrak_settle_title,
                    state.contractLabel,
                    state.settleDate?.let(::formatTanggalBulan).orEmpty(),
                ),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_SETTLE_TITLE),
            )

            ContractDateField(
                date = state.settleDate,
                label = stringResource(R.string.kontrak_settle_field_date),
                error = state.dateError,
                onPick = { date -> onChange { it.selectSettleDate(date) } },
                // Says *what the action is for*, not merely that a date is wanted: the
                // natural misreading is "the day the next bill is due", and the hint is
                // what stops the owner picking today.
                placeholder = stringResource(R.string.kontrak_settle_date_hint),
                modifier = Modifier.testTag(TAG_SETTLE_DATE),
            )

            // The impact, drawn **below** the field and above the buttons, because it is
            // the consequence of the field and the owner reads top-down: choose, read,
            // commit.
            if (state.settleDate != null) {
                if (state.daysClosed > 0) {
                    Text(
                        text = stringResource(
                            R.string.kontrak_settle_impact,
                            formatRupiah(state.amountRemoved),
                            state.daysClosed,
                        ),
                        style = MaterialTheme.knTypography.body,
                        color = MaterialTheme.knColors.textPrimary,
                        modifier = Modifier.testTag(TAG_SETTLE_IMPACT),
                    )
                    if (state.daysRemaining > 0) {
                        Text(
                            text = stringResource(
                                R.string.kontrak_settle_remaining,
                                formatRupiah(state.amountRemaining),
                                state.daysRemaining,
                            ),
                            style = MaterialTheme.knTypography.body,
                            color = MaterialTheme.knColors.textSecondary,
                            modifier = Modifier.testTag(TAG_SETTLE_REMAINING),
                        )
                    }
                } else {
                    // A date that closes nothing gets a sentence rather than a silent
                    // disabled button: the owner picked a real day and needs to know why
                    // it did nothing.
                    Text(
                        text = stringResource(R.string.kontrak_settle_closes_nothing),
                        style = MaterialTheme.knTypography.body,
                        color = MaterialTheme.knColors.textTertiary,
                        modifier = Modifier.testTag(TAG_SETTLE_CLOSES_NOTHING),
                    )
                }
            }

            successMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Success,
                    onDismiss = onDismissSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_SETTLE_SUCCESS),
                )
            }

            errorMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Danger,
                    onDismiss = onDismissError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_SETTLE_ERROR),
                )
            }

            KnButton(
                label = stringResource(R.string.kontrak_settle_confirm),
                onClick = onConfirmSettle,
                icon = Lucide.CircleCheck,
                variant = KnButtonVariant.Primary,
                // `canSettle`: a date **and** something to close. The second half is
                // the one that carries the risk — FR-9b cannot be undone from inside the
                // app — so a settlement that would change nothing is not a button the
                // owner can press by mistake.
                enabled = state.canSettle,
                modifier = Modifier.testTag(TAG_SETTLE_CONFIRM),
            )

            KnButton(
                label = stringResource(R.string.kontrak_cancel),
                onClick = onClose,
                variant = KnButtonVariant.Ghost,
                modifier = Modifier.testTag(TAG_SETTLE_CLOSE),
            )
        }
    }
}

/** A bound on the sheet's content. See [EndContractSheet]'s for why it is not load-bearing. */
private val SettleSheetMaxHeight = 900.dp

internal const val TAG_SETTLE_SHEET = "kontrak-settle-sheet"
internal const val TAG_SETTLE_TITLE = "kontrak-settle-title"
internal const val TAG_SETTLE_DATE = "kontrak-settle-date"
internal const val TAG_SETTLE_IMPACT = "kontrak-settle-impact"
internal const val TAG_SETTLE_REMAINING = "kontrak-settle-remaining"
internal const val TAG_SETTLE_CLOSES_NOTHING = "kontrak-settle-closes-nothing"
internal const val TAG_SETTLE_CONFIRM = "kontrak-settle-confirm"
internal const val TAG_SETTLE_CLOSE = "kontrak-settle-close"
internal const val TAG_SETTLE_ERROR = "kontrak-settle-error"
internal const val TAG_SETTLE_SUCCESS = "kontrak-settle-success"