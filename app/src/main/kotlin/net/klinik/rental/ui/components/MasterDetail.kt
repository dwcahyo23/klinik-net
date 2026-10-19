package net.klinik.rental.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.klinik.rental.ui.theme.knSpacing

/**
 * `.master-detail` from `shared.css:1018-1026` — a list beside the thing it selects.
 *
 * ## The rule, and where it comes from
 *
 * `shared.css:1020` is `grid-template-columns: minmax(0, 400px) minmax(0, 1fr)` with a `--sp-5`
 * gap; `:1033` collapses it to one column on a portrait frame. Both halves are reproduced here:
 * a 400dp list with a 20dp gap beside a flexible detail, stacked when the window is narrow.
 *
 * The list width is **fixed**, not weighted, for the reason `TunggakanScreen` recorded when it
 * first built this by hand: a weighted list on a 1280dp tablet ellipses the figures, and the
 * figures are the whole point of a row. The detail pane takes whatever is left.
 *
 * ## Why `BoxWithConstraints` and not the shell's `Configuration`
 *
 * Because this component is measured where it is drawn. A screen inside a 72dp rail on a 711dp
 * window has 639dp to give, and branching on the *window* width would stack the panes at a width
 * where two of them plainly fit. The threshold is [MasterDetailBreakpoint] — the same 600dp the
 * shell uses, applied to the space this layout actually has.
 *
 * @param list the left pane: rows, their skeleton, or their empty state.
 * @param detail the right pane: the selected item, or `null` content when nothing is selected.
 *   A pane with nothing in it draws nothing rather than an empty state of its own — an empty
 *   *detail* beside a populated list is not a state the owner needs explained.
 */
@Composable
fun KnMasterDetail(
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= MasterDetailBreakpoint) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = spacing.xxl,
                        end = spacing.xxl,
                        top = spacing.xxl,
                        bottom = spacing.xxl,
                    ),
                horizontalArrangement = Arrangement.spacedBy(spacing.xl),
            ) {
                Box(
                    modifier = Modifier
                        .width(MasterDetailListWidth)
                        .fillMaxHeight(),
                ) {
                    list()
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    detail()
                }
            }
        } else {
            // Stacked, per `shared.css:1033`. The list keeps a bounded share of the height so
            // the detail is reachable by scrolling rather than pushed off the screen: a pane
            // that needs the whole window to reach is a pane the owner never sees.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = spacing.xxl,
                        end = spacing.xxl,
                        top = spacing.xxl,
                        bottom = spacing.xxl,
                    ),
                verticalArrangement = Arrangement.spacedBy(spacing.xl),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(MasterDetailStackedListWeight),
                ) {
                    list()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - MasterDetailStackedListWeight),
                ) {
                    detail()
                }
            }
        }
    }
}

/**
 * `minmax(0, 400px)` from `shared.css:1020`.
 *
 * A fixed width rather than a share of the window: the list's job is to be scannable, and a list
 * that widens with the display stops being one. 400dp is what the prototype measured against a
 * 1280dp frame.
 */
private val MasterDetailListWidth = 400.dp

/**
 * Where the panes stop sitting side by side.
 *
 * The same 600dp the shell uses for rail-versus-bottom-nav (`AppShell.kt:377`), because the two
 * answer the same question — "is this window wide enough for two columns?" — and two different
 * numbers would produce a window that has a rail and a stacked master-detail at once.
 */
private val MasterDetailBreakpoint = 600.dp

/**
 * The list's share of a stacked layout.
 *
 * `0.55` rather than half: the list is what the owner scrolls and scans, and the detail is what
 * they read after choosing. Slightly more than half keeps four or five rows visible above the
 * fold, which is what makes a choice possible without scrolling twice.
 */
private const val MasterDetailStackedListWeight = 0.55f
