package net.klinik.rental.ui.screens.kontrak

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.model.ServiceException
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
 * Robolectric render of the **real** exception sheet.
 *
 * ## What this file is for, given `KontrakScreenTest` already covers the sheet's mount
 *
 * That file answers "does the screen put the sheet up, and is its callback wired".
 * This one answers the questions that are about the sheet *on its own*: what is
 * inside it, what is **absent** for a read-only contract, and whether its own failure
 * banner can render at all.
 *
 * The read-only row is the reason this file exists separately. "No add block and no
 * remove control is **present** — not merely disabled" cannot be asserted from the
 * screen's test without a full state, and it is the one AC where a passing-but-wrong
 * implementation is easiest: `enabled = false` on an add button renders a control
 * that still claims the contract can take an exception.
 *
 * ## The two rules every test here obeys
 *
 * - `assertExists()`, never `assertIsDisplayed()`. Below the Robolectric fold a node
 *   measures zero, and "off-screen, not missing" reads identically to "present".
 * - One `setContent` per test, holding `var state by mutableStateOf(...)` outside so
 *   the state can be mutated between `waitForIdle()` calls.
 *
 * The pure `ExceptionFormState` rules live beside this in `ExceptionFormTest` — no
 * Compose needed for a `copy()`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ExceptionFormSheetTest {

    @get:Rule
    val compose = createComposeRule()

    // --------------------------------------------------------- what is in the sheet

    /**
     * The three add controls exist for an open contract: the date field, the reason
     * field and the Add button.
     */
    @Test
    fun `the date field, the reason field and add all exist`() {
        compose.setContent { theme { Sheet(state = openSheet()) } }

        compose.onNodeWithTag(TAG_EXCEPTION_DATE).assertExists()
        compose.onNodeWithTag(TAG_EXCEPTION_REASON).assertExists()
        compose.onNodeWithTag(TAG_EXCEPTION_ADD).assertExists()
    }

    /**
     * The title names **who** and **which car**, because that is what identifies a
     * rental in this app's row semantics.
     */
    @Test
    fun `the title names the renter and the plate`() {
        compose.setContent { theme { Sheet(state = openSheet()) } }

        compose.onNodeWithTag(TAG_EXCEPTION_TITLE).assertExists()
        compose.onNodeWithText("Pengecualian — Budi Santoso · B 1235 TF").assertExists()
    }

    /**
     * Add is enabled exactly when `canAddException` says so — disabled on entry,
     * enabled once both fields carry a value.
     *
     * The AC words this as "a blank reason leaves add **disabled** rather than storing
     * an exception whose keterangan is empty", so the disabled state is the claim and
     * it has to be observed on the control, not inferred from the repository's `require`.
     */
    @Test
    fun `add is enabled only once a date and a reason are both present`() {
        var state by mutableStateOf(openSheet())
        compose.setContent { theme { Sheet(state = state) } }

        compose.onNodeWithTag(TAG_EXCEPTION_ADD).assertIsNotEnabled()

        state = state.selectExceptionDate(MARKED_DAY)
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_EXCEPTION_ADD).assertIsNotEnabled()

        state = state.setExceptionReason("Ganti ban, mobil di bengkel")
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_EXCEPTION_ADD).assertIsEnabled()
    }

    /**
     * Typing a reason reaches the state through `onChange`, so the button becomes
     * live without anything else driving it.
     *
     * This is the wiring claim the enabled/disabled test depends on: a sheet that
     * renders the right control but never forwards the input would still pass it,
     * because that test drives `state` directly.
     *
     * Typed into the node that carries the **text-field** semantics — found by
     * `hasSetTextAction()`, then checked to be the reason field by a
     * `hasAnyAncestor(TAG_EXCEPTION_REASON)` sibling match, because `KnField` puts the
     * caller's tag on its outer *column* (label, border, row) while the editable node
     * is the `BasicTextField` nested inside. `useUnmergedTree` because the merged tree
     * collapses the field into its parent row.
     *
     * The date field is `enabled = false` and carries no SetText action, so
     * `hasSetTextAction()` is unambiguous here rather than "the only field".
     */
    @Test
    fun `typing a reason reaches the sheet state`() {
        var state by mutableStateOf(openSheet().selectExceptionDate(MARKED_DAY))
        compose.setContent {
            theme {
                Sheet(state = state, onChange = { transform -> state = transform(state) })
            }
        }

        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .onFirst()
            .assert(hasAnyAncestor(hasTestTag(TAG_EXCEPTION_REASON)))
        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .onFirst()
            .performTextInput("Ganti ban")
        compose.waitForIdle()

        assertEquals("Ganti ban", state.addReason)
        compose.onNodeWithTag(TAG_EXCEPTION_ADD).assertIsEnabled()
    }

    /**
     * An empty list draws the empty state that **says what to do**, not merely that
     * there is nothing.
     *
     * For a read-only contract the add instruction above it is gone, so this is the
     * only thing that could have told the owner why the list is empty and empty it.
     */
    @Test
    fun `an empty list says what to do`() {
        compose.setContent { theme { Sheet(state = openSheet(), rows = emptyList()) } }

        compose.onNodeWithTag(TAG_EXCEPTION_EMPTY).assertExists()
        compose.onNodeWithText("Belum ada pengecualian").assertExists()
    }

    /**
     * Each stored row renders its date and its reason.
     *
     * The reason is the other half of the same claim: a row whose "keterangan" line
     * is blank is indistinguishable from a row whose reason was lost, which is why
     * `ServiceException` refuses a blank one at construction.
     */
    @Test
    fun `each row renders its date and its reason`() {
        compose.setContent { theme { Sheet(state = openSheet(), rows = exceptionRows()) } }

        compose.onNodeWithTag(exceptionRowTag("exception-1")).assertExists()
        compose.onNodeWithText("14 Okt").assertExists()
        compose.onNodeWithText("Ganti ban, mobil di bengkel").assertExists()

        compose.onNodeWithTag(exceptionRowTag("exception-2")).assertExists()
        compose.onNodeWithText("16 Okt").assertExists()
        compose.onNodeWithText("Antre servis").assertExists()
    }

    /**
     * A failure with no field of its own renders **inside** the sheet.
     *
     * The AC's own clause: the screen's banner is a different window and would be
     * invisible while the sheet is up. Composed here directly, so what is asserted is
     * that the sheet can draw a banner at all.
     */
    @Test
    fun `a failure renders inside the sheet`() {
        compose.setContent {
            theme { Sheet(state = openSheet(), errorMessage = "Kontrak ini sudah berakhir") }
        }

        compose.onNodeWithTag(TAG_EXCEPTION_ERROR).assertExists()
        compose.onNodeWithText("Kontrak ini sudah berakhir").assertExists()
    }

    /**
     * And a successful write's confirmation is drawn inside it too — same window
     * reason, and it would be equally invisible from the screen.
     */
    @Test
    fun `a confirmation renders inside the sheet`() {
        compose.setContent {
            theme { Sheet(state = openSheet(), successMessage = "14 Okt ditandai tanpa tagihan") }
        }

        compose.onNodeWithTag(TAG_EXCEPTION_SUCCESS).assertExists()
    }

    // ------------------------------------------------------ the read-only contract

    /**
     * `EXCEPTION_ENDED_LIST` — for an ended contract every row still renders.
     *
     * Read-only means the exceptions are **history**, and the history is the record of
     * what was not billed. Hiding it would remove the only evidence that a day was
     * excused.
     */
    @Test
    fun `an ended contract still shows its rows`() {
        compose.setContent { theme { Sheet(state = readOnlySheet(), rows = exceptionRows()) } }

        compose.onNodeWithTag(exceptionRowTag("exception-1")).assertExists()
        compose.onNodeWithTag(exceptionRowTag("exception-2")).assertExists()
        compose.onNodeWithText("14 Okt").assertExists()
        compose.onNodeWithText("Ganti ban, mobil di bengkel").assertExists()
    }

    /**
     * And the **add block is absent**, not disabled.
     *
     * This is the AC's exact wording and the assertion most worth keeping: an add
     * button rendered greyed-out still claims the contract can take an exception, and
     * the repository would refuse it anyway — so a disabled control is a claim the
     * data layer does not honour.
     *
     * `onAllNodes` rather than `onNodeWithTag` + `assertDoesNotExist`, because a
     * `KnButton` with no tag would pass the second check while still being on screen.
     */
    @Test
    fun `an ended contract has no add block at all`() {
        compose.setContent { theme { Sheet(state = readOnlySheet(), rows = exceptionRows()) } }

        compose.onNodeWithTag(TAG_EXCEPTION_DATE).assertDoesNotExist()
        compose.onNodeWithTag(TAG_EXCEPTION_REASON).assertDoesNotExist()
        compose.onNodeWithTag(TAG_EXCEPTION_ADD).assertDoesNotExist()
    }

    /**
     * And **no remove control** — the same reason, and the harder half: the row
     * itself must still be there while its trailing slot is `null`.
     */
    @Test
    fun `an ended contract has no remove control on any row`() {
        compose.setContent { theme { Sheet(state = readOnlySheet(), rows = exceptionRows()) } }

        compose.onNodeWithTag(exceptionRemoveTag("exception-1")).assertDoesNotExist()
        compose.onNodeWithTag(exceptionRemoveTag("exception-2")).assertDoesNotExist()
        assertTrue(
            "the rows themselves are present — a removed row would pass this vacuously",
            compose.onAllNodes(hasTestTag(TAG_EXCEPTION_EMPTY)).fetchSemanticsNodes().isEmpty(),
        )
    }

    // ------------------------------------------------- the removal confirmation

    /**
     * No dialog until the ViewModel says one is pending.
     *
     * `pendingRemovalId` drives its visibility rather than a `remember`, so that a
     * rotation mid-confirmation keeps the dialog. This asserts the other half: a null
     * id mounts nothing.
     */
    @Test
    fun `no removal dialog without a pending row`() {
        compose.setContent { theme { Sheet(state = openSheet(), rows = exceptionRows()) } }

        compose.onNodeWithTag(TAG_EXCEPTION_REMOVE_DIALOG).assertDoesNotExist()
    }

    /**
     * A pending row mounts the dialog, its **confirm** reaches `onConfirmRemove`, and
     * its dismiss reaches `onCancelRemove`.
     *
     * Both directions, because one handler wired to both would arm a delete the owner
     * never confirmed — the reason `KontrakScreen` takes `onCancelRemove` separately.
     *
     * The buttons are addressed by their **labels scoped to the dialog**, not by
     * tags: `KnModal` takes no tag for either button. The scoping is load-bearing —
     * `kontrak_exception_remove_dismiss` and the sheet's own `kontrak_cancel` are both
     * `Batal`, so an unscoped `onNodeWithText("Batal")` matches two nodes and fails on
     * ambiguity rather than on behaviour. The dialog also says the day will be billed
     * again, which is the impact the AC asks it to name.
     *
     * `performSemanticsAction(OnClick)` rather than `performClick`: a control inside a
     * dialog window has no pointer position to click.
     */
    @Test
    fun `the impact dialog confirms and dismisses through separate handlers`() {
        var confirmed = false
        var cancelled = false
        compose.setContent {
            theme {
                Sheet(
                    state = openSheet().copy(pendingRemovalId = "exception-1"),
                    rows = exceptionRows(),
                    onConfirmRemove = { confirmed = true },
                    onCancelRemove = { cancelled = true },
                )
            }
        }

        compose.onNodeWithTag(TAG_EXCEPTION_REMOVE_DIALOG).assertExists()
        // The dialog names the day it is about to remove, not just that a row is going.
        compose.onNodeWithText(
            "Pengecualian 14 Okt dihapus, dan tanggal itu kembali ditagihkan ke penyewa.",
        ).assertExists()

        compose.onNode(inDialog() and hasText("Hapus"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("confirm reaches its own handler", confirmed)
        assertFalse("and never the cancel one", cancelled)

        // Reset before the second half: the point is which handler *this* click
        // reaches, not that neither flag was ever set.
        confirmed = false
        compose.onNode(inDialog() and hasText("Batal"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("dismiss reaches the cancel handler", cancelled)
        assertFalse("and never the confirm one", confirmed)
    }

    /** Any node inside the removal dialog, whatever its own tag or label. */
    private fun inDialog(): SemanticsMatcher =
        hasAnyAncestor(hasTestTag(TAG_EXCEPTION_REMOVE_DIALOG))

    // ------------------------------------------------------------- touch targets

    /**
     * Every interactive node in the sheet clears the 48dp floor.
     *
     * A taller viewport, because the sheet's rows sit below the Robolectric window's
     * bottom edge and a node past that edge measures zero — which would make this
     * pass or fail for a reason that has nothing to do with touch targets.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `every interactive node clears the touch floor`() {
        compose.setContent {
            theme { Sheet(state = openSheet().copy(pendingRemovalId = "exception-1"), rows = exceptionRows()) }
        }

        compose.onNodeWithTag(TAG_EXCEPTION_DATE).assertAtLeast(MIN_TARGET, "exception date field")
        compose.onNodeWithTag(TAG_EXCEPTION_REASON).assertAtLeast(MIN_TARGET, "exception reason field")
        compose.onNodeWithTag(TAG_EXCEPTION_ADD).assertAtLeast(MIN_TARGET, "exception add")
        compose.onNodeWithTag(TAG_EXCEPTION_CLOSE).assertAtLeast(MIN_TARGET, "exception close")
        compose.onNodeWithTag(exceptionRemoveTag("exception-1"))
            .assertAtLeast(MIN_TARGET, "exception remove control")
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * The sheet with every handler counted, so a test can assert one without the
     * others reading as "never wired".
     */
    @Composable
    private fun Sheet(
        state: ExceptionFormState,
        rows: List<ServiceExceptionRow> = emptyList(),
        onChange: ((ExceptionFormState) -> ExceptionFormState) -> Unit = {},
        onAdd: () -> Unit = {},
        onRequestRemove: (String) -> Unit = {},
        onConfirmRemove: () -> Unit = {},
        onCancelRemove: () -> Unit = {},
        errorMessage: String? = null,
        successMessage: String? = null,
    ) {
        ExceptionFormSheet(
            state = state,
            rows = rows,
            onClose = {},
            onChange = onChange,
            onAdd = onAdd,
            onRequestRemove = onRequestRemove,
            onConfirmRemove = onConfirmRemove,
            onCancelRemove = onCancelRemove,
            errorMessage = errorMessage,
            successMessage = successMessage,
        )
    }

    /** An open contract: the add block is mounted and Add starts disabled. */
    private fun openSheet() = ExceptionFormState(
        contractId = "c1",
        contractLabel = "Budi Santoso · B 1235 TF",
        isReadOnly = false,
    )

    /** An ended contract: no add block, no remove controls. */
    private fun readOnlySheet() = openSheet().copy(isReadOnly = true)

    /**
     * Two stored exceptions, built through the **real** projection.
     *
     * `toRow()` rather than a hand-built `ServiceExceptionRow`, because a literal
     * `dateText` here would be a second, unchecked spelling of the format the sheet
     * renders — and the fixture would keep describing the old one after a change.
     */
    private fun exceptionRows(): List<ServiceExceptionRow> = listOf(
        ServiceException(
            id = "exception-1",
            contractId = "c1",
            date = MARKED_DAY,
            reason = "Ganti ban, mobil di bengkel",
        ).toRow(),
        ServiceException(
            id = "exception-2",
            contractId = "c1",
            date = LocalDate.of(2026, 10, 16),
            reason = "Antre servis",
        ).toRow(),
    )

    /**
     * Both axes, for the same reason `KontrakScreenTest` measures both: a control
     * that is wide enough and short, or short and wide, passes a single-axis check.
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
        val MARKED_DAY: LocalDate = LocalDate.of(2026, 10, 14)
    }
}