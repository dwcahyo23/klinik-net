package net.klinik.rental.ui.screens.beranda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import net.klinik.rental.data.repository.ContractArrears
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.screens.kendaraan.VehicleStatus
import net.klinik.rental.ui.strings.RStrings
import java.time.Clock

/**
 * Owns the morning summary's state — Story 4.3.
 *
 * ## Why this screen has no arithmetic of its own
 *
 * Because a dashboard that summarises by querying separately is the second-derivation defect
 * this repo keeps finding, and here it would be the worst instance yet: the owner reads four
 * numbers at once and has no way to see which one disagrees with the list they just opened.
 *
 * So the two figures come from the **existing** reads:
 *
 *  - active arrears is the sum over `observeArrearsForAllContracts`, which already filters
 *    zero amounts — so the population is Story 4.1's list by construction, not by a second
 *    filter that could disagree with it
 *  - the fleet split goes through `VehicleStatus.of`, Story 2.3's derivation, rather than a
 *    count column this app does not have
 *
 * The collection rate is the only figure that is genuinely new, and it is one division over
 * two figures that both already exist.
 *
 * ## Why the clock is handed to the repository
 *
 * Because "today" has to be read where the emission happens. A date captured when this
 * ViewModel was built would freeze the morning screen on yesterday's income until the owner
 * left and came back — the same defect Story 4.1's review found in the arrears read.
 */
class BerandaViewModel(
    private val payments: PaymentRepository,
    vehicleRepository: VehicleRepository,
    private val strings: RStrings,
    private val clock: Clock,
    /**
     * Overrides the arrears source. **For tests only.**
     *
     * A seam because `PaymentRepository` is a `final class` and cannot be stubbed. Defaulted
     * so production never has to know this exists, and named for what it replaces rather than
     * for how it works.
     */
    private val arrearsSourceOverride: (() -> Flow<List<ContractArrears>>)? = null,
) : ViewModel() {

    /**
     * The arrears source, tagged so "not yet read" is a value.
     *
     * A sealed type rather than a nullable list for the reason `TunggakanViewModel` gives: an
     * empty arrears list and a list that has not been read are both empty at runtime, and on
     * this screen the difference is the difference between "nobody owes you anything" and "I
     * have not looked yet".
     */
    private sealed interface Source {
        data object Loading : Source
        data class Loaded(val entries: List<ContractArrears>) : Source
        data object Failed : Source
    }

    /**
     * The arrears source.
     *
     * The failure is a **value** rather than a side-channel write, for the reason
     * `TunggakanViewModel` gives: a `MutableStateFlow` written beside the flow is not one of
     * the `combine`'s inputs, so the write re-renders nothing and the screen would sit on a
     * skeleton forever.
     */
    private val arrearsSource: Flow<Source> =
        (arrearsSourceOverride?.invoke() ?: payments.observeArrearsForAllContracts(clock))
            .map<List<ContractArrears>, Source> { entries -> Source.Loaded(entries) }
            .onStart { emit(Source.Loading) }
            .catch { emit(Source.Failed) }

    /**
     * The fleet, with its open-contract plates beside it.
     *
     * Two flows rather than one because the status is a **derivation** — a vehicle is
     * `Tersewa` because an open contract names its plate — and the derivation belongs in one
     * place. `KendaraanViewModel` does the same two, and the plates come from the repository's
     * own `observeRentedPlates` rather than from a second contract query.
     */
    private val fleet: Flow<Pair<List<net.klinik.rental.domain.model.Vehicle>, Set<String>>> =
        combine(
            vehicleRepository.observeVehicles(),
            vehicleRepository.observeRentedPlates(),
        ) { vehicles, rented -> vehicles to rented }

    /**
     * The screen state.
     *
     * `flatMapLatest` on the arrears source keeps the failure a value rather than an
     * exception, and the nested `combine` keeps every source typed — the same reason
     * `KendaraanViewModel` nests rather than reaching for the `vararg` overload.
     */
    val uiState: StateFlow<BerandaUiState> = combine(
        arrearsSource,
        payments.observeTodaysIncome(clock),
        fleet,
    ) { source, income, (vehicles, rentedPlates) ->
        val split = splitFleet(vehicles, rentedPlates)
        val arrears = source.arrearsTotal()
        BerandaUiState(
            incomeText = formatRupiah(income),
            arrearsText = formatRupiah(arrears),
            fleetText = strings.berandaFleetSplit(split.available, split.rented, split.service),
            rateText = strings.berandaRate(collectionRate(income, arrears)),
            fleet = split,
            incomeRupiah = income,
            arrearsRupiah = arrears,
            // Loading is the **source's** state, not an empty list: a read that has not
            // happened and a read that found nothing are different facts, and a morning
            // screen that says "Rp 0" before it has looked is the app claiming nobody owes
            // anything.
            isLoading = source is Source.Loading,
        )
    }
        // After the projection, so an emission that produces identical figures does not
        // recompose the tiles for no visible change.
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BerandaUiState(isLoading = true),
        )

    /**
     * The arrears total, in whole rupiah.
     *
     * The sum over the entries, which already excludes the contracts that owe nothing —
     * so the population is Story 4.1's list by construction. A failed or unfinished read
     * contributes **zero**, because a total nobody can reconcile is worse than a missing
     * one: the tile shows the loading state instead, and the owner is never shown a figure
     * the app could not stand behind.
     */
    private fun Source.arrearsTotal(): Long = when (this) {
        Source.Loading, is Source.Failed -> 0L
        is Source.Loaded -> entries.sumOf { it.amount }
    }
}

/**
 * The fleet's three counts, through Story 2.3's derivation.
 *
 * The status is **derived** rather than counted from a column, because this app stores no
 * status: a vehicle is `Tersewa` because an open contract names its plate, and `Servis`
 * because the owner set the manual marker. `VehicleStatus.of` is that rule, and it is the
 * same function the fleet list uses — so the morning screen and the fleet screen cannot
 * disagree about which car is off the road.
 */
fun splitFleet(
    vehicles: List<net.klinik.rental.domain.model.Vehicle>,
    rentedPlates: Set<String>,
): FleetSplit {
    var available = 0
    var rented = 0
    var service = 0
    for (vehicle in vehicles) {
        when (
            VehicleStatus.of(
                isServis = vehicle.isServis,
                hasOpenContract = vehicle.plate in rentedPlates,
            )
        ) {
            VehicleStatus.Servis -> service++
            VehicleStatus.Tersewa -> rented++
            VehicleStatus.Tersedia -> available++
        }
    }
    return FleetSplit(available = available, rented = rented, service = service)
}
