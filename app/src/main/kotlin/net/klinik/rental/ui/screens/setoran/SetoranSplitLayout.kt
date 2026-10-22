package net.klinik.rental.ui.screens.setoran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnNumPad
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The Setoran **Input** tab as the prototype's two-pane split — `payments.html`.
 *
 * ## Why this is not a sheet in landscape
 *
 * Because the prototype does not use one there. `shared.css:685-691` makes the Input tab a
 * flex row: the form on the left (`.split-form`, which scrolls), the numpad in a **fixed
 * 400px column** on the right (`.numpad-shell`, with a left border and no top border).
 * Both are on screen at once, and the numpad never moves.
 *
 * The screenshot that settled it is `.shots/prototype/payments.png`: the pad is a column
 * with its own display at the top and a `SIMPAN` key at the bottom right, and the form
 * beside it. The app's sheet hid all of that behind a swipe-up.
 *
 * ## Why the width, and why it is a **width** and not an orientation
 *
 * The prototype's own rule is a media query on the frame, and the app's equivalent is the
 * window's width. 600dp is the same breakpoint [net.klinik.rental.ui.shell.AppShell]
 * uses for the rail, so the two decisions cannot disagree: a window that shows the rail
 * shows the split.
 *
 * ## Why the same fields as the sheet
 *
 * Because they are the same form. [DepositFormFields] is the one implementation, and this
 * and the sheet are two layouts over it — a second copy of the renter picker, the method
 * refusal and the transfer-only account field would be a second place each rule lives.
 *
 * @param state the deposit state.
 * @param onChange the form's own reducer.
 * @param onSave the numpad's Save, which **computes the preview** rather than writing —
 *   the same split the sheet has: the pad's SIMPAN opens the preview, and the preview's
 *   confirm is what writes.
 * @param onClose leaves the form. The landscape split has no "Batal" of its own, so this is
 *   the sheet's escape hatch reused: closing the form is the same act either way.
 */
@Composable
internal fun SetoranSplitLayout(
    state: SetoranUiState,
    onChange: ((DepositFormState) -> DepositFormState) -> Unit,
    onSave: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    val form = state.form ?: return

    Row(
        modifier = modifier.fillMaxSize(),
    ) {
        // ---- the form. Scrolls on its own; the pad beside it does not move.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                // `min-width: 0` in the CSS, and the reason for it: a flex child that
                // cannot shrink below its content pushes the numpad column off the
                // window. The weight alone does not stop that.
                .padding(spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(
                    if (form.isEditing) R.string.deposito_edit_title else R.string.deposito_title,
                ),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_DEPOSIT_TITLE),
            )

            DepositFormFields(
                state = state,
                onChange = onChange,
            )
        }

        // ---- the numpad column.
        //
        // The prototype's `.numpad-shell` (`shared.css:687-691`): a card-coloured column
        // with a **left** border and no top border, so it reads as a separate surface
        // rather than as more of the form. The border is the whole of that distinction —
        // without it the pad is a panel of white inside a white page.
        //
        // The border colour is read **here**, outside the draw scope: a `drawBehind` lambda
        // is a draw scope, not a composable one, so `MaterialTheme` cannot be read inside it.
        val borderColour = MaterialTheme.knColors.border
        Column(
            modifier = Modifier
                .width(NumpadColumnWidth)
                .fillMaxHeight()
                .background(MaterialTheme.knColors.bgCard)
                // The line on the **left** only, drawn as a line rather than a border:
                // the CSS says `border-left: 1px solid var(--border)`, and a full border
                // would draw a line along the screen's outer edge too.
                .drawBehind {
                    drawLine(
                        color = borderColour,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 1f,
                    )
                },
        ) {
            KnNumPad(
                value = form.amountDigits,
                onValueChange = { digits -> onChange { it.copy(amountDigits = digits) } },
                onSave = onSave,
                modifier = Modifier.testTag(TAG_NUMPAD),
            )
        }
    }
}

/**
 * The width of the numpad column.
 *
 * `shared.css:688` — `width: 400px; flex: 0 0 400px`. A fixed width, not a share: the pad
 * is a grid of large keys, and a grid whose keys resize with the window is a grid the owner
 * can mis-hit.
 */
private val NumpadColumnWidth = 400.dp

/**
 * The **portrait** presentation of the same split: the form above, the pad below.
 *
 * `shared.css:694-701` stacks the two in portrait — the numpad becomes a full-width panel
 * under the form, with a top border and a shadow above it.
 *
 * ## Why this is not the bottom sheet
 *
 * Because the prototype's portrait is a **stacked panel**, not a sheet: the pad is part of
 * the page, always visible, and the form above it scrolls. A sheet is a different shape —
 * it covers the page and comes from the bottom edge — and the prototype has no such shape
 * anywhere.
 *
 * So the sheet is a fallback for a window too narrow for either split, and this stacked
 * layout is what portrait actually gets.
 */
@Composable
internal fun SetoranStackedLayout(
    state: SetoranUiState,
    onChange: ((DepositFormState) -> DepositFormState) -> Unit,
    onSave: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    val form = state.form ?: return

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(
                    if (form.isEditing) R.string.deposito_edit_title else R.string.deposito_title,
                ),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_DEPOSIT_TITLE),
            )

            DepositFormFields(
                state = state,
                onChange = onChange,
            )

            KnButton(
                label = stringResource(R.string.deposito_cancel),
                onClick = onClose,
                variant = KnButtonVariant.Ghost,
                modifier = Modifier.testTag(TAG_DEPOSIT_CANCEL),
            )
        }

        // The pad, full width under the form. `shared.css:696-700`.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.knColors.bgCard),
        ) {
            KnNumPad(
                value = form.amountDigits,
                onValueChange = { digits -> onChange { it.copy(amountDigits = digits) } },
                onSave = onSave,
                modifier = Modifier.testTag(TAG_NUMPAD),
            )
        }
    }
}

/**
 * Whether this window is wide enough for the side-by-side split.
 *
 * The same 600dp the shell uses for the rail, so the two agree: a window that shows the
 * nav rail is a window that shows the numpad beside the form.
 */
internal const val SPLIT_LAYOUT_MIN_WIDTH_DP = 600

/**
 * Picks the split or the stacked layout for the width it is given.
 *
 * @param state the deposit state.
 * @param onChange the form's own reducer.
 * @param onSave the numpad's Save.
 * @param onClose leaves the form.
 */
@Composable
internal fun SetoranInputLayout(
    state: SetoranUiState,
    onChange: ((DepositFormState) -> DepositFormState) -> Unit,
    onSave: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= SPLIT_LAYOUT_MIN_WIDTH_DP.dp) {
            SetoranSplitLayout(
                state = state,
                onChange = onChange,
                onSave = onSave,
                onClose = onClose,
            )
        } else {
            SetoranStackedLayout(
                state = state,
                onChange = onChange,
                onSave = onSave,
                onClose = onClose,
            )
        }
    }
}
