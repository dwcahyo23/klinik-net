package net.klinik.rental.ui.screens.kontrak

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.ui.format.formatTanggalBulan
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Robolectric render of the contract screen.
 *
 * ## What is asserted here and why it is not a review item
 *
 * Three things the AC names: the empty state says *what to do*, a populated row
 * names the renter and the plate, and no interactive node measures below 48dp.
 *
 * The third cannot be checked by reading the code. Every component here clears the
 * floor on its own, but a component that only clears it because its own modifier is
 * generous is one edit away from failing — and the failing node is often a glyph
 * that *looks* small, which is exactly what a reviewer skims past. So the nodes are
 * laid out and measured.
 *
 * The screen is stateless, so no database and no ViewModel is involved: the states
 * below are real `KontrakUiState` values, built by hand.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class KontrakScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------- empty state

    /**
     * The empty state says what to do — the title *and* the instruction, with a
     * `Buka Kontrak` action.
     *
     * A bare "no data" would satisfy "an empty state exists" and fail the AC, which
     * is why the body is asserted separately rather than as part of the title. The
     * AC names all three parts explicitly.
     *
     * `assertExists` rather than `assertIsDisplayed`: the action lays out below the
     * bottom edge of the Robolectric window, and a node past the bottom edge measures
     * zero — off-screen, not missing. The displayed claim would then be about the
     * harness rather than the screen.
     */
    @Test
    fun `the empty state says what to do`() {
        compose.setContent { theme { Screen(state = KontrakUiState()) } }

        compose.onNodeWithTag(TAG_EMPTY).assertExists()
        compose.onNodeWithText("Belum ada Kontrak").assertExists()
        compose.onNodeWithText(
            "Buka kontrak pertama untuk mulai mencatat sewa.",
        ).assertExists()
        // The action is what makes it an instruction rather than a statement. Both
        // it and the top bar draw `Buka Kontrak`, so it is addressed by its own tag —
        // the label alone matches two nodes.
        compose.onNodeWithTag(TAG_ADD).assertExists().assertHasClickAction()
        // The label is asserted on the action itself rather than screen-wide, because
        // the top bar carries the same words.
        compose.onAllNodes(hasText("Buka Kontrak")).assertCountEquals(2)
    }

    /**
     * An empty list must not look like an error. A bare "no data" or a red message
     * would satisfy "an empty state exists" and break the epic's rule that an error
     * state never looks like a new account.
     */
    @Test
    fun `an empty list renders no error copy`() {
        compose.setContent { theme { Screen(state = KontrakUiState()) } }

        assertTrue(
            "an empty list must not show a failure message",
            compose.onAllNodes(
                hasClickAction().not() and hasText("Gagal menyimpan. Coba lagi.", substring = true),
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * A refusal the form does not model *is* shown.
     *
     * `KendaraanUiState.errorMessage` exists but `KendaraanScreen` never renders it —
     * dead state that made a refused write invisible. This screen's is rendered, and
     * this pins the difference: a failed write that looks like a save that landed is
     * the outcome the epic context forbids, and it cannot happen if nothing is ever
     * drawn.
     */
    @Test
    fun `a screen-level refusal is rendered rather than swallowed`() {
        compose.setContent {
            theme {
                Screen(state = KontrakUiState(errorMessage = "Gagal menyimpan. Coba lagi."))
            }
        }

        compose.onNodeWithText("Gagal menyimpan. Coba lagi.").assertExists()
    }

    // ------------------------------------------------------------ populated list

    /**
     * The AC: a populated row names the renter *and* the plate.
     *
     * Both, because either alone does not identify a contract — the owner needs to
     * know who has which car.
     */
    @Test
    fun `a populated row names the renter and the plate`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onNodeWithTag("kontrak-row-c1").assertExists()
        compose.onNodeWithText("Budi Santoso").assertExists()
        // The date comes from the *real* formatter — see `populatedState`.
        compose.onNodeWithText("B 1235 TF · 1 Okt").assertExists()
    }

    /**
     * The row's date carries the **month**, so two rentals begun on the same day of
     * different months are distinguishable.
     *
     * This is the AC the weekday form could not meet: `1 Okt` and `1 Des` differ, and
     * a weekday format would render both as `1 <weekday>` and leave the owner unable
     * to tell which rental they are looking at. Two rows whose dates are a month apart
     * are asserted side by side, which is the shape the failure actually took.
     */
    @Test
    fun `a row's date carries the month`() {
        compose.setContent {
            theme {
                Screen(
                    state = KontrakUiState(
                        rows = listOf(
                            row(id = "c1", date = LocalDate.of(2026, 10, 1)),
                            row(id = "c2", date = LocalDate.of(2026, 12, 1)),
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithText("B 1235 TF · 1 Okt").assertExists()
        compose.onNodeWithText("B 1235 TF · 1 Des").assertExists()
    }

    /**
     * The rate and the schedule are on the row, so the owner can read the terms
     * without opening anything.
     *
     * The rate is the pre-rendered `formatRupiah` output, so this also pins that the
     * screen does not assemble money by hand — a second `"Rp " + value` here is how
     * two screens end up showing the same amount two ways.
     */
    @Test
    fun `a populated row shows the rate and the schedule`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onNodeWithText("Rp 250.000").assertExists()
        // A six-day week names its rest day…
        compose.onNodeWithText("Libur Min").assertExists()
        // …and a seven-day one says so, rather than showing a rest day it does not
        // have.
        compose.onAllNodes(hasText("7 hari")).assertCountEquals(1)
    }

    /**
     * Every row's spoken label names **both** the renter and the car.
     *
     * The label used to be the plate alone, which made TalkBack read the plate twice —
     * once as the label and once as the subtitle — and never read the renter at all.
     * Both are asserted, and the plate-alone form is asserted *absent*, so a
     * regression to the old wording fails rather than silently passing the first
     * assertion.
     */
    @Test
    fun `each row names its renter and its vehicle to a screen reader`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onAllNodes(
            hasContentDescription("Kontrak Budi Santoso dengan B 1235 TF"),
        ).assertCountEquals(1)
        compose.onAllNodes(
            hasContentDescription("Kontrak Siti Aminah dengan B 9876 AB"),
        ).assertCountEquals(1)
        // The old plate-only label must not still be in the tree: it read the plate
        // twice and the renter never.
        assertTrue(
            "the row label must not be the plate alone",
            compose.onAllNodes(hasContentDescription("Kontrak B 1235 TF"))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * The empty state's own action opens the form too — not only the top bar's.
     *
     * On an empty list this button is the *only* affordance, so `assertHasClickAction`
     * on it is the whole of what pass 2 proved about it, and that assertion is
     * satisfied by any lambda at all: `KnButton` hands `onClick` to a clickable
     * `Surface` regardless of the body's contents. Change `KontrakEmpty`'s action to
     * `{}` and every other assertion in this file stays green — the top bar opens the
     * form, the empty state's button does nothing, and the owner is told to press the
     * one control that is dead.
     */
    @Test
    fun `the empty state's action opens the form`() {
        var opened = false
        compose.setContent { theme { Screen(state = KontrakUiState(), onOpen = { opened = true }) } }

        compose.onNodeWithTag(TAG_ADD)
            .assertExists()
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("the empty state's action must invoke onOpen", opened)
    }

    /**
     * Dismissing the confirmation clears the confirmation, and dismissing the refusal
     * clears the refusal.
     *
     * ## Why the tone, not the state
     *
     * The banner's dismiss handler used to branch on `state.errorMessage != null`
     * while the *selection* above it suppressed that same value whenever the sheet was
     * open. So with the sheet up and a confirmation queued, the drawn banner was the
     * confirmation and the dismiss cleared the hidden error — leaving the visible
     * confirmation stuck on screen with nothing left to clear it. The handler now
     * tests the tone of the banner it is closing, which is the banner that was drawn.
     *
     * Both directions are asserted, so "always calls `onSnackbarDismiss`" fails as
     * loudly as the original bug.
     */
    @Test
    fun `dismissing the banner clears the message that was drawn`() {
        var snackbarCleared = false
        var errorCleared = false
        val state = KontrakUiState(
            rows = populatedState().rows,
            errorMessage = "Gagal menyimpan. Coba lagi.",
        )

        compose.setContent {
            theme {
                Screen(
                    state = state,
                    snackbarMessage = "Kontrak B 1235 TF tersimpan",
                    onSnackbarDismiss = { snackbarCleared = true },
                    onErrorDismiss = { errorCleared = true },
                )
            }
        }

        // The error wins when both are set — that is the pre-existing rule, and this
        // is the case the old handler got wrong once the sheet was involved.
        compose.onNodeWithText("Gagal menyimpan. Coba lagi.").assertExists()

        // The dismiss control is the clickable node nested inside the banner.
        // Addressed structurally rather than by a label, because
        // `KnSnackbarDismiss` renders a bare `Lucide.X` glyph with no content
        // description of its own. `useUnmergedTree` is required because the banner's
        // own semantics merge its descendants away.
        compose.onNode(
            hasClickAction() and hasAnyAncestor(hasTestTag(TAG_BANNER)),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("dismissing a refusal must clear the refusal", errorCleared)
        assertFalse(
            "dismissing a refusal must not clear the confirmation behind it",
            snackbarCleared,
        )
    }

    // --------------------------------------------------------------- the banner

    /**
     * The success snackbar renders on its own.
     *
     * The banner branch has two inputs and three reachable states, and a screen that
     * drew neither — or drew the wrong one — would satisfy every other assertion in
     * this file. This is the confirmation half.
     */
    @Test
    fun `a confirmation renders as a snackbar`() {
        compose.setContent {
            theme {
                Screen(
                    state = KontrakUiState(),
                    snackbarMessage = "Kontrak B 1235 TF tersimpan",
                )
            }
        }

        compose.onNodeWithText("Kontrak B 1235 TF tersimpan").assertExists()
    }

    /**
     * With both messages non-null the **error wins**.
     *
     * The two are not mutually exclusive upstream: a success followed by a failure
     * leaves both non-null, because neither clears the other. Drawing them
     * independently would stack two bars on the same pixels, and the owner would read
     * whichever the renderer put last. So the error is asserted present *and* the
     * confirmation asserted absent — a screen that drew both, or drew the wrong one,
     * fails here.
     */
    @Test
    fun `the error wins when both messages are set`() {
        compose.setContent {
            theme {
                Screen(
                    state = KontrakUiState(errorMessage = "Gagal menyimpan. Coba lagi."),
                    snackbarMessage = "Kontrak B 1235 TF tersimpan",
                )
            }
        }

        compose.onNodeWithText("Gagal menyimpan. Coba lagi.").assertExists()
        assertTrue(
            "the confirmation must not be drawn alongside the error",
            compose.onAllNodes(hasText("Kontrak B 1235 TF tersimpan"))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * And while the form is open the error is handed to the **sheet**, which draws it.
     *
     * A `ModalBottomSheet` is a separate window above the screen's, so a banner
     * composed here would be invisible behind it. The screen therefore suppresses its
     * own banner and passes the message down — and this asserts the message reaches
     * the sheet's error slot, which is where it is actually visible. `Robolectric`
     * sees both windows in one tree, so "the text exists" would pass either way; the
     * sheet's own tag is what distinguishes the two.
     */
    @Test
    fun `an open sheet draws the error rather than the screen beneath it`() {
        compose.setContent {
            theme {
                Screen(
                    state = KontrakUiState(
                        form = ContractFormState(),
                        errorMessage = "Gagal menyimpan. Coba lagi.",
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_SHEET_ERROR).assertExists()
        compose.onNodeWithText("Gagal menyimpan. Coba lagi.").assertExists()
    }

    /**
     * A row carries no click action.
     *
     * Story 2.7 owns ending a contract and Story 2.6 owns editing its exceptions, and
     * neither exists yet. A tappable row that opened nothing would be a gesture-only
     * path to an action the app cannot perform, which is the opposite of the epic
     * context's "a gesture is never the only path to an important action" — a tap that
     * does nothing is worse than no tap at all.
     */
    @Test
    fun `a row is not clickable until a story gives it something to do`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        assertTrue(
            "a row that opens nothing must not look tappable",
            compose.onAllNodes(
                hasTestTag("kontrak-row-c1") and hasClickAction(),
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    // ------------------------------------------------------------- the sheet mounts

    /**
     * The screen mounts the form sheet when its state says the form is open.
     *
     * ## Why this is the assertion the story most needed
     *
     * The sheet is the only path to this story's verb. A screen that took `onOpen`
     * and rendered no sheet would ship a story whose primary action no user could
     * reach — and every other assertion in this file, plus every pure-function test
     * in `ContractFormTest`, would still pass. So the sheet's presence is asserted
     * directly, by a tag only the sheet declares.
     */
    @Test
    fun `the form sheet is mounted when the form is open`() {
        compose.setContent {
            theme { Screen(state = KontrakUiState(form = ContractFormState())) }
        }

        compose.onNodeWithTag(TAG_SHEET).assertExists()
    }

    /**
     * And it is *not* mounted when the form is closed — a sheet that never unmounts
     * would cover the list it was opened over.
     */
    @Test
    fun `the form sheet is absent when the form is closed`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        assertTrue(
            "a closed form must leave no sheet over the list",
            compose.onAllNodes(hasTestTag(TAG_SHEET)).fetchSemanticsNodes().isEmpty(),
        )
    }

    // -------------------------------------------------------- touch target floor

    /**
     * The 48dp floor, measured across every interactive node on the screen.
     *
     * Walked by query rather than by a hard-coded list of tags, so a control added
     * later without a tag is still covered — and a new 32dp button fails the build
     * whether or not anyone remembered to tag it.
     */
    @Test
    fun `every interactive node clears the touch floor`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        val interactive = compose.onAllNodes(hasClickAction())
        val nodes = interactive.fetchSemanticsNodes()

        // A guard against a vacuous pass: if the walk ever matches nothing, a broken
        // screen would look like a screen full of compliant controls.
        assertTrue("expected interactive nodes, found none", nodes.isNotEmpty())

        nodes.indices.forEach { i ->
            interactive[i].assertAtLeast(MIN_TARGET, "kontrak control #$i")
        }
    }

    /**
     * The empty state's action clears the floor, measured on **the node the screen
     * actually draws**.
     *
     * ## Why this replaces a standalone button
     *
     * The previous version of this test composed its own `KnButton` carrying
     * `TAG_ADD` and measured *that*. It measured a different instance from the one
     * on screen, so the claim it appeared to make — "the empty contract screen's
     * action clears the floor" — was not the claim it actually proved. Pass a
     * smaller `KnButtonSize` to `KontrakEmpty`'s action, or constrain it in a box,
     * and the old test stayed green while the screen shipped a sub-48dp control.
     *
     * Inside the full screen the `KnEmptyState` lays out below the Robolectric
     * window's bottom edge — the empty state is a tall block, and a node laid out
     * past the bottom edge measures zero, which would make this test pass or fail
     * for a reason that has nothing to do with touch targets.
     *
     * So this one test runs with a **taller viewport** (`qualifiers` below) rather
     * than scrolling: the screen's inner column fills its parent and clips, so a
     * scroll host outside it cannot bring the button into view, and shrinking the
     * box just moves the clipping. Giving the screen a window tall enough for its
     * own content is the only way to measure the node it really draws.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the empty state's action clears the touch floor`() {
        compose.setContent { theme { Screen(state = KontrakUiState()) } }

        val action = compose.onNodeWithTag(TAG_ADD)
        action.assertExists().assertHasClickAction()
        // And it is *inside* the screen's empty state, not a node the test planted.
        compose.onNodeWithTag(TAG_EMPTY).assertExists()
        action.assertAtLeast(MIN_TARGET, "empty-state action")
    }

    /**
     * The top-bar `Buka Kontrak` action is clickable and invokes `onOpen`.
     *
     * The empty state's half of "the owner can reach the form" is covered above, and
     * the sheet's mount is covered by `the form sheet is mounted when the form is
     * open` — but between them sits a gap: nothing ever *clicked* the top-bar
     * action, so `onClick = onOpen` was unverified on a populated screen, where the
     * empty state's button is not there to be mistaken for it.
     */
    @Test
    fun `the top bar opens the form from a populated list`() {
        var opened = false
        compose.setContent {
            theme { Screen(state = populatedState(), onOpen = { opened = true }) }
        }

        compose.onNodeWithTag(TAG_ADD_ACTION)
            .assertExists()
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("the top-bar action must invoke onOpen", opened)
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Wraps in the app theme — a screen composed outside it silently gets the light
     * defaults (`staticCompositionLocalOf`, Story 2.1's noted gotcha).
     */
    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * Routes the state through the screen with no-op actions.
     *
     * `snackbarMessage` is a parameter rather than a fixed `null` so the banner
     * branch's confirmation half can be rendered — see the banner tests above.
     */
    @Composable
    private fun Screen(
        state: KontrakUiState,
        snackbarMessage: String? = null,
        onOpen: () -> Unit = {},
        onCloseForm: () -> Unit = {},
        onFormChange: ((ContractFormState) -> ContractFormState) -> Unit = {},
        onSave: () -> Unit = {},
        onSnackbarDismiss: () -> Unit = {},
        onErrorDismiss: () -> Unit = {},
    ) {
        KontrakScreen(
            state = state,
            onOpen = onOpen,
            // Required since review pass 2, so these three are passed explicitly —
            // the point of making them required is that a test cannot accidentally
            // omit them and get a sheet that silently does nothing.
            onCloseForm = onCloseForm,
            onFormChange = onFormChange,
            onSave = onSave,
            snackbarMessage = snackbarMessage,
            // Both supplied explicitly, and deliberately not aliased to one another:
            // the aliasing is what let a refusal be cleared through the
            // confirmation's setter.
            onSnackbarDismiss = onSnackbarDismiss,
            onErrorDismiss = onErrorDismiss,
        )
    }

    /** One six-day contract and one seven-day contract, so both badges are covered. */
    private fun populatedState() = KontrakUiState(
        rows = listOf(
            ContractRow(
                contract = Contract(
                    id = "c1",
                    renterId = "r1",
                    vehicleId = "v1",
                    startDate = LocalDate.of(2026, 10, 1),
                    dailyRate = 250_000L,
                    restDay = DayOfWeek.SUNDAY,
                ),
                renterName = "Budi Santoso",
                vehiclePlate = "B 1235 TF",
                rateText = "Rp 250.000",
                dateText = formatTanggalBulan(LocalDate.of(2026, 10, 1)),
            ),
            ContractRow(
                contract = Contract(
                    id = "c2",
                    renterId = "r2",
                    vehicleId = "v2",
                    startDate = LocalDate.of(2026, 10, 2),
                    dailyRate = 275_000L,
                    restDay = null,
                ),
                renterName = "Siti Aminah",
                vehiclePlate = "B 9876 AB",
                rateText = "Rp 275.000",
                dateText = formatTanggalBulan(LocalDate.of(2026, 10, 2)),
            ),
        ),
    )

    /**
     * One row built through the **real** date formatter.
     *
     * `dateText = formatTanggalBulan(date)` rather than a hand-written string, because
     * a literal here is a second, unchecked spelling of the format the screen renders:
     * the fixture would keep describing the old format after a change and the test
     * would keep passing against a screen that no longer renders it.
     */
    private fun row(id: String, date: LocalDate) = ContractRow(
        contract = Contract(
            id = id,
            renterId = "r1",
            vehicleId = "v1",
            startDate = date,
            dailyRate = 250_000L,
            restDay = null,
        ),
        renterName = "Budi Santoso",
        vehiclePlate = "B 1235 TF",
        rateText = "Rp 250.000",
        dateText = formatTanggalBulan(date),
    )

    /**
     * Both axes, in one helper.
     *
     * The AC says "no interactive node measures below 48dp", and the failure mode
     * that matters is the one nobody looks at: a control that is wide enough and
     * short, or short and wide. Asserting one axis would let that through.
     */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertAtLeast(
        target: Dp,
        what: String,
    ) {
        try {
            assertWidthIsAtLeast(target)
            assertHeightIsAtLeast(target)
        } catch (e: AssertionError) {
            throw AssertionError("$what failed the 48dp floor: ${e.message}", e)
        }
    }

    private companion object {
        val MIN_TARGET: Dp = 48.dp
        const val TAG_SHEET = "contract-form-sheet"
        const val TAG_SHEET_ERROR = "contract-sheet-error"
    }
}