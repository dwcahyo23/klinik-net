package net.klinik.rental.ui.screens.penyewa

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserPlus
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric render of the renter screen.
 *
 * ## What is asserted here and why it is not a review item
 *
 * Three things the AC names: the empty state says *what to do*, a populated list
 * shows the renter's name, and no interactive node measures below 48dp.
 *
 * The third is the one that cannot be checked by reading the code. Every
 * component here clears the floor on its own, but a component that only clears it
 * because its own modifier is generous is one edit away from failing — and the
 * failing node is often a glyph that *looks* small, which is exactly what a
 * reviewer skims past. So the nodes are laid out and measured.
 *
 * The screen is stateless, so no database and no ViewModel is involved: the
 * states below are real `PenyewaUiState` values, built by hand.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
class PenyewaScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------- empty state

    /**
     * The empty state says what to do — the title *and* the instruction, with a
     * `Tambah Penyewa` action. A bare "no data" would satisfy "an empty state
     * exists" and fail the AC, which is why the body is asserted separately
     * rather than as part of the title.
     *
     * `assertExists` rather than `assertIsDisplayed`: the action lays out below
     * the bottom edge of the Robolectric window, and a node past the bottom edge
     * measures zero — off-screen, not missing. The displayed claim would then be
     * about the harness rather than the screen. What is actually under test —
     * that the action exists, is clickable and clears the floor — is asserted.
     */
    @Test
    fun `the empty state says what to do`() {
        compose.setContent { theme { Screen(state = PenyewaUiState()) } }

        compose.onNodeWithTag(TAG_EMPTY_LIST).assertExists()
        compose.onNodeWithText("Belum ada Penyewa").assertExists()
        compose.onNodeWithText(
            "Tambahkan penyewa pertama untuk mulai mencatat sewa.",
        ).assertExists()
        // The action is what makes it an instruction rather than a statement.
        compose.onNodeWithTag(TAG_ADD).assertExists().assertHasClickAction()
        // And it carries the copy the AC names, not a bare "Tambah". Scoped to the **empty
        // state's** action: the top bar carries a "Tambah Penyewa" of its own
        // (`renters.html:33`), so an unscoped match finds two.
        compose.onNode(
            hasText("Tambah Penyewa") and hasAnyAncestor(hasTestTag(TAG_EMPTY_LIST)),
        ).assertExists()
    }

    /**
     * An empty list must not look like an error. A bare "no data" or a red
     * message would satisfy "an empty state exists" and break the epic's rule that
     * an error state never looks like a new account.
     */
    @Test
    fun `an empty list renders no error copy`() {
        compose.setContent { theme { Screen(state = PenyewaUiState()) } }

        // `getOrNull` rather than an exception: "no node carries this text" is the
        // expected outcome here, which `onNodeWithText` cannot express.
        val errorish = compose.onAllNodes(
            hasClickAction().not() and hasText("Gagal menyimpan. Coba lagi.", substring = true),
        )
        assertTrue(
            "an empty list must not show a failure message",
            errorish.fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * A refusal the form does not model *is* shown.
     *
     * `KendaraanUiState.errorMessage` exists but `KendaraanScreen` never renders
     * it — dead state that made a refused write invisible. This screen's is
     * rendered, and this is what pins the difference: a failed write that looks
     * like a save that landed is the outcome the epic context forbids, and it
     * cannot happen if nothing is ever drawn.
     */
    @Test
    fun `a screen-level refusal is rendered rather than swallowed`() {
        compose.setContent {
            theme {
                Screen(state = PenyewaUiState(errorMessage = "Gagal menyimpan. Coba lagi."))
            }
        }

        compose.onNodeWithText("Gagal menyimpan. Coba lagi.").assertExists()
    }

    // ------------------------------------------------------------ populated list

    /**
     * A populated list renders the renter's name.
     *
     * And the phone number, because a renter the owner can only see the name of
     * is not a renter they can act on — the number is the second thing they read.
     */
    @Test
    fun `a populated list renders the name and the phone number`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onNodeWithTag("penyewa-row-r1").assertExists()
        // `useUnmergedTree = true`: `KnListRow` merges its descendants, so the name is not its
        // own node in the merged tree.
        compose.onNode(
            hasText("Budi Santoso") and hasAnyAncestor(hasTestTag("penyewa-row-r1")),
            useUnmergedTree = true,
        ).assertExists()
        compose.onNode(
            hasText("08123456789 · Darurat 08129876543") and
                hasAnyAncestor(hasTestTag("penyewa-row-r1")),
            useUnmergedTree = true,
        ).assertExists()
    }

    /**
     * The account badge appears only for a renter who has one.
     *
     * Two renters, one with an account and one without, so the assertion is about
     * the *condition* rather than about the badge existing at all — a screen that
     * rendered `Rek -` for a cash-paying renter would claim data the database does
     * not hold.
     */
    @Test
    fun `the account badge appears only when there is an account`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        // Budi has one.
        compose.onAllNodes(
            insideRow("penyewa-row-r1") and hasText("Rek BCA 1234567890"),
        ).assertCountEquals(1)
        // Siti does not, so no badge is drawn on her row.
        compose.onAllNodes(
            insideRow("penyewa-row-r2") and hasText("Rek", substring = true),
        ).assertCountEquals(0)
    }

    /**
     * The detail pane's edit verb opens the **selected** renter's form.
     *
     * This replaces the old "each row names its own renter to a screen reader" assertion. The row
     * no longer carries an edit affordance at all: `renters.html:226` puts the verb in the detail
     * pane's header, and a row that both selected and edited could not be used to read a person
     * without opening a form over them.
     *
     * The claim is the same one that test made — one control, naming the person it acts on —
     * moved to where the control now is: tapping the pane's button must reach the handler with
     * **that** renter, not merely be tappable.
     */
    @Test
    fun `the detail pane's edit verb opens the selected renter`() {
        var edited: String? = null
        compose.setContent {
            theme {
                PenyewaScreen(
                    state = populatedState().copy(selectedRenterId = "r1"),
                    onQueryChange = {},
                    onClearQuery = {},
                    onAdd = {},
                    onSelect = {},
                    onEdit = { edited = it.name },
                    onErrorDismiss = {},
                )
            }
        }

        compose.onNodeWithTag(TAG_DETAIL_EDIT).performClick()

        assertEquals("the pane's verb must reach the renter it names", "Budi Santoso", edited)
    }

    // ---------------------------------------------------------- empty *search*

    /**
     * An empty *search* is not an empty list: it gets its own message naming the
     * query, so the owner can tell "nothing matched" from "nothing stored".
     *
     * The AC calls this out specifically, and it is the one that is easy to satisfy
     * by accident — a screen that shows the empty-list state for both would pass
     * "there is an empty state" and fail here.
     */
    @Test
    fun `an empty search result is not the empty list`() {
        compose.setContent {
            theme { Screen(state = populatedState().copy(query = "tidak ada")) }
        }

        compose.onNodeWithTag(TAG_EMPTY_SEARCH).assertExists()
        compose.onNodeWithText("Tidak ada yang cocok").assertExists()
        // The query is named, so the two emptinesses are tellable apart.
        compose.onNodeWithText(
            "Tidak ada penyewa untuk “tidak ada”. Coba kata lain atau hapus pencarian.",
        ).assertExists()
        // And the empty-list state is *not* shown, because renters do exist.
        assertTrue(
            "a failed search must not render the empty-list state",
            compose.onAllNodes(hasTestTag(TAG_EMPTY_LIST)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * Searching narrows the list in place — the same screen, not a second
     * destination. Asserted as "the non-matching row is gone", which is what
     * "filtering, never navigation" means observably.
     */
    @Test
    fun `searching narrows the list without a second screen`() {
        compose.setContent {
            theme { Screen(state = populatedState().copy(query = "Budi")) }
        }

        compose.onNodeWithTag("penyewa-row-r1").assertExists()
        assertTrue(
            "a filtered-out row must not remain in the list",
            compose.onAllNodes(hasTestTag("penyewa-row-r2")).fetchSemanticsNodes().isEmpty(),
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

        // A guard against a vacuous pass: if the walk ever matches nothing, a
        // broken screen would look like a screen full of compliant controls.
        assertTrue("expected interactive nodes, found none", nodes.isNotEmpty())
        assertTrue(
            "expected at least the search bar and 2 rows; found ${nodes.size}",
            nodes.size >= 3,
        )

        nodes.indices.forEach { i ->
            interactive[i].assertAtLeast(MIN_TARGET, "penyewa control #$i ${describe(interactive[i])}")
        }
    }

    /**
     * The empty state's action clears the floor, measured on the button in a
     * standalone render where it has visible bounds.
     *
     * Inside the full screen the `KnEmptyState`'s content lays out below the
     * Robolectric window's bottom edge — `fillMaxWidth` with `giant` vertical
     * padding pushes the button off-screen, and an off-screen node measures zero.
     * The button's size guarantee is the same `KnButton` the row test already
     * measures, so this confirms the *action slot* carries the button, not that
     * the button is a different component.
     */
    @Test
    fun `the empty state's action clears the touch floor`() {
        compose.setContent {
            theme {
                KnButton(
                    label = "Tambah Penyewa",
                    onClick = {},
                    icon = Lucide.UserPlus,
                    modifier = Modifier.testTag(TAG_ADD),
                )
            }
        }

        val action = compose.onNodeWithTag(TAG_ADD)
        action.assertExists().assertHasClickAction()
        action.assertAtLeast(MIN_TARGET, "empty-state action")
    }

    /**
     * A list row is 48dp on the *row*, not on the name text — a row that looks
     * tall enough because of its avatar is not a target the row's own bounds
     * guarantee.
     */
    @Test
    fun `a list row clears the touch floor`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onNodeWithTag("penyewa-row-r1").assertAtLeast(MIN_TARGET, "list row")
    }

    /** The search bar's clear button is the node most likely to be sized by its glyph. */
    @Test
    fun `the search bar and its clear button clear the touch floor`() {
        compose.setContent {
            theme { Screen(state = populatedState().copy(query = "Budi")) }
        }

        // The search input is matched by its set-text action rather than by a tag:
        // `KnSearchBar` has no tag of its own, and adding one to the shared
        // component for this story's benefit would put a screen-specific hook in
        // the design system.
        val input = compose.onAllNodes(hasSetTextAction())
        assertTrue("expected the search input", input.fetchSemanticsNodes().isNotEmpty())
        input.assertAllAtLeast("search input")

        // The clear button is the only remaining clickable that is not a row: it
        // appears whenever the query is non-empty.
        val clear = compose.onAllNodes(
            hasClickAction() and hasContentDescription("Hapus pencarian"),
        )
        val clearNodes = clear.fetchSemanticsNodes()
        assertTrue("expected the search clear button", clearNodes.isNotEmpty())
        clearNodes.indices.forEach { i ->
            clear[i].assertAtLeast(MIN_TARGET, "search clear button #$i")
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Wraps in the app theme — a screen composed outside it silently gets the
     * light defaults (`staticCompositionLocalOf`, Story 2.1's noted gotcha).
     */
    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /** Routes the state through the screen with no-op actions. */
    @Composable
    private fun Screen(state: PenyewaUiState) {
        PenyewaScreen(
            state = state,
            onQueryChange = {},
            onClearQuery = {},
            onAdd = {},
            onSelect = {},
            onEdit = {},
            // Required rather than defaulted: this screen's alias
            // `onErrorDismiss = onSnackbarDismiss` was wrong — the two dismiss
            // different messages.
            onErrorDismiss = {},
        )
    }

    /** One renter with an account, one without. */
    private fun populatedState() = PenyewaUiState(
        rows = listOf(
            RenterRow(
                Renter(
                    id = "r1",
                    name = "Budi Santoso",
                    phone = "08123456789",
                    bankAccount = "BCA 1234567890",
                    emergencyContact = "08129876543",
                ),
            ),
            RenterRow(
                Renter(
                    id = "r2",
                    name = "Siti Aminah",
                    phone = "08129876543",
                    bankAccount = null,
                    emergencyContact = "08123456789",
                ),
            ),
        ),
    )

    /**
     * Both axes, in one helper.
     *
     * The AC says "no interactive node measures below 48dp", and the failure mode
     * that matters is the one nobody looks at: a control that is wide enough and
     * short, or short and wide. Asserting one axis would let that through.
     */
    private fun SemanticsNodeInteraction.assertAtLeast(target: Dp, what: String) {
        try {
            assertWidthIsAtLeast(target)
            assertHeightIsAtLeast(target)
        } catch (e: AssertionError) {
            throw AssertionError("$what failed the 48dp floor: ${e.message}", e)
        }
    }

    /** Every node in the collection, each labelled so a failure names its index. */
    private fun SemanticsNodeInteractionCollection.assertAllAtLeast(what: String) {
        val nodes = fetchSemanticsNodes()
        assertTrue("$what: nothing to measure", nodes.isNotEmpty())
        nodes.indices.forEach { i -> this[i].assertAtLeast(MIN_TARGET, "$what #$i") }
    }

    /** Nodes carrying [tag] or inside one. Both halves, as in `AccessibilityTest`. */
    private fun insideRow(tag: String): SemanticsMatcher =
        hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))

    /** A short description, so a failure says *which* control. */
    private fun describe(node: SemanticsNodeInteraction): String {
        val config = node.fetchSemanticsNode().config
        val text = config.getOrNull(SemanticsProperties.Text)
        val size = node.fetchSemanticsNode().size
        return "(text=${text?.joinToString(", ")?.take(30)}, ${size.width}x${size.height})"
    }

    private companion object {
        val MIN_TARGET: Dp = 48.dp
        const val TAG_ADD = "penyewa-add"
        const val TAG_EMPTY_LIST = "penyewa-empty-list"
        const val TAG_EMPTY_SEARCH = "penyewa-empty-search"
    }
}
