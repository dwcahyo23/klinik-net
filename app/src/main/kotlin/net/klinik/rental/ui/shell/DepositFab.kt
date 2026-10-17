package net.klinik.rental.ui.shell

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookPen
import net.klinik.rental.R

/**
 * The shell's quick-action button: `fab-mini`, at 54dp.
 *
 * ## Why `SmallFloatingActionButton` and not a speed dial
 *
 * UX-DR17 specifies **both**: a `.fab` (60dp) that opens a speed dial of three labelled
 * actions, and a `.fab-mini` (54dp) for a single action. Only one quick action exists —
 * recording a deposit — so this is the `fab-mini` case, and it is the component's own
 * rule rather than a compromise: a speed dial holding one entry is a menu that hides the
 * action behind a tap, and the owner would press it once for nothing.
 *
 * When the other two quick actions land, this becomes the dial's toggle. That is a
 * different component with the same position, not an edit to this one, so nothing here
 * anticipates it.
 *
 * ## Why it does not appear on Beranda
 *
 * UX-DR17 is explicit: *"Speed dial TIDAK muncul di Beranda"* — the four-tile grid is the
 * **only** door to a quick action there, because two doors to the same action is how one of
 * them goes stale. Beranda is Story 4.3 and does not exist yet, so this has nothing to
 * hide from; the condition to add is then one line, and it is recorded on [visible] rather
 * than left to be discovered.
 *
 * ## Why the button carries a label
 *
 * `SmallFloatingActionButton` holds a 54dp circle, and an icon alone in it would be a
 * gesture the owner has to recognise. The label sits beside it in the pill the epic's
 * `.fab-mini` describes, so the action is readable rather than guessed — the same reason
 * the contract row's end verb spells `Akhiri` instead of drawing an icon.
 */
@Composable
internal fun DepositFab(
    visible: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    SmallFloatingActionButton(
        onClick = onOpen,
        // 54dp, which is UX-DR17's `.fab-mini` figure and also `KnButtonSize.Small`'s
        // floor — so it clears the same 48dp minimum every other control in the app is
        // measured against.
        modifier = modifier
            .size(54.dp)
            .testTag(TAG_DEPOSIT_FAB),
    ) {
        Icon(
            imageVector = Lucide.NotebookPen,
            contentDescription = null,
        )
    }
    // The label is a separate composable in this file rather than inside the button,
    // because a `SmallFloatingActionButton`'s content slot is a single icon-sized box and
    // putting text in it would fight that constraint.
    Text(
        text = stringResource(R.string.deposito_action),
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Start,
        modifier = Modifier.testTag(TAG_DEPOSIT_FAB_LABEL),
    )
}

/**
 * The FAB's tag.
 *
 * Its own, distinct from every screen's add action: the FAB appears over the whole app, so
 * a test addressing `kontrak-add` must not resolve to it — and vice versa.
 */
internal const val TAG_DEPOSIT_FAB = "deposito-fab"

internal const val TAG_DEPOSIT_FAB_LABEL = "deposito-fab-label"