package net.klinik.rental.data.backup

import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.klinik.rental.data.db.SCHEMA_VERSION
import net.klinik.rental.data.db.dao.AllocationDao
import net.klinik.rental.data.db.dao.BusinessAccountDao
import net.klinik.rental.data.db.dao.ContractDao
import net.klinik.rental.data.db.dao.PaymentDao
import net.klinik.rental.data.db.dao.RenterDao
import net.klinik.rental.data.db.dao.ServiceExceptionDao
import net.klinik.rental.data.db.dao.SettingDao
import net.klinik.rental.data.db.dao.VehicleDao
import net.klinik.rental.data.db.entity.SettingEntity

/**
 * What an export produced: the text to write, and the name the owner sees.
 *
 * A pair rather than two return values because the two cannot be independently wrong: a
 * repository that returned a filename from somewhere else would let the picker suggest one name
 * and the file record another, and the owner would discover it by finding a backup dated
 * yesterday's export.
 */
data class BackupDocument(
    /** `klinik-net-backup-2026-10-19.json`, pre-filled into the system picker. */
    val filename: String,
    /** The whole JSON document. */
    val json: String,
)

/**
 * Story 5.1's export read: eight tables out, one file's worth of text in.
 *
 * ## Why this reads and never writes
 *
 * Because the epic's criterion is explicit — *"data di aplikasi tetap utuh; ekspor tidak menghapus
 * apa pun"* — and because the only way that stays true structurally is if the exporter has no
 * write method at all. An exporter with a `suspend fun clear()` would be one careless caller away
 * from the one operation this app must never perform by accident. Restoration (Story 5.2) writes;
 * this does not, and the asymmetry is the safety property.
 *
 * ## Why six reads reuse a `Flow` and two are new queries
 *
 * Six tables already have a whole-table reader, and `observeAll().first()` on a Room flow is one
 * query rather than a workaround — the flow emits on subscription, so `first()` returns exactly
 * once. `AllocationDao` and `SettingDao` had no reader scoped to the whole table, so each got
 * one. Adding `all()` to the other six would have put a second whole-table spelling beside the
 * first on six tables and given Story 5.2 a choice of two that agree today.
 *
 * ## Why the filename's date comes from the injected [Clock]
 *
 * Because AD-8 makes `LocalDate.now()` reachable only through the `Clock`, and a filename is
 * exactly the place a hardcoded date would be least visible and most annoying: every backup from
 * 2026 would be called the same thing, and the criterion's "several backups must be tellable
 * apart" would be satisfied by nothing.
 *
 * Two exports on the **same** day get the same name, and that is not this class's problem to
 * solve: the Storage Access Framework asks the owner before replacing an existing file, which is
 * a better answer than an invented suffix the owner would have to decode.
 */
class BackupRepository(
    private val vehicleDao: VehicleDao,
    private val renterDao: RenterDao,
    private val contractDao: ContractDao,
    private val serviceExceptionDao: ServiceExceptionDao,
    private val businessAccountDao: BusinessAccountDao,
    private val paymentDao: PaymentDao,
    private val allocationDao: AllocationDao,
    private val settingDao: SettingDao,
    private val clock: Clock,
) {

    /**
     * The whole database, as one document.
     *
     * Reads are **sequential, not concurrent**: eight independent queries on one SQLite
     * connection gain nothing from parallelism and would make the file's contents depend on
     * which query finished first — a deposit written between two of them would appear half in
     * the file and half outside it.
     */
    suspend fun build(): BackupDocument {
        // The last export date is written **here**, beside the act that earned it, rather than
        // in the ViewModel's success branch. The two write the same string and differ in one
        // way that matters: this runs when the export succeeds, a message is written after it
        // returns, and a process death between the two loses the record of a backup that
        // exists. Story 5.5's reminder compares against this, so a missed write is a banner
        // that nags an owner who already backed up.
        //
        // One `putSetting`, in the same read-then-write shape as `build()`'s eight reads. Not
        // in a transaction: there is nothing to be atomic with, and `withTransaction` asserts
        // it is off the main thread, which a test that only reads the document would trip.
        settingDao.putSetting(
            SettingEntity(
                key = KEY_LAST_BACKUP_DATE,
                value = LocalDate.now(clock).toString(),
            ),
        )

        return BackupDocument(
            filename = filenameFor(LocalDate.now(clock)),
            json = BackupJson.build(
                vehicles = vehicleDao.observeAll().first(),
                renters = renterDao.observeAll().first(),
                contracts = contractDao.observeAll().first(),
                serviceExceptions = serviceExceptionDao.observeAll().first(),
                businessAccounts = businessAccountDao.observeAll().first(),
                payments = paymentDao.observeAllForHistory().first(),
                allocations = allocationDao.all(),
                settings = settingDao.all(),
                schemaVersion = SCHEMA_VERSION,
            ),
        )
    }

    /**
     * How many deposits exist at all - Story 5.5's "does this owner have anything to lose".
     *
     * A **deposit**, not a general row count, on purpose. A vehicle added yesterday can be
     * retyped in a minute; six months of deposits is what "ini satu-satunya salinan data" is
     * true of, and it is what the reminder protects. Counting vehicles would remind an owner
     * with a full fleet and no rentals that their backup is stale, which is not the same
     * warning.
     *
     * A [Flow] rather than a suspend count because the answer has to **change**: the owner
     * records their first deposit in another screen, returns, and the reminder must appear
     * without reopening the app. A count read once at construction would nag an owner who
     * had not yet done anything.
     */
    fun observeDepositCount(): Flow<Int> =
        paymentDao.observeAllForHistory().map { rows -> rows.size }

    /**
     * The date of the last export, or `null` when the owner has never exported.
     *
     * A **date**, not a boolean, because Story 5.5's banner names it: a last date is a fact
     * the owner reads, and a boolean could only answer today's question. Today's comparison is
     * then a derivation rather than a second stored fact.
     */
    suspend fun lastBackupDate(): LocalDate? =
        settingDao.findValue(KEY_LAST_BACKUP_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * The filename an export on [date] is offered under.
     *
     * Separate from [build] and pure, so the "the name carries the date" criterion is a
     * one-line assertion rather than a test that has to run a whole export to look at a string.
     */
    fun filenameFor(date: LocalDate): String = "klinik-net-backup-$date.json"

    companion object {
        /**
         * The settings key holding the last export date.
         *
         * Deliberately **not** in `BackupJson.NEVER_EXPORTED`: a last-export date is a
         * preference the owner benefits from having restored onto a new device — it is not a
         * secret, and dropping it would make the first reminder on a restored install fire
         * against a backup the owner demonstrably already has.
         */
        const val KEY_LAST_BACKUP_DATE = "lastBackupDate"

        /**
         * The hour after which the reminder may appear, when the owner has not chosen one.
         *
         * 09:00: after the day's deposits are usually recorded and before the owner has left
         * the office. See the spec's Design Notes for why this is a setting rather than a
         * constant — the same reminder is noise at 07:00 for a bookkeeper and too late at 22:00
         * for someone who runs the books each morning.
         */
        const val DEFAULT_NUDGE_HOUR = 9
    }
}
