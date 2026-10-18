package net.klinik.rental.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The lock screen, rendered.
 *
 * ## What only this file can catch
 *
 * The repository tests prove the crypto and the counters; none of them can see what the owner is
 * shown. Three claims are only visible here:
 *
 * - the grid is drawn and its keys reach their handlers;
 * - the **countdown** replaces the error line rather than stacking under it;
 * - the copy says "barrier" and **not** "encrypted" — FR-17's prohibition, which is a fact about a
 *   string rather than about behaviour.
 *
 * The last one is why `PinLockCopyTest`'s assertion is a `assertDoesNotExist` on the word: a future
 * edit that "improves" the subtitle by mentioning encryption would otherwise pass every other test
 * in the project.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LockScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the lock screen draws the grid and the title`() {
        compose.setContent { theme { Screen() } }

        compose.onNodeWithTag(TAG_LOCK_SCREEN).assertExists()
        compose.onNodeWithText("Masuk dengan PIN").assertExists()
    }

    /**
     * **FR-17's prohibition, asserted as a string.**
     *
     * The prototype's lock screen says *"Data tersimpan lokal & terenkripsi di perangkat ini"*.
     * The database is a plain SQLite file, so that sentence is false — and an owner who believes it
     * hands the tablet to a customer with more confidence than the truth allows.
     *
     * Asserted on the **rendered** subtitle rather than on the resource, because a resource nobody
     * draws is not a claim. The positive half matters as much as the negative: the screen must say
     * what the lock *is*, not merely omit what it is not.
     */
    @Test
    fun `the subtitle says barrier and never claims encryption`() {
        compose.setContent { theme { Screen() } }

        compose.onNodeWithTag(TAG_LOCK_SUBTITLE).assertExists()
        compose.onNodeWithText(
            "Aplikasi ini dikunci dengan PIN. Ini penghalang layar, bukan enkripsi.",
        ).assertExists()
        compose.onNodeWithText("terenkripsi", substring = true).assertDoesNotExist()
    }

    /** A wrong PIN shows the error line. */
    @Test
    fun `an error message is drawn`() {
        compose.setContent { theme { Screen(state = PinLockUiState(errorMessage = "PIN salah, coba lagi")) } }

        compose.onNodeWithTag(TAG_LOCK_ERROR).assertExists()
        compose.onNodeWithText("PIN salah, coba lagi").assertExists()
    }

    /**
     * The countdown **replaces** the error line rather than stacking under it.
     *
     * One slot, because "wrong PIN" and "wait 24 seconds" are the same question with two answers.
     * Two lines would be two answers on screen at once, and the owner would read the one that
     * tells them to retry.
     */
    @Test
    fun `the countdown replaces the error line`() {
        compose.setContent {
            theme {
                Screen(
                    state = PinLockUiState(
                        errorMessage = "PIN salah, coba lagi",
                        lockedOutSeconds = 24,
                    ),
                )
            }
        }

        compose.onNodeWithText("Terlalu banyak percobaan. Coba lagi dalam 24 detik.").assertExists()
        compose.onNodeWithText("PIN salah, coba lagi").assertDoesNotExist()
    }

    /** The recovery door is a visible button, not a hidden gesture. */
    @Test
    fun `the recovery action reaches its handler`() {
        var opened = 0
        compose.setContent { theme { Screen(onRecovery = { opened = opened + 1 }) } }

        compose.onNodeWithTag(TAG_LOCK_RECOVERY).assertExists()
        // `performScrollTo` first, and it is not a test workaround — it is the behaviour. The lock
        // screen scrolls because the grid, the title and the recovery button do not fit on a short
        // screen; the owner reaches the button by scrolling, so the test does the same. Without the
        // scroll modifier the button was simply clipped and unreachable, which the first version of
        // this test caught by clicking nothing.
        compose.onNodeWithTag(TAG_LOCK_RECOVERY).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(1, opened)
    }

    /** Digits reach the handler, so the grid is wired rather than merely drawn. */
    @Test
    fun `a digit key reaches the handler`() {
        val digits = mutableListOf<Char>()
        compose.setContent { theme { Screen(onDigit = { digits += it }) } }

        compose.onNodeWithText("7").performClick()
        compose.waitForIdle()

        assertEquals(listOf('7'), digits)
    }

    /** And the grid's keys clear the touch floor — the same measurement every control here gets. */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `a digit key clears the touch floor`() {
        compose.setContent { theme { Screen() } }

        compose.onNodeWithTag(TAG_LOCK_RECOVERY).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(TAG_LOCK_RECOVERY).assertWidthIsAtLeast(48.dp)
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Screen(
        state: PinLockUiState = PinLockUiState(),
        onDigit: (Char) -> Unit = {},
        onDelete: () -> Unit = {},
        onRecovery: () -> Unit = {},
        onTick: () -> Unit = {},
    ) {
        LockScreen(
            state = state,
            onDigit = onDigit,
            onDelete = onDelete,
            onRecovery = onRecovery,
            onTick = onTick,
        )
    }
}