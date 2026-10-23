package net.klinik.rental.ui.screens.tunggakan

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import net.klinik.rental.R
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnKpiGrid
import net.klinik.rental.ui.components.KnKpiIconLayout
import net.klinik.rental.ui.components.KnKpiTile
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnMasterDetail
import net.klinik.rental.ui.components.KnSkeletonList
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.components.KnTopBar
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The arrears list: who owes, how much, and for how many days.
 *
 * ## Why this screen exists where it does
 *
 * Because it is the screen the owner opens every morning, and it was absent. `Kendaraan`,
 * `Penyewa` and `Kontrak` all answered "what have I stored"; none answered "who owes me".
 *
 * ## Why every row shows amount **and** day count
 *
 * Because the criterion makes them one fact: *"tidak pernah salah satu saja."* The rupiah
 * alone cannot tell the owner whether to call today or next week, and the day count alone
 * cannot tell them whether it is worth calling at all. They are assembled into
 * [ArrearsRow.figureText] by the ViewModel, so the two can never be drawn in one row and
 * withheld from the next.
 *
 * ## Why the colour cannot be chosen
 *
 * The badge tone comes from `TunggakanUiState.toneFor(riskOf(days))` and is carried on the
 * row. There is no path by which a composable or a caller picks it, so the failure UX-DR9
 * names — a number reading "3 hari" beside a green badge — is not representable. The ladder's
 * own **word** is drawn beside the badge as well, because colour is never the only carrier of
 * meaning.
 *
 * ## Why landscape carries a detail pane and portrait does not
 *
 * Because UX-DR13 says comparing two renters must not mean paging between screens, and the
 * configuration is read from the composition rather than from a parameter — the orientation
 * changes while the app is open, and a parameter would need the Activity to be told.
 *
 * The pane is a **Row beside the list**, not a second destination and not a sheet: a
 * destination would make comparing mean navigating, and a sheet would cover the list it is
 * meant to be read against.
 *
 * ## Why this screen has no action of its own
 *
 * Because every action on an arrears row belongs to a later story: the WhatsApp reminder is
 * 4.2, and the deposit cancellation and correction that Epic 3 left unmounted belong to
 * whichever story builds the deposit history. A row whose verbs are stubs would be a control
 * that renders and does nothing — the defect this repo has already shipped twice.
 *
 * ## Why a portrait row is **not** a tap target
 *
 * Because there is no pane beside it to receive the selection. A row wired to `onSelect` in
 * portrait would store a selection nothing draws — the control-that-renders-and-does-nothing
 * defect `Screens.Kontrak`'s KDoc records as having shipped twice in this repo. So the row's
 * `onClick` is `null` in portrait, which `KnListRow` draws as an explicit non-clickable row
 * with unchanged padding and type rather than as a dead target.
 *
 * Landscape is where comparison happens (UX-DR13), and landscape is the only orientation
 * where the tap means something.
 *
 * ## Why the reminder verb is on every row, in both orientations
 *
 * Because it is an action on *that* row and needs no pane to land in — unlike selection, which
 * only means something beside a detail view. Story 4.2's criterion opens with the owner pressing
 * the verb on a row, so hiding it in portrait would make the story unreachable on the phone
 * layout.
 *
 * ## Why the subtitle is absent rather than zero
 *
 * Because "0 penyewa" on every load states a conclusion the app has not reached. While
 * loading, and while a read has failed, there is no count to report — and a bar reading zero
 * is the empty-looks-real failure on the one line that names the screen's subject.
 *
 * @param onSelect selects one row for the landscape detail pane. Required, not defaulted:
 *   a pane wired to `{}` renders rows that select nothing, which looks like a broken screen
 *   rather than a missing handler.
 * @param onErrorDismiss clears [TunggakanUiState.errorMessage]. Required for the same
 *   reason as [onSelect] — a defaulted dismiss leaves a banner that cannot be closed, and
 *   nothing at the call site says so. The banner's **tone** is not a parameter: it is
 *   derived from [TunggakanUiState.isFailed], so severity cannot disagree with the state
 *   that produced it.
 * @param onRemind opens the reminder dialog for one row. Required for the same reason.
 * @param pendingLink the link the owner asked to open, or `null`. Consumed by [onReminderLinkOpened]
 *   as soon as it is launched, so one send cannot open twice.
 */
@Composable
fun TunggakanScreen(
    state: TunggakanUiState,
    onSelect: (String) -> Unit,
    onRiskFilterChange: (RiskFilter) -> Unit,
    onErrorDismiss: () -> Unit,
    onRemind: (String) -> Unit,
    onReminderChange: (String) -> Unit,
    onReminderSend: () -> Unit,
    onReminderCopy: () -> Unit,
    onReminderDismiss: () -> Unit,
    onReminderLinkOpened: () -> Unit,
    onReminderOpenFailed: () -> Unit,
    pendingLink: String?,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        Column(Modifier.fillMaxSize()) {
            KnTopBar(
                title = stringResource(R.string.tunggakan_title),
                // The count on the bar rather than a KPI tile, for the reason
                // `KontrakScreen` gives: the screen says how much it is showing without
                // spending a card on it, and this list has no KPI row to put one in. Absent
                // rather than "0 penyewa" while loading or failed — see the class KDoc.
                subtitle = if (state.isLoading || state.isFailed) {
                    null
                } else {
                    stringResource(R.string.tunggakan_count, state.renterCount)
                },
                // `arrears.html:29-35` — the risk chips live in the bar, beside the search pill
                // the shell provides, not in a row above the page.
                actions = {
                    TunggakanRiskChips(
                        selected = state.riskFilter,
                        onSelect = onRiskFilterChange,
                    )
                },
            )

            // The page body — `shared.css:285-289`.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                // `arrears.html:41` — four figures above the panes. Drawn only once the read
                // has landed, and drawn nothing at all when it failed: four zeros on the screen
                // that is about to say "nobody owes you anything" is the one place four zeros
                // must never appear.
                if (!state.isLoading && !state.isFailed) {
                    ArrearsSummaryRow(state.summary)
                }

                // The panes, in **both** orientations. The old code branched on
                // `isLandscape` and drew a bare list in portrait, which is not what
                // `shared.css:1033` does: it *stacks* the two panes, so the detail is still
                // reachable on a narrow window rather than dropped.
                KnMasterDetail(
                    list = {
                        ArrearsList(
                            state = state,
                            onSelect = onSelect,
                            onRemind = onRemind,
                        )
                    },
                    detail = {
                        TunggakanDetail(row = state.selectedRow)
                    },
                )
            }

            // The banner is drawn over unchanged content, per the four-state rule. It sits
            // here rather than inside the list because a failure to compute is not a fact
            // about any one row.
            //
            // The tone comes from `isFailed`, **not** from comparing the text against a
            // second copy of the same message: severity inferred by string equality is a
            // claim two channels have to agree on, and `MainActivity` passed the same value
            // on both — so the two could differ and nothing would say which was right.
            state.errorMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = if (state.isFailed) KnBadgeTone.Danger else KnBadgeTone.Success,
                    onDismiss = onErrorDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.sm)
                        .testTag(TAG_ERROR),
                )
            }
        }
    }

    // The reminder dialog, mounted **outside** the screen's own column so it composes in its
    // own window above rather than inside the layout. `state.reminder` being non-null is what
    // makes it visible, so the window cannot be up with nothing in it.
    state.reminder?.let { reminder ->
        val copy = rememberClipboardCopy()
        ReminderDialog(
            state = reminder,
            onChange = onReminderChange,
            onSend = onReminderSend,
            onCopy = {
                // Copy what is in the field **now**, then tell the ViewModel so the
                // confirmation reaches the state like every other message. The ViewModel still
                // refuses an empty message — the disabled button is a courtesy, not the rule.
                if (reminder.canSend) {
                    copy(reminder.text)
                    onReminderCopy()
                }
            },
            onDismiss = onReminderDismiss,
        )
    }

    // Opening the link is a side effect, so it happens here rather than in the ViewModel: the
    // state carries *what* to open, and this is where an `Intent` can be built.
    //
    // A failed launch is **reported, not swallowed**. No WhatsApp installed is the common case,
    // and the owner would otherwise tap Send, watch nothing happen, and have no idea that the
    // Copy button beside it exists for exactly this. `runCatching` used to hide that.
    val context = LocalContext.current
    LaunchedEffect(pendingLink) {
        pendingLink?.let { link ->
            val opened = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, link.toUri()).apply {
                        // A new task, because this app has one Activity: without the flag the
                        // intent would try to launch *this* app's task and fail.
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }.isSuccess
            if (!opened) onReminderOpenFailed()
            // Consumed either way: a failed launch retried on every recomposition would report
            // the same failure forever, and the owner's next attempt is a fresh Send.
            onReminderLinkOpened()
        }
    }
}

/**
 * `arrears.html:112-136` — the four figures above the panes.
 *
 * ## Why the first card is **not** accented
 *
 * The prototype gives total arrears a brand edge (`kpi-accent`), and this screen used to
 * reproduce it. The owner asked for it to go: *"garis border ini di hapus saja agar sama seperti
 * card lain seperti card Kritis Perhatian Ringan."* Four cards in a row, one of them wearing an
 * edge, reads as four cards of which one is a different kind — and the total's own red icon and
 * red meta already say what the edge was saying.
 *
 * The accent machinery stays in `KnCard` for a caller that wants it; this screen simply does not
 * ask for one.
 */
@Composable
private fun ArrearsSummaryRow(summary: ArrearsSummary) {
    KnKpiGrid(
        modifier = Modifier.testTag(TAG_SUMMARY),
        tiles = listOf(
            { KnKpiTile(
                label = stringResource(R.string.tunggakan_summary_total),
                value = summary.totalText,
                meta = stringResource(
                    R.string.tunggakan_summary_total_meta,
                    summary.owingCount,
                    summary.contractCount,
                ),
                icon = Lucide.CircleAlert,
                iconTint = MaterialTheme.knColors.semantic.danger,
                metaTint = MaterialTheme.knColors.semantic.danger,
                iconLayout = KnKpiIconLayout.Corner,
            ) },
            { KnKpiTile(
                label = stringResource(R.string.tunggakan_summary_critical),
                value = summary.criticalCount.toString(),
                meta = summary.criticalText,
                icon = Lucide.Clock,
                iconTint = MaterialTheme.knColors.semantic.danger,
                metaTint = MaterialTheme.knColors.semantic.danger,
                iconLayout = KnKpiIconLayout.Corner,
            ) },
            { KnKpiTile(
                label = stringResource(R.string.tunggakan_summary_attention),
                value = summary.attentionCount.toString(),
                meta = summary.attentionText,
                icon = Lucide.Clock,
                iconTint = MaterialTheme.knColors.semantic.warning,
                metaTint = MaterialTheme.knColors.semantic.warning,
                iconLayout = KnKpiIconLayout.Corner,
            ) },
            { KnKpiTile(
                label = stringResource(R.string.tunggakan_summary_light),
                value = summary.lightCount.toString(),
                meta = summary.lightText,
                icon = Lucide.Info,
                iconTint = MaterialTheme.knColors.semantic.info,
                metaTint = MaterialTheme.knColors.semantic.success,
                iconLayout = KnKpiIconLayout.Corner,
            ) },
        ),
    )
}

/** `arrears.html:29-35` — the five risk chips, in the top bar's actions slot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TunggakanRiskChips(selected: RiskFilter, onSelect: (RiskFilter) -> Unit) {
    FlowRow(
        modifier = Modifier.testTag(TAG_RISK_CHIPS),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.xs),
    ) {
        RISK_FILTERS.forEach { filter ->
            KnChip(
                text = stringResource(riskFilterLabel(filter)),
                selected = filter == selected,
                onClick = { onSelect(filter) },
                modifier = Modifier.testTag(riskFilterTag(filter)),
            )
        }
    }
}

private val RISK_FILTERS = listOf(
    RiskFilter.Semua,
    RiskFilter.Kritis,
    RiskFilter.Perhatian,
    RiskFilter.Ringan,
    RiskFilter.Lancar,
)

/**
 * A chip's label.
 *
 * The ladder chips reuse `tunggakanRiskLabel`, the **same** string a row's badge resolves, so a
 * chip and the badge under it cannot name different severities — the defect a duplicated set of
 * literals invites and this file has to keep refusing.
 */
private fun riskFilterLabel(filter: RiskFilter): Int = when (filter) {
    RiskFilter.Semua -> R.string.tunggakan_filter_all
    RiskFilter.Kritis -> R.string.tunggakan_risk_kritis
    RiskFilter.Perhatian -> R.string.tunggakan_risk_perhatian
    RiskFilter.Ringan -> R.string.tunggakan_risk_ringan
    RiskFilter.Lancar -> R.string.tunggakan_risk_lancar
}

/**
 * The list half, loading and empty states included.
 *
 * @param onSelect the row's click handler. Never `null` now: the detail pane is drawn in both
 *   orientations (`shared.css:1033` stacks them), so a selection always has somewhere to land.
 */
@Composable
private fun ArrearsList(
    state: TunggakanUiState,
    onSelect: (String) -> Unit,
    onRemind: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Box(modifier) {
        when {
            // Loading first, and mutually exclusive with the branches below. Room's first
            // emission is an empty list, so without this the owner reads "Tidak ada
            // tunggakan" on every load — which on *this* screen is the claim "nobody owes
            // you anything", read on a screen that has not looked yet.
            state.isLoading -> KnSkeletonList()

            // A failed calculation draws **nothing at all**, not even the empty
            // state. The empty state says "semua sewa sudah lunas" — that nobody owes
            // anything — and pairing it with a failure banner states a confident wrong
            // number and then apologises for it. The banner alone is the honest output: it
            // says the question is unanswered, which is exactly what happened. Keyed on
            // `isFailed` rather than on a banner being present, because a banner a caller
            // sets by hand is not the same claim as a read that failed.
            state.isFailed -> Unit

            // Narrowed by the bar's chip, so an empty result can also be "nothing in this
            // risk band" rather than "nothing at all". The message says which.
            state.visibleRows.isEmpty() -> KnEmptyState(
                title = stringResource(
                    if (state.rows.isEmpty()) {
                        R.string.tunggakan_empty_title
                    } else {
                        R.string.tunggakan_empty_filtered_title
                    },
                ),
                // Says what to do, per the epic context: an empty state that only says
                // "nothing" leaves the owner unsure whether to trust it or wait.
                body = stringResource(
                    if (state.rows.isEmpty()) {
                        R.string.tunggakan_empty_body
                    } else {
                        R.string.tunggakan_empty_filtered_body
                    },
                ),
                icon = Lucide.CircleCheck,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = spacing.colossal),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(
                    items = state.visibleRows,
                    // Keyed on the contract id so a row leaving the list under a write
                    // does not shift the remaining rows' saved state onto another rental.
                    key = { it.contractId },
                ) { row ->
                    ArrearsRowItem(
                        row = row,
                        selected = row.contractId == state.selectedContractId,
                        onClick = { onSelect(row.contractId) },
                        onRemind = { onRemind(row.contractId) },
                    )
                }
            }
        }
    }
}

/**
 * One arrears row.
 *
 * @param selected drawn in landscape, where the pane beside the list is what the
 *   selection means. `KnListRow` draws the active edge itself, so this passes the flag
 *   rather than composing a selection treatment of its own.
 * @param onClick `null` in portrait, where a selection has no pane to land in. See the
 *   class KDoc — a row that stores a selection nothing draws is the defect this repo has
 *   already shipped twice.
 */
@Composable
private fun ArrearsRowItem(
    row: ArrearsRow,
    selected: Boolean,
    onClick: (() -> Unit)?,
    onRemind: () -> Unit,
) {
    val spacing = MaterialTheme.knSpacing
    // Spoken as one sentence rather than as separate figures, so a screen reader announces
    // the row's claim rather than a bare number and a bare badge.
    val description = "${row.renterName}, ${row.figureText}, ${row.riskLabel}"

    KnListRow(
        title = row.renterName,
        subtitle = stringResource(R.string.tunggakan_row_subtitle, row.vehiclePlate),
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .testTag(TAG_ROW_PREFIX + row.contractId)
            .semantics { contentDescription = description },
        leading = { KnAvatar(name = row.renterName, avatar = row.renterAvatar) },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                // The AC's shape, already assembled by the ViewModel — no `formatRupiah`
                // call in this file, so two screens cannot spell a rupiah two ways.
                Text(
                    text = row.figureText,
                    style = MaterialTheme.knTypography.figure,
                    color = MaterialTheme.knColors.textPrimary,
                    // `tabular-nums` so a column of amounts aligns for visual comparison,
                    // which is the design system's own reason for the token (AR-11).
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The ended marker, drawn **beside** the risk badge rather than in
                    // place of it: a finished rental can still owe money, so the two facts
                    // are independent and hiding either loses one.
                    if (row.isEnded) {
                        KnBadge(
                            text = stringResource(R.string.tunggakan_row_ended),
                            tone = KnBadgeTone.Neutral,
                            modifier = Modifier.testTag(TAG_ENDED_PREFIX + row.contractId),
                        )
                    }
                    // The risk badge, carrying the ladder's own word. The tone is derived in
                    // the state and cannot be passed in — see the class KDoc.
                    KnBadge(
                        text = row.riskLabel,
                        tone = row.tone,
                        modifier = Modifier.testTag(TAG_RISK_PREFIX + row.contractId),
                    )
                }
                // Story 4.2's verb. Its own button rather than the row's tap: the row selects
                // for the detail pane (landscape only), and sending a reminder is a different
                // decision with money consequences. Two actions on one row must be two targets.
                //
                // `KnButton` consumes its own tap, so in landscape the row's selection does not
                // also fire. That is `Surface(onClick=)`'s own behaviour, not something asserted
                // here — the button's semantics are the inner node's.
                ReminderAction(
                    onClick = onRemind,
                    modifier = Modifier.testTag(
                        TAG_REMINDER_ACTION_PREFIX + row.contractId,
                    ),
                )
            }
        },
    )
}

/**
 * The landscape detail pane.
 *
 * Shows the selected row's figures under their own labels, because a figure already on
 * screen twice — once on the row, once here — needs a name each time or the owner is reading
 * two identical numbers with no idea which is which.
 *
 * The **oldest open charge day** is the pane's own reason for existing beyond tidiness: the
 * epic's note says that if Budi paid on Sunday for Monday, that day is what explains his
 * figure, and it is the one thing the list cannot show in a column without crowding every
 * row.
 */
@Composable
private fun TunggakanDetail(
    row: ArrearsRow?,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Surface(
        modifier = modifier,
        color = MaterialTheme.knColors.bgCard,
        shape = RoundedCornerShape(MaterialTheme.knRadii.lg),
    ) {
        if (row == null) {
            // The prompt, not an empty panel: a blank pane beside a list reads as a
            // rendering failure rather than as "nothing selected yet". An `if/else` rather
            // than an early `return`, which is not permitted in a `@Composable` body.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.tunggakan_detail_empty),
                    style = MaterialTheme.knTypography.body,
                    color = MaterialTheme.knColors.textTertiary,
                    modifier = Modifier.testTag(TAG_DETAIL_EMPTY),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.lg)
                    .testTag(TAG_DETAIL),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.tunggakan_detail_title),
                    style = MaterialTheme.knTypography.label,
                    color = MaterialTheme.knColors.textSecondary,
                )
                Text(
                    text = stringResource(
                        R.string.tunggakan_detail_label,
                        row.renterName,
                        row.vehiclePlate,
                    ),
                    style = MaterialTheme.knTypography.title,
                    color = MaterialTheme.knColors.textPrimary,
                )
                DetailLine(
                    label = stringResource(R.string.tunggakan_detail_amount_label),
                    value = row.figureText,
                    testTag = TAG_DETAIL_AMOUNT,
                )
                DetailLine(
                    label = stringResource(R.string.tunggakan_detail_days_label),
                    value = row.daysLabel,
                    testTag = TAG_DETAIL_DAYS,
                )
                // The oldest open day, or the "nothing open" line. Never omitted: a
                // missing row would leave the owner unable to tell "no day is open" from
                // "this pane does not show that".
                DetailLine(
                    label = stringResource(R.string.tunggakan_detail_oldest_label),
                    value = row.oldestDayText
                        ?: stringResource(R.string.tunggakan_detail_oldest_none),
                    testTag = TAG_DETAIL_OLDEST,
                )
            }
        }
    }
}

/** One labelled figure in the detail pane. */
@Composable
private fun DetailLine(label: String, value: String, testTag: String) {
    val spacing = MaterialTheme.knSpacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.knTypography.label,
            color = MaterialTheme.knColors.textTertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.knTypography.figure,
            color = MaterialTheme.knColors.textPrimary,
        )
    }
}

/** Row tags, keyed on the contract id so a test can address one row. */
internal const val TAG_ROW_PREFIX = "tunggakan-row-"
internal const val TAG_RISK_PREFIX = "tunggakan-risk-"
internal const val TAG_ENDED_PREFIX = "tunggakan-ended-"

internal const val TAG_DETAIL = "tunggakan-detail"
internal const val TAG_DETAIL_EMPTY = "tunggakan-detail-empty"
internal const val TAG_DETAIL_AMOUNT = "tunggakan-detail-amount"
internal const val TAG_DETAIL_DAYS = "tunggakan-detail-days"
internal const val TAG_DETAIL_OLDEST = "tunggakan-detail-oldest"
internal const val TAG_ERROR = "tunggakan-error"

/** The four summary cards — `arrears.html:41`. */
internal const val TAG_SUMMARY = "tunggakan-summary"

/** The five risk chips in the top bar, and one chip's tag. */
internal const val TAG_RISK_CHIPS = "tunggakan-risk-chips"

internal fun riskFilterTag(filter: RiskFilter): String = "tunggakan-chip-${filter.name}"

@Preview(showBackground = true, heightDp = 2400, widthDp = 1280)
@Composable
private fun LandscapePreview() {
    KlinikNetTheme(darkTheme = false) {
        TunggakanScreen(
            state = TunggakanUiState(
                rows = listOf(
                    ArrearsRow(
                        contractId = "c1",
                        renterName = "Budi Santoso",
                        vehiclePlate = "B 1235 TF",
                        renterPhone = "0812-8899-7722",
                        amount = 750000,
                        figureText = "Rp 750.000 · 3 hari",
                        daysLabel = "3 hari",
                        riskLabel = "Perhatian",
                        tone = TunggakanUiState.toneFor(RiskLevel.PERHATIAN),
                        risk = RiskLevel.PERHATIAN,
                        oldestDayText = "14 Okt",
                        isEnded = false,
                    ),
                    ArrearsRow(
                        contractId = "c2",
                        renterName = "Siti Aminah",
                        vehiclePlate = "B 9876 AB",
                        renterPhone = "0812-8899-7722",
                        figureText = "Rp 3.500.000 · 14 hari",
                        amount = 3_500_000,
                        daysLabel = "14 hari",
                        riskLabel = "Kritis",
                        tone = TunggakanUiState.toneFor(RiskLevel.KRITIS),
                        risk = RiskLevel.KRITIS,
                        oldestDayText = "01 Sep",
                        isEnded = true,
                    ),
                ),
                selectedContractId = "c2",
            ),
            onSelect = {},
            onRiskFilterChange = {},
            onErrorDismiss = {},
            onRemind = {},
            onReminderChange = {},
            onReminderSend = {},
            onReminderCopy = {},
            onReminderDismiss = {},
            onReminderLinkOpened = {},
            onReminderOpenFailed = {},
            pendingLink = null,
        )
    }
}

@Preview(showBackground = true, heightDp = 2400, widthDp = 800)
@Composable
private fun PortraitPreview() {
    KlinikNetTheme(darkTheme = false) {
        TunggakanScreen(
            state = TunggakanUiState(
                rows = listOf(
                    ArrearsRow(
                        contractId = "c1",
                        renterName = "Budi Santoso",
                        vehiclePlate = "B 1235 TF",
                        renterPhone = "0812-8899-7722",
                        amount = 750000,
                        figureText = "Rp 750.000 · 3 hari",
                        daysLabel = "3 hari",
                        riskLabel = "Perhatian",
                        tone = TunggakanUiState.toneFor(RiskLevel.PERHATIAN),
                        risk = RiskLevel.PERHATIAN,
                        oldestDayText = "14 Okt",
                        isEnded = false,
                    ),
                ),
            ),
            onSelect = {},
            onRiskFilterChange = {},
            onErrorDismiss = {},
            onRemind = {},
            onReminderChange = {},
            onReminderSend = {},
            onReminderCopy = {},
            onReminderDismiss = {},
            onReminderLinkOpened = {},
            onReminderOpenFailed = {},
            pendingLink = null,
        )
    }
}

@Preview(showBackground = true, heightDp = 1200, widthDp = 800)
@Composable
private fun EmptyPreview() {
    KlinikNetTheme(darkTheme = false) {
        TunggakanScreen(
            state = TunggakanUiState(isLoading = false),
            onSelect = {},
            onRiskFilterChange = {},
            onErrorDismiss = {},
            onRemind = {},
            onReminderChange = {},
            onReminderSend = {},
            onReminderCopy = {},
            onReminderDismiss = {},
            onReminderLinkOpened = {},
            onReminderOpenFailed = {},
            pendingLink = null,
        )
    }
}
