package net.klinik.rental.ui.screens.pengaturan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonSize
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnSkeletonList
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.components.KnTopBar
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The settings screen: the business accounts transfers arrive at.
 *
 * ## Why this is a destination rather than a sheet off another screen
 *
 * Story 3.1's acceptance criterion opens with *"saya membuka Pengaturan"*, and the app had no
 * such screen. It is the fourth `Destination` rather than a top-bar action on the fleet
 * screen for two reasons: Epic 5's configuration story needs the same place, so this is the
 * first tenant of a shared destination rather than a one-off; and `AppShell` derives both the
 * rail and the bottom nav from the enum, so the fourth entry appears in both orientations
 * with no second edit.
 *
 * ## The four states
 *
 * - **Loading** — a skeleton shaped like the rows that will arrive. Mutually exclusive with
 *   everything below, and checked *first*: the database's first emission is an empty list, so
 *   without this branch the owner reads "Belum ada rekening" on every load — the
 *   empty-looks-like-a-new-account failure the epic context forbids.
 * - **Empty** — says *what to do*, and says why it matters: adding an account is what makes
 *   recording a transfer possible.
 * - **Error** — a Danger banner over whatever content is already there, never an empty list
 *   that looks like a fresh install.
 * - **Success** — a snackbar that does not change screen state.
 *
 * ## What is deliberately absent
 *
 * No search bar: the list is a handful of accounts and no acceptance criterion asks for one,
 * and `KnSearchBar`'s IME bug comes with the bar. No delete control: see
 * `BusinessAccountDao`'s KDoc — the table that would make an account "in use" is Story 3.2's.
 * No numpad: an account number is an identifier, not an amount.
 *
 * @param onEdit receives the row's account. Opening the form is a sheet over this screen, not
 *   a navigation, so returning from it restores the owner's place.
 */
@Composable
fun PengaturanScreen(
    state: PengaturanUiState,
    onAdd: () -> Unit,
    onEdit: (AccountRow) -> Unit,
    onCloseForm: () -> Unit,
    onFormChange: ((AccountFormState) -> AccountFormState) -> Unit,
    onSave: () -> Unit,
    // Story 4.4's identity sheet. Required for the same reason `onErrorDismiss` is: each of
    // these renders a control, and a defaulted lambda is a control that does nothing.
    onOpenIdentity: () -> Unit,
    onCloseIdentity: () -> Unit,
    onIdentityChange: ((IdentityFormState) -> IdentityFormState) -> Unit,
    onSaveIdentity: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarMessage: String? = null,
    onSnackbarDismiss: () -> Unit = {},
    // The identity sheet's own banner pair. A **third** message slot on this screen, and the
    // reason is the one `KontrakScreen` gives for its three: two `BottomCenter` bars drawn
    // independently stack on the same pixels, so a shared pair would place the screen's
    // failure and the sheet's on top of each other — and one form's dismissal would clear the
    // other's message.
    identityErrorMessage: String? = null,
    onIdentityErrorDismiss: () -> Unit,
    // Required, not defaulted. A defaulted lambda produces a control that renders and
    // silently does nothing — the failure the required-parameter rule exists to prevent, and
    // it has caught unwired call sites in five test files across two stories.
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
                    title = stringResource(R.string.pengaturan_title),
                    // The account count lives on the bar rather than in a KPI tile, so the
                    // screen says how much data it is showing without spending a card on it.
                    subtitle = stringResource(
                        R.string.pengaturan_account_count,
                        state.accountCount,
                    ),
                    actions = {
                        // The bar's action, so the owner can add an account whether or not any
                        // exist. `Small` because the bar is 72dp tall and a `Default` button
                        // would fill it edge to edge; `Small` still carries the 48dp floor
                        // through `KnButton`.
                        //
                        // Its own tag rather than the empty state's, which draws the same
                        // label: a shared tag would make a test asserting "the empty state's
                        // action" silently resolve to the top bar's the moment an account
                        // exists — the trap `KontrakScreen` records.
                        KnButton(
                            label = stringResource(R.string.pengaturan_add_action),
                            onClick = onAdd,
                            icon = Lucide.Landmark,
                            size = KnButtonSize.Small,
                            modifier = Modifier.testTag(TAG_ADD_ACTION),
                        )
                    },
                )

                // Story 4.4's identity block, **above** the list and outside its `when`.
                //
                // Outside, because the list's four states are the *account list's*, and the
                // identity is a separate thing whose visibility has nothing to do with how
                // many accounts exist: a fresh install with no accounts still needs to be
                // able to name its business. Putting it inside the `isEmpty` branch would
                // hide the very form a first run most needs.
                IdentitySection(
                    name = state.identity?.name,
                    // Disabled until the identity has been read. The action is not a
                    // navigation — it opens a form seeded from storage — so a tap before the
                    // read lands would open the form on the **default** and the owner's next
                    // save would write that over their real business name. The same reason
                    // `KnNumPad`'s Save is gated rather than merely flashing.
                    enabled = state.identity != null,
                    onOpen = onOpenIdentity,
                )

                Box(Modifier.weight(1f)) {
                    when {
                        state.isLoading -> KnSkeletonList(
                            rows = SKELETON_ROWS,
                            modifier = Modifier.padding(
                                start = spacing.lg,
                                end = spacing.lg,
                                top = spacing.lg,
                            ),
                        )

                        state.isEmpty -> PengaturanEmpty(onAdd = onAdd)

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
                                // Keyed on the account id: an edit that re-emits must not make
                                // the remaining rows shift their saved state onto a different
                                // account.
                                key = { it.account.id },
                            ) { row ->
                                AccountListRow(row = row, onEdit = { onEdit(row) })
                            }
                        }
                    }
                }
            }

            // One snackbar, never two. Both messages are `BottomCenter` with the same padding,
            // so drawing them independently stacks two bars on the same pixels. They are also
            // not exclusive upstream: a success followed by a failure leaves both non-null,
            // because neither clears the other. So the error wins — it is the one that has to
            // be read.
            //
            // While the form sheet is up both belong *inside* it: this banner is composed in
            // the screen's own window and a sheet is a separate window layered above, so a bar
            // drawn here would be invisible. Same rule as `KontrakScreen`'s three sheets.
            val banner: Pair<String, KnBadgeTone>? = if (state.form != null || state.identityForm != null) {
                null
            } else {
                state.errorMessage?.let { it to KnBadgeTone.Danger }
                    ?: snackbarMessage?.let { it to KnBadgeTone.Success }
            }

            banner?.let { (message, tone) ->
                KnSnackbar(
                    message = message,
                    tone = tone,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(spacing.lg)
                        .testTag(TAG_BANNER),
                    onDismiss = {
                        // Branch on the tone rather than on `state.errorMessage`, because the
                        // tone is what was actually *drawn*. Reading the state instead would be
                        // a second thing to keep in step with the first.
                        if (tone == KnBadgeTone.Danger) onErrorDismiss() else onSnackbarDismiss()
                    },
                )
            }
        }

        // The form is a sheet *over* the list, mounted from this file, so dismissing it
        // restores the owner's place.
        state.form?.let { form ->
            AccountFormSheet(
                state = form,
                errorMessage = state.errorMessage,
                onDismissRequest = onCloseForm,
                onChange = onFormChange,
                onSave = onSave,
                // Both messages into the sheet's own window — see the banner note above.
                successMessage = snackbarMessage,
                onDismissSuccess = onSnackbarDismiss,
                onDismissError = onErrorDismiss,
            )
        }
        // And the identity sheet beside it, never inside it: two `ModalBottomSheet` windows
        // over one state is the defect Story 2.8's review found as its only `high`, and the
        // account and identity forms are never open at once — see [openIdentityForm]'s guard.
        //
        // Its **own** banner pair, not the screen's. The screen's `errorMessage` is the
        // *account* form's refusal: passing it here would draw an account refusal inside the
        // identity window, and its dismiss would clear the other form's message. That is the
        // shared-channel defect `KontrakScreen` gives each of its three sheets a private
        // message slot to avoid, and it was the shape `TunggakanScreen` shipped in Story 4.1.
        state.identityForm?.let { form ->
            IdentityFormSheet(
                state = form,
                errorMessage = state.identityErrorMessage,
                onDismissRequest = onCloseIdentity,
                onChange = onIdentityChange,
                onSave = onSaveIdentity,
                successMessage = state.identitySuccessMessage,
                onDismissSuccess = onSnackbarDismiss,
                onDismissError = onIdentityErrorDismiss,
            )
        }
    }
}

/**
 * Story 4.4's identity block: the business name, and the action that edits it.
 *
 * ## Why the name is shown and the address and phone are not
 *
 * Because the acceptance criterion says so twice, in the affirmative and then in the negative:
 * *"kalau ternyata tidak dipakai di layar mana pun, keduanya tidak muncul di layar mana pun."*
 * Only the name earns its place here — it is the value Story 4.2 quotes in a message a tenant
 * reads. The other two are stored and available, and a screen that rendered them would be
 * putting business contact details on a screen whose job is something else.
 *
 * ## Why the block is a card and not another top-bar action
 *
 * Because the top bar's one action is already "add an account", and two actions on a bar is a
 * decision about which one the owner wants — on a screen where one of them is a first-run
 * requirement and the other is occasional. A labelled block says what it is instead.
 */
@Composable
private fun IdentitySection(
    name: String?,
    enabled: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    Surface(
        modifier = modifier
            .padding(horizontal = spacing.lg)
            .padding(top = spacing.lg)
            .testTag(TAG_IDENTITY_SECTION),
        color = MaterialTheme.knColors.bgCard,
        contentColor = MaterialTheme.knColors.textPrimary,
        shape = RoundedCornerShape(MaterialTheme.knRadii.lg),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.pengaturan_identity_section),
                style = MaterialTheme.knTypography.label,
                color = MaterialTheme.knColors.textSecondary,
            )
            Text(
                // The stored name, or the default — `BusinessIdentityRepository.identity()`
                // already applies it, so this screen never holds a second copy of it. Showing
                // the default before anything is saved is deliberate: the owner should see
                // what the message would quote rather than discover it after sending one.
                text = name ?: stringResource(R.string.pengaturan_identity_section_body),
                style = if (name != null) {
                    MaterialTheme.knTypography.title
                } else {
                    MaterialTheme.knTypography.body
                },
                color = if (name != null) {
                    MaterialTheme.knColors.textPrimary
                } else {
                    MaterialTheme.knColors.textSecondary
                },
                modifier = Modifier.testTag(TAG_IDENTITY_NAME),
            )
            KnButton(
                label = stringResource(
                    if (name != null) {
                        R.string.pengaturan_identity_edit_action
                    } else {
                        R.string.pengaturan_identity_action
                    },
                ),
                onClick = onOpen,
                variant = KnButtonVariant.Secondary,
                size = KnButtonSize.Small,
                // See the call site: a live button here would open a form seeded from a
                // read that has not landed.
                enabled = enabled,
                modifier = Modifier.testTag(TAG_IDENTITY_ACTION),
            )
        }
    }
}

/** One account: the bank as the title, the number beneath it. */
@Composable
private fun AccountListRow(
    row: AccountRow,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(
        R.string.pengaturan_edit_description,
        row.account.bankName,
    )

    KnListRow(
        // The **bank** is the title and the number the subtitle, not the other way round: the
        // owner scans for which bank first ("the transfer came into BCA"), and a list of
        // ten-digit numbers would be unreadable at a glance. The spoken label names the bank
        // for the same reason — it is what the row is *about*.
        title = row.account.bankName,
        subtitle = row.numberText,
        modifier = modifier
            .testTag(accountRowTag(row.account.id))
            .semantics { contentDescription = editLabel },
        leading = {
            // The bank's own initial as the avatar, which is what `KnAvatar` is for. An
            // account has no image and no identifier shorter than its number.
            KnAvatar(name = row.account.bankName)
        },
        onClick = onEdit,
    )
}

/** The empty state: says what to do, and why it matters. */
@Composable
private fun PengaturanEmpty(onAdd: () -> Unit) {
    val spacing = MaterialTheme.knSpacing

    KnEmptyState(
        title = stringResource(R.string.pengaturan_empty_title),
        body = stringResource(R.string.pengaturan_empty_body),
        icon = Lucide.Landmark,
        action = {
            KnButton(
                label = stringResource(R.string.pengaturan_add_action),
                onClick = onAdd,
                icon = Lucide.Landmark,
                variant = KnButtonVariant.Primary,
                modifier = Modifier.testTag(TAG_ADD),
            )
        },
        modifier = Modifier
            .padding(horizontal = spacing.lg)
            .testTag(TAG_EMPTY),
    )
}

/**
 * How many skeleton rows the loading state draws.
 *
 * **Six, matching every other screen** — not three, which is roughly how many accounts a rental
 * business holds. The count is a *shell-wide* fact rather than a per-screen one: `FourStatesTest`
 * asserts the same skeleton row count for all four screens, so a screen that drew its own number
 * would be the one the cross-cutting contract could not cover. A skeleton's job is to occupy the
 * space the content will, and a count that differs per screen makes the shared placeholder a
 * claim about each screen instead of one about the app.
 */
private const val SKELETON_ROWS = 6

/** A row's tag, derived from the account id so two rows cannot collide. */
internal fun accountRowTag(id: String): String = "pengaturan-row-$id"

internal const val TAG_ADD = "pengaturan-add"
internal const val TAG_ADD_ACTION = "pengaturan-add-action"
internal const val TAG_EMPTY = "pengaturan-empty"
internal const val TAG_BANNER = "pengaturan-banner"

// Story 4.4's identity block and sheet. Distinct from the account row's tags: a shared tag
// would let a test asserting "the empty state's action" silently resolve to the identity's
// once an account exists — the trap `KontrakScreen` records.
internal const val TAG_IDENTITY_SECTION = "pengaturan-identity-section"
internal const val TAG_IDENTITY_NAME = "pengaturan-identity-name"
internal const val TAG_IDENTITY_ACTION = "pengaturan-identity-action"

/**
 * The preview, built through the **real** state types.
 *
 * `numberText` is a literal here because there is no formatter to call — an account number is
 * rendered verbatim — so unlike the other screens' previews there is no unchecked format to
 * drift from.
 */
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun PengaturanScreenPreview() {
    KlinikNetTheme(darkTheme = false) {
        PengaturanScreen(
            state = PengaturanUiState(
                rows = listOf(
                    AccountRow(
                        account = net.klinik.rental.domain.model.BusinessAccount(
                            id = "a1",
                            bankName = "BCA",
                            number = "1234567890",
                        ),
                        numberText = "1234567890",
                    ),
                    AccountRow(
                        account = net.klinik.rental.domain.model.BusinessAccount(
                            id = "a2",
                            bankName = "Mandiri",
                            number = "9876543210",
                        ),
                        numberText = "9876543210",
                    ),
                ),
            ),
            onAdd = {},
            onEdit = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            onOpenIdentity = {},
            onCloseIdentity = {},
            onIdentityChange = { it },
            onSaveIdentity = {},
            onIdentityErrorDismiss = {},
            onErrorDismiss = {},
        )
    }
}
