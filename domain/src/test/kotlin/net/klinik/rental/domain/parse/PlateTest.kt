package net.klinik.rental.domain.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `NORMALIZE_PLATE` rows of Story 2.2's I/O matrix.
 *
 * This function's output is the value the database unique index is built on, so
 * these assertions are really about what counts as "the same plate": two
 * spellings that normalise alike must collide in the database, not just in a UI
 * check.
 */
class PlateTest {

    /** The matrix row: whitespace squeezed, uppercased, trimmed. */
    @Test
    fun `internal whitespace is squeezed and the plate is uppercased`() {
        assertEquals("B 1235 TF", normalizePlate("  b   1235  tf "))
    }

    /** A blank plate has no normalised form, so it is `null` rather than `""`. */
    @Test
    fun `a whitespace-only plate has no normalised form`() {
        assertNull(normalizePlate("   "))
    }

    @Test
    fun `an empty plate has no normalised form`() {
        assertNull(normalizePlate(""))
    }

    /** The epic context's own example: two spellings must become one string. */
    @Test
    fun `case and spacing variants normalise to the same plate`() {
        val variants = listOf("b 1235 tf", "B 1235 TF", "  b 1235   tf", "B   1235 TF  ")
        val normalized = variants.map { normalizePlate(it) }
        assertEquals(1, normalized.distinct().size)
        assertEquals("B 1235 TF", normalized.first())
    }

    /**
     * Squeezing *multiple* internal spaces to one is what stops `B  1235  TF`
     * and `B 1235 TF` becoming two rows for one physical car. `trim()` alone
     * would not do this — it only touches the ends.
     *
     * A *missing* internal space is a different case and deliberately not fixed
     * here: the requirement is "trimmed, uppercased, whitespace squeezed", not
     * "re-spaced". Inventing a separator the owner did not type would mean
     * `B1235TF` and `B 1235 TF` collided, which is a shape inference this story
     * explicitly declines to make.
     */
    @Test
    fun `repeated internal spaces do not create a second plate`() {
        assertEquals(normalizePlate("B 1235 TF"), normalizePlate("B  1235   TF"))
        assertEquals(normalizePlate("B 1235 TF"), normalizePlate(" B 1235 TF "))
    }

    /** Internal single spaces are preserved — they separate the plate's parts. */
    @Test
    fun `the single space between plate parts is kept`() {
        assertEquals("B 1235 TF", normalizePlate("B 1235 TF"))
        assertEquals("B 1 AB", normalizePlate("B  1  AB"))
    }

    /**
     * No plate-format regex is ported from the prototype. A shape check would
     * reject plates the owner actually owns, so the only rules are "not blank"
     * and "normalised".
     */
    @Test
    fun `no shape is imposed beyond non-blank`() {
        assertEquals("1234", normalizePlate("1234"))
        assertEquals("X", normalizePlate("x"))
        assertEquals("B 12345 TF", normalizePlate("b 12345 tf"))
    }

    /** Idempotent, because the repository normalises then the model re-checks. */
    @Test
    fun `normalising an already-normal plate changes nothing`() {
        val once = normalizePlate("  b   1235  tf ")!!
        assertEquals(once, normalizePlate(once))
    }

    /** A tab or newline between parts is whitespace and squeezes like a space. */
    @Test
    fun `a tab is whitespace`() {
        assertEquals("B 1235 TF", normalizePlate("b\t1235\ntf"))
    }
}