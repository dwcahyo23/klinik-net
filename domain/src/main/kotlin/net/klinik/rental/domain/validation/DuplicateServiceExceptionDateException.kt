package net.klinik.rental.domain.validation

import net.klinik.rental.domain.model.ServiceException
import java.time.LocalDate

/**
 * Thrown when a contract already carries an exception for the same date.
 *
 * ## Why this is a distinct type rather than a generic "conflict"
 *
 * "That day is already marked" and "that car is already out" are different facts
 * with different actions: the first is fixed by choosing another day, the second by
 * ending a contract. A single exception carrying a string would collapse the two,
 * and the Indonesian copy would then have to guess which one it is rendering.
 *
 * The guarantee itself is not this exception and not the courtesy pre-check — it is
 * the `UNIQUE(contractId, date)` index on `SERVICE_EXCEPTION`, for the same reason
 * [ContractAlreadyActiveException] is not its own index. This is the named form of
 * that index's refusal.
 *
 * ## Why the date is carried, and why it is the *stored* one
 *
 * [DuplicatePlateException] and [ContractAlreadyActiveException] both carry the
 * value read back from the row that actually holds the key, never the value the
 * caller submitted. Here the two are the same string — the column is the key and the
 * index compares it exactly — but the *path* matters: the repository raises this
 * either from its pre-check or from re-reading the row the index refused, so the
 * date in the message always comes from a row that exists in the list behind the
 * sheet.
 *
 * @param date the marked day, read back from the stored row.
 * @param contractId the contract that already carries it, so the message can name
 *   the contract whose sheet the owner is looking at.
 */
class DuplicateServiceExceptionDateException(
    val date: LocalDate,
    val contractId: String,
) : IllegalStateException(
    "a service exception for ${ServiceException::class.simpleName} contract " +
        "$contractId already exists on $date",
)
