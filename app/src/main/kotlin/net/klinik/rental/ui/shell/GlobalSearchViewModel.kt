package net.klinik.rental.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle

/**
 * Owns the global search field — Story 4.6.
 *
 * ## Why this is a ViewModel rather than `remember`ed state in the shell
 *
 * Because the query has to survive a rotation. A field whose text vanished on every
 * configuration change would be a search the owner retypes each time they turn the tablet —
 * the same reason every screen in this app holds its form state in a ViewModel.
 *
 * ## Why it reads the two lists directly rather than a search query
 *
 * Because the search is **local**: there are fourteen renters and eighteen vehicles, both
 * already observed by their own screens' repositories, and a SQL `LIKE` would be a second
 * place the matching rule lives. `RenterRepository.observeRenters` and
 * `VehicleRepository.observeVehicles` are Room's own invalidation flows, so a renter added
 * while the field is open appears in the results without a refresh — the same property every
 * other screen gets for free.
 *
 * ## Why the loading flag is derived rather than stored
 *
 * A `MutableStateFlow<Boolean>` set in the constructor and cleared on the first emission is a
 * second thing to keep in step with the subscriptions, and a flag that disagrees with its
 * lists is exactly what the flag exists to prevent. Each flow carries its own "has emitted"
 * bit, and the pair is combined.
 */
class GlobalSearchViewModel(
    renterRepository: RenterRepository,
    vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /**
     * The renters plus "has the read happened yet", as one flow.
     *
     * The same shape `PenyewaViewModel.rentersWithLoadFlag` uses, for the same reason: one
     * subscription, one query, and a load bit that cannot drift from the list beside it.
     */
    private val renters: kotlinx.coroutines.flow.Flow<Pair<List<Renter>, Boolean>> =
        renterRepository.observeRenters()
            .map { it to true }
            .onStart { emit(emptyList<Renter>() to false) }

    private val vehicles: kotlinx.coroutines.flow.Flow<Pair<List<Vehicle>, Boolean>> =
        vehicleRepository.observeVehicles()
            .map { it to true }
            .onStart { emit(emptyList<Vehicle>() to false) }

    val uiState: StateFlow<GlobalSearchUiState> = combine(
        renters,
        vehicles,
        query,
    ) { (renterRows, rentersLoaded), (vehicleRows, vehiclesLoaded), currentQuery ->
        GlobalSearchUiState.of(
            query = currentQuery,
            renters = renterRows,
            vehicles = vehicleRows,
            isLoading = !(rentersLoaded && vehiclesLoaded),
        )
    }
        // After the projection, so an emission that produces identical results does not
        // recompose the field's overlay for no visible change.
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GlobalSearchUiState(isLoading = true),
        )

    /** Carries a keystroke. The field is the only writer. */
    fun onQueryChange(value: String) {
        query.value = value
    }

    /**
     * Clears the field.
     *
     * Its own method rather than `onQueryChange("")` so the bar's clear button and the
     * selection both reach the same act by name — and so a future "clear on navigate" has one
     * place to call.
     */
    fun clearQuery() {
        query.value = ""
    }
}
