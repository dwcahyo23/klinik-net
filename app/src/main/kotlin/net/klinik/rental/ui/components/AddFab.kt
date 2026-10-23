package net.klinik.rental.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knBrandShadow
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/**
 * The app's one **add** button — the prototype's `.fab` (`shared.css:325-340`).
 *
 * ## Why a floating button and not one in the top bar
 *
 * Because the top bar already carries the screen's title and its search field, and a third
 * control there pushed the title into an ellipsis: on the device "Tunggakan" rendered as
 * "Tungga…". A floating button costs the bar nothing.
 *
 * ## Why it is the **only** add control on a screen
 *
 * Because a screen had two or three of them — one in the bar, one inside the empty state,
 * and (before this) none at all once the list had rows. Three behaviours for one action is
 * how one of them goes stale, and it is what the owner reported: *"ada double tombol yang
 * fungsinya sama"*.
 *
 * So the empty state loses its button and the bar loses its button; this one is mounted by
 * the screen's `Scaffold`-equivalent `Box` and is present in **every** state — loading,
 * empty, and populated. One control, one place, one behaviour.
 *
 * ## Why the label is beside the icon rather than under it
 *
 * The prototype's `.fab` is a pill: a 24dp plus and the words, in one rounded shape
 * (`shared.css:325-340`). An icon-only circle would be a gesture the owner has to
 * recognise; the words say what will be added.
 *
 * @param label the action's words — "Tambah Mobil", "Tambah Penyewa", "Tambah rekening".
 * @param icon the glyph. Required: a floating button with no glyph is a pill of text that
 *   reads as a link rather than as the screen's primary action.
 * @param onClick the action. Required, never defaulted — a FAB wired to `{}` is a control
 *   that reaches nothing, which this repo has shipped three times.
 */
@Composable
fun KnAddFab(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kn = MaterialTheme.knColors
    val shape = RoundedCornerShape(MaterialTheme.knRadii.full)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = modifier
            // The prototype's `min-height: 60px` (`shared.css:328`), and the app's 48dp
            // floor above it — a floating button is the screen's primary action and is
            // pressed while the owner is standing.
            .minTouchTarget()
            .focusRing(focused, MaterialTheme.knRadii.full)
            .knBrandShadow(shape),
        shape = shape,
        color = kn.primaryFill,
        // `.fab { color: #fff }` (`shared.css:331`) — white on the brand fill.
        //
        // Not `kn.onBrand`: that token is brand-coloured *text on a tinted surface*
        // (`#DB163C`), and putting it on the brand fill drew a red label on red, which
        // read as a disabled button.
        contentColor = Color.White,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.knSpacing.xxl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                MaterialTheme.knSpacing.sm,
            ),
        ) {
            Icon(
                imageVector = icon,
                // The label beside it is the name; a description here would say it twice.
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                // `.fab { font-weight: 800; font-size: var(--fs-sm) }` (`shared.css:333`).
                style = MaterialTheme.knTypography.label,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}
