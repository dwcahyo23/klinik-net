package net.klinik.rental.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knShadow
import net.klinik.rental.ui.theme.knElevation
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/**
 * `.card` from `shared.css` — the app's one card surface.
 *
 * Radius 20, `--el-1`, `--sp-5` padding.
 *
 * ## Why this is the only card
 *
 * Because the app had three: this one, a second `Surface` inside `KnKpiTile`, and a
 * third inside `KnKpiTileSkeleton` — each re-implementing the same radius, shadow and
 * background, and each free to drift. The prototype has one (`.card`), so this is it:
 * the KPI tile and its skeleton are **this**, with the tile's two extras switched on.
 *
 * ## Why the padding moved inside
 *
 * The CSS is `.card { background: var(--bg-card); padding: var(--sp-5) }` — the
 * background covers the whole card and the padding is *inside* it. This used to apply
 * the padding to the `Surface`'s own modifier, which shrank the painted card and left
 * a transparent gutter around it: the card was 40dp narrower than the space it was
 * given, and the gutter showed the page through. The padding now sits on the content,
 * inside the painted surface, so the card fills its slot the way the prototype's does.
 *
 * @param onClick when non-null the card becomes a clickable surface. Clickable
 *   cards are not the default: a card that *looks* tappable but is not is a
 *   gesture-only path to an important action, which the epic context forbids.
 * @param accent the 5dp brand edge down the left side — `.kpi-accent::after`
 *   (`shared.css:430-433`). Drawn over the surface rather than laid out beside it,
 *   so it spans the card's full height whatever the content's height is.
 * @param icon the 40dp corner glyph — `.kpi-icon` (`shared.css:421-426`), which
 *   the prototype pins to the card's top-right corner at 16px.
 */
@Composable
fun KnCard(
    modifier: Modifier = Modifier,
    padding: Dp = MaterialTheme.knSpacing.xl,
    outlined: Boolean = false,
    onClick: (() -> Unit)? = null,
    accent: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    content: @Composable () -> Unit,
) {
    val kn = MaterialTheme.knColors
    val shape = RoundedCornerShape(MaterialTheme.knRadii.lg)
    val elevation = if (outlined) 0.dp else MaterialTheme.knElevation.level1
    // The ring and the shadow wrap the *card*, so they are applied before the content
    // padding. Appended after it, both would size to the padded content rect and the
    // ring would float inside the card's own edge rather than trace it.
    val base = modifier
        .fillMaxWidth()
        .knShadow(elevation, shape)
        .then(
            if (accent) {
                // The colour is read **here**, outside the draw scope: a `drawBehind` lambda
                // is a draw scope, not a composable one, so `MaterialTheme` cannot be read
                // inside it.
                val accentColour = kn.primaryFill
                Modifier.drawBehind {
                    drawRect(
                        color = accentColour,
                        size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height),
                    )
                }
            } else {
                Modifier
            },
        )
    val border = if (outlined) androidx.compose.foundation.BorderStroke(1.dp, kn.border) else null

    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Surface(
            onClick = onClick,
            // A card carrying little content is only `padding` tall — 40dp at
            // the default spacing — so a clickable card would otherwise be an
            // interactive node below the 48dp floor. The floor is applied here
            // rather than left to the caller.
            modifier = base
                .minTouchTarget()
                .focusRing(focused, MaterialTheme.knRadii.lg),
            shape = shape,
            color = kn.bgCard,
            contentColor = kn.textPrimary,
            border = border,
            interactionSource = interaction,
            content = { CardContent(padding, icon, iconTint, content) },
        )
    } else {
        Surface(
            modifier = base,
            shape = shape,
            color = kn.bgCard,
            contentColor = kn.textPrimary,
            border = border,
            content = { CardContent(padding, icon, iconTint, content) },
        )
    }
}

/**
 * A card's body: the content, inset by the card's padding, plus the corner glyph
 * when there is one.
 *
 * The glyph is **absolute** — pinned to the card's top-right corner — because the
 * prototype's is (`position:absolute; top:16px; right:16px`, `shared.css:421-426`)
 * and because a glyph in the flow would push the content sideways instead of
 * sitting over the card's corner.
 */
@Composable
private fun CardContent(
    padding: Dp,
    icon: ImageVector?,
    iconTint: Color?,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(padding)) { content() }
        if (icon != null) {
            KpiIcon(
                icon = icon,
                tint = iconTint,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(KpiCornerIconGap),
            )
        }
    }
}

/** `.card-header` — title on the leading side, optional trailing slot. */
@Composable
fun KnCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.knTypography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.knColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.knTypography.body,
                    color = MaterialTheme.knColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(MaterialTheme.knSpacing.md))
            trailing()
        }
    }
}

/**
 * `.kpi` from `shared.css` — the tile that carries a headline number.
 *
 * [value] arrives pre-formatted. This component deliberately does not own a
 * money formatter: rupiah comes from `formatRupiah` at the call site and a count
 * comes as a count, which keeps one formatter in the app instead of one per
 * tile. The tile applies [net.klinik.rental.ui.theme.TABULAR_FIGURES] via
 * [MaterialTheme.knTypography.moneyLarge so columns of figures align.
 *
 * ## Why the figure is `moneyLarge` and not `displayMedium`
 *
 * Because a number is a number, and a figure has to line up in a column.
 *
 * `moneyLarge` is the prototype's own `.kpi-value` numbers, read off: `--fs-2xl`
 * 36, `font-weight: 800`, `letter-spacing: -1.2px`, `font-variant-numeric:
 * tabular-nums`.
 *
 * ## Why every line here is one line long
 *
 * Because a tile that wraps is a tile that grows, and a row of tiles that
 * disagrees about its own height is a row that reads as broken. The Tunggakan
 * summary is the case that caught it: *"%1$d penyewa dari %2$d kontrak"* is a
 * long sentence in a 14sp body face, and in a 230dp tile it wrapped to two lines
 * and stretched that one tile past its three neighbours.
 *
 * So the label, the value and the meta each get `maxLines = 1` and an ellipsis.
 * A caption too long for its tile is now **shortened by the renderer** instead
 * of **wrapped by the layout**, and a caller who wants a shorter caption
 * simply passes a shorter one.
 */
@Composable
fun KnKpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    meta: String? = null,
    metaTint: Color? = null,
    /** The 5dp brand edge from `.kpi-accent::after`. */
    accent: Boolean = false,
    /**
     * Where the glyph goes.
     *
     * [KnKpiIconLayout.Stacked] is this component's original shape and stays the default, so
     * Dashboard and Laporan do not move. [KnKpiIconLayout.Corner] is the prototype's `.kpi`, whose
     * `.kpi-icon` is `position:absolute; top:16px; right:16px` (`shared.css:421-426`) — the
     * card the Mobil and Tunggakan summaries are built from.
     */
    iconLayout: KnKpiIconLayout = KnKpiIconLayout.Stacked,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val corner = iconLayout == KnKpiIconLayout.Corner
    // `String.uppercase()` takes a `java.util.Locale`; Compose exposes its own locale
    // type, so it is bridged rather than silently defaulting to ROOT — the same
    // bridge `KnTableHeaderRow` uses.
    val locale = ComposeLocale.current.toLanguageTag()
        .let { java.util.Locale.forLanguageTag(it) }

    KnCard(
        modifier = modifier,
        accent = accent,
        // The glyph is the card's corner glyph, so the card owns it — that is the
        // whole reason the card and the tile are one component.
        icon = icon.takeIf { corner },
        iconTint = iconTint,
    ) {
        Column(
            // `weight(1f)`: the column takes the space the card gives it and the
            // text inside it may shrink. Without it a long caption is measured
            // at its natural width and the card grows instead of the text.
            modifier = if (corner) {
                // The trailing inset for the **corner glyph**. The label is a single line
                // and the glyph is pinned to the card's top-right at 16dp, so without this
                // the two overlap: "PERIODE CUT OFF BERJALAN" ran straight under the
                // calendar. The old stacked layout had this inset and the corner layout
                // lost it when the two became one component.
                //
                // The label ellipses instead, so a long label shortens rather than
                // colliding — the same rule the value and the meta already follow.
                Modifier
                    .fillMaxWidth()
                    .padding(end = KpiCornerIconSize + KpiCornerIconGap)
            } else {
                Modifier
            },
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (icon != null && !corner) {
                KpiIcon(icon = icon, tint = iconTint)
            }
            Text(
                text = label.uppercase(locale),
                style = MaterialTheme.knTypography.overline,
                color = kn.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.knTypography.moneyLarge,
                color = kn.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.knTypography.body,
                    color = metaTint ?: kn.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Where a KPI tile draws its glyph. See [KnKpiTile.iconLayout]. */
enum class KnKpiIconLayout { Stacked, Corner }

/**
 * A [KnKpiTile]'s placeholder, drawn at the size the loaded tile will take.
 *
 * ## Why this mirrors the tile rather than carrying a height
 *
 * Because a tile's height **is** its content's: `spacing.xl` of inset, then the label, the
 * value and the optional meta at their own line heights — and, in [KnKpiIconLayout.Stacked],
 * a 40dp glyph above them. A skeleton with a fixed dp is a number that was right once and
 * then drifted: `Beranda` and `Laporan` both shipped `height(116.dp)` beside a tile that no
 * longer measures 116dp, so the row reflowed the moment the figures landed.
 *
 * So this composes the same inset and the same line heights. Change the tile's typography and
 * the skeleton follows without an edit; the paired test measures both and fails if they part.
 *
 * The bars are decorative, so they carry no content description — the enclosing state is what
 * a screen reader announces, and `KnSkeletonList` follows the same rule.
 *
 * @param iconLayout which of [KnKpiTile]'s two shapes to reserve. A stacked tile is taller by
 *   the glyph and its gap; a corner tile's glyph is pinned over the content and adds nothing.
 */
@Composable
fun KnKpiTileSkeleton(
    modifier: Modifier = Modifier,
    iconLayout: KnKpiIconLayout = KnKpiIconLayout.Stacked,
) {
    val spacing = MaterialTheme.knSpacing
    val typography = MaterialTheme.knTypography
    val kn = MaterialTheme.knColors
    val barShape = RoundedCornerShape(MaterialTheme.knRadii.sm)
    val corner = iconLayout == KnKpiIconLayout.Corner

    // A `Text` of one space in the tile's own style, rather than a `Box` of the style's
    // `lineHeight`: a real `Text` carries the same font padding the real label does, and
    // computing a height from `lineHeight` alone is short by exactly that padding (measured:
    // 10dp, which is how this was caught). The space is transparent; only its background bar
    // is visible, so nothing is read out.
    @Composable
    fun bar(widthFraction: Float, style: TextStyle, topPadding: Dp = 0.dp) {
        Text(
            text = " ",
            style = style,
            color = Color.Transparent,
            modifier = Modifier
                .padding(top = topPadding)
                .fillMaxWidth(widthFraction)
                .background(kn.bgSunken, barShape),
        )
    }

    KnCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (!corner) {
                // `.kpi-icon`: 40dp, with no gap of its own — the label's line box is
                // what separates it from the text below, exactly as in the tile.
                Box(
                    Modifier
                        .size(40.dp)
                        .background(kn.bgSunken, RoundedCornerShape(MaterialTheme.knRadii.md)),
                )
            }
            bar(widthFraction = 0.55f, style = typography.overline)
            bar(
                widthFraction = 0.8f,
                style = typography.moneyLarge,
            )
            // The **meta** bar, because every dashboard tile has one (`dashboard.html:174-194`)
            // and the tile is taller for it. A skeleton that reserved only label and value
            // would be 43dp short of the tile it stands in for, and the row would jump the
            // moment the figures landed — the defect this function's whole design is against.
            bar(widthFraction = 0.65f, style = typography.body)
        }
    }
}

/** `.kpi-icon` — a 40dp rounded square, 20dp glyph, the tint at 12% behind it. */
@Composable
private fun KpiIcon(icon: ImageVector, tint: Color?, modifier: Modifier = Modifier) {
    val iconColour = tint ?: MaterialTheme.knColors.semantic.info
    Surface(
        modifier = modifier.size(KpiCornerIconSize),
        shape = RoundedCornerShape(MaterialTheme.knRadii.md),
        color = iconColour.copy(alpha = 0.12f),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColour,
            modifier = Modifier.padding(9.dp),
        )
    }
}

/**
 * The corner glyph's size — `.kpi-icon { width: 40px; height: 40px }`
 * (`shared.css:421-426`).
 */
private val KpiCornerIconSize = 40.dp

/**
 * The gap between the text column and the corner glyph.
 *
 * The CSS puts the glyph at `right: 16px` (`shared.css:422`), and the text needs the same
 * 16dp of clearance so the two do not touch. A long label ellipses in the space that is
 * left.
 */
private val KpiCornerIconGap = 16.dp

/**
 * `.kpi-grid` from `shared.css:397-401` — `repeat(auto-fit, minmax(230px, 1fr))`.
 *
 * ## Why plain rows rather than a `FlowRow`
 *
 * Because `Modifier.weight(1f)` inside a `FlowRow` is not a main-axis weight. A wrapping row is
 * measured with an unbounded height, its weighted children absorb it, and the grid grows to fill
 * whatever height it is offered — leaving the rows above and below it with none. This is the
 * defect `deferred-work.md` records for `Beranda`; reproducing it in three new places would be
 * the same bug three more times.
 *
 * So this lays out plain `Row`s: four across when four 230dp tiles fit, two by two otherwise —
 * the same answer `auto-fit` gives — and every tile takes an equal share of its line, which is a
 * main-axis weight that actually means something. The `Box` around each tile carries the weight,
 * so a tile stays a plain composable that knows nothing about its neighbours.
 *
 * The tiles arrive as a **list**, not as a content lambda. A lambda that records composables and
 * a lambda that emits them are two things nothing forces a caller to tell apart, and the
 * recording version silently draws nothing.
 */
@Composable
fun KnKpiGrid(
    modifier: Modifier = Modifier,
    tiles: List<@Composable () -> Unit>,
) {
    val gap = MaterialTheme.knSpacing.lg
    // 4 x 230 + 3 x 16 (`--sp-4`), the `auto-fit` threshold at four columns.
    // `Dp * Int`, not `Int * Dp`: only the first is an operator on `Dp`.
    val fourAcross = KpiMinTileWidth * 4 + gap * 3

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= fourAcross) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            tiles.chunked(perRow).forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    line.forEach { tile ->
                        // The `Box` carries the weight, not the tile: the tile is a plain
                        // composable and has no business knowing how many siblings it has.
                        Box(Modifier.weight(1f)) { tile() }
                    }
                }
            }
        }
    }
}

/** `minmax(230px, 1fr)` from `shared.css:399`. */
private val KpiMinTileWidth = 230.dp
