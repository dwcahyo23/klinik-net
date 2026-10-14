package net.klinik.rental.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.R
import net.klinik.rental.ui.components.DISPLAY_TEST_TAG
import net.klinik.rental.ui.components.KnNumPad
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The NUMPAD_SAVE_ZERO row of the spec's I/O matrix:
 * *"value 0 or negative -> Save rejected, red flash; rejected loudly, never
 * silently defaulted."*
 *
 * [net.klinik.rental.ui.NumPadLogicTest] already covers which values are
 * accepted or rejected. What it cannot cover is the word **loudly** — a
 * rejection nobody can perceive is not a rejection. So this drives the real
 * composable and asks what actually reached the screen.
 *
 * ## Two channels are checked, because one is not enough
 *
 * The flash is a colour change, so it is read from the rendered pixels. That is
 * also the weakness of colour alone: it is invisible to a screen reader and
 * unreliable for a colourblind user. So the message line is checked separately
 * in the semantics tree. A regression that quietly removed the message while
 * leaving the background animation in place would still fail here.
 *
 * ## Why the clock is driven by hand, and in this order
 *
 * The rejection holds for `slowMillis` and then releases, so the assertions are
 * about a transient state. Two properties of `compose.mainClock` matter here,
 * and getting the order wrong is what made an earlier version of this test fail
 * for a reason that had nothing to do with the numpad:
 *
 *  - `autoAdvance = false` freezes the clock but does **not** run a pending
 *    recomposition, so a tap issued immediately after freezing may never have
 *    been applied.
 *  - `waitForIdle()` advances the clock until things settle — which, for a 300ms
 *    hold, means advancing *past* the hold and releasing the flash before the
 *    assertion ever ran.
 *
 * So the tap is issued with the clock free (which is what applies it), the clock
 * is frozen immediately afterwards, and every later wait is an explicit
 * `advanceTimeBy`. Both "the flash is up" and "the flash released" are then
 * deterministic rather than dependent on machine speed.
 *
 * ## Why the expected strings and colours are read, not typed
 *
 * Both the Indonesian message and the danger background come from the shipped
 * resources and tokens. A copy-pasted literal would keep passing after the real
 * value changed — which is precisely when the test is needed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NumPadRejectTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id)

    private val saveLabel: String get() = string(R.string.numpad_key_save)
    private val rejectedMessage: String get() = string(R.string.numpad_rejected_zero)
    private val displayLabel: String get() = string(R.string.numpad_display_label)

    /** Packed ARGB, from the same token the display paints with. */
    private val dangerBackground: Int get() = toArgb(LightColors.semanticBg.danger)
    private val sunkenBackground: Int get() = toArgb(LightColors.bgSunken)

    private fun toArgb(c: Color): Int = android.graphics.Color.argb(
        (c.alpha * 255).toInt(),
        (c.red * 255).toInt(),
        (c.green * 255).toInt(),
        (c.blue * 255).toInt(),
    )

    /**
     * Zero is rejected: nothing is handed to the caller, and the rejection is
     * both coloured and worded.
     */
    @Test
    fun `saving zero is rejected and shown on screen`() {
        var saved: Long? = null
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                val value = remember { mutableStateOf("0") }
                KnNumPad(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onSave = { saved = it },
                )
            }
        }

        rejectSave()

        assertEquals("a zero amount must never reach onSave", null, saved)
        compose.onNodeWithText(rejectedMessage).assertIsDisplayed()
        assertEquals(
            "the display background should be the danger companion after a rejection",
            dangerBackground,
            displayBackground(),
        )
    }

    /** An empty display is rejected for the same reason zero is. */
    @Test
    fun `saving an empty display is rejected and shown on screen`() {
        var saved: Long? = null
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                val value = remember { mutableStateOf("") }
                KnNumPad(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onSave = { saved = it },
                )
            }
        }

        rejectSave()

        assertEquals(null, saved)
        compose.onNodeWithText(rejectedMessage).assertIsDisplayed()
        assertEquals(dangerBackground, displayBackground())
    }

    /**
     * The control for the two tests above: before any rejection the message line
     * holds its normal label and the background holds its normal token.
     *
     * Without this, `assertIsDisplayed()` on the message could be passing
     * because the message is *always* on screen.
     */
    @Test
    fun `the normal state shows the label and not the rejection`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnNumPad(value = "250000", onValueChange = {}, onSave = {})
            }
        }

        compose.onNodeWithText(rejectedMessage).assertDoesNotExist()
        compose.onNodeWithText(displayLabel).assertIsDisplayed()
        assertEquals(
            "an untouched display should keep its normal sunken background",
            sunkenBackground,
            displayBackground(),
        )
    }

    /**
     * The accepted path, so the rejection tests cannot pass by the numpad simply
     * never accepting anything.
     */
    @Test
    fun `a positive amount is accepted and not flagged`() {
        var saved: Long? = null
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                KnNumPad(value = "250000", onValueChange = {}, onSave = { saved = it })
            }
        }

        rejectSave()

        assertEquals(250_000L, saved)
        compose.onNodeWithText(rejectedMessage).assertDoesNotExist()
        assertEquals(sunkenBackground, displayBackground())
    }

    /**
     * The flash is transient by design, and this proves both halves: it is up
     * during the hold, and it releases on its own afterwards. A latching flash
     * would leave the owner permanently looking at a red amount.
     */
    @Test
    fun `the rejection flash clears on its own`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                val value = remember { mutableStateOf("0") }
                KnNumPad(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onSave = {},
                )
            }
        }

        rejectSave()
        assertEquals(
            "the flash should be up while the hold lasts",
            dangerBackground,
            displayBackground(),
        )

        // Well past the hold, still on the frozen clock.
        compose.mainClock.advanceTimeBy(REJECT_HOLD_MILLIS * HOLD_FRAMES * 2)
        assertEquals(
            "the flash must release rather than latch",
            sunkenBackground,
            displayBackground(),
        )
        compose.onNodeWithText(rejectedMessage).assertDoesNotExist()
    }

    /**
     * The digit-tap path, and the internal state holder it depends on.
     *
     * `NumPadLogicTest` covers the pure rules and `AccessibilityTest` measures
     * bounds with `onValueChange = {}`, so nothing exercised the composable's own
     * digit path. That path holds an internal `digits` state so two taps landing
     * before recomposition compose on each other rather than on the same stale
     * string — reverting to reading the `value` parameter would drop a digit and
     * leave every other test green.
     *
     * Asserted on the composed display, which is what the owner sees.
     */
    @Test
    fun `tapping digits in sequence composes the display`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                val value = remember { mutableStateOf("") }
                KnNumPad(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onSave = {},
                )
            }
        }

        listOf("2", "5", "0").forEach { digit ->
            compose.onNodeWithText(digit).performClick()
        }
        compose.waitForIdle()

        // The `Rp` prefix is its own styled span, so the figure node holds the
        // grouped digits alone.
        compose.onNodeWithText("250").assertIsDisplayed()
    }

    /** Taps Save and freezes the clock, leaving the rejection mid-hold. */
    private fun rejectSave() {
        compose.onNodeWithText(saveLabel).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        // Past `fastMillis` (the background animation) and well short of
        // `slowMillis` (the hold), so the flash is painted and the message has
        // swapped in but nothing has expired yet.
        compose.mainClock.advanceTimeBy(FLASH_SETTLE_MILLIS)
        compose.mainClock.advanceTimeByFrame()
    }

    /**
     * The display panel's background, sampled from the panel's own pixels.
     *
     * Read as the mode over an inset region rather than from one fixed point: at
     * this window width the grid below the panel pushes it to a height where a
     * single sampled point can land on the panel's border or on the glyph beside
     * it, and a probe that reads a border reports the border's colour no matter
     * what the panel is doing.
     */
    private fun displayBackground(): Int {
        val image = compose.onNodeWithTag(DISPLAY_TEST_TAG).captureToImage()
        val counts = HashMap<Int, Int>()
        val inset = 6
        for (y in inset until image.height - inset) {
            for (x in inset until image.width / 2) {
                val value = pixel(image, x, y)
                counts[value] = (counts[value] ?: 0) + 1
            }
        }
        return counts.maxByOrNull { it.value }?.key
            ?: error("no pixels in the display band")
    }

    private fun pixel(image: ImageBitmap, x: Int, y: Int): Int {
        val argb = IntArray(4)
        image.readPixels(argb, x.coerceIn(0, image.width - 1), y.coerceIn(0, image.height - 1), 1, 1)
        // Force opaque: the rasterised bitmap carries the panel's alpha
        // separately, and comparing raw ARGB would report the compositing result
        // rather than the colour the token names.
        return argb[0] or (0xFF shl 24)
    }

    private companion object {
        /** Between `fastMillis` (140) and `slowMillis` (300) — see [rejectSave]. */
        const val FLASH_SETTLE_MILLIS = 180L

        /** The hold itself, from `KnMotion.slowMillis`; advanced well past. */
        const val REJECT_HOLD_MILLIS = 300L

        /** Mirrors REJECT_HOLD_FRAMES in NumPad.kt. */
        const val HOLD_FRAMES = 4
    }
}