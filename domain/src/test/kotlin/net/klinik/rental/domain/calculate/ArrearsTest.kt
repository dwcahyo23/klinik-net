package net.klinik.rental.domain.calculate

import net.klinik.rental.domain.model.Allocation
import net.klinik.rental.domain.validation.InvalidAmountException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Story 1.4's matrix, one row per test, on the `verify-fr10.md` fixture: period
 * 2026-02-25 → 2026-03-24 = 28 charge days at Rp 250.000.
 */
class ArrearsTest {

    private val period = CutoffPeriod(
        start = LocalDate.of(2026, 2, 25),
        end = LocalDate.of(2026, 3, 24),
    )
    private val asOf = LocalDate.of(2026, 3, 24)
    private val rate = 250_000L
    private val days = chargeDaysIn(period, ChargeSchedule(LocalDate.of(2026, 1, 1)), asOf)

    private fun row(day: LocalDate, amount: Long) = Allocation("p", day, amount)

    private fun arrears(allocations: List<Allocation>, chargeDays: List<LocalDate> = days, dailyRate: Long = rate) =
        arrearsOf("c1", chargeDays, allocations, dailyRate)

    // --- Matrix rows -------------------------------------------------------------------

    @Test
    fun `verify case 1 leaves 26 open days, 6500000 owed, oldest open 27 Feb, KRITIS`() {
        val entry = arrears(
            listOf(row(LocalDate.of(2026, 2, 25), rate), row(LocalDate.of(2026, 2, 26), rate)),
        )
        assertEquals(26, entry.days)
        assertEquals(6_500_000L, entry.amount)
        assertEquals(LocalDate.of(2026, 2, 27), entry.oldestOpenDay)
        assertEquals(RiskLevel.KRITIS, riskOf(entry.days))
    }

    @Test
    fun `partial payment keeps the day open, 28 days and 6900000 owed`() {
        // verify-fr10.md case 3: Rp 100.000 on 25 Feb leaves that day open.
        val entry = arrears(listOf(row(LocalDate.of(2026, 2, 25), 100_000L)))
        assertEquals(28, entry.days)
        assertEquals(6_900_000L, entry.amount)
        assertEquals(LocalDate.of(2026, 2, 25), entry.oldestOpenDay)
    }

    @Test
    fun `fully paid period has 0 days, 0 owed and LANCAR`() {
        val entry = arrears(days.map { row(it, rate) })
        assertEquals(0, entry.days)
        assertEquals(0L, entry.amount)
        assertNull(entry.oldestOpenDay)
        assertEquals(RiskLevel.LANCAR, riskOf(entry.days))
    }

    @Test
    fun `1000000 owed at 500000 a day is 2 days`() {
        val twoDays = days.take(2)
        val entry = arrears(emptyList(), chargeDays = twoDays, dailyRate = 500_000L)
        assertEquals(2, entry.days)
        assertEquals(1_000_000L, entry.amount)
    }

    @Test
    fun `credit on a paid period is ignored by the null row`() {
        val allocations = days.map { row(it, rate) } + Allocation("p", null, 500_000L)
        val entry = arrears(allocations)
        assertEquals(0, entry.days)
        assertEquals(0L, entry.amount)
    }

    @Test
    fun `row on a day outside the list does not close a listed day`() {
        val entry = arrears(listOf(row(LocalDate.of(2026, 4, 1), rate)))
        assertEquals(28, entry.days)
        assertEquals(7_000_000L, entry.amount)
    }


    @Test
    fun `a zero amount row is rejected by the Allocation constructor`() {
        // `Allocation`'s `init` enforces the amount > 0 rule, so `arrearsOf` never sees
        // a zero or negative row; this pins that the boundary holds at construction.
        assertThrows(InvalidAmountException::class.java) { Allocation("p", LocalDate.of(2026, 2, 25), 0L) }
    }

    @Test
    fun `zero rate is rejected`() {
        assertThrows(InvalidAmountException::class.java) { arrears(emptyList(), dailyRate = 0L) }
    }

    @Test
    fun `negative rate is rejected`() {
        assertThrows(InvalidAmountException::class.java) { arrears(emptyList(), dailyRate = -1L) }
    }

    @Test
    fun `negative day count is rejected by riskOf`() {
        assertThrows(IllegalArgumentException::class.java) { riskOf(-1) }
    }

    @Test
    fun `day count is not derived from amount divided by rate`() {
        // Two partial days of 200.000 each: 6.600.000 ÷ 250.000 = 26.4 would round up to 27,
        // but both days are open, so the count is 28.
        val entry = arrears(listOf(row(LocalDate.of(2026, 2, 25), 200_000L), row(LocalDate.of(2026, 2, 26), 200_000L)))
        assertEquals(28, entry.days)
        assertEquals(6_600_000L, entry.amount)
    }

    // --- Risk boundaries ---------------------------------------------------------------

    @Test
    fun `zero open days is LANCAR`() {
        assertEquals(RiskLevel.LANCAR, riskOf(0))
    }

    @Test
    fun `one and two open days are RINGAN`() {
        assertEquals(RiskLevel.RINGAN, riskOf(1))
        assertEquals(RiskLevel.RINGAN, riskOf(2))
    }

    @Test
    fun `three and six open days are PERHATIAN`() {
        assertEquals(RiskLevel.PERHATIAN, riskOf(3))
        assertEquals(RiskLevel.PERHATIAN, riskOf(6))
    }

    @Test
    fun `seven open days is KRITIS`() {
        assertEquals(RiskLevel.KRITIS, riskOf(7))
        assertEquals(RiskLevel.KRITIS, riskOf(8))
    }

    // --- Ordering ----------------------------------------------------------------------

    @Test
    fun `larger amount comes first`() {
        val small = ArrearsEntry("a", 1, rate, days.first())
        val large = ArrearsEntry("b", 4, 1_000_000L, days.first())
        assertEquals(listOf(large, small), listOf(small, large).inArrearsOrder())
    }

    @Test
    fun `equal amounts put the earlier oldest open day first`() {
        val later = ArrearsEntry("late", 2, 500_000L, LocalDate.of(2026, 3, 1))
        val earlier = ArrearsEntry("early", 2, 500_000L, days.first())
        assertEquals(listOf(earlier, later), listOf(later, earlier).inArrearsOrder())
    }

    @Test
    fun `zero amount entries are excluded from the list`() {
        val owing = ArrearsEntry("owing", 1, rate, days.first())
        val paid = ArrearsEntry("paid", 0, 0L, null)
        assertEquals(listOf(owing), listOf(paid, owing).inArrearsOrder())
    }
}
