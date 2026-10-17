package net.klinik.rental.ui.shell

import android.content.res.Configuration
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
        }
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

        // The FAB floats over whichever branch drew, aligned to the bottom-end with
        // the shell's own gutter. `align` rather than a nested `Box`, so it adds no
        // layout of its own and cannot shift the graph above it.
        DepositFab(
            visible = !screens.deposito.state.isOpen,
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
