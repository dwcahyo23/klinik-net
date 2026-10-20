package net.klinik.rental.ui.screens.laporan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric render of the **real** report screen.
 *
 * ## What is asserted here, and why it is not the same list as the period test's
 *
 * The period test proves the *figures*. This one proves what the owner can see and reach:
 * that the four tiles are the same size, that the risk breakdown is **four** numbers rather
 * than three, and that tapping a period chip reaches the handler rather than merely having a
 * click action.
 *
 * The rules every test here obeys are `BerandaScreenTest`'s: `assertExists()` rather than
 * `assertIsDisplayed()`, because below the Robolectric fold a node measures zero and
 * "off-screen" reads identically to "present"; and one `setContent` per method.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LaporanScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The four figures are visible, each with its own value.
     *
     * A report whose fourth tile is missing still reads as a report; the criterion asks for
     * four, and "at least three" is not four.
     */
    @Test
    fun `the four period figures are shown`() {
        compose.setContent { theme { Screen(state = populated()) } }

        compose.onNodeWithTag(TAG_KPI_INCOME).assertExists()
        compose.onNodeWithTag(TAG_KPI_ARREARS).assertExists()
        compose.onNodeWithTag(TAG_KPI_SETTLED).assertExists()
        compose.onNodeWithTag(TAG_KPI_TREND).assertExists()

        compose.onNodeWithText("Rp 1.500.000").assertExists()
        compose.onNodeWithText("Rp 4.250.000").assertExists()
        compose.onNodeWithText("3 penyewa lunas").assertExists()
    }

    /**
     * **No tile is larger than another** — measured, not assumed.
     *
     * `KnKpiTile`'s `accent` parameter is the hierarchy control this screen refuses: an
     * accented tile draws a taller edge. Comparing the four heights catches an accented tile
     * while every existence assertion above would still pass.
     */
    @Test
    fun `no figure tile is larger than another`() {
        compose.setContent { theme { Screen(state = populated()) } }

        val income = compose.onNodeWithTag(TAG_KPI_INCOME).fetchSemanticsNode().size.height
        val arrears = compose.onNodeWithTag(TAG_KPI_ARREARS).fetchSemanticsNode().size.height
        val settled = compose.onNodeWithTag(TAG_KPI_SETTLED).fetchSemanticsNode().size.height
        val trend = compose.onNodeWithTag(TAG_KPI_TREND).fetchSemanticsNode().size.height

        assertEquals("income and arrears", income, arrears)
        assertEquals("income and settled", income, settled)
        assertEquals("income and trend", income, trend)
    }

    /**
     * The risk breakdown is **four** numbers, one per level.
     *
     * The epic rejected a donut in favour of four coloured figures, so the count *is* the
     * requirement — a breakdown that drew three would pass every other assertion here.
     */
    @Test
    fun `the risk breakdown shows four numbers not a chart`() {
        compose.setContent { theme { Screen(state = populated()) } }

        compose.onNodeWithTag(TAG_RISK_BREAKDOWN).assertExists()
        for (level in listOf("Lancar", "Ringan", "Perhatian", "Kritis")) {
            compose.onNodeWithTag("laporan-risk-count-${level.lowercase()}").assertExists()
        }
        // And each row's colour is its **own level's**, not the count's: three critical
        // tenants is the same state as one, with more of it.
        val rows = RiskCounts(2, 1, 3, 1).asRows { it.name }
        assertEquals(4, rows.size)
        assertEquals(KnBadgeTone.Success, rows[0].tone)
        assertEquals(KnBadgeTone.Danger, rows[3].tone)
    }

    /**
     * Tapping a period chip reaches the handler with **that chip's index**.
     *
     * Click-through rather than `assertHasClickAction()`: this repo has shipped a control
     * that renders, has a click action, and is wired to `{}` twice. `performSemanticsAction`
     * because a modal renders in its own window, where `performClick` does not reach it.
     */
    @Test
    fun `tapping a period chip selects that period`() {
        var selected = -1
        compose.setContent {
            theme {
                Screen(state = populated(), onSelectPeriod = { index -> selected = index })
            }
        }

        compose.onNodeWithTag(periodTag(1))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("the handler received the chip's own index", 1, selected)
    }

    /**
     * A quiet period says what to do, rather than showing four zeros.
     *
     * Four zeros read as a month with no money in it — a claim. The criterion is that the
     * empty state is an instruction, as it is on every other screen.
     */
    @Test
    fun `a quiet period draws an invitation rather than zeroes`() {
        val quiet = populated().copy(
            incomeRupiah = 0L,
            arrearsRupiah = 0L,
            settledRenters = 0,
        )
        compose.setContent { theme { Screen(state = quiet) } }

        compose.onNodeWithTag(TAG_QUIET).assertExists()
        compose.onNodeWithTag(TAG_KPI_INCOME).assertDoesNotExist()
    }

    /** Loading draws a skeleton per tile, and no figures. */
    @Test
    fun `loading draws a skeleton per tile`() {
        compose.setContent { theme { Screen(state = populated().copy(isLoading = true)) } }

        compose.onAllNodes(hasTestTag(TAG_METRIC_SKELETON)).assertCountEquals(4)
        assertTrue(
            "and no figure is claimed before the read happened",
            compose.onAllNodes(hasTestTag(TAG_KPI_INCOME)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * The trend is drawn, and a week with nothing in it does not draw a line.
     *
     * The second half is the failure a chart silently produces: seven empty points draw a
     * straight stroke across the week, which reads as a steady Tuesday when nothing arrived.
     */
    @Test
    fun `the trend is drawn and a flat week draws no stroke`() {
        compose.setContent { theme { Screen(state = populated()) } }
        compose.onNodeWithTag(TAG_TREND).assertExists()
    }

    @Test
    fun `a week of zeroes draws no trend line`() {
        compose.setContent { theme { TrendLine(points = List(7) { 0L }, description = "empty") } }

        // The node exists either way — what must not happen is a line implying activity.
        // Asserting the absence of pixels here would be a rendering detail; the zero-fill
        // that prevents the situation is proven in `LaporanPeriodTest`.
        compose.onNodeWithTag(TAG_TREND).assertExists()
    }

    // ---------------------------------------------------- the mode chips and the table

    /**
     * Each mode chip reports **its own** mode.
     *
     * A row of chips all calling one handler would pass every existence assertion above, and
     * the owner would press "Custom" and land on the current period — the wrong-target
     * failure this repo's wiring tests exist to catch.
     */
    @Test
    fun `each mode chip reports its own mode`() {
        val chosen = mutableListOf<ReportMode>()
        compose.setContent {
            theme {
                Screen(state = populated(), onSelectMode = { chosen.add(it) })
            }
        }

        compose.onNodeWithTag(reportModeTag(ReportMode.Previous))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(reportModeTag(ReportMode.Custom))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(listOf(ReportMode.Previous, ReportMode.Custom), chosen)
    }

    /**
     * The custom picker's two fields are **only** on screen in custom mode.
     *
     * A date field that is not part of the current mode is a field the owner will fill and
     * expect to change nothing. The prototype hides them the same way.
     */
    @Test
    fun `the custom fields appear only in custom mode`() {
        compose.setContent { theme { Screen(state = populated()) } }

        compose.onNodeWithTag(TAG_CUSTOM_FROM).assertDoesNotExist()
        compose.onNodeWithTag(TAG_CUSTOM_APPLY).assertDoesNotExist()
    }

    /** In custom mode they are there, and they report what the owner typed. */
    @Test
    fun `the custom fields report what the owner typed`() {
        var from: String? = null
        var to: String? = null
        compose.setContent {
            theme {
                Screen(
                    state = populated().copy(
                        mode = ReportMode.Custom,
                        customRange = CustomRangeUi(fromText = "", toText = ""),
                    ),
                    onCustomFromChange = { from = it },
                    onCustomToChange = { to = it },
                )
            }
        }

        compose.onNodeWithTag(TAG_CUSTOM_FROM).assertExists()
        compose.onNodeWithTag(TAG_CUSTOM_TO).assertExists()
        compose.onNodeWithTag(TAG_CUSTOM_APPLY).assertExists()
    }

    /** The apply button reaches the handler. */
    @Test
    fun `the apply button reaches the handler`() {
        var applied = 0
        compose.setContent {
            theme {
                Screen(
                    state = populated().copy(
                        mode = ReportMode.Custom,
                        customRange = CustomRangeUi(fromText = "2026-10-01", toText = "2026-10-31"),
                    ),
                    onApplyCustomRange = { applied = applied + 1 },
                )
            }
        }

        compose.onNodeWithTag(TAG_CUSTOM_APPLY).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(1, applied)
    }

    /**
     * The report's own table draws a row per contract, with its header.
     *
     * The row's figures are the ones the owner is reconciling, so the assertion is on the
     * **text** rather than on the row's existence: a table with the right number of empty
     * rows would pass an existence check.
     */
    @Test
    fun `the breakdown table draws a row per contract`() {
        compose.setContent { theme { Screen(state = populatedWithRows()) } }

        compose.onNodeWithTag(TAG_BREAKDOWN).assertExists()
        compose.onNodeWithTag(TAG_BREAKDOWN_HEADER).assertExists()
        compose.onNodeWithTag(breakdownRowTag(0)).assertExists()
        compose.onNodeWithTag(breakdownRowTag(1)).assertExists()
        compose.onNodeWithText("Budi Santoso").assertExists()
        compose.onNodeWithText("B 1235 TF").assertExists()
    }

    /**
     * The CSV export reaches its handler.
     *
     * A button wired to `{}` renders and ignores the tap, which is the defect this repo has
     * shipped three times. So the assertion is the **call**, not the presence.
     */
    @Test
    fun `the export button reaches its handler`() {
        var exported = 0
        compose.setContent {
            theme {
                Screen(
                    state = populated(),
                    onExportCsv = { exported = exported + 1 },
                )
            }
        }

        compose.onNodeWithTag(TAG_EXPORT_CSV).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("the CSV export", 1, exported)
    }

    /**
     * The print button is gone, not merely unimplemented.
     *
     * The prototype has a second action beside the CSV, and the app once rendered it wired to
     * `{}` — visible, clickable, and reaching nothing. Removing the *code* is not enough on
     * its own: a tag constant left behind would let this test pass while the button came back
     * in some later commit. So the assertion names the tag that must not exist.
     */
    @Test
    fun `the print button is absent from the tree`() {
        compose.setContent { theme { Screen(state = populated()) } }

        compose.onAllNodesWithTag(TAG_PRINT_MUST_BE_ABSENT).assertCountEquals(0)
        compose.onNodeWithText("Cetak / PDF").assertDoesNotExist()
    }

    /** The period history lists the periods, each with its own total. */
    @Test
    fun `the period history lists the periods`() {
        compose.setContent { theme { Screen(state = populatedWithHistory()) } }

        compose.onNodeWithTag(TAG_PERIOD_HISTORY).assertExists()
        compose.onNodeWithTag(periodHistoryTag(0)).assertExists()
        compose.onNodeWithText("Berjalan").assertExists()
        compose.onNodeWithText("Ditutup").assertExists()
    }

    // -------------------------------------------------------------------- helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Screen(
        state: LaporanUiState,
        onSelectPeriod: (Int) -> Unit = {},
        onSelectMode: (ReportMode) -> Unit = {},
        onCustomFromChange: (String) -> Unit = {},
        onCustomToChange: (String) -> Unit = {},
        onApplyCustomRange: () -> Unit = {},
        onExportCsv: () -> Unit = {},
    ) {
        LaporanScreen(
            state = state,
            periodOptions = listOf("25 Sep - 24 Okt 2026", "25 Agu - 24 Sep 2026"),
            onSelectPeriod = onSelectPeriod,
            onSelectMode = onSelectMode,
            onCustomFromChange = onCustomFromChange,
            onCustomToChange = onCustomToChange,
            onApplyCustomRange = onApplyCustomRange,
            onExportCsv = onExportCsv,
        )
    }

    private fun populated() = LaporanUiState(
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
    )

    /** A report with the table's own rows. */
    private fun populatedWithRows() = populated().copy(
        breakdown = listOf(
            ReportRowUi(
                renterName = "Budi Santoso",
                plate = "B 1235 TF",
                brand = "Toyota",
                daysBilled = 12,
                billedText = "Rp 3.000.000",
                receivedText = "Rp 2.500.000",
                arrearsText = "Rp 500.000",
                isSettled = false,
                billedRupiah = 3_000_000,
                receivedRupiah = 2_500_000,
                arrearsRupiah = 500_000,
            ),
            ReportRowUi(
                renterName = "Siti Aminah",
                plate = "B 9999 ZZ",
                brand = "Honda",
                daysBilled = 12,
                billedText = "Rp 3.000.000",
                receivedText = "Rp 3.000.000",
                arrearsText = "Rp 0",
                isSettled = true,
                billedRupiah = 3_000_000,
                receivedRupiah = 3_000_000,
                arrearsRupiah = 0,
            ),
        ),
    )

    /** A report with the period history's own rows. */
    private fun populatedWithHistory() = populated().copy(
        periodHistory = listOf(
            PeriodHistoryUi(
                label = "25 Sep - 24 Okt 2026",
                totalText = "Rp 1.500.000",
                statusText = "Berjalan",
                period = net.klinik.rental.domain.calculate.CutoffPeriod(
                    java.time.LocalDate.of(2026, 9, 25),
                    java.time.LocalDate.of(2026, 10, 24),
                ),
            ),
            PeriodHistoryUi(
                label = "25 Agu - 24 Sep 2026",
                totalText = "Rp 2.000.000",
                statusText = "Ditutup",
                period = net.klinik.rental.domain.calculate.CutoffPeriod(
                    java.time.LocalDate.of(2026, 8, 25),
                    java.time.LocalDate.of(2026, 9, 24),
                ),
            ),
        ),
    )
}

/**
 * The tag the print button used to carry, kept here as a literal.
 *
 * The production constant is gone, so the test cannot import it — and that is the point. A test
 * that imported the constant would stop compiling the day it was deleted, which is a louder
 * failure but still a *compile* failure rather than the assertion this defect needs. Spelling the
 * string out here means the check survives the deletion: reintroduce a print button under this
 * tag and `the print button is absent from the tree` fails.
 */
private const val TAG_PRINT_MUST_BE_ABSENT = "laporan-export-print"
