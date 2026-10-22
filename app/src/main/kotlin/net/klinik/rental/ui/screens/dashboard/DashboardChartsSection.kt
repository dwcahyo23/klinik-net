package net.klinik.rental.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnBar
import net.klinik.rental.ui.components.KnBarChart
import net.klinik.rental.ui.components.KnCard
import net.klinik.rental.ui.components.KnCardHeader
import net.klinik.rental.ui.components.KnDonutChart
import net.klinik.rental.ui.components.KnLineChart
import net.klinik.rental.ui.components.KnProgressBar
import net.klinik.rental.ui.components.KnSlice
import net.klinik.rental.ui.components.KnTableHeaderRow
import net.klinik.rental.ui.components.KnTableRow
import net.klinik.rental.ui.components.KnTableCell
import net.klinik.rental.ui.components.donutPaletteColour
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The dashboard's charts, its period comparison, and its transaction table —
 * `dashboard.html:58-117`.
 *
 * ## Why this is a second file rather than more of [DashboardScreen]
 *
 * Because `DashboardScreen` is the *morning* screen — four figures and four doors — and this is the
 * **dashboard** the prototype puts behind its own rail item. They share a route today because the
 * app opens where the prototype opens, but they are two different readings of the same data and a
 * 900-line screen would make the next one harder to find.
 *
 * ## Why nothing here formats a figure
 *
 * Every number arrives formatted in [DashboardCharts]. The only strings assembled here are the
 * sentences the resources already hold, which is the rule `DashboardUiState`'s KDoc gives.
 */
@Composable
internal fun DashboardChartsSection(
    charts: DashboardCharts,
    onSeeAllTransactions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        TrendCard(charts)
        WeeklyCard(charts)
        DonutCard(charts)
        ComparisonCard(charts)
        RecentTable(rows = charts.recent, onSeeAll = onSeeAllTransactions)
    }
}

/**
 * `.chart-line` in its card — the 14-day series, its target, and the badge that reads the last day.
 *
 * ## Why the cards are stacked rather than laid side by side
 *
 * The prototype's own rows are `flex-wrap` with `min-width` floors of 420dp and 340dp, so at
 * tablet landscape width two would share a line and at phone width they stack. This draws one per
 * line at every width: the floors are the prototype's *minimums*, and a single column is what
 * those minimums produce below 760dp — which is every phone. A `BoxWithConstraints` here would be
 * a second layout rule to keep in step with the rail's, for a row that only ever has two states.
 */
@Composable
private fun TrendCard(charts: DashboardCharts) {
    val spacing = MaterialTheme.knSpacing
    KnCard(modifier = Modifier.testTag(TAG_TREND_CARD), padding = spacing.xl) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            KnCardHeader(
                title = stringResource(R.string.beranda_chart_trend_title),
                subtitle = stringResource(R.string.beranda_chart_trend_sub, charts.trendTotalText),
                trailing = {
                    // The badge is a **fact about the latest day**, and its tone follows from
                    // that fact — the ViewModel decides neither colour nor wording.
                    KnBadge(
                        text = if (charts.trendMeetsTarget) {
                            stringResource(R.string.beranda_chart_trend_meets)
                        } else {
                            stringResource(R.string.beranda_chart_trend_short, charts.trendGapText)
                        },
                        tone = if (charts.trendMeetsTarget) KnBadgeTone.Success else KnBadgeTone.Warning,
                    )
                },
            )
            KnLineChart(
                points = charts.trend,
                // The sentence a screen reader hears instead of the picture. Built from the two
                // figures the card already shows, so it cannot disagree with them.
                description = stringResource(R.string.beranda_chart_trend_title) + ", " +
                    charts.trendTotalText,
                target = charts.trendTarget.takeIf { it > 0L },
                labels = charts.trendLabels,
            )
            LegendRow()
        }
    }
}

/**
 * The two legend swatches under the line.
 *
 * `shared.css:764-766`. A legend rather than labelled axes: the chart draws no numbers on its
 * gridlines, so the swatches are what tell the owner which line is which.
 */
@Composable
private fun LegendRow() {
    val spacing = MaterialTheme.knSpacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.sm)
            .testTag(TAG_LEGEND),
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        LegendSwatch(MaterialTheme.knColors.primaryFill, stringResource(R.string.beranda_chart_legend_realised))
        LegendSwatch(MaterialTheme.knColors.borderStrong, stringResource(R.string.beranda_chart_legend_target))
    }
}

@Composable
private fun LegendSwatch(colour: Color, label: String) {
    val spacing = MaterialTheme.knSpacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(
            Modifier
                .size(11.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colour),
        )
        Text(
            text = label,
            style = MaterialTheme.knTypography.overline,
            color = MaterialTheme.knColors.textSecondary,
            maxLines = 1,
        )
    }
}

/** `#weekly-bars` — four bars, and the weekly target they are measured against. */
@Composable
private fun WeeklyCard(charts: DashboardCharts) {
    val spacing = MaterialTheme.knSpacing
    KnCard(modifier = Modifier.testTag(TAG_WEEKLY_CARD), padding = spacing.xl) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            KnCardHeader(
                title = stringResource(R.string.beranda_chart_weekly_title),
                subtitle = stringResource(R.string.beranda_chart_weekly_sub),
            )
            KnBarChart(
                bars = charts.weeks.mapIndexed { index, total ->
                    KnBar(
                        label = weekLabel(index, charts.weeks.size),
                        value = total,
                        valueText = charts.comparison.getOrNull(index)?.collectedText.orEmpty(),
                    )
                },
                description = stringResource(R.string.beranda_chart_weekly_title),
                target = charts.weekTarget,
            )
        }
    }
}

/**
 * `M-3`, `M-2`, `M-1`, `M-0` left to right.
 *
 * Derived from the position rather than stored beside the value: two lists that must stay in step
 * are two lists that can fall out of it, and the label is a function of the index by construction.
 */
private fun weekLabel(index: Int, count: Int): String = "M-" + (count - 1 - index)

/** `#donut` — the per-vehicle shares with their legend. */
@Composable
private fun DonutCard(charts: DashboardCharts) {
    val spacing = MaterialTheme.knSpacing
    val total = charts.slices.sum()
    KnCard(modifier = Modifier.testTag(TAG_DONUT_CARD), padding = spacing.xl) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            KnCardHeader(
                title = stringResource(R.string.beranda_chart_donut_title),
                subtitle = stringResource(R.string.beranda_chart_donut_sub),
            )
            KnDonutChart(
                slices = charts.slices.mapIndexed { index, amount ->
                    KnSlice(
                        label = charts.sliceLabels.getOrNull(index).orEmpty(),
                        value = amount,
                        // The legend's own figure — `dashboard.html:318` prints the slice's value,
                        // not its share, because "Rp 4.2 jt" is what the owner can reconcile.
                        valueText = charts.comparisonAmountText(amount),
                        colour = donutPaletteColour(index),
                    )
                },
                // The hole carries the **sum of the slices**, not the period's total income: a
                // ring whose parts do not add to its centre is a chart nobody can check.
                centreText = charts.comparisonAmountText(total),
                centreLabel = stringResource(R.string.beranda_chart_donut_centre),
                description = stringResource(R.string.beranda_chart_donut_title),
            )
        }
    }
}

/**
 * `#compare-rows` — the weekly target, and each week's progress against it.
 *
 * A progress bar rather than a second bar chart: this card answers "how close", which is a
 * fraction, and four bars that all need the same ceiling would repeat the chart above it.
 */
@Composable
private fun ComparisonCard(charts: DashboardCharts) {
    val spacing = MaterialTheme.knSpacing
    KnCard(modifier = Modifier.testTag(TAG_COMPARE_CARD), padding = spacing.xl) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            KnCardHeader(
                title = stringResource(R.string.beranda_chart_compare_title),
                subtitle = stringResource(R.string.beranda_chart_compare_sub),
            )
            Text(
                text = stringResource(
                    R.string.beranda_chart_compare_target,
                    charts.comparison.firstOrNull()?.targetText.orEmpty(),
                ),
                style = MaterialTheme.knTypography.overline,
                color = MaterialTheme.knColors.textTertiary,
            )
            charts.comparison.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.labelText,
                            style = MaterialTheme.knTypography.label,
                            color = MaterialTheme.knColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = row.collectedText,
                            style = MaterialTheme.knTypography.figure,
                            color = MaterialTheme.knColors.textPrimary,
                            maxLines = 1,
                        )
                    }
                    KnProgressBar(
                        fraction = row.fraction,
                        tone = if (row.fraction >= 1f) KnBadgeTone.Success else KnBadgeTone.Warning,
                    )
                }
            }
        }
    }
}

/**
 * `#recent-rows` — "Transaksi terakhir".
 *
 * ## Why this uses [KnTableRow]
 *
 * Because it is the prototype's most-used data pattern and, until this table, [KnTableRow] had
 * **zero call sites** — a component written, documented, and never reached. A table is the one
 * shape that answers "show me many rows with the same columns", and a list of cards pretending to
 * be one is what the audit found.
 *
 * The empty state is a sentence rather than a bare header band: a table with no rows and no
 * explanation reads as a read that failed.
 */
@Composable
private fun RecentTable(rows: List<RecentTransaction>, onSeeAll: () -> Unit) {
    val spacing = MaterialTheme.knSpacing
    KnCard(modifier = Modifier.testTag(TAG_RECENT_TABLE), padding = spacing.xl) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.beranda_recent_title),
                    style = MaterialTheme.knTypography.bodyLarge,
                    color = MaterialTheme.knColors.textPrimary,
                )
                KnBadge(
                    text = stringResource(R.string.beranda_recent_see_all),
                    tone = KnBadgeTone.Neutral,
                    onClick = onSeeAll,
                    modifier = Modifier.testTag(TAG_RECENT_SEE_ALL),
                )
            }
            KnTableHeaderRow(
                headers = listOf(
                    stringResource(R.string.beranda_recent_col_date),
                    stringResource(R.string.beranda_recent_col_renter),
                    stringResource(R.string.beranda_recent_col_plate),
                    stringResource(R.string.beranda_recent_col_bank),
                    stringResource(R.string.beranda_recent_col_reference),
                    stringResource(R.string.beranda_recent_col_amount),
                ),
            )
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.beranda_recent_empty),
                    style = MaterialTheme.knTypography.body,
                    color = MaterialTheme.knColors.textTertiary,
                    modifier = Modifier.padding(vertical = spacing.md),
                )
            } else {
                rows.forEach { row ->
                    KnTableRow(
                        cells = listOf(
                            KnTableCell(text = row.dateText, bold = true),
                            KnTableCell(text = row.renterName),
                            KnTableCell(text = row.plate),
                            KnTableCell(text = row.bank),
                            KnTableCell(text = row.reference),
                            KnTableCell(text = row.amountText, numeric = true),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * A figure for a chart's legend, formatted the way the rest of the app formats money.
 *
 * The slices and the donut's centre both need a formatted amount that `DashboardCharts` does not
 * carry pre-rendered — the comparison rows carry their own, because they show a target beside it.
 * One formatter, called here rather than interpolated.
 */
private fun DashboardCharts.comparisonAmountText(amount: Long): String = formatRupiah(amount)

internal const val TAG_TREND_CARD = "beranda-chart-trend"
internal const val TAG_WEEKLY_CARD = "beranda-chart-weekly"
internal const val TAG_DONUT_CARD = "beranda-chart-donut"
internal const val TAG_COMPARE_CARD = "beranda-chart-compare"
internal const val TAG_RECENT_TABLE = "beranda-recent-table"
internal const val TAG_RECENT_SEE_ALL = "beranda-recent-see-all"
internal const val TAG_LEGEND = "beranda-chart-legend"
