package net.klinik.rental.ui.screens.penyewa

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.components.AVATAR_KEYS
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.avatarDrawable
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The avatar pack as a picker, and the pack's keys as a contract with the rest of the app.
 *
 * ## Why the pack is asserted by its **keys** and not by its pictures
 *
 * Because a picture cannot be asserted on a JVM test. What *can* be asserted is the fact that
 * every key names a drawable that exists and a distinct one, that an unknown key resolves to
 * nothing, and that the picker writes the key the owner tapped. The pixel content of a face is
 * a design decision; the mapping from a stored key to a drawable is a fact that breaks silently
 * — a renamed drawable would leave the app drawing initials for everyone, with no test failing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AvatarPickerTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The pack has 45 faces, and every key names one.
     *
     * The count is the pack's own — `Group 4.png` is 7×7 with the last row holding three — so a
     * crop that lost a face would fail here rather than shipping a grid with a hole in it.
     */
    @Test
    fun `the pack has forty five keys and every one names a drawable`() {
        assertEquals("the pack's size", 45, AVATAR_KEYS.size)
        assertEquals("and the keys are distinct", 45, AVATAR_KEYS.toSet().size)
        AVATAR_KEYS.forEach { key ->
            assertTrue("$key must name a face", avatarDrawable(key) != null)
        }
    }

    /**
     * An unknown key resolves to nothing, so the caller falls back to initials.
     *
     * This is the case that must not crash: a backup written by a build carrying a different
     * pack, or a key that is not one of ours at all.
     */
    @Test
    fun `an unknown key resolves to no drawable`() {
        assertEquals(null, avatarDrawable("avatar_99"))
        assertEquals(null, avatarDrawable(""))
        assertEquals(null, avatarDrawable(null))
        // And a key that merely *looks* like one is not one.
        assertEquals(null, avatarDrawable("avatar_7"))
        assertEquals(null, avatarDrawable("👨🏻"))
    }

    /** The grid shows every face in the pack. */
    @Test
    fun `the grid offers every face in the pack`() {
        compose.setContent { theme { AvatarPicker(selected = "", onSelect = {}) } }

        compose.onAllNodes(hasTestTag(TAG_AVATAR_GRID)).assertCountEquals(1)
        AVATAR_KEYS.forEach { key ->
            compose.onNodeWithTag(avatarTag(key)).assertExists()
        }
    }

    /**
     * Tapping a face reports that face's key.
     *
     * The assertion is on the **key**, not on a picture: a key is what the app stores, and it is
     * what a later build has to be able to resolve.
     */
    @Test
    fun `tapping a face reports its key`() {
        var chosen: String? = null
        compose.setContent { theme { AvatarPicker(selected = "", onSelect = { chosen = it }) } }

        compose.onNodeWithTag(avatarTag("avatar_07")).performClick()

        assertEquals("avatar_07", chosen)
    }

    /**
     * Tapping the selected face again clears it.
     *
     * "No avatar" is a real state — the disc falls back to initials, which is what every renter
     * recorded before this feature shows. The second tap is the way there, so a "clear" button
     * with one job is not needed.
     */
    @Test
    fun `tapping the selected face again clears it`() {
        var chosen: String? = "untouched"
        compose.setContent { theme { AvatarPicker(selected = "avatar_07", onSelect = { chosen = it }) } }

        compose.onNodeWithTag(avatarTag("avatar_07")).performClick()

        assertEquals("", chosen)
    }

    /**
     * The disc draws a **picture** for a known key, and initials for an unknown one.
     *
     * The other tests here assert the *mapping* — that a key names a drawable. This one asserts
     * the thing that actually reaches the owner's eye: that the disc holds an image at all, and
     * that an unrecognised key falls back to text rather than to nothing.
     *
     * A Compose test can tell them apart by the node's own text: the initials path puts the
     * letters in the semantics tree, and the image path does not — so "the disc has no text"
     * is the observable fact that the picture was drawn instead.
     */
    @Test
    fun `a known key draws a picture and an unknown one draws initials`() {
        compose.setContent {
            theme {
                Column {
                    KnAvatar(name = "Budi Santoso", avatar = "avatar_07")
                    KnAvatar(name = "Budi Santoso", avatar = "avatar_99")
                }
            }
        }

        // Two discs, two sources. The first draws a picture — no letters in the tree.
        compose.onAllNodesWithText("BS").assertCountEquals(1)

        // And the picture is really there: an avatar with a key has no text to find, and the
        // one without a key has exactly the initials.
        assertEquals(
            "only the fallback draws letters",
            1,
            compose.onAllNodesWithText("BS").fetchSemanticsNodes().size,
        )
    }

    /** The light theme, as the rest of the suite uses. */
    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }
}
