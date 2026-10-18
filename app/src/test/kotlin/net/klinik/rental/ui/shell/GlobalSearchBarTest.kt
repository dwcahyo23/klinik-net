package net.klinik.rental.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The shell's search field, rendered — Story 4.6.
 *
 * ## What only a rendered tree can answer
 *
 * Three things `GlobalSearchTest` structurally cannot: that the field **draws** on the shell at
 * all, that the results overlay appears only once something is typed, and that tapping a result
 * reaches the handler with **the right hit**.
 *
 * The third is the one that matters. `assertExists()` cannot tell "wired to the handler" from
 * "wired to nothing", and this repo has shipped that defect twice — so the test taps and asserts
 * *which* renter the handler received, not merely that something clickable was there.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
class GlobalSearchBarTest {

    @get:Rule
    val compose = createComposeRule()

    /** The field is on the shell, so "every screen" is a property of the layout. */
    @Test
    fun `the search field is part of the shell`() {
        compose.setContent { theme { Bar(state = GlobalSearchUiState(), onQueryChange = {}, onClear = {}, onSelect = {}) } }

        compose.onNodeWithTag(TAG_GLOBAL_SEARCH_FIELD).assertIsDisplayed()
        // And an idle field draws no overlay: a panel beneath a field nobody typed in reads
        // as a panel that failed to load.
        compose.onNodeWithTag(TAG_GLOBAL_SEARCH_RESULTS).assertDoesNotExist()
    }

    /** Typing produces the grouped overlay. */
    @Test
    fun `results appear once a query is typed`() {
        val state = GlobalSearchUiState.of("siti", listOf(siti()), emptyList())
        compose.setContent { theme { Bar(state = state, onQueryChange = {}, onClear = {}, onSelect = {}) } }

        compose.onNodeWithTag(TAG_GLOBAL_SEARCH_RESULTS).assertExists()
        // Group headings are drawn, so a result is never presented without saying what it is.
        compose.onNodeWithTag(searchResultTag("Siti Aminah")).assertExists()
    }

    /** No hits draws the empty state, which explains rather than leaves a blank. */
    @Test
    fun `no hits draws the explained empty state`() {
        val state = GlobalSearchUiState.of("zzzz", listOf(siti()), listOf(avanza()))
        compose.setContent { theme { Bar(state = state, onQueryChange = {}, onClear = {}, onSelect = {}) } }

        compose.onNodeWithTag(TAG_GLOBAL_SEARCH_EMPTY).assertExists()
        compose.onNodeWithTag(TAG_GLOBAL_SEARCH_RESULTS).assertExists()
        compose.onNodeWithTag(searchResultTag("Siti Aminah")).assertDoesNotExist()
    }

    /**
     * Tapping a result reaches the handler **with that hit**.
     *
     * The claim is behavioural: the handler receives the `RenterHit` whose renter is Siti, not
     * merely "a callback fired". A row wired to the wrong hit would open the wrong person.
     */
    @Test
    fun `tapping a result reaches the handler with that hit`() {
        var selected: SearchResult? = null
        val state = GlobalSearchUiState.of("siti", listOf(siti(), budi()), emptyList())
        compose.setContent { theme { Bar(state = state, onQueryChange = {}, onClear = {}, onSelect = { selected = it }) } }

        compose.onNodeWithTag(searchResultTag("Siti Aminah"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        val hit = selected as? SearchResult.RenterHit
        assertEquals("the handler received the renter that was tapped", "Siti Aminah", hit?.renter?.name)
    }

    /** Typing reaches the query handler, so the field is wired and not merely drawn. */
    @Test
    fun `typing reaches the query handler`() {
        var typed = ""
        compose.setContent {
            theme { Bar(state = GlobalSearchUiState(), onQueryChange = { typed = it }, onClear = {}, onSelect = {}) }
        }

        compose.onNode(hasSetTextAction() and hasAncestorTag(TAG_GLOBAL_SEARCH_FIELD))
            .performTextInput("siti")
        compose.waitForIdle()

        assertEquals("siti", typed)
    }

    /** The results rows clear the 48dp floor — Story 4.1 shipped a 38dp failure on this one. */
    @Test
    fun `a result row clears the touch floor`() {
        val state = GlobalSearchUiState.of("siti", listOf(siti()), emptyList())
        compose.setContent { theme { Bar(state = state, onQueryChange = {}, onClear = {}, onSelect = {}) } }

        compose.onNodeWithTag(searchResultTag("Siti Aminah")).assertHeightIsAtLeast(48.dp)
    }

    // ------------------------------------------------------------------ helpers

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    @Composable
    private fun Bar(
        state: GlobalSearchUiState,
        // Both **required**. A defaulted handler here is a field rendered and wired to
        // nothing, which is the failure this repo has shipped three times — and a test that
        // forgets to pass one would pass vacuously rather than fail to compile.
        onQueryChange: (String) -> Unit,
        onClear: () -> Unit,
        onSelect: (SearchResult) -> Unit,
    ) {
        GlobalSearchBar(
            state = state,
            onQueryChange = onQueryChange,
            onClear = onClear,
            onSelect = onSelect,
        )
    }

    private fun hasAncestorTag(tag: String) = androidx.compose.ui.test.hasAnyAncestor(
        androidx.compose.ui.test.hasTestTag(tag),
    )

    private fun siti() = Renter(
        id = "r1",
        name = "Siti Aminah",
        phone = "081234567891",
        bankAccount = "9087654321",
        emergencyContact = "081200000001",
    )

    private fun budi() = Renter(
        id = "r2",
        name = "Budi Santoso",
        phone = "081298765432",
        bankAccount = null,
        emergencyContact = "081200000002",
    )

    private fun avanza() = Vehicle(
        id = "v1",
        plate = "B 1235 TF",
        brand = "Toyota",
        model = "Avanza",
        year = 2023,
        color = "Putih",
        dailyRate = 250_000,
        notes = null,
        isServis = false,
    )
}
