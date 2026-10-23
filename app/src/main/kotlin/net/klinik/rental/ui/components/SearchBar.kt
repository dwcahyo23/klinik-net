package net.klinik.rental.ui.components

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
                // `.search-bar { border: 1.5px solid transparent }` — the
                // unfocused state really is borderless, and the focus state is
                // what supplies the brand edge. So the width is unconditional
                // and only the colour changes; the earlier `if (focused) 1.5.dp
                // else 1.5.dp` was a no-op that read as a pending decision.
                width = 1.5.dp,
                color = if (focused) {
                    MaterialTheme.knColors.primaryFill
                } else {
                    Color.Transparent
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
        // `minTouchTarget` on the **Box**, and the field inside it wraps to its text.
        //
        // The floor has to be on the box, not on the field: `wrapContentHeight` makes the field
        // measure at its line height (35dp), so a floor *inside* it is ignored — which is what
        // shrank the tappable node below 48dp when the centring was first added.
        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = MaterialTheme.minTouchTarget),
            contentAlignment = Alignment.CenterStart,
        ) {
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
                //
                // ## Why `fillMaxHeight` on the field
                //
                // `BasicTextField` lays its text out from the **top** of whatever height it is
                // given, so inside a 48dp box the caret, the typed text and the tap target all
                // sat at the top — the owner's report: *"ketika saya klik search untuk input
                // text atau pointer inputnya tidak center."*
                //
                // `fillMaxHeight` makes the field take the box's full 48dp (so the tap target
                // keeps the floor the test measures) and `BasicTextField`'s own default
                // `LineHeightStyle` centres the single line inside that height. The earlier
                // `wrapContentHeight` centred the *text* but shrank the node to 35dp, which
                // failed the floor — the two have to be one thing, and this is the one.
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    // The bar's own name, in the input's own semantics. Without
                    // it a screen reader announces an unlabelled search box and
                    // then the placeholder, which is the *value* hint rather than
                    // the field's name.
                    .semantics { contentDescription = placeholder },
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