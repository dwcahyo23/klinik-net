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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * Story 5.4's preview: what the chosen billing day would do, **before** it is done.
 *
 * ## Why this dialog is mandatory rather than a courtesy
 *
 * Because the AC says so, and because the numbers in it are the only way an owner can tell a
 * routine change from one that rewrites their month's bill. The confirm writes **one** settings
 * row; the dialog exists to make sure nobody reaches that row without reading what the new day
 * does to their figures.
 *
 * ## Why the delta's direction is spelled out
 *
 * Because `Rp 750.000` without a direction is not a warning, it is a number. The ViewModel
 * formats "naik"/"turun" into the sentence, so the sign is never left to a glyph.
 */
@Composable
fun CutoffPreviewDialog(
    preview: CutoffPreviewUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnModal(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.pengaturan_cutoff_preview_title),
        // "Simpan tanggal ini" rather than "Simpan": the owner is not saving a preference, they
        // are moving every arrears figure in the app.
        confirmLabel = stringResource(R.string.pengaturan_cutoff_confirm),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.pengaturan_restore_cancel),
        onDismiss = onDismiss,
        modifier = modifier.testTag(TAG_CUTOFF_PREVIEW_DIALOG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = preview.changedLine,
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_CUTOFF_PREVIEW_CHANGED),
            )
            // The rupiah delta, in tabular figures — it is the number the decision turns on, and
            // a proportional face makes two columns of them hard to compare.
            Text(
                text = preview.deltaLine,
                style = MaterialTheme.knTypography.figure,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.knColors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_CUTOFF_PREVIEW_DELTA),
            )
            // The periods, so the delta is intelligible rather than just alarming: "it went up by
            // this much" is a different fact from "because the period now runs from here to
            // there".
            Text(
                text = preview.periodLine,
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_CUTOFF_PREVIEW_PERIODS),
            )
            // The consequence, said plainly: the ledger is not touched, and an owner who believes
            // otherwise would refuse a correct change.
            KnBadge(
                text = stringResource(R.string.pengaturan_cutoff_ledger_note),
                tone = KnBadgeTone.Info,
            )
        }
    }
}

internal const val TAG_CUTOFF_PREVIEW_DIALOG = "pengaturan-cutoff-preview-dialog"
internal const val TAG_CUTOFF_PREVIEW_CHANGED = "pengaturan-cutoff-preview-changed"
internal const val TAG_CUTOFF_PREVIEW_DELTA = "pengaturan-cutoff-preview-delta"
internal const val TAG_CUTOFF_PREVIEW_PERIODS = "pengaturan-cutoff-preview-periods"