package net.klinik.rental.ui.theme

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * Accessibility constants, as tokens so they can be asserted.
 *
 * `shared.css` line 83 sets `--tap-min: 48px` and lines 137-141 set
 * `outline: 3px solid var(--brand-500)`. Both are load-bearing, and both are
 * measured in `AccessibilityTest` rather than trusted to review.
 */
@Immutable
data class KnAccessibility(
    /** `--tap-min`. The floor for every interactive node, on either axis. */
    val minTouchTarget: Dp,
    /** The `:focus-visible` outline width. */
    val focusRingWidth: Dp,
    /** The `:focus-visible` outline colour. */
    val focusRingColor: Color,
    /** `outline-offset: 2px`. */
    val focusRingOffset: Dp,
    /**
     * The brand glow around a focused field — `0 0 0 4px var(--brand-50)`.
     *
     * The prototype's `.input:focus` carries two things: a `border-color` change *and*
     * a 4px brand halo. The border change is what this library already drew; the halo
     * was the part that made a focused field lift off the page rather than merely
     * change colour, and it is the one thing a screen reads as "polished" before it
     * reads as "correct".
     *
     * So the field draws the halo itself, from a token, rather than the focus ring
     * growing — the ring is a 3dp outline on *every* focusable element, and widening
     * it to a 4dp glow on fields alone would make the ring's width mean two things.
     */
    val focusGlowWidth: Dp,
    /** The halo's colour: `--brand-50` in light, the dark block's `--brand-50` in dark. */
    val focusGlowColor: Color,
) {
    companion object {
        val Clinic = KnAccessibility(
            minTouchTarget = 48.dp,
            focusRingWidth = 3.dp,
            focusRingColor = Brand500,
            focusRingOffset = 2.dp,
            focusGlowWidth = 4.dp,
            focusGlowColor = Brand50Light,
        )
    }
}

internal val LocalKnAccessibility = staticCompositionLocalOf { KnAccessibility.Clinic }

val MaterialTheme.knAccessibility: KnAccessibility
    @Composable
    @ReadOnlyComposable
    get() = LocalKnAccessibility.current

/** Convenience: the 48dp floor, for the many places that need only this. */
val MaterialTheme.minTouchTarget: Dp
    @Composable
    @ReadOnlyComposable
    get() = LocalKnAccessibility.current.minTouchTarget

/**
 * The focus ring's geometry and colour, read from [LocalKnAccessibility].
 *
 * There is no separate top-level constant for the ring width: it lives in
 * [KnAccessibility.focusRingWidth] alongside the 48dp floor, because both are
 * accessibility thresholds and both are provided by `KlinikNetTheme`. A second
 * exported copy would be a second source of truth that nothing reads.
 */

/**
 * Guarantees at least 48dp on both axes.
 *
 * Applied inside every interactive component rather than at the call site, so a
 * screen cannot opt out by forgetting. A chip whose label is short still gets a
 * 48dp hit area even though its visual box is 26dp tall.
 *
 * Reads the floor from [MaterialTheme.knAccessibility] rather than from
 * [KnAccessibility.Clinic] directly, so a value provided by `KlinikNetTheme` is
 * the one actually applied. A static read here would make the theme's provider
 * change no interactive node at all — the token would look configurable and not
 * be.
 */
@Composable
fun Modifier.minTouchTarget(): Modifier {
    val floor = MaterialTheme.knAccessibility.minTouchTarget
    return this.defaultMinSize(minWidth = floor, minHeight = floor)
}

/**
 * Draws the brand-coloured focus ring **on top of** the element it decorates.
 *
 * ## Why `drawWithContent` and not `drawBehind`
 *
 * `drawBehind` appends its draw step to the chain it is applied to, so on a
 * component that paints its own background *later* — which is exactly what
 * `Modifier.background()` and Material 3's `Surface` do — the opaque container
 * paints over the ring and hides it completely. `Modifier.border` has the same
 * problem, because it is a draw step in the same position. Measured on an opaque
 * primary button before this was changed: zero brand pixels on either side of
 * the focus transition, because the fill covered 100% of the ring.
 *
 * `drawWithContent` is defined the other way round — `drawContent()` paints
 * everything downstream, and the ring goes down afterwards. That holds whatever
 * the element puts in front of itself, which is why it survives being applied
 * to a `Surface` modifier.
 *
 * `FocusRingTest` renders an opaque primary button, focuses it and reads the
 * pixels back, so a regression here cannot pass quietly.
 *
 * ## Why not Material's own focus indicator
 *
 * The CSS ring is a solid 3px outline (`shared.css` 137-141). Material 3's
 * default indication is a translucent overlay whose opacity the system scales
 * with the active theme; on a warm palette that reads as a smudge rather than
 * as a boundary, and its geometry is not ours to state.
 *
 * ## Why inside the bounds rather than offset outward
 *
 * The CSS puts the ring 2px *outside* the element. A Compose ring drawn outside
 * its own layout bounds is clipped by whatever container holds the element, so a
 * focused control at the edge of a screen or at the edge of a scrolling row
 * would show half a ring. The ring is inset by half its stroke width instead,
 * which keeps it whole at any position.
 *
 * ## Why the colour is a parameter
 *
 * The brand ring is legible against a page, a card or the sunken surfaces — those
 * are all light or all dark. It is *not* legible against the saturated fills: the
 * primary fill is `--brand-500` darkened for white-label contrast, and the ring
 * measured 1.3:1 against it, i.e. effectively invisible on exactly the button a
 * keyboard user is most likely to be on. A filled variant therefore passes the
 * inverse ink, which clears 3:1 on all four fills.
 *
 * @param cornerRadius the element's own corner radius, so the ring follows a
 *   pill button's rounding instead of boxing it in a rounded rectangle.
 * @param color the ring colour. Defaults to the token; pass the element's own
 *   content ink when the element sits on a saturated fill.
 */
@Composable
fun Modifier.focusRing(
    focused: Boolean,
    cornerRadius: Dp = MaterialTheme.knRadii.sm,
    color: Color? = null,
): Modifier {
    if (!focused) return this
    val a11y = MaterialTheme.knAccessibility
    val ringColor = color ?: a11y.focusRingColor
    return this.drawWithContent {
        drawContent()
        // A stroke straddles its path, so inset by half the width to sit fully
        // inside the bounds instead of half-clipped.
        val stroke = a11y.focusRingWidth.toPx()
        val radius = cornerRadius.toPx()
        // Not every node carrying this modifier has `minTouchTarget` applied —
        // a ring is useful on a decorative-but-focusable node too. `Size`
        // rejects negative components, so a node narrower than the ring would
        // throw inside the draw pass rather than merely look odd. Clamped to
        // zero: the ring collapses to a line on such a node instead of taking
        // the frame down.
        val width = (size.width - stroke).coerceAtLeast(0f)
        val height = (size.height - stroke).coerceAtLeast(0f)
        if (width <= 0f || height <= 0f) return@drawWithContent
        drawRoundRect(
            color = ringColor,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = stroke),
        )
    }
}

/**
 * The brand halo a focused field wears.
 *
 * The prototype's `.input:focus { box-shadow: 0 0 0 4px var(--brand-50) }` is a **spread**
 * shadow: it extends past the field's edge, in a flat colour, with no blur and no offset.
 *
 * A `drawWithContent` cannot draw that. It paints the node's content *first*, so a halo
 * drawn in its lambda lands on top of the field, and a shape drawn beyond the node's own
 * bounds is clipped away. So the halo is a real `shadow` instead: a small elevation with a
 * brand-tinted ambient and spot colour. Compose blurs and offsets it rather than spreading
 * it flat, which is not the same picture — but it is the same *idea*, it survives being
 * drawn inside the node's own bounds, and it costs no layout shift. A focus that pushed the
 * field's neighbours by four pixels would be worse than the missing halo.
 *
 * The colour is the token's, so a field in the dark scheme glows in the dark scheme's
 * brand step rather than in light-theme pink.
 */
@Composable
fun Modifier.focusGlow(
    cornerRadius: Dp = MaterialTheme.knRadii.md,
    color: Color? = null,
): Modifier {
    val a11y = MaterialTheme.knAccessibility
    val glowColor = color ?: a11y.focusGlowColor
    return this.shadow(
        elevation = a11y.focusGlowWidth,
        shape = RoundedCornerShape(cornerRadius),
        clip = false,
        ambientColor = glowColor,
        spotColor = glowColor,
    )
}
