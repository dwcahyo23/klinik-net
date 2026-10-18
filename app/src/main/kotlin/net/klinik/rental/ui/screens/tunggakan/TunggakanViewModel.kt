package net.klinik.rental.ui.screens.tunggakan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import net.klinik.rental.data.repository.ContractArrears
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.ui.strings.RStrings
import java.time.Clock
import java.time.LocalDate

/**
 * Owns the arrears list's state — Story 4.1.
 *
 * ## Why the list re-emits on any write, with no refresh anywhere
 *
 * Because the whole point of this screen is that the number is trustworthy. The source is
 * `PaymentRepository.observeArrearsForAllContracts`, which sits on Room's invalidation
 * tracker: a deposit saved, a contract ended or a contract settled all re-emit here and the
 * list moves with no signal of its own. A manual refresh button would be a second mechanism,
 * and the epic context is explicit that a second mechanism can only ever disagree with the
 * first — the owner would press it and wonder whether the number before or after was right.
 *
 * ## Why the arrears and the names are joined here rather than in SQL
 *
 * Because the **figures** come from one call per contract inside the repository, and this class
 * only adds the two *static* names a row displays — a person's name and a car's plate. A stale
 * name cannot make a wrong number look right, so joining them downstream is safe; joining the
 * **figures** downstream would not be, and `ContractArrears` is what stops that.
 *
 * Same argument `KontrakViewModel` gives for its own join, and the same reason this does not
 * add a `@Query` with two JOINs: that would put a second reader of `renter` and `vehicle` next
 * to the existing ones.
 *
 * ## Why `asOf` is read from the [Clock] once per emission
 *
 * Because a billing period depends on "today", and a ViewModel that called
 * `LocalDate.now()` per contract could produce a list whose first row and last row were
 * computed for different days — visible only across midnight, and wrong either way. Pinning it
 * also makes the list testable: a test that passed in October failed in November.
 */
class TunggakanViewModel(
    private val payments: PaymentRepository,
    renterRepository: RenterRepository,
    vehicleRepository: VehicleRepository,
    private val strings: RStrings,
    private val clock: Clock,
) : ViewModel() {

    /** What the arrears source has produced, tagged so "not yet read" is a value. */
    private sealed interface Source {
        /**
         * Not yet read. Carries no entries, so the screen cannot draw a zero row from it.
         *
         * A sealed type rather than a nullable list: "nothing owed" and "not read yet" are
         * both an empty list at runtime and must not be the same branch, and this screen has
         * the strongest reason of any in the app — an empty *arrears* list is the claim
         * "nobody owes you anything".
         */
        data object Loading : Source

        /** Read, possibly to nothing owed. */
        data class Loaded(val entries: List<ContractArrears>) : Source
    }

    /**
     * Which row the landscape detail pane is showing.
     *
     * Held as its own flow rather than folded into the projection, so reading it inside another
     * `combine`'s lambda cannot emit a state carrying the *previous* selection — the same
     * reason `KontrakViewModel` keeps each of its five sheets as a real source.
     */
    private val selectedId = MutableStateFlow<String?>(null)

    /**
     * The last failure that has no row of its own.
     *
     * A banner over unchanged content, per the four-state rule: a calculation that could not
     * be completed must say so rather than draw an empty list, because an empty arrears list
     * reads as "everyone paid" and this app must never tell the owner that with confidence.
     */
    private val errorMessage = MutableStateFlow<String?>(null)

    /**
     * The arrears source, tagged and with its failures turned into a banner.
     *
     * Named rather than inlined so the type is written once: the `combine` below cannot
     * infer a `Flow<Source>` from a chain whose first element is `map { … }` on a sealed
     * hierarchy it has not been shown, and spelling the type here is clearer than three
     * explicit annotations on the lambda's parameter.
     */
    private val arrearsSource: Flow<Source> = payments
        .observeArrearsForAllContracts(asOf = LocalDate.now(clock))
        // `onStart` carries the *loading* value rather than branching the screen on
        // emptiness: Room's first emission is an empty list, so a screen that treated that
        // as "nothing owed" would show its empty state on every single load — the one
        // failure this screen can least afford, since it tells the owner nobody owes them
        // anything.
        .map<List<ContractArrears>, Source> { entries -> Source.Loaded(entries) }
        .onStart { emit(Source.Loading) }
        .catch { throwable ->
            // Reported, never swallowed, and never turned into an empty list: the list stops
            // being trustworthy the moment a failure renders as "no arrears". Re-thrown so
            // structured concurrency still sees it, after the banner has been raised — the
            // same order `KontrakViewModel` uses for its own re-throws.
            errorMessage.value = strings.tunggakanError()
            emit(Source.Loading)
            throw throwable
        }

    val uiState: StateFlow<TunggakanUiState> = combine(
        arrearsSource,
        renterRepository.observeRenters(),
        vehicleRepository.observeVehicles(),
        selectedId,
    ) { source, renters, vehicles, selection ->
        TunggakanUiState(
            rows = when (source) {
                // Nothing rendered while loading: a skeleton, never a zero row.
                Source.Loading -> emptyList()
                is Source.Loaded -> source.entries.toRows(
                    renterNames = renters.associate { it.id to it.name },
                    vehiclePlates = vehicles.associate { it.id to it.plate },
                    strings = strings,
                )
            },
            // Carried through unchanged rather than re-derived, so a row that stops owing
            // leaves the pane showing its prompt instead of silently retargeting to a
            // different renter the moment a payment lands.
            selectedContractId = selection,
            isLoading = source is Source.Loading,
            errorMessage = errorMessage.value,
        )
    }
        // After the projection, so a Room emission that produces an identical list does not
        // recompose every row for no visible change.
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            // `WhileSubscribed(5_000)` keeps the combine alive across a rotation instead of
            // re-running the whole allocation pass on every configuration change — which
            // matters more here than on a list screen, because recomputing arrears for every
            // contract is the expensive part of this screen.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TunggakanUiState(isLoading = true),
        )

    /**
     * Selects one row for the landscape detail pane, or clears it with `null`.
     *
     * Tapping the **same** row again clears the selection rather than being a no-op: in
     * landscape the pane is beside the list rather than behind it, so the owner needs a way
     * to dismiss it without leaving the screen, and a row that cannot be un-tapped is a
     * detail pane that is stuck.
     */
    fun onSelect(contractId: String?) {
        selectedId.value = if (selectedId.value == contractId) null else contractId
    }

    /** Clears the banner. Its own handler, not aliased to another message's setter. */
    fun consumeError() {
        errorMessage.value = null
    }
}