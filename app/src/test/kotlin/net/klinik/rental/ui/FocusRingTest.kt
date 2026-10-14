package net.klinik.rental.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.klinik.rental.ui.components.KnChip
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knColors
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The focus-ring AC, verified by looking at pixels.
 *
 * ## Why this test exists at all
 *
 * The ring was originally drawn with `Modifier.drawBehind`, which reads
 * correctly and *worked* on a transparent element — and on every opaque one the
 * element's own fill painted over it completely. No amount of reading
 * `Accessibility.kt` would have shown that: the modifier was applied, the
 * colours were right, the width was right. Only rendering showed zero brand
 * pixels on either side of the focus transition.
 *
 * `drawWithContent` fixes it, and this test is what stops the fix being undone
 * by someone who prefers `drawBehind`.
 *
 * ## Why focus is injected rather than requested
 *
 * `Surface(onClick=...)` is genuinely focusable, but Robolectric does not
 * deliver a focus request to it — a semantics probe shows the node offering
 * `RequestFocus` while still reporting `Focused = 'false'`. Asserting through
 * the real focus system would therefore fail for a harness reason while the
 * drawing stayed correct, and a test that cannot tell those apart is worse than
 * no test.
 *
 * So the state that `collectIsFocusedAsState` reads — a `Focus` interaction on
 * the `MutableInteractionSource` — is emitted directly. That is the same signal
 * the production components read, so the draw-order regression this guards is
 * the one that actually happened.
 *
 * ## Why `GraphicsMode.NATIVE`
 *
 * Robolectric's default graphics mode returns a blank bitmap, which would make
 * a pixel test pass for the wrong reason. NATIVE actually rasterises.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusRingTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the ring is painted on top of an opaque background`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column {
                    // Control: the same opaque fill, no ring.
                    Box(
                        Modifier
                            .size(120.dp)
                            .background(MaterialTheme.knColors.primaryFill)
                            .testTag(TAG),
                    )
                    // Subject: identical, plus the ring.
                    Box(
                        Modifier
                            .size(120.dp)
                            .background(MaterialTheme.knColors.primaryFill)
                            .testTag(TAG_FOCUSED)
                            .focusRing(focused = true),
                    )
                }
            }
        }

        val plain = compose.onNodeWithTag(TAG).captureToImage()
        val ringed = compose.onNodeWithTag(TAG_FOCUSED).captureToImage()

        // A solid brand fill is brand-coloured everywhere, so "count the brand
        // pixels" cannot separate the two. What the ring changes is the
        // *exact* brand value along the edge: the stroke is `--brand-500`,
        // the fill behind it is the darker `#E02649` used for white-on-brand
        // contrast. So the test counts pixels matching the ring colour exactly
        // in the edge band — zero on the control, non-zero with the ring.
        assertTrue(
            "the unringed fill should contain no --brand-500 pixels, got " +
                "${exactBrandPixelsOnEdge(plain)}",
            exactBrandPixelsOnEdge(plain) < 5,
        )
        assertTrue(
            "the ring was not drawn on top of the fill: found " +
                "${exactBrandPixelsOnEdge(ringed)} --brand-500 pixels on the edge",
            exactBrandPixelsOnEdge(ringed) > 40,
        )
    }

    /**
     * The control for the test above: an unfocused element draws nothing.
     *
     * Asserted on pixels for the same reason the other half is. A regression that
     * made `focusRing` ignore its `focused` flag would put a permanent ring on
 * *every* control in the app, and every positive assertion above would still
 * pass — they only ever look for the ring being present.
     */
    @Test
    fun `an unfocused element draws no ring`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Box(
                    Modifier
                        .size(120.dp)
                        .background(MaterialTheme.knColors.bgCard)
                        .focusRing(focused = false)
                        .testTag(TAG_UNFOCUSED),
                )
            }
        }

        assertTrue(
            "an unfocused element painted a ring: found " +
                "${exactBrandPixelsOnEdge(compose.onNodeWithTag(TAG_UNFOCUSED).captureToImage())} " +
                "--brand-500 pixels on the edge",
            exactBrandPixelsOnEdge(
                compose.onNodeWithTag(TAG_UNFOCUSED).captureToImage(),
            ) < 5,
        )
    }

    /**
     * The production signal path, on a real library component.
     *
     * The two tests above drive `Modifier.focusRing` on a bare `Box`, which
     * proves the modifier draws in the right order — but it never exercises a
     * component actually wiring `collectIsFocusedAsState` into it. A component
     * could drop the `.focusRing(...)` call entirely and every other test here
     * would still pass.
     *
     * So this focuses a real [KnChip] — one of the components that was given the
     * pattern after the review — and asserts the ring reaches the screen. The
     * focus is emitted directly because Robolectric does not deliver a focus
     * request to a Material `Surface`; the interaction emitted is exactly the one
     * `collectIsFocusedAsState` observes in production.
     */
    @Test
    fun `focusing a chip reveals the ring`() {
        val chipFocus = MutableInteractionSource()
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnChip(
                    text = "Tersedia",
                    selected = false,
                    onClick = {},
                    interactionSource = chipFocus,
                    modifier = Modifier
                        .size(160.dp)
                        .testTag(TAG_CHIP),
                )
            }
        }

        val before = exactBrandPixelsOnEdge(compose.onNodeWithTag(TAG_CHIP).captureToImage())

        // The same interaction `collectIsFocusedAsState` observes in production.
        runBlocking { chipFocus.emit(FocusInteraction.Focus()) }
        compose.waitForIdle()

        val after = exactBrandPixelsOnEdge(compose.onNodeWithTag(TAG_CHIP).captureToImage())
        assertTrue(
            "focusing the chip revealed no ring ($before -> $after brand pixels)",
            after > before,
        )
    }

    /**
     * The production signal path: a `Focus` interaction on the
     * `MutableInteractionSource` is what `collectIsFocusedAsState` observes, and
     * therefore what turns the ring on in a real component.
     */
    @Test
    fun `a Focus interaction on the interaction source drives the ring`() {
        val interaction = MutableInteractionSource()
        var focusedSeen = false

        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                val focused = interaction.collectIsFocusedAsState().value
                focusedSeen = focused
                Box(
                    Modifier
                        .size(120.dp)
                        .background(MaterialTheme.knColors.primaryFill)
                        .focusRing(focused = focused)
                        .testTag(TAG_INTERACTION),
                )
            }
        }

        interaction.emitFocus()
        compose.waitForIdle()

        val ringed = compose.onNodeWithTag(TAG_INTERACTION).captureToImage()
        assertTrue(
            "collectIsFocusedAsState did not observe the emitted Focus",
            focusedSeen,
        )
        assertTrue(
            "the ring did not appear when the interaction source reported focus",
            exactBrandPixelsOnEdge(ringed) > 40,
        )
    }

    /**
     * Counts pixels matching `--brand-500` almost exactly, in a band around the
     * node's edge where the ring is drawn. Exact rather than near, because the
     * ring colour and the button fill are both "brand" but are different hexes.
     */
    private fun exactBrandPixelsOnEdge(image: ImageBitmap): Int {
        val w = image.width
        val h = image.height
        val band = 8
        var count = 0
        // readPixels fills a packed ARGB int, not separate channels.
        val argb = IntArray(4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val onEdge = x < band || x >= w - band || y < band || y >= h - band
                if (!onEdge) continue
                image.readPixels(argb, x, y, 1, 1)
                val r = (argb[0] shr 16) and 0xFF
                val g = (argb[0] shr 8) and 0xFF
                val b = argb[0] and 0xFF
                val distance = Math.abs(r - 0xFF) + Math.abs(g - 0x38) + Math.abs(b - 0x5C)
                if (distance <= 6) count++
            }
        }
        return count
    }

    private fun MutableInteractionSource.emitFocus() = runBlocking {
        emit(FocusInteraction.Focus())
    }

    private companion object {
        const val TAG = "plain"
        const val TAG_FOCUSED = "focused"
        const val TAG_INTERACTION = "interaction"
        const val TAG_UNFOCUSED = "unfocused"
        const val TAG_CHIP = "chip"
    }
}