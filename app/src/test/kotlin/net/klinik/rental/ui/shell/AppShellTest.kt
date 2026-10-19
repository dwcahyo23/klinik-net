package net.klinik.rental.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.screens.kendaraan.KendaraanUiState
import net.klinik.rental.ui.screens.kendaraan.VehicleFilter
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

    /**
     * And the FAB is **absent on Setoran** — the second route in `FAB_HIDDEN_ROUTES`.
     *
     * The deposit screen's Input tab is the door to the sheet from there, exactly as
     * Beranda's grid is; a FAB over the same sheet would be the third door, and two doors
     * to one action is how one of them goes stale. Asserted the same way the Beranda
     * absence is: by reaching the destination and finding no FAB node at all.
     */
    @Test
    fun `the deposit FAB is absent on the deposit screen`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        compose.onNodeWithText("Setoran").performClick()
        compose.waitForIdle()

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

    // ------------------------------------------------- Story 4.6: search results

    /**
     * Choosing a search result opens **the screen that owns it**, narrowed to that item.
     *
     * ## Why this drives the real shell
     *
     * `GlobalSearchBarTest` renders the bar with its own `onSelect`, so it proves the component
     * *emits* a hit and stops there. Everything after the emit — the navigation, the query
     * handed to the destination, the overlay clearing — happens in `AppShell`, so a test of the
     * bar alone cannot reach any of it.
     *
     * And every shell fixture builds the search state from an empty query, which makes the bar
     * return before drawing results. So this supplies a **typed** query and real rows; without
     * them the results panel never composes and the whole select path is unreachable.
     *
     * The failure this guards is inert rather than loud: dropping the `narrowTo` call, or
     * swapping its two branches so a renter writes to the fleet, would land the owner on the
     * right screen with the wrong list and nothing to say why.
     */
    @Test
    fun `choosing a result opens its screen narrowed to that result`() {
        val wiring = ShellWiring().apply {
            searchQuery = "siti"
            searchRenters = listOf(sitiRenter(), budiRenter())
        }
        compose.setContent {
            theme { AppShell(screens = testScreens(wiring = wiring), isLandscape = true) }
        }

        compose.onNodeWithTag(searchResultTag("Siti Aminah")).performClick()
        compose.waitForIdle()

        // The destination moved…
        compose.onNodeWithText("Belum ada Penyewa").assertExists()
        // …and it moved carrying the query the hit names.
        assertEquals(
            "the renters screen is narrowed to the tapped renter",
            "Siti Aminah",
            wiring.penyewaQuery,
        )
    }

    /**
     * The second jump is the one that fails, and this is the case that says so.
     *
     * `restoreState` re-asserts a destination's saved state, so searching A, tapping, leaving,
     * then tapping B can land on A again — the story's central claim holding on the first
     * navigation and failing silently on every later one. It says which of the two candidate
     * fixes is right: dropping `restoreState` from this navigation, or ordering the writes so
     * the query wins. Both make this pass; only one is correct, and this is what decides.
     */
    @Test
    fun `a second result selection wins over the destination's restored state`() {
        val wiring = ShellWiring().apply {
            searchQuery = "siti"
            searchRenters = listOf(sitiRenter())
        }
        compose.setContent {
            theme { AppShell(screens = testScreens(wiring = wiring), isLandscape = true) }
        }

        // First search: land on the renters screen narrowed to Siti.
        compose.onNodeWithTag(searchResultTag("Siti Aminah")).performClick()
        compose.waitForIdle()
        assertEquals("first jump narrowed to Siti", "Siti Aminah", wiring.penyewaQuery)

        // Leave, then search for someone else and take that one instead.
        compose.onNodeWithText("Kendaraan").performClick()
        compose.waitForIdle()
        wiring.searchQuery = "budi"
        wiring.searchRenters = listOf(sitiRenter(), budiRenter())
        compose.waitForIdle()

        compose.onNodeWithTag(searchResultTag("Budi Santoso")).performClick()
        compose.waitForIdle()

        assertEquals(
            "the second jump narrows to Budi, not back to the restored Siti",
            "Budi Santoso",
            wiring.penyewaQuery,
        )
    }

    /**
     * Choosing a **vehicle** clears the fleet's status filter as well as its query.
     *
     * The fleet applies two narrowing layers, so a search that resets only one of them can
     * open a list with nothing in it: picking a car that is currently `Servis` while the
     * `Tersedia` chip is active lands the owner on an empty fleet with no visible reason.
     * A search says where the thing *is*, not which slice of the list it belongs in.
     */
    @Test
    fun `choosing a vehicle clears the fleet's status filter too`() {
        // The filter is passed to the **constructor**, not set in an `apply` block: the
        // fixture's callbacks write back into these properties on every click, so a value
        // assigned by a block after construction would be indistinguishable from one the
        // shell never touched.
        val wiring = ShellWiring(
            searchQuery = "1235",
            searchVehicles = listOf(avanzaVehicle()),
            // The fleet starts filtered to the cars that are *available*.
            kendaraanFilter = VehicleFilter.Tersedia,
        )
        compose.setContent {
            theme { AppShell(screens = testScreens(wiring = wiring), isLandscape = true) }
        }

        compose.onNodeWithTag(searchResultTag("B 1235 TF")).performClick()
        compose.waitForIdle()

        assertEquals(
            "the filter resets alongside the query",
            VehicleFilter.Semua,
            wiring.kendaraanFilter,
        )
        assertEquals(
            "and the fleet is narrowed to the car that was tapped — the full plate, " +
                "not the fragment the owner typed, so the screen's own bar shows what it can clear",
            "B 1235 TF",
            wiring.kendaraanQuery,
        )
    }

    /**
     * `SEARCH_EVERY_SCREEN`: the field is mounted by the shell, so it rides on every destination.
     *
     * Driven through the real shell and the real rail rather than by rendering the bar in
     * isolation — the earlier version of this test proved the composable exists, which is not
     * the claim.
     */
    @Test
    fun `the search field is present on every destination`() {
        compose.setContent { theme { AppShell(screens = testScreens(), isLandscape = true) } }

        Destination.entries.forEach { destination ->
            // Scoped to the **rail**, not `onNodeWithText`: once a destination's name is also
            // its screen's own title — which Beranda now is, being both the start destination
            // and the first rail item — a bare text match finds two nodes and the injection
            // fails. The rail is what this test is walking.
            compose.onNode(
                hasText(destinationLabel(destination)) and hasAnyAncestor(hasTestTag(TAG_RAIL)),
            ).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(TAG_GLOBAL_SEARCH_FIELD).assertExists()
        }
    }

    /**
     * Navigating away closes the overlay.
     *
     * The results are a view of *this* screen, so they must not travel to the next one and
     * stay pinned over it.
     */
    @Test
    fun `navigating away closes the results overlay`() {
        val wiring = ShellWiring().apply {
            searchQuery = "siti"
            searchRenters = listOf(sitiRenter())
        }
        compose.setContent {
            theme { AppShell(screens = testScreens(wiring = wiring), isLandscape = true) }
        }

        compose.onNodeWithTag(TAG_GLOBAL_SEARCH_RESULTS).assertExists()

        compose.onNodeWithText("Kontrak").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_GLOBAL_SEARCH_RESULTS).assertDoesNotExist()
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
     * The **second** assertion — that the summary leads the table — is what this file's own
     * convention asks for: the rail is built from `entries`, so the entry that leads the
     * table leads the rail. Story 4.3 appended Beranda rather than putting it first,
     * because UX-DR17's rail order needed three destinations that did not exist yet;
     * `the rail is ordered by UX-DR17's frequency order` is the test that closes that.
     */
    @Test
    fun `the start destination is the morning summary`() {
        assertEquals(Destination.Beranda, Destination.START)
    }

    /**
     * The rail **is** UX-DR17's order, and this is the only test that says so.
     *
     * `every entry maps to its own index` proves the table is internally consistent — a
     * re-ordering satisfies it exactly as well as the current order does. This test is what
     * makes the re-ordering a fact rather than a KDoc claim: UX-DR17 orders the rail by how
     * often the owner goes there, and a table that drifts back to declaration order would
     * put Settings second and Settings is the destination he visits least.
     *
     * Kontrak sits **after** Kendaraan because it is not in UX-DR17's list at all — it
     * arrived with Epic 3, after that list was written — and the prototype's screen map
     * hangs it beneath the fleet. That is the one entry whose position is a judgement, and
     * saying so here is what stops it being "tidied" later by someone who has forgotten.
     */
    @Test
    fun `the rail is ordered by UX-DR17's frequency order`() {
        assertEquals(
            listOf(
                Destination.Beranda,
                Destination.Setoran,
                Destination.Kendaraan,
                Destination.Kontrak,
                Destination.Penyewa,
                Destination.Tunggakan,
                Destination.Laporan,
                Destination.Pengaturan,
            ),
            Destination.entries.toList(),
        )
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
        // The seventh, added by Story 4.5. The `when` going non-exhaustive is again the
        // mechanism this file documents: the seventh destination's label could have been
        // spelled anything and every other assertion here would still have passed.
        Destination.Laporan -> "Laporan"
        // The eighth — the deposit screen. The rail's order is now UX-DR17's, with this
        // entry second from the top, and the label is asserted like every other's.
        Destination.Setoran -> "Setoran"
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

    /**
     * Two renters for the search tests.
     *
     * `Siti Aminah` is declared before `Budi Santoso` **on purpose**. A fixture sorted the same
     * way as the results would let a query that wrongly returned the wrong renter still pass,
     * because the assertion would hold either way.
     */
    private fun sitiRenter() = Renter(
        id = "r1",
        name = "Siti Aminah",
        phone = "081234567891",
        bankAccount = "9087654321",
        emergencyContact = "081200000001",
    )

    private fun budiRenter() = Renter(
        id = "r2",
        name = "Budi Santoso",
        phone = "081298765432",
        bankAccount = null,
        emergencyContact = "081200000002",
    )

    /** One car, so the fleet half of the search has something to select. */
    private fun avanzaVehicle() = Vehicle(
        id = "v1",
        plate = "B 1235 TF",
        brand = "Toyota",
        model = "Avanza",
        year = 2023,
        color = "Putih",
        dailyRate = 250_000,
        notes = null,
        isServis = false,
    )

    /**
     * A destination's visible label, resolved from the resource rather than typed out, so this
     * test cannot fall behind a rename the way a literal would.
     */
    private fun destinationLabel(destination: Destination): String =
        ApplicationProvider.getApplicationContext<Context>().getString(destination.labelRes)

    /**
     * The wiring a test can **read back**.
     *
     * ## Why this exists
     *
     * Because `AppShell` takes state by value. A `Screens` of no-op lambdas can prove the shell
     * renders a search field, and it can prove the bar *emits* a hit — but nothing can prove the
     * shell **wrote** that hit to a destination, because the value it was handed never changes.
     *
     * So the three narrowable values live here behind `mutableStateOf` and the callbacks the
     * shell calls write to them. `Screens` itself is derived from them on every composition, so
     * the shell sees the same recomposing state production does.
     *
     * This is the difference between testing a wiring and testing that a wiring exists — and it
     * is what makes `choosing a result opens its screen narrowed to that result` able to fail.
     */
    private class ShellWiring(
        /** The renters screen's narrowing. */
        penyewaQuery: String = "",
        /** The fleet's two narrowers — the shell must write **both**. */
        kendaraanQuery: String = "",
        kendaraanFilter: VehicleFilter = VehicleFilter.Semua,
        /** The shell field's own query, so a test can type into it. */
        searchQuery: String = "",
        /** The rows the field searches, so results exist to tap. */
        searchRenters: List<Renter> = emptyList(),
        searchVehicles: List<Vehicle> = emptyList(),
        /** Incremented by `onClear`, so a test can assert the overlay was dismissed. */
        searchClearCount: Int = 0,
    ) {
        var penyewaQuery by mutableStateOf(penyewaQuery)
        var kendaraanQuery by mutableStateOf(kendaraanQuery)
        var kendaraanFilter by mutableStateOf(kendaraanFilter)
        var searchQuery by mutableStateOf(searchQuery)
        var searchRenters by mutableStateOf(searchRenters)
        var searchVehicles by mutableStateOf(searchVehicles)
        var searchClearCount by mutableStateOf(searchClearCount)

        /**
         * Records a clear **and** empties the query the field's state is derived from.
         *
         * Both halves, because the production handler is handed an immutable snapshot: the
         * real `onClear` tells the ViewModel, and the next composition re-derives
         * `GlobalSearchUiState` from an empty query. A fixture that only counted the call
         * would leave the overlay drawn, because `isActive` still reads the old query — so the
         * test would fail for a reason in the fixture rather than in the shell.
         */
        fun searchCleared() {
            searchClearCount += 1
            searchQuery = ""
        }
    }

    private fun testScreens(
        depositoState: net.klinik.rental.ui.screens.setoran.SetoranUiState =
            net.klinik.rental.ui.screens.setoran.SetoranUiState(),
        wiring: ShellWiring? = null,
    ): Screens = Screens(
        kendaraan = Screens.Kendaraan(
            state = KendaraanUiState(
                query = wiring?.kendaraanQuery.orEmpty(),
                filter = wiring?.kendaraanFilter ?: VehicleFilter.Semua,
            ),
            snackbarMessage = null,
            onQueryChange = { wiring?.kendaraanQuery = it },
            onFilterChange = { wiring?.kendaraanFilter = it },
            onClearQuery = { wiring?.kendaraanQuery = "" },
            onAdd = {},
            onEdit = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
        penyewa = Screens.Penyewa(
            state = PenyewaUiState(query = wiring?.penyewaQuery.orEmpty()),
            snackbarMessage = null,
            onQueryChange = { wiring?.penyewaQuery = it },
            onClearQuery = { wiring?.penyewaQuery = "" },
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
            depositHistory = null,
            onOpenHistory = {},
            onRequestDepositCancel = { _, _, _ -> },
            onRequestDepositEdit = {},
            onDepositHistoryDismiss = {},
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
            // Story 5.1's backup, stated like every other bundle here: this fixture builds
            // the container the shell hands to the settings route, so an omitted callback
            // would be a silent no-op at runtime rather than a compile error.
            onExportErrorDismiss = {},
            onExportGranted = {},
            backupFilename = { "klinik-net-backup-2026-10-19.json" },
            onRestoreFileChosen = {},
            onConfirmRestore = {},
            onDismissRestore = {},
            onRestoreErrorDismiss = {},
            onOpenPinSetup = {},
            onDisablePin = {},
            onAutoLockChange = {},
            onPinSetupDigit = {},
            onPinSetupDelete = {},
            onConfirmPinCodeWritten = {},
            onDismissPinSetup = {},
            onCutoffPick = {},
            onConfirmCutoff = {},
            onDismissCutoffPreview = {},
            onCutoffErrorDismiss = {},
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
            onBackupReminderTap = {},
        ),
        // The seventh, Story 4.5's. Empty but present: the parameter is required so this
        // fixture cannot ship a report with no figures and call the shell green.
        laporan = Screens.Laporan(
            state = net.klinik.rental.ui.screens.laporan.LaporanUiState(),
            periodOptions = listOf("25 Sep - 24 Okt 2026"),
            onSelectPeriod = {},
        ),
        // Story 4.6's field. Required for the same reason every other parameter here is: the
        // compiler found this call site rather than a test discovering at runtime that the
        // shell renders a search box wired to nothing.
        //
        // The state is **derived** from the wiring rather than handed over, so it recomposes as
        // the test types — the same `stateIn` shape production has.
        search = Screens.Search(
            state = GlobalSearchUiState.of(
                query = wiring?.searchQuery.orEmpty(),
                renters = wiring?.searchRenters.orEmpty(),
                vehicles = wiring?.searchVehicles.orEmpty(),
            ),
            onQueryChange = { wiring?.searchQuery = it },
            onClear = { wiring?.searchCleared() },
        ),
        // The deposit screen's wiring, stated like every other bundle here: this fixture
        // builds the container the shell hands to the route, so an omitted callback would
        // be a silent no-op at runtime rather than a compile error.
        setoran = Screens.Setoran(
            state = net.klinik.rental.ui.screens.setoran.SetoranScreenState(),
            onSelectTab = {},
            onRequestDepositCancel = { _, _, _ -> },
            onRequestDepositEdit = {},
            onDismissError = {},
        ),
    )
}




