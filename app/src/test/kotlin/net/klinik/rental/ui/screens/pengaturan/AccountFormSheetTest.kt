package net.klinik.rental.ui.screens.pengaturan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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

/**
 * Robolectric render of the **real** account sheet.
 *
 * ## What this file is for
 *
 * `PengaturanScreenTest` answers "does the screen mount it, and is its action wired". This one
 * answers what is inside it — and, most importantly, that the sheet carries **no delete
 * control**. That absence is Story 3.1's deliberate scope cut: the table that would make an
 * account "in use" is Story 3.2's, so a delete shipped now would guard nothing. A test that
 * only checked "the save button exists" would pass an implementation that had grown one.
 *
 * ## The two rules every test here obeys
 *
 * - `assertExists()`, never `assertIsDisplayed()`. Below the Robolectric fold a node measures
 *   zero, so "off-screen, not missing" reads identically to "present".
 * - One `setContent` per test, holding `var state by mutableStateOf(...)` outside so the state
 *   can be mutated between `waitForIdle()` calls.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AccountFormSheetTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The two fields, save and cancel all exist.
     *
     * Cancel matters as much as save: a sheet whose only exit is its own save button is a trap
     * on the path where the owner has decided *not* to add anything.
     */
    @Test
    fun `the fields and both buttons exist`() {
        compose.setContent { theme { Sheet(state = AccountFormState()) } }

        compose.onNodeWithTag(TAG_FIELD_BANK).assertExists()
        compose.onNodeWithTag(TAG_FIELD_NUMBER).assertExists()
        compose.onNodeWithTag(TAG_ACCOUNT_SAVE).assertExists()
        compose.onNodeWithTag(TAG_ACCOUNT_CANCEL).assertExists()
    }

    /**
     * The title says "add" on a new form and "edit" on an existing one.
     *
     * An edit that kept the "Tambah" heading would read as a create, and the owner would
     * reasonably expect a second account rather than a changed one.
     */
    @Test
    fun `the title differs between add and edit`() {
        var state by mutableStateOf(AccountFormState())
        compose.setContent { theme { Sheet(state = state) } }

        compose.onNodeWithText("Tambah rekening").assertExists()

        state = AccountFormState(id = "a1", bankName = "BCA", number = "111")
        compose.waitForIdle()

        compose.onNodeWithText("Ubah rekening").assertExists()
    }

    /**
     * Save is disabled until both fields are filled, then enabled.
     *
     * Both directions, because either alone is satisfiable by a button that is always disabled
     * or always enabled — and the *empty* half is the one that matters, since the form's own
     * completeness rule is what stops a blank write reaching the model.
     */
    @Test
    fun `save is enabled only once both fields are filled`() {
        var state by mutableStateOf(AccountFormState())
        compose.setContent { theme { Sheet(state = state) } }

        compose.onNodeWithTag(TAG_ACCOUNT_SAVE).assertIsNotEnabled()

        state = state.setBankName("BCA")
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_ACCOUNT_SAVE).assertIsNotEnabled()

        state = state.setNumber("1234567890")
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_ACCOUNT_SAVE).assertIsEnabled()
    }

    /**
     * A malformed number leaves Save **enabled** — the deliberate asymmetry.
     *
     * A disabled button beside a filled field would leave the owner with no explanation; an
     * enabled button that produces a refusal naming the format is actionable. The repository
     * refuses the format; the sheet only checks completeness.
     */
    @Test
    fun `a malformed number leaves save enabled so the refusal can explain`() {
        compose.setContent {
            theme { Sheet(state = AccountFormState(bankName = "BCA", number = "12ab34")) }
        }

        compose.onNodeWithTag(TAG_ACCOUNT_SAVE).assertIsEnabled()
    }

    /**
     * Typing reaches the state through `onChange`, so the button becomes live without anything
     * else driving it.
     *
     * This is the wiring claim the enabled/disabled test depends on: a sheet that rendered the
     * right controls but never forwarded input would still pass it, because that test drives
     * `state` directly.
     *
     * Typed into the node under the tag that carries the text-field semantics: `KnField` puts
     * the caller's tag on its outer column, while the editable node is the `BasicTextField`
     * nested inside.
     */
    @Test
    fun `typing a bank name reaches the state`() {
        var state by mutableStateOf(AccountFormState())
        compose.setContent {
            theme { Sheet(state = state, onChange = { transform -> state = transform(state) }) }
        }

        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .onFirst()
            .performTextInput("BCA")
        compose.waitForIdle()

        assertEquals("BCA", state.bankName)
    }

    /** A field refusal renders on **its own** field. */
    @Test
    fun `a bank refusal renders on the bank field`() {
        compose.setContent {
            theme {
                Sheet(
                    state = AccountFormState(bankError = "Isi nama banknya, misalnya BCA."),
                )
            }
        }

        compose.onNodeWithText("Isi nama banknya, misalnya BCA.").assertExists()
        compose.onNodeWithTag(TAG_ACCOUNT_ERROR).assertDoesNotExist()
    }

    /** And a number refusal on the number field. */
    @Test
    fun `a number refusal renders on the number field`() {
        compose.setContent {
            theme {
                Sheet(
                    state = AccountFormState(
                        numberError = "Nomor rekening hanya berisi angka.",
                    ),
                )
            }
        }

        compose.onNodeWithText("Nomor rekening hanya berisi angka.").assertExists()
        compose.onNodeWithTag(TAG_ACCOUNT_ERROR).assertDoesNotExist()
    }

    /**
     * A failure with no field of its own renders **inside** the sheet.
     *
     * The screen's banner is in a different window and would be invisible while the sheet is up
     * — the same rule every other sheet in this app follows.
     */
    @Test
    fun `a failure with no field renders inside the sheet`() {
        compose.setContent {
            theme { Sheet(state = AccountFormState(), errorMessage = "Gagal menyimpan rekening. Coba lagi.") }
        }

        compose.onNodeWithTag(TAG_ACCOUNT_ERROR).assertExists()
        compose.onNodeWithText("Gagal menyimpan rekening. Coba lagi.").assertExists()
    }

    /** And a successful write's confirmation is drawn inside it too. */
    @Test
    fun `a confirmation renders inside the sheet`() {
        compose.setContent {
            theme { Sheet(state = AccountFormState(), successMessage = "Rekening BCA tersimpan") }
        }

        compose.onNodeWithTag(TAG_ACCOUNT_SUCCESS).assertExists()
    }

    /**
     * **The sheet carries no delete control, and no impact dialog.**
     *
     * This is Story 3.1's deliberate scope cut, asserted rather than left implicit. The
     * acceptance criterion says an account a deposit uses cannot be deleted, enforced in the
     * data layer — and the table that would make an account "in use" is Story 3.2's. A delete
     * shipped now would be a control whose guard is vacuously true: it would refuse nothing
     * while looking complete.
     *
     * The modal check is here too, because a delete's confirmation dialog is the shape a later
     * author would reach for first, and its absence is part of the same claim.
     */
    @Test
    fun `the sheet offers no delete control and no impact dialog`() {
        compose.setContent { theme { Sheet(state = AccountFormState(id = "a1", bankName = "BCA", number = "111")) } }

        assertFalse(
            "an account cannot be deleted in this story — the referencing table does not exist yet",
            compose.onAllNodes(hasTestTag("pengaturan-account-delete")).fetchSemanticsNodes().isNotEmpty(),
        )
        assertFalse(
            "and there is no confirmation dialog, because there is no destructive action",
            compose.onAllNodes(hasTestTag("pengaturan-account-delete-dialog")).fetchSemanticsNodes().isNotEmpty(),
        )
        compose.onNodeWithText("Hapus rekening?").assertDoesNotExist()
    }

    /**
     * Every interactive node clears the 48dp floor.
     *
     * A taller viewport, because the sheet's content sits below the Robolectric window's bottom
     * edge and a node past that edge measures zero — which would make this pass or fail for a
     * reason that has nothing to do with touch targets.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `every interactive node clears the touch floor`() {
        compose.setContent { theme { Sheet(state = AccountFormState(bankName = "BCA", number = "111")) } }

        compose.onNodeWithTag(TAG_FIELD_BANK).assertAtLeast(MIN_TARGET, "bank field")
        compose.onNodeWithTag(TAG_FIELD_NUMBER).assertAtLeast(MIN_TARGET, "number field")
        compose.onNodeWithTag(TAG_ACCOUNT_SAVE).assertAtLeast(MIN_TARGET, "save")
        compose.onNodeWithTag(TAG_ACCOUNT_CANCEL).assertAtLeast(MIN_TARGET, "cancel")
    }

    /** Save reaches `onSave`, and cancel reaches `onDismissRequest`. */
    @Test
    fun `save and cancel reach their handlers`() {
        var saved = false
        var closed = false
        compose.setContent {
            theme {
                Sheet(
                    state = AccountFormState(bankName = "BCA", number = "111"),
                    onSave = { saved = true },
                    onDismissRequest = { closed = true },
                )
            }
        }

        compose.onNodeWithTag(TAG_ACCOUNT_SAVE).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("save must reach the ViewModel", saved)

        compose.onNodeWithTag(TAG_ACCOUNT_CANCEL).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("and cancel must close the sheet", closed)
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Sheet(
        state: AccountFormState,
        onChange: ((AccountFormState) -> AccountFormState) -> Unit = {},
        onSave: () -> Unit = {},
        onDismissRequest: () -> Unit = {},
        errorMessage: String? = null,
        successMessage: String? = null,
    ) {
        AccountFormSheet(
            state = state,
            onDismissRequest = onDismissRequest,
            onChange = onChange,
            onSave = onSave,
            errorMessage = errorMessage,
            successMessage = successMessage,
        )
    }

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
    }
}
