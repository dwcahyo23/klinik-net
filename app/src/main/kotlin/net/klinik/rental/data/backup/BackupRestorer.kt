package net.klinik.rental.data.backup

import androidx.room.withTransaction
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.SCHEMA_VERSION
import net.klinik.rental.data.db.dao.AllocationDao
import net.klinik.rental.data.db.dao.BusinessAccountDao
import net.klinik.rental.data.db.dao.ContractDao
import net.klinik.rental.data.db.dao.PaymentDao
import net.klinik.rental.data.db.dao.RenterDao
import net.klinik.rental.data.db.dao.ServiceExceptionDao
import net.klinik.rental.data.db.dao.SettingDao
import net.klinik.rental.data.db.dao.VehicleDao
import java.time.LocalDate

/**
 * Story 5.2's write half: one parsed file in, a database replaced atomically.
 *
 * ## Why this is two functions and not one
 *
 * [parse] and [apply] are separate because the owner decides between them. A single
 * `restore(json)` would have to either show a preview from a file it had already written, or
 * parse the file twice — and the second parse could disagree with the first. Splitting them means
 * the preview the owner confirms and the rows that are written are **the same object**.
 *
 * ## Why the whole thing is one transaction
 *
 * Because a half-restored database is worse than an untouched one. If the eighth insert fails
 * after the seventh succeeded, the owner is left with a database holding a partial file — which
 * renders as a real business with missing records, indistinguishable from data loss. One
 * transaction means the only two outcomes are "the old data" and "the new data".
 *
 * ## Why `clearAllTables()` rather than eight `DELETE` queries
 *
 * Because the foreign keys are `RESTRICT` and that reads like it forbids a wholesale delete. It
 * does not: SQLite evaluates a constraint only for rows it actually modifies, and `DELETE FROM t`
 * removes rows without modifying any — the same mechanism Room's own cascade bookkeeping relies
 * on. Eight per-table deletes would be eight methods with a real ordering requirement the schema
 * does not impose.
 *
 * ## Why the insert order is the foreign-key order
 *
 * vehicle, renter, business_account → contract → service_exception → payment → allocation →
 * settings. `allocation.contractId` has **no** foreign key to `contract` — it is a plain column —
 * so it constrains nothing, and a reader that assumed otherwise would be encoding a constraint
 * the schema does not have. `settings` has no parents and could go anywhere; it goes last because
 * a failure there is the least destructive thing to discover.
 *
 * ## Why every row goes through the DAO's single-row insert
 *
 * Because those are the only writes that exist, and because the entities' own `init` guards run
 * on the way in. A raw `INSERT` would be faster and would bypass exactly the checks that make a
 * malformed file a refusal rather than a corrupt database.
 */
class BackupRestorer(
    private val database: KlinikDatabase,
    private val vehicleDao: VehicleDao,
    private val renterDao: RenterDao,
    private val contractDao: ContractDao,
    private val serviceExceptionDao: ServiceExceptionDao,
    private val businessAccountDao: BusinessAccountDao,
    private val paymentDao: PaymentDao,
    private val allocationDao: AllocationDao,
    private val settingDao: SettingDao,
) {

    /**
     * Reads [json] and refuses anything this build cannot restore.
     *
     * The version gate lives in [BackupJsonReader], so this is a one-line delegation rather than
     * a second place the rule is stated — see that object's KDoc for why the check happens before
     * any row is handed to a caller.
     *
     * @param asOf the caller's "today", forwarded to the reader's future-date refusal. Passed
     *   through rather than read here so the rule lives in one place and a test pins the day.
     * @throws RestoreRejected when the file is not JSON, is missing a table, holds a row the
     *   entities refuse, carries a different `schemaVersion`, or ends a contract after [asOf].
     */
    fun parse(json: String, asOf: LocalDate): BackupJsonReader.Restore =
        BackupJsonReader.read(json, SCHEMA_VERSION, asOf)

    /**
     * Replaces the whole database with [restore], atomically.
     *
     * ## Why there is no return value
     *
     * Because the only two outcomes are success and a thrown exception, and a `Boolean` would
     * invite a caller to ignore it. A failed insert throws out of the transaction, the transaction
     * rolls back, and the ViewModel reports it — the same shape every other repository write here
     * takes.
     */
    suspend fun apply(restore: BackupJsonReader.Restore) {
        database.withTransaction {
            // The old data goes first, and inside the same transaction, so a failure below leaves
            // it untouched rather than leaving an empty database and a partial file.
            database.clearAllTables()

            restore.vehicles.forEach { vehicleDao.insert(it) }
            restore.renters.forEach { renterDao.insert(it) }
            restore.businessAccounts.forEach { businessAccountDao.insertBusinessAccount(it) }

            // Contracts need both parents; service exceptions and payments need the contract.
            restore.contracts.forEach { contractDao.insertContract(it) }
            restore.serviceExceptions.forEach { serviceExceptionDao.insertServiceException(it) }
            restore.payments.forEach { paymentDao.insertPayment(it) }

            // Allocations need their payment. Copied as stored — never re-derived, because
            // `previewAllocation` replays against *today's* cutoff and would move a March
            // deposit onto different days.
            restore.allocations.forEach { allocationDao.insertAllocation(it) }

            // `putSetting` is the one `@Upsert` in the app, so a settings key present in both the
            // old database and the file is replaced rather than colliding. The rows here have
            // already been filtered against `NEVER_EXPORTED` by the reader.
            restore.settings.forEach { settingDao.putSetting(it) }
        }
    }
}