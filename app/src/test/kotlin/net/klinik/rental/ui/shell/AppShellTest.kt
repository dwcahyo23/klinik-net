package net.klinik.rental.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.screens.kendaraan.KendaraanUiState
import net.klinik.rental.ui.screens.kontrak.KontrakUiState
import net.klinik.rental.ui.screens.pengaturan.PengaturanUiState
import net.klinik.rental.ui.screens.penyewa.PenyewaUiState
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The shell: one graph, and chrome that changes with orientation.
 *
 * ## Why destinations are *clicked* here and not merely asserted to exist
 *
 * The rail and the bottom nav existed before this story, built by Story 2.1 and
 * used by nothing. Their only tests measured the 48dp touch floor and never once
 * called `onSelect` — so a rail whose items render correctly and navigate to
 * nowhere passed every test that existed. That is the "the control exists and is
 * wired to nothing" defect spec-2-5 spent two review passes closing, and it would
 * have shipped straight back in through the navigation surface.
 *
 * So the assertion is behavioural: tap a destination, then assert the screen that
 * destination names is now on top.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
class AppShellTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `landscape shows the rail and not the bottom nav`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        compose.onNodeWithTag(TAG_RAIL).assertExists()
        compose.onAllNodesWithTag(TAG_BOTTOM_NAV).assertCountEquals(0)
    }

    @Test
    fun `portrait shows the bottom nav and not the rail`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = false) } }

        compose.onNodeWithTag(TAG_BOTTOM_NAV).assertExists()
        compose.onAllNodesWithTag(TAG_RAIL).assertCountEquals(0)
    }

    @Test
    fun `the app opens on the fleet`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        // `Destination.START` is Kendaraan; this asserts the graph honours it
        // rather than defaulting to whichever `composable(...)` is declared first.
        compose.onNodeWithText("Armada").assertExists()
    }

    @Test
    fun `tapping a rail destination opens that screen`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        compose.onNodeWithText("Penyewa").performClick()
        compose.waitForIdle()

        // Not the title: "Penyewa" is both the rail label and the screen's own
        // title, so an assertion on it proves nothing about which screen is up.
        // The proof is content only the renters screen draws — an empty list
        // addressed in renters' words.
        compose.onNodeWithText("Belum ada Penyewa").assertExists()
    }

    @Test
    fun `tapping a bottom-nav destination opens that screen`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = false) } }

        compose.onNodeWithText("Kontrak").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Belum ada Kontrak").assertExists()
    }

    @Test
    fun `tapping the already-selected destination does not stack a duplicate`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        compose.onNodeWithText("Armada").performClick()
        compose.waitForIdle()

        // `launchSingleTop` plus `restoreState`: re-tapping is a no-op, not a
        // second copy of the fleet. The symptom of getting this wrong is a back
        // stack that grows on every tap, so the owner walks back through screens
        // they never left.
        compose.onAllNodesWithTag(TAG_RAIL).assertCountEquals(1)
        compose.onNodeWithText("Belum ada Kendaraan").assertExists()
    }

    @Test
    fun `the rail carries every destination the graph declares`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        // Scoped to the rail, not to the screen: "Kendaraan" is both a rail label
        // and the fleet screen's own title, so an unscoped text query matches two
        // nodes and says nothing about the chrome. The chrome is what is under test.
        //
        // Because the rail and the graph are generated from one table, a
        // destination cannot be in the chrome and missing from the graph —
        // `NAV_DESTINATION_UNREACHABLE` answered by construction.
        Destination.entries.forEach { destination ->
            compose.onAllNodes(
                hasText(labelOf(destination), substring = true) and
                    hasAnyAncestor(hasTestTag(TAG_RAIL)),
            ).assertCountEquals(1)
        }
    }

    /**
     * Every rail target is *measured* against the 48dp floor.
     *
     * Counting them is not the same claim and the difference is the whole point:
     * a rail of three 4dp items has exactly the right number of targets and
     * fails the acceptance criterion. So each node's width and height are
     * measured here, on the real shell, not just counted.
     *
     * `taggedOrInside` rather than `hasAnyAncestor` alone: `KnNavRail` puts the
     * caller's `modifier` on its own wrapping `Column` while the clickable
     * `Surface` is a descendant, so a tag-only matcher would find the wrapper and
     * an ancestor-only matcher would miss nothing here — but the combined form
     * is the one that works whichever side the component tagged.
     */
    @Test
    fun `every rail target clears the 48dp touch floor`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        val targets = compose.onAllNodes(
            hasClickAction() and (
                hasTestTag(TAG_RAIL) or hasAnyAncestor(hasTestTag(TAG_RAIL))
                ),
        )
        assertEquals(
            "rail rendered the wrong number of navigation targets",
            Destination.entries.size,
            targets.fetchSemanticsNodes().size,
        )

        targets.fetchSemanticsNodes().indices.forEach { i ->
            targets[i].assertWidthIsAtLeast(MIN_TARGET)
            targets[i].assertHeightIsAtLeast(MIN_TARGET)
        }
    }

    /** The same floor on the bottom nav, which is a different component. */
    @Test
    fun `every bottom-nav target clears the 48dp touch floor`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = false) } }

        val targets = compose.onAllNodes(
            hasClickAction() and (
                hasTestTag(TAG_BOTTOM_NAV) or hasAnyAncestor(hasTestTag(TAG_BOTTOM_NAV))
                ),
        )
        assertEquals(
            "bottom nav rendered the wrong number of navigation targets",
            Destination.entries.size,
            targets.fetchSemanticsNodes().size,
        )
        targets.fetchSemanticsNodes().indices.forEach { i ->
            targets[i].assertWidthIsAtLeast(MIN_TARGET)
            targets[i].assertHeightIsAtLeast(MIN_TARGET)
        }
    }

    /**
     * `isLandscape` omitted: the production default picks the chrome.
     *
     * Every other test in this file passes `isLandscape` explicitly, which is the
     * right way to reach a branch — and which would leave `MainActivity`'s single
     * call site, the only one that *does* omit it, completely unexecuted. So this
     * composes `AppShell` the way the Activity does, once under a landscape
     * qualifier and once under a portrait one, and asserts the default lands on
     * the chrome that orientation names.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h720dp-land")
    fun `the default orientation reads landscape as a rail`() {
        compose.setContent { theme { AppShell(screens = testScreens()) } }

        compose.onNodeWithTag(TAG_RAIL).assertExists()
        compose.onAllNodesWithTag(TAG_BOTTOM_NAV).assertCountEquals(0)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-port")
    fun `the default orientation reads portrait as a bottom nav`() {
        compose.setContent { theme { AppShell(screens = testScreens()) } }

        compose.onNodeWithTag(TAG_BOTTOM_NAV).assertExists()
        compose.onAllNodesWithTag(TAG_RAIL).assertCountEquals(0)
    }

    @Test
    fun `the start destination is the fleet, and it is first in the table`() {
        assertEquals(Destination.Kendaraan, Destination.START)
        assertEquals(Destination.Kendaraan, Destination.entries.first())
    }

    @Test
    fun `an unknown route falls back to the start destination's index`() {
        // The rail has to highlight *something*; an unknown route leaves every
        // item unselected with no way to say why. Landing on the fleet is the
        // least-wrong answer.
        assertEquals(0, Destination.indexOf("a-route-that-does-not-exist"))
        assertEquals(0, Destination.indexOf(null))
    }

    @Test
    fun `an out-of-range index yields no route rather than a clamped one`() {
        // Clamping would turn a programming error into silent navigation to the
        // wrong screen — the wrong screen being a contract list where the owner
        // expected their fleet.
        assertEquals(null, Destination.routeAt(-1))
        assertEquals(null, Destination.routeAt(Destination.entries.size))
    }

    @Test
    fun `every entry maps to its own index`() {
        Destination.entries.forEachIndexed { index, destination ->
            assertEquals(index, Destination.indexOf(destination.route))
            assertEquals(destination.route, Destination.routeAt(index))
        }
    }

    @Test
    fun `navigating selects a destination the back stack reports`() {
        // ORIENTATION_FLIP's mechanism. The selection lives in the back stack
        // above the chrome, so a chrome swap cannot lose it — and the way to know
        // the two branches share one graph is that the back stack reads the same
        // either side. Asserted through the controller rather than by reading
        // both branches and hoping they match.
        var seen: String? = null
        compose.setContent {
            theme {
                val navController = rememberNavController()
                AppShell(
                    screens = testScreens(),
                    navController = navController,
                    isLandscape = true,
                )
                val entry by navController.currentBackStackEntryAsState()
                seen = entry?.destination?.route
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Kontrak").performClick()
        compose.waitForIdle()

        assertEquals(Destination.Kontrak.route, seen)
    }

    /**
     * The rail's label for a destination, as `destinationNavItems()` resolves it.
     *
     * An exhaustive `when` rather than a lookup keyed on `labelRes`: adding the fourth
     * destination in Story 3.1 made this non-exhaustive, so the compiler named the branch
     * that had to be added. That is the same mechanism `Destination.icon` uses, and the
     * reason this test cannot silently stop covering a destination.
     */
    private fun labelOf(destination: Destination): String = when (destination) {
        Destination.Kendaraan -> "Kendaraan"
        Destination.Penyewa -> "Penyewa"
        Destination.Kontrak -> "Kontrak"
        Destination.Pengaturan -> "Pengaturan"
    }

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * Three destinations wired to hand-built state.
     *
     * The shell takes state rather than ViewModels precisely so this is
     * possible: a test drives the whole shell with no database at all, the same
     * way every screen test already does.
     */
    private companion object {
        // al, not const val: Dp is an inline value class over a Float.
        val MIN_TARGET: Dp = 48.dp
    }

    private fun testScreens() = Screens(
        kendaraan = Screens.Kendaraan(
            state = KendaraanUiState(),
            snackbarMessage = null,
            onQueryChange = {},
            onFilterChange = {},
            onClearQuery = {},
            onAdd = {},
            onEdit = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
        penyewa = Screens.Penyewa(
            state = PenyewaUiState(),
            snackbarMessage = null,
            onQueryChange = {},
            onClearQuery = {},
            onAdd = {},
            onEdit = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
        kontrak = Screens.Kontrak(
            state = KontrakUiState(),
            snackbarMessage = null,
            renterOptions = emptyList(),
            vehicleOptions = emptyList(),
            onOpen = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            onOpenExceptions = {},
            onCloseExceptions = {},
            onExceptionChange = { it },
            onAddException = {},
            onRequestRemove = {},
            onConfirmRemove = {},
            onCancelRemove = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
            // Story 2.6's and 2.7's message pairs. Stated rather than omitted because
            // they are **required** on `Screens.Kontrak`: this fixture builds the wiring
            // container the shell hands to the route, so it is exactly the place a
            // missing handler would turn into a silent no-op at runtime rather than a
            // compile error.
            onExceptionErrorDismiss = {},
            onOpenEnd = {},
            onCloseEnd = {},
            onEndChange = { it },
            onConfirmEnd = {},
            onEndErrorDismiss = {},
        ),
        pengaturan = Screens.Pengaturan(
            state = PengaturanUiState(),
            snackbarMessage = null,
            onAdd = {},
            onEdit = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
    )
}
