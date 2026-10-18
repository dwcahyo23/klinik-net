package net.klinik.rental.ui.screens.setoran

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookPen
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.screens.kontrak.DepositHistoryBlock
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The Setoran destination: two tabs, Input and Riwayat — the prototype's
 * `payments.html` structure.
 *
 * ## Why the Input tab opens the **existing** sheet rather than holding a form
 *
 * Because the sheet carries the numpad, the mandatory preview and every validation the
 * app has: a second form here would be a second derivation of everything Story 3.2
 * built, and the two would drift the first time one was fixed. The tab's one control
 * triggers [onOpenDepositInput], which the shell wires to the same
 * `screens.deposito.onOpen` the FAB and Beranda's grid use — UX-DR17's "the doors
 * contain the same actions".
 *
 * ## Why the Riwayat tab is a cross-contract list
 *
 * Because the per-contract block in the contract sheet answers "what did **this**
 * rental's payments look like"; this tab answers "what did money come in, period". Both
 * are kept: one is a bank statement, the other is a ledger. The rows reuse
 * `DepositHistoryBlock` — the same row shape, cancelled marker and absent-verbs rule the
 * contract sheet already draws — each row naming its own renter · plate, because a
 * cross-contract list whose rows did not say whose money it was would be unreadable.
 *
 * ## Why a cancelled row appears here, marked, with its reason and **no verbs**
 *
 * Because Story 3.4 kept the row so the audit trail stays readable, and hiding a
 * cancellation here would undo that decision one more screen later. The verbs are absent
 * rather than greyed out for the reason `DepositHistoryBlock`'s own KDoc gives: both
 * repositories refuse the action, and a control that renders and ignores the tap is the
 * "wired to nothing" defect this repo has shipped three times. The rows carry
 * `isEditable`, so the block decides by construction.
 *
 * @param state the screen's own state — see [SetoranScreenState].
 * @param onSelectTab switches tabs. Required, not defaulted: a tab wired to `{}` renders
 *   and ignores the tap, which is the same defect every required callback here exists to
 *   prevent.
 * @param onOpenDepositInput opens the existing deposit sheet. Required, for the same
 *   reason — it is the Input tab's only reason to exist.
 * @param onRequestDepositCancel opens Story 3.4's cancellation dialog for one deposit.
 *   Required: these two verbs are Stories 3.4's and 3.5's entry points on this surface,
 *   and both shipped unreachable once already.
 * @param onRequestDepositEdit opens Story 3.5's correction form. Required, likewise.
 * @param onDismissError clears the history read's failure banner. Required: a defaulted
 *   dismiss leaves a banner that cannot be closed.
 */
@Composable
fun SetoranScreen(
    state: SetoranScreenState,
    onSelectTab: (SetoranTab) -> Unit,
    onOpenDepositInput: () -> Unit,
    onRequestDepositCancel: (contractId: String, renterName: String, paymentId: String) -> Unit,
    onRequestDepositEdit: (paymentId: String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.setoran_screen_title),
                    style = MaterialTheme.knTypography.title,
                    color = MaterialTheme.knColors.textPrimary,
                    modifier = Modifier
                        .padding(top = spacing.lg)
                        .testTag(TAG_SETORAN_TITLE),
                )

                // The two tabs, as a pair of chips — the app's own tab control (no
                // `TabRow` exists anywhere in this app; the filter chips are the
                // established pattern). Input is selected first.
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.md)
                        .testTag(TAG_SETORAN_TABS),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    SetoranTab.entries.forEach { tab ->
                        KnChip(
                            text = stringResource(tab.labelRes()),
                            selected = tab == state.selectedTab,
                            onClick = { onSelectTab(tab) },
                            modifier = Modifier.testTag(setoranTabTag(tab)),
                        )
                    }
                }
            }

            when (state.selectedTab) {
                SetoranTab.Input -> InputTab(
                    onOpen = onOpenDepositInput,
                    modifier = Modifier.weight(1f),
                )

                SetoranTab.Riwayat -> HistoryTab(
                    state = state,
                    onRequestDepositCancel = onRequestDepositCancel,
                    onRequestDepositEdit = onRequestDepositEdit,
                    onDismissError = onDismissError,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The Input tab: one door to the existing deposit sheet.
 *
 * ## Why a single action and not a form
 *
 * The sheet is the form. Duplicating even the amount field here would be a second place
 * a rupiah figure is typed and validated — the "second derivation" defect the spec's
 * Never list names first.
 */
@Composable
private fun InputTab(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KnEmptyState(
        title = stringResource(R.string.setoran_input_title),
        // Says what happens next, so the tab is an instruction rather than a stub.
        body = stringResource(R.string.setoran_input_body),
        icon = Lucide.NotebookPen,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_SETORAN_INPUT_TAB),
        action = {
            KnButton(
                label = stringResource(R.string.deposito_action),
                onClick = onOpen,
                variant = KnButtonVariant.Primary,
                modifier = Modifier.testTag(TAG_SETORAN_INPUT_ACTION),
            )
        },
    )
}

/**
 * The Riwayat tab: the summary strip, the error banner, and the cross-contract rows.
 *
 * The rows are `DepositHistoryBlock` — the **same** composable the contract sheet mounts,
 * given cross-contract rows. Reusing it is the point: the row shape, the cancelled
 * marker, the absent verbs and the empty state were all built and tested once.
 */
@Composable
private fun HistoryTab(
    state: SetoranScreenState,
    onRequestDepositCancel: (contractId: String, renterName: String, paymentId: String) -> Unit,
    onRequestDepositEdit: (paymentId: String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_SETORAN_HISTORY),
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = spacing.lg,
            bottom = spacing.colossal,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // The four plain figures. Derived in the ViewModel from the same emission as the
        // rows, so the strip cannot disagree with the list beneath it. `null` while
        // loading or failed — a zeroed strip over a read that has not happened is the
        // same "Rp 0" lie Beranda refuses to draw.
        state.summary?.let { summary ->
            item(key = "summary") {
                DepositSummaryStrip(
                    summary = summary,
                    modifier = Modifier.testTag(TAG_SETORAN_SUMMARY),
                )
            }
        }

        state.errorMessage?.let { message ->
            item(key = "error") {
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Danger,
                    onDismiss = onDismissError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_SETORAN_ERROR),
                )
            }
        }

        when {
            // Failed: the banner above is the whole answer. Drawing the empty state
            // beside it would say "no deposits anywhere" and then apologise.
            state.errorMessage != null -> Unit

            state.isEmpty -> item(key = "empty") {
                KnEmptyState(
                    title = stringResource(R.string.setoran_history_empty_title),
                    body = stringResource(R.string.setoran_history_empty_body),
                    icon = Lucide.CircleAlert,
                    modifier = Modifier.testTag(TAG_SETORAN_EMPTY),
                )
            }

            else -> items(
                items = state.rows,
                key = { it.paymentId },
            ) { row ->
                // One block per row is deliberate: the block's empty/error states belong
                // to the per-contract surface, and this screen owns both here. A block
                // carrying exactly one row keeps the cross-contract list a list, and the
                // block's cancel callback reads that row — so the handler receives the
                // tapped row's own contract, renter and payment id, which is the
                // property the wiring test pins.
                DepositHistoryBlock(
                    state = DepositHistoryUi(
                        contractId = row.contractId,
                        contractLabel = row.contractLabel,
                        renterName = row.renterName,
                        rows = listOf(row),
                    ),
                    onRequestCancel = onRequestDepositCancel,
                    onRequestEdit = onRequestDepositEdit,
                    // The **row's own** `renterName · plate`, which is what makes this list
                    // cross-contract rather than a second per-contract sheet: without it a
                    // rupiah figure beside a date says nothing about whose money it was.
                    // `titleText` rather than a new parameter because the block already draws
                    // a heading above its rows, and a second heading would say the same thing.
                    titleText = row.contractLabel,
                    // The block's own banner is never raised here: `DepositHistoryUi`'s
                    // `errorMessage` is left at its `null` default because the screen owns
                    // the one failure this list has, above. Forwarded rather than `{}` so
                    // that state has a way out if a caller ever fills it.
                    onErrorDismiss = onDismissError,
                )
            }
        }
    }
}

/**
 * The strip of four plain figures. No chart, no KPI hierarchy — four numbers that answer
 * "how much came in, and what did I undo".
 */
@Composable
private fun DepositSummaryStrip(
    summary: DepositSummaryUi,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        DepositSummaryFigure(
            label = stringResource(R.string.setoran_summary_total),
            value = summary.total.toString(),
        )
        DepositSummaryFigure(
            label = stringResource(R.string.setoran_summary_active),
            value = summary.active.toString(),
        )
        DepositSummaryFigure(
            label = stringResource(R.string.setoran_summary_cancelled),
            value = summary.cancelled.toString(),
        )
        // The one figure that is money rather than a count — already formatted, so no
        // second spelling of a rupiah figure exists on this screen.
        DepositSummaryFigure(
            label = stringResource(R.string.setoran_summary_received_today),
            value = summary.receivedTodayText,
        )
    }
}

/** One label-over-value pair in the strip. */
@Composable
private fun DepositSummaryFigure(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.knTypography.label,
            color = MaterialTheme.knColors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.knTypography.figure,
            color = MaterialTheme.knColors.textPrimary,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** A tab's display string, from the enum — one place maps the tab to its copy. */
private fun SetoranTab.labelRes(): Int = when (this) {
    SetoranTab.Input -> R.string.setoran_tab_input
    SetoranTab.Riwayat -> R.string.setoran_tab_history
}

/** A tab chip's tag, keyed on the tab so two chips cannot collide. */
internal fun setoranTabTag(tab: SetoranTab): String = "setoran-tab-${tab.name}"

internal const val TAG_SETORAN_TITLE = "setoran-title"
internal const val TAG_SETORAN_TABS = "setoran-tabs"
internal const val TAG_SETORAN_INPUT_TAB = "setoran-input-tab"
internal const val TAG_SETORAN_INPUT_ACTION = "setoran-input-action"
internal const val TAG_SETORAN_HISTORY = "setoran-history"
internal const val TAG_SETORAN_SUMMARY = "setoran-summary"
internal const val TAG_SETORAN_ERROR = "setoran-error"
internal const val TAG_SETORAN_EMPTY = "setoran-empty"
