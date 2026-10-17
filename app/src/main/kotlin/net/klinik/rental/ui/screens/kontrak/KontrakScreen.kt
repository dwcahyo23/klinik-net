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
import net.klinik.rental.ui.components.KnSkeletonList
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
 * No KPI tile grid, no nav rail, no bottom nav, and no end-contract action. The
 * nav rail and bottom nav are the shell's, and the end-contract action is Story
 * 2.7's.
 *
 * Loading is a skeleton and a refusal is a banner, both added by Story 2.8,
 * which discharged the "no skeleton" note that used to sit here. The four states
 * are: skeleton, empty, a Danger banner over unchanged content, and a Success
 * snackbar over unchanged content.
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
 * ## Why this screen also owns the exception sheet
 *
 * `NavGraph.kt` mounts `ContractFormSheet` from *this* screen and deliberately does
 * not mount a second sheet of its own — two `ModalBottomSheet` windows over one state
 * was the only `high` Story 2.8's review found, and nesting one sheet's window inside
 * another's is the same shape. So `ExceptionFormSheet` is mounted here too, beside
 * the open-form sheet, and never from `KontrakRoute`.
 *
 * Only one of the two can be open at a time: the exception sheet is reached by
 * tapping a row of the *list*, which is behind the open-form sheet's scrim, and the
 * two hold separate state with separate confirmations. That is a consequence of the
 * geometry rather than an invariant somebody has to maintain — which is the point.
 *
 * ## Why the screen's banner is suppressed while either sheet is up
 *
 * A `ModalBottomSheet` is a separate window layered above this one, so a banner
 * composed here would sit *beneath* it and be invisible. While the open form is up
 * the error is handed down to `ContractFormSheet`; while the exception sheet is up it
 * is handed down to `ExceptionFormSheet`. Either way it is still shown exactly once,
 * in the window that is actually on top. The tone travels with the message that
 * produced it, so the dismiss handler closes the banner that was *drawn* — see the
 * `onDismiss` comment below.
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
 *
 * @param onOpenExceptions opens the exception sheet on one contract. Required, not
 *   defaulted, for the reason `onCloseForm` is: a defaulted lambda produces a control
 *   that renders and silently does nothing — and this one is the row's only verb.
 * @param onCloseExceptions the exception sheet's scrim tap and back gesture.
 * @param onExceptionChange the exception sheet's state transform, hoisted for the
 *   same reason `onFormChange` is.
 * @param onAddException submits the exception form.
 * @param onRequestRemove opens the impact dialog for one row; the row's id travels.
 * @param onConfirmRemove the confirmed half of the remove.
 * @param onCancelRemove the impact dialog's dismissal — scrim, back and Ghost button,
 *   which are one decision and therefore one handler.
 * @param exceptionErrorMessage the exception sheet's *own* banner slot, for the same
 *   window reason as [formErrorMessage].
 *
 * ## Where a successful write is confirmed
 *
 * The confirmation is **the sheet's**, not this screen's, while the sheet is up. A
 * snackbar drawn here is in the window beneath the sheet, so it would stay invisible
 * until the owner dismissed the sheet — which is exactly the moment they want
 * to read it, and an owner who marked a day and saw nothing would reasonably mark it
 * again. So `snackbarMessage` is handed down to the sheet and drawn there, and this
 * screen's own banner stays suppressed for as long as either sheet is up.
 *
 * Once the sheet is gone the same message draws here, over the list, because by then
 * there is no window above it. One message, two windows it can be drawn in, one
 * setter, so a dismissal cannot leave the bar stuck whichever window was showing it.
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
    // Six more, required for exactly the same reason. A row that opens nothing is the
    // defect this set exists to close, and a sheet of exception rows whose Add is
    // wired to `{}` is the same defect one level down.
    onOpenExceptions: (ContractRow) -> Unit,
    onCloseExceptions: () -> Unit,
    onExceptionChange: ((ExceptionFormState) -> ExceptionFormState) -> Unit,
    onAddException: () -> Unit,
    onRequestRemove: (String) -> Unit,
    onConfirmRemove: () -> Unit,
    // The seventh, and separate from `onRequestRemove` on purpose: tapping a row
    // asks which row is about to be removed, and dismissing the dialog retracts that
    // question. One handler wired to both would make a cancelled confirmation arm a
    // delete the owner never confirmed.
    onCancelRemove: () -> Unit,
    modifier: Modifier = Modifier,
    formErrorMessage: String? = null,
    exceptionErrorMessage: String? = null,
    renterOptions: List<Renter> = emptyList(),
    vehicleOptions: List<Vehicle> = emptyList(),
    snackbarMessage: String? = null,
    onSnackbarDismiss: () -> Unit = {},
    // Deliberately NOT defaulted to `onSnackbarDismiss`. That aliasing looked
    // harmless and was a trap: a caller wiring only `onSnackbarDismiss` — which is
    // exactly what a sibling screen's composition does — gets an error banner
    // dismissed through the *confirmation's* setter, so the error stays on screen
    // and the confirmation clears invisibly. Two different messages, two handlers.
    //
    // And now not defaulted to `{}` either: a no-op default produces the same
    // symptom by a different route — the banner is unreachable, and nothing at the
    // call site says so. A required parameter turns that into a compile error.
    onErrorDismiss: () -> Unit,
    /**
     * The exception sheet's own error dismiss.
     *
     * Its own handler rather than aliased to [onErrorDismiss], for the same reason
     * [onErrorDismiss] is not aliased to [onSnackbarDismiss]: the two dismiss
     * different messages, and sharing a setter is how one message is cleared through
     * the other's channel while staying on screen. Not defaulted to `{}` either —
     * that makes the sheet's banner unreachable and nothing at the call site says so.
     */
    onExceptionErrorDismiss: () -> Unit,
    // Story 2.7's four, required for the same reason the seven above are.
    onOpenEnd: (ContractRow) -> Unit,
    onCloseEnd: () -> Unit,
    onEndChange: ((EndContractFormState) -> EndContractFormState) -> Unit,
    onConfirmEnd: () -> Unit,
    /**
     * The end sheet's own error slot and dismiss, as its own pair.
     *
     * Three sheets means three windows means three message slots. Aliasing any of these
     * to another is how one message is cleared through another's channel while staying
     * on screen — which is the exact trap [onErrorDismiss]'s KDoc records.
     */
    endErrorMessage: String? = null,
    onEndErrorDismiss: () -> Unit,
    // Story 3.6's four, required for the same reason the two sets above are.
    onOpenSettle: (ContractRow) -> Unit,
    onCloseSettle: () -> Unit,
    onSettleChange: ((SettleFormState) -> SettleFormState) -> Unit,
    onConfirmSettle: () -> Unit,
    /** The settlement sheet's own error slot and dismiss, as its own pair. */
    settleErrorMessage: String? = null,
    onSettleErrorDismiss: () -> Unit,
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
                    when {
                        // Loading first, and mutually exclusive with everything
                        // below. Room's first emission *is* an empty list, so
                        // without this branch the owner reads "Belum ada Kontrak"
                        // on every single load — the exact
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

                        state.isEmpty -> KontrakEmpty(onOpen = onOpen)
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
                                items = state.rows,
                                // Keyed on the contract id: a row removed under a
                                // write must not make the remaining rows shift their
                                // saved state onto a different rental.
                                key = { it.contract.id },
                            ) { row ->
                                KontrakRow(
                                    row = row,
                                    // The row's own click opens the exceptions, which
                                    // is what it has done since Story 2.6 gave it a verb.
                                    // Ending is a **second** verb and gets its own
                                    // control rather than displacing this one: the
                                    // exception list is where the owner goes to read
                                    // what a rental has been excused, and it is also
                                    // usable on an ended contract, so it cannot be the
                                    // one that disappears.
                                    onClick = { onOpenExceptions(row) },
                                    // `null` on an ended row, so `KontrakRow` draws no
                                    // end control at all — absent, not disabled, for
                                    // the reason `ExceptionFormSheet` unmounts its add
                                    // block rather than greying it out.
                                    onEndClick = if (row.canEnd) {
                                        { onOpenEnd(row) }
                                    } else {
                                        null
                                    },
                                    settleClick = { onOpenSettle(row) },
                                )
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
            // While **any** sheet is open **both** messages belong *inside* it: this
            // banner is composed in the screen's own window and a sheet is a separate
            // window layered above, so a bar drawn here would be invisible. They are
            // therefore suppressed here and handed down to whichever sheet is up —
            // each is still shown exactly once, in the window that is actually on top.
            //
            // Three sheets as of Story 2.7. This was the condition to watch: adding a
            // sheet and forgetting this line draws a confirmation beneath it, which
            // reads to the owner as "nothing happened".
            val banner: Pair<String, KnBadgeTone>? = if (
                state.form != null || state.exceptionSheet != null || state.endSheet != null
            ) {
                // Suppressed entirely, confirmation included: both are handed to
                // whichever sheet is up, and a bar drawn here would be beneath it.
                null
            } else {
                state.errorMessage
                    ?.let { it to KnBadgeTone.Danger }
                    ?: snackbarMessage?.let { it to KnBadgeTone.Success }
            }

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

        // And the exception sheet, mounted from **this** file beside the open form
        // and never from `KontrakRoute` — see the class KDoc on why two sheets over
        // one state is the defect. `onCancelRemove` shares one handler across the
        // dialog's scrim, back gesture and Ghost button because those are one
        // decision, not three.
        state.exceptionSheet?.let { sheet ->
            ExceptionFormSheet(
                state = sheet,
                rows = state.exceptionRows,
                errorMessage = exceptionErrorMessage ?: state.errorMessage,
                onClose = onCloseExceptions,
                onChange = onExceptionChange,
                onAdd = onAddException,
                onRequestRemove = onRequestRemove,
                onConfirmRemove = onConfirmRemove,
                onCancelRemove = onCancelRemove,
                // Both messages into the sheet's own window — see the class KDoc on
                // where a successful write is confirmed, and why "after the sheet closes"
                // is too late to be useful.
                successMessage = snackbarMessage,
                // The *same* setter, not a second one: it is one message, and two
                // setters is how a dismissal ends up clearing something the owner did
                // not dismiss.
                onDismissSuccess = onSnackbarDismiss,
                onDismissError = onExceptionErrorDismiss,
            )
        }

        // And the end sheet — the third, mounted from **this** file for the same reason
        // the other two are. `state.endSheet` and the two above are mutually exclusive in
        // practice (the row's verbs open one sheet at a time) but nothing *enforces* it,
        // so this is the line that would be wrong if two ever overlapped: two
        // `ModalBottomSheet` windows over one state is the `high` Story 2.8's review found.
        state.endSheet?.let { sheet ->
            EndContractSheet(
                state = sheet,
                errorMessage = endErrorMessage ?: state.errorMessage,
                onClose = onCloseEnd,
                onChange = onEndChange,
                onConfirmEnd = onConfirmEnd,
                // Its own dismiss pair, not the exception sheet's: three windows, three
                // message slots, and sharing a setter is how one message is cleared
                // through another's channel while staying on screen.
                successMessage = snackbarMessage,
                onDismissSuccess = onSnackbarDismiss,
                onDismissError = onEndErrorDismiss,
            )
        }

        // And the FR-9b sheet, the fourth, beside its three siblings rather than inside
        // any of them — two `ModalBottomSheet` windows over one state is the defect
        // Story 2.8's review found as its only `high`.
        state.settleSheet?.let { sheet ->
            SettleContractSheet(
                state = sheet,
                errorMessage = settleErrorMessage ?: state.errorMessage,
                onClose = onCloseSettle,
                onChange = onSettleChange,
                onConfirmSettle = onConfirmSettle,
                successMessage = snackbarMessage,
                onDismissSuccess = onSnackbarDismiss,
                onDismissError = onSettleErrorDismiss,
            )
        }
    }
}

/**
 * One contract: who rents it and which car, with the rate, the start date and how
 * many days it is not charged.
 *
 * ## Why the row's spoken label names both the renter and the car
 *
 * The label used to be the plate alone, which left TalkBack reading the plate twice
 * — once as the label and once as the row's subtitle — and never reading the
 * renter at all. A row identifies a rental by *who* and *which car*, so the label
 * names both, which is also the pair the visual row leads with (the renter's name as
 * the title, the plate in the subtitle).
 *
 * ## Why the row is now clickable, and what it opens
 *
 * It was deliberately not: Story 2.7 owns ending a contract and this story owns
 * editing its exceptions, and a tappable row that opened nothing would be a
 * gesture-only path to an action the app cannot perform. This is the story that gives
 * it the verb, so the row now opens the exception sheet — a sheet *over the list*,
 * not a destination, so dismissing restores the owner's place.
 *
 * ## Why the exception count is on the row
 *
 * The AC asks the owner to see which rentals carry compensation days **before**
 * opening anything, so the count cannot live only inside the sheet. It is drawn only
 * when it is non-zero: a `0` on every row would be a claim about data nobody asked
 * for, and a badge that is always there stops being read as news.
 *
 * ## Why ending gets a second control rather than replacing the row's click
 *
 * Two verbs, two affordances. The row itself opens the exceptions — the place the owner
 * reads what a rental has been excused, which is also usable on an ended contract, so it
 * is the wrong thing to take away. Ending is the destructive, once-per-rental action and
 * wants a control that *says* "Akhiri" rather than a gesture nobody can distinguish from
 * the row's other job.
 *
 * @param onEndClick `null` on an ended contract, and the row then draws **no** end
 *   control at all. Absent rather than disabled, for the reason
 *   `ExceptionFormSheet` unmounts its add block: a greyed-out "Akhiri" on a contract
 *   that already ended still claims the action is available, and the repository would
 *   refuse it anyway.
 */
@Composable
private fun KontrakRow(
    row: ContractRow,
    onClick: () -> Unit,
    onEndClick: (() -> Unit)?,
    /**
     * The settlement verb's handler.
     *
     * Required rather than nullable because this control is offered on **every** row
     * that carries no settlement yet, open or ended — the inverse of [onEndClick]'s
     * rule, and for the reason the button's own comment gives: FR-9b's day-one exit is
     * needed most on the ended contracts the end verb is hidden from.
     */
    settleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                // And the compensation count, only when there is one. Neutral tone
                // for the same reason as the schedule badge above: this is a fact
                // about the contract, not a status, and the semantic four colours
                // are reserved for status.
                if (row.exceptionCount > 0) {
                    KnBadge(
                        text = stringResource(
                            R.string.kontrak_exception_count,
                            row.exceptionCount,
                        ),
                        tone = KnBadgeTone.Neutral,
                    )
                }
                // The end verb, as a *labelled* control rather than an icon.
                //
                // Every other control on this row is a badge or the row's own click, so
                // a bare icon here would be a third gesture shape and — being inside a
                // row that is itself clickable — a target the owner has to aim at
                // deliberately. Spelling it `Akhiri` is what makes it distinguishable
                // from "open this row" without a dialog to explain it.
                //
                // `null` on an ended row, so nothing is drawn at all. See the KDoc's
                // note on absence rather than disabled.
                onEndClick?.let { end ->
                    KnButton(
                        label = stringResource(R.string.kontrak_end_action),
                        onClick = end,
                        variant = KnButtonVariant.Ghost,
                        size = KnButtonSize.Small,
                        modifier = Modifier.testTag("kontrak-row-end-${row.contract.id}"),
                    )
                }
                // And the settlement verb, drawn on **every** row rather than only an
                // open one — the deliberate opposite of the end verb above.
                //
                // FR-9b exists for contracts that ended months ago with days paid
                // elsewhere, which is exactly the row `canEnd` hides the end verb from.
                // Requiring an open contract would make the day-one exit unavailable on
                // the contracts that need it most, so this control is offered wherever a
                // contract is.
                //
                // `null` once the contract is already settled, so a contract carrying a
                // settlement is not offered a second one — absence rather than disabled,
                // the same rule as the end verb.
                if (row.settledThroughText == null) {
                    KnButton(
                        label = stringResource(R.string.kontrak_settle_action),
                        onClick = settleClick,
                        variant = KnButtonVariant.Ghost,
                        size = KnButtonSize.Small,
                        modifier = Modifier.testTag("kontrak-row-settle-${row.contract.id}"),
                    )
                }
                // And, on a settled contract, the day it is settled through — beside the
                // end date rather than in place of it, because the two answer different
                // questions and a contract can carry both.
                row.settledThroughText?.let { settled ->
                    Text(
                        text = stringResource(R.string.kontrak_row_settled, settled),
                        style = MaterialTheme.knTypography.label,
                        color = MaterialTheme.knColors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("kontrak-row-settled-${row.contract.id}"),
                    )
                }
                // And, on an ended row, *when* it ended — in place of the start date's
                // subtitle. The owner reconciling a bill needs the end, and a row that
                // only said when the rental began leaves the question open.
                row.endDateText?.let { ended ->
                    Text(
                        text = stringResource(R.string.kontrak_row_ended, ended),
                        // `label` and not a dedicated caption style: the design system's scale is
                        // body / label / figure, and this line is the same class of text as
                        // the badges above it — so it uses their style rather than
                        // introducing a fourth size nobody else on this screen draws.
                        style = MaterialTheme.knTypography.label,
                        color = MaterialTheme.knColors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("kontrak-row-ended-${row.contract.id}"),
                    )
                }
            }
        },
        onClick = onClick,
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
            // Required for the same reason — a row that opens nothing in a preview
            // would render exactly like a row that works.
            onOpenExceptions = {},
            onCloseExceptions = {},
            onExceptionChange = {},
            onAddException = {},
            onRequestRemove = {},
            onConfirmRemove = {},
            onCancelRemove = {},
            onErrorDismiss = {},
            onExceptionErrorDismiss = {},
            // And Story 2.7's five, for the same reason: a preview whose rows show an
            // end control wired to `{}` would look exactly like one that works.
            onOpenEnd = {},
            onCloseEnd = {},
            onEndChange = { it },
            onConfirmEnd = {},
            onEndErrorDismiss = {},
            // And Story 3.6's six, for the same reason: a preview row whose settlement
            // control is wired to `{}` would look exactly like one that works, and FR-9b
            // is not undoable.
            onOpenSettle = {},
            onCloseSettle = {},
            onSettleChange = { it },
            onConfirmSettle = {},
            onSettleErrorDismiss = {},
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
        // One row carries compensation days and one does not, so the preview shows
        // the badge's presence *and* its absence — a preview that only had a counted
        // row would make a badge that draws on every row look right.
        exceptionCount = 2,
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
