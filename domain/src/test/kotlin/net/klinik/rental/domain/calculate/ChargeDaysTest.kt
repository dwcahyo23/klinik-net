package net.klinik.rental.domain.calculate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Charge-day half of the Story 1.2 matrix.
 *
 * The four exclusions are tested one at a time. A single combined test would still
 * pass if any two rules were swapped, which is exactly the class of bug this story
 * exists to remove from `prototype/data.js`.
 */
class ChargeDaysTest {

    private val period = CutoffPeriod(
        start = LocalDate.of(2026, 2, 25),
        end = LocalDate.of(2026, 3, 24),
    )
    private val periodEnd = LocalDate.of(2026, 3, 24)
    private val longAgo = LocalDate.of(2020, 1, 1)

    @Test
    fun `seven day schedule charges every day of the period`() {
        val days = chargeDaysIn(period, ChargeSchedule(contractStart = longAgo), asOf = periodEnd)

        assertEquals(28, days.size)
        assertEquals(period.start, days.first())
        assertEquals(period.end, days.last())
    }

    @Test
    fun `six day schedule excludes exactly the Sundays`() {
        val days = chargeDaysIn(
            period,
            ChargeSchedule(contractStart = longAgo, restDay = DayOfWeek.SUNDAY),
            asOf = periodEnd,
        )

        assertEquals(24, days.size)
        // Mar 1, 8, 15 and 22 are the four Sundays inside 25 Feb - 24 Mar.
        listOf(1, 8, 15, 22).forEach { dayOfMarch ->
            assertFalse(
                "2026-03-$dayOfMarch is a Sunday and must not be charged",
                LocalDate.of(2026, 3, dayOfMarch) in days,
            )
        }
    }

    @Test
    fun `six day schedule keeps every non-Sunday day`() {
        val days = chargeDaysIn(
            period,
            ChargeSchedule(contractStart = longAgo, restDay = DayOfWeek.SUNDAY),
            asOf = periodEnd,
        )

        val skipped = period.days().toSet() - days.toSet()

        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 8),
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 3, 22),
            ),
            skipped.sorted(),
        )
    }

    @Test
    fun `a rest day other than Sunday still excludes only that weekday`() {
        val days = chargeDaysIn(
            period,
            ChargeSchedule(contractStart = longAgo, restDay = DayOfWeek.WEDNESDAY),
            asOf = periodEnd,
        )

        // 25 Feb is a Wednesday; the period spans exactly four of them.
        assertEquals(24, days.size)
        assertFalse(LocalDate.of(2026, 2, 25) in days)
        listOf(4, 11, 18).forEach { dayOfMarch ->
            assertFalse(
                "2026-03-$dayOfMarch is a Wednesday and must not be charged",
                LocalDate.of(2026, 3, dayOfMarch) in days,
            )
        }
    }

    @Test
    fun `two exclusions on the same day decrement the count once`() {
        // 2026-03-01 is both a Sunday and a service exception. A chained filter
        // counts correctly, but the prototype's single-pass loop also has to, and
        // this is the one intersection where the two implementations could differ.
        val sunday = LocalDate.of(2026, 3, 1)
        val days = chargeDaysIn(
            period,
            ChargeSchedule(
                contractStart = longAgo,
                restDay = DayOfWeek.SUNDAY,
                serviceExceptions = setOf(sunday),
            ),
            asOf = periodEnd,
        )

        assertEquals(24, days.size)
        assertFalse(sunday in days)
    }

    @Test
    fun `a service exception on the contract start day decrements the count once`() {
        val start = LocalDate.of(2026, 2, 27)

        val days = chargeDaysIn(
            period,
            ChargeSchedule(contractStart = start, serviceExceptions = setOf(start)),
            asOf = periodEnd,
        )

        assertEquals(25, days.size)
        assertFalse(start in days)
    }

    @Test
    fun `a contract starting late drops the days before it`() {
        val days = chargeDaysIn(
            period,
            ChargeSchedule(contractStart = LocalDate.of(2026, 2, 27)),
            asOf = periodEnd,
        )

        assertEquals(26, days.size)
        assertEquals(LocalDate.of(2026, 2, 27), days.first())
        assertFalse(LocalDate.of(2026, 2, 25) in days)
        assertFalse(LocalDate.of(2026, 2, 26) in days)
    }

    @Test
    fun `the contract start day itself is charged`() {
        val start = LocalDate.of(2026, 2, 27)

        val days = chargeDaysIn(period, ChargeSchedule(contractStart = start), asOf = periodEnd)

        assertTrue("the start day is on or after contractStart, so it charges", start in days)
    }

    @Test
    fun `a contract starting after the period charges nothing`() {
        val days = chargeDaysIn(
            period,
            ChargeSchedule(contractStart = LocalDate.of(2026, 4, 1)),
            asOf = periodEnd,
        )

        assertTrue(days.isEmpty())
    }

    @Test
    fun `a service exception date is not charged`() {
        val exception = LocalDate.of(2026, 3, 5)

        val days = chargeDaysIn(
            period,
            ChargeSchedule(contractStart = longAgo, serviceExceptions = setOf(exception)),
            asOf = periodEnd,
        )

        assertFalse(exception in days)
        assertEquals(27, days.size)
    }

    @Test
    fun `several service exceptions are each excluded`() {
        val exceptions = setOf(
            LocalDate.of(2026, 3, 5),
            LocalDate.of(2026, 3, 12),
            LocalDate.of(2026, 2, 28),
        )

        val days = chargeDaysIn(
            period,
            ChargeSchedule(contractStart = longAgo, serviceExceptions = exceptions),
            asOf = periodEnd,
        )

        assertEquals(25, days.size)
        assertTrue(days.none { it in exceptions })
    }

    @Test
    fun `a service exception outside the period changes nothing`() {
        val days = chargeDaysIn(
            period,
            ChargeSchedule(contractStart = longAgo, serviceExceptions = setOf(LocalDate.of(2026, 5, 1))),
            asOf = periodEnd,
        )

        assertEquals(28, days.size)
    }

    @Test
    fun `days after asOf are not charged`() {
        val nextPeriod = CutoffPeriod.containing(cutoffDay = 25, today = LocalDate.of(2026, 3, 25))

        val days = chargeDaysIn(nextPeriod, ChargeSchedule(contractStart = longAgo), asOf = LocalDate.of(2026, 3, 25))

        assertEquals(1, days.size)
        assertEquals(LocalDate.of(2026, 3, 25), days.single())
    }

    @Test
    fun `a day past asOf is excluded even when nothing else filters it`() {
        // Pinned deliberately on a day that is not a rest day and carries no service
        // exception, so the asOf clause is the only thing that can remove it. The
        // former test used a period whose every day was already past asOf, which made
        // its "excludes the future" assertion vacuously true.
        val days = chargeDaysIn(period, ChargeSchedule(contractStart = longAgo), asOf = LocalDate.of(2026, 3, 5))

        assertEquals(9, days.size)
        assertEquals(LocalDate.of(2026, 3, 5), days.last())
        assertFalse(LocalDate.of(2026, 3, 6) in days)
        assertFalse(LocalDate.of(2026, 3, 30) in days)
    }

    @Test
    fun `an asOf before the period start charges nothing`() {
        val days = chargeDaysIn(period, ChargeSchedule(contractStart = longAgo), asOf = LocalDate.of(2026, 2, 24))

        assertTrue(days.isEmpty())
    }

    @Test
    fun `an asOf inside the period caps the charge days there`() {
        val days = chargeDaysIn(period, ChargeSchedule(contractStart = longAgo), asOf = LocalDate.of(2026, 3, 5))

        assertEquals(9, days.size)
        assertEquals(LocalDate.of(2026, 3, 5), days.last())
    }

    @Test
    fun `an asOf after the period end charges the whole period`() {
        val days = chargeDaysIn(period, ChargeSchedule(contractStart = longAgo), asOf = LocalDate.of(2026, 12, 31))

        assertEquals(28, days.size)
    }

    @Test
    fun `all four exclusions compose`() {
        // asOf is deliberately a Tuesday, not a Sunday: with 2026-03-15 as the cap the
        // final assertion would already follow from the rest-day rule, leaving the cap
        // unexercised.
        val days = chargeDaysIn(
            period,
            ChargeSchedule(
                contractStart = LocalDate.of(2026, 2, 27),
                restDay = DayOfWeek.SUNDAY,
                serviceExceptions = setOf(LocalDate.of(2026, 3, 10)),
            ),
            asOf = LocalDate.of(2026, 3, 17),
        )

        // 19 days from 27 Feb to 17 Mar, less 3 Sundays (1, 8, 15 Mar) and the
        // 10 Mar exception = 15.
        assertEquals(15, days.size)
        assertEquals(LocalDate.of(2026, 2, 27), days.first())
        assertEquals(LocalDate.of(2026, 3, 17), days.last())
        assertFalse(LocalDate.of(2026, 3, 1) in days)
        assertFalse(LocalDate.of(2026, 3, 8) in days)
        assertFalse(LocalDate.of(2026, 3, 10) in days)
        assertTrue("nothing after the 17 Mar cap survives", days.none { it > LocalDate.of(2026, 3, 17) })
    }

    @Test
    fun `charge days are returned in ascending order without gaps`() {
        val days = chargeDaysIn(period, ChargeSchedule(contractStart = longAgo), asOf = periodEnd)

        assertEquals(days.sorted(), days)
        assertEquals(days.distinct(), days)
    }

    @Test
    fun `a Sunday is not a charge day, and the next day is`() {
        // FR-10 case 2 in part: 1 Mar 2026 is a Sunday, so it is not charged. The money is
        // still credited, and Story 1.3 allocates it to the *oldest open* charge day rather
        // than to Mon 2 Mar — allocation is chronological, and this fixture leaves 25 Feb
        // unpaid ahead of it. That story owns the rule; this one only decides which days are
        // chargeable and must not encode the fix itself.
        val days = chargeDaysIn(
            period,
            ChargeSchedule(contractStart = longAgo, restDay = DayOfWeek.SUNDAY),
            asOf = periodEnd,
        )

        assertFalse(LocalDate.of(2026, 3, 1) in days)
        assertTrue(LocalDate.of(2026, 3, 2) in days)
    }
}