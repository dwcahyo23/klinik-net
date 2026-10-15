package net.klinik.rental.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.VehicleEntity

/**
 * Reads and writes for `VEHICLE` and the `CONTRACT` slice that status
 * derivation needs.
 *
 * ## Why this DAO does no validation
 *
 * A DAO that "helpfully" checked a plate for duplicates would be a second place
 * the rule lives, and the place that loses the race. Uniqueness is enforced by
 * the schema's `UNIQUE` index and positivity by the domain model; this layer
 * only moves rows and lets SQLite say no. `VehicleRepository` is where a refusal
 * gets an exception type and an Indonesian message.
 *
 * Note what is *absent*: there is no `status` column, no `currentRenterId`, and
 * no `updateStatus`. There is no query here that could set one.
 */
@Dao
interface VehicleDao {

    // ------------------------------------------------------------------ reads

    /**
     * Every vehicle, as a [Flow].
     *
     * A `Flow`, not a one-shot read, and there is no manual invalidation signal
     * anywhere in the app: Room's own tracker re-emits on write, so a vehicle
     * saved by the form appears in the list without a refresh (the AC), and a
     * second mechanism could only ever disagree with this one.
     *
     * Ordered by plate so the list order is stable and meaningful rather than
     * dependent on insertion order. ISO `yyyy-MM-dd` date columns sort
     * lexicographically; this column is a plain string and sorts the same way.
     */
    @Query("SELECT * FROM vehicle ORDER BY plate ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicle WHERE id = :id")
    suspend fun findById(id: String): VehicleEntity?

    /**
     * The stored plate for [vehicleId], or `null` if there is no such vehicle.
     *
     * Used by the delete path to name the blocking vehicle in
     * `VehicleHasActiveContractException` — a refusal the owner cannot act on is
     * not a message.
     */
    @Query("SELECT plate FROM vehicle WHERE id = :id")
    suspend fun findPlateById(id: String): String?

    /**
     * Whether this plate is already taken, matched on the *normalised* value.
     *
     * A pre-check, not the guarantee: it lets the form fail before the owner
     * leaves the sheet, but two callers can both pass it. The `UNIQUE` index is
     * what actually refuses. Both exist deliberately — see
     * `DuplicatePlateException`'s KDoc.
     */
    @Query("SELECT plate FROM vehicle WHERE plate = :plate LIMIT 1")
    suspend fun findPlate(plate: String): String?

    /**
     * Whether this vehicle has an open contract — the whole of status
     * derivation's `tersewa` rule, asked as one indexed existence check rather
     * than by loading contracts and filtering in Kotlin.
     *
     * `endDate IS NULL` is what "active" means; see `ContractEntity`.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM contract
            WHERE vehicleId = :vehicleId AND endDate IS NULL
        )
        """,
    )
    suspend fun hasOpenContract(vehicleId: String): Boolean

    /** Vehicles with an open contract, for the list's `tersewa` count. */
    @Query(
        """
        SELECT v.* FROM vehicle v
        WHERE EXISTS(
            SELECT 1 FROM contract c
            WHERE c.vehicleId = v.id AND c.endDate IS NULL
        )
        ORDER BY v.plate ASC
        """,
    )
    fun observeRented(): Flow<List<VehicleEntity>>

    // ----------------------------------------------------------------- writes

    /**
     * Inserts, letting the `UNIQUE` index raise.
     *
     * `OnConflictStrategy.ABORT` is Room's default and is stated explicitly
     * because it is the load-bearing choice here. `REPLACE` would *delete* the
     * conflicting row and insert this one instead — which would let a race
     * silently overwrite a real vehicle and, because of `onDelete = RESTRICT`,
     * fail outright once contracts point at it. An edit goes through [update],
     * which matches on the primary key and therefore cannot hit the plate index
     * at all.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(vehicle: VehicleEntity)

    /** Edits in place, matched on the primary key. */
    @Update
    suspend fun update(vehicle: VehicleEntity)

    /**
     * Deletes by id, which the foreign key can refuse.
     *
     * No `@Delete` and no cascade here: `CONTRACT.vehicleId` carries
     * `onDelete = ForeignKey.RESTRICT`, so SQLite raises instead of removing the
     * contract. `VehicleRepository` translates that into
     * `VehicleHasActiveContractException` *after* checking whether an open
     * contract exists, so the typed exception names the reason rather than
     * surfacing a raw SQLite error.
     */
    @Query("DELETE FROM vehicle WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /**
     * Writes the minimum contract that makes the delete refusal testable.
     *
     * Test-only in practice — Story 2.5 opens contracts through its own
     * repository — but it belongs on this DAO rather than in a test's raw SQL,
     * so the foreign key and the `ContractEntity` shape are exercised the same
     * way production would exercise them.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertContract(contract: ContractEntity)

    /**
     * Every contract row, as a [Flow], in a stable order.
     *
     * Read-only observation used to assert contract-row invariants — Story 2.3's
     * "marking servis leaves the contract row byte-for-byte untouched" test
     * queries through this, and a later story can rely on the same stable order
     * when it adds its own invariants. `startDate` is ISO `yyyy-MM-dd` TEXT, so
     * lexicographic order equals chronological order; `id` breaks the tie.
     */
    @Query("SELECT * FROM contract ORDER BY startDate ASC, id ASC")
    fun allContracts(): Flow<List<ContractEntity>>
}