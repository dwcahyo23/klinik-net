package net.klinik.rental.domain.calculate

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The inputs that decide whether a given day is charged, other than the period
 * itself.
 *
 * Deliberately *not* a `WorkSchedule` enum: the two schedules the acceptance
 * criteria name are fully expressed by [restDay] — `null` means every day is a
 * working day, `DayOfWeek.SUNDAY` means a six-day week. An enum would carry no
 * behaviour the nullable field does not already carry. If a five-day schedule ever
 * arrives, the story that needs it adds it.
 */
data class ChargeSchedule(
    /** The contract's start date. Days before it are never charged. */
    val contractStart: LocalDate,
    /** The one day off per week, or `null` for a seven-day contract. */
    val restDay: DayOfWeek? = null,
    /** Dates on which no charge applies, e.g. while the car is in the workshop. */
    val serviceExceptions: Set<LocalDate> = emptySet(),
)

/**
 * The charge days inside [period] for [schedule], capped at [asOf].
 *
 * Four exclusions, ported from the prototype's day loop (`prototype/data.js:338-358`):
 * days before `contractStart`, the contract's rest day, service-exception dates, and
 * days after `asOf`. The prototype's `paidOnDate`/`missedDaysList` branch is *not*
 * ported — that is the FR-10 case-2 defect, where an overpayment on one day hid
 * arrears on another; allocation is Story 1.3's job and works from stored facts.
 *
 * Returns the days rather than a count because the list costs nothing extra over a
 * count and is exactly what Story 1.3's allocator iterates. The result is ascending
 * and gap-free, which is what the allocator's "nearest charge day" rule depends on
 * (`filter { it <= paymentDate }.maxOrNull()` is only correct on a sorted list).
 *
 * Note the deliberate omission: `contract.endDate` is *not* honoured. No acceptance
 * criterion covers a contract ending mid-period, and the correct rule has at least
 * two defensible readings. Story 2.7 (`mengakhiri kontrak sewa`) settles it; charge
 * days are capped at [asOf] only.
 */
fun chargeDaysIn(period: CutoffPeriod, schedule: ChargeSchedule, asOf: LocalDate): List<LocalDate> =
    period.days()
        .filter { it >= schedule.contractStart }
        .filter { it.dayOfWeek != schedule.restDay }
        .filter { it !in schedule.serviceExceptions }
        .filter { it <= asOf }
        .toList()