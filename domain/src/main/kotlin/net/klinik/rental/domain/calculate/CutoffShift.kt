package net.klinik.rental.domain.calculate

import net.klinik.rental.domain.model.Allocation
import java.time.LocalDate

/**
 * The before/after pair of [ArrearsEntry] for one cutoff change.
 *
 * Both entries are computed from the *same* stored allocation rows: [before] over the day
 * list of the period that was in force, [after] over the day list of the period the new
 * cutoff day produces. Nothing in between mutates the rows.
 */
data class CutoffShiftResult(
    val before: ArrearsEntry,
    val after: ArrearsEntry,
)

/**
 * FR-15a, the owner's decision "digeser, bukan diturunkan ulang": changing `cutoffDay`
 * only shifts which days enter the computation. The stored rows — every row, including
 * `chargeDate = null` credit rows — are re-read, never deleted, rewritten, or re-derived.
 *
 * This function takes the two day lists, not the two cutoff days: the caller (Epic 5's
 * settings screen) already owns `CutoffPeriod.containing` and `chargeDaysIn` and passes
 * the ranges in. Keeping the cutoff-day math out of the function is what makes it
 * provably a range shift — [allocatePayments] is the function that may *produce* the
 * rows, and it is deliberately not called here. A row whose `chargeDate` falls outside
 * the new period's day list is ignored by [arrearsOf] for that period; it is not deleted
 * by this call — re-crediting it onto a day is the allocator's job in the period that
 * contains that day (`verify-fr10.md` case 4 — the Rp 13.000.000 credit row is not
 * counted for a period whose day list lacks its day, and not deleted either).
 */
fun cutoffShift(
    contractId: String,
    oldChargeDays: List<LocalDate>,
    newChargeDays: List<LocalDate>,
    allocations: List<Allocation>,
    dailyRate: Long,
): CutoffShiftResult = CutoffShiftResult(
    before = arrearsOf(contractId, oldChargeDays, allocations, dailyRate),
    after = arrearsOf(contractId, newChargeDays, allocations, dailyRate),
)
