package net.klinik.rental.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [BackupJsonReader]'s contract, on the input side.
 *
 * ## Why this needs `RobolectricTestRunner` while [BackupJsonTest] needs none
 *
 * Because `org.json` is an Android framework class. On a plain JVM unit test the `android.jar`
 * stubs answer instead, and `isReturnDefaultValues = true` makes them answer with **defaults
 * rather than throwing** — which is the whole hazard this file exists to guard against, applied
 * to the test harness itself: a stubbed `getJSONArray` returning an empty array would make
 * "a missing table is refused" pass without ever exercising the real library.
 *
 * So the strictness assertions below only mean something here, and they would mean nothing on a
 * runner-less JVM test. That asymmetry is worth stating rather than leaving as a mystery: the
 * writer is pure Kotlin and needs no framework, the reader needs the framework's JSON and
 * therefore needs the framework's test runner.
 *
 * ## What is asserted, and why each one is a refusal
 *
 * Every case here is a file that must NOT restore. The dangerous property of a restore is that a
 * bad file looks exactly like a good one with nothing in it, so each test names the reason the
 * reader refuses and — more importantly — asserts that it refuses *at all* rather than returning
 * a shorter list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupJsonReaderTest {

    private val version = 8

    /** A complete, valid file. Every other case is this file with one thing changed. */
    private fun validJson(): String = """
        {
          "schemaVersion": $version,
          "vehicles": [
            {"id":"v1","plate":"B 1235 TF","brand":"Toyota","model":"Avanza","year":2023,
             "color":null,"dailyRate":250000,"notes":null,"isServis":false}
          ],
          "renters": [
            {"id":"r1","name":"Budi Santoso","phone":"08123456789","bankAccount":null,
             "emergencyContact":"08129876543"}
          ],
          "contracts": [
            {"id":"c1","renterId":"r1","vehicleId":"v1","startDate":"2026-10-01",
             "dailyRate":250000,"restDay":null,"activeVehicleId":"v1","activeRenterId":"r1",
             "endDate":null,"settledThroughDate":null}
          ],
          "serviceExceptions": [],
          "businessAccounts": [
            {"id":"a1","bankName":"BCA","number":"1234567890"}
          ],
          "payments": [
            {"id":"p1","contractId":"c1","accountId":null,"date":"2026-10-05","amount":250000,
             "method":"tunai","referenceNo":null,"notes":null,
             "createdAt":"2026-10-05T09:00:00Z","status":"confirmed","cancelReason":null}
          ],
          "allocations": [
            {"id":"al1","paymentId":"p1","contractId":"c1","chargeDate":"2026-10-05",
             "amount":250000}
          ],
          "settings": [{"key":"businessName","value":"Klinik Net"}]
        }
    """.trimIndent()

    // ------------------------------------------------------------------ the happy path

    @Test
    fun `a valid file reads back into every table`() {
        val restore = BackupJsonReader.read(validJson(), version)

        assertEquals(1, restore.vehicles.size)
        assertEquals("B 1235 TF", restore.vehicles.first().plate)
        assertEquals(250_000L, restore.vehicles.first().dailyRate)
        assertEquals("Budi Santoso", restore.renters.first().name)
        assertEquals("2026-10-01", restore.contracts.first().startDate.toString())
        assertEquals("BCA", restore.businessAccounts.first().bankName)
        assertEquals(250_000L, restore.payments.first().amount)
        assertEquals("al1", restore.allocations.first().id)
        assertEquals("businessName", restore.settings.first().key)
    }

    /** The preview is the AC's own shape: four counts and the last period. */
    @Test
    fun `the preview carries the four counts and the last payment date`() {
        val preview = BackupJsonReader.read(validJson(), version).preview

        assertEquals(1, preview.vehicleCount)
        assertEquals(1, preview.renterCount)
        assertEquals(1, preview.contractCount)
        assertEquals(1, preview.paymentCount)
        assertEquals("2026-10-05", preview.lastPaymentDate.toString())
    }

    /**
     * A file with no deposits has **no** last date, rather than the epoch.
     *
     * `maxOfOrNull` rather than `sorted().last()` — the empty list would hand a caller a date of
     * 1970-01-01 and the preview would print "Periode terakhir: 1 Jan 1970" for a backup with
     * nothing in it. The copy can say "no deposits" only because the type permits it.
     */
    @Test
    fun `a file with no deposits has no last period rather than an epoch`() {
        // An **empty** array, not a missing key. The difference is the whole reader: a missing
        // key is a refusal (asserted twice below), and an empty array is a real fact about the
        // owner's business — "this backup has no deposits" — which the preview has to be able to
        // say. Using the wrong one here would make this test assert the refusal path.
        val restore = BackupJsonReader.read(emptyTable("payments"), version)

        assertTrue("the file really has no payments", restore.payments.isEmpty())
        assertNull("and no last period, rather than the epoch", restore.preview.lastPaymentDate)
    }

    /**
     * A valid file whose table is **empty** restores as empty.
     *
     * The counterpart to the missing-key refusal, and the one that proves the two are actually
     * distinguished rather than both producing a list. A fresh install's backup has eight empty
     * arrays and must restore to an empty app — that is a fact, not a corruption.
     */
    @Test
    fun `an empty table restores as empty rather than being refused`() {
        val restore = BackupJsonReader.read(emptyTable("vehicles"), version)

        assertTrue(restore.vehicles.isEmpty())
        assertEquals("while the rest of the file is intact", 1, restore.renters.size)
    }

    // ------------------------------------------------------------------ the refusals

    /**
     * Text that is not JSON at all.
     *
     * The owner picked a photo or a spreadsheet. It must be refused rather than parsed into
     * whatever happens to be valid.
     */
    @Test
    fun `text that is not json is refused`() {
        val thrown = rejected("ini bukan json")
        assertEquals(BackupJsonReader.REASON_NOT_JSON, thrown.reason)
    }

    /**
     * No `schemaVersion` at the root.
     *
     * Refused rather than assumed. Guessing is the failure NFR-3 names: a reader that assumes
     * "close enough" is a reader that half-converts and then fails *after* writing.
     */
    @Test
    fun `a file with no schema version is refused`() {
        val json = validJson().replace("\"schemaVersion\": $version,", "")

        assertEquals(BackupJsonReader.REASON_NO_VERSION, rejected(json).reason)
    }

    /** A file from a **newer** app is refused, naming its version. */
    @Test
    fun `a newer file is refused and carries its version`() {
        val thrown = rejected(validJson().replace("\"schemaVersion\": $version", "\"schemaVersion\": 99"))

        assertEquals(BackupJsonReader.REASON_VERSION, thrown.reason)
        assertEquals(99, thrown.fileVersion)
    }

    /**
     * An **older** file is refused too.
     *
     * Story 5.6 is what teaches the reader to accept one. Until then refusing in both directions
     * is the honest answer, and the test says so explicitly because "older files are fine" is
     * the assumption someone would otherwise carry into the next change.
     */
    @Test
    fun `an older file is refused until a later story teaches the reader to migrate`() {
        val thrown = rejected(validJson().replace("\"schemaVersion\": $version", "\"schemaVersion\": 7"))

        assertEquals(BackupJsonReader.REASON_VERSION, thrown.reason)
        assertEquals(7, thrown.fileVersion)
    }

    /**
     * **The load-bearing test.** A file missing a table key is refused, not read as empty.
     *
     * This is the failure the whole reader design exists to prevent: `optJSONArray` returns an
     * empty array for a missing key, an empty array restores as "this owner has nothing", and the
     * settings screen then renders it as a brand-new install while reporting success. The owner
     * would have been told their records came back and would be looking at an empty app.
     */
    @Test
    fun `a file missing a table key is refused, not read as empty`() {
        val thrown = rejected(withoutTable("vehicles"))

        assertEquals(BackupJsonReader.REASON_MISSING_TABLE, thrown.reason)
        assertEquals("vehicles", thrown.table)
    }

    /** Every one of the eight keys is load-bearing — tested as a loop so a new table inherits it. */
    @Test
    fun `every table key is required`() {
        listOf(
            "vehicles",
            "renters",
            "contracts",
            "serviceExceptions",
            "businessAccounts",
            "payments",
            "allocations",
            "settings",
        ).forEach { key ->
            assertEquals(
                "\"$key\" must be required, not defaulted to empty",
                BackupJsonReader.REASON_MISSING_TABLE,
                rejected(withoutTable(key)).reason,
            )
        }
    }

    /**
     * A row missing a required field is refused, naming the table.
     *
     * The `getString` in the row readers throws on an absent key, and this asserts the exception
     * becomes a refusal rather than a crash that reaches the owner as a stack trace.
     */
    @Test
    fun `a row missing a required field is refused naming its table`() {
        val json = validJson().replace(""""phone":"08123456789",""", "")

        val thrown = rejected(json)

        assertEquals(BackupJsonReader.REASON_BAD_ROW, thrown.reason)
        assertEquals("renters", thrown.table)
    }

    /**
     * A row the **entity's own guard** refuses is refused, not repaired.
     *
     * `ContractEntity.init` pins `activeVehicleId == vehicleId` while `endDate` is null. A file
     * whose author got that wrong describes a database that cannot exist, and the reader that
     * "fixed" it would be inventing a database the owner never backed up.
     */
    @Test
    fun `a contract row the entity refuses is rejected rather than repaired`() {
        val json = validJson().replace(""""activeVehicleId":"v1"""", """"activeVehicleId":"v9"""")

        val thrown = rejected(json)

        assertEquals(BackupJsonReader.REASON_BAD_ROW, thrown.reason)
        assertEquals("contracts", thrown.table)
    }

    /**
     * A `pinHash` in the file is dropped **on read**, not only on write.
     *
     * The writer's deny-list covers files this app wrote. A file from an older build, or one
     * assembled by hand, is the case the read-side filter exists for — and one list, filtered in
     * both directions, means a key cannot be smuggled past the writer.
     */
    @Test
    fun `a pin hash in the file is dropped on read`() {
        val json = validJson().replace(
            """"settings": [{"key":"businessName","value":"Klinik Net"}]""",
            """"settings": [{"key":"businessName","value":"Klinik Net"},
                            {"key":"pinHash","value":"secret"}]""",
        )

        val settings = BackupJsonReader.read(json, version).settings

        assertEquals("only the business name survives", 1, settings.size)
        assertEquals("businessName", settings.first().key)
    }

    // ------------------------------------------------------------------ helpers

    /**
     * [validJson] with the `"key": [...]` pair for [key] **replaced by an empty array**.
     *
     * Distinct from [withoutTable] because the two are opposite facts: a present-but-empty table
     * is a real business state, and an absent one is a corrupt file. Testing one where the other
     * was meant is how a suite ends up green while asserting nothing.
     */
    private fun emptyTable(key: String): String {
        val json = validJson()
        val emptied = Regex("""("\s*$key\s*":\s*)\[.*?]""", RegexOption.DOT_MATCHES_ALL)
            .replace(json, "$1[]")
        assertTrue(
            "the regex must actually empty \"$key\"",
            emptied != json && emptied.contains("\"$key\": []"),
        )
        return emptied
    }

    /**
     * [validJson] with the `"key": [...]` pair for [key] **removed entirely**.
     *
     * A regex rather than a `replace` on the literal text, because the writer indents rows and a
     * literal that matches on one revision stops matching on the next — silently, leaving the
     * key in place, which would turn every assertion below into a test of nothing.
     */
    private fun withoutTable(key: String): String {
        val json = validJson()
        val without = Regex(""",?\s*"$key"\s*:\s*\[.*?]\s*}""", RegexOption.DOT_MATCHES_ALL)
            .replace(json, "}")
        assertTrue(
            "the regex must actually remove \"$key\" — a fixture that changed nothing " +
                "would make this test pass for the wrong reason",
            without != json,
        )
        assertTrue(
            "and the key must really be gone",
            !without.contains("\"$key\""),
        )
        return without
    }

    private fun rejected(json: String): RestoreRejected = try {
        BackupJsonReader.read(json, version)
        throw AssertionError(
            "the file must be refused, but it parsed — a refusal that does not happen is a " +
                "successful restore of nothing",
        )
    } catch (e: RestoreRejected) {
        e
    }
}