package net.klinik.rental.ui.screens.laporan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knTypography

/**
 * The seven-day income trend, drawn with [Modifier.drawBehind].
 *
 * ## Why `drawBehind` and not a `Canvas`
 *
 * Because the epic decided not to add a chart library, and the platform already ships a
 * drawing surface — but the surface does not have to be a `Canvas` *composable*. A
 * `drawBehind` modifier draws in the same box as the content it decorates, so the trend
 * cannot end up a pixel away from the card it belongs to, and it adds no layout node for
 * the card to account for.
 *
 * ## Why not a chart library
 *
 * Because the epic decided it and the reason is worth keeping: the platform ships a drawing
 * surface, and a chart library would be the app's first runtime dependency for a screen that
 * draws two strokes. AR-15 rules the whole category out — "setiap dependency adalah sesuatu
 * yang bisa rusak sendirian" — and a line chart is about as much chart as this screen needs.
 *
 * ## Why the colour comes from the theme
 *
 * Because a second colour value is a second thing to keep in step with the first, and dark
 * theme would then be a special case rather than a consequence. `knColors.primaryFill` is the
 * app's one accent and it already has both themes resolved, so this screen cannot end up with
 * a trend that disappears at night.
 *
 * ## Why a flat line and not a filled area
 *
 * Because the area under the line is space the owner reads as a quantity, and it is not one:
 * the figures are seven daily totals with no shared baseline a reader could assume. A stroke
 * says "this happened, in this order", which is the whole claim.
 *
 * ## Why the scale is zero-based
 *
 * Because a truncated axis exaggerates: seven days of Rp 1.000.000 and Rp 1.100.000 would
 * draw the same dramatic slope as Rp 1.000.000 and Rp 9.000.000. A trend line that can
 * oversell a bad week is the wrong chart for a figure the owner will act on.
 *
 * @param points one total per day, oldest first. A day with nothing reads as zero rather
 *   than being skipped — the repository zero-fills, so a gap is a fact about the week.
 * @param description what a screen reader hears instead of a shape it cannot draw.
 */
@Composable
fun TrendLine(
    points: List<Long>,
    description: String,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
) {
    val strokeColour = MaterialTheme.knColors.primaryFill
    val gridColour = MaterialTheme.knColors.border

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TREND_HEIGHT)
            // The line is a picture, so it carries words rather than a click action — the
            // same rule every chart-like surface in this app follows.
            .semantics { contentDescription = description }
            .testTag(TAG_TREND)
            .drawBehind {
                if (points.size < 2) {
                    // One point is not a trend, and a flat line through a single day would draw a
                    // slope that never existed.
                    return@drawBehind
                }

                val largest = points.maxOrNull() ?: return@drawBehind
                val baseline = size.height
                val step = size.width / (points.size - 1).toFloat()

                // A zero total would divide the whole chart by zero, and every figure here is a
                // rupiah amount so zero is a real week rather than a missing one.
                val scale = if (largest <= 0L) 0f else baseline / largest

                val path = Path()
                points.forEachIndexed { index, total ->
                    val x = step * index
                    val y = baseline - (total.toFloat() * scale)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawLine(
                    color = gridColour,
                    start = Offset(0f, baseline),
                    end = Offset(size.width, baseline),
                    strokeWidth = 1f,
                )

                drawPath(
                    path = path,
                    color = strokeColour,
                    style = Stroke(width = STROKE_WIDTH, cap = StrokeCap.Round),
                )
            },
    ) {
        // The x-axis, as real `Text` so the dates are read, not drawn — the same shape
        // `KnLineChart` uses. Every label is drawn here rather than every other: this chart is
        // seven points across the full width, which leaves room for all of them.
        if (labels.isNotEmpty() && labels.size == points.size) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.knTypography.overline,
                        color = MaterialTheme.knColors.textTertiary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * How tall the trend draws.
 *
 * A bound rather than a wrap: the figure sits under four KPI tiles on a screen whose criterion
 * is that nothing important is pushed below the fold, so the chart takes a fixed share rather
 * than whatever height seven numbers happen to want.
 */
private val TREND_HEIGHT = 120.dp

private const val STROKE_WIDTH = 3f

internal const val TAG_TREND = "laporan-trend"