package net.klinik.rental.domain.validation

import java.time.LocalDate

/**
 * Thrown when a contract is ended on a day before it started.
 *
 * ## Why the end is refused rather than corrected
 *
 * `Contract.init` refuses the same pair on construction — *"a date typed into the wrong
 * field"* — and that sentence is the whole reason. An end date before the start is not
 * a near miss to be tidied: it is the owner naming a day the rental did not cover, and
 * the only two ways to "accept" it are worse than refusing.
 *
 * Clamping forward to the start date would end the contract on a day the owner never
 * chose, and ending a contract is what frees the vehicle — so the error would show up
 * as a car that reads `tersedia` on the wrong day. Swapping the two dates would silently
 * rewrite the rental's own beginning. Either way the owner's data now says something
 * they did not say, and every figure derived from it inherits the error.
 *
 * ## Why it carries the start date rather than the end date
 *
 * The fix is "pick a day on or after the contract began", and the owner cannot act on
 * that without knowing which day the contract began — the end date they typed is
 * already sitting in the field they typed it into. This is the same rule
 * [ServiceExceptionBeforeContractStartException] and
 * [DuplicateServiceExceptionDateException] follow: a refusal names the row that
 * actually exists, not the value the caller just submitted.
 *
 * ## Why it is a separate type from [ContractNotOpenException]
 *
 * Because they are different facts with different fixes. `ContractNotOpenException` says
 * *this contract has already ended* — the action makes no sense at all. This one says
 * *this date is impossible* — the action is right and the figure is wrong. Collapsing
 * them would tell the owner to reopen a contract when what they need is a different day.
 *
 * @param contractId the contract whose start date the message names.
 * @param startDate the contract's own stored start date, read back from the `contract`
 *   row rather than echoed from the input.
 */
class ContractEndBeforeStartException(
    val contractId: String,
    val startDate: LocalDate,
) : IllegalStateException(
    "contract $contractId starts on $startDate, so it cannot end before that day",
)