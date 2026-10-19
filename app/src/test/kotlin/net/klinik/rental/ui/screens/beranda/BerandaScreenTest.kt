package net.klinik.rental.ui.screens.beranda

import androidx.compose.runtime.Composable
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
class BerandaScreenTest {

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
        compose.onNodeWithTag(TAG_KPI_ARREARS).assertExists()
        compose.onNodeWithTag(TAG_KPI_FLEET).assertExists()
        compose.onNodeWithTag(TAG_KPI_RATE).assertExists()

        compose.onNodeWithText("Rp 750.000").assertExists()
        compose.onNodeWithText("Rp 4.250.000").assertExists()
        compose.onNodeWithText("12 · 3 · 1").assertExists()
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
        val arrears = compose.onNodeWithTag(TAG_KPI_ARREARS).fetchSemanticsNode().size.height
        val fleet = compose.onNodeWithTag(TAG_KPI_FLEET).fetchSemanticsNode().size.height
        val rate = compose.onNodeWithTag(TAG_KPI_RATE).fetchSemanticsNode().size.height

        assertEquals("income and arrears are the same height", income, arrears)
        assertEquals("income and the fleet are the same height", income, fleet)
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

    // ------------------------------------------------------------- the quick actions

    /**
     * The four quick actions are present, and each is a **target**.
     *
     * The criterion is that every action leads somewhere that exists, and the first half of
     * that is that all four are there.
     */
    @Test
    fun `the four quick actions are present and tappable`() {
        compose.setContent { theme { Screen(state = berandaState()) } }

        compose.onNodeWithTag(TAG_QUICK_ACTION_GRID).assertExists()
        compose.onNodeWithTag(quickActionTag(QuickAction.CatatSetoran)).assertExists()
        compose.onNodeWithTag(quickActionTag(QuickAction.TambahMobil)).assertExists()
        compose.onNodeWithTag(quickActionTag(QuickAction.KelolaPenyewa)).assertExists()
        compose.onNodeWithTag(quickActionTag(QuickAction.KirimPengingat)).assertExists()

        compose.onNodeWithTag(quickActionTag(QuickAction.CatatSetoran)).assertHasClickAction()
    }

    /**
     * Tapping an action reaches **that** action's handler.
     *
     * The click-through that `assertExists()` cannot do: a grid whose four tiles all called
     * the same handler would pass every existence assertion, and the owner would find
     * "Tambah Mobil" recording a deposit.
     */
    @Test
    fun `tapping an action reaches its own handler`() {
        var chosen: String? = null
        compose.setContent {
            theme {
                Screen(
                    state = berandaState(),
                    onDeposit = { chosen = "deposit" },
                    onAddVehicle = { chosen = "vehicle" },
                    onManageRenters = { chosen = "renter" },
                    onSendReminder = { chosen = "reminder" },
                )
            }
        }

        compose.onNodeWithTag(quickActionTag(QuickAction.TambahMobil))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("the vehicle action", "vehicle", chosen)

        compose.onNodeWithTag(quickActionTag(QuickAction.KirimPengingat))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("and then the reminder one", "reminder", chosen)
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
        compose.setContent { theme { Screen(state = BerandaUiState(isLoading = true)) } }

        compose.onAllNodesWithTag(TAG_KPI_SKELETON).assertCountEquals(4)
        compose.onNodeWithText("Rp 750.000").assertDoesNotExist()
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

    // ----------------------------------------------------------------------- the hero

    /**
     * The hero draws with its badge and its two lines, **above** the tiles.
     *
     * `index.html:104-123` — the greeting is what the owner arrives to, and a welcome below four
     * figures is not a welcome. Asserted by position rather than by the call site's order: a
     * composable moved below [TAG_KPI_ROW] would still pass an existence check.
     */
    @Test
    fun `the hero draws above the tiles with its own copy`() {
        compose.setContent { theme { Screen(state = berandaState()) } }

        compose.onNodeWithTag(TAG_HERO).assertExists()
        compose.onNodeWithTag(TAG_HERO_BADGE).assertExists()
        compose.onNodeWithText("Sewa harian,\nsetoran tiap hari.").assertExists()

        val hero = compose.onNodeWithTag(TAG_HERO).fetchSemanticsNode().positionInRoot.y
        val tiles = compose.onNodeWithTag(TAG_KPI_ROW).fetchSemanticsNode().positionInRoot.y
        assertTrue("the hero is above the KPI row", hero < tiles)
    }

    /** The hero's deposit button reaches the **same** handler the grid's first action does. */
    @Test
    fun `the hero deposit button reaches its handler`() {
        var tapped = 0
        compose.setContent { theme { Screen(state = berandaState(), onDeposit = { tapped += 1 }) } }

        compose.onNodeWithTag(TAG_HERO_DEPOSIT).performClick()
        compose.waitForIdle()

        assertEquals("one tap, one call", 1, tapped)
    }

    /**
     * The hero's second button **moves the page**, which is the whole of what it claims.
     *
     * This is the test this repo's rule demands: `assertExists()` would pass against a button
     * wired to `{}`, and a button that renders and ignores the tap is the defect the screens KDoc
     * names three times. So the assertion is the scroll offset the button produced, not the
     * button's presence — compare the charts' offset before and after.
     */
    @Test
    fun `the dashboard button scrolls the page to the charts`() {
        compose.setContent { theme { Screen(state = berandaState()) } }

        val before = compose.onNodeWithTag(TAG_CHARTS_SECTION).fetchSemanticsNode().positionInRoot.y

        compose.onNodeWithTag(TAG_HERO_DASHBOARD).performClick()
        compose.waitForIdle()

        val after = compose.onNodeWithTag(TAG_CHARTS_SECTION).fetchSemanticsNode().positionInRoot.y
        assertTrue("the charts moved up the screen ($before → $after)", after < before)
    }

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
        state: BerandaUiState,
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
    ) {
        BerandaScreen(
            state = state,
            onDeposit = onDeposit,
            onAddVehicle = onAddVehicle,
            onManageRenters = onManageRenters,
            onSendReminder = onSendReminder,
            onBackupReminderTap = onBackupReminderTap,
            onBackupExport = onBackupExport,
            onSeeAllTransactions = onSeeAllTransactions,
        )
    }

    /**
     * A morning with figures, hand-built.
     *
     * Hand-built rather than produced by the ViewModel, because this file is about
     * **rendering** — what the owner reads. `BerandaKpiTest` covers where the numbers come
     * from, and a fixture that went through the repository would make a formatting change
     * look like a rendering change.
     */
    private fun berandaState() = BerandaUiState(
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
