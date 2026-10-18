package net.klinik.rental.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import net.klinik.rental.ui.theme.knElevation
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knShadow
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
 * field is cleared. Drawing it beneath the bar keeps the destination underneath untouched, so
 * dismissing the results restores exactly where the owner was.
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
    val kn = MaterialTheme.knColors

    Column(modifier = modifier.fillMaxWidth()) {
        KnSearchBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onClear = onClear,
            placeholder = stringResource(R.string.search_global_placeholder),
            modifier = Modifier
                .padding(horizontal = spacing.lg, vertical = spacing.sm)
                .testTag(TAG_GLOBAL_SEARCH_FIELD),
        )

        // Nothing below the bar unless something was typed: an idle field that drew an empty
        // panel would look like a panel that failed to load.
        if (!state.isActive || state.isLoading) return@Column

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                // A bound rather than a height: a query matching one renter draws one row and
                // a query matching everything draws what fits, and the overlay must never
                // cover the whole destination.
                .heightIn(max = SearchOverlayMaxHeight)
                // Above the destination in *colour* as well as in stacking order: the overlay
                // sits on the same window as the screen, so a flat card on a flat background
                // would read as part of the list rather than as a result set.
                .knShadow(MaterialTheme.knElevation.level2, RoundedCornerShape(MaterialTheme.knRadii.lg))
                .testTag(TAG_GLOBAL_SEARCH_RESULTS),
            shape = RoundedCornerShape(MaterialTheme.knRadii.lg),
            color = kn.bgCard,
        ) {
            Column(
                modifier = Modifier
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

internal const val TAG_GLOBAL_SEARCH_FIELD = "shell-search-field"
internal const val TAG_GLOBAL_SEARCH_RESULTS = "shell-search-results"
internal const val TAG_GLOBAL_SEARCH_EMPTY = "shell-search-empty"

/** A result row's tag, keyed on its label — a plate or a name, both unique in this app. */
internal fun searchResultTag(label: String): String = "shell-search-result-$label"
