package net.klinik.rental.ui.screens.penyewa

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.validation.DuplicateNameException
import net.klinik.rental.ui.strings.RStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The end-to-end save path: the ViewModel, the repository, Room's invalidation
 * tracker and `combine`, against a real database.
 *
 * ## Why this exists when the layers below are already tested
 *
 * `RenterRepositoryTest` proves the write and the refusal. `PenyewaFilterTest`
 * proves the search. This file proves the two are wired to each other — the three
 * claims that are individually true and jointly untested:
 *
 *  - a saved renter appears in `uiState.rows` **with no manual refresh**, because
 *    Room's invalidation tracker re-emits and nothing in the app signals
 *  - the copy for a refusal comes from `RStrings`, so it is Indonesian and names
 *    the stored renter rather than echoing the exception
 *  - a blank name never reaches the database at all
 *
 * Story 2.3 deferred the equivalent test at the `MobilViewModel` boundary for
 * the same reason, and recorded it as a real gap. This story has no such deferral:
 * the ViewModel is built here, so the test is written with it.
 *
 * ## Why `Dispatchers.Main` is swapped
 *
 * `viewModelScope` runs on `Dispatchers.Main`, which does not exist on the JVM
 * without Robolectric's main looper being driven. A `StandardTestDispatcher` is
 * installed instead so `launch` bodies are queued and drained deterministically by
 * `runTest`, which is what makes these assertions about *after the save settled*
 * rather than about *eventually*.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PenyewaViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var database: KlinikDatabase
    private lateinit var repository: RenterRepository
    private lateinit var strings: RStrings

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, KlinikDatabase::class.java)
            // Room runs queries on its own executor, which `advanceUntilIdle()`
            // does not drain — so a row written by `save` would sit in the
            // database while `uiState.value` still held the pre-write emission,
            // and every assertion below would read an empty list. Pointing both
            // executors at the test dispatcher puts Room's work on the same
            // virtual clock the assertions advance, which is what makes
            // "after the save settled" mean settled rather than "eventually".
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        repository = RenterRepository(database.renterDao())
        strings = RStrings(context)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------ save reaches the list

    /**
     * The AC's central claim: a renter saved from the form is visible in the list
     * with no manual refresh.
     *
     * There is no refresh call anywhere in this test — no method to call even if
     * there were one — because the guarantee is satisfied by *not having* a
     * refresh path at all. Room's invalidation tracker re-emits, `combine` rebuilds,
     * and the row appears.
     */
    @Test
    fun `a saved renter appears in the list with no manual refresh`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        openAndSave(
            viewModel = viewModel,
            name = "Budi Santoso",
            phone = "08123456789",
            emergencyContact = "08129876543",
            bankAccount = "BCA 1234567890",
        )
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.rows.size)
        assertEquals("Budi Santoso", state.rows.single().renter.name)
        assertEquals(1, state.renterCount)
    }

    /**
     * A successful save closes the form and confirms in Indonesian, naming the
     * person so the confirmation is recognisable.
     */
    @Test
    fun `a successful save closes the form and confirms in Indonesian`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull("the form must be open before saving", viewModel.uiState.value.form)

        openAndSave(
            viewModel = viewModel,
            name = "Budi Santoso",
            phone = "08123456789",
            emergencyContact = "08129876543",
            bankAccount = "",
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("the form must close on success", viewModel.uiState.value.form)
        assertEquals("Budi Santoso tersimpan", viewModel.snackbarMessage.value)
    }

    /**
     * SAVE_RENTER_OPTIONAL_ACCOUNT, at the ViewModel boundary: a blank account is
     * stored as `null` rather than as an empty string.
     *
     * `ifBlank { null }` is the ViewModel's half of the optional-field rule, and
     * this is what pins it — an empty account would render as a badge on the row
     * and look like real data.
     */
    @Test
    fun `a blank bank account is stored as null`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        openAndSave(
            viewModel = viewModel,
            name = "Siti Aminah",
            phone = "08129876543",
            emergencyContact = "08123456789",
            bankAccount = "   ",
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.rows.single().renter.bankAccount)
        assertNull(database.renterDao().findById(viewModel.uiState.value.rows.single().renter.id)!!.bankAccount)
    }

    // ------------------------------------------------------------ the refusals

    /**
     * SAVE_RENTER_BLANK_NAME: nothing is written, and the message lands on the
     * field rather than floating over the list.
     *
     * A field-level message is the right surface here because the sheet stays
     * open — the owner is still looking at the form, so the form is where the
     * reason belongs.
     */
    @Test
    fun `a blank name writes nothing and shows the message on the field`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        openAndSave(
            viewModel = viewModel,
            name = "   ",
            phone = "08123456789",
            emergencyContact = "08129876543",
            bankAccount = "",
        )
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull("the sheet must stay open on a refused save", form)
        assertEquals("Wajib diisi. Isi kolom ini lalu kirim ulang.", form!!.nameError)
        assertNull("the screen-level error must stay empty for a field fault", viewModel.uiState.value.errorMessage)
        // Nothing was written.
        assertEquals(0, viewModel.uiState.value.rows.size)
        assertEquals(0, repository.observeRenters().first().size)
    }

    /**
     * A blank phone is refused the same way a blank name is, and lands on the
     * phone field rather than the name.
     *
     * `Renter` refuses all three required fields with the same typed exception,
     * so the routing is what is under test: an owner looking at an empty phone
     * field needs that field to say why, not whichever one the model happened to
     * check first.
     */
    @Test
    fun `a blank phone is refused on the phone field and writes nothing`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        openAndSave(
            viewModel = viewModel,
            name = "Budi Santoso",
            phone = "   ",
            emergencyContact = "08129876543",
            bankAccount = "",
        )
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull("the sheet must stay open on a refused save", form)
        assertEquals("Wajib diisi. Isi kolom ini lalu kirim ulang.", form!!.phoneError)
        assertNull("the name must not be blamed for a blank phone", form.nameError)
        assertEquals(0, repository.observeRenters().first().size)
    }

    /** And the same for the emergency contact. */
    @Test
    fun `a blank emergency contact is refused on that field`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        openAndSave(
            viewModel = viewModel,
            name = "Budi Santoso",
            phone = "08123456789",
            emergencyContact = "",
            bankAccount = "",
        )
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull("the sheet must stay open on a refused save", form)
        assertEquals("Wajib diisi. Isi kolom ini lalu kirim ulang.", form!!.emergencyError)
        assertNull("the name must not be blamed for a blank contact", form.nameError)
        assertEquals(0, repository.observeRenters().first().size)
    }

    /**
     * A second tap on Save while the first write is in flight is ignored.
     *
     * Without the guard the second save is enqueued for the same renter, the
     * first succeeds, and the second is then reported as a duplicate of its own
     * row — reopening a form that had already closed.
     */
    @Test
    fun `a second save while one is in flight is ignored`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        openAndSave(
            viewModel = viewModel,
            name = "Budi Santoso",
            phone = "08123456789",
            emergencyContact = "08129876543",
            bankAccount = "",
        )
        dispatcher.scheduler.advanceUntilIdle()

        // The form closed on success, so reopen it with the same values and tap
        // Save twice before advancing the scheduler.
        viewModel.openAddForm()
        viewModel.onFormChange {
            it.copy(
                name = "Siti Aminah",
                phone = "081200000000",
                emergencyContact = "08129999999",
            )
        }
        viewModel.saveForm()
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val rows = repository.observeRenters().first()
        assertEquals("one renter per tap, not two rows", 2, rows.size)
        assertNull("the form closes once, on the write that landed", viewModel.uiState.value.form)
    }

    /**
     * SAVE_RENTER_DUPLICATE_NAME: the refusal names the *stored* renter, so the
     * owner knows which person to look at.
     */
    @Test
    fun `a duplicate name is refused and names the stored renter`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        openAndSave(
            viewModel = viewModel,
            name = "Budi Santoso",
            phone = "08123456789",
            emergencyContact = "08129876543",
            bankAccount = "",
        )
        dispatcher.scheduler.advanceUntilIdle()

        // A second, different renter tries to take the name. Every required field is
        // filled: `Renter` refuses a blank emergency contact at construction,
        // which fires before the duplicate check, so an incomplete fixture here
        // would be testing the blank-field refusal and calling it a duplicate.
        viewModel.openAddForm()
        viewModel.onFormChange {
            it.copy(
                name = "Budi Santoso",
                phone = "081200000000",
                emergencyContact = "08129999999",
            )
        }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertNotNull("the sheet must stay open on a refused save", form)
        assertEquals(
            "Nama Budi Santoso sudah dipakai penyewa lain. Periksa lagi namanya.",
            form!!.nameError,
        )
        // And the original renter is untouched — only one row exists.
        assertEquals(1, viewModel.uiState.value.rows.size)
        assertEquals("08123456789", viewModel.uiState.value.rows.single().renter.phone)
    }

    /**
     * EDIT_RENTER: editing one renter leaves the other byte-for-byte unchanged.
     *
     * The update is keyed on the primary key, so it reaches exactly one row — a
     * broad write would pass this assertion by accident while corrupting the
     * neighbour.
     */
    @Test
    fun `editing one renter leaves the other unchanged`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        openAndSave(
            viewModel = viewModel,
            name = "Budi Santoso",
            phone = "08123456789",
            emergencyContact = "08129876543",
            bankAccount = "",
        )
        dispatcher.scheduler.advanceUntilIdle()

        // A second renter, so there is a neighbour to protect.
        repository.save(
            Renter(
                id = "r2",
                name = "Siti Aminah",
                phone = "08129876543",
                bankAccount = null,
                emergencyContact = "08123456789",
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        val budi = viewModel.uiState.value.rows.single { it.renter.name == "Budi Santoso" }.renter
        viewModel.openEditForm(budi)
        viewModel.onFormChange { it.copy(phone = "081200000000") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val stored = viewModel.uiState.value.rows.associateBy { it.renter.name }
        assertEquals(2, stored.size)
        assertEquals("081200000000", stored.getValue("Budi Santoso").renter.phone)
        assertEquals("08129876543", stored.getValue("Siti Aminah").renter.phone)
        assertEquals("08123456789", stored.getValue("Siti Aminah").renter.emergencyContact)
    }

    /**
     * An edit may keep its own name without colliding with itself — the same
     * distinction `save` draws between "this is my row" and "someone else holds
     * this name", reached here through the edit form rather than the repository.
     */
    @Test
    fun `a renter can be edited without changing their name`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        openAndSave(
            viewModel = viewModel,
            name = "Budi Santoso",
            phone = "08123456789",
            emergencyContact = "08129876543",
            bankAccount = "",
        )
        dispatcher.scheduler.advanceUntilIdle()

        val budi = viewModel.uiState.value.rows.single().renter
        viewModel.openEditForm(budi)
        // Name untouched, phone changed.
        viewModel.onFormChange { it.copy(phone = "081200000000") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.rows.size)
        assertEquals("081200000000", viewModel.uiState.value.rows.single().renter.phone)
    }

    /**
     * The edit form is populated from the stored renter, and an account survives
     * the round trip through the form's `String` representation.
     *
     * This is the half that would silently blank somebody's bank account: the
     * stored value is nullable and the form's is not, so a `?: ""` that dropped
     * more than the null would produce an empty string on the next save.
     */
    @Test
    fun `the edit form is populated from the stored renter`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        repository.save(
            Renter(
                id = "r1",
                name = "Budi Santoso",
                phone = "08123456789",
                bankAccount = "BCA 1234567890",
                emergencyContact = "08129876543",
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openEditForm(viewModel.uiState.value.rows.single().renter)
        // `openEditForm` writes the form StateFlow, so the combine that carries it
        // into `uiState` has to be advanced before the read — same reason as every
        // other `.value` read in this file.
        dispatcher.scheduler.advanceUntilIdle()

        val form = requireNotNull(viewModel.uiState.value.form) {
            "openEditForm must open the form the owner edits"
        }

        assertTrue("the form must know it is editing", form.isEditing)
        assertEquals("Budi Santoso", form.name)
        assertEquals("08123456789", form.phone)
        assertEquals("BCA 1234567890", form.bankAccount)
        assertEquals("08129876543", form.emergencyContact)
    }

    /**
     * And an edit that never touches the account keeps it — the write-back path,
     * not just the load path.
     */
    @Test
    fun `an edit that does not touch the account keeps it`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        repository.save(
            Renter(
                id = "r1",
                name = "Budi Santoso",
                phone = "08123456789",
                bankAccount = "BCA 1234567890",
                emergencyContact = "08129876543",
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openEditForm(viewModel.uiState.value.rows.single().renter)
        viewModel.onFormChange { it.copy(phone = "081200000000") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("BCA 1234567890", viewModel.uiState.value.rows.single().renter.bankAccount)
    }

    // -------------------------------------------------------------- searching

    /**
     * SEARCH_RENTER through the ViewModel: typing a query narrows `visibleRows`
     * in place, and `rows` — the stored set — is unchanged.
     *
     * Asserted on both, because a screen that narrowed the *stored* list would
     * satisfy the first and lose the renter when the query cleared.
     */
    @Test
    fun `a query narrows the visible rows without changing the stored ones`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        repository.save(
            Renter(
                id = "r1",
                name = "Budi Santoso",
                phone = "08123456789",
                bankAccount = "BCA 1234567890",
                emergencyContact = "08129876543",
            ),
        )
        repository.save(
            Renter(
                id = "r2",
                name = "Siti Aminah",
                phone = "08129876543",
                bankAccount = null,
                emergencyContact = "08123456789",
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChange("Budi")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.rows.size)
        assertEquals(1, state.visibleRows.size)
        assertEquals("Budi Santoso", state.visibleRows.single().renter.name)
        // And the count on the bar is the stored count, not the visible one.
        assertEquals(2, state.renterCount)

        // Clearing restores the whole list.
        viewModel.clearQuery()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.visibleRows.size)
    }

    /**
     * A query matching nothing is the *empty-search* state, not the empty-list one
     * — the two need opposite actions and must never collapse.
     */
    @Test
    fun `an unmatched query is an empty search not an empty list`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        repository.save(
            Renter(
                id = "r1",
                name = "Budi Santoso",
                phone = "08123456789",
                bankAccount = null,
                emergencyContact = "08129876543",
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChange("tidak ada")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("a failed search is not an empty store", state.isEmptyList.not())
        assertTrue(state.isEmptySearch)
    }

    // ------------------------------------------------------------------ helper

    /**
     * Builds the ViewModel **with an active collector** on `uiState`.
     *
     * ## Why this helper exists at all
     *
     * `uiState` is `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000))`.
     * That sharing policy is the right one for a screen: it keeps the Room query
     * alive across a rotation but stops it when nothing is on screen.
     *
     * The cost is that with no subscriber there is no upstream collection, so the
     * Room `Flow` is never queried and `uiState.value` stays at `initialValue` —
     * an empty `PenyewaUiState` — no matter how many rows the database holds.
     * Reading `.value` without subscribing therefore cannot observe anything.
     *
     * So every test here goes through this helper, which subscribes for the
     * duration of the test and cancels on teardown. The assertions still read
     * `.value`; they just now read a value that has actually been produced.
     * A test written against the `.value` directly would pass `assertNotNull(form)`
     * after `openAddForm()` and then fail on every rows assertion with an
     * empty list — which is what happened before this helper existed.
     */
    private fun TestScope.observingViewModel(): PenyewaViewModel {
        val viewModel = PenyewaViewModel(repository, strings)
        // `backgroundScope` is cancelled when the test body finishes, so the
        // collector cannot outlive the test or leak into the next one.
        backgroundScope.launch(dispatcher) {
            viewModel.uiState.collect { }
        }
        return viewModel
    }

    /**
     * `isLoading` is true before the first emission and false after it.
     *
     * Both halves, because either alone is satisfiable by an implementation that
     * does nothing useful: asserting only "true at the start" passes a flag that
     * never clears, and asserting only "false at the end" passes a flag that is
     * never set — which renders "Belum ada Penyewa" on every load, the exact
     * empty-looks-like-a-new-account failure the flag exists to prevent.
     *
     * The start is read before the scheduler runs, because `runTest` does not
     * advance virtual time until the body suspends: at that point `stateIn`'s
     * `initialValue` is what is in force, and that is the value a screen draws on
     * a cold open.
     */
    @Test
    fun `isLoading is true before the first emission and false after`() = runTest(dispatcher) {
        val viewModel = PenyewaViewModel(repository, strings)

        // Before subscribing, `stateIn` hands out its initial value — the frame a
        // screen draws before the Room flow has said anything.
        assertTrue(
            "a cold open must not read as an empty renter list",
            viewModel.uiState.value.isLoading,
        )

        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(
            "an emitted (empty) renter list is not a loading state",
            viewModel.uiState.value.isLoading,
        )
    }

    /**
     * Opens the form, fills it, and saves.
     *
     * `openAddForm` is inside because every caller wants the form open, and a
     * caller that forgot it would see `saveForm` return early — a silent no-op
     * that looks like a passing test.
     */
    private fun openAndSave(
        viewModel: PenyewaViewModel,
        name: String,
        phone: String,
        emergencyContact: String,
        bankAccount: String,
    ) {
        viewModel.openAddForm()
        viewModel.onFormChange {
            it.copy(
                name = name,
                phone = phone,
                emergencyContact = emergencyContact,
                bankAccount = bankAccount,
            )
        }
        viewModel.saveForm()
    }
}