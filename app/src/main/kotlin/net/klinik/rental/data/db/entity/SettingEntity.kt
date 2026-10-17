package net.klinik.rental.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Configuration as key/value text.
 *
 * ## Why this is a table and not DataStore
 *
 * The epic context is explicit: configuration belongs in the database *"supaya ikut tercakup
 * backup/restore (FR-16)"*. Device preferences are not covered by the app's own export, so a
 * cutoff day in DataStore would be one the owner cannot restore — and Story 5.4's whole subject
 * is changing one.
 *
 * ## Why the value column is `String` and must not hold money
 *
 * Because `AD-2`'s bound applies to numeric columns, and a string has no such bound. The first
 * key is the cutoff day — an `Int` 1..28 held as text, validated by
 * `containing(cutoffDay, today)` on read rather than by the column's type. A rupiah figure in
 * this column would be exactly the "money as a string" shape the architecture warns against: it
 * loses its type, its validation and its ordering.
 *
 * ## Why there is no migration concern for the value
 *
 * Nothing is `DEFAULT`ed. A key that is absent means "not configured yet", and the reader
 * supplies the default — which is how a fresh install records a deposit on its first run
 * instead of failing because a configuration row has not been written. Story 5.4 writes the row;
 * until then the value is not in the database at all.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    /** The key: `cutoffDay` today. */
    @PrimaryKey
    val key: String,
    val value: String,
) {
    init {
        require(key.isNotBlank()) { "key must not be blank" }
    }
}