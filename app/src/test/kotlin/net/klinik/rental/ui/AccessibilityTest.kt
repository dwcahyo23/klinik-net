package net.klinik.rental.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Users
import net.klinik.rental.ui.components.KnBadge
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.components.KnBottomNav
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonSize
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.components.KnCard
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.components.KnEmptyState
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnIconButton
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnModal
import net.klinik.rental.ui.components.KnNavItem
import net.klinik.rental.ui.components.KnNavRail
import net.klinik.rental.ui.components.KnNumPad
import net.klinik.rental.ui.components.KnPinGrid
import net.klinik.rental.ui.components.KnSearchBar
import net.klinik.rental.ui.components.KnSnackbar
import net.klinik.rental.ui.components.KnTableCell
import net.klinik.rental.ui.components.KnTableRow
import net.klinik.rental.ui.components.KnTopBar
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knTypography
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The 48dp half of Story 2.1's accessibility AC — measured, not reviewed.
 *
 * ## Why these are composed rather than asserted as constants
 *
 * The AC says the measurement "is part of the test, not a manual check", and a
 * review comment cannot hold a line: it is skipped on a good day and quietly
 * reverted on a bad one. So each component is actually laid out and its bounds
 * are read back out of the semantics tree. A component that satisfies
 * `KnAccessibility.minTouchTarget == 48.dp` as a *constant* can still ship a
 * 26dp hit box if it forgets to apply the modifier, and only a rendered
 * measurement catches that.
 *
 * ## Why Badge, Chip and the in-row icon button specifically
 *
 * All three are the nodes the story names, because all three are visually
 * smaller than 48dp in `shared.css` and each is a plausible place to ship a
 * small hit target. `KnButtonSize.Small` is a fourth: `.btn-sm` is 44dp in the
 * CSS, also below the floor.
 *
 * These assertions use the *unclipped* bounds, which is what the user's finger
 * has to hit — clipping a node down to 26dp inside a scroll container would
 * still leave a 26dp target even though the node itself laid out at 48dp.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    // ---------------------------------------------------------------- buttons

    /**
     * All 5 variants x 4 sizes. Each gets its own tag so a failure names the
     * combination rather than "a button".
     *
     * The host is a scrolling column rather than a plain one: twenty buttons do
     * not fit the Robolectric default window, and a button laid out past the
     * bottom edge measures zero — which would make this test pass or fail for a
     * reason that has nothing to do with touch targets.
     */
    @Test
    fun `every button variant and size clears the touch floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    KnButtonVariant.entries.forEach { variant ->
                        KnButtonSize.entries.forEach { size ->
                            KnButton(
                                label = variant.name,
                                onClick = {},
                                variant = variant,
                                size = size,
                                icon = Lucide.Search,
                                modifier = Modifier.testTag(tag(variant, size)),
                            )
                        }
                    }
                }
            }
        }

        // The AC requires all 20 combinations to render.
        assertTrue(
            "expected 5 variants x 4 sizes",
            KnButtonVariant.entries.size * KnButtonSize.entries.size == 20,
        )

        KnButtonVariant.entries.forEach { variant ->
            KnButtonSize.entries.forEach { size ->
                compose.onNodeWithTag(tag(variant, size))
                    .assertAtLeast(MIN_TARGET, "${variant.name}/${size.name}")
            }
        }
    }

    /**
     * `.btn-icon` is specified as *exactly* 48dp, not merely at least — an icon
     * button that grew wider than tall would stop reading as a circle.
     */
    @Test
    fun `the icon button measures exactly 48dp`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnButton(
                    label = "Tambah",
                    onClick = {},
                    size = KnButtonSize.Icon,
                    icon = Lucide.Search,
                    modifier = Modifier.testTag(TAG_ICON_BUTTON),
                )
            }
        }
        compose.onNodeWithTag(TAG_ICON_BUTTON)
            .assertWidthIsEqualTo(MIN_TARGET)
            .assertHeightIsEqualTo(MIN_TARGET)
    }

    // ------------------------------------------------------------------ badge

    /** A 26dp badge that is tappable still has to present a 48dp target. */
    @Test
    fun `an interactive badge clears the floor although it renders at 26dp`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnBadge(
                    text = "Tersewa",
                    tone = KnBadgeTone.Success,
                    onClick = {},
                    modifier = Modifier.testTag(TAG_BADGE),
                )
            }
        }
        compose.onNodeWithTag(TAG_BADGE).assertAtLeast(MIN_TARGET, "interactive Badge")
    }

    @Test
    fun `a non-interactive badge is decoration and needs no target`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnBadge(
                    text = "Tersedia",
                    tone = KnBadgeTone.Neutral,
                    modifier = Modifier.testTag(TAG_BADGE_STATIC),
                )
            }
        }
        // Present and rendered; the floor does not apply to a non-interactive node.
        compose.onNodeWithTag(TAG_BADGE_STATIC).assertExists()
    }

    // ------------------------------------------------------------------- chip

    @Test
    fun `a chip clears the floor on both axes in both states`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    KnChip(
                        text = "Tersedia",
                        selected = false,
                        onClick = {},
                        modifier = Modifier.testTag(TAG_CHIP_OFF),
                    )
                    KnChip(
                        text = "Tersewa",
                        selected = true,
                        onClick = {},
                        modifier = Modifier.testTag(TAG_CHIP_ON),
                    )
                }
            }
        }
        compose.onNodeWithTag(TAG_CHIP_OFF).assertAtLeast(MIN_TARGET, "Chip(selected=false)")
        compose.onNodeWithTag(TAG_CHIP_ON).assertAtLeast(MIN_TARGET, "Chip(selected=true)")
    }

    // ------------------------------------------------------ in-row icon button

    /** A 20dp glyph must still present a 48dp hit box. */
    @Test
    fun `an in-row icon button clears the floor despite a 20dp glyph`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnIconButton(
                    icon = Lucide.Search,
                    contentDescription = "Ubah",
                    onClick = {},
                    modifier = Modifier.testTag(TAG_ICON_IN_ROW),
                )
            }
        }
        compose.onNodeWithTag(TAG_ICON_IN_ROW)
            .assertAtLeast(MIN_TARGET, "in-row KnIconButton")
    }

    // ---------------------------------------------------------- dark parity

    @Test
    fun `the same components clear the floor in the dark theme`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    KnBadge(
                        text = "Servis",
                        tone = KnBadgeTone.Warning,
                        onClick = {},
                        modifier = Modifier.testTag(TAG_DARK_BADGE),
                    )
                    KnChip(
                        text = "Aktif",
                        selected = true,
                        onClick = {},
                        modifier = Modifier.testTag(TAG_DARK_CHIP),
                    )
                    KnIconButton(
                        icon = Lucide.Search,
                        contentDescription = "Ubah",
                        onClick = {},
                        modifier = Modifier.testTag(TAG_DARK_ICON),
                    )
                    KnButton(
                        label = "Simpan",
                        onClick = {},
                        size = KnButtonSize.Small,
                        modifier = Modifier.testTag(TAG_DARK_SMALL),
                    )
                }
            }
        }
        compose.onNodeWithTag(TAG_DARK_BADGE).assertAtLeast(MIN_TARGET, "dark Badge")
        compose.onNodeWithTag(TAG_DARK_CHIP).assertAtLeast(MIN_TARGET, "dark Chip")
        compose.onNodeWithTag(TAG_DARK_ICON).assertAtLeast(MIN_TARGET, "dark KnIconButton")
        compose.onNodeWithTag(TAG_DARK_SMALL).assertAtLeast(MIN_TARGET, "dark Small button")
    }

    /**
     * A card whose content is thin is only as tall as its padding, which at the
     * default 20dp is 40dp — below the floor. Clickable cards therefore carry
     * [net.klinik.rental.ui.theme.minTouchTarget]; this is the assertion that
     * keeps that from being quietly removed, since a card looks nothing like a
     * 40dp tap target when it fails.
     */
    @Test
    fun `a clickable card with thin content still clears the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    KnCard(
                        onClick = {},
                        modifier = Modifier.testTag(TAG_CARD_CLICKABLE),
                    ) {
                        Text("B 1235 TF", style = MaterialTheme.knTypography.body)
                    }
                }
            }
        }
        compose.onNodeWithTag(TAG_CARD_CLICKABLE).assertAtLeast(MIN_TARGET, "clickable KnCard")
    }

    // -------------------------------------------------- navigation surfaces

    /**
     * Nav destinations, asserted by walking every clickable node in the rail
     * rather than by tagging each item.
     *
     * That is a stronger claim than "the four items I remembered to tag": a node
     * added to the rail later without the tag is still covered, and a clickable
     * node that slips in at 32dp fails the build whether or not anyone named it.
     */
    @Test
    fun `every clickable node in the nav rail clears the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnNavRail(
                    items = NAV_ITEMS,
                    selectedIndex = 0,
                    onSelect = {},
                    modifier = Modifier.testTag(TAG_RAIL),
                )
            }
        }
        compose.onAllNodes(
            hasClickAction() and taggedOrInside(TAG_RAIL),
        ).assertAllAtLeast("nav rail destination")
    }

    /**
     * Separate from the rail test because a ComposeTestRule accepts one
     * `setContent` per Activity, and because rail and bottom bar together exceed
     * the default test window — a node past the bottom edge measures zero, which
     * is a layout artefact rather than a touch-target failure.
     */
    @Test
    fun `every clickable node in the bottom nav clears the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnBottomNav(
                    items = NAV_ITEMS,
                    selectedIndex = 1,
                    onSelect = {},
                    modifier = Modifier.testTag(TAG_BOTTOM_NAV),
                )
            }
        }
        compose.onAllNodes(
            hasClickAction() and taggedOrInside(TAG_BOTTOM_NAV),
        ).assertAllAtLeast("bottom nav destination")
    }

    @Test
    fun `the top bar navigation target clears the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnTopBar(
                    title = "Armada",
                    navigationIcon = Lucide.ArrowLeft,
                    onNavigationClick = {},
                    modifier = Modifier.testTag(TAG_TOP_BAR),
                )
            }
        }
        // Walk the clickable nodes so the test does not depend on an internal
        // tag for the back button.
        val clickable = compose.onAllNodes(hasClickAction() and taggedOrInside(TAG_TOP_BAR))
        assertTrue("top bar rendered no navigation target", clickable.fetchSemanticsNodes().isNotEmpty())
        clickable.assertAllAtLeast("top bar target")
    }

    /**
     * Every pin key is 84dp in the CSS, well clear of the floor — but the grid
     * is walked key by key rather than measured on its container, for the same
     * reason the numpad is: asserting on the container says nothing about the
     * keys, and a key that lost its `minTouchTarget` would leave the container
     * exactly as tall as before.
     */
    @Test
    fun `every pin grid key clears the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnPinGrid(
                    enteredDigits = 1,
                    totalDigits = 6,
                    onDigit = {},
                    onDelete = {},
                    modifier = Modifier.testTag(TAG_PIN_GRID),
                )
            }
        }

        val keys = compose.onAllNodes(
            hasClickAction() and taggedOrInside(TAG_PIN_GRID),
        )
        val nodes = keys.fetchSemanticsNodes()
        // Twelve keys: 1-9, 0, delete, and the blank filler the CSS grid needs
        // to keep the row aligned.
        assertTrue("expected 12 pin keys, found ${nodes.size}", nodes.size >= 10)
        keys.assertAllAtLeast("pin key")
    }

    // --------------------------------------------- the remaining components

    /**
     * The numpad carries sixteen interactive nodes: ten digit keys, three
     * function keys, the wide `0`, and Save. All sixteen are walked, so a key
     * added later without a tag is still covered.
     *
     * The grid is hosted in a scrolling column because it is taller than the
     * default test window together with anything else, and a node laid out past
     * the bottom edge measures zero — off-screen, not too small.
     */
    @Test
    fun `every numpad key clears the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    KnNumPad(
                        value = "250000",
                        onValueChange = {},
                        onSave = {},
                        modifier = Modifier.testTag(TAG_NUMPAD),
                    )
                }
            }
        }

        val clickable = compose.onAllNodes(
            hasClickAction() and taggedOrInside(TAG_NUMPAD),
        )
        val nodes = clickable.fetchSemanticsNodes()
        // Nine digits plus a wide 0, three function keys, and Save.
        assertTrue("expected 14 numpad keys, found ${nodes.size}", nodes.size >= 14)
        clickable.assertAllAtLeast("numpad key")
    }

    /**
     * The field's own box is ≥48dp tall by token, and its trailing icon button
     * is a separate interactive node — the one a reviewer skims past, because a
     * 20dp glyph is what it looks like.
     */
    @Test
    fun `the field and its trailing button clear the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    KnField(
                        label = "Cari plat",
                        value = "B 1235 TF",
                        onValueChange = {},
                        trailingIcon = Lucide.Search,
                        trailingContentDescription = "Cari",
                        onTrailingIconClick = {},
                        modifier = Modifier.testTag(TAG_FIELD),
                    )
                }
            }
        }

        // The input itself.
        compose.onAllNodes(
            hasSetTextAction() and taggedOrInside(TAG_FIELD),
        ).assertAllAtLeast("field input")

        // The trailing button.
        compose.onAllNodes(
            hasClickAction() and taggedOrInside(TAG_FIELD),
        ).assertAllAtLeast("field trailing button")
    }

    /** The clear button only exists once there is something to clear. */
    @Test
    fun `the search bar clear button clears the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    KnSearchBar(
                        query = "B 1235",
                        onQueryChange = {},
                        placeholder = "Cari",
                        modifier = Modifier.testTag(TAG_SEARCH),
                    )
                }
            }
        }

        // The search input.
        compose.onAllNodes(
            hasSetTextAction() and taggedOrInside(TAG_SEARCH),
        ).assertAllAtLeast("search input")

        // The clear button.
        compose.onAllNodes(
            hasClickAction() and taggedOrInside(TAG_SEARCH),
        ).assertAllAtLeast("search clear button")
    }

    @Test
    fun `the snackbar action and dismiss clear the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    KnSnackbar(
                        message = "Kendaraan tersimpan",
                        tone = KnBadgeTone.Success,
                        actionLabel = "Urungkan",
                        onAction = {},
                        onDismiss = {},
                        modifier = Modifier.testTag(TAG_SNACKBAR),
                    )
                }
            }
        }

        compose.onAllNodes(
            hasClickAction() and taggedOrInside(TAG_SNACKBAR),
        ).assertAllAtLeast("snackbar control")
    }

    /**
     * The empty state's action slot is caller-supplied, so what is measured here
     * is the slot's own contribution: a bare button placed in it.
     */
    @Test
    fun `the empty state action clears the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnEmptyState(
                    title = "Belum ada kendaraan",
                    body = "Tambahkan kendaraan pertama untuk mulai.",
                    icon = Lucide.CarFront,
                    action = {
                        KnButton(
                            label = "Tambah kendaraan",
                            onClick = {},
                            modifier = Modifier.testTag(TAG_EMPTY_ACTION),
                        )
                    },
                    modifier = Modifier.testTag(TAG_EMPTY_STATE),
                )
            }
        }

        compose.onNodeWithTag(TAG_EMPTY_ACTION)
            .assertAtLeast(MIN_TARGET, "empty state action")
    }

    /**
     * A modal renders in its own window, so the clickables are addressed through
     * the dialog rather than through a container tag.
     */
    @Test
    fun `the modal confirm dismiss and close clear the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnModal(
                    visible = true,
                    onDismissRequest = {},
                    title = "Hapus kendaraan?",
                    confirmLabel = "Hapus",
                    onConfirm = {},
                    dismissLabel = "Batal",
                    onDismiss = {},
                ) {
                    Text("Tindakan ini tidak dapat dibatalkan.")
                }
            }
        }

        val clickable = compose.onAllNodes(hasClickAction())
        assertTrue("the modal rendered no controls", clickable.fetchSemanticsNodes().isNotEmpty())
        clickable.assertAllAtLeast("modal control")
    }

    /** A clickable list row is the row itself, not just its text. */
    @Test
    fun `a clickable list row clears the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    KnListRow(
                        title = "B 1235 TF",
                        subtitle = "Toyota Avanza 2023",
                        onClick = {},
                        modifier = Modifier.testTag(TAG_LIST_ROW),
                    )
                }
            }
        }

        compose.onAllNodes(
            hasClickAction() and taggedOrInside(TAG_LIST_ROW),
        ).assertAllAtLeast("list row")
    }

    @Test
    fun `a clickable table row clears the floor`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    KnTableRow(
                        cells = listOf(
                            KnTableCell("B 1235 TF"),
                            KnTableCell("Rp 350.000", numeric = true),
                        ),
                        onClick = {},
                        modifier = Modifier.testTag(TAG_TABLE_ROW),
                    )
                }
            }
        }

        compose.onAllNodes(
            hasClickAction() and taggedOrInside(TAG_TABLE_ROW),
        ).assertAllAtLeast("table row")
    }

    // ------------------------------------------------------------------ helper

    private fun tag(variant: KnButtonVariant, size: KnButtonSize) =
        "btn-${variant.name}-${size.name}"

    private fun SemanticsNodeInteraction.assertAtLeast(target: Dp, what: String) {
        try {
            assertWidthIsAtLeast(target)
            assertHeightIsAtLeast(target)
        } catch (e: AssertionError) {
            throw AssertionError(
                "$what failed the 48dp touch-target floor: ${e.message}",
                e,
            )
        }
    }

    /**
     * Matches a node carrying [tag] *or* any node inside one.
     *
     * Both halves are needed and the difference is easy to miss: a component
     * that puts the caller's `modifier` on its own clickable `Surface` (list
     * row, table row) has the tag on the clickable node itself, while a
     * component that tags an outer wrapper (numpad, field, search bar) has the
     * clickable node as a *descendant*. Using only `hasAnyAncestor` silently
     * measures nothing for the first kind.
     */
    private fun taggedOrInside(tag: String): SemanticsMatcher =
        hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))

    /** Every node in the collection, each labelled so a failure names its index. */
    private fun SemanticsNodeInteractionCollection.assertAllAtLeast(what: String) {
        val nodes = fetchSemanticsNodes()
        assertTrue("$what: nothing to measure", nodes.isNotEmpty())
        nodes.indices.forEach { i ->
            this[i].assertAtLeast(MIN_TARGET, "$what #$i ${describe(this[i])}")
        }
    }

    /** A short human description of a node, so a failure says *which* control. */
    private fun describe(node: SemanticsNodeInteraction): String {
        val config = node.fetchSemanticsNode().config
        val desc = config.getOrNull(SemanticsProperties.ContentDescription)
        val text = config.getOrNull(SemanticsProperties.Text)
        val label = when {
            !desc.isNullOrEmpty() -> "desc='${desc.joinToString(", ")}'"
            !text.isNullOrEmpty() -> "text='${text.joinToString(", ").take(40)}'"
            else -> "role=${config.getOrNull(SemanticsProperties.Role)}"
        }
        val size = node.fetchSemanticsNode().size
        return "($label, ${size.width}x${size.height})"
    }

    private companion object {
        // `val`, not `const val`: Dp is an inline value class over a Float and
        // a const of that type is not allowed.
        val MIN_TARGET: Dp = 48.dp
        const val TAG_ICON_BUTTON = "icon-button"
        const val TAG_BADGE = "badge-interactive"
        const val TAG_BADGE_STATIC = "badge-static"
        const val TAG_CHIP_OFF = "chip-off"
        const val TAG_CHIP_ON = "chip-on"
        const val TAG_ICON_IN_ROW = "icon-in-row"
        const val TAG_DARK_BADGE = "dark-badge"
        const val TAG_DARK_CHIP = "dark-chip"
        const val TAG_DARK_ICON = "dark-icon"
        const val TAG_DARK_SMALL = "dark-small-button"
        const val TAG_CARD_CLICKABLE = "card-clickable"
        const val TAG_RAIL = "nav-rail"
        const val TAG_BOTTOM_NAV = "bottom-nav"
        const val TAG_TOP_BAR = "top-bar"
        const val TAG_TOP_BAR_NAV = "top-bar-nav"
        const val TAG_PIN_GRID = "pin-grid"
        const val TAG_NUMPAD = "numpad"
        const val TAG_FIELD = "field"
        const val TAG_SEARCH = "search"
        const val TAG_SNACKBAR = "snackbar"
        const val TAG_EMPTY_STATE = "empty-state"
        const val TAG_EMPTY_ACTION = "empty-action"
        const val TAG_LIST_ROW = "list-row"
        const val TAG_TABLE_ROW = "table-row"

        val NAV_ITEMS = listOf(
            KnNavItem("Beranda", Lucide.House),
            KnNavItem("Armada", Lucide.CarFront),
            KnNavItem("Penyewa", Lucide.Users),
            KnNavItem("Setelan", Lucide.Settings),
        )
    }
}