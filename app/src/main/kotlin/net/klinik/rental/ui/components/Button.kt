package net.klinik.rental.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.klinik.rental.ui.theme.KnAccessibility
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knAccessibility
import net.klinik.rental.ui.theme.knBrandShadow
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knElevation
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knShadow
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/** The five button variants from `shared.css` section 5. */
enum class KnButtonVariant { Primary, Secondary, Ghost, Danger, Success }

/**
 * The four sizes. [Icon] is the `.btn-icon` shape from the CSS: exactly 48dp,
 * pill, no label.
 */
enum class KnButtonSize(
    internal val height: Dp,
    internal val minWidth: Dp,
    internal val horizontalPadding: Dp,
    internal val gap: Dp,
    internal val iconSize: Dp,
) {
    /**
     * `.btn-lg` — 58dp.
     *
     * The icon stays 18dp, the size `.btn svg` sets for **every** button size. The CSS's
     * `.btn-lg` changes only the label's font-size, so a large button with a larger glyph
     * was a shape the prototype does not have — and a screen with one large button and
     * three defaults read as if two different button styles were in play.
     */
    Large(58.dp, 0.dp, 32.dp, 8.dp, 18.dp),

    /** The default `.btn` — 48dp, the `--tap-min` floor. */
    Default(48.dp, 0.dp, 20.dp, 8.dp, 18.dp),

    /**
     * `.btn-sm` — 44dp tall but still 48dp wide.
     *
     * The CSS sets `min-height: 44px`, which is below the 48dp floor. The floor
     * wins: the hit area is padded back out to 48dp via [minTouchTarget] so the
     * *measured* bound never drops below the threshold, while the visual box
     * keeps the tighter CSS height. `.btn-sm` is only used inside dense rows
     * where a 58dp control would not fit.
     */
    Small(44.dp, 48.dp, 16.dp, 6.dp, 16.dp),

    /** `.btn-icon` — exactly 48dp square, pill. */
    Icon(48.dp, 48.dp, 0.dp, 0.dp, 22.dp),
}

/**
 * The core button.
 *
 * Sizes and variants are exhaustive enums, not nullable parameters: "a button
 * with no variant" is not a state this library can express.
 *
 * Every size clears the 48dp touch floor on both axes. For [KnButtonSize.Small]
 * and [KnButtonSize.Icon] the floor is enforced inside the component rather than
 * left to the caller, because the AC says *no* interactive node may fall below
 * 48dp — including a chip, a badge and an in-row icon button — and the
 * accessibility test cannot tell a caller who forgot.
 */
@Composable
fun KnButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KnButtonVariant = KnButtonVariant.Primary,
    size: KnButtonSize = KnButtonSize.Default,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val palette = KnButtonPalette(variant)
    val shape = RoundedCornerShape(MaterialTheme.knRadii.full)
    // Read from the theme rather than from `KnAccessibility.Clinic`: the value
    // `KlinikNetTheme` provides is the one that must be applied, and a static
    // read would make the token look configurable without being so.
    val touchFloor = MaterialTheme.minTouchTarget
    // The brand shadow is drawn by `knShadow`, not by `Surface(shadowElevation
    // = …)`: that parameter takes no colour, so passing the height alone would
    // render Material's default black and quietly drop `--el-brand`.
    //
    // The secondary variant carries `--el-1`, the same elevation a card carries:
    // the prototype's `.btn-secondary` is a filled card-coloured surface, and
    // without a shadow it was indistinguishable from a ghost button — the two
    // variants the prototype separates by a shadow, read as the same control.
    val shadowModifier = when (variant) {
        KnButtonVariant.Primary -> Modifier.knBrandShadow(shape)
        KnButtonVariant.Secondary -> Modifier.knShadow(MaterialTheme.knElevation.level1, shape)
        else -> Modifier
    }

    // An icon-size button draws no label, so the icon is its only content. With
    // no icon there is nothing at all: a 48dp target a screen reader announces as
    // an unlabelled "Button". Falling back to the label as the description would
    // be worse — it renders blank while pretending to be named. Rendering nothing
    // is the honest outcome, and the caller's layout simply has no item there.
    if (size == KnButtonSize.Icon && icon == null) return

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            // The floor on every size, not just the short ones. Height is
            // already guaranteed by `heightIn` below, but width is not: a button
            // is content-sized, so a short label can leave the node under 48dp
            // across. Under Robolectric's collapsed font metrics a "Batal" ghost
            // button measures 46dp wide, which is what surfaced this. Applying
            // the floor unconditionally makes the guarantee structural instead
            // of dependent on how long the caller's label happens to be.
            .minTouchTarget()
            .focusRing(focused = focused, cornerRadius = MaterialTheme.knRadii.full, color = palette.ringColor)
            .then(shadowModifier),
        shape = shape,
        color = palette.container,
        contentColor = palette.content,
        border = palette.border,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = size.height)
                .then(
                    if (size == KnButtonSize.Icon) {
                        Modifier.size(touchFloor)
                    } else {
                        Modifier.padding(horizontal = size.horizontalPadding)
                    }
                ),
            horizontalArrangement = if (size == KnButtonSize.Icon) {
                Arrangement.Center
            } else {
                Arrangement.spacedBy(size.gap, Alignment.CenterHorizontally)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    // The label already names the action; an icon-only button
                    // needs its own description.
                    contentDescription = if (size == KnButtonSize.Icon) {
                        contentDescription ?: label
                    } else {
                        null
                    },
                    modifier = Modifier.size(size.iconSize),
                    tint = LocalContentColor.current,
                )
            }
            if (size != KnButtonSize.Icon) {
                Text(
                    text = label,
                    style = when (size) {
                        KnButtonSize.Large -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.knTypography.label
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Resolved colours for a variant. Kept out of the composable body for clarity. */
@androidx.compose.runtime.Immutable
internal data class KnButtonPalette(
    val container: Color,
    val content: Color,
    val border: BorderStroke?,
    /**
     * The focus ring's colour on this variant.
     *
     * The brand ring is legible on every surface, but *not* on a saturated fill:
     * `--brand-500` measures 1.3:1 against the primary fill, which is brand
     * darkened precisely until white clears 4.5:1 on it. A filled variant
     * therefore rings in its own label ink, which clears 3:1 on all four fills;
     * the unfilled ones keep the brand token.
     */
    val ringColor: Color,
)

@Composable
internal fun KnButtonPalette(variant: KnButtonVariant): KnButtonPalette {
    val kn = MaterialTheme.knColors
    return when (variant) {
        KnButtonVariant.Primary -> KnButtonPalette(
            container = kn.primaryFill,
            ringColor = kn.surfaceInverse,
            content = Color.White,
            border = null,
        )

        KnButtonVariant.Secondary -> KnButtonPalette(
            container = kn.bgCard,
            ringColor = MaterialTheme.knAccessibility.focusRingColor,
            content = kn.textPrimary,
            border = BorderStroke(1.dp, kn.border),
        )

        KnButtonVariant.Ghost -> KnButtonPalette(
            container = Color.Transparent,
            ringColor = MaterialTheme.knAccessibility.focusRingColor,
            content = kn.textSecondary,
            border = null,
        )

        KnButtonVariant.Danger -> KnButtonPalette(
            container = kn.dangerFill,
            ringColor = kn.surfaceInverse,
            content = Color.White,
            border = null,
        )

        KnButtonVariant.Success -> KnButtonPalette(
            container = kn.successFill,
            ringColor = kn.surfaceInverse,
            content = Color.White,
            border = null,
        )
    }
}
