package net.klinik.rental.ui.screens.kontrak

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Robolectric render of the **real** FR-9b sheet.
 *
 * ## What this file is for
 *
 * `KontrakScreenTest` answers "does the screen mount it, and is its verb wired", and
 * `SettleContractViewModelTest` answers which figures reach the state. Neither observes the
 * **copy** — and the copy is the acceptance criterion here: the AC requires the
 * confirmation to *name* the contract, the days and the rupiah. A state test proves the
 * numbers were computed; only this file proves the owner is shown them, in the epic's own
 * words.
 *
 * ## The two rules every test here obeys
 *
 * - `assertExists()`, never `assertIsDisplayed()`. Below the Robolectric fold a node
 *   measures zero, so "off-screen, not missing" reads identically to "present".
 * - One `setContent` per test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettleContractSheetTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The date field, the confirm and the close all exist.
     *
     * The close matters as much as the confirm: a sheet whose only exit is its own confirm
     * button is a trap on the path where the owner decides *not* to settle anything — and
     * FR-9b gives no undo, so a decision made by dismissal must be as easy as one made by
     * pressing confirm.
     */
    @Test
    fun `the date field, confirm and close all exist`() {
        compose.setContent { theme { Sheet(state = sheet()) } }

        compose.onNodeWithTag(TAG_SETTLE_DATE).assertExists()
        compose.onNodeWithTag(TAG_SETTLE_CONFIRM).assertExists()
        compose.onNodeWithTag(TAG_SETTLE_CLOSE).assertExists()
    }

    /**
     * The title names **who**, **which car** and **through which day** — the epic's literal
     * shape, `Tandi Kontrak Budi Santoso · B 1235 TF lunas sampai 24 Okt?`.
     *
     * Asserted as a string rather than in parts, because the AC prescribes the sentence and
     * a test on the three fields separately would pass on a title assembled three different
     * ways.
     */
    @Test
    fun `the title names the contract and the settlement date`() {
        compose.setContent { theme { Sheet(state = sheet(settled = true)) } }

        compose.onNodeWithText("Tandai Kontrak Budi Santoso · B 1235 TF lunas sampai 24 Okt?")
            .assertExists()
    }

    /**
     * The impact reads the epic's **own sentence**, with both figures in it.
     *
     * `Menghapus Rp 4.250.000 tunggakan (17 hari) dari daftar ini.` — verbatim from the AC.
     * The rupiah is spelled by `formatRupiah`, so this also pins that the sheet formats
     * rather than composing a figure by hand.
     */
    @Test
    fun `the impact names the rupiah and the day count in the epic's words`() {
        compose.setContent { theme { Sheet(state = sheet(settled = true)) } }

        compose.onNodeWithTag(TAG_SETTLE_IMPACT).assertExists()
        compose.onNodeWithText("Menghapus Rp 4.250.000 tunggakan (17 hari) dari daftar ini.")
            .assertExists()
    }

    /**
     * And what *remains* is named too, when anything does.
     *
     * The AC asks only for what is removed, but a confirmation naming only a removal cannot
     * tell a correct settlement from one that wiped the whole period. This line is what turns
     * "how much" into an answerable question, so it is asserted rather than left implicit.
     */
    @Test
    fun `the remaining figures are named when anything is left owing`() {
        compose.setContent { theme { Sheet(state = sheet(settled = true, remaining = true)) } }

        compose.onNodeWithTag(TAG_SETTLE_REMAINING).assertExists()
        compose.onNodeWithText("Sisa setelah ini: Rp 1.250.000 (5 hari).").assertExists()
    }

    /**
     * Confirm is enabled once a date is chosen **and** something would close.
     *
     * The positive half of the gate `canSettle` claims, and the claim is only meaningful
     * against its negative half below — a button that is always enabled passes this.
     */
    @Test
    fun `confirm is enabled once the date closes something`() {
        compose.setContent { theme { Sheet(state = sheet(settled = true)) } }

        compose.onNodeWithTag(TAG_SETTLE_CONFIRM).assertExists().assertIsEnabled()
    }

    /**
     * A date that closes **nothing** disables the confirm — and says why, rather than
     * leaving a dead button.
     *
     * This is the gate's whole reason. FR-9b has no undo and no audit trail, so a settlement
     * that changes nothing is an action the owner cannot reverse from inside the app; the
     * sentence is what turns a disabled control into an answer rather than a puzzle.
     */
    @Test
    fun `a date that closes nothing disables confirm and explains itself`() {
        compose.setContent { theme { Sheet(state = sheet(settled = true, closesNothing = true)) } }

        compose.onNodeWithTag(TAG_SETTLE_CONFIRM).assertExists().assertIsNotEnabled()
        compose.onNodeWithTag(TAG_SETTLE_CLOSES_NOTHING).assertExists()
        compose.onNodeWithText(
            "Tanggal itu tidak menutup hari tagih apa pun. " +
                "Hari-hari sebelumnya sudah ditandai lunas atau tidak ditagihkan.",
        ).assertExists()
        // And no impact line is drawn, because there is nothing to remove — a
        // "Menghapus Rp 0" would be a figure the app cannot support.
        assertTrue(
            "no impact line for a settlement that removes nothing",
            compose.onAllNodes(hasTestTag(TAG_SETTLE_IMPACT)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * No date chosen at all: no impact, and the button is unavailable.
     *
     * The state the sheet opens in, and the one the owner sees before picking — so the
     * absence of figures here is what makes the figures arriving later legible.
     */
    @Test
    fun `no date means no figures and no confirm`() {
        compose.setContent { theme { Sheet(state = sheet()) } }

        compose.onNodeWithTag(TAG_SETTLE_CONFIRM).assertExists().assertIsNotEnabled()
        assertTrue(
            "nothing is named before a day is chosen",
            compose.onAllNodes(hasTestTag(TAG_SETTLE_IMPACT)).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "and neither is the closes-nothing sentence — there is no date to judge yet",
            compose.onAllNodes(hasTestTag(TAG_SETTLE_CLOSES_NOTHING))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * Confirming reaches `onConfirmSettle`, and closing reaches `onClose` — separately.
     *
     * `assertHasClickAction()` cannot tell a handler wired to the button from one wired to
     * nothing, and this repo has shipped that defect twice (Stories 3.4 and 3.5 each
     * reached production with an unwired entry point). So the *payload* is what is asserted:
     * each click fires its own flag and not the other's.
     */
    @Test
    fun `confirm and close reach their own handlers, separately`() {
        var confirmed = false
        var closed = false
        compose.setContent {
            theme {
                Sheet(
                    state = sheet(settled = true),
                    onConfirmSettle = { confirmed = true },
                    onClose = { closed = true },
                )
            }
        }

        compose.onNodeWithTag(TAG_SETTLE_CONFIRM)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("confirm reaches its own handler", confirmed)
        assertTrue("and never the close one", !closed)

        compose.onNodeWithTag(TAG_SETTLE_CLOSE)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("close reaches its own handler", closed)
    }

    /**
     * A banner-level refusal renders **inside** the sheet.
     *
     * A `ModalBottomSheet` is a separate window above the screen's, so a message the screen
     * drew would sit beneath it and read as nothing at all — which is why this slot is the
     * settlement one and never falls back to the screen's.
     */
    @Test
    fun `a banner-level refusal renders inside the sheet`() {
        compose.setContent {
            theme {
                Sheet(
                    state = sheet(),
                    errorMessage = "Gagal menandai kontrak lunas. Coba lagi.",
                )
            }
        }

        compose.onNodeWithTag(TAG_SETTLE_ERROR).assertExists()
        compose.onNodeWithText("Gagal menandai kontrak lunas. Coba lagi.").assertExists()
    }

    /** And the confirmation renders inside it too, for the same window reason. */
    @Test
    fun `a confirmation renders inside the sheet`() {
        compose.setContent {
            theme {
                Sheet(
                    state = sheet(),
                    successMessage = "17 hari ditandai lunas sampai 24 Okt",
                )
            }
        }

        compose.onNodeWithTag(TAG_SETTLE_SUCCESS).assertExists()
    }

    /**
     * Both buttons clear the 48dp touch floor.
     *
     * Both axes, for the reason `EndContractSheetTest` measures both: a control wide enough
     * and short, or short and wide, passes a single-axis check.
     */
    @Test
    fun `the buttons clear the touch floor`() {
        compose.setContent { theme { Sheet(state = sheet(settled = true)) } }

        compose.onNodeWithTag(TAG_SETTLE_CONFIRM).assertAtLeast(MIN_TARGET, "settle confirm")
        compose.onNodeWithTag(TAG_SETTLE_CLOSE).assertAtLeast(MIN_TARGET, "settle close")
    }

    /**
 * The error banner's close reaches its own dismiss handler.
 *
 * The defect this exists against is the sheet defaulting `onDismissError` to `{}`: the
 * message renders and there is no way to clear it, so a single refusal becomes permanent on
 * an action the owner must be able to retry. Both dismiss handlers are **required**
 * parameters on the sheet now.
 *
 * Addressed through the close glyph rather than the banner's own tag, because
 * `KnSnackbar` puts `onDismiss` on a trailing affordance *inside* the bar — the container
 * carries no click of its own, so clicking the tag would fail on a missing `OnClick`.
 */
@Test
    fun `the error banner's close reaches its own dismiss`() {
        var dismissed = false
        compose.setContent {
            theme {
                Sheet(
                    state = sheet(),
                    errorMessage = "Gagal menandai kontrak lunas. Coba lagi.",
                    onDismissError = { dismissed = true },
                )
            }
        }

        compose.onNodeWithContentDescription("Tutup")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("a banner that cannot be cleared is permanent", dismissed)
    }

    /** And the confirmation's close reaches its own, separate handler. */
    @Test
    fun `the success banner's close reaches its own dismiss`() {
        var dismissed = false
        compose.setContent {
            theme {
                Sheet(
                    state = sheet(),
                    successMessage = "17 hari ditandai lunas sampai 24 Okt",
                    onDismissSuccess = { dismissed = true },
                )
            }
        }

        compose.onNodeWithContentDescription("Tutup")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("the confirmation must be dismissible too", dismissed)
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Sheet(
        state: SettleFormState,
        onClose: () -> Unit = {},
        onChange: ((SettleFormState) -> SettleFormState) -> Unit = {},
        onConfirmSettle: () -> Unit = {},
        errorMessage: String? = null,
        successMessage: String? = null,
        onDismissError: () -> Unit = {},
        onDismissSuccess: () -> Unit = {},
    ) {
        SettleContractSheet(
            state = state,
            onClose = onClose,
            onChange = onChange,
            onConfirmSettle = onConfirmSettle,
            errorMessage = errorMessage,
            onDismissError = onDismissError,
            successMessage = successMessage,
            onDismissSuccess = onDismissSuccess,
        )
    }

    /**
     * A sheet with a date chosen, unless [settled] says otherwise.
     *
     * The figures are the ones `SettleContractViewModelTest` proves the ViewModel computes
     * for this contract — 17 charge days at Rp 250.000, all of them closed. Holding the same
     * figures in both files is deliberate: this one asserts the *copy*, and a figure invented
     * here would let a copy change slip past.
     */
    private fun sheet(
        settled: Boolean = false,
        remaining: Boolean = false,
        closesNothing: Boolean = false,
    ) = SettleFormState(
        contractId = "c1",
        contractLabel = "Budi Santoso · B 1235 TF",
        settleDate = if (settled) SETTLED_THROUGH else null,
        daysClosed = when {
            !settled -> 0
            closesNothing -> 0
            else -> 17
        },
        amountRemoved = if (settled && !closesNothing) 4_250_000L else 0L,
        daysRemaining = if (remaining) 5 else 0,
        amountRemaining = if (remaining) 1_250_000L else 0L,
    )

    private fun SemanticsNodeInteraction.assertAtLeast(target: Dp, what: String) {
        try {
            assertWidthIsAtLeast(target)
            assertHeightIsAtLeast(target)
        } catch (e: AssertionError) {
            throw AssertionError("$what failed the 48dp floor: ${e.message}", e)
        }
    }

    private companion object {
        val MIN_TARGET: Dp = 48.dp
        /** Past every charge day in the 17-day period, so `settled = true` closes them all. */
        val SETTLED_THROUGH: LocalDate = LocalDate.of(2026, 10, 24)
    }
}
