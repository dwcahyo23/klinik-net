package net.klinik.rental.ui.screens.penyewa

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.validation.DuplicateNameException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The `SAVE_RENTER` matrix rows, against a real Room database.
 *
 * ## Why these are not fakes
 *
 * The uniqueness guarantee in this story is a **schema** guarantee — a `UNIQUE`
 * index on `RENTER.name` — and a fake DAO cannot demonstrate it. A hand-written
 * fake that "checks for duplicates" would pass this test while the real
 * constraint was missing, which is precisely the failure it exists to catch. So
 * these run against an in-memory Room database built by the same annotation
 * processor that builds the shipped one.
 *
 * `inMemoryDatabaseBuilder` is fine here because the test asserts *schema*
 * behaviour, not persistence across a process restart — that is
 * `DatabaseMigrationTest`'s job, and it needs a real file.
 *
 * ## Why Robolectric
 *
 * Room needs a `Context` and a real SQLite. Robolectric supplies both on the
 * JVM, so the AC is verified without an emulator.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34])
class RenterRepositoryTest {

    private lateinit var database: KlinikDatabase
    private lateinit var repository: RenterRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        )
            // Every assertion here is a suspend function driven by `runBlocking`,
            // so there is no main-thread query to forbid.
            .build()
        repository = RenterRepository(database.renterDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --------------------------------------------- SAVE_RENTER: valid row

    /**
     * The matrix row: a valid renter is written, and comes back out of the flow.
     *
     * Observed through the `Flow` rather than a one-shot read, because the flow
     * is what the screen subscribes to — this is also the AC's "visible with no
     * manual refresh": Room's invalidation tracker re-emits with no signal from
     * anywhere in the app.
     */
    @Test
    fun `a valid renter is written and observed`() = runBlocking {
        repository.save(renter("Budi Santoso"))

        val stored = repository.observeRenters().first()
        assertEquals(1, stored.size)
        assertEquals("Budi Santoso", stored.single().name)
        assertEquals("08123456789", stored.single().phone)
        assertEquals("08129876543", stored.single().emergencyContact)
    }

    /**
     * The stored name is what the owner typed — trimmed of keyboard padding, but
     * otherwise uncased and unsqueezed.
     *
     * Asserted through the raw column, bypassing every mapping, because the
     * `UNIQUE` index is built on this exact string. A write path that
     * case-folded the name here would make the index compare differently from
     * what the owner sees, which is the whole failure the plate's normalisation
     * rules exist to prevent — pointed the other way for a value that has no
     * canonical form.
     */
    @Test
    fun `the name reaches the database exactly as entered`() = runBlocking {
        repository.save(renter("budi santoso", id = "first"))

        val raw = database.renterDao().findById("first")
        assertNotNull(raw)
        assertEquals("budi santoso", raw!!.name)
    }

    // ------------------------------------------ SAVE_RENTER: duplicate name

    /**
     * The matrix row: a colliding name writes nothing and raises
     * `DuplicateNameException` naming the stored name.
     */
    @Test
    fun `a colliding name writes nothing and names the stored renter`() {
        runBlocking { repository.save(renter("Budi Santoso", id = "first")) }

        val error = assertThrows(DuplicateNameException::class.java) {
            runBlocking { repository.save(renter("Budi Santoso", id = "second")) }
        }

        // The message names a renter that actually exists.
        assertEquals("Budi Santoso", error.name)
        assertTrue(error.message!!.contains("Budi Santoso"))

        // Nothing was written: still one row.
        val stored = runBlocking { repository.observeRenters().first() }
        assertEquals(1, stored.size)
        assertEquals("first", stored.single().id)
    }

    /**
     * The `UNIQUE` index itself, demonstrated at the column level — the
     * guarantee a repository pre-check cannot give, because two writes that
     * raced could both pass that check and both land.
     *
     * Both rows here carry the identical string, so the constraint that fires is
     * the index's and not a check this suite added.
     */
    @Test
    fun `the unique index refuses a second identical name`() {
        runBlocking { database.renterDao().insert(entity("Budi Santoso", id = "first")) }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { database.renterDao().insert(entity("Budi Santoso", id = "second")) }
        }

        assertEquals(1, runBlocking { repository.observeRenters().first() }.size)
    }

    /**
     * Uniqueness is *exact*, so two spellings that differ in case are two
     * different people as far as the database is concerned.
     *
     * This is the deliberate contrast with `vehicle.plate`, whose index is built
     * on a normalised value so `b 1235 tf` and `B 1235 TF` collide. A plate has
     * one canonical form; a person's name does not, and uppercasing a name would
     * degrade every label, list row and WhatsApp message built from it. So
     * `Budi` and `budi` coexist, and the spec asserts the opposite behaviour on
     * purpose — which only holds if it is written down.
     */
    @Test
    fun `uniqueness is exact so two casings coexist`() = runBlocking {
        repository.save(renter("Budi Santoso", id = "first"))
        repository.save(renter("budi santoso", id = "second"))

        val stored = repository.observeRenters().first()
        assertEquals(2, stored.size)
        assertEquals(
            setOf("Budi Santoso", "budi santoso"),
            stored.map { it.name }.toSet(),
        )
    }

    /**
     * And the entity refuses a blank name on construction, so a raw `@Insert`
     * cannot land a row the domain would never accept.
     *
     * The `UNIQUE` index cannot cover this half: two blank names are two
     * different empty strings as far as SQLite is concerned, so the index would
     * happily store both. The non-blank rule lives in `RenterEntity.init` and in
     * `Renter.init`, and this is what pins the entity half of it.
     */
    @Test
    fun `a blank name cannot even be constructed into an entity`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            entity("   ", id = "first")
        }
        assertTrue(error.message!!.contains("name must not be blank"))
    }

    // ------------------------------------------- SAVE_RENTER: optional account

    /**
     * The matrix row: a blank bank account is saved as `null`, not as an empty
     * string.
     *
     * Asserted on the raw column rather than on the domain model, because an
     * empty account number is a *different wrong answer* from a null one: it
     * looks like real data, renders as a badge on the row, and reconciles against
     * nothing.
     */
    @Test
    fun `a blank bank account is saved as null`() = runBlocking {
        repository.save(renter("Siti Aminah", id = "first", bankAccount = null))

        val raw = database.renterDao().findById("first")
        assertNotNull(raw)
        assertNull("a cash-paying renter has no account, not an empty one", raw!!.bankAccount)
        assertNull(runBlocking { repository.observeRenters().first() }.single().bankAccount)
    }

    /** And a supplied account round-trips exactly. */
    @Test
    fun `a supplied bank account round-trips`() = runBlocking {
        repository.save(renter("Budi Santoso", id = "first", bankAccount = "BCA 1234567890"))

        val raw = database.renterDao().findById("first")
        assertEquals("BCA 1234567890", raw!!.bankAccount)
    }

    /**
     * Editing a renter who *does* have an account, to blank, clears it — the
     * optional field is optional in both directions.
     */
    @Test
    fun `an account can be cleared by an edit`() = runBlocking {
        repository.save(renter("Budi Santoso", id = "first", bankAccount = "BCA 1234567890"))
        repository.save(renter("Budi Santoso", id = "first", bankAccount = null))

        assertNull(database.renterDao().findById("first")!!.bankAccount)
    }

    // ------------------------------------------------------ EDIT_RENTER

    /**
     * The matrix row: two renters stored, one edited, the other byte-for-byte
     * unchanged.
     *
     * The update is keyed on the primary key, so it reaches exactly one row — a
     * broad `save` that rewrote the table would make this assertion pass by
     * accident while still corrupting the neighbour.
     */
    @Test
    fun `editing one renter leaves the other unchanged`() = runBlocking {
        repository.save(renter("Budi Santoso", id = "first"))
        repository.save(renter("Siti Aminah", id = "second"))

        repository.save(
            renter("Budi Santoso", id = "first", phone = "081200000000"),
        )

        val stored = repository.observeRenters().first().associateBy { it.id }
        assertEquals(2, stored.size)
        // The edited row took the new number…
        assertEquals("081200000000", stored.getValue("first").phone)
        // …and the neighbour is exactly what it was.
        assertEquals("Siti Aminah", stored.getValue("second").name)
        assertEquals("08123456789", stored.getValue("second").phone)
        assertEquals("08129876543", stored.getValue("second").emergencyContact)
    }

    /**
     * An edit may keep its own name without colliding with itself — the same
     * distinction the vehicle path draws, and the reason `save` compares the
     * stored name against the row's own id before refusing.
     */
    @Test
    fun `a renter can be edited without changing their name`() = runBlocking {
        repository.save(renter("Budi Santoso", id = "first"))
        repository.save(renter("Siti Aminah", id = "second"))

        // Unchanged name, changed number: this is an update, not a duplicate.
        repository.save(renter("Budi Santoso", id = "first", phone = "081200000000"))

        val stored = repository.observeRenters().first().associateBy { it.id }
        assertEquals(2, stored.size)
        assertEquals("081200000000", stored.getValue("first").phone)
    }

    /** And an edit may *change* the name to one nobody else holds. */
    @Test
    fun `an edit can change the name to an unused one`() = runBlocking {
        repository.save(renter("Budi Santoso", id = "first"))

        repository.save(renter("Budi Santoso Jr", id = "first"))

        assertEquals("Budi Santoso Jr", runBlocking { repository.observeRenters().first() }.single().name)
    }

    /**
     * The branch this repository exists to fix: an edit that renames onto a
     * *different* stored renter's name is refused.
     *
     * This is the one case `VehicleRepository.save` gets wrong, because it asks
     * "is my id stored?" only when the name is already taken — so a rename to a
     * taken name reports the rename as an insert, the insert collides on the
     * primary key, and the owner is told a name they do not hold is duplicated.
     * Here both questions are asked unconditionally, so the collision is caught
     * as a collision.
     *
     * The neighbour assertion is the part that matters: without it, a save that
     * overwrote Budi's row with Siti's data would pass every other test in this
     * file, because the other edit tests either keep the name or use one nobody
     * holds.
     */
    @Test
    fun `an edit cannot take another renter's name`() = runBlocking {
        repository.save(renter("Budi Santoso", id = "first", phone = "08123456789"))
        repository.save(renter("Siti Aminah", id = "second", phone = "08129876543"))

        assertThrows(DuplicateNameException::class.java) {
            runBlocking { repository.save(renter("Siti Aminah", id = "first", phone = "081200000000")) }
        }

        val stored = repository.observeRenters().first().associateBy { it.id }
        assertEquals(2, stored.size)
        // Budi kept his own name and his own number...
        assertEquals("Budi Santoso", stored.getValue("first").name)
        assertEquals("08123456789", stored.getValue("first").phone)
        // ...and Siti's row was not overwritten by his save.
        assertEquals("Siti Aminah", stored.getValue("second").name)
        assertEquals("08129876543", stored.getValue("second").phone)
    }

    /**
     * The name is trimmed before it reaches the index, so padding cannot make
     * one person into two rows.
     *
     * Without the trim, `"Budi Santoso"` and `"  Budi Santoso  "` are two
     * distinct strings, the unique index sees them as two different people, and
     * both rows exist — with the second invisible in every list, because the
     * owner never typed those spaces. With it, the second save is correctly
     * refused as the duplicate it really is.
     */
    @Test
    fun `a padded name collides with its trimmed self rather than becoming a second row`() =
        runBlocking {
            repository.save(renter("Budi Santoso", id = "first"))

            // Same person, same name, padded. Trimming anywhere else would let
            // this land as a second row instead of a duplicate.
            assertThrows(DuplicateNameException::class.java) {
                runBlocking { repository.save(renter("  Budi Santoso  ", id = "second")) }
            }

            val stored = repository.observeRenters().first()
            assertEquals(1, stored.size)
            assertEquals("Budi Santoso", stored.single().name)
        }

    /** And the stored value carries no padding, whatever the caller passed. */
    @Test
    fun `a name is stored trimmed even on the first save`() = runBlocking {
        repository.save(renter("   Siti Aminah   ", id = "first"))

        assertEquals("Siti Aminah", repository.observeRenters().first().single().name)
    }

    // ------------------------------------------------------ SAVE_RENTER_BLANK_NAME

    /**
     * The matrix row: a whitespace-only name writes nothing.
     *
     * `"   "` rather than `""` because that is what an untouched text field
     * produces when Save is tapped — the common case, not an edge one.
     */
    @Test
    fun `a blank name writes nothing`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.save(renter("   ")) }
        }
        assertEquals(0, runBlocking { repository.observeRenters().first() }.size)
    }

    /** No `Renter` is constructed for it either, so the rule holds above the DAO. */
    @Test
    fun `a blank name cannot be constructed into a renter at all`() {
        assertThrows(IllegalArgumentException::class.java) {
            Renter(
                id = "renter-1",
                name = "",
                phone = "08123456789",
                bankAccount = null,
                emergencyContact = "08129876543",
            )
        }
        assertEquals(0, runBlocking { repository.observeRenters().first() }.size)
    }

    // --------------------------------------------------------------- ids

    /**
     * `newRenterId` hands out distinct ids, so two renters saved in the same
     * breath cannot silently overwrite each other on the primary key.
     */
    @Test
    fun `new renter ids are distinct`() {
        val ids = (1..50).map { repository.newRenterId() }
        assertEquals(50, ids.toSet().size)
        // UUID form, so the `uuid` column is honest about what it holds.
        assertTrue(ids.first().matches(UUID_PATTERN))
    }

    // -------------------------------------------------- out of scope, forever

    /**
     * There is no `ktp` and no `address` column, so there is no path for the
     * out-of-scope personal data to come back in through a future query.
     *
     * Asserted at the schema rather than only at the domain model, because a
     * column that exists is a column a later story can populate by accident.
     */
    @Test
    fun `the schema carries no ktp or address column`() {
        val columns = tableColumns(database, "renter")
        assertFalse("the app must not collect a national ID", "ktp" in columns)
        assertFalse("the app must not collect a national ID", "nik" in columns)
        assertFalse("the app must not collect an address", "address" in columns)
        assertFalse("the app must not collect an address", "alamat" in columns)
        // And the six it should carry, named verbatim as the Kotlin properties.
        assertEquals(
            listOf("id", "name", "phone", "bankAccount", "emergencyContact", "avatar"),
            columns,
        )
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A raw entity, bypassing the domain model entirely.
     *
     * Used for the uniqueness rows, which are about what the *schema* refuses —
     * and the domain model cannot be used for that, because by the time a
     * `Renter` exists its name has already been checked.
     */
    private fun entity(name: String, id: String) = RenterEntity(
        id = id,
        name = name,
        phone = "08123456789",
        bankAccount = "BCA 1234567890",
        emergencyContact = "08129876543",
    )

    private fun renter(
        name: String,
        id: String = "renter-1",
        phone: String = "08123456789",
        bankAccount: String? = "BCA 1234567890",
    ) = Renter(
        id = id,
        name = name,
        phone = phone,
        bankAccount = bankAccount,
        emergencyContact = "08129876543",
    )

    /**
     * Column names of [table], in declaration order, read from the schema Room
     * actually built.
     *
     * Asserting the schema rather than the entity is the point: a column that
     * *exists* is a column a later story can populate by accident, so the
     * out-of-scope check has to see the database, not just the Kotlin class.
     */
    private fun tableColumns(db: KlinikDatabase, table: String): List<String> {
        val sql = db.openHelper.readableDatabase
        return sql.query("PRAGMA table_info(`$table`)").use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name")))
            }
        }
    }

    private companion object {
        /** A canonical UUID string — 8-4-4-4-12 hex digits. */
        val UUID_PATTERN =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    }
}