package net.klinik.rental.domain.calculate

import net.klinik.rental.domain.model.Allocation
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.domain.validation.InvalidAmountException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Story 1.3's matrix, one row per test.
 *
 * The fixture is `verify-fr10.md`'s: today 2026-03-24, cutoff 25, period
 * 2026-02-25 → 2026-03-24 = 28 charge days, Rp 250.000 per day. Every test states its own
 * payments rather than sharing one, because the matrix rows differ *only* in the payment —
 * a shared fixture would let a change to the contract setup pass as a change in behaviour.
 *
 * Two sweeps at the bottom carry the invariants that must hold for any input at all, since
 * the named rows cannot enumerate them: every payment's rows sum to its amount, and no two
 * rows share a `(paymentId, chargeDate)` pair.
 */
class AllocationEngineTest {

    private val period = CutoffPeriod(
        start = LocalDate.of(2026, 2, 25),
        end = LocalDate.of(2026, 3, 24),
    )
    private val nextPeriod = CutoffPeriod(
        start = LocalDate.of(2026, 3, 25),
        end = LocalDate.of(2026, 4, 24),
    )
    private val asOf = LocalDate.of(2026, 3, 24)
    private val nextAsOf = LocalDate.of(2026, 4, 24)
    private val rate = 250_000L
    private val contractStart = LocalDate.of(2026, 1, 1)
    private val sevenDay = ChargeSchedule(contractStart = contractStart)

    /** The 28 charge days of the fixture period, straight from Story 1.2's engine. */
    private val days = chargeDaysIn(period, sevenDay, asOf)

    // --- Matrix rows -------------------------------------------------------------------

    @Test
    fun `one payment closes the two oldest charge days`() {
        // verify-fr10.md case 1. The payment is dated 20 Mar and still lands on 25 + 26 Feb:
        // allocation is chronological, not nearest-to-the-payment-date.
        val rows = allocate(
            Payment(id = "p1", date = LocalDate.of(2026, 3, 20), amount = 500_000L),
        )

        assertEquals(2, rows.size)
        assertEquals(listOf(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 2, 26)), rows.chargeDates())
        assertEquals(500_000L, rows.sumOf { it.amount })
    }

    @Test
    fun `a payment covering the whole period leaves no null row`() {
        // 28 × 250.000 = 7.000.000 exactly. A remainder row here would be a fabricated credit.
        val rows = allocate(
            Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 7_000_000L),
        )

        assertEquals(28, rows.size)
        assertTrue("an exact fit must not manufacture credit", rows.none { it.chargeDate == null })
        assertEquals(7_000_000L, rows.sumOf { it.amount })
    }

    @Test
    fun `an overpayment stores the surplus as one null charge date row`() {
        // verify-fr10.md case 4, the Rp 13.000.000 that used to disappear at the rollover.
        val rows = allocate(
            Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 20_000_000L),
        )

        assertEquals(29, rows.size)
        assertEquals(28, rows.count { it.chargeDate != null })
        val remainder = rows.single { it.chargeDate == null }
        assertEquals("p1", remainder.paymentId)
        assertEquals(13_000_000L, remainder.amount)
        assertEquals(20_000_000L, rows.sumOf { it.amount })
    }

    @Test
    fun `the surplus leaves nothing owed in the period it was paid for`() {
        val rows = allocate(
            Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 20_000_000L),
        )

        // Arrears is not computed here (that is Story 1.4); what this story owns is that the
        // day rows cover the liability exactly, so the difference is zero by construction.
        val liability = days.size.toLong() * rate
        val allocatedToDays = rows.filter { it.chargeDate != null }.sumOf { it.amount }

        assertEquals(0L, liability - allocatedToDays)
    }

    @Test
    fun `credit carried into the next period is spent on its charge days`() {
        // The second half of verify-fr10.md case 4: the same Rp 20.000.000 replayed against
        // the next period's 31 days leaves 12.250.000, not the 13.000.000 that vanished when
        // credit was a recomputed-from-payments figure.
        val nextDays = chargeDaysIn(nextPeriod, sevenDay, nextAsOf)

        val rows = allocatePayments(
            payments = listOf(Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 20_000_000L)),
            chargeDays = nextDays,
            dailyRate = rate,
            asOf = nextAsOf,
        )

        assertEquals(31, nextDays.size)
        assertEquals(31, rows.count { it.chargeDate != null })
        assertEquals(7_750_000L, rows.filter { it.chargeDate != null }.sumOf { it.amount })
        assertEquals(12_250_000L, rows.single { it.chargeDate == null }.amount)
        assertEquals(20_000_000L, rows.sumOf { it.amount })
    }

    @Test
    fun `a partial payment becomes one partial row against the oldest open day`() {
        // verify-fr10.md case 3: Rp 100.000 closes nothing in full, and must not round up to
        // a whole day — that rounding is what hid real arrears behind a large overpay.
        val rows = allocate(
            Payment(id = "p1", date = LocalDate.of(2026, 3, 10), amount = 100_000L),
        )

        assertEquals(1, rows.size)
        assertEquals(LocalDate.of(2026, 2, 25), rows.single().chargeDate)
        assertEquals(100_000L, rows.single().amount)
    }

    @Test
    fun `a payment dated on the rest day is still credited`() {
        // verify-fr10.md case 2, the Rp 1.000.000-per-month prototype bug. 1 Mar 2026 is a
        // Sunday; it is not a charge day, so the money goes to the oldest day that is.
        val sixDay = ChargeSchedule(contractStart = contractStart, restDay = DayOfWeek.SUNDAY)
        val chargeDays = chargeDaysIn(period, sixDay, asOf)

        val rows = allocatePayments(
            payments = listOf(Payment(id = "p1", date = LocalDate.of(2026, 3, 1), amount = 250_000L)),
            chargeDays = chargeDays,
            dailyRate = rate,
            asOf = asOf,
        )

        assertFalse("Sunday is not charged", LocalDate.of(2026, 3, 1) in chargeDays)
        assertEquals(1, rows.size)
        assertEquals("the oldest open charge day", chargeDays.first(), rows.single().chargeDate)
        assertEquals("arrears falls by one day", 250_000L, rows.sumOf { it.amount })
    }

    @Test
    fun `a payment before the contract starts closes the nearest charge day`() {
        // Anchored at Monday 2026-03-02 so that "nearest charge day" and "oldest open day"
        // are the same day — see the spec's Design Notes on AC 4. The substantive claims
        // (Sunday uncharged, money credited) hold under either rule; this fixture is what
        // makes the sentence literally true rather than accidentally true.
        val start = LocalDate.of(2026, 3, 2)
        val schedule = ChargeSchedule(contractStart = start, restDay = DayOfWeek.SUNDAY)

        val rows = allocatePayments(
            payments = listOf(Payment(id = "p1", date = LocalDate.of(2026, 3, 1), amount = 250_000L)),
            chargeDays = chargeDaysIn(period, schedule, asOf),
            dailyRate = rate,
            asOf = asOf,
        )

        assertEquals(start, rows.single().chargeDate)
        assertEquals(250_000L, rows.single().amount)
    }

    @Test
    fun `a payment dated on the period's opening day is credited`() {
        val rows = allocate(
            Payment(id = "p1", date = period.start, amount = 250_000L),
        )

        assertEquals(period.start, rows.single().chargeDate)
    }

    @Test
    fun `a payment dated before the period opened is still credited`() {
        // epics.md:427 — "pembayaran sebelum kontrak dimulai tetap masuk pool". The gating is
        // `asOf`, never "inside this period": a deposit from the tail of last month closes
        // this month's oldest day. The period-start case above cannot tell that apart from a
        // plain date comparison, because its payment is on the first possible day.
        val rows = allocate(
            Payment(id = "p1", date = LocalDate.of(2026, 2, 10), amount = 250_000L),
        )

        assertEquals(1, rows.size)
        assertEquals(period.start, rows.single().chargeDate)
        assertEquals(250_000L, rows.single().amount)
    }

    @Test
    fun `a payment dated exactly on asOf is credited`() {
        // The boundary the `a payment dated after asOf produces no rows` case cannot see:
        // that one is a week clear of the edge, so swapping `isAfter` for `isBefore` — which
        // would drop every deposit recorded today — leaves all 30 tests green. asOf is today
        // in production, so a deposit keyed in this morning lands exactly here.
        val rows = allocate(
            Payment(id = "today", date = asOf, amount = 500_000L),
        )

        assertEquals(2, rows.size)
        assertEquals(days.take(2), rows.chargeDates())
        assertEquals(500_000L, rows.sumOf { it.amount })
    }

    @Test
    fun `a payment dated on asOf leaves the other payments untouched`() {
        val rows = allocate(
            Payment(id = "today", date = asOf, amount = 250_000L),
            Payment(id = "future", date = LocalDate.of(2026, 3, 25), amount = 250_000L),
        )

        assertEquals(1, rows.size)
        assertEquals("today", rows.single().paymentId)
        assertEquals(period.start, rows.single().chargeDate)
    }

    @Test
    fun `a payment dated after asOf produces no rows`() {
        // verify-fr10.md case 7. It is not rejected or dropped-with-a-warning: it simply has
        // not happened yet, and the next allocation run picks it up.
        val rows = allocate(
            Payment(id = "future", date = LocalDate.of(2026, 3, 30), amount = 250_000L),
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `a future payment leaves the other payments untouched`() {
        val rows = allocate(
            Payment(id = "p1", date = LocalDate.of(2026, 3, 20), amount = 500_000L),
            Payment(id = "future", date = LocalDate.of(2026, 3, 30), amount = 250_000L),
        )

        assertEquals(2, rows.size)
        assertTrue("the future payment owns none of the rows", rows.all { it.paymentId == "p1" })
        assertEquals(500_000L, rows.sumOf { it.amount })
    }

    @Test
    fun `no payments produce no allocations`() {
        val rows = allocatePayments(emptyList(), days, rate, asOf)

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `a payment on a contract with no charge days yet becomes one null row`() {
        // The contract starts after the period opens, so there is nothing to charge yet —
        // the money must still be on record, or it vanishes until someone retypes it.
        val rows = allocatePayments(
            payments = listOf(Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 250_000L)),
            chargeDays = chargeDaysIn(period, ChargeSchedule(contractStart = LocalDate.of(2026, 6, 1)), asOf),
            dailyRate = rate,
            asOf = asOf,
        )

        assertEquals(1, rows.size)
        assertNull(rows.single().chargeDate)
        assertEquals(250_000L, rows.single().amount)
    }

    @Test
    fun `a zero amount is rejected`() {
        val thrown = assertThrows(InvalidAmountException::class.java) {
            Payment(id = "p1", date = LocalDate.of(2026, 3, 1), amount = 0L)
        }

        assertEquals(0L, thrown.amount)
    }

    @Test
    fun `a negative amount is rejected`() {
        val thrown = assertThrows(InvalidAmountException::class.java) {
            Payment(id = "p1", date = LocalDate.of(2026, 3, 1), amount = -250_000L)
        }

        assertEquals(-250_000L, thrown.amount)
    }

    @Test
    fun `a zero rate is rejected`() {
        assertThrows(InvalidAmountException::class.java) {
            allocatePayments(emptyList(), days, dailyRate = 0L, asOf = asOf)
        }
    }

    @Test
    fun `an allocation row cannot be built for no money`() {
        // A zero row would leave Σ allocation == amount intact while recording that a day was
        // closed for nothing, which is exactly the sort of error no sum can catch.
        assertThrows(InvalidAmountException::class.java) {
            Allocation(paymentId = "p1", chargeDate = LocalDate.of(2026, 2, 25), amount = 0L)
        }
    }

    @Test
    fun `charge days out of order are rejected rather than silently mis-allocated`() {
        val shuffled = listOf(LocalDate.of(2026, 2, 26), LocalDate.of(2026, 2, 25))

        assertThrows(IllegalStateException::class.java) {
            allocatePayments(emptyList(), shuffled, rate, asOf)
        }
    }

    @Test
    fun `a repeated charge day is rejected because it would duplicate the unique key`() {
        val repeated = listOf(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 2, 25))

        assertThrows(IllegalStateException::class.java) {
            allocatePayments(emptyList(), repeated, rate, asOf)
        }
    }

    @Test
    fun `two deposits sharing an id are rejected because they would collide on the unique key`() {
        // Each row would carry the same paymentId, so a day closed twice. Nothing downstream
        // sums these rows per id, so no other rule would notice the collision.
        assertThrows(IllegalStateException::class.java) {
            allocatePayments(
                payments = listOf(
                    Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 250_000L),
                    Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 250_000L),
                ),
                chargeDays = days,
                dailyRate = rate,
                asOf = asOf,
            )
        }
    }

    @Test
    fun `a future payment sharing an id with a counted one is not a collision`() {
        // The id guard runs after the asOf filter on purpose: a re-scheduled deposit that was
        // moved into the future is not in this period's queue and cannot collide in its rows.
        val rows = allocatePayments(
            payments = listOf(
                Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 250_000L),
                Payment(id = "p1", date = LocalDate.of(2026, 3, 30), amount = 250_000L),
            ),
            chargeDays = days,
            dailyRate = rate,
            asOf = asOf,
        )

        assertEquals(1, rows.size)
        assertEquals(250_000L, rows.single().amount)
    }

    @Test
    fun `the remainder is what is left of the partly spent payment not its whole amount`() {
        // One day against a deposit four times too large for it: the day loop stops mid-payment,
        // so `remaining` (250.000) is smaller than `queue[index].amount` (1.000.000). The
        // remainder loop's `if (i == index) remaining else queue[i].amount` arm exists only for
        // this case, and every other test reaching it arrives with the two already equal — so
        // reading `queue[i].amount` unconditionally would report Rp 1.000.000 of credit for a
        // payment that has already spent Rp 750.000, and Σ would no longer hold.
        val rows = allocatePayments(
            payments = listOf(Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 1_000_000L)),
            chargeDays = listOf(LocalDate.of(2026, 2, 25)),
            dailyRate = rate,
            asOf = asOf,
        )

        assertEquals(2, rows.size)
        assertEquals(250_000L, rows[0].amount)
        assertEquals(LocalDate.of(2026, 2, 25), rows[0].chargeDate)
        assertNull(rows[1].chargeDate)
        assertEquals("only the unspent rupiah is credit", 750_000L, rows[1].amount)
        assertEquals(1_000_000L, rows.sumOf { it.amount })
    }

    @Test
    fun `a partly spent payment's remainder spans several spent days`() {
        // The same arm across two days: p1 spends 250.000 on each, so 500.000 is what remains
        // and 500.000 is what the null row must carry — not the 1.000.000 it arrived with, and
        // not the 250.000 a single-day remainder would leave behind.
        val rows = allocatePayments(
            payments = listOf(
                Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 1_000_000L),
                Payment(id = "p2", date = LocalDate.of(2026, 2, 25), amount = 250_000L),
            ),
            chargeDays = listOf(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 2, 26)),
            dailyRate = rate,
            asOf = asOf,
        )

        assertEquals(
            listOf("p1", "p1", "p1", "p2"),
            rows.map { it.paymentId },
        )
        assertEquals(listOf(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 2, 26)), rows.take(2).chargeDates())
        assertNull(rows[2].chargeDate)
        assertEquals("500.000 of the 1.000.000 was never spent", 500_000L, rows[2].amount)
        assertNull(rows[3].chargeDate)
        assertEquals(250_000L, rows[3].amount)
        assertEquals(1_250_000L, rows.sumOf { it.amount })
    }

    @Test
    fun `each payment holding cash keeps its own remainder row`() {
        // Three deposits, one charge day: only the first can be spent on it, so the other two
        // are credit. A single leftover row would drop two deposits' worth of rupiah, which is
        // this story's own bug reproduced at Rp 375.000 instead of Rp 13.000.000.
        val rows = allocatePayments(
            payments = listOf(
                Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 250_000L),
                Payment(id = "p2", date = LocalDate.of(2026, 2, 25), amount = 100_000L),
                Payment(id = "p3", date = LocalDate.of(2026, 2, 25), amount = 125_000L),
            ),
            chargeDays = listOf(LocalDate.of(2026, 2, 25)),
            dailyRate = rate,
            asOf = asOf,
        )

        assertEquals(3, rows.size)
        assertEquals(
            listOf("p1", "p2", "p3"),
            rows.map { it.paymentId },
        )
        assertEquals("the only spendable rupiah closes the day", LocalDate.of(2026, 2, 25), rows[0].chargeDate)
        assertEquals(250_000L, rows[0].amount)
        assertEquals(100_000L, rows[1].amount)
        assertNull(rows[1].chargeDate)
        assertEquals(125_000L, rows[2].amount)
        assertNull(rows[2].chargeDate)
        assertEquals("both remainders are kept", 225_000L, rows.filter { it.chargeDate == null }.sumOf { it.amount })
    }

    // --- Ordering and grouping ----------------------------------------------------------

    @Test
    fun `payments are consumed oldest first and ties break on id`() {
        // Two payments on the same day must land in a fixed order, or the stored rows would
        // differ between recomputes for no reason a tenant could explain.
        val rows = allocatePayments(
            payments = listOf(
                Payment(id = "b", date = LocalDate.of(2026, 2, 25), amount = 250_000L),
                Payment(id = "a", date = LocalDate.of(2026, 2, 25), amount = 250_000L),
                Payment(id = "c", date = LocalDate.of(2026, 2, 24), amount = 250_000L),
            ),
            chargeDays = days,
            dailyRate = rate,
            asOf = asOf,
        )

        assertEquals(listOf("c", "a", "b"), rows.map { it.paymentId })
        assertEquals(days.take(3), rows.map { it.chargeDate })
    }

    @Test
    fun `a later payment tops up the day an earlier one only partly closed`() {
        // Two deposits, neither enough on its own. They combine on the oldest day rather than
        // each claiming a day of its own, which is what "Σ allocation == amount per payment"
        // plus "no day charged twice" implies.
        val rows = allocatePayments(
            payments = listOf(
                Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 100_000L),
                Payment(id = "p2", date = LocalDate.of(2026, 2, 26), amount = 150_000L),
            ),
            chargeDays = days,
            dailyRate = rate,
            asOf = asOf,
        )

        assertEquals(2, rows.size)
        assertEquals(listOf(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 2, 25)), rows.chargeDates())
        assertEquals(listOf("p1", "p2"), rows.map { it.paymentId })
        assertEquals(250_000L, rows.sumOf { it.amount })
    }

    @Test
    fun `one payment spanning several days produces ascending rows with no remainder last`() {
        val rows = allocate(
            Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 600_000L),
        )

        assertEquals(3, rows.size)
        assertEquals(days.take(3), rows.chargeDates())
        assertTrue("the null remainder is never emitted when there is none", rows.all { it.chargeDate != null })
    }

    // --- Prefix stability ---------------------------------------------------------------

    @Test
    fun `adding the next period's days leaves every earlier row untouched`() {
        // The property that makes storing credit safe: when the period rolls, the earlier
        // facts are extended, never rewritten. If this fails, a recompute would edit rows a
        // tenant's money already closed.
        val payments = listOf(
            Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 500_000L),
            Payment(id = "p2", date = LocalDate.of(2026, 2, 25), amount = 20_000_000L),
            Payment(id = "p3", date = LocalDate.of(2026, 3, 2), amount = 125_000L),
        )

        val thisPeriod = allocatePayments(payments, days, rate, asOf)
        val acrossBoth = allocatePayments(payments, days + chargeDaysIn(nextPeriod, sevenDay, nextAsOf), rate, nextAsOf)

        val earlierDates = days.toSet()
        assertEquals(
            thisPeriod.filter { it.chargeDate in earlierDates },
            acrossBoth.filter { it.chargeDate in earlierDates },
        )
    }

    @Test
    fun `prefix stability holds for a sweep of payment amounts`() {
        // Swept rather than stated once: the property is the one AD-3 depends on, and an
        // amount that happens to align with the day count would pass a single-case test.
        val sweepDays = days + chargeDaysIn(nextPeriod, sevenDay, nextAsOf)

        listOf(1L, 100_000L, 249_999L, 250_000L, 250_001L, 7_000_000L, 20_000_000L, 59_000_000L)
            .forEach { amount ->
                val payments = listOf(Payment(id = "p", date = LocalDate.of(2026, 2, 25), amount = amount))
                val short = allocatePayments(payments, days, rate, asOf)
                val long = allocatePayments(payments, sweepDays, rate, nextAsOf)

                assertEquals(
                    "rows for the first period must be identical for a Rp $amount payment",
                    short.filter { it.chargeDate in days.toSet() },
                    long.filter { it.chargeDate in days.toSet() },
                )
            }
    }

    @Test
    fun `the surplus row is regenerated as later days consume it`() {
        // The null row is not accumulated. Appending the next period's 31 days moves
        // Rp 7.750.000 of the surplus onto them and rewrites the row to what is left — which
        // is why credit can be regenerated from stored facts instead of being carried
        // forward in a column somebody has to remember to update.
        val payment = Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 20_000_000L)
        val bothDays = days + chargeDaysIn(nextPeriod, sevenDay, nextAsOf)

        val first = allocate(payment)
        val second = allocatePayments(listOf(payment), bothDays, rate, nextAsOf)

        assertEquals(13_000_000L, first.single { it.chargeDate == null }.amount)
        assertEquals(59, second.count { it.chargeDate != null })
        assertEquals(59L * rate, second.filter { it.chargeDate != null }.sumOf { it.amount })
        assertEquals(5_250_000L, second.single { it.chargeDate == null }.amount)
    }

    // --- Property sweeps ---------------------------------------------------------------

    @Test
    fun `every payment's rows sum to its amount`() {
        scenarios().forEach { scenario ->
            val rows = allocatePayments(scenario.payments, scenario.days, scenario.rate, scenario.asOf)

            scenario.payments
                .filter { !it.date.isAfter(scenario.asOf) }
                .forEach { payment ->
                    val total = rows.filter { it.paymentId == payment.id }.sumOf { it.amount }
                    assertEquals(
                        "AD-3: allocation rows must sum to the payment's amount (${scenario.name})",
                        payment.amount,
                        total,
                    )
                }
        }
    }

    @Test
    fun `no two rows share a paymentId and chargeDate pair`() {
        scenarios().forEach { scenario ->
            val rows = allocatePayments(scenario.payments, scenario.days, scenario.rate, scenario.asOf)

            val keys = rows.map { it.paymentId to it.chargeDate }
            assertEquals(
                "UNIQUE(paymentId, chargeDate) must hold by construction (${scenario.name})",
                keys.size,
                keys.toSet().size,
            )
        }
    }

    // --- Fixtures ----------------------------------------------------------------------

    /** Allocates against the fixture period, its 28 charge days and today's date. */
    private fun allocate(vararg payments: Payment): List<Allocation> =
        allocatePayments(payments.toList(), days, rate, asOf)

    /** Charge dates in emission order; `null` marks the unplaced remainder row. */
private fun List<Allocation>.chargeDates(): List<LocalDate?> = map { it.chargeDate }

    private fun scenario(name: String, days: List<LocalDate>, asOf: LocalDate, vararg payments: Payment) =
        Scenario(name, payments.toList(), days, rate, asOf)

    /**
     * One entry per awkward shape the matrix names plus the ones it leaves implicit.
     *
     * A sweep is the only place the two unconditional rules get tested against inputs nobody
     * wrote a named test for — several payments, partial sums, overpay, rest days, future
     * payments, and a contract with no charge days at all.
     */
    private fun scenarios(): List<Scenario> {
        val sixDay = ChargeSchedule(contractStart = contractStart, restDay = DayOfWeek.SUNDAY)
        return listOf(
            scenario("no payments", days, asOf),
            scenario(
                "single exact fit",
                days,
                asOf,
                Payment("p1", LocalDate.of(2026, 2, 25), 7_000_000L),
            ),
            scenario(
                "single overpay",
                days,
                asOf,
                Payment("p1", LocalDate.of(2026, 2, 25), 20_000_000L),
            ),
            scenario(
                "two partials on one day",
                days,
                asOf,
                Payment("p1", LocalDate.of(2026, 2, 25), 100_000L),
                Payment("p2", LocalDate.of(2026, 2, 26), 150_000L),
            ),
            scenario(
                "mixed with a future payment",
                days,
                asOf,
                Payment("p1", LocalDate.of(2026, 2, 25), 300_000L),
                Payment("p2", LocalDate.of(2026, 3, 1), 750_000L),
                Payment("p3", LocalDate.of(2026, 3, 30), 250_000L),
            ),
            scenario(
                "six day schedule",
                chargeDaysIn(period, sixDay, asOf),
                asOf,
                Payment("p1", LocalDate.of(2026, 3, 1), 250_000L),
                Payment("p2", LocalDate.of(2026, 2, 25), 6_000_000L),
            ),
            scenario(
                "contract not started",
                chargeDaysIn(period, ChargeSchedule(contractStart = LocalDate.of(2026, 6, 1)), asOf),
                asOf,
                Payment("p1", LocalDate.of(2026, 2, 25), 250_000L),
            ),
            scenario(
                "amounts spanning the rollover",
                days + chargeDaysIn(nextPeriod, sevenDay, nextAsOf),
                nextAsOf,
                Payment("p1", LocalDate.of(2026, 2, 25), 500_000L),
                Payment("p2", LocalDate.of(2026, 3, 20), 12_345_678L),
            ),
        )
    }

    private data class Scenario(
        val name: String,
        val payments: List<Payment>,
        val days: List<LocalDate>,
        val rate: Long,
        val asOf: LocalDate,
    )
}
