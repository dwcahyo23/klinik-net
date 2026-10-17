package net.klinik.rental.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.klinik.rental.data.db.entity.BusinessAccountEntity

/**
 * Reads and writes for `BUSINESS_ACCOUNT`.
 *
 * ## Why there is no delete method, and why this KDoc says so at length
 *
 * Story 3.1's acceptance criterion says an account that a deposit uses cannot be deleted,
 * enforced **in the data layer**. The table that would be "in use" — `PAYMENT` — is Story
 * 3.2's subject and does not exist yet, so in this story nothing can reference an account
 * and every account is trivially unused.
 *
 * A `delete` shipped now would therefore carry a guard whose condition is **vacuously
 * true**: it would pass every test, look complete, and refuse nothing. That is precisely
 * the "the control exists but is guarded by nothing" shape this project's reviews have
 * caught more than once, and shipping the appearance of a rule is worse than shipping
 * neither the rule nor its appearance — the first is a lie a reviewer has to disprove, the
 * second is a known gap.
 *
 * So there is no delete here, no delete on `BusinessAccountRepository`, and no delete
 * control on the screen. The foreign key and the refusal land with `PAYMENT.accountId` in
 * Story 3.2, where a real referencing row exists to refuse against. If you are reading this
 * because you came to add one "for symmetry with `VehicleDao`": the symmetry is the bug.
 *
 * ## Why `observeAll` orders by bank first
 *
 * `ORDER BY bankName ASC, number ASC`. The owner scans for **which bank** first — "the
 * transfer came into BCA" — and the list is short enough that a second sort key is only
 * there to make the order total, so a row cannot jump when an unrelated one is written.
 *
 * The bank name is free text, so this is a lexicographic sort on the owner's own spelling
 * rather than a canonical bank order. That is deliberate: any other ordering would have to
 * know which banks exist, which is the closed list this story refused.
 *
 * ## Why the writes are named rather than bare `insert`/`update`
 *
 * So "the only writers of this table" stays one greppable fact — the argument
 * `ContractDao.insertContract` and `ServiceExceptionDao.insertServiceException` both make.
 * A bare `insert` would read as a fourth DAO's generic write and lose that.
 *
 * ## Why there is no `@Transaction` anywhere
 *
 * Every operation here touches exactly one row, so there is nothing to isolate. The
 * repository owns the only multi-step concern (insert-vs-update, which reads before it
 * writes) and runs it through its own `inTransaction`, exactly as `VehicleRepository` does.
 */
@Dao
interface BusinessAccountDao {

    // ------------------------------------------------------------------ reads

    /**
     * Every account, as a [Flow], bank first.
     *
     * A `Flow`, not a one-shot read, and there is no manual invalidation signal anywhere in
     * the app: Room's own tracker re-emits on write, so an account added or edited appears
     * in the list without a refresh, and a second mechanism could only ever disagree with
     * this one.
     */
    @Query("SELECT * FROM business_account ORDER BY bankName ASC, number ASC")
    fun observeAll(): Flow<List<BusinessAccountEntity>>

    /**
     * The account with [id], or `null`.
     *
     * The repository's insert-vs-update decision reads this first, which is what makes
     * `save` one method rather than two the UI has to choose between.
     */
    @Query("SELECT * FROM business_account WHERE id = :id")
    suspend fun findById(id: String): BusinessAccountEntity?

    // ----------------------------------------------------------------- writes

    /**
     * Inserts, letting a primary-key clash raise.
     *
     * `OnConflictStrategy.ABORT` is Room's default and is stated explicitly because it is
     * the load-bearing choice: `REPLACE` would *delete* the conflicting row and insert this
     * one instead, which for an account means a row the owner created and a deposit may
     * later reference would be silently replaced by a different one.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBusinessAccount(account: BusinessAccountEntity)

    /**
     * Edits in place, matched on the primary key.
     *
     * An edit is what keeps the account's identity: a deposit references the id, so
     * changing the number here does not reach a recorded deposit — the account is
     * referenced rather than copied, which is the acceptance criterion this method exists
     * to make true.
     */
    @Update
    suspend fun updateBusinessAccount(account: BusinessAccountEntity)
}
