package net.klinik.rental.ui.screens.kendaraan

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
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SearchX
import net.klinik.rental.R
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnSearchBar
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
 * No KPI tile grid, no nav rail, no bottom nav, no skeleton, no error state, no
 * FAB. All of those are Story 2.8 and Story 4.3, and a card of four figures
 * nobody asked for is not this story. The one feedback surface here is a
 * snackbar, and it appears over the page without changing it.
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
    onEdit: (Vehicle) -> Unit,
    modifier: Modifier = Modifier,
    snackbarMessage: String? = null,
    onSnackbarDismiss: () -> Unit = {},
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
                )

                KnSearchBar(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    onClear = onClearQuery,
                    placeholder = stringResource(R.string.kendaraan_search_placeholder),
                    modifier = Modifier.padding(
                        start = spacing.lg,
                        end = spacing.lg,
                        top = spacing.lg,
                    ),
                )

                KendaraanFilterRow(
                    selected = state.filter,
                    counts = state.counts,
                    onSelect = onFilterChange,
                    modifier = Modifier.padding(
                        start = spacing.lg,
                        end = spacing.lg,
                        top = spacing.md,
                    ),
                )

                Box(Modifier.weight(1f)) {
                    when {
                        // Two different emptinesses with two different messages.
                        // A stored-but-unmatched search is not an error and must
                        // not read like an empty account.
                        state.isEmptyFleet -> KendaraanEmptyFleet(onAdd = onAdd)
                        state.isEmptySearch -> KendaraanEmptySearch(
                            query = state.query,
                            onAdd = onAdd,
                        )

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = spacing.lg,
                                end = spacing.lg,
                                top = spacing.lg,
                                bottom = spacing.colossal,
                            ),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            items(
                                items = state.visibleRows,
                                // Keyed on the vehicle id: removing a row under a
                                // filter must not make the remaining rows shift
                                // their saved state onto a different car.
                                key = { it.vehicle.id },
                            ) { row ->
                                KendaraanRow(row = row, onEdit = { onEdit(row.vehicle) })
                            }
                        }
                    }
                }
            }

            // Over the page, not replacing it: a confirmation must not change
            // screen state, so the list behind stays exactly where it was.
            snackbarMessage?.let { message ->
                KnSnackbar(
                    message = message,
                    tone = KnBadgeTone.Success,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(spacing.lg),
                    onDismiss = onSnackbarDismiss,
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

/** One vehicle: plate, brand and year, the rate, and its derived status. */
@Composable
private fun KendaraanRow(
    row: VehicleRow,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(
        R.string.kendaraan_edit_description,
        row.vehicle.plate,
    )

    KnListRow(
        title = row.vehicle.plate,
        subtitle = stringResource(
            R.string.kendaraan_row_subtitle,
            row.vehicle.model,
            row.vehicle.year,
        ),
        modifier = modifier
            .testTag("kendaraan-row-${row.vehicle.id}")
            .semantics { contentDescription = editLabel },
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
        onClick = onEdit,
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
 * Tersedia is `Success`, tersewa is `Brand` and servis is `Warning` — the four
 * semantic colours of the design system, with no fifth status colour invented.
 */
private val VehicleStatus.tone: KnBadgeTone
    get() = when (this) {
        VehicleStatus.Tersedia -> KnBadgeTone.Success
        VehicleStatus.Tersewa -> KnBadgeTone.Brand
        VehicleStatus.Servis -> KnBadgeTone.Warning
    }

private val FILTERS = listOf(
    VehicleFilter.Semua,
    VehicleFilter.Tersedia,
    VehicleFilter.Tersewa,
    VehicleFilter.Servis,
)

internal const val TAG_ADD = "kendaraan-add"
internal const val TAG_EMPTY_FLEET = "kendaraan-empty-fleet"
internal const val TAG_EMPTY_SEARCH = "kendaraan-empty-search"

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
            onEdit = {},
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