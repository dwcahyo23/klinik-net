package net.klinik.rental.domain.model

import net.klinik.rental.domain.parse.normalizePlate
import net.klinik.rental.domain.validation.InvalidAmountException

/**
 * One vehicle in the fleet: the first thing Andi types, and the thing every
 * rental depends on.
 *
 * Deliberately not the `VEHICLE` Room entity — AD-1 keeps `domain/` free of Room,
 * and the entity in `data/db/entity/` maps to this.
 *
 * ## Why there is no `status`
 *
 * The prototype stored `status` on the vehicle and also stored
 * `currentRenterId`. Both are display conventions that drift: a contract can end
 * without anyone updating the column, and the column then lies until the next
 * write happens to correct it. Status is *derived* —
 * `servis` from [isServis], `tersewa` from the existence of an open contract
 * (`endDate IS NULL`), `tersedia` otherwise — so it cannot be stale. A stored
 * status would have to be deleted later; a derivation only gets its rules
 * extended, which is what Story 2.3 does.
 *
 * [isServis] is the one status input that *is* stored, because it is a manual
 * marker about the car itself, not a fact any other table can answer. It never
 * stops billing: the only thing that stops a charge day is a service exception
 * recorded against a contract for a specific date.
 *
 * ## Why the plate is validated on construction
 *
 * [plate] is required to be *already normalised*. Storing a normalised value in
 * the field rather than normalising on read means the unique index is built on
 * the same string the model holds, so a row cannot be written whose plate text
 * differs from the key that makes it unique. The repository normalises first
 * and the constructor then confirms the round-trip, which turns "somebody forgot
 * to normalise" into a construction failure instead of a duplicate row.
 *
 * [id] is a `String` rather than a `UUID` because AD-1 restricts `domain/` to
 * `kotlin.*`, `java.time.*` and `kotlinx.coroutines.*` — `java.util.UUID` is not
 * importable, so the id arrives as a string from the layer that owns
 * persistence. The database keeps it a `uuid` column; nothing here parses it.
 *
 * @param year the model year as entered. No range check: the intent states
 *   required-and-unique for the plate and nothing at all for the year, and
 *   inventing a plausible range would reject a real car.
 * @param color optional — plenty of fleets are single-colour and the owner
 *   should not be forced to invent a second fact.
 */
data class Vehicle(
    /** Stable identity; the `vehicleId` half of `CONTRACT`'s foreign key. */
    val id: String,
    /** Normalised plate: trimmed, whitespace-squeezed, uppercased. Never blank. */
    val plate: String,
    val brand: String,
    val model: String,
    val year: Int,
    val color: String? = null,
    /** Rupiah per day. Always greater than zero (NFR-6). */
    val dailyRate: Long,
    val notes: String? = null,
    /** The manual service marker. See the class KDoc for what it does not do. */
    val isServis: Boolean = false,
) {
    init {
        // Refuses a blank plate by way of the same rule the unique index uses,
        // so `normalizePlate` is the single definition of "a plate has a form".
        val normalized = normalizePlate(plate)
        require(normalized != null) {
            "plate must not be blank, but was '$plate'"
        }
        require(normalized == plate) {
            "plate must already be normalised: expected '$normalized', but was '$plate'"
        }
        // Mirrors `Payment` and `Allocation`: money is checked where it enters
        // the domain, never defaulted. The prototype's
        // `dailyRate: parseInt(x) || 250000` is the opposite of this rule.
        InvalidAmountException.requireValid(dailyRate)
    }
}