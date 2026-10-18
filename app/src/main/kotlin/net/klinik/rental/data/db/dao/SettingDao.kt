package net.klinik.rental.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import net.klinik.rental.data.db.entity.SettingEntity

/**
 * Reads and writes for `SETTINGS`.
 *
 * ## Why there is no `@Insert` and an `@Upsert` instead
 *
 * Because configuration is *changed*, not appended: Story 5.4 moves the cutoff day from 25 to
 * 20, which is an update to an existing key rather than a second row with the same key. An
 * `@Insert` would refuse that with a constraint failure the owner cannot act on, and a
 * delete-then-insert would leave a window where the setting does not exist — which, for the
 * cutoff day, means a deposit recorded in that window is allocated against the wrong period.
 *
 * `@Upsert` makes "write this setting" one statement with no gap.
 *
 * ## Why the default is supplied on read, not stored
 *
 * [findInt] falls back to [default] when the key is absent, so a **fresh install can record a
 * deposit on its first run** rather than failing because configuration has not been written.
 * The alternative — seeding a row at migration time — puts a value in the database that the
 * owner never chose, and makes "unset" indistinguishable from "set to the default" the moment
 * Story 5.4 wants to show what a value actually is.
 */
@Dao
interface SettingDao {

    /** The raw stored value, or `null` when the key has never been written. */
    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun findValue(key: String): String?

    /**
     * Every setting row, ordered by key.
     *
     * Story 5.1's backup read, and the only whole-table read this DAO has: everything else
     * here is per-key, because the app reads configuration one value at a time. A backup is
     * the one caller that does not know the keys in advance — it has to take whatever is
     * there, including keys a later version wrote and this one has never heard of.
     *
     * The ordering exists so the exported file is stable between two runs over the same
     * database, which is what lets an owner diff two backups and see what actually changed.
     */
    @Query("SELECT * FROM settings ORDER BY key ASC")
    suspend fun all(): List<SettingEntity>

    /**
     * The stored value read as an [Int], falling back to [default].
     *
     * The fallback covers three cases that all want the same answer: the key was never
     * written, it holds something unparseable (a half-finished edit, or a value written by a
     * future version), or the caller asked for a key that does not exist. In all three the
     * owner gets a working app and a figure that is at least explicable.
     *
     * An **unparseable** value falls back rather than throwing, and that is a deliberate
     * choice: configuration is not money, and refusing to record a deposit because a setting
     * is corrupt would be a worse failure than recording it against the default cutoff.
     */
    suspend fun findInt(key: String, default: Int): Int =
        findValue(key)?.trim()?.toIntOrNull() ?: default

    /**
     * Writes the setting, replacing any existing row for the key.
     *
     * Named rather than a bare `upsert`, so "the only writer of this table" stays one
     * greppable fact — the same argument `PaymentDao.insertPayment` makes.
     */
    @Upsert
    suspend fun putSetting(setting: SettingEntity)

    /**
     * Removes the row for [key], if there is one.
     *
     * Added by Story 4.4 and **not** by any earlier story, which is worth stating: until then
     * every key written was permanent. A field the owner can set once and never change is not
     * configuration, it is a constant — and the business address and phone are exactly the
     * fields that change when the business moves.
     *
     * The distinction this preserves is between *absent* and *present but empty*. A row holding
     * `""` would be a value the owner set to nothing, which reads back as "they filled this in
     * and it is blank" rather than "they never touched it" — and the two are different facts
     * about the same business.
     *
     * A `@Query` rather than Room's `@Delete` annotation, which matches on the **primary key**
     * of an entity: there is no entity to hand it, and a delete-by-key is what a settings table
     * actually means.
     */
    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun deleteSetting(key: String): Int
}