package net.klinik.rental.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A standalone re-derivation of the WCAG formula, deliberately *not* sharing
 * code with the suite it checks.
 *
 * ## Why the luminance is re-implemented here
 *
 * The point of a cross-check is to catch a mistake in the implementation under
 * test. An earlier version of this file called
 * [net.klinik.rental.ui.relativeLuminance] — the same function
 * `ContrastTest` uses — and therefore agreed with it by construction: a wrong
 * exponent or wrong channel weights would have passed here exactly as it passed
 * there. Worse, the cross-check only ran at white-on-black, where the ratio is
 * 21.0 for *any* monotone luminance curve, so it could not have detected the
 * class of bug it was written for.
 *
 * So the channel linearisation and the 0.2126 / 0.7152 / 0.0722 weighting are
 * written out again below, and the agreement is checked on **intermediate**
 * colours too, where a wrong curve actually diverges.
 */
class WcagFormulaTest {

    @Test
    fun `white on black is the maximum contrast ratio`() {
        assertEquals(21.0, ratio(Color.White, Color.Black), 0.005)
    }

    @Test
    fun `identical colours have a ratio of one`() {
        assertEquals(1.0, ratio(Color(0xFF1A1715), Color(0xFF1A1715)), 0.0001)
    }

    @Test
    fun `the ratio does not depend on argument order`() {
        assertEquals(
            ratio(Color(0xFF57514B), Color(0xFFFFFFFF)),
            ratio(Color(0xFFFFFFFF), Color(0xFF57514B)),
            1e-12,
        )
    }

    /**
     * Agreement on the extremes, which every correct implementation shares.
     */
    @Test
    fun `the implementations agree at white on black`() {
        assertEquals(
            ratio(Color.White, Color.Black),
            net.klinik.rental.ui.contrastRatio(Color.White, Color.Black),
            1e-9,
        )
    }

    /**
     * Agreement on the token values that actually ship, where a wrong luminance
     * curve would show. This is the assertion the earlier version could not
     * make: white-on-black is 21:1 under any monotone curve, but these pairs
     * are not.
     */
    @Test
    fun `the implementations agree on the shipped token pairs`() {
        val pairs = listOf(
            Triple("light tertiary on card", Color(0xFF746D65), Color(0xFFFFFFFF)),
            Triple("light primary on page", Color(0xFF1A1715), Color(0xFFF9F7F4)),
            Triple("dark tertiary on card", Color(0xFFA29892), Color(0xFF1F1A19)),
            Triple("dark secondary on page", Color(0xFFC9BFB9), Color(0xFF14100F)),
            Triple("white on primary fill", Color(0xFFFFFFFF), Color(0xFFE02649)),
            Triple("brand on brand surface", Color(0xFFDB163C), Color(0xFFFFF1F4)),
        )
        pairs.forEach { (name, fg, bg) ->
            val mine = ratio(fg, bg)
            val theirs = net.klinik.rental.ui.contrastRatio(fg, bg)
            assertEquals(
                "$name: the two luminance implementations disagree",
                mine,
                theirs,
                1e-9,
            )
        }
    }

    /**
     * A guard on the re-implementation itself: at least one pair must land
     * strictly between 1 and 21, so a future edit that silently broke this file
     * cannot pass by making every ratio degenerate.
     */
    @Test
    fun `intermediate pairs land strictly between the extremes`() {
        val r = ratio(Color(0xFF79726A), Color(0xFFFFFFFF))
        assertTrue("expected a mid-range ratio, got $r", r > 1.5 && r < 20.0)
    }

    /** Relative luminance, derived from the channel bytes. */
    private fun luminance(color: Color): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            // WCAG: below 0.03928 the segment is linear, above it is the
            // exponentiated form.
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = channel(color.red.roundToByteBits())
        val g = channel(color.green.roundToByteBits())
        val b = channel(color.blue.roundToByteBits())
        // Rec. 709 luma weights, as WCAG specifies.
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** The 8-bit channel value Compose's normalised float corresponds to. */
    private fun Float.roundToByteBits(): Int = (this * 255f + 0.5f).toInt()

    private fun ratio(fg: Color, bg: Color): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        val value = (max(a, b) + 0.05) / (min(a, b) + 0.05)
        // A NaN would come from a 0/0 or an infinity; neither is a legitimate
        // result, and letting it through would make every downstream
        // comparison silently false rather than failing.
        check(value.isFinite() && value >= 1.0) { "implausible contrast ratio: $value" }
        return value
    }
}