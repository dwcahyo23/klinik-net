package net.klinik.rental.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.screens.beranda.BerandaScreen
import net.klinik.rental.ui.screens.kendaraan.KendaraanScreen
import net.klinik.rental.ui.screens.kendaraan.KendaraanUiState
import net.klinik.rental.ui.screens.kendaraan.VehicleFilter
import net.klinik.rental.ui.screens.kendaraan.VehicleFormSheet
import net.klinik.rental.ui.screens.kontrak.ContractFormState
import net.klinik.rental.ui.screens.kontrak.KontrakScreen
import net.klinik.rental.ui.screens.kontrak.KontrakUiState
import net.klinik.rental.ui.screens.laporan.LaporanScreen
import net.klinik.rental.ui.screens.penyewa.PenyewaScreen
import net.klinik.rental.ui.screens.penyewa.PenyewaUiState
import net.klinik.rental.ui.screens.penyewa.RenterFormSheet
import net.klinik.rental.ui.screens.pengaturan.AccountRow
import net.klinik.rental.ui.screens.pengaturan.PengaturanScreen
import net.klinik.rental.ui.screens.pengaturan.PengaturanUiState
import net.klinik.rental.ui.screens.setoran.DepositSheet
import net.klinik.rental.ui.backup.BackupFileReader
import net.klinik.rental.ui.screens.setoran.SetoranScreen
import net.klinik.rental.ui.screens.tunggakan.TunggakanScreen

/**
 * The app's single navigation graph (AR-18).
 *
 * ## Why the routes come from [Destination]
 *
 * The three `composable(...)` calls below are hand-written, but each one's route
 * *is* `Destination.X.route` rather than a string literal, and the rail is built
 * from the same table. A destination therefore cannot exist in the chrome and not
 * in the graph — `NAV_DESTINATION_UNREACHABLE` answered structurally rather than
 * by discipline.
 *
 * ## Where each screen's form sheet is mounted
 *
 * A sheet is a *window over* a destination, not a destination of its own, and
 * dismissing one restores the owner's place in the list rather than navigating
 * away from it. `KendaraanRoute` and `PenyewaRoute` mount theirs here because
 * those two screens have no private sheet host; `KontrakRoute` does not, because
 * `KontrakScreen` already owns its own — see that function's KDoc for why the
 * asymmetry is load-bearing.
 *
 * The sheets read their state from the same `Screens` wiring the screen does, so
 * `ROTATION_WITH_HALF_FORM` holds: the form lives in the ViewModel and this
 * composable is recreated on a configuration change without touching it.
 *
 * ## Why the graph is passed [Screens] rather than the ViewModels
 *
 * The shell must not reach the object graph. Passing already-collected state plus
 * actions means `AppShellTest` can drive the whole shell with hand-built state and
 * no database, the same way every screen test already does.
 */
@Composable
internal fun AppNavGraph(
    navController: NavHostController,
    screens: Screens,
    modifier: Modifier = Modifier,
    startDestination: Destination = Destination.START,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier,
    ) {
        composable(Destination.Kendaraan.route) { KendaraanRoute(screens.kendaraan) }
        composable(Destination.Penyewa.route) { PenyewaRoute(screens.penyewa) }
        composable(Destination.Kontrak.route) { KontrakRoute(screens.kontrak) }
        // The fourth, added by Story 3.1. A `Destination` entry with no `composable` here is
        // a nav item that navigates to nothing — the two are asserted together in
        // `AppShellTest`, because a route composed but absent from the enum is invisible in
        // the nav and one present in the enum but not composed lands on a blank screen.
        composable(Destination.Pengaturan.route) {
            // The read half of Story 5.2's file access. Taken here rather than in the ViewModel
            // because a `ContentResolver` needs a `Context` and the ViewModel deliberately holds
            // none — the same division `BackupFileWriter` documents on the write side.
            val context = LocalContext.current

            // Story 5.1's launcher, declared here because a `rememberLauncherForActivityResult`
            // needs a `ComponentActivity` and this is the one place in the graph that has one.
            // The destination is the **route** rather than the whole app because an export is
            // only reachable from the settings screen, and a launcher declared in `AppShell`
            // would let every screen open a file picker.
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument(MIME_JSON),
            ) { uri ->
                // Null means the owner backed out of the picker. That is not a failure and gets
                // no message — the system already closed itself, and a banner saying "export
                // failed" over a screen that is perfectly fine is the app telling the owner
                // something untrue.
                if (uri != null) screens.pengaturan.onExportGranted(uri)
            }

            // Story 5.2's launcher, beside it. `OpenDocument` rather than `GetContent`: the two
            // differ in what the picker offers, and only `OpenDocument` filters by MIME type —
            // which is what stops the owner choosing a photo of a receipt and getting a parse
            // error instead of a picker that never showed him the photo.
            //
            // The array is a `vararg` of MIME types and a single-element array is deliberate:
            // `arrayOf(MIME_JSON)` offers exactly the JSON files, where the catch-all would
            // offer everything the provider can open.
            val restoreLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                // Null means the owner backed out. Same reasoning as the export: no message,
                // because nothing failed.
                if (uri != null) {
                    val json = BackupFileReader.read(context, uri)
                    screens.pengaturan.onRestoreFileChosen(json)
                }
            }

            PengaturanRoute(
                wiring = screens.pengaturan,
                onExport = { launcher.launch(screens.pengaturan.backupFilename()) },
                onRestore = { restoreLauncher.launch(arrayOf(MIME_JSON)) },
            )
        }
        // The fifth, added by Story 4.1 — and the first with **no** sheet of its own, so
        // this route composes the screen directly rather than wrapping it in a `Box` for
        // sheets like `KendaraanRoute` and `PenyewaRoute` do.
        composable(Destination.Tunggakan.route) { TunggakanRoute(screens.tunggakan) }
        // The sixth, added by Story 4.3 — the morning summary the app now opens on. Like
        // `TunggakanRoute` it composes the screen directly: it has no sheet of its own, and
        // its quick actions are **verbs** the shell performs rather than sheets it mounts.
        composable(Destination.Beranda.route) { BerandaRoute(screens.beranda, navController) }

        // The seventh, added by Story 4.5 — the period report. No sheet of its own and no
        // navigation verb: its only action is picking a period, which is the screen's own
        // state rather than the shell's.
        composable(Destination.Laporan.route) { LaporanRoute(screens.laporan) }

        // The eighth — the deposit screen, two tabs. Its Input tab opens the **existing**
        // deposit sheet, which is why the route forwards `screens.deposito.onOpen` rather
        // than a handler of its own: one sheet, and the doors contain the same actions.
        // The two history verbs forward to the deposit ViewModel's handlers, required
        // like every other verb in this file.
        composable(Destination.Setoran.route) { SetoranRoute(screens.setoran, screens.deposito) }
    }
}

/**
 * The deposit screen's destination.
 *
 * Takes **two** wiring bundles and that is deliberate: the screen's own state comes from
 * [wiring], while its Input tab and its two history verbs act on the deposit sheet's
 * state and ViewModel — the same objects the FAB and the contract sheet use. Splitting
 * them here rather than merging the bundles is what keeps "one deposit sheet" true in
 * the wiring rather than only in a comment.
 */
@Composable
private fun SetoranRoute(wiring: Screens.Setoran, deposito: Screens.Deposito) {
    SetoranScreen(
        state = wiring.state,
        onSelectTab = wiring.onSelectTab,
        onOpenDepositInput = deposito.onOpen,
        onRequestDepositCancel = wiring.onRequestDepositCancel,
        onRequestDepositEdit = wiring.onRequestDepositEdit,
        onDismissError = wiring.onDismissError,
        onHistoryQueryChange = wiring.onHistoryQueryChange,
        onClearHistoryQuery = wiring.onClearHistoryQuery,
        onSelectHistoryFilter = wiring.onSelectHistoryFilter,
    )
}

/**
 * The period-report destination.
 *
 * No `navController` parameter, because nothing here navigates: the report's only control
 * changes which period it reads, and that is `wiring.onSelectPeriod` reaching the ViewModel
 * directly. A route that took a controller it never used would be a hint that this screen owns
 * navigation it does not.
 */
@Composable
private fun LaporanRoute(wiring: Screens.Laporan) {
    LaporanScreen(
        state = wiring.state,
        periodOptions = wiring.periodOptions,
        onSelectPeriod = wiring.onSelectPeriod,
    )
}

/**
 * The morning summary.
 *
 * The route exists to hand the screen its four action handlers, and nothing else: the
 * actions are **navigations or sheet opens the shell owns**, so the screen reports which
 * action was chosen and the shell decides what that means. That is the same rule every
 * other route follows, and the reason a test can drive Beranda with hand-built state and no
 * database.
 *
 * ## Why the three navigations are derived here and not in `Screens`
 *
 * Because the nav controller lives in [AppShell], and a second one — the one
 * `MainActivity` would need to wire "Tambah Mobil" — is a back stack that disagrees with
 * itself. So the route derives them from the controller the shell already holds, and
 * `Screens.Beranda` carries only the state and the deposit sheet's own action.
 *
 * Every destination these name **exists**: the fleet, the renters and the arrears list.
 * The acceptance criterion is explicit that no quick-action tile may open a screen this app
 * does not have, and this is where that promise is kept.
 */
@Composable
private fun BerandaRoute(wiring: Screens.Beranda, navController: NavHostController) {
    BerandaScreen(
        state = wiring.state,
        onDeposit = wiring.onDeposit,
        onAddVehicle = { navController.navigateTo(Destination.Kendaraan.route) },
        onManageRenters = { navController.navigateTo(Destination.Penyewa.route) },
        onSendReminder = { navController.navigateTo(Destination.Tunggakan.route) },
        // Story 5.5's reminder routes to **Pengaturan**, not to a picker: the export lives
        // there, and a second door to the same action is the defect this shell's KDoc warns
        // about. `onBackupReminderTap` is forwarded to the bundle rather than handled here,
        // so a test can assert the tap reached its handler and not merely opened a dialog.
        onBackupReminderTap = wiring.onBackupReminderTap,
        onBackupExport = { navController.navigateTo(Destination.Pengaturan.route) },
        // The prototype's own "Lihat semua →" (`dashboard.html:111`) goes to the deposit
        // screen's history tab — the same destination its rail item names, because the table
        // and that screen are one list read at two depths.
        onSeeAllTransactions = { navController.navigateTo(Destination.Setoran.route) },
    )
}

/**
 * Navigates to [route], the same way the rail does.
 *
 * The same `popUpTo`/`launchSingleTop`/`restoreState` shape [AppShell]'s own navigation
 * uses, so a quick action and a rail item leave the back stack in the same condition. Two
 * navigation shapes would be two back-stack behaviours, and the one that is rare is the one
 * nobody tests.
 */
private fun NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The MIME type the file picker offers for a backup.
 *
 * `application/json` rather than the catch-all that `CreateDocument`'s no-argument constructor
 * uses. The catch-all cannot be written literally in a KDoc block: its first and last characters
 * are a comment terminator, so the text ends the comment early and the compiler reads the rest
 * as top-level source. That is exactly what happened here, and it is worth writing down — the
 * fix is prose, and the next person will otherwise "restore" the literal and get this again.
 *
 * The difference is visible to the owner. With a real MIME type the provider greys out
 * everything that is not a JSON file, which is the difference between picking the right backup
 * and picking a photo of a receipt.
 */
private const val MIME_JSON = "application/json"

/**
 * The arrears destination.
 *
 * The one route with no sheet host: the list's only action is selecting a row for the
 * landscape detail pane, and that pane is drawn *by the screen* beside the list rather than
 * mounted here as a second window — two surfaces over one state is the defect
 * `KontrakRoute`'s KDoc records, and a pane that covered the list it is read against would
 * be the same shape in a smaller size.
 */
@Composable
private fun TunggakanRoute(wiring: Screens.Tunggakan) {
    TunggakanScreen(
        state = wiring.state,
        onSelect = wiring.onSelect,
        onRiskFilterChange = wiring.onRiskFilterChange,
        // Required on the screen and forwarded here as given. The banner's *text* lives on
        // the state, so there is no second message channel to keep in step with the first.
        onErrorDismiss = wiring.onErrorDismiss,
        onRemind = wiring.onRemind,
        onReminderChange = wiring.onReminderChange,
        onReminderSend = wiring.onReminderSend,
        onReminderCopy = wiring.onReminderCopy,
        onReminderDismiss = wiring.onReminderDismiss,
        onReminderLinkOpened = wiring.onReminderLinkOpened,
        onReminderOpenFailed = wiring.onReminderOpenFailed,
        pendingLink = wiring.pendingReminderLink,
    )
}

/**
 * One screen's collected state plus the actions its screen and its form sheet
 * call.
 *
 * ## Why these are bundled rather than passed as twenty parameters
 *
 * The graph carries three screens' worth of wiring and is composed from two
 * places. A flat parameter list of that length is unreadable at the call site and
 * cannot be checked by the compiler — one transposed pair of same-typed lambdas
 * compiles cleanly and drives the wrong ViewModel. A named container makes each
 * screen's wiring a single argument.
 *
 * These are plain classes rather than `data class`es on purpose: the lambdas have
 * no meaningful structural equality, so a generated `equals` would report two
 * identically-wired screens as different and defeat the one thing this type is
 * for.
 *
 * @property snackbarMessage the transient confirmation, collected by the caller.
 *   Held here rather than read from a ViewModel inside a composable so the shell
 *   stays free of the object graph.
 */
internal class Screens(
    val kendaraan: Kendaraan,
    val penyewa: Penyewa,
    val kontrak: Kontrak,
    val pengaturan: Pengaturan,
    /**
     * The deposit sheet's wiring.
     *
     * A destination class with **no route** — the sheet is mounted by `AppShell` over
     * whichever screen is showing, not navigated to. Story 3.2's AC opens the form from
     * "Beranda atau speed dial" and neither exists, so the shell's FAB is the door; that
     * is a quick action, not a place you browse, and a fifth nav tab would make it one.
     *
     * The sheet's *content* state is collected separately from the shell's own state, for
     * the reason `AppShell` takes state rather than ViewModels: a test drives the whole
     * shell with hand-built values and no database.
     */
    val deposito: Deposito,
    /**
     * The arrears list's wiring — Story 4.1.
     *
     * Four parameters, and the smallest bundle in this type, because the list is the only
     * screen so far with **no** sheets: its one action is selecting a row for the landscape
     * detail pane, which the screen draws itself.
     *
     * [onSelect] and [onErrorDismiss] are **required, not defaulted**, for the same reason
     * every other required callback here is: a defaulted lambda produces a list whose rows
     * select nothing, which reads as a broken screen rather than as a missing handler — and
     * this repo has shipped that defect twice.
     */
    val tunggakan: Tunggakan,
    /**
     * The morning summary's wiring — Story 4.3.
     *
     * Two parameters, the smallest bundle in this type, because the summary's three
     * *navigations* are derived by the route from the nav controller the shell already
     * holds, and only the deposit sheet's action belongs here.
     */
    val beranda: Beranda,

    /**
     * The period report's wiring. See [Laporan].
     *
     * No route of its own in this class's own sense — the route lives on [Destination], and
     * `AppNavGraph` composes it from the same `entries` it derives the rail from, so a
     * destination cannot exist in the chrome and not in the graph.
     */
    val laporan: Laporan,

    /**
     * The shell's global search — Story 4.6.
     *
     * A property of the **shell**, not of a destination: the field is mounted above whichever
     * screen shows, so there is no screen that owns it and no route to compose. Putting it in
     * a destination class would have said it belonged to that screen.
     *
     * [onSelect] carries the hit and nothing else. Turning it into a route is the shell's job,
     * because the shell is what holds the nav controller — a wiring class that knew routes
     * would be a second place navigation is decided.
     */
    val search: Search,

    /**
     * The deposit screen's wiring — two tabs, Input and Riwayat.
     *
     * Three callbacks, **required, not defaulted**, for the reason every other bundle here
     * gives: a defaulted lambda produces a tab or a verb that renders and silently does
     * nothing, and the two verbs are Stories 3.4's and 3.5's entry points — features that
     * shipped unreachable once already. The Input tab's own action is **not** here: it
     * opens the existing deposit sheet, so the route forwards `Screens.Deposito.onOpen`
     * directly rather than carrying a second handle to the same door.
     */
    val setoran: Setoran,
) {
    /**
     * The arrears destination's wiring.
     *
     * Three parameters, and the smallest bundle in this type, because the list is the only
     * screen so far with **no** sheets: its one action is selecting a row for the landscape
     * detail pane, which the screen draws itself.
     *
     * There is deliberately **no** `errorMessage` here, though three of its siblings carry
     * one. Those carry it because their banner lives in a *separate window* — a sheet over
     * this screen — and the screen's own banner would be invisible behind it. This screen's
     * banner is its own, so the message rides on [state] and there is no second copy to
     * keep in step: a state whose message disagrees with a separately-passed one is a
     * severity inferred from the wrong place.
     *
     * [onSelect] and [onErrorDismiss] are **required, not defaulted**, for the same reason
     * every other required callback here is: a defaulted lambda produces a list whose rows
     * select nothing, or a banner that cannot be closed, and neither reads as a missing
     * handler from the call site.
     */
    class Tunggakan(
        val state: net.klinik.rental.ui.screens.tunggakan.TunggakanUiState,
        val onSelect: (String) -> Unit,
        val onRiskFilterChange: (net.klinik.rental.ui.screens.tunggakan.RiskFilter) -> Unit,
        val onErrorDismiss: () -> Unit,
        /**
         * Story 4.2's five. Required, not defaulted, for the reason every callback in this
         * class is: a defaulted lambda produces a row whose reminder button renders, is
         * tappable, and opens nothing.
         */
        val onRemind: (String) -> Unit,
        val onReminderChange: (String) -> Unit,
        val onReminderSend: () -> Unit,
        val onReminderCopy: () -> Unit,
        val onReminderDismiss: () -> Unit,
        val onReminderLinkOpened: () -> Unit,
        val onReminderOpenFailed: () -> Unit,
        val pendingReminderLink: String?,
    )
    /**
     * The shell's global search field — Story 4.6.
     *
     * Two callbacks and one state, and **neither callback is defaulted**: a defaulted
     * `onQueryChange` produces a field that takes typing and shows nothing, which reads as a
     * broken control rather than a missing handler.
     *
     * There is deliberately **no** `onSelect` here. Choosing a result is a *navigation*, and
     * the nav controller lives in [AppShell] — so the shell derives it from the result's own
     * route, exactly as `BerandaRoute` derives its three quick actions. A second nav
     * controller, or a route passed in from `MainActivity`, would be a back stack that
     * disagrees with itself.
     */
    class Search(
        val state: GlobalSearchUiState,
        val onQueryChange: (String) -> Unit,
        val onClear: () -> Unit,
    )

    /** The fleet destination's wiring. */
    class Kendaraan(
        val state: KendaraanUiState,
        val snackbarMessage: String?,
        val onQueryChange: (String) -> Unit,
        val onFilterChange: (VehicleFilter) -> Unit,
        val onClearQuery: () -> Unit,
        val onAdd: () -> Unit,
        val onSelect: (String) -> Unit,
        val onEdit: (Vehicle) -> Unit,
        val onCloseForm: () -> Unit,
        val onFormChange: ((net.klinik.rental.ui.screens.kendaraan.VehicleFormState) -> net.klinik.rental.ui.screens.kendaraan.VehicleFormState) -> Unit,
        val onSave: () -> Unit,
        val onSnackbarDismiss: () -> Unit,
        val onErrorDismiss: () -> Unit,
    )

    /** The renters destination's wiring. */
    class Penyewa(
        val state: PenyewaUiState,
        val snackbarMessage: String?,
        val onQueryChange: (String) -> Unit,
        val onClearQuery: () -> Unit,
        val onAdd: () -> Unit,
        val onSelect: (String) -> Unit,
        val onEdit: (Renter) -> Unit,
        val onCloseForm: () -> Unit,
        val onFormChange: ((net.klinik.rental.ui.screens.penyewa.RenterFormState) -> net.klinik.rental.ui.screens.penyewa.RenterFormState) -> Unit,
        val onSave: () -> Unit,
        val onSnackbarDismiss: () -> Unit,
        val onErrorDismiss: () -> Unit,
    )

    /** The contracts destination's wiring. */
    class Kontrak(
        val state: KontrakUiState,
        val snackbarMessage: String?,
        val renterOptions: List<Renter>,
        val vehicleOptions: List<Vehicle>,
        val onOpen: () -> Unit,
        val onCloseForm: () -> Unit,
        val onFormChange: ((ContractFormState) -> ContractFormState) -> Unit,
        val onSave: () -> Unit,
        // The exception sheet's seven. Required for the same reason the three above
        // are — see `KontrakScreen`'s KDoc, where the defaulted lambda this replaced
        // is described. `onRequestRemove` carries the row's id because the id is what
        // the delete is keyed on.
        val onOpenExceptions: (net.klinik.rental.ui.screens.kontrak.ContractRow) -> Unit,
        val onCloseExceptions: () -> Unit,
        val onExceptionChange:
            ((net.klinik.rental.ui.screens.kontrak.ExceptionFormState) -> net.klinik.rental.ui.screens.kontrak.ExceptionFormState) -> Unit,
        val onAddException: () -> Unit,
        val onRequestRemove: (String) -> Unit,
        val onConfirmRemove: () -> Unit,
        val onCancelRemove: () -> Unit,
        val onSnackbarDismiss: () -> Unit,
        val onErrorDismiss: () -> Unit,
        /**
         * The exception sheet's own failure message, separate from
         * `state.errorMessage`.
         *
         * Its own slot because the sheet is a different **window**: the screen's
         * banner sits behind it and would be invisible while the sheet is up, which is
         * the AC's "the failure renders inside the sheet" clause. Not defaulted to
         * `onErrorDismiss` either — one handler wired to both would clear one message
         * through the other's setter and leave it on screen.
         */
        val exceptionErrorMessage: String? = null,
        val onExceptionErrorDismiss: () -> Unit = {},
        // Story 2.7's four, required for the same reason the seven above are: a
        // defaulted lambda here produces a row whose end control renders and does
        // nothing, which is the failure mode the whole required-parameter rule exists
        // to prevent.
        val onOpenEnd: (net.klinik.rental.ui.screens.kontrak.ContractRow) -> Unit,
        val onCloseEnd: () -> Unit,
        val onEndChange:
            ((net.klinik.rental.ui.screens.kontrak.EndContractFormState) -> net.klinik.rental.ui.screens.kontrak.EndContractFormState) -> Unit,
        val onConfirmEnd: () -> Unit,
        /**
         * The end sheet's own failure message and dismiss, as a third pair.
         *
         * Three sheets means three windows means three message slots. This one is not
         * defaulted to `{}`: a no-op default makes the banner unreachable and nothing at
         * the call site says so.
         */
        val endErrorMessage: String? = null,
        val onEndErrorDismiss: () -> Unit,
        /**
         * Story 3.6's FR-9b wiring — the settlement sheet's four plus its own pair.
         *
         * Required for the same reason the three sets above are: a defaulted lambda
         * produces a confirm that renders and silently does nothing, and this action
         * changes what the owner is told they owe.
         */
        val onOpenSettle: (net.klinik.rental.ui.screens.kontrak.ContractRow) -> Unit,
        val onCloseSettle: () -> Unit,
        val onSettleChange:
            ((net.klinik.rental.ui.screens.kontrak.SettleFormState) -> net.klinik.rental.ui.screens.kontrak.SettleFormState) -> Unit,
        val onConfirmSettle: () -> Unit,
        /** The settlement sheet's own banner pair — a fourth window, a fourth slot. */
        val settleErrorMessage: String? = null,
        val onSettleErrorDismiss: () -> Unit,
        // The deposit history's four. Required for the same reason every set above is:
        // these are the verbs that mount Stories 3.4 and 3.5, and both shipped
        // **unreachable** once already. A defaulted lambda here is the same defect again.
        val depositHistory: net.klinik.rental.ui.screens.setoran.DepositHistoryUi? = null,
        val onRequestDepositCancel: (
            contractId: String,
            renterName: String,
            paymentId: String,
        ) -> Unit,
        val onRequestDepositEdit: (paymentId: String) -> Unit,
        val onDepositHistoryDismiss: () -> Unit,
        /**
         * Opens one contract's deposit history.
         *
         * Takes the row rather than ids, because the row carries the renter name the
         * cancellation's impact sentence needs — and a caller that had to look that up
         * would be a second read of a fact the row already holds.
         */
        val onOpenHistory: (net.klinik.rental.ui.screens.kontrak.ContractRow) -> Unit,
    )

    /**
     * The settings destination's wiring.
     *
     * Thirteen callbacks, all required, for the reason the three destinations above give: a
     * defaulted lambda produces a control that renders and silently does nothing, and this
     * screen's only verbs are adding or editing an account and filling in the business
     * identity. The screen also mounts its own form sheets, so nothing is composed here — the
     * same asymmetry `KontrakRoute` documents.
     */
    class Pengaturan(
        val state: PengaturanUiState,
        val snackbarMessage: String?,
        val onAdd: () -> Unit,
        val onEdit: (AccountRow) -> Unit,
        val onCloseForm: () -> Unit,
        val onFormChange:
            ((net.klinik.rental.ui.screens.pengaturan.AccountFormState) -> net.klinik.rental.ui.screens.pengaturan.AccountFormState) -> Unit,
        val onSave: () -> Unit,
        // Story 4.4's identity sheet. Required like the rest, for the same reason — and
        // defaulted, these four would be the "control exists, wired to nothing" defect this
        // repo has shipped twice.
        val onOpenIdentity: () -> Unit,
        val onCloseIdentity: () -> Unit,
        val onIdentityChange:
            ((net.klinik.rental.ui.screens.pengaturan.IdentityFormState) -> net.klinik.rental.ui.screens.pengaturan.IdentityFormState) -> Unit,
        val onSaveIdentity: () -> Unit,
        // The identity sheet's own banner dismissal. A **separate** slot from
        // `onErrorDismiss` for the reason `Screens.Kontrak` keeps three: one setter per
        // window, so clearing a message cannot go through another form's channel.
        val onIdentityErrorDismiss: () -> Unit,
        val onSnackbarDismiss: () -> Unit,
        val onErrorDismiss: () -> Unit,
        /**
         * Story 5.1's export-failure dismissal.
         *
         * A **separate** slot from [onErrorDismiss] for the same reason the identity sheet has
         * one: the export's banner shares the screen's single banner slot, and clearing it must
         * go through the exporter's own channel so a dismissal cannot be mistaken for "the form
         * error is gone too".
         *
         * The launcher's own half — [onExport] — is **not** here: it needs an
         * `ActivityResultLauncher`, so it is a parameter of `PengaturanRoute` rather than a field
         * of this bundle. A bundle that could be built without a launcher would let a caller
         * assemble the whole settings screen and forget the export entirely.
         */
        val onExportErrorDismiss: () -> Unit,
        /**
         * Writes the backup to the `Uri` the file picker granted.
         *
         * A parameter rather than a `() -> Unit` because **the launcher has to be somewhere**,
         * and this is that somewhere's only handle. Hiding the write behind a no-argument
         * callback would mean the picker callback in `AppNavGraph` has nowhere to send the
         * destination — and the two halves of "pick a file, then write it" would have to be
         * wired by whoever builds this bundle, at every call site.
         */
        val onExportGranted: (android.net.Uri) -> Unit,
        /**
         * The filename offered to the picker, `klinik-net-backup-<today>.json`.
         *
         * Read through a function rather than a stored string so the date is the day the
         * **picker opens**, not the day the screen was built — a settings screen left open
         * across midnight would otherwise offer yesterday's name.
         */
        val backupFilename: () -> String,
        /**
         * Story 5.2's restore: the file the owner chose, already read to text.
         *
         * `String?` rather than a `Uri`, because reading the file is the **route's** job — it
         * holds the `ContentResolver` and the granted `Uri`, and `data/` must not see either.
         * `null` means the read failed, which is a different message from "wrong file".
         */
        val onRestoreFileChosen: (String?) -> Unit,
        /** Confirms the preview, replacing the whole database. */
        val onConfirmRestore: () -> Unit,
        /** Closes the preview without writing anything. */
        val onDismissRestore: () -> Unit,
        /** Dismisses a refusal. Its own slot, so it cannot clear the form's message. */
        val onRestoreErrorDismiss: () -> Unit,
        /** Opens Story 5.3's setup flow. */
        val onOpenPinSetup: () -> Unit,
        /** Turns the lock off, removing both secrets. */
        val onDisablePin: () -> Unit,
        /** Changes the auto-lock duration, one of `PinLockRepository.AUTO_LOCK_CHOICES`. */
        val onAutoLockChange: (Int) -> Unit,
        /** One key of the setup grid. */
        val onPinSetupDigit: (Char) -> Unit,
        /** The setup grid's backspace. */
        val onPinSetupDelete: () -> Unit,
        /** Writes the lock — the confirmation the AC requires before the flow finishes. */
        val onConfirmPinCodeWritten: () -> Unit,
        /** Closes the setup flow without writing anything. */
        val onDismissPinSetup: () -> Unit,
        /**
         * Story 5.4: a day was picked. Computes the preview and shows the dialog — **never
         * writes**, which is what makes the AC's *"pratinjau ini wajib"* true rather than
         * aspirational.
         */
        val onCutoffPick: (Int) -> Unit,
        /** Writes the day whose consequences the owner just saw. */
        val onConfirmCutoff: () -> Unit,
        /** Closes the preview without writing anything. */
        val onDismissCutoffPreview: () -> Unit,
        /** Dismisses a refused cutoff change, through its own channel. */
        val onCutoffErrorDismiss: () -> Unit,
    )

    /**
     * The deposit sheet's wiring — no route, mounted by the shell.
     *
     * [state] is the **whole** sheet state including the pickers, because the sheet and
     * the FAB are mounted by two different composables and both read it. Splitting it
     * would mean the FAB collecting what it does not draw.
     */
    class Deposito(
        val state: net.klinik.rental.ui.screens.setoran.SetoranUiState,
        val onOpen: () -> Unit,
        val onClose: () -> Unit,
        val onFormChange:
            ((net.klinik.rental.ui.screens.setoran.DepositFormState) -> net.klinik.rental.ui.screens.setoran.DepositFormState) -> Unit,
        /**
         * The pad's Save, which **computes the preview** rather than writing.
         *
         * Named separately from [onConfirmPreview] because the two are different acts, and
         * the numpad reaching the write directly is the defect UX-DR10 forbids: a preview
         * that could be skipped is a confirmation, not a preview.
         */
        val onSave: (Long) -> Unit,
        /**
         * The dialog's confirm and dismiss — required, not defaulted.
         *
         * A default would be a confirm that silently does nothing: the owner taps
         * "Ya, simpan" and watches nothing happen with a deposit they believe they took.
         */
        val onConfirmPreview: () -> Unit,
        val onDismissPreview: () -> Unit,
        val onSnackbarDismiss: () -> Unit,
        val onErrorDismiss: () -> Unit,
    )

    /**
     * The morning summary's wiring — Story 4.3.
     *
     * Two members, both required, for the reason the destinations above give: a defaulted
     * lambda produces a tile that renders and silently does nothing, and this repo has
     * shipped that defect twice. A quick-action tile is the *only* door to its action from
     * this screen, so a tile wired to `{}` is a door that opens onto nothing.
     *
     * Only the **deposit sheet**'s action is here, because that sheet is mounted by the
     * shell over every destination. The other three actions are *navigations*, and the
     * shell holds the nav controller — so the route derives them from it, rather than this
     * class carrying a second copy of the graph.
     */
    class Beranda(
        val state: net.klinik.rental.ui.screens.beranda.BerandaUiState,
        val onDeposit: () -> Unit,
        /**
         * Story 5.5: the backup reminder was tapped.
         *
         * Required, not defaulted, for the reason every member here is — and it is carried
         * through the route rather than handled by it, so a test can assert the tap reached
         * **something** rather than only that a dialog appeared. The dialog is the screen's own
         * window; this is the fact that the owner asked, which is worth being observable.
         */
        val onBackupReminderTap: () -> Unit,
    )

    /**
     * The period report's wiring — Story 4.5.
     *
     * `strings` is carried rather than constructed here: this package holds no `Context`, and a
     * `RStrings` built at the call site would be a second object per screen resolving the same
     * resources. The Activity builds one and hands it over, as it does for every other screen.
     *
     * `onSelectPeriod` is required, not defaulted — a picker wired to `{}` renders six months
     * that lead nowhere, which is the same control-that-reaches-nothing shape this class has
     * refused to ship twice.
     */
    class Laporan(
        val state: net.klinik.rental.ui.screens.laporan.LaporanUiState,
        val periodOptions: List<String>,
        val onSelectPeriod: (Int) -> Unit,
    )

    /**
     * The deposit screen's wiring — two tabs.
     *
     * The screen's state is its own type ([net.klinik.rental.ui.screens.setoran.SetoranScreenState]),
     * deliberately separate from the deposit **sheet's** state: the sheet is a window the
     * shell mounts over any screen, and this is a destination with its own lifetime.
     *
     * All three callbacks are required, not defaulted — see the field KDoc on the class
     * above.
     */
    class Setoran(
        val state: net.klinik.rental.ui.screens.setoran.SetoranScreenState,
        val onSelectTab: (net.klinik.rental.ui.screens.setoran.SetoranTab) -> Unit,
        val onRequestDepositCancel: (
            contractId: String,
            renterName: String,
            paymentId: String,
        ) -> Unit,
        val onRequestDepositEdit: (paymentId: String) -> Unit,
        val onDismissError: () -> Unit,
        /**
         * The history search box's three handlers.
         *
         * Required, not defaulted — the same rule as every other callback here. A search box
         * wired to `{}` renders and filters nothing, which is the "control wired to nothing"
         * defect this repo has shipped three times; and a filter chip that cannot be un-selected
         * is a one-way door.
         */
        val onHistoryQueryChange: (String) -> Unit,
        val onClearHistoryQuery: () -> Unit,
        val onSelectHistoryFilter: (net.klinik.rental.ui.screens.setoran.HistoryFilter?) -> Unit,
    )
}

/** The fleet destination: the screen, and the sheet it owns. */
@Composable
private fun KendaraanRoute(wiring: Screens.Kendaraan) {
    Box(Modifier.fillMaxSize()) {
        KendaraanScreen(
            state = wiring.state,
            onQueryChange = wiring.onQueryChange,
            onFilterChange = wiring.onFilterChange,
            onClearQuery = wiring.onClearQuery,
            onAdd = wiring.onAdd,
            onSelect = wiring.onSelect,
            onEdit = wiring.onEdit,
            snackbarMessage = wiring.snackbarMessage,
            onSnackbarDismiss = wiring.onSnackbarDismiss,
            // Deliberately a separate handler rather than an alias of
            // `onSnackbarDismiss`: the two dismiss different messages, and the
            // aliasing is how a refusal ends up cleared through the
            // confirmation's setter while staying on screen forever.
            onErrorDismiss = wiring.onErrorDismiss,
        )

        wiring.state.form?.let { form ->
            VehicleFormSheet(
                state = form,
                onDismissRequest = wiring.onCloseForm,
                onChange = wiring.onFormChange,
                onSave = wiring.onSave,
            )
        }
    }
}

/** The renters destination: the screen, and the sheet it owns. */
@Composable
private fun PenyewaRoute(wiring: Screens.Penyewa) {
    Box(Modifier.fillMaxSize()) {
        PenyewaScreen(
            state = wiring.state,
            onQueryChange = wiring.onQueryChange,
            onClearQuery = wiring.onClearQuery,
            onAdd = wiring.onAdd,
            onSelect = wiring.onSelect,
            onEdit = wiring.onEdit,
            snackbarMessage = wiring.snackbarMessage,
            onSnackbarDismiss = wiring.onSnackbarDismiss,
            onErrorDismiss = wiring.onErrorDismiss,
        )

        wiring.state.form?.let { form ->
            RenterFormSheet(
                state = form,
                onDismissRequest = wiring.onCloseForm,
                onChange = wiring.onFormChange,
                onSave = wiring.onSave,
            )
        }
    }
}

/**
 * The contracts destination.
 *
 * ## Why this route mounts no sheet at all
 *
 * `KontrakScreen` is the one screen that owns its sheets — `ContractFormSheet` since
 * Story 2.5 and `ExceptionFormSheet` since 2.6 — and it is why that screen takes
 * `onCloseForm`/`onFormChange`/`onSave` and the exception sheet's seven callbacks as
 * *required* parameters. Mounting either sheet here as well would compose two
 * `ModalBottomSheet` windows over the same state and render `errorMessage` twice.
 *
 * Two windows over one state was the only `high` Story 2.8's review found, and it is
 * the reason the exception surface is **one** sheet carrying three blocks rather than
 * a list sheet with a nested add sheet.
 *
 * `KendaraanRoute` and `PenyewaRoute` are the opposite case and keep their sheet
 * mounts: those two screens have no private sheet host, so the graph is the only
 * place that can draw one. The asymmetry is a property of the screens, not an
 * oversight here.
 */
@Composable
private fun KontrakRoute(wiring: Screens.Kontrak) {
    KontrakScreen(
        state = wiring.state,
        onOpen = wiring.onOpen,
        onCloseForm = wiring.onCloseForm,
        onFormChange = wiring.onFormChange,
        onSave = wiring.onSave,
        onOpenExceptions = wiring.onOpenExceptions,
        onCloseExceptions = wiring.onCloseExceptions,
        onExceptionChange = wiring.onExceptionChange,
        onAddException = wiring.onAddException,
        onRequestRemove = wiring.onRequestRemove,
        onConfirmRemove = wiring.onConfirmRemove,
        onCancelRemove = wiring.onCancelRemove,
        snackbarMessage = wiring.snackbarMessage,
        onSnackbarDismiss = wiring.onSnackbarDismiss,
        onErrorDismiss = wiring.onErrorDismiss,
        // The sheet's own banner. Forwarded as its own pair for the reason
        // `Screens.Kontrak.exceptionErrorMessage` gives — and required to be passed
        // rather than omitted, because an omitted pair is a sheet whose failure
        // banner never renders and never clears.
        exceptionErrorMessage = wiring.exceptionErrorMessage,
        onExceptionErrorDismiss = wiring.onExceptionErrorDismiss,
        // And Story 2.7's five, forwarded for the same reason — and this route still
        // mounts **no sheet of its own**. The end sheet is drawn by `KontrakScreen`
        // beside its two siblings, exactly as they are.
        onOpenEnd = wiring.onOpenEnd,
        onCloseEnd = wiring.onCloseEnd,
        onEndChange = wiring.onEndChange,
        onConfirmEnd = wiring.onConfirmEnd,
        endErrorMessage = wiring.endErrorMessage,
        onEndErrorDismiss = wiring.onEndErrorDismiss,
        // Story 3.6's six, forwarded for the same reason — and this route still mounts
        // **no sheet of its own**. The settlement sheet is drawn by `KontrakScreen`
        // beside its three siblings, exactly as they are.
        onOpenSettle = wiring.onOpenSettle,
        onCloseSettle = wiring.onCloseSettle,
        onSettleChange = wiring.onSettleChange,
        onConfirmSettle = wiring.onConfirmSettle,
        settleErrorMessage = wiring.settleErrorMessage,
        onSettleErrorDismiss = wiring.onSettleErrorDismiss,
        // And the deposit history's four, forwarded for the same reason. Still **no** sheet
        // of its own from this route: the history is a block on `KontrakScreen`, not a
        // fifth window over its state.
        depositHistory = wiring.depositHistory,
        onOpenHistory = wiring.onOpenHistory,
        onRequestDepositCancel = wiring.onRequestDepositCancel,
        onRequestDepositEdit = wiring.onRequestDepositEdit,
        onDepositHistoryDismiss = wiring.onDepositHistoryDismiss,
        renterOptions = wiring.renterOptions,
        vehicleOptions = wiring.vehicleOptions,
    )
}

/**
 * The settings destination.
 *
 * Like `KontrakRoute`, this mounts **no sheet**: `PengaturanScreen` draws its own form sheet,
 * which is the shape this screen's form has. Unlike `KendaraanRoute` and `PenyewaRoute`, which
 * keep their mounts here because their screens have no private sheet host — the asymmetry is a
 * property of the screens, not an oversight.
 */
@Composable
private fun PengaturanRoute(
    wiring: Screens.Pengaturan,
    onExport: () -> Unit,
    onRestore: () -> Unit,
) {
    PengaturanScreen(
        state = wiring.state,
        onAdd = wiring.onAdd,
        onEdit = wiring.onEdit,
        onCloseForm = wiring.onCloseForm,
        onFormChange = wiring.onFormChange,
        onSave = wiring.onSave,
        // Story 4.4's four, forwarded for the same reason the account sheet's are: the screen
        // owns its sheet, so a callback this route dropped would be a control that renders and
        // reaches nothing.
        onOpenIdentity = wiring.onOpenIdentity,
        onCloseIdentity = wiring.onCloseIdentity,
        onIdentityChange = wiring.onIdentityChange,
        onSaveIdentity = wiring.onSaveIdentity,
        onIdentityErrorDismiss = wiring.onIdentityErrorDismiss,
        snackbarMessage = wiring.snackbarMessage,
        onSnackbarDismiss = wiring.onSnackbarDismiss,
        onErrorDismiss = wiring.onErrorDismiss,
        // Story 5.1's, forwarded the same way — except the export's *launcher*, which the route
        // below owns because a launcher needs an Activity and this function is the one place
        // in the graph that already has one.
        onExport = onExport,
        onExportErrorDismiss = wiring.onExportErrorDismiss,
        // Story 5.2's. The two **verbs** forward to the ViewModel; the picker is the route's,
        // for the same reason the export's is.
        onRestore = onRestore,
        onConfirmRestore = wiring.onConfirmRestore,
        onDismissRestore = wiring.onDismissRestore,
        onRestoreErrorDismiss = wiring.onRestoreErrorDismiss,
        // Story 5.3's four. The verbs forward to the ViewModel; the setup dialog is the screen's
        // own window, so nothing is mounted here — the same asymmetry the account sheet documents.
        onOpenPinSetup = wiring.onOpenPinSetup,
        onDisablePin = wiring.onDisablePin,
        onAutoLockChange = wiring.onAutoLockChange,
        onPinSetupDigit = wiring.onPinSetupDigit,
        onPinSetupDelete = wiring.onPinSetupDelete,
        onConfirmPinCodeWritten = wiring.onConfirmPinCodeWritten,
        onDismissPinSetup = wiring.onDismissPinSetup,
        // Story 5.4's. The pick never writes: it computes a preview and shows the dialog, so
        // the only action that changes the setting is the dialog's confirm.
        onCutoffPick = wiring.onCutoffPick,
        onConfirmCutoff = wiring.onConfirmCutoff,
        onDismissCutoffPreview = wiring.onDismissCutoffPreview,
        onCutoffErrorDismiss = wiring.onCutoffErrorDismiss,
    )
}

/**
 * The deposit sheet — mounted here, over whichever destination is showing.
 *
 * ## Why this is a function and not three lines in `AppShell`
 *
 * It is the same wiring `KendaraanRoute` and `PenyewaRoute` do, and giving it a name means
 * the shell's two branches (rail / bottom nav) stay identical. A sheet composed inside
 * the `if (isLandscape)` branch and again in the `else` is two places to forget.
 *
 * ## Why it has no route
 *
 * A deposit is a quick action, not a place the owner browses. Adding a fifth nav tab would
 * make it one, and the epic context is explicit that the bottom nav and the rail are for
 * destinations while quick actions live behind the FAB — and that Beranda's grid will be
 * the *only* door there once it exists.
 */
@Composable
internal fun DepositHost(wiring: Screens.Deposito) {
    DepositSheet(
        state = wiring.state,
        onClose = wiring.onClose,
        onChange = wiring.onFormChange,
        onSave = wiring.onSave,
        onConfirmPreview = wiring.onConfirmPreview,
        onDismissPreview = wiring.onDismissPreview,
    )
}
