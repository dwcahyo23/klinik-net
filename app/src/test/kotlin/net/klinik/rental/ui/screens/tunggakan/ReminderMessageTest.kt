package net.klinik.rental.ui.screens.tunggakan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Story 4.2's message and link rules, as pure functions.
 *
 * ## Why these are not Compose tests
 *
 * Because the story's load-bearing rule — *"tautan dibangun ulang dari teks hasil suntingan"* — is
 * a property of two strings, not of a screen. A rendering test can only prove that a button was
 * pressed; these prove that the link is a **function of the text**, which is what makes an edit
 * real rather than cosmetic.
 *
 * No Robolectric: nothing here touches Android.
 */
class ReminderMessageTest {

    private val template =
        { renter: String, business: String, plate: String, figure: String ->
            "Halo $renter, ini pengingat dari $business. Tunggakan $plate saat ini $figure."
        }

    /**
     * `MSG_COMPOSE` — the message names all four facts and nothing else.
     *
     * The figure is passed through **verbatim**, which is the point: it is the row's own
     * `figureText`, so the number the owner reads and the number the tenant receives are one
     * rendering rather than two.
     */
    @Test
    fun `the message carries the renter, business, plate and the row's own figure`() {
        val message = ReminderMessage.build(
            renter = "Budi Santoso",
            businessName = "PT Klinik Net Rent Car",
            plate = "B 1235 TF",
            figureText = "Rp 750.000 · 3 hari",
            template = template,
        )

        assertTrue(message.contains("Budi Santoso"))
        assertTrue(message.contains("PT Klinik Net Rent Car"))
        assertTrue(message.contains("B 1235 TF"))
        assertTrue("the figure verbatim, not re-formatted", message.contains("Rp 750.000 · 3 hari"))
    }

    /**
     * `MSG_COMPOSE` — **no per-day dates.**
     *
     * Asserted as the absence of a date rather than by inspecting the template, because the rule
     * is about what a tenant reads: a message naming 14 Okt and 15 Okt is an audit, not a
     * reminder. A date added later as a "helpful extra" would be caught here.
     */
    @Test
    fun `the message names no per-day date`() {
        val message = ReminderMessage.build(
            renter = "Budi Santoso",
            businessName = "PT Klinik Net Rent Car",
            plate = "B 1235 TF",
            figureText = "Rp 750.000 · 3 hari",
            template = template,
        )

        for (date in listOf("14 Okt", "01 Sep", "2026-10-14", "Oktober")) {
            assertTrue("the message must not name a day: $date", !message.contains(date))
        }
    }

    /**
     * `MSG_BUSINESS_NAME` — the name comes from the caller, so a custom one is quoted.
     *
     * The failure this guards is a template with the default baked in: the owner renames their
     * business, and every message still speaks for `PT Klinik Net Rent Car`.
     */
    @Test
    fun `a custom business name replaces the default`() {
        val message = ReminderMessage.build(
            renter = "Budi",
            businessName = "Rental Pak Andi",
            plate = "B 1 X",
            figureText = "Rp 1 · 1 hari",
            template = template,
        )

        assertTrue(message.contains("Rental Pak Andi"))
        assertTrue(
            "the default must never appear when a custom name is stored",
            !message.contains("PT Klinik Net Rent Car"),
        )
    }

    /**
     * `MSG_EDIT_REBUILDS` — the link is a **function of the text**.
     *
     * The whole acceptance criterion: an owner who changes the amount must not send the
     * pre-edit number. Two different texts must produce two different links, and each must carry
     * its own text.
     */
    @Test
    fun `the link is rebuilt from whatever text it is given`() {
        val original = "Tunggakan Rp 750.000 · 3 hari"
        val edited = "Tunggakan Rp 500.000 · 3 hari"

        val first = ReminderMessage.whatsappLink("0812-8899-7722", original)
        val second = ReminderMessage.whatsappLink("0812-8899-7722", edited)

        assertTrue("the two links differ", first != second)
        assertTrue("and the second carries the edited figure", second.contains("500.000"))
        assertTrue("not the original one", !second.contains("750.000"))
    }

    /**
     * `MSG_COMPOSE` — the link targets the row's renter, normalised for `wa.me`.
     *
     * The stored number is whatever the owner typed; `wa.me` resolves only international digits.
     * A leading `0` becomes `62`, and punctuation is stripped.
     */
    @Test
    fun `a local number is normalised into an international one`() {
        assertEquals("6281288997722", ReminderMessage.international("0812-8899-7722"))
        assertEquals("6281288997722", ReminderMessage.international("081288997722"))
        assertEquals("6281288997722", ReminderMessage.international("+62 812 8899 7722"))
        assertEquals("6281288997722", ReminderMessage.international("6281288997722"))
    }

    /** And the normalised number is what the link addresses. */
    @Test
    fun `the link addresses the renter's number`() {
        val link = ReminderMessage.whatsappLink("0812-8899-7722", "halo")

        assertTrue(link.startsWith("https://wa.me/6281288997722?text="))
    }

    /**
     * A number with no digits is refused rather than producing a broken link.
     *
     * An empty recipient would open WhatsApp on a blank contact with a real bill in the box —
     * a message about money with no addressee, which is worse than no message.
     */
    @Test
    fun `a number with no digits is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReminderMessage.whatsappLink("", "halo")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderMessage.international("---")
        }
    }

    /**
     * Encoding: spaces become `%20`, not `+`.
     *
     * `URLEncoder` would produce `+`, which is correct for a form body and wrong in a URI query
     * — WhatsApp receives a literal plus. This is the one encoding decision in the file, so it is
     * pinned rather than assumed.
     */
    @Test
    fun `the text is percent-encoded for a query, not form-encoded`() {
        val encoded = ReminderMessage.encode("Halo Budi, Rp 750.000 · 3 hari & terima kasih")

        assertTrue("a space is %20, never +", encoded.contains("%20"))
        assertTrue("and never a literal plus", !encoded.contains("+"))
        assertTrue("an ampersand would truncate the query", encoded.contains("%26"))
        assertTrue("letters are left alone", encoded.contains("Halo"))
        assertTrue("so are digits", encoded.contains("750"))
    }

    /** A multi-byte character is encoded byte-wise, so the link survives UTF-8. */
    @Test
    fun `multi-byte characters encode as their UTF-8 bytes`() {
        val encoded = ReminderMessage.encode("·")

        assertEquals("%C2%B7", encoded)
    }
}
