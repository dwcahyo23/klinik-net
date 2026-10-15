package net.klinik.rental.ui.screens.penyewa

import net.klinik.rental.domain.model.Renter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `SEARCH_RENTER` matrix row, against the pure function.
 *
 * ## Why this runs without a device
 *
 * [matchesRenterQuery] and [filterByQuery] are top-level pure functions rather
 * than methods on the ViewModel. That is what makes the search AC testable on the
 * JVM: a Compose test would have to render the screen, drive a text field and
 * read the list back to assert the same thing, and it would fail for layout
 * reasons rather than logic ones.
 *
 * A plain JUnit test — no Robolectric, no Compose — because nothing here touches
 * Android. The one thing it cannot prove is that the *database* feeds the list in
 * that order; `RenterOrderTest` covers that half.
 */
class PenyewaFilterTest {

    // ------------------------------------------------------------ SEARCH_RENTER

    /**
     * The matrix row: a substring of the name, the phone, or the bank account
     * matches.
     *
     * All three fields are covered, because the spec names all three and a search
     * that quietly only matched one would satisfy "the search bar works" while
     * failing the row that says what it searches.
     */
    @Test
    fun `search matches a substring of name phone or bank account`() {
        val budi = budi().renter

        assertTrue("name", matchesRenterQuery(budi, "Santoso"))
        assertTrue("phone", matchesRenterQuery(budi, "0812"))
        assertTrue("bank account", matchesRenterQuery(budi, "1234567"))
    }

    /**
     * The query narrows the *list*, and the rows that do not match are gone.
     *
     * Asserted as "the non-matching row is absent" rather than as a count,
     * because a count would pass just as happily if the filter returned two copies
     * of the right row.
     */
    @Test
    fun `search narrows the list to the matching rows`() {
        val rows = listOf(budi(), siti())

        assertEquals(
            listOf("Budi Santoso"),
            filterByQuery(rows, "Budi").map { it.renter.name },
        )
        assertEquals(
            listOf("Siti Aminah"),
            filterByQuery(rows, "Aminah").map { it.renter.name },
        )
    }

    /**
     * Case-insensitive, because the owner types on a phone keyboard with no shift
     * key within reach — a search that only matched the stored capitalisation
     * would be useless for exactly the input it exists for.
     *
     * The *stored* name keeps its capitalisation: this lowercases the comparison,
     * not the data. Uppercasing a name would degrade every label, list row and
     * WhatsApp message built from it.
     */
    @Test
    fun `search is case-insensitive`() {
        val rows = listOf(budi())

        assertEquals(1, filterByQuery(rows, "budi").size)
        assertEquals(1, filterByQuery(rows, "BUDI").size)
        assertEquals(1, filterByQuery(rows, "santoso").size)
        assertEquals("Budi Santoso", rows.single().renter.name)
    }

    /**
     * A blank or whitespace-only query matches everything, so the list starts
     * whole rather than empty.
     */
    @Test
    fun `a blank query matches every renter`() {
        val rows = listOf(budi(), siti())

        assertEquals(2, filterByQuery(rows, "").size)
        assertEquals(2, filterByQuery(rows, "   ").size)
        assertTrue(matchesRenterQuery(budi().renter, ""))
    }

    /**
     * A query that matches nothing returns nothing — and that emptiness is the
     * *empty-search* state, not an error. Asserted here as the fact the state
     * distinction rests on; the rendering of the two states is
     * `PenyewaScreenTest`'s job.
     */
    @Test
    fun `a query matching nothing returns an empty list`() {
        val rows = listOf(budi(), siti())

        assertTrue(filterByQuery(rows, "tidak ada").isEmpty())
    }

    /**
     * A renter with no bank account is searchable by the two fields they do have
     * and does not throw on the third.
     *
     * The `null` account is the case a naive `row.bankAccount.contains(needle)`
     * would crash on, and a crash inside a search-as-you-type keystroke is a
     * frozen screen — so the `?.` in the production code is load-bearing, and this
     * is what pins it.
     */
    @Test
    fun `a renter without a bank account is still searchable`() {
        val cash = siti().renter

        assertTrue(matchesRenterQuery(cash, "Siti"))
        assertTrue(matchesRenterQuery(cash, "0812"))
        // A bank-account query cannot match them, and says so by not crashing.
        assertEquals(false, matchesRenterQuery(cash, "BCA"))
    }

    /**
     * The emergency contact is deliberately *not* a search field.
     *
     * An owner looking up a renter is looking for the person, and matching an
     * emergency number belongs to the emergency. A search that returned Budi
     * because Siti's emergency number happened to contain the typed digits would
     * be answering a question nobody asked — and where a family shares one number
     * across two renters, a false match is a real possibility rather than a
     * thought experiment.
     */
    @Test
    fun `the emergency contact is not a search field`() {
        // A number that is nobody's phone and only Budi's emergency contact.
        val emergencyOnly = "081200000000"

        val rows = listOf(
            RenterRow(
                Renter(
                    id = "r1",
                    name = "Budi Santoso",
                    phone = "08123456789",
                    bankAccount = null,
                    emergencyContact = emergencyOnly,
                ),
            ),
        )

        assertTrue(
            "the emergency contact must not be searchable",
            filterByQuery(rows, emergencyOnly).isEmpty(),
        )
    }

    /**
     * Filtering never changes the stored data — it narrows a view of it.
     *
     * Asserted because a filter that mutated the list (say, by dropping the
     * non-matching rows) would pass every narrowing assertion above and then lose
     * the renter when the owner cleared the search.
     */
    @Test
    fun `filtering does not change the rows it was given`() {
        val rows = listOf(budi(), siti())
        val before = rows.map { it.renter.id }

        filterByQuery(rows, "Budi")

        assertEquals(before, rows.map { it.renter.id })
    }

    /**
     * The matrix row, routed through [filterByQuery] — the function the screen
     * actually calls.
     *
     * `visibleRows` is `filterByQuery(rows, query)`, so an assertion made against
     * `matchesRenterQuery` alone proves the rule in a function no screen runs.
     * Testing all three fields through the production entry point is what makes
     * "search narrows the list by name, phone or bank account" a claim about the
     * app rather than about a helper.
     */
    @Test
    fun `filterByQuery matches on name phone and bank account`() {
        val rows = listOf(budi(), siti())

        assertEquals(
            listOf("Budi Santoso"),
            filterByQuery(rows, "Santoso").map { it.renter.name },
        )
        assertEquals(
            listOf("Budi Santoso"),
            filterByQuery(rows, "08123456789").map { it.renter.name },
        )
        assertEquals(
            listOf("Budi Santoso"),
            filterByQuery(rows, "1234567890").map { it.renter.name },
        )
    }

    // ------------------------------------------------------ the two emptinesses

    /**
     * The distinction the screen renders: an empty *store* and an empty *search*
     * are different facts, and the UI state must be able to tell them apart.
     *
     * This is the epic context's rule — an error state may never look like a new
     * account — stated as a property of the state rather than of the pixels.
     */
    @Test
    fun `an empty store and an empty search are distinguishable`() {
        val rows = listOf(budi(), siti())

        val emptyStore = PenyewaUiState()
        assertTrue(emptyStore.isEmptyList)
        assertEquals(false, emptyStore.isEmptySearch)

        val emptySearch = PenyewaUiState(rows = rows, query = "tidak ada")
        assertEquals(false, emptySearch.isEmptyList)
        assertTrue(emptySearch.isEmptySearch)
    }

    /** And the row count on the top bar is the *stored* count, not the visible one. */
    @Test
    fun `the count is the stored count not the filtered one`() {
        val state = PenyewaUiState(rows = listOf(budi(), siti()), query = "Budi")

        assertEquals(2, state.renterCount)
        assertEquals(1, state.visibleRows.size)
    }

    // ------------------------------------------------------------------ helpers

    private fun budi() = RenterRow(
        Renter(
            id = "r1",
            name = "Budi Santoso",
            phone = "08123456789",
            bankAccount = "BCA 1234567890",
            emergencyContact = "08129876543",
        ),
    )

    private fun siti() = RenterRow(
        Renter(
            id = "r2",
            name = "Siti Aminah",
            phone = "08129876543",
            bankAccount = null,
            emergencyContact = "08123456789",
        ),
    )
}