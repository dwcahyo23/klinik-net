package net.klinik.rental.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The business account's construction rules, and the fields it must not grow.
 *
 * ## Why the negative test carries as much weight as the guards
 *
 * `isDefault`, `isActive` and `status` are the three fields a later story would reach for —
 * "which account do we default to", "is this account still used", "should we hide an old
 * one". Story 3.1's acceptance criteria ask for none of them, and each would be a second
 * source of truth about something the owner did not ask to express: a default flag would
 * have to be kept unique across rows, and an active flag would have to agree with whether
 * any deposit references the account. The reflection test below makes adding one a
 * deliberate act with a failing test attached, rather than a field that appears quietly and
 * is then depended on.
 */
class BusinessAccountTest {

    // -------------------------------------------------------------- the guards

    /**
     * A blank id is refused.
     *
     * The id is what a `PAYMENT` row will reference, so a blank one is an account nothing
     * can point at — refused here rather than at a foreign key three layers down.
     */
    @Test
    fun `a blank id is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            BusinessAccount(id = "", bankName = "BCA", number = "12345678")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BusinessAccount(id = "   ", bankName = "BCA", number = "12345678")
        }
    }

    /**
     * A blank bank name is refused.
     *
     * The bank is what the owner scans for when picking an account, so an account with no
     * bank is a row they cannot identify in their own list.
     */
    @Test
    fun `a blank bank name is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            BusinessAccount(id = "a1", bankName = "", number = "12345678")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BusinessAccount(id = "a1", bankName = "  ", number = "12345678")
        }
    }

    /**
     * And a blank number.
     *
     * The number is the whole point of the row: an account with no number is one the owner
     * cannot reconcile against a bank mutation, which is the job the field exists for.
     */
    @Test
    fun `a blank number is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            BusinessAccount(id = "a1", bankName = "BCA", number = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BusinessAccount(id = "a1", bankName = "BCA", number = "   ")
        }
    }

    /** A valid account constructs, and carries what it was given. */
    @Test
    fun `a valid account constructs`() {
        val account = BusinessAccount(id = "a1", bankName = "BCA", number = "12345678")

        assertEquals("a1", account.id)
        assertEquals("BCA", account.bankName)
        assertEquals("12345678", account.number)
    }

    /**
     * Two accounts at one bank, and one number at two banks, are both constructible.
     *
     * There is no uniqueness rule in this model and there must not be one: the acceptance
     * criterion asks for a *list* the owner adds to. A business account and a payroll
     * account at the same bank are legitimate, and the same number held at two banks is the
     * owner's business rather than a data error. This test is what stops a later reader
     * adding `require` uniqueness "for consistency with vehicle".
     */
    @Test
    fun `the model imposes no uniqueness`() {
        val first = BusinessAccount(id = "a1", bankName = "BCA", number = "111")
        val second = BusinessAccount(id = "a2", bankName = "BCA", number = "222")
        val third = BusinessAccount(id = "a3", bankName = "Mandiri", number = "111")

        assertEquals("BCA", first.bankName)
        assertEquals("BCA", second.bankName)
        assertEquals(first.number, third.number)
    }

    // --------------------------------------------------- the fields it must not grow

    /**
     * No `isDefault` — "which account do we default to".
     *
     * A default would have to be unique across rows to mean anything, which is a constraint
     * no acceptance criterion asks for, and it would let a deposit form pre-select an
     * account the owner did not choose. That is the prototype's hidden-default failure this
     * project has refused twice already (the start date and the end date both open empty for
     * the same reason).
     */
    @Test
    fun `the model declares no default flag`() {
        val declared = BusinessAccount::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("a default account is a hidden default, not a field", declared.contains("isDefault"))
        assertFalse("nor a 'primary' spelling of the same idea", declared.contains("isPrimary"))
    }

    /**
     * No `isActive` — "should we hide an old one".
     *
     * Whether an account is still used is answerable from the deposits that reference it,
     * so a stored flag would be a second source of truth that can disagree with the rows.
     * Story 3.2's delete refusal is the real expression of "this account is in use".
     */
    @Test
    fun `the model declares no active flag`() {
        val declared = BusinessAccount::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("in-use is derived from deposits, never stored", declared.contains("isActive"))
        assertFalse("and so is its inverse", declared.contains("isArchived"))
    }

    /**
     * No `status`, for the reason `Contract` has none either.
     *
     * The project already rejected a stored status on a contract, on the grounds that it
     * duplicates a fact the end date holds. An account has even less need: nothing about it
     * changes state.
     */
    @Test
    fun `the model declares no status`() {
        val declared = BusinessAccount::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("status must be derived, never stored", declared.contains("status"))
    }
}
