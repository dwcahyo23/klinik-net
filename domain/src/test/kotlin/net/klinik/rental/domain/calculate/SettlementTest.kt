package net.klinik.rental.domain.calculate

import net.klinik.rental.domain.model.Allocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * FR-9b: "tandai lunas sampai tanggal" narrows the day list. Settlement never writes
 * allocation rows, so an empty narrowed list must read as a fully paid period — 0 days,
 * 0 rupiah, LANCAR — the same figure a period whose days all carry full rows reads.
 */
class SettlementTest {

    private val today = LocalDate.of(2026, 3, 24)
    private val periodA = CutoffPeriod.containing(25, today)
    private val asOf = LocalDate.of(2026, 3, 24)
    private val rate = 250_000L
    private val schedule = ChargeSchedule(LocalDate.of(2026, 1, 1))
    private val days = chargeDaysIn(periodA, schedule, asOf) // 28 days, 25 Feb – 24 Mar

    private fun row(day: LocalDate, amount: Long = rate, paymentId: String = "p") = Allocation(paymentId, day, amount)

    @Test
    fun `settledThroughDate akhir periode menutup seluruh hari tanpa baris alokasi`() {
        val open = settledDays(days, LocalDate.of(2026, 3, 24))
        assertTrue(open.isEmpty())

        val entry = arrearsOf("c1", open, emptyList(), rate)
        assertEquals(0, entry.days)
        assertEquals(0L, entry.amount)
        assertNull(entry.oldestOpenDay)
        assertEquals(RiskLevel.LANCAR, riskOf(entry.days))
    }

    @Test
    fun `settledThroughDate tengah periode menyisakan hari setelahnya`() {
        val open = settledDays(days, LocalDate.of(2026, 3, 10))
        assertEquals(14, open.size)
        assertEquals(LocalDate.of(2026, 3, 11), open.first())
        assertEquals(LocalDate.of(2026, 3, 24), open.last())

        val entry = arrearsOf("c1", open, emptyList(), rate)
        assertEquals(14, entry.days)
        assertEquals(3_500_000L, entry.amount)
        assertEquals(LocalDate.of(2026, 3, 11), entry.oldestOpenDay)
    }

    @Test
    fun `settledThroughDate di awal periode menutup hari pertama saja`() {
        val open = settledDays(days, LocalDate.of(2026, 2, 25))
        assertEquals(27, open.size)
        assertEquals(LocalDate.of(2026, 2, 26), open.first())
    }

    @Test
    fun `settledThroughDate sebelum periode tidak menutup apa pun`() {
        val open = settledDays(days, LocalDate.of(2026, 2, 20))
        assertEquals(28, open.size)
        assertTrue(open == days)
    }

    @Test
    fun `settledThroughDate null mengembalikan daftar hari apa adanya`() {
        assertSame(days, settledDays(days, null))
    }

    @Test
    fun `settledThroughDate tengah periode tetap dihitung bersama baris setelahnya`() {
        val open = settledDays(days, LocalDate.of(2026, 3, 10))
        val rows = listOf(
            row(LocalDate.of(2026, 3, 11)),
            row(LocalDate.of(2026, 3, 12)),
            row(LocalDate.of(2026, 3, 13)),
            row(LocalDate.of(2026, 3, 14)),
            row(LocalDate.of(2026, 3, 15)),
        )

        val entry = arrearsOf("c1", open, rows, rate)
        assertEquals(9, entry.days)
        assertEquals(2_250_000L, entry.amount)
        assertEquals(LocalDate.of(2026, 3, 16), entry.oldestOpenDay)
    }

    @Test
    fun `settledThroughDate di hari libur sama dengan hari sebelumnya`() {
        // 6-day schedule: Sundays are not charge days. settledThroughDate on a Sunday
        // (1 Mar 2026) closes the same set of days as settling through the preceding
        // charge day (28 Feb) — a marker on a non-charge day is indistinguishable from
        // one on the charge day before it.
        val sixDay = ChargeSchedule(LocalDate.of(2026, 1, 1), restDay = DayOfWeek.SUNDAY)
        val chargeDays = chargeDaysIn(periodA, sixDay, asOf) // Sundays excluded
        val sunday = LocalDate.of(2026, 3, 1)
        val precedingChargeDay = LocalDate.of(2026, 2, 28)
        assertEquals(settledDays(chargeDays, sunday), settledDays(chargeDays, precedingChargeDay))
    }

    @Test
    fun `settledThroughDate akhir periode dan baris penuh sama-sama nol`() {
        val open = settledDays(days, LocalDate.of(2026, 3, 24))
        val rows = days.map { row(it) } // 28 full rows

        // The rows are not double-counted against settlement: settlement narrows the day
        // list first, and only the narrowed list is read.
        val entry = arrearsOf("c1", open, rows, rate)
        assertEquals(0, entry.days)
        assertEquals(0L, entry.amount)
    }
}
