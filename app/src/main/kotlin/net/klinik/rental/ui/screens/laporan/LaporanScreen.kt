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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.UserCheck
import com.composables.icons.lucide.Wallet
import net.klinik.rental.R
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnKpiTile
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
 * Because the period is the app's billing boundary, and a free date range would let the owner
 * cut a window the allocation engine never charged — producing a report of a period that does
 * not exist. Chips carrying [CutoffPeriod.label] mean a period's name and the days the engine
 * charged for it are the same string.
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
 */
@Composable
fun LaporanScreen(
    state: LaporanUiState,
    periodOptions: List<String>,
    onSelectPeriod: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        Column(Modifier.fillMaxSize()) {
            KnTopBar(title = stringResource(R.string.laporan_title))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                PeriodPicker(
                    options = periodOptions,
                    selectedIndex = 0,
                    onSelect = onSelectPeriod,
                )

                if (state.isLoading) {
                    // A skeleton per tile, for the same reason Beranda has one: four zeros
                    // read as a month with no money in it, which is a claim rather than a
                    // placeholder.
                    repeat(4) { MetricSkeleton(modifier = Modifier.weight(1f)) }
                } else if (state.isQuietPeriod) {
                    QuietPeriod()
                } else {
                    Metrics(state)
                    Trend(state)
                    RiskBreakdown(state)
                }
            }
        }
    }
}

/**
 * The period chips, newest first.
 *
 * `KnChip` rather than a dropdown because the periods are few, fixed, and each one is a month
 * the owner may want to check; a menu behind one tap would hide what this screen is for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val spacing = MaterialTheme.knSpacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = stringResource(R.string.laporan_period_label),
            style = MaterialTheme.knTypography.label,
            color = MaterialTheme.knColors.textSecondary,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_PERIOD_PICKER),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            options.forEachIndexed { index, label ->
                KnChip(
                    text = label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.testTag(periodTag(index)),
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

/** One tile's placeholder. */
@Composable
private fun MetricSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(116.dp)
            .testTag(TAG_METRIC_SKELETON),
        color = MaterialTheme.knColors.bgSunken,
    ) {}
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

/** A period chip's tag, keyed on its index so the two cannot drift. */
internal fun periodTag(index: Int): String = "laporan-period-$index"

/** One risk row's tag. */
internal fun riskTag(row: RiskRow): String = "laporan-risk-${row.label.lowercase()}"

internal fun riskCountTag(row: RiskRow): String = "laporan-risk-count-${row.label.lowercase()}"

internal const val TAG_PERIOD_PICKER = "laporan-period-picker"
internal const val TAG_METRICS = "laporan-metrics"
internal const val TAG_KPI_INCOME = "laporan-kpi-income"
internal const val TAG_KPI_ARREARS = "laporan-kpi-arrears"
internal const val TAG_KPI_SETTLED = "laporan-kpi-settled"
internal const val TAG_KPI_TREND = "laporan-kpi-trend"
internal const val TAG_RISK_BREAKDOWN = "laporan-risk"
internal const val TAG_METRIC_SKELETON = "laporan-metric-skeleton"
internal const val TAG_QUIET = "laporan-quiet"

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
        )
    }
}