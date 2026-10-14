package net.klinik.rental.ui.theme

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
) {
    companion object {
        val Clinic = KnAccessibility(
            minTouchTarget = 48.dp,
            focusRingWidth = 3.dp,
            focusRingColor = Brand500,
            focusRingOffset = 2.dp,
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
 */
fun Modifier.minTouchTarget(): Modifier = this.defaultMinSize(
    minWidth = KnAccessibility.Clinic.minTouchTarget,
    minHeight = KnAccessibility.Clinic.minTouchTarget,
)

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
 * @param cornerRadius the element's own corner radius, so the ring follows a
 *   pill button's rounding instead of boxing it in a rounded rectangle.
 */
@Composable
fun Modifier.focusRing(
    focused: Boolean,
    cornerRadius: Dp = KnRadii.Clinic.sm,
): Modifier {
    if (!focused) return this
    val a11y = MaterialTheme.knAccessibility
    return this.drawWithContent {
        drawContent()
        // A stroke straddles its path, so inset by half the width to sit fully
        // inside the bounds instead of half-clipped.
        val stroke = a11y.focusRingWidth.toPx()
        val radius = cornerRadius.toPx()
        drawRoundRect(
            color = a11y.focusRingColor,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = stroke),
        )
    }
}
