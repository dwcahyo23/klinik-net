package net.klinik.rental.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SearchX
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnSearchBar
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The shell's search field, and the results that hang beneath it — Story 4.6.
 *
 * ## Why this draws its own overlay rather than a second destination
 *
 * Because a search result is a **shortcut to a place that already exists**, not a place. A
 * results screen would need a back stack entry, a route, and a way back that is not the same
 * as the destination the owner came from — all for a list that disappears the moment the
 * field is cleared.
 *
 * ## Why it is a pill and a dropdown, and not a row
 *
 * It used to be a full-width row drawn above the whole shell, which pushed every screen down by
 * 64dp and put two search fields on the two screens that already had one. The prototype keeps one
 * field **inside** the 72dp top bar (`index.html:92-97`); this follows it, and the destination
 * underneath is never moved. The panel is a `DropdownMenu` so it anchors to the pill, dismisses on
 * an outside tap and on Back, and cannot reflow the bar it hangs from.
 *
 * ## Why the results are a plain column and not a `LazyColumn`
 *
 * Because the whole point of this search is a fleet the owner can also just scroll to. Fourteen
 * renters and eighteen vehicles is a list that fits in a handful of rows; a lazy list here
 * would be machinery for a scale this app does not have, and it would refuse to measure inside
 * the bounded box the overlay draws into.
 *
 * ## Why the empty state names what was *not* searched
 *
 * Because the epic records, deliberately, that notes, transaction references and deposit
 * numbers are out of scope. An owner who scans a receipt number into this field and gets
 * "nothing found" would reasonably conclude the app is broken. Saying which fields were
 * looked at is the difference between an answer and a failure.
 *
 * @param onSelect receives the hit the owner tapped. The caller decides which destination owns
 *   it — this component does not know about navigation, so it cannot get a route wrong.
 */
@Composable
internal fun GlobalSearchBar(
    state: GlobalSearchUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Box(modifier = modifier) {
        KnSearchBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onClear = onClear,
            placeholder = stringResource(R.string.search_global_placeholder),
            // `index.html:92` — `style="max-width:280px"`. Capped rather than weighted, so the pill
            // looks the same on a 360dp phone and a 711dp tablet instead of eating the bar.
            modifier = Modifier
                .widthIn(max = SearchBarMaxWidth)
                .testTag(TAG_GLOBAL_SEARCH_FIELD),
        )

        // Nothing below the bar unless something was typed: an idle field that drew an empty
        // panel would look like a panel that failed to load.
        if (!state.isActive || state.isLoading) return@Box

        // Anchored to the field rather than laid out beneath it — the bar is 72dp tall, and a
        // result set laid out *inside* it would either be clipped or shrink the title row. A
        // `DropdownMenu` draws in its own window below its anchor, dismisses on an outside tap and
        // on Back, and cannot push the destination down; the old full-width row could do all three
        // of the things it now cannot.
        DropdownMenu(
            expanded = true,
            onDismissRequest = onClear,
            // **No bound here.** A `DropdownMenu` measures its content against an infinite
            // height, so a `heightIn` on the menu throws "Vertically scrollable component was
            // measured with an infinity maximum height" the moment the inner column scrolls.
            // The bound belongs on the scrolling column itself.
            modifier = Modifier
                .width(SearchOverlayMaxWidth)
                .testTag(TAG_GLOBAL_SEARCH_RESULTS),
        ) {
            Column(
                modifier = Modifier
                    // A bound rather than a height: a query matching one renter draws one row and
                    // a query matching everything draws what fits, and the overlay must never
                    // cover the whole destination.
                    .heightIn(max = SearchOverlayMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (state.isEmpty) {
                    KnEmptyState(
                        title = stringResource(R.string.search_empty_title),
                        body = stringResource(R.string.search_empty_body, state.query),
                        icon = Lucide.SearchX,
                        modifier = Modifier.testTag(TAG_GLOBAL_SEARCH_EMPTY),
                    )
                    return@Column
                }

                if (state.renters.isNotEmpty()) {
                    GroupHeading(stringResource(R.string.search_group_renters))
                    state.renters.forEach { hit ->
                        ResultRow(hit, onSelect)
                    }
                }
                if (state.vehicles.isNotEmpty()) {
                    GroupHeading(stringResource(R.string.search_group_vehicles))
                    state.vehicles.forEach { hit ->
                        ResultRow(hit, onSelect)
                    }
                }
            }
        }
    }
}

/** A group's heading. Small and quiet: the rows are the content, this is the label. */
@Composable
private fun GroupHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.knTypography.label,
        color = MaterialTheme.knColors.textSecondary,
        modifier = Modifier.padding(
            start = MaterialTheme.knSpacing.md,
            top = MaterialTheme.knSpacing.sm,
            bottom = MaterialTheme.knSpacing.xs,
        ),
    )
}

/** One result, addressed by its label so two hits of one kind cannot share a tag. */
@Composable
private fun ResultRow(hit: SearchResult, onSelect: (SearchResult) -> Unit) {
    KnListRow(
        title = hit.label,
        subtitle = hit.detail,
        onClick = { onSelect(hit) },
        modifier = Modifier.testTag(searchResultTag(hit.label)),
    )
}

/** A bound on the result overlay, so a broad query cannot cover the destination. */
private val SearchOverlayMaxHeight = 320.dp

/**
 * The panel's width.
 *
 * The prototype caps the *field* at 280px and lets results run as wide as the page. A dropdown is
 * narrower than the page by design, and 360dp is wide enough for `B 1235 TF — Toyota Avanza` on one
 * line before it ellipses. Fixed rather than `fillMaxWidth`, because the dropdown's parent is the
 * 280dp pill and a match-parent width would make the two disagree the moment the cap changes.
 */
private val SearchOverlayMaxWidth = 360.dp

/** `index.html:92` — `style="max-width:280px"`. */
private val SearchBarMaxWidth = 280.dp

internal const val TAG_GLOBAL_SEARCH_FIELD = "shell-search-field"
internal const val TAG_GLOBAL_SEARCH_RESULTS = "shell-search-results"
internal const val TAG_GLOBAL_SEARCH_EMPTY = "shell-search-empty"

/** A result row's tag, keyed on its label — a plate or a name, both unique in this app. */
internal fun searchResultTag(label: String): String = "shell-search-result-$label"
