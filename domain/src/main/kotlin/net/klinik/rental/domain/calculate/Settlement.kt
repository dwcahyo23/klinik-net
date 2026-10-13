package net.klinik.rental.domain.calculate

import java.time.LocalDate

/**
 * FR-9b: the days that remain open after settlement.
 *
 * `settledThroughDate` is the "tandai lunas sampai tanggal" marker — a field on the
 * contract, not a fake Setoran. A settled day is a closed day with **no allocation row
 * written for it**: settlement narrows the day list, it never produces rows. An empty
 * result must therefore read, in [arrearsOf], as a fully paid period — 0 days, 0
 * rupiah, LANCAR — exactly the way a period whose days all carry full rows does.
 *
 * The rule is "at or before": a charge day equal to [settledThroughDate] is closed, so
 * the result keeps only days strictly after it. A settlement date before the period's
 * first day closes nothing. A `null` date means no settlement at all — the input list
 * is returned as-is, not a copy, so the common no-settlement case allocates nothing and
 * the list's identity stays visible at the call site. Any *non-null* date always returns
 * a fresh list via `filter`, even when it closes nothing, so the result is only
 * identity-equal to the input in the `null` case.
 *
 * This function is pure list narrowing: it writes nothing and reads nothing but its
 * arguments. `endDate` plays no role here (Story 2.7).
 */
fun settledDays(
    chargeDays: List<LocalDate>,
    settledThroughDate: LocalDate?,
): List<LocalDate> {
    if (settledThroughDate == null) return chargeDays
    return chargeDays.filter { it.isAfter(settledThroughDate) }
}
