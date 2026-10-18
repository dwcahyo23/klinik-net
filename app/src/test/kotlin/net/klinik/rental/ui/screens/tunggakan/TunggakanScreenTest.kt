package net.klinik.rental.ui.screens.tunggakan

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
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
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.domain.calculate.riskOf
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Story 4.1's screen: the rows, the badge tone, and the landscape detail pane.
 *
 * ## Why this file clicks rather than asserts existence
 *
 * Because `assertExists()` cannot tell a control wired to its handler from one wired to
 * nothing, and this repo has shipped that defect **three times** now — Story 2.5's Save,
 * Story 3.4's `requestCancel` and Story 3.5's `openEditDeposit` all reached production with
 * no caller. So the row's tap is asserted through `SemanticsActions.OnClick` and checked
 * against *which* row was selected, not merely that something clickable exists.
 *
 * ## The two rules every test here obeys
 *
 * - `assertExists()`, never `assertIsDisplayed()`, for anything below the Robolectric fold —
 *   a node that measures zero is indistinguishable from one that is absent. The empty state
 *   is the exception: it is a centred, unlaid-out card, so it *is* displayed.
 * - One `setContent` per test, with state held outside where a test mutates it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TunggakanScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // --------------------------------------------------------------- the row's facts

    /**
     * Every row shows **both** figures, in the AC's shape.
     *
     * Asserted on the composed string rather than on the two fields separately, because
     * "the amount is drawn and the day count is drawn" is exactly what a row showing one
     * without the other also satisfies.
     */
    @Test
    fun `each row shows the amount and the day count together`() {
        compose.setContent { theme { Screen(state = arrearsState()) } }

        compose.onNodeWithText("Rp 750.000 · 3 hari").assertExists()
        compose.onNodeWithText("Rp 250.000 · 1 hari").assertExists()
    }

    /**
     * And the badge carries the ladder's own **word** beside the colour.
     *
     * Rendered rather than colour-only because colour is never the only carrier of meaning
     * (UX-DR9): a badge the owner cannot read is a badge they cannot trust.
     */
    @Test
    fun `each row carries the risk ladder's word`() {
        compose.setContent { theme { Screen(state = arrearsState()) } }

        compose.onNodeWithTag(TAG_RISK_PREFIX + "c1", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Perhatian").assertExists()
    }

    /**
     * The badge tone is **derived** from the day count, not chosen beside it.
     *
     * Asserted through `TunggakanUiState.toneFor` at every ladder step rather than on a
     * rendered node, because a screen that picked a colour per row would still render
     * *something* — and the failure the criterion names is precisely a row whose number and
     * colour disagree, which no `assertExists` can see.
     */
    @Test
    fun `the badge tone is derived from the day count`() {
        assertEquals(KnBadgeTone.Success, TunggakanUiState.toneFor(riskOf(0)))
        assertEquals(KnBadgeTone.Warning, TunggakanUiState.toneFor(riskOf(2)))
        assertEquals(KnBadgeTone.Info, TunggakanUiState.toneFor(riskOf(3)))
        assertEquals(KnBadgeTone.Danger, TunggakanUiState.toneFor(riskOf(7)))
    }

    /** An ended contract keeps its marker, drawn beside the badge rather than replacing it. */
    @Test
    fun `an ended contract is marked without losing its risk badge`() {
        compose.setContent { theme { Screen(state = arrearsState()) } }

        // `useUnmergedTree` on both: the badges live in `KnListRow`'s trailing slot, and the
        // row merges its descendants — so in the merged tree they are one node with the
        // row's own tag, and a query without the flag cannot find either.
        compose.onNodeWithTag(TAG_ENDED_PREFIX + "c2", useUnmergedTree = true).assertExists()
        // Beside, not instead: the risk badge is still on that row.
        compose.onNodeWithTag(TAG_RISK_PREFIX + "c2", useUnmergedTree = true).assertExists()
    }

    // ------------------------------------------------------------------ LIST_EMPTY

    /** The empty state says what to do, rather than only that there is nothing. */
    @Test
    fun `an empty list explains itself instead of merely being empty`() {
        compose.setContent { theme { Screen(state = TunggakanUiState()) } }

        compose.onNodeWithText("Tidak ada tunggakan").assertIsDisplayed()
        compose.onNodeWithText(
            "Semua sewa sudah lunas. Kalau ada penyewa yang owe, tunggakannya muncul di sini setelah dicatat dari layar Kontrak.",
        ).assertIsDisplayed()
    }

    /**
     * Loading draws a skeleton, never the empty state.
     *
     * The assertion that matters is the **absence**: Room's first emission is an empty list,
     * so a screen branching on emptiness would show "Tidak ada tunggakan" — telling the
     * owner nobody owes them anything — before the read has looked at all.
     */
    @Test
    fun `loading never draws the empty state`() {
        compose.setContent { theme { Screen(state = TunggakanUiState(isLoading = true)) } }

        assertTrue(
            "the empty state must not stand in for a read that has not happened",
            compose.onAllNodes(hasText("Tidak ada tunggakan")).fetchSemanticsNodes().isEmpty(),
        )
    }

    // -------------------------------------------------------------- the row's wiring

    /**
     * Tapping a row reaches the handler, with **that** row's id.
     *
     * The click-through this file exists for. `assertHasClickAction()` would be satisfied by
     * a row wired to `{}`, which is the defect that shipped twice before this story.
     *
     * **Landscape, deliberately.** A portrait row is *not* a tap target — there is no pane
     * beside it to receive the selection — so this test running in Robolectric's default
     * portrait would fail for the right reason and prove nothing about the wiring. The
     * portrait half is [a portrait row is not a tap target], and the two are a pair.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp-land")
    fun `tapping a row selects that row`() {
        val selected = mutableListOf<String>()
        compose.setContent {
            theme { Screen(state = arrearsState(), onSelect = { selected += it }) }
        }

        compose.onNodeWithTag(TAG_ROW_PREFIX + "c2")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("and it is the row that was tapped", listOf("c2"), selected)
    }

    /**
     * In **landscape** — and only landscape — the row is a tap target.
     *
     * Half of a pair, and the half that would silently stop holding if the screen's
     * orientation branch were ever changed. The portrait half asserts the absence of the
     * click action; this one asserts its presence, so a screen that made every row
     * non-clickable would fail here rather than pass by making nothing happen.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp-land")
    fun `a landscape row is a tap target`() {
        compose.setContent { theme { Screen(state = arrearsState()) } }

        compose.onNodeWithTag(TAG_ROW_PREFIX + "c1")
            .assertHasClickAction()
    }

    /**
     * In **portrait** a row is **not** a tap target, because there is no pane beside it to
     * receive the selection.
     *
     * The other half of the pair above, and the one this story first shipped wrong: a row
     * wired to a handler whose selection nothing draws is the control-that-renders-and-does-
     * nothing defect `Screens.Kontrak`'s KDoc records as having reached production twice in
     * this repo. `KnListRow`'s nullable `onClick` is what makes "not a target" expressible.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-port")
    fun `a portrait row is not a tap target, because nothing would read the selection`() {
        compose.setContent { theme { Screen(state = arrearsState(selected = "c1")) } }

        assertTrue(
            "a portrait row must not be clickable — there is no pane to select into",
            compose.onAllNodes(hasTestTag(TAG_ROW_PREFIX + "c1") and hasClickAction())
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    // ------------------------------------------------- landscape master-detail (UX-DR13)

    /**
     * In landscape the list and the detail are **both** on screen.
     *
     * The criterion's whole claim: comparing two renters must not mean paging between
     * screens. Asserted on both nodes existing rather than on either alone, since a screen
     * drawing only the list satisfies "the list is visible".
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp-land")
    fun `landscape shows the list and the detail pane together`() {
        compose.setContent { theme { Screen(state = arrearsState(selected = "c1")) } }

        compose.onNodeWithTag(TAG_ROW_PREFIX + "c1").assertExists()
        compose.onNodeWithTag(TAG_DETAIL).assertExists()
        compose.onNodeWithTag(TAG_DETAIL_DIVIDER).assertExists()
    }

    /**
     * With no row selected the pane shows its **prompt** rather than an empty box.
     *
     * A blank pane beside a list reads as a rendering failure, not as "nothing selected".
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp-land")
    fun `the detail pane prompts when nothing is selected`() {
        compose.setContent { theme { Screen(state = arrearsState()) } }

        compose.onNodeWithTag(TAG_DETAIL_EMPTY).assertExists()
        assertTrue(
            "and no detail is drawn behind the prompt",
            compose.onAllNodes(hasTestTag(TAG_DETAIL)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /** The detail names the row's figures under their own labels, including the oldest day. */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp-land")
    fun `the detail pane names the amount the days and the oldest open day`() {
        compose.setContent { theme { Screen(state = arrearsState(selected = "c1")) } }

        compose.onNodeWithTag(TAG_DETAIL_AMOUNT).assertExists()
        compose.onNodeWithTag(TAG_DETAIL_DAYS).assertExists()
        compose.onNodeWithTag(TAG_DETAIL_OLDEST).assertExists()
        compose.onNodeWithText("14 Okt").assertExists()
    }

    /**
     * Portrait draws **no** pane at all, rather than one squeezed beside the list.
     *
     * The master-detail is a landscape affordance (UX-DR13); a 400dp pane on a phone would
     * ellipsise the figures, which are the whole point of the row.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-port")
    fun `portrait draws no detail pane`() {
        compose.setContent { theme { Screen(state = arrearsState(selected = "c1")) } }

        assertTrue(
            "portrait collapses to the list alone",
            compose.onAllNodes(hasTestTag(TAG_DETAIL)).fetchSemanticsNodes().isEmpty(),
        )
        compose.onNodeWithTag(TAG_ROW_PREFIX + "c1").assertExists()
    }

    // ------------------------------------------------------------- the touch floor

    /**
     * Every row clears the 48dp floor.
     *
     * The whole row is the control — there is no smaller button inside it, unlike
     * `KontrakRow` — so this is `KnListRow`'s own floor reaching the node a test addresses.
     */
    @Test
    fun `each row clears the touch target floor`() {
        compose.setContent { theme { Screen(state = arrearsState()) } }

        compose.onNodeWithTag(TAG_ROW_PREFIX + "c1").assertAtLeast(MIN_TARGET, "arrears row")
        compose.onNodeWithTag(TAG_ROW_PREFIX + "c2").assertAtLeast(MIN_TARGET, "arrears row")
    }

    /**
     * The reminder verb clears the same floor, and is measured rather than assumed.
     *
     * `KnButtonSize.Small` pads its hit area back out to 48dp through `minTouchTarget`, so this
     * *should* pass — and that is exactly why it is asserted. Story 4.1 shipped an identical
     * 38dp failure on this floor, and `AccessibilityTest`'s fixture has no rows, so nothing else
     * measures this button.
     */
    @Test
    fun `the reminder verb clears the touch target floor`() {
        compose.setContent { theme { Screen(state = arrearsState()) } }

        compose.onNodeWithTag(TAG_REMINDER_ACTION_PREFIX + "c1")
            .assertAtLeast(MIN_TARGET, "reminder verb")
        compose.onNodeWithTag(TAG_REMINDER_ACTION_PREFIX + "c2")
            .assertAtLeast(MIN_TARGET, "reminder verb")
    }

    // ------------------------------------------------------------------ the banner

    /**
     * A failed calculation draws a banner and **not** an empty list.
     *
     * The failure this screen can least afford: an empty arrears list reads as "everyone
     * paid", so a banner drawn over an empty list would be the wrong claim with a warning
     * attached to it. Keyed on `isFailed`, so a hand-built banner over a healthy list is a
     * *different* state and this does not accidentally cover it.
     */
    @Test
    fun `a failure draws a banner rather than an empty list`() {
        compose.setContent {
            theme {
                Screen(
                    state = TunggakanUiState(
                        isFailed = true,
                        errorMessage = "Gagal menghitung tunggakan. Coba lagi.",
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_ERROR).assertExists()
        assertTrue(
            "and never the empty state beside it",
            compose.onAllNodes(hasText("Tidak ada tunggakan")).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * And the banner's **dismiss** reaches the handler.
     *
     * The wiring test for a control the review flagged as defaulting to `{}`. A banner that
     * renders and cannot be closed is the same defect as one that never appears: the owner
     * is left reading a failure they cannot dismiss and no clear answer to what to do.
     */
    @Test
    fun `the banner's dismiss reaches its handler`() {
        var dismissed = false
        compose.setContent {
            theme {
                Screen(
                    state = TunggakanUiState(
                        isFailed = true,
                        errorMessage = "Gagal menghitung tunggakan. Coba lagi.",
                    ),
                    onErrorDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithTag(TAG_ERROR).assertExists()
        // The dismiss **glyph**, not the banner: `KnSnackbar`'s container is a `Surface`
        // with no click action of its own, and clicking it would fail with "the node is
        // missing [OnClick]" — which reads as a wiring defect but is just the wrong node.
        // Addressed by its `contentDescription`, because that is where `KnSnackbarDismiss`
        // puts the label — an icon's, not a `Text`'s.
        compose.onNodeWithContentDescription("Tutup")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("the dismiss glyph reached its handler", dismissed)
    }

    /**
     * The top bar states **no count** while loading, rather than "0 penyewa".
     *
     * "0 penyewa" on every load is the empty-looks-real failure on the one line that names
     * the screen's subject: it tells the owner nobody owes them anything before the app has
     * looked at anything.
     */
    @Test
    fun `the top bar claims no count while the read has not landed`() {
        compose.setContent { theme { Screen(state = TunggakanUiState(isLoading = true)) } }

        assertTrue(
            "\"0 penyewa\" must not stand in for a read that has not happened",
            compose.onAllNodes(hasText("0 penyewa")).fetchSemanticsNodes().isEmpty(),
        )
    }

    /** And it states the count once there is one to state. */
    @Test
    fun `the top bar names how many people are listed`() {
        compose.setContent { theme { Screen(state = arrearsState()) } }

        compose.onNodeWithText("2 penyewa").assertExists()
    }

    // -------------------------------------------------------------------- helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * The screen with its two required callbacks.
     *
     * Both are parameters because every test that does not care about a tap would otherwise
     * need the same no-op, and the tests that *do* care need their own — which is the whole
     * point of the file.
     */
    @Composable
    private fun Screen(
        state: TunggakanUiState,
        onSelect: (String) -> Unit = {},
        onErrorDismiss: () -> Unit = {},
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
            onSelect = onSelect,
            onErrorDismiss = onErrorDismiss,
            onRemind = onRemind,
            onReminderChange = onReminderChange,
            onReminderSend = onReminderSend,
            onReminderCopy = onReminderCopy,
            onReminderDismiss = onReminderDismiss,
            onReminderLinkOpened = onReminderLinkOpened,
            pendingLink = pendingLink,
        )
    }

    /**
     * Two owing contracts, the first selected.
     *
     * The figures are hand-built rather than produced by the ViewModel, because this file
     * is about **rendering** — what the owner reads. `TunggakanArrearsTest` covers where the
     * numbers come from, and a fixture that went through the repository would make a
     * formatting change look like a rendering change.
     */
    private fun arrearsState(selected: String? = null) = TunggakanUiState(
        rows = listOf(
            ArrearsRow(
                contractId = "c1",
                renterName = "Budi Santoso",
                vehiclePlate = "B 1235 TF",
                renterPhone = "0812-8899-7722",
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
                figureText = "Rp 250.000 · 1 hari",
                daysLabel = "1 hari",
                riskLabel = "Ringan",
                tone = TunggakanUiState.toneFor(RiskLevel.RINGAN),
                risk = RiskLevel.RINGAN,
                oldestDayText = "02 Nov",
                isEnded = true,
            ),
        ),
        selectedContractId = selected,
        isLoading = false,
    )

    /** Every interactive node clears the 48dp floor — the app's absolute minimum. */
    private fun SemanticsNodeInteraction.assertAtLeast(min: Dp, what: String) {
        assertWidthIsAtLeast(min)
        assertHeightIsAtLeast(min)
        assertTrue("$what must clear the ${min.value}dp floor", true)
    }

    private companion object {
        // val, not const val: Dp is an inline value class over a Float.
        val MIN_TARGET: Dp = 48.dp
    }
}
