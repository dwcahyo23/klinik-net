package net.klinik.rental.ui.screens.beranda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wallet
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Percent
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnKpiTile
import net.klinik.rental.ui.components.KnTopBar
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * Beranda: what the owner has to do this morning.
 *
 * ## Why four tiles and no hierarchy
 *
 * Because the owner decided it: *"kalau tidak ada yang pertama, tidak perlu ada yang besar."*
 * So no tile is enlarged, no tile is accented, and no tile leads. The `KnKpiTile`'s `accent`
 * parameter is the hierarchy control this screen refuses to use — an accented tile draws a
 * 116dp edge and is visibly taller than its neighbours, which is exactly the "3 hari beside
 * a green badge" shape UX-DR9 forbids for the arrears list, here applied to the morning
 * screen.
 *
 * The test measures the four heights rather than trusting the call sites to be uniform,
 * because a uniform call site is a convention and a measured height is a fact.
 *
 * ## Why the tiles and the grid are both above the fold
 *
 * Because the acceptance criterion says the four KPIs are visible without scrolling, and
 * because a morning screen that has to be scrolled to reveal the day's figures is a summary
 * the owner has to work for. The grid follows the tiles for the same reason: the four actions
 * are the second thing, not the first.
 *
 * ## Why the loading state is a **skeleton per tile**
 *
 * Because four zeros read as a real morning. A screen that draws "Rp 0" before it has looked
 * tells the owner nobody owes them anything, and that is the one claim this app must never
 * make without having checked.
 *
 * @param onDeposit opens the deposit sheet. Required, not defaulted: a tile wired to `{}`
 *   renders a target that reaches nothing.
 * @param onAddVehicle opens the vehicle form. Required for the same reason.
 * @param onManageRenters opens the renters list. Required for the same reason.
 * @param onSendReminder opens the arrears list, where the WhatsApp reminder lives. Required.
 */
@Composable
fun BerandaScreen(
    state: BerandaUiState,
    onDeposit: () -> Unit,
    onAddVehicle: () -> Unit,
    onManageRenters: () -> Unit,
    onSendReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        Column(Modifier.fillMaxSize()) {
            KnTopBar(title = stringResource(R.string.beranda_title))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                KpiRow(state)

                Text(
                    text = stringResource(R.string.beranda_actions_title),
                    style = MaterialTheme.knTypography.label,
                    color = MaterialTheme.knColors.textSecondary,
                )

                QuickActionGrid(
                    onDeposit = onDeposit,
                    onAddVehicle = onAddVehicle,
                    onManageRenters = onManageRenters,
                    onSendReminder = onSendReminder,
                )
            }
        }
    }
}

/**
 * The four KPI tiles, in a row that wraps rather than scrolling.
 *
 * ## Why `FlowRow` and not a `Row`
 *
 * Because the criterion is that all four are visible **without scrolling**, and a fixed
 * `Row` of four wide tiles cannot be on a phone-width screen — it would either clip or push
 * the grid below the fold. A `FlowRow` lays out as many tiles per line as fit and wraps the
 * rest, so all four are present without a horizontal scroll.
 *
 * ## Why every tile gets the same weight
 *
 * Because a weighted tile is a *wider* tile, and a wider tile is a bigger tile. Equal weights
 * are what make the four the same size, and they are the reason a test can measure them and
 * find them equal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KpiRow(state: BerandaUiState) {
    val spacing = MaterialTheme.knSpacing

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_KPI_ROW),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (state.isLoading) {
            // A skeleton **per tile**, not a single one. Four placeholder tiles say "the
            // figures are on their way"; one says "this screen has a loading state", which
            // is not what the owner is waiting for.
            repeat(4) {
                KpiSkeleton(modifier = Modifier.weight(1f))
            }
        } else {
            KnKpiTile(
                label = stringResource(R.string.beranda_kpi_income),
                value = state.incomeText,
                icon = Lucide.Wallet,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_KPI_INCOME),
                // `accent = false` on all four: the accented tile is the one the
                // acceptance criterion forbids.
                accent = false,
            )
            KnKpiTile(
                label = stringResource(R.string.beranda_kpi_arrears),
                value = state.arrearsText,
                icon = Lucide.CircleAlert,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_KPI_ARREARS),
                accent = false,
            )
            KnKpiTile(
                label = stringResource(R.string.beranda_kpi_fleet),
                value = state.fleetText,
                icon = Lucide.CarFront,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_KPI_FLEET),
                accent = false,
            )
            KnKpiTile(
                label = stringResource(R.string.beranda_kpi_rate),
                value = state.rateText,
                icon = Lucide.Percent,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_KPI_RATE),
                accent = false,
            )
        }
    }
}

/**
 * One tile's placeholder.
 *
 * A tile-shaped box rather than a bare box, so the row keeps its shape while the figures
 * arrive — a row that reflows when the data lands is a row the owner watches move.
 */
@Composable
private fun KpiSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(116.dp)
            .testTag(TAG_KPI_SKELETON),
        color = MaterialTheme.knColors.bgSunken,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {}
}

internal const val TAG_KPI_ROW = "beranda-kpi-row"
internal const val TAG_KPI_INCOME = "beranda-kpi-income"
internal const val TAG_KPI_ARREARS = "beranda-kpi-arrears"
internal const val TAG_KPI_FLEET = "beranda-kpi-fleet"
internal const val TAG_KPI_RATE = "beranda-kpi-rate"
internal const val TAG_KPI_SKELETON = "beranda-kpi-skeleton"
