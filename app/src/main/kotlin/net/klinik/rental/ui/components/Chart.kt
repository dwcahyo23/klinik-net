package net.klinik.rental.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The dashboard's charts — `shared.css` section 11 and `dashboard.html:60-106`.
 *
 * ## Why these are a library and not four private composables on one screen
 *
 * Because they are **charts**, and a chart is the one thing in this app that cannot be described
 * in words: a line the owner reads as "up", a donut slice that reads as "a third". Once three
 * screens need one, a fourth copy is not a maintenance cost, it is a fourth definition of "what a
 * donut looks like".
 *
 * ## Why `Canvas` and not a chart dependency
 *
 * The epic's AR-15 rule — every dependency is something that can break on its own — and each of
 * these is two strokes and a few rectangles. A charting library would be the app's first runtime
 * dependency for a screen that needs no more than that.
 *
 * ## Why every chart carries a `contentDescription` and no click action
 *
 * Because a chart is a picture. A screen reader cannot draw one, so each carries the sentence it
 * stands for; and it has no tap target, because there is nothing on a chart to press.
 */

/**
 * `.chart-line` — a daily series with a dashed target line and a gradient fill.
 *
 * The prototype's line is `stroke-width:3` with round joins, an area filled with a brand
 * gradient fading to transparent, four dashed gridlines, and a dot per point (`dashboard.html:225-246`).
 *
 * @param points one value per day, oldest first. Zero-filled by the caller — a skipped day would
 *   draw a straight line through it, reading as a steady Tuesday when nothing arrived.
 * @param target an optional horizontal reference (the daily rate the active contracts bill).
 * @param labels the x-axis labels, drawn every other point when there are more than eight.
 * @param description the sentence a screen reader hears.
 */
@Composable
fun KnLineChart(
    points: List<Long>,
    description: String,
    modifier: Modifier = Modifier,
    target: Long? = null,
    labels: List<String> = emptyList(),
) {
    val stroke = MaterialTheme.knColors.primaryFill
    val grid = MaterialTheme.knColors.border
    val targetColour = MaterialTheme.knColors.borderStrong
    // Read **before** the draw scope: a `drawBehind` lambda is a draw scope, not a composable one,
    // so `MaterialTheme` cannot be read inside it — a line this easy to get wrong is why the
    // colours are hoisted rather than reached for at each use.
    val cardColour = MaterialTheme.knColors.bgCard

    // `drawBehind` rather than a `Canvas` composable: the chart is a picture drawn **on** the
    // card, and a `Canvas` is a separate composable that has to be measured and placed beside
    // the content. A `drawBehind` modifier draws in the same box, so the chart cannot drift a
    // pixel away from the card it belongs to, and it cannot add a layout node the card then has
    // to account for.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LineChartHeight)
            .semantics { contentDescription = description }
            .testTag(TAG_LINE_CHART)
            .drawBehind {
                if (points.size < 2) return@drawBehind

                // The target lifts the ceiling even when no day reached it — otherwise a week below
                // target would draw a line pinned to the top of its own range and read as a success.
                val ceiling = maxOf(points.max(), target ?: 0L).toFloat()
                val stepX = size.width / (points.size - 1).toFloat()
                val scale = if (ceiling <= 0f) 0f else size.height / ceiling

                // Four gridlines, matching `--sp` dashes in the CSS (`stroke-dasharray: 3 5`).
                repeat(4) { i ->
                    val y = size.height * (i + 1) / 5f
                    drawLine(
                        color = grid,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(
                            // `stroke-dasharray: 3 5` — the prototype's own gridline dash. The
                            // previous 6/8 read as long sparse strokes, so the background
                            // texture competed with the data line it sits behind.
                            floatArrayOf(3f, 5f),
                        ),
                    )
                }

                target?.let { value ->
                    val y = size.height - (value.toFloat() * scale)
                    drawLine(
                        color = targetColour,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(
                            // The prototype dashes the target line on the same 3/5 rhythm as the
                            // gridlines, so it reads as part of the same ruled sheet rather
                            // than as a second, heavier series.
                            floatArrayOf(3f, 5f),
                        ),
                    )
                }

                val line = Path()
                points.forEachIndexed { index, value ->
                    val x = stepX * index
                    val y = size.height - (value.toFloat() * scale)
                    if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }

                // The fill closes the path along the baseline. The gradient is a horizontal wipe
                // rather than the CSS's vertical one only because the CSS's two stops both
                // resolve to the same transparent brand, and a vertical fade on a zero-based
                // chart is invisible anyway.
                val fill = Path().apply {
                    addPath(line)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        colors = listOf(stroke.copy(alpha = 0.22f), stroke.copy(alpha = 0f)),
                    ),
                )
                drawPath(
                    path = line,
                    color = stroke,
                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                )

                // A dot per point, the prototype's `r=4` circle with a card-coloured fill.
                points.forEachIndexed { index, value ->
                    drawCircle(
                        color = cardColour,
                        radius = 4f,
                        center = Offset(stepX * index, size.height - (value.toFloat() * scale)),
                    )
                    drawCircle(
                        color = stroke,
                        radius = 4f,
                        center = Offset(stepX * index, size.height - (value.toFloat() * scale)),
                        style = Stroke(width = 2.5f),
                    )
                }
            },
    )
}

/** `.chart-line { height: 210px }` — `shared.css:763`. */
private val LineChartHeight = 210.dp

/**
 * `.bars` — a row of vertical bars with a value above and a label below.
 *
 * The prototype's bars are `max-width:46px`, rounded 8px at the top and 4px at the bottom, and
 * coloured `--brand-300` unless they missed the target (`dashboard.html:270-273`).
 *
 * @param bars the columns, left to right. An empty list draws nothing — a chart with no data is
 *   not a chart of zeroes.
 * @param target the value a bar must reach to be drawn in the full brand colour.
 * @param description the sentence a screen reader hears, so the shape is not the only carrier.
 */
@Composable
fun KnBarChart(
    bars: List<KnBar>,
    description: String,
    modifier: Modifier = Modifier,
    target: Long = 0L,
) {
    if (bars.isEmpty()) return
    val spacing = MaterialTheme.knSpacing
    val ceiling = maxOf(bars.maxOf { it.value }, target).coerceAtLeast(1L).toFloat()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .testTag(TAG_BAR_CHART),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarChartHeight),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bar ->
                val fraction = bar.value.toFloat() / ceiling
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                ) {
                    Text(
                        text = bar.valueText,
                        style = MaterialTheme.knTypography.overline.copy(fontSize = 12.sp),
                        color = MaterialTheme.knColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    // A minimum of 5px so a zero-value bar still reads as a bar rather than as a
                    // gap in the row — the CSS's `.bar { min-height: 5px }`.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maxOf(BarChartHeight * fraction, MinBarHeight))
                            .clip(BarShape)
                            .background(
                                if (bar.value >= target) {
                                    // `--brand-300`, the bar that met its target.
                                    MaterialTheme.knColors.brand300
                                } else {
                                    // `--warm-300`, the bar that fell short. A muted grey
                                    // beside a soft pink says "behind" without a legend.
                                    MaterialTheme.knColors.warm300
                                },
                            )
                            .testTag(TAG_BAR_PREFIX + bar.label),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = MaterialTheme.knTypography.overline.copy(fontSize = 11.sp),
                    color = MaterialTheme.knColors.textTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One bar: its value, its label, and whether it beat the target. */
data class KnBar(
    val label: String,
    val value: Long,
    /** Already formatted — the same one formatter the tables use, applied by the caller. */
    val valueText: String,
)

/** `.bars { height: 190px }` — `shared.css:768`. */
private val BarChartHeight = 190.dp

/** `.bar { min-height: 5px }` — `shared.css:772`. */
private val MinBarHeight = 5.dp

/** `.bar { border-radius: 8px 8px 4px 4px }` — `shared.css:770`. */
private val BarShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomEnd = 4.dp, bottomStart = 4.dp)

/**
 * `.donut` — a share-of-total ring with the total in the hole.
 *
 * ## Why a sweep gradient and not arcs
 *
 * Because the prototype draws this ring as a `conic-gradient` on a `border-radius: 50%`
 * div (`dashboard.html:311`, `shared.css:777`) — a **filled circle** with a card-coloured
 * disc laid over its middle, not a stroked ring. The old implementation drew stroked arcs
 * on a `Canvas`, which is a different shape: a stroked ring leaves the outer edge of the
 * stroke square where the circle is not, and it has to fake the hole with a second disc.
 *
 * So the ring is now a `Brush.sweepGradient` on a circle, with the same card-coloured disc
 * over the middle that the CSS's `.donut-center` is. The gradient's stops are the slices'
 * own arcs, in the same order and with the same 3° gap the arcs had.
 *
 * @param slices largest first; an empty list draws the sunken placeholder rather than a full ring,
 *   because a complete ring with no data reads as "everything is one thing".
 * @param centreText the figure in the hole — already formatted.
 * @param centreLabel the word under it.
 * @param description the sentence a screen reader hears.
 */
@Composable
fun KnDonutChart(
    slices: List<KnSlice>,
    centreText: String,
    centreLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    val sunken = MaterialTheme.knColors.bgSunken
    val total = slices.sumOf { it.value }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .testTag(TAG_DONUT),
        horizontalArrangement = Arrangement.spacedBy(spacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(DonutSize), contentAlignment = Alignment.Center) {
            if (total <= 0L) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(sunken),
                )
            } else {
                // The slices' arcs as gradient stops. The sweep starts at 12 o'clock, which is
                // what `conic-gradient` does by default and what the arcs did with their
                // `-90f` start.
                val stops = buildList {
                    var start = 0f
                    slices.forEach { slice ->
                        val sweep = (slice.value.toFloat() / total.toFloat()) * 360f
                        // A stop is an `offset to colour` pair, and the transparent copies
                        // are the slice's own boundaries — the gradient goes from this
                        // slice's colour to nothing and back to the next slice's colour.
                        add(start to slice.colour.copy(alpha = 0f))
                        add(start to slice.colour)
                        add((start + sweep - DonutSliceGap).coerceAtLeast(start) to slice.colour)
                        add((start + sweep).coerceAtLeast(start) to slice.colour.copy(alpha = 0f))
                        start += sweep
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Brush.sweepGradient(*stops.toTypedArray())),
                )
            }
            // The hole is a card-coloured disc over the middle — the CSS's `.donut-center`
            // is `position:absolute; inset: 26%` with the card's own background, so the
            // ring's thickness is whatever the inset leaves.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(DonutHolePadding)
                    .clip(CircleShape)
                    .background(MaterialTheme.knColors.bgCard),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centreText,
                    // `.donut-center .dc-val` is `--fs-lg` weight 800 with `tabular-nums`.
                    style = MaterialTheme.knTypography.figureLarge,
                    color = MaterialTheme.knColors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = centreLabel.uppercase(),
                    style = MaterialTheme.knTypography.overline.copy(
                        fontSize = 11.sp,
                        // `.dc-lbl { letter-spacing: .5px }`
                        letterSpacing = 0.5.sp,
                    ),
                    color = MaterialTheme.knColors.textTertiary,
                    maxLines = 1,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            slices.take(DonutLegendLimit).forEach { slice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_DONUT_LEGEND_PREFIX + slice.label),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(slice.colour),
                        )
                        Text(
                            text = slice.label,
                            style = MaterialTheme.knTypography.figure,
                            color = MaterialTheme.knColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = slice.valueText,
                        style = MaterialTheme.knTypography.overline.copy(fontSize = 12.sp),
                        color = MaterialTheme.knColors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** One slice of the donut. */
data class KnSlice(
    val label: String,
    val value: Long,
    /** Already formatted. */
    val valueText: String,
    /** From [DonutPalette], in order. */
    val colour: Color,
)

/** `.donut { width: 170px; height: 170px }` — `shared.css:777`. */
private val DonutSize = 170.dp

/** The gap between slices, in degrees. The prototype leaves none; two touching arcs read as one. */
private const val DonutSliceGap = 1.5f

/**
 * `.donut-center { inset: 26% }` — the hole, as a fraction of the diameter.
 *
 * A fraction rather than a `Dp` because the ring is drawn at whatever size the caller gives it,
 * and a fixed inset would be right at 170dp and wrong at every other size.
 */
private const val DonutHoleFraction = 0.26f

/** The ring's thickness, so the hole's padding can be derived from it. */
private val DonutHolePadding = DonutSize * DonutHoleFraction

/** `entries.slice(0, 6)` — the prototype shows six legend rows (`dashboard.html:313`). */
private const val DonutLegendLimit = 6

/**
 * `dashboard.html:292` — the eight-colour categorical palette.
 *
 * Taken from the prototype's own list and ordered by size, so the biggest slice is always the
 * first colour. Brand hues first, then the three semantics, then the neutral and the palest
 * brand — which is what makes a slice that is neither "ours" nor "a problem" still readable.
 */
@Composable
fun donutPaletteColour(index: Int): Color {
    val kn = MaterialTheme.knColors
    // The prototype's eight-colour donut ramp, in order (`dashboard.html:292`). The two
    // brand steps in it are the ones that carry the chart: `--brand-500` and
    // `--brand-300`. The old second slot was `--brand-50`, a tint so close to the card
    // that the slice measured 1.10:1 against it — invisible. `brand300` is the same
    // ramp the bar chart's "met target" bar uses, so the two charts agree.
    val palette = listOf(
        kn.primaryFill,
        kn.brand300,
        kn.semantic.warning,
        kn.semantic.success,
        kn.semantic.info,
        kn.textTertiary,
        kn.border,
        kn.warm300,
    )
    return palette[index % palette.size]
}

/**
 * `.progress` — a filled bar with a success/warning/danger tone.
 *
 * `shared.css:787-792`. Used by the arrears detail's "kewajiban vs diterima" bar
 * (`arrears.html:207-216`), which is the only place the prototype draws one.
 *
 * @param fraction 0..1. Values outside are clamped rather than throwing: a figure the caller
 *   computed wrong should draw a full or empty bar, not crash a screen the owner is reading.
 */
@Composable
fun KnProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    tone: KnBadgeTone = KnBadgeTone.Brand,
) {
    val kn = MaterialTheme.knColors
    val fill = when (tone) {
        KnBadgeTone.Success -> kn.semantic.success
        KnBadgeTone.Warning -> kn.semantic.warning
        KnBadgeTone.Danger -> kn.semantic.danger
        KnBadgeTone.Info -> kn.semantic.info
        KnBadgeTone.Brand -> kn.primaryFill
        KnBadgeTone.Neutral -> kn.textTertiary
    }
    val clamped = fraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ProgressHeight)
            .clip(RoundedCornerShape(MaterialTheme.knRadii.full))
            .background(kn.bgSunken)
            .testTag(TAG_PROGRESS),
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .background(fill),
        )
    }
}

/** `.progress { height: 10px }` — `shared.css:787`. */
private val ProgressHeight = 10.dp

/** Tags, so a test can address a chart without addressing its shape. */
internal const val TAG_LINE_CHART = "chart-line"
internal const val TAG_BAR_CHART = "chart-bars"
internal const val TAG_BAR_PREFIX = "chart-bar-"
internal const val TAG_DONUT = "chart-donut"
internal const val TAG_DONUT_LEGEND_PREFIX = "chart-donut-legend-"
internal const val TAG_PROGRESS = "chart-progress"