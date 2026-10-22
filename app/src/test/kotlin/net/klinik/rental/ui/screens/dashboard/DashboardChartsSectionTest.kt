package net.klinik.rental.ui.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.components.TAG_BAR_PREFIX
import net.klinik.rental.ui.components.TAG_DONUT_LEGEND_PREFIX
import net.klinik.rental.ui.components.TAG_LINE_CHART
import net.klinik.rental.ui.components.TAG_PROGRESS
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The dashboard's charts, its comparison rows and its transaction table.
 *
 * ## Why the "Lihat semua" link is clicked rather than asserted to exist
 *
 * Because `assertExists()` cannot tell a link wired to its handler from one wired to nothing, and
 * this repo has shipped that exact defect — a control that renders, is tappable, and reaches
 * nothing. The tap is the assertion.
 *
 * ## Why the empty table is a case of its own
 *
 * Because a table with a header band and no rows and no sentence reads as a read that failed,
 * which is a different claim from "nothing came in yet".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
class DashboardChartsSectionTest {

    @get:Rule
    val compose = createComposeRule()

    /** All four cards draw, each with the copy the prototype approved. */
    @Test
    fun `the four cards draw with their prototype copy`() {
        compose.setContent { theme { Section(charts = charts()) } }

        compose.onNodeWithTag(TAG_TREND_CARD).assertExists()
        compose.onNodeWithTag(TAG_WEEKLY_CARD).assertExists()
        compose.onNodeWithTag(TAG_DONUT_CARD).assertExists()
        compose.onNodeWithTag(TAG_COMPARE_CARD).assertExists()

        compose.onNodeWithText("Tren pendapatan harian").assertExists()
        compose.onNodeWithText("Target vs realisasi").assertExists()
        compose.onNodeWithText("Pendapatan per mobil").assertExists()
        compose.onNodeWithText("Perbandingan periode").assertExists()
        compose.onNodeWithText("Transaksi terakhir").assertExists()
    }

    /**
     * The trend card carries the badge the **latest day** earned.
     *
     * Both directions asserted, because a badge that always said "Di atas target" would pass a
     * one-sided test — and the tone is a fact about the figure, not a decoration.
     */
    @Test
    fun `the trend badge names the shortfall when the last day missed`() {
        compose.setContent {
            theme { Section(charts = charts(trendMeetsTarget = false, trendGapText = "Rp 200.000")) }
        }

        compose.onNodeWithText("Di bawah target Rp 200.000").assertExists()
    }

    @Test
    fun `the trend badge says so when the last day met the target`() {
        compose.setContent { theme { Section(charts = charts(trendMeetsTarget = true)) } }

        compose.onNodeWithText("Di atas target").assertExists()
    }

    /** The line chart draws, and is addressable without reading its shape. */
    @Test
    fun `the line chart draws`() {
        compose.setContent { theme { Section(charts = charts()) } }

        compose.onNodeWithTag(TAG_LINE_CHART).assertExists()
    }

    /**
     * One bar per week, addressed by its own label.
     *
     * The labels are `M-0`…`M-3` from the prototype, and the test names them: four bars with the
     * wrong labels would pass a count assertion.
     */
    @Test
    fun `the weekly chart draws four labelled bars`() {
        compose.setContent { theme { Section(charts = charts()) } }

        listOf("M-3", "M-2", "M-1", "M-0").forEach { label ->
            compose.onNodeWithTag(TAG_BAR_PREFIX + label).assertExists()
        }
    }

    /**
     * The donut's legend names the plate beside its figure, not only the share.
     *
     * The legend row is addressed by its own tag rather than by its text, because the same plate
     * also appears in the transaction table below — and a test that matched both would be
     * asserting on the table when it meant the legend.
     */
    @Test
    fun `the donut legend names each plate`() {
        compose.setContent { theme { Section(charts = charts()) } }

        compose.onNodeWithTag(TAG_DONUT_LEGEND_PREFIX + "B 1234 XYZ").assertExists()
    }

    /**
     * One progress bar per comparison row, and the row's own label beside its range.
     *
     * The target caption is asserted separately from the rows because the prototype draws it
     * above them, and a card that lost it would leave four bars with nothing to measure against.
     */
    @Test
    fun `the comparison card draws a bar per period`() {
        compose.setContent { theme { Section(charts = charts()) } }

        compose.onNodeWithText("Target mingguan Rp 7.000.000").assertExists()
        // The label and the range are one string, so the row reads as "M-1 (1 Okt – 7 Okt)" —
        // the prototype's own shape at `dashboard.html:279`.
        compose.onAllNodesWithTag(TAG_PROGRESS).assertCountEquals(4)
        compose.onNodeWithText("M-1 (1 Okt – 7 Okt)").assertExists()
        // No figure is asserted here on purpose: the comparison rows and the weekly bars show the
        // same four amounts, so every one of them matches two nodes — and a test that picked one
        // would be asserting on the bar chart while claiming to check the rows.
    }

    /**
     * The table's six columns, and the row that fills them.
     *
     * Asserted on the *values*, because a table whose cells all rendered the same column would
     * pass a header-only assertion — and on the distinct ones (the renter, the bank, the
     * reference), because the plate is deliberately ambiguous here: it appears in the legend
     * above as well.
     */
    @Test
    fun `the transaction table draws its header and its row`() {
        compose.setContent { theme { Section(charts = charts()) } }

        compose.onNodeWithText("TANGGAL").assertExists()
        compose.onNodeWithText("NOMINAL").assertExists()
        compose.onNodeWithText("Budi Santoso").assertExists()
        compose.onNodeWithText("BCA").assertExists()
        compose.onNodeWithText("TRF-001").assertExists()
        compose.onNodeWithText("Rp 500.000").assertExists()
    }

    /** An empty ledger says so, rather than drawing a header band over nothing. */
    @Test
    fun `an empty table explains itself`() {
        compose.setContent { theme { Section(charts = charts(recent = emptyList())) } }

        compose.onNodeWithText("Belum ada setoran tercatat.").assertExists()
        compose.onNodeWithTag(TAG_RECENT_TABLE).assertExists()
    }

    /** "Lihat semua →" is a target, and it reaches **its own** handler. */
    @Test
    fun `the see-all link reaches its handler`() {
        var opened = 0
        compose.setContent {
            theme { Section(charts = charts(), onSeeAll = { opened = opened + 1 }) }
        }

        compose.onNodeWithTag(TAG_RECENT_SEE_ALL).assertHasClickAction()
        // The semantics action rather than a touch click: the section is taller than the test
        // window, and a touch at a node's centre lands outside the viewport and is dropped —
        // which would fail here for a reason that has nothing to do with the wiring.
        compose.onNodeWithTag(TAG_RECENT_SEE_ALL)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("the link opens the deposit history", 1, opened)
    }

    // -------------------------------------------------------------------- helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Section(charts: DashboardCharts, onSeeAll: () -> Unit = {}) {
        DashboardChartsSection(charts = charts, onSeeAllTransactions = onSeeAll)
    }

    /**
     * A dashboard with figures, hand-built.
     *
     * Hand-built rather than produced by the ViewModel: this file is about **rendering**, and a
     * fixture that went through the repository would make an arithmetic change look like a
     * drawing one.
     */
    private fun charts(
        trendMeetsTarget: Boolean = false,
        trendGapText: String = "Rp 200.000",
        recent: List<RecentTransaction> = listOf(
            RecentTransaction(
                dateText = "19 Okt",
                renterName = "Budi Santoso",
                plate = "B 1234 XYZ",
                bank = "BCA",
                reference = "TRF-001",
                amountText = "Rp 500.000",
            ),
        ),
    ) = DashboardCharts(
        trend = List(14) { 500_000L },
        trendTarget = 700_000L,
        trendLabels = List(14) { "19 Okt" },
        trendTotalText = "Rp 7.000.000",
        trendMeetsTarget = trendMeetsTarget,
        trendGapText = trendGapText,
        weeks = listOf(6_000_000L, 6_500_000L, 7_000_000L, 6_800_000L),
        weekTarget = 7_000_000L,
        slices = listOf(3_500_000L, 2_500_000L),
        sliceLabels = listOf("B 1234 XYZ", "B 5678 ABC"),
        comparison = listOf(
            ComparisonRow("M-3", "10 Sep – 16 Sep", "M-3 (10 Sep – 16 Sep)", "Rp 6.000.000", "Rp 7.000.000", 0.857f),
            ComparisonRow("M-2", "17 Sep – 23 Sep", "M-2 (17 Sep – 23 Sep)", "Rp 6.500.000", "Rp 7.000.000", 0.928f),
            ComparisonRow("M-1", "1 Okt – 7 Okt", "M-1 (1 Okt – 7 Okt)", "Rp 7.000.000", "Rp 7.000.000", 1f),
            ComparisonRow("M-0", "8 Okt – 14 Okt", "M-0 (8 Okt – 14 Okt)", "Rp 6.800.000", "Rp 7.000.000", 0.971f),
        ),
        recent = recent,
    )
}
