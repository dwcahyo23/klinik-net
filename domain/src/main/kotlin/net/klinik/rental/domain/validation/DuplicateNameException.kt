package net.klinik.rental.domain.validation

/**
 * Thrown when a renter's name is already stored by a *different* renter.
 *
 * ## Why this is the same shape as [DuplicatePlateException]
 *
 * The renter's name is the key that reconciles a bank mutation against a person
 * (PRD §5), so it carries the identical obligation a plate carries: required,
 * non-blank, and unique in the app. That obligation is enforced the same way —
 * a real `UNIQUE` index on `RENTER.name`, with this exception as the named form
 * of the index's refusal.
 *
 * So the pre-check in `RenterRepository` is a courtesy that lets the owner find
 * out before leaving the sheet, and this is the guarantee that holds when two
 * callers race past it.
 *
 * ## Why the *stored* name is carried
 *
 * After the refusal the plate path re-reads the colliding value rather than
 * echoing SQLite's message, and the same applies here: naming the row that
 * actually exists is what makes the Indonesian copy actionable, and
 * `SQLiteConstraintException`'s message names a constraint, not a person. A
 * refusal the owner cannot act on is not a message.
 *
 * ## Why uniqueness is exact, not case-folded
 *
 * The epic context normalises *plates* — uppercased, spaces squeezed, so `b
 * 1235 tf` and `B 1235 TF` collide — in the same sentence that requires the
 * renter name to be unique, and deliberately does not extend that rule to names.
 * A plate has one canonical form; a person's name does not, and uppercasing a
 * name would degrade every label, list row and WhatsApp message built from it.
 * So `Budi` and `budi` can coexist. If the owner finds that ambiguous, the fix
 * is a normalisation rule applied deliberately — not one inferred here.
 *
 * @param name the name that collided — carried so the Indonesian message can
 *   name the stored person rather than saying "duplicate".
 */
class DuplicateNameException(
    val name: String,
) : IllegalStateException(
    "a renter named $name is already stored",
)