package net.klinik.rental.ui.screens.kendaraan

import net.klinik.rental.domain.model.Vehicle
import java.time.DayOfWeek
import net.klinik.rental.ui.asSearchNeedle
import net.klinik.rental.ui.matchesSearch

/**
 * The three fleet statuses, derived — never stored on the vehicle.
 *
 * Story 2.3 owns what these *mean*; this story needs only the derivation, and
 * gets it now so the filter row can exist at all. A stored `status` column would
 * have to be deleted later; a derivation only gets its rules extended.
 */
enum class VehicleStatus {
    /** The manual marker is set. Never stops billing on its own. */
    Servis,

    /** An open contract exists (`endDate IS NULL`). */
    Tersewa,

    /** Neither of the above. */
    Tersedia,
    ;

    companion object {
        /**
         * The derivation, in one place.
         *
         * [isServis] is checked first and deliberately wins: a car in for service
         * *and* still under contract is `Servis`, because that is the fact the
         * owner needs on their morning screen — the car is off the road. The
         * open contract still bills either way, so nothing is lost by preferring
         * it here.
         */
        fun of(isServis: Boolean, hasOpenContract: Boolean): VehicleStatus = when {
            isServis -> Servis
            hasOpenContract -> Tersewa
            else -> Tersedia
        }
    }
}

/** The four chips on the filter row. Not a route — a filter over one list. */
enum class VehicleFilter(val label: String) {
    Semua("Semua"),
    Tersedia("Tersedia"),
    Tersewa("Tersewa"),
    Servis("Servis"),
}

/** One row: a vehicle plus its derived status and pre-rendered figures. */
data class VehicleRow(
    val vehicle: Vehicle,
    val status: VehicleStatus,
    /** `formatRupiah(vehicle.dailyRate)`, computed once per emission. */
    val rateText: String,
)

/**
 * The whole screen in one value.
 *
 * Built with `combine` + `stateIn` + `distinctUntilChanged()` over the Room flow
 * (see `KendaraanViewModel`).
 *
 * @param isLoading true while the Room flow has not emitted anything yet.
 *
 *   ## Why this is a separate field and not `rows.isEmpty()`
 *
 *   Room's first emission *is* an empty list, so "no rows" cannot distinguish
 *   "still reading" from "read, and the fleet is empty" — and the second must
 *   render an invitation to add a car. Story 2.8's whole subject is that
 *   distinction: a skeleton drawn *over* an empty state reproduces "empty data
 *   that looks like a new account" on every single load, because during every load
 *   `rows` is empty.
 *
 *   ## Why it defaults to `false`
 *
 *   Every fixture in every test builds a state with rows already in hand, and a
 *   default of `true` would make each of them a loading screen. The
 *   ViewModel's `stateIn(initialValue = …)` passes `isLoading = true`
 *   explicitly, because *that* value genuinely is still loading — so the default
 *   describes a hand-built state and the initial value describes a real one.
 *
 *   ## Why it is derived rather than a mutable flag
 *
 *   The ViewModel combines a `flow { emit(false); … }`-shaped source into the
 *   existing `combine`. A `MutableStateFlow<Boolean>` set to `true` on entry and
 *   cleared on first emission would be a second thing to keep in step with the
 *   subscription — and a screen whose flag says "loaded" while its list is empty
 *   is the exact failure this flag exists to prevent.
 *
 * @param errorMessage Indonesian copy for the last refused write, or `null`.
 *   Already-resolved text rather than an exception, because the exception's whole
 *   purpose is to survive until here and pick its words.
 */
data class KendaraanUiState(
    val rows: List<VehicleRow> = emptyList(),
    val query: String = "",
    val filter: VehicleFilter = VehicleFilter.Semua,
    /** Per-status counts, for the chips. Over the *filtered-by-query* set. */
    val counts: Map<VehicleFilter, Int> = emptyMap(),
    val form: VehicleFormState? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    /**
     * The vehicle whose detail pane is showing, or `null` for "nothing chosen yet".
     *
     * Held as an **id**, not a `VehicleRow`: a row is rebuilt from every emission, and a held row
     * would go stale the moment the owner renames a car in the pane beside it.
     */
    val selectedVehicleId: String? = null,
    /** The four summary figures above the list — `cars.html:155-167`. */
    val summary: FleetSummary = FleetSummary(),
    /** The open contract behind the selected vehicle, or `null` when it is not rented. */
    val selectedContract: VehicleContractFacts? = null,
) {
    /** The rows the list renders: query, then status filter. */
    val visibleRows: List<VehicleRow> = filterByStatus(matchesQuery(rows, query), filter)

    /**
     * The row the detail pane shows.
     *
     * ## Why this falls back to the first visible row
     *
     * Because `cars.html:202` does exactly that — `if (!selectedId || !cars.find(...)) selectedId
     * = cars[0].id` — and the alternative is a pane that sits empty on arrival while a list of
     * cars is plainly there. The fallback is also what keeps the pane honest when a filter hides
     * the selection: the owner sees the first car of the list they can actually see.
     */
    val selectedRow: VehicleRow?
        get() = visibleRows.firstOrNull { it.vehicle.id == selectedVehicleId } ?: visibleRows.firstOrNull()

    /**
     * Whether the empty state applies.
     *
     * Two different emptinesses with two different messages: a genuinely empty
     * fleet is an invitation to add a car, while an empty *search result* is not
     * a failure and must not read like one. The epic context is explicit that an
     * error state may never look like a new account.
     */
    val isEmptyFleet: Boolean get() = rows.isEmpty()

    val isEmptySearch: Boolean
        get() = rows.isNotEmpty() && visibleRows.isEmpty()

    /** The subtitle on the top bar: how many cars are in the fleet. */
    val fleetCount: Int get() = rows.size
}

/**
 * The four figures in the summary row — `cars.html:147-167`.
 *
 * Every one is derived from [KendaraanUiState.rows] in the ViewModel, so the cards and the list
 * are one read. The figures arrive pre-formatted for the reason every other money value in this
 * app does: `formatRupiah` runs once.
 *
 * @property total registered vehicles.
 * @property rented vehicles, and the utilisation percentage `rented / total`.
 * @property available vehicles.
 * @property potentialText the daily income if every non-servicing car were rented.
 * @property averageText the mean daily rate across the whole fleet.
 */
data class FleetSummary(
    val total: Int = 0,
    val rented: Int = 0,
    val available: Int = 0,
    val utilisationText: String = "",
    val potentialText: String = "",
    val averageText: String = "",
)

/**
 * What the detail pane needs about a vehicle's open contract.
 *
 * ## Why these five facts and not the whole `Contract`
 *
 * Because the pane shows a renter's name, a plate, a rate and a start date — and nothing else on
 * a `Contract` is drawn. Passing the domain object would invite the pane to start deriving from
 * it, which is the second-derivation defect this repo keeps finding.
 *
 * @property renterName the person currently renting, already joined.
 * @property renterPhone their number, for the pane's call action.
 * @property startText the contract's start date, pre-formatted.
 * @property restDay the one day off per week, or `null` for a seven-day week. Carried as the
 *   domain value rather than as a label so the pane resolves the same string resource the
 *   contract screen's own badge uses — two spellings of one schedule is how a badge and a date
 *   beside it end up naming different days.
 * @property rateText the frozen daily rate.
 */
data class VehicleContractFacts(
    val renterName: String,
    val renterPhone: String,
    val startText: String,
    val restDay: DayOfWeek?,
    val rateText: String,
)

/**
 * The vehicle form's own state, held by the ViewModel.
 *
 * @param rateDigits the raw numpad output, *not* a parsed amount. Keeping the
 *   digits rather than the `Long` is what lets `parseRupiahInput` be the only
 *   parse path: the numpad emits digits, the parser turns them into a `Long` at
 *   save time, and nothing between can substitute a default.
 * @param error the last refusal's Indonesian message, or `null`.
 */
data class VehicleFormState(
    val id: String? = null,
    val plate: String = "",
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val color: String = "",
    val rateDigits: String = "",
    val notes: String = "",
    val isServis: Boolean = false,
    /** Field-level messages, keyed by field. `null` means "not validated yet". */
    val plateError: String? = null,
    val rateError: String? = null,
) {
    /**
     * Whether this is an edit rather than a create.
     *
     * A computed property rather than a stored flag: a second boolean would be
     * able to disagree with [id], and the disagreement would show as an edit form
     * that inserts a duplicate.
     */
    val isEditing: Boolean get() = id != null
}

/**
 * Substring, case-insensitive, over plate/brand/model/color — the prototype's
 * four fields and no more.
 *
 * ## Why these four and not the others
 *
 * Year, notes, daily rate, status and renter name are deliberately *not*
 * searched. An owner looking for "Agya" is looking for a car, not for a document
 * containing the word "nilai", and a search that matched every note ever written
 * would return the whole fleet for almost any query.
 *
 * A blank or whitespace-only query matches everything, so the list starts whole.
 *
 * Case-insensitive because the owner types on a phone keyboard with no shift key
 * within reach; a search that only matched the stored capitalisation would be
 * useless for exactly the input it is designed for.
 *
 * Pure, so the `SEARCH_QUERY` matrix rows are testable without a device.
 */
fun matchesQuery(rows: List<VehicleRow>, query: String): List<VehicleRow> {
    val needle = query.asSearchNeedle()
    if (needle.isEmpty()) return rows
    // Composed from `Vehicle.matchesSearch` rather than repeating the four-way predicate —
    // Story 4.6 moved it to `ui/SearchMatch.kt` because the shell's global search must find
    // the same cars this list does. One implementation, two callers, exactly the arrangement
    // `filterByQuery`'s own KDoc argues for on the renter side.
    return rows.filter { row -> row.vehicle.matchesSearch(needle) }
}

/**
 * Narrows to one status, or passes everything through for [VehicleFilter.Semua].
 *
 * A filter over one list, never a separate destination: there is no route change
 * and no second screen, so the other chips stay reachable and the owner's place
 * in the list survives the tap.
 *
 * `null` colour and `null` notes cannot throw here — the filter reads only the
 * derived [VehicleStatus], which is always present.
 */
fun filterByStatus(rows: List<VehicleRow>, filter: VehicleFilter): List<VehicleRow> =
    when (filter) {
        VehicleFilter.Semua -> rows
        VehicleFilter.Tersedia -> rows.filter { it.status == VehicleStatus.Tersedia }
        VehicleFilter.Tersewa -> rows.filter { it.status == VehicleStatus.Tersewa }
        VehicleFilter.Servis -> rows.filter { it.status == VehicleStatus.Servis }
    }

/**
 * The per-chip counts.
 *
 * Counted over the query-filtered set, not the whole fleet: a chip that says
 * "3" while the list shows one match is a claim the screen cannot support, and
 * the owner cannot tell which of the two numbers is the lie.
 */
fun countByStatus(rows: List<VehicleRow>): Map<VehicleFilter, Int> = mapOf(
    VehicleFilter.Semua to rows.size,
    VehicleFilter.Tersedia to rows.count { it.status == VehicleStatus.Tersedia },
    VehicleFilter.Tersewa to rows.count { it.status == VehicleStatus.Tersewa },
    VehicleFilter.Servis to rows.count { it.status == VehicleStatus.Servis },
)