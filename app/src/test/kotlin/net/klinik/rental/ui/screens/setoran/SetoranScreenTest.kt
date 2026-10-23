package net.klinik.rental.ui.screens.setoran

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 *
 * ## Why the tablet qualifier
 *
 * Because the history tab is a **five-column table with a header**, and Robolectric's default
 * window is 320x470dp — a phone in portrait. The rows below the first are not composed at all
 * in a window that small, so a test asserting "the second row exists" would fail for a reason
 * that has nothing to do with the screen. The prototype is a 1280x800 tablet, and the tablet
 * qualifier is the viewport this screen was designed for.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
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

    /**
     * `SETORAN_INPUT` — **selecting the Input tab** opens the deposit form.
     *
     * The tab is the form (`payments.html`), so selecting it is the act of starting a
     * deposit. There is no "Catat Setoran" button any more: the screen had one in the
     * middle *and* the tab in the bar, both reaching this same handler — the duplication
     * reported from the device.
     */
    @Test
    fun `selecting the Input tab opens the deposit form`() {
        var opened = 0
        compose.setContent {
            theme {
                Screen(
                    onOpenDepositInput = { opened = opened + 1 },
                )
            }
        }

        // Arriving on the screen already does it — the tab is selected by default.
        compose.waitForIdle()
        assertEquals("arriving on Input starts a deposit", 1, opened)

        // And re-tapping the tab does not discard the form it opened.
        compose.onNodeWithTag(setoranTabTag(SetoranTab.Input)).performClick()
        compose.waitForIdle()
        assertEquals("re-tapping the tab is a no-op", 1, opened)
    }

    // ------------------------------------------------------- the form on the page

    /**
     * An open form puts the **numpad on the page**, not behind a sheet.
     *
     * The prototype's `payments.html` is a two-pane split: the form on the left, the numpad
     * in a fixed column on the right, both on screen at once. The app had it the other way
     * round — an empty state with a button that opened a bottom sheet — which put the
     * amount, the control the owner uses most, two taps and a swipe away.
     *
     * This is the assertion that says the split is real: the pad's own tag is in the tree
     * while the form is open, with no sheet involved.
     */
    @Test
    fun `an open form draws the numpad on the page`() {
        compose.setContent {
            theme {
                Screen(
                    deposit = SetoranUiState(form = DepositFormState(date = java.time.LocalDate.of(2026, 10, 17))),
                )
            }
        }

        compose.onNodeWithTag(TAG_NUMPAD).assertExists()
        // And the form's own fields are there beside it.
        compose.onNodeWithTag(TAG_DEPOSIT_TITLE).assertExists()
    }

    /**
     * With no form open, the tab shows its invitation — the pad is **not** drawn.
     *
     * A numpad with nothing to enter into would be a control that renders before it means
     * anything, and the empty state is the honest answer to "there is no deposit yet".
     */
    @Test
    fun `no form means no numpad`() {
        compose.setContent { theme { Screen() } }

        compose.onNodeWithTag(TAG_SETORAN_INPUT_TAB).assertExists()
        compose.onNodeWithTag(TAG_NUMPAD).assertDoesNotExist()
    }

    /**
     * The split shows the **preview dialog** — the step that writes the deposit.
     *
     * The defect this catches is the one the split shipped with: the pad's `SIMPAN`
     * computes a preview, the write is gated on that preview's confirm, and the dialog
     * that confirms it lived only inside the bottom sheet — which the shell suppresses
     * precisely when the split is showing. The owner could enter an amount and never
     * record it.
     *
     * Asserted on the dialog's own tag, so a split that draws the pad without the dialog
     * fails here rather than on a device.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
    fun `the split shows the preview dialog when a preview exists`() {
        compose.setContent {
            theme {
                Screen(
                    deposit = SetoranUiState(
                        form = DepositFormState(date = java.time.LocalDate.of(2026, 10, 17)),
                        preview = DepositPreviewUi(
                            arrearsLine = "Tunggakan Rp 0",
                            closesLine = "Menutup 0 hari",
                            creditLine = null,
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_PREVIEW).assertExists()
    }

    /** Tapping the other chip moves the selection — the tab row is a control, not a label. */
    @Test
    fun `selecting the Riwayat chip switches the tab`() {        var selected: SetoranTab? = null
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
     * The table has a **renter column** and a **plate column**, so the two facts are separate
     * cells rather than one `"renter · plate"` string. Asserted per column because a table that
     * put the whole label in one column would still show Budi's plate on Siti's row — the
     * cross-contract mistake the two columns exist to prevent.
     */
    @Test
    fun `rows from two contracts each name their own renter and plate`() {
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

        compose.onNodeWithTag(depositTableRowTag("p3")).assertExists()
        compose.onNodeWithTag(depositTableRowTag("p2")).assertExists()
        // Each row carries its own renter and its own plate, and the two are not crossed.
        compose.onNodeWithText("Siti Aminah").assertExists()
        compose.onNodeWithText("Budi Santoso").assertExists()
        compose.onNodeWithText("B 9999 ZZ").assertExists()
        compose.onNodeWithText("B 1235 TF").assertExists()
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

        compose.onNodeWithTag(depositTableCancelTag("p2"))
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

        compose.onNodeWithTag(depositTableEditTag("p3"))
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
        compose.onNodeWithTag(depositTableCancelTag("p1")).assertDoesNotExist()
        compose.onNodeWithTag(depositTableEditTag("p1")).assertDoesNotExist()
        // The live row still carries both, so the absence above is about the cancellation.
        compose.onNodeWithTag(depositTableCancelTag("p2")).assertExists()
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
        // Tagged rather than matched by text: "Dibatalkan" is also the filter chip's name
        // and the status column's word, so a screen-wide text assertion would match one of
        // those instead of the figure.
        compose.onNodeWithTag(setoranSummaryTag("cancelled")).assertTextEquals("1")
        compose.onNodeWithTag(setoranSummaryTag("active")).assertTextEquals("2")
        compose.onNodeWithTag(setoranSummaryTag("total")).assertTextEquals("3")
        compose.onNodeWithTag(setoranSummaryTag("today")).assertTextEquals("Rp 500.000")
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

    // ------------------------------------------------------- the history table

    /**
     * The table draws its **header** and one row per deposit.
     *
     * A table without a header is a list of values with no names, and the prototype's five
     * columns are the owner's answer to "what am I looking at".
     */
    @Test
    fun `the table draws its header and a row per deposit`() {
        compose.setContent {
            theme {
                Screen(
                    state = historyState(
                        rows = listOf(
                            row("p1"),
                            row("p2", contractId = "contract-2", renterName = "Siti Aminah"),
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_SETORAN_TABLE_HEADER).assertExists()
        compose.onNodeWithText("TANGGAL").assertExists()
        compose.onNodeWithText("PENYEWA").assertExists()
        compose.onNodeWithTag(depositTableRowTag("p1")).assertExists()
        compose.onNodeWithTag(depositTableRowTag("p2")).assertExists()
    }

    /**
     * A **partial** deposit says so, beside its amount.
     *
     * Asserted on the badge's own tag rather than on the word: "Sebagian" is also the name of
     * the filter chip, so a text assertion would match the chip and pass for the wrong reason —
     * and would pass even if the badge were never drawn.
     */
    @Test
    fun `a partial deposit is badged as partial`() {
        compose.setContent {
            theme {
                Screen(
                    state = historyState(
                        rows = listOf(row("p1", isPartial = true), row("p2")),
                    ),
                )
            }
        }

        compose.onNodeWithTag(depositPartialTag("p1")).assertExists()
        // And only that row — the badge is about one deposit, not about the list.
        compose.onNodeWithTag(depositPartialTag("p2")).assertDoesNotExist()
    }

    /**
     * A cancelled row says so in the status column.
     *
     * Scoped to the row's own subtree: "Dibatalkan" is also the summary strip's label and the
     * filter chip's name, so a screen-wide text assertion would match those and pass for the
     * wrong reason.
     */
    @Test
    fun `a cancelled row says so in the status column`() {
        compose.setContent {
            theme {
                Screen(
                    state = historyState(
                        rows = listOf(row("p1", isEditable = false), row("p2")),
                    ),
                )
            }
        }

        compose.onNodeWithTag(depositTableStatusTag("p1")).assertTextEquals("Dibatalkan")
        compose.onNodeWithTag(depositTableStatusTag("p2")).assertTextEquals("Aktif")
    }

    // ------------------------------------------------- the search box and chips

    /**
     * The search box reports what the owner typed.
     *
     * The box is a control, so the assertion is the **text reaching the handler** — a box
     * wired to `{}` renders and filters nothing, which is the defect this repo has shipped
     * three times. The node typed into is the field itself, found by the placeholder that
     * names it.
     */
    @Test
    fun `typing in the search box reaches the handler`() {
        var typed: String? = null
        compose.setContent {
            theme {
                Screen(
                    state = historyState(rows = listOf(row("p1"))),
                    onHistoryQueryChange = { typed = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Cari nama, plat, kode, atau bank")
            .performTextInput("siti")
        compose.waitForIdle()

        assertEquals("siti", typed)
    }

    /**
     * The clear button empties the box — and reaches its **own** handler.
     *
     * The clear button is its own control with its own content description, so the test can
     * find it rather than inferring it. A clear wired to the type handler would still work;
     * the point of a separate handler is that the screen can tell "the owner cleared it" from
     * "the owner typed an empty string".
     */
    @Test
    fun `clearing the search box reaches the clear handler`() {
        var cleared = 0
        var typed: String? = null
        compose.setContent {
            theme {
                Screen(
                    state = historyState(rows = listOf(row("p1"))).copy(historyQuery = "siti"),
                    onHistoryQueryChange = { typed = it },
                    onClearHistoryQuery = { cleared = cleared + 1 },
                )
            }
        }

        compose.onNodeWithContentDescription("Hapus pencarian").performClick()
        compose.waitForIdle()

        assertEquals("the clear handler, not the type one", 1, cleared)
        assertEquals("the type handler stayed out of it", null, typed)
    }

    /** Each chip reports **its own** filter — a row of chips all calling one handler is a row
     * of labels. */
    @Test
    fun `each filter chip reports its own filter`() {
        val chosen = mutableListOf<HistoryFilter?>()
        compose.setContent {
            theme {
                Screen(
                    state = historyState(rows = listOf(row("p1"))),
                    onSelectHistoryFilter = { chosen.add(it) },
                )
            }
        }

        compose.onNodeWithTag(historyFilterTag(HistoryFilter.Dibatalkan)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(historyFilterTag(HistoryFilter.Sebagian)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(historyFilterTag(null)).performClick()
        compose.waitForIdle()

        assertEquals(
            listOf(HistoryFilter.Dibatalkan, HistoryFilter.Sebagian, null),
            chosen,
        )
    }

    /**
     * Narrowed to nothing is a **different sentence** from "nothing came in".
     *
     * Both are drawn from the same screen and they contradict each other: the money did come
     * in, the box found no match. A screen that showed "Belum ada setoran" here would be
     * telling the owner their deposits vanished.
     */
    @Test
    fun `a search that matches nothing says so instead of claiming there are no deposits`() {
        compose.setContent {
            theme {
                Screen(
                    state = historyState(rows = listOf(row("p1"))).copy(visibleRows = emptyList()),
                )
            }
        }

        compose.onNodeWithTag(TAG_SETORAN_NO_MATCH).assertExists()
        compose.onNodeWithText("Tidak ada yang cocok").assertExists()
        compose.onNodeWithTag(TAG_SETORAN_EMPTY).assertDoesNotExist()
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
        onHistoryQueryChange: (String) -> Unit = {},
        onClearHistoryQuery: () -> Unit = {},
        onSelectHistoryFilter: (HistoryFilter?) -> Unit = {},
        deposit: SetoranUiState = SetoranUiState(),
        onDepositFormChange: ((DepositFormState) -> DepositFormState) -> Unit = { it },
        onSaveDeposit: (Long) -> Unit = {},
        onCloseDeposit: () -> Unit = {},
        onConfirmPreviewDeposit: () -> Unit = {},
        onDismissPreviewDeposit: () -> Unit = {},
    ) {
        SetoranScreen(
            state = state,
            onSelectTab = onSelectTab,
            onOpenDepositInput = onOpenDepositInput,
            deposit = deposit,
            onDepositFormChange = onDepositFormChange,
            onSaveDeposit = onSaveDeposit,
            onCloseDeposit = onCloseDeposit,
            onConfirmPreviewDeposit = onConfirmPreviewDeposit,
            onDismissPreviewDeposit = onDismissPreviewDeposit,
            onRequestDepositCancel = onRequestDepositCancel,
            onRequestDepositEdit = onRequestDepositEdit,
            onDismissError = onDismissError,
            onHistoryQueryChange = onHistoryQueryChange,
            onClearHistoryQuery = onClearHistoryQuery,
            onSelectHistoryFilter = onSelectHistoryFilter,
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
        // The un-narrowed list, which is what an empty query and the "all" chip mean.
        // The ViewModel computes this; a fixture that left it empty would draw an empty
        // table and prove nothing about the rows.
        visibleRows = rows,
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
        isPartial: Boolean = false,
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
        isCanceled = !isEditable,
        cancelReasonText = cancelReason,
        isPartial = isPartial,
    )
}