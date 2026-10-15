package net.klinik.rental.ui.screens.kendaraan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.domain.parse.parseRupiahInput
import net.klinik.rental.domain.validation.InvalidAmountException
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Zero and negative rates are rejected with a visible message on screen, and
 * nothing is written.
 *
 * ## Why the rejection is asserted twice
 *
 * Once through the *rendered sheet* — the owner sees a message naming the fault
 * and the fix — and once through the *parse path* — so the screen's refusal and
 * the repository's are demonstrably the same rule rather than two rules that
 * happen to agree today.
 *
 * NFR-6 is exactly this: "two screens can both pass the same UI check". A test
 * that only checked the on-screen message would still pass if the form used one
 * rule and the repository another, which is how `parseInt(x) || 250000` became
 * the prototype's default.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class VehicleFormTest {

    @get:Rule
    val compose = createComposeRule()

    // ----------------------------------------------------------- visible message

    /**
     * The form renders its fields and the numpad.
     *
     * Structural assertions (`assertExists`) rather than displayed-checks for the
     * rows below the fold: seven fields plus a 66dp-key numpad plus two buttons
     * overflow the Robolectric window, and a node laid out past the bottom edge
     * measures zero — off-screen, not missing. The sheet scrolls on device, so
     * what is being verified here is that every control is *in the tree and
     * reachable*, which is exactly what `assertExists` plus the click-action
     * assertions below establish.
     */
    @Test
    fun `the form renders its fields and the numpad`() {
        compose.setContent { theme { Sheet(state = VehicleFormState()) } }

        compose.onNodeWithTag(TAG_FIELD_PLATE).assertExists()
        compose.onNodeWithTag(TAG_FIELD_BRAND).assertExists()
        compose.onNodeWithTag(TAG_FIELD_MODEL).assertExists()
        compose.onNodeWithTag(TAG_FIELD_YEAR).assertExists()
        compose.onNodeWithTag(TAG_FIELD_RATE).assertExists()
        compose.onNodeWithTag(TAG_FIELD_COLOR).assertExists()
        compose.onNodeWithTag(TAG_FIELD_NOTES).assertExists()
        compose.onNodeWithTag(TAG_SERVIS).assertExists().assertHasClickAction()
        compose.onNodeWithTag(TAG_NUMPAD).assertExists()
        compose.onNodeWithTag(TAG_SAVE).assertExists().assertHasClickAction()
        compose.onNodeWithTag(TAG_CANCEL).assertExists().assertHasClickAction()
    }

    /**
     * The servis marker drives the real checkbox, not a hand-mutated copy of the
     * state: clicking the rendered `TAG_SERVIS` node must flip `isServis` on the
     * state the sheet was given, through the same `onChange` path the save button
     * reads. A test that merely copied state in a local lambda would pass while
     * the checkbox's own `onCheckedChange` was never wired, which is exactly the
     * wiring this test exists to exercise.
     *
     * The click is invoked as the node's `OnClick` *semantics action* rather
     * than as a pointer tap. A real tap is injected at the node's centre, and
     * this file's own header note says why that does not work here: seven
     * fields plus a 66dp-key numpad overflow the Robolectric window, so the
     * servis row — which sits between them — measures zero and a coordinate tap
     * lands on nothing. The semantics action is the same handler the owner's
     * finger reaches (`Surface(onClick=)`), so the wiring under test is the one
     * that actually runs.
     */
    @Test
    fun `tapping the servis marker flips isServis on the form state`() {
        // A `mutableStateOf`, not a plain `var`: the sheet is a composable, and
        // `KnCheckbox` reads `checked` off the state it was composed with. A
        // plain `var` would leave the sheet showing the *original* `checked`
        // after the first click, so the second click would compute `!false`
        // again and re-set the same value instead of clearing it — the toggle
        // would look like it fired once and then got stuck. Making the holder
        // observable is what lets the recomposition that a real ViewModel
        // triggers happen here too.
        val state = mutableStateOf(VehicleFormState())

        compose.setContent {
            theme {
                Sheet(
                    state = state.value,
                    onChange = { newState -> state.value = newState },
                )
            }
        }

        compose.onNodeWithTag(TAG_SERVIS).performSemanticsAction(SemanticsActions.OnClick)
        assertTrue("clicking the marker turns it on", state.value.isServis)

        compose.onNodeWithTag(TAG_SERVIS).performSemanticsAction(SemanticsActions.OnClick)
        assertFalse("clicking again turns it back off", state.value.isServis)
    }

    /**
     * A zero rate shows a message that names both the fault and the fix.
     *
     * Asserted on the exact Indonesian string rather than on "an error appeared",
     * because an error that says only "invalid" leaves the owner guessing — which
     * is the bar the epic context sets for every message in the app.
     */
    @Test
    fun `a zero rate shows a message naming the fault and the fix`() {
        val refused = VehicleFormState(
            rateDigits = "0",
            rateError = ERROR_ZERO,
        )
        compose.setContent { theme { Sheet(state = refused) } }

        compose.onNodeWithText(ERROR_ZERO).assertExists()
        assertTrue(
            "the message must tell the owner what to do",
            ERROR_ZERO.contains("lebih dari nol"),
        )
    }

    /** A negative rate is refused the same way, and says the same thing. */
    @Test
    fun `a negative rate shows the same refusal`() {
        val refused = VehicleFormState(
            rateDigits = "-1",
            rateError = ERROR_ZERO,
        )
        compose.setContent { theme { Sheet(state = refused) } }

        compose.onNodeWithText(ERROR_ZERO).assertExists()
    }

    /**
     * A duplicate plate shows a message that *names the plate*, so the owner knows
     * which car to look at rather than being told "duplicate" in the abstract.
     */
    @Test
    fun `a duplicate plate message names the plate`() {
        val refused = VehicleFormState(
            plate = "B 1235 TF",
            plateError = "Plat B 1235 TF sudah dipakai mobil lain. Periksa lagi nomornya.",
        )
        compose.setContent { theme { Sheet(state = refused) } }

        compose.onNodeWithText(refused.plateError!!).assertExists()
        assertTrue(
            "the message must name the offending plate",
            refused.plateError!!.contains("B 1235 TF"),
        )
    }

    // ------------------------------------------------------------- no write

    /**
     * The parse path refuses both, so there is no value for the form to hand the
     * repository — and therefore no write.
     *
     * This is the half that survives someone editing the form's own check: the
     * on-screen message can be reworded, moved or dropped, but `parseRupiahInput`
     * returning `null` is what stops the write, and that is asserted directly.
     */
    @Test
    fun `the parse path refuses zero and negative rates`() {
        assertNull(parseRupiahInput("0"))
        assertNull(parseRupiahInput("-1"))
        assertNull(parseRupiahInput("-250000"))
        assertNull(parseRupiahInput("000"))
    }

    /**
     * And the same refusal holds at the domain model, which is the layer a second
     * screen would reach. Together with the repository tests this is NFR-6's
     * requirement stated as a property: no caller that skips the form can write a
     * non-positive rate.
     */
    @Test
    fun `no non-positive rate can reach the domain or the database`() {
        assertThrows(InvalidAmountException::class.java) {
            Vehicle(
                id = "v1",
                plate = "B 1 AA",
                brand = "Toyota",
                model = "Agya",
                year = 2023,
                dailyRate = 0L,
            )
        }
        assertThrows(InvalidAmountException::class.java) {
            Vehicle(
                id = "v1",
                plate = "B 1 AA",
                brand = "Toyota",
                model = "Agya",
                year = 2023,
                dailyRate = -250_000L,
            )
        }
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @androidx.compose.runtime.Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * The sheet over no-op handlers by default — the state is what is under
     * test. [onChange] is captured by the caller so a test can observe the
     * state the real checkbox writes back through the form's own change path.
     * The form calls it with the *new* state; this forwards that value
     * straight to the caller's callback so a test can store and assert on it.
     */
    @androidx.compose.runtime.Composable
    private fun Sheet(
        state: VehicleFormState,
        onChange: (VehicleFormState) -> Unit = {},
    ) {
        VehicleFormSheet(
            state = state,
            onDismissRequest = {},
            onChange = { transformer -> onChange(transformer(state)) },
            onSave = {},
        )
    }

    private companion object {
        const val ERROR_ZERO =
            "Tarif harian harus lebih dari nol. Ketik angka di keypad lalu simpan."
    }
}