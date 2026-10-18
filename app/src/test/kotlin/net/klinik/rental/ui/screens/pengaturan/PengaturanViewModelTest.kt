package net.klinik.rental.ui.screens.pengaturan

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
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.data.repository.BusinessIdentityRepository
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
 * The settings screen's ViewModel, end to end against a real database.
 *
 * ## Why this exists when the repository is already tested
 *
 * `BusinessAccountRepositoryTest` proves the writes and the refusals. This file proves the two
 * are wired to each other — the claims that are individually true and jointly untested: that a
 * save closes the sheet, that a refusal lands on the field it belongs to rather than the banner,
 * and that add and edit take different paths.
 *
 * ## The `observingViewModel` helper
 *
 * `uiState` is `stateIn(WhileSubscribed(5_000))`, so with no subscriber the upstream flows are
 * never collected and `.value` stays at `initialValue`. Reading it without subscribing cannot
 * observe anything. The helper subscribes, and `backgroundScope` cancels the collector when the
 * test body finishes so it cannot outlive the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PengaturanViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var database: KlinikDatabase
    private lateinit var repository: BusinessAccountRepository
    private lateinit var identity: BusinessIdentityRepository
    private lateinit var strings: RStrings

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, KlinikDatabase::class.java)
            // Room runs queries on its own executor, which `advanceUntilIdle()` does not drain —
            // so a row written by `save` would sit in the database while `uiState.value` still
            // held the pre-write emission. Pointing both executors at the test dispatcher puts
            // Room's work on the same virtual clock the assertions advance.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        // No database handle — see the class KDoc, and `KontrakViewModelTest`'s matching
        // comment. `withTransaction` asserts it is not running on the main thread, and
        // `StandardTestDispatcher` drains *on* the main thread, so passing the handle here
        // makes every save throw — swallowed into the generic banner, which reads as "the
        // write failed" rather than as a test-harness problem.
        //
        // The transaction path is covered by `BusinessAccountRepositoryTest`, which uses
        // `runBlocking` and therefore a real background thread. This asymmetry is deliberate;
        // do not "fix" it by passing the database here.
        repository = BusinessAccountRepository(database.businessAccountDao())
        // Story 4.4's identity, over the same DAO alone and for the same reason: passing the
        // database would make `withTransaction` throw on the main thread under this
        // dispatcher, and the account suite already covers the transaction path.
        identity = BusinessIdentityRepository(database.settingDao())
        strings = RStrings(context)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------- the write

    /**
     * The AC's central claim: a saved account is visible with **no manual refresh**.
     *
     * There is no refresh call anywhere in this test — no method to call even if there were one —
     * because the guarantee is satisfied by *not having* a refresh path at all. The database's
     * tracker re-emits, `combine` rebuilds, and the row appears.
     */
    @Test
    fun `a saved account appears with no manual refresh`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        viewModel.onFormChange { it.setBankName("BCA").setNumber("1234567890") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.rows.size)
        assertEquals("BCA", state.rows.single().account.bankName)
        assertEquals("1234567890", state.rows.single().numberText)
        assertNull("a successful save closes the sheet", state.form)
        assertNull("and leaves no failure behind", state.errorMessage)
    }

    /** And the confirmation names the bank. */
    @Test
    fun `a successful save confirms in Indonesian`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        viewModel.onFormChange { it.setBankName("BCA").setNumber("111") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Rekening BCA tersimpan", viewModel.snackbarMessage.value)
    }

    /**
     * An **add** inserts, and an **edit** updates the same row.
     *
     * The distinction is carried by `AccountFormState.id`: null while adding, set while editing.
     * A form that took a fresh id on edit would insert a duplicate account for one bank — and
     * `PAYMENT` rows in Story 3.2 will reference the id, so a new one would orphan them.
     */
    @Test
    fun `add inserts and edit updates the same row`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        viewModel.onFormChange { it.setBankName("BCA").setNumber("111") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()
        val originalId = viewModel.uiState.value.rows.single().account.id

        viewModel.openEditForm(viewModel.uiState.value.rows.single())
        viewModel.onFormChange { it.setNumber("999") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("an edit must not add a row", 1, state.rows.size)
        assertEquals("and keeps the id", originalId, state.rows.single().account.id)
        assertEquals("with the new number", "999", state.rows.single().numberText)
    }

    // ---------------------------------------------------------- the refusals

    /**
     * A blank bank name lands on the **bank field**, not the banner.
     *
     * The field-level routing is the whole reason `BlankAccountFieldException` has one subclass
     * per field: the owner looking at an empty bank field needs *that* field to say why.
     */
    @Test
    fun `a blank bank name lands on the bank field`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        viewModel.onFormChange { it.setNumber("111") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val sheet = viewModel.uiState.value.form!!
        assertNotNull("the bank field carries the refusal", sheet.bankError)
        assertNull("and the number is not blamed", sheet.numberError)
        assertNull("nothing reached the banner", viewModel.uiState.value.errorMessage)
        assertTrue("nothing was written", viewModel.uiState.value.rows.isEmpty())
    }

    /** And a blank number lands on the number field. */
    @Test
    fun `a blank number lands on the number field`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        viewModel.onFormChange { it.setBankName("BCA") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        val sheet = viewModel.uiState.value.form!!
        assertNotNull("the number field carries the refusal", sheet.numberError)
        assertNull("and the bank is not blamed", sheet.bankError)
        assertTrue("nothing was written", viewModel.uiState.value.rows.isEmpty())
    }

    /**
     * A **malformed** number lands on the number field with the format message, not "wajib diisi".
     *
     * The distinction is the reason `InvalidAccountNumberException` is not an
     * `IllegalArgumentException` subtype: a caller catching the blank family would otherwise
     * swallow it and show "wajib diisi" on a field that visibly contains text.
     */
    @Test
    fun `a malformed number lands on the number field with the format message`() =
        runTest(dispatcher) {
            val viewModel = observingViewModel()

            viewModel.openAddForm()
            viewModel.onFormChange { it.setBankName("BCA").setNumber("12ab34") }
            viewModel.saveForm()
            dispatcher.scheduler.advanceUntilIdle()

            val sheet = viewModel.uiState.value.form!!
            assertNotNull("the number field carries the refusal", sheet.numberError)
            assertTrue(
                "and it names the accepted format: ${sheet.numberError}",
                sheet.numberError!!.contains("angka"),
            )
            assertTrue("nothing was written", viewModel.uiState.value.rows.isEmpty())
        }

    /**
     * A second save while one is in flight is ignored.
     *
     * Without the guard two writes race: the second inserts a duplicate id or overwrites the
     * first's values, and the owner sees a result they did not ask for.
     */
    @Test
    fun `a second save while one is in flight is ignored`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        viewModel.onFormChange { it.setBankName("BCA").setNumber("111") }
        viewModel.saveForm()
        // No `advanceUntilIdle` between them: the first write is still in flight.
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("exactly one row is stored", 1, viewModel.uiState.value.rows.size)
    }

    // ------------------------------------------------------------- the sheet

    /**
     * Closing the sheet clears it and its message.
     *
     * A refusal raised while the sheet was up must not survive onto the list, where it would read
     * as a current failure with nothing left to explain it.
     */
    @Test
    fun `closing the form clears it and its message`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        viewModel.onFormChange { it.setNumber("111") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull("precondition: a refusal is showing", viewModel.uiState.value.form!!.bankError)

        viewModel.closeForm()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("the sheet is closed", viewModel.uiState.value.form)
        assertNull("and its message goes with it", viewModel.uiState.value.errorMessage)
    }

    /**
     * The add form opens **empty**, and the edit form is pre-filled.
     *
     * The asymmetry is deliberate: an add with a pre-filled bank name would be the prototype's
     * hidden default wearing a label, while an edit *is* about the stored values — showing them
     * is the point.
     */
    @Test
    fun `add opens empty and edit opens pre-filled`() = runTest(dispatcher) {
        val viewModel = observingViewModel()

        viewModel.openAddForm()
        dispatcher.scheduler.advanceUntilIdle()
        val add = viewModel.uiState.value.form!!
        assertEquals("", add.bankName)
        assertEquals("", add.number)
        assertFalse("and it is not an edit", add.isEditing)

        // Store one, then open it for editing.
        viewModel.onFormChange { it.setBankName("BCA").setNumber("111") }
        viewModel.saveForm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.openEditForm(viewModel.uiState.value.rows.single())
        dispatcher.scheduler.advanceUntilIdle()

        val edit = viewModel.uiState.value.form!!
        assertEquals("BCA", edit.bankName)
        assertEquals("111", edit.number)
        assertTrue("and it carries the id, which is what makes it an update", edit.isEditing)
    }

    /**
     * `isLoading` is true before the first emission and false after it.
     *
     * Both halves, because either alone is satisfiable by an implementation that does nothing
     * useful: "true at the start" alone passes a flag that never clears, and "false at the end"
     * alone passes a flag that is never set — which renders "Belum ada rekening" on every load.
     *
     * Built directly rather than through `observingViewModel`, because that helper subscribes
     * first, and subscribing is exactly the event that ends the loading state.
     */
    @Test
    fun `isLoading is true before the first emission and false after`() = runTest(dispatcher) {
        val viewModel = PengaturanViewModel(repository, identity, strings)

        assertTrue(
            "a cold open must not read as an empty account list",
            viewModel.uiState.value.isLoading,
        )

        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(
            "an emitted (empty) account list is not a loading state",
            viewModel.uiState.value.isLoading,
        )
    }

    // ------------------------------------------------- Story 4.4: the identity

    /**
     * The regression this story's first pass shipped: **editing the name erased the address
     * and the phone.**
     *
     * The form used to be seeded with the name only, so an edit opened the other two empty;
     * `toBusinessIdentity` turned an empty field into `null`, and the repository reads `null`
     * as *clear this field*. The owner's path was: fill in all three, weeks later correct a typo
     * in the name, save — and get a success confirmation with the other two gone, on the one
     * action this UI cannot undo.
     *
     * `IdentityFormTest` never saw it, because it hand-builds `IdentityFormState()` with no
     * stored identity in play — which is why the regression belongs **here**, where the flow
     * runs storage → form → storage.
     */
    @Test
    fun `correcting only the name leaves the address and phone stored`() =
        runTest(dispatcher) {
            identity.save("PT Klinik Net Rent Car", "Jl. Boulevard Raya Q18", "0812-8899-7722")

            val viewModel = observingViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.openIdentityForm()
            dispatcher.scheduler.advanceUntilIdle()

            // Pre-filled on all three — the half of the fix that makes the save safe.
            val opened = viewModel.uiState.value.identityForm
            assertNotNull("the form opened", opened)
            assertEquals("PT Klinik Net Rent Car", opened!!.name)
            assertEquals("Jl. Boulevard Raya Q18", opened.address)
            assertEquals("0812-8899-7722", opened.phone)

            // The owner fixes one character and saves, touching nothing else.
            viewModel.onIdentityChange { it.setIdentityName("PT KLINIK NET RENT CAR") }
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.saveIdentity()
            dispatcher.scheduler.advanceUntilIdle()

            val stored = identity.identity()
            assertEquals("the corrected name", "PT KLINIK NET RENT CAR", stored.name)
            assertEquals(
                "and the address the owner never touched is still there",
                "Jl. Boulevard Raya Q18",
                stored.address,
            )
            assertEquals(
                "and so is the phone — a name-only edit is not a request to clear the rest",
                "0812-8899-7722",
                stored.phone,
            )
        }

    /**
     * A **fresh** form still opens with the default name and the optional fields empty.
     *
     * The counterpart to the test above, and the reason the two live together: pre-filling an
     * edit and leaving a fresh form blank are the same rule seen from two sides, and an
     * implementation that pre-filled everything — or nothing — would satisfy one and fail the
     * other.
     */
    @Test
    fun `a first run opens with the default name and no invented address`() =
        runTest(dispatcher) {
            val viewModel = observingViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.openIdentityForm()
            dispatcher.scheduler.advanceUntilIdle()

            val form = viewModel.uiState.value.identityForm!!
            assertEquals(BusinessIdentityRepository.DEFAULT_NAME, form.name)
            assertEquals("and no invented address", "", form.address)
            assertEquals("nor phone", "", form.phone)
        }

    /**
     * **Only one sheet may be up at a time**, in both directions.
     *
     * Two `ModalBottomSheet` windows over one state is the defect Story 2.8's review found as
     * its only `high`: two scrims, two tap-blockers, and a screen scrolling under both. The
     * guard was one-way at first — `openIdentityForm` cleared the account form, but
     * `openAddForm` left the identity form standing — so the property the screen's own comment
     * claims was true from one side and false from the other.
     *
     * Both directions are asserted here because a test of one would pass against the one-way
     * version that shipped.
     */
    @Test
    fun `opening one sheet closes the other`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openAddForm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.openIdentityForm()
        dispatcher.scheduler.advanceUntilIdle()

        val afterIdentity = viewModel.uiState.value
        assertNotNull("the identity sheet is up", afterIdentity.identityForm)
        assertNull("and the account sheet is gone", afterIdentity.form)

        viewModel.openAddForm()
        dispatcher.scheduler.advanceUntilIdle()

        val afterAccount = viewModel.uiState.value
        assertNotNull("the account sheet is back", afterAccount.form)
        assertNull("and the identity sheet is gone", afterAccount.identityForm)
    }

    /** A blank name is refused on its field, and nothing is written. */
    @Test
    fun `a blank name is refused on the field and writes nothing`() = runTest(dispatcher) {
        identity.save("Rental SAYA", "Jl. Baru", "0812")
        val viewModel = observingViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openIdentityForm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onIdentityChange { it.setIdentityName("   ") }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.saveIdentity()
        dispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.uiState.value.identityForm
        assertNotNull("the form stays open so the owner can fix it", form)
        assertNotNull(
            "and the refusal is on the name field",
            form!!.nameError,
        )
        assertEquals(
            "while the stored identity is untouched",
            "Rental SAYA",
            identity.identity().name,
        )
    }

    /** Saving closes the form and confirms in Indonesian. */
    @Test
    fun `saving the identity closes the form and confirms`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openIdentityForm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onIdentityChange { it.setIdentityAddress("Jl. Boulevard Raya Q18") }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.saveIdentity()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("the form closed", viewModel.uiState.value.identityForm)
        // The confirmation is its own `snackbarMessage` flow, not a field on `uiState` —
        // success does not change screen state, which is the four-state rule.
        assertEquals(
            "the name is the one the sheet was opened on — this test changes only the address",
            "PT Klinik Net Rent Car tersimpan sebagai identitas usaha",
            viewModel.snackbarMessage.value,
        )
        assertEquals("Jl. Boulevard Raya Q18", identity.identity().address)
    }

    /** The section's header reads what is stored, not what the form last held. */
    @Test
    fun `the section shows the stored name after a save`() = runTest(dispatcher) {
        val viewModel = observingViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openIdentityForm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onIdentityChange { it.setIdentityName("Rental SAYA") }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.saveIdentity()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the header follows the write — a section still showing the old name beside a form "
                + "that just saved a new one is a screen contradicting itself",
            "Rental SAYA",
            viewModel.uiState.value.identity?.name,
        )
    }

    // ------------------------------------------------------------------ helper

    /**
     * Builds the ViewModel **with an active collector** on `uiState`.
     *
     * `stateIn(WhileSubscribed(5_000))` means no subscriber produces no upstream collection, so
     * the database flow is never queried and `.value` stays at `initialValue` no matter how many
     * rows are stored. `backgroundScope` is cancelled when the test body finishes, so the
     * collector cannot outlive the test or leak into the next one.
     */
    private fun TestScope.observingViewModel(): PengaturanViewModel {
        val viewModel = PengaturanViewModel(repository, identity, strings)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        return viewModel
    }
}
