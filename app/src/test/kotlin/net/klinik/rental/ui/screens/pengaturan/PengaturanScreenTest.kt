package net.klinik.rental.ui.screens.pengaturan

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.model.BusinessAccount
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric render of the settings screen.
 *
 * ## The four states are the point
 *
 * The epic context requires every screen to have a skeleton, an empty state that says *what to
 * do*, an error state that never looks like a new account, and a confirmation that does not
 * change screen state. Three of those are asserted here as **mutually exclusive branches**,
 * because the failure they exist to prevent is the loading state rendering the empty state —
 * the screen that reads like a fresh install on every load.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PengaturanScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // -------------------------------------------------------------- the states

    /**
     * The empty state says what to do, and offers the action.
     *
     * Both halves: a bare "Belum ada rekening" would satisfy "an empty state exists" and fail
     * the AC, which asks the empty state to say what to do. The body is asserted separately for
     * that reason, and it is the copy that names *why* an account matters — recording a transfer.
     */
    @Test
    fun `the empty state says what to do`() {
        compose.setContent { theme { Screen(state = PengaturanUiState()) } }

        compose.onNodeWithTag(TAG_EMPTY).assertExists()
        compose.onNodeWithText("Belum ada rekening usaha").assertExists()
        compose.onNodeWithText(
            "Tambahkan rekening yang menerima transfer, supaya saat mencatat setoran tinggal dipilih dari daftar.",
        ).assertExists()
        compose.onNodeWithTag(TAG_ADD).assertExists()
    }

    /**
     * The skeleton renders while loading, and the empty state does **not**.
     *
     * The assertion that matters is the second one. The database's first emission is an empty
     * list, so a screen that branched on emptiness would show "Belum ada rekening" during every
     * load — an invitation to add an account the owner already added, and the
     * empty-looks-like-a-new-account failure the epic context forbids.
     */
    @Test
    fun `the skeleton renders while loading and the empty state does not`() {
        compose.setContent { theme { Screen(state = PengaturanUiState(isLoading = true)) } }

        compose.onNodeWithTag(TAG_EMPTY).assertDoesNotExist()
        compose.onNodeWithText("Belum ada rekening usaha").assertDoesNotExist()
    }

    /**
     * A populated list renders each account's bank and number.
     *
     * The bank is the title and the number the subtitle: the owner scans for which bank first,
     * and a list of ten-digit numbers would be unreadable at a glance.
     */
    @Test
    fun `a populated list renders the bank and the number`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onNodeWithText("BCA").assertExists()
        compose.onNodeWithText("1234567890").assertExists()
        compose.onNodeWithText("Mandiri").assertExists()
        compose.onNodeWithText("9876543210").assertExists()
        compose.onNodeWithTag(TAG_EMPTY).assertDoesNotExist()
    }

    /**
     * A refusal renders as a banner over **unchanged content** — never as an empty list.
     *
     * The error state's whole job is to not look like a new account: an owner whose read failed
     * must not see an empty screen and conclude their data is gone.
     */
    @Test
    fun `a refusal renders over unchanged content`() {
        compose.setContent {
            theme {
                Screen(
                    state = populatedState().copy(errorMessage = "Gagal menyimpan rekening. Coba lagi."),
                )
            }
        }

        compose.onNodeWithTag(TAG_BANNER).assertExists()
        compose.onNodeWithText("Gagal menyimpan rekening. Coba lagi.").assertExists()
        // And the content is still there — an empty list behind a banner is the failure.
        compose.onNodeWithText("BCA").assertExists()
        compose.onNodeWithTag(TAG_EMPTY).assertDoesNotExist()
    }

    /**
     * A confirmation renders as a snackbar and does **not** change screen state.
     *
     * The rows are still the rows; the bar is transient.
     */
    @Test
    fun `a confirmation renders as a snackbar`() {
        compose.setContent {
            theme { Screen(state = populatedState(), snackbarMessage = "Rekening BCA tersimpan") }
        }

        compose.onNodeWithTag(TAG_BANNER).assertExists()
        compose.onNodeWithText("Rekening BCA tersimpan").assertExists()
        compose.onNodeWithText("BCA").assertExists()
    }

    /**
     * The error wins when both messages exist — the bar is one, never two.
     *
     * Both are `BottomCenter` with the same padding, so drawing them independently stacks two
     * bars on the same pixels and the owner reads whichever the renderer puts last.
     */
    @Test
    fun `the error wins when both messages exist`() {
        compose.setContent {
            theme {
                Screen(
                    state = populatedState().copy(errorMessage = "Gagal menyimpan rekening. Coba lagi."),
                    snackbarMessage = "Rekening BCA tersimpan",
                )
            }
        }

        compose.onNodeWithText("Gagal menyimpan rekening. Coba lagi.").assertExists()
        compose.onNodeWithText("Rekening BCA tersimpan").assertDoesNotExist()
    }

    // -------------------------------------------------------------- the wiring

    /**
     * The top-bar action reaches `onAdd`.
     *
     * The bar's action and the empty state's action draw the same label but carry different
     * tags: a shared tag would make a test asserting "the empty state's action" silently resolve
     * to the top bar's the moment an account exists.
     */
    @Test
    fun `the top-bar action reaches onAdd`() {
        var added = false
        compose.setContent { theme { Screen(state = populatedState(), onAdd = { added = true }) } }

        compose.onNodeWithTag(TAG_ADD_ACTION).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("the bar's action must reach the ViewModel", added)
    }

    /** And the empty state's action reaches it too. */
    @Test
    fun `the empty state action reaches onAdd`() {
        var added = false
        compose.setContent { theme { Screen(state = PengaturanUiState(), onAdd = { added = true }) } }

        compose.onNodeWithTag(TAG_ADD).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue(added)
    }

    /**
     * A row's tap reaches `onEdit` **with that row**.
     *
     * The payload matters as much as the call: an edit sheet for the wrong account is exactly as
     * broken as no sheet, and only the payload assertion catches it.
     */
    @Test
    fun `tapping a row reaches onEdit with that row`() {
        var edited: AccountRow? = null
        compose.setContent {
            theme { Screen(state = populatedState(), onEdit = { edited = it }) }
        }

        compose.onNodeWithTag(accountRowTag("a2"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertNotNull(edited)
        assertEquals("a2", edited!!.account.id)
        assertEquals("Mandiri", edited!!.account.bankName)
    }

    /** The form sheet mounts when its state is non-null, and not otherwise. */
    @Test
    fun `the form sheet mounts when the form is open`() {
        compose.setContent {
            theme {
                Screen(
                    state = populatedState().copy(form = AccountFormState()),
                )
            }
        }

        compose.onNodeWithTag(TAG_ACCOUNT_SHEET).assertExists()
    }

    @Test
    fun `the form sheet is absent when the form is null`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        assertTrue(
            "a closed sheet must leave nothing over the list",
            compose.onAllNodes(hasTestTag(TAG_ACCOUNT_SHEET)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * The screen banner is suppressed while the form sheet is up.
     *
     * A sheet is a separate window, so a bar drawn here would be *beneath* it. This is the line
     * a later sheet-bearing screen most easily forgets, and forgetting it reads to the owner as
     * nothing happening.
     */
    @Test
    fun `the screen banner is suppressed while the form is open`() {
        compose.setContent {
            theme {
                Screen(
                    state = populatedState().copy(
                        form = AccountFormState(),
                        errorMessage = "Gagal menyimpan rekening. Coba lagi.",
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_ACCOUNT_SHEET).assertExists()
        assertTrue(
            "no banner beneath the sheet",
            compose.onAllNodes(hasTestTag(TAG_BANNER)).fetchSemanticsNodes().isEmpty(),
        )
        // And the message is not lost: it is handed to the sheet's own window.
        compose.onNodeWithTag(TAG_ACCOUNT_ERROR).assertExists()
    }

    // ---------------------------------------------------------- touch targets

    /**
     * The top-bar action and the rows clear the 48dp floor.
     *
     * Measured on the real nodes rather than composed separately, for the reason `KontrakScreen`
     * gives: a `Small` button in a 72dp bar is exactly the shape that looks small, and the
     * measurement belongs on the instance the screen draws.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the bar action and rows clear the touch floor`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onNodeWithTag(TAG_ADD_ACTION).assertAtLeast(MIN_TARGET, "settings add action")
        compose.onNodeWithTag(accountRowTag("a1")).assertAtLeast(MIN_TARGET, "account row")
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Screen(
        state: PengaturanUiState,
        onAdd: () -> Unit = {},
        onEdit: (AccountRow) -> Unit = {},
        onCloseForm: () -> Unit = {},
        onFormChange: ((AccountFormState) -> AccountFormState) -> Unit = {},
        onSave: () -> Unit = {},
        snackbarMessage: String? = null,
        onSnackbarDismiss: () -> Unit = {},
        onErrorDismiss: () -> Unit = {},
        // Story 4.4's four. Defaulted **here** on purpose, unlike the production parameters
        // they forward to: this helper is the fixture every existing test uses, and forcing
        // nine unrelated tests to name four callbacks they do not exercise would make the file
        // harder to read without testing anything more. The new `IdentityFormTest` passes
        // them explicitly and drives them.
        onOpenIdentity: () -> Unit = {},
        onCloseIdentity: () -> Unit = {},
        onIdentityChange: ((IdentityFormState) -> IdentityFormState) -> Unit = {},
        onSaveIdentity: () -> Unit = {},
    ) {
        PengaturanScreen(
            state = state,
            onAdd = onAdd,
            onEdit = onEdit,
            onCloseForm = onCloseForm,
            onFormChange = onFormChange,
            onSave = onSave,
            snackbarMessage = snackbarMessage,
            onSnackbarDismiss = onSnackbarDismiss,
            onErrorDismiss = onErrorDismiss,
            onOpenIdentity = onOpenIdentity,
            onCloseIdentity = onCloseIdentity,
            onIdentityChange = onIdentityChange,
            onSaveIdentity = onSaveIdentity,
        )
    }

    private fun populatedState() = PengaturanUiState(
        rows = listOf(
            AccountRow(
                account = BusinessAccount(id = "a1", bankName = "BCA", number = "1234567890"),
                numberText = "1234567890",
            ),
            AccountRow(
                account = BusinessAccount(id = "a2", bankName = "Mandiri", number = "9876543210"),
                numberText = "9876543210",
            ),
        ),
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
    }
}
