package net.klinik.rental.ui.screens.beranda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wallet
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Percent
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.launch
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnCard
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
fun BerandaScreen(
    state: BerandaUiState,
    onDeposit: () -> Unit,
    onAddVehicle: () -> Unit,
    onManageRenters: () -> Unit,
    onSendReminder: () -> Unit,
    onBackupReminderTap: () -> Unit,
    onBackupExport: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    // Story 5.5's dialog, mounted from this file beside the banner that opens it — the two are
    // one feature and a dialog living in another file would be a window whose owner state is
    // somewhere else.
    var reminderDialogShown by rememberSaveable { mutableStateOf(false) }

    // The hero's second door scrolls to the charts. Both the scroll state and the charts'
    // measured offset live here because they are one decision — *where* the dashboard starts
    // is only knowable from the layout that draws it, and the only thing that needs to know
    // is the button above it.
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var chartsOffset by remember { mutableStateOf(0) }

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
                    .verticalScroll(scrollState)
                    .padding(spacing.lg),
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

                // The hero sits **above** the tiles, matching `index.html:104-123`: it is the
                // screen's greeting and its two doors, and a welcome below four figures is not a
                // welcome.
                HeroCard(
                    onDeposit = onDeposit,
                    // A scroll, not a navigation. See [HeroCard]'s KDoc: the dashboard is the
                    // lower half of this page, so the button moves the page rather than
                    // pretending to be a destination this app does not have.
                    onOpenDashboard = { scope.launch { scrollState.animateScrollTo(chartsOffset) } },
                )

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

                // Below the actions, and only once the read has landed: a chart drawn from a
                // read that has not happened is a flat line at zero, which reads as "no income
                // for two weeks" rather than as "not asked yet".
                if (!state.isLoading) {
                    DashboardChartsSection(
                        charts = state.charts,
                        onSeeAllTransactions = onSeeAllTransactions,
                        // Where the section starts, measured in the scrolling column's own
                        // coordinates — the only place the answer exists. Measured rather than
                        // computed from the heights above it: those change with the reminder
                        // banner, the wrap of the tiles and the tablet's width, and a computed
                        // offset would be right on one device and wrong on the next. The
                        // column's own top padding means the scroll lands a few dp short, which
                        // is the direction that helps: the first card arrives whole rather than
                        // flush against the bar.
                        modifier = Modifier
                            .testTag(TAG_CHARTS_SECTION)
                            .onGloballyPositioned { chartsOffset = it.positionInParent().y.toInt() },
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

/**
 * The hero card — `index.html:104-123`.
 *
 * ## Why this screen has one and the dashboard tab does not
 *
 * Because the prototype only has one on its **home** view (`index.html`), and its `dashboard.html`
 * opens straight into the KPI grid. The greeting belongs where the owner arrives, not on a page
 * they navigated to on purpose.
 *
 * ## Why it is drawn even when the figures are still loading
 *
 * Because it carries no figure. The badge and the two lines are static copy and the two buttons are
 * navigation, so a skeleton here would be a placeholder for nothing — and the screen's own KDoc
 * already argues that four fake tiles read as a real morning while a greeting does not.
 *
 * ## Why the second button says "Buka Dashboard" and goes nowhere new
 *
 * It scrolls to [DashboardChartsSection], which is on **this** screen below the fold. The
 * prototype's "Buka Dashboard" is a link to `dashboard.html`; here the dashboard is the lower half
 * of the page it is already on, so the button moves the page rather than pretending to be a second
 * destination this app does not have. A button wired to `{}` would be the renders-and-does-nothing
 * defect the `Screens.Kontrak` KDoc forbids — this one animates the scroll, and the test that pins
 * it is the scroll position, not the button's existence.
 *
 * @param onOpenDashboard moves the page to the charts. Required, not defaulted, for the reason
 *   above — and unlike the screen's other callbacks it is **local** to this file: it closes over
 *   the scrolling column's own [ScrollState], which no other layer can see.
 */
@Composable
private fun HeroCard(
    onDeposit: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    val spacing = MaterialTheme.knSpacing
    KnCard(modifier = Modifier.testTag(TAG_HERO), padding = spacing.xxl) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            KnBadge(
                text = stringResource(R.string.beranda_hero_badge),
                tone = KnBadgeTone.Brand,
                modifier = Modifier.testTag(TAG_HERO_BADGE),
            )
            Text(
                text = stringResource(R.string.beranda_hero_title),
                style = MaterialTheme.knTypography.displaySmall,
                color = MaterialTheme.knColors.textPrimary,
            )
            Text(
                text = stringResource(R.string.beranda_hero_body),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                KnButton(
                    label = stringResource(R.string.beranda_action_deposit),
                    onClick = onDeposit,
                    icon = Lucide.Plus,
                    modifier = Modifier.testTag(TAG_HERO_DEPOSIT),
                )
                KnButton(
                    label = stringResource(R.string.beranda_hero_open_dashboard),
                    onClick = onOpenDashboard,
                    variant = KnButtonVariant.Secondary,
                    modifier = Modifier.testTag(TAG_HERO_DASHBOARD),
                )
            }
        }
    }
}

internal const val TAG_KPI_ROW = "beranda-kpi-row"
internal const val TAG_HERO = "beranda-hero"
internal const val TAG_HERO_BADGE = "beranda-hero-badge"
internal const val TAG_HERO_DEPOSIT = "beranda-hero-deposit"
internal const val TAG_HERO_DASHBOARD = "beranda-hero-dashboard"
// The charts section's own anchor: the hero's second button scrolls here, so the tag is what
// lets a test ask whether the page actually moved rather than only whether the button exists.
internal const val TAG_CHARTS_SECTION = "beranda-charts-section"
internal const val TAG_KPI_ARREARS = "beranda-kpi-arrears"
internal const val TAG_KPI_INCOME = "beranda-kpi-income"
internal const val TAG_KPI_FLEET = "beranda-kpi-fleet"
internal const val TAG_KPI_RATE = "beranda-kpi-rate"
internal const val TAG_KPI_SKELETON = "beranda-kpi-skeleton"
