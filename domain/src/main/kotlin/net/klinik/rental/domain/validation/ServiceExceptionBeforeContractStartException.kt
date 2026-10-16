package net.klinik.rental.domain.validation

import java.time.LocalDate

/**
 * Thrown when an exception is marked on a day before its contract started.
 *
 * ## Why the row is refused rather than stored
 *
 * Such a row is **inert**. `chargeDaysIn` filters `it >= contractStart` *before* it
 * ever reaches the exception filter, so a day before the contract began can never
 * stop a charge — the row would sit in the sheet's list claiming a compensation the
 * bill never got, and the owner would eventually trust it.
 *
 * And normalising it instead — swapping in the contract's start date, or clamping
 * forward — would be worse than refusing: it would silently mark a **different** day
 * than the owner chose, and the marked day is a day the renter is not charged.
 * `Contract.init` refused an end before the start on exactly this reasoning ("a date
 * typed into the wrong field"), and the same sentence applies here.
 *
 * ## Why it carries the start date
 *
 * The fix is "pick a day on or after the contract began", and the owner cannot act
 * on that without knowing which day the contract began. So the *start date* travels
 * with the refusal rather than the offending date, which the form is already showing
 * — the same "name a row that actually exists" rule
 * [DuplicateServiceExceptionDateException] and
 * [net.klinik.rental.domain.validation.ContractAlreadyActiveException] follow.
 *
 * @param contractId the contract whose start date the message names.
 * @param startDate the contract's own stored start date, read back from the `contract`
 *   row rather than echoed from the input.
 */
class ServiceExceptionBeforeContractStartException(
    val contractId: String,
    val startDate: LocalDate,
) : IllegalStateException(
    "contract $contractId starts on $startDate, so a service exception before that " +
        "day could never stop a charge",
)
