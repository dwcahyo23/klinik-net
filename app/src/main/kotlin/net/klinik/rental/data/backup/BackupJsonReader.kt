package net.klinik.rental.data.backup

import net.klinik.rental.data.db.entity.AllocationEntity
import net.klinik.rental.data.db.entity.BusinessAccountEntity
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.ServiceExceptionEntity
import net.klinik.rental.data.db.entity.SettingEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Story 5.2's half of the format: [BackupJson]'s eight tables read back into entities.
 *
 * ## Why `org.json` is used here when [BackupJson] refused it to write
 *
 * The two refusals point in opposite directions, so the reasoning is worth keeping apart rather
 * than collapsing into "we rejected `org.json`".
 *
 * [BackupJson] rejected it for **writing** on two grounds: `JSONObject` is a `HashMap`, so the
 * same database would export two byte-different files; and `isReturnDefaultValues = true` makes
 * `put` a silent no-op on a plain JVM test, so a serialization bug would pass a green suite and
 * ship a wrong backup.
 *
 * Neither applies to **reading**. Key order is irrelevant for input. And Robolectric supplies a
 * real `org.json`, so the stubs are never reached in a test that has a runner — which the ones
 * that matter here all do.
 *
 * What the library *does* provide, and why it is the right choice over a hand-rolled parser for a
 * document this app's own writer produced: the strict/strict boundary is `getJSONArray` versus
 * `optJSONArray`, and it is the API's own shape rather than a discipline to maintain.
 *
 * ## Why `optJSONArray` appears **nowhere** in this file
 *
 * Because `optJSONArray` returns an empty array for a missing key, and an empty array restores as
 * "this owner has no vehicles" — which the settings screen then renders as a brand-new install.
 * The owner would have been told his restore succeeded and would be looking at an empty app. That
 * is the single most expensive failure available in this epic, and it is available *silently*.
 *
 * So every table is read with `getJSONArray`, which throws `JSONException` on a missing key, and
 * that exception becomes [RestoreRejected]. "No vehicles" and "the `vehicles` key is missing"
 * are different facts, and only the first is a decision this app is entitled to make.
 *
 * ## Why the version is checked **here** rather than in the restorer
 *
 * Because a reader that returns rows before anyone has asked whether the rows mean anything is a
 * reader that hands the restorer a database to write. The version is the one question whose
 * answer changes whether every other field can be trusted, so it is the first thing read and the
 * last thing a caller can skip.
 */
object BackupJsonReader {

    /**
     * The eight refusal reasons, as constants on the reader that raises them.
     *
     * Declared here rather than only on [RestoreRejected] so a reader's `throw` site and its
     * reason cannot drift: adding a reason means adding it in one place, and the `when` over
     * [RestoreRejected.reason] that turns one into a message then fails to compile until the UI
     * has a sentence for it.
     */
    const val REASON_NOT_JSON = "not-json"
    const val REASON_NO_VERSION = "no-version"
    const val REASON_VERSION_TOO_NEW = "version-too-new"
    const val REASON_VERSION_TOO_OLD = "version-too-old"
    const val REASON_MISSING_TABLE = "missing-table"
    const val REASON_BAD_ROW = "bad-row"

    /**
     * A contract whose end or settlement date is after today.
     *
     * ## Why restore refuses what the write paths already refuse
     *
     * `endContract` and `settleThrough` both refuse a date after today, because under
     * `isOpen = endDate == null` any written end date ends the contract on the spot, and a
     * settlement marker closes every charge day at or before it. A backup written by a build
     * before those guards existed — or on a device whose clock was ahead — carries exactly the
     * rows they now reject, and restoring it would put the app back into the state the guards
     * exist to prevent: a car reading `tersedia` for a rental that is still running, or arrears
     * that have disappeared with nobody having paid.
     *
     * ## Why the whole file is refused rather than the row repaired
     *
     * Because the alternatives are both worse. Dropping the row would restore a database with a
     * contract missing, which is data the owner never deleted. Nulling the future date would be
     * this reader inventing a contract the owner never had — the one thing the class KDoc says a
     * reader must never do. And a restored future end date has **no in-app fix**: `endContract`
     * only accepts an open contract, so there is no way to clear it afterwards.
     *
     * The cost is real and worth naming: an owner holding such a file cannot restore it. The
     * message says so plainly, and an earlier backup is the way back.
     */
    const val REASON_FUTURE_DATE = "future-date"

    /**
     * Every table from one file, plus what the preview shows.
     *
     * A single value rather than a parse and a preview as two steps, because a preview computed
     * from a *different* read than the one restored would be a preview that lies — and a preview
     * is what the owner decides on.
     */
    data class Restore(
        val schemaVersion: Int,
        val vehicles: List<VehicleEntity>,
        val renters: List<RenterEntity>,
        val contracts: List<ContractEntity>,
        val serviceExceptions: List<ServiceExceptionEntity>,
        val businessAccounts: List<BusinessAccountEntity>,
        val payments: List<PaymentEntity>,
        val allocations: List<AllocationEntity>,
        /**
         * `settings`, minus [BackupJson.NEVER_EXPORTED].
         *
         * Filtered **on read** and not only on write, so a file that carries a `pinHash` — one
         * written by an older build, or assembled by hand — cannot put a lock-screen hash into
         * the database. The writer's filter and this one are the same list for that reason: one
         * list, two directions, and a file cannot smuggle a key past the writer.
         */
        val settings: List<SettingEntity>,
    ) {
        /** What the owner sees before confirming. Derived here so it cannot drift from the rows. */
        val preview: RestorePreview = RestorePreview(
            vehicleCount = vehicles.size,
            renterCount = renters.size,
            contractCount = contracts.size,
            paymentCount = payments.size,
            // The last date money arrived — "Periode terakhir" in the criterion's own words.
            // `maxOfOrNull` rather than `sorted.last()` so an empty file yields `null` and the
            // copy can say so, instead of printing an epoch date for a backup with no deposits.
            lastPaymentDate = payments.maxOfOrNull { it.date },
        )
    }

    /**
     * The oldest file version this reader can restore.
     *
     * ## Why this is the version the **export** shipped at
     *
     * Because a backup file only exists if the app wrote one, and the app's write path is
     * the export. The export shipped at `SCHEMA_VERSION = 8` — commit `201c9ed`, which
     * introduced the whole format — and nothing before it could produce a file at all.
     *
     * So the floor is 8, and not some earlier version of the *database*. The two are
     * different things and confusing them costs real work: the database went 1 → 9, and a
     * v6 database had no `payment.status` — but no v6 **file** was ever written, because
     * the export did not exist. `git log -S "appendField"` on the writer returns exactly
     * one commit, the one that introduced it.
     *
     * ## Why it is a constant and not derived from the migrations
     *
     * Because the migrations describe how the *database* changed, and the file format
     * describes what the *export* wrote. Deriving one from the other would have to know
     * which columns a version's file carried, which is the writer's business — and the
     * writer, [BackupJson], is the one place that states the file's shape.
     *
     * When the next column is added, this moves up by one and [renter]'s reader stops
     * requiring the new key. That is the whole change a version bump costs from now on.
     */
    const val OLDEST_READABLE_VERSION = 8

    /**
     * The file version that first carried `renter.avatar`.
     *
     * A column's own version, separate from [OLDEST_READABLE_VERSION], because the two
     * answer different questions: the floor asks "can this file be read at all?", and this
     * asks "which columns does it have?".
     *
     * It moves up with the column, and the next one will too — which is the whole point of
     * the version being in the file rather than guessed at.
     */
    private const val AVATAR_COLUMN_VERSION = 9

    /**
     * Reads one backup file's text.
     *
     * @param asOf the caller's "today", the day [REASON_FUTURE_DATE] compares against. A parameter
     *   rather than a `LocalDate.now()` here for the reason AD-8 gives everywhere else: a reader
     *   that reads the system clock is a reader a test cannot pin.
     * @throws RestoreRejected when the text is not JSON, when a key is missing, when a value has
     *   the wrong type, when the version is not the app's, or when a contract carries a date after
     *   [asOf]. Every one of those is a refusal the owner can act on, and **none of them may be
     *   turned into an empty list**.
     */
    fun read(json: String, expectedVersion: Int, asOf: LocalDate): Restore {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            throw RestoreRejected(REASON_NOT_JSON)
        }

        val version = try {
            root.getInt(FIELD_SCHEMA_VERSION)
        } catch (_: JSONException) {
            // A file with no version at all is refused rather than guessed at. Guessing is the
            // failure NFR-3 names: a parse that half-converts and then fails *after* writing.
            throw RestoreRejected(REASON_NO_VERSION)
        }

        if (version > expectedVersion) {
            // A file from a **newer** build. Refused, because that build may have written
            // columns this one has no column for, and reading it would silently drop data.
            // A separate reason from the too-old case, because the owner's next step is the
            // opposite: update the app, rather than re-take the backup.
            throw RestoreRejected(REASON_VERSION_TOO_NEW, fileVersion = version)
        }

        if (version < OLDEST_READABLE_VERSION) {
            // A file older than the export itself. This app never wrote one, so the file
            // came from somewhere else — and the only thing the reader can say about it is
            // that it cannot read it.
            throw RestoreRejected(REASON_VERSION_TOO_OLD, fileVersion = version)
        }

        return Restore(
            schemaVersion = version,
            vehicles = array(root, FIELD_VEHICLES, ::vehicle),
            renters = array(root, FIELD_RENTERS) { renter(it, version) },
            contracts = array(root, FIELD_CONTRACTS) { contract(it, asOf) },
            serviceExceptions = array(root, FIELD_SERVICE_EXCEPTIONS, ::serviceException),
            businessAccounts = array(root, FIELD_BUSINESS_ACCOUNTS, ::businessAccount),
            payments = array(root, FIELD_PAYMENTS, ::payment),
            allocations = array(root, FIELD_ALLOCATIONS, ::allocation),
            settings = array(root, FIELD_SETTINGS, ::setting)
                .filterNot { it.key in BackupJson.NEVER_EXPORTED },
        )
    }

    // ------------------------------------------------------------------ reading

    /**
     * Reads one table as a list, or throws.
     *
     * [getJSONArray] rather than `optJSONArray` — see the class KDoc. The whole design of this
     * reader is that a key which is absent is an error, and this is where that is enforced.
     */
    private inline fun <T> array(root: JSONObject, key: String, read: (JSONObject) -> T): List<T> {
        val array = try {
            root.getJSONArray(key)
        } catch (_: JSONException) {
            throw RestoreRejected(REASON_MISSING_TABLE, table = key)
        }
        return (0 until array.length()).map { index ->
            val row = array.optJSONObject(index)
                ?: throw RestoreRejected(REASON_BAD_ROW, table = key)
            try {
                read(row)
            } catch (_: JSONException) {
                throw RestoreRejected(REASON_BAD_ROW, table = key)
            } catch (_: IllegalArgumentException) {
                // The entities' own `init` guards — an un-normalised plate, a cancelled deposit
                // with no reason, an `activeVehicleId` that disagrees with `endDate`. Caught here
                // rather than allowed to escape, because an `IllegalArgumentException` crossing
                // this boundary would read to the owner as a crash rather than as "this file is
                // not one this app wrote".
                throw RestoreRejected(REASON_BAD_ROW, table = key)
            }
        }
    }

    private fun vehicle(row: JSONObject) = VehicleEntity(
        id = row.getString("id"),
        plate = row.getString("plate"),
        brand = row.getString("brand"),
        model = row.getString("model"),
        year = row.getInt("year"),
        color = row.nullableString("color"),
        dailyRate = row.getLong("dailyRate"),
        notes = row.nullableString("notes"),
        isServis = row.optBoolean("isServis", false),
    )

    /**
     * One renter row.
     *
     * `avatar` is the one column a **later** version added, so it is the one column a
     * *current* file may be missing — and only when the file says it is an older version.
     *
     * The check is on the file's own version, not on whether the key happens to be there:
     * a v9 file with no `avatar` key is a **corrupt** file, and refusing it is the same
     * rule the rest of the reader applies. A v8 file has no `avatar` key at all, and every
     * renter in it restores with `avatar = null` — the same value a v9 renter who never
     * picked a face has.
     */
    private fun renter(row: JSONObject, fileVersion: Int) = RenterEntity(
        id = row.getString("id"),
        name = row.getString("name"),
        phone = row.getString("phone"),
        bankAccount = row.nullableString("bankAccount"),
        emergencyContact = row.getString("emergencyContact"),
        avatar = if (fileVersion >= AVATAR_COLUMN_VERSION) {
            // A current file must carry the column. `requiredNullableString` throws for an
            // absent key — and the throw is caught by `array` and reported as a bad row,
            // which is exactly right for a file that claims to be current and is not.
            // A JSON `null` is a *value* the column may hold, and restores as `null`.
            row.requiredNullableString("avatar")
        } else {
            // An older file has no such column, and the value the schema would have
            // given it is null.
            null
        },
    )

    /**
     * One contract row, refusing a date the app's own write paths would refuse.
     *
     * See [REASON_FUTURE_DATE] for why a future date is a refusal rather than a repair. Both
     * dates are checked because they fail differently: an end date frees the car, a settlement
     * marker hides the arrears. Either one alone is enough to refuse the file.
     */
    private fun contract(row: JSONObject, asOf: LocalDate): ContractEntity {
        val endDate = row.nullableString("endDate")?.let(LocalDate::parse)
        val settledThroughDate = row.nullableString("settledThroughDate")?.let(LocalDate::parse)

        if (endDate != null && endDate.isAfter(asOf)) {
            throw RestoreRejected(REASON_FUTURE_DATE, table = FIELD_CONTRACTS)
        }
        if (settledThroughDate != null && settledThroughDate.isAfter(asOf)) {
            throw RestoreRejected(REASON_FUTURE_DATE, table = FIELD_CONTRACTS)
        }

        return ContractEntity(
            id = row.getString("id"),
            renterId = row.getString("renterId"),
            vehicleId = row.getString("vehicleId"),
            startDate = LocalDate.parse(row.getString("startDate")),
            dailyRate = row.getLong("dailyRate"),
            restDay = row.nullableInt("restDay")?.let(DayOfWeek::of),
            // The two active columns are read exactly as written. `ContractEntity.init` pins them
            // to `endDate`, so a file whose author got that wrong is refused by the entity rather
            // than repaired here — a repair would be this reader inventing a database the owner
            // never backed up.
            activeVehicleId = row.nullableString("activeVehicleId"),
            activeRenterId = row.nullableString("activeRenterId"),
            endDate = endDate,
            settledThroughDate = settledThroughDate,
        )
    }

    private fun serviceException(row: JSONObject) = ServiceExceptionEntity(
        id = row.getString("id"),
        contractId = row.getString("contractId"),
        date = LocalDate.parse(row.getString("date")),
        reason = row.getString("reason"),
    )

    private fun businessAccount(row: JSONObject) = BusinessAccountEntity(
        id = row.getString("id"),
        bankName = row.getString("bankName"),
        number = row.getString("number"),
    )

    private fun payment(row: JSONObject) = PaymentEntity(
        id = row.getString("id"),
        contractId = row.getString("contractId"),
        accountId = row.nullableString("accountId"),
        date = LocalDate.parse(row.getString("date")),
        amount = row.getLong("amount"),
        method = row.getString("method"),
        referenceNo = row.nullableString("referenceNo"),
        notes = row.nullableString("notes"),
        createdAt = Instant.parse(row.getString("createdAt")),
        status = row.getString("status"),
        cancelReason = row.nullableString("cancelReason"),
    )

    private fun allocation(row: JSONObject) = AllocationEntity(
        id = row.getString("id"),
        paymentId = row.getString("paymentId"),
        // **No foreign key to `contract`.** It is a plain column, so the insert order this file
        // implies does not depend on it — and a reader that assumed otherwise would be encoding
        // a constraint the schema does not have.
        contractId = row.getString("contractId"),
        chargeDate = row.nullableString("chargeDate")?.let(LocalDate::parse),
        amount = row.getLong("amount"),
    )

    private fun setting(row: JSONObject) = SettingEntity(
        key = row.getString("key"),
        value = row.getString("value"),
    )

    /**
     * The string at [key], or `null` when the JSON value is `null`.
     *
     * A distinct `JSONObject.NULL` check rather than `isNull`, because a column that was `null`
     * in the database is written as JSON `null` and a reader that treated it as "absent" would
     * turn a saved-empty field into a missing one — and Story 4.4's `clearIfAbsent` distinguishes
     * exactly those two.
     */
    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.nullableInt(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    /**
     * The string at [key], which must be **present** — and `null` is a value it may hold.
     *
     * ## Why this is not [nullableString]
     *
     * Because `nullableString` cannot tell an absent key from a JSON `null`:
     * `isNull` is false for an absent key, and `getString` on an absent key returns `""` rather
     * than throwing. So a file that claims to be current but omits a column it must carry would
     * read as "the column is empty" and restore — turning a corrupt file into data.
     *
     * This one checks the key is present *first*, and then reads it with the same
     * `JSONObject.NULL` handling. So the two states stay distinct: a column that was `null` when
     * the file was written restores as `null`, and a column that is **missing** from a file
     * that must carry it is a bad row.
     */
    private fun JSONObject.requiredNullableString(key: String): String? {
        if (!has(key)) {
            // Thrown rather than returned, so `array` catches it as a bad row — the same
            // treatment every other unreadable row gets.
            throw JSONException("no value for '$key'")
        }
        return if (isNull(key)) null else getString(key)
    }

    /** The eight table keys, as [BackupJson] writes them. One list, two directions. */
    internal const val FIELD_SCHEMA_VERSION = "schemaVersion"
    internal const val FIELD_VEHICLES = "vehicles"
    internal const val FIELD_RENTERS = "renters"
    internal const val FIELD_CONTRACTS = "contracts"
    internal const val FIELD_SERVICE_EXCEPTIONS = "serviceExceptions"
    internal const val FIELD_BUSINESS_ACCOUNTS = "businessAccounts"
    internal const val FIELD_PAYMENTS = "payments"
    internal const val FIELD_ALLOCATIONS = "allocations"
    internal const val FIELD_SETTINGS = "settings"
}

/**
 * What the preview says before the owner confirms, in the criterion's own shape:
 * `18 mobil · 14 penyewa · 312 setoran` and `Periode terakhir: 24 Mar 2026`.
 */
data class RestorePreview(
    val vehicleCount: Int,
    val renterCount: Int,
    val contractCount: Int,
    val paymentCount: Int,
    /** `null` when the file holds no deposits, so the copy can say so rather than print a date. */
    val lastPaymentDate: LocalDate?,
)

/**
 * The restore refused, and why — every reason is one the owner can act on.
 *
 * ## Why this is a type rather than a `Result` or a string
 *
 * Because the reasons are decisions, not sentences. The UI turns a [reason] into a message, the
 * test asserts on a reason, and a future reason cannot be added without a compiler error at every
 * `when` that has to handle it. It also guarantees there is **no** path that turns a parse
 * failure into an empty restore, because there is no branch from which one could come.
 */
class RestoreRejected(
    val reason: String,
    /** The file's own version, when the refusal is a mismatch. */
    val fileVersion: Int? = null,
    /** The table whose row could not be read, when the refusal is about a row. */
    val table: String? = null,
) : Exception(
    buildString {
        append("restore rejected: ").append(reason)
        if (fileVersion != null) append(" (file version ").append(fileVersion).append(')')
        if (table != null) append(" (table ").append(table).append(')')
    },
)