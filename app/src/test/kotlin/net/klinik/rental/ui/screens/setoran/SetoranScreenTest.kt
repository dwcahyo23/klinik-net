package net.klinik.rental.ui.screens.setoran

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.screens.kontrak.depositHistoryCancelTag
import net.klinik.rental.ui.screens.kontrak.depositHistoryEditTag
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Setoran destination, rendered.
 *
 * ## What only this file can catch
 *
 * `DepositHistoryTest` proves the cross-contract **read**; `AppShellTest` proves the FAB is
 * absent on this route. Neither can catch the three claims only the screen makes:
 *
 * - it opens on **Input**, and the tab switch is real;
 * - the Input tab's one control reaches the handler that opens the sheet;
 * - a row from a **different** contract than its neighbours still carries its own renter,
 *   and its verbs name **that** renter.
 *
 * The last one is the whole reason `DepositHistoryRowUi` grew three fields. A cross-contract
 * list whose cancel verb read block-level state would open Story 3.4's dialog — which names a
 * person and an arrears figure — about Budi when the owner tapped Siti's deposit. A render
 * test that only counted rows would pass over exactly that.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SetoranScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /** `SETORAN_TABS` — two chips, and the screen opens on Input. */
    @Test
    fun `the screen opens on the Input tab with both tabs present`() {
        compose.setContent { theme { Screen() } }

        compose.onNodeWithText("Input Setoran").assertExists()
        compose.onNodeWithText("Riwayat Transaksi").assertExists()
        compose.onNodeWithTag(TAG_SETORAN_INPUT_TAB).assertExists()
        // And the *other* tab is not drawn, so the selection is a fact and not a default.
        compose.onNodeWithTag(TAG_SETORAN_HISTORY).assertDoesNotExist()
    }

    /** `SETORAN_INPUT` — the tab's one control reaches the sheet's opener. */
    @Test
    fun `the Input tab's action reaches the deposit sheet opener`() {
        var opened = 0
        compose.setContent {
            theme {
                Screen(
                    onOpenDepositInput = { opened = opened + 1 },
                )
            }
        }

        compose.onNodeWithTag(TAG_SETORAN_INPUT_ACTION).performClick()
        compose.waitForIdle()

        assertEquals("the existing sheet, not a second form", 1, opened)
    }

    /** Tapping the other chip moves the selection — the tab row is a control, not a label. */
    @Test
    fun `selecting the Riwayat chip switches the tab`() {
        var selected: SetoranTab? = null
        compose.setContent {
            theme {
                Screen(
                    onSelectTab = { selected = it },
                )
            }
        }

        compose.onNodeWithTag(setoranTabTag(SetoranTab.Riwayat)).performClick()
        compose.waitForIdle()

        assertEquals(SetoranTab.Riwayat, selected)
    }

    /**
     * `SETORAN_CROSS` — deposits from **two** contracts appear, each naming its own renter.
     *
     * The label assertion is the acceptance criterion: *"each row naming its renter · plate"*.
     * Without it the list would be a column of rupiah figures and dates saying nothing about
     * whose money it was.
     */
    @Test
    fun `rows from two contracts each name their own renter`() {
        compose.setContent {
            theme {
                Screen(
                    state = historyState(
                        rows = listOf(
                            row("p3", contractId = "contract-2", renterName = "Siti Aminah"),
                            row("p2", contractId = "contract-1", renterName = "Budi Santoso"),
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithText("Siti Aminah · B 9999 ZZ").assertExists()
        compose.onNodeWithText("Budi Santoso · B 1235 TF").assertExists()
    }

    /**
     * The verb on a row names **that row's** contract and renter.
     *
     * This is the wrong-target failure, asserted directly: two rows from two contracts, the
     * second row tapped, and the handler must receive Siti's ids rather than the list's first
     * row. `onRequestCancel` reaches `requestCancel(contractId, renterName, paymentId)`, which
     * is what writes the dialog's *"Tunggakan Pak … akan kembali menjadi…"* line.
     */
    @Test
    fun `a row's cancel verb carries that row's contract and renter`() {
        var args: Triple<String, String, String>? = null
        compose.setContent {
            theme {
                Screen(
                    state = historyState(
                        rows = listOf(
                            row("p3", contractId = "contract-2", renterName = "Siti Aminah"),
                            row("p2", contractId = "contract-1", renterName = "Budi Santoso"),
                        ),
                    ),
                    onRequestDepositCancel = { contractId, renter, paymentId ->
                        args = Triple(contractId, renter, paymentId)
                    },
                )
            }
        }

        compose.onNodeWithTag(depositHistoryCancelTag("p2"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(
            "the tapped row's own contract and renter, not its neighbour's",
            Triple("contract-1", "Budi Santoso", "p2"),
            args,
        )
    }

    /** And the other verb carries its payment id, the same way. */
    @Test
    fun `a row's correct verb carries that row's payment id`() {
        var edited: String? = null
        compose.setContent {
            theme {
                Screen(
                    state = historyState(
                        rows = listOf(
                            row("p3", contractId = "contract-2", renterName = "Siti Aminah"),
                            row("p2", contractId = "contract-1", renterName = "Budi Santoso"),
                        ),
                    ),
                    onRequestDepositEdit = { edited = it },
                )
            }
        }

        compose.onNodeWithTag(depositHistoryEditTag("p3"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("p3", edited)
    }

    /**
     * `SETORAN_CANCELLED` — a cancelled deposit is listed, marked, keeps its reason, and
     * offers **neither** verb.
     *
     * Absence rather than disabled: `cancelDeposit` and `correctDeposit` both refuse a
     * cancelled deposit, so a control that rendered there would accept a tap and do nothing.
     */
    @Test
    fun `a cancelled row is listed with its reason and offers no verbs`() {
        compose.setContent {
            theme {
                Screen(
                    state = historyState(
                        rows = listOf(
                            row("p1", isEditable = false, cancelReason = "salah tanggal"),
                            row("p2"),
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithText("Alasan pembatalan: salah tanggal").assertExists()
        compose.onNodeWithTag(depositHistoryCancelTag("p1")).assertDoesNotExist()
        compose.onNodeWithTag(depositHistoryEditTag("p1")).assertDoesNotExist()
        // The live row still carries both, so the absence above is about the cancellation.
        compose.onNodeWithTag(depositHistoryCancelTag("p2")).assertExists()
    }

    /** `SETORAN_SUMMARY` — the four figures come from the same emission as the rows. */
    @Test
    fun `the summary counts the rows it lists, cancelled included`() {
        compose.setContent {
            theme {
                Screen(
                    state = historyState(
                        rows = listOf(
                            row("p1"),
                            row("p2"),
                            row("p3", isEditable = false),
                        ),
                        summary = DepositSummaryUi(
                            total = 3,
                            active = 2,
                            cancelled = 1,
                            receivedTodayText = "Rp 500.000",
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_SETORAN_SUMMARY).assertExists()
        compose.onNodeWithText("Total transaksi").assertExists()
        compose.onNodeWithText("Dibatalkan").assertExists()
        compose.onNodeWithText("Rp 500.000").assertExists()
    }

    /**
     * A **failed** read is a banner, never an empty list.
     *
     * "No deposits anywhere" is a claim about money. A read that failed has not looked, and
     * drawing the empty state beside the banner would say the two contradictory things at
     * once — then apologise for one of them.
     */
    @Test
    fun `a failed read shows the banner and not the empty state`() {
        compose.setContent {
            theme {
                Screen(
                    state = SetoranScreenState(
                        selectedTab = SetoranTab.Riwayat,
                        rows = emptyList(),
                        summary = null,
                        errorMessage = "Gagal memuat riwayat setoran",
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_SETORAN_ERROR).assertExists()
        compose.onNodeWithTag(TAG_SETORAN_EMPTY).assertDoesNotExist()
    }

    /** `SETORAN_EMPTY` — no deposits anywhere is an invitation, not a blank area. */
    @Test
    fun `an empty history says what to do`() {
        compose.setContent {
            theme {
                Screen(
                    state = historyState(rows = emptyList(), summary = null),
                )
            }
        }

        compose.onNodeWithTag(TAG_SETORAN_EMPTY).assertExists()
        compose.onNodeWithText("Belum ada setoran").assertExists()
    }

    /** The summary is **absent** while nothing has been read — a "Rp 0" strip is a lie. */
    @Test
    fun `no summary is drawn before the read has happened`() {
        compose.setContent {
            theme {
                Screen(
                    state = historyState(
                        rows = emptyList(),
                        summary = null,
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_SETORAN_SUMMARY).assertDoesNotExist()
    }

    /** The empty state appears **once**, not once per row-block. */
    @Test
    fun `an empty history draws exactly one empty state`() {
        compose.setContent {
            theme {
                Screen(state = historyState(rows = emptyList(), summary = null))
            }
        }

        compose.onAllNodesWithTag(TAG_SETORAN_EMPTY).assertCountEquals(1)
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Screen(
        state: SetoranScreenState = SetoranScreenState(),
        onSelectTab: (SetoranTab) -> Unit = {},
        onOpenDepositInput: () -> Unit = {},
        onRequestDepositCancel: (String, String, String) -> Unit = { _, _, _ -> },
        onRequestDepositEdit: (String) -> Unit = {},
        onDismissError: () -> Unit = {},
    ) {
        SetoranScreen(
            state = state,
            onSelectTab = onSelectTab,
            onOpenDepositInput = onOpenDepositInput,
            onRequestDepositCancel = onRequestDepositCancel,
            onRequestDepositEdit = onRequestDepositEdit,
            onDismissError = onDismissError,
        )
    }

    /** A history tab holding rows — the state the Riwayat tab is drawn from. */
    private fun historyState(
        rows: List<DepositHistoryRowUi>,
        summary: DepositSummaryUi? = DepositSummaryUi(
            total = rows.size,
            active = rows.count { it.isEditable },
            cancelled = rows.count { !it.isEditable },
            receivedTodayText = "Rp 250.000",
        ),
    ) = SetoranScreenState(
        selectedTab = SetoranTab.Riwayat,
        rows = rows,
        summary = summary,
        errorMessage = null,
    )

    /**
     * One row, already formatted — the ViewModel builds these, so a test that assembled the
     * strings itself would be testing a second formatter.
     */
    private fun row(
        paymentId: String,
        contractId: String = "contract-1",
        renterName: String = "Budi Santoso",
        isEditable: Boolean = true,
        cancelReason: String? = null,
    ) = DepositHistoryRowUi(
        paymentId = paymentId,
        contractId = contractId,
        renterName = renterName,
        contractLabel = "$renterName · ${if (contractId == "contract-2") "B 9999 ZZ" else "B 1235 TF"}",
        amountText = "Rp 250.000",
        dateText = "5 Okt",
        methodText = "Tunai",
        accountText = null,
        referenceNo = null,
        notes = null,
        isEditable = isEditable,
        cancelReasonText = cancelReason,
    )
}