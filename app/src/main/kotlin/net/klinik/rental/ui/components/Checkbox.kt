package net.klinik.rental.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/**
 * The `.checkbox` row — a single boolean toggle with a label.
 *
 * Material3's `Checkbox` is the box, wrapped so the library's own rules hold:
 * the row carries [minTouchTarget] (the box is ~40dp, under the 48dp floor a
 * 32-40dp control would otherwise present), the focus ring is the brand one
 * rather than Material's translucent overlay, and the selection colour is
 * `primaryFill` so a ticked box reads as the same brand hue as a selected chip.
 *
 * The `selected` property on the clickable `Surface` is what a screen reader
 * announces about the toggle itself — "checked" or "unchecked" — rather than a
 * second, unnamed control. The label's `Text` node sits beside it and is
 * announced separately; the two are not merged into one node, so a reader gets
 * the state from the box and the name from the label, not a single combined
 * string.
 *
 * @param hint an optional second line under the label, in the tertiary ink. The
 *   servis toggle uses it; the component itself does not decide when a hint
 *   exists, which is why it is a parameter and not always drawn.
 */
@Composable
fun KnCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        modifier = modifier
            .minTouchTarget()
            .focusRing(focused = focused)
            .semantics {
                selected = checked
            },
        color = Color.Transparent,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = kn.primaryFill,
                    uncheckedColor = kn.borderStrong,
                    checkmarkColor = Color.White,
                ),
                interactionSource = interaction,
            )

            if (hint != null) {
                Column {
                    Text(text = label, style = MaterialTheme.knTypography.label, color = kn.textPrimary)
                    Text(
                        text = hint,
                        style = MaterialTheme.knTypography.body,
                        color = kn.textTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } else {
                Text(text = label, style = MaterialTheme.knTypography.label, color = kn.textPrimary)
            }
        }
    }
}
