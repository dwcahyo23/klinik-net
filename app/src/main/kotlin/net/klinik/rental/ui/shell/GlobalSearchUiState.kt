package net.klinik.rental.ui.shell

import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.asSearchNeedle
import net.klinik.rental.ui.matchesSearch

/**
 * One searchable result, tagged by the kind that owns it.
 *
 * ## Why the kind is a sealed member rather than a label
 *
 * Because a result has to open **the screen that owns it**, and the two screens take
 * different arguments: the renter screen is narrowed by a name, the fleet screen by a plate.
 * Carrying the domain value rather than a pre-rendered string keeps that decision at the
 * call site — where the route lives — instead of forcing this type to know about navigation.
 */
sealed interface SearchResult {
    /** The line the result renders. */
    val label: String

    /** The quiet line beneath it, or `null` when there is nothing worth adding. */
    val detail: String?

    /**
     * The route that owns this hit.
     *
     * A route rather than a kind, because the shell navigates with it and a `when (kind)` in
     * the shell would be a second place that decides which destination a renter belongs to.
     * The value comes from [Destination], so a renamed route cannot leave a result pointing at
     * a screen that no longer exists.
     */
    val route: String

    data class RenterHit(val renter: Renter) : SearchResult {
        override val label: String get() = renter.name
        override val detail: String get() = renter.phone
        override val route: String get() = Destination.Penyewa.route
    }

    data class VehicleHit(val vehicle: Vehicle) : SearchResult {
        override val label: String get() = vehicle.plate
        override val detail: String get() = "${vehicle.brand} ${vehicle.model}"
        override val route: String get() = Destination.Kendaraan.route
    }
}

/**
 * The global search field's state — Story 4.6.
 *
 * ## Why the two kinds are separate lists rather than one sorted list
 *
 * Because the results are **grouped by kind**, and a group is only meaningful if its members
 * stay together. Sorting renters and vehicles into one list would put a car between two
 * people and lose the headings' reason for existing.
 *
 * ## Why an empty result set is a state with a message rather than a blank list
 *
 * The AC asks for an empty state *"yang menjelaskan"*. A blank area under a search box reads
 * as a screen that has not finished loading — and this app has shipped that confusion before,
 * which is why every screen distinguishes "still reading" from "read, and empty". Here the
 * distinction matters differently: nothing is loading, the owner typed something, and the
 * answer is that it is not there. So [isEmpty] is only true once a query exists.
 *
 * @param query what the owner has typed. Blank means the field is idle and nothing renders.
 * @param isLoading true before the first emission of either list — the same flag every other
 *   screen carries, and the same reason: Room's first emission is an empty list.
 */
data class GlobalSearchUiState(
    val query: String = "",
    val renters: List<SearchResult.RenterHit> = emptyList(),
    val vehicles: List<SearchResult.VehicleHit> = emptyList(),
    val isLoading: Boolean = false,
) {
    /** Whether the owner has typed anything. A blank field renders no overlay at all. */
    val isActive: Boolean get() = query.isNotBlank()

    /** Whether both groups came back empty for a query that was actually typed. */
    val isEmpty: Boolean get() = isActive && !isLoading && renters.isEmpty() && vehicles.isEmpty()

    /** Every hit, in group order, so a test can address them without knowing the split. */
    val all: List<SearchResult> get() = renters + vehicles

    companion object {
        /**
         * Applies the shared rule to both lists.
         *
         * The rule lives in `ui/SearchMatch.kt` and is the same one the two screens use, so
         * `siti` finds the same person here as it does on the renters screen — which is the
         * whole reason this function exists rather than a third predicate.
         */
        fun of(
            query: String,
            renters: List<Renter>,
            vehicles: List<Vehicle>,
            isLoading: Boolean = false,
        ): GlobalSearchUiState {
            val needle = query.asSearchNeedle()
            return GlobalSearchUiState(
                query = query,
                renters = if (needle.isEmpty()) {
                    emptyList()
                } else {
                    renters.filter { it.matchesSearch(needle) }.map(SearchResult::RenterHit)
                },
                vehicles = if (needle.isEmpty()) {
                    emptyList()
                } else {
                    vehicles.filter { it.matchesSearch(needle) }.map(SearchResult::VehicleHit)
                },
                isLoading = isLoading,
            )
        }
    }
}
