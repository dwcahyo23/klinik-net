package net.klinik.rental.ui.screens.pengaturan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.data.repository.BusinessIdentityRepository
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Story 4.4's identity form: what it renders, and whether its Save is actually wired.
 *
 * ## Why the click-through is the test that matters here
 *
 * `assertExists().assertHasClickAction()` is satisfied by a control wired to `{}` — and this
 * repo has shipped that defect twice: Story 3.4's `requestCancel` and Story 3.5's
 * `openEditDeposit` were both implemented, both tested, and both unreachable because no
 * production caller ever fired them. A sheet whose Save renders and reaches nothing looks
 * identical to one that works, and it is the *only* control this story adds. So the Save is
 * **tapped** and the handler is asserted to have received the typed values — not that some
 * node has a click action.
 *
 * `performSemanticsAction(SemanticsActions.OnClick)` rather than `performClick`, because a
 * `ModalBottomSheet` renders in its own window and `performClick` does not reach it under
 * Robolectric. The same rule `RenterFormTest` follows.
 *
 * One `setContent` per test, holding `var state by mutableStateOf(...)` outside.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class IdentityFormTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The name opens **pre-filled with the default**, and it is editable.
     *
     * Both halves, and the first is the one a blank-field implementation fails: the criterion
     * says the default is *"sudah ada di prototipe, dan sekarang bisa diubah, bukan hardcode"*.
     * A blank field would invite an empty business name in every outgoing message; a field
     * pre-filled but not editable would be the hardcode this story exists to replace.
     */
    @Test
    fun `the name opens pre-filled with the default`() {
        compose.setContent { theme { Sheet(state = IdentityFormState(name = BusinessIdentityRepository.DEFAULT_NAME), onChange = { t -> t }, onSave = {}) } }

        compose.onNodeWithTag(TAG_IDENTITY_FIELD_NAME).assertExists()
        compose.onNodeWithText(BusinessIdentityRepository.DEFAULT_NAME).assertExists()
        assertEquals(
            "the address opens empty — a placeholder there would invite a value the owner " +
                "never chose",
            "",
            fieldText(TAG_IDENTITY_FIELD_ADDRESS),
        )
        assertEquals("and so does the phone", "", fieldText(TAG_IDENTITY_FIELD_PHONE))
    }

    /**
     * Save is disabled **after a blank name is typed**, not merely on a hand-built state.
     *
     * The version above constructs `IdentityFormState(name = "   ")` directly, which proves
     * `canSaveIdentity` reads the field — and says nothing about the *handler* path. If
     * `setIdentityName` stopped clearing or stopped copying, or `onChange` were wired to
     * nothing, this test would stay green while the owner could save an empty business name.
     * So the blank is typed through `onChange` here.
     */
    @Test
    fun `typing a blank name through onChange disables save`() {
        var state by mutableStateOf(IdentityFormState(name = "Rental SAYA"))
        compose.setContent {
            theme {
                Sheet(
                    state = state,
                    onChange = { transform -> state = transform(state) },
                    onSave = {},
                )
            }
        }

        compose.onNodeWithTag(TAG_IDENTITY_SAVE).assertExists().assertIsEnabled()

        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_IDENTITY_FIELD_NAME)),
        ).performTextReplacement("   ")
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_IDENTITY_SAVE).assertExists().assertIsNotEnabled()
    }

    /** A blank name keeps Save **disabled**, with the field's own reason. */
    @Test
    fun `a blank name keeps save disabled`() {
        compose.setContent {
            theme { Sheet(state = IdentityFormState(name = "   "), onChange = { t -> t }, onSave = {}) }
        }

        compose.onNodeWithTag(TAG_IDENTITY_SAVE).assertExists().assertIsNotEnabled()
    }

    /**
     * The blank refusal **renders on the name field**, not as a banner.
     *
     * An owner looking at the name needs the *name* to say why; the message landing on the
     * address would accuse a field that cannot fail.
     */
    @Test
    fun `the blank refusal renders on the name field`() {
        compose.setContent {
            theme {
                Sheet(
                    state = IdentityFormState(
                        nameError = "Isi nama usaha. Nama ini yang muncul di pesan pengingat ke penyewa.",
                    ),
                    onChange = { t -> t },
                    onSave = {},
                )
            }
        }

        // **Inside the name field, not merely somewhere on the sheet.** `KnField` renders
        // `error` as a row *beneath* its label, so the message is a descendant of the field's
        // tagged node rather than a node carrying the tag itself — which is why the query is
        // `hasAnyAncestor`, the same shape `RenterFormTest` uses for its own fields. Asking for
        // tag *and* text on one node would find nothing even when the message is exactly where
        // it belongs.
        compose.onNode(
            hasText(MESSAGE, substring = true)
                and hasAnyAncestor(hasTestTag(TAG_IDENTITY_FIELD_NAME)),
        ).assertExists()

        // And it is **not** on the address field, which has no rule to fail.
        assertTrue(
            "the address cannot be refused, so it never carries the message",
            compose.onAllNodes(
                hasText(MESSAGE, substring = true)
                    and hasAnyAncestor(hasTestTag(TAG_IDENTITY_FIELD_ADDRESS)),
            ).fetchSemanticsNodes().isEmpty(),
        )

        // Nor as a sheet-level banner — this refusal has a field, so it lands there.
        compose.onNodeWithTag(TAG_IDENTITY_ERROR).assertDoesNotExist()
    }

    /**
     * **The click-through.** Tapping Save reaches the handler with the form's own values.
     *
     * The values are asserted, not just the call: a handler wired to `onSave` with the wrong
     * argument — or with a hand-built identity instead of the form's — passes every
     * existence assertion and still saves the wrong business name into a message a tenant
     * reads.
     */
    @Test
    fun `tapping save hands the form's own values to the handler`() {
        var saved: IdentityFormState? = null
        var state by mutableStateOf(
            IdentityFormState(
                name = "Rental SAYA",
                address = "Jl. Boulevard Raya Q18",
                phone = "0812-8899-7722",
            ),
        )
        compose.setContent {
            theme {
                Sheet(
                    state = state,
                    onChange = { transform -> state = transform(state) },
                    onSave = { saved = state },
                )
            }
        }

        compose.onNodeWithTag(TAG_IDENTITY_SAVE)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertNotNull("save was reached", saved)
        assertEquals("Rental SAYA", saved!!.name)
        assertEquals("Jl. Boulevard Raya Q18", saved!!.address)
        assertEquals("0812-8899-7722", saved!!.phone)
    }

    /**
     * An edit to a field reaches the handler too — `onChange` wired to nothing would leave the
     * Save handing over values the owner never typed, which is the failure the click-through
     * above cannot catch because its starting state is correct.
     */
    @Test
    fun `an edit reaches the save handler through onChange`() {
        var saved: IdentityFormState? = null
        var state by mutableStateOf(IdentityFormState())
        compose.setContent {
            theme {
                Sheet(
                    state = state,
                    onChange = { transform -> state = transform(state) },
                    onSave = { saved = state },
                )
            }
        }

        // `hasSetTextAction() and hasAnyAncestor(...)`, not the tag alone: `KnField` puts the
        // tag on the field's own node while the **input** is a descendant, and `RenterFormTest`
        // makes the same distinction — a `SetText` action on the outer node does not exist.
        // `performTextReplacement`, not `performTextInput`: the field opens holding the default
        // name, and `performTextInput` **appends** — so inputting would leave
        // "Rental SAYAPT Klinik Net Rent Car", and the assertion would be testing the harness
        // rather than the handler. An owner replacing the default selects it first; this is
        // that, expressed once.
        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_IDENTITY_FIELD_NAME)),
        ).performTextReplacement("Rental SAYA")
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_IDENTITY_SAVE)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(
            "what the owner typed, not what the form opened with",
            "Rental SAYA",
            saved?.name,
        )
    }

    /** Cancelling reaches its own handler, and not save's. */
    @Test
    fun `cancelling reaches its own handler and not save`() {
        var saved = false
        var cancelled = false
        compose.setContent {
            theme {
                Sheet(
                    state = IdentityFormState(),
                    onChange = { t -> t },
                    onSave = { saved = true },
                    onDismissRequest = { cancelled = true },
                )
            }
        }

        compose.onNodeWithTag(TAG_IDENTITY_CANCEL)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("cancel reached its handler", cancelled)
        assertFalse("and never the save one", saved)
    }

    /** Both messages draw **inside** the sheet, which is a window of its own. */
    @Test
    fun `a failure and a confirmation both render inside the sheet`() {
        compose.setContent {
            theme {
                Sheet(
                    state = IdentityFormState(),
                    onChange = { t -> t },
                    onSave = {},
                    errorMessage = "Gagal menyimpan. Coba lagi.",
                    successMessage = "Rental SAYA tersimpan sebagai identitas usaha",
                )
            }
        }

        compose.onNodeWithTag(TAG_IDENTITY_ERROR).assertExists()
        compose.onNodeWithTag(TAG_IDENTITY_SUCCESS).assertExists()
    }

    /** All three fields are present, and each has its own tag. */
    @Test
    fun `the sheet renders all three fields`() {
        compose.setContent { theme { Sheet(state = IdentityFormState(name = BusinessIdentityRepository.DEFAULT_NAME), onChange = { t -> t }, onSave = {}) } }

        compose.onNodeWithTag(TAG_IDENTITY_FIELD_NAME).assertExists()
        compose.onNodeWithTag(TAG_IDENTITY_FIELD_ADDRESS).assertExists()
        compose.onNodeWithTag(TAG_IDENTITY_FIELD_PHONE).assertExists()
        compose.onNodeWithTag(TAG_IDENTITY_SAVE).assertExists().assertIsEnabled()
    }

    // -------------------------------------------------------------------- helpers

    /**
     * The transform the sheet hands to `onChange`, applied to the state it was given.
     *
     * A **value**, not a callback: `IdentityFormSheet` takes `onChange` as
     * `((IdentityFormState) -> IdentityFormState) -> Unit`, so a test that wants the field's
     * edit to reach the state must hand it that shape. Writing `onChange = { transform ->
     * state = transform(state) }` reads as equivalent and is not — that lambda's return value
     * is what the sheet's inner handler returns, and `mutableStateOf` yields `Unit`.
     */
    private fun IdentityFormState.changedTo(
        next: IdentityFormState,
    ): IdentityFormState = next

    /**
     * The address and phone open with **nothing in them**.
     *
     * Asserted on the input's own text rather than on a node's absence: the fields must exist —
     * the owner needs to type into them — so what is being claimed is that they hold nothing.
     * A placeholder here would be an invented value the owner never chose, which is the defect
     * `ContractFormSheet`'s start-date KDoc refuses for exactly this reason.
     */
    private fun fieldText(tag: String): String =
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
            .text

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Sheet(
        state: IdentityFormState,
        onDismissRequest: () -> Unit = {},
        /**
         * The sheet's `onChange`: a **callback taking a transform**, which it then calls with
         * the current state. Defaulted and mis-shaped `onChange` is what makes every edit
         * assertion vacuously pass while the owner's typing reaches nowhere — the defect
         * `Screens.Pengaturan`'s KDoc exists to prevent, and which this repo has shipped
         * twice. Required, so a test that forgets one fails to compile instead of passing.
         *
         * Call it as `onChange { it.setIdentityName("…") }` — a transform, not a statement —
         * because `mutableStateOf` yields `Unit`, and a lambda whose last expression is an
         * assignment would satisfy the callback's shape while returning nothing.
         */
        onChange: ((IdentityFormState) -> IdentityFormState) -> Unit,
        /** Also required: a defaulted save lets a test assert "reached" while it reached nothing. */
        onSave: () -> Unit,
        // These carry the message, so defaulting them only asserts "a banner renders" — never
        // that its close glyph reaches a handler, which is the `SettleContractSheet` gap.
        errorMessage: String? = null,
        onDismissError: () -> Unit = {},
        successMessage: String? = null,
        onDismissSuccess: () -> Unit = {},
    ) {
        IdentityFormSheet(
            state = state,
            onDismissRequest = onDismissRequest,
            onChange = onChange,
            onSave = onSave,
            errorMessage = errorMessage,
            onDismissError = onDismissError,
            successMessage = successMessage,
            onDismissSuccess = onDismissSuccess,
        )
    }

    private companion object {
        /** The blank-name refusal, spelled once so the two scoped queries cannot drift. */
        const val MESSAGE = "Isi nama usaha"
    }
}
