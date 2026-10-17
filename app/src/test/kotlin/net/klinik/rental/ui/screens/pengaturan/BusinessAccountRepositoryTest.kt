package net.klinik.rental.ui.screens.pengaturan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.domain.model.BusinessAccount
import net.klinik.rental.domain.validation.BlankAccountFieldException
import net.klinik.rental.domain.validation.InvalidAccountNumberException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The `ACCOUNT_*` matrix rows, against a real Room database.
 *
 * ## Why these are not fakes
 *
 * The normalisation this story performs is a **schema-adjacent** fact: the stored column holds
 * the canonical form, so a repository that normalised only on the way out would pass a fake
 * and store two spellings of one account here. So these run against an in-memory database
 * built by the same annotation processor as the shipped one.
 *
 * ## Why there is no delete test
 *
 * There is no delete method to test. Story 3.1's acceptance criterion is that an account a
 * deposit uses cannot be deleted — and the `PAYMENT` table that would reference one is Story
 * 3.2's and does not exist, so a delete now would carry a guard whose condition is vacuously
 * true. `BusinessAccountDao`'s KDoc has the full argument. The test arrives with the foreign
 * key, in 3.2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BusinessAccountRepositoryTest {

    private lateinit var database: KlinikDatabase
    private lateinit var repository: BusinessAccountRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KlinikDatabase::class.java,
        ).build()
        repository = BusinessAccountRepository(database.businessAccountDao(), database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------------ the adds

    /**
     * `ACCOUNT_ADD` — the happy path, read back through the flow.
     *
     * A `Flow` rather than a one-shot read, because it is what the screen subscribes to and
     * what makes "no manual refresh" structural: the database's tracker re-emits on write.
     */
    @Test
    fun `an account is stored and observed`() = runBlocking {
        repository.save(account(bankName = "BCA", number = "1234567890"))

        val stored = repository.observeAccounts().first()
        assertEquals(1, stored.size)
        assertEquals("BCA", stored.single().bankName)
        assertEquals("1234567890", stored.single().number)
    }

    /**
     * `ACCOUNT_ADD_SECOND` — two accounts at one bank are legitimate.
     *
     * The tempting constraint is `UNIQUE(bankName)`, or `UNIQUE(number)` by analogy with
     * `vehicle.plate`. Both would refuse a real setup: a business account and a payroll
     * account at the same bank are normal. This is the test that would fail if either index
     * were invented.
     */
    @Test
    fun `two accounts at one bank are both stored`() = runBlocking {
        repository.save(account(id = "a1", bankName = "BCA", number = "111"))
        repository.save(account(id = "a2", bankName = "BCA", number = "222"))

        val stored = repository.observeAccounts().first()
        assertEquals(2, stored.size)
        assertEquals(setOf("BCA"), stored.map { it.bankName }.toSet())
    }

    /**
     * `ACCOUNT_ADD_SAME_NUMBER` — the same number at two banks is two accounts.
     *
     * The owner may genuinely hold one number at two institutions, and the app has no
     * business deciding that is an error. Nothing in the acceptance criterion asks for
     * deduplication.
     */
    @Test
    fun `the same number at two banks is two accounts`() = runBlocking {
        repository.save(account(id = "a1", bankName = "BCA", number = "111"))
        repository.save(account(id = "a2", bankName = "Mandiri", number = "111"))

        val stored = repository.observeAccounts().first()
        assertEquals(2, stored.size)
        assertEquals(setOf("BCA", "Mandiri"), stored.map { it.bankName }.toSet())
        assertEquals("both carry the same number", setOf("111"), stored.map { it.number }.toSet())
    }

    // ----------------------------------------------------------------- the edits

    /**
     * `ACCOUNT_EDIT` — an edit updates in place, and the **id survives**.
     *
     * The id surviving is the load-bearing half: a `PAYMENT` row will reference it, so an
     * edit that inserted a new row would orphan every deposit recorded against the old one.
     * The row count is asserted for the same reason — a save that inserted instead of updating
     * would pass an "the number changed" assertion.
     */
    @Test
    fun `editing an account updates in place and keeps its id`() = runBlocking {
        val original = account(id = "a1", bankName = "BCA", number = "111")
        repository.save(original)

        repository.save(original.copy(number = "999"))

        val stored = repository.observeAccounts().first()
        assertEquals("an edit must not add a row", 1, stored.size)
        assertEquals("the id is unchanged", "a1", stored.single().id)
        assertEquals("and the new number is stored", "999", stored.single().number)
    }

    /**
     * `ACCOUNT_SPACES_TRIMMED` — the bank name is trimmed on the way in.
     *
     * A name stored with trailing spaces would sort differently in the list and would look
     * like a different bank from the same one typed cleanly.
     */
    @Test
    fun `a bank name is trimmed`() = runBlocking {
        repository.save(account(bankName = "  BCA  ", number = "111"))

        assertEquals("BCA", repository.observeAccounts().first().single().bankName)
    }

    /**
     * `ACCOUNT_EDIT_NUMBER_NORMALISED` — two spellings of one number become one value.
     *
     * The stored form is the canonical one, so the owner comparing against a bank statement
     * sees one number regardless of which source they typed it from. Note this is *not* a
     * dedupe rule — two rows with different ids still both exist; what is canonical is the
     * value inside each.
     */
    @Test
    fun `an account number is normalised on the way in`() = runBlocking {
        repository.save(account(id = "a1", bankName = "BCA", number = "1234 5678"))
        repository.save(account(id = "a2", bankName = "Mandiri", number = "1234-5678"))

        val stored = repository.observeAccounts().first().sortedBy { it.id }
        assertEquals("spaces stripped", "12345678", stored[0].number)
        assertEquals("dashes stripped", "12345678", stored[1].number)
    }

    /**
     * And an edit that only changes the *separators* still updates the stored value.
     *
     * The subtle case: `1234 5678` and `12345678` are the same account, so saving the second
     * over the first is a no-op in value terms — and the id must still be the same, not a new
     * row. A repository that compared raw strings would treat them as different.
     */
    @Test
    fun `editing only the separators keeps one account`() = runBlocking {
        val original = account(id = "a1", bankName = "BCA", number = "1234 5678")
        repository.save(original)

        repository.save(original.copy(number = "1234-5678"))

        val stored = repository.observeAccounts().first()
        assertEquals("still one row", 1, stored.size)
        assertEquals("a1", stored.single().id)
        assertEquals("12345678", stored.single().number)
    }

    // -------------------------------------------------------------- the refusals

    /** `ACCOUNT_BLANK_BANK` — refused, and the exception names the bank field. */
    @Test
    fun `a blank bank name is refused`() = runBlocking {
        val error = assertThrows(BlankAccountFieldException.BlankBankNameException::class.java) {
            runBlocking { repository.save(account(bankName = "  ", number = "111")) }
        }

        assertEquals("bankName", error.field)
        assertTrue("nothing is written", repository.observeAccounts().first().isEmpty())
    }

    /** `ACCOUNT_BLANK_NUMBER` — refused, and the exception names the number field. */
    @Test
    fun `a blank number is refused`() = runBlocking {
        val error = assertThrows(BlankAccountFieldException.BlankNumberException::class.java) {
            runBlocking { repository.save(account(bankName = "BCA", number = "   ")) }
        }

        assertEquals("number", error.field)
        assertTrue("nothing is written", repository.observeAccounts().first().isEmpty())
    }

    /**
     * `ACCOUNT_BAD_NUMBER_FORMAT` — a malformed number is refused with its own type.
     *
     * Its own type rather than the blank-field family, because the two are different fixes:
     * a blank field asks the owner to type something, a malformed one asks them to type
     * something **different**. A caller catching the blank family must not swallow this one
     * and show "wajib diisi" on a field that visibly contains text.
     */
    @Test
    fun `a malformed number is refused`() = runBlocking {
        assertThrows(InvalidAccountNumberException::class.java) {
            runBlocking { repository.save(account(bankName = "BCA", number = "12ab34")) }
        }
        assertThrows(InvalidAccountNumberException::class.java) {
            runBlocking { repository.save(account(bankName = "BCA", number = "1234#5678")) }
        }
        assertThrows(InvalidAccountNumberException::class.java) {
            runBlocking { repository.save(account(bankName = "BCA", number = "---")) }
        }
        assertTrue("nothing is written", repository.observeAccounts().first().isEmpty())
    }

    /**
     * `ACCOUNT_NUMBER_TOO_LONG` — a pasted statement line is refused.
     *
     * The bound exists because this field is filled by pasting from a banking app or a
     * statement, and without it a paste that grabbed a whole page lands silently as a very
     * long "account number".
     */
    @Test
    fun `a number pasted from a statement is refused`() = runBlocking {
        assertThrows(InvalidAccountNumberException::class.java) {
            runBlocking {
                repository.save(
                    account(bankName = "BCA", number = "2026-10-17 REF 998877 1234567890 5000000"),
                )
            }
        }
        assertTrue(repository.observeAccounts().first().isEmpty())
    }

    /**
     * A number the owner's bank would not recognise is **accepted**.
     *
     * This is the assertion that keeps validation format-only. The acceptance criterion says
     * nothing changes when the bank's records disagree with the owner's, so a repository that
     * grew a checksum or a prefix rule would refuse a real account — and the owner would have
     * no recourse. Any digits within the bound are storable.
     */
    @Test
    fun `an unusual but well-formed number is accepted`() = runBlocking {
        repository.save(account(bankName = "Bank Tak Dikenal", number = "0000000000000001"))

        assertEquals(
            "0000000000000001",
            repository.observeAccounts().first().single().number,
        )
    }

    // ------------------------------------------------------------------ the order

    /**
     * The list is ordered by bank, then number.
     *
     * The owner scans for **which bank** first, so the bank is the primary key of the ordering.
     * The number breaks the tie, so the order is total and a row cannot jump when an unrelated
     * one is written.
     */
    @Test
    fun `accounts are listed by bank then number`() = runBlocking {
        repository.save(account(id = "a1", bankName = "Mandiri", number = "222"))
        repository.save(account(id = "a2", bankName = "BCA", number = "999"))
        repository.save(account(id = "a3", bankName = "BCA", number = "111"))

        val stored = repository.observeAccounts().first()
        assertEquals(
            listOf("BCA" to "111", "BCA" to "999", "Mandiri" to "222"),
            stored.map { it.bankName to it.number },
        )
    }

    /** A new id is a UUID-shaped string, and two calls differ. */
    @Test
    fun `new account ids are distinct`() {
        assertNotEquals(repository.newAccountId(), repository.newAccountId())
    }

    // ---------------------------------------------------------------- fixtures

    private fun account(
        id: String = "a1",
        bankName: String = "BCA",
        number: String = "1234567890",
    ) = BusinessAccount(id = id, bankName = bankName, number = number)
}
