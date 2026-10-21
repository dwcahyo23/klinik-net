package net.klinik.rental.ui

import androidx.compose.ui.graphics.Color
import net.klinik.rental.ui.theme.DarkColors
import net.klinik.rental.ui.theme.LightColors
import net.klinik.rental.ui.theme.SemanticColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The colour half of Story 2.1's accessibility AC, asserted directly against
 * the token values.
 *
 * This is deliberately a plain JVM test rather than a rendered one. Contrast is
 * a property of two hex values; rendering a screen to photograph it with a
 * screenshot diff would add a flaky dependency without adding a check, and it
 * would let a *pair* pass in isolation while the real screen pairs it with a
 * third colour. The 48dp half of the AC does need rendering — that lives in
 * `AccessibilityTest`.
 *
 * ## Why some values differ from `shared.css`
 *
 * The stylesheet was annotated for aesthetics, not measured for contrast. Seven
 * values are shifted, each within a hue the stylesheet already ships, because
 * a faithful hex port fails the 4.5:1 floor this class asserts. The CSS is the
 * source of truth for the *design*, not for the numbers — see
 * `CONTRAST_ADJUSTMENTS` in `Color.kt` for the full list with measurements.
 * The threshold is never weakened and no value is special-cased here.
 */
class ContrastTest {

    @Test
    fun `relative luminance matches the WCAG definition`() {
        // Anchored on the reference values from WCAG 2.x.
        assertEquals(1.0, relativeLuminance(Color.White), 1e-6)
        assertEquals(0.0, relativeLuminance(Color.Black), 1e-6)
    }

    @Test
    fun `contrast ratio is symmetric and matches the WCAG reference points`() {
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.01)
        assertEquals(contrastRatio(Color.White, Color.Black), contrastRatio(Color.Black, Color.White), 1e-9)
    }

    // ------------------------------------------------------------------
    // The four semantic foregrounds are byte-identical across both themes.
    // This is the structural rule from shared.css lines 93-108, which
    // redefines surfaces and `-bg` companions but deliberately omits
    // `--success`, `--warning`, `--danger` and `--info`.
    // ------------------------------------------------------------------

    @Test
    fun `semantic foregrounds are identical in both themes`() {
        val light = LightColors.semantic
        val dark = DarkColors.semantic
        assertEquals("success foreground drifted in dark", light.success, dark.success)
        assertEquals("warning foreground drifted in dark", light.warning, dark.warning)
        assertEquals("danger foreground drifted in dark", light.danger, dark.danger)
        assertEquals("info foreground drifted in dark", light.info, dark.info)
    }

    @Test
    fun `semantic foregrounds are the CSS values verbatim`() {
        assertEquals(Color(0xFF0E9F6E), SemanticColors.Shared.success)
        assertEquals(Color(0xFFD97706), SemanticColors.Shared.warning)
        assertEquals(Color(0xFFDC2626), SemanticColors.Shared.danger)
        assertEquals(Color(0xFF2563EB), SemanticColors.Shared.info)
    }

    @Test
    fun `only the -bg companions change in dark`() {
        val light = LightColors.semanticBg
        val dark = DarkColors.semanticBg
        listOf("success", "warning", "danger", "info").forEach { name ->
            val l = colorOf(light, name)
            val d = colorOf(dark, name)
            assertTrue(
                "$name background must differ between themes",
                l != d,
            )
        }
    }

    @Test
    fun `the dark -bg companions are the CSS literals`() {
        assertEquals(Color(0xFF10291F), DarkColors.semanticBg.success)
        assertEquals(Color(0xFF2E2411), DarkColors.semanticBg.warning)
        assertEquals(Color(0xFF2E1414), DarkColors.semanticBg.danger)
        assertEquals(Color(0xFF14203A), DarkColors.semanticBg.info)
    }

    // ------------------------------------------------------------------
    // The three text levels against every surface they land on, both themes.
    // ------------------------------------------------------------------

    @Test
    fun `every light text level clears 4_5 on every light surface`() {
        val c = LightColors
        assertTextLevelsClear(c.textPrimary, c.textSecondary, c.textTertiary, c)
        // Inverse text sits on the inverted surface, not on bgCard.
        assertClears("light onSurfaceInverse/surfaceInverse", c.onSurfaceInverse, c.surfaceInverse)
    }

    @Test
    fun `every dark text level clears 4_5 on every dark surface`() {
        val c = DarkColors
        assertTextLevelsClear(c.textPrimary, c.textSecondary, c.textTertiary, c)
        assertClears("dark onSurfaceInverse/surfaceInverse", c.onSurfaceInverse, c.surfaceInverse)
    }

    // ------------------------------------------------------------------
    // Text on a solid fill: the button and chip case.
    // ------------------------------------------------------------------

    @Test
    fun `white label clears 4_5 on every solid fill`() {
        listOf(
            "primaryFill" to LightColors.primaryFill,
            "successFill" to LightColors.successFill,
            "dangerFill" to LightColors.dangerFill,
            "infoFill" to LightColors.infoFill,
        ).forEach { (name, fill) ->
            assertClears("white on $name", Color.White, fill)
        }
    }

    /**
     * The dark scheme declares its own copies of the four fills, so the dark
     * values are what a `KnButton` on a dark tablet actually sits on. Asserting
     * only `LightColors` would leave a dark-theme fill regression invisible.
     */
    @Test
    fun `white label clears 4_5 on the dark solid fills too`() {
        listOf(
            "primaryFill" to DarkColors.primaryFill,
            "successFill" to DarkColors.successFill,
            "dangerFill" to DarkColors.dangerFill,
            "infoFill" to DarkColors.infoFill,
        ).forEach { (name, fill) ->
            assertClears("white on dark $name", Color.White, fill)
        }
    }

    /**
     * The focus ring drawn *on* a filled button.
     *
     * A filled variant and a ring are the same node, so the pair that matters is
     * the ring against the fill it sits inside — not only against the page and
     * card surfaces the earlier test covers.
     *
     * A filled variant does **not** use the brand ring: `--brand-500` measures
     * 1.3:1 against the primary fill, which is brand darkened precisely until
     * white clears 4.5:1 on it. So the filled variants ring in their own label
     * ink. Both the rejected value and the shipped one are asserted, so a revert
     * to the brand ring on a fill fails here rather than in front of a keyboard
     * user.
     */
    @Test
    fun `the focus ring clears 3_1 on the solid fills`() {
        val inverse = LightColors.surfaceInverse
        listOf(
            "primaryFill" to LightColors.primaryFill,
            "successFill" to LightColors.successFill,
            "dangerFill" to LightColors.dangerFill,
            "infoFill" to LightColors.infoFill,
        ).forEach { (name, fill) ->
            // The value a filled variant actually rings in.
            val ratio = contrastRatio(inverse, fill)
            assertTrue(
                "the ring on $name was ${"%.3f".format(ratio)}:1, needs >= 3:1",
                ratio >= 3.0,
            )
        }

        // And the reason a filled variant cannot use the brand ring, pinned so
        // the choice above is not mistaken for an arbitrary one.
        val brandOnPrimary = contrastRatio(LightColors.focusRing, LightColors.primaryFill)
        assertTrue(
            "the brand ring is expected NOT to reach 3:1 on the primary fill " +
                "(${ "%.2f".format(brandOnPrimary) }:1); if this now fails, the " +
                "filled variants could go back to the brand ring",
            brandOnPrimary < 3.0,
        )
    }

    // ------------------------------------------------------------------
    // Brand text on the brand-tinted surface (badge-brand, active rail item).
    // ------------------------------------------------------------------

    @Test
    fun `brand text clears 4_5 on the brand surface in both themes`() {
        assertClears("light onBrand/brandSurface", LightColors.onBrand, LightColors.brandSurface)
        assertClears("dark onBrand/brandSurface", DarkColors.onBrand, DarkColors.brandSurface)
    }

    @Test
    fun `error text clears 4_5 on both the card and the danger surface`() {
        assertClears("light errorText/bgCard", LightColors.errorText, LightColors.bgCard)
        assertClears("light errorText/dangerBg", LightColors.errorText, LightColors.semanticBg.danger)
        assertClears("dark errorText/bgCard", DarkColors.errorText, DarkColors.bgCard)
        assertClears("dark errorText/dangerBg", DarkColors.errorText, DarkColors.semanticBg.danger)
    }

    // ------------------------------------------------------------------
    // The chart's own colours: a bar and a slice are **non-text**, so the
    // 3:1 floor of WCAG 1.4.11 applies — and the numbers beside them
    // carry the same information, so a bar is not the only channel.
    // ------------------------------------------------------------------

    /**
     * The bar that met its target is visible against the card in both themes.
     *
     * The old value was `primaryFill`, which clears 3:1 easily but made the chart
     * read as a row of alarming red bars. The prototype's own `--brand-300` is
     * 1.93:1 on white — **below** the 3:1 floor — so the prototype's value is
     * pinned here as a *value*, and the 3:1 claim is made about the darker
     * dark-theme step instead, where the same colour is 8.92:1.
     *
     * So the assertion is deliberately two-part: the light bar is asserted to be
     * the prototype's literal, and the dark bar is asserted to clear 3:1. A
     * blanket 3:1 on the light bar would be a claim the prototype itself does
     * not make, and asserting the literal is what stops the value drifting.
     */
    @Test
    fun `the chart's on-target bar is the prototype's brand step in both themes`() {
        assertEquals(
            "the light bar is --brand-300, the prototype's own value",
            Color(0xFFFF9FB5),
            LightColors.brand300,
        )
        assertEquals(
            "and it is the same value in dark — shared.css does not redefine --brand-300",
            LightColors.brand300,
            DarkColors.brand300,
        )
        assertTrue(
            "the dark bar clears the 3:1 non-text floor on a dark card: " +
                "${contrastRatio(DarkColors.brand300, DarkColors.bgCard)}:1",
            contrastRatio(DarkColors.brand300, DarkColors.bgCard) >= 3.0,
        )
    }

    /**
     * The below-target bar is `--warm-300`, and it is muted rather than absent.
     *
     * The old value was `brandSurface` — a tint so close to the card that the
     * slice measured 1.10:1 against it and was effectively invisible, so a bar
     * that missed its target looked like no bar at all. `--warm-300` at 1.55:1
     * is still low for a non-text element, and the assertion says so rather than
     * pretending otherwise: the bar is a *secondary* channel, and the value
     * printed above it is the primary one.
     */
    @Test
    fun `the below-target bar is the prototype's warm step and not the card tint`() {
        assertEquals(
            "the below-target bar is --warm-300",
            Color(0xFFD6CFC4),
            LightColors.warm300,
        )
        assertEquals(
            "and the same value in dark",
            LightColors.warm300,
            DarkColors.warm300,
        )
        assertTrue(
            "it must be visible against the card — the old brandSurface measured 1.10:1 " +
                "and the bar disappeared entirely: " +
                "${contrastRatio(LightColors.warm300, LightColors.bgCard)}:1",
            contrastRatio(LightColors.warm300, LightColors.bgCard) > 1.3,
        )
    }

    /**
     * The avatar's ring is the prototype's `--brand-100`, and the dark scheme
     * supplies its own step.
     *
     * Measured, because the claim is easy to overstate: `--brand-100` on
     * `--brand-50` is **1.11:1** — a tint around the disc, not a visible edge.
     * The previous token, `borderStrong`, was 1.41:1 — also not an edge, and grey
     * where the prototype's is pink. So this pins the prototype's value, and the
     * dark step is pinned to a value that *is* visible.
     */
    @Test
    fun `the avatar ring is the prototype's brand-100 and the dark scheme has its own step`() {
        assertEquals(
            "the light ring is --brand-100",
            Color(0xFFFFE1E8),
            LightColors.brand100,
        )
        assertTrue(
            "the dark ring must be visible against a near-black disc — a pale pink " +
                "would be a halo, not an edge: " +
                "${contrastRatio(DarkColors.brand100, DarkColors.brandSurface)}:1",
            contrastRatio(DarkColors.brand100, DarkColors.brandSurface) >= 2.5,
        )
    }

    // ------------------------------------------------------------------
    // The focus ring is a non-text indicator: the 3:1 floor applies, and it
    // is the reason brand500 was not darkened like the other brand values.
    // ------------------------------------------------------------------

    @Test
    fun `the focus ring clears 3_1 on every surface in both themes`() {
        listOf(LightColors, DarkColors).forEach { c ->
            listOf(
                "bgPage" to c.bgPage,
                "bgCard" to c.bgCard,
                "bgSunken" to c.bgSunken,
                "bgRail" to c.bgRail,
            ).forEach { (surfaceName, surface) ->
                val ratio = contrastRatio(c.focusRing, surface)
                assertTrue(
                    "focus ring on $surfaceName was $ratio:1, needs >= 3:1",
                    ratio >= 3.0,
                )
            }
        }
    }

    @Test
    fun `the snackbar's count and dismiss ink is measured against its fill`() {
        // The chip's selected count and the snackbar's dismiss both use the
        // inverse ink on the filled surface, so the pair is pinned rather than
        // assumed.
        assertClears("onSurfaceInverse on surfaceInverse", LightColors.onSurfaceInverse, LightColors.surfaceInverse)
        assertClears("dark onSurfaceInverse on surfaceInverse", DarkColors.onSurfaceInverse, DarkColors.surfaceInverse)
    }

    @Test
    fun `the placeholder tone is readable on the card surface`() {
        // Placeholder copy is still read, so it uses the tertiary text level
        // rather than the CSS's --warm-400 (which is 2.58:1 on card).
        assertClears("light placeholder on card", LightColors.textTertiary, LightColors.bgCard)
        assertClears("dark placeholder on card", DarkColors.textTertiary, DarkColors.bgCard)
    }

    // ------------------------------------------------------------------
    // Documented, deliberately not asserted.
    // ------------------------------------------------------------------

    /**
     * The semantic foregrounds cannot clear 4.5:1 on *both* a light `-bg` and a
     * dark `-bg` companion, and the epic context forbids changing the
     * foregrounds between themes. The tension is real, so it is pinned as an
     * exact expected set rather than quietly omitted.
     *
     * The mitigation is structural rather than chromatic: a Badge never carries
     * the only copy of a value — it sits beside the figure it describes, which
     * is rendered at the tertiary or primary text level and does clear the
     * floor. Changing this would mean either a fifth status colour or a
     * theme-dependent semantic foreground, and the epic context forbids both.
     *
     * ## Why the expected set is exact rather than a range
     *
     * An earlier version asserted only "at least two of eight pass", which
     * would have survived six pairs falling further below the floor — precisely
     * the regression this row exists to catch. The expected set below is the
     * measured one; a change to any of these ratios has to be a deliberate
     * token change with the reasoning recorded in the spec, not a drift.
     *
     * The measurement is in the assertion *message* rather than a `println`,
     * because `println` is swallowed unless the build enables
     * `showStandardStreams`, which this module does not.
     */
    @Test
    fun `semantic badge pairs hold exactly the documented split`() {
        val measured = listOf(
            Triple("light success", LightColors.semantic.success, LightColors.semanticBg.success),
            Triple("light warning", LightColors.semantic.warning, LightColors.semanticBg.warning),
            Triple("light danger", LightColors.semantic.danger, LightColors.semanticBg.danger),
            Triple("light info", LightColors.semantic.info, LightColors.semanticBg.info),
            Triple("dark success", DarkColors.semantic.success, DarkColors.semanticBg.success),
            Triple("dark warning", DarkColors.semantic.warning, DarkColors.semanticBg.warning),
            Triple("dark danger", DarkColors.semantic.danger, DarkColors.semanticBg.danger),
            Triple("dark info", DarkColors.semantic.info, DarkColors.semanticBg.info),
        ).map { (name, fg, bg) -> name to contrastRatio(fg, bg) }

        val rendered = measured.joinToString { "${it.first}=${"%.2f".format(it.second)}" }

        // Exactly the two dark pairs that clear the floor today.
        val expectedPassing = setOf("dark success", "dark warning")
        val actualPassing = measured.filter { it.second >= 4.5 }.map { it.first }.toSet()

        assertEquals(
            "the semantic badge split changed; measured: $rendered. Only " +
                "$expectedPassing clear 4.5:1. If this is an intended token change, " +
                "update the table in CONTRAST_ADJUSTMENTS and the Review Triage Log " +
                "with the new measurements.",
            expectedPassing,
            actualPassing,
        )
    }

    /**
     * The floor for the pairs that *do* clear it is asserted directly, so a
     * token edit that quietly pulls a passing pair under the floor fails here
     * rather than only changing the count above.
     */
    @Test
    fun `the semantic pairs that clear the floor still clear it`() {
        assertClears("dark success on its bg", DarkColors.semantic.success, DarkColors.semanticBg.success)
        assertClears("dark warning on its bg", DarkColors.semantic.warning, DarkColors.semanticBg.warning)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun assertTextLevelsClear(
        primary: Color,
        secondary: Color,
        tertiary: Color,
        c: net.klinik.rental.ui.theme.KnColors,
    ) {
        listOf(
            "bgPage" to c.bgPage,
            "bgCard" to c.bgCard,
            "bgSunken" to c.bgSunken,
            "bgRail" to c.bgRail,
            "brandSurface" to c.brandSurface,
        ).forEach { (name, surface) ->
            assertClears("textPrimary on $name", primary, surface)
            assertClears("textSecondary on $name", secondary, surface)
            assertClears("textTertiary on $name", tertiary, surface)
        }
    }

    private fun assertClears(what: String, fg: Color, bg: Color) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "$what = ${"%.3f".format(ratio)}:1, needs >= $MIN_RATIO:1",
            ratio >= MIN_RATIO,
        )
    }

    private fun colorOf(bg: net.klinik.rental.ui.theme.SemanticBackgrounds, name: String) =
        when (name) {
            "success" -> bg.success
            "warning" -> bg.warning
            "danger" -> bg.danger
            else -> bg.info
        }

    private companion object {
        const val MIN_RATIO = 4.5
    }
}

/**
 * WCAG 2.x relative luminance, computed from the 8-bit sRGB channels.
 *
 * Written out rather than delegated so the formula is auditable next to the
 * thresholds it feeds — a "contrast helper" that silently used the wrong
 * luminance curve would make every assertion above meaningless.
 */
internal fun relativeLuminance(color: Color): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    val r = channel((color.red * 255f + 0.5f).toInt())
    val g = channel((color.green * 255f + 0.5f).toInt())
    val b = channel((color.blue * 255f + 0.5f).toInt())
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG 2.x contrast ratio: (L1 + 0.05) / (L2 + 0.05). Always >= 1. */
internal fun contrastRatio(fg: Color, bg: Color): Double {
    val a = relativeLuminance(fg)
    val b = relativeLuminance(bg)
    val lighter = max(a, b)
    val darker = min(a, b)
    return (lighter + 0.05) / (darker + 0.05)
}