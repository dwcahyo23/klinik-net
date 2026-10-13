package net.klinik.rental.domain.validation

import net.klinik.rental.domain.calculate.CutoffPeriod

/**
 * Thrown when a cutoff day falls outside [CutoffPeriod.MIN_CUTOFF_DAY]..[CutoffPeriod.MAX_CUTOFF_DAY].
 *
 * `cutoffDay` is not a constant here — it arrives from the settings table, so an
 * out-of-range value is untrusted input. It fails loudly rather than producing a
 * silent empty or short period: the bound keeps period construction total, because
 * `withDayOfMonth(29)` on a common-year February throws `DateTimeException`.
 *
 * The message is built from the constants rather than restating them, so widening
 * the bound cannot leave this text contradicting the rule the code enforces.
 */
class InvalidCutoffDayException(
    /** The offending value, carried so callers can report or log it. */
    val cutoffDay: Int,
) : IllegalArgumentException(
    "cutoffDay must be between ${CutoffPeriod.MIN_CUTOFF_DAY} and " +
        "${CutoffPeriod.MAX_CUTOFF_DAY}, but was $cutoffDay",
)