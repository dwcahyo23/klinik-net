package net.klinik.rental.ui.screens.kendaraan

import net.klinik.rental.domain.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `SEARCH_QUERY` and `STATUS_FILTER` matrix rows, against the pure
 * functions.
 *
 * ## Why these run without a device
 *
 * [matchesQuery], [filterByStatus] and [countByStatus] are top-level pure
 * functions rather than methods on the ViewModel. That is what makes the search
 * and filter ACs testable on the JVM: a Compose test would have to render the
 * screen, drive a text field and read the list back to assert the same thing,
 * and it would fail for layout reasons rather than logic ones.
 *
 * A plain JUnit test — no Robolectric, no Compose — because nothing here touches
 * Android.
 */
class VehicleFilterTest {

    // ------------------------------------------------------------ SEARCH_QUERY

    /**
     * The matrix row: `"agya"`, `"AGYA"` and `"b 123"` all find matching rows.
     *
     * The owner types on a phone keyboard, so a search that only matched the
     * stored capitalisation would be useless for the exact input it exists for.
     */
    @Test
    fun `search is case-insensitive across the four fields`() {
        val rows = listOf(agya("B 1235 TF"), brio("B 9876 AB"))

        assertEquals(
            listOf("B 1235 TF"),
            matchesQuery(rows, "agya").map { it.vehicle.plate },
        )
        assertEquals(
            listOf("B 1235 TF"),
            matchesQuery(rows, "AGYA").map { it.vehicle.plate },
        )
        assertEquals(
            listOf("B 1235 TF"),
            matchesQuery(rows, "b 123").map { it.vehicle.plate },
        )
    }

    /** Matches on plate, brand, model and colour — the prototype's four fields. */
    @Test
    fun `search matches plate brand model and colour`() {
        val rows = listOf(agya("B 1235 TF"), brio("B 9876 AB"))
        assertEquals(listOf("B 1235 TF"), matchesQuery(rows, "toyota").map { it.vehicle.plate })
        assertEquals(listOf("B 1235 TF"), matchesQuery(rows, "agya").map { it.vehicle.plate })
        assertEquals(listOf("B 9876 AB"), matchesQuery(rows, "honda").map { it.vehicle.plate })
        assertEquals(listOf("B 9876 AB"), matchesQuery(rows, "brio").map { it.vehicle.plate })
        assertEquals(listOf("B 1235 TF"), matchesQuery(rows, "hitam").map { it.vehicle.plate })
    }

    /**
     * A blank query matches everything, so the list starts whole rather than
     * empty — an empty list on first render would read as "no data stored".
     */
    @Test
    fun `a blank query matches every row`() {
        val rows = listOf(agya("B 1235 TF"), brio("B 9876 AB"))
        assertEquals(rows, matchesQuery(rows, ""))
        assertEquals(rows, matchesQuery(rows, "   "))
    }

    @Test
    fun `a query matching nothing returns no rows`() {
        assertTrue(matchesQuery(listOf(agya("B 1235 TF")), "zzzz").isEmpty())
    }

    /**
     * Year, notes and rate are deliberately *not* searched — an owner looking
     * for "Agya" is not looking for a document containing the word "nilai", and
     * a search matching every note ever written would return the whole fleet.
     */
    @Test
    fun `year and rate are not searched`() {
        val rows = listOf(agya("B 1235 TF"))
        assertTrue(matchesQuery(rows, "2023").isEmpty())
        assertTrue(matchesQuery(rows, "250000").isEmpty())
        assertTrue(matchesQuery(rows, "250.000").isEmpty())
    }

    /** A vehicle with no colour recorded cannot throw when colour is searched. */
    @Test
    fun `a null colour does not break the search`() {
        val rows = listOf(agya("B 1235 TF").copy(vehicle = agya("B 1235 TF").vehicle.copy(color = null)))
        assertEquals(1, matchesQuery(rows, "b 1235").size)
        assertTrue(matchesQuery(rows, "merah").isEmpty())
    }

    // ----------------------------------------------------------- STATUS_FILTER

    @Test
    fun `the semua filter passes every row through`() {
        val rows = listOf(tersedia("B 1 AA"), tersewa("B 2 AA"), servis("B 3 AA"))
        assertEquals(3, filterByStatus(rows, VehicleFilter.Semua).size)
    }

    @Test
    fun `each status filter narrows to only that status`() {
        val rows = listOf(
            tersedia("B 1 AA"),
            tersedia("B 2 AA"),
            tersewa("B 3 AA"),
            servis("B 4 AA"),
        )
        assertEquals(
            listOf("B 1 AA", "B 2 AA"),
            filterByStatus(rows, VehicleFilter.Tersedia).map { it.vehicle.plate },
        )
        assertEquals(
            listOf("B 3 AA"),
            filterByStatus(rows, VehicleFilter.Tersewa).map { it.vehicle.plate },
        )
        assertEquals(
            listOf("B 4 AA"),
            filterByStatus(rows, VehicleFilter.Servis).map { it.vehicle.plate },
        )
    }

    /**
     * A filter over one list, never a separate destination: composing search and
     * filter narrows the same rows rather than producing a new set.
     */
    @Test
    fun `query and status filter compose`() {
        val rows = listOf(
            tersedia("B 1235 TF"),
            tersewa("B 1235 UV"),
            tersedia("B 9876 AB"),
        )
        val byQuery = matchesQuery(rows, "b 1235")
        assertEquals(2, byQuery.size)
        assertEquals(
            listOf("B 1235 TF"),
            filterByStatus(byQuery, VehicleFilter.Tersedia).map { it.vehicle.plate },
        )
    }

    // ------------------------------------------------------------------ counts

    @Test
    fun `counts describe the fleet`() {
        val rows = listOf(tersedia("B 1 AA"), tersewa("B 2 AA"), servis("B 3 AA"))
        val counts = countByStatus(rows)
        assertEquals(3, counts[VehicleFilter.Semua])
        assertEquals(1, counts[VehicleFilter.Tersedia])
        assertEquals(1, counts[VehicleFilter.Tersewa])
        assertEquals(1, counts[VehicleFilter.Servis])
    }

    /**
     * Counts are taken over the *query-filtered* set. A chip that says "3" while
     * the list shows one match is a claim the screen cannot support, and the
     * owner cannot tell which of the two numbers is the lie.
     */
    @Test
    fun `counts follow the query`() {
        val rows = listOf(
            tersedia("B 1235 TF"),
            tersewa("B 1235 UV"),
            tersedia("B 9876 AB"),
        )
        val counts = countByStatus(matchesQuery(rows, "b 1235"))
        assertEquals(2, counts[VehicleFilter.Semua])
        assertEquals(1, counts[VehicleFilter.Tersedia])
        assertEquals(1, counts[VehicleFilter.Tersewa])

        // A query that matches one plate drops the total to 1, so the "semua"
        // chip and the list below it cannot disagree.
        assertEquals(1, countByStatus(matchesQuery(rows, "b 9876"))[VehicleFilter.Semua])
    }

    // -------------------------------------------------------- status derivation

    /**
     * `servis` from the manual marker, `tersewa` from an open contract,
     * `tersedia` otherwise — Story 2.3's rule, arriving early so the filter row
     * can exist.
     */
    @Test
    fun `status is derived from the marker and the open contract`() {
        assertEquals(VehicleStatus.Servis, VehicleStatus.of(isServis = true, hasOpenContract = false))
        assertEquals(VehicleStatus.Tersewa, VehicleStatus.of(isServis = false, hasOpenContract = true))
        assertEquals(VehicleStatus.Tersedia, VehicleStatus.of(isServis = false, hasOpenContract = false))
    }

    /**
     * Servis wins over an open contract: a car in for service *and* still rented
     * is `Servis`, because the fact the owner needs on their morning screen is
     * that the car is off the road. The contract still bills either way, so
     * nothing is lost by preferring it here.
     */
    @Test
    fun `servis takes precedence over an open contract`() {
        assertEquals(VehicleStatus.Servis, VehicleStatus.of(isServis = true, hasOpenContract = true))
    }

    // ------------------------------------------------------------- empty states

    /**
     * An empty fleet and an empty search result are different situations needing
     * different messages: one is an invitation to add a car, the other is not a
     * failure at all. They must never collapse into the same state.
     */
    @Test
    fun `an empty fleet is distinguished from an empty search`() {
        val empty = KendaraanUiState()
        assertTrue(empty.isEmptyFleet)
        assertFalse(empty.isEmptySearch)

        val searched = KendaraanUiState(rows = listOf(agya("B 1235 TF")), query = "zzzz")
        assertFalse(searched.isEmptyFleet)
        assertTrue(searched.isEmptySearch)
        assertTrue(searched.visibleRows.isEmpty())
    }

    /** A filtered-to-nothing list is an empty *search*, not an empty fleet. */
    @Test
    fun `filtering to nothing is an empty search not an empty fleet`() {
        val state = KendaraanUiState(
            rows = listOf(tersedia("B 1235 TF")),
            filter = VehicleFilter.Servis,
        )
        assertFalse(state.isEmptyFleet)
        assertTrue(state.isEmptySearch)
    }

    @Test
    fun `the fleet count is the number of rows`() {
        assertEquals(0, KendaraanUiState().fleetCount)
        assertEquals(2, KendaraanUiState(rows = listOf(agya("B 1 AA"), brio("B 2 AA"))).fleetCount)
    }

    // ------------------------------------------------------------------ helpers

    private fun row(vehicle: Vehicle, status: VehicleStatus) =
        VehicleRow(vehicle = vehicle, status = status, rateText = "")

    /** A Toyota Agya in hitam, available — the default fixture. */
    private fun agya(plate: String) = row(
        Vehicle(
            id = "id-$plate",
            plate = plate,
            brand = "Toyota",
            model = "Agya",
            year = 2023,
            color = "Hitam",
            dailyRate = 250_000L,
        ),
        VehicleStatus.Tersedia,
    )

    /** A Honda Brio in putih, available. */
    private fun brio(plate: String) = row(
        Vehicle(
            id = "id-$plate",
            plate = plate,
            brand = "Honda",
            model = "Brio",
            year = 2022,
            color = "Putih",
            dailyRate = 275_000L,
        ),
        VehicleStatus.Tersedia,
    )

    private fun tersedia(plate: String) = row(agya(plate).vehicle, VehicleStatus.Tersedia)
    private fun tersewa(plate: String) = row(agya(plate).vehicle, VehicleStatus.Tersewa)
    private fun servis(plate: String) = row(agya(plate).vehicle, VehicleStatus.Servis)
}