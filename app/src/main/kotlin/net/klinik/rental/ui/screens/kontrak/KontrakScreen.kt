package net.klinik.rental.ui.screens.kontrak

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.Lucide
import net.klinik.rental.R
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonSize
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.components.KnTopBar
import net.klinik.rental.ui.format.formatTanggalBulan
import net.klinik.rental.ui.format.shortLabel
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The contract screen: every rental, newest first.
 *
 * ## Why the action is on the top bar and not a FAB
 *
 * `PenyewaScreen` puts its add action on the empty state and nowhere else, and
 * `KendaraanScreen` does the same. The epic context's floating action button with
 * its speed dial is Story 2.8's shell, and it explicitly appears on every screen
 * *except* the home screen — so a FAB here would be a second door to the same action
 * the shell is about to add. A top-bar action is what this screen owns, it is
 * reachable whether or not the list is empty, and it does not compete with the
 * shell's later FAB.
 *
 * ## Why there is no search bar and no filter row
 *
 * No acceptance criterion asks for either, and `KnSearchBar`'s IME bug
 * (`spec-2-4:224` — `onSearch = { (onSubmit ?: onClear ?: {})() }` clears the query
 * when no `onSubmit` is passed) would come with the bar. So there is one empty state
 * rather than two, because there is no "no match" state to tell apart from "nothing
 * stored".
 *
 * ## What is deliberately absent
 *
 * No KPI tile grid, no nav rail, no bottom nav, no skeleton, and no end-contract
 * action. All of those are Stories 2.7, 2.8 and 4.3. The one feedback surface is a
 * snackbar, and it appears over the page without changing it.
 *
 * Every rupiah figure and every date on this screen arrives already formatted in
 * [ContractRow]. There is no `formatRupiah` call in this file — money is converted
 * once, in the ViewModel, so two screens cannot disagree about how a rate looks.
 *
 * ## Why this screen mounts its own form sheet
 *
 * The sheet is the only path to this story's verb, so a screen that took `onOpen`
 * and rendered no sheet would ship a story with no reachable action.
 * `MainActivity.kt:131-138` already establishes the shape for `VehicleFormSheet` —
 * the screen composes the sheet when its state says the form is open, and forwards
 * the sheet's three callbacks. This is that shape, with the sheet's own
 * `errorMessage` slot alongside.
 *
 * Navigation is still Story 2.8's: nothing routes *to* this screen yet. Mounting the
 * sheet is not navigation — it is the screen owning the form it opened.
 *
 * @param onOpen opens the form for a new contract. A sheet over this screen, not a
 *   navigation, so returning from it restores the owner's place.
 * @param onCloseForm the sheet's scrim tap and back gesture. Both are wired: a sheet
 *   with no way out except its own buttons is a trap.
 * @param onFormChange the sheet's state transform, hoisted so a rotation cannot lose
 *   half-typed input.
 * @param onSave submits the form.
 * @param formErrorMessage the sheet's *own* error slot. See [ContractFormSheet] on
 *   why an unexpected write failure cannot be shown by the screen's banner.
 */
@Composable
fun KontrakScreen(
    state: KontrakUiState,
    onOpen: () -> Unit,
    // The three sheet callbacks are REQUIRED, not defaulted. Defaulting them to
    // `{}` re-arms the exact defect this amendment exists to close: a caller that
    // wires only `state` and `onOpen` gets a sheet that mounts, renders every
    // control correctly and silently does nothing when tapped. Mounting and wiring
    // are different claims, and a default parameter can only ever fail the second
    // one quietly. The display-only parameters below stay defaulted.
    onCloseForm: () -> Unit,
    onFormChange: ((ContractFormState) -> ContractFormState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    formErrorMessage: String? = null,
    renterOptions: List<Renter> = emptyList(),
    vehicleOptions: List<Vehicle> = emptyList(),
    snackbarMessage: String? = null,
    onSnackbarDismiss: () -> Unit = {},
    // Deliberately NOT defaulted to `onSnackbarDismiss`. That aliasing looked
    // harmless and was a trap: a caller wiring only `onSnackbarDismiss` — which is
    // exactly what a sibling screen's composition does — gets an error banner
    // dismissed through the *confirmation's* setter, so the error stays on screen
    // and the confirmation clears invisibly. Two different messages, two handlers.
    onErrorDismiss: () -> Unit = {},
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
                    title = stringResource(R.string.kontrak_title),
                    // The contract count lives on the bar rather than in a KPI
                    // tile, so the screen says how much data it is showing without
                    // spending a card on it.
                    subtitle = stringResource(R.string.kontrak_count, state.contractCount),
                    actions = {
                        // The bar's action, so the owner can open a contract whether
                        // or not any exist. Sized `Small` because the bar is 72dp
                        // tall and a `Default` button would fill it edge to edge;
                        // `Small` still carries the 48dp floor through `KnButton`.
                        //
                        // Its own tag rather than `TAG_ADD`, which the empty state's
                        // action carries: both draw the same label, so a shared tag
                        // would make `onNodeWithTag(TAG_ADD)` ambiguous the moment a
                        // contract exists — and a test asserting "the empty state's
                        // action" would silently resolve to the top bar's instead.
                        KnButton(
                            label = stringResource(R.string.kontrak_add),
                            onClick = onOpen,
                            icon = Lucide.ClipboardList,
                            size = KnButtonSize.Small,
                            modifier = Modifier.testTag(TAG_ADD_ACTION),
                        )
                    },
                )

                Box(Modifier.weight(1f)) {
                    if (state.isEmpty) {
                        KontrakEmpty(onOpen = onOpen)
                    } else {
                        LazyColumn(
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
                                items = state.rows,
                                // Keyed on the contract id: a row removed under a
                                // write must not make the remaining rows shift their
                                // saved state onto a different rental.
                                key = { it.contract.id },
                            ) { row ->
                                KontrakRow(row = row)
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
            // followed by a failure leaves both non-null, because neither clears the
            // other. So the error wins — it is the one that has to be read.
            //
            // While the sheet is open, though, the error belongs *inside* it: this
            // banner is composed in the screen's own window and the sheet is a
            // separate window layered above, so a banner here would be invisible.
            // The error is therefore suppressed here and handed to the sheet — it is
            // still shown exactly once, in the window that is actually on top.
            val banner: Pair<String, KnBadgeTone>? = state.errorMessage
                ?.takeIf { state.form == null }
                ?.let { it to KnBadgeTone.Danger }
                ?: snackbarMessage?.let { it to KnBadgeTone.Success }

            banner?.let { (message, tone) ->
                KnSnackbar(
                    message = message,
                    tone = tone,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(spacing.lg)
                        // So the banner's own dismiss control can be addressed. The
                        // control is a bare glyph inside it with no label of its own,
                        // so without this a test could only reach it by guessing at
                        // the surrounding layout.
                        .testTag(TAG_BANNER),
                    onDismiss = {
                        // Dismiss whichever banner was actually *drawn*, not
                        // whichever message exists. While the sheet is open the
                        // selection above suppressed the error and fell through to
                        // the confirmation, so branching on `state.errorMessage !=
                        // null` here would clear the hidden error and leave the
                        // visible confirmation on screen forever. The tone travels
                        // with the message that produced it, so one test on it is the
                        // whole decision — and it cannot disagree with what is drawn,
                        // because it *is* what is drawn.
                        if (tone == KnBadgeTone.Danger) onErrorDismiss() else onSnackbarDismiss()
                    },
                )
            }
        }

        // The form is a sheet *over* the list rather than a second destination, so
        // dismissing it restores the owner's place. Composed here, from this
        // screen's own state, the same way `MainActivity` composes
        // `VehicleFormSheet` — the screen owns the form it opened.
        state.form?.let { form ->
            ContractFormSheet(
                state = form,
                renterOptions = renterOptions,
                vehicleOptions = vehicleOptions,
                // The screen's own error slot, handed down because a failure that
                // happens while this sheet is up has nowhere else to appear — see
                // `ContractFormSheet`'s KDoc and the banner note above.
                errorMessage = formErrorMessage ?: state.errorMessage,
                onDismissRequest = onCloseForm,
                onChange = onFormChange,
                onSave = onSave,
            )
        }
    }
}

/**
 * One contract: who rents it and which car, with the rate and the start date.
 *
 * ## Why the row's spoken label names both the renter and the car
 *
 * The label used to be the plate alone, which left TalkBack reading the plate twice
 * — once as the label and once as the row's subtitle — and never reading the
 * renter at all. A row identifies a rental by *who* and *which car*, so the label
 * names both, which is also the pair the visual row leads with (the renter's name as
 * the title, the plate in the subtitle).
 *
 * ## Why this row is not clickable
 *
 * Story 2.7 owns ending a contract and Story 2.6 owns editing its exceptions, and
 * neither exists yet. A tappable row that opened nothing would be a gesture-only
 * path to an action the app cannot perform, which is the opposite of the epic
 * context's "a gesture is never the only path to an important action" — a tap that
 * does nothing is worse than no tap at all, because it teaches the owner the screen
 * is broken. So the row is read-only and the tests assert it carries no click
 * action.
 */
@Composable
private fun KontrakRow(row: ContractRow, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.knSpacing
    val label = stringResource(
        R.string.kontrak_row_description,
        row.renterName,
        row.vehiclePlate,
    )

    KnListRow(
        title = row.renterName,
        subtitle = stringResource(
            R.string.kontrak_row_subtitle,
            row.vehiclePlate,
            row.dateText,
        ),
        modifier = modifier
            .testTag("kontrak-row-${row.contract.id}")
            .semantics { contentDescription = label },
        leading = {
            // `KnAvatar` at its default 44dp, not a 48dp icon button: the leading
            // slot is hard-boxed to 44dp, so a larger element would overflow it
            // rather than fill it. The plate is the identifier the owner reads, and
            // it is exactly the input an avatar's initials come from.
            KnAvatar(name = row.vehiclePlate)
        },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                // Already `formatRupiah`'d in the ViewModel. Assembling money here
                // by hand is how two screens end up showing the same rate two
                // different ways.
                Text(
                    text = row.rateText,
                    style = MaterialTheme.knTypography.figure,
                    color = MaterialTheme.knColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The schedule, as a badge: the rest day for a six-day week, or
                // `7 hari` for a seven-day one. It is a *fact about the contract*
                // rather than a status, so it takes the neutral tone — the semantic
                // four are reserved for status and no fifth colour is invented.
                KnBadge(
                    text = row.scheduleText(),
                    tone = KnBadgeTone.Neutral,
                )
            }
        },
    )
}

/**
 * The schedule as the owner reads it: `Libur Min`, or `7 hari`.
 *
 * Composed here from the stored [DayOfWeek] rather than stored as a string, because
 * two encodings of one fact can disagree — see `Contract`'s KDoc. The `7 hari` form
 * is a *label for the absence of a rest day*, not a second value: it is derived from
 * `restDay == null`, so there is no state in which the two could contradict.
 *
 * The weekday comes from [shortLabel] — the same table the date formatter indexes —
 * rather than from a `kontrak_day_*` string resource. Two lists of the same seven
 * words on one screen is how the badge and a date beside it end up naming different
 * days.
 */
@Composable
private fun ContractRow.scheduleText(): String {
    val restDay = contract.restDay
    return if (restDay == null) {
        stringResource(R.string.kontrak_schedule_seven)
    } else {
        stringResource(R.string.kontrak_row_rest_day, restDay.shortLabel())
    }
}

/** Nothing stored yet: say what is missing and what to do about it. */
@Composable
private fun KontrakEmpty(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    KnEmptyState(
        title = stringResource(R.string.kontrak_empty_title),
        body = stringResource(R.string.kontrak_empty_body),
        icon = Lucide.ClipboardList,
        action = {
            KnButton(
                label = stringResource(R.string.kontrak_add),
                onClick = onOpen,
                icon = Lucide.ClipboardList,
                modifier = Modifier.testTag(TAG_ADD),
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_EMPTY),
    )
}

internal const val TAG_ADD = "kontrak-add"
internal const val TAG_ADD_ACTION = "kontrak-add-action"
internal const val TAG_BANNER = "kontrak-banner"
internal const val TAG_EMPTY = "kontrak-empty"

@Preview(showBackground = true)
@Composable
private fun KontrakScreenPreview() {
    KlinikNetTheme(darkTheme = false) {
        KontrakScreen(
            state = KontrakUiState(rows = PREVIEW_ROWS),
            onOpen = {},
            // Required since review pass 2, so a preview states them explicitly
            // rather than inheriting a silent no-op.
            onCloseForm = {},
            onFormChange = {},
            onSave = {},
        )
    }
}

/**
 * Preview fixtures, built through the *real* formatter.
 *
 * `dateText = formatTanggalBulan(...)` rather than a hand-written `"23 Jum"`: a
 * literal here would be a second, unchecked spelling of the format the screen
 * actually renders, and the preview would keep showing the old one after a format
 * change. `23 Okt` is what 23 October 2026 really renders as.
 */
private val PREVIEW_ROWS = listOf(
    ContractRow(
        contract = Contract(
            id = "1",
            renterId = "r1",
            vehicleId = "v1",
            startDate = LocalDate.of(2026, 10, 23),
            dailyRate = 250_000L,
            restDay = DayOfWeek.SUNDAY,
        ),
        renterName = "Budi Santoso",
        vehiclePlate = "B 1235 TF",
        rateText = "Rp 250.000",
        dateText = formatTanggalBulan(LocalDate.of(2026, 10, 23)),
    ),
    ContractRow(
        contract = Contract(
            id = "2",
            renterId = "r2",
            vehicleId = "v2",
            startDate = LocalDate.of(2026, 10, 20),
            dailyRate = 275_000L,
            restDay = null,
        ),
        renterName = "Siti Aminah",
        vehiclePlate = "B 9876 AB",
        rateText = "Rp 275.000",
        dateText = formatTanggalBulan(LocalDate.of(2026, 10, 20)),
    ),
)
