package net.klinik.rental.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.klinik.rental.data.db.entity.AllocationEntity

/**
 * Reads and writes for `ALLOCATION`.
 *
 * ## Why the writes are per-row and not a batch
 *
 * Because the repository writes the whole set from one `allocatePayments` call and the set is
 * at most `payments.size + 1` rows — one per day a deposit touches, plus its credit row. A batch
 * `@Insert` would gain nothing measurable and cost a transaction boundary of its own inside a
 * transaction the repository already owns. What matters is that the *repository* treats the
 * set as one unit, and this DAO deliberately does not: it moves rows and lets the caller own
 * the atomicity, which is the `ContractDao`/`VehicleDao` division too.
 *
 * ## Why there is no delete
 *
 * Story 3.4's, when a deposit is cancelled: the allocation rows are deleted and every
 * remaining allocation is recomputed from the deposits that survive. A delete method here
 * without that recomputation would raise arrears for one contract and leave the others
 * inconsistent — which is why 3.4 rewrites the set rather than deleting one row.
 */
@Dao
interface AllocationDao {

    // ------------------------------------------------------------------ reads

    /**
     * Every allocation row on one contract, ordered by day.
     *
     * The ordering puts the credit row (`chargeDate = null`) **last**, which is what makes
     * it readable as the remainder: a reader scanning the list sees the applied days, then the
     * money that is not yet on any of them. `arrearsOf` does not care about the order — it
     * buckets by date — so this is for a human and for the future arrears list.
     *
     * SQLite sorts `NULL` first ascending, so the ordering is written explicitly rather than
     * left to the default: `ORDER BY chargeDate ASC` would put credit *first*, which reads as
     * the deposit having been applied before anything else.
     */
    @Query(
        """
        SELECT * FROM allocation
        WHERE contractId = :contractId
        ORDER BY chargeDate IS NULL, chargeDate ASC, id ASC
        """,
    )
    suspend fun forContract(contractId: String): List<AllocationEntity>

    /**
     * The same rows as a [Flow], for the arrears screens in Epic 4.
     *
     * A deposit must move the arrears number the moment it lands, with no refresh anywhere in
     * the app — so the read has to be subscribable.
     */
    @Query(
        """
        SELECT * FROM allocation
        WHERE contractId = :contractId
        ORDER BY chargeDate IS NULL, chargeDate ASC, id ASC
        """,
    )
    fun observeForContract(contractId: String): Flow<List<AllocationEntity>>

    /** The credit rows for one contract — the `chargeDate IS NULL` remainder. */
    @Query("SELECT * FROM allocation WHERE contractId = :contractId AND chargeDate IS NULL")
    suspend fun creditRowsFor(contractId: String): List<AllocationEntity>

    // ----------------------------------------------------------------- writes

    /**
     * Inserts one allocation row.
     *
     * `ABORT` is stated explicitly for the same reason as on `payment`: `REPLACE` would delete
     * the conflicting row, and a duplicate `(paymentId, chargeDate)` is precisely the corruption
     * the unique index exists to prevent — turning it into a silent overwrite would make the
     * index decorative.
     *
     * Named rather than a bare `insert`, so the table's writers stay one greppable fact.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocation(allocation: AllocationEntity)
}