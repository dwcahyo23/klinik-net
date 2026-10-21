package net.klinik.rental.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The `RENTER` table.
 *
 * ## Why `name` is the unique index
 *
 * The renter's name is the key that reconciles a bank mutation against a person
 * (PRD §5), so it carries the same obligation a plate carries on `VEHICLE`:
 * required, non-blank, and unique in the app. It is a real `UNIQUE` index here
 * rather than a check in Kotlin, for the same reason `VehicleEntity` has one —
 * a lookup-before-insert can be raced, and only the database settles the race.
 * `OnConflictStrategy.ABORT` (on the DAO) makes the refusal a refusal rather
 * than a silent overwrite.
 *
 * ## Why the index is on the *exact* name
 *
 * `VehicleEntity`'s index is on the *normalised* plate, so `b 1235 tf` and
 * `B 1235 TF` collide there. This index is deliberately different: names are not
 * normalised, so `Budi` and `budi` are two distinct stored names.
 *
 * The epic context states the plate rule and the name-uniqueness requirement in
 * the same sentence and does not extend one to the other, and the contrast is
 * the point. A plate has one canonical form and a person's name does not —
 * uppercasing a name would degrade every label, list row and WhatsApp message
 * built from it. Matching a sender name against a stored renter is a manual
 * step the owner does in m-banking, so the app's obligation is only that the
 * name is unique enough for search to resolve to one person.
 *
 * ## Why there is no `ktp` and no `address`
 *
 * PRD §6.1 pushes national ID number and address out of scope. They are absent
 * here structurally — there is no column to fill and no path for the data to
 * come back in — rather than merely hidden behind a form that does not show it.
 *
 * ## Why `bankAccount` is the only nullable column
 *
 * The epic context's data model marks exactly one renter field nullable, and
 * that is the one plenty of renters do not have: cash-paying renters are common,
 * and forcing an account number pushes the owner to invent one. `phone` and
 * `emergencyContact` are required — a number the owner cannot reach a renter on
 * is not a fact worth storing.
 *
 * @property id a `uuid` column in the database, surfaced to the domain as a
 *   `String`. Room generates nothing: ids arrive from the repository, and
 *   there is no seeded data in this story.
 */
@Entity(
    tableName = "renter",
    indices = [
        // Exact, not normalised — see the class KDoc on why this differs from
        // `vehicle.plate`'s index.
        Index(value = ["name"], unique = true),
    ],
)
data class RenterEntity(
    @PrimaryKey
    val id: String,
    /** Required, non-blank, and unique. Stored exactly as entered. */
    val name: String,
    val phone: String,
    @ColumnInfo(defaultValue = "NULL")
    val bankAccount: String?,
    val emergencyContact: String,
    /**
     * The avatar pack key drawn on this renter's disc, or `null` for "none chosen".
     *
     * A key (`avatar_07`) rather than an emoji or a drawable id: it survives a font change, and
     * an unrecognised one is *recognisably* unrecognised rather than rendering as somebody
     * else's face. The column is `TEXT` rather than an enum so a future pack's keys are storable
     * without another migration; the app writes only keys it carries.
     *
     * Nullable because every renter recorded before v9 has none, and because `KnAvatar` already
     * has an answer for "no avatar": initials. Back-filling would invent a fact about a person.
     */
    @ColumnInfo(defaultValue = "NULL")
    val avatar: String? = null,
) {
    init {
        // The `UNIQUE` index only refuses a duplicate; it cannot refuse a blank
        // name, because two blanks are just two different empty strings as far
        // as SQLite is concerned. So the non-blank half of the rule is checked
        // here, which closes it for a raw `@Insert` the same way `VehicleEntity`
        // closes the un-normalised-plate case — structurally, rather than by
        // trusting every caller to remember.
        require(name.isNotBlank()) {
            "name must not be blank, but was '$name'"
        }

        // The same for the two contact columns. They are `NOT NULL` in the schema,
        // but SQLite accepts an empty string for a NOT NULL TEXT column, so the
        // declaration alone would let a raw `@Insert` land a renter with no way
        // to reach them — and every later screen would read that blank as a
        // collected number.
        require(phone.isNotBlank()) {
            "phone must not be blank, but was '$phone'"
        }
        require(emergencyContact.isNotBlank()) {
            "emergencyContact must not be blank, but was '$emergencyContact'"
        }

        // And the primary key, for the same reason: an empty id collides with
        // every other empty id, and the repository's constraint translation
        // would report it as a *name* duplicate.
        require(id.isNotBlank()) {
            "id must not be blank"
        }
    }
}