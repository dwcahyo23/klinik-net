package net.klinik.rental.domain.validation

/**
 * Thrown when a plate is already stored, checked *after* normalisation.
 *
 * ## Why this is a distinct type rather than a generic "duplicate key"
 *
 * The prototype checked uniqueness with
 * `JSON.parse(localStorage).some(...)` — a read of the whole store with no index
 * behind it, so two writes that raced could both pass and both land. Here the
 * real guarantee is the database unique index on the normalised `plate`
 * column; this exception is how that index's refusal is named.
 *
 * It is therefore raised by two different layers, and deliberately so:
 *
 *  - [net.klinik.rental.domain.parse.normalizePlate] makes the *check* cheap, so
 *    the form can tell the owner before they leave the sheet
 *  - the unique index makes the *guarantee* real, so two callers racing past the
 *    check cannot both succeed
 *
 * The pre-check is an optimisation and a courtesy; the index is the rule. A
 * screen that skipped the pre-check would still be correct, only slower to fail.
 *
 * @param plate the normalised plate that collided — carried so the Indonesian
 *   message can name the offending plate rather than saying "duplicate".
 */
class DuplicatePlateException(
    val plate: String,
) : IllegalStateException(
    "a vehicle with plate $plate is already stored",
)