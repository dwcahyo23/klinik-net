package net.klinik.rental.domain.parse

/**
 * The canonical form of a business account number, and the value the account stores.
 *
 * ## This is format validation and **nothing else**
 *
 * The epic's acceptance criterion is explicit: *"formatnya divalidasi sebatas format saja
 * … **tidak** diverifikasi terhadap bank — tidak ada yang berubah kalau bank punya nomor
 * berbeda."* So this function strips the separators a human types and refuses anything
 * that is not a digit — and that is the whole rule. There is deliberately:
 *
 *  - **no checksum**, because no Indonesian bank publishes one that is stable across
 *    account types, and inventing one would refuse real accounts;
 *  - **no routing or bank lookup**, because that would need a network call and would make
 *    a stored account depend on a third party's opinion of it;
 *  - **no length rule beyond a sanity bound**, because account lengths differ per bank and
 *    the bound below exists for a different reason entirely.
 *
 * A future reader who finds this function "too permissive" is the reader this KDoc is for:
 * tightening it into a shape check is the exact change the acceptance criterion forbids,
 * and the failure it would cause is silent — an account the owner's bank accepts would be
 * refused by their own app.
 *
 * ## Why the separators are stripped rather than squeezed
 *
 * `normalizePlate` squeezes whitespace to one space because a plate's space is *part of
 * the identifier* (`B 1235 TF` is not `B1235TF`). An account number is the opposite: the
 * spaces and dashes are pure presentation. The owner's banking app writes `1234 5678`,
 * their statement writes `1234-5678`, and their bank's website writes `12345678` — three
 * spellings of one account. Stripping the separators makes all three one string, which is
 * what lets an account edited from one source still be the same account.
 *
 * ## Why the length bound exists, and why it is not a bank rule
 *
 * This field is filled by **pasting from a banking app or a statement**. Without a bound, a
 * paste that grabs a whole page — a balance, a date, a reference number, the account —
 * lands silently as a 200-character "account number", and the owner has no way to see that
 * what they pasted is not what they meant. The bound is a paste guard, not a format rule,
 * and it is generous enough that no real account hits it.
 *
 * @return the normalised number, or `null` when [raw] has no digits at all after the
 *   separators are removed, or when it exceeds [MAX_ACCOUNT_NUMBER_DIGITS]. A blank number
 *   has no normalised form, so returning `""` would let a caller store an account that can
 *   never be reconciled against a bank mutation.
 */
fun normalizeAccountNumber(raw: String): String? {
    // Every separator a human or a banking app might insert between digit groups. The
    // set is closed and stated rather than a generic "strip non-alphanumerics", because
    // a stray letter means the owner pasted the wrong thing and should be told so
    // rather than have it silently deleted from their account number.
    val digits = raw.filterNot { it == ' ' || it == '-' || it == ' ' }
    if (digits.isEmpty()) return null
    if (digits.length > MAX_ACCOUNT_NUMBER_DIGITS) return null
    // Anything left that is not a digit is a refusal, not something to strip: `12ab` is
    // not an account number, and quietly storing `12` would record an account the owner
    // did not type.
    if (!digits.all { it.isDigit() }) return null
    return digits
}

/**
 * How many digits a pasted value may carry before it is refused.
 *
 * Indonesian account numbers are commonly 10–16 digits; the longest the owner is likely
 * to meet is well under 30. Forty is chosen so that no plausible real account is refused
 * while a paste of a statement line — which carries a date, a reference and an amount
 * alongside the number — cannot pass. See the KDoc above: this is a paste guard, and it
 * is deliberately not a per-bank rule.
 */
const val MAX_ACCOUNT_NUMBER_DIGITS = 40
