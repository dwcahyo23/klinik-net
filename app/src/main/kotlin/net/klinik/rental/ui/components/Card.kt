package net.klinik.rental.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
 * `.card` from `shared.css` — radius 20, `--el-1`, `--sp-5` padding.
 *
 * Presentation only. Nothing here computes arrears, risk tier, credit or
 * status; a card renders what it is handed.
 *
 * ## Why the card fills its parent's width
 *
 * Because a card that sized to its own longest child is the defect `PengaturanScreen`
 * shipped: its four section surfaces rendered at roughly 45% and 100% of the page on the
 * same screen, because a `Surface` with no width modifier wraps its content and the
 * content happened to differ. A card is a page section, not an inline chip, so
 * [Modifier.fillMaxWidth] belongs to the component rather than to each call site — and
 * putting it here means a screen cannot forget, which is the failure this replaces.
 *
 * A caller that genuinely wants a narrower card constrains the *parent*, not this: the
 * modifier is applied before [padding] so it sizes the outer box the shadow traces, not
 * the padded content rect.
 *
 * @param onClick when non-null the card becomes a clickable surface. Clickable
 *   cards are not the default: a card that *looks* tappable but is not is a
 *   gesture-only path to an important action, which the epic context forbids.
 */
@Composable
fun KnCard(
    modifier: Modifier = Modifier,
    padding: Dp = MaterialTheme.knSpacing.xl,
    outlined: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val kn = MaterialTheme.knColors
    val shape = RoundedCornerShape(MaterialTheme.knRadii.lg)
    val elevation = if (outlined) 0.dp else MaterialTheme.knElevation.level1
    // The ring and the shadow wrap the *card*, so they are applied before the
    // content padding. Appended after it, both would size to the padded content
    // rect and the ring would float inside the card's own edge rather than trace
    // it — correct-looking until you focus a card and see the 3dp line sitting
    // one padding-width in from the border.
    val base = modifier
        .fillMaxWidth()
        .knShadow(elevation, shape)
        .padding(padding)
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
            content = { content() },
        )
    } else {
        Surface(
            modifier = base,
            shape = shape,
            color = kn.bgCard,
            contentColor = kn.textPrimary,
            border = border,
            content = { content() },
        )
    }
}

/**
 * The widest a screen's content column is allowed to be.
 *
 * ## Why this exists
 *
 * The prototype has no max-width, and the reason is in its frame: `shared.css` was only
 * ever previewed at 1280x800, where a full-width card is already a comfortable measure. On
 * the audited 711dp tablet a full-width card puts a single sentence 700dp long on screen —
 * long enough that the eye loses the line on the return sweep, which is the one thing a
 * reading column is supposed to prevent.
 *
 * 840dp rather than a token, because it is not in the spacing scale and adding one would be
 * pretending it is one of the four-pixels-per-step sizes it is not. It lives here, beside
 * [KnCard], rather than in a screen because the cap and [KnCard]'s [Modifier.fillMaxWidth]
 * are the same decision seen from two ends: the card fills whatever the column gives it,
 * and the column refuses to give more than this. Declared `internal` so a screen reads a
 * named constant instead of repeating `840.dp`.
 *
 * A screen opts in by wrapping its body in
 * `Modifier.widthIn(max = KnContentMaxWidth).align(Alignment.CenterHorizontally)` — the
 * width modifier first, then `fillMaxWidth`, because in the other order the fill has
 * already fixed the width before the cap can narrow it.
 */
internal val KnContentMaxWidth = 840.dp

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
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.knTypography.body,
                    color = MaterialTheme.knColors.textTertiary,
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
 * [MaterialTheme.knTypography].displayMedium so columns of figures align.
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
     * Beranda and Laporan do not move. [KnKpiIconLayout.Corner] is the prototype's `.kpi`, whose
     * `.kpi-icon` is `position:absolute; top:16px; right:16px` (`shared.css:421-426`) — the
     * card the Mobil and Tunggakan summaries are built from.
     */
    iconLayout: KnKpiIconLayout = KnKpiIconLayout.Stacked,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val shape = RoundedCornerShape(MaterialTheme.knRadii.lg)
    val corner = iconLayout == KnKpiIconLayout.Corner

    Surface(
        modifier = modifier.knShadow(MaterialTheme.knElevation.level1, shape),
        shape = shape,
        color = kn.bgCard,
        contentColor = kn.textPrimary,
    ) {
        Row {
            if (accent) {
                Surface(
                    modifier = Modifier.size(width = 5.dp, height = 116.dp),
                    color = MaterialTheme.knColors.primaryFill,
                    content = {},
                )
            }
            // `fillMaxWidth` in the corner layout only: a stacked tile sizes to its content, and a
            // corner tile has to fill so its glyph can be pinned to the right edge.
            Box(
                modifier = if (corner) {
                    Modifier.fillMaxWidth().padding(spacing.xl)
                } else {
                    Modifier.padding(spacing.xl)
                },
            ) {
                // The trailing inset reserved for the pinned glyph, so the value cannot run
                // underneath it on a long figure.
                Column(
                    modifier = if (corner) Modifier.padding(end = 48.dp) else Modifier,
                ) {
                    if (icon != null && !corner) {
                        KpiIcon(icon = icon, tint = iconTint)
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.knTypography.overline,
                        color = kn.textTertiary,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.knTypography.displayMedium,
                        color = kn.textPrimary,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                    if (meta != null) {
                        Text(
                            text = meta,
                            style = MaterialTheme.knTypography.body,
                            color = metaTint ?: kn.textTertiary,
                            modifier = Modifier.padding(top = spacing.sm),
                        )
                    }
                }
                if (icon != null && corner) {
                    KpiIcon(
                        icon = icon,
                        tint = iconTint,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
    }
}

/** Where a KPI tile draws its glyph. See [KnKpiTile.iconLayout]. */
enum class KnKpiIconLayout { Stacked, Corner }

/** `.kpi-icon` — a 40dp rounded square, 20dp glyph, the tint at 12% behind it. */
@Composable
private fun KpiIcon(icon: ImageVector, tint: Color?, modifier: Modifier = Modifier) {
    val iconColour = tint ?: MaterialTheme.knColors.semantic.info
    Surface(
        modifier = modifier.size(40.dp),
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
