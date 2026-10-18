package net.klinik.rental.ui

import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle

/**
 * The app's **one** search rule — Story 4.6.
 *
 * ## Why this lives above the screens rather than beside one of them
 *
 * The global search field in the shell and the two lists that can be narrowed both have to
 * agree about what "matches" means. `PenyewaUiState.filterByQuery`'s own KDoc names the
 * failure this prevents: *"two independent copies of one rule is how the search AC ends up
 * verified against a function the screen never calls."* A third copy in the shell would be
 * that failure again, one story later.
 *
 * ## Why each kind keeps the fields it already matched
 *
 * The two rules were **not** identical before this file existed, and that is deliberate
 * rather than accidental:
 *
 *  - a renter matches on name, phone and bank account — the three things an owner knows
 *    about a person, with `emergencyContact` deliberately excluded because matching an
 *    emergency number answers a question nobody asked
 *  - a vehicle matches on plate, brand, model and colour — the AC names plates, but
 *    narrowing to plates alone would be a silent regression on a screen that already
 *    matches a car by "Avanza"
 *
 * So this file carries both shapes rather than one general "matches anything" rule, and it
 * does not narrow either one.
 *
 * ## Why the needle is normalised by the caller
 *
 * [matchesSearch] takes an already-lowercased, already-trimmed needle. Normalising inside
 * would re-normalise once per row per emission, and — more importantly — would let two
 * callers disagree about whether a blank query matches everything. [asSearchNeedle] is the
 * one place that decides, and both kinds share it.
 */
fun String.asSearchNeedle(): String = trim().lowercase()

/**
 * Whether one renter matches [needle].
 *
 * A blank needle matches everything, which is what lets a list start whole.
 */
fun Renter.matchesSearch(needle: String): Boolean =
    needle.isEmpty() ||
        name.lowercase().contains(needle) ||
        phone.lowercase().contains(needle) ||
        bankAccount?.lowercase()?.contains(needle) == true

/**
 * Whether one vehicle matches [needle].
 *
 * The four fields are the ones the fleet screen already searched by. See the file KDoc on
 * why the plate alone is not enough.
 */
fun Vehicle.matchesSearch(needle: String): Boolean =
    needle.isEmpty() ||
        plate.lowercase().contains(needle) ||
        brand.lowercase().contains(needle) ||
        model.lowercase().contains(needle) ||
        color?.lowercase()?.contains(needle) == true
