package net.klinik.rental.domain.calculate

import net.klinik.rental.domain.validation.InvalidCutoffDayException
import java.time.LocalDate
import kotlin.streams.asSequence

/**
 * One billing period: the days from a cutoff day up to — but not including — the
 * next month's cutoff day.
 *
 * Every arrears figure downstream answers "which days am I charging for?" by asking
 * this type, so the rule lives here once rather than being re-derived in the
 * allocation engine (Story 1.3) and the arrears calculator (Story 1.4).
 *
 * This replaces the `getCurrentCutoffPeriod()` prototype function, which hardcoded
 * `SIM_TODAY` and had no cutoff validation. Here the date is a parameter.
 */
data class CutoffPeriod(
    /** Inclusive first day: the cutoff day of the month the period opened in. */
    val start: LocalDate,
    /** Inclusive last day: one day before the next month's cutoff day. */
    val end: LocalDate,
) {
    init {
        require(!end.isBefore(start)) { "period end ($end) must not precede its start ($start)" }
    }

    /** How many days the period spans, regardless of schedule or exclusions. */
    val lengthInDays: Int get() = (end.toEpochDay() - start.toEpochDay()).toInt() + 1

    /**
     * Human-readable period, e.g. `25 Feb - 24 Mar 2026`.
     *
     * Month names are the abbreviated Indonesian set reused verbatim from
     * `prototype/shared.js`, so the prototype and this engine cannot drift into
     * showing the tenant two different names for the same period. The start year is
     * included only when the period crosses a year boundary (`25 Des 2025 - 24 Jan 2026`),
     * which is the same rule `fmt.dateShort` + trailing year already implies.
     */
    val label: String
        get() {
            val startYear = if (start.year == end.year) "" else " ${start.year}"
            return "${start.dayOfMonth} ${MONTH_ABBREVIATIONS[start.monthValue - 1]}$startYear" +
                " - ${end.dayOfMonth} ${MONTH_ABBREVIATIONS[end.monthValue - 1]} ${end.year}"
        }

    /** Iterates every day in the period, start and end included. */
    fun days(): Sequence<LocalDate> = start.datesUntil(end.plusDays(1)).asSequence()

    companion object {

        /**
         * Lowest legal cutoff day.
         *
         * The 1–28 bound is not cosmetic: it keeps period construction total.
         * `withDayOfMonth(29)` on a common-year February throws `DateTimeException`,
         * so a cutoff of 29 would make `containing()` fail for most of the year
         * rather than return a period.
         */
        const val MIN_CUTOFF_DAY = 1

        /** Highest legal cutoff day. See [MIN_CUTOFF_DAY] for why 28 and not 29. */
        const val MAX_CUTOFF_DAY = 28

        private val MONTH_ABBREVIATIONS =
            listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

        /**
         * The cutoff period containing [today], given the configured cutoff day.
         *
         * On or after the cutoff day, the period opened in this month; before it, it
         * opened last month. Both branches end one day before the next cutoff, so the
         * cutoff day itself always belongs to the *new* period.
         *
         * @throws InvalidCutoffDayException if [cutoffDay] is outside 1..28
         */
        fun containing(cutoffDay: Int, today: LocalDate): CutoffPeriod {
            requireValidCutoffDay(cutoffDay)
            val startMonth = if (today.dayOfMonth >= cutoffDay) today else today.minusMonths(1)
            val start = startMonth.withDayOfMonth(cutoffDay)
            // plusMonths(1).minusDays(1) is the "day before the next cutoff" rule and
            // stays correct in months shorter than the cutoff day (February), because
            // withDayOfMonth(cutoffDay) on February is always in range at 1..28.
            return CutoffPeriod(start, start.plusMonths(1).minusDays(1))
        }

        /**
         * Validates a cutoff day without building a period — the settings screen
         * needs this while the user is still typing.
         */
        fun requireValidCutoffDay(cutoffDay: Int) {
            if (cutoffDay < MIN_CUTOFF_DAY || cutoffDay > MAX_CUTOFF_DAY) {
                throw InvalidCutoffDayException(cutoffDay)
            }
        }
    }
}