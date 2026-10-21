package net.klinik.rental.domain.model

import net.klinik.rental.domain.validation.BlankRenterFieldException.BlankEmergencyContactException
import net.klinik.rental.domain.validation.BlankRenterFieldException.BlankNameException
import net.klinik.rental.domain.validation.BlankRenterFieldException.BlankPhoneException

/**
 * One person who rents a vehicle: the person every later story names.
 *
 * Deliberately not the `RENTER` Room entity — AD-1 keeps `domain/` free of Room,
 * and the entity in `data/db/entity/` maps to this.
 *
 * ## Why the name is validated on construction
 *
 * [name] is the key that reconciles a bank mutation against a person (PRD §5),
 * so it is required, non-blank and unique. Two of those three are this model's
 * job; uniqueness is the database's, because only a `UNIQUE` index can refuse
 * two callers who both passed the pre-check.
 *
 * `require(name.isNotBlank())` is the same construction-time refusal
 * [Vehicle] uses for its plate: an invalid renter cannot exist in memory, let
 * alone reach the database. A whitespace-only name is refused on the same rule
 * as an empty one, because `"   "` is not a name an owner can search for again.
 *
 * ## Why there is no `ktp` and no `address`
 *
 * The PRD (§6.1) pushes national ID number and address out of scope, and the
 * epic context restates it as a design constraint. Collecting personal data
 * without a reason is the defect this epic exists to remove — so neither field
 * is declared here, and their absence is asserted in `RenterTest` rather than
 * left to a reviewer's memory. A field added "just in case" would be a field
 * the app can never justify deleting.
 *
 * ## Why there is no status or a derived flag
 *
 * The same reasoning as `Vehicle`: a renter is rented or not according to
 * whether an open `CONTRACT` names them, so any stored flag on the renter would
 * be a second source of truth that drifts the moment a contract ends.
 *
 * [id] is a `String` rather than a `UUID` for the same reason as [Vehicle]: AD-1
 * restricts `domain/` to `kotlin.*`, `java.time.*` and
 * `kotlinx.coroutines.*`, so `java.util.UUID` is not importable and the id
 * arrives as a string from the layer that owns persistence. The database keeps
 * it a `uuid` column; nothing here parses it.
 *
 * @param phone required. The epic context's data model annotates only
 *   `bankAccount` as nullable, so every other text field is required — a number
 *   the owner cannot reach a renter on is not a fact worth storing.
 * @param bankAccount optional (`null`), the one field the epic context marks
 *   nullable. Plenty of renters are paid in cash, and forcing an account number
 *   would push the owner to invent one.
 * @param emergencyContact required, for the same reason as [phone].
 */
data class Renter(
    /** Stable identity; the `renterId` half of `CONTRACT`'s future foreign key. */
    val id: String,
    /** Required, non-blank, and unique in the app. Never normalised. */
    val name: String,
    val phone: String,
    val bankAccount: String? = null,
    val emergencyContact: String,
    /**
     * The avatar pack key drawn on this renter's disc, or `null` for "none chosen".
     *
     * A key (`avatar_07`), not an emoji: the app's pack is a fixed set, and a key names an
     * entry in it. An emoji would be a glyph the system font decides the shape of.
     *
     * Not validated beyond being trimmed at the write. A key the app does not carry is a
     * legitimate value — a backup from a build with a different pack — and the domain has no
     * business knowing which keys exist.
     */
    val avatar: String? = null,
) {
    init {
        // Each refusal is a typed exception carrying *which* field was empty, so
        // the ViewModel can put the message on that field instead of guessing.
        // See `BlankRenterFieldException` for why that matters to the owner.
        if (name.isBlank()) throw BlankNameException(name)

        // The same refusal for the two contact fields. Without it the columns are
        // `NOT NULL` in the schema but an empty string satisfies that, so the
        // owner sees three apparently-required fields and can silently store two
        // of them blank: a renter nobody can be reached on, recorded as though
        // the number had been collected. Story 2.5's contract picker and Epic 3's
        // WhatsApp reminder both read these columns, and a blank cell would be
        // the only record that they never were.
        if (phone.isBlank()) throw BlankPhoneException(phone)
        if (emergencyContact.isBlank()) {
            throw BlankEmergencyContactException(emergencyContact)
        }

        // An empty id is a primary key that collides with every other empty id,
        // and the repository would report that as a *name* duplicate — the wrong
        // column again. Refused here so the caller hears about the id.
        require(id.isNotBlank()) {
            "id must not be blank"
        }
    }
}