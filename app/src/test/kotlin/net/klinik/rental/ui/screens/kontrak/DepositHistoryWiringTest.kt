package net.klinik.rental.ui.screens.kontrak

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.screens.setoran.DepositHistoryRowUi
import net.klinik.rental.ui.screens.setoran.DepositHistoryUi
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The two verbs that close Epic 3's deferrals, asserted end to end.
 *
 * ## Why this file is the story
 *
 * Story 3.4's `requestCancel` and Story 3.5's `openEditDeposit` were **implemented, tested,
 * and unreachable**. Both read the contract from the deposit sheet's own form state, so a
 * cancellation could only be started from inside a form that opened blank — and both specs
 * recorded that as a deferral pointing at a deposit history.
 *
 * `DepositHistoryTest` proves the rows are there. **This file proves they are tappable**, and
 * that is the assertion that closes the deferrals: without a test naming the payment id that
 * reached the handler, the code could be perfect and both features would still be reachable
 * by nobody.
 *
 * ## Why each test asserts the **id**, not "a callback fired"
 *
 * Because a row wired to the wrong deposit is the failure that matters here: cancelling Budi's
 * payment when the owner tapped Siti's. `assertHasClickAction()` cannot tell that from a
 * correct wiring — this repo has shipped that defect three times — so every test below reads
 * what the handler actually received.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DepositHistoryWiringTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Tapping **cancel** reaches the handler **with that row's payment id**.
     *
     * Story 3.4's deferral, closing. Asserted on the id rather than on a flag because a
     * handler that received *some* id would satisfy a weaker test while opening the
     * cancellation dialog for the wrong person.
     */
    @Test
    fun `tapping cancel reaches the handler with that row's payment id`() {
        var cancelledFor: String? = null
        compose.setContent {
            theme {
                Block(
                    state = history(),
                    onRequestCancel = { _, _, paymentId -> cancelledFor = paymentId },
                )
            }
        }

        compose.onNodeWithTag(depositHistoryCancelTag("p1"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("the tapped row's own deposit", "p1", cancelledFor)
    }

    /**
     * The cancel verb carries the **contract and renter** the deposit belongs to.
     *
     * Story 3.4's impact sentence is *"Tunggakan Pak Budi akan kembali menjadi…"* — it names
     * a person, and `requestCancel` reads both from parameters now. A row that passed only
     * the payment id would leave the dialog saying nothing about who it affects.
     */
    @Test
    fun `the cancel verb carries the contract and the renter`() {
        var cancelArgs: Triple<String, String, String>? = null
        compose.setContent {
            theme {
                Block(
                    state = history(),
                    onRequestCancel = { contractId, renter, paymentId ->
                        cancelArgs = Triple(contractId, renter, paymentId)
                    },
                )
            }
        }

        compose.onNodeWithTag(depositHistoryCancelTag("p2"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(
            "the row that owns the deposit decides where the cancellation applies",
            Triple(CONTRACT_ID, "Budi Santoso", "p2"),
            cancelArgs,
        )
    }

    /**
     * Tapping **correct** reaches Story 3.5's `openEditDeposit` **with that payment id**.
     *
     * Story 3.5's deferral, closing — the same assertion, the other verb.
     */
    @Test
    fun `tapping correct reaches the handler with that row's payment id`() {
        var edited: String? = null
        compose.setContent {
            theme {
                Block(
                    state = history(),
                    onRequestEdit = { paymentId -> edited = paymentId },
                )
            }
        }

        compose.onNodeWithTag(depositHistoryEditTag("p2"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("the tapped row's own deposit", "p2", edited)
    }

    /**
     * A **cancelled** row offers **neither** verb.
     *
     * Not disabled — **absent**. `correctDeposit` refuses a cancelled deposit with
     * `DepositAlreadyCanceledException` and `cancelDeposit` refuses a second cancellation,
     * so a control rendered on that row would accept a tap and do nothing: the "renders and
     * is wired to nothing" defect this repo has shipped three times, and the epic's own rule
     * that a greyed-out control is a claim the data layer will not honour.
     *
     * Asserted as **absence of the node**, not as a disabled state — the distinction is the
     * whole point of this test.
     */
    @Test
    fun `a cancelled row offers neither verb`() {
        compose.setContent {
            theme {
                Block(
                    state = history(
                        rows = listOf(
                            row("p1", isEditable = false),
                            row("p2", isEditable = true),
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithTag(depositHistoryEditTag("p1")).assertDoesNotExist()
        compose.onNodeWithTag(depositHistoryCancelTag("p1")).assertDoesNotExist()
        // And the live row still has both — so the absence above is about the cancellation,
        // not about the block failing to render verbs at all.
        compose.onNodeWithTag(depositHistoryEditTag("p2")).assertExists()
        compose.onNodeWithTag(depositHistoryCancelTag("p2")).assertExists()
    }

    /**
     * The owner's reason for cancelling is **shown** on a cancelled row.
     *
     * Story 3.4's soft delete exists so this sentence is readable later. A badge that says
     * "Dibatalkan" without the reason would keep the row and lose the only part of it that
     * answers *"why did Budi's arrears jump on the 17th"*.
     */
    @Test
    fun `a cancelled row shows the owner's reason`() {
        compose.setContent {
            theme {
                Block(
                    state = history(
                        rows = listOf(row("p1", isEditable = false, cancelReason = "salah tanggal")),
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_DEPOSIT_HISTORY_REASON).assertExists()
        compose.onNodeWithText("Alasan pembatalan: salah tanggal").assertExists()
    }

    /** No deposits is an **invitation**, not a blank area. */
    @Test
    fun `a contract with no deposits says what to do`() {
        compose.setContent {
            theme { Block(state = history(rows = emptyList())) }
        }

        compose.onNodeWithTag(TAG_DEPOSIT_HISTORY_EMPTY).assertExists()
        compose.onNodeWithText("Belum ada setoran").assertExists()
    }

    /**
     * Every verb clears the 48dp floor.
     *
     * Story 4.1 shipped a 38dp rail item and Story 4.5 shipped a 44dp bottom nav — both on
     * this exact floor, both caught only by a measurement. The verbs are the app's only route
     * to cancelling and correcting money, so they are measured rather than assumed.
     */
    @Test
    fun `every verb clears the touch floor`() {
        compose.setContent {
            theme {
                Block(
                    state = history(rows = listOf(row("p1", isEditable = true))),
                )
            }
        }

        compose.onNodeWithTag(depositHistoryEditTag("p1")).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(depositHistoryCancelTag("p1")).assertHeightIsAtLeast(48.dp)
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Block(
        state: DepositHistoryUi,
        onRequestCancel: (String, String, String) -> Unit = { _, _, _ -> },
        onRequestEdit: (String) -> Unit = {},
    ) {
        DepositHistoryBlock(
            state = state,
            onRequestCancel = onRequestCancel,
            onRequestEdit = onRequestEdit,
            onErrorDismiss = {},
        )
    }

    /** A history over two deposits: one live, one cancelled — the shape most rows take. */
    private fun history(rows: List<DepositHistoryRowUi> = listOf(row("p1"), row("p2"))) =
        DepositHistoryUi(
            contractId = CONTRACT_ID,
            contractLabel = "Budi Santoso · B 1235 TF",
            renterName = "Budi Santoso",
            rows = rows,
            errorMessage = null,
        )

    /**
     * One row, already formatted — the ViewModel builds these, so a test that
     * hand-assembled the strings would be testing a second formatter.
     */
    private fun row(
        paymentId: String,
        isEditable: Boolean = true,
        cancelReason: String? = null,
    ) = DepositHistoryRowUi(
        paymentId = paymentId,
        amountText = "Rp 250.000",
        dateText = "5 Okt",
        methodText = "Tunai",
        accountText = null,
        referenceNo = null,
        notes = null,
        isEditable = isEditable,
        cancelReasonText = cancelReason,
    )

    private companion object {
        const val CONTRACT_ID = "contract-1"
    }
}
