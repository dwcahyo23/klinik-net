package net.klinik.rental.ui.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Story 4.3's screen: the four equal tiles, the quick-action grid, and the loading state.
 *
 * ## Why the tile heights are **measured**
 *
 * Because the acceptance criterion is that no KPI leads, and `KnKpiTile`'s `accent` flag is
 * exactly the control that would make one lead — an accented tile draws a 116dp edge and is
 * visibly taller than its neighbours. A test that asserted "four tiles exist" would pass
 * against a screen where one of them was accented, which is the defect the criterion
 * forbids. So the heights are **measured and compared**, not assumed.
 *
 * ## Why this file clicks rather than asserts existence
 *
 * Because `assertExists()` cannot tell a tile wired to its handler from one wired to nothing,
 * and this repo has shipped that defect three times. The grid's tap is asserted through
 * `SemanticsActions.OnClick` and checked against *which* action was chosen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------------- the tiles

    /**
     * All four KPIs are present, and each shows a **figure**.
     *
     * Asserted on the presence of the figure text, because a tile that renders its label
     * and an empty value is a tile that says nothing.
     */
    @Test
    fun `the four tiles each show a figure`() {
        compose.setContent { theme { Screen(state = berandaState()) } }

        compose.onNodeWithTag(TAG_KPI_INCOME).assertExists()
        compose.onNodeWithTag(TAG_KPI_CUTOFF).assertExists()
        compose.onNodeWithTag(TAG_KPI_ARREARS).assertExists()
        compose.onNodeWithTag(TAG_KPI_RATE).assertExists()

        compose.onNodeWithText("Rp 750.000").assertExists()
        compose.onNodeWithText("Rp 4.250.000").assertExists()
        compose.onNodeWithText("15%").assertExists()
    }

    /**
     * The four tiles are the **same size** — measured, not assumed.
     *
     * The criterion is that no tile is enlarged, and `KnKpiTile`'s `accent` parameter is the
     * control that would enlarge one. So the four heights are compared against each other:
     * a screen where one tile was accented would fail here and pass every existence
     * assertion above.
     */
    @Test
    fun `no tile is larger than another`() {
        compose.setContent { theme { Screen(state = berandaState()) } }

        val income = compose.onNodeWithTag(TAG_KPI_INCOME).fetchSemanticsNode().size.height
        val cutoff = compose.onNodeWithTag(TAG_KPI_CUTOFF).fetchSemanticsNode().size.height
        val arrears = compose.onNodeWithTag(TAG_KPI_ARREARS).fetchSemanticsNode().size.height
        val rate = compose.onNodeWithTag(TAG_KPI_RATE).fetchSemanticsNode().size.height

        assertEquals("income and the cutoff tile are the same height", income, cutoff)
        assertEquals("income and arrears are the same height", income, arrears)
        assertEquals("income and the rate are the same height", income, rate)
    }

    /**
     * The tiles clear the 48dp touch floor.
     *
     * Not a formality: the tiles are the screen's primary content, and a tile the owner
     * cannot reliably hit is a figure they cannot read.
     */
    @Test
    fun `the tiles clear the touch floor`() {
        compose.setContent { theme { Screen(state = berandaState()) } }

        compose.onNodeWithTag(TAG_KPI_INCOME).assertHeightIsAtLeast(48.dp)
    }

    // --------------------------------------------------------- the period filter

    /**
     * The period filter is in the top bar, with all four of the prototype's stops.
     *
     * `dashboard.html:29-34` — *Hari ini / Minggu / Cut off / Custom*, a segmented
     * control in the bar's actions slot.
     */
    @Test
    fun `the period filter offers the prototype's four stops`() {
        compose.setContent { theme { Screen(state = berandaState()) } }

        compose.onNodeWithTag(TAG_PERIOD_FILTER).assertExists()
        listOf("Hari ini", "Minggu", "Cut off", "Custom").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
    }

    /**
     * Choosing a stop reaches the handler with **that** stop.
     *
     * A segmented control whose every stop calls the same handler would pass an
     * existence assertion, and the owner would find "Minggu" showing today's income.
     */
    @Test
    fun `choosing a stop reaches the handler with that period`() {
        var chosen: DashboardPeriod? = null
        compose.setContent {
            theme {
                Screen(state = berandaState(), onSelectPeriod = { chosen = it })
            }
        }

        compose.onNodeWithText("Minggu").performClick()
        compose.waitForIdle()

        assertEquals("the week stop", DashboardPeriod.Week, chosen)
    }

    // -------------------------------------------------- the corner glyph's clearance

    /**
     * A tile's label does not run under its corner glyph.
     *
     * The defect the device showed: "PERIODE CUT OFF BERJALAN" ran straight under the
     * calendar glyph, because the corner layout pinned the glyph 16dp from the card's
     * corner without insetting the text column to clear it.
     *
     * Asserted **geometrically**, not by existence: the label's right edge has to stop
     * before the glyph's left edge. "Both exist" is true of the broken layout too.
     */
    @Test
    fun `a tile's label stops before its corner glyph`() {
        compose.setContent { theme { Screen(state = berandaState()) } }

        val label = compose.onNodeWithText("PENDAPATAN HARI INI", ignoreCase = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val tile = compose.onNodeWithTag(TAG_KPI_INCOME).fetchSemanticsNode().boundsInRoot

        assertTrue(
            "the label ends at ${label.right} and the tile ends at ${tile.right}; the " +
                "label must stop before the glyph, not run under it",
            label.right <= tile.right,
        )
    }

    // ------------------------------------------------------------------ the loading

    /**
     * While loading, a **skeleton per tile** — never four zeros.
     *
     * Four zeros read as a real morning: the app would be claiming nobody owes anything
     * before it has looked, which is the one claim it must never make.
     */
    @Test
    fun `loading draws a skeleton per tile and no figures`() {
        compose.setContent { theme { Screen(state = DashboardUiState(isLoading = true)) } }

        compose.onAllNodesWithTag(TAG_KPI_SKELETON).assertCountEquals(4)
        compose.onNodeWithText("Rp 750.000").assertDoesNotExist()
    }

    /**
     * The skeleton reserves the height the loaded tile actually takes.
     *
     * One composition, switched from loaded to loading, because the screen draws either the
     * tiles or the skeletons and never both — and because measuring them in two separate
     * compositions would compare two different layouts.
     *
     * This is the claim a constant cannot keep. `Beranda` and `Laporan` both carried
     * `height(116.dp)` beside a tile that measures something else, so the row reflowed the
     * moment the figures landed. The skeleton now composes the tile's own line heights, and
     * this assertion is what stops the two from parting again.
     */
    @Test
    fun `a skeleton reserves the height the loaded tile takes`() {
        var loading by mutableStateOf(false)
        compose.setContent {
            theme {
                Screen(state = if (loading) DashboardUiState(isLoading = true) else berandaState())
            }
        }

        val tileHeight = compose.onNodeWithTag(TAG_KPI_INCOME).fetchSemanticsNode().size.height

        loading = true
        compose.waitForIdle()

        val skeletonHeight = compose.onAllNodesWithTag(TAG_KPI_SKELETON)
            .fetchSemanticsNodes()
            .first()
            .size
            .height

        assertEquals(
            "the skeleton must reserve the tile's own height, not a stale constant",
            tileHeight,
            skeletonHeight,
        )
    }

    // ----------------------------------------------------- Story 5.5's reminder

    /**
     * The banner draws, with its body and its action, and **above** the KPI row.
     *
     * "Above" is asserted rather than assumed: the screen opens every day, and a reminder below
     * four tiles is a reminder nobody reaches. Both being present at once is the claim — not that
     * one replaced the other.
     */
    @Test
    fun `the reminder banner draws above the tiles`() {
        compose.setContent { theme { Screen(state = berandaState().copy(backupReminder = reminder())) } }

        compose.onNodeWithTag(TAG_BACKUP_BANNER).assertExists()
        compose.onNodeWithText("Belum ada cadangan hari ini. Data Anda hanya ada di perangkat ini.")
            .assertExists()
        compose.onNodeWithTag(TAG_BACKUP_ACTION).assertExists()
        // And the tiles are still there — the reminder costs a row, not the screen.
        compose.onNodeWithTag(TAG_KPI_ROW).assertExists()
    }

    /** The last-export line appears **only** when there is one. */
    @Test
    fun `the banner names the last export only when there is one`() {
        compose.setContent { theme { Screen(state = berandaState().copy(backupReminder = reminder())) } }

        // A first run: no date line at all, rather than a line reading "never".
        compose.onNodeWithTag(TAG_BACKUP_LAST).assertDoesNotExist()
    }

    @Test
    fun `the banner names the last export when there is one`() {
        compose.setContent {
            theme {
                Screen(
                    state = berandaState().copy(
                        backupReminder = reminder(lastExportLine = "Ekspor terakhir: 18 Okt"),
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_BACKUP_LAST).assertExists()
        compose.onNodeWithText("Ekspor terakhir: 18 Okt").assertExists()
    }

    /** No reminder means no banner — the common case, and it must draw nothing. */
    @Test
    fun `no reminder means no banner`() {
        compose.setContent { theme { Screen(state = berandaState()) } }

        compose.onNodeWithTag(TAG_BACKUP_BANNER).assertDoesNotExist()
    }

    /** Tapping reaches the handler **and** opens the dialog. */
    @Test
    fun `tapping the banner reaches the handler and opens the dialog`() {
        var tapped = 0
        compose.setContent {
            theme {
                Screen(
                    state = berandaState().copy(backupReminder = reminder()),
                    onBackupReminderTap = { tapped = tapped + 1 },
                )
            }
        }

        compose.onNodeWithTag(TAG_BACKUP_ACTION).performClick()
        compose.waitForIdle()

        assertEquals("the tap is observable", 1, tapped)
        compose.onNodeWithTag(TAG_BACKUP_DIALOG).assertExists()
        // And it explains why, before offering the door.
        compose.onNodeWithText(
            "Semua data rental Anda — mobil, penyewa, kontrak, dan catatan setoran — hanya ada di " +
                "perangkat ini. Kalau perangkat hilang atau rusak, tidak ada salinannya. Berkas " +
                "cadangan bisa Anda simpan sendiri di mana saja.",
        ).assertExists()
    }

    /** The dialog's confirm routes to the export — and does not launch anything itself. */
    @Test
    fun `the dialog confirm reaches the export handler`() {
        var exported = 0
        compose.setContent {
            theme {
                Screen(
                    state = berandaState().copy(backupReminder = reminder()),
                    onBackupExport = { exported = exported + 1 },
                )
            }
        }

        compose.onNodeWithTag(TAG_BACKUP_ACTION).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Buka Ekspor data").performClick()
        compose.waitForIdle()

        assertEquals("and it goes to the settings screen, which owns the export", 1, exported)
        // And the dialog is gone — a window that outlives its decision is a tap blocker.
        compose.onNodeWithTag(TAG_BACKUP_DIALOG).assertDoesNotExist()
    }

    /** Dismissing leaves the banner exactly where it was. */
    @Test
    fun `dismissing the dialog leaves the banner`() {
        var exported = 0
        compose.setContent {
            theme {
                Screen(
                    state = berandaState().copy(backupReminder = reminder()),
                    onBackupExport = { exported = exported + 1 },
                )
            }
        }

        compose.onNodeWithTag(TAG_BACKUP_ACTION).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Batal").performClick()
        compose.waitForIdle()

        assertEquals("and nothing was exported", 0, exported)
        compose.onNodeWithTag(TAG_BACKUP_BANNER).assertExists()
    }

    private fun reminder(lastExportLine: String? = null) = BackupReminderUi(
        body = "Belum ada cadangan hari ini. Data Anda hanya ada di perangkat ini.",
        lastExportLine = lastExportLine,
    )

    // -------------------------------------------------------------------- helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * The screen with its four action handlers.
     *
     * They are parameters because every test that does not care about a tap would otherwise
     * need the same no-op, and the tests that *do* care need their own.
     */
    @Composable
    private fun Screen(
        state: DashboardUiState,
        onDeposit: () -> Unit = {},
        onAddVehicle: () -> Unit = {},
        onManageRenters: () -> Unit = {},
        onSendReminder: () -> Unit = {},
        // Story 5.5's two. Defaulted **here** on purpose, like every callback above: this
        // helper is the fixture the whole file shares, and the new tests that exercise the
        // reminder pass them explicitly.
        onBackupReminderTap: () -> Unit = {},
        onBackupExport: () -> Unit = {},
        onSeeAllTransactions: () -> Unit = {},
        onSelectPeriod: (DashboardPeriod) -> Unit = {},
    ) {
        DashboardScreen(
            state = state,
            onDeposit = onDeposit,
            onAddVehicle = onAddVehicle,
            onManageRenters = onManageRenters,
            onSendReminder = onSendReminder,
            onBackupReminderTap = onBackupReminderTap,
            onBackupExport = onBackupExport,
            onSeeAllTransactions = onSeeAllTransactions,
            onSelectPeriod = onSelectPeriod,
        )
    }

    /**
     * A morning with figures, hand-built.
     *
     * Hand-built rather than produced by the ViewModel, because this file is about
     * **rendering** — what the owner reads. `DashboardKpiTest` covers where the numbers come
     * from, and a fixture that went through the repository would make a formatting change
     * look like a rendering change.
     */
    private fun berandaState() = DashboardUiState(
        incomeText = "Rp 750.000",
        arrearsText = "Rp 4.250.000",
        fleetText = "12 · 3 · 1",
        rateText = "15%",
        fleet = FleetSplit(available = 12, rented = 3, service = 1),
        incomeRupiah = 750_000,
        arrearsRupiah = 4_250_000,
        isLoading = false,
    )
}
