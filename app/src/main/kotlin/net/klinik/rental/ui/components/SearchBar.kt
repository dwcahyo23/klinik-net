package net.klinik.rental.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import net.klinik.rental.R
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/**
 * `.search-bar` from `shared.css` section 8 — a sunken pill that becomes a
 * card-coloured field with a brand border once it has focus.
 *
 * The clear button only appears when there is something to clear, and it is a
 * full 48dp target rather than the 16dp glyph next to it.
 */
@Composable
fun KnSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    onClear: (() -> Unit)? = null,
    /**
     * Invoked by the keyboard's search key.
     *
     * Falls back to [onClear] when absent, so a bar that only supports clearing
     * still responds rather than swallowing the key.
     */
    onSubmit: (() -> Unit)? = null,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(MaterialTheme.knRadii.full)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .minTouchTarget()
            .background(
                if (focused) kn.bgCard else kn.bgSunken,
                shape,
            )
            .border(
                width = if (focused) 1.5.dp else 1.5.dp,
                color = if (focused) {
                    MaterialTheme.knColors.primaryFill
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
                shape = shape,
            )
            .focusRing(
                focused = focused,
                cornerRadius = MaterialTheme.knRadii.full,
            )
            .padding(horizontal = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(
            imageVector = Lucide.Search,
            contentDescription = null,
            tint = kn.textTertiary,
            modifier = Modifier.size(20.dp),
        )
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.knTypography.body,
                    color = kn.textTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.knTypography.body.copy(color = kn.textPrimary),
                cursorBrush = SolidColor(MaterialTheme.knColors.primaryFill),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // The search key on the IME is a first-class path, not a
                // decoration: without this the owner taps "search" on the
                // keyboard and nothing happens, which reads as a broken bar.
                keyboardActions = KeyboardActions(
                    onSearch = { (onSubmit ?: onClear ?: {})() },
                ),
                // The CSS gives `.search-bar input` its own `min-height:
                // var(--tap-min)` (shared.css line 557) rather than relying on
                // the wrapper's. Without it the tappable input is one text line
                // tall inside a 48dp bar — the padding is not the target.
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MaterialTheme.minTouchTarget),
            )
        }
        if (query.isNotEmpty()) {
            KnIconButton(
                icon = Lucide.X,
                // The glyph carries no text of its own, so without this the
                // button reaches a screen reader as a bare "Button" with no way
                // to tell it apart from the rest of the bar.
                contentDescription = stringResource(R.string.search_clear),
                onClick = { (onClear ?: { onQueryChange("") })() },
            )
        }
    }
}