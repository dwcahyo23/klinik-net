package net.klinik.rental.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonSize
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * Story 5.5's banner: small, above the fold, and gone the moment today's export exists.
 *
 * ## Why it is a banner and not a dialog
 *
 * Because the AC says *"kecil, tidak menghalangi"*, and because the epic rejects automation on
 * the grounds that it acts **for** the owner. A dialog on every un-backed-up morning is the same
 * move at a smaller scale: something that interrupts until it is dismissed, which teaches the
 * owner to dismiss rather than to read.
 *
 * ## Why it sits **above** the KPI row
 *
 * Because the whole screen opens every day, and a reminder buried under four tiles is a reminder
 * nobody sees. It is one row tall and the tiles still fit below it — the column scrolls, and
 * `BerandaScreenTest` measures that both are present at once rather than one below the other.
 */
@Composable
fun BackupReminderBanner(
    reminder: BackupReminderUi,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_BACKUP_BANNER),
        color = MaterialTheme.knColors.bgCard,
        contentColor = MaterialTheme.knColors.textPrimary,
        shape = RoundedCornerShape(MaterialTheme.knRadii.lg),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = reminder.body,
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_BACKUP_BODY),
            )
            // `null` for a first run — the AC says "kalau ada", so no line reading "never".
            reminder.lastExportLine?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.knTypography.body,
                    color = MaterialTheme.knColors.textTertiary,
                    modifier = Modifier.testTag(TAG_BACKUP_LAST),
                )
            }
            KnButton(
                label = stringResource(R.string.beranda_backup_reminder_action),
                onClick = onTap,
                variant = KnButtonVariant.Secondary,
                size = KnButtonSize.Small,
                modifier = Modifier.testTag(TAG_BACKUP_ACTION),
            )
        }
    }
}

/**
 * The tap's consequence: what is at stake, and where the export is.
 *
 * ## Why this does not launch the export itself
 *
 * Because the export lives in `Pengaturan` and this is a **second door** to it. The dialog's job
 * is the explanation; the route is the app's own navigation, so the owner arrives where the
 * backup section is rather than in a picker that appeared from nowhere.
 */
@Composable
fun BackupReminderDialog(
    onGo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnModal(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.beranda_backup_reminder_title),
        confirmLabel = stringResource(R.string.beranda_backup_reminder_dialog_go),
        onConfirm = onGo,
        dismissLabel = stringResource(R.string.pengaturan_restore_cancel),
        onDismiss = onDismiss,
        modifier = modifier.testTag(TAG_BACKUP_DIALOG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.beranda_backup_reminder_dialog_body),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
                textAlign = TextAlign.Start,
            )
        }
    }
}

internal const val TAG_BACKUP_BANNER = "beranda-backup-banner"
internal const val TAG_BACKUP_BODY = "beranda-backup-body"
internal const val TAG_BACKUP_LAST = "beranda-backup-last"
internal const val TAG_BACKUP_ACTION = "beranda-backup-action"
internal const val TAG_BACKUP_DIALOG = "beranda-backup-dialog"