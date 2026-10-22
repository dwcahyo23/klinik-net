package net.klinik.rental.ui.screens.laporan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.UserCheck
import com.composables.icons.lucide.Wallet
import net.klinik.rental.R
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnKpiIconLayout
import net.klinik.rental.ui.components.KnKpiTile
import net.klinik.rental.ui.components.KnKpiTileSkeleton
import net.klinik.rental.ui.components.KnTableCell
import net.klinik.rental.ui.components.KnTableHeaderRow
import net.klinik.rental.ui.components.KnTableRow
import net.klinik.rental.ui.components.KnTopBar
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * Laporan: what the month looked like.
 *
 * ## Why the period picker is chips rather than a date field
 *
 * Because the period is the app's own billing boundary, and a free date range would let the
 * owner cut a window the allocation engine never charged — producing a report of a period that
 * does not exist. Chips carrying [net.klinik.rental.domain.calculate.CutoffPeriod.label] mean
 * a period's name and the days the engine charged for it are the same string.
 *
 * ## Why there **is** a custom range, then
 *
 * Because the prototype has one and the owner asked for the prototype. The tension with the
 * paragraph above is real, and the resolution is that the custom range is the one place where
 * the report is explicitly about a window the owner chose — so it says so, in the badge beside
 * the label, rather than pretending the window is a billing period. The figures inside it are
 * still the engine's: the days billed come from the engine's own charge-day list, narrowed to
 * the chosen window.
 *
 * ## Why there is **no chart** for the risk breakdown
 *
 * Because the epic decided it, and the reason is worth keeping: four coloured numbers are read
 * in one pass, need nothing installed, and cannot go stale in a dependency. A donut would need
 * a chart library to be legible at all — which is the same argument that puts the trend line on
 * a `Canvas`.
 *
 * ## Why the four KPI tiles are not accented
 *
 * Same reason Beranda's are flat: `KnKpiTile`'s `accent` parameter draws a taller edge, and
 * this screen's figures are the same kind of thing. A report that led with one figure would
 * be claiming it matters more than the others.
 *
 * @param onSelectPeriod picks another month. Required: a picker wired to `{}` shows periods
 *   that lead nowhere.
 * @param onSelectMode picks one of the three mode chips. Required, for the same reason.
 * @param onCustomFromChange records the "Dari" field. Required, likewise.
 * @param onCustomToChange records the "Sampai" field. Required, likewise.
 * @param onApplyCustomRange applies the typed range. Required, likewise.
 * @param onExportCsv opens the file picker for the CSV. Required: a button wired to `{}`
 *   renders and does nothing, which is the defect this repo has shipped three times.
 */
@Composable
fun LaporanScreen(
    state: LaporanUiState,
    periodOptions: List<String>,
    onSelectPeriod: (Int) -> Unit,
    onSelectMode: (ReportMode) -> Unit,
    onCustomFromChange: (String) -> Unit,
    onCustomToChange: (String) -> Unit,
    onApplyCustomRange: () -> Unit,
    onExportCsv: () -> Unit,
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
                title = stringResource(R.string.laporan_title),
                // The export lives in the **bar**, matching `reports.html:28-38`: it is
                // the screen's action, not a card's. And the bar is where the theme toggle is
                // mounted, so a screen with a hand-rolled heading loses that control.
                //
                // One button, not two. The prototype's second action was "Cetak / PDF", and
                // the app rendered it wired to `{}` — a control that reaches nothing, which
                // this repo has shipped three times. Story 4.5's Never-list and the
                // architecture's deferred table both rule out a print export, so the button
                // is gone rather than waiting to be implemented.
                actions = {
                    KnButton(
                        label = stringResource(R.string.laporan_export_csv),
                        onClick = onExportCsv,
                        icon = Lucide.Download,
                        variant = KnButtonVariant.Primary,
                        enabled = !state.isExporting,
                        modifier = Modifier.testTag(TAG_EXPORT_CSV),
                    )
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // `--sp-6`, the prototype's page gutter (`shared.css:285-289`).
                    .padding(spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                PeriodPicker(
                    state = state,
                    options = periodOptions,
                    onSelectPeriod = onSelectPeriod,
                    onSelectMode = onSelectMode,
                    onCustomFromChange = onCustomFromChange,
                    onCustomToChange = onCustomToChange,
                    onApplyCustomRange = onApplyCustomRange,
                )

                if (state.isLoading) {
                    // A skeleton per tile, for the same reason Beranda has one: four zeros
                    // read as a month with no money in it, which is a claim rather than a
                    // placeholder.
                    repeat(4) { MetricSkeleton(modifier = Modifier.weight(1f)) }
                } else {
                    // The four tiles are **always** drawn, and the quiet-period note sits
                    // **below** them.
                    //
                    // The prototype does that (`reports.html:77` — the KPI grid is
                    // unconditional, and a period with no money shows four zeroes and a
                    // note). Replacing the tiles with the note meant the owner of a quiet
                    // month saw no figures at all, which reads as a broken screen rather
                    // than as a quiet month.
                    Metrics(state)
                    if (state.isQuietPeriod) {
                        QuietPeriod()
                    }
                    Trend(state)
                    RiskBreakdown(state)
                    BreakdownTable(state)
                }

                // The period history sits **below** the figures, not above them. It is a
                // record of what the other months came to — a second question, answered after
                // this period's.
                PeriodHistory(state = state, onSelectPeriod = onSelectPeriod)
            }
        }
    }
}

/**
 * The period card: the three mode chips, the custom picker, and the period history.
 *
 * The card is the prototype's own (`reports.html:43-74`) — the period's name and its badge on
 * one line, the chips under them, and the two date fields when custom is chosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodPicker(
    state: LaporanUiState,
    options: List<String>,
    onSelectPeriod: (Int) -> Unit,
    onSelectMode: (ReportMode) -> Unit,
    onCustomFromChange: (String) -> Unit,
    onCustomToChange: (String) -> Unit,
    onApplyCustomRange: () -> Unit,
) {
    val spacing = MaterialTheme.knSpacing
    net.klinik.rental.ui.components.KnCard(modifier = Modifier.testTag(TAG_PERIOD_CARD)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.laporan_period_label),
                        style = MaterialTheme.knTypography.title,
                        color = MaterialTheme.knColors.textPrimary,
                    )
                    Text(
                        text = state.periodLabel,
                        style = MaterialTheme.knTypography.body,
                        color = MaterialTheme.knColors.textSecondary,
                        modifier = Modifier.testTag(TAG_PERIOD_LABEL),
                    )
                }
                // The badge says which mode is on. A report whose figures come from a custom
                // window but whose badge says "Periode berjalan" is telling the owner two
                // different things about the same six numbers.
                KnBadge(
                    text = stringResource(state.mode.labelRes()),
                    tone = if (state.mode == ReportMode.Custom) KnBadgeTone.Info else KnBadgeTone.Brand,
                    modifier = Modifier.testTag(TAG_PERIOD_BADGE),
                )
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_PERIOD_PICKER),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                // The three mode chips, and then the six periods. The periods are the
                // prototype's own row; the modes sit above them because they are the
                // coarser question — *which* set of days, before *which* month.
                KnChip(
                    text = stringResource(R.string.laporan_mode_current),
                    selected = state.mode == ReportMode.Current,
                    onClick = { onSelectMode(ReportMode.Current) },
                    modifier = Modifier.testTag(reportModeTag(ReportMode.Current)),
                )
                KnChip(
                    text = stringResource(R.string.laporan_mode_previous),
                    selected = state.mode == ReportMode.Previous,
                    onClick = { onSelectMode(ReportMode.Previous) },
                    modifier = Modifier.testTag(reportModeTag(ReportMode.Previous)),
                )
                KnChip(
                    text = stringResource(R.string.laporan_mode_custom),
                    selected = state.mode == ReportMode.Custom,
                    onClick = { onSelectMode(ReportMode.Custom) },
                    modifier = Modifier.testTag(reportModeTag(ReportMode.Custom)),
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                options.forEachIndexed { index, label ->
                    KnChip(
                        text = label,
                        selected = state.mode == ReportMode.Current && index == 0,
                        onClick = { onSelectPeriod(index) },
                        modifier = Modifier.testTag(periodTag(index)),
                    )
                }
            }

            // The two date fields, only in custom mode. A date field that is not part of the
            // current mode is a field the owner will fill and expect to change nothing.
            state.customRange?.let { range ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    KnField(
                        label = stringResource(R.string.laporan_custom_from),
                        value = range.fromText,
                        onValueChange = onCustomFromChange,
                        placeholder = stringResource(R.string.laporan_custom_placeholder),
                        error = range.error,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TAG_CUSTOM_FROM),
                    )
                    KnField(
                        label = stringResource(R.string.laporan_custom_to),
                        value = range.toText,
                        onValueChange = onCustomToChange,
                        placeholder = stringResource(R.string.laporan_custom_placeholder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TAG_CUSTOM_TO),
                    )
                    KnButton(
                        label = stringResource(R.string.laporan_custom_apply),
                        onClick = onApplyCustomRange,
                        variant = KnButtonVariant.Primary,
                        modifier = Modifier.testTag(TAG_CUSTOM_APPLY),
                    )
                }
            }
        }
    }
}

/**
 * The period history — the prototype's "Riwayat periode".
 *
 * Each row is a period's own total, and tapping one reads the report at that period. The
 * rows are **not** the mode chips: a history is a record of what has been, and a chip is a
 * choice of what to read.
 */
@Composable
private fun PeriodHistory(
    state: LaporanUiState,
    onSelectPeriod: (Int) -> Unit,
) {
    val spacing = MaterialTheme.knSpacing
    if (state.periodHistory.isEmpty()) return

    net.klinik.rental.ui.components.KnCard(modifier = Modifier.testTag(TAG_PERIOD_HISTORY)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = stringResource(R.string.laporan_history_title),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
            )
            state.periodHistory.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(periodHistoryTag(index)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.knTypography.body,
                            color = MaterialTheme.knColors.textPrimary,
                        )
                        KnBadge(
                            text = entry.statusText,
                            tone = if (entry.statusText == stringResource(R.string.laporan_period_running)) {
                                KnBadgeTone.Info
                            } else {
                                KnBadgeTone.Neutral
                            },
                        )
                    }
                    Text(
                        text = entry.totalText,
                        style = MaterialTheme.knTypography.figure,
                        color = MaterialTheme.knColors.textPrimary,
                    )
                }
            }
        }
    }
}

/**
 * The report's own table — "Rekap per penyewa & mobil".
 *
 * One row per contract, with the days billed, what was billed, what came in and what is
 * still owed. The **arrears column is the arrears list's own figure**, not `billed −
 * received`: those are two different questions, and a table that answered the second under
 * the first's heading would disagree with the list the owner reads beside it.
 */
@Composable
private fun BreakdownTable(state: LaporanUiState) {
    val spacing = MaterialTheme.knSpacing
    if (state.breakdown.isEmpty()) return

    net.klinik.rental.ui.components.KnCard(modifier = Modifier.testTag(TAG_BREAKDOWN)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.laporan_table_title),
                    style = MaterialTheme.knTypography.title,
                    color = MaterialTheme.knColors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.laporan_table_count, state.breakdown.size),
                    style = MaterialTheme.knTypography.label,
                    color = MaterialTheme.knColors.textTertiary,
                )
            }
            KnTableHeaderRow(
                headers = listOf(
                    stringResource(R.string.laporan_table_renter),
                    stringResource(R.string.laporan_table_plate),
                    stringResource(R.string.laporan_table_days),
                    stringResource(R.string.laporan_table_billed),
                    stringResource(R.string.laporan_table_received),
                    stringResource(R.string.laporan_table_arrears),
                    stringResource(R.string.laporan_table_status),
                ),
                modifier = Modifier.testTag(TAG_BREAKDOWN_HEADER),
            )
            state.breakdown.forEachIndexed { index, row ->
                KnTableRow(
                    cells = listOf(
                        KnTableCell(row.renterName),
                        KnTableCell(row.plate),
                        KnTableCell(row.daysBilled.toString(), numeric = true),
                        KnTableCell(row.billedText, numeric = true),
                        KnTableCell(row.receivedText, numeric = true),
                        KnTableCell(row.arrearsText, numeric = true),
                        KnTableCell(
                            text = stringResource(
                                if (row.isSettled) R.string.laporan_status_settled
                                else R.string.laporan_status_arrears,
                            ),
                            badge = if (row.isSettled) null else stringResource(R.string.laporan_status_arrears),
                            badgeTone = KnBadgeTone.Danger,
                        ),
                    ),
                    modifier = Modifier.testTag(breakdownRowTag(index)),
                )
            }
        }
    }
}

/** The four period figures, flat. See the class KDoc on why none leads. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Metrics(state: LaporanUiState) {
    val spacing = MaterialTheme.knSpacing
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_METRICS),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        KnKpiTile(
            label = stringResource(R.string.laporan_kpi_income),
            value = state.incomeText,
            icon = Lucide.Wallet,
            modifier = Modifier
                .weight(1f)
                .testTag(TAG_KPI_INCOME),
            accent = false,
        )
        KnKpiTile(
            label = stringResource(R.string.laporan_kpi_arrears),
            value = state.arrearsText,
            icon = Lucide.CircleAlert,
            modifier = Modifier
                .weight(1f)
                .testTag(TAG_KPI_ARREARS),
            accent = false,
        )
        KnKpiTile(
            label = stringResource(R.string.laporan_kpi_settled),
            value = state.settledText,
            icon = Lucide.UserCheck,
            modifier = Modifier
                .weight(1f)
                .testTag(TAG_KPI_SETTLED),
            accent = false,
        )
        KnKpiTile(
            label = stringResource(R.string.laporan_kpi_trend),
            value = state.trendTotalText,
            icon = Lucide.TrendingUp,
            modifier = Modifier
                .weight(1f)
                .testTag(TAG_KPI_TREND),
            accent = false,
        )
    }
}

/** The seven-day line, under the figures it summarises. */
@Composable
private fun Trend(state: LaporanUiState) {
    val spacing = MaterialTheme.knSpacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        TrendLine(
            points = state.trend,
            description = stringResource(R.string.laporan_kpi_trend) +
                ": " + state.trendTotalText,
        )
    }
}

/**
 * The four ladder levels as four numbers.
 *
 * The count and its colour sit together so neither carries the claim alone: colour alone would
 * leave a screen-reader user with four identical tones, and the number alone would lose the
 * one glance that says "three of mine are critical".
 */
@Composable
private fun RiskBreakdown(state: LaporanUiState) {
    val spacing = MaterialTheme.knSpacing
    val rows = state.risk

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_RISK_BREAKDOWN),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.laporan_risk_title),
            style = MaterialTheme.knTypography.label,
            color = MaterialTheme.knColors.textSecondary,
        )
        rows.forEach { row ->
            RiskNumber(row = row, modifier = Modifier.testTag(riskTag(row)))
        }
    }
}

/** One level: its colour, its name and its count. */
@Composable
private fun RiskNumber(row: RiskRow, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.knSpacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.knTypography.body,
            color = MaterialTheme.knColors.textSecondary,
        )
        Text(
            text = row.count.toString(),
            style = MaterialTheme.knTypography.figure,
            color = toneColour(row.tone),
            textAlign = TextAlign.End,
            modifier = Modifier.testTag(riskCountTag(row)),
        )
    }
}

/**
 * One tile's placeholder.
 *
 * [KnKpiTileSkeleton] rather than a local box: this screen's tiles use the **stacked** glyph,
 * so the skeleton has to reserve that shape — and it reads the tile's own line heights, so the
 * row cannot reflow when the figures land.
 */
@Composable
private fun MetricSkeleton(modifier: Modifier = Modifier) {
    KnKpiTileSkeleton(
        iconLayout = KnKpiIconLayout.Stacked,
        modifier = modifier.testTag(TAG_METRIC_SKELETON),
    )
}

/**
 * A period with nothing in it.
 *
 * Says what to do rather than only that there is nothing — the same rule every empty state in
 * this app follows, because "nothing here" without a next step reads as a broken screen.
 */
@Composable
private fun QuietPeriod() {
    val spacing = MaterialTheme.knSpacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_QUIET),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.laporan_empty_title),
            style = MaterialTheme.knTypography.body,
            color = MaterialTheme.knColors.textPrimary,
        )
        Text(
            text = stringResource(R.string.laporan_empty_body),
            style = MaterialTheme.knTypography.body,
            color = MaterialTheme.knColors.textSecondary,
        )
    }
}

/**
 * The colour a badge tone draws in.
 *
 * @Composable because the tone is a **semantic** role rather than a colour: it reads
 * `MaterialTheme` here so a screen cannot pass a level's tone in and pair it with some other
 * colour at the call site. That pairing is where "the figure says 9 hari and the badge is
 * green" would come from.
 */
@Composable
private fun toneColour(tone: KnBadgeTone) = when (tone) {
    KnBadgeTone.Success -> MaterialTheme.knColors.semantic.success
    KnBadgeTone.Info -> MaterialTheme.knColors.semantic.info
    KnBadgeTone.Warning -> MaterialTheme.knColors.semantic.warning
    KnBadgeTone.Danger -> MaterialTheme.knColors.semantic.danger
    KnBadgeTone.Brand -> MaterialTheme.knColors.primaryFill
    KnBadgeTone.Neutral -> MaterialTheme.knColors.neutralMarker
}

/** A mode chip's tag. */
internal fun reportModeTag(mode: ReportMode): String = "laporan-mode-${mode.name}"

/** A period chip's tag, keyed on its index so the two cannot drift. */
internal fun periodTag(index: Int): String = "laporan-period-$index"

/** A history row's tag, keyed on its index. */
internal fun periodHistoryTag(index: Int): String = "laporan-history-$index"

/** A breakdown row's tag, keyed on its index. */
internal fun breakdownRowTag(index: Int): String = "laporan-row-$index"

/** One risk row's tag. */
internal fun riskTag(row: RiskRow): String = "laporan-risk-${row.label.lowercase()}"

internal fun riskCountTag(row: RiskRow): String = "laporan-risk-count-${row.label.lowercase()}"

internal const val TAG_PERIOD_PICKER = "laporan-period-picker"
internal const val TAG_PERIOD_CARD = "laporan-period-card"
internal const val TAG_PERIOD_LABEL = "laporan-period-label"
internal const val TAG_PERIOD_BADGE = "laporan-period-badge"
internal const val TAG_PERIOD_HISTORY = "laporan-period-history"
internal const val TAG_CUSTOM_FROM = "laporan-custom-from"
internal const val TAG_CUSTOM_TO = "laporan-custom-to"
internal const val TAG_CUSTOM_APPLY = "laporan-custom-apply"
internal const val TAG_BREAKDOWN = "laporan-breakdown"
internal const val TAG_BREAKDOWN_HEADER = "laporan-breakdown-header"
internal const val TAG_EXPORT_CSV = "laporan-export-csv"
internal const val TAG_METRICS = "laporan-metrics"
internal const val TAG_KPI_INCOME = "laporan-kpi-income"
internal const val TAG_KPI_ARREARS = "laporan-kpi-arrears"
internal const val TAG_KPI_SETTLED = "laporan-kpi-settled"
internal const val TAG_KPI_TREND = "laporan-kpi-trend"
internal const val TAG_RISK_BREAKDOWN = "laporan-risk"
internal const val TAG_METRIC_SKELETON = "laporan-metric-skeleton"
internal const val TAG_QUIET = "laporan-quiet"

/** A mode's own copy — one place maps the enum to its label. */
private fun ReportMode.labelRes(): Int = when (this) {
    ReportMode.Current -> R.string.laporan_mode_current
    ReportMode.Previous -> R.string.laporan_mode_previous
    ReportMode.Custom -> R.string.laporan_mode_custom
}

@Preview(showBackground = true)
@Composable
private fun LaporanScreenPreview() {
    KlinikNetTheme(darkTheme = false) {
        LaporanScreen(
            state = LaporanUiState(
                periodLabel = "25 Sep - 24 Okt 2026",
                incomeText = "Rp 1.500.000",
                arrearsText = "Rp 4.250.000",
                settledText = "3 penyewa lunas",
                trendTotalText = "Rp 750.000",
                incomeRupiah = 1_500_000,
                arrearsRupiah = 4_250_000,
                settledRenters = 3,
                risk = RiskCounts(2, 1, 3, 1).asRows { level ->
                    when (level) {
                        RiskLevel.LANCAR -> "Lancar"
                        RiskLevel.RINGAN -> "Ringan"
                        RiskLevel.PERHATIAN -> "Perhatian"
                        RiskLevel.KRITIS -> "Kritis"
                    }
                },
                trend = listOf(250_000, 0, 500_000, 0, 250_000, 500_000, 250_000),
            ),
            periodOptions = listOf("25 Sep - 24 Okt 2026", "25 Agu - 24 Sep 2026"),
            onSelectPeriod = {},
            onSelectMode = {},
            onCustomFromChange = {},
            onCustomToChange = {},
            onApplyCustomRange = {},
            onExportCsv = {},
        )
    }
}
