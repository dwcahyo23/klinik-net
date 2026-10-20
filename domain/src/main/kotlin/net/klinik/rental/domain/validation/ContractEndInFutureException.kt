package net.klinik.rental.domain.validation

import java.time.LocalDate

/**
 * Thrown when a contract is ended on a day that has not happened yet.
 *
 * ## Why a future end is refused rather than stored
 *
 * `Contract.isOpen` is `endDate == null`, and every "is this car rented?" query asks
 * `endDate IS NULL` — so writing *any* end date ends the contract on the spot, including
 * one six weeks out. The vehicle would read `tersedia` today, the renter's slot would free
 * today, and the charge days would stop today, for a rental that is still running. That is
 * the same failure class as clamping a date the owner chose: the app would end a rental on
 * a day nobody agreed to.
 *
 * Storing the date and keeping the car occupied until it arrives would mean `isOpen` has to
 * take a date, which rewrites both active `UNIQUE` indexes, every `endDate IS NULL` query
 * and the backup schema — for a case the owner can express by ending the contract on the
 * day it actually ends. So the write is refused instead.
 *
 * ## Why it carries the day it compared against
 *
 * A message that says only "the date cannot be in the future" leaves the owner to work out
 * which day the app thinks it is. [asOf] is the day the check used, and naming it makes the
 * refusal actionable without the owner guessing — the same rule
 * [ContractEndBeforeStartException] follows by naming the start date. The message says only
 * that the date is after [asOf]: whether [asOf] *is* today is the caller's knowledge, not
 * this type's, so the sentence is written to stay true for any value it is given.
 *
 * ## Why a past date is still accepted
 *
 * Only strictly-after-[asOf] is refused. A contract that ended before this app existed is
 * real, and refusing a past date would make the owner's own history unrecordable — the
 * same reasoning [net.klinik.rental.domain.model.Contract] gives for a past start date.
 *
 * @param contractId the contract the end was attempted on.
 * @param asOf the day the check compared against, supplied by the caller rather than read
 *   from a clock here: `domain/` has no clock, and a test pins the day by passing it.
 */
class ContractEndInFutureException(
    val contractId: String,
    val asOf: LocalDate,
) : IllegalStateException(
    "contract $contractId cannot end after $asOf",
)
