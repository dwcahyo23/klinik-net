package net.klinik.rental.ui.shell

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBottomNav
import net.klinik.rental.ui.components.KnNavRail
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knSpacing

/**
 * The app's chrome: a rail in landscape, a bottom nav in portrait, over the same
 * graph.
 *
 * ## Why width is a parameter rather than read inside
 *
 * The signature takes [showRail] and the default expression reads
 * `LocalConfiguration` for the Activity's own composition. That is not a
 * convenience — it is what makes both branches testable. A composable that
 * measured the window itself and branched internally would need a Robolectric
 * `@Config` per width to reach each branch, and "the same graph, different
 * chrome" would be untestable in the one place it needs to be checked.
 *
 * ## Why **width**, and not orientation
 *
 * Because orientation is not what the choice is about, and the device audit is
 * what proved it. On the 711x1138dp tablet, portrait gets the **72dp compact
 * rail** the prototype specifies (`shared.css:1030-1032` — the rail narrows to
 * `--rail-w-compact` and `.rail-item span { display: none }`), while a 360dp
 * phone cannot afford a rail at all — 72dp is 20% of the screen — and needs the
 * bottom bar. Both devices are portrait. Orientation cannot separate them; width
 * can.
 *
 * The prototype only ever previewed a 1280x800 frame, so it hid `.bottom-nav`
 * outright (`shared.css:292`) and had no rule to copy for the narrow case.
 * [NAV_RAIL_BREAKPOINT_DP] is therefore this app's own, and it is named here
 * rather than left as a bare literal so the divergence from the prototype is
 * visible rather than accidental.
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
 * branches each built their own graph, "identical graph in both widths"
 * (AR-18) would be a convention rather than a fact, and a destination added to
 * one branch and not the other would be a screen that exists only on a wide
 * window. `ORIENTATION_FLIP` therefore holds structurally: the branch picks *chrome*, and
 * the back stack lives above it, so a rotation cannot lose the selection.
 *
 * @param screens the destinations' collected state and actions — see
 *   [Screens]. Passed in rather than reached for, so the shell holds no ViewModel
 *   and a test can drive the whole shell with hand-built state.
 */
@Composable
internal fun AppShell(
    screens: Screens,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    showRail: Boolean = LocalConfiguration.current.showsRail(),
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

    // ## Why the insets are consumed **here** and nowhere else
    //
    // `MainActivity` calls `enableEdgeToEdge()`, which is a promise that the window
    // extends under the status and navigation bars — and a promise nobody kept. Before
    // this line the app contained no `WindowInsets` reference at all, so the top bar
    // was drawn at y=0 underneath the clock and the content sat under the gesture bar.
    //
    // Consumed at the outermost layout rather than per screen, so it is a property
    // of the shell instead of a rule every destination has to remember, and a screen
    // added next year inherits the clearance with it. Every screen below this
    // `Box` — including the sheets, which are separate windows and handle their own —
    // draws inside the already-inset area.
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // `fillMaxSize`, not `weight`: the provider's content lambda is not a `ColumnScope`,
        // and the outer `Box` already sizes this to the whole inset area.
        Box(Modifier.fillMaxSize()) {
            if (showRail) {
                Row(Modifier.fillMaxSize()) {
                    KnNavRail(
                        items = items,
                        selectedIndex = selectedIndex,
                        onSelect = navigateTo,
                        // The rail is **compact** here, and this is the only call site
                        // that says so. `KnNavRailWidthCompact` (`Navigation.kt`) has
                        // existed since the component was written and nothing ever
                        // passed it, so the rail drew at `--rail-w` (88dp) with its
                        // labels and the prototype's compact rule — 72dp, no labels
                        // (`shared.css:1030-1032`) — described a layout nothing
                        // rendered. This branch is the only place the compact rail is
                        // reachable, because the other branch draws the bottom nav.
                        compact = true,
                        // The brand mark the prototype's rail carries at its head
                        // (`shared.js:334` — a 44x44 logo above the items). The slot
                        // existed with nothing ever passing it, so the rail's top band
                        // was an empty 52dp.
                        logoSlot = {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.logo_mark),
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                            )
                        },
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

            // The deposit sheet, mounted by the shell rather than by any destination.
            //
            // It is a `ModalBottomSheet`, which must be composed **beside** the layout
            // rather than inside it — and it is a child of this same `Box`, so the shell
            // draws the graph and the sheet in the same place.
            //
            // **Not** mounted while the Setoran screen is showing the form inline. The
            // prototype's Input tab *is* the form (`payments.html`), so on that screen the
            // form is already on the page and a sheet over it would be the same form
            // twice.
            //
            // The check is on the **route** as well as the flag. A correction opened from
            // a contract row sets the same form state, and the owner is on the Kontrak
            // screen when they do it — so the sheet must still open there. Only the
            // Setoran screen, on its Input tab, draws the form itself.
            val isSetoranShowing = backStackEntry?.destination?.route == Destination.Setoran.route
            if (!(isSetoranShowing && screens.setoran.state.isShowingForm)) {
                DepositHost(screens.deposito)
            }
        }
    }
}

/**
 * Whether this configuration is wide enough for the rail.
 *
 * `screenWidthDp` rather than a `WindowMetrics` read or `LocalWindowInfo`: the
 * configuration is already a dependency of this file and its value is what the
 * shell's default expression needs, while `WindowMetrics` would add an import to
 * answer the same question.
 *
 * Named rather than a property so the import reads at the call site and so a test
 * that wants the *default* expression has one thing to look up.
 */
@Composable
private fun Configuration.showsRail(): Boolean = screenWidthDp >= NAV_RAIL_BREAKPOINT_DP

/**
 * The window width at which the rail replaces the bottom nav, in dp.
 *
 * It is **this app's**, not the prototype's. `shared.css:292` hides `.bottom-nav`
 * unconditionally because the prototype only ever previewed a 1280x800 frame, so it
 * had no narrow case to answer and no rule to copy.
 *
 * 600dp is the conventional medium-window boundary, and it is the number that makes
 * both audited cases land where the spec's acceptance table requires: a 711dp
 * portrait tablet is above it and gets the compact rail; a 360dp phone is below it
 * and gets the bottom nav. An 800dp landscape phone is also above it — deliberately,
 * since an 88dp rail there is 11% of the screen, not the 25% an orientation-based
 * rule would have spent.
 *
 * `Int` rather than `Dp` because [Configuration.screenWidthDp] is already in dp, and
 * converting a threshold back into density pixels to compare it against a dp value
 * would be a step that could only be wrong.
 */
private const val NAV_RAIL_BREAKPOINT_DP = 600

/**
 * Tags on the two chrome variants.
 *
 * They live here rather than in `Navigation.kt` because the components themselves
 * are unchanged by this story — it is the *shell* that now decides which one is
 * mounted, so the shell is what a test needs to address.
 */
const val TAG_RAIL = "shell-nav-rail"
const val TAG_BOTTOM_NAV = "shell-bottom-nav"
