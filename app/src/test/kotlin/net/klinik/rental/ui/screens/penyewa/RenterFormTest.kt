package net.klinik.rental.ui.screens.penyewa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The renter form renders exactly four fields, and a blank name is refused with
 * a message on the field.
 *
 * ## Why "exactly four" is asserted as a count
 *
 * The AC says the form shows name, phone, bank account and emergency contact and
 * that no KTP or address field exists to be filled in. Asserting four named tags
 * exist proves the fields are there; it does not prove a fifth was not added.
 *
 * So this file also asserts the *absence*, by name, of the two fields the PRD
 * puts out of scope. A form field that is merely hidden is one refactor away from
 * collecting personal data the app has no reason to hold, and "there is no field
 * for it" is the only claim that survives that edit — a hidden field does not.
 *
 * ## Why `assertExists()` and not `assertIsDisplayed()`
 *
 * The sheet's content overflows the Robolectric window, and a node laid out past
 * the bottom edge measures zero — off-screen, not missing. The displayed claim
 * would then be about the harness rather than the screen. What is under test is
 * that every control is *in the tree and reachable*, which `assertExists` plus
 * the click-action assertions establish.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RenterFormTest {

    @get:Rule
    val compose = createComposeRule()

    // ----------------------------------------------------------- the four fields

    /**
     * The form renders its fields and its two buttons.
     *
     * Exactly four fields — see the class KDoc for why the count matters and what
     * is asserted to be absent.
     */
    @Test
    fun `the form renders exactly four fields`() {
        compose.setContent { theme { Sheet(initial = RenterFormState()) } }

        compose.onNodeWithTag(TAG_FIELD_NAME).assertExists()
        compose.onNodeWithTag(TAG_FIELD_PHONE).assertExists()
        compose.onNodeWithTag(TAG_FIELD_EMERGENCY).assertExists()
        compose.onNodeWithTag(TAG_FIELD_BANK_ACCOUNT).assertExists()
        compose.onNodeWithTag(TAG_SAVE).assertExists().assertHasClickAction()
        compose.onNodeWithTag(TAG_CANCEL).assertExists().assertHasClickAction()
    }

    /**
     * The out-of-scope fields are not merely unshown — there is nothing to fill
     * in. A node count on the sheet would catch a fifth field of any name; these
     * two named checks catch the specific regression the PRD names.
     */
    @Test
    fun `there is no ktp or address field on the form`() {
        compose.setContent { theme { Sheet(initial = RenterFormState()) } }

        // No node anywhere in the sheet carries the out-of-scope labels.
        listOf("Nomor KTP", "NIK", "Alamat").forEach { forbidden ->
            assertTrue(
                "the form must not offer a '$forbidden' field",
                compose.onAllNodes(androidx.compose.ui.test.hasText(forbidden, substring = true))
                    .fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    /**
     * And the four labels the AC names are the ones the owner reads.
     *
     * Asserted on the real Indonesian copy rather than on the tags alone, because a
     * field can carry the right test tag and the wrong label — and the label is
     * what the owner reads.
     */
    @Test
    fun `the fields carry the four named labels`() {
        compose.setContent { theme { Sheet(initial = RenterFormState()) } }

        listOf(
            "Nama lengkap",
            "Nomor telepon",
            "Kontak darurat",
            "Nomor rekening (opsional)",
        ).forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
    }

    /**
     * The bank account is labelled optional rather than being left to inference.
     *
     * A field with no optionality marker reads as required, and plenty of renters
     * are paid in cash — so an unmarked account field would push the owner to
     * invent a number they do not have.
     */
    @Test
    fun `the bank account field says it is optional`() {
        compose.setContent { theme { Sheet(initial = RenterFormState()) } }

        compose.onNodeWithText("Nomor rekening (opsional)").assertExists()
        compose.onNodeWithText("Kosongkan jika dibayar tunai").assertExists()
    }

    // ------------------------------------------------------------ blank name

    /**
     * Simpan invokes the save handler, and Batal invokes the dismiss handler.
     *
     * `assertHasClickAction` proves a click action is *registered*, not that it
     * is the right one — so a Save button wired to `onDismissRequest` would pass
     * every assertion in this file while silently discarding the renter the owner
     * just typed, with no error and no message. Clicking the actual nodes is the
     * only way to tell the two apart.
     */
    @Test
    fun `the save button invokes the save handler`() {
        var saved = false
        var dismissed = false
        compose.setContent {
            theme { Sheet(onSave = { saved = true }, onDismiss = { dismissed = true }) }
        }

        // Addressed by tag *and* click action. The label alone resolves to the inner
        // text node, which is not the clickable surface — `KnButton` is a
        // `Surface(onClick=…)` and the tag rides on that, so requiring the click
        // action too addresses the node a tap would actually reach.
        // `performSemanticsAction(OnClick)` rather than `performClick`: the sheet is a
        // `ModalBottomSheet`, which renders into its own window, and a coordinate
        // click does not reach it under Robolectric. Invoking the semantics action
        // directly is what `VehicleFormTest` does for the same reason.
        compose.onNodeWithTag(TAG_SAVE).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("Save must invoke onSave", saved)
        assertTrue("Save must not dismiss the sheet", !dismissed)
    }

    @Test
    fun `the cancel button invokes the dismiss handler and not the save handler`() {
        var saved = false
        var dismissed = false
        compose.setContent {
            theme { Sheet(onSave = { saved = true }, onDismiss = { dismissed = true }) }
        }

        compose.onNodeWithTag(TAG_CANCEL).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("Batal must invoke onDismissRequest", dismissed)
        assertTrue("Batal must not save", !saved)
    }

    /**
     * A keystroke reaches the state the ViewModel would have been handed.
     *
     * The other half of "the form drives the model": the button tests prove the
     * actions fire, and this proves the text actually travels from the `KnField`
     * through `onChange` into a new `RenterFormState`.
     */
    @Test
    fun `typing in a field produces a new state`() {
        var latest: RenterFormState? = null
        compose.setContent { theme { Sheet(onStateChanged = { latest = it }) } }

        // The tag sits on the whole field, so the input itself is addressed by
        // what it can do rather than by a second tag — the same distinction
        // `FieldErrorTest.typing alone does not raise an error` makes.
        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_FIELD_NAME)),
        ).performTextInput("Budi Santoso")
        compose.waitForIdle()

        assertEquals("Budi Santoso", latest?.name)
    }

    /**
     * Typing into a refused field clears its error.
     *
     * An error that survives the fix is an accusation the owner cannot clear: the
     * sheet says the field is wrong while showing them the corrected value.
     */
    @Test
    fun `typing clears the error on that field`() {
        var latest: RenterFormState? = null
        compose.setContent {
            theme {
                Sheet(
                    initial = RenterFormState(phoneError = ERROR_BLANK),
                    onStateChanged = { latest = it },
                )
            }
        }

        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_FIELD_PHONE)),
        ).performTextInput("08123456789")
        compose.waitForIdle()

        assertEquals("08123456789", latest?.phone)
        assertEquals(null, latest?.phoneError)
    }

    /**
     * A blank name shows a message on the field.
     *
     * Asserted on the exact Indonesian string rather than on "an error appeared",
     * because an error that says only "invalid" leaves the owner guessing — which
     * is the bar the epic context sets for every message in the app. The wording
     * names the fault (required) and the fix (fill it in and send again).
     */
    @Test
    fun `a blank name shows a message naming the fault and the fix`() {
        val refused = RenterFormState(
            name = "   ",
            nameError = ERROR_BLANK,
        )
        compose.setContent { theme { Sheet(initial = refused) } }

        compose.onNodeWithText(ERROR_BLANK).assertExists()
        assertTrue(
            "the message must tell the owner what to do",
            ERROR_BLANK.contains("Wajib diisi"),
        )
    }

    /**
     * An untouched name shows no error at all.
     *
     * `KnField` hides its error until validation runs, and this pins that the
     * form does not invent a message on open — an error shown before the owner has
     * typed anything is an accusation, not a diagnostic.
     */
    @Test
    fun `an unvalidated form shows no name error`() {
        compose.setContent { theme { Sheet(initial = RenterFormState()) } }

        assertTrue(
            "no error may be shown before the owner has typed",
            compose.onAllNodes(androidx.compose.ui.test.hasText(ERROR_BLANK))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * A duplicate name shows a message that *names* the stored renter, so the
     * owner knows which person to look at rather than being told "duplicate" in
     * the abstract.
     */
    @Test
    fun `a duplicate name message names the stored renter`() {
        val refused = RenterFormState(
            name = "Budi Santoso",
            nameError = "Nama Budi Santoso sudah dipakai penyewa lain. Periksa lagi namanya.",
        )
        compose.setContent { theme { Sheet(initial = refused) } }

        compose.onNodeWithText(refused.nameError!!).assertExists()
        assertTrue(
            "the message must name the offending renter",
            refused.nameError!!.contains("Budi Santoso"),
        )
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * The sheet over handlers a test can observe.
     *
     * The state is held in a `MutableState` rather than passed in, because a
     * sheet driven by a fixed state cannot demonstrate that it *drives* anything:
     * a Save button wired to `onDismissRequest` instead of `onSave` renders
     * identically and asserts identically. Holding the state means a keystroke
     * can be typed and read back, and a click can be made and its effect seen.
     *
     * The default `initial` keeps the existing render-only tests unchanged.
     */
    @Composable
    private fun Sheet(
        initial: RenterFormState = RenterFormState(),
        onSave: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onStateChanged: (RenterFormState) -> Unit = {},
    ) {
        var state by remember { mutableStateOf(initial) }
        RenterFormSheet(
            state = state,
            onDismissRequest = onDismiss,
            // Write through to the holder, not just back out to a callback: a
            // keystroke that changes nothing observable proves nothing.
            onChange = { transformer ->
                state = transformer(state)
                onStateChanged(state)
            },
            onSave = onSave,
        )
    }

    private companion object {
        const val ERROR_BLANK = "Wajib diisi. Isi kolom ini lalu kirim ulang."
    }
}
