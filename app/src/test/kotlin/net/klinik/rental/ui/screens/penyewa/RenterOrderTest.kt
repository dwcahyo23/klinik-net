package net.klinik.rental.ui.screens.penyewa

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.RenterEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `observeAll` is `ORDER BY name ASC`, so the list the owner reads is alphabetical
 * and independent of insertion order.
 *
 * Separate from `PenyewaFilterTest` because it needs a real SQLite for the
 * `ORDER BY` to mean anything. `PenyewaFilterTest` hands rows in directly, so it
 * proves the *filter* preserves order and cannot prove the *database* produces
 * it — a missing `ORDER BY` would pass every filter assertion and then hand the
 * screen an arbitrary order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenterOrderTest {

    private lateinit var database: KlinikDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `renters come back ordered by name not by insertion`() = runBlocking {
        val dao = database.renterDao()
        // Inserted deliberately out of alphabetical order.
        dao.insert(entity("Siti Aminah", id = "r3"))
        dao.insert(entity("Andi Wijaya", id = "r1"))
        dao.insert(entity("Budi Santoso", id = "r2"))

        assertEquals(
            listOf("Andi Wijaya", "Budi Santoso", "Siti Aminah"),
            dao.observeAll().first().map { it.name },
        )
    }

    /** And an edit keeps the row in its new alphabetical position. */
    @Test
    fun `an edit moves the row to its new alphabetical position`() = runBlocking {
        val dao = database.renterDao()
        dao.insert(entity("Siti Aminah", id = "r1"))
        dao.insert(entity("Budi Santoso", id = "r2"))
        dao.insert(entity("Andi Wijaya", id = "r3"))
        assertEquals(
            listOf("Andi Wijaya", "Budi Santoso", "Siti Aminah"),
            dao.observeAll().first().map { it.name },
        )

        // Budi renames himself to a first name, and the list re-sorts itself.
        dao.update(entity("Andi Budi", id = "r2"))

        assertEquals(
            listOf("Andi Budi", "Andi Wijaya", "Siti Aminah"),
            dao.observeAll().first().map { it.name },
        )
    }

    private fun entity(name: String, id: String) = RenterEntity(
        id = id,
        name = name,
        phone = "08123456789",
        bankAccount = null,
        emergencyContact = "08129876543",
    )
}