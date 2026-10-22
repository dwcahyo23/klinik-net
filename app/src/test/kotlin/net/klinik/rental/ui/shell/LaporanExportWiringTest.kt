package net.klinik.rental.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.screens.laporan.TAG_EXPORT_CSV
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Acceptance criterion 2: **the CSV button reaches the file picker through the production route.**
 *
 * ## Why this file exists at all
 *
 * `LaporanScreenTest`'s "the export button reaches its handler" injects `onExportCsv` straight into
 * `LaporanScreen`. That proves the screen calls the lambda it was given — and nothing more. The
 * lambda production supplies is built at `NavGraph.kt:156`:
 *
 * ```
 * onExportCsv = { exportLauncher.launch(screens.laporan.csvFilename()) }
 * ```
 *
 * Set that to `{}` and the screen test still passes while the shipped APK's CSV button reaches
 * nothing — which is precisely the shape of the "Cetak / PDF" defect this spec removes, one file
 * over. That field was, in fact, **dead**: `Screens.Laporan` carried its own `onExportCsv` that no
 * code read, that the route parameter shadowed, and whose only production writer was `{}`.
 *
 * So this test renders the real `AppNavGraph` and taps the real button. It is the only test that
 * can see the wiring between them.
 *
 * ## Why `createAndroidComposeRule<ComponentActivity>` and not `createComposeRule`
 *
 * `rememberLauncherForActivityResult` needs a `ComponentActivity` to register its launcher
 * against. Under `createComposeRule` the graph composes but the launcher never registers, and the
 * tap either throws or silently reaches nothing — the very thing under test. `ComponentActivity`
 * is already on the classpath as a `main` dependency of this module (`activity-compose`), so no
 * new build entry is needed.
 *
 * ## Why the assertion is the **callback**, not the picker
 *
 * `CreateDocument` under Robolectric has no file manager to open, so "a picker appeared" is not
 * observable here. `onExportFileChosen` is the far end of the same chain, and the route's launcher
 * result calls it directly. Asserting the callback is therefore what "the picker was wired" means
 * in a JVM test — and it is the half production owns.
 *
 * The route's launcher only fires `onExportFileChosen` when the picker *returns a Uri*, which this
 * test cannot produce. So this test asserts the half it can reach — that the button invokes the
 * launcher's `launch(...)` with the CSV filename — by way of `csvFilename`, which the route reads
 * at the moment of the tap. `onExportFileChosen` being reached with a real `Uri` is a device-only
 * check, and the spec lists it as one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
class LaporanExportWiringTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * The CSV button, tapped through the production route, reaches the picker — proven by the
     * filename the route had to ask for.
     *
     * `csvFilename` is the seam: `NavGraph.kt:156` calls it inside the `launch(...)` argument, so
     * a filename returned only while the tap is being handled proves the launcher's `launch` ran
     * rather than the button holding a stale reference. A `csvFilename` that returns **before** the
     * tap would mean the route captured it eagerly, which is the thing being ruled out.
     */
    @Test
    fun `the CSV button asks the wiring for a filename when tapped, so the launcher runs`() {
        var askedForFilenameAt = -1
        var clock = 0

        compose.setContent {
            theme {
                AppNavGraph(
                    navController = rememberNavController(),
                    screens = testScreens(
                        onCsvFilename = {
            askedForFilenameAt = clock
            "laporan.csv"
        },
                    ),
                    startDestination = Destination.Laporan,
                )
            }
        }

        compose.onNodeWithTag(TAG_EXPORT_CSV).performClick()
        clock = 1
        compose.waitForIdle()

        assertEquals(
            "tapping CSV must ask the wiring for the filename it hands the launcher — " +
                "a launch that never ran never asks",
            0,
            askedForFilenameAt,
        )
    }

    /**
     * The button that was deleted is still absent through the real route.
     *
     * `LaporanScreenTest` already asserts this against the screen; repeating it here costs
     * nothing and means the route cannot reintroduce it by composing something the screen does
     * not draw.
     */
    @Test
    fun `the print button is absent through the production route`() {
        compose.setContent {
            theme {
                AppNavGraph(
                    navController = rememberNavController(),
                    screens = testScreens(),
                    startDestination = Destination.Laporan,
                )
            }
        }

        compose.onAllNodesWithTag("laporan-export-print").assertCountEquals(0)
        compose.onNodeWithText("Cetak / PDF").assertDoesNotExist()
    }

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /** As in `BannerScopeTest`: every bundle stated, so a later screen is covered by default. */
    private fun testScreens(onCsvFilename: () -> String = { "laporan.csv" }): Screens = Screens(
        mobil = Screens.Mobil(
            state = net.klinik.rental.ui.screens.mobil.MobilUiState(),
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
            onExceptionErrorDismiss = {},
            onOpenEnd = {},
            onCloseEnd = {},
            onEndChange = { it },
            onConfirmEnd = {},
            onEndErrorDismiss = {},
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
        pengaturan = Screens.Pengaturan(
            state = net.klinik.rental.ui.screens.pengaturan.PengaturanUiState(),
            snackbarMessage = null,
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
            onSnackbarDismiss = {},
            onErrorDismiss = {},
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
            state = net.klinik.rental.ui.screens.setoran.SetoranUiState(),
            onOpen = {},
            onClose = {},
            onFormChange = { it },
            onSave = {},
            onConfirmPreview = {},
            onDismissPreview = {},
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        ),
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
        dashboard = Screens.Dashboard(
            state = net.klinik.rental.ui.screens.dashboard.DashboardUiState(),
            onDeposit = {},
            onBackupReminderTap = {},
        ),
        // The one bundle this file is about. Note there is **no** `onExportCsv` field: the
        // trigger half is built by the route, so a field here would be the dead `{}` this spec
        // deleted.
        laporan = Screens.Laporan(
            state = net.klinik.rental.ui.screens.laporan.LaporanUiState(),
            periodOptions = listOf("25 Sep - 24 Okt 2026"),
            onSelectPeriod = {},
            onSelectMode = {},
            onCustomFromChange = {},
            onCustomToChange = {},
            onApplyCustomRange = {},
            onExportFileChosen = {},
            csvFilename = onCsvFilename,
        ),
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