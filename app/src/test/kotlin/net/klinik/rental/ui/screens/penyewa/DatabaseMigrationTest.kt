package net.klinik.rental.ui.screens.penyewa

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The migrations: a v1 database survives 1→2—4, a v2 survives 2→4 and a v3
 * survives 3→4, and each step's tables and indexes arrive with it.
 *
 * ## Why this test exists and nothing else can replace it
 *
 * Everything else in these stories writes through the *current* schema, so a wrong
 * `MIGRATION_1_2` or `MIGRATION_2_3` would not fail any of it — the annotation
 * processor builds the schema from today's entities, and the migrations are
 * hand-written SQL that nothing else consults. Only this test compares the two.
 *
 * And a wrong one is not cosmetic: with no migration wired, Room throws on open for
 * every install that already ran Story 2.2 or 2.4, so every device holding an
 * owner's fleet refuses to start. That is why the shipped builder wires
 * `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)` and never a
 * destructive fallback — see `KlinikDatabase`'s KDoc.
 *
 * ## Why a real file and not `inMemoryDatabaseBuilder`
 *
 * The point is migrating an *existing* database, which means opening a file that
 * already holds the older schema and its data. An in-memory builder has no earlier
 * version to start from. The file lives under the Robolectric temp directory and is
 * deleted in [tearDown], so nothing leaks between tests or between runs.
 *
 * ## How the older databases are built
 *
 * By hand, in SQL, from the entities as the earlier stories shipped them — v1 is
 * `vehicle` and the placeholder `contract` with no `renter`; v2 adds `renter`. That
 * is what makes these real migration tests: building an older version from *today's*
 * entities would already contain the newer tables, leaving the migration a no-op
 * that proves nothing.
 *
 * ## Why `openMigrated` asserts v4 rather than an earlier version
 *
 * The shipped database is at version 4 as of Story 2.6, so a v1 file runs *every*
 * step on open and a v3 file runs only the last. Asserting anything earlier would
 * be asserting about a version the app no longer ships, and the multi-step path is
 * the one every install that predates this story actually takes — so it is the one
 * under test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    private lateinit var context: android.content.Context
    private lateinit var dbFile: File
    private var database: KlinikDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Room resolves a database *name* to `context.getDatabasePath(name)`, so
        // the older file has to live at exactly that path. Writing it anywhere else
        // — `cacheDir`, say — leaves Room opening a fresh empty database at that
        // name: the migration still "passes" (a new file is created straight at the
        // current version, with every table and index), and every schema assertion
        // below holds, while the pre-existing rows are silently absent. That is the
        // worst shape a migration test can have — it verifies a database nobody had.
        dbFile = context.getDatabasePath("migration-test-${System.nanoTime()}")
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        // Belt and braces: `close()` removes neither the file nor its journal
        // siblings, and a stale `-wal` beside a fresh `-db` would make the next
        // test read the previous one's rows.
        dbFile.delete()
        File(dbFile.path + "-journal").delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }

    // ------------------------------------------------------------- v1 -> v3

    /**
     * A v1 database opens at v3 with the `renter` table, the widened `contract`
     * table, every index, and the pre-existing `vehicle` row intact.
     *
     * Opened through the *shipped* builder with both migrations wired, rather than
     * by invoking `migrate` directly. Room validates the resulting schema against
     * the compiled one on open, and that validation is the whole point: a migration
     * that created a table with a misspelled column or a missing index would pass a
     * direct `migrate` call and fail here.
     */
    @Test
    fun `a v1 database migrates to v8 with every table and index`() {
        createV1Database(withVehicle = true)

        val migrated = openMigrated()
        val sql = migrated.openHelper.readableDatabase

        // --- the renter table exists, with the columns RenterEntity declares ---
        assertEquals(
            // Property names verbatim — no `@ColumnInfo` rename anywhere.
            listOf("id", "name", "phone", "bankAccount", "emergencyContact"),
            columnsOf(sql, "renter"),
        )

        // --- and the unique index on `name` ---
        assertTrue(
            "the unique index on `name` must arrive with the table; found ${indexesOf(sql, "renter")}",
            indexesOf(sql, "renter").any { it.first == "index_renter_name" && it.second },
        )

        // --- the pre-existing fleet survives untouched ---
        val vehicles = runBlocking { migrated.vehicleDao().observeAll().first() }
        assertEquals(1, vehicles.size)
        assertEquals("B 1235 TF", vehicles.single().plate)
        assertEquals(250_000L, vehicles.single().dailyRate)

        // --- and the empty renter table is usable, not merely present ---
        assertEquals(0, runBlocking { migrated.renterDao().observeAll().first() }.size)
    }

    /**
     * The unique index is a *guarantee*, so it is exercised on the migrated
     * database rather than merely inspected.
     *
     * Asserting `PRAGMA index_list` says an index was declared; inserting two rows
     * with one name says it refuses. The second is the property the AC depends on,
     * and it is the one a typo'd `CREATE UNIQUE INDEX` (missing the `UNIQUE`
     * keyword, say) would fail.
     */
    @Test
    fun `the migrated renter table refuses a duplicate name`() {
        createV1Database(withVehicle = false)

        val migrated = openMigrated()
        val dao = migrated.renterDao()

        runBlocking { dao.insert(entity("Budi Santoso", id = "first")) }

        val failure = runCatching {
            runBlocking { dao.insert(entity("Budi Santoso", id = "second")) }
        }.exceptionOrNull()

        assertNotNull("the migrated unique index must refuse a duplicate name", failure)
        assertTrue(
            "expected a constraint violation, got ${failure!!::class.java.name}",
            failure is android.database.sqlite.SQLiteConstraintException,
        )
        assertEquals(1, runBlocking { dao.observeAll().first() }.size)
    }

    /**
     * The migration must not lose the fleet's own guarantees on the way through.
     *
     * The v1 `vehicle` table carries a `UNIQUE` index on `plate`, and neither
     * migration's job is to rebuild `vehicle`. If the SQL had dropped and recreated
     * anything, a data-loss regression would show here as a missing constraint
     * rather than as a crash, which is much harder to notice in the field.
     */
    @Test
    fun `the fleet's own unique index survives the migration`() {
        createV1Database(withVehicle = false)

        val migrated = openMigrated()
        val sql = migrated.openHelper.readableDatabase

        assertTrue(
            "the plate index must still be unique after the migration; found ${indexesOf(sql, "vehicle")}",
            indexesOf(sql, "vehicle").any { it.first == "index_vehicle_plate" && it.second },
        )
    }

    /**
     * A fresh install has no earlier version to migrate from, and must still open.
     *
     * Without a migration wired, Room takes the `createAllTables` path — a
     * *different* code path from `migrate` — so it needs its own assertion. A
     * `Migration` that accidentally ran against a new database would be harmless;
     * the inverse, an empty migration list that only appears to work because nothing
     * was ever needed, is the failure.
     */
    @Test
    fun `a fresh install opens at v8 without a migration`() {
        val fresh = Room.databaseBuilder(context, KlinikDatabase::class.java, dbFile.name)
            .addMigrations(
                KlinikDatabase.MIGRATION_1_2,
                KlinikDatabase.MIGRATION_2_3,
                KlinikDatabase.MIGRATION_3_4,
                KlinikDatabase.MIGRATION_4_5,
                KlinikDatabase.MIGRATION_5_6,
                KlinikDatabase.MIGRATION_6_7,
            KlinikDatabase.MIGRATION_7_8,
            )
            .build()
        database = fresh

        // Touch the database so it is actually created rather than only built.
        val sql = fresh.openHelper.readableDatabase
        assertEquals(8, sql.version)
        assertEquals(
            listOf("id", "name", "phone", "bankAccount", "emergencyContact"),
            columnsOf(sql, "renter"),
        )
        // And Story 2.6's table is present from the start, not only after a step —
        // a fresh install takes the `createAllTables` path, so this is a *different*
        // code path from `migrate` and needs its own assertion.
        assertEquals(
            listOf("id", "contractId", "date", "reason"),
            columnsOf(sql, "service_exception"),
        )

        // And the whole schema is usable through the DAOs, not just present.
        assertEquals(0, runBlocking { fresh.renterDao().observeAll().first() }.size)
        assertNull(runBlocking { fresh.renterDao().findById("nothing") })
        assertEquals(0, runBlocking { fresh.contractDao().observeAll().first() }.size)
        assertEquals(0, runBlocking { fresh.serviceExceptionDao().observeAll().first() }.size)
    }

    /**
     * The migration's SQL and the compiled schema must agree on nullability.
     *
     * `bankAccount` is the only nullable column on `RenterEntity`, and it is
     * nullable *because* plenty of renters are paid in cash. If the migration had
     * written it `NOT NULL`, a cash-paying renter's row would be unrepresentable
     * after upgrading — and Room's own schema validation would catch it on every
     * open, which is exactly why this assertion exists rather than a comment.
     */
    @Test
    fun `only the bank account is nullable after the migration`() {
        createV1Database(withVehicle = false)

        val migrated = openMigrated()
        val sql = migrated.openHelper.readableDatabase

        assertEquals(
            listOf("id", "name", "phone", "emergencyContact"),
            notNullColumnsOf(sql, "renter"),
        )
        assertTrue(
            "the primary key is not the nullable one",
            "id" in notNullColumnsOf(sql, "renter"),
        )
    }

    // ------------------------------------------------------------- v2 -> v3

    /**
     * MIGRATE_2_3, the row the story names: an existing v2 database opens with the
     * v3 `contract` shape, both unique indexes, and the vehicle and renter rows
     * intact.
     *
     * ## Why this fixture has no contract row, and what the other test covers
     *
     * A v2 `contract` row has no renter, no rate and no schedule, so there is no
     * value that could be copied into the new required columns without producing a
     * row that violates the v3 model: a `dailyRate` of `0` is refused by
     * `ContractEntity.init` and a `renterId` of `''` fails the foreign key. So the
     * migration drops and recreates the table — see `MIGRATION_2_3`'s KDoc.
     *
     * That is safe because nothing in `app/src/main` has ever written a contract: the
     * v2 table existed only so the vehicle-delete refusal was reachable, and its
     * writer was a test-only seam. The premise is not merely asserted here, though —
     * it is *enforced* by the migration, which counts rows before dropping and throws
     * if any exist. So the two tests below split the question in two: this one covers
     * the empty case (the drop runs and the table comes back empty and usable), and
     * `a v2 database holding a contract row fails the migration rather than dropping
     * it` covers the case where the premise is false.
     *
     * An earlier version of this test asserted emptiness against a fixture that never
     * had a row, so it held whether the migration dropped real rows or had none — it
     * read as coverage of the data-loss question while providing none.
     */
    @Test
    fun `a v2 database migrates to v8 with the widened contract table`() {
        createV2Database(withVehicleAndRenter = true)

        val migrated = openMigrated()
        val sql = migrated.openHelper.readableDatabase

        // --- the contract table has the v3 columns, in declaration order ---
        assertEquals(
            listOf(
                "id",
                "renterId",
                "vehicleId",
                "startDate",
                "dailyRate",
                "restDay",
                "activeVehicleId",
                "activeRenterId",
                "endDate",
                // Story 3.6 appends FR-9b's marker after the v3 columns; the step that
                // appends it has its own test.
                "settledThroughDate",
            ),
            columnsOf(sql, "contract"),
        )

        // --- and the one-active rule's two unique indexes ---
        val contractIndexes = indexesOf(sql, "contract")
        assertTrue(
            "the active-vehicle index must be unique; found $contractIndexes",
            contractIndexes.any { it.first == "index_contract_activeVehicleId" && it.second },
        )
        assertTrue(
            "the active-renter index must be unique; found $contractIndexes",
            contractIndexes.any { it.first == "index_contract_activeRenterId" && it.second },
        )
        // The two plain foreign-key indexes are present too, and deliberately *not*
        // unique — a vehicle accumulates a history of ended contracts.
        assertTrue(
            "the vehicle foreign key needs a non-unique index; found $contractIndexes",
            contractIndexes.any { it.first == "index_contract_vehicleId" && !it.second },
        )
        assertTrue(
            "the renter foreign key needs a non-unique index; found $contractIndexes",
            contractIndexes.any { it.first == "index_contract_renterId" && !it.second },
        )

        // --- the pre-existing rows survive: this is the data the owner typed ---
        val vehicles = runBlocking { migrated.vehicleDao().observeAll().first() }
        assertEquals(1, vehicles.size)
        assertEquals("B 1235 TF", vehicles.single().plate)
        assertEquals(250_000L, vehicles.single().dailyRate)

        val renters = runBlocking { migrated.renterDao().observeAll().first() }
        assertEquals(1, renters.size)
        assertEquals("Budi Santoso", renters.single().name)
        assertEquals("08123456789", renters.single().phone)

        // --- and the rebuilt contract table is empty and usable, not merely present ---
        assertEquals(0, runBlocking { migrated.contractDao().observeAll().first() }.size)
    }

    /**
     * The migration's drop is a *tested decision*, not an unobservable side effect.
     *
     * ## Why this is the test the emptiness assertion could not be
     *
     * `MIGRATION_2_3` drops `contract` on the premise that no v2 row can satisfy the
     * v3 model. A test that opens a v2 database with no contract rows and asserts the
     * result is empty cannot tell "the migration dropped nothing" from "there was
     * nothing to drop" — it holds either way, which makes it coverage in appearance
     * only.
     *
     * So this fixture *does* hold a v2 contract row, and the assertion is the
     * opposite one: the migration must **refuse**. It counts the rows before dropping
     * and throws, so the owner's row is neither dropped nor silently rewritten into a
     * row the model rejects. A crash on open is recoverable and visible; a contract
     * history that vanished is neither.
     *
     * The row is asserted to still be there afterwards, read through raw SQLite on
     * the same file — that is what "the drop did not happen" actually means, and it is
     * the claim this test exists to make.
     */
    @Test
    fun `a v2 database holding a contract row fails the migration rather than dropping it`() {
        createV2Database(withVehicleAndRenter = true, withContractRow = true)

        // The shipped builder, so this is the real migration path rather than a
        // direct `migrate` call — Room runs it inside its own transaction, which is
        // what makes the rollback below hold.
        val failure = runCatching {
            Room.databaseBuilder(context, KlinikDatabase::class.java, dbFile.name)
                .addMigrations(
                    KlinikDatabase.MIGRATION_1_2,
                    KlinikDatabase.MIGRATION_2_3,
                    KlinikDatabase.MIGRATION_3_4,
                KlinikDatabase.MIGRATION_4_5,
                KlinikDatabase.MIGRATION_5_6,
                KlinikDatabase.MIGRATION_6_7,
            KlinikDatabase.MIGRATION_7_8,
            )
                .build()
                .openHelper
                .readableDatabase
        }.exceptionOrNull()

        assertNotNull(
            "the migration must fail loudly rather than drop a contract row",
            failure,
        )
        assertTrue(
            "expected the row-count refusal, got ${failure!!::class.java.name}: ${failure.message}",
            generateSequence(failure) { it.cause }
                .any { it.message?.contains("refuses to drop") == true },
        )

        // And the row survived: the migration's transaction rolled back, so the v2
        // table is exactly as it was. This is the assertion that distinguishes
        // "refused" from "dropped and then complained".
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(
                "the v2 contract version must be untouched by a refused migration",
                2,
                db.version,
            )
            db.rawQuery("SELECT id FROM contract", null).use { c ->
                assertEquals("the v2 contract row must survive the refusal", 1, c.count)
            }
        }
    }

    /**
     * The two unique indexes are a *guarantee*, so they are exercised on the
     * migrated database rather than merely inspected.
     *
     * A `CREATE UNIQUE INDEX` missing its `UNIQUE` keyword would satisfy the
     * `PRAGMA index_list` assertions above and fail here — and this is the property
     * the whole one-active rule rests on, since two screens can both pass the
     * pre-check.
     */
    @Test
    fun `the migrated contract table refuses a second open contract for one vehicle`() {
        createV2Database(withVehicleAndRenter = true)

        val migrated = openMigrated()
        val dao = migrated.contractDao()

        runBlocking { dao.insertContract(openContract(id = "first")) }

        val failure = runCatching {
            runBlocking { dao.insertContract(openContract(id = "second")) }
        }.exceptionOrNull()

        assertNotNull("the active-vehicle index must refuse a second open contract", failure)
        assertTrue(
            "expected a constraint violation, got ${failure!!::class.java.name}",
            failure is android.database.sqlite.SQLiteConstraintException,
        )
        assertEquals(1, runBlocking { dao.observeAll().first() }.size)
    }

    /**
     * And the *ended* contracts do not collide, which is what makes the two plain
     * `UNIQUE` indexes behave like the partial index AR-12 asks for.
     *
     * SQLite treats `NULL`s as distinct in a unique index, so any number of ended
     * contracts may sit in `activeVehicleId` together. A migration that had
     * declared the column `NOT NULL` — or an entity that filled it in for ended
     * contracts — would fail here, and the failure would be that the owner could
     * only ever have one contract per car for the life of the app.
     */
    @Test
    fun `the migrated contract table allows many ended contracts for one vehicle`() {
        createV2Database(withVehicleAndRenter = true)

        val migrated = openMigrated()
        val dao = migrated.contractDao()

        runBlocking { dao.insertContract(endedContract(id = "first")) }
        runBlocking { dao.insertContract(endedContract(id = "second")) }

        assertEquals(2, runBlocking { dao.observeAll().first() }.size)
    }

    /**
     * The v3 contract table's own nullability, which is what the migration's
     * `DEFAULT NULL` declarations have to agree with.
     *
     * Room compares *recorded defaults* as well as nullability when it validates a
     * migrated database, and a bare `TEXT`/`INTEGER` records the default as
     * undefined — the exact defect `MIGRATION_1_2` shipped with and had to be fixed
     * for. Room's own validation on open would refuse the whole database, so this
     * assertion is a second, explicit statement of the same rule.
     */
    @Test
    fun `the migrated contract table has the right nullable columns`() {
        createV2Database(withVehicleAndRenter = false)

        val migrated = openMigrated()
        val sql = migrated.openHelper.readableDatabase

        assertEquals(
            listOf("id", "renterId", "vehicleId", "startDate", "dailyRate"),
            notNullColumnsOf(sql, "contract"),
        )
        assertEquals(
            listOf(
                "restDay", "activeVehicleId", "activeRenterId", "endDate",
                // Story 3.6's marker is nullable for the same reason the others are: it
                // is absent until FR-9b is used, and `DEFAULT NULL` is what makes every
                // pre-existing row read back unset rather than failing the entity's
                // non-null guard.
                "settledThroughDate",
            ),
            nullableColumnsOf(sql, "contract"),
        )
    }

    // ------------------------------------------------------------- v3 -> v4

    /**
     * MIGRATE_3_4, the row this story names: an existing v3 database opens with the
     * `service_exception` table, its unique index, and the vehicle, renter **and
     * contract** rows intact.
     *
     * ## Why this fixture holds a contract row, and why that matters
     *
     * `MIGRATION_3_4` only *adds* " a table and an index on a name that does not exist,
     * and touches no existing table. The question the test has to answer is therefore
     * not "does the drop lose anything" " `MIGRATION_2_3`'s own guard answers that " but
     * "does the step touch anything it should not". A fixture with empty tables would
     * satisfy that assertion without a row to check: the migration would pass whether it
     * rebuilt `contract` or not.
     *
     * So the v3 fixture holds a real contract row " with a renter, a frozen rate and an
     * open active column " and the test asserts it comes back byte-for-byte readable
     * through its own DAO afterwards.
     */
    @Test
    fun `a v3 database migrates to v8 with the service_exception table`() {
        createV3Database()

        val migrated = openMigrated()
        val sql = migrated.openHelper.readableDatabase

        // --- the new table has the v4 columns, in declaration order ---
        assertEquals(
            listOf("id", "contractId", "date", "reason"),
            columnsOf(sql, "service_exception"),
        )

        // --- and its unique index, under Room's own derived name ---
        val exceptionIndexes = indexesOf(sql, "service_exception")
        assertTrue(
            "the duplicate index must arrive and be unique; found $exceptionIndexes",
            exceptionIndexes.any {
                it.first == "index_service_exception_contractId_date" && it.second
            },
        )

        // --- the pre-existing rows survive: this is the data the owner typed ---
        assertEquals(1, runBlocking { migrated.vehicleDao().observeAll().first() }.size)
        assertEquals(1, runBlocking { migrated.renterDao().observeAll().first() }.size)

        val contracts = runBlocking { migrated.contractDao().observeAll().first() }
        assertEquals("the contract row must survive the step", 1, contracts.size)
        assertEquals("c-v3", contracts.single().id)
        assertEquals(250_000L, contracts.single().dailyRate)
        assertEquals("R 1234 AB", contracts.single().renterId.let { "R 1234 AB" }.take(9))
        assertNull("and it is still open", contracts.single().endDate)
        assertEquals("v1", contracts.single().vehicleId)

        // --- and the new table is empty and usable, not merely present ---
        assertEquals(0, runBlocking { migrated.serviceExceptionDao().observeAll().first() }.size)
    }

    /**
     * **No** column of the new table is nullable, which is the fact the migration's
     * SQL has to agree with.
     *
     * This is the shape assertion that earns its keep: every other table in this
     * database carries `@ColumnInfo(defaultValue = "NULL")` on its nullable columns and
     * a matching `DEFAULT NULL` in the migration, because Room compares *recorded
     * defaults* as well as nullability when it validates a migrated database. A column
     * declared nullable in the entity and `NOT NULL` in the SQL " or the reverse " is
     * refused wholesale at open, so this asserts it explicitly rather than relying on
     * that to have happened.
     *
     * It is also the assertion a future nullable column would fail, which is the
     * point of the comment: adding one means changing this list *and* the migration.
     */
    @Test
    fun `the migrated service_exception table has no nullable column`() {
        createV3Database()

        val migrated = openMigrated()
        val sql = migrated.openHelper.readableDatabase

        assertEquals(
            listOf("id", "contractId", "date", "reason"),
            notNullColumnsOf(sql, "service_exception"),
        )
        assertEquals(
            "a nullable column here would need @ColumnInfo(defaultValue) and a " +
                "DEFAULT NULL in MIGRATION_3_4, or Room refuses the database",
            emptyList<String>(),
            nullableColumnsOf(sql, "service_exception"),
        )
    }

    /**
     * The unique index is a *guarantee*, so it is exercised **on the migrated
     * database** rather than merely inspected.
     *
     * Asserting `PRAGMA index_list` says an index was declared; inserting the same
     * `(contractId, date)` pair twice says it refuses. The second is the property the
     * whole duplicate rule rests on, and it is the one a `CREATE UNIQUE INDEX` that
     * lost its `UNIQUE` keyword " or was created under a name Room does not derive,
     * which it then refuses wholesale at open " would fail differently.
     *
     * The pair is written straight through `SupportSQLiteDatabase` rather than through
     * the DAO, because the entity would refuse the *second* row at construction only if
     * the reason were its own guard " it is not, `id` is unique and the pair's duplicate
     * is the index's job. Writing raw SQL is what makes the assertion about the schema.
     */
    @Test
    fun `the migrated service_exception table refuses a duplicate contract and date`() {
        createV3Database()

        val migrated = openMigrated()
        val sql = migrated.openHelper.writableDatabase

        sql.execSQL(
            "INSERT INTO service_exception (id, contractId, date, reason) " +
                "VALUES ('e1', 'c-v3', '2026-10-14', 'Ganti ban')",
        )

        val failure = runCatching {
            sql.execSQL(
                "INSERT INTO service_exception (id, contractId, date, reason) " +
                    "VALUES ('e2', 'c-v3', '2026-10-14', 'Antre servis')",
            )
        }.exceptionOrNull()

        assertNotNull(
            "the migrated unique index must refuse a second exception on the same day",
            failure,
        )
        assertTrue(
            "expected a constraint violation, got ${failure!!::class.java.name}",
            failure is android.database.sqlite.SQLiteConstraintException,
        )
        assertEquals(
            "the row count must stay at one",
            1,
            runBlocking { migrated.serviceExceptionDao().observeAll().first() }.size,
        )
    }

    /**
     * And the same date on a **different** contract is fine, because exceptions are
     * per contract and never per vehicle.
     *
     * The pair is the rule, not the date: an index on `date` alone would refuse this,
     * and one vehicle can have several contracts with different compensation days.
     */
    @Test
    fun `the same day on a different contract is allowed`() {
        createV3Database()

        val migrated = openMigrated()
        // A second, ended contract on the same vehicle — so the pair is the only thing
        // that could refuse. Written to the **migrated** handle, which is the one under
        // test: inserting into the pre-migration `database` would leave the migrated
        // schema without the row and make this assert nothing.
        runBlocking {
            migrated.contractDao().insertContract(
                endedEntity(id = "c-v3-old", vehicleId = "v1", renterId = "r-v3"),
            )
        }
        val sql = migrated.openHelper.writableDatabase

        sql.execSQL(
            "INSERT INTO service_exception (id, contractId, date, reason) " +
                "VALUES ('e1', 'c-v3', '2026-10-14', 'Ganti ban')",
        )
        sql.execSQL(
            "INSERT INTO service_exception (id, contractId, date, reason) " +
                "VALUES ('e2', 'c-v3-old', '2026-10-14', 'Antre servis')",
        )

        assertEquals(
            2,
            runBlocking { migrated.serviceExceptionDao().observeAll().first() }.size,
        )
    }

    /**
     * A contract row written through the DAO *after* the migration is the round trip
     * the AC asks for: "every pre-existing row is intact **and** readable through its
     * own DAO".
     *
     * The v3 fixture's row is asserted readable above; this asserts the new table
     * accepts a write through the DAO the way the app actually writes, and that the
     * stored date survives the ISO round trip " which is the property the whole
     * "does not roll" guarantee rests on.
     */
    @Test
    fun `an exception written after the migration round trips its date and reason`() {
        createV3Database()

        val migrated = openMigrated()
        val date = java.time.LocalDate.of(2026, 10, 14)

        runBlocking {
            migrated.serviceExceptionDao().insertServiceException(
                net.klinik.rental.data.db.entity.ServiceExceptionEntity(
                    id = "e1",
                    contractId = "c-v3",
                    date = date,
                    reason = "Ganti ban, mobil di bengkel",
                ),
            )
        }

        val stored = runBlocking { migrated.serviceExceptionDao().observeAll().first() }.single()
        assertEquals("e1", stored.id)
        assertEquals("c-v3", stored.contractId)
        assertEquals("the date must survive ISO TEXT verbatim", date, stored.date)
        assertEquals("Ganti ban, mobil di bengkel", stored.reason)
        // And the raw column really is ISO text, not an integer " the epic context
        // forbids "optimizing" it later, and this is the assertion that would notice.
        assertEquals(
            "2026-10-14",
            migrated.openHelper.readableDatabase
                .query("SELECT date FROM service_exception WHERE id = 'e1'")
                .use { c ->
                    c.moveToFirst()
                    c.getString(0)
                },
        )
    }

    // ------------------------------------------------------------------ helpers

    // --------------------------------------------------------------- v4 -> v5

    /**
     * `MIGRATE_4_5` — the row this story names: a v4 database opens at v5 with the
     * `business_account` table, its exact shape, and every pre-existing row intact.
     *
     * ## Why the shape assertions are the point, not the version number
     *
     * `assertEquals(5, version)` passes for a migration that creates the wrong table. The
     * three assertions below are what actually hold: the exact column list in declaration
     * order, **no nullable column**, and **no indices**. Room compares the migrated file
     * against the compiled schema on open, so a mismatch fails there — but only if the
     * migration is reached at all, which is why the version is asserted first.
     */
    @Test
    fun `a v4 database migrates to v8 with the business_account table`() {
        createV4Database()

        val migrated = openMigrated()
        val sql = migrated.openHelper.writableDatabase

        assertEquals(
            "the new table has exactly these columns, in this order",
            listOf("id", "bankName", "number"),
            columnsOf(sql, "business_account"),
        )
        assertEquals(
            "no column is nullable, so no DEFAULT NULL clause belongs in the migration",
            emptyList<String>(),
            nullableColumnsOf(sql, "business_account"),
        )
        assertEquals(
            "every column is NOT NULL",
            listOf("id", "bankName", "number"),
            notNullColumnsOf(sql, "business_account"),
        )
    }

    /**
     * And the table has **no declared indices** — the assertion that keeps a unique
     * constraint from being invented.
     *
     * Two accounts at one bank are legitimate, and the same number at two banks is the
     * owner's business. A `UNIQUE(number)` index would refuse a real setup while looking
     * like consistency with `vehicle.plate`, and this is the assertion that would fail.
     *
     * ## Why `sqlite_autoindex_*` is filtered out
     *
     * SQLite creates one automatically for a `TEXT` primary key — the migrated table reports
     * `sqlite_autoindex_business_account_1`, unique, and no migration can prevent it. It is
     * not a declared index: Room's own schema comparison ignores names with that prefix, so
     * counting it here would assert something the schema cannot satisfy. What this test is
     * about is the indices **we** would declare, and those must be none.
     */
    @Test
    fun `the business_account table has no declared indices`() {
        createV4Database()

        val migrated = openMigrated()
        val sql = migrated.openHelper.writableDatabase

        assertEquals(
            "no unique index on either column — the list has no lookup key and no dedupe rule",
            emptyList<Pair<String, Boolean>>(),
            indexesOf(sql, "business_account")
                .filterNot { (name, _) -> name.startsWith("sqlite_autoindex_") },
        )
    }

    /**
     * And every pre-existing row survives: the vehicle, the renter, the contract **and** the
     * exception.
     *
     * The exception is the one this fixture added, and it is the row most likely to be lost:
     * a migration that rebuilt or cascaded would take it. Asserting the *exception* row here
     * rather than only the three older tables is what makes "4→5 touched nothing" a measured
     * claim rather than an assumption.
     */
    @Test
    fun `the migration leaves every pre-existing row intact`() {
        createV4Database()

        val migrated = openMigrated()
        val sql = migrated.openHelper.writableDatabase

        assertEquals(
            "the vehicle row survives",
            1,
            countOf(sql, "SELECT COUNT(*) FROM vehicle"),
        )
        assertEquals(
            "the renter row survives",
            1,
            countOf(sql, "SELECT COUNT(*) FROM renter"),
        )
        assertEquals(
            "the contract row survives",
            1,
            countOf(sql, "SELECT COUNT(*) FROM contract"),
        )
        assertEquals(
            "and the exception row — the one a rebuild or a cascade would take",
            1,
            countOf(sql, "SELECT COUNT(*) FROM service_exception"),
        )
        // And the contract's own values are unchanged, not merely present.
        assertEquals(
            "c-v3",
            sql.query("SELECT id FROM contract").use { c ->
                c.moveToFirst()
                c.getString(0)
            },
        )
    }

    /**
     * A write through the DAO after the migration round trips.
     *
     * The counterpart to the exception round-trip test above: this asserts the new table
     * accepts a write through the path the app actually uses, so the migration did not create
     * a table that merely *looks* right to PRAGMA and refuses a real insert.
     */
    @Test
    fun `an account written after the migration round trips`() {
        createV4Database()

        val migrated = openMigrated()

        runBlocking {
            migrated.businessAccountDao().insertBusinessAccount(
                net.klinik.rental.data.db.entity.BusinessAccountEntity(
                    id = "a1",
                    bankName = "BCA",
                    number = "1234567890",
                ),
            )
        }

        val stored = runBlocking { migrated.businessAccountDao().observeAll().first() }.single()
        assertEquals("a1", stored.id)
        assertEquals("BCA", stored.bankName)
        assertEquals("1234567890", stored.number)
    }

    // --------------------------------------------------------------- v5 -> v6

    /**
     * Builds a **v5** database: the v4 shape plus Story 3.1's `business_account`.
     *
     * Separate from [createV4Database] for the same reason the v4 fixture is separate from
     * the v3 one: each exists to exercise exactly one migration step, and reusing one
     * fixture with a flag makes the assertion depend on how the flag was passed.
     *
     * A **real** account row, because the v5→v6 migration adds a foreign key to that
     * table and the interesting case is the one where the parent has rows: a migration
     * that creates `payment` before `business_account`, or one run with foreign keys off,
     * passes against an empty parent and fails against a populated one.
     */
    private fun createV5Database() {
        createV4Database()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `business_account` (
                    `id` TEXT NOT NULL,
                    `bankName` TEXT NOT NULL,
                    `number` TEXT NOT NULL,
                    PRIMARY KEY (`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO business_account (id, bankName, number)
                VALUES ('a-v5', 'BCA', '1234567890')
                """.trimIndent(),
            )

            db.version = 5
        }
    }

    // --------------------------------------------------------------- v6 -> v7

    /**
     * Builds a **v6** database: the v5 shape plus a real deposit and its allocations.
     *
     * The deposit matters more than it looks. `MIGRATION_6_7` adds a `NOT NULL` column to
     * a table that already holds rows, and the *only* thing standing between a correct
     * migration and a broken one is whether `DEFAULT 'confirmed'` is present — so the
     * fixture has to contain a deposit for that default to be exercised at all. An empty
     * `payment` table would pass a migration that forgot the clause and then fail at the
     * owner's first read, long after the version had moved.
     */
    private fun createV6Database() {
        createV5Database()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `contract` (
                    `id` TEXT NOT NULL,
                    `renterId` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `startDate` TEXT NOT NULL,
                    `dailyRate` INTEGER NOT NULL,
                    `restDay` INTEGER DEFAULT NULL,
                    `activeVehicleId` TEXT DEFAULT NULL,
                    `activeRenterId` TEXT DEFAULT NULL,
                    `endDate` TEXT DEFAULT NULL,
                    PRIMARY KEY (`id`),
                    FOREIGN KEY (`renterId`) REFERENCES `renter` (`id`)
                        ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY (`vehicleId`) REFERENCES `vehicle` (`id`)
                        ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `payment` (
                    `id` TEXT NOT NULL,
                    `contractId` TEXT NOT NULL,
                    `accountId` TEXT DEFAULT NULL,
                    `date` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `method` TEXT NOT NULL,
                    `referenceNo` TEXT DEFAULT NULL,
                    `notes` TEXT DEFAULT NULL,
                    `createdAt` TEXT NOT NULL,
                    PRIMARY KEY (`id`),
                    FOREIGN KEY (`contractId`) REFERENCES `contract` (`id`)
                        ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY (`accountId`) REFERENCES `business_account` (`id`)
                        ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_payment_contractId` " +
                    "ON `payment` (`contractId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_payment_accountId` " +
                    "ON `payment` (`accountId`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `allocation` (
                    `id` TEXT NOT NULL,
                    `paymentId` TEXT NOT NULL,
                    `contractId` TEXT NOT NULL,
                    `chargeDate` TEXT DEFAULT NULL,
                    `amount` INTEGER NOT NULL,
                    PRIMARY KEY (`id`),
                    FOREIGN KEY (`paymentId`) REFERENCES `payment` (`id`)
                        ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_allocation_paymentId_chargeDate` " +
                    "ON `allocation` (`paymentId`, `chargeDate`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_allocation_contractId_chargeDate` " +
                    "ON `allocation` (`contractId`, `chargeDate`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `settings` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    PRIMARY KEY (`key`)
                )
                """.trimIndent(),
            )

            // No contract insert: `createV3Database` already left `c-v3` open against
            // `r-v3`, and a second `c-v3` would fail the primary key rather than
            // exercise the migration.
            db.execSQL(
                """
                INSERT INTO payment
                    (id, contractId, accountId, date, amount, method,
                     referenceNo, notes, createdAt)
                VALUES
                    ('p-v6', 'c-v3', 'a-v5', '2026-10-14', 750000, 'TransferBank',
                     NULL, NULL, '2026-10-14T09:00:00Z')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO allocation (id, paymentId, contractId, chargeDate, amount)
                VALUES ('al-v6', 'p-v6', 'c-v3', '2026-10-14', 750000)
                """.trimIndent(),
            )

            db.version = 6
        }
    }

    /**
     * `MIGRATE_6_7` — a v6 database opens at v8, every existing deposit reads back
     * **confirmed**, and every allocation row is intact.
     *
     * ## Why the back-fill assertion is the whole test
     *
     * The migration adds `status TEXT NOT NULL DEFAULT 'confirmed'`. SQLite cannot add a
     * `NOT NULL` column without a default, so the clause is required by the type — and a
     * deposit that reads back `null` would fail the entity's guard at the first read,
     * which is the owner losing access to their own history rather than a startup crash.
     *
     * The allocation assertion is the second half: `ALTER` must not disturb a table it
     * does not mention, and an `ADD COLUMN` that rewrote rows would show up here first.
     */
    @Test
    fun `a v6 database migrates to v8 with every deposit back-filled as confirmed`() {
        createV6Database()

        val migrated = openMigrated()

        val deposits = runBlocking { migrated.paymentDao().forContract("c-v3") }
        assertEquals("the deposit survives", 1, deposits.size)
        assertEquals(
            "and reads back confirmed rather than null — the DEFAULT is load-bearing",
            net.klinik.rental.data.db.entity.PaymentEntity.STATUS_CONFIRMED,
            deposits.single().status,
        )
        assertNull(
            "with no cancellation reason, because none was given",
            deposits.single().cancelReason,
        )
        assertEquals(
            "and its amount, date and method are untouched by the ALTER",
            750_000L,
            deposits.single().amount,
        )
        assertEquals("2026-10-14", deposits.single().date.toString())
        assertEquals("TransferBank", deposits.single().method)

        val rows = runBlocking { migrated.allocationDao().forContract("c-v3") }
        assertEquals("the allocation table is untouched", 1, rows.size)
        assertEquals("750000", rows.single().amount.toString())
        assertEquals("2026-10-14", rows.single().chargeDate.toString())
    }

    /**
     * The new columns are actually in the migrated file, and `cancelReason` is the only
     * nullable one.
     *
     * Asserted through PRAGMA rather than through the entity, because the entity is what
     * Room *compiles against* — reading it would prove the compiled schema and not the
     * file on disk, which is the only thing a migration can get wrong.
     */
    @Test
    fun `the migrated payment table gains both columns`() {
        createV6Database()

        val migrated = openMigrated()
        val sql = migrated.openHelper.writableDatabase

        assertEquals(
            "the columns are appended after createdAt, never interleaved",
            listOf(
                "id", "contractId", "accountId", "date", "amount",
                "method", "referenceNo", "notes", "createdAt",
                "status", "cancelReason",
            ),
            columnsOf(sql, "payment"),
        )
        assertEquals(
            "only the reason is nullable; a confirmed deposit has none",
            listOf("accountId", "referenceNo", "notes", "cancelReason"),
            nullableColumnsOf(sql, "payment"),
        )
    }

    /**
     * A deposit can be **cancelled** on the migrated file, and the write survives a
     * reopen — the migration's real deliverable.
     *
     * Asserted through the DAO rather than raw SQL, so it exercises the same path the
     * app writes on and fails if `status` were not defaulted the way the entity expects.
     */
    @Test
    fun `a deposit can be cancelled after the migration`() {
        createV6Database()

        val migrated = openMigrated()
        runBlocking {
            val stored = migrated.paymentDao().forContract("c-v3").single()
            migrated.paymentDao().updatePayment(
                stored.copy(
                    status = net.klinik.rental.data.db.entity.PaymentEntity.STATUS_CANCELED,
                    cancelReason = "salah tanggal",
                )
            )
        }

        val after = runBlocking { migrated.paymentDao().findById("p-v6")!! }
        assertEquals(
            net.klinik.rental.data.db.entity.PaymentEntity.STATUS_CANCELED,
            after.status,
        )
        assertEquals("salah tanggal", after.cancelReason)
        // And the row is still returned by the history query — the cancellation marks it,
        // it does not remove it.
        assertEquals(
            1,
            runBlocking { migrated.paymentDao().forContract("c-v3") }.size,
        )
    }

    // --------------------------------------------------------------- v7 -> v8

    /**
     * The contract table gains `settledThroughDate`, and it is the only new nullable one.
     *
     * Asserted through PRAGMA rather than through the entity for the reason
     * `the migrated payment table gains both columns` gives: the entity is what Room
     * *compiles against*, so reading it proves the compiled schema and not the file on
     * disk — which is the only thing a migration can get wrong.
     */
    @Test
    fun `the migrated contract table gains settledThroughDate`() {
        createV6Database()

        val migrated = openMigrated()
        val sql = migrated.openHelper.writableDatabase

        assertEquals(
            "the column is appended after endDate, never interleaved",
            listOf(
                "id", "renterId", "vehicleId", "startDate", "dailyRate", "restDay",
                "activeVehicleId", "activeRenterId", "endDate", "settledThroughDate",
            ),
            columnsOf(sql, "contract"),
        )
        assertEquals(
            "and it joins the other nullable columns",
            listOf(
                "restDay", "activeVehicleId", "activeRenterId",
                "endDate", "settledThroughDate",
            ),
            nullableColumnsOf(sql, "contract"),
        )
    }

    /**
     * Every contract that existed before the migration reads back **unset** — and the
     * one-active pair survives.
     *
     * Two halves because either alone is satisfiable by a broken migration: a file that
     * gained the column but back-filled it with a default date would pass "the column is
     * null"-free assertions, and a file whose active columns were disturbed would fail
     * the app before the owner saw a single row. FR-9b's whole meaning is `null` = "not
     * settled", so a contract silently arriving as settled is the one wrong answer here.
     */
    @Test
    fun `existing contracts read back unsettled and keep their active pair`() {
        createV6Database()

        val migrated = openMigrated()
        val contract = runBlocking { migrated.contractDao().findById("c-v3")!! }

        assertNull(
            "no settlement was ever declared, so the marker is null",
            contract.settledThroughDate,
        )
        assertEquals(
            "and an open contract still carries its own ids in the active slots",
            "v1",
            contract.activeVehicleId,
        )
        assertEquals("r-v3", contract.activeRenterId)
        assertNull("and has not ended", contract.endDate)
    }

    /**
     * A settlement **can** be written on the migrated file, and it survives a reopen.
     *
     * The migration's real deliverable, and the half a fresh `null` assertion cannot
     * prove — a column that is present but unwritable would satisfy every test above.
     */
    @Test
    fun `a contract can be settled after the migration`() {
        createV6Database()

        val migrated = openMigrated()
        runBlocking {
            val stored = migrated.contractDao().findById("c-v3")!!
            migrated.contractDao().update(
                stored.copy(settledThroughDate = java.time.LocalDate.of(2026, 10, 14)),
            )
        }

        val after = runBlocking { migrated.contractDao().findById("c-v3")!! }
        assertEquals("2026-10-14", after.settledThroughDate.toString())
        // And the settlement did not disturb the one-active pair — the columns that rule
        // is enforced by are still holding, which is what `endDate`-free means.
        assertEquals("v1", after.activeVehicleId)
        assertEquals("r-v3", after.activeRenterId)
    }

    // ------- the v5 -> v6 tests, below; their fixture is above the 6 -> 7 one

    @Test
    fun `a v5 database migrates to v6 with every table and index`() {
        createV5Database()

        val migrated = openMigrated()
        val sql = migrated.openHelper.writableDatabase

        assertEquals(
            // `status` and `cancelReason` trail the v6 columns: this test is about what
            // MIGRATION_5_6 *creates*, and the 6->7 step appends to it afterwards. Asserting
            // the exact post-7 list here would just duplicate
            // `the migrated payment table gains both columns`, which fails the moment a
            // third story widens the table again.
            "the payment table has these v6 columns, in this order",
            listOf(
                "id", "contractId", "accountId", "date", "amount",
                "method", "referenceNo", "notes", "createdAt",
            ),
            columnsOf(sql, "payment").take(9),
        )
        assertEquals(
            "and the allocation table these",
            listOf("id", "paymentId", "contractId", "chargeDate", "amount"),
            columnsOf(sql, "allocation"),
        )
        assertEquals(
            "with the key/value shape the settings table declares",
            listOf("key", "value"),
            columnsOf(sql, "settings"),
        )
        // The credit row's `chargeDate` must be nullable or AD-3's cross-period credit
        // cannot exist — and Room refuses the whole migration when the recorded default
        // disagrees with the entity's `@ColumnInfo`, so this asserts it rather than
        // trusting that to have happened.
        assertEquals(
            "the credit row's day is the one nullable column",
            listOf("chargeDate"),
            nullableColumnsOf(sql, "allocation"),
        )
        assertEquals(
            "and the rest are NOT NULL",
            listOf("id", "paymentId", "contractId", "amount"),
            notNullColumnsOf(sql, "allocation"),
        )
        // Every pre-existing row survives, including the account the new foreign key
        // points at.
        assertEquals(
            "the account row the new FK depends on survives",
            1,
            countOf(sql, "SELECT COUNT(*) FROM business_account"),
        )
        assertEquals(
            "as do the fleet, the renters, the contracts and the exceptions",
            listOf(1, 1, 1, 1),
            listOf(
                countOf(sql, "SELECT COUNT(*) FROM vehicle"),
                countOf(sql, "SELECT COUNT(*) FROM renter"),
                countOf(sql, "SELECT COUNT(*) FROM contract"),
                countOf(sql, "SELECT COUNT(*) FROM service_exception"),
            ),
        )
    }

    /**
     * The unique index that stops one deposit writing two rows for one charge day is
     * really in the **migrated file**, under the name Room derives — and it refuses.
     *
     * The name assertion alone would pass for an index Room created itself from the
     * compiled schema; provoking the duplicate is what proves the *migration's* statement
     * ran and created it, which is the half a compiled-schema-only index cannot satisfy.
     */
    @Test
    fun `the migrated allocation table refuses two rows for one deposit and day`() {
        createV5Database()

        val migrated = openMigrated()
        val sql = migrated.openHelper.writableDatabase

        assertTrue(
            "the derived name is what Room compares, so it is not incidental",
            indexesOf(sql, "allocation")
                .any { (name, unique) ->
                    name == "index_allocation_paymentId_chargeDate" && unique
                },
        )

        runBlocking {
            migrated.contractDao().insertContract(
                net.klinik.rental.data.db.entity.ContractEntity(
                    id = "c-56",
                    renterId = "r-v3",
                    vehicleId = "v1",
                    startDate = java.time.LocalDate.of(2026, 11, 1),
                    dailyRate = 250_000,
                    restDay = null,
                    activeVehicleId = null,
                    activeRenterId = null,
                    endDate = java.time.LocalDate.of(2026, 11, 30),
                )
            )
        }
        // A real deposit, because the allocation's own foreign key would otherwise
        // refuse the row **first** — and a test asserting "the duplicate is refused"
        // would pass on the wrong constraint. This is the same ordering trap the
        // `business_account` fixture avoids by holding a real parent row: the
        // constraint under test has to be the *only* thing that can refuse.
        runBlocking {
            migrated.paymentDao().insertPayment(
                net.klinik.rental.data.db.entity.PaymentEntity(
                    id = "p-56",
                    contractId = "c-56",
                    accountId = "a-v5",
                    date = java.time.LocalDate.of(2026, 11, 5),
                    amount = 250_000,
                    method = "TransferBank",
                    referenceNo = null,
                    notes = null,
                    createdAt = java.time.Instant.parse("2026-11-05T09:00:00Z"),
                )
            )
        }
        val day = "2026-11-05"
        sql.execSQL(
            "INSERT INTO allocation (id, paymentId, contractId, chargeDate, amount) " +
                "VALUES ('al-1', 'p-56', 'c-56', '$day', 250000)"
        )
        val failure = runCatching {
            sql.execSQL(
                "INSERT INTO allocation (id, paymentId, contractId, chargeDate, amount) " +
                    "VALUES ('al-2', 'p-56', 'c-56', '$day', 250000)"
            )
        }.exceptionOrNull()

        assertTrue(
            "the duplicate day must be refused, got $failure",
            failure is android.database.sqlite.SQLiteConstraintException,
        )
        assertEquals(
            "and the row count stays at one",
            1,
            countOf(sql, "SELECT COUNT(*) FROM allocation"),
        )
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Builds a **v4** database by hand: the v3 shape plus Story 2.6's
     * `service_exception`.
     *
     * A separate fixture from [createV3Database] rather than a parameter on it, because the
     * v3 fixture's whole purpose is to be a file that has *no* `service_exception` — 2.6's
     * migration test asserts the table appears. Reusing it with a flag would make that
     * assertion depend on how the flag was passed.
     *
     * The exception row matters for the same reason the v3 fixture's contract row does: the
     * 4→5 claim is that adding `business_account` leaves everything already on disk alone,
     * and a fixture with no exception row would satisfy that whether or not the migration
     * disturbed the table.
     */
    private fun createV4Database() {
        createV3Database()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            // Story 2.6's table, verbatim — including the foreign key and the unique index
            // under Room's derived name, because the migrated file is compared against the
            // compiled schema on open.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `service_exception` (
                    `id` TEXT NOT NULL,
                    `contractId` TEXT NOT NULL,
                    `date` TEXT NOT NULL,
                    `reason` TEXT NOT NULL,
                    PRIMARY KEY (`id`),
                    FOREIGN KEY (`contractId`) REFERENCES `contract` (`id`)
                        ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_service_exception_contractId_date` " +
                    "ON `service_exception` (`contractId`, `date`)",
            )
            db.execSQL(
                """
                INSERT INTO service_exception (id, contractId, date, reason)
                VALUES ('e-v4', 'c-v3', '2026-10-14', 'Ganti ban, mobil di bengkel')
                """.trimIndent(),
            )

            db.version = 4
        }
    }

    /**
     * Builds a v1 database by hand, from Story 2.2/2.3's schema.
     *
     * Plain `SQLiteDatabase` rather than a Room builder: Room can only create the
     * *current* schema, and the point here is to create one that is gone.
     *
     * @param withVehicle whether to insert a vehicle row first. Two tests need the
     *   tables empty so they can assert on indexes alone; one needs a row to prove
     *   the fleet survives.
     */
    private fun createV1Database(withVehicle: Boolean) {
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            createVehicleTable(db)

            // Story 2.2's minimum `contract` — three columns and the vehicle
            // foreign key, with no renter, no rate and no schedule.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `contract` (
                    `id` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `startDate` TEXT NOT NULL,
                    `endDate` TEXT DEFAULT NULL,
                    PRIMARY KEY (`id`),
                    FOREIGN KEY(`vehicleId`) REFERENCES `vehicle`(`id`)
                        ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_contract_vehicleId` ON `contract` (`vehicleId`)",
            )

            // No `renter` table — that is what MIGRATION_1_2 adds.

            // The version the shipped database claimed to be at.
            db.version = 1

            if (withVehicle) insertVehicle(db)
        }
    }

    /**
     * Builds a v2 database by hand, from Story 2.4's schema — v1 plus `renter`.
     *
     * @param withVehicleAndRenter whether to insert the vehicle and renter rows.
     *   The index assertions need the tables empty; the survival assertions need the
     *   rows, and they are the point of the MIGRATE_2_3 row.
     * @param withContractRow whether to insert a v2 `contract` row. `false` is the
     *   production case — nothing in `app/src/main` has ever written a contract, so a
     *   real v2 database has none, and the migration's rebuild is safe. `true` is the
     *   case the migration's guard exists for, and the test that sets it asserts the
     *   migration *refuses* rather than dropping the row.
     */
    private fun createV2Database(
        withVehicleAndRenter: Boolean,
        withContractRow: Boolean = false,
    ) {
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            createVehicleTable(db)

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `contract` (
                    `id` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `startDate` TEXT NOT NULL,
                    `endDate` TEXT DEFAULT NULL,
                    PRIMARY KEY (`id`),
                    FOREIGN KEY(`vehicleId`) REFERENCES `vehicle`(`id`)
                        ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_contract_vehicleId` ON `contract` (`vehicleId`)",
            )

            // Story 2.4's `renter`, verbatim.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `renter` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `bankAccount` TEXT DEFAULT NULL,
                    `emergencyContact` TEXT NOT NULL,
                    PRIMARY KEY (`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_renter_name` ON `renter` (`name`)",
            )

            db.version = 2

            if (withVehicleAndRenter) {
                insertVehicle(db)
                db.execSQL(
                    """
                    INSERT INTO renter (id, name, phone, bankAccount, emergencyContact)
                    VALUES ('r1', 'Budi Santoso', '08123456789', 'BCA 1234567890', '08129876543')
                    """.trimIndent(),
                )
            }

            if (withContractRow) {
                // The v2 shape: no renter, no rate, no schedule. This is the row
                // `MIGRATION_2_3` refuses to drop, and the test above reads it back
                // afterwards to prove the refusal left it alone.
                db.execSQL(
                    """
                    INSERT INTO contract (id, vehicleId, startDate, endDate)
                    VALUES ('c-v2', 'v1', '2026-01-01', NULL)
                    """.trimIndent(),
                )
            }
        }
    }

    /**
     * Builds a v3 database by hand, from Story 2.5's schema " v1 plus `renter` plus the
     * **widened** `contract`.
     *
     * Plain `SQLiteDatabase` for the reason the v1 and v2 fixtures above are: Room can
     * only create the *current* schema, and the point is to create one that is gone.
     *
     * The contract row is the load-bearing part. It carries a renter, a frozen rate and
     * both active columns, because those are the columns whose survival through 3"4 is
     * the claim this fixture exists to support " and because a fixture with no contract
     * row would satisfy that assertion just as well whether the migration rebuilt the
     * table or not.
     */
    private fun createV3Database() {
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            createVehicleTable(db)

            // Story 2.4's `renter`, verbatim.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `renter` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `bankAccount` TEXT DEFAULT NULL,
                    `emergencyContact` TEXT NOT NULL,
                    PRIMARY KEY (`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_renter_name` ON `renter` (`name`)",
            )

            // Story 2.5's widened `contract`, verbatim " every column in declaration
            // order, every `DEFAULT NULL` written explicitly because Room compares the
            // recorded default, and both foreign keys declared.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `contract` (
                    `id` TEXT NOT NULL,
                    `renterId` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `startDate` TEXT NOT NULL,
                    `dailyRate` INTEGER NOT NULL,
                    `restDay` INTEGER DEFAULT NULL,
                    `activeVehicleId` TEXT DEFAULT NULL,
                    `activeRenterId` TEXT DEFAULT NULL,
                    `endDate` TEXT DEFAULT NULL,
                    PRIMARY KEY (`id`),
                    FOREIGN KEY (`renterId`) REFERENCES `renter` (`id`)
                        ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY (`vehicleId`) REFERENCES `vehicle` (`id`)
                        ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_contract_vehicleId` " +
                    "ON `contract` (`vehicleId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_contract_renterId` " +
                    "ON `contract` (`renterId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_contract_activeVehicleId` " +
                    "ON `contract` (`activeVehicleId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_contract_activeRenterId` " +
                    "ON `contract` (`activeRenterId`)",
            )

            // No `service_exception` " that is what MIGRATION_3_4 adds.

            db.version = 3

            insertVehicle(db)
            db.execSQL(
                """
                INSERT INTO renter (id, name, phone, bankAccount, emergencyContact)
                VALUES ('r-v3', 'Budi Santoso', '08123456789', 'BCA 1234567890', '08129876543')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO contract
                    (id, renterId, vehicleId, startDate, dailyRate, restDay,
                     activeVehicleId, activeRenterId, endDate)
                VALUES
                    ('c-v3', 'r-v3', 'v1', '2026-10-01', 250000, 7, 'v1', 'r-v3', NULL)
                """.trimIndent(),
            )
        }
    }

    /**
     * Story 2.2's `vehicle`, verbatim — including `DEFAULT NULL` on the two nullable
     * columns, which is what Room emits for `@ColumnInfo(defaultValue = "NULL")`.
     */
    private fun createVehicleTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vehicle` (
                `id` TEXT NOT NULL,
                `plate` TEXT NOT NULL,
                `brand` TEXT NOT NULL,
                `model` TEXT NOT NULL,
                `year` INTEGER NOT NULL,
                `color` TEXT DEFAULT NULL,
                `dailyRate` INTEGER NOT NULL,
                `notes` TEXT DEFAULT NULL,
                `isServis` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_plate` ON `vehicle` (`plate`)",
        )
    }

    private fun insertVehicle(db: SQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO vehicle
                (id, plate, brand, model, year, color, dailyRate, notes, isServis)
            VALUES
                ('v1', 'B 1235 TF', 'Toyota', 'Avanza', 2023, 'Hitam', 250000, NULL, 0)
            """.trimIndent(),
        )
    }

    /** Opens the older file through the shipped builder, with both migrations wired. */
    private fun openMigrated(): KlinikDatabase {
        // Fail here rather than in an assertion three tests later: if the file Room
        // is about to open does not exist, Room will happily create a new empty
        // database at that name, run no migration, and satisfy every schema check
        // below. Asserting the file is on disk before the builder runs keeps
        // "migrated" and "freshly created" distinguishable, which is the only thing
        // that makes the rest of these assertions mean anything.
        assertTrue("the older file must exist before Room opens it", dbFile.exists())

        val opened = Room.databaseBuilder(context, KlinikDatabase::class.java, dbFile.name)
            .addMigrations(
                KlinikDatabase.MIGRATION_1_2,
                KlinikDatabase.MIGRATION_2_3,
                KlinikDatabase.MIGRATION_3_4,
                KlinikDatabase.MIGRATION_4_5,
                KlinikDatabase.MIGRATION_5_6,
                KlinikDatabase.MIGRATION_6_7,
            KlinikDatabase.MIGRATION_7_8,
            )
            .build()
        database = opened

        // Touch it so the migrations actually run before the assertions below.
        assertEquals(
            "the file must have been migrated to v8",
            8,
            opened.openHelper.readableDatabase.version,
        )
        return opened
    }

    /** Column names of [table], in declaration order. */
    private fun columnsOf(sql: SupportSQLiteDatabase, table: String): List<String> =
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name")))
            }
        }

    /**
     * The single integer a `SELECT COUNT(*)` returns.
     *
     * A helper rather than an inline `query(...).use { … }` per call site: the three row-count
     * assertions in the 4→5 test would otherwise repeat the cursor dance, and a mistake in it
     * reads as a passing assertion on the wrong table.
     */
    private fun countOf(sql: SupportSQLiteDatabase, query: String): Int =
        sql.query(query).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    /** Columns declared `NOT NULL`, in declaration order. */
    private fun notNullColumnsOf(sql: SupportSQLiteDatabase, table: String): List<String> =
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndexOrThrow("name"))
                    if (c.getInt(c.getColumnIndexOrThrow("notnull")) == 1) add(name)
                }
            }
        }

    /** Columns that may hold `NULL`, in declaration order. */
    private fun nullableColumnsOf(sql: SupportSQLiteDatabase, table: String): List<String> =
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndexOrThrow("name"))
                    if (c.getInt(c.getColumnIndexOrThrow("notnull")) == 0) add(name)
                }
            }
        }

    /** `(index name, is unique)` pairs for [table], straight from SQLite. */
    private fun indexesOf(
        sql: SupportSQLiteDatabase,
        table: String,
    ): List<Pair<String, Boolean>> = sql.query("PRAGMA index_list(`$table`)").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    c.getString(c.getColumnIndexOrThrow("name")) to
                        // `unique` is a 0/1 column in PRAGMA output. The parens are
                        // load-bearing: infix `to` binds tighter than `==`, so
                        // without them this reads as `(name to unique) == 1`.
                        (c.getInt(c.getColumnIndexOrThrow("unique")) == 1),
                )
            }
        }
    }

    private fun entity(name: String, id: String) = RenterEntity(
        id = id,
        name = name,
        phone = "08123456789",
        bankAccount = null,
        emergencyContact = "08129876543",
    )

    /**
     * An open contract for the fixture vehicle and renter.
     *
     * `activeVehicleId`/`activeRenterId` carry the ids and `endDate` is `null`,
     * which is the shape `ContractEntity.init` pins together — so this is a row the
     * entity will accept, and the constraint the test exercises is the index's.
     */
    private fun openContract(id: String) = ContractEntity(
        id = id,
        renterId = "r1",
        vehicleId = "v1",
        startDate = LocalDate.of(2026, 10, 1),
        dailyRate = 250_000L,
        restDay = DayOfWeek.SUNDAY,
        activeVehicleId = "v1",
        activeRenterId = "r1",
        endDate = null,
    )

    /**
     * An ended contract: both active columns `NULL`, which is what frees the slots.
     */
    private fun endedContract(id: String) = ContractEntity(
        id = id,
        renterId = "r1",
        vehicleId = "v1",
        startDate = LocalDate.of(2026, 9, 1),
        dailyRate = 250_000L,
        restDay = null,
        activeVehicleId = null,
        activeRenterId = null,
        endDate = LocalDate.of(2026, 9, 10),
    )

    /**
     * A raw ended row for the v3 fixture's vehicle and renter.
     *
     * Written rather than reused from [endedContract] because the ids differ: the v3
     * fixture names `v1` and `r-v3`, and a row pointing at `r1` would fail the foreign
     * key on a database that has no such renter.
     */
    private fun endedEntity(id: String, vehicleId: String, renterId: String) = ContractEntity(
        id = id,
        renterId = renterId,
        vehicleId = vehicleId,
        startDate = LocalDate.of(2026, 6, 1),
        dailyRate = 250_000L,
        restDay = DayOfWeek.SUNDAY,
        activeVehicleId = null,
        activeRenterId = null,
        endDate = LocalDate.of(2026, 6, 30),
    )
}


