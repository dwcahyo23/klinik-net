package net.klinik.rental.ui.screens.penyewa

import net.klinik.rental.domain.model.Renter
import net.klinik.rental.ui.asSearchNeedle
import net.klinik.rental.ui.matchesSearch

/**
 * One row: a renter plus the phone number shown beneath the name.
 *
 * There is no status on a renter and no money on this screen. A renter is rented
 * or not according to whether an open `CONTRACT` names them, which is Story 2.5's
 * question to answer; until then a stored flag here would be a second source of
 * truth that drifts the moment a contract ends. So this screen is deliberately
 * the plainest one in the app: a name, a phone number, and an optional account.
 */
data class RenterRow(val renter: Renter)

/**
 * The whole screen in one value.
 *
 * Built with `combine` + `stateIn` + `distinctUntilChanged()` over the Room flow
 * (see `PenyewaViewModel`).
 *
 * @param isLoading true while the Room flow has not emitted anything yet.
 *   See `KendaraanUiState.isLoading` for why this cannot be `rows.isEmpty()`:
 *   Room's first emission is an empty list, so during every load a screen that
 *   branched on emptiness showed "Belum ada Penyewa" — an invitation to add a
 *   person the owner already added. The default is `false` because a hand-built
 *   fixture already holds its rows; `PenyewaViewModel`'s `stateIn` initial value
 *   passes `true` explicitly.
 * @param errorMessage Indonesian copy for the last refused write, or `null`.
 *   Already-resolved text rather than an exception, because the exception's
 *   whole purpose is to survive until here and pick its words.
 */
data class PenyewaUiState(
    val rows: List<RenterRow> = emptyList(),
    val query: String = "",
    val form: RenterFormState? = null,
    /**
     * Carried and *rendered*.
     *
     * `KendaraanUiState.errorMessage` was not drawn by its screen until Story 2.8
     * — dead state that made a refusal invisible. This one is rendered by the
     * screen as a snackbar, so a name that is not unique never leaves the owner
     * guessing whether the save landed.
     */
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    /**
     * The renter whose detail pane is showing, or `null` for "nothing chosen yet".
     *
     * An **id**, not a row: rows are rebuilt from every emission, and a held row would go stale
     * the moment the owner edits the person it names.
     */
    val selectedRenterId: String? = null,
    /** The open contract behind the selected renter, or `null` when they are not renting. */
    val selectedContract: RenterContractFacts? = null,
) {
    /** The rows the list renders: the query narrowed, in place. */
    val visibleRows: List<RenterRow> = filterByQuery(rows, query)

    /**
     * The row the detail pane shows.
     *
     * Falls back to the first visible row, which is `renters.html`'s own rule: a pane that sits
     * empty on arrival while a list of people is plainly there reads as broken.
     */
    val selectedRow: RenterRow?
        get() = visibleRows.firstOrNull { it.renter.id == selectedRenterId }
            ?: visibleRows.firstOrNull()

    /**
     * Whether the empty state applies.
     *
     * Two different emptinesses with two different messages: a genuinely empty
     * renter list is an invitation to add a person, while an empty *search
     * result* is not a failure and must not read like one. The epic context is
     * explicit that an error state may never look like a new account.
     */
    val isEmptyList: Boolean get() = rows.isEmpty()

    val isEmptySearch: Boolean
        get() = rows.isNotEmpty() && visibleRows.isEmpty()

    /**
     * The subtitle on the top bar.
     *
     * The **stored** count, not the visible one, and deliberately so: while a
     * search is active the bar answers "how much have I stored?" while the list
     * answers "how many match?", and both are true at once. `kendaraan_count`
     * counts the filtered set because it sits above a *chip* whose number must
     * agree with the list beside it; this bar is a total, not a filter readout.
     */
    val renterCount: Int get() = rows.size
}

/**
 * What the renter detail pane needs about the person's open contract.
 *
 * `renters.html:202-279`: a large avatar beside the name, three small figures, then the active
 * contract — its car, its rate, its schedule. The figures the prototype adds (total setoran,
 * tunggakan, jumlah transaksi) come from the payments repository and are `TunggakanScreen`'s and
 * `KontrakScreen`'s subject; a second read of them here would be a second answer to one bill, so
 * the pane shows the contract and leaves the money to the screens that own it.
 *
 * @property vehiclePlate the car being rented.
 * @property vehicleText `Toyota Avanza 2023`, already joined.
 * @property rateText the frozen daily rate.
 * @property startText the contract's start date, pre-formatted.
 * @property restDay the one day off per week, or `null` for a seven-day week.
 */
data class RenterContractFacts(
    val vehiclePlate: String,
    val vehicleText: String,
    val rateText: String,
    val startText: String,
    val restDay: java.time.DayOfWeek?,
)

/**
 * The renter form's own state, held by the ViewModel.
 *
 * Every field is a `String`, not a typed value, because there is nothing here to
 * parse: no money, no date, no number that means a quantity. The name's
 * non-blankness is the one rule, and it is a `require` in `Renter.init` rather
 * than a parse — which is why trimming happens here rather than at save time.
 *
 * @param error the last refusal's Indonesian message for [name], or `null`.
 *   Field-level rather than screen-level, because a refused save leaves the
 *   sheet open and a message floating over the list would be about a form the
 *   owner is still looking at.
 */
data class RenterFormState(
    val id: String? = null,
    val name: String = "",
    val phone: String = "",
    val bankAccount: String = "",
    val emergencyContact: String = "",
    /**
     * The avatar pack key for this renter's disc, or blank for "none chosen".
     *
     * A key (`avatar_07`), not an emoji and not a drawable id — see `AVATAR_KEYS`. A key
     * survives a font change, cannot be half-typed, and an unrecognised one is recognisably
     * unrecognised rather than rendering as somebody else's face.
     *
     * A `String` rather than a `String?` for the same reason [bankAccount] is: the form's
     * representation of "not set" is an empty field the owner can pick into, and a nullable
     * would put an `?: ""` at every edit site.
     */
    val avatar: String = "",
    val nameError: String? = null,
    /**
     * Per-field messages, one slot per required field.
     *
     * Three separate slots rather than a single `error`, because the refusal has
     * to land on the field that caused it: an owner looking at an empty phone
     * field needs that field to say why, not the one they happened to be reading.
     * `bankAccount` has no slot because it is optional and cannot be refused.
     */
    val phoneError: String? = null,
    val emergencyError: String? = null,
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
 * Substring, case-insensitive, over name/phone/bank account.
 *
 * ## Why these three
 *
 * They are the three fields an owner would search a person by: who they are, how
 * to call them, and where the money lands. There is no fourth — `emergencyContact`
 * is deliberately *not* searched, on the same reasoning that a vehicle's notes
 * are not: matching a contact number belongs to the emergency, not to the list,
 * and a search that returned a renter because their emergency number happened to
 * match would be answering a question nobody asked.
 *
 * A blank or whitespace-only query matches everything, so the list starts whole.
 *
 * Case-insensitive because the owner types on a phone keyboard with no shift key
 * within reach; a search that only matched the stored capitalisation would be
 * useless for exactly the input it is designed for. The *stored* name keeps its
 * capitalisation — this lowercases the comparison, not the data.
 *
 * Pure, so the `SEARCH_RENTER` matrix row is testable without a device.
 */
fun filterByQuery(rows: List<RenterRow>, query: String): List<RenterRow> {
    val needle = query.trim()
    if (needle.isEmpty()) return rows
    // Composed from `matchesRenterQuery` rather than repeating its three-way
    // predicate. Two independent copies of one rule is how the search AC ends up
    // verified against a function the screen never calls: `matchesRenterQuery`
    // would be tested while `filterByQuery` — the one `visibleRows` uses — could
    // lose a field and leave every test green.
    return rows.filter { matchesRenterQuery(it.renter, needle) }
}

/**
 * Whether one renter matches [query].
 *
 * Exposed separately so the `SEARCH_RENTER` matrix row can be asserted against
 * the rule itself rather than through a list that happens to contain one
 * matching row.
 */
fun matchesRenterQuery(renter: Renter, query: String): Boolean =
    // The predicate itself moved to `ui/SearchMatch.kt` in Story 4.6, because the global
    // search field in the shell has to match renters the same way this list does. This
    // function stays as the screen's entry point so every existing caller and test keeps
    // one name to reach, and so there is still exactly one implementation.
    renter.matchesSearch(query.asSearchNeedle())