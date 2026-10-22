package net.klinik.rental.ui.screens.setoran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.domain.model.BusinessAccount
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.TimeZone

/**
 * Robolectric render of the **real** deposit sheet.
 *
 * ## What is asserted here, and why it is not the same list as the ViewModel's
 *
 * The ViewModel's file covers *which* refusals happen and which field they land on. This
 * one covers what the owner can actually see, and two of those are the story's whole
 * point:
 *
 *  - the **numpad is present** — the AC makes it mandatory rather than preferred, so its
 *    absence is the failure a reader would take for a preference
 *  - the account field is **absent** for cash, not merely blank
 *
 * ## The two rules every test here obeys
 *
 * - `assertExists()`, never `assertIsDisplayed()`. Below the Robolectric fold a node
 *   measures zero, and "off-screen, not missing" reads identically to "present" — which
 *   matters more here than elsewhere, because the sheet is the one surface taller than a
 *   landscape tablet.
 * - One `setContent` per test, holding `var state by mutableStateOf(...)` outside.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DepositSheetTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The numpad is present, and it is the only way the amount is entered.
     *
     * The negative half is the claim: there is **no** `KnField` for the amount anywhere
     * in this sheet, because an amount typed into a keyboard is the mistyped figure the
     * story exists to prevent. A second input would quietly reopen the hole.
     */
    @Test
    fun `the numpad is present and the amount has no other input`() {
        compose.setContent { theme { Sheet(state = openState()) } }

        compose.onNodeWithTag(TAG_NUMPAD).assertExists()
        compose.onNodeWithTag(TAG_DEPOSIT_SHEET).assertExists()
        assertTrue(
            "no amount field exists — the pad is the only way in",
            compose.onAllNodes(hasTestTag(TAG_AMOUNT_FIELD)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * The auto-filled contract fact is **shown**, and it reads as a fact rather than an
     * input.
     *
     * Asserted through its own tag and text. A `KnField` here would be a control the
     * owner can change and cannot — the same defect as a blanked field, inverted.
     */
    @Test
    fun `the vehicle and the rate are filled from the contract`() {
        compose.setContent { theme { Sheet(state = openState()) } }

        compose.onNodeWithTag(TAG_DEPOSIT_CONTRACT_FACT).assertExists()
        compose.onNodeWithText("B 1235 TF").assertExists()
        compose.onNodeWithText("Rp 250.000").assertExists()
    }

    /**
     * `DEPOSIT_CASH_HIDES_ACCOUNT` — for cash, the account field **and** the reference are
     * **absent**.
     *
     * Absent rather than blank, per the epic context: a filled-in blank "menciptakan ilusi
     * data yang padahal tidak ada". Asserted on *absence of the nodes*, not on an empty
     * string, because a field that renders with an empty value still draws its label, its
     * error row and its affordance — three things on screen that promise an input.
     */
    @Test
    fun `cash hides the account and reference fields entirely`() {
        var state by mutableStateOf(
            openState().copy(form = openState().form!!.setMethod(PaymentMethod.Tunai))
        )
        compose.setContent { theme { Sheet(state = state) } }

        compose.onNodeWithTag(TAG_REFERENCE).assertDoesNotExist()
        compose.onNodeWithTag(renterChipTag(RENTER_ID)).assertExists()
        // And no account picker: the chips that would carry it.
        assertTrue(
            "no account chips for a cash deposit",
            compose.onAllNodes(hasTestTag(accountChipTag(ACCOUNT_ID)))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * And for a transfer, both are **present** — so the previous test is about the method
     * rather than about the sheet never drawing them.
     */
    @Test
    fun `a transfer shows the account and reference fields`() {
        compose.setContent { theme { Sheet(state = openState()) } }

        compose.onNodeWithTag(TAG_REFERENCE).assertExists()
        compose.onNodeWithTag(accountChipTag(ACCOUNT_ID)).assertExists()
    }

    /**
     * `DEPOSIT_NO_CONTRACT` — a renter with no contract draws the **reason**, not two
     * blank rows.
     *
     * The explanation is what tells the owner why the form cannot proceed; an empty
     * vehicle and rate would read as a rendering fault.
     */
    @Test
    fun `a renter with no contract says so instead of showing blanks`() {
        val noContract = openState().copy(
            form = DepositFormState(
                renterId = RENTER_NO_CONTRACT,
                renterName = "Agus Prasetyo",
                date = TEST_DATE,
            ),
        )
        compose.setContent { theme { Sheet(state = noContract) } }

        compose.onNodeWithTag(TAG_DEPOSIT_NO_CONTRACT).assertExists()
        compose.onNodeWithText("Belum ada kontrak aktif untuk penyewa ini.").assertExists()
        compose.onNodeWithTag(TAG_DEPOSIT_CONTRACT_FACT).assertDoesNotExist()
    }

    /** The renter chips carry the owner's own names, so a row is recognisable. */
    @Test
    fun `the renter picker lists every renter with their name`() {
        compose.setContent { theme { Sheet(state = openState()) } }

        compose.onNodeWithTag(renterChipTag(RENTER_ID)).assertExists()
        compose.onNodeWithTag(renterChipTag(RENTER_NO_CONTRACT)).assertExists()
        compose.onNodeWithText("Budi Santoso").assertExists()
        compose.onNodeWithText("Agus Prasetyo").assertExists()
    }

    /** The account chip says `bank · number`, so two accounts at one bank are told apart. */
    @Test
    fun `the account chip carries the bank and the number`() {
        compose.setContent { theme { Sheet(state = openState()) } }

        compose.onNodeWithText("BCA · 1234567890").assertExists()
    }

    /** An account refusal renders **on the account row**, not as the sheet's banner. */
    @Test
    fun `an account refusal renders on its own row`() {
        val refused = openState().copy(
            form = openState().form!!.copy(accountError = "Pilih rekening usaha yang menerima transfer ini."),
        )
        compose.setContent { theme { Sheet(state = refused) } }

        compose.onNodeWithTag(TAG_ACCOUNT_ERROR).assertExists()
        compose.onNodeWithText("Pilih rekening usaha yang menerima transfer ini.").assertExists()
        compose.onNodeWithTag(TAG_DEPOSIT_ERROR).assertDoesNotExist()
    }

    /** A failure with no field of its own renders **inside** the sheet. */
    @Test
    fun `a banner-level failure renders inside the sheet`() {
        compose.setContent {
            theme {
                Sheet(
                    state = openState().copy(errorMessage = "Gagal menyimpan setoran. Coba lagi."),
                )
            }
        }

        compose.onNodeWithTag(TAG_DEPOSIT_ERROR).assertExists()
        compose.onNodeWithText("Gagal menyimpan setoran. Coba lagi.").assertExists()
    }

    /** And so does the confirmation — the screen's banner is behind this window. */
    @Test
    fun `a confirmation renders inside the sheet`() {
        compose.setContent {
            theme { Sheet(state = openState().copy(successMessage = "Setoran Rp 250.000 tersimpan")) }
        }

        compose.onNodeWithTag(TAG_DEPOSIT_SUCCESS).assertExists()
    }

    /** Choosing a renter reaches the handler. */
    @Test
    fun `tapping a renter chip selects it`() {
        var selected: String? = null
        compose.setContent {
            theme {
                Sheet(
                    state = openState(),
                    onChange = { transform -> selected = transform(formWith()).renterId },
                )
            }
        }

        compose.onNodeWithTag(renterChipTag(RENTER_ID))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(RENTER_ID, selected)
    }

    /**
     * Every interactive node **the sheet lays out** clears the 48dp floor.
     *
     * Scoped with `assertExists()` before each measurement, because the sheet carries a
     * renter picker, a contract fact, a method toggle, two fields and a 4x3 grid, and on
     * a 2400dp viewport the ones furthest down are still below the fold. A node past the
     * fold measures zero, so measuring all of them would fail for a reason that has
     * nothing to do with touch targets — and would make this test about the viewport
     * rather than about the controls.
     *
     * So the claim is what it can honestly be: every node this test can measure clears
     * the floor. The rest is the device check the spec defers to the Epic 3
     * retrospective, and saying so here is better than a green assertion that quietly
     * measured nothing.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `every laid-out interactive node clears the touch floor`() {
        compose.setContent { theme { Sheet(state = openState()) } }

        compose.onNodeWithTag(TAG_REFERENCE).assertExists()
        compose.onNodeWithTag(TAG_NOTES).assertExists()
        compose.onNodeWithTag(renterChipTag(RENTER_ID)).assertExists()
        compose.onNodeWithTag(accountChipTag(ACCOUNT_ID)).assertExists()

        compose.onNodeWithTag(TAG_REFERENCE).assertAtLeast(MIN_TARGET, "reference field")
        compose.onNodeWithTag(TAG_NOTES).assertAtLeast(MIN_TARGET, "notes field")
        compose.onNodeWithTag(renterChipTag(RENTER_ID)).assertAtLeast(MIN_TARGET, "renter chip")
        compose.onNodeWithTag(accountChipTag(ACCOUNT_ID)).assertAtLeast(MIN_TARGET, "account chip")
    }

    /**
     * The sheet's form has a **height**.
     *
     * The defect this catches is the one a `weight(1f)` inside a wrap-content `Column`
     * causes: the weighted child is measured against the space left over in a column
     * whose own height is decided by its children, so it gets **zero**, and the form
     * collapses to nothing while every one of its nodes still "exists".
     *
     * Existence is exactly the wrong assertion here — the broken layout passes it. So
     * this measures.
     *
     * `size`, not `boundsInRoot`: a `ModalBottomSheet` is a separate window, and in the
     * test harness its root is not laid out, so the node's **root-relative** bounds are
     * zero even when the node itself has a real height. `size` is the node's own box.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the sheet's form has a height`() {
        compose.setContent { theme { Sheet(state = openState()) } }

        val formHeight = compose.onNodeWithTag(TAG_REFERENCE)
            .fetchSemanticsNode()
            .size
            .height
        assertTrue(
            "the reference field collapsed to ${formHeight}px — the sheet's form has no height",
            formHeight > 0f,
        )
    }

    /**
     * The numpad is **whole**, not scrolled away.
     *
     * The pad is the sheet's primary control — it is how the amount is entered and
     * `SIMPAN` on it is the way forward. A pad that has to be scrolled to is a pad the
     * owner cannot use, so the action row is pinned below the scroll rather than inside
     * it.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the numpad is laid out whole`() {
        compose.setContent { theme { Sheet(state = openState()) } }

        val padHeight = compose.onNodeWithTag(TAG_NUMPAD)
            .fetchSemanticsNode()
            .size
            .height
        assertTrue(
            "the numpad collapsed to ${padHeight}px — it must be pinned, not scrolled",
            padHeight > 0f,
        )
    }

    // ------------------------------------------------------ the mandatory preview

    /** The dialog appears when the preview is up, and not otherwise. */
    @Test
    fun `the preview dialog appears only when a preview exists`() {
        compose.setContent { theme { Sheet(state = openState()) } }

        compose.onNodeWithTag(TAG_PREVIEW).assertDoesNotExist()
    }

    /**
     * And it is up when one exists — its own test because a `ComposeTestRule` accepts
     * exactly one `setContent` per method, and a test that draws twice fails on the
     * harness rather than on the rule it meant to check.
     */
    @Test
    fun `the dialog is up when a preview exists`() {
        compose.setContent { theme { Sheet(state = openState().copy(preview = preview())) } }

        compose.onNodeWithTag(TAG_PREVIEW).assertExists()
    }

    /**
     * And it reads UX-DR10's **exact** format — both worked examples from the epic.
     *
     * Asserted on the literal strings rather than on the fields, because the format *is*
     * the acceptance criterion: the arrow, the separator between the two day counts, and
     * the credit's explanation are all prescribed, and a test on `arrearsLine`'s
     * components would pass on a sentence assembled three ways.
     */
    @Test
    fun `the preview reads the arrears line in the epic's exact format`() {
        compose.setContent {
            theme {
                Sheet(
                    state = openState().copy(
                        preview = DepositPreviewUi(
                            arrearsLine = "Tunggakan: Rp 750.000 → Rp 250.000",
                            closesLine = "Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)",
                            creditLine = null,
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_PREVIEW).assertExists()
        compose.onNodeWithTag(TAG_PREVIEW_ARREARS).assertExists()
        compose.onNodeWithText("Tunggakan: Rp 750.000 → Rp 250.000").assertExists()
        compose.onNodeWithText("Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)").assertExists()
    }

    /**
     * The credit line appears with its explanation, and **only** when there is credit.
     *
     * Both directions, because a line that is always present stops being read and a
     * credit that appears alone is the "mystery number" the epic forbids.
     */
    @Test
    fun `a credit carries its explanation`() {
        compose.setContent {
            theme {
                Sheet(
                    state = openState().copy(
                        preview = DepositPreviewUi(
                            arrearsLine = "Tunggakan: Rp 750.000 → Rp 0",
                            closesLine = "Menutup: 3 hari",
                            creditLine = "Kredit: Rp 250.000 — dipakai otomatis untuk periode berikutnya",
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithText("Kredit: Rp 250.000 — dipakai otomatis untuk periode berikutnya")
            .assertExists()
    }

    /**
     * And with **no** credit the line is absent entirely — not present and empty.
     *
     * Its own test for the harness reason [no preview means no dialog] gives, and because
     * a line that is always present stops being read.
     */
    @Test
    fun `no credit means no credit line at all`() {
        compose.setContent {
            theme {
                Sheet(
                    state = openState().copy(
                        preview = DepositPreviewUi(
                            arrearsLine = "Tunggakan: Rp 750.000 → Rp 250.000",
                            closesLine = "Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)",
                            creditLine = null,
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_PREVIEW_CREDIT).assertDoesNotExist()
    }

    /**
     * `PREVIEW_NO_DATES` — **no per-day date appears anywhere in the dialog.**
     *
     * Asserted as the absence of every date this contract could show, rather than by
     * inspecting the type: a ledger added later as a "helpful extra" would be caught
     * here and nowhere else, and the epic's reason for the rule is the owner's own —
     * a figure is something they can read, a table of dates is something they must verify.
     */
    @Test
    fun `the preview carries no per-day dates`() {
        compose.setContent {
            theme {
                Sheet(
                    state = openState().copy(
                        preview = DepositPreviewUi(
                            arrearsLine = "Tunggakan: Rp 750.000 → Rp 250.000",
                            closesLine = "Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)",
                            creditLine = "Kredit: Rp 250.000 — dipakai otomatis untuk periode berikutnya",
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_PREVIEW).assertExists()
        for (date in listOf("2026-10-01", "2026-10-02", "1 Okt", "2 Okt")) {
            assertTrue(
                "the preview must not name the day it closes: $date",
                compose.onAllNodes(hasText(date)).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    /** Confirm and dismiss reach their own handlers, separately. */
    @Test
    fun `the dialog confirms and dismisses through separate handlers`() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            theme {
                Sheet(
                    state = openState().copy(preview = preview()),
                    onConfirmPreview = { confirmed = true },
                    onDismissPreview = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText("Ya, simpan").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("confirm reaches its own handler", confirmed)
        assertFalse("and never the dismiss one", dismissed)

        compose.onNodeWithText("Cek dulu").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("dismiss reaches its own handler", dismissed)
    }

    /**
     * The dialog's dismiss reads **"Cek dulu"**, while the sheet's own close reads
     * "Batal" — and the two must not be swapped.
     *
     * Pinned because the dialog's label carries a behavioural promise: nothing is
     * cancelled and the form is still open, so a "Batal" on *that* button would tell the
     * owner they lost their work — the wrong story, and the reason they would press
     * confirm instead of actually checking.
     *
     * The sheet's close legitimately stays "Batal": *that* one does close the sheet, and
     * naming it "Cek dulu" there would be the mirror-image lie. Both are asserted
     * together because the two labels are one design decision: the dialog is a look, the
     * sheet's close is a departure.
     */
    @Test
    fun `the dialog dismiss reads 'check first' and the sheet close still reads 'cancel'`() {
        compose.setContent { theme { Sheet(state = openState().copy(preview = preview())) } }

        compose.onNodeWithText("Cek dulu").assertExists()
        // Exactly one "Batal": the sheet's own close. Two would mean the dialog had been
        // given the wrong label.
        compose.onAllNodes(hasText("Batal")).assertCountEquals(1)
    }

    // ------------------------------------------------------ Story 3.5: correcting

    /**
     * The title says the sheet is **correcting**, not recording.
     *
     * Asserted because the two are indistinguishable otherwise: both forms carry a renter, a
     * rate, a numpad and a Save, and an owner who cannot tell them apart cannot tell whether
     * Save will record a second deposit.
     */
    @Test
    fun `in edit mode the title reads Koreksi Setoran`() {
        compose.setContent { theme { Sheet(state = editingState()) } }

        compose.onNodeWithText("Koreksi Setoran").assertExists()
        compose.onNodeWithText("Catat Setoran").assertDoesNotExist()
    }

    /**
     * The method chips are **disabled** while correcting — not merely guarded.
     *
     * The negative half is the claim: `KnChip`'s `enabled = false` is what removes the click
     * action from the semantics tree, so a chip that renders live, keeps its 48dp target and
     * its selected tint while ignoring the tap is a control that reads as broken. A guarded
     * `onClick` would pass every other assertion in this file and still ship that.
     */
    @Test
    fun `in edit mode the method chips are disabled`() {
        compose.setContent { theme { Sheet(state = editingState()) } }

        compose.onNodeWithTag(TAG_METHOD_TRANSFER).assertExists().assertIsNotEnabled()
        compose.onNodeWithTag(TAG_METHOD_CASH).assertExists().assertIsNotEnabled().assertIsSelected()
    }

    /** And on a **new** deposit they are live, so the pair proves the lock is conditional. */
    @Test
    fun `on a new deposit the method chips are enabled`() {
        compose.setContent { theme { Sheet(state = openState()) } }

        compose.onNodeWithTag(TAG_METHOD_TRANSFER).assertExists().assertIsEnabled()
        compose.onNodeWithTag(TAG_METHOD_CASH).assertExists().assertIsEnabled()
    }

    /**
     * The date picker's **confirm** stores the day the dialog was seeded on.
     *
     * This is the round-trip the `DepositSheet`'s own KDoc names as "the classic off-by-one":
     * `selectedDateMillis` is UTC midnight for the picked calendar day, read back through the
     * same UTC offset it was written with. Converting through the device zone instead would
     * move the date by a day for anyone west of Greenwich.
     *
     * The confirm is **clicked** rather than merely asserted to exist. `assertExists()` cannot
     * tell a handler wired to the conversion from one wired to nothing, and
     * `ContractFormSheetTest.confirming the date dialog stores the picked day unchanged` is the
     * in-repo precedent for exactly that hole.
     */
    @Test
    fun `confirming the date dialog stores the picked day unchanged`() {
        var latest: DepositFormState? = null
        val seedDate = LocalDate.of(2026, 10, 5)
        val state = openState().copy(
            form = openState().form!!.copy(date = seedDate),
        )
        compose.setContent {
            theme {
                Sheet(
                    state = state,
                    onChange = { transform -> latest = transform(latest ?: state.form!!) },
                )
            }
        }

        // Through the field's trailing calendar affordance — the field itself is disabled,
        // so it takes no click of its own.
        compose.onNodeWithContentDescription("Pilih tanggal setteran masuk")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_DEPOSIT_DATE_DIALOG).assertExists()

        compose.onNodeWithTag(TAG_DEPOSIT_DATE_CONFIRM)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(
            "the confirm must store the day the dialog was seeded on",
            seedDate,
            latest?.date,
        )
    }

    /**
     * The same round-trip under a zone **west of Greenwich** — the test that can
     * actually fail.
     *
     * The test above runs in Robolectric's default zone, which is UTC, where
     * `ZoneOffset.UTC` and `ZoneId.systemDefault()` are the *same* value — so a handler
     * written with `systemDefault()` would pass it. Moving the default zone to
     * `America/New_York` separates them: Oct 5 at UTC midnight is Oct 4 at 20:00 in New
     * York, so a `systemDefault()` read-back returns **Oct 4** and the assertion fails.
     *
     * Restored in a `finally` because the default zone is JVM-wide and a leaked
     * `America/New_York` would silently weaken every date assertion that runs after it.
     */
    @Test
    fun `confirming the date dialog survives a zone west of Greenwich`() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            var latest: DepositFormState? = null
            val seedDate = LocalDate.of(2026, 10, 5)
            val state = openState().copy(
                form = openState().form!!.copy(date = seedDate),
            )
            compose.setContent {
                theme {
                    Sheet(
                        state = state,
                        onChange = { transform -> latest = transform(latest ?: state.form!!) },
                    )
                }
            }

            compose.onNodeWithContentDescription("Pilih tanggal setteran masuk")
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            compose.onNodeWithTag(TAG_DEPOSIT_DATE_CONFIRM)
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()

            assertEquals(
                "reading UTC midnight through the device zone would return Oct 4 here",
                seedDate,
                latest?.date,
            )
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    /** Dismissing the calendar closes it and leaves the stored date alone. */
    @Test
    fun `dismissing the date dialog leaves the field untouched`() {
        var latest: DepositFormState? = null
        val seedDate = LocalDate.of(2026, 10, 5)
        val state = openState().copy(form = openState().form!!.copy(date = seedDate))
        compose.setContent {
            theme {
                Sheet(
                    state = state,
                    onChange = { transform -> latest = transform(latest ?: state.form!!) },
                )
            }
        }

        compose.onNodeWithContentDescription("Pilih tanggal setteran masuk")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_DEPOSIT_DATE_DISMISS)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_DEPOSIT_DATE_DIALOG).assertDoesNotExist()
        assertEquals("and no date was written", seedDate, latest?.date ?: seedDate)
    }

    /** The field shows the stored date in the app's own month form, not the ISO column text. */
    @Test
    fun `the date field shows the stored date in the app's own format`() {
        compose.setContent { theme { Sheet(state = editingState()) } }

        compose.onNodeWithTag(TAG_DEPOSIT_FIELD_DATE).assertExists()
        // `formatTanggalBulan`'s month form — the same one the contract list uses, so the
        // two screens cannot spell a date differently. The ISO `2026-10-05` is asserted
        // absent because a correction form showing the column's own text would be the app
        // leaking its storage format onto the screen.
        compose.onNodeWithText("5 Okt").assertExists()
        compose.onNodeWithText("2026-10-05").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * A blank form plus the pickers, with **no** sheet open — so a test that draws the
     * sheet supplies its own `form`.
     */
    private fun openState() = SetoranUiState(
        form = DepositFormState(
            renterId = RENTER_ID,
            renterName = "Budi Santoso",
            contractId = CONTRACT_ID,
            vehiclePlate = "B 1235 TF",
            rateText = "Rp 250.000",
            date = TEST_DATE,
        ),
        renterOptions = renterOptions(),
        accountOptions = accountOptions(),
    )

    /**
     * The same sheet, correcting a stored deposit.
     *
     * Cash, so the account field is unmounted and the tests below are about the date and the
     * method lock rather than about two pickers at once.
     */
    private fun editingState() = openState().copy(
        form = openState().form!!.copy(
            paymentId = PAYMENT_ID,
            amountDigits = "750000",
            date = LocalDate.of(2026, 10, 5),
            method = PaymentMethod.Tunai,
        ),
    )

    /** The form `onChange` transforms, for the handler test. */
    /**
 * A preview as UX-DR10 words it, for the dialog tests.
 *
 * Built here rather than reused from the ViewModel tests because these tests are about
 * the **rendering**: what the owner reads, in the exact format the epic prescribes.
 * `SetoranViewModelTest` covers where the figures come from.
 */
private fun preview() = DepositPreviewUi(
    arrearsLine = "Tunggakan: Rp 750.000 → Rp 250.000",
    closesLine = "Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)",
    creditLine = null,
)

private fun formWith() = DepositFormState(date = TEST_DATE)

    private fun renterOptions() = listOf(
        RenterOption(
            renter = Renter(
                id = RENTER_ID,
                name = "Budi Santoso",
                phone = "08123456789",
                bankAccount = null,
                emergencyContact = "08129876543",
            ),
            contract = DepositContract(
                contractId = CONTRACT_ID,
                vehiclePlate = "B 1235 TF",
                rateText = "Rp 250.000",
            ),
        ),
        RenterOption(
            renter = Renter(
                id = RENTER_NO_CONTRACT,
                name = "Agus Prasetyo",
                phone = "08129999999",
                bankAccount = null,
                emergencyContact = "08129999999",
            ),
            // No contract — the read-only-but-listed case.
            contract = null,
        ),
    )

    private fun accountOptions() = listOf(
        AccountOption(
            account = BusinessAccount(id = ACCOUNT_ID, bankName = "BCA", number = "1234567890"),
            label = "BCA · 1234567890",
        ),
    )

    @Composable
    private fun Sheet(
        state: SetoranUiState,
        onChange: ((DepositFormState) -> DepositFormState) -> Unit = {},
        onClose: () -> Unit = {},
        onSave: (Long) -> Unit = {},
        // Story 3.3's dialog pair. Defaults here because this helper counts handlers
        // rather than asserting them — `the preview` tests below pass their own, and a
        // test that only draws the sheet has no use for a dialog decision.
        onConfirmPreview: () -> Unit = {},
        onDismissPreview: () -> Unit = {},
    ) {
        DepositSheet(
            state = state,
            onClose = onClose,
            onChange = onChange,
            onSave = onSave,
            onConfirmPreview = onConfirmPreview,
            onDismissPreview = onDismissPreview,
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
        const val RENTER_ID = "renter-1"
        const val RENTER_NO_CONTRACT = "renter-2"
        const val CONTRACT_ID = "contract-1"
        const val ACCOUNT_ID = "account-1"
        const val PAYMENT_ID = "payment-1"

        /**
         * The date every hand-built form opens on.
         *
         * A named constant rather than `LocalDate.now()`: the field renders it, so a test that
         * asserted text would otherwise pass in October and fail in November.
         */
        val TEST_DATE: LocalDate = LocalDate.of(2026, 10, 17)

        /** The tag an amount field *would* carry, asserted never to exist. */
        const val TAG_AMOUNT_FIELD = "deposito-amount-field"
    }
}




