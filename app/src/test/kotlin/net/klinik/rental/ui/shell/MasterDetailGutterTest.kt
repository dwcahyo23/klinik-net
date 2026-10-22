package net.klinik.rental.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.components.KnMasterDetail
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.KnSpacing
import net.klinik.rental.ui.theme.knSpacing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The gutter `KnMasterDetail` leaves, measured rather than read.
 *
 * ## What the defect was
 *
 * `shared.css:285-289` pads `.page-body` **once**, with `var(--sp-6)` — 24dp. The app applied
 * that padding twice on all three master-detail screens: each caller pads its own body column
 * (`TunggakanScreen:186`, `MobilScreen:160`, `PenyewaScreen:145`) and `KnMasterDetail` then
 * added a second `spacing.xxl` of its own. The prototype's 24dp shipped as 48dp.
 *
 * ## Why the fix belongs in the component, and why the test is shaped the way it is
 *
 * The padding was removed from `KnMasterDetail` rather than from the three callers, because the
 * callers were already right: one edit closes three bugs, and the alternative leaves a component
 * that silently assumes every caller padded for it — which makes the *fourth* caller wrong.
 *
 * So this test rebuilds the shape the callers use — a body column padded `spacing.xxl` with
 * `KnMasterDetail` inside it — and measures the gutter that actually reaches the content. It
 * fails if **either** layer starts padding again, which is the property neither a source read nor
 * a label-only assertion can catch.
 *
 * ## Both orientations, because the two branches are separate code
 *
 * `KnMasterDetail` branches on `BoxWithConstraints` at 600dp, and the two branches each carried
 * their own copy of the padding. A test that ran at one width would have passed with the other
 * branch still padded — which is why this defect was invisible rather than merely wrong. Each
 * orientation is asserted separately, against the same 24dp.
 *
 * The widths are Robolectric `qualifiers`, so the branch is chosen by the real layout condition
 * and not by a parameter the test sets — a parameter would let a future test quietly assert both
 * branches at the same width and prove nothing.
 */
@RunWith(AndroidJUnit4::class)
class MasterDetailGutterTest {

    @get:Rule
    val compose = createComposeRule()

    /** Captured during composition, so px can be converted to the dp the assertion speaks in. */
    private var density: Density? = null

    /**
     * Wide — the panes sit side by side, so the gutter is visible on the **leading** edge.
     *
     * In a `Row` the list comes first, so nothing else can contribute an inset on that edge:
     * the measured number is the gutter and nothing but the gutter.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the wide layout leaves a single 24dp gutter at the leading edge`() {
        compose.setContent { theme { Body() } }

        assertEquals(
            "the list must start one 24dp gutter in, not two",
            GUTTER,
            leadingInsetOf(TAG_LIST),
        )
    }

    /**
     * Wide — the same gutter at the **trailing** edge.
     *
     * The defect was four-sided padding at both layers, so asserting only the leading edge
     * would let a regression that dropped `start` but kept `end` pass. The detail pane is last
     * in the `Row`, so its right edge carries the end gutter and nothing else.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the wide layout leaves a single 24dp gutter at the trailing edge`() {
        compose.setContent { theme { Body() } }

        assertEquals(
            "the detail pane must stop one 24dp gutter short of the right edge",
            GUTTER,
            trailingInsetOf(TAG_DETAIL),
        )
    }

    /**
     * Wide — the same gutter at the **bottom** edge.
     *
     * The four `padding` arguments are set independently, so a regression that kept `start`,
     * `end` and `top` but dropped `bottom` would leave every other assertion green. The detail
     * pane fills the row's height, so the root's bottom edge minus its own is the bottom gutter
     * and nothing else.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the wide layout leaves a single gutter at the bottom edge`() {
        compose.setContent { theme { Body() } }

        assertEquals(
            "the panes must stop one gutter short of the bottom edge",
            GUTTER,
            bottomInsetOf(TAG_DETAIL),
        )
    }

    /**
     * Narrow — the panes stack, so the gutter is visible on the **top** edge instead.
     *
     * `top` rather than `left` because that is where the gutter shows in this branch. Both
     * branches apply the same four-sided padding, so a one-sided regression in either is caught
     * by whichever of the two edges this orientation happens to measure.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w560dp-h2400dp-port")
    fun `the narrow layout leaves a single 24dp gutter`() {
        compose.setContent { theme { Body() } }

        assertEquals(
            "the list must start one 24dp gutter down, not two",
            GUTTER,
            topInsetOf(TAG_LIST),
        )
    }

    /**
     * Narrow — the **bottom** edge, which is the other edge this branch shows.
     *
     * In a `Column` the list sits above the detail, so the detail's bottom edge carries the
     * bottom gutter. Its own `bottom` argument is independent of the `top` one measured above,
     * which is why both are asserted.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w560dp-h2400dp-port")
    fun `the narrow layout leaves a single gutter at the bottom edge`() {
        compose.setContent { theme { Body() } }

        assertEquals(
            "the detail pane must stop one gutter short of the bottom edge",
            GUTTER,
            bottomInsetOf(TAG_DETAIL),
        )
    }

    // ------------------------------------------------- the 600dp branch boundary

    /**
     * One dp **above** the breakpoint the panes sit side by side.
     *
     * `Body()` pads its column by `spacing.xxl` on each side, so the width `KnMasterDetail`
     * actually receives is the window's **minus 48dp** — `BoxWithConstraints` measures the
     * constraint it is given, not the window. A 648dp window therefore hands it exactly 600dp,
     * which is the first width on the wide side of the threshold. Hence the two neighbouring
     * widths below and here, which is the whole reason this pair exists: one dp apart, either
     * side of the branch.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w648dp-h2400dp-port")
    fun `at the breakpoint width the panes sit side by side`() {
        compose.setContent { theme { Body() } }

        assertEquals(
            "648dp of window is 600dp to KnMasterDetail, which is the wide side of the branch",
            Arrangement.SIDE_BY_SIDE,
            arrangementOf(TAG_LIST, TAG_DETAIL),
        )
    }

    /**
     * One dp **below** the breakpoint the panes stack.
     *
     * The companion to the test above, and the assertion that makes the pair a pin rather than
     * two observations: the threshold is only pinned if both sides of it are observed. Asserting
     * "wide is wide" alone would pass at 640dp or at 7000dp; asserting "narrow is narrow" alone
     * would pass at 200dp. Together they bracket it.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w647dp-h2400dp-port")
    fun `one dp below the breakpoint width the panes stack`() {
        compose.setContent { theme { Body() } }

        assertEquals(
            "647dp of window is 599dp to KnMasterDetail, which is the narrow side of the branch",
            Arrangement.STACKED,
            arrangementOf(TAG_LIST, TAG_DETAIL),
        )
    }

    /**
     * The caller shape under test: `KnMasterDetail` inside a body column padded `spacing.xxl`,
     * which is what `TunggakanScreen`, `MobilScreen` and `PenyewaScreen` each do.
     *
     * The tagged `Column` is wrapped in a tagged root so the offsets have a known origin — a
     * measurement taken against the window rather than against this node would silently absorb
     * whatever margin the harness happened to add. The panes fill their slots so they carry real
     * bounds: a zero-sized node measures as zero on every edge and would pass for the right answer.
     *
     * This harness proves the **component** adds no padding. It cannot prove the three real
     * callers still add theirs — that is what the per-screen inset assertions in
     * `MobilScreenTest`, `PenyewaScreenTest` and `TunggakanScreenTest` are for, since after
     * the component fix the callers are the only padding layer left.
     */
    @Composable
    private fun Body() {
        density = LocalDensity.current
        val spacing = androidx.compose.material3.MaterialTheme.knSpacing
        Column(modifier = Modifier.fillMaxSize().testTag(TAG_ROOT)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(spacing.xxl),
            ) {
                KnMasterDetail(
                    list = { Box(Modifier.fillMaxSize().testTag(TAG_LIST)) },
                    detail = { Box(Modifier.fillMaxSize().testTag(TAG_DETAIL)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * The distance from [tag]'s matching edge to the root's, in dp.
 *
 * `left` and `top` insets grow away from the root's own top-left, so they are
 * `node - root`. `right` and `bottom` are anchored at the **far** edge of the root, so they are
 * `root - node`. That is the whole difference between the two forms, and getting it backwards
 * yields a negative number that reads as a plausible-but-wrong measurement rather than as an
 * arithmetic slip — hence one function that spells out which side it subtracts from.
 */
private fun insetFrom(
    rule: ComposeContentTestRule,
    tag: String,
    density: Density,
    farEdge: Boolean,
    edge: (androidx.compose.ui.geometry.Rect) -> Float,
): Dp {
    val root = rule.onNodeWithTag(TAG_ROOT).fetchSemanticsNode()
    val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
    val a = edge(node.boundsInRoot)
    val b = edge(root.boundsInRoot)
    val px = if (farEdge) b - a else a - b
    return with(density) { px.toDp() }
}

    /** Inset from the root's **leading** (left) edge — `node - root`. */
    private fun leadingInsetOf(tag: String): Dp =
        insetFrom(compose, tag, requireDensity(), farEdge = false) { it.left }

    /** Inset from the root's **trailing** (right) edge — `root - node`. */
    private fun trailingInsetOf(tag: String): Dp =
        insetFrom(compose, tag, requireDensity(), farEdge = true) { it.right }

    /** Inset from the root's **top** edge — `node - root`. */
    private fun topInsetOf(tag: String): Dp =
        insetFrom(compose, tag, requireDensity(), farEdge = false) { it.top }

    /** Inset from the root's **bottom** edge — `root - node`. */
    private fun bottomInsetOf(tag: String): Dp =
        insetFrom(compose, tag, requireDensity(), farEdge = true) { it.bottom }

    /**
     * Which branch the two panes took, read from their geometry rather than from the number.
     *
     * The branch is genuinely observable, so this does not re-assert the constant:
     *
     * - **Side by side** — the `Row` branch gives both panes `fillMaxHeight()`, so they share one
     *   vertical band: `list.bottom` lands below `detail.top`. The list also starts to the
     *   **left** of the detail, which is the part that actually distinguishes this from stacked.
     * - **Stacked** — the `Column` branch gives the list `weight(0.55)` and the detail the rest,
     *   so they occupy disjoint bands: `list.bottom` is at or above `detail.top`, and both start
     *   at the same left edge because both `fillMaxWidth()`.
     *
     * Reading the arrangement off the layout rather than off `600.dp` is what makes the two
     * boundary tests a pin: a constant moved to 700dp would still satisfy "is this wide or
     * narrow?" at 1280dp and 560dp, but it cannot satisfy *both* neighbours of the boundary at
     * once. A test that re-read the constant would pass under any value.
     */
    private fun arrangementOf(listTag: String, detailTag: String): Arrangement {
        val list = compose.onNodeWithTag(listTag).fetchSemanticsNode().boundsInRoot
        val detail = compose.onNodeWithTag(detailTag).fetchSemanticsNode().boundsInRoot
        return if (list.right <= detail.left) Arrangement.SIDE_BY_SIDE else Arrangement.STACKED
    }

    private fun requireDensity(): Density =
        checkNotNull(density) { "the composition must run before any measurement" }

    private companion object {
        const val TAG_ROOT = "gutter-root"
        const val TAG_LIST = "gutter-list"
        const val TAG_DETAIL = "gutter-detail"

        /**
         * `shared.css:285-289` — `--sp-6`, which is `knSpacing.xxl`.
         *
         * Read from the token rather than typed as `24.dp`, because `MobilScreenTest`
         * reads its expected colours the same way for the same reason: a typed literal goes on
         * passing after the token changes, which is exactly when this assertion is worth having.
         * `KnSpacing.Clinic` is the single instance both themes hand out, so this stays correct
         * in either theme without a composition.
         */
        val GUTTER: Dp = KnSpacing.Clinic.xxl
    }
}

/**
 * How the two panes are laid out, decided by which branch of `KnMasterDetail` ran.
 *
 * An enum rather than a `Boolean` so an assertion failure reads "expected SIDE_BY_SIDE but was
 * STACKED". A boolean failure would say only `true`/`false` and leave the reader to remember
 * which side was which.
 */
private enum class Arrangement { SIDE_BY_SIDE, STACKED }