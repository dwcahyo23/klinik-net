package net.klinik.rental.ui.screens.mobil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.domain.parse.parseRupiahInput
import net.klinik.rental.domain.validation.DuplicatePlateException
import net.klinik.rental.domain.validation.InvalidAmountException
import net.klinik.rental.domain.validation.VehicleHasActiveContractException
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.strings.RStrings
import java.time.Clock

/**
 * Owns the fleet screen's state.
 *
 * ## No manual invalidation signal
 *
 * The list comes from `repository.observeVehicles()`, which is Room's own
 * invalidation flow. A vehicle saved by the form re-emits there and the screen
 * updates — no refresh button, no `MutableStateFlow<Long>` of "something
 * changed". The epic context is explicit that a second mechanism can only ever
 * disagree with Room's, and the AC for "visible without a manual refresh" is
 * satisfied by *not having* a refresh path at all.
 *
 * ## The three flows that are combined
 *
 * Room's vehicles, the search query, the status filter, plus the form state. All
 * are combined into one [MobilUiState] and `distinctUntilChanged()`d, so a
 * query change that does not alter the result set does not recompose the list.
 */
class MobilViewModel(
    private val repository: VehicleRepository,
    private val strings: RStrings,
    /**
     * The contracts behind the rented plates.
     *
     * Optional so the two existing tests — which build this ViewModel against a vehicle
     * repository alone — keep compiling, and so a caller that only wants the list can omit it.
     * When it is absent the detail pane simply never shows a contract, which is the correct
     * rendering for a fleet with nothing rented.
     *
     * It exists because `observeRentedPlates()` returns a `Set<String>` of plates: enough to
     * derive a status, and nothing about *who* is renting. The pane needs the renter's name and
     * number, and a second reader of `contract` in the ViewModel is exactly the pattern
     * `KontrakViewModel` already uses for its own joins.
     */
    private val contracts: ContractRepository? = null,
    /**
     * The renters, for the detail pane's name and phone.
     *
     * Optional for the same reason [contracts] is. Joined in Kotlin rather than by a `@Query`
     * with a JOIN, which would put a second reader of `renter` beside `observeRenters()` — the
     * pattern `KontrakViewModel` and `TunggakanViewModel` already establish.
     */
    private val renters: RenterRepository? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(VehicleFilter.Semua)
    private val form = MutableStateFlow<VehicleFormState?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    /**
     * Which vehicle the detail pane is showing.
     *
     * `null` means "nothing chosen yet", and [MobilUiState.selectedRow] falls back to the
     * first visible row — the prototype's own rule (`cars.html:202`).
     */
    private val selectedVehicleId = MutableStateFlow<String?>(null)

    /**
     * "Has the fleet read not happened yet", as a flow rather than a flag.
     *
     * A `MutableStateFlow<Boolean>` would be a second thing to keep in step with
     * the subscription — set true in the constructor, cleared on the first
     * emission — and a screen whose flag disagrees with its list is precisely the
     * failure the flag exists to prevent. `distinctUntilChanged()` makes the
     * second and every later emission a no-op, so the flag settles once.
     *
     * Wrapping the *same* [VehicleRepository.observeVehicles] call the list reads
     * means one subscription and one query, not two.
     */
    private fun vehiclesWithLoadFlag(): Flow<Pair<List<Vehicle>, Boolean>> =
        repository.observeVehicles()
            .map { it to true }
            .onStart { emit(emptyList<Vehicle>() to false) }

    /** The screen state.
     *
     * `flatMapLatest` on the rented-plate set keeps status derivation in one
     * place: the repository exposes which plates have an open contract, and the
     * combine below turns that plus `isServis` into a [VehicleStatus]. Nothing
     * here reads a stored status column, because there is none.
     *
     * The two `combine` calls are nesting, not decoration: the typed overloads
     * stop at five flows, and this screen has six sources. Nesting keeps every
     * one typed rather than reaching for the `vararg` overload, which would hand
     * the lambda an `Array<Any?>` and push the inference problem into the body.
     */
    val uiState: StateFlow<MobilUiState> = combine(
        combine(
            vehiclesWithLoadFlag(),
            repository.observeRentedPlates(),
            query,
            filter,
            contracts?.observeContracts() ?: flowOf(emptyList()),
        ) { (vehicles, hasEmitted), rentedPlates, currentQuery, currentFilter, openContracts ->
            val rows = vehicles.map { vehicle ->
                VehicleRow(
                    vehicle = vehicle,
                    status = VehicleStatus.of(
                        isServis = vehicle.isServis,
                        hasOpenContract = vehicle.plate in rentedPlates,
                    ),
                    // `formatRupiah` here and nowhere else: the only money-to-text
                    // path, so a rate cannot look different on two screens.
                    rateText = formatRupiah(vehicle.dailyRate),
                )
            }
            FleetSnapshot(
                rows = rows,
                query = currentQuery,
                filter = currentFilter,
                isLoading = !hasEmitted,
                contracts = openContracts,
                startTextOf = { contract -> strings.formatExceptionDate(contract.startDate) },
            )
        },
        renters?.observeRenters() ?: flowOf(emptyList()),
        form,
        errorMessage,
        selectedVehicleId,
    ) { fleet, renterList, currentForm, error, selection ->
        val rentersById = renterList.associateBy { it.id }
        val selected = fleet.rows.firstOrNull { it.vehicle.id == selection }
            ?: fleet.rows.firstOrNull()

        MobilUiState(
            rows = fleet.rows,
            query = fleet.query,
            filter = fleet.filter,
            // Counted over the query-filtered set, so a chip's number and the
            // list below it can never disagree.
            counts = countByStatus(matchesQuery(fleet.rows, fleet.query)),
            form = currentForm,
            errorMessage = error,
            isLoading = fleet.isLoading,
            selectedVehicleId = selected?.vehicle?.id,
            summary = fleet.summary(),
            selectedContract = selected?.vehicle?.let { vehicle ->
                fleet.contractFactsFor(vehicle, rentersById)
            },
        )
    }
        // Distinct *after* the projection, so a Room emission that produces an
        // identical list does not push a new state downstream and recompose
        // every row for no visible change.
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            // `WhileSubscribed(5_000)` keeps the combine alive across a rotation
            // instead of re-running Room's query on every configuration change.
            started = SharingStarted.WhileSubscribed(5_000),
            // `isLoading = true`, unlike the field's default. Before the flow has
            // emitted there is genuinely nothing to show, and the default's `false`
            // would render the empty state — "your fleet is empty, add a car" —
            // for the frame between here and the first emission.
            initialValue = MobilUiState(isLoading = true),
        )

    /**
     * What the data half of the combine produces.
     *
     * A named type rather than a `Triple`, because `Triple` would make the
     * second and third reads `component2`/`component3` at the call site — and a
     * silently swapped query and filter produces a screen that looks fine and
     * filters wrongly.
     */
    private data class FleetSnapshot(
        val rows: List<VehicleRow>,
        val query: String,
        val filter: VehicleFilter,
        val isLoading: Boolean,
        val contracts: List<Contract>,
        /** Carried so this projection can format a date without reaching into the outer class. */
        val startTextOf: (Contract) -> String,
    ) {
        /** `cars.html:147-167` — four figures over the same rows the list draws. */
        fun summary(): FleetSummary {
            val total = rows.size
            val rented = rows.count { it.status == VehicleStatus.Tersewa }
            val available = rows.count { it.status == VehicleStatus.Tersedia }
            val nonServicing = rows.filter { it.status != VehicleStatus.Servis }
            return FleetSummary(
                total = total,
                rented = rented,
                available = available,
                // A fleet with no cars has no utilisation; `0%` is a figure, not a division
                // by zero dressed up.
                utilisationText = if (total == 0) "0%" else "${rented * 100 / total}%",
                potentialText = formatRupiah(nonServicing.sumOf { it.vehicle.dailyRate }),
                averageText = formatRupiah(
                    if (total == 0) 0 else rows.sumOf { it.vehicle.dailyRate } / total,
                ),
            )
        }

        /** The open contract behind [vehicle], or `null` when it is not rented. */
        fun contractFactsFor(
            vehicle: Vehicle,
            rentersById: Map<String, Renter>,
        ): VehicleContractFacts? {
            val contract = contracts.firstOrNull {
                it.vehicleId == vehicle.id && it.endDate == null
            } ?: return null
            val renter = rentersById[contract.renterId] ?: return null
            return VehicleContractFacts(
                renterName = renter.name,
                renterPhone = renter.phone,
                // The same formatter the contract screen's exception rows use, so a start
                // date cannot be spelled two ways in one app.
                startText = startTextOf(contract),
                restDay = contract.restDay,
                rateText = formatRupiah(contract.dailyRate),
            )
        }
    }

    /** The interaction half: the open form, the last refusal, and the pane's selection. */
    private data class FormSnapshot(
        val form: VehicleFormState?,
        val error: String?,
        val selection: String?,
    )

    /** A transient confirmation. Success does not change screen state. */
    val snackbarMessage: StateFlow<String?> = message

    // ------------------------------------------------------------------ input

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: VehicleFilter) {
        // A filter, not a navigation: no route change, so the other chips stay
        // reachable and the owner's place in the list survives.
        filter.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    /**
     * Selects the vehicle whose detail the pane shows.
     *
     * Tapping the same row again **keeps** it selected rather than clearing, unlike
     * `TunggakanViewModel.onSelect`. The difference is the pane: there, the owner needs a way to
     * dismiss a pane beside the list; here the pane is the list's other half and an empty one
     * beside a full list is not a state `cars.html` has.
     */
    fun select(vehicleId: String) {
        selectedVehicleId.value = vehicleId
    }

    /** Opens the edit sheet for whatever the pane is showing — the prototype's pane button. */
    fun editSelected() {
        uiState.value.selectedRow?.let { openEditForm(it.vehicle) }
    }

    // ------------------------------------------------------------------- form

    fun openAddForm() {
        errorMessage.value = null
        form.value = VehicleFormState()
    }

    /**
     * Opens the form on an existing vehicle.
     *
     * The rate arrives as `rateDigits` rather than a formatted string, because
     * `KnNumPad` re-syncs from its `value` and a grouped `Rp 250.000` would be
     * parsed as nonsense by its own normaliser. The stored `Long` goes back to
     * digits, and the *only* way out is `parseRupiahInput` again — so an edit
     * cannot take a different path than a create.
     */
    fun openEditForm(vehicle: Vehicle) {
        errorMessage.value = null
        form.value = VehicleFormState(
            id = vehicle.id,
            plate = vehicle.plate,
            brand = vehicle.brand,
            model = vehicle.model,
            year = vehicle.year.toString(),
            color = vehicle.color.orEmpty(),
            rateDigits = vehicle.dailyRate.toString(),
            notes = vehicle.notes.orEmpty(),
            isServis = vehicle.isServis,
        )
    }

    fun closeForm() {
        form.value = null
    }

    fun onFormChange(transform: (VehicleFormState) -> VehicleFormState) {
        form.value = form.value?.let(transform)
    }

    /**
     * Saves the form, translating each typed exception into its Indonesian copy.
     *
     * ## Where the money rule is enforced, three times
     *
     * 1. Here, via [parseRupiahInput] — so the owner sees the message while the
     *    sheet is still open
     * 2. In the repository, via `requirePositiveRate` — so a second screen
     *    cannot pass a check this one has
     * 3. In `Vehicle`'s constructor, via `InvalidAmountException.requireValid`
     *
     * NFR-6's "two screens can both pass the same UI check" is precisely why
     * steps 2 and 3 exist: step 1 is a courtesy, not the rule.
     *
     * On success the form closes and the list updates on its own — Room re-emits
     * and `combine` rebuilds the state. There is no manual "now refresh".
     */
    fun saveForm() {
        val current = form.value ?: return
        val rate = parseRupiahInput(current.rateDigits)

        if (rate == null) {
            form.value = current.copy(
                rateError = strings.kendaraanErrorRateZero(),
            )
            return
        }

        val year = current.year.trim().toIntOrNull()
        if (year == null) {
            form.value = current.copy(
                rateError = null,
                plateError = null,
            )
            errorMessage.value = strings.kendaraanErrorBlankYear()
            return
        }

        val vehicle = try {
            Vehicle(
                // A blank plate normalises to null in `VehicleRepository`, which
                // throws `IllegalArgumentException`. Checked here so the message
                // lands on the field rather than as a generic refusal.
                id = current.id ?: repository.newVehicleId(),
                plate = current.plate.trim(),
                brand = current.brand.trim(),
                model = current.model.trim(),
                year = year,
                color = current.color.trim().ifBlank { null },
                dailyRate = rate,
                notes = current.notes.trim().ifBlank { null },
                isServis = current.isServis,
            )
        } catch (e: InvalidAmountException) {
            form.value = current.copy(rateError = strings.kendaraanErrorRateZero())
            return
        } catch (e: IllegalArgumentException) {
            form.value = current.copy(plateError = strings.kendaraanErrorBlankPlate())
            return
        }

        viewModelScope.launch {
            try {
                repository.save(vehicle)
                form.value = null
                errorMessage.value = null
                message.value = strings.kendaraanSaved(vehicle.plate)
            } catch (e: DuplicatePlateException) {
                // The plate is named, so the owner knows which car to look at.
                form.value = current.copy(plateError = strings.kendaraanErrorDuplicatePlate(e.plate))
            } catch (e: InvalidAmountException) {
                form.value = current.copy(rateError = strings.kendaraanErrorRateZero())
            } catch (e: VehicleHasActiveContractException) {
                errorMessage.value = strings.kendaraanErrorHasContract(e.plate)
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun consumeError() {
        errorMessage.value = null
    }

    /**
     * Deletes a vehicle — the guarded path, with no UI attached.
     *
     * The screen deliberately exposes no delete affordance this story: the
     * mandatory deletion reason has nowhere to go, and the prototype's dialog
     * collected one and dropped it. The *guard* ships and is tested; the
     * *button* waits for the story that owns the audit sink. See the story's
     * design note.
     */
    suspend fun deleteVehicle(vehicleId: String): Result<Unit> =
        try {
            repository.delete(vehicleId)
            Result.success(Unit)
        } catch (e: VehicleHasActiveContractException) {
            errorMessage.value = strings.kendaraanErrorHasContract(e.plate)
            Result.failure(e)
        }
}