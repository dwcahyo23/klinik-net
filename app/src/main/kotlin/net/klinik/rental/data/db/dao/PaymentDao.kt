package net.klinik.rental.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.klinik.rental.data.db.entity.PaymentEntity

/**
 * Reads and writes for `PAYMENT`.
 *
 * ## Why there is no delete and no update
 *
 * Both are Story 3.4's, and the shape is settled before it arrives. AD-3 cancels a deposit by
 * deleting its allocation rows and recomputing the contract from what is left — so a cancelled
 * payment is *removed*, not flagged, and an update method here would invite a caller to change
 * an amount without recomputing the allocations, which is the one operation this schema cannot
 * survive. Story 3.2 records a deposit; it does not change one.
 *
 * ## Why reads are per-contract rather than a single global list
 *
 * Because arrears is a per-contract figure, and `PaymentRepository.saveDeposit` needs the
 * contract's *existing* deposits to feed the allocator — the whole of what has already been
 * applied. A global `observeAll` would make the caller group by contract, which is exactly the
 * grouping mistake `Allocation`'s KDoc warns about when it says `contractId` is absent from the
 * domain value.
 */
@Dao
interface PaymentDao {

    // ------------------------------------------------------------------ reads

    /**
     * Every deposit on one contract, oldest first.
     *
     * The allocator takes this list and closes the **oldest open day first**, so the ordering is
     * a convenience rather than a correctness requirement — it sorts internally. What the
     * ordering *is* good for is a human reading the list: deposits dated 14 Oct appear before
     * 16 Oct, matching how a bank statement reads.
     */
    @Query("SELECT * FROM payment WHERE contractId = :contractId ORDER BY date ASC, id ASC")
    suspend fun forContract(contractId: String): List<PaymentEntity>

    /**
     * The same list as a [Flow], for the arrears screens in Epic 4.
     *
     * Added alongside [forContract] rather than replacing it: the save path needs a one-shot
     * read it can hand straight to the allocator, and the arrears screens need a
     * subscription. A `Flow` the save path has to `first()` puts a coroutine on a path that
     * should not have one.
     */
    @Query("SELECT * FROM payment WHERE contractId = :contractId ORDER BY date ASC, id ASC")
    fun observeForContract(contractId: String): Flow<List<PaymentEntity>>

    /**
     * How many deposits reference [accountId].
     *
     * This is **Story 3.1's deferred delete refusal**, arriving with the rows that can make an
     * account "in use". The `RESTRICT` on `payment.accountId` enforces it at the database; this
     * count exists so the refusal can *name* what holds the account, which a constraint failure
     * cannot do.
     */
    @Query("SELECT COUNT(*) FROM payment WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: String): Int

    // ----------------------------------------------------------------- writes

    /**
     * Inserts one deposit.
     *
     * `OnConflictStrategy.ABORT` is Room's default and is stated explicitly because it is
     * load-bearing: `REPLACE` would *delete* the conflicting row and insert this one instead,
     * taking the deposit's allocation rows with it — money the owner took, erased by a
     * duplicate id.
     *
     * Named rather than a bare `insert`, so "the only writer of this table" stays one
     * greppable fact.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: PaymentEntity)

    /**
     * Writes a deposit back in place — Story 3.4's, and the only one.
     *
     * ## Why there is **no delete**
     *
     * The epic's acceptance criterion is explicit: *"pembatalan dicatat sebagai perubahan
     * status dengan alasan — tidak ada hard delete."* A deleted deposit cannot be audited,
     * which is the entire reason the reason is mandatory. What a cancellation *does* delete
     * is the deposit's allocation rows — see [AllocationDao.deleteForPayment] — because
     * those encode its effect on arrears rather than the fact that it happened.
     *
     * ## Why nothing filters on [PaymentEntity.status]
     *
     * Because that filter is the failure the domain's `Payment` KDoc names: a caller who
     * forgets pays the wrong arrears and nothing notices. So [forContract] and
     * [observeForContract] return **every** row, cancelled included — the audit trail
     * depends on it — and a cancelled deposit is excluded by the *allocator* not by a
     * query. Cancelling deletes its allocation rows and re-allocates the contract from the
     * survivors, so a cancelled deposit that reached the allocator anyway would break
     * `Σ allocation == amount` loudly rather than quietly.
     */
    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    /** One deposit by id, for the cancellation's own read. */
    @Query("SELECT * FROM payment WHERE id = :id")
    suspend fun findById(id: String): PaymentEntity?
}