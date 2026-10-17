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
    /**
     * The day the contract ended, or `null` while it is still running.
     *
     * **The end date is itself charged** — days *after* it are not. The owner hands the
     * car back on this day, so this is the last day the rental covered, and the
     * distinction is money: charging it when it was returned early takes a day the
     * renter used neither their car nor a charge for.
     *
     * `null` means "still running" and is the default, so every schedule built before
     * a contract could end keeps its meaning untouched. That default is load-bearing
     * rather than convenient — an ended contract and a running one produce different
     * bills, and the twenty-odd `ChargeDaysTest` cases that construct this without an
     * end date are what prove the `null` did not quietly become "ends today".
     */
    val contractEnd: LocalDate? = null,
)

/**
 * The charge days inside [period] for [schedule], capped at [asOf].
 *
 * Five exclusions, ported from the prototype's day loop (`prototype/data.js:338-358`):
 * days before `contractStart`, the contract's rest day, service-exception dates, days
 * after `contractEnd`, and days after `asOf`. The prototype's
 * `paidOnDate`/`missedDaysList` branch is *not*
 * ported — that is the FR-10 case-2 defect, where an overpayment on one day hid
 * arrears on another; allocation is Story 1.3's job and works from stored facts.
 *
 * Returns the days rather than a count because the list costs nothing extra over a
 * count and is exactly what Story 1.3's allocator iterates. The result is ascending
 * and gap-free, and that is a precondition rather than a convenience:
 * `allocatePayments` walks the list once filling the oldest open day first, and rejects a
 * list that is not strictly ascending — a repeated or out-of-order day would let one
 * payment write two rows for the same `(paymentId, chargeDate)`, the exact duplicate
 * `UNIQUE(paymentId, chargeDate)` exists to forbid.
 *
 * There is deliberately **no "nearest charge day" rule** anywhere in the allocator. Money
 * arriving on a rest day, on a service exception, or before the contract started still
 * closes the *oldest open* day, because allocation is chronological: `verify-fr10.md`
 * case 1 pins Rp 500.000 received 20 Mar onto 25 + 26 Feb, not onto 19 + 20 Mar. An earlier
 * draft of this KDoc claimed the allocator used `filter { it <= paymentDate }.maxOrNull()`
 * and justified the sort order by it; that sentence was wrong, and it is the one a
 * maintainer would read before "fixing" the allocator to match.
 *
 * Note that `contractEnd` is honoured but `asOf` is still applied **after** it: a period
 * computed for an ended contract is capped at whichever comes first, and neither filter
 * replaces the other. `asOf` answers "how far have we got"; `contractEnd` answers "how long
 * did this rental last", and a bill needs both.
 */
fun chargeDaysIn(period: CutoffPeriod, schedule: ChargeSchedule, asOf: LocalDate): List<LocalDate> =
    period.days()
        .filter { it >= schedule.contractStart }
        .filter { it.dayOfWeek != schedule.restDay }
        .filter { it !in schedule.serviceExceptions }
        .filter { schedule.contractEnd == null || it <= schedule.contractEnd }
        .filter { it <= asOf }
        .toList()