package net.klinik.rental.ui.shell

import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.matchesSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Story 4.6's matrix, against the pure search rule and the grouping.
 *
 * ## Why this runs without a device
 *
 * [GlobalSearchUiState.of] and the two `matchesSearch` predicates are pure, so every row here —
 * partial matching, case, grouping, the empty state, and the fields that are deliberately *not*
 * searched — is assertable on the JVM. A Compose test would have to render the shell, type into
 * a field and read the overlay back to prove the same thing, and would fail for layout reasons
 * rather than logic ones.
 *
 * ## Why the excluded-field rows are here rather than in a comment
 *
 * The epic records, deliberately, that notes, transaction references and deposit numbers are not
 * searched. A rule stated only in prose is one a later refactor can widen without noticing.
 */
class GlobalSearchTest {

    // ----------------------------------------------------------------- matching

    /** `SEARCH_PARTIAL`: a fragment of a name finds the renter, and case does not matter. */
    @Test
    fun `a partial query finds a renter regardless of case`() {
        val state = GlobalSearchUiState.of(
            query = "siti",
            renters = listOf(siti(), budi()),
            vehicles = emptyList(),
        )

        assertEquals("one hit", 1, state.renters.size)
        assertEquals("and it is the right person", "Siti Aminah", state.renters.single().label)
    }

    /** `SEARCH_PLATE`: a fragment of a plate finds the car. */
    @Test
    fun `a plate fragment finds the vehicle`() {
        val state = GlobalSearchUiState.of(
            query = "1235",
            renters = emptyList(),
            vehicles = listOf(avanza()),
        )

        assertEquals(1, state.vehicles.size)
        assertEquals("B 1235 TF", state.vehicles.single().label)
    }

    /**
     * The vehicle rule keeps every field it matched **before** this story.
     *
     * The AC names plates, and narrowing to plates alone would be a silent regression on a
     * screen that already matches a car by "Avanza". Asserted on brand and model so the narrowing
     * cannot happen quietly.
     */
    @Test
    fun `a vehicle still matches on brand model and colour`() {
        val car = avanza()
        assertTrue("brand", car.matchesSearch("toyota"))
        assertTrue("model", car.matchesSearch("avanza"))
        assertTrue("colour", car.matchesSearch("putih"))
    }

    /** `SEARCH_GROUPED`: both kinds, each in its own list. */
    @Test
    fun `matches in both kinds are grouped rather than interleaved`() {
        val state = GlobalSearchUiState.of(
            query = "a",
            renters = listOf(siti(), budi()),
            vehicles = listOf(avanza()),
        )

        assertEquals("the renters group", 2, state.renters.size)
        assertEquals("the vehicles group", 1, state.vehicles.size)
        assertEquals("and the flat view keeps them in group order", 3, state.all.size)
    }

    /** `SEARCH_NONE`: a query with no hits reports the empty state rather than nothing. */
    @Test
    fun `no hits reports an explained empty state`() {
        val state = GlobalSearchUiState.of(
            query = "zzzz",
            renters = listOf(siti()),
            vehicles = listOf(avanza()),
        )

        assertTrue("the field is active", state.isActive)
        assertTrue("and there is nothing to show", state.isEmpty)
    }

    /**
     * A blank field is **not** an empty state.
     *
     * The distinction the AC's "empty state" clause risks losing: nothing typed is an idle
     * field, and drawing "tidak ada yang cocok" over it would accuse the owner of a search they
     * never ran.
     */
    @Test
    fun `a blank query is idle rather than an empty result`() {
        val state = GlobalSearchUiState.of(query = "   ", renters = listOf(siti()), vehicles = emptyList())

        assertFalse("not active", state.isActive)
        assertFalse("and not an empty result", state.isEmpty)
        assertTrue("nothing rendered", state.all.isEmpty())
    }

    /**
     * `SEARCH_NOTES_EXCLUDED`: a query matching only a deposit's note or reference finds nothing.
     *
     * The rule the epic records deliberately. The assertion is that the searchable fields are
     * exactly a renter's name/phone/account and a vehicle's plate/brand/model/colour — a note or
     * a transaction reference reaches neither predicate, because neither type carries one.
     */
    @Test
    fun `notes and references are not searchable`() {
        val state = GlobalSearchUiState.of(
            query = "transfer bca 9921",
            renters = listOf(siti()),
            vehicles = listOf(avanza()),
        )

        assertTrue("neither kind matches", state.all.isEmpty())
        assertTrue("and the empty state explains it", state.isEmpty)
    }

    /** A renter is still found by phone and account — the fields the list has always matched. */
    @Test
    fun `a renter matches on phone and bank account too`() {
        val renter = siti()
        assertTrue("phone", renter.matchesSearch("0812"))
        assertTrue("account", renter.matchesSearch("9087"))
    }

    /** Every hit carries the route that owns it, so the shell never guesses a destination. */
    @Test
    fun `each hit carries its owning route`() {
        val state = GlobalSearchUiState.of(
            query = "a",
            renters = listOf(siti()),
            vehicles = listOf(avanza()),
        )

        assertEquals(Destination.Penyewa.route, state.renters.single().route)
        assertEquals(Destination.Kendaraan.route, state.vehicles.single().route)
    }

    // ----------------------------------------------------------------- fixtures

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
