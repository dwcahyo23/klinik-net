package net.klinik.rental.ui.screens.kendaraan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.SearchX
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.Wallet
import net.klinik.rental.R
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnAvatarSize
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonSize
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnCard
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnKpiGrid
import net.klinik.rental.ui.components.KnKpiIconLayout
import net.klinik.rental.ui.components.KnKpiTile
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnMasterDetail
import net.klinik.rental.ui.components.KnSearchBar
import net.klinik.rental.ui.components.KnSkeletonList
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.components.KnTopBar
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The fleet screen: every vehicle, searchable and filterable in place.
 *
 * ## Why this is one screen and not a list plus a detail route
 *
 * The search bar and the status chips both narrow *the same list*. There is no
 * route change and no second destination, which is what keeps the other chips
 * reachable after a tap and the owner's scroll position intact. Master-detail
 * for vehicles is the shell's job (Story 2.8's landscape layout), not this
 * screen's.
 *
 * ## What is deliberately absent
 *
 * No KPI tile grid, no nav rail, no bottom nav, no FAB. Those are Story 2.8's
 * shell and Story 4.3's, and a card of four figures nobody asked for is not this
 * story.
 *
 * Loading is a skeleton and a refusal is a banner — both added by Story 2.8,
 * which is what discharged the "no skeleton, no error state" note that used to
 * sit here. The four states are: skeleton, empty (two variants), a Danger banner
 * over unchanged content, and a Success snackbar over unchanged content.
 *
 * Every rupiah figure on this screen arrives already formatted in
 * [VehicleRow.rateText]. There is no `formatRupiah` call in this file — money is
 * converted once, in the ViewModel, so two screens cannot disagree about how a
 * rate looks.
 *
 * @param onEdit receives the row's vehicle. Opening the form is a sheet over this
 *   screen, not a navigation, so returning from it restores the owner's place.
 */
@Composable
fun KendaraanScreen(
    state: KendaraanUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (VehicleFilter) -> Unit,
    onClearQuery: () -> Unit,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: (Vehicle) -> Unit,
    modifier: Modifier = Modifier,
    snackbarMessage: String? = null,
    onSnackbarDismiss: () -> Unit = {},
    // Required, and deliberately not aliased to `onSnackbarDismiss`. The two
    // dismiss different messages; aliasing them is how a refusal ends up cleared
    // through the confirmation's setter and stays on screen forever.
    onErrorDismiss: () -> Unit,
) {
    val spacing = MaterialTheme.knSpacing

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                KnTopBar(
                    title = stringResource(R.string.kendaraan_title),
                    // The fleet count lives on the bar rather than in a KPI tile,
                    // so the screen says how much data it is showing without
                    // spending a card on it.
                    subtitle = stringResource(R.string.kendaraan_count, state.fleetCount),
                    // `cars.html:29-37` puts the field and the add button **in the bar**, not in a row
                    // beneath it. The field is this screen's own — it is the only control that
                    // can widen a list the shell's global search narrowed — so it goes in the
                    // `search` slot rather than falling back to `LocalTopBarSearch`.
                    search = {
                        KnSearchBar(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            onClear = onClearQuery,
                            placeholder = stringResource(R.string.kendaraan_search_placeholder),
                            modifier = Modifier
                                .widthIn(max = net.klinik.rental.ui.components.TopBarSearchMaxWidth)
                                .testTag(TAG_SEARCH),
                        )
                    },
                    actions = {
                        KnButton(
                            label = stringResource(R.string.kendaraan_add),
                            onClick = onAdd,
                            icon = Lucide.Plus,
                            variant = KnButtonVariant.Primary,
                            modifier = Modifier.testTag(TAG_ADD_ACTION),
                        )
                    },
                )

                // The page body — `shared.css:285-289`, `padding: var(--sp-6)`.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                ) {
                    // `cars.html:42` — four figures above the chips. No figures while the first
                    // read has not landed: four zeros read as an empty fleet, which is a claim
                    // rather than a placeholder.
                    if (!state.isLoading) {
                        FleetSummaryRow(state.summary)
                    }

                    KendaraanFilterRow(
                        selected = state.filter,
                        counts = state.counts,
                        onSelect = onFilterChange,
                    )

                    KnMasterDetail(
                        // `weight(1f)`: `KnMasterDetail` fills by default, so without it the
                        // panes would take the whole column and push the chips and the summary
                        // off the top of the screen.
                        modifier = Modifier.weight(1f),
                        list = {
                            when {
                                state.isLoading -> KnSkeletonList()
                                state.isEmptyFleet -> KendaraanEmptyFleet(onAdd = onAdd)
                                state.isEmptySearch -> KendaraanEmptySearch(
                                    query = state.query,
                                    onAdd = onAdd,
                                )

                                else -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = spacing.colossal),
                                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                ) {
                                    items(
                                        items = state.visibleRows,
                                        // Keyed on the vehicle id: removing a row under a
                                        // filter must not make the remaining rows shift
                                        // their saved state onto a different car.
                                        key = { it.vehicle.id },
                                    ) { row ->
                                        KendaraanRow(
                                            row = row,
                                            selected = row.vehicle.id == state.selectedRow?.vehicle?.id,
                                            onClick = { onSelect(row.vehicle.id) },
                                        )
                                    }
                                }
                            }
                        },
                        detail = {
                            val selected = state.selectedRow
                            if (selected != null && !state.isLoading) {
                                KendaraanDetail(
                                    row = selected,
                                    contract = state.selectedContract,
                                    onEdit = { onEdit(selected.vehicle) },
                                )
                            }
                        },
                    )
                }
            }

            // Over the page, not replacing it: a confirmation must not change
            // screen state, so the list behind stays exactly where it was.
            //
            // One bar, never two — the same rule `PenyewaScreen` documents. Both
            // messages are `BottomCenter` with the same padding, so drawing them
            // independently stacks two bars on the same pixels, and they are not
            // exclusive upstream: a success followed by a failure leaves both
            // non-null. The error wins, because it is the one that has to be read.
            //
            // This screen previously drew *neither* error — `state.errorMessage`
            // was set by the ViewModel and never rendered, so a refused write
            // (a duplicate plate, a zero rate) failed silently.
            val banner: Pair<String, KnBadgeTone>? = state.errorMessage?.let {
                it to KnBadgeTone.Danger
            } ?: snackbarMessage?.let { it to KnBadgeTone.Success }

            banner?.let { (message, tone) ->
                KnSnackbar(
                    message = message,
                    tone = tone,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(spacing.lg)
                        .testTag(TAG_BANNER),
                    onDismiss = {
                        // Branch on the tone rather than on state.errorMessage, because
                        // the tone is what was actually *drawn*. These two screens have no
                        // sheet of their own, so the selection above always falls through
                        // to the error when one exists and the two agree —
                        // but reading the state instead would be a second thing to keep in
                        // step with the first, and it is KontrakScreen (which does have a
                        // sheet, and therefore *can* draw the confirmation over a pending
                        // error) that shows why: there, rrorMessage != null is true while
                        // the confirmation is the bar on screen.
                        if (tone == KnBadgeTone.Danger) onErrorDismiss() else onSnackbarDismiss()
                    },
                )
            }
        }
    }
}

/**
 * The four status chips, each carrying its count.
 *
 * A `FlowRow` rather than a `Row`: four Indonesian labels plus counts do not fit
 * a narrow phone in one line, and a horizontally scrolling row would hide the
 * other filters — which the AC requires to stay reachable after a tap. Wrapping
 * keeps all four visible at every width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KendaraanFilterRow(
    selected: VehicleFilter,
    counts: Map<VehicleFilter, Int>,
    onSelect: (VehicleFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.sm),
    ) {
        FILTERS.forEach { filter ->
            KnChip(
                text = stringResource(filter.labelRes),
                selected = filter == selected,
                // `KnChip` hides a zero count rather than rendering "0": a zero is
                // a claim about the data the component has no basis to make, and
                // the tap still narrows to nothing, which the empty-search state
                // then explains.
                count = counts[filter],
                onClick = { onSelect(filter) },
                modifier = Modifier.testTag("kendaraan-chip-${filter.name}"),
            )
        }
    }
}

/**
 * `cars.html:147-167` — the four figures above the chips.
 *
 * The prototype's `.kpi` puts its glyph in the card's top-right corner, which is what
 * `KnKpiIconLayout.Corner` draws. A `FlowRow` rather than a grid, so four cards become two rows of
 * two on a narrow window instead of four squeezed columns.
 */
@Composable
private fun FleetSummaryRow(summary: FleetSummary) {
    KnKpiGrid(
        modifier = Modifier.testTag(TAG_SUMMARY),
        tiles = listOf(
            { KnKpiTile(
                label = stringResource(R.string.kendaraan_summary_total),
                value = summary.total.toString(),
                meta = stringResource(R.string.kendaraan_summary_total_meta, summary.total),
                icon = Lucide.CarFront,
                iconTint = MaterialTheme.knColors.onBrand,
                iconLayout = KnKpiIconLayout.Corner,
            ) },
            { KnKpiTile(
                label = stringResource(R.string.kendaraan_summary_rented),
                value = summary.rented.toString(),
                meta = stringResource(R.string.kendaraan_summary_rented_meta, summary.utilisationText),
                icon = Lucide.Users,
                iconTint = MaterialTheme.knColors.semantic.success,
                iconLayout = KnKpiIconLayout.Corner,
            ) },
            { KnKpiTile(
                label = stringResource(R.string.kendaraan_summary_available),
                value = summary.available.toString(),
                meta = stringResource(R.string.kendaraan_summary_available_meta),
                icon = Lucide.CircleCheck,
                iconTint = MaterialTheme.knColors.semantic.info,
                iconLayout = KnKpiIconLayout.Corner,
            ) },
            { KnKpiTile(
                label = stringResource(R.string.kendaraan_summary_potential),
                value = summary.potentialText,
                meta = stringResource(R.string.kendaraan_summary_potential_meta, summary.averageText),
                icon = Lucide.Wallet,
                iconTint = MaterialTheme.knColors.semantic.warning,
                iconLayout = KnKpiIconLayout.Corner,
            ) },
        ),
    )
}

/**
 * One vehicle: plate, brand and year, the rate, and its derived status.
 *
 * @param selected draws the active edge `KnListRow` already owns, which is what the prototype's
 *   `.list-row.active` is (`shared.css:743`).
 * @param onClick selects the row for the pane. Editing is the **pane's** button
 *   (`cars.html:262`), not the row's: a row that both selects and edits cannot be used to read a
 *   car without opening a form over it.
 */
@Composable
private fun KendaraanRow(
    row: VehicleRow,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KnListRow(
        title = row.vehicle.plate,
        subtitle = stringResource(
            R.string.kendaraan_row_subtitle,
            row.vehicle.model,
            row.vehicle.year,
        ),
        selected = selected,
        modifier = modifier.testTag("kendaraan-row-${row.vehicle.id}"),
        leading = {
            // `KnAvatar` at its default 44dp, not a 48dp icon button: the leading
            // slot is hard-boxed to 44dp by `KnListRow`, so a larger element
            // would overflow it rather than fill it.
            KnAvatar(name = row.vehicle.plate)
        },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.xs),
            ) {
                // Already `formatRupiah`'d in the ViewModel. Assembling money
                // here by hand is how two screens end up showing the same rate two
                // different ways.
                Text(
                    text = row.rateText,
                    style = MaterialTheme.knTypography.figure,
                    color = MaterialTheme.knColors.textPrimary,
                )
                KnBadge(
                    text = stringResource(row.status.labelRes),
                    tone = row.status.tone,
                )
            }
        },
        onClick = onClick,
    )
}

/**
 * `cars.html:236-330` — the selected vehicle, its open contract, and the edit verb.
 *
 * The pane draws the car's own facts and, when it is rented, who has it. The three figures the
 * prototype shows (tarif, total diterima, transaksi) need the contract's payment history, which
 * this screen does not read — the amount received is `KontrakScreen`'s subject and a second read
 * of it here would be a second answer to the same question. The pane therefore shows the rate and
 * the renter, and leaves the running total to the screen that already computes it.
 */
@Composable
private fun KendaraanDetail(
    row: VehicleRow,
    contract: VehicleContractFacts?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    val vehicle = row.vehicle

    KnCard(modifier = modifier.fillMaxWidth(), padding = spacing.xxl) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                verticalAlignment = Alignment.Top,
            ) {
                KnAvatar(name = vehicle.plate, size = KnAvatarSize.Large)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = vehicle.plate,
                        style = MaterialTheme.knTypography.displaySmall,
                        color = MaterialTheme.knColors.textPrimary,
                    )
                    Text(
                        text = "${vehicle.brand} ${vehicle.model}",
                        style = MaterialTheme.knTypography.label,
                        color = MaterialTheme.knColors.textSecondary,
                    )
                    Text(
                        text = listOfNotNull(
                            vehicle.color,
                            stringResource(R.string.kendaraan_detail_year, vehicle.year),
                        ).joinToString(" · "),
                        style = MaterialTheme.knTypography.body,
                        color = MaterialTheme.knColors.textTertiary,
                    )
                }
                KnButton(
                    label = stringResource(R.string.kendaraan_edit),
                    onClick = onEdit,
                    icon = Lucide.Pencil,
                    variant = KnButtonVariant.Secondary,
                    size = KnButtonSize.Small,
                    modifier = Modifier.testTag(TAG_DETAIL_EDIT),
                )
            }

            KnDivider()

            // `.kpi-grid` inside the pane — three narrow figures, `shared.css:267`.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                DetailFigure(
                    label = stringResource(R.string.kendaraan_detail_rate),
                    value = row.rateText,
                    modifier = Modifier.weight(1f),
                )
                DetailFigure(
                    label = stringResource(R.string.kendaraan_detail_status),
                    value = stringResource(row.status.labelRes),
                    modifier = Modifier.weight(1f),
                )
                DetailFigure(
                    label = stringResource(R.string.kendaraan_detail_notes),
                    value = vehicle.notes ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }

            if (contract != null) {
                KnDivider()
                Text(
                    text = stringResource(R.string.kendaraan_detail_contract_title),
                    style = MaterialTheme.knTypography.label,
                    color = MaterialTheme.knColors.textSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xl),
                ) {
                    DetailFigure(
                        label = stringResource(R.string.kendaraan_detail_renter),
                        value = contract.renterName,
                        modifier = Modifier.weight(1f),
                    )
                    DetailFigure(
                        label = stringResource(R.string.kendaraan_detail_phone),
                        value = contract.renterPhone,
                        modifier = Modifier.weight(1f),
                    )
                    DetailFigure(
                        label = stringResource(R.string.kendaraan_detail_start),
                        value = contract.startText,
                        modifier = Modifier.weight(1f),
                    )
                    DetailFigure(
                        label = stringResource(R.string.kendaraan_detail_schedule),
                        value = if (contract.restDay == null) {
                            stringResource(R.string.kontrak_schedule_seven)
                        } else {
                            stringResource(R.string.kontrak_schedule_six)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One labelled figure inside a detail pane — the prototype's `.kpi` at `padding: var(--sp-4)`. */
@Composable
private fun DetailFigure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.knTypography.overline.copy(fontSize = 11.sp),
            color = MaterialTheme.knColors.textTertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.knTypography.figure,
            color = MaterialTheme.knColors.textPrimary,
            modifier = Modifier.padding(top = MaterialTheme.knSpacing.xs),
        )
    }
}

/** `shared.css:1003` — `.divider { height: 1px; background: var(--border) }`. */
@Composable
private fun KnDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.knColors.divider),
    )
}

/** Nothing stored yet: say what is missing and what to do about it. */
@Composable
private fun KendaraanEmptyFleet(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    KnEmptyState(
        title = stringResource(R.string.kendaraan_empty_title),
        body = stringResource(R.string.kendaraan_empty_body),
        icon = Lucide.CarFront,
        action = {
            KnButton(
                label = stringResource(R.string.kendaraan_add),
                onClick = onAdd,
                icon = Lucide.CarFront,
                modifier = Modifier.testTag(TAG_ADD),
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_EMPTY_FLEET),
    )
}

/**
 * Nothing matched the search. Names the query so the owner can tell this apart
 * from an empty account — the two need opposite actions.
 */
@Composable
private fun KendaraanEmptySearch(
    query: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KnEmptyState(
        title = stringResource(R.string.kendaraan_empty_search_title),
        body = stringResource(R.string.kendaraan_empty_search_body, query),
        icon = Lucide.SearchX,
        action = {
            KnButton(
                label = stringResource(R.string.kendaraan_add),
                onClick = onAdd,
                variant = KnButtonVariant.Secondary,
                modifier = Modifier.testTag(TAG_ADD),
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_EMPTY_SEARCH),
    )
}

/** Resource ids for the four chips, kept beside the row that reads them. */
private val VehicleFilter.labelRes: Int
    get() = when (this) {
        VehicleFilter.Semua -> R.string.kendaraan_filter_semua
        VehicleFilter.Tersedia -> R.string.kendaraan_filter_tersedia
        VehicleFilter.Tersewa -> R.string.kendaraan_filter_tersewa
        VehicleFilter.Servis -> R.string.kendaraan_filter_servis
    }

private val VehicleStatus.labelRes: Int
    get() = when (this) {
        VehicleStatus.Tersedia -> R.string.kendaraan_status_tersedia
        VehicleStatus.Tersewa -> R.string.kendaraan_status_tersewa
        VehicleStatus.Servis -> R.string.kendaraan_status_servis
    }

/**
 * Tone per status.
 *
 * Tersedia is `Info`, tersewa is `Success` and servis is `Warning` — three of the
 * design system's semantic colours, with no fourth status colour invented.
 *
 * The mapping is `prototype/cars.html:177-179`: `rented` is `badge-success` and
 * `available` is `badge-info`. It shipped the other way round, which reads as a
 * fleet at full capacity looking idle and a fleet fully booked looking free —
 * the two states an owner most needs told apart at a glance.
 */
private val VehicleStatus.tone: KnBadgeTone
    get() = when (this) {
        VehicleStatus.Tersedia -> KnBadgeTone.Info
        VehicleStatus.Tersewa -> KnBadgeTone.Success
        VehicleStatus.Servis -> KnBadgeTone.Warning
    }

private val FILTERS = listOf(
    VehicleFilter.Semua,
    VehicleFilter.Tersedia,
    VehicleFilter.Tersewa,
    VehicleFilter.Servis,
)

internal const val TAG_ADD = "kendaraan-add"

/** The screen's own search field, now in the top bar rather than a row beneath it. */
internal const val TAG_SEARCH = "kendaraan-search"

/**
 * The bar's add button.
 *
 * Its own tag, because it and the empty state's action draw the same label and both can be on
 * screen at once — the bar is always there. A shared tag would make `onNodeWithTag(TAG_ADD)`
 * ambiguous the moment the fleet is empty. `KontrakScreen` records the same trap.
 */
internal const val TAG_ADD_ACTION = "kendaraan-add-action"
internal const val TAG_EMPTY_FLEET = "kendaraan-empty-fleet"
internal const val TAG_EMPTY_SEARCH = "kendaraan-empty-search"

/** The four summary cards — `cars.html:42`. */
internal const val TAG_SUMMARY = "kendaraan-summary"

/** The edit verb inside the detail pane; the row selects rather than edits. */
internal const val TAG_DETAIL_EDIT = "kendaraan-detail-edit"

/** The single banner, whatever its tone — so one test can address it. */
internal const val TAG_BANNER = "kendaraan-banner"

@Preview(showBackground = true)
@Composable
private fun KendaraanScreenPreview() {
    KlinikNetTheme(darkTheme = false) {
        KendaraanScreen(
            state = KendaraanUiState(rows = PREVIEW_ROWS),
            onQueryChange = {},
            onFilterChange = {},
            onClearQuery = {},
            onAdd = {},
            onSelect = {},
            onEdit = {},
            onErrorDismiss = {},
        )
    }
}

private val PREVIEW_ROWS = listOf(
    VehicleRow(
        vehicle = Vehicle(
            id = "1",
            plate = "B 1235 TF",
            brand = "Toyota",
            model = "Avanza",
            year = 2023,
            dailyRate = 250_000L,
        ),
        status = VehicleStatus.Tersedia,
        rateText = "Rp 250.000",
    ),
    VehicleRow(
        vehicle = Vehicle(
            id = "2",
            plate = "B 9876 AB",
            brand = "Honda",
            model = "Brio",
            year = 2022,
            dailyRate = 275_000L,
        ),
        status = VehicleStatus.Tersewa,
        rateText = "Rp 275.000",
    ),
)