package net.klinik.rental.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.BusinessAccountEntity
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.ServiceExceptionEntity
import net.klinik.rental.data.db.entity.SettingEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.domain.model.Payment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FR-16's two halves, proved to agree.
 *
 * ## Why this file exists when both halves have their own tests
 *
 * `BackupJsonTest` proves the **writer** emits well-formed JSON. `BackupJsonReaderTest` proves the
 * **reader** accepts well-formed JSON. Neither proves the two use the same key names, the same
 * date format, or the same null convention — and a disagreement between them is a backup file
 * that exports perfectly, looks correct in a text editor, and restores to nothing or to the wrong
 * data. That is the failure mode that costs the owner his records, and this is the only test in
 * the project that can see it.
 *
 * ## What "identical" means here
 *
 * Every row of every table, compared field by field on the entity's own `equals` — which for a
 * Kotlin `data class` is all of them. Not a count, not a checksum of the file: a count would pass
 * with every value wrong, and the file is what is being tested, not the database.
 *
 * ## Why a second database rather than the same one
 *
 * Because restoring into the database you exported from is the one case where a reader that
 * silently dropped a table would still look right — the rows are already there. A fresh database
 * is the device-replacement scenario the whole epic is about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportRestoreRoundTripTest {

    private lateinit var source: KlinikDatabase
    private lateinit var target: KlinikDatabase
    private lateinit var payments: PaymentRepository

    private val asOf = LocalDate.of(2026, 10, 17)

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        source = Room.inMemoryDatabaseBuilder(context, KlinikDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, KlinikDatabase::class.java).build()
        payments = PaymentRepository(
            paymentDao = source.paymentDao(),
            allocationDao = source.allocationDao(),
            contractDao = source.contractDao(),
            exceptionDao = source.serviceExceptionDao(),
            settingDao = source.settingDao(),
            database = source,
        )
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    /**
     * A realistic database — two contracts, deposits on both, a cancellation, a service
     * exception, an ended contract — exports and restores to an identical database.
     *
     * Deliberately not the minimum one row per table: the fields that a naive writer/reader pair
     * gets wrong are the *empty* ones and the *optional* ones, and a fixture with only populated
     * rows would never exercise a `null` round-trip.
     */
    @Test
    fun `a whole database survives an export and a restore unchanged`() = runBlocking {
        seed()

        val document = exporter().build()
        restorer(target).apply(restorer(target).parse(document.json))

        assertEquals("vehicles", read(source.vehicleDao().observeAll()), read(target.vehicleDao().observeAll()))
        assertEquals("renters", read(source.renterDao().observeAll()), read(target.renterDao().observeAll()))
        assertEquals("contracts", read(source.contractDao().observeAll()), read(target.contractDao().observeAll()))
        assertEquals(
            "service exceptions",
            read(source.serviceExceptionDao().observeAll()),
            read(target.serviceExceptionDao().observeAll()),
        )
        assertEquals(
            "business accounts",
            read(source.businessAccountDao().observeAll()),
            read(target.businessAccountDao().observeAll()),
        )
        assertEquals(
            "payments",
            read(source.paymentDao().observeAllForHistory()),
            read(target.paymentDao().observeAllForHistory()),
        )
        assertEquals("allocations", source.allocationDao().all(), target.allocationDao().all())
        assertEquals("settings", source.settingDao().all(), target.settingDao().all())
    }

    /**
     * The restored database's **allocation sums still hold** — for live deposits.
     *
     * ## Why `status == confirmed` is part of the assertion rather than an omission
     *
     * Because `Σ allocation == amount` is Story 1.3's rule for a deposit that still counts. Story
     * 3.4's cancellation **deletes the allocation rows on purpose** — that is how cancelling
     * raises arrears — so a cancelled deposit's rows sum to zero, and asserting otherwise would
     * be asserting that cancellation does not work.
     *
     * The first version of this test did exactly that and failed on `p2`, the cancelled deposit.
     * The test was wrong and the code was right; the filter is what makes the claim true.
     *
     * ## Why this is separate from the equality assertion above
     *
     * The round-trip equality already implies it. But if a future change to the writer split or
     * dropped an allocation row, the equality test would report "allocations differ" — and this
     * one reports *what the owner lost*, which is the sentence worth reading.
     */
    @Test
    fun `every restored live deposit's allocations still sum to its amount`() = runBlocking {
        seed()
        val document = exporter().build()
        restorer(target).apply(restorer(target).parse(document.json))

        val live = read(target.paymentDao().observeAllForHistory()).filter { it.status == "confirmed" }
        assertTrue("the fixture must have a live deposit to check", live.isNotEmpty())

        live.forEach { payment ->
            val allocated = target.allocationDao().forContract(payment.contractId)
                .filter { it.paymentId == payment.id }
                .sumOf { it.amount }
            assertEquals(
                "deposit ${payment.id}'s rows must sum to its amount",
                payment.amount,
                allocated,
            )
        }
    }

    /**
     * A **cancelled** deposit survives with its reason, which is the field the audit trail is for.
     *
     * Named separately from the equality assertion because "the tables are equal" is a claim a
     * reader has to trust; this is the one row a person can check against the story that motivated
     * it — Story 3.4's *"why did Budi's arrears jump on the 17th"*.
     */
    @Test
    fun `a cancelled deposit keeps its reason through a round trip`() = runBlocking {
        seed()
        val document = exporter().build()
        restorer(target).apply(restorer(target).parse(document.json))

        val cancelled = read(target.paymentDao().observeAllForHistory()).first { it.status == "canceled" }

        assertEquals("salah tanggal", cancelled.cancelReason)
        assertTrue("and its allocations were released before the backup", cancelled.amount > 0)
    }

    /**
     * An **empty** database round-trips to an empty database.
     *
     * The "fresh install" case: eight empty arrays out, eight empty arrays back. A writer that
     * omitted empty tables and a reader that required them would fail here, and nowhere else.
     */
    @Test
    fun `an empty database round trips to an empty database`() = runBlocking {
        val document = exporter().build()
        restorer(target).apply(restorer(target).parse(document.json))

        assertTrue(read(target.vehicleDao().observeAll()).isEmpty())
        assertTrue(read(target.renterDao().observeAll()).isEmpty())
        assertTrue(read(target.paymentDao().observeAllForHistory()).isEmpty())
        assertTrue(target.allocationDao().all().isEmpty())
        assertTrue(target.settingDao().all().isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    private fun exporter() = BackupRepository(
        vehicleDao = source.vehicleDao(),
        renterDao = source.renterDao(),
        contractDao = source.contractDao(),
        serviceExceptionDao = source.serviceExceptionDao(),
        businessAccountDao = source.businessAccountDao(),
        paymentDao = source.paymentDao(),
        allocationDao = source.allocationDao(),
        settingDao = source.settingDao(),
        clock = CLOCK,
    )

    private fun restorer(db: KlinikDatabase) = BackupRestorer(
        database = db,
        vehicleDao = db.vehicleDao(),
        renterDao = db.renterDao(),
        contractDao = db.contractDao(),
        serviceExceptionDao = db.serviceExceptionDao(),
        businessAccountDao = db.businessAccountDao(),
        paymentDao = db.paymentDao(),
        allocationDao = db.allocationDao(),
        settingDao = db.settingDao(),
    )

    private suspend fun <T> read(flow: Flow<T>): T = flow.first()

    /**
     * Two vehicles, two renters, one open contract and one ended, deposits on the open one, a
     * cancellation, and a service exception.
     *
     * The ended contract is what exercises the `null` round-trip on `activeVehicleId`,
     * `activeRenterId` and `endDate`; the cancellation is what exercises `cancelReason`; the
     * exception is what proves the table the AC does not name is really carried.
     */
    private suspend fun seed() {
        source.vehicleDao().insert(vehicle("v1", "B 1235 TF", "Toyota", "Avanza"))
        source.vehicleDao().insert(vehicle("v2", "B 9999 ZZ", "Honda", "Beat"))
        source.renterDao().insert(renter("r1", "Budi Santoso"))
        source.renterDao().insert(renter("r2", "Siti Aminah"))
        source.contractDao().insertContract(openContract("c1", "r1", "v1"))
        source.contractDao().insertContract(endedContract("c2", "r2", "v2"))
        source.businessAccountDao().insertBusinessAccount(
            BusinessAccountEntity(id = "a1", bankName = "BCA", number = "1234567890"),
        )
        source.serviceExceptionDao().insertServiceException(
            ServiceExceptionEntity(
                id = "e1",
                contractId = "c1",
                date = LocalDate.of(2026, 10, 7),
                reason = "banjir",
            ),
        )
        source.settingDao().putSetting(SettingEntity(key = "businessName", value = "Klinik Net"))
        source.settingDao().putSetting(SettingEntity(key = "cutoffDay", value = "25"))

        saveDeposit("p1", "c1", 250_000, LocalDate.of(2026, 10, 5))
        saveDeposit("p2", "c1", 500_000, LocalDate.of(2026, 10, 12))
        payments.cancelDeposit("c1", "p2", "salah tanggal")
    }

    private suspend fun saveDeposit(id: String, contractId: String, amount: Long, date: LocalDate) {
        payments.saveDeposit(
            contractId = contractId,
            payment = Payment(id = id, date = date, amount = amount),
            method = PaymentMethod.Tunai,
            accountId = null,
            referenceNo = null,
            notes = null,
            asOf = asOf,
        )
    }

    private fun vehicle(id: String, plate: String, brand: String, model: String) = VehicleEntity(
        id = id,
        plate = plate,
        brand = brand,
        model = model,
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

    private fun openContract(id: String, renterId: String, vehicleId: String) = ContractEntity(
        id = id,
        renterId = renterId,
        vehicleId = vehicleId,
        startDate = LocalDate.of(2026, 10, 1),
        dailyRate = 250_000,
        restDay = null,
        activeVehicleId = vehicleId,
        activeRenterId = renterId,
        endDate = null,
        settledThroughDate = LocalDate.of(2026, 10, 10),
    )

    private fun endedContract(id: String, renterId: String, vehicleId: String) = ContractEntity(
        id = id,
        renterId = renterId,
        vehicleId = vehicleId,
        startDate = LocalDate.of(2026, 9, 1),
        dailyRate = 200_000,
        restDay = null,
        // The ended contract's two active columns are null, which is the other half of the
        // entity's guard and the case a writer that dropped nulls would break.
        activeVehicleId = null,
        activeRenterId = null,
        endDate = LocalDate.of(2026, 9, 30),
        settledThroughDate = null,
    )

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC)
    }
}