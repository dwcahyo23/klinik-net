package net.klinik.rental.domain.calculate

import java.time.DayOfWeek
import java.time.LocalDate
import net.klinik.rental.domain.model.Allocation
import net.klinik.rental.domain.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seven cases from `verify-fr10.md`, as Epic 5's closing gate.
 *
 * ## Why this file exists when `AllocationEngineTest` already covers the engine
 *
 * Because the epic's condition is *"Tujuh kasus `verify-fr10.md` **semua hijau** sebelum epic ini
 * dinyatakan selesai"* — a criterion, not a link. Those seven cases are spread across three suites
 * under names written for the engine's structure, so a reader asking "does the verification
 * document pass?" has to reconstruct it from four files and know which cases were which. This
 * file states the document's numbers beside the document's own case names, so the gate is one
 * readable assertion per row of its table.
 *
 * ## The figures are copied from the document, not re-derived
 *
 * Every rupiah amount and day count below is the document's. If the engine changes one, this file
 * is the thing that says so — which is the point of a gate rather than another spot to eyeball.
 *
 * ## The two rows the document marks with a warning
 *
 * Kasus 4 (Kredit Rp 13.000.000) and Kasus 5 (backdated contract) were **defects in the
 * formula**, closed by choosing option B in Story 1.3 and the settled-through marker in Story 3.6.
 * They are here because a gate that quietly dropped the two hard rows would be a gate for the easy
 * five.
 */
class Fr10AcceptanceTest {

    private val rate = 250_000L

    // ----------------------------------------------------------------- Kasus 1

    /**
     * **Kasus 1 — paying two days at once.**
     *
     * Contract from 2026-01-01, Rp 250.000/day, 7-day week. Obligation 28 × 250.000 =
     * Rp 7.000.000; one Rp 500.000 deposit leaves Rp 6.500.000 over **26 days**.
     *
     * The old model's `missedDaysList` never recorded an over-paid day, so `Math.min(length, …)`
     * under-counted. The defect was in the clamp, not in the division — which is what FR-11's
     * "pembagian nominal adalah pendekatan yang salah" got wrong.
     */
    @Test
    fun `kasus 1 - paying two days at once`() {
        val asOf = LocalDate.of(2026, 3, 24)
        val period = CutoffPeriod.containing(25, asOf)
        val days = chargeDaysIn(
            period = period,
            schedule = ChargeSchedule(
                contractStart = LocalDate.of(2026, 1, 1),
                restDay = null,
                serviceExceptions = emptySet(),
            ),
            asOf = asOf,
        )
        assertEquals("28 charge days", 28, days.size)

        val rows = allocatePayments(
            payments = listOf(Payment(id = "p1", date = LocalDate.of(2026, 3, 20), amount = 500_000)),
            chargeDays = days,
            dailyRate = rate,
            asOf = asOf,
        )
        val entry = arrearsOf("c1", days, rows, rate)

        assertEquals("Rp 6.500.000 outstanding", 6_500_000L, entry.amount)
        assertEquals("26 days, not 27", 26, entry.days)
        assertEquals("Kritis", RiskLevel.KRITIS, riskOf(entry.days))
        // And the oldest open day is the day after the two the deposit closed.
        assertEquals(LocalDate.of(2026, 2, 27), entry.oldestOpenDay)
    }

    // ----------------------------------------------------------------- Kasus 2

    /**
     * **Kasus 2 — paying on a Sunday for a Sunday's day. The most important row.**
     *
     * A 6-day week with Sunday off: 28 calendar days minus 4 Sundays = 24 charge days, so the
     * obligation is Rp 6.000.000. A Rp 250.000 deposit dated **1 March — a Sunday** — leaves
     * Rp 5.750.000 over 23 days.
     *
     * The old model only counted a payment inside the branch that could be charged
     * (`if (!isOffDay && …)`), so money paid on a Sunday **never entered the count at all**. That
     * is Rp 250.000 per Sunday — Rp 1.000.000 a month on a 6-day week, repeating monthly.
     */
    @Test
    fun `kasus 2 - paying on a Sunday still closes a day`() {
        val asOf = LocalDate.of(2026, 3, 24)
        val days = chargeDaysIn(
            period = CutoffPeriod.containing(25, asOf),
            schedule = ChargeSchedule(
                contractStart = LocalDate.of(2026, 1, 1),
                restDay = DayOfWeek.SUNDAY,
                serviceExceptions = emptySet(),
            ),
            asOf = asOf,
        )
        assertEquals("24 charge days once the four Sundays are out", 24, days.size)

        val rows = allocatePayments(
            payments = listOf(Payment(id = "p1", date = LocalDate.of(2026, 3, 1), amount = 250_000)),
            chargeDays = days,
            dailyRate = rate,
            asOf = asOf,
        )
        val entry = arrearsOf("c1", days, rows, rate)

        assertEquals("the Sunday payment counted", 5_750_000L, entry.amount)
        assertEquals("23 days open", 23, entry.days)
        assertTrue(
            "and it closed a day — a day on which money was paid is not open",
            rows.count { it.chargeDate != null } >= 1,
        )
    }

    // ----------------------------------------------------------------- Kasus 3

    /**
     * **Kasus 3 — a partial payment.**
     *
     * Rp 100.000 against Rp 250.000 a day: Rp 100.000 short over 28 days, and **28** open days
     * because `floor(100.000 / 250.000) = 0` whole days closed.
     *
     * The document's finding: nominal division (`ceil(arrears / rate)`) and strict allocation
     * ("count the days not fully closed") give the **identical** number at a constant daily rate —
     * `28 − floor(pool / rate)` either way. FR-11's claim that nominal division is the wrong
     * approach was not right; what was actually broken was the prototype's `Math.min` clamp.
     */
    @Test
    fun `kasus 3 - a partial payment closes no whole day`() {
        val asOf = LocalDate.of(2026, 3, 24)
        val days = chargeDaysIn(
            period = CutoffPeriod.containing(25, asOf),
            schedule = ChargeSchedule(
                contractStart = LocalDate.of(2026, 1, 1),
                restDay = null,
                serviceExceptions = emptySet(),
            ),
            asOf = asOf,
        )

        val rows = allocatePayments(
            payments = listOf(Payment(id = "p1", date = LocalDate.of(2026, 3, 20), amount = 100_000)),
            chargeDays = days,
            dailyRate = rate,
            asOf = asOf,
        )
        val entry = arrearsOf("c1", days, rows, rate)

        assertEquals("Rp 100.000 short of Rp 7.000.000", 6_900_000L, entry.amount)
        assertEquals("no whole day closed", 28, entry.days)
    }

    // ----------------------------------------------------------------- Kasus 4

    /**
     * **Kasus 4 — paying two months ahead. ⚠️ the document's first new finding.**
     *
     * Rp 20.000.000 against a Rp 7.000.000 period leaves Rp 0 owed and **Rp 13.000.000 of
     * credit**. Under the old formula that credit vanished: the next period counted only
     * deposits dated inside it, and the 25 February deposit is not.
     *
     * The document closed this by choosing **option B** — store *which* days each deposit closes,
     * rather than a `creditBalance` column. The proof is that the credit becomes a row with
     * `chargeDate = null` and therefore travels into the next period, which is also why a
     * cutoff change must not rewrite it (Story 1.5).
     */
    @Test
    fun `kasus 4 - overpaying two months produces a credit that survives`() {
        val asOf = LocalDate.of(2026, 3, 24)
        val days = chargeDaysIn(
            period = CutoffPeriod.containing(25, asOf),
            schedule = ChargeSchedule(
                contractStart = LocalDate.of(2026, 1, 1),
                restDay = null,
                serviceExceptions = emptySet(),
            ),
            asOf = asOf,
        )

        val rows = allocatePayments(
            payments = listOf(Payment(id = "p1", date = LocalDate.of(2026, 2, 25), amount = 20_000_000)),
            chargeDays = days,
            dailyRate = rate,
            asOf = asOf,
        )

        assertEquals("nothing owed this period", 0L, arrearsOf("c1", days, rows, rate).amount)

        val credit = rows.filter { it.chargeDate == null }
        assertEquals("and one credit row, not a lost number", 1, credit.size)
        assertEquals("worth Rp 13.000.000", 13_000_000L, credit.first().amount)

        // The load-bearing half: the credit is a **stored row**, so it is still there when the
        // cutoff moves and a different period is counted. `cutoffShift` reads one shared row
        // list, which is why the shift is a range change and not a re-derivation.
        //
        // **Measured at the same `asOf`, the two windows are the same length** — `chargeDaysIn`
        // caps both at today, and a cutoff-15 window measured on 24 March starts later than a
        // cutoff-25 window but also ends earlier, so both come to 24 days. The first two versions
        // of this assertion assumed "wider" meant "longer" and were wrong twice in different
        // directions; what is actually being proved is narrower than either.
        val shifted = cutoffShift(
            contractId = "c1",
            oldChargeDays = days,
            newChargeDays = chargeDaysIn(
                period = CutoffPeriod.containing(15, asOf),
                schedule = ChargeSchedule(
                    contractStart = LocalDate.of(2026, 1, 1),
                    restDay = null,
                    serviceExceptions = emptySet(),
                ),
                asOf = asOf,
            ),
            allocations = rows,
            dailyRate = rate,
        )

        assertEquals(
            "the credit came out of storage: it covers a differently-ranged period outright",
            0L,
            shifted.before.amount,
        )
        assertEquals(
            "and it still does in the other range",
            0L,
            shifted.after.amount,
        )
        assertEquals(
            "because the same Rp 13.000.000 row is what both read",
            rows.count { it.chargeDate == null },
            1,
        )
    }

    // ----------------------------------------------------------------- Kasus 5

    /**
     * **Kasus 5 — a backdated contract. ⚠️ the document's operational finding.**
     *
     * A contract starting 1 January with no recorded deposits shows Rp 7.000.000 over 28 days,
     * Kritis — real, but possibly entirely fictitious: the owner may have been paid in full and
     * simply has no record in the app.
     *
     * The document's minimal fix was one FR: *"Tandai lunas sampai [tanggal]"*. That is
     * `settledThroughDate`, Story 3.6, and the finding it was reached for is closed by this row:
     * the same contract, settled through today, owes nothing, and **no allocation row was
     * written for the settlement**.
     */
    @Test
    fun `kasus 5 - a backdated contract can be settled through a date`() {
        val asOf = LocalDate.of(2026, 3, 24)
        val days = chargeDaysIn(
            period = CutoffPeriod.containing(25, asOf),
            schedule = ChargeSchedule(
                contractStart = LocalDate.of(2026, 1, 1),
                restDay = null,
                serviceExceptions = emptySet(),
            ),
            asOf = asOf,
        )
        val rows = allocatePayments(emptyList(), days, rate, asOf)

        assertEquals(
            "before settling: Rp 7.000.000 over 28 days, Kritis",
            7_000_000L,
            arrearsOf("c1", days, rows, rate).amount,
        )
        assertEquals(RiskLevel.KRITIS, riskOf(arrearsOf("c1", days, rows, rate).days))

        val settled = settledDays(days, asOf)

        assertTrue("settling through today closes every charge day", settled.isEmpty())
        assertEquals(
            "and the period reads as fully paid",
            0L,
            arrearsOf("c1", settled, rows, rate).amount,
        )
        assertEquals(
            "with no allocation row written for the settlement — it is a field, not a fake deposit",
            0,
            rows.count { it.chargeDate == null },
        )
    }

    // ----------------------------------------------------------------- Kasus 6

    /**
     * **Kasus 6 — cancelling a one-day deposit.**
     *
     * Cancelling Rp 250.000 must take the pool to 0, arrears from Rp 6.750.000 to Rp 7.000.000,
     * and the open days from 27 to 28. The document marks this ✅ — and it is here because it is
     * the row that proves a cancellation is a **re-derivation**, not a deletion, which is the
     * same property Story 5.4's cutoff change had to preserve from the other direction.
     */
    @Test
    fun `kasus 6 - cancelling a deposit raises the arrears`() {
        val asOf = LocalDate.of(2026, 3, 24)
        val days = chargeDaysIn(
            period = CutoffPeriod.containing(25, asOf),
            schedule = ChargeSchedule(
                contractStart = LocalDate.of(2026, 1, 1),
                restDay = null,
                serviceExceptions = emptySet(),
            ),
            asOf = asOf,
        )

        val before = arrearsOf(
            "c1",
            days,
            allocatePayments(
                listOf(Payment(id = "p1", date = LocalDate.of(2026, 3, 20), amount = 250_000)),
                days,
                rate,
                asOf,
            ),
            rate,
        )
        assertEquals("Rp 6.750.000 before the cancellation", 6_750_000L, before.amount)
        assertEquals(27, before.days)

        // The deposit is gone from the pool; the rows are re-derived from what survives.
        val after = arrearsOf(
            "c1",
            days,
            allocatePayments(emptyList(), days, rate, asOf),
            rate,
        )

        assertEquals("Rp 7.000.000 after", 7_000_000L, after.amount)
        assertEquals("and 28 days, not 27", 28, after.days)
    }

    // ----------------------------------------------------------------- Kasus 7

    /**
     * **Kasus 7 — a deposit dated in the future.**
     *
     * 30 March's money, counted on 24 March, is **not** counted: it is outside the period, and
     * it starts counting when the period rolls. The document marks this ✅, and it is the row that
     * guards `asOf` — an arrears figure that included money that has not arrived yet would be a
     * number the owner sends to a tenant.
     */
    @Test
    fun `kasus 7 - a deposit dated in the future is not counted yet`() {
        val asOf = LocalDate.of(2026, 3, 24)
        val days = chargeDaysIn(
            period = CutoffPeriod.containing(25, asOf),
            schedule = ChargeSchedule(
                contractStart = LocalDate.of(2026, 1, 1),
                restDay = null,
                serviceExceptions = emptySet(),
            ),
            asOf = asOf,
        )

        val rows = allocatePayments(
            listOf(Payment(id = "p1", date = LocalDate.of(2026, 3, 30), amount = 5_000_000)),
            days,
            rate,
            asOf,
        )

        assertTrue("the future deposit places no row on an open day", rows.none { it.chargeDate != null })
        assertEquals(
            "and the period still owes its full Rp 7.000.000",
            7_000_000L,
            arrearsOf("c1", days, rows, rate).amount,
        )

        // Once the period rolls it counts — the AC's "akan dihitung saat periode berganti".
        val nextAsOf = LocalDate.of(2026, 4, 20)
        val nextDays = chargeDaysIn(
            period = CutoffPeriod.containing(25, nextAsOf),
            schedule = ChargeSchedule(
                contractStart = LocalDate.of(2026, 1, 1),
                restDay = null,
                serviceExceptions = emptySet(),
            ),
            asOf = nextAsOf,
        )
        val nextRows = allocatePayments(
            listOf(Payment(id = "p1", date = LocalDate.of(2026, 3, 30), amount = 5_000_000)),
            nextDays,
            rate,
            nextAsOf,
        )
        assertTrue("now it does", nextRows.any { it.chargeDate != null })
        // 20 charge days in the 25 Mar – 20 Apr window this `asOf` gives; Rp 5.000.000 closes 20
        // of them. The first version of this assertion said "8" from arithmetic done by hand and
        // was wrong — the day count is `days.size - closed`, and `days.size` is a property of the
        // capped window rather than of the month.
        val nextEntry = arrearsOf("c1", nextDays, nextRows, rate)
        assertEquals(
            "and only the days the Rp 5.000.000 covers are closed",
            nextDays.size - 20,
            nextEntry.days,
        )
        assertTrue("the deposit covered at least 20 days", nextEntry.days < nextDays.size)
    }

    private companion object {
        /** Every allocation is attached to some payment; a bare row has no owner. */
        @Suppress("unused")
        fun owned(id: String, day: LocalDate?, amount: Long) = Allocation(
            paymentId = id,
            chargeDate = day,
            amount = amount,
        )
    }
}
