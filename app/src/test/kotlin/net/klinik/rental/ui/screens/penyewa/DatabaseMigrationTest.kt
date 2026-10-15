package net.klinik.rental.ui.screens.penyewa

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
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

/**
 * MIGRATE_1_2: a v1 database survives the migration, and the `renter` table and
 * its unique index arrive with it.
 *
 * ## Why this test exists and nothing else can replace it
 *
 * Everything else in this story writes through the *current* schema, so a wrong
 * `MIGRATION_1_2` would not fail any of it — the annotation processor builds the
 * schema from today's entities, and the migration is hand-written SQL that nothing
 * else consults. Only this test compares the two.
 *
 * And a wrong one is not cosmetic: with no migration wired, Room throws on open
 * for every install that already ran Story 2.2, so every device holding an
 * owner's fleet refuses to start. That is why the shipped builder wires
 * `.addMigrations(MIGRATION_1_2)` and never a destructive fallback — see
 * `KlinikDatabase`'s KDoc.
 *
 * ## Why a real file and not `inMemoryDatabaseBuilder`
 *
 * The point is migrating an *existing* v1 database, which means opening a file
 * that already holds the v1 schema and v1 data. An in-memory builder has no v1 to
 * start from. The file lives under the Robolectric temp directory and is deleted
 * in [tearDown], so nothing leaks between tests or between runs.
 *
 * ## How the v1 database is built
 *
 * By hand, in SQL, from the v1 entities as Stories 2.2 and 2.3 shipped them —
 * `vehicle` and `contract` only, with `renter` absent. That is what makes this a
 * real migration test: building v1 from *today's* entities would already contain
 * `renter`, leaving the migration a no-op that proves nothing.
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
        // the v1 file has to live at exactly that path. Writing it anywhere else
        // — `cacheDir`, say — leaves Room opening a fresh empty database at that
        // name: the migration still "passes" (a new file is created straight at
        // v2, with the renter table and its index), and every schema assertion
        // below holds, while the vehicle row is silently absent. That is the worst
        // shape a migration test can have — it verifies a database nobody had.
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

    /**
     * The matrix row: an existing v1 database opens at v2 with the `renter` table
     * and its unique index in place, and the pre-existing `vehicle` row intact.
     *
     * Opened through the *shipped* builder with `MIGRATION_1_2` wired, rather than
     * by invoking `migrate` directly. Room validates the resulting schema against
     * the compiled one on open, and that validation is the whole point: a
     * migration that created the table with a misspelled column would pass a
     * direct `migrate` call and fail here.
     */
    @Test
    fun `a v1 database migrates to v2 with the renter table and its unique index`() {
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
     * Asserting `PRAGMA index_list` says an index was declared; inserting two
     * rows with one name says it refuses. The second is the property the AC
     * depends on, and it is the one a typo'd `CREATE UNIQUE INDEX` (missing the
     * `UNIQUE` keyword, say) would fail.
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
     * The v1 `vehicle` table carries a `UNIQUE` index on `plate`, and this
     * migration's job is to add `renter` — not to rebuild `vehicle`. If the SQL
     * had dropped and recreated anything, a data-loss regression would show here
     * as a missing constraint rather than as a crash, which is much harder to
     * notice in the field.
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
     * A fresh install has no v1 to migrate from, and must still open.
     *
     * Without a migration wired, Room takes the `createAllTables` path — a
     * *different* code path from `migrate` — so it needs its own assertion. A
     * `Migration(1, 2)` that accidentally ran against a new database would be
     * harmless; the inverse, an empty migration list that only appears to work
     * because nothing was ever needed, is the failure.
     */
    @Test
    fun `a fresh install opens at v2 without a migration`() {
        val fresh = Room.databaseBuilder(context, KlinikDatabase::class.java, dbFile.name)
            .addMigrations(KlinikDatabase.MIGRATION_1_2)
            .build()
        database = fresh

        // Touch the database so it is actually created rather than only built.
        val sql = fresh.openHelper.readableDatabase
        assertEquals(2, sql.version)
        assertEquals(
            listOf("id", "name", "phone", "bankAccount", "emergencyContact"),
            columnsOf(sql, "renter"),
        )

        // And the whole schema is usable through the DAOs, not just present.
        assertEquals(0, runBlocking { fresh.renterDao().observeAll().first() }.size)
        assertNull(runBlocking { fresh.renterDao().findById("nothing") })
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

        val notNull = sql.query("PRAGMA table_info(`renter`)").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndexOrThrow("name"))
                    val required = c.getInt(c.getColumnIndexOrThrow("notnull")) == 1
                    if (required) add(name)
                }
            }
        }
        assertEquals(
            listOf("id", "name", "phone", "emergencyContact"),
            notNull,
        )
        assertTrue(
            "the primary key is not the nullable one",
            "id" in notNull,
        )
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Builds a v1 database by hand, from Story 2.2/2.3's schema.
     *
     * Plain `SQLiteDatabase` rather than a Room builder: Room can only create the
     * *current* schema, and the point here is to create the one that is gone.
     *
     * @param withVehicle whether to insert a vehicle row first. Two tests need
     *   the tables empty so they can assert on indexes alone; one needs a row to
     *   prove the fleet survives.
     */
    private fun createV1Database(withVehicle: Boolean) {
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            // Story 2.2's `vehicle`, verbatim — including `DEFAULT NULL` on the
            // two nullable columns, which is what Room emits for
            // `@ColumnInfo(defaultValue = "NULL")`.
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

            // Story 2.2's minimum `contract`.
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

            // No `renter` table — that is what the migration adds.

            // The version the shipped database claims to be at.
            db.version = 1

            if (withVehicle) {
                db.execSQL(
                    """
                    INSERT INTO vehicle
                        (id, plate, brand, model, year, color, dailyRate, notes, isServis)
                    VALUES
                        ('v1', 'B 1235 TF', 'Toyota', 'Avanza', 2023, 'Hitam', 250000, NULL, 0)
                    """.trimIndent(),
                )
            }
        }
    }

    /** Opens the v1 file through the shipped builder, with the migration wired. */
    private fun openMigrated(): KlinikDatabase {
        // Fail here rather than in an assertion three tests later: if the file
        // Room is about to open does not exist, Room will happily create a new
        // empty database at that name, run no migration, and satisfy every schema
        // check below. Asserting the v1 file is on disk before the builder runs
        // keeps "migrated" and "freshly created" distinguishable, which is the
        // only thing that makes the rest of these assertions mean anything.
        assertTrue("the v1 file must exist before Room opens it", dbFile.exists())

        val opened = Room.databaseBuilder(context, KlinikDatabase::class.java, dbFile.name)
            .addMigrations(KlinikDatabase.MIGRATION_1_2)
            .build()
        database = opened

        // Touch it so the migration actually runs before the assertions below.
        assertEquals(
            "the file must have been migrated to v2",
            2,
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

    /** `(index name, is unique)` pairs for [table], straight from SQLite. */
    private fun indexesOf(
        sql: SupportSQLiteDatabase,
        table: String,
    ): List<Pair<String, Boolean>> = sql.query("PRAGMA index_list(`$table`)").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    c.getString(c.getColumnIndexOrThrow("name")) to
                        // `unique` is a 0/1 column in PRAGMA output. The parens
                        // are load-bearing: infix `to` binds tighter than `==`, so
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
}