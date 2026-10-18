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

    /**
     * Every contract id that has an allocation, as a [Flow].
     *
     * The allocation table's half of Story 4.1's invalidation triggers -- see
     * `PaymentDao.observeContractIds` for why a flow over `contract` alone is not enough.
     * A save writes `payment` and `allocation` in one transaction, so both must be named or
     * the arrears list would go stale on the write it exists to reflect.
     */
    @Query("SELECT DISTINCT contractId FROM allocation")
    fun observeContractIds(): Flow<List<String>>

    /**
     * Every allocation row on every contract.
     *
     * Story 5.1's backup read, and the reason this query exists at all: every other read here
     * is scoped to one contract, because the app's own arithmetic is always asked about a
     * contract. A backup is the one read that is not — it takes the whole table, and a
     * per-contract loop over `observeContracts()` would be eight hundred queries for the
     * same rows.
     *
     * **No `ORDER BY` here on purpose.** The backup writer sorts by primary key, so the
     * file's bytes depend on the data and not on which screen last happened to change this
     * DAO's ordering. An `ORDER BY` would add a second answer to "what order is this table
     * in", and only one of them would reach the file.
     */
    @Query("SELECT * FROM allocation")
    suspend fun all(): List<AllocationEntity>

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

    /**
     * Releases every row one deposit placed, in Story 3.4's cancellation.
     *
     * ## What is deleted here and what is deleted nowhere
     *
     * These rows, and **only** these. They encode the deposit's *effect* on arrears —
     * which days it closed and how much of each — and cancelling reverses exactly that.
     * The deposit row itself is never deleted: the epic's criterion is *"tidak ada hard
     * delete"*, because a row that cannot be audited cannot explain why the owner's arrears
     * jumped on a given day, which is the question a cancellation exists to answer.
     *
     * ## Why this is not `onDelete = CASCADE` doing it for us
     *
     * Because a cascade would fire the moment the **payment** row went, and this
     * cancellation does not delete that row. Deleting placements is a deliberate step with
     * a re-allocation after it — the same transaction, in that order — and a cascade would
     * do it silently, at a moment the code does not choose.
     */
    @Query("DELETE FROM allocation WHERE paymentId = :paymentId")
    suspend fun deleteForPayment(paymentId: String): Int

    /**
     * Removes **every** allocation on a contract, for the re-allocation that follows a
     * cancellation.
     *
     * Wholesale rather than row-by-row because the re-allocation rewrites placements for
     * *all* deposits, not only the cancelled one: releasing Rp 500.000 from a contract with
     * three later deposits can shift each of them onto an earlier day. `previewAllocation`
     * returns the survivor's rows and the repository replaces them wholesale.
     */
    @Query("DELETE FROM allocation WHERE contractId = :contractId")
    suspend fun deleteForContract(contractId: String): Int
}