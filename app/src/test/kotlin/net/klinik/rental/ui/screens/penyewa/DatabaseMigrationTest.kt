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
 * The migrations: a v1 database survives 1→2 and a v2 database survives 2→3, and
 * each step's tables and indexes arrive with it.
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
 * `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` and never a destructive fallback —
 * see `KlinikDatabase`'s KDoc.
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
 * ## Why `openMigrated` asserts v3 rather than v2
 *
 * The shipped database is at version 3 as of Story 2.5, so a v1 file runs *both*
 * steps on open. Asserting v2 here would be asserting about a version the app no
 * longer ships, and the two-step path is the one every existing install actually
 * takes — so it is the one under test.
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
    fun `a v1 database migrates to v3 with every table and index`() {
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
    fun `a fresh install opens at v3 without a migration`() {
        val fresh = Room.databaseBuilder(context, KlinikDatabase::class.java, dbFile.name)
            .addMigrations(KlinikDatabase.MIGRATION_1_2, KlinikDatabase.MIGRATION_2_3)
            .build()
        database = fresh

        // Touch the database so it is actually created rather than only built.
        val sql = fresh.openHelper.readableDatabase
        assertEquals(3, sql.version)
        assertEquals(
            listOf("id", "name", "phone", "bankAccount", "emergencyContact"),
            columnsOf(sql, "renter"),
        )

        // And the whole schema is usable through the DAOs, not just present.
        assertEquals(0, runBlocking { fresh.renterDao().observeAll().first() }.size)
        assertNull(runBlocking { fresh.renterDao().findById("nothing") })
        assertEquals(0, runBlocking { fresh.contractDao().observeAll().first() }.size)
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
    fun `a v2 database migrates to v3 with the widened contract table`() {
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
                .addMigrations(KlinikDatabase.MIGRATION_1_2, KlinikDatabase.MIGRATION_2_3)
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
            listOf("restDay", "activeVehicleId", "activeRenterId", "endDate"),
            nullableColumnsOf(sql, "contract"),
        )
    }

    // ------------------------------------------------------------------ helpers

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
            .addMigrations(KlinikDatabase.MIGRATION_1_2, KlinikDatabase.MIGRATION_2_3)
            .build()
        database = opened

        // Touch it so the migrations actually run before the assertions below.
        assertEquals(
            "the file must have been migrated to v3",
            3,
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
}