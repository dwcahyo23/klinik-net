package net.klinik.rental.ui.screens.kontrak

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Robolectric render of the **real** end sheet.
 *
 * ## What this file is for
 *
 * `KontrakScreenTest` answers "does the screen mount it, and is its verb wired". This
 * one answers what is inside it and — the part worth a file of its own — that the
 * confirm button's enabled state follows the one rule the sheet has.
 *
 * The end sheet is one field and one button, so there is genuinely less to check here
 * than for the exception sheet. That is the point of the story's design, not a gap in the
 * tests: a sheet whose whole content is `canEndContract` has exactly one interesting
 * claim, and this is where it is pinned.
 *
 * ## The two rules every test here obeys
 *
 * - `assertExists()`, never `assertIsDisplayed()`. Below the Robolectric fold a node
 *   measures zero, so "off-screen, not missing" reads identically to "present".
 * - One `setContent` per test, holding `var state by mutableStateOf(...)` outside so the
 *   state can be mutated between `waitForIdle()` calls.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EndContractSheetTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The date field, the confirm and the close are all present.
     *
     * The close matters as much as the confirm: a sheet whose only exit is its own
     * confirm button is a trap on the one path where the owner has decided *not* to end
     * anything. The scrim tap and back gesture route to the same handler, which is what
     * the `EndContractSheet` KDoc claims.
     */
    @Test
    fun `the date field, confirm and close all exist`() {
        compose.setContent { theme { Sheet(state = sheet()) } }

        compose.onNodeWithTag(TAG_END_DATE).assertExists()
        compose.onNodeWithTag(TAG_END_CONFIRM).assertExists()
        compose.onNodeWithTag(TAG_END_CLOSE).assertExists()
    }

    /**
     * The title names **who** and **which car**, the same label the exception sheet uses.
     *
     * Two sheets naming one rental two different ways is how the owner stops trusting
     * which contract a dialog is about — so this asserts the literal string rather than
     * that "a title exists".
     */
    @Test
    fun `the title names the renter and the plate`() {
        compose.setContent { theme { Sheet(state = sheet()) } }

        compose.onNodeWithTag(TAG_END_TITLE).assertExists()
        compose.onNodeWithText("Akhiri kontrak — Budi Santoso · B 1235 TF").assertExists()
    }

    /**
     * The date field starts **empty**, and says it is waiting.
     *
     * This is the claim most likely to be "simplified" by a later reader who finds an
     * empty field untidy. Ending a contract frees a car, so a default the owner never
     * looked at shows up as a vehicle reading `tersedia` on a day nobody agreed to: the
     * prototype's hidden default wearing a label.
     *
     * Asserted by the **absence** of a chosen date rather than by looking for an input
     * node: the field is `enabled = false` — it is filled from the picker, never typed —
     * so it carries no text-input semantics to target. What must not be there is a date
     * value, and the hint that says the field is empty.
     */
    @Test
    fun `the date field starts empty and says so`() {
        compose.setContent { theme { Sheet(state = sheet()) } }

        compose.onNodeWithTag(TAG_END_DATE).assertExists()
        compose.onNodeWithText("Tanggal penyewa mengembalikan mobil").assertExists()
        assertFalse(
            "no date is pre-filled — ending on an unread figure frees the car on the wrong day",
            compose.onAllNodes(hasText("2026-10-20")).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * Confirm is disabled until a date is chosen, and enabled once one is.
     *
     * Both directions, because either alone is satisfiable by a button that is always
     * disabled or always enabled — and the *empty* half is the one that matters, because
     * a non-nullable repository parameter is what stops a blank date reaching storage,
     * and the disabled button is what stops it looking available.
     */
    @Test
    fun `confirm is enabled only once a date is chosen`() {
        var state by mutableStateOf(sheet())
        compose.setContent { theme { Sheet(state = state) } }

        compose.onNodeWithTag(TAG_END_CONFIRM).assertIsNotEnabled()

        state = state.selectEndDate(END_DAY)
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_END_CONFIRM).assertIsEnabled()
    }

    /**
     * Choosing a date clears **its own** error.
     *
     * The selection *is* the fix, so an error surviving it would accuse the owner of a
     * field they have just filled in — the same rule `selectExceptionDate` follows, and
     * worth pinning here because it is a pure function the sheet only inherits by calling
     * the right one.
     */
    @Test
    fun `choosing a date clears the date error`() {
        var state by mutableStateOf(sheet().copy(dateError = "Kontrak dimulai 1 Okt."))
        compose.setContent { theme { Sheet(state = state) } }

        compose.onNodeWithText("Kontrak dimulai 1 Okt.").assertExists()

        state = state.selectEndDate(END_DAY)
        compose.waitForIdle()

        compose.onNodeWithText("Kontrak dimulai 1 Okt.").assertDoesNotExist()
    }

    /**
     * A before-start refusal renders **on the date field**, naming the start date.
     *
     * The field, not the banner: the date is the only thing the owner can change, and a
     * banner naming a figure with no field to act on makes them guess which of the two
     * inputs is wrong.
     */
    @Test
    fun `a refusal renders on the date field`() {
        compose.setContent {
            theme {
                Sheet(state = sheet().copy(dateError = "Kontrak dimulai 1 Okt, jadi tidak bisa berakhir sebelum tanggal itu."))
            }
        }

        compose.onNodeWithText("Kontrak dimulai 1 Okt, jadi tidak bisa berakhir sebelum tanggal itu.")
            .assertExists()
        compose.onNodeWithTag(TAG_END_ERROR).assertDoesNotExist()
    }

    /**
     * A failure with no field of its own renders **inside** the sheet.
     *
     * The AC's own clause, and the reason the screen's banner is suppressed while this
     * is up: it is a different window. An already-ended contract is the realistic case —
     * the repository refuses and there is no date to blame.
     */
    @Test
    fun `a failure with no field renders inside the sheet`() {
        compose.setContent {
            theme { Sheet(state = sheet(), errorMessage = "Kontrak ini sudah berakhir, jadi tidak bisa diubah lagi.") }
        }

        compose.onNodeWithTag(TAG_END_ERROR).assertExists()
        compose.onNodeWithText("Kontrak ini sudah berakhir, jadi tidak bisa diubah lagi.").assertExists()
    }

    /**
     * And a successful write's confirmation is drawn inside it too.
     *
     * The same window reason as the failure: a confirmation rendered in the screen's
     * composition would sit *behind* the sheet, which is the one moment the owner wants
     * to read it.
     */
    @Test
    fun `a confirmation renders inside the sheet`() {
        compose.setContent {
            theme { Sheet(state = sheet(), successMessage = "Kontrak berakhir 20 Okt") }
        }

        compose.onNodeWithTag(TAG_END_SUCCESS).assertExists()
        compose.onNodeWithText("Kontrak berakhir 20 Okt").assertExists()
    }

    /**
     * Confirm reaches `onConfirmEnd` — the sheet's whole purpose is that one write, and
     * a sheet that renders a perfectly good button wired to nothing is the exact defect
     * the required-parameter rule exists to prevent.
     */
    @Test
    fun `confirm reaches the handler`() {
        var confirmed = false
        compose.setContent {
            theme {
                Sheet(
                    state = sheet().selectEndDate(END_DAY),
                    onConfirmEnd = { confirmed = true },
                )
            }
        }

        compose.onNodeWithTag(TAG_END_CONFIRM)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("the write must reach the ViewModel", confirmed)
    }

    /**
     * And Close reaches `onClose` — the scrim tap, the back gesture and the Ghost
     * button all route here, which is what makes them one decision rather than three.
     */
    @Test
    fun `close reaches the handler`() {
        var closed = false
        compose.setContent { theme { Sheet(state = sheet(), onClose = { closed = true }) } }

        compose.onNodeWithTag(TAG_END_CLOSE)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("dismissing must be possible without ending anything", closed)
    }

    /**
     * Every interactive node clears the 48dp floor.
     *
     * A taller viewport, because the sheet's content sits below the Robolectric window's
     * bottom edge and a node past that edge measures zero — which would make this pass
     * or fail for a reason that has nothing to do with touch targets.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `every interactive node clears the touch floor`() {
        compose.setContent { theme { Sheet(state = sheet().selectEndDate(END_DAY)) } }

        compose.onNodeWithTag(TAG_END_DATE).assertAtLeast(MIN_TARGET, "end date field")
        compose.onNodeWithTag(TAG_END_CONFIRM).assertAtLeast(MIN_TARGET, "end confirm")
        compose.onNodeWithTag(TAG_END_CLOSE).assertAtLeast(MIN_TARGET, "end close")
    }

    /**
     * There is **no** impact dialog — the sheet is one field and two buttons.
     *
     * Asserted because its absence is a design decision a reviewer would otherwise
     * re-litigate: UX-DR11's middle tier is for actions that change numbers, and ending
     * changes none that the owner did not just type. A test that only checked the
     * dialog was absent *when no row was pending* would pass an implementation that had
     * grown one.
     */
    @Test
    fun `the sheet draws no impact dialog`() {
        compose.setContent { theme { Sheet(state = sheet().selectEndDate(END_DAY)) } }

        assertFalse(
            "ending changes no number the owner did not enter, so there is nothing to disclose",
            compose.onAllNodes(hasTestTag(TAG_EXCEPTION_REMOVE_DIALOG))
                .fetchSemanticsNodes().isNotEmpty(),
        )
        // And none of the exception sheet's own vocabulary leaked in — a second reason
        // this sheet is a separate composable rather than a mode of that one.
        compose.onNodeWithText("Hapus pengecualian?").assertDoesNotExist()
        compose.onNodeWithTag(TAG_EXCEPTION_REASON).assertDoesNotExist()
        compose.onNodeWithTag(TAG_EXCEPTION_ADD).assertDoesNotExist()
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * The sheet with every handler counted, so a test can assert one without the others
     * reading as "never wired" — the same rule `ExceptionFormSheetTest` states.
     */
    @Composable
    private fun Sheet(
        state: EndContractFormState,
        onClose: () -> Unit = {},
        onChange: ((EndContractFormState) -> EndContractFormState) -> Unit = {},
        onConfirmEnd: () -> Unit = {},
        errorMessage: String? = null,
        successMessage: String? = null,
    ) {
        EndContractSheet(
            state = state,
            onClose = onClose,
            onChange = onChange,
            onConfirmEnd = onConfirmEnd,
            errorMessage = errorMessage,
            successMessage = successMessage,
        )
    }

    /** A fresh sheet: no date chosen, no error, not read-only — there is no such flag. */
    private fun sheet() = EndContractFormState(
        contractId = "c1",
        contractLabel = "Budi Santoso · B 1235 TF",
    )

    /**
     * Both axes, for the reason `ExceptionFormSheetTest` measures both: a control that
     * is wide enough and short, or short and wide, passes a single-axis check.
     */
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
        val END_DAY: LocalDate = LocalDate.of(2026, 10, 20)
    }
}