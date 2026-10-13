package net.klinik.rental.domain.calculate

import net.klinik.rental.domain.model.Allocation
import net.klinik.rental.domain.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * FR-15a: changing the cutoff day shifts the charge-day range, it never re-derives the
 * stored rows. One test per observable consequence of the shift.
 */
class CutoffShiftTest {

    private val today = LocalDate.of(2026, 3, 24)
    private val periodA = CutoffPeriod.containing(25, today) // 25 Feb – 24 Mar, 28 days
    private val periodB = CutoffPeriod.containing(15, today) // 15 Mar – 14 Apr
    private val asOf = LocalDate.of(2026, 3, 24)
    private val rate = 250_000L
    private val schedule = ChargeSchedule(LocalDate.of(2026, 1, 1))
    private val daysA = chargeDaysIn(periodA, schedule, asOf) // 28 days
    private val daysB = chargeDaysIn(periodB, schedule, asOf) // 10 days (15–24 Mar, capped at asOf)

    private fun row(day: LocalDate, amount: Long = rate, paymentId: String = "p") = Allocation(paymentId, day, amount)

    @Test
    fun `geser cutoff dari 25 ke 15 hanya menggeser rentang, baris tidak berubah`() {
        // Fixture sanity: stop here if the period math ever drifts.
        assertEquals(28, daysA.size)
        assertEquals(10, daysB.size)

        val rows = allocatePayments(listOf(Payment("p", LocalDate.of(2026, 2, 25), rate)), daysA, rate, asOf)
        assertEquals(1, rows.size)
        assertEquals(rate, rows[0].amount)
        assertEquals(LocalDate.of(2026, 2, 25), rows[0].chargeDate)

        val result = cutoffShift("c1", daysA, daysB, rows, rate)

        // B (cutoff 15, today 24 Mar) runs 15 Mar – 14 Apr, capped at asOf → 15–24 Mar:
        // 25 Feb is outside it, so no row counts there and all 10 days stay open.
        assertEquals(27, result.before.days)
        assertEquals(6_750_000L, result.before.amount)
        assertEquals(10, result.after.days)
        assertEquals(2_500_000L, result.after.amount)

        // The shift is a range shift: the rows are re-read, never rewritten.
        assertEquals(1, rows.size)
        assertTrue(rows.any { it.chargeDate == LocalDate.of(2026, 2, 25) })
    }

    @Test
    fun `baris 24 Mar tetap masuk hitungan saat cutoff berubah dari 25 ke 15`() {
        // 24 Mar is inside period B's day list (15 Mar – 14 Apr) but before the asOf cap, so
        // a row on it counts for B exactly as it counts for A: the shift moves the range,
        // it does not delete the row.
        val rows = listOf(row(LocalDate.of(2026, 3, 24)))
        val result = cutoffShift("c1", daysA, daysB, rows, rate)

        // A: 24 Mar is the newest of 28 charge days → 27 open.
        assertEquals(27, result.before.days)
        assertEquals(6_750_000L, result.before.amount)
        // B: 24 Mar is the last of the 10 charge days (15–24 Mar, capped at asOf) → 9 open.
        assertEquals(9, result.after.days)
        assertEquals(2_250_000L, result.after.amount)

        // The row persists in the shared list; it counts again when a period containing a
        // day it closes is computed — here, B's range still reaches 24 Mar.
        assertTrue(rows.any { it.chargeDate == LocalDate.of(2026, 3, 24) })
    }

    @Test
    fun `baris kredit chargeDate null bertahan saat cutoff bergeser`() {
        val rows = allocatePayments(listOf(Payment("p", LocalDate.of(2026, 2, 25), 20_000_000L)), daysA, rate, asOf)
        assertEquals(29, rows.size)
        val nullRow = rows.filter { it.chargeDate == null }
        assertEquals(1, nullRow.size)
        assertEquals(13_000_000L, nullRow[0].amount)

        val result = cutoffShift("c1", daysA, daysB, rows, rate)

        // A is fully closed by the 28 day-rows (28×250.000 = 7.000.000 on listed days).
        // B's 10 charge days (15–24 Mar) are exactly the last ten of A's 28 day-rows, so
        // they are closed too; the null row counts in neither list, its 13.000.000 never
        // leaks into either figure.
        assertEquals(0, result.before.days)
        assertEquals(0L, result.before.amount)
        assertEquals(0, result.after.days)
        assertEquals(0L, result.after.amount)

        // The credit row survives every shift — it is regenerated, never deleted.
        assertEquals(29, rows.size)
        assertTrue(rows.any { it.chargeDate == null && it.amount == 13_000_000L })
    }

    @Test
    fun `baris 25 Feb keluar dari hitungan B saat cutoff berubah dari 25 ke 1, tapi tetap ada`() {
        // Period B (cutoff 1, today 24 Mar) runs 1 Mar – 31 Mar, capped at asOf → 1–24 Mar.
        // A row on 25 Feb is inside period A's list but outside B's: B ignores it, the row
        // still exists in the shared list, and it counts again when a period containing 25 Feb
        // is next computed. This is the "day exits the period" property of the shift decision.
        val periodC = CutoffPeriod.containing(1, today)
        val daysC = chargeDaysIn(periodC, schedule, asOf) // 1–24 Mar, 24 days
        val rows = listOf(row(LocalDate.of(2026, 2, 25)))
        val result = cutoffShift("c1", daysA, daysC, rows, rate)

        // A: 25 Feb closed → 27 open, 6.750.000.
        assertEquals(27, result.before.days)
        assertEquals(6_750_000L, result.before.amount)
        // C: 25 Feb not in C's list (C starts 1 Mar) → all 24 days open, 6.000.000.
        assertEquals(24, result.after.days)
        assertEquals(6_000_000L, result.after.amount)

        // The exiting row persists; it is ignored by B, not deleted.
        assertEquals(1, rows.size)
        assertTrue(rows.any { it.chargeDate == LocalDate.of(2026, 2, 25) })
    }

    @Test
    fun `baris 20 Mar tetap menutup hari saat cutoff bergeser ke 15`() {
        // 20 Mar is inside both A's (25 Feb – 24 Mar) and B's (15 Mar – 14 Apr, capped to
        // 15–24 Mar) day lists, so a row on it closes a day in both — the "day re-enters"
        // property: the range moves, the row's day still counts.
        val rows = listOf(row(LocalDate.of(2026, 3, 20)))
        val result = cutoffShift("c1", daysA, daysB, rows, rate)

        assertEquals(27, result.before.days)
        assertEquals(6_750_000L, result.before.amount)
        assertEquals(9, result.after.days)
        assertEquals(2_250_000L, result.after.amount)
    }

    @Test
    fun `geser tanpa baris alokasi sama sekali hanya menggeser rentang`() {
        // No stored rows: every listed day is open, and the figure is simply the range
        // times the rate on each side. Empty allocations stay empty through the shift.
        val result = cutoffShift("c1", daysA, daysB, emptyList(), rate)

        assertEquals(28, result.before.days)
        assertEquals(7_000_000L, result.before.amount)
        assertEquals(10, result.after.days)
        assertEquals(2_500_000L, result.after.amount)
    }

    @Test
    fun `geser ke periode yang harinya semua lewat asOf menghasilkan angka nol`() {
        // The new period's charge days all fall after asOf, so the shifted range is empty:
        // `after` is the zero figure, and a `chargeDate = null` credit row contributes
        // nothing to it — it neither leaks in nor is lost.
        val rows = listOf(Allocation("p", null, 13_000_000L))
        val result = cutoffShift("c1", daysA, emptyList(), rows, rate)

        assertEquals(28, result.before.days)
        assertEquals(0, result.after.days)
        assertEquals(0L, result.after.amount)
        assertTrue(rows.any { it.chargeDate == null && it.amount == 13_000_000L })
    }

    @Test
    fun `geser cutoff ke hari yang sama menghasilkan before sama dengan after`() {
        val rows = listOf(row(LocalDate.of(2026, 2, 25)))
        val result = cutoffShift("c1", daysA, daysA, rows, rate)

        assertEquals(result.after, result.before)
    }
}
