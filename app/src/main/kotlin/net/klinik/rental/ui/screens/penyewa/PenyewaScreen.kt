package net.klinik.rental.ui.screens.penyewa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SearchX
import com.composables.icons.lucide.UserPlus
import net.klinik.rental.R
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnSearchBar
import net.klinik.rental.ui.components.KnSkeletonList
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.components.KnTopBar
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing

/**
 * The renter screen: every person who rents, searchable in place.
 *
 * ## Why this is one screen and not a list plus a detail route
 *
 * The search bar narrows *the same list*. There is no route change and no second
 * destination, which keeps the owner's scroll position and makes the bar a
 * filter rather than a navigation. Master-detail for renters is the shell's job
 * (Story 2.8's landscape layout), not this screen's — see the story's design
 * note, and `KendaraanScreen`'s matching deferral.
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
 * There is no money on this screen at all, so unlike `KendaraanScreen` there is
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
                )

                KnSearchBar(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    onClear = onClearQuery,
                    placeholder = stringResource(R.string.penyewa_search_placeholder),
                    modifier = Modifier.padding(
                        start = spacing.lg,
                        end = spacing.lg,
                        top = spacing.lg,
                    ),
                )

                Box(Modifier.weight(1f)) {
                    when {
                        // Loading first, and mutually exclusive with everything
                        // below. Room's first emission *is* an empty list, so
                        // without this branch the owner reads "Belum ada
                        // Penyewa" on every single load — the exact
                        // empty-looks-like-a-new-account failure the story exists
                        // to prevent.
                        state.isLoading -> KnSkeletonList(
                            rows = SKELETON_ROWS,
                            modifier = Modifier.padding(
                                start = spacing.lg,
                                end = spacing.lg,
                                top = spacing.lg,
                            ),
                        )

                        // Two different emptinesses with two different messages.
                        // A stored-but-unmatched search is not an error and must
                        // not read like an empty account.
                        state.isEmptyList -> PenyewaEmptyList(onAdd = onAdd)
                        state.isEmptySearch -> PenyewaEmptySearch(
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
                                // Keyed on the renter id: removing a row under a
                                // search must not make the remaining rows shift
                                // their saved state onto a different person.
                                key = { it.renter.id },
                            ) { row ->
                                PenyewaRow(row = row, onEdit = { onEdit(row.renter) })
                            }
                        }
                    }
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

/** One renter: name, phone, and the account when there is one. */
@Composable
private fun PenyewaRow(
    row: RenterRow,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(
        R.string.penyewa_edit_description,
        row.renter.name,
    )

    KnListRow(
        title = row.renter.name,
        subtitle = stringResource(
            R.string.penyewa_row_subtitle,
            row.renter.phone,
            row.renter.emergencyContact,
        ),
        modifier = modifier
            .testTag("penyewa-row-${row.renter.id}")
            .semantics { contentDescription = editLabel },
        leading = {
            // `KnAvatar` at its default 44dp, not a 48dp icon button: the leading
            // slot is hard-boxed to 44dp by `KnListRow`, so a larger element
            // would overflow it rather than fill it. The name is exactly the
            // input an avatar's initials are derived from.
            KnAvatar(name = row.renter.name)
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
        onClick = onEdit,
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
internal const val TAG_EMPTY_LIST = "penyewa-empty-list"
internal const val TAG_EMPTY_SEARCH = "penyewa-empty-search"

/** The single banner, whatever its tone — so one test can address it. */
internal const val TAG_BANNER = "penyewa-banner"

/**
 * How many placeholder rows the skeleton draws.
 *
 * A count rather than one block so the loading state has the *shape* of the list
 * it stands in for. Six matches the stated fleet scale without pretending to
 * know the real count — a skeleton showing exactly the final row count would
 * need the count, which is what is still loading.
 */
private const val SKELETON_ROWS = 6

@Preview(showBackground = true)
@Composable
private fun PenyewaScreenPreview() {
    KlinikNetTheme(darkTheme = false) {
        PenyewaScreen(
            state = PenyewaUiState(rows = PREVIEW_ROWS),
            onQueryChange = {},
            onClearQuery = {},
            onAdd = {},
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
