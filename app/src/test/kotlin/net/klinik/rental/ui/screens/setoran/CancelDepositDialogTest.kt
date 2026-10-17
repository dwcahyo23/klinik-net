package net.klinik.rental.ui.screens.setoran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric render of the **real** cancellation dialog.
 *
 * ## What this file is for, given `CancelDepositTest` covers the repository
 *
 * That one proves the arithmetic and the refusal rules. This one proves what the owner is
 * actually shown, and — the part nothing else can reach — that the dialog's four callbacks
 * are wired to the four things they are for. A dialog that renders every string correctly
 * and hands every press to the same lambda would pass a render test that only reads text.
 *
 * ## The AC this makes structural
 *
 *"setoran yang sudah dibatalkan tidak bisa dibatalkan lagi"* is enforced in the repository
 * and read back in `uiState.isCanceling`; this file does not restate it. What it does pin
 * is UX-DR11's third tier: **a dialog plus a mandatory reason**, both present in the same
 * window, which is the only confirmation in the app that asks for writing.
 *
 * ## The two rules every test here obeys
 *
 * - `assertExists()`, never `assertIsDisplayed()`. Below the Robolectric fold a node
 *   measures zero, and "off-screen, not missing" reads identically to "present".
 * - One `setContent` per test, holding `var state by mutableStateOf(...)` outside so the
 *   state can be mutated between `waitForIdle()` calls.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CancelDepositDialogTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The three things the dialog is made of all exist: the impact sentence, the reason
     * field, and the modal around them.
     *
     * Asserted as three nodes rather than as the dialog's presence, because a dialog whose
     * body was dropped renders fine — the title, the two buttons and an empty box all draw.
     */
    @Test
    fun `the impact and the reason field both exist`() {
        compose.setContent { theme { Dialog(state = openDialog()) } }

        compose.onNodeWithTag(TAG_CANCEL_DIALOG).assertExists()
        compose.onNodeWithTag(TAG_CANCEL_IMPACT).assertExists()
        compose.onNodeWithTag(TAG_CANCEL_REASON).assertExists()
    }

    /**
     * The impact line is the epic's **sentence**, naming the renter and the figure their
     * arrears return to.
     *
     * This is the assertion that keeps the dialog from being a second preview. A reused
     * `Tunggakan: Rp x → Rp y` line would render here and satisfy every structural test in
     * this file while dropping the one clause the AC actually specifies.
     */
    @Test
    fun `the impact names the renter and the figure their arrears return to`() {
        compose.setContent { theme { Dialog(state = openDialog()) } }

        compose.onNodeWithText("Tunggakan Budi Santoso akan kembali menjadi Rp 4.250.000.")
            .assertExists()
        // And emphatically not the preview's two-figure shape.
        compose.onAllNodes(hasText("→")).assertCountEquals(0)
    }

    /** The title names what is being undone, with the amount in it. */
    @Test
    fun `the title names the amount being undone`() {
        compose.setContent { theme { Dialog(state = openDialog()) } }

        compose.onNodeWithText("Batalkan setoran Rp 750.000?").assertExists()
    }

    /**
     * The reason field is labelled as **mandatory**, in the label itself.
     *
     * Asserted on the label rather than on the error, because the error only appears after
     * a failed press — and a field whose obligation is only explained reactively is one
     * the owner has already got wrong once.
     */
    @Test
    fun `the reason field says it is mandatory`() {
        compose.setContent { theme { Dialog(state = openDialog()) } }

        compose.onNodeWithText("Alasan pembatalan (wajib)").assertExists()
    }

    /**
     * The confirm is disabled until a reason exists.
     *
     * The AC's *"alasan wajib diisi"* in control terms. The rule is enforced twice — here
     * for the button and in the repository for the data — so this asserts the reflection of
     * the rule rather than its only enforcement.
     */
    @Test
    fun `the confirm is disabled until a reason is written`() {
        var state by mutableStateOf(openDialog())
        compose.setContent { theme { Dialog(state = state) } }

        compose.onNodeWithText("Batalkan setoran").assertIsNotEnabled()

        state = state.setReason("salah tanggal")
        compose.waitForIdle()

        compose.onNodeWithText("Batalkan setoran").assertIsEnabled()
    }

    /**
     * Typing reaches the state through `onChange`, so the button goes live on its own.
     *
     * The wiring claim the enabled/disabled test depends on: a dialog that renders the
     * right control but never forwards the input still passes that test, because it
     * drives `state` directly.
     *
     * Typed into the node carrying the **text-field** semantics — found by
     * `hasSetTextAction()`, then checked to be the reason field by a
     * `hasAnyAncestor(TAG_CANCEL_REASON)` sibling match, because `KnField` puts the
     * caller's tag on its outer *column* while the editable node is the `BasicTextField`
     * nested inside. `useUnmergedTree` because the merged tree collapses the field into
     * its parent row.
     */
    @Test
    fun `typing a reason reaches the dialog state`() {
        var state by mutableStateOf(openDialog())
        compose.setContent {
            theme {
                Dialog(state = state, onChange = { next -> state = next })
            }
        }

        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .onFirst()
            .assert(hasAnyAncestor(hasTestTag(TAG_CANCEL_REASON)))
        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .onFirst()
            .performTextInput("salah tanggal")
        compose.waitForIdle()

        assertEquals("salah tanggal", state.reason)
        assertTrue("and the button is now live", state.canCancel)
        compose.onNodeWithText("Batalkan setoran").assertIsEnabled()
    }

    /**
     * An error is drawn under the field, and it is the mandatory-reason message.
     *
     * Asserted on the text because the error string is the AC's own reasoning — *"tanpa
     * alasan, riwayat setteran ini tidak bisa diaudit"* — and an error that rendered as
     * "wajib diisi" would satisfy every other test in this file.
     */
    @Test
    fun `the refusal renders inside the dialog`() {
        compose.setContent {
            theme {
                Dialog(
                    state = openDialog().copy(
                        reasonError = "Tulis alasan pembatalannya. " +
                            "Tanpa alasan, riwayat setteran ini tidak bisa diaudit.",
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_CANCEL_REASON).assertExists()
        compose.onNodeWithText(
            "Tulis alasan pembatalannya. Tanpa alasan, riwayat setteran ini tidak bisa diaudit.",
        ).assertExists()
    }

    /**
     * The confirm press reaches `onConfirm`, and does **not** reach `onDismiss`.
     *
     * The two buttons differ by one word and the owner is destroying an audit record, so a
     * dialog where both fire the same lambda is exactly the failure this guards. Counted
     * rather than merely non-null, because "the dismiss fired too" reads identically to
     * "the dismiss did nothing" if you only assert the confirm.
     */
    @Test
    fun `the confirm button calls onConfirm and not onDismiss`() {
        var confirms = 0
        var dismisses = 0
        compose.setContent {
            theme {
                Dialog(
                    state = openDialog().setReason("salah tanggal"),
                    onConfirm = { confirms++ },
                    onDismiss = { dismisses++ },
                )
            }
        }

        compose.onNodeWithText("Batalkan setoran").assertIsEnabled()
        compose.onNodeWithText("Batalkan setoran").performClick()
        compose.waitForIdle()

        assertEquals("the confirm fired once", 1, confirms)
        assertEquals("and the dismiss did not", 0, dismisses)
    }

    /**
     * And the dismiss button is the opposite: it fires its own handler and nothing else.
     *
     * Its own test rather than part of the one above, because both fire a lambda and a
     * shared assertion would pass if the two lambdas were swapped.
     */
    @Test
    fun `the dismiss button calls onDismiss and not onConfirm`() {
        var confirms = 0
        var dismisses = 0
        compose.setContent {
            theme {
                Dialog(
                    state = openDialog().setReason("salah tanggal"),
                    onConfirm = { confirms++ },
                    onDismiss = { dismisses++ },
                )
            }
        }

        // Present even with a reason written — walking away is always available, which is
        // what makes the mandatory reason a rule rather than a trap.
        compose.onNodeWithText("Batal").assertIsEnabled()
        compose.onNodeWithText("Batal").performClick()
        compose.waitForIdle()

        assertEquals("the dismiss fired once", 1, dismisses)
        assertEquals("and the confirm did not", 0, confirms)
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun Dialog(
        state: CancelDepositUi,
        onChange: (CancelDepositUi) -> Unit = {},
        onConfirm: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        CancelDepositDialog(
            state = state,
            onChange = onChange,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * The dialog as the ViewModel builds it for Budi, on the fixture the ViewModel tests
     * use: 17 charge days at Rp 250.000, a Rp 750.000 deposit on 14 Oct.
     *
     * The arrears figure is the whole period because the deposit is what would be
     * cancelled — so undoing it returns every open day. Written here rather than derived,
     * so the sentence this file asserts cannot drift with the fixture.
     */
    private fun openDialog() = CancelDepositUi(
        paymentId = "payment-1",
        contractId = "contract-1",
        title = "Batalkan setoran Rp 750.000?",
        impactLine = "Tunggakan Budi Santoso akan kembali menjadi Rp 4.250.000.",
        amountLabel = "Rp 750.000",
    )
}