package net.klinik.rental.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.klinik.rental.data.db.entity.ContractEntity

/**
 * Reads and writes for `CONTRACT`.
 *
 * ## Why this DAO does no validation
 *
 * A DAO that "helpfully" checked whether a vehicle already had an open contract
 * would be a second place the one-active rule lives, and the place that loses the
 * race. That rule is enforced by the schema's two `UNIQUE` indexes over the active
 * columns, and the money rule by the entity and the domain model; this layer only
 * moves rows and lets SQLite say no. `ContractRepository` is where a refusal gets
 * an exception type and an Indonesian message.
 *
 * Note what is *absent*: no `status` query, no `settledThroughDate` column, and no
 * exception table. `endDate IS NULL` is what "open" means, settlement is Epic 3's,
 * and service exceptions are Story 2.6's.
 *
 * ## Why the two blocking-name lookups are JOINs when the list's are not
 *
 * [findOpenPlateFor] and [findOpenRenterNameFor] join `vehicle` and `renter`, and
 * that is deliberate rather than an oversight. The "join in Kotlin, not in SQL" rule
 * — see `ContractRepository.observeContracts` — governs the **list's display names**:
 * those are read on every emission, both flows are already observed by the ViewModel,
 * and a `@Query` with two JOINs would be a second reader of `renter` and `vehicle`
 * beside `observeRenters()`/`observeVehicles()`, which is the two-readers problem
 * AD-5 exists to prevent.
 *
 * These two are a different shape. Each returns **one value**, is called **once**,
 * and only on the path where a write has just been refused — inside a transaction
 * that is already unwinding. Routing them through the ViewModel's collected flows
 * would make a refusal's message depend on what the UI happened to have read, which
 * is exactly the drift that re-reading the slot exists to prevent. So the rule is
 * stated at the surface it belongs to, and these are its named exception.
 *
 * ## Why the contract seams live here now
 *
 * A contract write and a contract read were declared on `VehicleDao` in Story 2.2,
 * where they were explicitly a test-only stand-in for the DAO that did not exist
 * yet. It exists now, so they moved rather than being duplicated: one DAO owns the
 * contract table, and `VehicleDao` no longer imports `ContractEntity` at all.
 *
 * [insertContract] kept its name through the move so that "the only writer of the
 * contract table" stays one greppable fact — see its own KDoc. [allContracts] is
 * distinct from [observeAll] in ordering alone, and both are kept because the
 * invariant tests read the ascending one and the list screen reads the descending
 * one; collapsing them would silently change what those tests observe.
 */
@Dao
interface ContractDao {

    // ------------------------------------------------------------------ reads

    /**
     * Every contract, as a [Flow].
     *
     * A `Flow`, not a one-shot read, and there is no manual invalidation signal
     * anywhere in the app: Room's own tracker re-emits on write, so a contract
     * opened by the form appears in the list without a refresh (the AC), and a
     * second mechanism could only ever disagree with this one.
     *
     * Ordered by `startDate` descending so the newest rental is first — the one the
     * owner just opened is the one they look for. `startDate` is ISO `yyyy-MM-dd`
     * TEXT, so lexicographic order equals chronological order and no `date()`
     * conversion is needed; `id` breaks the tie so the order is total and a row
     * cannot jump when an unrelated contract is written.
     */
    @Query("SELECT * FROM contract ORDER BY startDate DESC, id ASC")
    fun observeAll(): Flow<List<ContractEntity>>

    @Query("SELECT * FROM contract WHERE id = :id")
    suspend fun findById(id: String): ContractEntity?

    /**
     * The contract whose *active* vehicle column holds [vehicleId], or `null`.
     *
     * ## Why this reads `activeVehicleId` and not `endDate`
     *
     * `activeVehicleId IS NOT NULL` and `endDate IS NULL` are the same condition by
     * construction — `ContractEntity.init` pins the two together — but only the
     * active column has a `UNIQUE` index behind it. Asking the question the index
     * answers means this lookup is an index seek rather than a table scan, and it
     * means the pre-check and the guarantee are asking about the same column. A
     * query on `endDate` would be a second definition of "open" that could drift
     * from the one the index enforces.
     */
    @Query("SELECT * FROM contract WHERE activeVehicleId = :vehicleId LIMIT 1")
    suspend fun findActiveByVehicleId(vehicleId: String): ContractEntity?

    /** The same for the renter side of the one-active rule. */
    @Query("SELECT * FROM contract WHERE activeRenterId = :renterId LIMIT 1")
    suspend fun findActiveByRenterId(renterId: String): ContractEntity?

    /**
     * The plate of the vehicle holding this open slot, or `null` if none does.
     *
     * Used by the refusal path to name the blocking car. Re-read *after* a
     * constraint failure so the message names a row that actually exists rather
     * than echoing the value the caller passed — the same rule
     * `VehicleRepository.insertNormalized` follows for a colliding plate.
     *
     * `plate` is normalised in the column, so the string this returns is the one
     * the owner sees in the fleet list.
     */
    @Query(
        """
        SELECT v.plate FROM contract c
        JOIN vehicle v ON v.id = c.activeVehicleId
        WHERE c.activeVehicleId = :vehicleId
        LIMIT 1
        """,
    )
    suspend fun findOpenPlateFor(vehicleId: String): String?

    /**
     * The name of the renter holding this open slot, or `null` if none does.
     *
     * The renter-side twin of [findOpenPlateFor], and the value the
     * "already renting" message names. Names are not normalised, so this is the
     * exact stored string.
     */
    @Query(
        """
        SELECT r.name FROM contract c
        JOIN renter r ON r.id = c.activeRenterId
        WHERE c.activeRenterId = :renterId
        LIMIT 1
        """,
    )
    suspend fun findOpenRenterNameFor(renterId: String): String?

    /**
     * Every contract row, as a [Flow], in a stable order.
     *
     * Read-only observation used to assert contract-row invariants — Story 2.3's
     * "marking servis leaves the contract row byte-for-byte untouched" test
     * queries through this, and a later story can rely on the same stable order
     * when it adds its own invariants. `startDate` is ISO `yyyy-MM-dd` TEXT, so
     * lexicographic order equals chronological order; `id` breaks the tie.
     *
     * Distinct from [observeAll] only in ordering: this one is ascending, which is
     * what the invariant tests read, and it was the seam that shipped in Story 2.2.
     * Collapsing the two would silently change what those tests observe.
     */
    @Query("SELECT * FROM contract ORDER BY startDate ASC, id ASC")
    fun allContracts(): Flow<List<ContractEntity>>

    // ----------------------------------------------------------------- writes

    /**
     * Inserts, letting the two `UNIQUE` indexes raise.
     *
     * `OnConflictStrategy.ABORT` is Room's default and is stated explicitly because
     * it is the load-bearing choice here. `REPLACE` would *delete* the conflicting
     * row and insert this one instead — which would let a race silently replace a
     * running contract, taking its frozen rate and its start date with it, and it
     * would leave the previous contract's allocations pointing at a contract that
     * no longer exists.
     *
     * Named `insertContract` rather than the bare `insert` the other two DAOs use:
     * this is the seam Story 2.2 declared on `VehicleDao` as a test-only stand-in,
     * and it moved here with its name so that "the contract table has no production
     * writer other than this one" stays a single greppable fact. A bare `insert`
     * would read as a fourth DAO's generic write and lose that.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertContract(contract: ContractEntity)

    /** Edits in place, matched on the primary key. Story 2.7 is the first caller. */
    @Update
    suspend fun update(contract: ContractEntity)
}
