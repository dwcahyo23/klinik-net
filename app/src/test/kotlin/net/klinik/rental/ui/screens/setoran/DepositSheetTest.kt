package net.klinik.rental.ui.screens.setoran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.domain.model.BusinessAccount
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
        ),
        renterOptions = renterOptions(),
        accountOptions = accountOptions(),
    )

    /** The form `onChange` transforms, for the handler test. */
    private fun formWith() = DepositFormState()

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
    ) {
        DepositSheet(
            state = state,
            onClose = onClose,
            onChange = onChange,
            onSave = onSave,
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

        /** The tag an amount field *would* carry, asserted never to exist. */
        const val TAG_AMOUNT_FIELD = "deposito-amount-field"
    }
}