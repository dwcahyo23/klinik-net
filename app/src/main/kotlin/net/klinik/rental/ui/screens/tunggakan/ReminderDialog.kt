package net.klinik.rental.ui.screens.tunggakan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The WhatsApp reminder — Story 4.2.
 *
 * ## Why the text is editable rather than a fixed sentence
 *
 * Because the owner knows things this app does not: that the tenant promised to pay Friday, that
 * a transfer is in transit. A message they can adjust is more useful than a locked one — and the
 * link is rebuilt from whatever they leave in the field, so an edit is real rather than cosmetic.
 *
 * ## Why there are two actions and neither is hidden behind the other
 *
 * The WhatsApp button opens the app with the text prefilled; Copy exists for the case where no
 * link can be opened — a tablet without WhatsApp installed, or an owner who wants to paste into
 * a group. The epic's criterion names both, and a gesture or an overflow menu would make one of
 * them undiscoverable.
 *
 * ## Why the copy happens here and not in the ViewModel
 *
 * Because `LocalClipboardManager` needs a composition. The ViewModel is told *that* it happened,
 * so the confirmation still flows through the same `combine` as everything else rather than
 * being a second channel.
 *
 * @param onSend opens the link. Required, not defaulted: a button wired to `{}` renders, is
 *   tappable, and does nothing — the defect this repo has shipped twice.
 * @param onCopy the clipboard write, performed by the caller with the current text. Required for
 *   the same reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    state: ReminderUi,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnModal(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.tunggakan_reminder_title),
        // No `confirmLabel`/`onConfirm`: this dialog's two actions are the message's own, drawn
        // below the field. A "Simpan" in the footer would be a third verb for a dialog whose
        // whole content is one editable sentence and two ways to send it.
        dismissLabel = stringResource(R.string.tunggakan_reminder_close),
        onDismiss = onDismiss,
        modifier = modifier.testTag(TAG_REMINDER),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            KnField(
                label = stringResource(R.string.tunggakan_reminder_field_label),
                value = state.text,
                onValueChange = onChange,
                hint = stringResource(R.string.tunggakan_reminder_hint),
                error = state.errorMessage,
                // Multi-line: the composed sentence is longer than one line on a tablet, and a
                // single-line field would scroll the owner past the figure they are checking.
                singleLine = false,
                maxLines = REMINDER_MAX_LINES,
                modifier = Modifier.testTag(TAG_REMINDER_FIELD),
            )

            state.copiedMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Success,
                    onDismiss = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_REMINDER_COPIED),
                )
            }

            // FlowRow rather than a Row: two buttons plus their labels do not fit a narrow
            // portrait tablet side by side, and a squeezed button is a smaller target.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                KnButton(
                    label = stringResource(R.string.tunggakan_reminder_send),
                    onClick = onSend,
                    variant = KnButtonVariant.Primary,
                    // Disabled on an emptied message, so the refusal is visible before the tap
                    // rather than only after it — and `confirmReminderText` still refuses,
                    // because a disabled button is a courtesy and the rule lives below it.
                    enabled = state.canSend,
                    modifier = Modifier.testTag(TAG_REMINDER_SEND),
                )
                KnButton(
                    label = stringResource(R.string.tunggakan_reminder_copy),
                    onClick = onCopy,
                    variant = KnButtonVariant.Secondary,
                    enabled = state.canSend,
                    modifier = Modifier.testTag(TAG_REMINDER_COPY),
                )
            }
        }
    }
}

/**
 * The row's reminder verb.
 *
 * A labelled `KnButton` rather than an icon: the same argument `KontrakScreen`'s `Akhiri` gives —
 * an icon in a dense row is a glyph the owner has to recognise, and this action has money
 * consequences.
 *
 * **The tag is the caller's, applied once.** A tag applied here as well would be a second
 * `testTag` on the same node, and the later one wins — the exact regression
 * `ContractFormSheetTest` records, where a picker's caller-supplied tag never reached the tree
 * because the component applied its own afterwards.
 */
@Composable
internal fun ReminderAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    KnButton(
        label = stringResource(R.string.tunggakan_reminder_action),
        onClick = onClick,
        variant = KnButtonVariant.Secondary,
        size = net.klinik.rental.ui.components.KnButtonSize.Small,
        modifier = modifier,
    )
}

/**
 * Copies [text] to the clipboard.
 *
 * A small composable rather than a ViewModel call, because the clipboard manager lives in the
 * composition. It is separated so both the dialog's button and a future row affordance can use
 * one implementation.
 */
@Composable
internal fun rememberClipboardCopy(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return { text -> clipboard.setText(AnnotatedString(text)) }
}

/** How tall the message field grows before it scrolls. */
private const val REMINDER_MAX_LINES = 8

internal const val TAG_REMINDER = "tunggakan-reminder"
internal const val TAG_REMINDER_FIELD = "tunggakan-reminder-field"
internal const val TAG_REMINDER_SEND = "tunggakan-reminder-send"
internal const val TAG_REMINDER_COPY = "tunggakan-reminder-copy"
internal const val TAG_REMINDER_COPIED = "tunggakan-reminder-copied"
internal const val TAG_REMINDER_ACTION_PREFIX = "tunggakan-reminder-action-"
