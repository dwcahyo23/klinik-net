package net.klinik.rental.domain.parse

/**
 * The canonical form of a vehicle plate, and the value the unique index is
 * built on.
 *
 * ## Why normalisation, not a shape
 *
 * The epic context requires a plate to be "unique *after normalization*
 * (uppercased, spaces squeezed), so `b 1235 tf` and `B 1235 TF` collide". It
 * deliberately does not require a shape. The prototype carried a
 * `^[A-Z]{1,3}\s?\d{1,4}\s?[A-Z]{0,3}$` check, and that regex disagrees with its
 * own normaliser — it rejects `B 1235 TF` (two internal spaces after squeezing
 * becomes one, but the regex allows only one `\s?`) while accepting plates its
 * own DB index would treat as distinct. A shape check is not being ported.
 *
 * ## Why this is the *only* normaliser
 *
 * The database unique index is on the normalised value, so a row written through
 * any path that skipped this function would collide with — or wrongly fail to
 * collide with — a stored row. Every writer normalises first: the form, the
 * repository, and `Vehicle`'s own constructor.
 *
 * ## Why the whitespace rule is "squeeze", not "strip"
 *
 * `trim()` alone would let `B  1235  TF` and `B 1235 TF` become two distinct
 * rows for one physical car — the same plate typed with two different amounts of
 * finger pressure on the keyboard. Squeezing internal runs to a single space
 * makes every such spelling produce one string, which is what the uniqueness
 * requirement actually needs.
 *
 * What this deliberately does *not* do is insert a space the owner did not type:
 * `B1235TF` stays `B1235TF`. Re-spacing would be a shape inference, and this
 * story requires "required and unique after normalisation", not a plate format.
 *
 * @return the normalised plate, or `null` when [raw] is blank or whitespace
 *   only. A blank plate has no normalised form, so returning `""` would let a
 *   caller store an empty primary key of sorts — and two blanks would then
 *   "collide" in the unique index for the wrong reason.
 */
fun normalizePlate(raw: String): String? {
    // Order matters: squeeze first, so leading and trailing runs collapse into
    // the single separator that `trim()` then removes entirely rather than
    // leaving a stray space behind.
    val squeezed = raw.replace(WHITESPACE_RUN, " ").trim()
    if (squeezed.isEmpty()) return null
    return squeezed.uppercase()
}

/**
 * Every run of whitespace, of any kind, as one separator.
 *
 * `[\\s+]` rather than `" +"`: the owner types on an Android soft keyboard where
 * a non-breaking space is easy to produce by accident, and `trim()` does not
 * remove one in the middle of a string. A plate the owner typed and a plate that
 * round-trips through the keyboard have to normalise the same way, or the edit
 * form will report its own saved value as a different plate.
 */
private val WHITESPACE_RUN = Regex("\\s+")