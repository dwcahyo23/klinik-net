package net.klinik.rental.ui.screens.penyewa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.SearchX
import com.composables.icons.lucide.UserPlus
import net.klinik.rental.R
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnAvatarSize
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonSize
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnCard
import net.klinik.rental.ui.components.KnEmptyState
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
 * The renter screen: every person who rents, searchable in place.
 *
 * ## Why this is one screen and not a list plus a detail route
 *
 * The search bar narrows *the same list*. There is no route change and no second
 * destination, which keeps the owner's scroll position and makes the bar a
 * filter rather than a navigation. Master-detail for renters is the shell's job
 * (Story 2.8's landscape layout), not this screen's — see the story's design
 * note, and `MobilScreen`'s matching deferral.
 *
 * ## What is deliberately absent
 *
 * No KPI tile grid, no nav rail, no bottom nav, no FAB. Those are the shell's
 * (Story 2.8) and Story 4.3's.
 *
 * Loading is a skeleton and a refusal is a banner — both added by Story 2.8,
 * which discharged the "no skeleton" note that used to sit here. The four states
 * are: skeleton, empty (two variants), a Danger banner over unchanged content,
 * and a Success snackbar over unchanged content.
 *
 * There is no money on this screen at all, so unlike `MobilScreen` there is
 * no `formatRupiah` call in this file — money is converted in exactly one place
 * and this screen has none to convert.
 *
 * @param onEdit receives the row's renter. Opening the form is a sheet over this
 *   screen, not a navigation, so returning from it restores the owner's place.
 */
@Composable
fun PenyewaScreen(
    state: PenyewaUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: (Renter) -> Unit,
    modifier: Modifier = Modifier,
    snackbarMessage: String? = null,
    onSnackbarDismiss: () -> Unit = {},
    // Promoted from `= onSnackbarDismiss`. The alias was wrong: the two dismiss
    // different messages, so aliasing them means a caller wiring only the
    // confirmation's setter clears refusals through the wrong path — and this
    // screen's own KDoc records a sibling where exactly that left a banner stuck.
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
                    title = stringResource(R.string.penyewa_title),
                    // The renter count lives on the bar rather than in a KPI
                    // tile, so the screen says how much data it is showing
                    // without spending a card on it.
                    subtitle = stringResource(R.string.penyewa_count, state.renterCount),
                    // `renters.html:29-36` — the add button and this screen's own field are both in the
                    // bar. The field is its own for the reason `AppShell.narrowTo` gives: it is
                    // the only control that can widen a list the shell's search narrowed.
                    search = {
                        KnSearchBar(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            onClear = onClearQuery,
                            placeholder = stringResource(R.string.penyewa_search_placeholder),
                            modifier = Modifier
                                .widthIn(max = net.klinik.rental.ui.components.TopBarSearchMaxWidth)
                                .testTag(TAG_SEARCH),
                        )
                    },
                    actions = {
                        KnButton(
                            label = stringResource(R.string.penyewa_add),
                            onClick = onAdd,
                            icon = Lucide.Plus,
                            modifier = Modifier.testTag(TAG_ADD_ACTION),
                        )
                    },
                )

                // The page body — `shared.css:285-289`. No KPI row: `renters.html` has none.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(spacing.xxl),
                ) {
                    KnMasterDetail(
                        list = {
                            when {
                                state.isLoading -> KnSkeletonList()
                                state.isEmptyList -> PenyewaEmptyList(onAdd = onAdd)
                                state.isEmptySearch -> PenyewaEmptySearch(
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
                                        // Keyed on the renter id: removing a row under a
                                        // search must not make the remaining rows shift
                                        // their saved state onto a different person.
                                        key = { it.renter.id },
                                    ) { row ->
                                        PenyewaRow(
                                            row = row,
                                            selected = row.renter.id == state.selectedRow?.renter?.id,
                                            onClick = { onSelect(row.renter.id) },
                                        )
                                    }
                                }
                            }
                        },
                        detail = {
                            val selected = state.selectedRow
                            if (selected != null && !state.isLoading) {
                                PenyewaDetail(
                                    row = selected,
                                    contract = state.selectedContract,
                                    onEdit = { onEdit(selected.renter) },
                                )
                            }
                        },
                    )
                }
            }

            // One snackbar, never two.
            //
            // Both messages are `BottomCenter` with the same padding, so drawing
            // them independently stacks two bars on the same pixels — the error
            // lands on top of a confirmation and the owner reads whichever the
            // renderer puts last. They are also not exclusive upstream: a success
            // followed by a failure leaves both non-null, because neither clears
            // the other. So the error wins — it is the one that has to be read.
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
                        // So the banner's own dismiss control can be addressed, matching the
                        // two sibling screens. The control is a bare glyph inside it with no
                        // label of its own, so without a tag a test can only reach it by
                        // guessing at the surrounding layout.
                        .testTag(TAG_BANNER),
                    onDismiss = {
                        // Dismiss whichever is actually showing, so a queued
                        // confirmation is not silently dropped behind an error.
                                                // Branch on the tone rather than on state.errorMessage, because
                        // the tone is what was actually *drawn*. This screen has no sheet of
                        // its own, so the selection above always falls through to the error
                        // when one exists and the two agree " + [char]0x2014 + "
                        // but reading the state instead would be a second thing to keep in
                        // step with the first, and KontrakScreen (which does have a sheet,
                        // and therefore *can* draw the confirmation over a pending error)
                        // shows why: there, rrorMessage != null is true while the
                        // confirmation is the bar on screen.
                        if (tone == KnBadgeTone.Danger) onErrorDismiss() else onSnackbarDismiss()
                    },
                )
            }
        }
    }
}

/**
 * One renter: name, phone, and the account when there is one.
 *
 * @param selected draws the active edge `KnListRow` owns — the prototype's `.list-row.active`.
 * @param onClick selects the row for the pane. Editing is the **pane's** button
 *   (`renters.html:226`), so reading a renter never opens a form over the list.
 */
@Composable
private fun PenyewaRow(
    row: RenterRow,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KnListRow(
        title = row.renter.name,
        subtitle = stringResource(
            R.string.penyewa_row_subtitle,
            row.renter.phone,
            row.renter.emergencyContact,
        ),
        selected = selected,
        modifier = modifier.testTag("penyewa-row-${row.renter.id}"),
        leading = {
            // `KnAvatar` at its default 44dp, not a 48dp icon button: the leading
            // slot is hard-boxed to 44dp by `KnListRow`, so a larger element
            // would overflow it rather than fill it. The name is exactly the
            // input an avatar's initials are derived from.
            KnAvatar(name = row.renter.name, avatar = row.renter.avatar)
        },
        trailing = {
            // Only when the owner recorded one. A badge reading "-" for a renter
            // paid in cash would be a claim about data the app does not have.
            row.renter.bankAccount?.let { account ->
                KnBadge(
                    text = stringResource(R.string.penyewa_row_account, account),
                    tone = KnBadgeTone.Neutral,
                    modifier = Modifier.padding(top = MaterialTheme.knSpacing.xs),
                )
            }
        },
        onClick = onClick,
    )
}

/**
 * `renters.html:202-279` — the selected renter, their contact details, and their open contract.
 *
 * The three figures the prototype adds above the contract (total setoran, tunggakan, jumlah
 * transaksi) are computed from the payments repository. They are `TunggakanScreen`'s and
 * `KontrakScreen`'s subject, and a third derivation of the same bill is exactly the defect this
 * repo keeps finding — so this pane shows who is renting and on what terms, which is the half
 * those screens do not.
 */
@Composable
private fun PenyewaDetail(
    row: RenterRow,
    contract: RenterContractFacts?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    val renter = row.renter

    KnCard(modifier = modifier.fillMaxWidth(), padding = spacing.xxl) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                verticalAlignment = Alignment.Top,
            ) {
                KnAvatar(name = renter.name, size = KnAvatarSize.Large, avatar = renter.avatar)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = renter.name,
                        style = MaterialTheme.knTypography.displaySmall,
                        color = MaterialTheme.knColors.textPrimary,
                    )
                    Text(
                        text = renter.phone,
                        style = MaterialTheme.knTypography.label,
                        color = MaterialTheme.knColors.textSecondary,
                    )
                }
                KnButton(
                    label = stringResource(R.string.penyewa_edit),
                    onClick = onEdit,
                    icon = Lucide.Pencil,
                    variant = KnButtonVariant.Secondary,
                    size = KnButtonSize.Small,
                    modifier = Modifier.testTag(TAG_DETAIL_EDIT),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                RenterFigure(
                    label = stringResource(R.string.penyewa_detail_phone),
                    value = renter.phone,
                    modifier = Modifier.weight(1f),
                )
                RenterFigure(
                    label = stringResource(R.string.penyewa_detail_emergency),
                    value = renter.emergencyContact,
                    modifier = Modifier.weight(1f),
                )
                RenterFigure(
                    label = stringResource(R.string.penyewa_detail_account),
                    value = renter.bankAccount ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }

            if (contract != null) {
                RenterDivider()
                Text(
                    text = stringResource(R.string.penyewa_detail_contract_title),
                    style = MaterialTheme.knTypography.label,
                    color = MaterialTheme.knColors.textSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xl),
                ) {
                    RenterFigure(
                        label = stringResource(R.string.penyewa_detail_vehicle),
                        value = "${contract.vehiclePlate} · ${contract.vehicleText}",
                        modifier = Modifier.weight(1f),
                    )
                    RenterFigure(
                        label = stringResource(R.string.penyewa_detail_rate),
                        value = contract.rateText,
                        modifier = Modifier.weight(1f),
                    )
                    RenterFigure(
                        label = stringResource(R.string.penyewa_detail_start),
                        value = contract.startText,
                        modifier = Modifier.weight(1f),
                    )
                    RenterFigure(
                        label = stringResource(R.string.penyewa_detail_schedule),
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

/** One labelled figure inside the pane — the prototype's `.kpi` at `padding: var(--sp-4)`. */
@Composable
private fun RenterFigure(label: String, value: String, modifier: Modifier = Modifier) {
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
private fun RenterDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.knColors.divider),
    )
}

/** Nothing stored yet: say what is missing and what to do about it. */
@Composable
private fun PenyewaEmptyList(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    KnEmptyState(
        title = stringResource(R.string.penyewa_empty_title),
        body = stringResource(R.string.penyewa_empty_body),
        icon = Lucide.UserPlus,
        action = {
            KnButton(
                label = stringResource(R.string.penyewa_add),
                onClick = onAdd,
                icon = Lucide.UserPlus,
                modifier = Modifier.testTag(TAG_ADD),
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_EMPTY_LIST),
    )
}

/**
 * Nothing matched the search. Names the query so the owner can tell this apart
 * from an empty account — the two need opposite actions.
 */
@Composable
private fun PenyewaEmptySearch(
    query: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KnEmptyState(
        title = stringResource(R.string.penyewa_empty_search_title),
        body = stringResource(R.string.penyewa_empty_search_body, query),
        icon = Lucide.SearchX,
        action = {
            KnButton(
                label = stringResource(R.string.penyewa_add),
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

internal const val TAG_ADD = "penyewa-add"
/**
 * The bar's add button.
 *
 * Its own tag, because it and the empty state's action draw the same label and both can be on
 * screen at once — the bar is always there. A shared tag would make `onNodeWithTag(TAG_ADD)`
 * ambiguous the moment the list is empty, and a test asserting "the empty state's action" would
 * silently resolve to the bar's. `KontrakScreen` records the same trap.
 */
internal const val TAG_ADD_ACTION = "penyewa-add-action"
internal const val TAG_EMPTY_LIST = "penyewa-empty-list"
internal const val TAG_EMPTY_SEARCH = "penyewa-empty-search"

/** The edit verb inside the detail pane; the row selects rather than edits. */
internal const val TAG_DETAIL_EDIT = "penyewa-detail-edit"

/** The screen's own search field, now in the top bar rather than a row beneath it. */
internal const val TAG_SEARCH = "penyewa-search"

/** The single banner, whatever its tone — so one test can address it. */
internal const val TAG_BANNER = "penyewa-banner"

@Preview(showBackground = true)
@Composable
private fun PenyewaScreenPreview() {
    KlinikNetTheme(darkTheme = false) {
        PenyewaScreen(
            state = PenyewaUiState(rows = PREVIEW_ROWS),
            onQueryChange = {},
            onClearQuery = {},
            onAdd = {},
            onSelect = {},
            onEdit = {},
            onErrorDismiss = {},
        )
    }
}

private val PREVIEW_ROWS = listOf(
    RenterRow(
        Renter(
            id = "1",
            name = "Budi Santoso",
            phone = "08123456789",
            bankAccount = "BCA 1234567890",
            emergencyContact = "08129876543",
        ),
    ),
    RenterRow(
        Renter(
            id = "2",
            name = "Siti Aminah",
            phone = "08129876543",
            bankAccount = null,
            emergencyContact = "08123456789",
        ),
    ),
)
