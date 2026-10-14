package net.klinik.rental.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.klinik.rental.domain.parse.normalizePlate
import net.klinik.rental.domain.validation.InvalidAmountException

/**
 * The `VEHICLE` table.
 *
 * ## Why `plate` is the unique index, not `currentRenterId`
 *
 * The prototype kept uniqueness in a JavaScript `.some()` over
 * `JSON.parse(localStorage)` — a full scan of the store with no index behind it,
 * so two writes that raced could both pass and both land. Here `plate` is a real
 * `UNIQUE` index and `ABORT` is the insert strategy: the database refuses, so
 * the guarantee holds no matter how many callers race past the UI's pre-check.
 *
 * The index is on the *normalised* value, which is why
 * `net.klinik.rental.domain.parse.normalizePlate` is applied before every write
 * (see `VehicleRepository`) and again by the domain model's constructor. A row
 * whose stored text differed from its key would let `b 1235 tf` and `B 1235 TF`
 * coexist — exactly the collision the epic context forbids.
 *
 * ## Why there is no `status` column
 *
 * Status is derived, never stored: `servis` from [isServis], `tersewa` from the
 * existence of an open contract (`CONTRACT.endDate IS NULL`), `tersedia`
 * otherwise. A stored column drifts the moment a contract ends without anyone
 * updating the vehicle row, and the drift is invisible until an owner books
 * against a car that is already rented. See the epic context's "Status is
 * display, not truth".
 *
 * [isServis] is the one status *input* that is stored, because it is a manual
 * marker about the car itself that no other table can answer.
 *
 * ## Why `dailyRate` is `Long` and not `Int`
 *
 * Rupiah end to end in whole units (NFR-6). A monthly figure on a long rental is
 * tens of millions and an `Int` overflows silently above ~2.1 billion, which is
 * a plausible accumulated amount — so the column is `Long`, matching the domain.
 *
 * @property id a `uuid` column in the database, surfaced to the domain as a
 *   `String`. Room generates nothing: ids arrive from the caller, and there is no
 *   seeded data and no `Date.now()`-derived id in this story.
 */
@Entity(
    tableName = "vehicle",
    indices = [
        // Unique *after* normalisation — the column stores the normalised value,
        // so `b 1235 tf` and `B 1235 TF` collide here rather than only in a UI
        // check.
        Index(value = ["plate"], unique = true),
    ],
)
data class VehicleEntity(
    @PrimaryKey
    val id: String,
    val plate: String,
    val brand: String,
    val model: String,
    val year: Int,
    @ColumnInfo(defaultValue = "NULL")
    val color: String?,
    /** Whole rupiah, always greater than zero. */
    val dailyRate: Long,
    @ColumnInfo(defaultValue = "NULL")
    val notes: String?,
    /** The manual service marker. Never stops billing on its own. */
    val isServis: Boolean = false,
) {
    init {
        // The `UNIQUE` index is only as good as the values it compares, and a
        // column holding `"b   1235  tf"` alongside `"B 1235 TF"` would let one
        // physical car become two rows — the index would compare them as
        // different strings and correctly *not* fire.
        //
        // So the value written to the column must already be normalised, and this
        // refuses it loudly rather than relying on every caller to remember.
        // `VehicleRepository` normalises first and `Vehicle`'s constructor
        // confirms the round-trip; this is the last place an un-normalised plate
        // could slip through, and it closes it structurally instead of by
        // convention.
        val normalized = normalizePlate(plate)
        require(normalized != null && normalized == plate) {
            "plate must already be normalised: got '$plate'"
        }
        // Money is rejected at the entity as well as in the domain, so a raw
        // `@Insert` cannot land a zero rate either.
        InvalidAmountException.requireValid(dailyRate)
    }
}