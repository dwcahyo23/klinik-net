package net.klinik.rental.domain.calculate

import net.klinik.rental.domain.model.Allocation
import net.klinik.rental.domain.validation.InvalidAmountException
import java.time.LocalDate

/**
 * The arrears of one contract over one period, derived from stored allocation rows.
 *
 * [days] is the count of open charge days, never `ceil(amount ÷ rate)`: a partly paid day is
 * open and counts as one whole day, so the two can disagree (`verify-fr10.md` case 3).
 * [amount] is the Tunggakan in whole rupiah. [oldestOpenDay] is `null` when no day is open.
 */
data class ArrearsEntry(
    val contractId: String,
    val days: Int,
    val amount: Long,
    val oldestOpenDay: LocalDate?,
)

/** Risk level sent to the tenant, derived from the open-day count (FR-12). */
enum class RiskLevel { LANCAR, RINGAN, PERHATIAN, KRITIS }

/**
 * Arrears for one contract over the listed [chargeDays].
 *
 * A charge day is closed only when the allocation rows with that `chargeDate` sum to at least
 * [dailyRate]. Rows with a `null` `chargeDate`, or whose `chargeDate` is not in [chargeDays],
 * do not count here. Tunggakan = max(0, listed days × rate − Σ rows on listed days).
 *
 * [chargeDays] must be the list Story 1.2's `chargeDaysIn` produced; this function does not
 * re-filter by date or by contract end. [allocations] are the rows for the one contract this
 * call is about — [Allocation] does not carry a `contractId` of its own, so the caller
 * groups the rows per contract before calling, and mixing in another contract's rows would
 * silently close days this contract did not pay.
 *
 * @throws InvalidAmountException if [dailyRate] is zero or negative
 */
fun arrearsOf(
    contractId: String,
    chargeDays: List<LocalDate>,
    allocations: List<Allocation>,
    dailyRate: Long,
): ArrearsEntry {
    InvalidAmountException.requireValid(dailyRate)
    val listed = chargeDays.toHashSet()
    val paidPerDay = HashMap<LocalDate, Long>()
    for (row in allocations) {
        val day = row.chargeDate ?: continue
        if (day !in listed) continue
        // A zero or negative row would close a day with no money, so the figure is only as
        // trustworthy as the rows that reach it. [Allocation]'s `init` already rejects a
        // zero or negative amount on construction, and the allocator's own `Σ allocation ==
        // amount` check makes a negative day-row unreachable — this is a boundary that owns
        // the sum, not a filter.
        paidPerDay.merge(day, row.amount) { a, b -> a + b }
    }
    val open = chargeDays.filter { (paidPerDay[it] ?: 0L) < dailyRate }
    val paidOnListed = paidPerDay.values.sum()
    val amount = maxOf(0L, chargeDays.size.toLong() * dailyRate - paidOnListed)
    return ArrearsEntry(
        contractId = contractId,
        days = open.size,
        amount = amount,
        oldestOpenDay = open.minOrNull(),
    )
}

/**
 * Risk level for an open-day count: 0 LANCAR, 1–2 RINGAN, 3–6 PERHATIAN, 7+ KRITIS.
 *
 * @throws IllegalArgumentException if [days] is negative
 */
fun riskOf(days: Int): RiskLevel {
    require(days >= 0) { "open-day count must not be negative, but was $days" }
    return when {
        days == 0 -> RiskLevel.LANCAR
        days <= 2 -> RiskLevel.RINGAN
        days <= 6 -> RiskLevel.PERHATIAN
        else -> RiskLevel.KRITIS
    }
}

/**
 * The Tunggakan list order: entries with a positive amount only, larger amount first, then
 * earlier oldest open day first.
 */
fun List<ArrearsEntry>.inArrearsOrder(): List<ArrearsEntry> =
    filter { it.amount > 0L }
        .sortedWith(
            compareByDescending<ArrearsEntry> { it.amount }
                .thenBy(nullsLast()) { it.oldestOpenDay },
        )
