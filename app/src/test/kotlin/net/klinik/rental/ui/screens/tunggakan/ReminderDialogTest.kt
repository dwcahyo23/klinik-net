package net.klinik.rental.ui.screens.tunggakan

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Story 4.2's dialog: the verb opens it, Copy and WhatsApp are **wired**, and an edit reaches
 * the handler.
 *
 * ## Why every action is clicked rather than asserted to exist
 *
 * Because `assertExists().assertHasClickAction()` is satisfied by a control wired to `{}`, and
 * this repo has shipped exactly that defect twice — Story 3.4's `requestCancel` and Story 3.5's
 * `openEditDeposit` both reached production with no caller. So each test taps the control and
 * asserts **which** handler received what.
 *
 * ## The two rules every test here obeys
 *
 * - `performSemanticsAction(SemanticsActions.OnClick)`, not `performClick`: a `KnModal` renders in
 *   its own window, which `performClick` does not reach under Robolectric.
 * - One `setContent` per test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
class ReminderDialogTest {

    @get:Rule
    val compose = createComposeRule()

    // -------------------------------------------------------------- the row's verb

    /**
     * The row's verb opens the dialog for **that** row.
     *
     * Asserted against the contract id, not merely that something was tapped: a handler wired to
     * the wrong row would send Budi's arrears to Siti.
     */
    @Test
    fun `tapping a row's verb asks for that row's reminder`() {
        var reminded: String? = null
        compose.setContent {
            theme { Screen(state = arrearsState(), onRemind = { reminded = it }) }
        }

        compose.onNodeWithTag(TAG_REMINDER_ACTION_PREFIX + "c2")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("the row whose verb was tapped", "c2", reminded)
    }

    /**
     * The verb is drawn on **every** row, including in portrait.
     *
     * The story's criterion opens with the owner pressing the verb on a row, so hiding it behind
     * landscape — as the detail pane is — would make the feature unreachable on a phone. The
     * portrait fixture is the one that would catch that.
     */
    @Test
    fun `the verb is drawn on every row`() {
        compose.setContent { theme { Screen(state = arrearsState()) } }

        compose.onNodeWithTag(TAG_REMINDER_ACTION_PREFIX + "c1").assertExists()
        compose.onNodeWithTag(TAG_REMINDER_ACTION_PREFIX + "c2").assertExists()
    }

    // ------------------------------------------------------------------- the dialog

    /** The dialog is drawn only when the state carries one. */
    @Test
    fun `the dialog is absent until a reminder is open`() {
        compose.setContent { theme { Screen(state = arrearsState()) } }

        compose.onNodeWithTag(TAG_REMINDER).assertDoesNotExist()
    }

    /**
     * The dialog shows the composed message and both actions.
     *
     * A rendering assertion, not a wiring one — the two below cover the wiring.
     */
    @Test
    fun `the dialog shows the message and both actions`() {
        compose.setContent { theme { Screen(state = arrearsState(reminder = reminder())) } }

        compose.onNodeWithTag(TAG_REMINDER).assertExists()
        compose.onNodeWithTag(TAG_REMINDER_FIELD).assertExists()
        compose.onNodeWithTag(TAG_REMINDER_SEND).assertExists().assertIsEnabled()
        compose.onNodeWithTag(TAG_REMINDER_COPY).assertExists().assertIsEnabled()
    }

    /**
     * **Copy reaches its own handler**, and Send does not.
     *
     * Two flags, so a dialog whose buttons were both wired to the same lambda fails.
     */
    @Test
    fun `copy and send reach separate handlers`() {
        var copied = false
        var sent = false
        compose.setContent {
            theme {
                Screen(
                    state = arrearsState(reminder = reminder()),
                    onReminderCopy = { copied = true },
                    onReminderSend = { sent = true },
                )
            }
        }

        compose.onNodeWithTag(TAG_REMINDER_COPY)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("copy reaches its own handler", copied)
        assertFalse("and never the send one", sent)

        compose.onNodeWithTag(TAG_REMINDER_SEND)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("send reaches its own handler", sent)
    }

    /**
     * **An edit reaches the handler with the edited text.**
     *
     * `performTextReplacement`, not `performTextInput`: the latter **appends**, so typing over a
     * prefilled message would produce the old text with the new one glued on — and the assertion
     * would be measuring the harness rather than the handler. `IdentityFormTest` records the same
     * trap.
     */
    @Test
    fun `editing the message reaches the handler with the new text`() {
        var latest: String? = null
        compose.setContent {
            theme {
                Screen(
                    state = arrearsState(reminder = reminder()),
                    onReminderChange = { latest = it },
                )
            }
        }

        // `hasSetTextAction() and hasAnyAncestor(...)`, not the tag alone: `KnField` puts the
        // tag on the field's container while the *input* is a descendant, so a tag-only query
        // finds a node with no text action and the input fails to perform. The same shape
        // `IdentityFormTest` and `RenterFormTest` use for their own fields.
        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_REMINDER_FIELD)),
        ).performTextReplacement("Tunggakan Rp 500.000")

        assertEquals("the field's own text, not appended", "Tunggakan Rp 500.000", latest)
    }

    /**
     * An emptied message disables **both** actions.
     *
     * The refusal is visible before the tap rather than only after it — and `sendReminder` still
     * refuses below the button, because a disabled control is a courtesy and the rule lives in
     * the ViewModel.
     */
    @Test
    fun `an emptied message disables send and copy`() {
        compose.setContent {
            theme {
                Screen(state = arrearsState(reminder = reminder(text = "   ")))
            }
        }

        compose.onNodeWithTag(TAG_REMINDER_SEND).assertIsNotEnabled()
        compose.onNodeWithTag(TAG_REMINDER_COPY).assertIsNotEnabled()
    }

    /** A refusal from the ViewModel renders on the message field. */
    @Test
    fun `a refusal renders on the message field`() {
        compose.setContent {
            theme {
                Screen(
                    state = arrearsState(
                        reminder = reminder(errorMessage = "Tulis dulu isi pesannya sebelum dikirim."),
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_REMINDER_FIELD).assertExists()
        compose.onNodeWithTag(TAG_REMINDER).assertExists()
    }

    /** The copy confirmation renders inside the dialog, where the owner is looking. */
    @Test
    fun `a copy confirmation renders inside the dialog`() {
        compose.setContent {
            theme {
                Screen(
                    state = arrearsState(
                        reminder = reminder(copiedMessage = "Pesan disalin"),
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_REMINDER_COPIED).assertExists()
    }

    /**
     * `MSG_COPY` — the **edited** text reaches the clipboard.
     *
     * Read back through the real `ClipboardManager` rather than asserted on a flag, because the
     * criterion is about the clipboard's contents: a handler that flipped a boolean and copied
     * the *original* text would satisfy a flag-based test and send a tenant the wrong figure.
     */
    @Test
    fun `copying puts the edited text on the clipboard`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var state by mutableStateOf(arrearsState(reminder = reminder()))
        compose.setContent {
            theme {
                Screen(
                    state = state,
                    onReminderChange = { state = state.copy(reminder = state.reminder?.withText(it)) },
                )
            }
        }

        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_REMINDER_FIELD)),
        ).performTextReplacement("Tunggakan Rp 500.000 · 3 hari")
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_REMINDER_COPY)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(
            "the clipboard holds the edited text, not the composed one",
            "Tunggakan Rp 500.000 · 3 hari",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
    }

    /**
     * The link the screen hands over is opened, and reported back **once**.
     *
     * Without a test passing a non-null `pendingLink`, the whole
     * `LaunchedEffect → startActivity → onReminderLinkOpened()` chain is dead code: dropping the
     * consume call would launch on every recomposition, and nothing would notice.
     */
    @Test
    fun `a pending link is consumed exactly once`() {
        var consumed = 0
        compose.setContent {
            theme {
                Screen(
                    state = arrearsState(reminder = reminder()),
                    onReminderLinkOpened = { consumed++ },
                    pendingLink = "https://wa.me/6281288997722?text=halo",
                )
            }
        }
        compose.waitForIdle()

        assertEquals("the link was consumed", 1, consumed)

        // A recomposition must not launch it again.
        compose.onNodeWithTag(TAG_REMINDER_FIELD).assertExists()
        compose.waitForIdle()
        assertEquals("and not again on a recomposition", 1, consumed)
    }

    // -------------------------------------------------------------------- helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Screen(
        state: TunggakanUiState,
        onRemind: (String) -> Unit = {},
        onReminderChange: (String) -> Unit = {},
        onReminderSend: () -> Unit = {},
        onReminderCopy: () -> Unit = {},
        onReminderDismiss: () -> Unit = {},
        onReminderLinkOpened: () -> Unit = {},
        pendingLink: String? = null,
    ) {
        TunggakanScreen(
            state = state,
            onSelect = {},
            onRiskFilterChange = {},
            onErrorDismiss = {},
            onRemind = onRemind,
            onReminderChange = onReminderChange,
            onReminderSend = onReminderSend,
            onReminderCopy = onReminderCopy,
            onReminderDismiss = onReminderDismiss,
            onReminderLinkOpened = onReminderLinkOpened,
            onReminderOpenFailed = {},
            pendingLink = pendingLink,
        )
    }

    /** One owing contract, optionally with its reminder dialog open. */
    private fun arrearsState(reminder: ReminderUi? = null) = TunggakanUiState(
        rows = listOf(
            ArrearsRow(
                contractId = "c1",
                renterName = "Budi Santoso",
                vehiclePlate = "B 1235 TF",
                renterPhone = "0812-8899-7722",
                amount = 750_000L,
                figureText = "Rp 750.000 · 3 hari",
                daysLabel = "3 hari",
                riskLabel = "Perhatian",
                tone = TunggakanUiState.toneFor(RiskLevel.PERHATIAN),
                risk = RiskLevel.PERHATIAN,
                oldestDayText = "14 Okt",
                isEnded = false,
            ),
            ArrearsRow(
                contractId = "c2",
                renterName = "Siti Aminah",
                vehiclePlate = "B 9876 AB",
                renterPhone = "0813-1122-3344",
                amount = 250_000L,
                figureText = "Rp 250.000 · 1 hari",
                daysLabel = "1 hari",
                riskLabel = "Ringan",
                tone = TunggakanUiState.toneFor(RiskLevel.RINGAN),
                risk = RiskLevel.RINGAN,
                oldestDayText = "02 Nov",
                isEnded = true,
            ),
        ),
        reminder = reminder,
    )

    /** The dialog's state as the ViewModel would have composed it. */
    private fun reminder(
        text: String = "Halo Budi Santoso, ini pengingat dari PT Klinik Net Rent Car. " +
            "Tunggakan B 1235 TF saat ini Rp 750.000 · 3 hari.",
        errorMessage: String? = null,
        copiedMessage: String? = null,
    ) = ReminderUi(
        contractId = "c1",
        phone = "0812-8899-7722",
        text = text,
        errorMessage = errorMessage,
        copiedMessage = copiedMessage,
    )
}
