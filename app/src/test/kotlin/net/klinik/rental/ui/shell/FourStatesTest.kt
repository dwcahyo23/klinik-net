package net.klinik.rental.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.components.SkeletonRowTag
import net.klinik.rental.ui.format.formatTanggalBulan
import net.klinik.rental.domain.model.BusinessAccount
import net.klinik.rental.ui.screens.kendaraan.KendaraanScreen
import net.klinik.rental.ui.screens.kendaraan.KendaraanUiState
import net.klinik.rental.ui.screens.kendaraan.VehicleRow
import net.klinik.rental.ui.screens.kendaraan.VehicleStatus
import net.klinik.rental.ui.screens.kendaraan.TAG_BANNER as TAG_BANNER_KENDARAAN
import net.klinik.rental.ui.screens.kendaraan.TAG_EMPTY_FLEET
import net.klinik.rental.ui.screens.kontrak.ContractRow
import net.klinik.rental.ui.screens.kontrak.KontrakScreen
import net.klinik.rental.ui.screens.kontrak.KontrakUiState
import net.klinik.rental.ui.screens.kontrak.TAG_BANNER as TAG_BANNER_KONTRAK
import net.klinik.rental.ui.screens.kontrak.TAG_EMPTY as TAG_EMPTY_KONTRAK
import net.klinik.rental.ui.screens.penyewa.PenyewaScreen
import net.klinik.rental.ui.screens.penyewa.PenyewaUiState
import net.klinik.rental.ui.screens.penyewa.RenterRow
import net.klinik.rental.ui.screens.penyewa.TAG_BANNER as TAG_BANNER_PENYEWA
import net.klinik.rental.ui.screens.penyewa.TAG_EMPTY_LIST
import net.klinik.rental.ui.screens.pengaturan.AccountRow
import net.klinik.rental.ui.screens.pengaturan.PengaturanScreen
import net.klinik.rental.ui.screens.pengaturan.PengaturanUiState
import net.klinik.rental.ui.screens.pengaturan.TAG_BANNER as TAG_BANNER_PENGATURAN
import net.klinik.rental.ui.screens.pengaturan.TAG_EMPTY as TAG_EMPTY_PENGATURAN
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/** A refusal message, distinct from every confirmation. */
private const val ERROR_COPY = "B 1235 TF sudah dipakai mobil lain."

/** A confirmation. Non-null alongside [ERROR_COPY] in the refused state. */
private const val CONFIRM_COPY = "B 1235 TF tersimpan"

/** The placeholder count every screen draws. */
private const val SKELETON_ROWS = 6
/**
 * The four-state contract, asserted for **every** screen rather than for the
 * screen that happened to be built first.
 *
 * ## Why one file with a table, and not three files
 *
 * The story's criterion is "berlaku untuk setiap layar — bukan hanya layar yang
 * dibangun lebih dulu". Three separate test classes would each stay green while
 * the other two rotted, and a fourth screen added by a later story would inherit
 * nothing — the contract would go back to being prose the moment someone forgot
 * to open this file. Here a screen without an entry in [SCREENS] is itself the
 * failure, and it is visible before a single assertion runs.
 *
 * ## Why the *absences* are the load-bearing assertions
 *
 * Asserting "the skeleton appears" is satisfied by an implementation that also
 * draws the empty state underneath it — and that implementation reproduces
 * exactly the failure this story exists to prevent, because Room's first emission
 * *is* an empty list, so the owner would read "Belum ada Kendaraan" on every
 * single load. Every pair below therefore asserts both directions: skeleton
 * present **and** empty absent, then empty present **and** skeleton absent.
 *
 * The same applies to the banner: "the error is visible" is trivially satisfied
 * by replacing the list with the error. The criterion is stronger than that — a
 * refusal must not change what is on screen — so each banner test loads a
 * populated state and asserts the row text is still there.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FourStatesTest {

    @get:Rule
    val compose = createComposeRule()


    /**
     * One entry per screen.
     *
     * @property skeletonTag the placeholder marker. The same constant for all
     *   three — `KnSkeletonList` is one shared component, and sharing it *is* the
     *   claim that every screen draws the same placeholder.
     * @property rowText copy present only once real rows are loaded, so "the list
     *   is still there" is checked against content rather than a tag.
     * @property loading the screen in its loading state.
     * @property empty the screen with a loaded-but-empty list.
     * @property refused the loaded screen plus a refusal message.
     * @property confirmed the loaded screen plus a confirmation.
     */
    private class Screen(
        val name: String,
        val skeletonTag: String,
        val emptyTag: String,
        val bannerTag: String,
        val emptyTitle: String,
        val emptyBody: String,
        val rowText: String,
        val loading: @Composable () -> Unit,
        val empty: @Composable () -> Unit,
        val refused: @Composable () -> Unit,
        val confirmed: @Composable () -> Unit,
    )

    private val SCREENS: List<Screen> = listOf(
        Screen(
            name = "kendaraan",
            skeletonTag = SkeletonRowTag,
            emptyTag = TAG_EMPTY_FLEET,
            bannerTag = TAG_BANNER_KENDARAAN,
            emptyTitle = "Belum ada Kendaraan",
            emptyBody = "Tambahkan mobil pertama untuk mulai mencatat sewa.",
            rowText = "B 1235 TF",
            loading = {
                KendaraanView(KendaraanUiState(isLoading = true))
            },
            empty = {
                KendaraanView(KendaraanUiState())
            },
            refused = {
                KendaraanView(
                    state = KendaraanUiState(
                        rows = listOf(kendaraanRow()),
                        errorMessage = ERROR_COPY,
                    ),
                    snackbar = CONFIRM_COPY,
                )
            },
            confirmed = {
                KendaraanView(
                    state = KendaraanUiState(rows = listOf(kendaraanRow())),
                    snackbar = CONFIRM_COPY,
                )
            },
        ),
        Screen(
            name = "penyewa",
            skeletonTag = SkeletonRowTag,
            emptyTag = TAG_EMPTY_LIST,
            bannerTag = TAG_BANNER_PENYEWA,
            emptyTitle = "Belum ada Penyewa",
            emptyBody = "Tambahkan penyewa pertama untuk mulai mencatat sewa.",
            rowText = "Budi Santoso",
            loading = {
                PenyewaView(PenyewaUiState(isLoading = true))
            },
            empty = {
                PenyewaView(PenyewaUiState())
            },
            refused = {
                PenyewaView(
                    state = PenyewaUiState(
                        rows = listOf(renterRow()),
                        errorMessage = ERROR_COPY,
                    ),
                    snackbar = CONFIRM_COPY,
                )
            },
            confirmed = {
                PenyewaView(
                    state = PenyewaUiState(rows = listOf(renterRow())),
                    snackbar = CONFIRM_COPY,
                )
            },
        ),
        Screen(
            name = "kontrak",
            skeletonTag = SkeletonRowTag,
            emptyTag = TAG_EMPTY_KONTRAK,
            bannerTag = TAG_BANNER_KONTRAK,
            emptyTitle = "Belum ada Kontrak",
            emptyBody = "Buka kontrak pertama untuk mulai mencatat sewa.",
            rowText = "Budi Santoso",
            loading = {
                KontrakView(KontrakUiState(isLoading = true))
            },
            empty = {
                KontrakView(KontrakUiState())
            },
            refused = {
                KontrakView(
                    state = KontrakUiState(
                        rows = listOf(contractRow()),
                        errorMessage = ERROR_COPY,
                    ),
                    snackbar = CONFIRM_COPY,
                )
            },
            confirmed = {
                KontrakView(
                    state = KontrakUiState(rows = listOf(contractRow())),
                    snackbar = CONFIRM_COPY,
                )
            },
        ),
        // Story 3.1's destination. Its own `PengaturanScreenTest` covers the four states in
        // detail; this entry is what makes the *cross-cutting* claims apply to it too — no
        // spinner, every empty state says what to do, and a refusal never renders as an empty
        // state. The file's own KDoc says a screen without an entry here is the failure.
        Screen(
            name = "pengaturan",
            skeletonTag = SkeletonRowTag,
            emptyTag = TAG_EMPTY_PENGATURAN,
            bannerTag = TAG_BANNER_PENGATURAN,
            emptyTitle = "Belum ada rekening usaha",
            emptyBody = "Tambahkan rekening yang menerima transfer, supaya saat mencatat setoran tinggal dipilih dari daftar.",
            rowText = "BCA",
            loading = {
                PengaturanView(PengaturanUiState(isLoading = true))
            },
            empty = {
                PengaturanView(PengaturanUiState())
            },
            refused = {
                PengaturanView(
                    state = PengaturanUiState(
                        rows = listOf(accountRow()),
                        errorMessage = ERROR_COPY,
                    ),
                    snackbar = CONFIRM_COPY,
                )
            },
            confirmed = {
                PengaturanView(
                    state = PengaturanUiState(rows = listOf(accountRow())),
                    snackbar = CONFIRM_COPY,
                )
            },
        ),
    )

    // ---------------------------------------------------------------- loading

    @Test
    fun `the skeleton renders while loading and the empty state does not`() {
        eachScreen(ScreenState.Loading) { screen ->
            compose.onAllNodesWithTag(screen.skeletonTag).assertCountEquals(SKELETON_ROWS)
            compose.onNodeWithTag(screen.emptyTag).assertDoesNotExist()
            compose.onNodeWithText(screen.emptyTitle).assertDoesNotExist()
        }
    }

    @Test
    fun `no screen draws a spinner`() {
        eachScreen(ScreenState.Loading) { screen ->
            // The criterion says "bukan spinner". A reviewer reading that sentence
            // cannot tell whether one was added later, so the rule is executable:
            // a `CircularProgressIndicator` publishes exactly this key.
            val indeterminate = compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            ).fetchSemanticsNodes()
            assertTrue(
                "${screen.name}: found ${indeterminate.size} progress node(s)",
                indeterminate.isEmpty(),
            )
        }
    }

    // ------------------------------------------------------------------ empty

    @Test
    fun `the empty state renders after load and the skeleton does not`() {
        eachScreen(ScreenState.Empty) { screen ->
            compose.onNodeWithTag(screen.emptyTag).assertExists()
            compose.onAllNodesWithTag(screen.skeletonTag).assertCountEquals(0)
        }
    }

    @Test
    fun `every empty state says what to do, not just that there is nothing`() {
        eachScreen(ScreenState.Empty) { screen ->
            // The criterion is "empty state menyebut apa yang harus dilakukan". A
            // title alone satisfies "an empty state exists" and fails this.
            compose.onNodeWithText(screen.emptyTitle).assertExists()
            compose.onNodeWithText(screen.emptyBody).assertExists()
        }
    }

    // ------------------------------------------------------------------ error

    @Test
    fun `a refusal renders over the unchanged list`() {
        eachScreen(ScreenState.Refused) { screen ->
            compose.onNodeWithText(ERROR_COPY).assertExists()
            // The rows are still there. An error that replaced the list is a
            // different state, and the criterion forbids it — the owner must keep
            // seeing what is already recorded.
            //
            // `atLeastOne` rather than `onNodeWithText`: the master-detail's pane repeats the
            // selected row's name or plate in its own heading (`cars.html:256`,
            // `renters.html:219`), so an exact-match assertion finds two nodes on the screens
            // that have a pane. The claim is "the row is still drawn", not "it is drawn once".
            assertTrue(
                "${screen.name}: the refused screen must still draw its row",
                compose.onAllNodes(hasText(screen.rowText)).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun `a refusal never renders as an empty state`() {
        eachScreen(ScreenState.Refused) { screen ->
            compose.onNodeWithTag(screen.emptyTag).assertDoesNotExist()
            compose.onNodeWithText(screen.emptyTitle).assertDoesNotExist()
        }
    }

    @Test
    fun `an error wins over a confirmation`() {
        eachScreen(ScreenState.Refused) { screen ->
            // Both are non-null upstream — neither clears the other — so the
            // screen has to choose, and it chooses the one that has to be read.
            compose.onNodeWithText(ERROR_COPY).assertExists()
            compose.onNodeWithText(CONFIRM_COPY).assertDoesNotExist()
        }
    }

    @Test
    fun `every screen has its own banner node`() {
        eachScreen(ScreenState.Refused) { screen ->
            compose.onNodeWithTag(screen.bannerTag).assertExists()
        }
    }

    // ---------------------------------------------------------------- success

    @Test
    fun `a confirmation renders over unchanged content`() {
        eachScreen(ScreenState.Confirmed) { screen ->
            compose.onNodeWithText(CONFIRM_COPY).assertExists()
            // "Snackbar tidak mengubah state layar" — the whole criterion in one
            // assertion: the list did not move. `atLeastOne` for the same reason the refusal
            // case above gives: the pane repeats the selected row's text.
            assertTrue(
                "${screen.name}: the confirmed screen must still draw its row",
                compose.onAllNodes(hasText(screen.rowText)).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun `a confirmation is not an empty state`() {
        eachScreen(ScreenState.Confirmed) { screen ->
            compose.onNodeWithTag(screen.emptyTag).assertDoesNotExist()
        }
    }

    // ---------------------------------------------------------------- harness

    /** Which of a screen's four states to compose for the current pass. */
    private enum class ScreenState { Loading, Empty, Refused, Confirmed }

    /**
     * Composes each screen in [state] and hands the live rule to [body].
     *
     * One `setContent` for the whole loop, with the screen swapped by writing to
     * a `mutableStateOf`. A `ComposeTestRule` accepts exactly one `setContent` per
     * test method — calling it again throws `IllegalStateException` — so looping the
     * screens *outside* the composition is not an option. Driving it from inside
     * means one tree per pass and a rule that stays valid.
     */
    private fun eachScreen(state: ScreenState, body: (Screen) -> Unit) {
        val current = mutableStateOf(SCREENS.first())
        compose.setContent { theme { current.value.composableFor(state)() } }

        // Collected rather than rethrown from inside the loop.
        //
        // An assertion in [body] throws, and a throw here would abandon the
        // remaining screens: a failure in `kendaraan` would mean `penyewa` and
        // `kontrak` were never checked at all, and the report would name one
        // screen for a contract that covers three. Collecting means every screen
        // runs, every screen that fails is named, and one assertion — the
        // "the contract applies to *every* screen" claim — is what finally fails.
        val failures = mutableListOf<String>()
        SCREENS.forEach { screen ->
            current.value = screen
            compose.waitForIdle()
            try {
                body(screen)
            } catch (e: AssertionError) {
                failures += "${screen.name}: ${e.message?.lines()?.firstOrNull() ?: "assertion failed"}"
            }
        }

        assertTrue(
            "${failures.size} of ${SCREENS.size} screen(s) failed:" +
                failures.joinToString(separator = "\n") { "\n  - $it" },
            failures.isEmpty(),
        )
    }

    private fun Screen.composableFor(state: ScreenState): @Composable () -> Unit = when (state) {
        ScreenState.Loading -> loading
        ScreenState.Empty -> empty
        ScreenState.Refused -> refused
        ScreenState.Confirmed -> confirmed
    }

    /** Wraps in the app theme; outside it a screen silently gets light defaults. */
    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    // ---------------------------------------------------------------- wiring

    @Composable
    private fun KendaraanView(
        state: KendaraanUiState,
        snackbar: String? = null,
    ) {
        KendaraanScreen(
            state = state,
            onQueryChange = {},
            onFilterChange = {},
            onClearQuery = {},
            onAdd = {},
            onSelect = {},
            onEdit = {},
            snackbarMessage = snackbar,
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        )
    }

    @Composable
    private fun PenyewaView(
        state: PenyewaUiState,
        snackbar: String? = null,
    ) {
        PenyewaScreen(
            state = state,
            onQueryChange = {},
            onClearQuery = {},
            onAdd = {},
            onSelect = {},
            onEdit = {},
            snackbarMessage = snackbar,
            onSnackbarDismiss = {},
            onErrorDismiss = {},
        )
    }

    @Composable
    private fun KontrakView(
        state: KontrakUiState,
        snackbar: String? = null,
    ) {
        KontrakScreen(
            state = state,
            onOpen = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            // Story 2.6's exception wiring. Required on the screen, so they are stated
            // here too — this file is about the four *states*, and a sheet left
            // unmounted by these state values is what keeps the assertions honest.
            onOpenExceptions = {},
            onCloseExceptions = {},
            onExceptionChange = { it },
            onAddException = {},
            onRequestRemove = {},
            onConfirmRemove = {},
            onCancelRemove = {},
            snackbarMessage = snackbar,
            onSnackbarDismiss = {},
            onErrorDismiss = {},
            onExceptionErrorDismiss = {},
            // Story 2.7's five, stated here for the reason the file's own comment gives:
            // these state values decide which sheet is mounted, so an unwired sheet is
            // exactly what would keep these assertions honest.
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
        )
    }

    @Composable
    private fun PengaturanView(
        state: PengaturanUiState,
        snackbar: String? = null,
    ) {
        PengaturanScreen(
            state = state,
            onAdd = {},
            onEdit = {},
            onCloseForm = {},
            onFormChange = { it },
            onSave = {},
            // Story 4.4's identity callbacks. Required on the real wiring, stated here so this
            // cross-screen fixture keeps naming every callback each screen renders a control
            // for — a defaulted one would be a control this test cannot reach.
            onOpenIdentity = {},
            onCloseIdentity = {},
            onIdentityChange = { it },
            onSaveIdentity = {},
            onIdentityErrorDismiss = {},
            snackbarMessage = snackbar,
            onSnackbarDismiss = {},
            onErrorDismiss = {},
            // Story 5.1's backup, stated for the same reason as the identity callbacks above:
            // this fixture names every callback each screen renders a control for, and the
            // export button is one.
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
        )
    }

    // --------------------------------------------------------------- fixtures

    private fun kendaraanRow() = VehicleRow(
        vehicle = Vehicle(
            id = "v1",
            plate = "B 1235 TF",
            brand = "Toyota",
            model = "Agya",
            year = 2022,
            color = "Putih",
            dailyRate = 250_000L,
        ),
        status = VehicleStatus.Tersedia,
        rateText = "Rp 250.000",
    )

    private fun renterRow() = RenterRow(
        Renter(
            id = "r1",
            name = "Budi Santoso",
            phone = "08123456789",
            bankAccount = null,
            emergencyContact = "08129876543",
        ),
    )

    /**
     * One stored account.
     *
     * `rowText = "BCA"` above: the **bank** is the row's title and the number its subtitle, so
     * the "the list is still there" assertion checks the thing the owner scans for.
     */
    private fun accountRow() = AccountRow(
        account = BusinessAccount(id = "a1", bankName = "BCA", number = "1234567890"),
        numberText = "1234567890",
    )

    private fun contractRow() = ContractRow(
        contract = Contract(
            id = "c1",
            renterId = "r1",
            vehicleId = "v1",
            startDate = LocalDate.of(2026, 10, 1),
            dailyRate = 250_000L,
            restDay = DayOfWeek.SUNDAY,
        ),
        renterName = "Budi Santoso",
        vehiclePlate = "B 1235 TF",
        rateText = "Rp 250.000",
        dateText = formatTanggalBulan(LocalDate.of(2026, 10, 1)),
    )
}
