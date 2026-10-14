package net.klinik.rental.domain.parse

import net.klinik.rental.domain.validation.InvalidAmountException

/**
 * The one rupiah-input parser in the app (NFR-6).
 *
 * ## Why this lives in `domain/` and not in `data/` or `ui/`
 *
 * The epic context asks for "one function in the lowest layer that returns null
 * and refuses". `domain/` is the lowest layer and is pure JVM, so this file is
 * testable with `./gradlew :domain:test` and no Android SDK installed — which is
 * the point of the module split.
 *
 * Putting the same rule in the form and in the repository would be the defect
 * NFR-6 names: two screens could each pass their own UI check and still write a
 * bad row. Here, both sides call this.
 *
 * ## What "digits and spaces only" means
 *
 * [parseRupiahInput] accepts digit characters with spaces between them (so a
 * pasted `Rp 250.000` group does not become `null` merely for carrying its
 * separators) and refuses *everything else*. In particular it refuses:
 *
 *  - `"0"` — zero is not a rate, and `InvalidAmountException.MIN_AMOUNT` is `1L`
 *  - `"-1"` — the minus sign is not a digit
 *  - `"2.5"` — a rupiah amount is whole, and a dot means the caller thinks in
 *    sen or thousands; `250.000` is 250 thousand, not 2.5
 *  - `"abc"`, `""` — unparseable, and there is no fallback figure to return
 *
 * The prototype's `parseInt(x) || 250000` turned all of these into Rp 250.000.
 * A silently invented rate is worse than a refusal: the owner books a rental at
 * a price nobody agreed to, and the number is indistinguishable from a real one.
 *
 * ## Why this does not reuse `KnNumPad`
 *
 * `KnNumPad` already produces clean digit strings, so the numpad path never
 * needs this parser. It exists for the paths that do *not* come from the numpad:
 * an edit form restoring stored text, a future import, a paste. Those are
 * exactly the paths where the prototype's `parseInt(...) || 250000` lived.
 *
 * @return whole rupiah, or `null` when [input] is not a positive whole-rupiah
 *   amount. `null` means "refuse"; it never means "use a default".
 */
fun parseRupiahInput(input: String): Long? {
    // Spaces are tolerated as grouping ("250 000"); they are not digits and
    // carry no value. Everything else that is not a digit rejects the whole
    // string rather than being skipped — silently dropping a "-" would turn
    // "-1" into "1", which is the exact failure this function exists to prevent.
    val digits = input.filterNot { it.isWhitespace() }
    if (digits.isEmpty()) return null
    if (!digits.all { it in '0'..'9' }) return null

    // `toLongOrNull` rather than `toLong`: an owner can type more than 19
    // digits on a numpad that caps at 12, and an edit form can restore anything
    // already stored. Overflow must refuse, not throw NumberFormatException at
    // a caller that has no reason to expect it.
    val amount = digits.toLongOrNull() ?: return null

    // Zero is refused here rather than deferred to `Vehicle`'s constructor so
    // that the *form* gets the same answer as the *repository* from the same
    // call: NFR-6's "two screens can both pass the same UI check".
    return if (amount >= InvalidAmountException.MIN_AMOUNT) amount else null
}

/**
 * Validates a rupiah amount that is already a [Long] — the repository's and the
 * domain model's half of the NFR-6 rule.
 *
 * Delegating to [InvalidAmountException.requireValid] rather than re-implementing
 * the bound keeps one definition of "a legal amount" in the module. [Payment] and
 * [net.klinik.rental.domain.model.Allocation] call the same helper from their
 * initialisers, so a vehicle rate is held to exactly the rule a deposit is.
 *
 * @return [amount], so a caller can write `requirePositiveRate(parse(...))`
 *   without a separate statement.
 * @throws InvalidAmountException when [amount] is zero or negative. This throws
 *   rather than returning `null` because by this point the value is no longer
 *   text: there is no "unparseable" state left to represent, only an invalid one.
 */
fun requirePositiveRate(amount: Long): Long {
    InvalidAmountException.requireValid(amount)
    return amount
}