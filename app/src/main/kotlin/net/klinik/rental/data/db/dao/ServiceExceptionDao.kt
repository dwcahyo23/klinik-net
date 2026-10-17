package net.klinik.rental.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.klinik.rental.data.db.entity.ServiceExceptionEntity
import java.time.LocalDate

/**
 * Reads and writes for `SERVICE_EXCEPTION`.
 *
 * ## Why this DAO does no validation
 *
 * A DAO that "helpfully" checked whether a contract already carried an exception
 * for a date would be a second place the duplicate rule lives, and the place that
 * loses the race. The rule is the `UNIQUE(contractId, date)` index, and
 * `ContractRepository` is where its refusal gets an exception type and an
 * Indonesian message. This layer only moves rows and lets SQLite say no.
 *
 * ## Why [observeAll] is **ascending**
 *
 * `ORDER BY date ASC, id ASC` — the opposite of `ContractDao.observeAll`'s
 * `startDate DESC`. That is deliberate and the two lists have different jobs.
 *
 * The contract list puts the newest *rental* first, because the owner is looking
 * for the contract they just opened. The exception list is a billing statement read
 * in chronological order: "which days were not charged, in order" is the question
 * being answered when someone reconciles an invoice against a bank mutation, and
 * reversing it would make that reading harder rather than easier. `id` breaks the
 * tie so the order is total and a row cannot jump when an unrelated one is written.
 *
 * `date` is ISO `yyyy-MM-dd` TEXT, so lexicographic order equals chronological order
 * and no `date()` conversion is needed — see `Converters`.
 *
 * ## Why there is no `@Transaction` anywhere on this interface
 *
 * The exception writes are *not* single-row-and-done: adding one reads the contract
 * to decide whether it has ended, pre-checks the duplicate, and then writes — a
 * read-check-write that must see one snapshot, or two callers could both pass the
 * checks before either wrote. That is a transaction, and it belongs to the
 * repository, which owns the database handle. `ContractDao` has no `@Transaction`
 * for the same reason; see `ContractRepository.inTransaction`.
 */
@Dao
interface ServiceExceptionDao {

    // ------------------------------------------------------------------ reads

    /**
     * Every exception, as a [Flow], oldest first.
     *
     * A `Flow`, not a one-shot read, and there is no manual invalidation signal
     * anywhere in the app: Room's own tracker re-emits on write, so an exception
     * added from the sheet appears in the sheet's own list without a refresh, and a
     * second mechanism could only ever disagree with this one.
     */
    @Query("SELECT * FROM service_exception ORDER BY date ASC, id ASC")
    fun observeAll(): Flow<List<ServiceExceptionEntity>>

    /**
     * Every exception on one contract, for the charge-day calculation.
     *
     * Added by Story 3.2, which is the first caller that needs the exceptions as a *set*:
     * building a `ChargeSchedule` for a contract means reading exactly that contract's
     * excused days rather than every stored exception and filtering in Kotlin.
     *
     * The `UNIQUE(contractId, date)` index covers this as a prefix seek, so it is not a scan
     * — which is why no index is needed for it.
     */
    @Query("SELECT * FROM service_exception WHERE contractId = :contractId ORDER BY date ASC")
    suspend fun forContract(contractId: String): List<ServiceExceptionEntity>

    /**
     * The exception on [date] for this contract, or `null`.
     *
     * The courtesy pre-check's only caller — `ContractRepository` asks this before
     * it writes so the owner finds out while the sheet is still open, and again
     * *inside* the catch that translates the index's refusal, so the message names a
     * row that actually exists. Asking about one contract's one day is exactly the
     * shape the `UNIQUE(contractId, date)` index answers, so this is an index seek
     * rather than a scan.
     */
    @Query(
        """
        SELECT * FROM service_exception
        WHERE contractId = :contractId AND date = :date
        LIMIT 1
        """,
    )
    suspend fun findByContractAndDate(contractId: String, date: LocalDate): ServiceExceptionEntity?

    // ----------------------------------------------------------------- writes

    /**
     * Inserts, letting the `UNIQUE(contractId, date)` index raise.
     *
     * `OnConflictStrategy.ABORT` is Room's default and is stated explicitly because
     * it is the load-bearing choice: `REPLACE` would *delete* the conflicting row
     * and insert this one instead, which would silently overwrite an exception the
     * owner already recorded — replacing one reason with another and changing what
     * the owner was told the day was not for, with no refusal at all.
     *
     * Named `insertServiceException` rather than a bare `insert`, for the reason
     * `ContractDao.insertContract` gives: "the only writer of this table" has to
     * stay one greppable fact. A bare `insert` would read as a fourth DAO's generic
     * write and lose that.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertServiceException(exception: ServiceExceptionEntity)

    /**
     * Deletes one row, matched on **both** the id and its contract.
     *
     * A `@Query` rather than Room's `@Delete` annotation, which matches on the
     * primary key alone and therefore cannot express the second predicate. The
     * contract id is in the `WHERE` on purpose, and it is the rule rather than a
     * nicety: a row id is not something `ui/` can reach without a lookup, so a
     * delete keyed on the id alone is a delete any caller can aim at any contract
     * it happens to hold a row id for. `ContractRepository.removeServiceException`
     * passes the contract the sheet was opened for, so the write is confined to it.
     *
     * Named `deleteServiceExceptionById` for the same reason as
     * [insertServiceException]: the table's writer stays one greppable fact.
     */
    @Query("DELETE FROM service_exception WHERE id = :id AND contractId = :contractId")
    suspend fun deleteServiceExceptionById(id: String, contractId: String)
}
