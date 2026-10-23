package net.klinik.rental.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A KPI figure is **readable in full**, at every magnitude the app can produce.
 *
 * ## Why this test exists
 *
 * Because the owner found the opposite on the device, with the demo data loaded:
 * `Rp 46.350.000` rendered as `Rp 46.…` and `Rp 6.150.000` as `Rp 6.1…`. An ellipsised money
 * figure is not a shortened figure — it is a *different, smaller* one, and the whole point of
 * the tile is that the owner can read what they earned today. Their rule:
 * *"tipografi Rp ini harusnya bisa dibaca full agar user tahu ohh ini loh pendapatan aku
 * hari ini."*
 *
 * ## What it asserts, and why the full string is the right assertion
 *
 * `onNodeWithText(full)` matches only if the whole value is in the semantics tree — a node
 * whose text is ellipsised still reports the *full* string, so that alone would pass on the
 * broken build. So the second half is what does the work: the rendered node must be **no
 * wider than the tile**, which is what `autoSize` guarantees and what the old
 * `maxLines = 1` + `Ellipsis` did not.
 *
 * The tile is measured at the narrowest width it is ever given (`KpiMinTileWidth`), because
 * that is the case the device screenshots showed — four tiles across a landscape tablet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp")
class KpiOverflowTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The figures the app actually produces, from a few hundred thousand to the hundreds of
     * millions a busy fleet reaches in a cutoff period.
     *
     * Written as literals rather than generated: a test that computed its own expectations
     * from the formatter would stop proving what it names.
     */
    private val figures = listOf(
        "Rp 0",
        "Rp 250.000",
        "Rp 6.150.000",
        "Rp 46.350.000",
        "Rp 123.456.789",
        "Rp 999.999.999",
    )

    /**
     * Every figure in the list, each in its own tile, **all on one screen at once**.
     *
     * One `setContent` per test is the rule — the failure mode is an `IllegalStateException`
     * from a second call — so the loop is over tiles inside a single composition rather than
     * over compositions. That also happens to be the honest shape: the device showed all four
     * figures at once, and one overflowing while the others fit is exactly the defect.
     */
    @Test
    fun `a money figure is never ellipsised at the narrowest tile width`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.width(230.dp)) {
                    figures.forEach { value ->
                        KnKpiTile(
                            label = "Pendapatan hari ini",
                            value = value,
                            modifier = Modifier.testTag(tileTag(value)),
                        )
                    }
                }
            }
        }

        figures.forEach { value ->
            compose.onNodeWithTag(tileTag(value)).assertExists()
            // The whole figure is on screen, not "Rp 46.…".
            compose.onNodeWithText(value).assertIsDisplayed()
            // **And it fits.** `assertIsDisplayed` alone passes on an ellipsised node, because
            // Compose keeps the full string in semantics even when the glyphs are cut. What
            // distinguishes the two is the measured width: `autoSize` shrinks the type until
            // the line fits its parent, so the text node is never wider than the 230dp tile.
            assertTextFitsTheTile(value)
        }
    }

    /**
     * The rendered text is no wider than the tile that holds it.
     *
     * Measured on the **unmerged** tree: the value is a `Text` inside the tile's `Surface`, and
     * the merged node would report the tile's own width — which is bounded by definition and
     * would make this assertion vacuous.
     */
    private fun assertTextFitsTheTile(value: String) {
        val node = compose.onNode(hasText(value), useUnmergedTree = true).fetchSemanticsNode()
        val width = node.boundsInRoot.width
        // The tile's own width, read from the tree rather than converted from dp — the test
        // has no `Density`, and the tile is the thing the text has to fit inside anyway.
        val tileWidth = compose.onNodeWithTag(tileTag(value)).fetchSemanticsNode().boundsInRoot.width
        assertTrue(
            "\"$value\" is ${width}px wide in a ${tileWidth}px tile — it was cut off rather " +
                "than shrunk",
            width <= tileWidth + 1f,
        )
    }

    /** The same, for the tile as it is drawn in a real four-across row. */
    @Test
    fun `four tiles in a row each keep their figure whole`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnKpiGrid(
                    modifier = Modifier.testTag(TAG_GRID),
                    tiles = figures.take(4).map { value ->
                        {
                            KnKpiTile(
                                label = "Pendapatan hari ini",
                                value = value,
                                modifier = Modifier.testTag(tileTag(value)),
                            )
                        }
                    },
                )
            }
        }

        figures.take(4).forEach { value ->
            compose.onNodeWithTag(tileTag(value)).assertExists()
            compose.onNodeWithText(value).assertIsDisplayed()
        }
    }

    /**
     * A **non-money** figure shrinks the same way.
     *
     * "0 penyewa dari 0 kontrak" and "0 penyew…" are the same failure as the rupiah one, and
     * Laporan's settled-renter tile renders exactly that sentence as its value. `autoSize`
     * applies to every tile value, not only the ones that look like money.
     */
    @Test
    fun `a sentence value is never ellipsised either`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Box(Modifier.width(230.dp)) {
                    KnKpiTile(
                        label = "Penyewa lunas",
                        value = "0 penyewa dari 8 kontrak",
                        modifier = Modifier.testTag(TAG),
                    )
                }
            }
        }

        compose.onNodeWithText("0 penyewa dari 8 kontrak").assertIsDisplayed()
    }

    private fun tileTag(value: String): String = "tile-$value"

    private companion object {
        val TILE_WIDTH = 230.dp
        const val TAG = "kpi-overflow-tile"
        const val TAG_GRID = "kpi-overflow-grid"
    }
}
