package net.klinik.rental.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    val base = modifier
        .fillMaxWidth()
        .padding(padding)
    val border = if (outlined) androidx.compose.foundation.BorderStroke(1.dp, kn.border) else null
    val elevation = if (outlined) 0.dp else MaterialTheme.knElevation.level1

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
                .focusRing(focused, MaterialTheme.knRadii.lg)
                .knShadow(elevation, shape),
            shape = shape,
            color = kn.bgCard,
            contentColor = kn.textPrimary,
            border = border,
            interactionSource = interaction,
            content = { content() },
        )
    } else {
        Surface(
            modifier = base.then(Modifier.knShadow(elevation, shape)),
            shape = shape,
            color = kn.bgCard,
            contentColor = kn.textPrimary,
            border = border,
            content = { content() },
        )
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
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val shape = RoundedCornerShape(MaterialTheme.knRadii.lg)

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
            Column(Modifier.padding(spacing.xl)) {
                if (icon != null) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(MaterialTheme.knRadii.md),
                        color = (iconTint ?: kn.semantic.info).copy(alpha = 0.12f),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint ?: kn.semantic.info,
                            modifier = Modifier.padding(9.dp),
                        )
                    }
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
        }
    }
}
