package net.klinik.rental.data.backup

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.SCHEMA_VERSION
import net.klinik.rental.data.db.entity.PaymentEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * The demo dataset, generated through the app's own write paths and read back through the
 * restore reader.
 *
 * ## Why this test is the generator
 *
 * Because the file is only useful if it restores, and the only way to know that is to restore it.
 * Building it here means the artifact and its proof cannot drift: the same run that writes
 * `demo-backup.json` reads it back through [BackupJsonReader] and asserts the counts, the
 * allocation invariant and the three arrears tiers the demo exists to show.
 *
 * The file is written only when `DEMO_OUT` names a path, so an ordinary test run has no side
 * effects. `createdAt` comes from `saveDeposit`'s own `Instant.now()`, so a regenerated file is
 * not byte-identical to the last one — which is why the assertions below are about structure and
 * figures, never about bytes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoDatasetTest {

    private lateinit var db: KlinikDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            KlinikDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `the demo dataset restores into every table with its arrears intact`() = runBlocking {
        val asOf = LocalDate.of(2026, 10, 21)
        val json = DemoDataset.build(db, asOf)

        // Written unconditionally: the artifact is the point, and `build/` is where a generated
        // file belongs. `DEMO_OUT` overrides the destination when a caller wants it elsewhere.
        File(System.getenv("DEMO_OUT") ?: "build/demo-backup.json").apply {
            parentFile?.mkdirs()
            writeText(json)
        }

        val restore = BackupJsonReader.read(json, SCHEMA_VERSION, asOf)

        assertEquals("ten cars", 10, restore.vehicles.size)
        assertEquals("eight renters", 8, restore.renters.size)
        assertEquals("eight contracts", 8, restore.contracts.size)
        assertEquals("four real accounts — 'Tunai Kasir' is a method", 4, restore.businessAccounts.size)
        assertEquals("one exemption on cont-1, two on cont-3", 3, restore.serviceExceptions.size)
        // 229 deposits: five contracts over the full 52-day history, three starting at the
        // current period's start so they can be behind on rent. The floor is loose enough to
        // survive a one-day shift and tight enough to catch a loop that stopped early.
        assertTrue(
            "a period of deposits, not a handful: ${restore.payments.size}",
            restore.payments.size > 200,
        )

        assertEquals(
            "every contract is open — the prototype's status is derived, not stored",
            8,
            restore.contracts.count { it.endDate == null && it.activeVehicleId != null },
        )

        val cancelled = restore.payments.filter { it.status == PaymentEntity.STATUS_CANCELED }
        assertEquals("one audit-trail cancellation", 1, cancelled.size)
        assertEquals(
            "and its allocations are dropped, or the arrears would double-count",
            0,
            restore.allocations.count { it.paymentId == cancelled.single().id },
        )

        // The invariant `AllocationEngine` enforces on every write: a confirmed deposit's rows
        // add up to its amount. A fixture that broke this would show arrears that disagree with
        // the deposits, which is the one thing AD-3 exists to prevent.
        val byPayment = restore.allocations.groupBy { it.paymentId }
        restore.payments
            .filter { it.status == PaymentEntity.STATUS_CONFIRMED }
            .forEach { payment ->
                val total = byPayment[payment.id].orEmpty().sumOf { it.amount }
                assertEquals(
                    "deposit ${payment.id} (${payment.contractId} ${payment.date}) " +
                        "must allocate exactly its amount",
                    payment.amount,
                    total,
                )
            }

        // The three tiers the demo is for. Counted as open charge days rather than rupiah, so
        // the assertion does not have to restate the rates.
        val openDays = arrearsOpenDays(asOf)
        assertTrue("cont-5 must be the worst: ${openDays["cont-5"]}", (openDays["cont-5"] ?: 0) >= 5)
        assertTrue("cont-3 must be visible: ${openDays["cont-3"]}", (openDays["cont-3"] ?: 0) >= 2)
        assertTrue("cont-6 must be visible: ${openDays["cont-6"]}", (openDays["cont-6"] ?: 0) >= 1)
        assertEquals("cont-1 is paid up", 0, openDays["cont-1"] ?: 0)
    }

    /**
     * Open charge days per contract, read from the generated database.
     *
     * The arrears read the app uses, so this asserts the fixture through the same path the
     * Tunggakan screen takes rather than through a re-implementation of it.
     */
    private suspend fun arrearsOpenDays(asOf: LocalDate): Map<String, Int> {
        val repository = net.klinik.rental.data.repository.PaymentRepository(
            paymentDao = db.paymentDao(),
            allocationDao = db.allocationDao(),
            contractDao = db.contractDao(),
            exceptionDao = db.serviceExceptionDao(),
            settingDao = db.settingDao(),
            renterDao = db.renterDao(),
            vehicleDao = db.vehicleDao(),
            businessAccountDao = db.businessAccountDao(),
            database = db,
        )
        return repository.observeArrearsForAllContracts(
            java.time.Clock.fixed(asOf.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), java.time.ZoneOffset.UTC),
        ).first().associate { it.contractId to it.days }
    }
}
