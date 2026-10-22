package net.klinik.rental.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.screens.mobil.TAG_BANNER
import net.klinik.rental.ui.screens.setoran.SetoranUiState
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the shell does with a message when the owner moves on.
 *
 * ## The limit this file records rather than hides
 *
 * A refusal lives in its own screen's ViewModel. `MainActivity` scopes all three
 * to the Activity rather than to a nav back-stack entry, so a message survives
 * the owner walking away from the screen that raised it — but it does **not**
 * follow them to a different screen: `BANNER_SCOPED_TO_RAISER` is the recorded
 * consequence, and hoisting every message to a shell-level bus is deliberately
 * not built.
 *
 * The test below pins the half that *is* claimed, so a future change that made
 * the scoping destination-scoped would fail here rather than in the field: the
 * raiser's banner stays addressable after the owner navigates away and back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
class BannerScopeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the raiser's banner is still there after navigating away and back`() {
        // A refusal raised on the fleet, then the owner goes to renters and back.
        // The fleet's banner must not have been destroyed by the round trip —
        // that is exactly what destination-scoped ViewModels would do.
        compose.setContent { theme { AppShell(screens = refusingScreens(), showRail = true) } }

        // The app opens on Beranda now, so the fleet is reached the way the owner
        // would reach it — through the rail. The refusal is the fleet's own.
        tapRailItem("Mobil")

        // It is up on arrival.
        compose.onAllNodesWithTag(TAG_BANNER).assertCountEquals(1)

        // Scoped to the rail, and by content description: the compact rail draws no
        // labels, and "Kendaraan"/"Penyewa" are also their own screens' titles, so an
        // unscoped text match would be ambiguous. The chrome is what is being tapped here.
        tapRailItem("Penyewa")
        tapRailItem("Mobil")

        compose.onAllNodesWithTag(TAG_BANNER).assertCountEquals(1)
    }

    @Test
    fun `a message does not follow the owner to another screen`() {
        // The recorded limit. The fleet's refusal is not rendered over the
        // renters' screen, because `state.errorMessage` belongs to the fleet's
        // own state and the renters' screen reads its own.
        compose.setContent { theme { AppShell(screens = refusingScreens(), showRail = true) } }

        tapRailItem("Penyewa")

        // Two screens' banners could both exist and only the mounted one be on
        // top; asserting the fleet's tag is *absent* proves the message stayed
        // put rather than leaking into a destination it was not about.
        compose.onAllNodesWithTag(TAG_BANNER).assertCountEquals(0)
    }

    /**
     * Taps the rail item named [label].
     *
     * By **content description** rather than by text, because the shell mounts the prototype's
     * compact rail and a compact rail draws no labels (`shared.css:1031`); the name is what it
     * keeps instead.
     *
     * Scoped to [TAG_RAIL] for a second reason, which held before this one: every destination
     * used here is also the name of its own screen — "Kendaraan" is the fleet screen's title and
     * "Penyewa" the renters screen's — so an unscoped `onNodeWithText` matches two nodes and
     * fails for being ambiguous rather than tapping anything.
     */
    private fun tapRailItem(label: String) {
        compose.onAllNodes(
            hasContentDescriptionExactly(label) and hasAnyAncestor(hasTestTag(TAG_RAIL)),
        )[0].performClick()
        compose.waitForIdle()
    }

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * The fleet carrying a refusal; the other two empty.
     *
     * Only the fleet is given an `errorMessage`, which is the whole point — a
     * test where all three are refusing could not tell a message that moved from
     * one that was simply never cleared.
     */
    private fun refusingScreens() = Screens(
        mobil = Screens.Mobil(
            state = net.klinik.rental.ui.screens.mobil.MobilUiState(
                errorMessage = "B 1235 TF sudah dipakai mobil lain.",
            ),
            snackbarMessage = null,
            onQueryChange = {},
            onFilterChange = {},
            onClearQuery = {},
            onAdd = {},
            onSelect = {},
            onEdit = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
        penyewa = Screens.Penyewa(
            state = net.klinik.rental.ui.screens.penyewa.PenyewaUiState(),
            snackbarMessage = null,
            onQueryChange = {},
            onClearQuery = {},
            onAdd = {},
            onSelect = {},
            onEdit = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
        kontrak = Screens.Kontrak(
            state = net.klinik.rental.ui.screens.kontrak.KontrakUiState(),
            snackbarMessage = null,
            renterOptions = emptyList(),
            vehicleOptions = emptyList(),
            onOpen = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            onOpenExceptions = {},
            onCloseExceptions = {},
            onExceptionChange = { it },
            onAddException = {},
            onRequestRemove = {},
            onConfirmRemove = {},
            onCancelRemove = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
            // This file exists to prove which window each message is drawn in, and
            // Story 2.7 added a third sheet and a third pair. Stating them is what lets
            // a later story's sheet be covered by the same assertions instead of
            // quietly going unmeasured.
            onExceptionErrorDismiss = {},
            onOpenEnd = {},
            onCloseEnd = {},
            onEndChange = { it },
            onConfirmEnd = {},
            onEndErrorDismiss = {},
            // Story 3.6's FR-9b wiring, the same no-op stance every other
            // callback in these shell fixtures takes.
            onOpenSettle = {},
            onCloseSettle = {},
            onSettleChange = { it },
            onConfirmSettle = {},
            onSettleErrorDismiss = {},
            depositHistory = null,
            onOpenHistory = {},
            onRequestDepositCancel = { _, _, _ -> },
            onRequestDepositEdit = {},
            onDepositHistoryDismiss = {},
        ),
        // Story 3.1's destination. This file proves which *window* each message is drawn
        // in, and the settings screen has a form sheet whose banner must land inside it
        // rather than behind it — so the wiring is stated here for the same reason the
        // contracts screen's three pairs are.
        pengaturan = Screens.Pengaturan(
            state = net.klinik.rental.ui.screens.pengaturan.PengaturanUiState(),
            snackbarMessage = null,
            onAdd = {},
            onEdit = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            // Story 4.4's identity callbacks, required on the real wiring. Named here because
            // this fixture exists to walk every window that can draw a banner, and the identity
            // sheet is one of them.
            onOpenIdentity = {},
            onCloseIdentity = {},
            onIdentityChange = { it },
            onSaveIdentity = {},
            onIdentityErrorDismiss = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
            // Story 5.1's backup, named for the same reason: this file walks every window
            // that can draw a banner, and the export's failure rides on the settings screen's
            // single banner slot.
            onExportErrorDismiss = {},
            onExportGranted = {},
            backupFilename = { "klinik-net-backup-2026-10-19.json" },
            onRestoreFileChosen = {},
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
        ),
        deposito = Screens.Deposito(
            state = SetoranUiState(),
            onOpen = {},
            onClose = {},
            onFormChange = { it },
            onSave = {},
            onConfirmPreview = {},
            onDismissPreview = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
        // Story 4.1's arrears list. It draws its own banner over its own content — there is
        // no sheet above it to swallow the message — so the wiring is stated here for the
        // same reason every other screen's is.
        tunggakan = Screens.Tunggakan(
            state = net.klinik.rental.ui.screens.tunggakan.TunggakanUiState(),
            onSelect = {},
            onRiskFilterChange = {},
            onErrorDismiss = {},
            onRemind = {},
            onReminderChange = {},
            onReminderSend = {},
            onReminderCopy = {},
            onReminderDismiss = {},
            onReminderLinkOpened = {},
            onReminderOpenFailed = {},
            pendingReminderLink = null,
        ),
        // Story 4.3's morning summary. It draws no banner of its own — every figure it
        // shows comes from a read that either succeeded or is still loading — but the
        // wiring is stated here so the fixture stays complete.
        dashboard = Screens.Dashboard(
            state = net.klinik.rental.ui.screens.dashboard.DashboardUiState(),
            onDeposit = {},
            onBackupReminderTap = {},
        ),
        // Story 4.5's report. Like Beranda, it draws no banner of its own — every figure
        // comes from a read — but the fixture states it so this stays complete.
        laporan = Screens.Laporan(
            state = net.klinik.rental.ui.screens.laporan.LaporanUiState(),
            periodOptions = listOf("25 Sep - 24 Okt 2026"),
            onSelectPeriod = {},
            onSelectMode = {},
            onCustomFromChange = {},
            onCustomToChange = {},
            onApplyCustomRange = {},
            onExportFileChosen = {},
            csvFilename = { "laporan.csv" },
        ),
        // The deposit screen's wiring, stated so this fixture stays complete: a screen
        // whose callbacks were omitted would differ from production in ways this file
        // could not see.
        setoran = Screens.Setoran(
            state = net.klinik.rental.ui.screens.setoran.SetoranScreenState(),
            onSelectTab = {},
            onRequestDepositCancel = { _, _, _ -> },
            onRequestDepositEdit = {},
            onDismissError = {},
            onHistoryQueryChange = {},
            onClearHistoryQuery = {},
            onSelectHistoryFilter = {},
        ),
    )

}





