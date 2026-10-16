package net.klinik.rental.ui.screens.kontrak

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.domain.calculate.ChargeSchedule
import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.domain.calculate.chargeDaysIn
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.ServiceException
import net.klinik.rental.domain.validation.DuplicateServiceExceptionDateException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The seam this story exists to discharge: **stored** exceptions actually stop the
 * charge engine from billing a day.
 *
 * ## Why this file exists and why it reads the repository
 *
 * `chargeDaysIn` has filtered on `ChargeSchedule.serviceExceptions` since Story 1.2,
 * through six tests in `ChargeDaysTest` — all of which construct the set by hand. That
 * proves the arithmetic consumes a `Set<LocalDate>`. It proves nothing about whether
 * an exception an owner marked through the app ever *becomes* that set, which is the
 * whole claim: the table, the DAO, the repository and the screen can all be perfect
 * while the engine still charges the day, and every existing test still passes.
 *
 * So this test walks the real seam end to end — `ContractRepository` to
 * `observeServiceExceptions()` to `ChargeSchedule` to `chargeDaysIn` — and asserts the
 * resulting day list. There is no faking anywhere in it, because a fake at any link
 * would be the one link that is not under test.
 *
 * ## The two assertions that carry the story
 *
 * (a) the marked day is absent, and (b) **the same day-number in the next period is
 * still charged**. (b) is the one that makes "an exception does not roll into the next
 * period" a measured fact rather than a promise about an implementation. The
 * alternative encoding — an index or an offset into the period, "skip day 14 of this
 * month" — passes (a) and fails (b) the first time a month rolls, which is why
 * `ServiceException.date` is absolute.
 *
 * @see ServiceException for why the date is absolute and the reason is required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceExceptionBillingTest {

    private lateinit var database: KlinikDatabase
    private lateinit var repository: ContractRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        repository = ContractRepository(
            database.contractDao(),
            database.serviceExceptionDao(),
            database,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * `EXCEPTION_NOT_BILLED` — the marked day is absent from the charge list, and
     * **every other day is unchanged**.
     *
     * The second half is the half a `filter` implementation gets wrong for free: a
     * filter that also drops neighbours, or that shifts the whole list by a day to
     * "compensate", still passes "the marked day is gone". The comparison against a
     * freshly computed unexceptioned baseline is what catches that.
     */
    @Test
    fun `the marked day is not charged and every other day is unchanged`() = runBlocking {
        seedOpenContract(VEHICLE_ID, RENTER_ID)
        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2026, 10, 1)))
        repository.addServiceException(
            ServiceException(
                id = EXCEPTION_ID,
                contractId = CONTRACT_ID,
                date = MARKED_DAY,
                reason = "Ganti ban, mobil di bengkel",
            )
        )

        val period = CutoffPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        val asOf = LocalDate.of(2026, 10, 31)
        val chargeable = chargeDaysIn(
            period = period,
            schedule = scheduleFor(contractStart = LocalDate.of(2026, 10, 1)),
            asOf = asOf,
        )
        val charged = chargeDaysIn(
            period = period,
            schedule = scheduleFromStored(contractStart = LocalDate.of(2026, 10, 1)),
            asOf = asOf,
        )

        assertFalse(
            "the marked day $MARKED_DAY must not appear in the charge list",
            charged.contains(MARKED_DAY),
        )
        // Exactly one day differs, and it is the marked one. Everything else is
        // byte-identical, which is what "every other day is unchanged" means.
        val lost = chargeable.filterNot { it in charged }
        val gained = charged.filterNot { it in chargeable }
        assertEquals("only the marked day drops out", listOf(MARKED_DAY), lost)
        assertEquals("and nothing is invented in its place", emptyList<LocalDate>(), gained)
        assertEquals(chargeable.size - 1, charged.size)
    }

    /**
     * `EXCEPTION_ADD_NO_ROLL` — 14 October marked; **14 April is still charged**.
     *
     * This is the assertion that discharges the AC "the exception attaches to its own
     * date and rolls with nothing". Both periods sit on day 14 of the month, and both
     * periods share a weekday alignment, so an offset or index encoding would drop 14
     * April too — and would do so without a single failing assertion in the rest of the
     * suite, because the bug only appears once the calendar turns.
     */
    @Test
    fun `a marked day does not roll into the next period`() = runBlocking {
        seedOpenContract(VEHICLE_ID, RENTER_ID)
        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2026, 4, 1)))
        repository.addServiceException(
            ServiceException(
                id = EXCEPTION_ID,
                contractId = CONTRACT_ID,
                date = LocalDate.of(2026, 10, 14),
                reason = "Ganti ban, mobil di bengkel",
            )
        )

        val schedule = scheduleFromStored(contractStart = LocalDate.of(2026, 4, 1))

        val october = chargeDaysIn(
            period = CutoffPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
            schedule = schedule,
            asOf = LocalDate.of(2026, 10, 31),
        )
        val april = chargeDaysIn(
            period = CutoffPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)),
            schedule = schedule,
            asOf = LocalDate.of(2026, 4, 30),
        )

        val fourteenthOfOctober = LocalDate.of(2026, 10, 14)
        val fourteenthOfApril = LocalDate.of(2026, 4, 14)
        assertEquals(
            "14 Oct and 14 Apr share a day-of-month on purpose; only the October one is marked",
            DayOfWeek.WEDNESDAY,
            fourteenthOfOctober.dayOfWeek,
        )
        assertFalse("the October day is not charged", october.contains(fourteenthOfOctober))
        assertTrue(
            "14 Apr is still charged: the exception attaches to its own date and rolls with nothing",
            april.contains(fourteenthOfApril),
        )
    }

    /**
     * The rest-day rule keeps working alongside the exception — an exception does not
     * replace the other exclusions, it adds one.
     *
     * Both filters are in the same pipeline, so a change to either could mask the
     * other: if the exception set were applied as "the last filter wins" rather than
     * `and`, a rest day that is also marked would come back. Asserting both days here
     * keeps them independent.
     */
    @Test
    fun `an exception does not resurrect a rest day`() = runBlocking {
        // 14 Oct 2026 is a Wednesday; the contract's rest day is Sunday, so 4 Oct and
        // 18 Oct are the Sundays inside the period.
        seedOpenContract(VEHICLE_ID, RENTER_ID)
        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2026, 10, 1)))
        repository.addServiceException(
            ServiceException(
                id = EXCEPTION_ID,
                contractId = CONTRACT_ID,
                date = LocalDate.of(2026, 10, 18),
                reason = "Bengkel_full",
            )
        )

        val charged = chargeDaysIn(
            period = CutoffPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
            schedule = scheduleFromStored(contractStart = LocalDate.of(2026, 10, 1)),
            asOf = LocalDate.of(2026, 10, 31),
        )

        val firstSunday = LocalDate.of(2026, 10, 4)
        val markedSunday = LocalDate.of(2026, 10, 18)
        assertFalse("an unmarked rest day is still not charged", charged.contains(firstSunday))
        assertFalse("and neither is one that is also marked", charged.contains(markedSunday))
    }

    /**
     * An exception outside the period changes nothing — the set is absolute, so a day
     * that never enters `period.days()` is inert.
     *
     * This is the same fact as `EXCEPTION_ADD_NO_ROLL` seen from the other side, and
     * it is cheap to pin: it fails if a future implementation ever starts iterating the
     * exception set to *shift* the period rather than filtering it.
     */
    @Test
    fun `an exception outside the period changes nothing`() = runBlocking {
        seedOpenContract(VEHICLE_ID, RENTER_ID)
        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2026, 10, 1)))
        repository.addServiceException(
            ServiceException(
                id = EXCEPTION_ID,
                contractId = CONTRACT_ID,
                date = LocalDate.of(2026, 12, 25),
                reason = "Libur nasional",
            )
        )

        val period = CutoffPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        val asOf = LocalDate.of(2026, 10, 31)
        assertEquals(
            chargeDaysIn(period, scheduleFor(LocalDate.of(2026, 10, 1)), asOf),
            chargeDaysIn(period, scheduleFromStored(LocalDate.of(2026, 10, 1)), asOf),
        )
    }

    /**
     * Removing the exception puts the day back on the bill — the exception is a stored
     * fact, not a one-way latch.
     *
     * Without this, an implementation that cached the charge days at open time, or
     * that filtered on a flag rather than on the row, would still pass the three tests
     * above: each of them only ever *adds*.
     */
    @Test
    fun `removing the exception charges the day again`() = runBlocking {
        seedOpenContract(VEHICLE_ID, RENTER_ID)
        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2026, 10, 1)))
        repository.addServiceException(
            ServiceException(
                id = EXCEPTION_ID,
                contractId = CONTRACT_ID,
                date = MARKED_DAY,
                reason = "Ganti ban, mobil di bengkel",
            )
        )
        repository.removeServiceException(EXCEPTION_ID, CONTRACT_ID)

        val period = CutoffPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        val asOf = LocalDate.of(2026, 10, 31)
        assertTrue(
            "the day is billable again once the row is gone",
            chargeDaysIn(period, scheduleFromStored(LocalDate.of(2026, 10, 1)), asOf)
                .contains(MARKED_DAY),
        )
    }

    /**
     * `EXCEPTION_PER_CONTRACT` — the same date on two contracts of one vehicle produces
     * two rows, and the exception set carries **both**.
     *
     * A per-vehicle encoding would collapse these into one row and, worse, would let
     * one contract's compensation silently excuse the same day on another rental. The
     * repository refuses to write the second on the same contract
     * (`EXCEPTION_DUPLICATE`) and accepts it on a different one — which is exactly the
     * distinction this test pins.
     */
    @Test
    fun `the same date on two contracts of one vehicle is two exceptions`() = runBlocking {
        // Two **different vehicles**, each with its own open contract and its own renter. The
        // one-active-per-vehicle rule is Story 2.5's and outranks this story, so a
        // second contract on the same vehicle would mean ending the first — and an
        // ended contract is exactly what this story makes read-only, so its exception
        // could not be written at all. The point under test is that the exception is
        // keyed on `contractId` and not on `vehicleId`, and two vehicles satisfy that
        // without dragging in the read-only rule.
        seedOpenContract(VEHICLE_ID, RENTER_ID)
        seedOpenContract(VEHICLE_ID_2, RENTER_ID_2, plate = "B 9999 TF")

        repository.open(contract(id = CONTRACT_ID, startDate = LocalDate.of(2026, 10, 1)))
        repository.open(
            contract(
                id = CONTRACT_ID_2,
                startDate = LocalDate.of(2026, 10, 1),
                renterId = RENTER_ID_2,
                vehicleId = VEHICLE_ID_2,
            )
        )

        // The same date, on both contracts: legal twice, and refused on either contract
        // a second time.
        repository.addServiceException(
            ServiceException("e1", CONTRACT_ID, MARKED_DAY, "Ganti ban")
        )
        repository.addServiceException(
            ServiceException("e2", CONTRACT_ID_2, MARKED_DAY, "Ganti ban")
        )

        val stored = repository.observeServiceExceptions().first()
        assertEquals("one row per contract, both present", 2, stored.size)
        assertEquals(
            setOf(CONTRACT_ID, CONTRACT_ID_2),
            stored.map { it.contractId }.toSet(),
        )
        assertEquals(
            "both rows carry the same date, so the engine skips that day for both",
            setOf(MARKED_DAY),
            stored.map { it.date }.toSet(),
        )

        // And the pair is still the rule: the same date on the *same* contract is
        // refused, which is what makes the two rows above a per-contract fact rather
        // than two copies of a per-vehicle one.
        val duplicate = assertThrows(
            DuplicateServiceExceptionDateException::class.java
        ) {
            runBlocking {
                repository.addServiceException(
                    ServiceException("e3", CONTRACT_ID, MARKED_DAY, "Ganti ban")
                )
            }
        }
        assertEquals("the refusal names the stored date", MARKED_DAY, duplicate.date)
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * The exception set **as the app builds it**: read back out of storage rather than
     * written by hand.
     *
     * This is the line the whole file turns on. A `ChargeSchedule` assembled from a
     * literal `setOf(date)` would test `ChargeDaysTest` again from a different
     * directory, and would pass while the repository emitted the wrong shape entirely.
     */
    private suspend fun scheduleFromStored(contractStart: LocalDate) = ChargeSchedule(
        contractStart = contractStart,
        restDay = DayOfWeek.SUNDAY,
        serviceExceptions = repository.observeServiceExceptions()
            .first()
            .map { it.date }
            .toSet(),
    )

    /** The same schedule with no exceptions — the baseline the marked run is compared against. */
    private fun scheduleFor(contractStart: LocalDate) = ChargeSchedule(
        contractStart = contractStart,
        restDay = DayOfWeek.SUNDAY,
        serviceExceptions = emptySet(),
    )

    private fun contract(
        id: String,
        startDate: LocalDate,
        renterId: String = RENTER_ID,
        vehicleId: String = VEHICLE_ID,
    ) = Contract(
        id = id,
        renterId = renterId,
        vehicleId = vehicleId,
        startDate = startDate,
        dailyRate = 250_000L,
        restDay = DayOfWeek.SUNDAY,
        endDate = null,
    )

    /**
     * A vehicle and a renter, so `open`'s foreign keys resolve.
     *
     * Both ids are written once per test: `UNIQUE(vehicle.plate)` and
     * `UNIQUE(renter.name)` mean a second call with the same ids is a constraint
     * violation, not an update.
     */
    private suspend fun seedOpenContract(
        vehicleId: String,
        renterId: String,
        plate: String = "B 1235 TF",
    ) {
        database.vehicleDao().insert(
            VehicleEntity(
                id = vehicleId,
                plate = plate,
                brand = "Toyota",
                model = "Avanza",
                year = 2023,
                color = "Hitam",
                dailyRate = 250_000L,
                notes = null,
            )
        )
        database.renterDao().insert(
            RenterEntity(
                id = renterId,
                // `UNIQUE(renter.name)` too, so the name follows the id.
                name = "Renter $renterId",
                phone = "08123456789",
                bankAccount = null,
                emergencyContact = "08129876543",
            )
        )
    }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val VEHICLE_ID_2 = "vehicle-2"
        const val RENTER_ID = "renter-1"
        const val RENTER_ID_2 = "renter-2"
        const val CONTRACT_ID = "contract-1"
        const val CONTRACT_ID_2 = "contract-2"
        const val EXCEPTION_ID = "exception-1"

        /** A Wednesday inside the October period, so the rest day is not in play. */
        val MARKED_DAY: LocalDate = LocalDate.of(2026, 10, 14)
    }
}