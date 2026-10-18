package net.klinik.rental.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        composable(Destination.Pengaturan.route) { PengaturanRoute(screens.pengaturan) }
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
    }
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
    /** The fleet destination's wiring. */
    class Kendaraan(
        val state: KendaraanUiState,
        val snackbarMessage: String?,
        val onQueryChange: (String) -> Unit,
        val onFilterChange: (VehicleFilter) -> Unit,
        val onClearQuery: () -> Unit,
        val onAdd: () -> Unit,
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
private fun PengaturanRoute(wiring: Screens.Pengaturan) {
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
