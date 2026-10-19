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
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.Pencil
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonSize
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnSearchBar
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.components.KnTableHeaderRow
import net.klinik.rental.ui.components.KnTableCell
import net.klinik.rental.ui.components.KnTableRow
import net.klinik.rental.ui.components.KnTopBar
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
 * @param onHistoryQueryChange records what the owner typed into the history search box.
 *   Required, for the reason above — a search box wired to `{}` renders and filters nothing.
 * @param onClearHistoryQuery clears it. Required, likewise.
 * @param onSelectHistoryFilter selects one of the three status chips, or `null` for all.
 *   Required, likewise.
 */
@Composable
fun SetoranScreen(
    state: SetoranScreenState,
    onSelectTab: (SetoranTab) -> Unit,
    onOpenDepositInput: () -> Unit,
    onRequestDepositCancel: (contractId: String, renterName: String, paymentId: String) -> Unit,
    onRequestDepositEdit: (paymentId: String) -> Unit,
    onDismissError: () -> Unit,
    onHistoryQueryChange: (String) -> Unit,
    onClearHistoryQuery: () -> Unit,
    onSelectHistoryFilter: (HistoryFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        Column(Modifier.fillMaxSize()) {
            // The screen's title and its two tabs live in the **top bar**, matching
            // `payments.html:23-35`: the tabs are `.chips` inside `.top-bar-actions`, not a row
            // under a bare heading. Two reasons it matters beyond fidelity — the bar is where the
            // theme toggle is mounted, so a screen with a hand-rolled heading loses that control;
            // and the bar's own gutter is the one the page body below it uses, which is what makes
            // the title and the first card start on the same line.
            KnTopBar(
                title = stringResource(R.string.setoran_screen_title),
                actions = {
                    FlowRow(
                        modifier = Modifier
                            .padding(end = spacing.xxl)
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
                },
                modifier = Modifier.testTag(TAG_SETORAN_TITLE),
            )

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
                    onHistoryQueryChange = onHistoryQueryChange,
                    onClearHistoryQuery = onClearHistoryQuery,
                    onSelectHistoryFilter = onSelectHistoryFilter,
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
 * The Riwayat tab: the summary strip, the search box, the status chips, and the table.
 *
 * ## Why a table and not the per-contract block here
 *
 * The prototype draws this tab as `table.data` (`payments.html`'s `hist-rows`): one line per
 * deposit, columns for date, renter, plate, bank, reference and amount. The per-contract block
 * — a card per deposit with its own heading and verbs — is the right shape **inside** a contract
 * sheet, where the owner is reading one rental. Here the question is "what came in", and sixty
 * cards answer it worse than sixty rows.
 *
 * ## What this table does not carry, and why
 *
 * The prototype has a restore button on every cancelled row. This app has no restore: Story 3.4
 * cancels a deposit by deleting its allocations, and the contract KDoc is explicit that undoing
 * that means re-entering the deposit rather than flipping a flag. A restore button wired to `{}`
 * is the defect this repo has shipped repeatedly, so the column is an **edit** verb instead — the
 * correction form Story 3.5 built, which is a real action on a real row.
 *
 * @param onHistoryQueryChange records the search box's text.
 * @param onClearHistoryQuery empties it.
 * @param onSelectHistoryFilter narrows to one of the three statuses, or `null` for all.
 */
@Composable
private fun HistoryTab(
    state: SetoranScreenState,
    onRequestDepositCancel: (contractId: String, renterName: String, paymentId: String) -> Unit,
    onRequestDepositEdit: (paymentId: String) -> Unit,
    onDismissError: () -> Unit,
    onHistoryQueryChange: (String) -> Unit,
    onClearHistoryQuery: () -> Unit,
    onSelectHistoryFilter: (HistoryFilter?) -> Unit,
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

        // The two controls sit above the table at every state — including empty and failed —
        // because a search box that disappears when the list is empty is a box the owner
        // cannot use to find out why. The prototype draws them the same way.
        item(key = "search") {
            KnSearchBar(
                query = state.historyQuery,
                onQueryChange = onHistoryQueryChange,
                onClear = onClearHistoryQuery,
                placeholder = stringResource(R.string.setoran_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_SETORAN_SEARCH),
            )
        }

        item(key = "filters") {
            HistoryFilterRow(
                selected = state.historyFilter,
                onSelect = onSelectHistoryFilter,
            )
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

            // Narrowed to nothing. A different sentence from "nothing came in", because the
            // money did come in — the box is what found no match.
            state.isFilteredToNothing -> item(key = "no-match") {
                KnEmptyState(
                    title = stringResource(R.string.setoran_history_no_match_title),
                    body = stringResource(R.string.setoran_history_no_match_body),
                    icon = Lucide.CircleAlert,
                    modifier = Modifier.testTag(TAG_SETORAN_NO_MATCH),
                )
            }

            else -> {
                item(key = "header") {
                    KnTableHeaderRow(
                        headers = listOf(
                            stringResource(R.string.setoran_table_date),
                            stringResource(R.string.setoran_table_renter),
                            stringResource(R.string.setoran_table_plate),
                            stringResource(R.string.setoran_table_amount),
                            stringResource(R.string.setoran_table_status),
                        ),
                        modifier = Modifier.testTag(TAG_SETORAN_TABLE_HEADER),
                    )
                }
                items(
                    items = state.visibleRows,
                    key = { it.paymentId },
                ) { row ->
                    DepositTableRow(
                        row = row,
                        onRequestCancel = onRequestDepositCancel,
                        onRequestEdit = onRequestDepositEdit,
                    )
                }
            }
        }
    }
}

/**
 * One deposit as a table row, plus the verbs that belong to it.
 *
 * The verbs sit **below** the row rather than inside it as a sixth column: `KnTableRow` gives
 * every cell an equal weight, and a column of one 48dp icon in a five-column table is a column
 * that squeezes the four that carry data. The row is still one deposit — the buttons are its
 * own actions, keyed by its own id.
 */
@Composable
private fun DepositTableRow(
    row: DepositHistoryRowUi,
    onRequestCancel: (contractId: String, renterName: String, paymentId: String) -> Unit,
    onRequestEdit: (paymentId: String) -> Unit,
) {
    val spacing = MaterialTheme.knSpacing
    // `renterName · plate` is the label; the plate column shows the half after the dot. Parsing
    // it back out of the label would be a second derivation of the same string, so the row's
    // owner formats it once — `SetoranUiState`'s rule. The label is `"<renter> · <plate>"`, and
    // a contract the ViewModel could not resolve has no dot, in which case the whole label is
    // the best answer there is.
    val plate = row.contractLabel.substringAfter(" · ", row.contractLabel)

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        KnTableRow(
            cells = listOf(
                KnTableCell(row.dateText, bold = true),
                KnTableCell(row.renterName),
                KnTableCell(plate),
                KnTableCell(row.amountText, numeric = true),
                KnTableCell(
                    text = stringResource(row.statusRes()),
                    // The status cell's own tag, so a test can ask what **this row** says
                    // without matching the filter chip and the summary strip's labels, which
                    // use the same two words.
                    tag = depositTableStatusTag(row.paymentId),
                ),
            ),
            modifier = Modifier.testTag(depositTableRowTag(row.paymentId)),
        )
        // Two sentences the table columns have no room for, drawn only when they apply:
        // a partial deposit's reason, and a cancellation's own words.
        if (row.isPartial) {
            KnBadge(
                text = stringResource(R.string.setoran_badge_partial),
                tone = KnBadgeTone.Warning,
                modifier = Modifier.testTag(depositPartialTag(row.paymentId)),
            )
        }
        // A cancelled row's own words. `cancelReasonText` is non-null only for a cancelled
        // row in practice, and `isEditable == false` is this app's definition of "cancelled",
        // so the two agree by construction.
        row.cancelReasonText?.takeIf { !row.isEditable }?.let { reason ->
            Text(
                // The contract block's own sentence, reused rather than re-spelled: two
                // wordings of "Alasan pembatalan: …" is two sentences that can drift, and
                // the sentence is about the same fact in both places.
                text = stringResource(R.string.kontrak_history_cancel_reason, reason),
                style = MaterialTheme.knTypography.label,
                color = MaterialTheme.knColors.textTertiary,
            )
        }
        if (row.isEditable) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                KnButton(
                    label = stringResource(R.string.setoran_row_edit),
                    onClick = { onRequestEdit(row.paymentId) },
                    variant = KnButtonVariant.Ghost,
                    size = KnButtonSize.Small,
                    icon = Lucide.Pencil,
                    modifier = Modifier.testTag(depositTableEditTag(row.paymentId)),
                )
                KnButton(
                    label = stringResource(R.string.setoran_row_cancel),
                    onClick = { onRequestCancel(row.contractId, row.renterName, row.paymentId) },
                    variant = KnButtonVariant.Ghost,
                    size = KnButtonSize.Small,
                    icon = Lucide.CircleX,
                    modifier = Modifier.testTag(depositTableCancelTag(row.paymentId)),
                )
            }
        }
    }
}

/** `Aktif` or `Dibatalkan` — the status column. Partial is a badge beside the amount, not a status. */
private fun DepositHistoryRowUi.statusRes(): Int =
    if (isEditable) R.string.setoran_status_active else R.string.setoran_status_cancelled

/**
 * The three status chips and the "all" chip — `payments.html`'s `[data-hfilter]`.
 *
 * "All" is a chip rather than the absence of a chip, so the owner can get back to the whole
 * list after narrowing: a filter with no way back is a one-way door.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryFilterRow(
    selected: HistoryFilter?,
    onSelect: (HistoryFilter?) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_SETORAN_FILTERS),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.xs),
    ) {
        KnChip(
            text = stringResource(R.string.setoran_filter_all),
            selected = selected == null,
            onClick = { onSelect(null) },
            modifier = Modifier.testTag(historyFilterTag(null)),
        )
        HistoryFilter.entries.forEach { filter ->
            KnChip(
                text = stringResource(filter.labelRes()),
                selected = selected == filter,
                onClick = { onSelect(filter) },
                modifier = Modifier.testTag(historyFilterTag(filter)),
            )
        }
    }
}

/** A filter's display string — one place maps the enum to its copy. */
private fun HistoryFilter.labelRes(): Int = when (this) {
    HistoryFilter.Aktif -> R.string.setoran_filter_active
    HistoryFilter.Sebagian -> R.string.setoran_filter_partial
    HistoryFilter.Dibatalkan -> R.string.setoran_filter_cancelled
}

/** A filter chip's tag. `null` is the "all" chip, hence the explicit `All` spelling. */
internal fun historyFilterTag(filter: HistoryFilter?): String =
    "setoran-filter-${filter?.name ?: "All"}"

internal fun depositTableRowTag(paymentId: String): String = "setoran-row-$paymentId"
internal fun depositTableCancelTag(paymentId: String): String = "setoran-row-cancel-$paymentId"
internal fun depositTableEditTag(paymentId: String): String = "setoran-row-edit-$paymentId"
internal fun depositPartialTag(paymentId: String): String = "setoran-row-partial-$paymentId"
internal fun depositTableStatusTag(paymentId: String): String = "setoran-row-status-$paymentId"

/** A summary figure's own tag, keyed on which figure it is. */
internal fun setoranSummaryTag(figure: String): String = "setoran-summary-$figure"


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
            tag = setoranSummaryTag("total"),
        )
        DepositSummaryFigure(
            label = stringResource(R.string.setoran_summary_active),
            value = summary.active.toString(),
            tag = setoranSummaryTag("active"),
        )
        DepositSummaryFigure(
            label = stringResource(R.string.setoran_summary_cancelled),
            value = summary.cancelled.toString(),
            tag = setoranSummaryTag("cancelled"),
        )
        // The one figure that is money rather than a count — already formatted, so no
        // second spelling of a rupiah figure exists on this screen.
        DepositSummaryFigure(
            label = stringResource(R.string.setoran_summary_received_today),
            value = summary.receivedTodayText,
            tag = setoranSummaryTag("today"),
        )
    }
}

/**
 * One label-over-value pair in the strip.
 *
 * The tag is on the **value**, not the pair: "Dibatalkan" and "Aktif" are also the filter
 * chips' names and the status column's words, so a test that wants to know what this figure
 * says needs a node that says it — the pair's box would read as empty.
 */
@Composable
private fun DepositSummaryFigure(
    label: String,
    value: String,
    tag: String,
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
            modifier = Modifier.testTag(tag),
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
internal const val TAG_SETORAN_SEARCH = "setoran-search"
internal const val TAG_SETORAN_FILTERS = "setoran-filters"
internal const val TAG_SETORAN_NO_MATCH = "setoran-no-match"
internal const val TAG_SETORAN_TABLE_HEADER = "setoran-table-header"
