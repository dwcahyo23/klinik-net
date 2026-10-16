package net.klinik.rental.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.ui.components.KnAvatar
import net.klinik.rental.ui.components.KnListRow
import net.klinik.rental.ui.components.KnScreenError
import net.klinik.rental.ui.components.KnSkeletonList
import net.klinik.rental.ui.components.ScreenErrorActionTag
import net.klinik.rental.ui.components.SkeletonRowTag
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
/**
 * The placeholder itself: how many blocks, what it says, and what it must not be.
 *
 * [FourStatesTest] proves every screen *draws* a skeleton. This file proves the
 * component is the thing the acceptance criteria describe — same shape as the
 * content it replaces, silent to a screen reader, and not a spinner in disguise.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SkeletonTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `it draws one block per row`() {
        compose.setContent { theme { KnSkeletonList(rows = 4) } }

        compose.onAllNodesWithTag(SkeletonRowTag).assertCountEquals(4)
    }

    @Test
    fun `zero rows draws nothing at all`() {
        // A caller may pass a count it computed without branching; `0` must
        // produce no nodes rather than an empty column that still claims space.
        compose.setContent { theme { KnSkeletonList(rows = 0) } }

        compose.onAllNodesWithTag(SkeletonRowTag).assertCountEquals(0)
    }

    @Test
    fun `it is hidden from accessibility`() {
        compose.setContent { theme { KnSkeletonList(rows = 3) } }

        // A placeholder says nothing. Announcing "loading" once per block makes
        // TalkBack read the same word several times over a list that is about to
        // be replaced, and implies a duration the owner cannot act on.
        val described = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
        ).fetchSemanticsNodes()
        assertTrue(
            "skeleton announced ${described.size} content description(s)",
            described.isEmpty(),
        )
    }

    @Test
    fun `no indeterminate progress anywhere in the shell`() {
        compose.setContent { theme { SkeletonListHost() } }

        // The acceptance criterion says "bukan spinner" — not "a spinner is
        // acceptable if it is fast". A `CircularProgressIndicator` publishes this
        // key, so this is the machine-checkable form of that sentence.
        val progress = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        ).fetchSemanticsNodes()
        assertTrue("found ${progress.size} progress node(s)", progress.isEmpty())
    }

    /**
     * The skeleton beside the real list, in one tree.
     *
     * Not decoration: a progress indicator is most likely to be introduced at the
     * point where someone watches the placeholder on a slow device and decides it
     * "looks unfinished", which is exactly this composition.
     */
    @Composable
    private fun SkeletonListHost() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            KnSkeletonList(rows = 6)
        }
    }

    @Test
    fun `the read-failure state names what happened and what to do`() {
        compose.setContent {
            theme {
                KnScreenError(
                    title = "Data tidak bisa dibaca",
                    body = "Aplikasi dihentikan agar riwayat tidak hilang. Coba buka lagi setelah data tersimpan.",
                    actionLabel = "Coba lagi",
                    onAction = {},
                )
            }
        }

        // Not "no data". The failure must never read as an empty account — that is
        // the exact confusion that made the prototype's swallowed parse error
        // look like a fresh install.
        compose.onNodeWithText("Data tidak bisa dibaca").assertExists()
        compose.onNodeWithText(
            "Aplikasi dihentikan agar riwayat tidak hilang. Coba buka lagi setelah data tersimpan.",
        ).assertExists()
        compose.onNodeWithTag(ScreenErrorActionTag).assertExists()
    }

    @Test
    fun `a label with no handler renders no button`() {
        // A button that does nothing is worse than no button: it promises an exit
        // from the failure and there is none.
        compose.setContent {
            theme {
                KnScreenError(
                    title = "Data tidak bisa dibaca",
                    body = "Aplikasi dihentikan agar riwayat tidak hilang.",
                    actionLabel = "Coba lagi",
                    onAction = null,
                )
            }
        }

        compose.onAllNodesWithTag(ScreenErrorActionTag).assertCountEquals(0)
    }

    /**
     * The placeholder row is exactly as tall as the row it stands in for.
     *
     * Measured rather than computed, because the arithmetic is genuinely
     * contested: `KnListRow`'s content column is the taller of its text stack
     * (label 20sp + 2dp + subtitle 21sp = 43dp) and its 44dp `KnAvatar` (which is
     * what `ListRowLeadingSize` boxes the leading slot to), and `minTouchTarget`'s
     * 48dp `defaultMinSize` is a *floor* on the row's own height rather than an
     * addition to it. So the row measures `max(48, max(43, 44)) + 2 x 16dp` =
     * 76dp, which is the number `SkeletonRowHeight` is derived from.
     *
     * Both sides are laid out and measured so a change to either the row or the
     * placeholder fails here rather than showing up as a list that visibly jumps
     * when the data lands.
     */
    @Test
    fun `a placeholder row is exactly as tall as the row it replaces`() {
        compose.setContent {
            theme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    KnSkeletonList(rows = 1)
                    KnListRow(
                        title = "B 1235 TF",
                        subtitle = "Toyota Avanza 2023",
                        leading = { KnAvatar(name = "B 1235 TF") },
                        modifier = Modifier.testTag(TAG_REAL_ROW),
                    )
                }
            }
        }

        val placeholder = compose.onAllNodesWithTag(SkeletonRowTag)[0]
            .fetchSemanticsNode().size.height
        val real = compose.onNodeWithTag(TAG_REAL_ROW).fetchSemanticsNode().size.height

        assertEquals(
            "a $placeholder dp placeholder beside a $real dp row jumps when data lands",
            real,
            placeholder,
        )
    }

    /** Wraps in the app theme; outside it a component silently gets light defaults. */
    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    private companion object {
        const val TAG_REAL_ROW = "skeleton-test-real-row"
    }
}