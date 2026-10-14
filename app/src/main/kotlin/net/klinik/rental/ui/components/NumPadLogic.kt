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
 * Appends [digit], or returns [current] unchanged when the cap is reached.
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
    val trimmed = current.trimStart('0')
    if (trimmed.length >= NUMPAD_MAX_DIGITS) return current
    val next = trimmed + digit
    return next.ifEmpty { "0" }
}

/**
 * Appends `"000"` — the thousands shortcut — within the same cap.
 *
 * Three digits, not one, because transcribing `Rp 1.250.000` by tapping
 * "1 2 5 0 0 0" is the slow path the key exists to remove.
 */
fun appendZeroPad(current: String): String {
    val trimmed = current.trimStart('0')
    val room = NUMPAD_MAX_DIGITS - trimmed.length
    if (room <= 0) return current
    val next = trimmed + "0".repeat(minOf(3, room))
    return next.ifEmpty { "0" }
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

/** The amount currently shown, for display and haptics. Zero for empty input. */
fun numPadAmount(value: String): Long = value.toLongOrNull() ?: 0L