package net.klinik.rental.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knElevation
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knShadow
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/**
 * `.segmented` from `shared.css:520-530` — a sunken pill group with one selected stop.
 *
 * ## Why this is not a row of [KnChip]
 *
 * Because the CSS's `.segmented` is a **container** with a sunken background and a 4px
 * inset, and the selected stop is a raised card inside it. A row of chips draws the same
 * words but a different shape: each chip carries its own border, and the group reads as a
 * row of separate controls rather than as one control with four positions.
 *
 * The prototype uses it for the dashboard's period filter (`dashboard.html:29-34`), which
 * is a **switch** — one of four mutually exclusive views of the same data — rather than a
 * filter that can be combined with others.
 *
 * ## Why the selection is in the semantics tree
 *
 * Because a screen reader has to say which stop is active, and four buttons labelled
 * *Hari ini / Minggu / Cut off / Custom* say nothing about that without it. The same rule
 * [KnChip] follows, for the same reason.
 *
 * @param options the stops, in order. The **selected** one is highlighted; the rest are
 *   not. Passing the selected index rather than a per-option flag keeps "one of them is
 *   selected" a fact the component holds rather than one each caller has to keep in step.
 * @param onSelect called with the index of the stop that was tapped.
 * @param label the selected stop's text. The component is not generic over the option
 *   type, because the prototype's only segmented control carries strings.
 */
@Composable
fun KnSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kn = MaterialTheme.knColors

    Row(
        modifier = modifier
            .background(kn.bgSunken, RoundedCornerShape(MaterialTheme.knRadii.full))
            // The CSS's `padding: 4px; gap: 2px` — the group's own inset, which is what
            // makes the selected stop read as raised out of a tray rather than as another
            // button in a row.
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            KnSegmentedStop(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

/**
 * One stop of a [KnSegmented].
 *
 * A `Surface` rather than a `KnButton`, because the selected stop is a **card inside a
 * tray** (`shared.css:530` — `background: var(--bg-card); box-shadow: var(--el-1)`), not a
 * coral button. The group's brand colour belongs to the *selected* state of a chip; here
 * the selection is a raised surface, which is a different shape.
 */
@Composable
private fun KnSegmentedStop(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val kn = MaterialTheme.knColors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(MaterialTheme.knRadii.full)

    Surface(
        onClick = onClick,
        modifier = Modifier
            // `min-height: 46px` in the CSS, and the 48dp floor above it.
            .defaultMinSize(minHeight = MaterialTheme.minTouchTarget)
            .focusRing(focused, MaterialTheme.knRadii.full)
            .semantics { this.selected = selected }
            // `--el-1` on the selected stop only: an unselected stop is flat inside the
            // tray, and a raised one would be a second raised thing.
            .then(
                if (selected) {
                    Modifier.knShadow(MaterialTheme.knElevation.level1, shape)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = if (selected) kn.bgCard else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) kn.primaryFill else kn.textTertiary,
        interactionSource = interaction,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = MaterialTheme.knSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                // `--fs-sm` weight 700 (`shared.css:526`).
                style = MaterialTheme.knTypography.label,
                color = if (selected) kn.primaryFill else kn.textTertiary,
                maxLines = 1,
            )
        }
    }
}
