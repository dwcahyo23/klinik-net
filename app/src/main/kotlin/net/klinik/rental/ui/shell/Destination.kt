package net.klinik.rental.ui.shell

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnNavItem

/**
 * Every destination the app has, in one table.
 *
 * ## Why the rail and the graph are both generated from this
 *
 * `KnNavRail` and `KnBottomNav` draw destinations; `NavHost` routes to them. If
 * those were two lists, a destination could exist in the chrome and not in the
 * graph — and the failure is not a crash, it is a nav item that looks real and
 * goes nowhere when tapped. `NAV_DESTINATION_UNREACHABLE` is answered here
 * structurally rather than by discipline: [AppShell] builds `KnNavItem`s from
 * [entries] and [AppNavGraph] builds `composable(...)` entries from the same
 * [entries], so the two cannot disagree.
 *
 * ## Why there is no index field
 *
 * `KnNavRail`/`KnBottomNav` take `selectedIndex: Int`, so something has to turn a
 * route into an index. Storing the index here would be a second encoding of the
 * same fact — exactly the "two facts encoded twice" defect that review found
 * three instances of in spec-2-5 (see [indexOf]). [indexOf] derives it from
 * [route] instead, at the one place that reads the back stack.
 *
 * ## Why there is no home screen
 *
 * The epic context reserves a home screen, its four-tile grid and its KPI row
 * for Story 4.3, and the "no Beranda" boundary is what keeps it there. Adding a
 * row here that routes to nothing is the exact defect this table exists to make
 * impossible; adding one that routes to a stub screen is worse. So the rail
 * carries only the three destinations that exist.
 *
 * @property route the navigation-compose route. Stable string, not the enum name,
 *   because these values are what a restored back stack reads back after a
 *   process death and after an app update — an enum rename would orphan a saved
 *   stack.
 * @property labelRes Indonesian label, resolved with `stringResource` at the call
 *   site. A `String` here would freeze today's locale.
 */
enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
) {
    /** The fleet. `KendaraanScreen`. */
    Kendaraan(route = "kendaraan", labelRes = R.string.nav_kendaraan),

    /** The renters. `PenyewaScreen`. */
    Penyewa(route = "penyewa", labelRes = R.string.nav_penyewa),

    /** The rental contracts. `KontrakScreen`. */
    Kontrak(route = "kontrak", labelRes = R.string.nav_kontrak),

    /**
     * The business accounts transfers arrive at. `PengaturanScreen`.
     *
     * The fourth destination, and the first tenant of a place Epic 5's configuration story
     * will also use — which is why it is a destination rather than a top-bar action on the
     * fleet screen. Being in this enum is what puts it in **both** the landscape rail and the
     * portrait bottom nav: `AppShell` derives both from `entries`, so there is no second list
     * to update and none to forget.
     */
    Pengaturan(route = "pengaturan", labelRes = R.string.nav_pengaturan),

    /**
     * The arrears list. `TunggakanScreen`.
     *
     * The fifth destination, and the one the owner opens most — the PRD calls it "layar yang
     * paling sering dibuka". It was added while [`START`] was still on [Kendaraan], because
     * Story 4.1's criteria do not ask for a different launch screen.
     */
    Tunggakan(route = "tunggakan", labelRes = R.string.nav_tunggakan),

    /**
     * The morning summary. `BerandaScreen`.
     *
     * The sixth destination, and the one the app **opens on** — Story 4.3 moved [START] here,
     * because the screen exists to answer "what do I have to do today" and a summary the
     * owner has to go *to* is not a summary.
     *
     * **Last** in the enum, and the ordering is the rail's ordering. UX-DR17's rail is
     * ordered by frequency — Beranda · Setoran · Kendaraan · Penyewa · Tunggakan · Laporan ·
     * Pengaturan — but three of those destinations (Setoran, Laporan and, in the prototype's
     * ordering, the position of Tunggakan) do not exist yet, and reordering the rail three
     * times as they arrive would churn the KDoc that explains the ordering rule. So Beranda is
     * **appended** here, and the rail is re-ordered once the set is complete.
     */
    Beranda(route = "beranda", labelRes = R.string.nav_beranda),
    ;

    companion object {
        /**
         * Where the app opens. Beranda — the morning summary.
         *
         * Story 4.3 moved this from the fleet. The prototype's own rail opens on the
         * dashboard, and the PRD calls the arrears list "layar yang paling sering dibuka";
         * the summary is where the owner starts, so it is where the app starts too.
         */
        val START: Destination = Beranda

        /**
         * The route for the rail item at [index], or `null` when the index is out
         * of range.
         *
         * Deliberately nullable rather than clamping. A clamped index turns a
         * programming error into silent navigation to the wrong screen, and the
         * wrong screen is a contract list where the owner expected their fleet.
         *
         * A companion rather than an instance method: an instance method would
         * need a `Destination` receiver to answer a question about the *list*, so
         * `Destination.routeAt(index)` would not compile and every call site would
         * have to invent an instance first.
         */
        fun routeAt(index: Int): String? = entries.getOrNull(index)?.route

        /**
         * The index of [route] in [entries], or `0` when the route is unknown.
         *
         * `0` rather than `-1` because the rail has to highlight *something* and
         * an out-of-range index would leave every item unselected with no way to
         * say why. `START` is the least-wrong answer for an unknown route.
         */
        fun indexOf(route: String?): Int =
            entries.indexOfFirst { it.route == route }.takeIf { it >= 0 } ?: 0
    }
}

/**
 * The rail's items, built from [Destination.entries].
 *
 * The labels are resolved with `stringResource` because a `String` captured in a
 * field would survive a locale change — the same reason `RStrings` refuses to
 * cache.
 *
 * The icons are the Lucide glyphs named per destination; Material Icons are
 * prohibited by the epic context.
 */
@Composable
internal fun destinationNavItems(): List<KnNavItem> = Destination.entries.map { destination ->
    KnNavItem(
        label = stringResource(destination.labelRes),
        icon = destination.icon,
    )
}

/**
 * The glyph for a destination.
 *
 * A property rather than a `when` at the call site: the set of destinations and
 * the set of icons is the same fact, and a `when` over the enum would be the
 * second place that has to be updated when a fourth screen arrives.
 *
 * That prediction came true in Story 3.1, and the compiler is what enforced it: adding
 * `Pengaturan` to the enum made this `when` non-exhaustive, so the fourth icon could not be
 * forgotten. `Landmark` for accounts — a bank building, which is the same glyph the screen's
 * own add action and empty state use.
 */
private val Destination.icon: ImageVector
    get() = when (this) {
        Destination.Kendaraan -> Lucide.CarFront
        Destination.Penyewa -> Lucide.Users
        Destination.Kontrak -> Lucide.ClipboardList
        Destination.Pengaturan -> Lucide.Landmark
        // The alert glyph, and not a warning triangle: the list's job is to *show* the
        // arrears rather than to alarm about them, and a triangle on every visit would make
        // the icon decorative rather than informative.
        Destination.Tunggakan -> Lucide.CircleAlert
        // The house, for the screen the app opens on. The same glyph the prototype's
        // dashboard uses, so the rail's first item reads as "home" the way every other
        // phone's does.
        Destination.Beranda -> Lucide.House
    }
