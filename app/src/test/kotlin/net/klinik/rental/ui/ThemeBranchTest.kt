package net.klinik.rental.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.components.KnField
import net.klinik.rental.ui.components.KnSearchBar
import net.klinik.rental.ui.theme.DarkColors
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.LightColors
import net.klinik.rental.ui.theme.minTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The dark-theme branch of [KlinikNetTheme], and the label association on the two
 * text inputs.
 *
 * ## Why this exists
 *
 * `ContrastTest` reads `LightColors` and `DarkColors` as plain data, and almost
 * every rendering test passes `darkTheme = false`. Nothing therefore observed
 * *the theme's own branch*: inverting the `if` in `KlinikNetTheme` would render
 * the entire app light on a dark tablet with the whole suite green. That is one
 * token away from shipping and invisible to every other test.
 *
 * So this goes through the composable and reads the scheme MaterialTheme actually
 * hands the content.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThemeBranchTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `darkTheme true hands the content the dark surfaces`() {
        val seen = mutableListOf<Pair<ULong, ULong>>()
        compose.setContent {
            KlinikNetTheme(darkTheme = true) {
                seen += schemePair()
                Text("x", modifier = Modifier.testTag(TAG))
            }
        }
        compose.waitForIdle()

        assertEquals(
            "the dark branch must hand MaterialTheme the dark surfaces",
            DarkColors.bgPage.value to DarkColors.bgCard.value,
            seen.last(),
        )
    }

    @Test
    fun `darkTheme false hands the content the light surfaces`() {
        val seen = mutableListOf<Pair<ULong, ULong>>()
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                seen += schemePair()
                Text("x", modifier = Modifier.testTag(TAG))
            }
        }
        compose.waitForIdle()

        assertEquals(
            "the light branch must hand MaterialTheme the light surfaces",
            LightColors.bgPage.value to LightColors.bgCard.value,
            seen.last(),
        )
    }

    /**
     * The two branches must genuinely differ, or the tests above could pass with
     * a hard-coded scheme regardless of the flag.
     */
    @Test
    fun `the two themes are genuinely different`() {
        assertTrue(
            "the branches resolved to the same background, so one of them is dead",
            LightColors.bgPage.value != DarkColors.bgPage.value,
        )
        assertTrue(
            "the two themes must not share a card surface",
            LightColors.bgCard.value != DarkColors.bgCard.value,
        )
    }

    /**
     * The 48dp floor the theme provides is the one components apply.
     * `Modifier.minTouchTarget()` reads `MaterialTheme.knAccessibility`, so this
     * is what holds the provider and the modifier in agreement.
     */
    @Test
    fun `the theme provides the 48dp floor the components read`() {
        var seen = -1f
        compose.setContent {
            KlinikNetTheme(darkTheme = false) { seen = MaterialTheme.minTouchTarget.value }
        }
        compose.waitForIdle()
        assertEquals(48f, seen, 0.01f)
    }

    /**
     * A field announces its label.
     *
     * The label `Text` and the `BasicTextField` were sibling nodes, so a screen
     * reader read the name as static prose and then an unnamed edit box — the
     * owner hearing a field with no name at all. The input carries the label in
     * its own semantics now, and this is what holds it there.
     */
    @Test
    fun `a field's input is announced with its label`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    KnField(
                        label = "Tarif harian",
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.testTag(TAG),
                    )
                }
            }
        }

        assertDescribes("Tarif harian", TAG)
    }

    @Test
    fun `a search bar's input is announced with a name`() {
        compose.setContent {
            KlinikNetTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    KnSearchBar(
                        query = "",
                        onQueryChange = {},
                        placeholder = "Cari plat",
                        modifier = Modifier.testTag(TAG_SEARCH),
                    )
                }
            }
        }

        assertDescribes("Cari plat", TAG_SEARCH)
    }

    /** The text input inside [root], and what a screen reader would call it. */
    private fun assertDescribes(expected: String, root: String) {
        val input = compose
            .onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag(root)))
            .fetchSemanticsNodes()
            .single()
        val description = input.config.getOrNull(SemanticsProperties.ContentDescription)
        assertTrue(
            "the edit box inside '$root' must announce '$expected'; it announced " +
                "${description?.toString() ?: "<nothing>"}",
            description.orEmpty().any { it.contains(expected) },
        )
    }

    /**
 * Reads the scheme from inside a composition.
 *
 * `Color.value` is a `ULong`, so the pair is compared as `ULong` rather than
 * widened — an `Int` round-trip would truncate two of the four channels and let
 * a wrong colour pass.
 */
@Composable
private fun schemePair(): Pair<ULong, ULong> =
    MaterialTheme.colorScheme.background.value to
        MaterialTheme.colorScheme.surface.value

    private companion object {
        const val TAG = "probe"
        const val TAG_SEARCH = "search-probe"
    }
}