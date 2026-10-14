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
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.ui.screens.kendaraan.KendaraanScreen
import net.klinik.rental.ui.screens.kendaraan.KendaraanViewModel
import net.klinik.rental.ui.screens.kendaraan.VehicleFormSheet
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
     * The fleet screen's ViewModel.
     *
     * Constructed by hand rather than by a DI container because there is no
     * container yet: one Activity, one screen, one repository. `by viewModels()`
     * scopes it to the Activity, so a rotation keeps the query, the filter and
     * any half-typed form input — which is why the form state is held in the
     * ViewModel rather than in a `remember` inside the sheet.
     *
     * The factory is a lambda so `KlinikDatabase.get` is not called until the
     * ViewModel is actually constructed; `by viewModels` may evaluate its
     * factory argument during `onCreate`, and the database touches disk.
     */
    private val viewModel: KendaraanViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createKendaraanViewModel(applicationContext) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The desugaring tripwire — see the class KDoc.
        val today = LocalDate.now()

        setContent {
            KlinikNetTheme(darkTheme = isSystemInDarkTheme()) {
                // The date is passed in rather than read here so this composable
                // stays free of a clock: "today" is the domain `Clock`'s job, and
                // a UI-layer `LocalDate.now()` would be a second source of truth
                // that a test could not pin.
                KlinikNetApp(today = today, viewModel = viewModel)
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

/**
 * The app's single screen: the vehicle fleet (Story 2.2).
 *
 * Stateless by design — everything rendered comes from `KendaraanUiState` and
 * every action goes back through the ViewModel, so a Robolectric test can drive
 * the whole screen with a hand-built state and no database at all.
 *
 * The ViewModel is a parameter rather than read from an enclosing Activity
 * because this is a top-level composable: a private composable *member* would
 * reach it, and coupling the screen to the Activity is exactly the dependency a
 * test then cannot supply.
 */
@Composable
private fun KlinikNetApp(
    today: LocalDate,
    viewModel: KendaraanViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        KendaraanScreen(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onClearQuery = viewModel::clearQuery,
            onAdd = viewModel::openAddForm,
            onEdit = viewModel::openEditForm,
            snackbarMessage = snackbar,
            onSnackbarDismiss = viewModel::consumeMessage,
        )

        // The form is a sheet *over* the list rather than a second destination,
        // so dismissing it restores the owner's place in the fleet.
        state.form?.let { form ->
            VehicleFormSheet(
                state = form,
                onDismissRequest = viewModel::closeForm,
                onChange = viewModel::onFormChange,
                onSave = viewModel::saveForm,
            )
        }
    }
}