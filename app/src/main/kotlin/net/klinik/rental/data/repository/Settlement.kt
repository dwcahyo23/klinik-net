package net.klinik.rental.data.repository

import java.time.LocalDate

/**
 * A settlement dated before the contract began — FR-9b's refusal, Story 3.6.
 *
 * The date is structurally inert rather than merely unhelpful: `chargeDaysIn` filters
 * `it >= contractStart` before settlement is ever applied, so such a date closes no day
 * while appearing to close one. Refusing it is the same rule `addServiceException`
 * applies for the identical check, and for the same reason — a marker on the wrong side
 * of the contract's start is a day the owner did not mean to mark.
 *
 * Carries the contract's own start date because that is the day the fix is relative to:
 * the offending date is already in the field the owner typed it into, and "pick a date
 * from here on" is not actionable without knowing where "here" is.
 */
class SettlementBeforeStartException(
    val contractId: String,
    val startDate: LocalDate,
) : IllegalStateException(
    "settling contract $contractId through a date before its start date $startDate",
)

/**
 * What a settlement would remove, and what would remain — Story 3.6.
 *
 * ## Why both halves
 *
 * Because the AC names both: *"jumlah hari yang dihapus, dan nominalnya"* is what is
 * gone, and a confirmation that says only that would leave the owner unable to tell a
 * correct settlement from one that ate the whole period. The remaining figures are what
 * stops that: an action that would leave nothing open and one that would leave twenty
 * days look identical without them.
 *
 * ## Why these are plain figures
 *
 * Same reason `DepositPreview` is: the confirmation renders text and never figures, so
 * every rupiah here is formatted once, by the ViewModel, through the same `formatRupiah`
 * every other surface uses. A money figure assembled in two places is one that can
 * differ in two places.
 */
data class SettlementImpact(
    /** Charge days this settlement closes. */
    val daysClosed: Int,
    /** Rupiah that leaves the arrears figure, in whole units. */
    val amountRemoved: Long,
    /** Charge days still owed afterwards. */
    val daysRemaining: Int,
    /** Rupiah still owed afterwards. */
    val amountRemaining: Long,
)
