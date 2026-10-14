package net.klinik.rental.ui.screens.kendaraan

import net.klinik.rental.domain.model.Vehicle

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
 * (see `KendaraanViewModel`). There is deliberately no `isLoading` flag and no
 * skeleton here: loading-as-skeleton is Story 2.8's contract across every
 * screen, and this story explicitly excludes skeletons. Room's first emission is
 * an empty list, which is what the empty state renders — which is honest, since
 * an empty database and a not-yet-read one are not distinguishable to the owner
 * and neither is a failure.
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
) {
    /** The rows the list renders: query, then status filter. */
    val visibleRows: List<VehicleRow> = filterByStatus(matchesQuery(rows, query), filter)

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
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return rows
    return rows.filter { row ->
        row.vehicle.plate.lowercase().contains(needle) ||
            row.vehicle.brand.lowercase().contains(needle) ||
            row.vehicle.model.lowercase().contains(needle) ||
            row.vehicle.color?.lowercase()?.contains(needle) == true
    }
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