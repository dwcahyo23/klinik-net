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
     * ## Why a blank business name is refused
     *
     * Because this is the one string that leaves the app and reaches another person, and the
     * failure is silent: a blank name produces *"ini pengingat dari . Tunggakan …"*, which reads
     * as a broken app rather than as a missing setting. A caller that cannot supply a name has a
     * wiring problem, and a wiring problem must be visible here rather than delivered to a
     * tenant.
     *
     * @param renter the tenant's name, as the row shows it.
     * @param businessName from `BusinessIdentityRepository.businessName()`, never a constant.
     * @param plate the vehicle's plate, as the row shows it.
     * @param figureText the row's own `Rp 750.000 · 3 hari`. Passed rather than rebuilt from an
     *   amount and a day count, because two renderings of one figure is exactly how the number
     *   on screen and the number in the message start to differ.
     * @param template the localised sentence, resolved by the caller from `RStrings` so this
     *   file stays free of `Context`.
     * @throws IllegalArgumentException when [businessName] is blank. See the paragraph above.
     */
    fun build(
        renter: String,
        businessName: String,
        plate: String,
        figureText: String,
        template: (renter: String, business: String, plate: String, figure: String) -> String,
    ): String {
        require(businessName.isNotBlank()) {
            "a reminder must name the business, but the name was blank — " +
                "check that the business identity is wired, because an unnamed reminder " +
                "reaches a tenant as a broken sentence"
        }
        return template(renter, businessName, plate, figureText)
    }

    /**
     * The `wa.me` link that carries [text] to [phone], or `null` when [phone] cannot address
     * anyone.
     *
     * ## Why `null` rather than an exception
     *
     * Because the phone comes from a stored row and the caller is a message edit — a field the
     * owner types into. Throwing from `onReminderChange` would crash the ViewModel coroutine on
     * every keystroke for a row whose renter has an unaddressable number, which is a worse
     * failure than a Send button that refuses. The dialog shows the refusal; the field still
     * accepts text.
     *
     * ## Why the number is in the link
     *
     * Because the row names one tenant and one figure, and the message is about that person's
     * bill. Opening WhatsApp's contact chooser instead would let the owner send Budi's arrears
     * to whoever they tapped next — the mistake this link exists to make impossible. The phone
     * is carried on `ArrearsRow` for the same reason the amount is: reading it at send time
     * would be the second snapshot the acceptance criterion forbids, for a value that decides
     * **who receives** the message.
     */
    fun whatsappLink(phone: String, text: String): String? {
        val recipient = international(phone) ?: return null
        return "https://wa.me/$recipient?text=" + encode(text)
    }

    /**
     * A stored Indonesian phone number as `wa.me` wants it, or `null` when it cannot be resolved.
     *
     * `+62…` and `62…` pass through unchanged apart from punctuation; `08…` becomes `628…`.
     *
     * ## Why a number claiming neither form is refused rather than passed through
     *
     * Because `wa.me` treats a bare digit string as an international number: `812…` resolves as
     * a Japanese number and the message is delivered to a stranger. Returning it unchanged would
     * invent a recipient just as effectively as guessing a country code — and more silently,
     * because it looks like it worked. The app stores Indonesian numbers, so a number that names
     * no country is a data problem the owner can fix in the renter form.
     */
    internal fun international(phone: String): String? {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> null
            // Already international, with or without the `+`.
            digits.startsWith("62") -> digits
            // Local form. `0` is the national trunk prefix and never part of the number.
            digits.startsWith("0") -> "62" + digits.substring(1)
            // No country code and no trunk prefix: not a number this app can address.
            else -> null
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
