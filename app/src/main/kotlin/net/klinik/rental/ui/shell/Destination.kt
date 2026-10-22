package net.klinik.rental.ui.shell

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.Wallet
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
 * ## Why the order is the prototype's
 *
 * `prototype/shared.js:95-104` lists the prototype's pages, and the rail reads in
 * that order: Dashboard, Setoran, Mobil, Penyewa, Tunggakan, Laporan, Pengaturan.
 * The app's own order had **Beranda** first and **Kontrak** third.
 *
 * The prototype's list has eight entries but only **seven** are app screens. The
 * eighth, `components.html`, is the design-system catalogue — a page about the
 * stylesheet rather than a screen the owner uses, so it has no Android
 * equivalent. `index.html` is likewise not a screen: it is the prototype's own
 * summary page, and `klinik-net-logo.html` is a brand sheet. Neither is ported.
 *
 * `Kontrak` is the other difference, and it comes from the same place — the
 * prototype has no separate contract page. Its contracts live inside the renter's
 * detail pane (`prototype/renters.html:247-264`), which is where this app's
 * [Penyewa] pane now carries them, so [Kontrak] stays a composed destination
 * with no rail item.
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
    /**
     * Whether this destination appears in the rail and the bottom nav.
     *
     * `true` for every destination the owner reaches by name. `false` for the one the
     * prototype reaches by **context** instead: its contracts live inside the renter's
     * detail pane (`prototype/renters.html:247-264`), so the app's contract screen is
     * reached from there rather than from the rail.
     *
     * The flag lives on the **destination** rather than as a separate list because the
     * rail and the graph are both generated from this table — and a destination with no
     * `composable(...)` entry is a nav item that navigates to nothing. A destination
     * that is composed but not in the rail is the same fact seen from the other side,
     * and one flag states it once.
     */
    val showInRail: Boolean = true,
) {
    /**
     * The dashboard. `DashboardScreen`.
     *
     * The one the app **opens on**, and the prototype's first rail item
     * (`shared.js:96`). It is the owner's morning screen: the period's income,
     * the arrears, the fleet and the charts.
     */
    Dashboard(route = "dashboard", labelRes = R.string.nav_dashboard),

    /**
     * The deposit screen. `SetoranScreen` — two tabs: Input, and the cross-contract
     * history.
     *
     * Second in the prototype's order, because recording money is the action the owner
     * performs most.
     */
    Setoran(route = "setoran", labelRes = R.string.nav_setoran),

    /**
     * The fleet. `MobilScreen`.
     *
     * The prototype's `cars.html`, which calls this page "Mobil" (`shared.js:98`).
     * The app used to call the screen "Kendaraan" and the page "Armada"; the
     * prototype's word is the one the rail now uses.
     */
    Mobil(route = "mobil", labelRes = R.string.nav_mobil),

    /**
     * The rental contracts. `KontrakScreen`.
     *
     * **Not in the rail** — `showInRail = false`. The prototype has no contract page:
     * a contract belongs to a renter, and the prototype shows it inside that renter's
     * detail pane (`prototype/renters.html:247-264`). The app's contract screen is
     * reached from there.
     *
     * It stays a **destination** rather than becoming a component of the renters screen
     * because it is a screen with its own master-detail, its own forms and its own
     * ViewModel; folding it in would mean the renters screen reached into another
     * screen's wiring.
     */
    Kontrak(route = "kontrak", labelRes = R.string.nav_kontrak, showInRail = false),

    /**
     * The renters. `PenyewaScreen`.
     *
     * The prototype's `renters.html`. A renter's detail pane carries that
     * renter's contract, its exceptions and its payment history — the app's
     * [Kontrak] screen is reached from there, not from the rail.
     */
    Penyewa(route = "penyewa", labelRes = R.string.nav_penyewa),

    /**
     * The arrears list. `TunggakanScreen`.
     *
     * The one the owner opens most among the data lists — the PRD calls it "layar yang
     * paling sering dibuka".
     */
    Tunggakan(route = "tunggakan", labelRes = R.string.nav_tunggakan),

    /** The period report. `LaporanScreen`. */
    Laporan(route = "laporan", labelRes = R.string.nav_laporan),

    /**
     * The business accounts transfers arrive at, and everything else that is set.
     * `PengaturanScreen`.
     *
     * **Last** in the enum, matching the prototype's order: settings is the
     * destination the owner visits least, so it sits at the rail's end rather than
     * where declaration accident put it.
     */
    Pengaturan(route = "pengaturan", labelRes = R.string.nav_pengaturan),
    ;

    companion object {
        /**
         * Where the app opens. Dashboard — the prototype's first rail item.
         */
        val START: Destination = Dashboard

        /**
         * The destinations the rail and the bottom nav draw.
         *
         * The **filtered** list, not [entries]. [Kontrak] is composed in the graph but
         * has no rail item, so the two lists are different lengths and the index a rail
         * item carries is an index into *this* list — not into [entries].
         *
         * That is why [indexOf] and [routeAt] both go through here: a route's rail index
         * and a rail index's route have to be the same mapping, and the one place that
         * knows the mapping is the one that knows which destinations are in the rail.
         */
        val railEntries: List<Destination> = entries.filter { it.showInRail }

        /**
         * The route for the rail item at [index], or `null` when the index is out
         * of range.
         *
         * Deliberately nullable rather than clamping. A clamped index turns a
         * programming error into silent navigation to the wrong screen, and the
         * wrong screen is a contract list where the owner expected their fleet.
         *
         * A companion rather than an instance method: an instance method would
         * have to invent a `Destination` receiver to answer a question about the
         * *list*, so `Destination.routeAt(index)` would not compile and every call
         * site would have to create an instance first.
         */
        fun routeAt(index: Int): String? = railEntries.getOrNull(index)?.route

        /**
         * The index of [route] in [railEntries], or `0` when the route is unknown.
         *
         * `0` rather than `-1` because the rail has to highlight *something* and
         * an out-of-range index would leave every item unselected with no way to
         * say why. [START] is the least-wrong answer for an unknown route.
         *
         * A route that is composed but **not** in the rail — [Kontrak] — resolves to
         * `0` too, and that is right: it is not a rail item, so no rail item is
         * highlighted for it.
         */
        fun indexOf(route: String?): Int =
            railEntries.indexOfFirst { it.route == route }.takeIf { it >= 0 } ?: 0
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
internal fun destinationNavItems(): List<KnNavItem> = Destination.railEntries.map { destination ->
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
 * forgotten. It read as a bank building then, on the argument that settings *is* the
 * accounts screen. On the device that argument failed: the rail's last item is not a bank,
 * it is the screen where every kind of setting lives, and a bank building on the rail told
 * the owner the opposite — which is why "Catat Setoran" and the rail's Pengaturan entry
 * read as the same destination while sitting next to each other. So the rail's entry is a
 * gear.
 *
 * The bank glyph is untouched **inside** `PengaturanScreen`, and deliberately: those four
 * uses are bank accounts, and a bank building is the right noun for a bank account. The
 * rail names a screen; the card names a thing. The spec's check that the two never drift
 * back into one another is `git grep -n Landmark app/src/main/kotlin/net/klinik/rental/ui/shell/Destination.kt`
 * returning nothing — so this paragraph says "bank building" rather than naming the glyph.
 */
private val Destination.icon: ImageVector
    get() = when (this) {
        Destination.Mobil -> Lucide.CarFront
        Destination.Penyewa -> Lucide.Users
        Destination.Pengaturan -> Lucide.Settings
        // The alert glyph, and not a warning triangle: the list's job is to *show* the
        // arrears rather than to alarm about them, and a triangle on every visit would make
        // the icon decorative rather than informative.
        Destination.Tunggakan -> Lucide.CircleAlert
        // The dashboard's own glyph — the prototype names it `dashboard`
        // (`shared.js:96`), and this is the Lucide equivalent.
        Destination.Dashboard -> Lucide.LayoutDashboard
        // The wallet, because the deposit screen is where the money is recorded — the
        // prototype names it `wallet` (`shared.js:97`).
        Destination.Setoran -> Lucide.Wallet
        // A bar chart for the period report — the screen's own subject rather than a
        // generic document, so the rail reads as seven different jobs.
        Destination.Laporan -> Lucide.ChartColumn
        // The contract screen has no rail item — it is reached from a renter's detail
        // pane — but it still needs a glyph for the case where it is opened directly.
        Destination.Kontrak -> Lucide.ClipboardList
    }
