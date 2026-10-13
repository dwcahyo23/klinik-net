package net.klinik.rental.domain.calculate

import net.klinik.rental.domain.validation.InvalidCutoffDayException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Period half of the Story 1.2 matrix.
 *
 * Each row is its own test on purpose: `Math.min`-style edge bugs hide in month
 * arithmetic, and a combined test passes with two branches swapped.
 */
class CutoffPeriodTest {

    @Test
    fun `period in progress runs from the previous cutoff to today`() {
        val period = CutoffPeriod.containing(cutoffDay = 25, today = LocalDate.of(2026, 3, 24))

        assertEquals(LocalDate.of(2026, 2, 25), period.start)
        assertEquals(LocalDate.of(2026, 3, 24), period.end)
        assertEquals("25 Feb - 24 Mar 2026", period.label)
    }

    @Test
    fun `period rolls over on the cutoff day itself`() {
        // The cutoff day belongs to the new period, not the one that ends before it.
        // An off-by-one here silently charges every customer one day early.
        val period = CutoffPeriod.containing(cutoffDay = 25, today = LocalDate.of(2026, 3, 25))

        assertEquals(LocalDate.of(2026, 3, 25), period.start)
        assertEquals(LocalDate.of(2026, 4, 24), period.end)
    }

    @Test
    fun `cutoff day 28 rolls over on 28 February without an invalid date`() {
        // The spec's matrix asks for a cutoff of 28 to survive February, where a
        // cutoff of 29 would need 29 Feb and silently clamp in a common year.
        // 2028 is a leap year, so this also covers the year where 29 Feb exists —
        // cutoff 28 must not pick it up either way.
        val period = CutoffPeriod.containing(cutoffDay = 28, today = LocalDate.of(2028, 2, 28))

        assertEquals(LocalDate.of(2028, 2, 28), period.start)
        assertEquals(LocalDate.of(2028, 3, 27), period.end)
        // 29, not 28: this period contains 29 Feb because 2028 is a leap year. A
        // common year gives 28 for the same inputs, which is why the cutoff is
        // capped at 28 — the period is never built from a day February lacks.
        assertEquals(29, period.lengthInDays)
    }

    @Test
    fun `cutoff day 28 spans the whole of February the day before rollover`() {
        // The period the spec's leap-February row names, reached from the last day
        // it actually contains: 2026-01-28 -> 2026-02-27, 31 charge days.
        val period = CutoffPeriod.containing(cutoffDay = 28, today = LocalDate.of(2026, 2, 27))

        assertEquals(LocalDate.of(2026, 1, 28), period.start)
        assertEquals(LocalDate.of(2026, 2, 27), period.end)
        assertEquals(31, period.lengthInDays)
    }

    @Test
    fun `cutoff day 28 after February 28 rolls into March`() {
        val period = CutoffPeriod.containing(cutoffDay = 28, today = LocalDate.of(2028, 3, 28))

        assertEquals(LocalDate.of(2028, 3, 28), period.start)
        assertEquals(LocalDate.of(2028, 4, 27), period.end)
    }

    @Test
    fun `period across a year boundary labels both years`() {
        val period = CutoffPeriod.containing(cutoffDay = 25, today = LocalDate.of(2025, 12, 25))

        assertEquals(LocalDate.of(2025, 12, 25), period.start)
        assertEquals(LocalDate.of(2026, 1, 24), period.end)
        assertEquals("25 Des 2025 - 24 Jan 2026", period.label)
    }

    @Test
    fun `January before the cutoff belongs to the previous December`() {
        val period = CutoffPeriod.containing(cutoffDay = 25, today = LocalDate.of(2026, 1, 24))

        assertEquals(LocalDate.of(2025, 12, 25), period.start)
        assertEquals(LocalDate.of(2026, 1, 24), period.end)
        assertEquals("25 Des 2025 - 24 Jan 2026", period.label)
    }

    @Test
    fun `cutoff day 1 keeps every day of the shortest month`() {
        val period = CutoffPeriod.containing(cutoffDay = 1, today = LocalDate.of(2026, 3, 1))

        assertEquals(LocalDate.of(2026, 3, 1), period.start)
        assertEquals(LocalDate.of(2026, 3, 31), period.end)
        assertEquals(31, period.lengthInDays)
    }

    @Test
    fun `days enumerates the whole period inclusive of both ends`() {
        val period = CutoffPeriod.containing(cutoffDay = 25, today = LocalDate.of(2026, 3, 24))

        val days = period.days().toList()

        assertEquals(28, days.size)
        assertEquals(period.start, days.first())
        assertEquals(period.end, days.last())
    }

    @Test
    fun `cutoff day 0 is rejected`() {
        assertThrows(InvalidCutoffDayException::class.java) {
            CutoffPeriod.containing(cutoffDay = 0, today = LocalDate.of(2026, 3, 24))
        }
    }

    @Test
    fun `cutoff day 29 is rejected`() {
        // 29 would skip 28 February entirely in a non-leap year.
        assertThrows(InvalidCutoffDayException::class.java) {
            CutoffPeriod.containing(cutoffDay = 29, today = LocalDate.of(2026, 3, 24))
        }
    }

    @Test
    fun `cutoff day 31 is rejected`() {
        assertThrows(InvalidCutoffDayException::class.java) {
            CutoffPeriod.containing(cutoffDay = 31, today = LocalDate.of(2026, 3, 24))
        }
    }

    @Test
    fun `negative cutoff day is rejected`() {
        assertThrows(InvalidCutoffDayException::class.java) {
            CutoffPeriod.containing(cutoffDay = -5, today = LocalDate.of(2026, 3, 24))
        }
    }

    @Test
    fun `the rejected cutoff day is carried on the exception`() {
        val thrown = assertThrows(InvalidCutoffDayException::class.java) {
            CutoffPeriod.containing(cutoffDay = 45, today = LocalDate.of(2026, 3, 24))
        }

        assertEquals(45, thrown.cutoffDay)
    }

    @Test
    fun `cutoff day validation is available without building a period`() {
        // The settings screen needs this while the user is still typing a value.
        CutoffPeriod.requireValidCutoffDay(1)
        CutoffPeriod.requireValidCutoffDay(28)
        assertThrows(InvalidCutoffDayException::class.java) { CutoffPeriod.requireValidCutoffDay(0) }
        assertThrows(InvalidCutoffDayException::class.java) { CutoffPeriod.requireValidCutoffDay(29) }
    }

    @Test
    fun `the boundary cutoff days are accepted`() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            CutoffPeriod.containing(cutoffDay = 28, today = LocalDate.of(2026, 3, 1)).start,
        )
        assertEquals(
            LocalDate.of(2026, 3, 1),
            CutoffPeriod.containing(cutoffDay = 1, today = LocalDate.of(2026, 3, 15)).start,
        )
    }

    @Test
    fun `every month abbreviation renders the prototype's spelling`() {
        // The list is copied from prototype/shared.js so the two cannot drift. That
        // guarantee is worthless unless the drift-prone entries are asserted: Mei, Agu
        // and Okt are the ones a maintainer would "correct" to May, Aug or Oct.
        val expected = listOf(
            "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
            "Jul", "Agu", "Sep", "Okt", "Nov", "Des",
        )

        expected.forEachIndexed { index, abbreviation ->
            val month = index + 1
            // cutoff 28 on the 28th makes the period open in the month under test, so
            // the abbreviation under test is the one on the left of the label.
            val period = CutoffPeriod.containing(cutoffDay = 28, today = LocalDate.of(2026, month, 28))

            assertTrue(
                "month $month must render as $abbreviation, got '${period.label}'",
                period.label.startsWith("28 $abbreviation "),
            )
        }
    }

    @Test
    fun `no cutoff day produces a period shorter than 28 days`() {
        // The stated reason for the 1..28 bound: construction stays total, and no
        // period skips a day. Also pins the "day before the next cutoff" half of the
        // rule, which is where a plusMonths(1) regression would hide.
        for (cutoffDay in 1..28) {
            for (month in 1..12) {
                val onOrAfterCutoff = CutoffPeriod.containing(cutoffDay, LocalDate.of(2026, month, 1).withDayOfMonth(28))
                val beforeCutoff = CutoffPeriod.containing(cutoffDay, LocalDate.of(2026, month, 1).withDayOfMonth(1))

                assertTrue(
                    "cutoffDay=$cutoffDay month=$month must span at least 28 days",
                    onOrAfterCutoff.lengthInDays >= 28,
                )
                assertTrue(
                    "cutoffDay=$cutoffDay month=$month: lengthInDays must agree with days()",
                    onOrAfterCutoff.lengthInDays == onOrAfterCutoff.days().count(),
                )
                assertEquals("period start must be on the cutoff day", cutoffDay, onOrAfterCutoff.start.dayOfMonth)
                // The "day before the next cutoff" half of the rule. Stated as the month
                // arithmetic it actually is, because for cutoff 1 the end is the last day
                // of the start month rather than a day numbered cutoffDay - 1.
                assertEquals(
                    "period must end one day before the next cutoff",
                    onOrAfterCutoff.start.plusMonths(1).minusDays(1),
                    onOrAfterCutoff.end,
                )
                assertEquals("before-cutoff branch must also start on the cutoff day", cutoffDay, beforeCutoff.start.dayOfMonth)
            }
        }
    }

    @Test
    fun `a period whose end precedes its start is rejected`() {
        // The constructor is public and tests build periods by hand, so an inverted
        // pair would otherwise yield an empty day list and a negative length silently.
        assertThrows(IllegalArgumentException::class.java) {
            CutoffPeriod(start = LocalDate.of(2026, 3, 25), end = LocalDate.of(2026, 2, 25))
        }
    }
}