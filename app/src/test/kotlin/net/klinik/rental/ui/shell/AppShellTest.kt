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
import net.klinik.rental.ui.screens.setoran.SetoranUiState
import net.klinik.rental.ui.screens.tunggakan.TunggakanUiState
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

    /**
     * Story 3.2's FAB is on the shell, so it appears over **every** destination — and the
     * branch that puts it somewhere else would be a control the owner reaches on some
     * tablets and not others, which is what AR-18's "identical graph in both
     * orientations" exists to prevent.
     *
     * One test per orientation rather than both in one, because a `ComposeTestRule`
     * accepts exactly one `setContent` per method — the reason this file's own harness
     * drives a single tree per pass.
     *
     * **One exception, and it is Story 4.3's:** the FAB is *absent* on Beranda, because
     * the summary's "Catat Setoran" tile is the only door to that action from there. So
     * these two tests navigate to the fleet first — otherwise they would be asserting the
     * FAB is present on the screen that deliberately has no FAB.
     *
     * The navigation goes through `Destination.Kendaraan.route` rather than a label typed as
     * text. `"Armada"` was never a nav label — the label is `"Kendaraan"` — and the tap only
     * worked before because that word appeared in the fleet screen's own title bar, so the
     * assertion passed while navigating to whatever happened to carry the word. Now that
     * Beranda is the start destination and owns no FAB, a test that clicks the title bar
     * instead of the rail lands on the start screen and finds nothing.
     */
    @Test
    fun `the deposit FAB appears in landscape`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        compose.onNodeWithText("Kendaraan").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_DEPOSIT_FAB).assertExists()
        compose.onAllNodesWithTag(TAG_RAIL).assertCountEquals(1)
    }

    @Test
    fun `the deposit FAB appears in portrait`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = false) } }

        compose.onNodeWithText("Kendaraan").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_DEPOSIT_FAB).assertExists()
        compose.onAllNodesWithTag(TAG_BOTTOM_NAV).assertCountEquals(1)
    }

    /**
     * And the FAB is **absent on Beranda** — the promise `DepositFab`'s KDoc has carried
     * since Story 3.2, waiting for the screen that would hide it.
     *
     * UX-DR17: *"speed dial TIDAK muncul di Beranda — grid aksi adalah satu-satunya pintu
     * dari sini."* Two doors to one action is how one of them goes stale, and the summary's
     * grid is the door that stays.
     */
    @Test
    fun `the deposit FAB is absent on the morning summary`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        // The app opens on Beranda, so the FAB is already gone.
        compose.onAllNodesWithTag(TAG_DEPOSIT_FAB).assertCountEquals(0)
    }

    /** And it clears the 48dp floor — UX-DR17's 54dp, measured rather than assumed. */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the deposit FAB clears the touch floor`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        // The fleet, because the FAB is deliberately absent on Beranda — see the
        // absence test above. The nav label is `"Kendaraan"`, not the screen's
        // `"Armada"` title; see the note on the FAB's landscape test.
        compose.onNodeWithText("Kendaraan").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_DEPOSIT_FAB)
            .assertExists()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    /**
     * The FAB **disappears** while the sheet is open.
     *
     * Both are windows: a FAB showing through an open sheet is the same defect at a
     * smaller size as two `ModalBottomSheet`s over one state, which was the only `high`
     * Story 2.8's review found.
     */
    @Test
    fun `the FAB is absent while the deposit sheet is open`() {
        val open = testScreens(
            depositoState = net.klinik.rental.ui.screens.setoran.SetoranUiState(
                form = net.klinik.rental.ui.screens.setoran.DepositFormState(
                    // Pinned rather than defaulted: this test is about window stacking, so
                    // the date only has to be *a* date — but a `now()` default would make
                    // the fixture drift with the wall clock.
                    date = java.time.LocalDate.of(2026, 10, 17),
                ),
            ),
        )
        compose.setContent { theme { AppShell(screens = open, isLandscape = true) } }

        compose.onAllNodesWithTag(TAG_DEPOSIT_FAB).assertCountEquals(0)
    }

    /**
     * The app opens on **Beranda**, the morning summary.
     *
     * Story 4.3 moved `Destination.START` from the fleet to the summary, because the
     * summary is the screen that answers "what do I have to do today" and a summary the
     * owner has to go *to* is not a summary. The assertion is on content only the summary
     * draws — the rail's "Beranda" label is also the screen's own title, so asserting on
     * that would prove nothing about which screen is up.
     */
    @Test
    fun `the app opens on the morning summary`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        // The summary's own figure label, which no other screen draws.
        compose.onNodeWithText("Pendapatan hari ini").assertExists()
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

        // The app opens on Beranda now, so the *already-selected* destination is the
        // summary — and this still asserts the fleet's own screen after tapping its
        // label, because the claim is about the back stack rather than about the start.
        compose.onNodeWithText("Kendaraan").performClick()
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

    /**
     * The start destination is the morning summary.
     *
     * Story 4.3 moved it from the fleet, because the summary is the screen that answers
     * "what do I have to do today" and a summary the owner has to go *to* is not a summary.
     *
     * The **second** assertion — that the summary is first in the table — is what this
     * file's own convention asks for: the rail is built from `entries`, so the entry that
     * leads the table leads the rail. Story 4.3 appended Beranda rather than putting it
     * first, because UX-DR17's rail order needs three destinations that do not exist yet
     * and reordering it three times as they arrive would churn the KDoc that explains the
     * rule. So the rail's *current* order has the summary last, and the rail is re-ordered
     * once the set is complete.
     */
    @Test
    fun `the start destination is the morning summary`() {
        assertEquals(Destination.Beranda, Destination.START)
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
        // The fifth, added by Story 4.1. The `when` being non-exhaustive again is the
        // mechanism this file documents: a destination whose label is not asserted here is a
        // nav item that could be spelled anything.
        Destination.Tunggakan -> "Tunggakan"
        // The sixth, added by Story 4.3 — and the one the app now opens on.
        Destination.Beranda -> "Beranda"
    }

    /**
     * Taps a rail item by its **label**.
     *
     * Scoped to the rail, because the fleet's rail label ("Kendaraan") and its screen
     * title ("Armada") are different words — and an unscoped text query for the title
     * matches the screen's own heading, which is not what a rail tap is. A query scoped
     * to the rail cannot reach the screen's title at all.
     */
    private fun tapRailItem(label: String) {
        compose.onAllNodes(
            hasText(label) and hasAnyAncestor(hasTestTag(TAG_RAIL)),
        )[0].performClick()
        compose.waitForIdle()
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

    private fun testScreens(
        depositoState: net.klinik.rental.ui.screens.setoran.SetoranUiState =
            net.klinik.rental.ui.screens.setoran.SetoranUiState(),
    ) = Screens(
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
            // Story 3.6's FR-9b wiring, the same no-op stance every other
            // callback in these shell fixtures takes.
            onOpenSettle = {},
            onCloseSettle = {},
            onSettleChange = { it },
            onConfirmSettle = {},
            onSettleErrorDismiss = {},
        ),
        pengaturan = Screens.Pengaturan(
            state = PengaturanUiState(),
            snackbarMessage = null,
            onAdd = {},
            onEdit = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            onOpenIdentity = {},
            onCloseIdentity = {},
            onIdentityChange = { it },
            onSaveIdentity = {},
            onIdentityErrorDismiss = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
        deposito = Screens.Deposito(
            state = depositoState,
            onOpen = {},
            onClose = {},
            onFormChange = { it },
            onSave = {},
            // Story 3.3's dialog pair. Required rather than defaulted on the real
            // wiring, and stated here for the same reason — a default would be a
            // confirm that silently does nothing.
            onConfirmPreview = {},
            onDismissPreview = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
        // Story 4.1's arrears wiring, the same no-op stance every other callback in these
        // shell fixtures takes. `onSelect` is **required** on the real type and is written
        // here for that reason: a defaulted handler would be a list whose rows select
        // nothing, which reads as a broken screen rather than a missing lambda.
        tunggakan = Screens.Tunggakan(
            state = TunggakanUiState(),
            onSelect = {},
            onErrorDismiss = {},
            onRemind = {},
            onReminderChange = {},
            onReminderSend = {},
            onReminderCopy = {},
            onReminderDismiss = {},
            onReminderLinkOpened = {},
            onReminderOpenFailed = {},
            pendingReminderLink = null,
        ),
        // Story 4.3's morning summary. Required for the same reason as every other
        // callback in these fixtures: a defaulted handler would be a quick-action tile
        // that reaches nothing, and the tile is the only door to its action from
        // Beranda.
        beranda = Screens.Beranda(
            state = net.klinik.rental.ui.screens.beranda.BerandaUiState(),
            onDeposit = {},
        ),
    )
}




