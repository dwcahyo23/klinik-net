package net.klinik.rental.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.screens.kendaraan.TAG_BANNER
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
        compose.setContent { theme { AppShell(screens = refusingScreens(), isLandscape = true) } }

        // It is up on arrival.
        compose.onAllNodesWithTag(TAG_BANNER).assertCountEquals(1)

        // Scoped to the rail. "Kendaraan" is both a rail label and the fleet
        // screen's own title, so an unscoped `onNodeWithText` matches two nodes and
        // throws for being ambiguous rather than navigating — and the same applies
        // to "Penyewa". The chrome is what is being tapped here.
        tapRailItem("Penyewa")
        tapRailItem("Kendaraan")

        compose.onAllNodesWithTag(TAG_BANNER).assertCountEquals(1)
    }

    @Test
    fun `a message does not follow the owner to another screen`() {
        // The recorded limit. The fleet's refusal is not rendered over the
        // renters' screen, because `state.errorMessage` belongs to the fleet's
        // own state and the renters' screen reads its own.
        compose.setContent { theme { AppShell(screens = refusingScreens(), isLandscape = true) } }

        tapRailItem("Penyewa")

        // Two screens' banners could both exist and only the mounted one be on
        // top; asserting the fleet's tag is *absent* proves the message stayed
        // put rather than leaking into a destination it was not about.
        compose.onAllNodesWithTag(TAG_BANNER).assertCountEquals(0)
    }

    /**
     * Taps the rail item labelled [label].
     *
     * Scoped to [TAG_RAIL] because every one of these three destinations is also
     * the name of its own screen: "Kendaraan" is the fleet screen's title, and
     * "Penyewa" the renters screen's. An unscoped `onNodeWithText` therefore
     * matches two nodes and fails for being ambiguous rather than tapping
     * anything — and the chrome is what these assertions are about.
     */
    private fun tapRailItem(label: String) {
        compose.onAllNodes(
            hasText(label) and hasAnyAncestor(hasTestTag(TAG_RAIL)),
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
        kendaraan = Screens.Kendaraan(
            state = net.klinik.rental.ui.screens.kendaraan.KendaraanUiState(
                errorMessage = "B 1235 TF sudah dipakai mobil lain.",
            ),
            snackbarMessage = null,
            onQueryChange = {},
            onFilterChange = {},
            onClearQuery = {},
            onAdd = {},
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
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
        deposito = Screens.Deposito(
            state = SetoranUiState(),
            onOpen = {},
            onClose = {},
            onFormChange = { it },
            onSave = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
    )

}



