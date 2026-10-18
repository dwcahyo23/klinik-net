package net.klinik.rental.ui.screens.tunggakan

/**
 * The reminder message and the link that carries it — Story 4.2.
 *
 * ## Why these are pure functions in their own file
 *
 * Because the story's load-bearing rule is *"tautan WhatsApp dibangun ulang dari teks hasil
 * suntingan"*, and a rule that only exists inside a composable can only be checked by rendering
 * one. As pure functions the rebuild is a one-line assertion, and the screen keeps no arithmetic
 * of its own — the same split `NumPadLogic` and `allocatePayments` already use.
 *
 * ## What is deliberately absent
 *
 * No per-day dates: the message is for a tenant, not an audit. No amount formatting either —
 * [build] takes the row's **already-formatted** figure line, so the number in the message is
 * byte-identical to the number on the row rather than a second rendering of the same `Long`.
 */
object ReminderMessage {

    /**
     * The message, composed from a row's own figures.
     *
     * @param renter the tenant's name, as the row shows it.
     * @param businessName from `BusinessIdentityRepository.businessName()`, never a constant.
     * @param plate the vehicle's plate, as the row shows it.
     * @param figureText the row's own `Rp 750.000 · 3 hari`. Passed rather than rebuilt from an
     *   amount and a day count, because two renderings of one figure is exactly how the number
     *   on screen and the number in the message start to differ.
     * @param template the localised sentence, resolved by the caller from `RStrings` so this
     *   file stays free of `Context`.
     */
    fun build(
        renter: String,
        businessName: String,
        plate: String,
        figureText: String,
        template: (renter: String, business: String, plate: String, figure: String) -> String,
    ): String = template(renter, businessName, plate, figureText)

    /**
     * The `wa.me` link that carries [text] to [phone].
     *
     * ## Why the number is in the link
     *
     * Because the row names one tenant and one figure, and the message is about that person's
     * bill. Opening WhatsApp's contact chooser instead would let the owner send Budi's arrears
     * to whoever they tapped next — the mistake this link exists to make impossible. The phone
     * is carried on `ArrearsRow` for the same reason the amount is: reading it at send time
     * would be the second snapshot the acceptance criterion forbids, for a value that decides
     * **who receives** the message.
     *
     * ## Why the number is normalised, and why that is not a second source of truth
     *
     * `wa.me` resolves only an international number with no punctuation. The stored number is
     * whatever the owner typed — `0812-8899-7722` in the prototype's own data — so the link
     * strips non-digits and rewrites a leading `0` as Indonesia's `62`. That is **link
     * formatting**, not a re-derivation of the number: it touches no stored value and the row
     * still shows exactly what the owner entered.
     *
     * @throws IllegalArgumentException when [phone] holds no digits. An empty recipient would
     *   open WhatsApp on a blank contact with a real bill in the box — refused here rather than
     *   rendered as a broken link.
     */
    fun whatsappLink(phone: String, text: String): String =
        "https://wa.me/" + international(phone) + "?text=" + encode(text)

    /**
     * A stored Indonesian phone number as `wa.me` wants it: digits only, `62` for a leading `0`.
     *
     * `+62…` and `62…` pass through unchanged apart from punctuation; `08…` becomes `628…`.
     * Anything else is returned digit-stripped, because guessing a country code for a number
     * that does not claim one would be inventing a recipient.
     */
    internal fun international(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        require(digits.isNotEmpty()) { "a reminder needs a phone number, but '$phone' has no digits" }
        return when {
            // Already international, with or without the `+`.
            digits.startsWith("62") -> digits
            // Local form. `0` is the national trunk prefix and never part of the number.
            digits.startsWith("0") -> "62" + digits.substring(1)
            else -> digits
        }
    }

    /**
     * Percent-encodes [text] for a query value.
     *
     * `java.net.URLEncoder` is deliberately not used: it encodes a space as `+`, which is
     * correct for `application/x-www-form-urlencoded` and wrong for a URI query — WhatsApp
     * receives a literal plus sign. This encodes spaces as `%20` and leaves the unreserved
     * characters alone.
     */
    internal fun encode(text: String): String = buildString {
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (
                char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
                char == '-' || char == '_' || char == '.' || char == '~'
            ) {
                append(char)
            } else {
                append('%')
                append(HEX[value shr 4])
                append(HEX[value and 0x0F])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
