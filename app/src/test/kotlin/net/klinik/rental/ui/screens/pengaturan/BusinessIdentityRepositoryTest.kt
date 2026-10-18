package net.klinik.rental.ui.screens.pengaturan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.data.repository.BlankIdentityNameException
import net.klinik.rental.data.repository.BusinessIdentityRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Story 4.4's `IDENTITY_*` matrix rows, against a real Room database.
 *
 * ## Why the `IDENTITY_BACKUP` row asserts no export
 *
 * The criterion says the identity survives a backup and restore, and this story's whole claim
 * about that is **where the value lives**: `settings` is the table the export will cover, and it
 * is in the database precisely because device preferences are outside the app's own export
 * (`SettingEntity`'s KDoc, FR-16). So the property to assert today is that the rows are written
 * to `settings` and read back from there — not that an export contains them, because **there is
 * no export yet**. That is Story 5.1, and asserting it here would need a stub that proves
 * nothing about the real thing.
 *
 * The test that matters is the one that would catch a regression: an implementation that moved
 * the identity to `SharedPreferences` would still pass every other test in this file and would
 * lose the business name on restore. So the rows are read back through the DAO, not through the
 * repository's own accessor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BusinessIdentityRepositoryTest {

    private lateinit var database: KlinikDatabase
    private lateinit var repository: BusinessIdentityRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        repository = BusinessIdentityRepository(database.settingDao(), database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * `IDENTITY_DEFAULT` — nothing saved, and the name reads as the prototype's default.
     *
     * Read back **through the DAO** rather than through `identity()`: this is what proves the
     * value is not stored, which is the property the default-on-read rule exists to keep — "unset"
     * must stay distinguishable from "set to the default", or Story 4.4's editable default
     * becomes indistinguishable from a value the owner never touched.
     */
    @Test
    fun `nothing saved means the name reads as the default and nothing is stored`() =
        runBlocking {
            assertNull(
                "no row exists yet — a seeded row would be a value the owner never chose",
                database.settingDao().findValue(BusinessIdentityRepository.KEY_NAME),
            )
            assertEquals(
                BusinessIdentityRepository.DEFAULT_NAME,
                repository.businessName(),
            )
            assertEquals(
                "and the whole identity reads as one editable default",
                BusinessIdentityRepository.DEFAULT_NAME,
                repository.identity().name,
            )
            assertNull(repository.identity().address)
            assertNull(repository.identity().phone)
        }

    /** `IDENTITY_SAVE_ALL` — three fields, three rows, one call. */
    @Test
    fun `all three fields are stored and read back`() = runBlocking {
        repository.save("PT Klinik Net Rent Car", "Jl. Boulevard Raya Q18", "0812-8899-7722")

        val identity = repository.identity()
        assertEquals("PT Klinik Net Rent Car", identity.name)
        assertEquals("Jl. Boulevard Raya Q18", identity.address)
        assertEquals("0812-8899-7722", identity.phone)

        assertEquals(
            "and through the DAO, so the rows really are in `settings` — the table the export "
                + "will cover, which is what makes the identity survive a restore",
            "PT Klinik Net Rent Car",
            database.settingDao().findValue(BusinessIdentityRepository.KEY_NAME),
        )
        assertEquals(
            "Jl. Boulevard Raya Q18",
            database.settingDao().findValue(BusinessIdentityRepository.KEY_ADDRESS),
        )
    }

    /**
     * `IDENTITY_SAVE_PARTIAL` — an unfilled field is **absent**, not stored as `""`.
     *
     * The two are different facts about the same business, and a row holding `""` reads back as
     * "the owner filled this in and it is blank" rather than "they never touched it" — which is
     * the claim a future message would be making about an address it never received.
     */
    @Test
    fun `an unfilled field is absent rather than stored as an empty string`() = runBlocking {
        repository.save("PT Klinik Net Rent Car", null, "0812-8899-7722")

        assertNull(repository.identity().address)
        assertNull(
            "and no row exists for it — an empty row is a value, an absent one is not",
            database.settingDao().findValue(BusinessIdentityRepository.KEY_ADDRESS),
        )
        assertEquals("0812-8899-7722", repository.identity().phone)
    }

    /** Whitespace is blank — whatever produced it, an empty business name is an empty name. */
    @Test
    fun `a whitespace name is refused`() = runBlocking {
        assertThrows(BlankIdentityNameException::class.java) {
            runBlocking { repository.save("   ", "Jl. Boulevard", "0812") }
        }

        assertEquals(
            "and nothing was written",
            BusinessIdentityRepository.DEFAULT_NAME,
            repository.businessName(),
        )
        assertNull(database.settingDao().findValue(BusinessIdentityRepository.KEY_ADDRESS))
    }

    /**
     * `IDENTITY_BLANK_NAME` — refused, and **nothing** written.
     *
     * Asserted on the address and phone too, because a refusal that stored the two it did have
     * would leave a business with an address and no name — half an identity, which is exactly
     * what saving as one block exists to prevent.
     */
    @Test
    fun `a blank name is refused and nothing is written`() = runBlocking {
        assertThrows(BlankIdentityNameException::class.java) {
            runBlocking { repository.save("", "Jl. Boulevard Raya Q18", "0812-8899-7722") }
        }

        assertNull(
            "no name row",
            database.settingDao().findValue(BusinessIdentityRepository.KEY_NAME),
        )
        assertNull(
            "no address row either — a refusal that stored the rest is a half identity",
            database.settingDao().findValue(BusinessIdentityRepository.KEY_ADDRESS),
        )
        assertNull(
            "nor a phone",
            database.settingDao().findValue(BusinessIdentityRepository.KEY_PHONE),
        )
    }

    /**
     * The name is trimmed on the way to storage.
     *
     * The stored value is what the message quotes, and a trailing space in it is invisible on
     * screen and visible in a WhatsApp preview — so it is trimmed here, once, rather than by
     * every reader.
     */
    @Test
    fun `the stored name is trimmed`() = runBlocking {
        repository.save("  PT Klinik Net Rent Car  ", null, null)

        assertEquals("PT Klinik Net Rent Car", repository.businessName())
        assertNull(
            "and a field that became blank after trimming is absent, not empty",
            database.settingDao().findValue(BusinessIdentityRepository.KEY_ADDRESS),
        )
    }

    /**
     * `IDENTITY_EDIT` — saving again replaces all three, and an old deposit is untouched.
     *
     * The deposit half is the acceptance criterion that sounds trivial and is not: a deposit
     * records what the owner took, not what the business was called that day. An implementation
     * that reached across to rewrite deposits — or that stored the identity on the contract —
     * would pass every other test in this file.
     */
    @Test
    fun `editing the identity leaves an existing deposit exactly as it was`() = runBlocking {
        // The fixture's own dependencies, because `PAYMENT` carries a `RESTRICT` foreign key to
        // `CONTRACT` and the deposit would otherwise be refused for a contract nobody holds —
        // a failure about the test's setup that reads as a failure about the identity.
        database.vehicleDao().insert(
            net.klinik.rental.data.db.entity.VehicleEntity(
                id = "vehicle-1",
                plate = "B 1235 TF",
                brand = "Toyota",
                model = "Avanza",
                year = 2023,
                color = null,
                dailyRate = 250_000,
                notes = null,
            ),
        )
        database.renterDao().insert(
            net.klinik.rental.data.db.entity.RenterEntity(
                id = "renter-1",
                name = "Budi Santoso",
                phone = "08123456789",
                bankAccount = null,
                emergencyContact = "08129876543",
            ),
        )
        database.contractDao().insertContract(
            net.klinik.rental.data.db.entity.ContractEntity(
                id = "contract-1",
                renterId = "renter-1",
                vehicleId = "vehicle-1",
                startDate = LocalDate.of(2026, 10, 1),
                dailyRate = 250_000,
                restDay = null,
                activeVehicleId = "vehicle-1",
                activeRenterId = "renter-1",
                endDate = null,
            ),
        )

        val before = PaymentEntity(
            id = "pay-1",
            contractId = "contract-1",
            accountId = null,
            date = LocalDate.of(2026, 10, 1),
            amount = 250_000,
            method = "Tunai",
            referenceNo = null,
            notes = null,
            createdAt = Instant.parse("2026-10-01T09:00:00Z"),
        )
        database.paymentDao().insertPayment(before)

        repository.save("PT Klinik Net Rent Car", "Jl. Lama", "0811")
        repository.save("Rental SAYA", "Jl. Baru", "0812")

        val after = database.paymentDao().findById("pay-1")!!
        assertEquals(
            "the amount, date, id and createdAt are untouched",
            before,
            after,
        )
        assertEquals("Rental SAYA", repository.businessName())
        assertEquals("Jl. Baru", repository.identity().address)
    }

    /**
     * A field the owner **clears** afterwards becomes absent again.
     *
     * The direction saving alone does not cover: without it, a business that moves and then
     * removes its old address would keep quoting the first one forever, because nothing in the
     * save path could ever take a row back out.
     */
    @Test
    fun `clearing a field afterwards removes it`() = runBlocking {
        repository.save("Rental SAYA", "Jl. Boulevard Raya Q18", "0812-8899-7722")
        assertEquals("Jl. Boulevard Raya Q18", repository.identity().address)

        repository.save("Rental SAYA", null, "0812-8899-7722")

        assertNull("gone, not blank", repository.identity().address)
        assertNull(
            database.settingDao().findValue(BusinessIdentityRepository.KEY_ADDRESS),
        )
        assertEquals("while the name survives", "Rental SAYA", repository.businessName())
    }

    /**
     * A name the owner typed replaces the default rather than joining it.
     *
     * The save is an upsert, so this is really a statement that the default is applied on read
     * and never written back — if a migration ever seeded it, this would produce
     * "PT Klinik Net Rent CarRental SAYA" and no other test would catch it.
     */
    @Test
    fun `a typed name replaces the default rather than appending to it`() = runBlocking {
        repository.save("Rental SAYA", null, null)

        assertEquals("Rental SAYA", repository.businessName())
    }
}
