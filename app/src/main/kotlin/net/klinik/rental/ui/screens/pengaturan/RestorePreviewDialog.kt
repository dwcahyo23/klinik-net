package net.klinik.rental.ui.screens.pengaturan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The restore's preview and confirmation, in one window.
 *
 * ## Why the preview and the confirm are the same dialog
 *
 * Because the AC requires the owner to see what is in the file **before** deciding, and a
 * two-step flow (preview dialog → confirm dialog) would put the decision one dismissal away from
 * the information. One window means the counts and the confirm button are read together, which is
 * the only arrangement where the numbers can change the answer.
 *
 * ## Why the confirm button says what it destroys
 *
 * `Ganti semua data` rather than `Pulihkan`. The owner is not restoring into an empty app — they
 * are replacing a business that currently exists, and a label that only names the benefit is a
 * label that hides the cost. `CancelDepositDialog` takes the same position for the same reason.
 *
 * ## Why the counts come from the parsed file and not from the database
 *
 * Because the question the owner is answering is *"is this the right file"*. The database's own
 * counts answer a different question and would be identical no matter which file was picked —
 * which is precisely the mistake this dialog exists to prevent.
 */
@Composable
fun RestorePreviewDialog(
    preview: RestorePreviewUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnModal(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.pengaturan_restore_title),
        confirmLabel = stringResource(R.string.pengaturan_restore_confirm),
        onConfirm = onConfirm,
        // Never disabled: unlike the cancellation dialog there is no field to fill, and a
        // confirm the owner cannot press is a dead end when the file is the one they wanted.
        confirmEnabled = true,
        dismissLabel = stringResource(R.string.pengaturan_restore_cancel),
        onDismiss = onDismiss,
        modifier = modifier.testTag(TAG_RESTORE_DIALOG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // The criterion's own sentence shape, built from the file's rows.
            Text(
                text = preview.countsLine,
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_RESTORE_COUNTS),
            )
            Text(
                text = preview.periodLine,
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
                modifier = Modifier.testTag(TAG_RESTORE_PERIOD),
            )
            // The cost, stated before the button that charges it. A warning in the same window as
            // the confirm is the only arrangement where it is read.
            KnBadge(
                text = stringResource(R.string.pengaturan_restore_overwrites),
                tone = KnBadgeTone.Danger,
            )
        }
    }
}

/**
 * What the dialog draws, already formatted.
 *
 * Built by the ViewModel for the same reason every other `*Ui` type here is: a figure assembled
 * in a composable is a figure assembled per recomposition, and the pluralisation of
 * `18 mobil · 14 penyewa · 312 setoran` is exactly the kind of thing that belongs in one place.
 */
data class RestorePreviewUi(
    /** `18 mobil · 14 penyewa · 312 setoran`. */
    val countsLine: String,
    /** `Periode terakhir: 24 Mar 2026`, or the sentence for a file with no deposits. */
    val periodLine: String,
)

internal const val TAG_RESTORE_DIALOG = "pengaturan-restore-dialog"
internal const val TAG_RESTORE_COUNTS = "pengaturan-restore-counts"
internal const val TAG_RESTORE_PERIOD = "pengaturan-restore-period"