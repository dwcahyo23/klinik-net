package net.klinik.rental.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.MessageCircle
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonSize
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The four quick actions, and where each one goes.
 *
 * ## Why every destination here **already exists**
 *
 * Because a tile that opens a screen this app does not have is a tile that lies, and the
 * acceptance criterion is explicit: *"setiap aksi cepat punya tujuan yang sudah ada — tidak
 * ada tombol yang membuka layar belum ada."* Story 4.3 does not get to invent a destination
 * to satisfy a grid.
 *
 * ## Why the grid is the **only** door to these actions from here
 *
 * Because UX-DR17 says *"speed dial TIDAK muncul di Beranda — grid aksi adalah satu-satunya
 * pintu dari sini"*, and the reason is worth keeping: two doors to one action is how one of
 * them goes stale. The shell's deposit FAB therefore disappears on this screen, and the grid's
 * "Catat Setoran" tile takes its place.
 *
 * ## Why the four are the same kind of thing
 *
 * Because a grid that leads with one action is a hierarchy, and this screen's tiles are
 * deliberately flat. The four buttons are all `Secondary` for the same reason the four KPI
 * tiles are all the same size: nothing here is more important than anything else here.
 */
enum class QuickAction(
    val labelRes: Int,
    val icon: ImageVector,
) {
    /** Records a deposit. The tile the FAB gives way to. */
    CatatSetoran(R.string.beranda_action_deposit, Lucide.NotebookPen),

    /** Adds a vehicle. */
    TambahMobil(R.string.beranda_action_vehicle, Lucide.CarFront),

    /** Opens the renters list. */
    KelolaPenyewa(R.string.beranda_action_renter, Lucide.Users),

    /** Opens the arrears list, where the WhatsApp reminder lives. */
    KirimPengingat(R.string.beranda_action_reminder, Lucide.MessageCircle),
}

/**
 * The quick-action grid: four tiles, and the handler each one calls.
 *
 * ## Why the handlers are passed in rather than navigated here
 *
 * Because this file must not reach the object graph. The screen draws tiles and reports which
 * one was tapped; the shell decides what that means — the same rule every other screen in this
 * app follows, and the reason a test can drive the grid with hand-built state and no
 * database.
 *
 * ## Why the buttons are `Secondary` and not `Primary`
 *
 * Because a `Primary` button is the app's one coral accent, and DESIGN.md says brand colour
 * is for *the* primary action on a screen. Four primary buttons would spend the accent four
 * times, which is the same "nothing leads" rule the KPI tiles follow.
 */
@Composable
fun QuickActionGrid(
    onDeposit: () -> Unit,
    onAddVehicle: () -> Unit,
    onManageRenters: () -> Unit,
    onSendReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    val handlers: Map<QuickAction, () -> Unit> = mapOf(
        QuickAction.CatatSetoran to onDeposit,
        QuickAction.TambahMobil to onAddVehicle,
        QuickAction.KelolaPenyewa to onManageRenters,
        QuickAction.KirimPengingat to onSendReminder,
    )

    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_QUICK_ACTION_GRID),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        QuickAction.entries.forEach { action ->
            KnButton(
                label = stringResource(action.labelRes),
                onClick = { handlers.getValue(action)() },
                variant = KnButtonVariant.Secondary,
                size = KnButtonSize.Default,
                icon = action.icon,
                modifier = Modifier
                    .weight(1f)
                    .testTag(quickActionTag(action)),
            )
        }
    }
}

/**
 * A quick action's tile, drawn as a card rather than a button.
 *
 * ## Why the tiles are cards and not buttons
 *
 * Because the design's own shape for this grid is a grid of **tiles** — a card with an icon
 * and a label — and because a card can carry the icon above the label the way `.kpi-icon`
 * does. A row of four pill buttons reads as a toolbar, which is a different thing from a
 * morning's four choices.
 *
 * ## Why the icon is above the label
 *
 * Because the tile is a square-ish target read at arm's length while standing, and the icon is
 * what is recognised before the words are read. That is the same reasoning the numpad's large
 * digits follow.
 */
@Composable
fun QuickActionTile(
    action: QuickAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    val kn = MaterialTheme.knColors

    Surface(
        onClick = onClick,
        modifier = modifier
            .testTag(quickActionTag(action)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(MaterialTheme.knRadii.lg),
        color = kn.bgCard,
        contentColor = kn.textPrimary,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            androidx.compose.material3.Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = kn.textSecondary,
            )
            Text(
                text = stringResource(action.labelRes),
                style = MaterialTheme.knTypography.body,
                color = kn.textPrimary,
            )
        }
    }
}

/** A quick action's tag, keyed on the action so the two cannot drift apart. */
internal fun quickActionTag(action: QuickAction): String = "beranda-action-${action.name}"

internal const val TAG_QUICK_ACTION_GRID = "beranda-quick-actions"
