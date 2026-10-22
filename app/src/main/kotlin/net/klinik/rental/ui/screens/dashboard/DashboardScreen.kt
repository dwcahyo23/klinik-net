package net.klinik.rental.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wallet
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Percent
import kotlinx.coroutines.launch
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnCard
import net.klinik.rental.ui.components.KnKpiGrid
import net.klinik.rental.ui.components.KnKpiIconLayout
import net.klinik.rental.ui.components.KnKpiTile
import net.klinik.rental.ui.components.KnKpiTileSkeleton
import net.klinik.rental.ui.components.KnSegmented
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
 * @param onSeeAllTransactions opens the deposit screen's history tab — the prototype's own
 *   "Lihat semua →" (`dashboard.html:111`). Required, not defaulted: a link wired to `{}` is the
 *   defect this repo's screens KDoc names twice, and the audit found it again on this table.
 *
 * There is deliberately **no** `onOpenDashboard` parameter beside these. The hero's second button
 * scrolls to the charts on this same screen, and a scroll is this composable's own layout state —
 * the same reason `reminderDialogShown` is local. Threading a callback through `NavGraph` and
 * `MainActivity` for a `scrollState.animateScrollTo` would add a hop that carries no decision.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onDeposit: () -> Unit,
    onAddVehicle: () -> Unit,
    onManageRenters: () -> Unit,
    onSendReminder: () -> Unit,
    onBackupReminderTap: () -> Unit,
    onBackupExport: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onSelectPeriod: (DashboardPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    // Story 5.5's dialog, mounted from this file beside the banner that opens it — the two are
    // one feature and a dialog living in another file would be a window whose owner state is
    // somewhere else.
    var reminderDialogShown by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        Column(Modifier.fillMaxSize()) {
            // The prototype's `dashboard.html:29-34`: a segmented period filter in the
            // top bar's actions slot, beside the theme toggle. It switches what the
            // **income** tile reads — the other three tiles are about the whole book.
            KnTopBar(
                title = stringResource(R.string.beranda_title),
                actions = {
                    KnSegmented(
                        options = DashboardPeriod.entries.map { stringResource(it.labelRes()) },
                        selectedIndex = DashboardPeriod.entries.indexOf(state.period),
                        onSelect = { index ->
                            onSelectPeriod(DashboardPeriod.entries[index])
                        },
                        modifier = Modifier
                            .padding(end = spacing.xxl)
                            .testTag(TAG_PERIOD_FILTER),
                    )
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                // Story 5.5's reminder, **above** the tiles: the screen opens every day and a
                // reminder below four KPIs is a reminder nobody reaches.
                state.backupReminder?.let { reminder ->
                    BackupReminderBanner(
                        reminder = reminder,
                        // The tap only records "the owner asked"; the dialog is this screen's
                        // own window, so opening it needs no ViewModel round trip.
                        onTap = {
                            reminderDialogShown = true
                            onBackupReminderTap()
                        },
                    )
                }

                // `dashboard.html:58` opens straight into the KPI grid. There is no hero on
                // this screen: the prototype's hero is on its **entry** page (`index.html`),
                // and this page is the one the owner navigated to on purpose.
                KpiRow(state)

                // Below the tiles, and only once the read has landed: a chart drawn from a
                // read that has not happened is a flat line at zero, which reads as "no income
                // for two weeks" rather than as "not asked yet".
                if (!state.isLoading) {
                    DashboardChartsSection(
                        charts = state.charts,
                        onSeeAllTransactions = onSeeAllTransactions,
                        modifier = Modifier.testTag(TAG_CHARTS_SECTION),
                    )
                }
            }
        }

        // The dialog, outside the scrolling column so it is not part of what scrolls.
        if (reminderDialogShown) {
            BackupReminderDialog(
                onGo = {
                    reminderDialogShown = false
                    onBackupExport()
                },
                onDismiss = { reminderDialogShown = false },
            )
        }
    }
}

/**
 * The four KPI tiles, arranged via [KnKpiGrid] to match the prototype's `.kpi-grid`.
 *
 * Each tile uses [KnKpiIconLayout.Corner] so the 40dp icon floats top-right per
 * `shared.css:421-426`. That is the prototype's `.kpi`, and it is also the layout Kendaraan
 * and Tunggakan already use — a stacked glyph is what made this screen's tiles taller than
 * the other two screens' for no reason the owner could see.
 *
 * [KnKpiGrid] rather than a bare `FlowRow`: it is the component the other two screens use,
 * and it divides a line into equal shares rather than trusting `weight` inside a wrapping
 * flow, which is how a longer figure used to widen its own tile.
 */
@Composable
private fun KpiRow(state: DashboardUiState) {
    if (state.isLoading) {
        KnKpiGrid(
            modifier = Modifier.testTag(TAG_KPI_ROW),
            tiles = listOf(
                { KpiPlaceholder() },
                { KpiPlaceholder() },
                { KpiPlaceholder() },
                { KpiPlaceholder() },
            ),
        )
    } else {
        KnKpiGrid(
            modifier = Modifier.testTag(TAG_KPI_ROW),
            tiles = listOf(
                {
                    // `dashboard.html:175-177` — income for the selected period, and the
                    // number of deposits behind it.
                    KnKpiTile(
                        label = stringResource(R.string.beranda_kpi_income),
                        value = state.incomeText,
                        meta = state.incomeMeta,
                        icon = Lucide.Wallet,
                        iconTint = MaterialTheme.knColors.primaryFill,
                        iconLayout = KnKpiIconLayout.Corner,
                        modifier = Modifier.testTag(TAG_KPI_INCOME),
                    )
                },
                {
                    // `dashboard.html:178-179` — the billing period the engine is charging
                    // for, which is a different window from the tile beside it.
                    KnKpiTile(
                        label = stringResource(R.string.beranda_kpi_cutoff),
                        value = state.cutoffIncomeText,
                        meta = state.cutoffSinceText,
                        icon = Lucide.CalendarDays,
                        iconTint = MaterialTheme.knColors.semantic.info,
                        iconLayout = KnKpiIconLayout.Corner,
                        modifier = Modifier.testTag(TAG_KPI_CUTOFF),
                    )
                },
                {
                    // `dashboard.html:180-181` — the arrears total and how many are critical.
                    KnKpiTile(
                        label = stringResource(R.string.beranda_kpi_arrears),
                        value = state.arrearsText,
                        meta = state.arrearsMeta,
                        icon = Lucide.CircleAlert,
                        iconTint = MaterialTheme.knColors.semantic.danger,
                        metaTint = if (state.arrearsMeta.startsWith("0 ")) null else MaterialTheme.knColors.semantic.danger,
                        iconLayout = KnKpiIconLayout.Corner,
                        modifier = Modifier.testTag(TAG_KPI_ARREARS),
                    )
                },
                {
                    // `dashboard.html:182-183` — the collection rate, over the fleet split.
                    KnKpiTile(
                        label = stringResource(R.string.beranda_kpi_rate),
                        value = state.rateText,
                        meta = state.rateMeta,
                        icon = Lucide.Percent,
                        iconTint = MaterialTheme.knColors.semantic.success,
                        iconLayout = KnKpiIconLayout.Corner,
                        modifier = Modifier.testTag(TAG_KPI_RATE),
                    )
                },
            ),
        )
    }
}

/**
 * One tile's placeholder, in the same corner shape the real tiles use.
 *
 * A thin wrapper so the tag and the layout choice live together: [KnKpiTileSkeleton] owns the
 * geometry, and this screen only says which of the two shapes it draws.
 */
@Composable
private fun KpiPlaceholder() {
    KnKpiTileSkeleton(
        iconLayout = KnKpiIconLayout.Corner,
        modifier = Modifier.testTag(TAG_KPI_SKELETON),
    )
}


internal const val TAG_KPI_ROW = "beranda-kpi-row"
internal const val TAG_CHARTS_SECTION = "beranda-charts-section"
internal const val TAG_PERIOD_FILTER = "beranda-period-filter"
internal const val TAG_KPI_ARREARS = "beranda-kpi-arrears"
internal const val TAG_KPI_CUTOFF = "beranda-kpi-cutoff"
internal const val TAG_KPI_INCOME = "beranda-kpi-income"
internal const val TAG_KPI_RATE = "beranda-kpi-rate"
internal const val TAG_KPI_SKELETON = "beranda-kpi-skeleton"
