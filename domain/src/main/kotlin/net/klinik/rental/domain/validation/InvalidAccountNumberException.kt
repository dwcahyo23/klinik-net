package net.klinik.rental.domain.validation

/**
 * Thrown when an account number is not in a storable format.
 *
 * ## Why this is separate from [BlankAccountFieldException]
 *
 * A blank field and a malformed one are different *fixes*, and that is the whole reason
 * they are different types. A blank number asks the owner to type something; a malformed
 * one asks them to type something **different** — and the second message has to say which
 * format is accepted, because "wajib diisi" on a field that visibly has `12ab#` in it reads
 * as the app being broken.
 *
 * Collapsing them into one refusal with a nullable reason would put the distinction in a
 * string the caller has to branch on, which is the shape this project's typed exceptions
 * exist to avoid.
 *
 * ## What it carries
 *
 * The **raw** value the owner submitted, not the normalised one — because there is no
 * normalised form; that is what failed. The message names it so a log line says what was
 * rejected, and `RStrings` supplies the owner-facing sentence.
 *
 * ## What this refusal must never grow into
 *
 * Its KDoc exists to stop one specific change: adding a bank lookup or a checksum here. The
 * acceptance criterion is that format is validated *"sebatas format saja"* and that nothing
 * changes when the bank's records disagree with the owner's — so a refusal raised because
 * some external source did not recognise the number would break the rule this exception
 * exists to enforce. See [net.klinik.rental.domain.parse.normalizeAccountNumber].
 *
 * ## Why it is not an `IllegalArgumentException`
 *
 * Unlike the blank-field family, this one is *not* a "bad argument" in the language's sense
 * — the value is a perfectly good string, it is just not an account number. Subtyping
 * `IllegalArgumentException` would let a caller that already catches the blank family
 * swallow this one by accident, and the owner would see "wajib diisi" on a filled field.
 */
class InvalidAccountNumberException(
    val raw: String,
) : IllegalStateException(
    "account number '$raw' is not a storable format: expected digits, optionally " +
        "separated by spaces or dashes, within the length bound",
)
