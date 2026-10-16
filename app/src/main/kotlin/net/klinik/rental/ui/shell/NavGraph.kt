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
import net.klinik.rental.ui.screens.kendaraan.KendaraanScreen
import net.klinik.rental.ui.screens.kendaraan.KendaraanUiState
import net.klinik.rental.ui.screens.kendaraan.VehicleFilter
import net.klinik.rental.ui.screens.kendaraan.VehicleFormSheet
import net.klinik.rental.ui.screens.kontrak.ContractFormState
import net.klinik.rental.ui.screens.kontrak.KontrakScreen
import net.klinik.rental.ui.screens.kontrak.KontrakUiState
import net.klinik.rental.ui.screens.penyewa.PenyewaScreen
import net.klinik.rental.ui.screens.penyewa.PenyewaUiState
import net.klinik.rental.ui.screens.penyewa.RenterFormSheet

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
    }
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
) {
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
        renterOptions = wiring.renterOptions,
        vehicleOptions = wiring.vehicleOptions,
    )
}
