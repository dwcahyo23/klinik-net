package net.klinik.rental.data.backup

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import net.klinik.rental.data.db.entity.AllocationEntity
import net.klinik.rental.data.db.entity.BusinessAccountEntity
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.ServiceExceptionEntity
import net.klinik.rental.data.db.entity.SettingEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup writer's contract, with **no runner and no Robolectric**.
 *
 * ## Why this file is the only test in the repo with neither
 *
 * Because [BackupJson] is a pure function of eight lists — no Android type, no Room, no coroutine,
 * no `ContentResolver`. That is the property the file's design was chosen for, and a test that
 * needed a Robolectric runner to run would mean the design had drifted back toward the framework.
 * A test this fast is also the kind that gets written a dozen times instead of twice.
 *
 * ## What "correct" means for a file nobody parses until Story 5.2
 *
 * Story 5.1 writes; Story 5.2 reads. So the assertions here are about what a *future* parser
 * needs to be able to rely on — and the three that matter most are the ones a hand-written writer
 * gets wrong most easily: escaping the owner's own text, an empty table being `[]` rather than an
 * absent key, and the same database producing the same bytes twice.
 */
class BackupJsonTest {

    /** `schemaVersion` at the root: NFR-3's basis, and Story 5.2 reads it before anything else. */
    @Test
    fun `the root carries the schema version`() {
        val json = build(schemaVersion = 8)

        assertTrue(
            "Story 5.2 must be able to read the version before it reads anything else",
            json.contains("\"schemaVersion\": 8"),
        )
    }

    /** All eight tables, named — the criterion's five plus the three the database also holds. */
    @Test
    fun `every table is present, including the three the criterion does not name`() {
        val json = build()

        listOf(
            "vehicles",
            "renters",
            "contracts",
            "serviceExceptions",
            "businessAccounts",
            "payments",
            "allocations",
            "settings",
        ).forEach { table ->
            assertTrue("\"$table\" is missing from the export", json.contains("\"$table\":"))
        }
    }

    /**
     * An empty table is `[]`, never an absent key.
     *
     * A fresh install is a normal thing to back up, and Story 5.2 must be able to say "this
     * backup has no vehicles" — which is a fact about the owner's business, distinct from the
     * table not existing. An omitted key makes those two look identical.
     */
    @Test
    fun `an empty table is an empty array rather than a missing key`() {
        val json = build()

        assertTrue("an empty table must still be named", json.contains("\"vehicles\": []"))
        assertTrue(json.contains("\"settings\": []"))
    }

    /**
     * The owner's own text survives the four characters that break a hand-written escaper.
     *
     * `A "B" \ C` is not hypothetical: it is a name, a cancellation reason, a note. An unescaped
     * quote produces a file Story 5.2 cannot parse, and the owner discovers it at the moment they
     * need the backup — with no other copy of the data.
     */
    @Test
    fun `quotes, backslashes and newlines in the owner's text are escaped`() {
        val json = BackupJson.build(
            vehicles = emptyList(),
            renters = listOf(
                renter(id = "r1", name = """A "B" \ C"""),
            ),
            contracts = emptyList(),
            serviceExceptions = emptyList(),
            businessAccounts = emptyList(),
            payments = emptyList(),
            allocations = emptyList(),
            settings = emptyList(),
            schemaVersion = 8,
        )

        assertTrue(
            "the escaped form is what reaches the file",
            json.contains(""""A \"B\" \\ C""""),
        )
        // And the raw form is gone — otherwise the first quote would have ended the string and
        // everything after it would be malformed.
        assertFalse(
            "no unescaped quote from the owner's text",
            json.contains(""""A "B"""),
        )
    }

    /** A newline inside a note: JSON forbids a literal one, and it corrupts every later field. */
    @Test
    fun `a newline in a note becomes a literal backslash-n`() {
        val json = BackupJson.build(
            vehicles = emptyList(),
            renters = emptyList(),
            contracts = emptyList(),
            serviceExceptions = emptyList(),
            businessAccounts = emptyList(),
            payments = emptyList(),
            allocations = emptyList(),
            settings = listOf(SettingEntity(key = "note", value = "baris satu\nbaris dua")),
            schemaVersion = 8,
        )

        assertTrue(json.contains(""""baris satu\nbaris dua""""))
        assertFalse("no raw newline inside the string", json.contains("baris satu\nbaris dua"))
    }

    /**
     * The same database produces the same bytes, twice.
     *
     * Rows are given to the writer in a deliberately wrong order to prove the sort is the
     * writer's and not the DAO's — six of the eight DAOs carry their own `ORDER BY`, and if any of
     * them leaked into the file the same data would export two different ways.
     */
    @Test
    fun `the same rows in any order produce identical output`() {
        val a = vehicle(id = "v1", plate = "B 1 AA")
        val b = vehicle(id = "v2", plate = "B 2 BB")
        val c = vehicle(id = "v3", plate = "B 3 CC")

        val forward = build(vehicles = listOf(a, b, c))
        val shuffled = build(vehicles = listOf(c, a, b))

        assertEquals("the file depends on the data, not on the reader's order", forward, shuffled)
    }

    /** And the primary key is what it sorts on, so the first row in the file is the lowest id. */
    @Test
    fun `rows are sorted by primary key`() {
        val json = build(
            vehicles = listOf(
                vehicle(id = "v3", plate = "B 3 CC"),
                vehicle(id = "v1", plate = "B 1 AA"),
            ),
        )

        assertTrue(
            "v1 is written before v3",
            json.indexOf("\"v1\"") < json.indexOf("\"v3\""),
        )
    }

    /**
     * `pinHash` never reaches the file, even when the table holds one.
     *
     * Story 5.3 adds the row; this test inserts it **before that story exists**, so the
     * exclusion is proved by the deny-list rather than by remembering to add the skip later. NFR-5
     * and AD-7 both forbid a hash reaching a backup, and a backup is a file the owner emails to
     * themselves.
     */
    @Test
    fun `the pin hash is excluded even when a row for it exists`() {
        val json = build(
            settings = listOf(
                SettingEntity(key = "businessName", value = "Klinik Net"),
                SettingEntity(key = "pinHash", value = "s3cr3t-hash-value"),
                SettingEntity(key = "recoveryHash", value = "another-hash"),
            ),
        )

        assertFalse("the PIN hash is absent", json.contains("pinHash"))
        assertFalse("the recovery hash is absent", json.contains("recoveryHash"))
        assertFalse("and neither value leaked under another key", json.contains("s3cr3t-hash-value"))
        assertTrue(
            "while every other setting still exports — the list excludes two keys, not the table",
            json.contains("businessName"),
        )
    }

    /** Money travels as a JSON number, never as a formatted string. */
    @Test
    fun `an amount is a number and not a formatted rupiah string`() {
        val json = build(
            payments = listOf(payment(id = "p1", amount = 250_000)),
        )

        assertTrue("\"amount\": 250000", json.contains("\"amount\": 250000"))
        assertFalse(
            "a backup that carried formatted money would have to be parsed back through formatRupiah",
            json.contains("Rp"),
        )
    }

    /**
     * `restDay` is its ISO number, because that is what the column holds.
     *
     * Writing `"MONDAY"` would make Story 5.2 translate a name back into an ordinal — a second
     * derivation of a value the database already stores as a number.
     */
    @Test
    fun `restDay is written as the number the column stores`() {
        val json = build(
            contracts = listOf(
                contract(id = "c1", restDay = DayOfWeek.SUNDAY),
            ),
        )

        assertTrue(json.contains("\"restDay\": 7"))
        assertFalse(json.contains("SUNDAY"))
    }

    /** And a contract with no rest day says `null`, which is distinguishable from day 0. */
    @Test
    fun `an absent rest day is null rather than a number`() {
        val json = build(contracts = listOf(contract(id = "c1", restDay = null)))

        assertTrue(json.contains("\"restDay\": null"))
    }

    /**
     * A cancelled deposit carries its status **and** the owner's reason.
     *
     * Story 3.4 kept the row precisely so the reason could be read later; a file that carried the
     * status without the reason would keep the fact and lose the only part of it that answers
     * "why did Budi's arrears jump on the 17th".
     */
    @Test
    fun `a cancelled deposit keeps its reason beside its status`() {
        val json = build(
            payments = listOf(
                payment(
                    id = "p1",
                    amount = 250_000,
                    status = "canceled",
                    cancelReason = "salah tanggal",
                ),
            ),
        )

        assertTrue(json.contains("\"status\": \"canceled\""))
        assertTrue(json.contains("\"cancelReason\": \"salah tanggal\""))
    }

    /** Allocation rows are copied whole, credit row (`chargeDate = null`) included. */
    @Test
    fun `an allocation's credit row is exported with a null charge date`() {
        val json = build(
            allocations = listOf(
                AllocationEntity(
                    id = "a1",
                    paymentId = "p1",
                    contractId = "c1",
                    chargeDate = null,
                    amount = 50_000,
                ),
            ),
        )

        assertTrue("the credit row survives", json.contains("\"chargeDate\": null"))
        assertTrue(json.contains("\"amount\": 50000"))
    }

    // ------------------------------------------------------------------ helpers

    private fun build(
        vehicles: List<VehicleEntity> = emptyList(),
        renters: List<RenterEntity> = emptyList(),
        contracts: List<ContractEntity> = emptyList(),
        serviceExceptions: List<ServiceExceptionEntity> = emptyList(),
        businessAccounts: List<BusinessAccountEntity> = emptyList(),
        payments: List<PaymentEntity> = emptyList(),
        allocations: List<AllocationEntity> = emptyList(),
        settings: List<SettingEntity> = emptyList(),
        schemaVersion: Int = 8,
    ) = BackupJson.build(
        vehicles = vehicles,
        renters = renters,
        contracts = contracts,
        serviceExceptions = serviceExceptions,
        businessAccounts = businessAccounts,
        payments = payments,
        allocations = allocations,
        settings = settings,
        schemaVersion = schemaVersion,
    )

    private fun vehicle(id: String, plate: String) = VehicleEntity(
        id = id,
        plate = plate,
        brand = "Toyota",
        model = "Avanza",
        year = 2023,
        color = null,
        dailyRate = 250_000,
        notes = null,
        isServis = false,
    )

    private fun renter(id: String, name: String) = RenterEntity(
        id = id,
        name = name,
        phone = "08123456789",
        bankAccount = null,
        emergencyContact = "08129876543",
    )

    private fun contract(id: String, restDay: DayOfWeek?) = ContractEntity(
        id = id,
        renterId = "r1",
        vehicleId = "v1",
        startDate = LocalDate.of(2026, 10, 1),
        dailyRate = 250_000,
        restDay = restDay,
        activeVehicleId = "v1",
        activeRenterId = "r1",
        endDate = null,
        settledThroughDate = null,
    )

    private fun payment(
        id: String,
        amount: Long,
        status: String = "confirmed",
        cancelReason: String? = null,
    ) = PaymentEntity(
        id = id,
        contractId = "c1",
        accountId = null,
        date = LocalDate.of(2026, 10, 5),
        amount = amount,
        method = "tunai",
        referenceNo = null,
        notes = null,
        createdAt = Instant.parse("2026-10-05T09:00:00Z"),
        status = status,
        cancelReason = cancelReason,
    )
}