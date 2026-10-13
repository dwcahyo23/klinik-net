package net.klinik.rental.domain.model

import net.klinik.rental.domain.validation.InvalidAmountException
import java.time.LocalDate

/**
 * One row of the `ALLOCATION` table: "this much of that payment closed this charge day".
 *
 * This is a stored fact, not a computed result (AD-3). Arrears is
 * `Kewajiban − Σ alokasi` over the current period's charge days, read straight off these
 * rows; nothing re-derives it by replaying payments against a window. That is what makes the
 * Rp 13.000.000 leftover survive the month rollover — credit is the [chargeDate] `null` row
 * itself, not a separate number somebody has to remember to carry forward.
 *
 * [contractId] is deliberately absent. The allocator replays one contract at a time and
 * the caller already holds that contract's id, so storing it again here would create a
 * second source of truth for a fact that cannot legitimately differ.
 *
 * The amount is validated on construction because the schema says `always > 0`: a zero row
 * would leave `Σ allocation == amount` intact while recording that a day was closed for no
 * money at all, and arrears would then be wrong in a way no sum can catch.
 */
data class Allocation(
    /** The [Payment] whose money this row spends. */
    val paymentId: String,
    /**
     * The charge day this money closed, or `null` when the money is not placed on any day
     * yet — AD-3's "dana ada, hari belum ditentukan".
     *
     * The `null` row is regenerated, never accumulated: allocating the same payments over
     * a longer day list rewrites it. See `allocatePayments`' KDoc for why that is safe.
     */
    val chargeDate: LocalDate?,
    /** Rupiah applied by this row. Always greater than zero. */
    val amount: Long,
) {
    init {
        InvalidAmountException.requireValid(amount)
    }
}