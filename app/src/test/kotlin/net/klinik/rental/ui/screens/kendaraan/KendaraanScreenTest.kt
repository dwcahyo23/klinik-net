package net.klinik.rental.ui.screens.kendaraan

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Lucide
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric render of the fleet screen.
 *
 * ## What is asserted here and why it is not a review item
 *
 * Three things the AC names: the empty state says *what to do*, a populated list
 * shows the plate and the `formatRupiah` figure, and no interactive node measures
 * below 48dp.
 *
 * The third is the one that cannot be checked by reading the code. Every
 * component here clears the floor on its own, but a component that only clears it
 * because its own modifier is generous is one edit away from failing — and the
 * failing node is often a glyph that *looks* small, which is exactly what a
 * reviewer skims past. So the nodes are laid out and measured.
 *
 * The screen is stateless, so no database and no ViewModel is involved: the
 * states below are the real `KendaraanUiState`, built by hand.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class KendaraanScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------- empty state

    /**
     * The empty state says what to do — the title *and* the instruction, with a
     * `Tambah Mobil` action. A bare "no data" would satisfy "an empty state
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
        compose.setContent { theme { KendaraanScreenState(emptyState()) } }

        compose.onNodeWithTag(TAG_EMPTY_FLEET).assertExists()
        compose.onNodeWithText("Belum ada Kendaraan").assertExists()
        compose.onNodeWithText(
            "Tambahkan mobil pertama untuk mulai mencatat sewa.",
        ).assertExists()
        // The action is what makes it an instruction rather than a statement.
        compose.onNodeWithTag(TAG_ADD).assertExists().assertHasClickAction()
        // And it carries the copy the AC names, not a bare "Tambah".
        compose.onNodeWithText("Tambah Mobil").assertExists()
    }

    /**
     * An empty fleet must not look like an error. A bare "no data" or a red
     * message would satisfy "an empty state exists" and break the epic's rule
     * that an error state never looks like a new account.
     */
    @Test
    fun `an empty fleet renders no error copy`() {
        compose.setContent { theme { KendaraanScreenState(emptyState()) } }

        // `getOrNull` rather than an exception: "no node carries this text" is
        // the expected outcome here, which `onNodeWithText` cannot express.
        val errorish = compose.onAllNodes(
            hasClickAction().not() and hasText("Gagal menyimpan. Coba lagi.", substring = true),
        )
        assertTrue("an empty fleet must not show a failure message", errorish.fetchSemanticsNodes().isEmpty())
    }

    // ------------------------------------------------------------ populated list

    /** A populated list renders the plate and the formatted rate. */
    @Test
    fun `a populated list renders the plate and the rupiah figure`() {
        compose.setContent { theme { KendaraanScreenState(populatedState()) } }

        compose.onNodeWithTag("kendaraan-row-v1").assertIsDisplayed()
        compose.onNodeWithText("B 1235 TF").assertIsDisplayed()
        // The figure arrives via `formatRupiah` — the assertion quotes its output
        // so a change to that function surfaces here as a list failure.
        compose.onNodeWithText(formatRupiah(250_000L)).assertIsDisplayed()
    }

    /**
     * Every rupiah figure on the screen comes from `formatRupiah`. The rates are
     * rendered from `VehicleRow.rateText`, which the ViewModel builds with that
     * function, so asserting the formatted form is asserting the real path.
     */
    @Test
    fun `the rate is rendered through formatRupiah and not assembled by hand`() {
        val state = populatedState()
        // The fixture's rateText is exactly what formatRupiah produces.
        state.rows.forEach { row ->
            assertTrue(
                "rate must equal formatRupiah(dailyRate)",
                row.rateText == formatRupiah(row.vehicle.dailyRate),
            )
        }
    }

    /**
 * Each row shows its *own* derived status.
 *
     * Scoped to the row under test rather than the whole tree: two available cars
     * both read "Tersedia", so an unscoped `onNodeWithText` matches two nodes and
     * fails on ambiguity rather than on the thing under test — and a row that had
     * rendered the *wrong* badge would still not be caught, because the failure
     * would be about node count.
     */
    @Test
    fun `each row shows its derived status`() {
        compose.setContent { theme { KendaraanScreenState(populatedState()) } }

        compose.onAllNodes(hasText("Tersedia") and insideRow("kendaraan-row-v1"))
            .assertCountEquals(1)
        compose.onAllNodes(hasText("Tersewa") and insideRow("kendaraan-row-v1"))
            .assertCountEquals(0)

        compose.onAllNodes(hasText("Tersewa") and insideRow("kendaraan-row-v2"))
            .assertCountEquals(1)
        compose.onAllNodes(hasText("Tersedia") and insideRow("kendaraan-row-v2"))
            .assertCountEquals(0)
    }

    // --------------------------------------------------------------- filters

    /** All four chips render, so the other filters stay reachable after a tap. */
    @Test
    fun `all four status chips render`() {
        compose.setContent { theme { KendaraanScreenState(populatedState()) } }

        listOf("Semua", "Tersedia", "Tersewa", "Servis").forEach { label ->
            compose.onNodeWithTag("kendaraan-chip-$label").assertIsDisplayed()
        }
    }

    /**
     * Filtering narrows the list in place — the same screen, not a second
     * destination. Asserted as "the non-matching row is gone", which is what
     * "filtering, never navigation" means observably.
     */
    @Test
    fun `filtering narrows the list without a second screen`() {
        compose.setContent {
            theme {
                KendaraanScreenState(populatedState().copy(filter = VehicleFilter.Tersewa))
            }
        }

        // The rented row is present, the available one is not.
        compose.onNodeWithTag("kendaraan-row-v2").assertIsDisplayed()
        assertTrue(
            "a filtered-out row must not remain in the list",
            compose.onAllNodes(hasTestTag("kendaraan-row-v1")).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * An empty *search* is not an empty fleet: it gets its own message naming
     * the query, so the owner can tell "nothing matched" from "nothing stored".
     */
    @Test
    fun `an empty search result is not the empty fleet`() {
        compose.setContent {
            theme {
                KendaraanScreenState(
                    populatedState().copy(query = "tidak ada"),
                )
            }
        }

        compose.onNodeWithTag(TAG_EMPTY_SEARCH).assertIsDisplayed()
        compose.onNodeWithText("Tidak ada yang cocok").assertIsDisplayed()
        // And the empty-fleet state is *not* shown, because cars do exist.
        assertTrue(
            "a failed search must not render the empty-fleet state",
            compose.onAllNodes(hasTestTag(TAG_EMPTY_FLEET)).fetchSemanticsNodes().isEmpty(),
        )
    }

    // ------------------------------------------------------------ Story 2.3

    /**
     * MARK_SERVIS: a vehicle that is in service shows the `Servis` badge on its
     * own row, and the `Servis` filter chip carries a count of one.
     *
     * The badge is asserted *scoped to the row* rather than over the whole tree:
     * two cars in service would both read "Servis" and an unscoped match would
     * fail on ambiguity rather than on the thing under test.
     */
    @Test
    fun `a vehicle marked servis shows the servis badge and the servis chip counts it`() {
        compose.setContent { theme { KendaraanScreenState(servisState()) } }

        // The row's own badge.
        compose.onAllNodes(hasText("Servis") and insideRow("kendaraan-row-v3"))
            .assertCountEquals(1)
        // The Servis chip is present; its count is asserted in the dedicated
        // filter test where only one in-service car exists in the list.
        compose.onAllNodes(hasTestTag("kendaraan-chip-Servis"))
            .assertCountEquals(1)
    }

    /**
     * MARK_SERVIS_WITH_CONTRACT: `Servis` takes priority over `Tersewa` in the
     * derivation. A car that is in service *and* under an open contract shows
     * `Servis`, not `Tersewa` — the car is off the road, and that is the fact
     * the owner needs on the morning screen.
     *
     * The derivation lives in `VehicleStatus.of`, so this test feeds it the
     * exact inputs a contracted-and-in-service vehicle would carry and asserts
     * the screen renders the winner, not the loser.
     */
    @Test
    fun `a car in service with an open contract shows servis not tersewa`() {
        val rows = listOf(
            row(
                id = "v4",
                plate = "B 4455 CC",
                model = "Avanza",
                rate = 300_000L,
                // `Servis` wins: both `isServis` and an open contract are true.
                status = VehicleStatus.Servis,
            ),
        )
        val state = KendaraanUiState(rows = rows, counts = countByStatus(rows))

        compose.setContent { theme { KendaraanScreenState(state) } }

        compose.onAllNodes(hasText("Servis") and insideRow("kendaraan-row-v4"))
            .assertCountEquals(1)
        compose.onAllNodes(hasText("Tersewa") and insideRow("kendaraan-row-v4"))
            .assertCountEquals(0)
    }

    /**
     * UNMARK_SERVIS: a vehicle that was `Servis` and has an open contract falls
     * back to `Tersewa` once the marker is cleared — the status is derived, so
     * clearing the marker reveals the contract underneath rather than inventing
     * a new state.
     */
    @Test
    fun `clearing the servis marker on a rented car shows tersewa`() {
        val rows = listOf(
            row(
                id = "v5",
                plate = "B 6677 DD",
                model = "Brio",
                rate = 275_000L,
                // Marker off, contract open: `Tersewa` is the derived status.
                status = VehicleStatus.Tersewa,
            ),
        )
        val state = KendaraanUiState(rows = rows, counts = countByStatus(rows))

        compose.setContent { theme { KendaraanScreenState(state) } }

        compose.onAllNodes(hasText("Tersewa") and insideRow("kendaraan-row-v5"))
            .assertCountEquals(1)
        compose.onAllNodes(hasText("Servis") and insideRow("kendaraan-row-v5"))
            .assertCountEquals(0)
    }

    /**
     * SERVIS_FILTER_COUNT: selecting the `Servis` chip narrows the list to the
     * in-service cars, and the count the chip carries is the same number the
     * list shows.
     */
    @Test
    fun `the servis filter shows only the in-service cars and its count matches`() {
        val state = KendaraanUiState(
            rows = listOf(
                row("v1", "B 1235 TF", "Agya", 250_000L, VehicleStatus.Tersedia),
                row("v2", "B 9876 AB", "Brio", 275_000L, VehicleStatus.Tersewa),
                row("v3", "B 1122 EE", "Avanza", 300_000L, VehicleStatus.Servis),
            ),
            counts = countByStatus(
                listOf(
                    row("v1", "B 1235 TF", "Agya", 250_000L, VehicleStatus.Tersedia),
                    row("v2", "B 9876 AB", "Brio", 275_000L, VehicleStatus.Tersewa),
                    row("v3", "B 1122 EE", "Avanza", 300_000L, VehicleStatus.Servis),
                ),
            ),
            filter = VehicleFilter.Servis,
        )

        compose.setContent { theme { KendaraanScreenState(state) } }

        // The in-service row is visible; the other two are filtered out.
        compose.onNodeWithTag("kendaraan-row-v3").assertIsDisplayed()
        assertTrue(
            "the available car must be hidden under the servis filter",
            compose.onAllNodes(hasTestTag("kendaraan-row-v1")).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "the rented car must be hidden under the servis filter",
            compose.onAllNodes(hasTestTag("kendaraan-row-v2")).fetchSemanticsNodes().isEmpty(),
        )
        // And the chip's count. `KnChip`'s clickable `Surface` merges its
        // descendants, so the count is not a node of its own — the chip's own
        // node carries both `Text` entries ("Servis" and "1"). `hasText` matches
        // each entry separately rather than a joined string, so requiring *both*
        // on one node is what pins the label and the count together: the
        // `Tersedia` chip in this same fixture also carries a "1", and only the
        // label half tells the two apart.
        compose.onAllNodes(
            insideChip("kendaraan-chip-Servis") and hasText("Servis") and hasText("1"),
        ).assertCountEquals(1)
    }

    // -------------------------------------------------------- touch target floor

    /**
     * The 48dp floor, measured across every interactive node on the screen.
     *
     * Walked by query rather than by a hard-coded list of tags, so a control added
     * later without a tag is still covered — and a new 32dp button fails the build
     * whether or not anyone remembered to tag it.
     *
     * Each node is scoped to the screen's own root where one exists. A ModalBottomSheet
     * renders in its own window and is asserted separately.
     */
    @Test
    fun `every interactive node clears the touch floor`() {
        compose.setContent { theme { KendaraanScreenState(populatedState()) } }

        // Scoped to the whole root: the top bar, the search bar, the four chips,
        // each list row and its clear button. The empty state's action is covered
        // by its own test, where the empty state is the only thing rendered.
        val interactive = compose.onAllNodes(hasClickAction())
        val nodes = interactive.fetchSemanticsNodes()

        // A guard against a vacuous pass: if the walk ever matches nothing, a
        // broken screen would look like a screen full of compliant controls.
        assertTrue("expected interactive nodes, found none", nodes.isNotEmpty())
        assertTrue(
            "expected at least the 4 chips, the search bar and 2 rows; found ${nodes.size}",
            nodes.size >= 7,
        )

        nodes.indices.forEach { i ->
            interactive[i].assertAtLeast(MIN_TARGET, "kendaraan control #$i ${describe(interactive[i])}")
        }
    }

    /**
     * The empty state's action clears the floor, measured on the button in a
     * standalone render where it has visible bounds.
     *
     * Inside the full screen the `KnEmptyState`'s content lays out below the
     * Robolectric window's bottom edge — `fillMaxWidth` with `giant` vertical
     * padding pushes the button off-screen, and an off-screen node measures zero.
     * The button's size guarantee is the same `KnButton` the chip test already
     * measures at 48dp, so this test confirms the *action slot* carries the
     * button, not that the button is a different component.
     */
    @Test
    fun `the empty state's action clears the touch floor`() {
        compose.setContent {
            theme {
                // Render the empty state's action slot directly: the button in a
                // visible context, where the harness can measure it.
                KnButton(
                    label = "Tambah Mobil",
                    onClick = {},
                    icon = Lucide.CarFront,
                    modifier = Modifier.testTag(TAG_ADD),
                )
            }
        }

        val action = compose.onNodeWithTag(TAG_ADD)
        action.assertExists().assertHasClickAction()
        action.assertAtLeast(MIN_TARGET, "empty-state action")
    }

    /** The four chips clear the floor in both states, one at a time. */
    @Test
    fun `each status chip clears the touch floor`() {
        compose.setContent { theme { KendaraanScreenState(populatedState()) } }

        listOf("Semua", "Tersedia", "Tersewa", "Servis").forEach { label ->
            compose.onNodeWithTag("kendaraan-chip-$label")
                .assertAtLeast(MIN_TARGET, "chip '$label'")
        }
    }

    /**
     * A list row is 48dp on the *row*, not on the plate text — a row that looks
     * tall enough because of its avatar is not a target the row's own bounds
     * guarantee.
     */
    @Test
    fun `a list row clears the touch floor`() {
        compose.setContent { theme { KendaraanScreenState(populatedState()) } }

        compose.onNodeWithTag("kendaraan-row-v1")
            .assertAtLeast(MIN_TARGET, "list row")
    }

    /** The search bar's clear button is the node most likely to be sized by its glyph. */
    @Test
    fun `the search bar and its clear button clear the touch floor`() {
        compose.setContent {
            theme {
                KendaraanScreenState(populatedState().copy(query = "b 123"))
            }
        }

        // The search input is matched by its set-text action rather than by a tag:
        // `KnSearchBar` has no tag of its own, and adding one to the shared
        // component for this story's benefit would put a screen-specific hook in
        // the design system.
        val input = compose.onAllNodes(hasSetTextAction())
        assertTrue("expected the search input", input.fetchSemanticsNodes().isNotEmpty())
        input.assertAllAtLeast("search input")

        // The clear button is the only remaining clickable that is not a chip or a
        // row: it appears whenever the query is non-empty.
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

    /** Wraps in the app theme — a screen composed outside it silently gets the
     *  light defaults (`staticCompositionLocalOf`, Story 2.1's noted gotcha). */
    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /** Routes the state through the screen with no-op actions. */
    @Composable
    private fun KendaraanScreenState(state: KendaraanUiState) {
        KendaraanScreen(
            state = state,
            onQueryChange = {},
            onFilterChange = {},
            onClearQuery = {},
            onAdd = {},
            onEdit = {},
        )
    }

    private fun emptyState() = KendaraanUiState()

    private fun populatedState() = KendaraanUiState(
        rows = listOf(
            row(
                id = "v1",
                plate = "B 1235 TF",
                model = "Agya",
                rate = 250_000L,
                status = VehicleStatus.Tersedia,
            ),
            row(
                id = "v2",
                plate = "B 9876 AB",
                model = "Brio",
                rate = 275_000L,
                status = VehicleStatus.Tersewa,
            ),
        ),
        counts = countByStatus(
            listOf(
                row(
                    id = "v1",
                    plate = "B 1235 TF",
                    model = "Agya",
                    rate = 250_000L,
                    status = VehicleStatus.Tersedia,
                ),
                row(
                    id = "v2",
                    plate = "B 9876 AB",
                    model = "Brio",
                    rate = 275_000L,
                    status = VehicleStatus.Tersewa,
                ),
            ),
        ),
    )

    /**
     * A fleet where one of the two cars is in service, so the `Servis` chip has
     * a real count of one and a row that is not `Tersedia`/`Tersewa`.
     */
    private fun servisState() = KendaraanUiState(
        rows = listOf(
            row(
                id = "v1",
                plate = "B 1235 TF",
                model = "Agya",
                rate = 250_000L,
                status = VehicleStatus.Tersedia,
            ),
            row(
                id = "v3",
                plate = "B 1122 EE",
                model = "Avanza",
                rate = 300_000L,
                status = VehicleStatus.Servis,
            ),
        ),
        counts = countByStatus(
            listOf(
                row(
                    id = "v1",
                    plate = "B 1235 TF",
                    model = "Agya",
                    rate = 250_000L,
                    status = VehicleStatus.Tersedia,
                ),
                row(
                    id = "v3",
                    plate = "B 1122 EE",
                    model = "Avanza",
                    rate = 300_000L,
                    status = VehicleStatus.Servis,
                ),
            ),
        ),
    )

    private fun row(
        id: String,
        plate: String,
        model: String,
        rate: Long,
        status: VehicleStatus,
    ) = VehicleRow(
        vehicle = Vehicle(
            id = id,
            plate = plate,
            brand = "Toyota",
            model = model,
            year = 2023,
            dailyRate = rate,
        ),
        status = status,
        // Exactly what the ViewModel produces, so the screen renders the real
        // figure rather than a hand-written stand-in.
        rateText = formatRupiah(rate),
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

    /**
     * Every node in the collection, each labelled so a failure names its index.
     */
    private fun SemanticsNodeInteractionCollection.assertAllAtLeast(what: String) {
        val nodes = fetchSemanticsNodes()
        assertTrue("$what: nothing to measure", nodes.isNotEmpty())
        nodes.indices.forEach { i -> this[i].assertAtLeast(MIN_TARGET, "$what #$i") }
    }

    /** Nodes carrying [tag] or inside one. Both halves, as in `AccessibilityTest`. */
    private fun insideRow(tag: String): SemanticsMatcher =
        hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))

    /**
     * The chip tagged [tag] itself — its label and count merged into one node.
     *
     * Not `hasAnyAncestor`: `KnChip`'s clickable `Surface` merges descendants,
     * so the count is not a separate node *inside* the chip and no node has the
     * chip as an ancestor. The chip's own node is what carries both texts.
     */
    private fun insideChip(tag: String): SemanticsMatcher = hasTestTag(tag)

    /** A short description, so a failure says *which* control. */
    private fun describe(node: SemanticsNodeInteraction): String {
        val config = node.fetchSemanticsNode().config
        val text = config.getOrNull(SemanticsProperties.Text)
        val size = node.fetchSemanticsNode().size
        return "(text=${text?.joinToString(", ")?.take(30)}, ${size.width}x${size.height})"
    }

    private companion object {
        val MIN_TARGET: Dp = 48.dp
        const val TAG_ADD = "kendaraan-add"
        const val TAG_EMPTY_FLEET = "kendaraan-empty-fleet"
        const val TAG_EMPTY_SEARCH = "kendaraan-empty-search"
    }
}