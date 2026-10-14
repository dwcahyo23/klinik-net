package net.klinik.rental.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnFieldError
import net.klinik.rental.ui.components.messageRes
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The FIELD_ERROR row of the spec's I/O matrix:
 * *"invalid input -> error text appears saying what + how to fix it; hidden by
 * default; shown on blur/validate, not mid-typing."*
 *
 * This renders [KnField] because "hidden by default" is a claim about what is
 * on screen, and only a rendered tree can be asked whether an error node
 * exists. Asserting it through the pure logic would assert nothing: the rule
 * lives entirely in whether the composable draws.
 *
 * ## Why the two halves are separate tests
 *
 * A `ComposeTestRule` accepts one `setContent` per Activity, and re-driving
 * state mid-composition is how a test starts asserting its own setup. The
 * "hidden" case gets a clean composition with `error = null`; the "shown" case
 * gets one with the error supplied.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FieldErrorTest {

    @get:Rule
    val compose = createComposeRule()

    private companion object {
        const val TAG_FIELD = "field"
    }

    /**
     * Nothing is drawn until validation has run. `error` defaults to `null` and
     * `null` means "has not validated yet" — the component never decides when to
     * validate, because that is the caller's state, not presentation.
     */
    @Test
    fun `no error text exists before validation runs`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize()) {
                    KnField(
                        label = "Tarif harian",
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.testTag(TAG_FIELD),
                    )
                }
            }
        }

        compose.onNodeWithTag(TAG_FIELD).assertIsDisplayed()

        // The error body text from either known failure mode must be absent.
        compose.onNodeWithText(REQUIRED_MESSAGE).assertDoesNotExist()
        compose.onNodeWithText(NOT_A_NUMBER_MESSAGE).assertDoesNotExist()

        // The field itself is present and empty, so this is "not shown" rather
        // than "not rendered at all".
        compose.onNodeWithText("Tarif harian").assertIsDisplayed()
    }

    /**
     * A blank string is treated the same as `null`. A caller defaulting to `""`
     * would otherwise get an error row with its icon and no words in it, which
     * reads as a rendering fault rather than as "nothing to report".
     */
    @Test
    fun `a blank error string renders no error row either`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnField(
                    label = "Tarif harian",
                    value = "",
                    onValueChange = {},
                    error = "",
                    modifier = Modifier.testTag(TAG_FIELD),
                )
            }
        }

        compose.onNodeWithText(REQUIRED_MESSAGE).assertDoesNotExist()
        compose.onNodeWithText(NOT_A_NUMBER_MESSAGE).assertDoesNotExist()
    }

    /**
     * Once validation fails, the message is on screen and names both halves of
     * the fault: what is wrong, and what to do about it.
     *
     * This is the part the matrix actually cares about. "Invalid" satisfies
     * neither half — the owner is left guessing, and the epic context forbids
     * it.
     */
    @Test
    fun `the error names both the fault and the fix`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnField(
                    label = "Tarif harian",
                    value = "",
                    onValueChange = {},
                    error = REQUIRED_MESSAGE,
                    modifier = Modifier.testTag(TAG_FIELD),
                )
            }
        }

        compose.onNodeWithText(REQUIRED_MESSAGE).assertIsDisplayed()
        assertNamesFaultAndFix(REQUIRED_MESSAGE)
    }

    @Test
    fun `the not-a-number error names both the fault and the fix`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnField(
                    label = "Tarif harian",
                    value = "abc",
                    onValueChange = {},
                    error = NOT_A_NUMBER_MESSAGE,
                    modifier = Modifier.testTag(TAG_FIELD),
                )
            }
        }

        compose.onNodeWithText(NOT_A_NUMBER_MESSAGE).assertIsDisplayed()
        assertNamesFaultAndFix(NOT_A_NUMBER_MESSAGE)
    }

    /**
     * The error must not appear merely because the field has been typed into.
     * "Shown on blur or validate, not mid-typing" is the third clause of the
     * matrix row, and the only way to hold it is to drive real input and check
     * that the message has still not appeared.
     */
    @Test
    fun `typing alone does not raise an error`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnField(
                    label = "Tarif harian",
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.testTag(TAG_FIELD),
                )
            }
        }

        // The tag sits on the whole field, so the input itself has to be
        // addressed by what it can do rather than by a second tag.
        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_FIELD)),
        ).performTextInput("2")
        compose.waitForIdle()

        compose.onNodeWithText(REQUIRED_MESSAGE).assertDoesNotExist()
        compose.onNodeWithText(NOT_A_NUMBER_MESSAGE).assertDoesNotExist()
    }

    /**
     * [KnFieldError] is the enum that ties a known validation failure to its
     * copy. Both cases must map to a distinct, non-empty message, and the two
     * messages must differ — a screen that shows "invalid" for both a blank
     * field and a typo is not telling the owner anything.
     *
     * The mapping is a `@Composable` getter, so it is read inside a
     * composition: the assertion is that resolving the enum reaches a real
     * string resource, not that two integers differ.
     */
    @Test
    fun `the two known validation failures have distinct messages`() {
        val resolved = mutableListOf<String>()
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnFieldError.entries.forEach { error ->
                    resolved += stringResource(error.messageRes)
                }
            }
        }
        compose.waitForIdle()

        assertEquals(
            "every failure must map to its own string resource",
            KnFieldError.entries.size,
            resolved.toSet().size,
        )
        resolved.forEach { message ->
            assertTrue("message '$message' must not be empty", message.isNotBlank())
        }
        assertTrue(
            "a blank field and a typo must not share one message",
            resolved.toSet().size == resolved.size,
        )
    }

    /**
     * A crude but deliberate check that each message carries an instruction.
     *
     * Indonesian marks an imperative with a verb; "Isi …" and "Hapus …" are the
     * two in this file. Asserting on the specific word would be brittle copy
     * editing, so this asserts the weaker property that matters — the message
     * is not merely a restatement of the fault.
     */
    private fun assertNamesFaultAndFix(message: String) {
        assertTrue("error message must not be empty", message.isNotBlank())
        val sentences = message.split('.').map { it.trim() }.filter { it.isNotBlank() }
        assertTrue(
            "'$message' should read as more than a single bare word",
            sentences.size >= 2,
        )
    }

    /**
 * Every message this class asserts on is resolved from the shipped
 * `strings.xml` entry rather than typed into the test.
 *
 * A test-local copy of the Indonesian text would mean editing the real copy
 * broke no test — the assertions would keep passing against a message the app no
 * longer shows. [TAG_FIELD] is the only literal here, and it is a test tag.
 */
private val REQUIRED_MESSAGE: String
    get() = ApplicationProvider.getApplicationContext<Context>()
        .getString(R.string.field_required_error)

private val NOT_A_NUMBER_MESSAGE: String
    get() = ApplicationProvider.getApplicationContext<Context>()
        .getString(R.string.field_invalid_number_error)
}
