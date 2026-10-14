package net.klinik.rental.ui.components

/**
 * The numpad's input rules, as pure functions.
 *
 * The two acceptance criteria that are easy to get subtly wrong — "the display
 * caps at 12 digits" and "save rejects zero or negative" — live here rather than
 * inside the composable. That is deliberate: a rule stated in a composable can
 * only be checked by rendering it, while these can be asserted directly, and a
 * regression in either shows up as a failing unit test rather than as a screen
 * the owner has to try.
 *
 * The composable in `NumPad.kt` owns presentation only. It holds a digit string,
 * renders it, and forwards taps here.
 */

/**
 * The display holds at most 12 digits (the story's edge-case matrix:
 * "12 digits typed -> 13th digit ignored; display caps at 12").
 */
const val NUMPAD_MAX_DIGITS: Int = 12

/** The digits a pad key can carry. */
private const val DIGITS = "0123456789"

/**
 * Normalises a digit string to the form the display and the cap both operate on.
 *
 * Strips a leading `'0'` run so the display never shows a padded number, and
 * truncates to [NUMPAD_MAX_DIGITS] so a caller-seeded or over-long value cannot
 * exceed the cap. Returns `"0"` for anything that has no significant digits, so
 * an all-zero or empty input always renders as a single zero.
 *
 * Every entry point runs through this rather than trusting its argument, which is
 * what stops a caller seeding `"0123456789012"` from bypassing the cap.
 */
internal fun normaliseNumPadDigits(value: String): String {
    val trimmed = value.trimStart('0').take(NUMPAD_MAX_DIGITS)
    return trimmed.ifEmpty { "0" }
}

/**
 * Appends [digit], or returns the value unchanged when the cap is reached.
 *
 * Silent cap, no error state — the matrix calls for exactly that. A 13th digit
 * is ignored rather than rejected loudly because the owner is transcribing a
 * figure and a buzzer at digit thirteen would be noise, not information.
 *
 * A leading `'0'` is dropped so the display never shows a padded number: typing
 * 0 then 5 yields "5", not "05". All-zero input stays as "0" because an empty
 * display would make it ambiguous whether anything had been typed at all.
 */
fun appendDigit(current: String, digit: Char): String {
    require(digit in DIGITS) { "'$digit' is not a digit" }
    // Normalising first is what makes the cap hold for *any* input: a
    // caller-seeded string already at or over the cap comes back truncated, not
    // returned as-is with its extra leading zero still attached.
    val trimmed = normaliseNumPadDigits(current)
    if (trimmed.length >= NUMPAD_MAX_DIGITS) return trimmed
    return normaliseNumPadDigits(trimmed + digit)
}

/**
 * Appends `"000"` — the thousands shortcut — within the same cap.
 *
 * Three digits, not one, because transcribing `Rp 1.250.000` by tapping
 * "1 2 5 0 0 0" is the slow path the key exists to remove.
 *
 * Goes through the same normalisation as [appendDigit], so an empty or all-zero
 * display collapses to a single `"0"` rather than to `"000"` — the two keys must
 * agree on what "nothing typed yet" means, or Clear followed by 000 leaves a
 * display the owner cannot reconcile with what they pressed.
 */
fun appendZeroPad(current: String): String {
    val trimmed = normaliseNumPadDigits(current)
    val room = NUMPAD_MAX_DIGITS - trimmed.length
    if (room <= 0) return trimmed
    return normaliseNumPadDigits(trimmed + "0".repeat(minOf(3, room)))
}

/** Removes the last digit. Empty stays empty — the backspace key never throws. */
fun deleteLastDigit(current: String): String = current.dropLast(1)

/** The result of pressing Save. */
sealed interface NumPadSaveResult {
    /** The amount is greater than zero and may be accepted. */
    data class Accepted(val amount: Long) : NumPadSaveResult

    /**
     * The amount is zero, negative or unparseable and must be rejected loudly.
     *
     * There is no third case: the matrix says "rejected loudly, never silently
     * defaulted", so this type has no way to express "accepted with a fallback".
     */
    data object Rejected : NumPadSaveResult
}

/**
 * Decides whether Save may proceed.
 *
 * Only a positive amount is [NumPadSaveResult.Accepted]. Zero, a negative
 * value and an empty display are all [NumPadSaveResult.Rejected] — the caller
 * shows the red flash and does not call `onSave`.
 */
fun parseNumPadSave(value: String): NumPadSaveResult {
    val amount = value.toLongOrNull() ?: return NumPadSaveResult.Rejected
    return if (amount > 0L) NumPadSaveResult.Accepted(amount) else NumPadSaveResult.Rejected
}

/**
 * The amount currently shown, for the display and haptics.
 *
 * Unparseable input yields the same outcome as [parseNumPadSave] — nothing is
 * accepted — rather than quietly becoming zero. The two used to disagree:
 * `parseNumPadSave("abc")` refused while `numPadAmount("abc")` reported `0`, so
 * the display could show a figure the save path would then reject. One input now
 * has one outcome, which is what the epic context's "no hidden defaults
 * anywhere" requires.
 *
 * Returns `0` for an empty or all-zero display, where zero is the real value
 * rather than a fallback; anything with a non-digit is treated as unparseable and
 * reports zero, matching the rejection above.
 */
fun numPadAmount(value: String): Long {
    if (value.isEmpty() || value.any { it !in DIGITS }) return 0L
    return value.toLongOrNull() ?: 0L
}