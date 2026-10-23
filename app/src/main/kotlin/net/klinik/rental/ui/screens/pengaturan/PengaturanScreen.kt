package net.klinik.rental.ui.screens.pengaturan

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.Lucide
import net.klinik.rental.R
import net.klinik.rental.data.repository.PinLockRepository
import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnAddFab
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonSize
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnCard
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnSkeletonList
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.components.KnTopBar
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
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
    // Story 5.1's two. Required, not defaulted, for the reason the ones above give — and with a
    // sharper edge here: a defaulted `onExport` would leave a button whose whole purpose is to
    // give the owner a copy of their data, wired to nothing, on the screen where data loss is
    // the thing being guarded against.
    onExport: () -> Unit,
    onExportErrorDismiss: () -> Unit,
    // Story 5.2's three. Required, not defaulted, for the reason every callback here gives — and
    // with the sharpest edge on the screen: a defaulted `onConfirmRestore` would be a button that
    // promises to replace the owner's data and does nothing.
    onRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    onRestoreErrorDismiss: () -> Unit,
    // Story 5.3's four. Required, not defaulted, for the reason every callback here gives — and
    // the sharpest is `onOpenPinSetup`: a defaulted lambda would be a lock the owner can see and
    // cannot turn on, on the screen where they go to protect their records.
    onOpenPinSetup: () -> Unit,
    onDisablePin: () -> Unit,
    onAutoLockChange: (Int) -> Unit,
    onPinSetupDigit: (Char) -> Unit,
    onPinSetupDelete: () -> Unit,
    onConfirmPinCodeWritten: () -> Unit,
    onDismissPinSetup: () -> Unit,
    // Story 5.4's four. Required, not defaulted — a defaulted `onCutoffPick` would leave 28
    // chips that render and ignore the tap, which is the defect this repo has shipped most often.
    onCutoffPick: (Int) -> Unit,
    onConfirmCutoff: () -> Unit,
    onDismissCutoffPreview: () -> Unit,
    onCutoffErrorDismiss: () -> Unit,
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
            Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    KnTopBar(
                        title = stringResource(R.string.pengaturan_title),
                        // The account count lives on the bar rather than in a KPI tile, so the
                        // screen says how much data it is showing without spending a card on it.
                        subtitle = stringResource(
                            R.string.pengaturan_account_count,
                            state.accountCount,
                        ),
                        // The bar's action is **gone**: it and the empty state's button
                        // were two doors to one action that existed in different states,
                        // which is the inconsistency reported from the device. The floating
                        // button below is this screen's only add control.
                    )

                    // ## Why the gutter lives on this one column
                    //
                    // The prototype sets no max-width (`shared.css:285-289`) — its page body
                    // is full-bleed with a 24px gutter. So the body is **not** capped: an
                    // earlier 840dp cap stranded a wide band of empty page down each side of
                    // the tablet, which is exactly what the prototype does not have.
                    //
                    // One owner for the gutter is the point. It used to be four: each section
                    // padded itself `spacing.lg` horizontally and wrapped its content, so the four
                    // cards rendered at whatever width their longest child happened to be — about
                    // 45% for "Identitas usaha" beside 100% for "Data & cadangan" — and no two
                    // lined up. `KnCard` fills, `KnCard` carries `--el-1`, and this column decides
                    // how wide a card is allowed to be.
                    //
                    // The padding is **all four sides**, not horizontal only. With
                    // `horizontal` alone the first card sat flush against the top bar — no
                    // gutter above it — while every card below it had one, which is the
                    // defect reported from the device.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // `--sp-6`, the prototype's page gutter (`shared.css:285-289`) — one
                            // gutter for the whole body rather than four.
                            .padding(spacing.xxl)
                            .align(Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(spacing.lg),
                    ) {
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
                            // navigation — it opens a form seeded from storage — so a tap before
                            // the read lands would open the form on the **default** and the
                            // owner's next save would write that over their real business name.
                            // The same reason `KnNumPad`'s Save is gated rather than merely
                            // flashing.
                            enabled = state.identity != null,
                            onOpen = onOpenIdentity,
                        )

                        // Story 5.1's backup, beside the identity block and **outside** the list's
                        // `when` for the same reason the identity is: the backup action's
                        // availability has nothing to do with how many accounts exist. Inside
                        // `isEmpty` it would be hidden on a fresh install — which is exactly the
                        // moment the owner most wants it, before there is anything to lose.
                        BackupSection(
                            isExporting = state.isExporting,
                            isRestoring = state.isRestoring,
                            onExport = onExport,
                            onRestore = onRestore,
                        )

                        // Story 5.3's security card. Rendered only when the lock state has been
                        // read: a switch drawn in a guessed position is a switch the owner flips
                        // the wrong way, and `null` is how this state says "not read yet".
                        state.pin?.let { pin ->
                            SecuritySection(
                                state = pin,
                                onToggle = if (pin.enabled) onDisablePin else onOpenPinSetup,
                                onAutoLockChange = onAutoLockChange,
                            )
                        }

                        // Story 5.4's billing day, below the security card: it is a consequence the
                        // owner should find after the lock, not between the identity and the backup
                        // buttons they came for.
                        state.cutoff?.let { cutoff ->
                            CutoffSection(state = cutoff, onPickDay = onCutoffPick)
                        }

                        when {
                            // No modifier: the gutter and the gap are the column's now, so a
                            // second set of padding here would offset the skeleton from the cards
                            // it stands in for.
                            state.isLoading -> KnSkeletonList()

                            state.isEmpty -> PengaturanEmpty()

                            // A plain `Column` of rows rather than a `LazyColumn`.
                            //
                            // The list cannot be lazily composed inside a scrolling `Column` — a
                            // `LazyColumn` with unbounded height throws — and it was a
                            // `LazyColumn` because the list used to be the only thing that
                            // scrolled. A handful of bank accounts is not a list that needs
                            // windowing: `PengaturanScreen`'s own KDoc calls it "a handful of
                            // accounts". If that ever stops being true, the fix is to pull the
                            // two cards *out* of the scroll and give the list its own bounded
                            // `Box` again — not to reintroduce nesting.
                            else -> Column(
                                modifier = Modifier.padding(bottom = spacing.colossal),
                                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                state.rows.forEach { row ->
                                    AccountListRow(row = row, onEdit = { onEdit(row) })
                                }
                            }
                        }
                    }
                }

            // The screen's **only** add control, present in every state — see
            // `MobilScreen`'s for why the bar's button and the empty state's both went.
            KnAddFab(
                label = stringResource(R.string.pengaturan_add_action),
                icon = Lucide.Landmark,
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(spacing.xxl)
                    .testTag(TAG_ADD_ACTION),
            )

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
                // The export's failure joins the same single banner rather than getting its own.
                // Two `BottomCenter` bars with the same padding stack on the same pixels — the
                // reason this is one pair and not two — and the export's failure is the more
                // urgent of the two, so it goes first. The restore's failure joins them for the
                // same reason, and goes ahead of both: a refused restore is the one message that
                // tells the owner their data is still there.
                state.restoreErrorMessage?.let { it to KnBadgeTone.Danger }
                    ?: state.cutoffErrorMessage?.let { it to KnBadgeTone.Danger }
                    ?: state.exportErrorMessage?.let { it to KnBadgeTone.Danger }
                    ?: state.errorMessage?.let { it to KnBadgeTone.Danger }
                    ?: snackbarMessage?.let { it to KnBadgeTone.Success }
            }

            banner?.let { (message, tone) ->
                KnSnackbar(
                    message = message,
                    tone = tone,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(spacing.lg)
                        // The three failure sources share one slot, so the tag is what
                        // distinguishes them — and the dismiss callback follows the tag, not
                        // the tone, because clearing the restore's failure must not also clear
                        // the form's.
                        .testTag(
                            when {
                                state.restoreErrorMessage != null -> TAG_RESTORE_ERROR
                                state.cutoffErrorMessage != null -> TAG_CUTOFF_ERROR
                                state.exportErrorMessage != null -> TAG_EXPORT_ERROR
                                else -> TAG_BANNER
                            },
                        ),
                    onDismiss = {
                        when {
                            state.restoreErrorMessage != null -> onRestoreErrorDismiss()
                            state.cutoffErrorMessage != null -> onCutoffErrorDismiss()
                            state.exportErrorMessage != null -> onExportErrorDismiss()
                            tone == KnBadgeTone.Danger -> onErrorDismiss()
                            else -> onSnackbarDismiss()
                        }
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

        // Story 5.2's preview, mounted from this file like the two sheets above and for the same
        // reason: it is a window over this screen's state, and a restore that replaced the
        // database while its own dialog lived in a different composition would be a window
        // describing a screen that no longer exists.
        //
        // It is a `KnModal` rather than a `ModalBottomSheet` because it carries a decision, not a
        // form: there is nothing to type, and the two buttons are the whole content.
        state.restorePreview?.let { preview ->
            RestorePreviewDialog(
                preview = preview,
                onConfirm = onConfirmRestore,
                onDismiss = onDismissRestore,
            )
        }

        // Story 5.3's setup flow. Mounted from this file like the sheets, for the same reason: it
        // is a window over this screen's state, and the PIN it writes is this screen's setting.
        state.pinSetup?.let { setup ->
            PinSetupDialog(
                state = setup,
                onDigit = onPinSetupDigit,
                onDelete = onPinSetupDelete,
                onConfirmWritten = onConfirmPinCodeWritten,
                onDismiss = onDismissPinSetup,
            )
        }

        // Story 5.4's preview, in the same window as the cards whose values it moves.
        state.cutoffPreview?.let { preview ->
            CutoffPreviewDialog(
                preview = preview,
                onConfirm = onConfirmCutoff,
                onDismiss = onDismissCutoffPreview,
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

    KnCard(
        modifier = modifier.testTag(TAG_IDENTITY_SECTION),
    ) {
        Column(
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

/**
 * Story 5.1's backup control and Story 5.2's restore, in one card.
 *
 * ## Why the section says **nothing** about encryption
 *
 * Because the file is plain JSON and FR-17 explicitly forbids reusing the prototype's *"data
 * tersimpan lokal dan terenkripsi"* — a claim that is not true of anything this app writes. The
 * copy states the two facts the owner is really asking when they back up: the data is on this
 * device, and nothing is sent anywhere. Those are both true.
 *
 * ## Why the restore button is now here, and what changed
 *
 * Story 5.1 shipped this card **without** one, and with a line saying restore was not available
 * yet. That line existed so a control the owner could see and tap would not do nothing — the
 * "renders and is wired to nothing" defect. Story 5.2 supplies the other half, so the line goes
 * and the button arrives in the same change. A disabled button in the interim would have been the
 * same defect with a grey tint.
 *
 * ## Why restore is `Secondary` and export is `Secondary` too
 *
 * Neither is the primary action of a settings screen — both are occasional, and the primary thing
 * on this screen is still adding an account. The **destructive** part is not the button but the
 * confirmation behind it, which names what it replaces; colouring the entry point red would warn
 * about the wrong moment.
 */
@Composable
private fun BackupSection(
    isExporting: Boolean,
    isRestoring: Boolean,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnCard(
        modifier = modifier.testTag(TAG_BACKUP_SECTION),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.pengaturan_backup_section),
                style = MaterialTheme.knTypography.label,
                color = MaterialTheme.knColors.textSecondary,
            )
            Text(
                text = stringResource(R.string.pengaturan_backup_section_body),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textPrimary,
            )
            KnButton(
                label = stringResource(
                    if (isExporting) {
                        R.string.pengaturan_export_exporting
                    } else {
                        R.string.pengaturan_export_action
                    },
                ),
                onClick = onExport,
                variant = KnButtonVariant.Secondary,
                size = KnButtonSize.Small,
                icon = Lucide.Download,
                // And disabled while it runs — a second tap would open a second file picker,
                // and the owner's second choice would silently win over the first.
                enabled = !isExporting,
                modifier = Modifier.testTag(TAG_EXPORT_ACTION),
            )
            KnButton(
                label = stringResource(
                    if (isRestoring) {
                        R.string.pengaturan_restore_restoring
                    } else {
                        R.string.pengaturan_restore_action
                    },
                ),
                onClick = onRestore,
                variant = KnButtonVariant.Secondary,
                size = KnButtonSize.Small,
                icon = Lucide.Upload,
                enabled = !isRestoring,
                modifier = Modifier.testTag(TAG_RESTORE_ACTION),
            )
        }
    }
}

/**
 * Story 5.3's security card: the lock switch and the auto-lock duration.
 *
 * ## Why the copy says "penghalang layar, bukan enkripsi"
 *
 * Because FR-17 forbids any claim larger than the truth, and the prototype's own settings card
 * says *"Perlindungan data lokal di perangkat"* above a lock that encrypts nothing. An owner
 * deciding whether to turn this on needs to know what it actually buys: a screen someone has to
 * get past, not a file someone cannot read.
 *
 * ## Why there is no "change PIN" control
 *
 * Because changing a PIN needs the **current** PIN or the recovery code, and both of those paths
 * already exist: the lock screen has "Lupa PIN?", and turning the switch off and on again is the
 * change flow. A third path would be a third place the old code has to be checked, and one of them
 * would be the one that forgot.
 *
 * ## Why the duration is a row of chips rather than a dropdown
 *
 * Because the app has no dropdown anywhere and the four choices fit. `KnChip` is the established
 * control for "pick one of a few", and it is already used by the fleet filter and the Setoran tabs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecuritySection(
    state: PinSettingsUi,
    onToggle: () -> Unit,
    onAutoLockChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnCard(
        modifier = modifier.testTag(TAG_SECURITY_SECTION),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.pengaturan_security_section),
                style = MaterialTheme.knTypography.label,
                color = MaterialTheme.knColors.textSecondary,
            )
            // The honest sentence — see the KDoc.
            Text(
                text = stringResource(R.string.pengaturan_security_section_body),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
            )
            KnButton(
                label = stringResource(
                    if (state.enabled) {
                        R.string.pengaturan_pin_disable_action
                    } else {
                        R.string.pengaturan_pin_enable_action
                    },
                ),
                onClick = onToggle,
                variant = if (state.enabled) KnButtonVariant.Secondary else KnButtonVariant.Primary,
                size = KnButtonSize.Small,
                icon = Lucide.Lock,
                modifier = Modifier.testTag(TAG_PIN_TOGGLE),
            )

            // The duration only means something while the lock is on: a row of chips beside a
            // disabled lock would be a control configuring nothing.
            if (state.enabled) {
                Text(
                    text = stringResource(R.string.pengaturan_autolock_label),
                    style = MaterialTheme.knTypography.body,
                    color = MaterialTheme.knColors.textSecondary,
                    modifier = Modifier.padding(top = spacing.xs),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    PinLockRepository.AUTO_LOCK_CHOICES.sorted().forEach { minutes ->
                        KnChip(
                            text = stringResource(R.string.pengaturan_autolock_option, minutes),
                            selected = minutes == state.autoLockMinutes,
                            onClick = { onAutoLockChange(minutes) },
                            modifier = Modifier.testTag(autoLockTag(minutes)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Story 5.4's billing-day card: 28 days, a chip each, and a preview on every pick.
 *
 * ## Why **28 chips** and not a dropdown
 *
 * Because the app has no dropdown anywhere, and the whole set fits in a `FlowRow` — the same
 * `KnChip` control the fleet filter and the Setoran tabs already use. A picker that asked the
 * owner to remember whether 28 is the maximum would be a worse version of a list that shows
 * every legal day.
 *
 * ## Why a pick opens a **preview**, not a write
 *
 * Because the AC says the preview is mandatory, and the honest way to make "mandatory" true is to
 * have no path from a tap to a write that skips the dialog. `onPickDay` is the only action this
 * card offers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CutoffSection(
    state: CutoffSettingsUi,
    onPickDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing

    KnCard(
        modifier = modifier.testTag(TAG_CUTOFF_SECTION),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.pengaturan_cutoff_section),
                style = MaterialTheme.knTypography.label,
                color = MaterialTheme.knColors.textSecondary,
            )
            Text(
                text = stringResource(R.string.pengaturan_cutoff_section_body),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textSecondary,
            )
            Text(
                text = stringResource(R.string.pengaturan_cutoff_current, state.day),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
                modifier = Modifier.testTag(TAG_CUTOFF_CURRENT),
            )
            // `null` here would be a static list, and a static list is a second copy of the
            // 1..28 rule that `CutoffPeriod` owns. The card reads the range it must offer from
            // the same constants the validation uses, so the two cannot disagree.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                (CutoffPeriod.MIN_CUTOFF_DAY..CutoffPeriod.MAX_CUTOFF_DAY).forEach { day ->
                    KnChip(
                        text = day.toString(),
                        selected = day == state.day,
                        onClick = { onPickDay(day) },
                        modifier = Modifier.testTag(cutoffDayTag(day)),
                    )
                }
            }
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
private fun PengaturanEmpty() {
    val spacing = MaterialTheme.knSpacing

    KnEmptyState(
        title = stringResource(R.string.pengaturan_empty_title),
        body = stringResource(R.string.pengaturan_empty_body),
        icon = Lucide.Landmark,
        // **No action button.** The screen's floating button is the one door to adding an
        // account, and it is drawn in every state — see `MobilScreen`'s for the reasoning.
        // No horizontal padding: the body's column owns the gutter, so padding it here as
        // well would double the inset and put this one out of line with the four cards
        // above it.
        modifier = Modifier.testTag(TAG_EMPTY),
    )
}

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

/**
 * The backup section and its one control.
 *
 * Their own tags rather than the account list's: `TAG_EMPTY`'s action draws the same
 * *"tambah rekening"* label, and a shared tag would let a test asserting the backup control
 * resolve to another button the moment the screen grows one.
 */
internal const val TAG_BACKUP_SECTION = "pengaturan-backup-section"
internal const val TAG_EXPORT_ACTION = "pengaturan-export-action"
internal const val TAG_EXPORT_ERROR = "pengaturan-export-error"

/** Story 5.2's restore control and its refusal banner. */
internal const val TAG_RESTORE_ACTION = "pengaturan-restore-action"
internal const val TAG_RESTORE_ERROR = "pengaturan-restore-error"

/** Story 5.3's security card. */
internal const val TAG_SECURITY_SECTION = "pengaturan-security-section"
internal const val TAG_PIN_TOGGLE = "pengaturan-pin-toggle"
internal fun autoLockTag(minutes: Int): String = "pengaturan-autolock-$minutes"

/** Story 5.4's card. Keyed on the day so a chip cannot be confused with its neighbours. */
internal const val TAG_CUTOFF_SECTION = "pengaturan-cutoff-section"
internal const val TAG_CUTOFF_CURRENT = "pengaturan-cutoff-current"
internal fun cutoffDayTag(day: Int): String = "pengaturan-cutoff-day-$day"
internal const val TAG_CUTOFF_ERROR = "pengaturan-cutoff-error"
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
            onExport = {},
            onExportErrorDismiss = {},
            onRestore = {},
            onConfirmRestore = {},
            onDismissRestore = {},
            onRestoreErrorDismiss = {},
            onOpenPinSetup = {},
            onDisablePin = {},
            onAutoLockChange = {},
            onPinSetupDigit = {},
            onPinSetupDelete = {},
            onConfirmPinCodeWritten = {},
            onDismissPinSetup = {},
            onCutoffPick = {},
            onConfirmCutoff = {},
            onDismissCutoffPreview = {},
            onCutoffErrorDismiss = {},
            onErrorDismiss = {},
        )
    }
}
