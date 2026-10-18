package net.klinik.rental.ui.shell

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.klinik.rental.ui.components.KnBottomNav
import net.klinik.rental.ui.components.KnNavRail
import net.klinik.rental.ui.screens.kendaraan.VehicleFilter
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knSpacing

/**
 * The app's chrome: a rail in landscape, a bottom nav in portrait, over the same
 * graph.
 *
 * ## Why orientation is a parameter rather than read inside
 *
 * The signature takes [isLandscape] and the default expression reads
 * `LocalConfiguration` for the Activity's own composition. That is not a
 * convenience — it is what makes both branches testable. A composable that
 * measured the window itself and branched internally would need a Robolectric
 * `@Config` per orientation to reach each branch, and "the same graph, different
 * chrome" would be untestable in the one place it needs to be checked.
 *
 * `Configuration.ORIENTATION_LANDSCAPE` rather than a width comparison: the
 * window manager decides orientation, and a width threshold would put a narrow
 * landscape phone on the portrait layout — the wrong answer for a device that is
 * genuinely sideways. No `WindowSizeClass`: the epic asks for two layouts, not a
 * size-class system.
 *
 * ## Why the selected index is derived rather than stored
 *
 * `KnNavRail`/`KnBottomNav` take `selectedIndex: Int` and navigation-compose
 * tracks a route. Holding both and keeping them in step is the "two facts encoded
 * twice" defect review found three instances of in spec-2-5. So the index is
 * computed from the back stack at the one place that reads it, via
 * [Destination.indexOf], and stored nowhere.
 *
 * ## Why the graph is the same object in both branches
 *
 * One `NavHost`, one [navController], one `startDestination`. If the two
 * orientations each built their own graph, "identical graph in both orientations"
 * (AR-18) would be a convention rather than a fact, and a destination added to
 * one branch and not the other would be a screen that exists only in portrait.
 * `ORIENTATION_FLIP` therefore holds structurally: the branch picks *chrome*, and
 * the back stack lives above it, so a rotation cannot lose the selection.
 *
 * @param screens the three destinations' collected state and actions — see
 *   [Screens]. Passed in rather than reached for, so the shell holds no ViewModel
 *   and a test can drive the whole shell with hand-built state.
 */
@Composable
internal fun AppShell(
    screens: Screens,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    isLandscape: Boolean = LocalConfiguration.current.isLandscape(),
) {
    // The single point that reads the back stack. Everything downstream — which
    // rail item is highlighted — is derived from it, so there is no index held
    // anywhere that could disagree with the route actually showing.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedIndex = Destination.indexOf(backStackEntry?.destination?.route)
    val items = destinationNavItems()
    val spacing = MaterialTheme.knSpacing

    /**
     * Closes the results overlay whenever the destination changes under it.
     *
     * Without this, choosing a rail or bottom-nav item while results are open leaves the
     * overlay pinned over a different screen — a result list belonging to the last screen,
     * drawn on top of this one. The overlay is a view of *where the owner was*; when that
     * changes it has nothing to say.
     *
     * Declared before [navigateTo] because that lambda calls it: a local declared after its
     * use reads as though the two were unrelated.
     */
    fun dismissOverlayOnNavigate() {
        if (screens.search.state.isActive) screens.search.onClear()
    }

    val navigateTo: (Int) -> Unit = { index ->
        // A null route is ignored rather than clamped. Clamping would turn a
        // programming error into silent navigation to the wrong screen, and the
        // wrong screen is a contract list where the owner expected their fleet.
        Destination.routeAt(index)?.let { route ->
            navController.navigate(route) {
                // One entry per destination, and re-tapping the current one is a
                // no-op rather than a stack of duplicates.
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            // The results overlay is a view of *this* screen, so it does not travel
            // to the next one — see `dismissOverlayOnNavigate`.
            dismissOverlayOnNavigate()
        }
    }

    // Whether Beranda is the screen showing — read from the back stack, the one
    // place that knows. The FAB's visibility follows it, and follows nothing else.
    val isOnBeranda = backStackEntry?.destination?.route == Destination.Beranda.route

    /**
     * Narrows the screen a result belongs to, so choosing a hit opens that screen **on that
     * item** rather than on the full list.
     *
     * ## Why this writes the destination's own query
     *
     * Because the screens keep their own search bars, and that is deliberate: the bar is also
     * the only control that **widens** a narrowed list again. Removing it, as the spec's task
     * list proposed, would leave a list the owner could filter but never unfilter — and would
     * strand the screen's `query` state with no writer.
     *
     * So the shell's field searches **across** the app and hands its finding to the screen that
     * owns it; the screen's own bar then shows that query and can clear it. The two controls
     * have different jobs rather than being duplicates.
     *
     * ## Why the fleet's status filter is cleared with its query
     *
     * Because the fleet screen applies **two** narrowing layers — the query and a status chip —
     * and a search that cleared only one of them can produce a list with nothing in it and no
     * visible reason. Choosing a car that is currently `Servis` while the `Tersedia` chip is
     * active opens an empty fleet, and the owner has no way to tell that from "the search is
     * broken". So the search resets **both** narrowers: a search says where the thing *is*, not
     * which slice of the list it should appear in.
     */
    fun narrowTo(hit: SearchResult) {
        when (hit) {
            is SearchResult.RenterHit -> screens.penyewa.onQueryChange(hit.renter.name)
            is SearchResult.VehicleHit -> {
                screens.kendaraan.onQueryChange(hit.vehicle.plate)
                screens.kendaraan.onFilterChange(VehicleFilter.Semua)
            }
        }
    }

    /**
     * Back closes the results overlay **before** it leaves the screen.
     *
     * An owner who typed a query and pressed Back has lost their place if the first Back
     * navigates: the thing they were doing was dismissible, and Back skipped past it to the
     * screen behind. So the overlay takes the first press and the destination keeps the
     * second — the same one-step-at-a-time rule the app applies to its sheets.
     *
     * Registered **only** while the overlay is active, so with nothing typed Back behaves
     * exactly as it did before this story. A handler left installed would silently eat the
     * first Back on every destination for the rest of the session.
     */
    BackHandler(enabled = screens.search.state.isActive) {
        screens.search.onClear()
    }

    // The deposit FAB, mounted by the **shell** rather than by any destination.
    //
    // Story 3.2's AC opens the deposit form from "Beranda atau speed dial", and neither
    // exists: UX-DR17 assigned the FAB and speed dial to Epic 2, and no Epic 2 story
    // built one, while Beranda itself is Story 4.3. So the shell carries a
    // `fab-mini` — UX-DR17's 54dp variant for exactly the single-action case — rather
    // than a speed dial holding one entry, which is a menu that hides the action behind
    // a tap.
    //
    // Mounted here and not inside a screen because it must appear over **every** screen.
    // When Beranda arrives it becomes the one door there and the FAB moves inside it,
    // which is what UX-DR17 requires: *"speed dial TIDAK muncul di Beranda"*.
    //
    // `Box`, because a `ModalBottomSheet` must be composed beside the layout rather
    // than inside it — and the FAB needs to float *over* the current screen, which a
    // `Column` sibling could not do. Both are children of one `Box`, so the shell
    // draws the graph and the sheet in the same place.
    Box(modifier.fillMaxSize()) {
        // Story 4.6's search field, **above** whichever branch draws.
        //
        // Mounted here rather than in each screen because "available on every screen" is then
        // a property of the layout rather than seven reminders: a destination added next year
        // inherits it, and one added today cannot ship without it. The two screens that used
        // to draw their own bars stopped, so the app has one search control rather than three.
        //
        // It sits above the graph in a `Column` rather than floating over it, so it never
        // covers a screen's own top bar — and the results overlay hangs beneath the field
        // inside its own bound, so a broad query cannot hide the destination either.
        Column(modifier.fillMaxSize()) {
            GlobalSearchBar(
                state = screens.search.state,
                onQueryChange = screens.search.onQueryChange,
                onClear = screens.search.onClear,
                // A result opens the destination that owns it, by the route the result
                // carries. Derived here rather than passed in, because the nav controller
                // lives in this function — see `Screens.Search`'s KDoc.
                onSelect = { hit ->
                    navController.navigate(hit.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    // The destination opens **on that item** rather than on the full list.
                    narrowTo(hit)
                    // And the panel closes: the owner is now looking at the screen they
                    // asked for, and an overlay still listing results would sit over the
                    // answer.
                    screens.search.onClear()
                },
            )

            Box(Modifier.weight(1f)) {
                if (isLandscape) {
                    Row(Modifier.fillMaxSize()) {
                        KnNavRail(
                            items = items,
                            selectedIndex = selectedIndex,
                            onSelect = navigateTo,
                            modifier = Modifier.testTag(TAG_RAIL),
                        )
                        AppNavGraph(
                            navController = navController,
                            screens = screens,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        AppNavGraph(
                            navController = navController,
                            screens = screens,
                            modifier = Modifier.weight(1f),
                        )
                        KnBottomNav(
                            items = items,
                            selectedIndex = selectedIndex,
                            onSelect = navigateTo,
                            modifier = Modifier.testTag(TAG_BOTTOM_NAV),
                        )
                    }
                }

                // The FAB floats over the content area, aligned to its bottom-end with the
                // shell's own gutter. `align` rather than a nested `Box`, so it adds no layout
                // of its own and cannot shift the graph above it.
                //
                // **Hidden on Beranda.** UX-DR17 is explicit — *"speed dial TIDAK muncul di
                // Beranda"* — and the reason is worth keeping: the grid's "Catat Setoran" tile
                // is the only door to that action from the morning screen, and two doors to one
                // action is how one of them goes stale. `DepositFab`'s KDoc has carried this
                // promise since Story 3.2, and this is the line it was waiting for.
                //
                // The check is the **route**, not a flag: the route is the fact, and a flag
                // beside it would be a second thing to keep in step — the defect this repo
                // finds whenever a fact is encoded twice.
                DepositFab(
                    visible = !screens.deposito.state.isOpen && !isOnBeranda,
                    onOpen = screens.deposito.onOpen,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(spacing.lg),
                )

                // And the sheet, last so it composes above the FAB — two windows over one
                // state is the defect Story 2.8's review found, and a FAB showing *through*
                // an open sheet is the same shape in a smaller size.
                DepositHost(screens.deposito)
            }
        }
    }
}

/**
 * Whether the current configuration is landscape.
 *
 * Named rather than a property so the import reads at the call site and so a test
 * that wants the *default* expression has one thing to look up.
 */
@Composable
private fun Configuration.isLandscape(): Boolean = orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * Tags on the two chrome variants.
 *
 * They live here rather than in `Navigation.kt` because the components themselves
 * are unchanged by this story — it is the *shell* that now decides which one is
 * mounted, so the shell is what a test needs to address.
 */
const val TAG_RAIL = "shell-nav-rail"
const val TAG_BOTTOM_NAV = "shell-bottom-nav"
