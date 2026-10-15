package net.klinik.rental.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.klinik.rental.data.db.entity.RenterEntity

/**
 * Reads and writes for `RENTER`.
 *
 * ## Why this DAO does no validation
 *
 * A DAO that "helpfully" checked a name for duplicates would be a second place
 * the rule lives, and the place that loses the race. Uniqueness is enforced by
 * the schema's `UNIQUE` index and non-emptiness by the entity and the domain
 * model; this layer only moves rows and lets SQLite say no. `RenterRepository`
 * is where a refusal gets an exception type and an Indonesian message.
 *
 * There is no `ktp` and no `address` query here either — not because they are
 * filtered, but because no such column exists to query.
 */
@Dao
interface RenterDao {

    // ------------------------------------------------------------------ reads

    /**
     * Every renter, as a [Flow].
     *
     * A `Flow`, not a one-shot read, and there is no manual invalidation signal
     * anywhere in the app: Room's own tracker re-emits on write, so a renter
     * saved by the form appears in the list without a refresh (the AC), and a
     * second mechanism could only ever disagree with this one.
     *
     * Ordered by name so the list order is stable and meaningful — the name is
     * the key the owner reconciles against, so alphabetical order is the one
     * they are already reading it in.
     *
     * `COLLATE NOCASE`, because SQLite's default `BINARY` orders by code point,
     * which puts every lowercase name ahead of every capitalised one: `budi`
     * sorts before `Zainal`. The stored name keeps its own capitalisation —
     * names are not plates and are not normalised (see `Renter`) — so the sort
     * has to ask for case-insensitive comparison itself rather than assume the
     * data is already uniform.
     */
    @Query("SELECT * FROM renter ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<RenterEntity>>

    @Query("SELECT * FROM renter WHERE id = :id")
    suspend fun findById(id: String): RenterEntity?

    /**
     * Whether this name is already taken.
     *
     * A pre-check, not the guarantee: it lets the form fail before the owner
     * leaves the sheet, but two callers can both pass it. The `UNIQUE` index is
     * what actually refuses. Both exist deliberately — see
     * `DuplicateNameException`'s KDoc.
     */
    @Query("SELECT name FROM renter WHERE name = :name LIMIT 1")
    suspend fun findName(name: String): String?

    /**
     * The id of the renter holding [name], or `null` if nobody holds it.
     *
     * The *id* rather than the name, because "is this my own name?" is a question
     * about identity and only the id answers it. `findName` cannot: it returns a
     * name, which says the name exists but not whose it is. `RenterRepository.save`
     * needs both facts — the id to update in place, and the holder's id to decide
     * whether a collision belongs to this row or a neighbour.
     */
    @Query("SELECT id FROM renter WHERE name = :name LIMIT 1")
    suspend fun findIdByName(name: String): String?

    // ----------------------------------------------------------------- writes

    /**
     * Inserts, letting the `UNIQUE` index raise.
     *
     * `OnConflictStrategy.ABORT` is Room's default and is stated explicitly
     * because it is the load-bearing choice here. `REPLACE` would *delete* the
     * colliding row and insert this one instead — which would let a race
     * silently overwrite a real renter and take their phone number with it. An
     * edit goes through [update], which matches on the primary key and
     * therefore cannot hit the name index at all.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(renter: RenterEntity)

    /** Edits in place, matched on the primary key. */
    @Update
    suspend fun update(renter: RenterEntity)
}