package net.klinik.rental.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.klinik.rental.data.db.entity.PaymentEntity
import java.time.LocalDate

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

    /**
     * Every contract id that has a deposit, as a [Flow].
     *
     * Added by Story 4.1 for the arrears list, and it exists because of a **Room
     * invalidation** fact rather than any need for the data: a `Flow` built on
     * `ContractDao.observeAll()` re-emits when the **contract** table is written and not
     * when a deposit lands, because a deposit writes `payment` and `allocation` only. A
     * list fed by that flow would show a stale figure after every save — which is exactly
     * the "no manual refresh" rule failing while looking implemented.
     *
     * So the list combines this with the allocation and exception flows, which between them
     * name every table the figure is derived from. The rows themselves are not used: this is
     * a **table-level invalidation trigger**, and a query that reads a real column would be
     * one more thing to keep in step for no benefit.
     */
    @Query("SELECT DISTINCT contractId FROM payment")
    fun observeContractIds(): Flow<List<String>>

    /**
     * Every deposit on **every** contract, newest first — the Setoran screen's cross-contract
     * history.
     *
     * ## Why this query exists despite the class KDoc's per-contract rule
     *
     * Because that rule is about the *allocator's* reads: a global list there would invite
     * grouping by hand, which is the mistake `Allocation`'s KDoc warns about. This read is
     * different in kind — it is an **audit listing**, not an arithmetic input, and its rows
     * go to a projection that already carries `contractId` per row
     * ([net.klinik.rental.data.repository.DepositHistoryRow]). The grouping lives on the row,
     * so no caller is invited to regroup.
     *
     * ## Why no status filter
     *
     * The same rule [observeForContract]'s section gives: the status filter belongs to the
     * allocator, not to a reader, and a history that hides a cancellation undoes the
     * audit trail Story 3.4 kept the row for.
     *
     * ## Why newest first
     *
     * Because the question this list answers is "what came in recently", which is the
     * opposite reading direction from a per-contract bank statement. The order comes from
     * the query rather than a sort in the UI, so neither list can disagree with its own
     * data.
     */
    @Query("SELECT * FROM payment ORDER BY date DESC, id DESC")
    fun observeAllForHistory(): Flow<List<PaymentEntity>>

    /**
     * The confirmed income of every day that has any, grouped by date — Story 4.3's
     * "Pendapatan hari ini".
     *
     * ## Why this groups by date rather than filtering to one
     *
     * Because a query with a `WHERE date = :date` parameter **freezes the date in the
     * subscription**: Room re-runs it with the same bound value when the table changes, so a
     * deposit landing after midnight would be summed against yesterday. That is the same
     * frozen-clock defect Story 4.1's review found in the arrears read, and the fix is the
     * same shape — the clock is read where the figure is *chosen*, not where the query is
     * built.
     *
     * Grouping by date hands the choice to Kotlin: one row per day, and the repository picks
     * today's row with a clock read on every emission. A write at 00:05 therefore counts
     * against the new day, on the same subscription, with no second mechanism.
     *
     * ## Why the status filter lives in the query
     *
     * Because a cancelled deposit is not income. It is kept for its audit trail and no longer
     * counts against arrears, so counting it here would put a number on the morning screen that
     * the rest of the app has already agreed is void — and the two would disagree the moment
     * a cancellation landed.
     *
     * The filter is in the SQL rather than in Kotlin for the same reason
     * [observeContractIds] reads no real column: this is read **per emission**, and a
     * Kotlin-side filter would have to be applied to a list that had already been fetched.
     */
    @Query(
        """
        SELECT date, SUM(amount) AS total FROM payment
        WHERE status = 'confirmed'
        GROUP BY date
        """,
    )
    fun observeIncomeByDate(): Flow<List<DailyIncome>>
}

/**
 * One day's confirmed income, as the grouped query returns it.
 *
 * A projection rather than an entity because the query returns two columns and no row of
 * its own — and a `@Query` with two JOINs would need a row POJO anyway, which the epic
 * context names as the cost of a join. Here there is no join, and the projection is the
 * smallest thing that can carry the figure.
 *
 * @property date the day the money arrived, as a billing fact.
 * @property total the confirmed rupiah received that day, in whole units.
 */
data class DailyIncome(
    val date: LocalDate,
    val total: Long,
)