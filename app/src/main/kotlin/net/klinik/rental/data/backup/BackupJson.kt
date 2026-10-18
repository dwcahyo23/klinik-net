package net.klinik.rental.data.backup

import net.klinik.rental.data.db.entity.AllocationEntity
import net.klinik.rental.data.db.entity.BusinessAccountEntity
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.ServiceExceptionEntity
import net.klinik.rental.data.db.entity.SettingEntity
import net.klinik.rental.data.db.entity.VehicleEntity

/**
 * The backup file's writer: eight tables in, one JSON document out.
 *
 * ## Why this is hand-written and not `kotlinx.serialization` or `org.json`
 *
 * Three reasons, and the third is the one that decides it.
 *
 * **No library is on the classpath**, so serialization would be a new dependency and a new
 * compiler plugin to write eight `data class` copies of eight Room entities — a second spelling
 * of every field that can disagree with the entity it copies. The entities are the schema; the
 * file should say so in one place.
 *
 * **`org.json` fails silently where it matters.** `app/build.gradle.kts` sets
 * `isReturnDefaultValues = true`, which makes the JVM stubs of `org.json` return defaults
 * instead of throwing. A serialization bug in a plain JVM test would therefore pass: `put`
 * would quietly do nothing and the test would assert against a string that is missing the field.
 * The backup would be wrong and the suite would be green — which is the "renders and is wired to
 * nothing" defect in the one place it costs the owner their records.
 *
 * **`JSONObject` has no key order.** It is backed by a hash map, so the same database exports two
 * different files. A backup the owner can open in a text editor and diff against last month's is
 * worth more than a marginal saving, and stable output is the whole reason the ordering is pinned
 * here rather than left to each DAO's `ORDER BY`.
 *
 * ## Why **all eight** tables and not the five the criterion names
 *
 * The criterion lists Kendaraan, Penyewa, Kontrak, Setoran and Pengaturan. The database also holds
 * `service_exception` — the owner's own "hari tanpa tagihan" marks from Story 2.6 — and
 * `allocation`, Story 1.3's per-day ledger. Both are the owner's data, both are unrecoverable
 * once the device is gone, and Story 5.2's criterion is *"seluruh data kembali utuh"*. A restore
 * that silently dropped the exception marks would be exactly the partial recovery it forbids.
 *
 * ## Why allocations are copied and not re-derived
 *
 * Because `previewAllocation` replays deposits oldest-day-first over the period the cutoff
 * defines **today**. A March deposit restored in November would land on different days, and the
 * restored ledger would disagree with the one that was backed up. Story 1.3's guarantee — that
 * `Σ allocation == amount` and that no day is left unaccounted for — is about *these* rows, so
 * the backup carries these rows.
 *
 * ## Why the deny-list is here and not in Story 5.3
 *
 * [NEVER_EXPORTED] ships now, before the rows it names exist. Story 5.3 adds `pinHash` and
 * `recoveryHash` to this same `settings` table; if the exclusion were written then, it would be a
 * different person editing a different file months later, and the skip would land in the *write*
 * path rather than the *read* path — which reads as working and leaks the hash to every copy of
 * the file the owner has ever made. NFR-5 and AD-7 both forbid that; the test inserts a
 * `pinHash` row that nothing in the app writes and asserts it is absent.
 *
 * ## What is *not* claimed
 *
 * This file is plain text. It is not encrypted, and no UI string may imply it is — FR-17
 * explicitly forbids reusing the prototype's *"data tersimpan lokal dan terenkripsi"*.
 */
object BackupJson {

    /**
     * `settings` keys that must never reach a backup file.
     *
     * ## Why this is every PIN key and not just the two hashes
     *
     * Story 5.1 shipped two keys as a placeholder, before Story 5.3 wrote any of them. Story 5.3
     * writes **eight**, and leaving any of the others out has a failure mode that is worse than a
     * leaked hash: a backup taken with the PIN on would restore `pinEnabled = true` onto a device
     * that has **no hash** — a lock nobody can open, holding the owner's records, on the day they
     * restored a backup because they had lost a phone.
     *
     * So the list covers every key the lock owns, and `BackupJsonTest` asserts the exclusion
     * **per key** rather than for "the hashes". Adding a ninth key to the lock without adding it
     * here is the one change this list cannot survive silently, which is why the test is a loop.
     *
     * Matched **exactly**, never by prefix: a prefix match would also swallow a future
     * `businessName`-adjacent key or a boolean that merely shares a stem, and silently dropping a
     * setting the owner configured is its own kind of data loss.
     */
    val NEVER_EXPORTED: Set<String> = setOf(
        // The two secrets. NFR-5 and AD-7 forbid either reaching a file.
        "pinHash",
        "recoveryHash",
        // Their salts: useless without the hashes, and pointless to carry.
        "pinSalt",
        "recoverySalt",
        // And the state, which is the dangerous half. Restoring `pinEnabled = true` without a
        // hash produces a lock that can never be opened.
        "pinEnabled",
        "pinAttempts",
        "pinLockoutUntil",
        "autoLockMinutes",
        "pinBackgroundedAt",
    )

    /** The JSON object for one table's rows, already sorted by primary key. */
    fun build(
        vehicles: List<VehicleEntity>,
        renters: List<RenterEntity>,
        contracts: List<ContractEntity>,
        serviceExceptions: List<ServiceExceptionEntity>,
        businessAccounts: List<BusinessAccountEntity>,
        payments: List<PaymentEntity>,
        allocations: List<AllocationEntity>,
        settings: List<SettingEntity>,
        schemaVersion: Int,
    ): String = buildString {
        append("{\n")
        // `schemaVersion` first, and at the root: Story 5.2 reads it before anything else, and
        // a reader who opens the file by eye sees the version in the first line.
        append("  \"schemaVersion\": ").append(schemaVersion).append(",\n")

        append("  \"vehicles\": ")
        appendArray(vehicles.sortedBy { it.id }) { vehicle(it) }
        append(",\n")

        append("  \"renters\": ")
        appendArray(renters.sortedBy { it.id }) { renter(it) }
        append(",\n")

        append("  \"contracts\": ")
        appendArray(contracts.sortedBy { it.id }) { contract(it) }
        append(",\n")

        append("  \"serviceExceptions\": ")
        appendArray(serviceExceptions.sortedBy { it.id }) { serviceException(it) }
        append(",\n")

        append("  \"businessAccounts\": ")
        appendArray(businessAccounts.sortedBy { it.id }) { businessAccount(it) }
        append(",\n")

        append("  \"payments\": ")
        appendArray(payments.sortedBy { it.id }) { payment(it) }
        append(",\n")

        append("  \"allocations\": ")
        appendArray(allocations.sortedBy { it.id }) { allocation(it) }
        append(",\n")

        append("  \"settings\": ")
        // Filtered **here**, at the only place the table is read for export. Filtering in
        // `BackupRepository` instead would put the rule next to the reads, where a future
        // eighth read of `settings` would not inherit it.
        appendArray(settings.filterNot { it.key in NEVER_EXPORTED }.sortedBy { it.key }) { setting(it) }
        append("\n}\n")
    }

    // ------------------------------------------------------------------ escaping

    /**
     * Appends `value` as a JSON string, quotes included.
     *
     * ## Why escaping is hand-rolled here rather than delegated
     *
     * Because a name like `A "B" \ C` or a cancellation reason that contains a newline is the
     * owner's own text, and JSON's rules for those four characters are the whole reason the file
     * can be parsed at all. Written by hand and **tested against those exact characters**: the
     * failure this guards is not theoretical, since a renter called `A "B"` would otherwise
     * produce a file Story 5.2 cannot read, and the owner's backup would be worthless to them
     * at the exact moment they needed it.
     *
     * Control characters below `0x20` are emitted as `\uXXXX` because JSON forbids a literal
     * newline inside a string — an unescaped one silently ends the string and corrupts every
     * field after it, rather than failing loudly at parse time.
     */
    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '' -> append("\\f")
                else -> if (c < ' ') {
                    append("\\u").append(String.format("%04x", c.code))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }

    /** `null` rather than `""`, so Story 5.2 can tell "the owner left this blank" from "no value". */
    private fun StringBuilder.appendNullable(value: String?) {
        if (value == null) append("null") else appendJsonString(value)
    }

    /**
     * Writes `[ {...}, {...} ]`, one object per line.
     *
     * **Eight empty arrays, never eight missing keys.** A fresh install exports
     * `"vehicles": []` rather than omitting the field, because Story 5.2 must be able to say
     * "this backup has no vehicles" — an absent key and an empty list mean different things, and
     * only one of them is a fact about the owner's business.
     */
    private fun <T> StringBuilder.appendArray(rows: List<T>, render: (T) -> Unit) {
        if (rows.isEmpty()) {
            append("[]")
            return
        }
        append("[\n")
        rows.forEachIndexed { index, row ->
            append("    {")
            render(row)
            append("}")
            if (index != rows.lastIndex) append(',')
            append('\n')
        }
        append("  ]")
    }

    /** A `"key": value` pair, with the comma when it is not the last. */
    private fun StringBuilder.appendField(first: Boolean, name: String, render: (StringBuilder) -> Unit) {
        if (!first) append(", ")
        appendJsonString(name)
        append(": ")
        render(this)
    }

    // --------------------------------------------------------------- the eight

    private fun StringBuilder.vehicle(v: VehicleEntity) {
        appendField(true, "id") { it.appendJsonString(v.id) }
        appendField(false, "plate") { it.appendJsonString(v.plate) }
        appendField(false, "brand") { it.appendJsonString(v.brand) }
        appendField(false, "model") { it.appendJsonString(v.model) }
        appendField(false, "year") { it.append(v.year) }
        appendField(false, "color") { it.appendNullable(v.color) }
        // A `Long` is written as a JSON number, never as a quoted string: `formatRupiah` is a
        // *display* concern and a backup that carried formatted money would have to be parsed
        // back through a formatter, which is a second derivation of the same figure.
        appendField(false, "dailyRate") { it.append(v.dailyRate) }
        appendField(false, "notes") { it.appendNullable(v.notes) }
        appendField(false, "isServis") { it.append(v.isServis) }
    }

    private fun StringBuilder.renter(r: RenterEntity) {
        appendField(true, "id") { it.appendJsonString(r.id) }
        appendField(false, "name") { it.appendJsonString(r.name) }
        appendField(false, "phone") { it.appendJsonString(r.phone) }
        appendField(false, "bankAccount") { it.appendNullable(r.bankAccount) }
        appendField(false, "emergencyContact") { it.appendJsonString(r.emergencyContact) }
    }

    private fun StringBuilder.contract(c: ContractEntity) {
        appendField(true, "id") { it.appendJsonString(c.id) }
        appendField(false, "renterId") { it.appendJsonString(c.renterId) }
        appendField(false, "vehicleId") { it.appendJsonString(c.vehicleId) }
        appendField(false, "startDate") { it.appendJsonString(c.startDate.toString()) }
        appendField(false, "dailyRate") { it.append(c.dailyRate) }
        // `restDay` is a `DayOfWeek`, stored as its ISO number 1..7. Written as that number
        // rather than as "MONDAY" so the file holds what the column holds and Story 5.2 reads
        // one representation rather than translating a name back into an ordinal.
        appendField(false, "restDay") {
            val day = c.restDay
            if (day == null) it.append("null") else it.append(day.value)
        }
        appendField(false, "activeVehicleId") { it.appendNullable(c.activeVehicleId) }
        appendField(false, "activeRenterId") { it.appendNullable(c.activeRenterId) }
        appendField(false, "endDate") { it.appendNullable(c.endDate?.toString()) }
        appendField(false, "settledThroughDate") { it.appendNullable(c.settledThroughDate?.toString()) }
    }

    private fun StringBuilder.serviceException(s: ServiceExceptionEntity) {
        appendField(true, "id") { it.appendJsonString(s.id) }
        appendField(false, "contractId") { it.appendJsonString(s.contractId) }
        appendField(false, "date") { it.appendJsonString(s.date.toString()) }
        appendField(false, "reason") { it.appendJsonString(s.reason) }
    }

    private fun StringBuilder.businessAccount(b: BusinessAccountEntity) {
        appendField(true, "id") { it.appendJsonString(b.id) }
        appendField(false, "bankName") { it.appendJsonString(b.bankName) }
        appendField(false, "number") { it.appendJsonString(b.number) }
    }

    private fun StringBuilder.payment(p: PaymentEntity) {
        appendField(true, "id") { it.appendJsonString(p.id) }
        appendField(false, "contractId") { it.appendJsonString(p.contractId) }
        appendField(false, "accountId") { it.appendNullable(p.accountId) }
        appendField(false, "date") { it.appendJsonString(p.date.toString()) }
        appendField(false, "amount") { it.append(p.amount) }
        appendField(false, "method") { it.appendJsonString(p.method) }
        appendField(false, "referenceNo") { it.appendNullable(p.referenceNo) }
        appendField(false, "notes") { it.appendNullable(p.notes) }
        appendField(false, "createdAt") { it.appendJsonString(p.createdAt.toString()) }
        // `status` and `cancelReason` travel together or not at all: Story 3.4's cancelled row is
        // only readable if the reason beside its status survives, and dropping one produces a
        // row that says "cancelled" and cannot say why.
        appendField(false, "status") { it.appendJsonString(p.status) }
        appendField(false, "cancelReason") { it.appendNullable(p.cancelReason) }
    }

    private fun StringBuilder.allocation(a: AllocationEntity) {
        appendField(true, "id") { it.appendJsonString(a.id) }
        appendField(false, "paymentId") { it.appendJsonString(a.paymentId) }
        appendField(false, "contractId") { it.appendJsonString(a.contractId) }
        appendField(false, "chargeDate") { it.appendNullable(a.chargeDate?.toString()) }
        appendField(false, "amount") { it.append(a.amount) }
    }

    private fun StringBuilder.setting(s: SettingEntity) {
        appendField(true, "key") { it.appendJsonString(s.key) }
        appendField(false, "value") { it.appendJsonString(s.value) }
    }
}