package net.klinik.rental.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/** The five badge tones from `shared.css` section 7. There is no sixth. */
enum class KnBadgeTone { Success, Warning, Danger, Info, Brand, Neutral }

/**
 * `.badge` — a status pill.
 *
 * Visual height is 26dp, well under the 48dp floor, so an *interactive* badge
 * wraps its content in [minTouchTarget] and becomes a role="button" with a
 * label. A non-interactive badge is decoration and carries no semantics beyond
 * its text.
 *
 * The tone's foreground is the shared semantic constant in both themes; only the
 * background follows the theme. Note that these pairs sit below 4.5:1 in one
 * theme or the other — see the note in `Color.kt`. The badge therefore never
 * carries the only copy of a number; it sits beside the value it describes.
 */
@Composable
fun KnBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: KnBadgeTone = KnBadgeTone.Neutral,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val kn = MaterialTheme.knColors
    val (fg, bg) = when (tone) {
        KnBadgeTone.Success -> kn.semantic.success to kn.semanticBg.success
        KnBadgeTone.Warning -> kn.semantic.warning to kn.semanticBg.warning
        KnBadgeTone.Danger -> kn.semantic.danger to kn.semanticBg.danger
        KnBadgeTone.Info -> kn.semantic.info to kn.semanticBg.info
        KnBadgeTone.Brand -> kn.onBrand to kn.brandSurface
        KnBadgeTone.Neutral -> kn.textSecondary to kn.bgSunken
    }

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.knSpacing.md)
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(text = text, style = MaterialTheme.knTypography.overline, color = fg)
        }
    }

    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        androidx.compose.material3.Surface(
            onClick = onClick,
            modifier = modifier
                .minTouchTarget()
                .focusRing(focused, MaterialTheme.knRadii.full),
            shape = RoundedCornerShape(MaterialTheme.knRadii.full),
            color = bg,
            contentColor = fg,
            interactionSource = interaction,
        ) { Box(Modifier.defaultMinSize(minHeight = 26.dp)) { content() } }
    } else {
        Box(
            modifier = modifier
                .background(bg, RoundedCornerShape(MaterialTheme.knRadii.full))
                .defaultMinSize(minHeight = 26.dp),
        ) { content() }
    }
}

/**
 * `.chip` — a filter toggle. Always interactive, always ≥ 48dp.
 *
 * The CSS already sets `min-height: 48px` on `.chip`, so unlike [KnBadge] this
 * one needs no padding trick; [minTouchTarget] is still applied so the floor
 * holds if the visual height is ever changed.
 *
 * @param interactionSource overridable so a test can drive focus. The ring is
 *   driven by `collectIsFocusedAsState`, and Robolectric does not deliver a focus
 *   request to a Material `Surface`; exposing the source is the only way to
 *   exercise the real component's focus path rather than a bare `Box` wearing the
 *   same modifier. Defaults to an internal instance, so callers never need it.
 * @param enabled defaults to `true` — a chip is a filter toggle, and a filter that
 *   cannot be toggled is a different component. It exists for the one case where the
 *   chip is on screen **to state a fact rather than to offer a choice**: the deposit
 *   sheet's payment-method row during a correction, which the epic forbids changing.
 *
 *   A disabled chip here rather than a guarded `onClick`, because a control that
 *   renders live, keeps its 48dp target and its selected tint, and then ignores the
 *   tap is indistinguishable from a broken app — the exact defect `Screens.Kontrak`'s
 *   KDoc exists to prevent. `Surface(enabled = false)` also drops the click action
 *   from the semantics tree, so a screen reader announces the fact rather than
 *   inviting an action that would not happen.
 */
@Composable
fun KnChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    leadingIcon: ImageVector? = null,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
) {
    val kn = MaterialTheme.knColors
    val selectedContainer = MaterialTheme.knColors.primaryFill
    val container = if (selected) selectedContainer else kn.bgCard
    // The count is not a tint of the label: `copy(alpha = …)` would produce an
    // effective colour that no token names and no test measures, which is the
    // same special-casing the contrast AC forbids. On the selected fill it takes
    // the inverse ink — `surfaceInverse` — which is what that fill is measured
    // against, and `ContrastTest` pins the pair on both the fill and the
    // unfocused card surface.
    val content = if (selected) Color.White else kn.textSecondary
    val countContent = if (selected) kn.surfaceInverse else kn.textTertiary
    val border = if (selected) null else BorderStroke(1.5.dp, kn.border)
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    androidx.compose.material3.Surface(
        onClick = onClick,
        // The disabled chip keeps its own colours rather than Material's alpha: this app
        // has no disabled token, and fading the label is a contrast decision made without
        // one. What tells the owner the row is fixed is the *absence* of the click action,
        // which `Surface` makes real rather than simulated.
        enabled = enabled,
        modifier = modifier
            .minTouchTarget()
            .focusRing(focused, MaterialTheme.knRadii.full)
            // A chip is a filter toggle, not a plain button: the selection is
            // carried in the semantics tree so a screen reader can say which
            // filter is active rather than announcing a row of identical
            // buttons. `Surface(onClick=)` already contributes the click role
            // and the action, so this adds only the state — two overlapping
            // clickable modifiers would make the node fire twice.
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(MaterialTheme.knRadii.full),
        color = container,
        contentColor = content,
        border = border,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.knSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(text = text, style = MaterialTheme.knTypography.body, color = content)
            // Only a real count. A zero or negative would render as "0" or
            // "-1" on a filter, which is a claim about the data that the
            // component has no basis to make.
            if (count != null && count > 0) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.knTypography.overline,
                    color = countContent,
                )
            }
        }
    }
}

/**
 * `.dot` — 8dp status dot from `shared.css` section 7.
 *
 * Non-text, so the 3:1 floor applies rather than 4.5:1. It is never the only
 * carrier of meaning: a dot sits beside the badge text or the value it marks.
 */
@Composable
fun KnDot(tone: KnBadgeTone, modifier: Modifier = Modifier) {
    val kn = MaterialTheme.knColors
    val color = when (tone) {
        KnBadgeTone.Success -> kn.semantic.success
        KnBadgeTone.Warning -> kn.semantic.warning
        KnBadgeTone.Danger -> kn.semantic.danger
        KnBadgeTone.Info -> kn.semantic.info
        KnBadgeTone.Brand -> MaterialTheme.knColors.primaryFill
        // `--warm-400`, the only warm-neutral that is not one of the three text
        // levels. Read from the neutral token rather than re-spelled as a hex.
        KnBadgeTone.Neutral -> MaterialTheme.knColors.neutralMarker
    }
    Box(
        modifier
            .size(8.dp)
            .background(color, RoundedCornerShape(MaterialTheme.knRadii.full)),
    )
}
