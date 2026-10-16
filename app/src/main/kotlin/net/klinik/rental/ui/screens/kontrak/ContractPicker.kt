package net.klinik.rental.ui.screens.kontrak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.format.shortLabel
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import java.time.DayOfWeek

/**
 * A read-only field that opens a modal list and hands back the chosen item.
 *
 * ## Why this exists rather than a dropdown
 *
 * No `DropdownMenu`, `ExposedDropdownMenuBox`, `RadioButton` or `FilterChip` appears
 * anywhere in `app/src/main`, and the contract form needs two "pick one from a
 * stored list" controls. Material's dropdown is a popup anchored to an anchor box:
 * on a tablet it renders as a narrow menu that clips a long renter name, and it puts
 * a twenty-row list in a popup with no avatar and no second line.
 *
 * `KnModal` is already this design system's answer for "choose from a list" — a real
 * platform `Dialog` that moves with the window and gets the back gesture, with a
 * scrollable body and a footer that stays put — and it holds `KnListRow`s, the same
 * row the renter and fleet lists use, with the same 48dp floor and the same avatar.
 * So both pickers are one component used twice rather than two dropdowns and a third
 * pattern for the fleet.
 *
 * ## Why the field is disabled rather than editable
 *
 * The field shows what was chosen; nothing can be typed into it. A picker whose
 * field accepted free text would invite the owner to type a renter's name, and there
 * is no path from a typed name to a renter id — the contract's foreign key needs the
 * id, and a name that nearly matches a stored row is not a selection. So the tap
 * opens the list and the list is the only way in.
 *
 * `KnField(enabled = false)` with a trailing affordance is the same shape
 * `VehicleFormSheet` uses for its rate field: a value the owner chooses rather than
 * types.
 *
 * ## Why the tags are caller-supplied fixed literals
 *
 * This component used to derive every tag from its `label`, and the label comes from
 * `stringResource`. That made a *copy edit* silently rename every test tag: change
 * the Indonesian word for `Penyewa` and the tests that address the picker stop
 * matching, with nothing to point at the cause. Worse, the sheet also passed its own
 * `Modifier.testTag(TAG_FIELD_RENTER)` into this component, which then applied a
 * second `testTag` to the same node — the later one wins, so the caller's tag never
 * reached the tree at all.
 *
 * So the tag is now a parameter, the caller passes one fixed literal per call site,
 * and this component applies exactly one `testTag` per node.
 *
 * ## Why the modal's open flag is local
 *
 * Whether a picker's list is up is presentation, not screen state: it is not
 * something a rotation has to preserve (the sheet above it is, and that lives in the
 * ViewModel), and putting it in `KontrakUiState` would make the ViewModel track which
 * popup is up. `remember` keeps it in the composition, which is what actually owns
 * it.
 *
 * ## Why it is generic and hands back the item
 *
 * The vehicle call site has to prefill the rate from the vehicle's own stored rate,
 * and the renter call site has to write the renter's name beside their id. Both need
 * the whole selected object, not just its id — so [onSelect] delivers the item and
 * the caller applies the pure helper in `KontrakUiState.kt` that knows what to do
 * with it. Handing back only an id would force the caller to look the item up again
 * from a list it already has, which is a second chance to pair one item's id with
 * another's label.
 *
 * @param label the field's label, read from `strings.xml` by the caller.
 * @param tag the field's test tag, and the stem of the modal's and its rows'. A
 *   fixed literal supplied by the caller — see the class KDoc.
 * @param value the chosen item's display text, or `""` before anything is picked.
 * @param hint the placeholder shown while [value] is empty.
 * @param emptyMessage what to say when there is nothing to choose from. Required and
 *   per-call-site rather than a shared default: one sentence for both pickers would
 *   name neither *which* list is empty nor *where* to go and fix it, and an empty
 *   picker is exactly the state where the owner needs both.
 * @param title the modal's own title, e.g. `Pilih penyewa`.
 * @param error this field's refusal message, or `null`. Cleared by the selection
 *   itself rather than by typing, because there is nothing to type.
 * @param rowKey a stable key per item, so a list that reorders does not move saved
 *   state onto a different person or car.
 */
@Composable
fun <T> ContractPicker(
    label: String,
    tag: String,
    value: String,
    hint: String,
    emptyMessage: String,
    title: String,
    options: List<T>,
    rowTitle: (T) -> String,
    rowSubtitle: (T) -> String,
    rowKey: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val spacing = MaterialTheme.knSpacing
    var open by remember { mutableStateOf(false) }

    KnField(
        label = label,
        value = value,
        // Inert, and never called: the field is disabled and the list is the only
        // way in. `KnField` requires a handler, so this is the honest empty one
        // rather than a second path that could accept typed text.
        onValueChange = {},
        placeholder = hint,
        error = error,
        enabled = false,
        // The one tap target. `KnIconButton` carries the 48dp floor itself, so the
        // chevron is not a glyph-sized target.
        trailingIcon = Lucide.ChevronDown,
        trailingContentDescription = label,
        onTrailingIconClick = { open = true },
        modifier = modifier.testTag(tag),
    )

    KnModal(
        visible = open,
        onDismissRequest = { open = false },
        title = title,
        modifier = Modifier.testTag("$tag-modal"),
    ) {
        if (options.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.lg)
                    .testTag(TAG_PICKER_EMPTY),
            )
        } else {
            // A plain `Column`, not a `LazyColumn`: `KnModal` already caps its own
            // height and scrolls its body, and a lazy list nested in that scroller
            // would fight it. Twenty vehicles is not a list that needs recycling.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                options.forEach { option ->
                    // Keyed on the item's own key, not the position: an unkeyed
                    // composition in a loop is keyed by slot, so a row removed from
                    // the observed list would migrate the next row's saved state
                    // onto a different person.
                    key(rowKey(option)) {
                        KnListRow(
                            title = rowTitle(option),
                            subtitle = rowSubtitle(option),
                            leading = {
                                // `KnAvatar` at its default 44dp: the leading slot
                                // is hard-boxed to 44dp, so a larger element would
                                // overflow it rather than fill it. The title is
                                // exactly the input an avatar's initials come from —
                                // a name for a renter, a plate for a vehicle.
                                KnAvatar(name = rowTitle(option))
                            },
                            modifier = Modifier.testTag("$tag-row-${rowKey(option)}"),
                            onClick = {
                                onSelect(option)
                                open = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The empty picker's tag. One tag for both pickers, because the assertion is about
 * the *state* being rendered — each picker's copy is asserted by its own text, which
 * is what the owner reads.
 */
internal const val TAG_PICKER_EMPTY = "kontrak-picker-empty"

/**
 * The weekday chips, in `DayOfWeek.entries` order.
 *
 * `entries` rather than a hand-written list of the seven constants: a hand-written
 * copy is exactly `entries` order today and silently wrong the moment either the
 * enum or the copy changes. `entries` is Monday-first because `DayOfWeek` is
 * ISO-8601-ordered, which is also the order a working week is read in — and Sunday,
 * the default rest day, is therefore last, which is where the owner most often wants
 * it.
 */
internal val REST_DAY_ORDER: List<DayOfWeek> = DayOfWeek.entries

/**
 * The label for a weekday chip.
 *
 * Read from [shortLabel], which is the same table every date in the app formats
 * through — *not* from a second set of `kontrak_day_*` string resources. Two lists of
 * the same seven words on one screen is how a chip and the `Libur Min` badge beside
 * it end up naming different days; the epic context's `23 Sen`/`24 Sel` vocabulary is
 * already the app's, and it has one definition.
 *
 * `@Composable` only because a caller may want it in a composition; it resolves no
 * resource.
 */
@Composable
internal fun DayOfWeek.label(): String = shortLabel()
