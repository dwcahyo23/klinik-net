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
 * A settlement dated **earlier** than one the contract already carries.
 *
 * The dangerous one, and the reason it carries *both* dates rather than just the stored
 * one: unlike a before-start date, which is inert, a backwards settlement **actively
 * reopens** every day between the two — turning days the owner declared paid back into
 * arrears they believed were gone. FR-9b gives no undo and no audit trail, so the recovery
 * is editing a row in a database file.
 *
 * Both dates are on the exception so the message can say what the contract already covers
 * and what was asked for: the owner cannot tell which of the two dates is the mistake
 * without being shown both, and the field they typed the rejected date into still holds it.
 *
 * The UI hides the settlement verb once a contract carries a marker, so this is not
 * reachable through the sheet. It is here because this layer owns the rule, and a second
 * caller would never consult a composable.
 */
class SettlementAlreadySettledException(
    val contractId: String,
    /** What the contract is already settled through. */
    val settledThroughDate: LocalDate,
    /** What this write tried to settle through, and was refused. */
    val attemptedDate: LocalDate,
) : IllegalStateException(
    "contract $contractId is already settled through $settledThroughDate, " +
        "so it cannot be settled back to $attemptedDate",
)

/**
 * A settlement dated **after the day the caller says it is**.
 *
 * ## Why a future settlement is refused rather than stored
 *
 * The marker is not inert. `settledDays` filters every charge day `<= settledThroughDate`,
 * so settling through 24 October on the 17th does not merely write a date — it closes
 * **today's** arrears too, and the rupiah behind them disappears from Tunggakan without
 * anyone having been paid. The owner reads "lunas sampai 24 Okt" and believes the whole
 * range is settled elsewhere; in fact three days of it are still owed, and nothing on the
 * screen says so.
 *
 * FR-9b's stated purpose is the opposite of that: marking obligations *"sudah tercatat di
 * tempat lain"*, which cannot be true of days that have not happened. So the write is
 * refused rather than clamped — clamping forward would close days the owner never named,
 * which is the same failure `endContract` refuses to commit for an end date.
 *
 * ## Why it carries the day it compared against
 *
 * For the reason [SettlementBeforeStartException] gives, from the other side: a message that
 * only said "the date cannot be in the future" leaves the owner to work out which day the
 * app thinks it is — and a wrong device clock is exactly the case where they most need to be
 * told. The message says only that the date is after [asOf]: whether [asOf] *is* today is the
 * caller's knowledge, not this type's, so the sentence stays true for any value it is given.
 *
 * ## Why a past date is still accepted
 *
 * Only strictly-after-[asOf] is refused. A day marked paid outside the app may be last week,
 * last year, or before this app existed — that is the entire use case, and refusing it would
 * make the owner's own history unrecordable.
 *
 * @param contractId the contract the settlement was attempted on.
 * @param asOf the day the check compared against, supplied by the caller rather than read
 *   from a clock here, so a test pins it by passing it.
 */
class SettlementInFutureException(
    val contractId: String,
    val asOf: LocalDate,
) : IllegalStateException(
    "contract $contractId cannot be settled through a date after $asOf",
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

/**
 * One contract's arrears as a **complete** entry — Story 4.1's list row source.
 *
 * ## Why this carries the contract's own fields beside the figures
 *
 * Because the epic context requires that a figure leaving the app be read from one snapshot.
 * If the list screen read the amount, then the day count, then the renter's name as three
 * separate things, a write landing between two of them could produce a row whose amount and
 * day count describe one moment and whose name describes another — and that row is what the
 * owner reads before contacting a tenant.
 *
 * So one call returns everything the row draws. The names themselves are still joined by the
 * ViewModel from the renter and vehicle flows, exactly as `KontrakViewModel` joins its list:
 * those two are **static facts** about a person and a car, not a moving figure, so a stale
 * name cannot make a wrong number look right.
 *
 * ## Why [isEnded] and not the end date
 *
 * Because the row marks a finished rental as finished; it does not display when the rental
 * ended — the contract screen already shows that, and this list's job is answering "who do I
 * contact today". Carrying the date here would invite a second screen to format it.
 *
 * @property days the open charge days, from stored allocation rows. **Never** `amount ÷
 *   rate`: a partly-paid day is open and counts as one whole day, so the two disagree and only
 *   the allocation-derived figure is right.
 */
data class ContractArrears(
    val contractId: String,
    /** Rupiah owed, in whole units. */
    val amount: Long,
    /** Open charge days. See the KDoc on why this is not derived from [amount]. */
    val days: Int,
    /** The oldest still-open charge day, or `null` when no day is open. */
    val oldestOpenDay: LocalDate?,
    val renterId: String,
    val vehicleId: String,
    /** Whether the rental has ended. An ended contract stays in the list until it is settled. */
    val isEnded: Boolean,
)
