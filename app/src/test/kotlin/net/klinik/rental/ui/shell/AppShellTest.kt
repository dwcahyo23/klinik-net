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
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
import net.klinik.rental.ui.screens.mobil.MobilUiState
import net.klinik.rental.ui.screens.mobil.TAG_SEARCH as TAG_KENDARAAN_SEARCH
import net.klinik.rental.ui.screens.mobil.VehicleFilter
import net.klinik.rental.ui.screens.kontrak.KontrakUiState
import net.klinik.rental.ui.screens.pengaturan.PengaturanUiState
import net.klinik.rental.ui.screens.penyewa.PenyewaUiState
import net.klinik.rental.ui.screens.penyewa.TAG_SEARCH as TAG_PENYEWA_SEARCH
import net.klinik.rental.ui.screens.setoran.SetoranUiState
import net.klinik.rental.ui.screens.setoran.TAG_SETORAN_TITLE
import net.klinik.rental.ui.screens.tunggakan.TunggakanUiState
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The shell: one graph, and chrome that changes with **window width**.
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
 *
 * ## Why rail taps go through a content description and not a label
 *
 * Because the rail this shell mounts is the prototype's **compact** one, and a compact rail
 * hides its labels (`shared.css:1031`). Every test here that navigates by tapping a rail
 * destination therefore addresses it by the name the rail exposes to a screen reader, not
 * by visible text — which is the only thing a compact rail has. Before this story the rail
 * drew its labels and the tests could use them; that is the one thing about them that had to
 * change.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
class AppShellTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a wide window shows the rail and not the bottom nav`() {
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = true) } }

        compose.onNodeWithTag(TAG_RAIL).assertExists()
        compose.onAllNodesWithTag(TAG_BOTTOM_NAV).assertCountEquals(0)
    }

    @Test
    fun `a narrow window shows the bottom nav and not the rail`() {
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = false) } }

        compose.onNodeWithTag(TAG_BOTTOM_NAV).assertExists()
        compose.onAllNodesWithTag(TAG_RAIL).assertCountEquals(0)
    }

    /**
     * The rail the shell mounts is the **compact** one, so it does not draw its labels.
     *
     * `shared.css:1030-1032` narrows the rail to `--rail-w-compact` and hides
     * `.rail-item span`. `KnNavRailWidthCompact` existed in `Navigation.kt` and nothing passed
     * it, so this is the assertion that the compact rail is reachable at all — a failure here
     * means the constant went back to being dead code with its labels back on.
     *
     * Asserted as *absence*, because the presence of a hidden label would be a different bug:
     * a transparent or zero-height label still occupies the item and is still announced, which
     * is the opposite of what the rule is for.
     */
    @Test
    fun `the compact rail draws no destination labels`() {
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = true) } }

        Destination.railEntries.forEach { destination ->
            compose.onAllNodes(
                hasText(labelOf(destination), substring = true) and
                    hasAnyAncestor(hasTestTag(TAG_RAIL)),
            ).assertCountEquals(0)
        }
    }

    /**
     * And every destination is still *named*, because a compact rail is icons only.
     *
     * The rail's `Icon` is `contentDescription = null` on the strength that the label text is
     * the destination's name — a premise a compact rail falsifies. So this asserts the name
     * reaches the accessibility tree through the surface itself, which is the only remaining
     * route to it. Without this, `the compact rail draws no destination labels` would be
     * satisfied by eight anonymous buttons.
     */
    @Test
    fun `the compact rail still names every destination`() {
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = true) } }

        Destination.railEntries.forEach { destination ->
            compose.onAllNodes(
                hasContentDescriptionExactly(labelOf(destination)) and
                    hasAnyAncestor(hasTestTag(TAG_RAIL)),
            ).assertCountEquals(1)
        }
    }

    /**
     * The app opens on **Beranda**, the morning summary.
     *
     * Story 4.3 moved `Destination.START` from the fleet to the summary, because the
     * summary is the screen that answers "what do I have to do today" and a summary the
     * owner has to go *to* is not a summary. The assertion is on content only the summary
     * draws — the rail's "Dashboard" label is also the screen's own title, so asserting on
     * that would prove nothing about which screen is up.
     */
    @Test
    fun `the app opens on the morning summary`() {
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = true) } }

        // The summary's own figure label, which no other screen draws.
        //
        // Uppercase, because the KPI label is: the prototype's `.kpi-label` carries
        // `text-transform: uppercase`, and the tile now applies it the way
        // `KnTableHeaderRow` does. `ignoreCase` so the assertion is about *which*
        // label this is, not about the casing convention — the casing has its own
        // test, in `KnKpiTile`'s.
        compose.onNodeWithText("Pendapatan hari ini", ignoreCase = true).assertExists()
    }

    @Test
    fun `tapping a rail destination opens that screen`() {
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = true) } }

        compose.onNodeWithContentDescription("Penyewa").performClick()
        compose.waitForIdle()

        // Not the title: "Penyewa" is both the rail label and the screen's own
        // title, so an assertion on it proves nothing about which screen is up.
        // The proof is content only the renters screen draws — an empty list
        // addressed in renters' words.
        compose.onNodeWithText("Belum ada Penyewa").assertExists()
    }

    @Test
    fun `tapping a bottom-nav destination opens that screen`() {
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = false) } }

        compose.onNodeWithText("Tunggakan").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Tidak ada tunggakan").assertExists()
    }

    @Test
    fun `tapping the already-selected destination does not stack a duplicate`() {
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = true) } }

        // The app opens on Beranda now, so the *already-selected* destination is the
        // summary — and this still asserts the fleet's own screen after tapping its
        // label, because the claim is about the back stack rather than about the start.
        compose.onNodeWithContentDescription("Mobil").performClick()
        compose.waitForIdle()

        // `launchSingleTop` plus `restoreState`: re-tapping is a no-op, not a
        // second copy of the fleet. The symptom of getting this wrong is a back
        // stack that grows on every tap, so the owner walks back through screens
        // they never left.
        compose.onAllNodesWithTag(TAG_RAIL).assertCountEquals(1)
        compose.onNodeWithText("Belum ada Kendaraan").assertExists()
    }

    /**
     * Every destination the graph declares is reachable **in the chrome**, by name.
     *
     * Scoped to the rail, not to the screen: "Mobil" is also the fleet screen's own title,
     * so an unscoped query matches two nodes and says nothing about the chrome. The chrome is
     * what is under test.
     *
     * Because the rail and the graph are generated from one table, a
     * destination cannot be in the chrome and missing from the graph —
     * `NAV_DESTINATION_UNREACHABLE` answered by construction.
     *
     * Matched on the destination's **name** — the content description — because this rail is
     * the compact one and does not draw labels. The name is what the owner is actually given
     * here, so it is what "the chrome carries this destination" has to mean.
     */
    @Test
    fun `the rail carries every destination the graph declares`() {
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = true) } }

        Destination.railEntries.forEach { destination ->
            compose.onAllNodes(
                hasContentDescriptionExactly(labelOf(destination)) and
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
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = true) } }

        val targets = compose.onAllNodes(
            hasClickAction() and (
                hasTestTag(TAG_RAIL) or hasAnyAncestor(hasTestTag(TAG_RAIL))
                ),
        )
        assertEquals(
            "rail rendered the wrong number of navigation targets",
            Destination.railEntries.size,
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
        compose.setContent { theme { AppShell(screens = testScreens(), showRail = false) } }

        val targets = compose.onAllNodes(
            hasClickAction() and (
                hasTestTag(TAG_BOTTOM_NAV) or hasAnyAncestor(hasTestTag(TAG_BOTTOM_NAV))
                ),
        )
        assertEquals(
            "bottom nav rendered the wrong number of navigation targets",
            Destination.railEntries.size,
            targets.fetchSemanticsNodes().size,
        )
        targets.fetchSemanticsNodes().indices.forEach { i ->
            targets[i].assertWidthIsAtLeast(MIN_TARGET)
            targets[i].assertHeightIsAtLeast(MIN_TARGET)
        }
    }

    /**
     * `showRail` omitted: the production default picks the chrome.
     *
     * Every other test in this file passes `showRail` explicitly, which is the right way to
     * reach a branch — and which would leave `MainActivity`'s single call site, the only one
     * that *does* omit it, completely unexecuted. So these compose `AppShell` the way the
     * Activity does and assert the default reads **width**, one window either side of the
     * 600dp breakpoint.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h720dp-land")
    fun `the default reads a wide window as a rail`() {
        compose.setContent { theme { AppShell(screens = testScreens()) } }

        compose.onNodeWithTag(TAG_RAIL).assertExists()
        compose.onAllNodesWithTag(TAG_BOTTOM_NAV).assertCountEquals(0)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-port")
    fun `the default reads a narrow window as a bottom nav`() {
        compose.setContent { theme { AppShell(screens = testScreens()) } }

        compose.onNodeWithTag(TAG_BOTTOM_NAV).assertExists()
        compose.onAllNodesWithTag(TAG_RAIL).assertCountEquals(0)
    }

    /**
     * And the breakpoint is width rather than orientation, which is the whole reason this
     * change exists.
     *
     * Both of these windows are **portrait**, and the old `Configuration.ORIENTATION_LANDSCAPE`
     * rule put both of them on the bottom nav — which on the audited 711x1138dp tablet
     * contradicted the prototype, which keeps a compact rail in portrait (`shared.css:1030`)
     * and hides the bottom nav outright (`shared.css:292`).
     *
     * Asserted on both sides of 600dp *in portrait* rather than across orientations, because
     * a test that varied orientation would pass under the old rule for one of its two cases
     * and prove nothing about which input the shell actually reads.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w711dp-h1138dp-port")
    fun `the audited tablet in portrait gets the compact rail`() {
        compose.setContent { theme { AppShell(screens = testScreens()) } }

        compose.onNodeWithTag(TAG_RAIL).assertExists()
        compose.onAllNodesWithTag(TAG_BOTTOM_NAV).assertCountEquals(0)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-port")
    fun `a phone in portrait gets the bottom nav`() {
        compose.setContent { theme { AppShell(screens = testScreens()) } }

        compose.onNodeWithTag(TAG_BOTTOM_NAV).assertExists()
        compose.onAllNodesWithTag(TAG_RAIL).assertCountEquals(0)
    }

    /**
     * And a **landscape** window narrower than the breakpoint still gets the bottom nav.
     *
     * The case the orientation rule got wrong the other way: an 800dp landscape phone is
     * sideways, and the old rule gave it the rail purely for being sideways. This asserts the
     * rule reads the axis the layout actually depends on.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w560dp-h320dp-land")
    fun `a narrow landscape window gets the bottom nav too`() {
        compose.setContent { theme { AppShell(screens = testScreens()) } }

        compose.onNodeWithTag(TAG_BOTTOM_NAV).assertExists()
        compose.onAllNodesWithTag(TAG_RAIL).assertCountEquals(0)
    }

    /**
     * The start destination is the dashboard.
     *
     * The prototype's rail opens on "Dashboard" (`prototype/shared.js:96`), and the app's
     * rail now reads in the same order.
     */
    @Test
    fun `the start destination is the dashboard`() {
        assertEquals(Destination.Dashboard, Destination.START)
    }

    /**
     * The rail **is** the prototype's order, and this is the only test that says so.
     *
     * `every entry maps to its own index` proves the table is internally consistent — a
     * re-ordering satisfies it exactly as well as the current order does. This test is what
     * makes the re-ordering a fact rather than a KDoc claim: the prototype's
     * `shared.js:95-104` lists the pages in this order, and a table that drifted back to
     * declaration order would put Settings second, and Settings is the destination the
     * owner visits least.
     *
     * `Kontrak` is **not** in the rail: the prototype has no contract page — its contracts
     * live inside the renter's detail pane (`renters.html:247-264`) — so the app reaches
     * the contract screen from there and keeps it out of the chrome.
     */
    @Test
    fun `the rail is ordered by the prototype's order`() {
        assertEquals(
            listOf(
                Destination.Dashboard,
                Destination.Setoran,
                Destination.Mobil,
                Destination.Penyewa,
                Destination.Tunggakan,
                Destination.Laporan,
                Destination.Pengaturan,
            ),
            Destination.railEntries.toList(),
        )
    }

    /**
     * The contract screen is composed but not in the rail.
     *
     * The prototype reaches a renter's contracts from that renter's detail pane, so the
     * app's contract destination has no rail item — but it is still a route, and a route
     * that is composed but not in the rail is the same fact seen from the other side.
     */
    @Test
    fun `the contract screen is composed but not in the rail`() {
        assertEquals(
            false,
            Destination.Kontrak.showInRail,
        )
        assertTrue(
            "the contract screen must not be a rail item",
            Destination.railEntries.none { it == Destination.Kontrak },
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
        assertEquals(null, Destination.routeAt(Destination.railEntries.size))
    }

    @Test
    fun `every entry maps to its own index`() {
        Destination.railEntries.forEachIndexed { index, destination ->
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
                    showRail = true,
                )
                val entry by navController.currentBackStackEntryAsState()
                seen = entry?.destination?.route
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Penyewa").performClick()
        compose.waitForIdle()

        assertEquals(Destination.Penyewa.route, seen)
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
        Destination.Mobil -> "Mobil"
        Destination.Penyewa -> "Penyewa"
        Destination.Kontrak -> "Kontrak"
        Destination.Pengaturan -> "Pengaturan"
        // The `when` being non-exhaustive is the mechanism this file documents: a
        // destination whose label is not asserted here is a nav item that could be spelled
        // anything.
        Destination.Tunggakan -> "Tunggakan"
        // The one the app now opens on.
        Destination.Dashboard -> "Dashboard"
        // The period report.
        Destination.Laporan -> "Laporan"
        // The deposit screen.
        Destination.Setoran -> "Setoran"
    }

    /**
     * Taps a rail item by the name the rail gives it.
     *
     * By **content description**, not by visible text, because the rail the shell mounts is the
     * prototype's compact one and a compact rail draws no labels (`shared.css:1031`). The
     * description is where a compact rail keeps the destination's name — see `KnNavRailItem`.
     *
     * Scoped to the rail for the reason it always was: the fleet's destination is "Mobil"
     * while its screen title is "Armada", and an unscoped query for the screen's own heading
     * would tap the heading instead of the rail. Scoping keeps the tap on the chrome.
     */
    private fun tapRailItem(label: String) {
        compose.onAllNodes(
            hasContentDescriptionExactly(label) and hasAnyAncestor(hasTestTag(TAG_RAIL)),
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
        mobil = Screens.Mobil(
            state = MobilUiState(
                query = wiring?.kendaraanQuery.orEmpty(),
                filter = wiring?.kendaraanFilter ?: VehicleFilter.Semua,
            ),
            snackbarMessage = null,
            onQueryChange = { wiring?.kendaraanQuery = it },
            onFilterChange = { wiring?.kendaraanFilter = it },
            onClearQuery = { wiring?.kendaraanQuery = "" },
            onAdd = {},
            onSelect = {},
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
            onSelect = {},
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
            onRiskFilterChange = {},
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
        dashboard = Screens.Dashboard(
            state = net.klinik.rental.ui.screens.dashboard.DashboardUiState(),
            onDeposit = {},
            onBackupReminderTap = {},
        ),
        // The seventh, Story 4.5's. Empty but present: the parameter is required so this
        // fixture cannot ship a report with no figures and call the shell green.
        laporan = Screens.Laporan(
            state = net.klinik.rental.ui.screens.laporan.LaporanUiState(),
            periodOptions = listOf("25 Sep - 24 Okt 2026"),
            onSelectPeriod = {},
            onSelectMode = {},
            onCustomFromChange = {},
            onCustomToChange = {},
            onApplyCustomRange = {},
            onExportFileChosen = {},
            csvFilename = { "laporan.csv" },
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
            onHistoryQueryChange = {},
            onClearHistoryQuery = {},
            onSelectHistoryFilter = {},
        ),
    )
}




