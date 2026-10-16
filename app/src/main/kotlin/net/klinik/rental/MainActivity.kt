package net.klinik.rental

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.ui.screens.kendaraan.KendaraanViewModel
import net.klinik.rental.ui.screens.kontrak.KontrakViewModel
import net.klinik.rental.ui.screens.penyewa.PenyewaViewModel
import net.klinik.rental.ui.shell.AppShell
import net.klinik.rental.ui.shell.Screens
import net.klinik.rental.ui.strings.RStrings
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
import java.time.LocalDate

/**
 * The single Activity for the app.
 *
 * `LocalDate.now()` is called here deliberately rather than somewhere in the
 * domain layer: on API 24/25 `java.time` only exists because of core library
 * desugaring. If that config is ever lost, this line is where it crashes —
 * visibly, at startup — instead of failing silently later.
 */
class MainActivity : ComponentActivity() {

    /**
     * The three ViewModels, one per destination.
     *
     * Constructed by hand rather than by a DI container because there is no
     * container yet: one Activity, one shell, three repositories. `by viewModels()`
     * scopes each to the Activity, so a rotation keeps the query, the filter and
     * any half-typed form input — which is why the form state is held in the
     * ViewModel rather than in a `remember` inside the sheet. That is what makes
     * `ROTATION_WITH_HALF_FORM` hold.
     *
     * The factories are lambdas so `KlinikDatabase.get` is not called until a
     * ViewModel is actually constructed; `by viewModels` may evaluate its factory
     * argument during `onCreate`, and the database touches disk.
     *
     * Activity-scoped rather than destination-scoped on purpose: a ViewModel
     * scoped to a nav back-stack entry would be destroyed when the owner
     * navigates away, taking a refusal message with it. `BANNER_SCOPED_TO_RAISER`
     * is the acknowledged limit of that choice — the message survives the
     * destination change but stays attached to its own screen's ViewModel.
     */
    private val kendaraanViewModel: KendaraanViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createKendaraanViewModel(applicationContext) as T
        }
    }

    private val penyewaViewModel: PenyewaViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createPenyewaViewModel(applicationContext) as T
        }
    }

    private val kontrakViewModel: KontrakViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createKontrakViewModel(applicationContext) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The desugaring tripwire — see the class KDoc. Bound to a name rather than
        // called and discarded so the call cannot be "cleaned up" as dead code: the
        // crash it produces on a build that lost core-library desugaring is the
        // whole point of the line.
        @Suppress("UNUSED_VARIABLE")
        val today = LocalDate.now()

        setContent {
            KlinikNetTheme(darkTheme = isSystemInDarkTheme()) {
                KlinikNetApp(
                    kendaraan = kendaraanViewModel,
                    penyewa = penyewaViewModel,
                    kontrak = kontrakViewModel,
                )
            }
        }
    }
}

/**
 * Builds the ViewModel over the database singleton.
 *
 * Top-level rather than a companion so the tests can build the same object graph
 * against an in-memory database without going through an Activity.
 */
internal fun createKendaraanViewModel(context: Context): KendaraanViewModel {
    val database = KlinikDatabase.get(context)
    return KendaraanViewModel(
        repository = VehicleRepository(database.vehicleDao(), database),
        strings = RStrings(context),
    )
}

/** The renters' ViewModel over the same singleton. See [createKendaraanViewModel]. */
internal fun createPenyewaViewModel(context: Context): PenyewaViewModel {
    val database = KlinikDatabase.get(context)
    return PenyewaViewModel(
        // `RenterRepository` takes only its DAO — it has no multi-row write that
        // needs `withTransaction`, so it never needed the database handle. Adding
        // a nullable parameter here to match its siblings would be a parameter
        // nothing uses.
        repository = RenterRepository(database.renterDao()),
        strings = RStrings(context),
    )
}

/**
 * The contracts' ViewModel, which reads three repositories because a contract row
 * carries neither a name nor a plate — the list joins them in Kotlin, and the
 * form's pickers need the whole renter and vehicle set.
 */
internal fun createKontrakViewModel(context: Context): KontrakViewModel {
    val database = KlinikDatabase.get(context)
    val strings = RStrings(context)
    return KontrakViewModel(
        contracts = ContractRepository(
            database.contractDao(),
            // The exception DAO, in `ContractRepository` rather than in a repository
            // of its own — see that class's KDoc. `MainActivity` gains a line
            // because that class grew a second DAO, and gains nothing else: no
            // constructor parameter on the ViewModel, no second `Screens` field.
            database.serviceExceptionDao(),
            database,
        ),
        renters = RenterRepository(database.renterDao()),
        vehicles = VehicleRepository(database.vehicleDao(), database),
        strings = strings,
    )
}

/**
 * The app shell: one graph, a rail in landscape and a bottom nav in portrait.
 *
 * Stateless by design — every screen is driven from its own `UiState` and every
 * action goes back through a ViewModel, so a Robolectric test can drive the whole
 * shell with hand-built state and no database at all.
 *
 * The ViewModels are parameters rather than read from an enclosing Activity
 * because this is a top-level composable: a private composable *member* would
 * reach them, and coupling the shell to the Activity is exactly the dependency a
 * test then cannot supply.
 *
 * There is deliberately no `today` parameter and no clock in this function. It
 * used to take a `LocalDate` that nothing read — "today" belongs to the domain
 * `Clock`, and a UI-layer `LocalDate.now()` would be a second source of truth no
 * test could pin. The desugaring tripwire is the one place `LocalDate.now()` is
 * still called, and it stays in `onCreate` above: if that config is ever lost, it
 * should crash visibly at startup rather than quietly here.
 *
 * This function collects the three screens' flows and hands the results to
 * [AppShell] as one [Screens]. Collection lives here, at the Activity boundary,
 * rather than inside the shell — the shell must not hold a ViewModel, or a test
 * would have to construct three of them to render one nav bar.
 */
@Composable
private fun KlinikNetApp(
    kendaraan: KendaraanViewModel,
    penyewa: PenyewaViewModel,
    kontrak: KontrakViewModel,
) {
    val kendaraanState by kendaraan.uiState.collectAsStateWithLifecycle()
    val kendaraanSnackbar by kendaraan.snackbarMessage.collectAsStateWithLifecycle()
    val penyewaState by penyewa.uiState.collectAsStateWithLifecycle()
    val penyewaSnackbar by penyewa.snackbarMessage.collectAsStateWithLifecycle()
    val kontrakState by kontrak.uiState.collectAsStateWithLifecycle()
    val kontrakSnackbar by kontrak.snackbarMessage.collectAsStateWithLifecycle()
    // Story 2.6's sheet has its **own** banner, because it is a separate window and
    // the screen's is behind it — see `Screens.Kontrak.exceptionErrorMessage`.
    val kontrakExceptionError by kontrak.exceptionErrorMessage.collectAsStateWithLifecycle()
    val renterOptions by kontrak.renterOptions.collectAsStateWithLifecycle()
    val vehicleOptions by kontrak.vehicleOptions.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        AppShell(
            screens = Screens(
                kendaraan = Screens.Kendaraan(
                    state = kendaraanState,
                    snackbarMessage = kendaraanSnackbar,
                    onQueryChange = kendaraan::onQueryChange,
                    onFilterChange = kendaraan::onFilterChange,
                    onClearQuery = kendaraan::clearQuery,
                    onAdd = kendaraan::openAddForm,
                    onEdit = kendaraan::openEditForm,
                    onCloseForm = kendaraan::closeForm,
                    onFormChange = kendaraan::onFormChange,
                    onSave = kendaraan::saveForm,
                    onSnackbarDismiss = kendaraan::consumeMessage,
                    onErrorDismiss = kendaraan::consumeError,
                ),
                penyewa = Screens.Penyewa(
                    state = penyewaState,
                    snackbarMessage = penyewaSnackbar,
                    onQueryChange = penyewa::onQueryChange,
                    onClearQuery = penyewa::clearQuery,
                    onAdd = penyewa::openAddForm,
                    onEdit = penyewa::openEditForm,
                    onCloseForm = penyewa::closeForm,
                    onFormChange = penyewa::onFormChange,
                    onSave = penyewa::saveForm,
                    onSnackbarDismiss = penyewa::consumeMessage,
                    onErrorDismiss = penyewa::consumeError,
                ),
                kontrak = Screens.Kontrak(
                    state = kontrakState,
                    snackbarMessage = kontrakSnackbar,
                    renterOptions = renterOptions,
                    vehicleOptions = vehicleOptions,
                    onOpen = kontrak::openAddForm,
                    onCloseForm = kontrak::closeForm,
                    onFormChange = kontrak::onFormChange,
                    onSave = kontrak::saveForm,
                    // Story 2.6's seven. `createKontrakViewModel` itself is unchanged
                    // by that story: `KontrakViewModel` already held
                    // `ContractRepository`, and extending that repository with the
                    // second DAO is what kept this wiring line the only place the
                    // feature touched. A second repository would have added a
                    // constructor parameter *and* a line here.
                    onOpenExceptions = kontrak::openExceptions,
                    onCloseExceptions = kontrak::closeExceptions,
                    onExceptionChange = kontrak::onExceptionChange,
                    onAddException = kontrak::addException,
                    onRequestRemove = kontrak::requestRemoveException,
                    onConfirmRemove = kontrak::confirmRemoveException,
                    onCancelRemove = kontrak::cancelRemoveException,
                    onSnackbarDismiss = kontrak::consumeMessage,
                    onErrorDismiss = kontrak::consumeError,
                    // The exception sheet's own banner pair, and its own handlers: it
                    // is a different window from the screen's banner, so sharing
                    // either setter would clear one message through the other's
                    // channel while it stayed on screen.
                    exceptionErrorMessage = kontrakExceptionError,
                    onExceptionErrorDismiss = kontrak::consumeExceptionError,
                ),
            ),
        )
    }
}
