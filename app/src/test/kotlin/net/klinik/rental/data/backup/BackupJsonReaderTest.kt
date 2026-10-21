package net.klinik.rental.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import net.klinik.rental.data.db.SCHEMA_VERSION
import java.io.File
import java.time.LocalDate

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

    private val version = SCHEMA_VERSION

    /**
     * The day the reader compares a contract's dates against.
     *
     * After every date in [validJson] (`2026-10-01`..`2026-10-05`), so the future-date refusal
     * cannot fire by accident: a test that wants it has to say so explicitly.
     */
    private val AS_OF: LocalDate = LocalDate.of(2026, 10, 31)

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
             "emergencyContact":"08129876543","avatar":"avatar_07"}
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
        val restore = BackupJsonReader.read(validJson(), version, AS_OF)

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
        val preview = BackupJsonReader.read(validJson(), version, AS_OF).preview

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
        val restore = BackupJsonReader.read(emptyTable("payments"), version, AS_OF)

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
        val restore = BackupJsonReader.read(emptyTable("vehicles"), version, AS_OF)

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

    /**
     * A file from a **newer** app is refused, and the refusal says which direction it went.
     *
     * The direction is the whole point: a newer file means the owner should update the app,
     * and an older one means they should take a fresh backup. One message for both would
     * leave the owner with a number and no next step.
     */
    @Test
    fun `a newer file is refused and names its version`() {
        val thrown = rejected(validJson().replace("\"schemaVersion\": $version", "\"schemaVersion\": 99"))

        assertEquals(BackupJsonReader.REASON_VERSION_TOO_NEW, thrown.reason)
        assertEquals(99, thrown.fileVersion)
    }

    /**
     * A file from an app **older than the export** is refused, and says so.
     *
     * A v7 file cannot exist — the export shipped at v8 — so this is a file from somewhere
     * else entirely, and the only true thing the reader can say about it is that it cannot
     * read it. A floor set below the export's would have to invent rows for columns that
     * never existed.
     */
    @Test
    fun `a file older than the export is refused and says so`() {
        val thrown = rejected(validJson().replace("\"schemaVersion\": $version", "\"schemaVersion\": 7"))

        assertEquals(BackupJsonReader.REASON_VERSION_TOO_OLD, thrown.reason)
        assertEquals(7, thrown.fileVersion)
    }

    /**
     * A file from the **previous** version restores, and its missing column takes the value
     * the schema would have given it.
     *
     * The one real older file this app has ever written, so it is the one that has to work.
     * The fixture is a genuine v8 backup — taken from the repository's history, not written
     * by hand — because a hand-written "v8" file would only prove that the reader accepts
     * a file the test built to suit it.
     *
     * So it is read out of git: `git show <commit>:demo-backup.json`, where that commit is
     * the one that added the file, before the avatar column existed. If the file is not
     * there the test fails loudly rather than skipping — a skipped test is not a proof.
     */
    @Test
    fun `a previous version file restores and its new column takes the schema's value`() {
        val v8 = previousVersionDemoFile()

        val restore = BackupJsonReader.read(v8, SCHEMA_VERSION, AS_OF)

        assertEquals("the file's own version is kept", 8, restore.schemaVersion)
        assertEquals("every renter is there", 8, restore.renters.size)
        assertEquals(
            "and every one of them has no avatar, which is the schema's value for it",
            null,
            restore.renters.first().avatar,
        )
        assertTrue(
            "the file really is the older shape — it carries no avatar key at all",
            !v8.contains("\"avatar\""),
        )
    }

    /**
     * A **current** file that omits a column it must carry is still refused.
     *
     * The version only decides which columns *may* be absent, never which *tables* must be
     * there, and never that a current file may be incomplete. A v9 file with no `avatar` key
     * is a corrupt file, and reading it as "no avatar" would turn corruption into data.
     */
    @Test
    fun `a current file missing a column it must carry is refused`() {
        val json = validJson().replace(
            ""","avatar":"avatar_07"""",
            "",
        )

        assertEquals(BackupJsonReader.REASON_BAD_ROW, rejected(json).reason)
    }

    /**
     * The genuine v8 backup, read out of the repository's history.
     *
     * `git show` rather than a checked-in copy, so the fixture cannot drift from the file the
     * app actually wrote — and so the test keeps working when the demo is regenerated.
     */
    private fun previousVersionDemoFile(): String {
        val commit = "4299bba" // the commit that added demo-backup.json, before the avatar column
        // The unit test's working directory is the `app` module, so the repository root is one
        // level up — not two.
        val process = ProcessBuilder(
            "git", "show", "$commit:demo-backup.json",
        ).directory(File("..")).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        assertTrue(
            "the previous-version demo file must be readable from git history — " +
                "run this test from the app module, or check that $commit is reachable: " +
                output.take(200),
            output.contains("\"schemaVersion\": 8"),
        )
        return output
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

    // ------------------------------------------------- the future-date refusal

    /**
     * A contract that ends **after** the day being restored is refused.
     *
     * The same state `endContract` refuses to create, reached through the other writer. Under
     * `isOpen = endDate == null` any stored end date ends the contract on the spot, so restoring
     * one would free the car for a rental still running — and there is no in-app way to clear it
     * afterwards, because `endContract` only accepts an open contract.
     *
     * The active columns are nulled alongside the date because `ContractEntity.init` pins them
     * to `endDate`; leaving them set would refuse the row for a different reason and this test
     * would pass without ever reaching the rule it is about.
     */
    @Test
    fun `a contract ending after today is refused`() {
        val json = validJson()
            .replace(""""activeVehicleId":"v1","activeRenterId":"r1"""", """"activeVehicleId":null,"activeRenterId":null""")
            .replace(""""endDate":null""", """"endDate":"2026-11-15"""")

        val thrown = rejected(json)

        assertEquals(BackupJsonReader.REASON_FUTURE_DATE, thrown.reason)
        assertEquals("contracts", thrown.table)
    }

    /**
     * A settlement marker **after** today is refused too, and for a different harm.
     *
     * `settledDays` closes every charge day at or before the marker, so a future marker restores
     * an app that believes days it has not billed yet are already paid — arrears disappear with
     * nobody having paid. One guard, two failures, so both are asserted.
     */
    @Test
    fun `a settlement marker after today is refused`() {
        val json = validJson().replace(
            """"endDate":null,"settledThroughDate":null""",
            """"endDate":null,"settledThroughDate":"2026-11-15"""",
        )

        val thrown = rejected(json)

        assertEquals(BackupJsonReader.REASON_FUTURE_DATE, thrown.reason)
        assertEquals("contracts", thrown.table)
    }

    /**
     * Today itself is **accepted** — the boundary the rule turns on.
     *
     * `isAfter`, not `!isBefore`. A contract that ended today is a real contract, and a marker
     * of today is the ordinary settlement. Refusing it would make the app unable to restore a
     * backup taken on the same day the rental ended.
     */
    @Test
    fun `a contract ending today is accepted`() {
        val json = validJson()
            .replace(""""activeVehicleId":"v1","activeRenterId":"r1"""", """"activeVehicleId":null,"activeRenterId":null""")
            .replace(""""endDate":null""", """"endDate":"2026-10-31"""")

        val restore = BackupJsonReader.read(json, version, AS_OF)

        assertEquals(LocalDate.of(2026, 10, 31), restore.contracts.first().endDate)
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

        val settings = BackupJsonReader.read(json, version, AS_OF).settings

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
        BackupJsonReader.read(json, version, AS_OF)
        throw AssertionError(
            "the file must be refused, but it parsed — a refusal that does not happen is a " +
                "successful restore of nothing",
        )
    } catch (e: RestoreRejected) {
        e
    }
}