package net.klinik.rental.ui.screens.kendaraan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.ui.strings.RStrings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The fleet screen's loading state, against a real Room database.
 *
 * ## Why this file exists at all
 *
 * `KendaraanViewModel` had no test file: every assertion about the fleet screen
 * was made against `KendaraanUiState` directly, so nothing exercised the
 * `combine` that produces it. Story 2.8 added `isLoading` to that combine, and an
 * untested `combine` is where a flag goes wrong quietly — invert the
 * `onStart { emit(… to false) }` and the whole suite stays green while a cold
 * open renders "Belum ada Kendaraan", the exact
 * empty-looks-like-a-new-account failure the flag exists to prevent.
 *
 * ## Why the assertions are the pair, not either half
 *
 * "true before the first emission" alone is satisfied by a flag that never
 * clears, so the screen would be a skeleton forever; "false after" alone is
 * satisfied by a flag that is never set, which is the bug above. Both together
 * are the claim.
 *
 * ## Why the executors point at the test dispatcher
 *
 * Room runs queries on its own executor, which `advanceUntilIdle()` does not
 * drain — so `uiState.value` would still hold the pre-read emission and the
 * second assertion would fail for a reason that has nothing to do with the flag.
 * Same reason `PenyewaViewModelTest` documents.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KendaraanViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var database: KlinikDatabase
    private lateinit var repository: VehicleRepository
    private lateinit var strings: RStrings

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, KlinikDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        repository = VehicleRepository(database.vehicleDao(), database)
        strings = RStrings(context)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    /**
     * `isLoading` is true before the first emission and false after it.
     *
     * Read before the scheduler runs: `runTest` does not advance virtual time
     * until the body suspends, so at that point `stateIn`'s `initialValue` is in
     * force — and that is the value a screen draws on a cold open, before Room has
     * said anything at all.
     */
    @Test
    fun `isLoading is true before the first emission and false after`() = runTest(dispatcher) {
        val viewModel = KendaraanViewModel(repository, strings)

        assertTrue(
            "a cold open must not read as an empty fleet",
            viewModel.uiState.value.isLoading,
        )

        observing(viewModel)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(
            "an emitted (empty) fleet is not a loading state",
            viewModel.uiState.value.isLoading,
        )
    }

    /**
     * And it is still false after a real read of a real fleet, not only after an
     * empty one.
     *
     * An empty emission and a populated one are both "the read finished"; a flag
     * that keyed off emptiness rather than off having-emitted would pass the test
     * above for the wrong reason and fail this one, which is the point of having
     * both.
     */
    @Test
    fun `isLoading is false once a populated fleet has been read`() = runTest(dispatcher) {
        repository.save(
            net.klinik.rental.domain.model.Vehicle(
                id = "v1",
                plate = "B 1235 TF",
                brand = "Toyota",
                model = "Avanza",
                year = 2023,
                dailyRate = 250_000L,
            )
        )
        val viewModel = KendaraanViewModel(repository, strings)

        observing(viewModel)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse("a read fleet is not a loading state", viewModel.uiState.value.isLoading)
        assertTrue("the read must have produced rows", viewModel.uiState.value.rows.isNotEmpty())
    }

    /**
     * Subscribes for the test's duration.
     *
     * `uiState` is `stateIn(WhileSubscribed(5_000))`, so with no subscriber there
     * is no upstream collection and `.value` stays at `initialValue` — reading it
     * directly cannot observe anything. `backgroundScope` is cancelled when the
     * body finishes, so the collector cannot outlive the test.
     */
    private fun TestScope.observing(viewModel: KendaraanViewModel) {
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
    }
}