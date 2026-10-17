package net.klinik.rental.domain.validation

/**
 * Thrown when a required business-account field is blank.
 *
 * ## Why one subclass per field rather than one exception with a field name
 *
 * `BusinessAccount.init` refuses a blank bank name and a blank number, and both refusals
 * would otherwise arrive at `PengaturanViewModel.saveForm` as an `IllegalArgumentException`
 * carrying only a message string — so the ViewModel could not tell which field was refused
 * and would have to show every message on whichever field it guessed first.
 *
 * One type per field keeps the *fact* ("the number was blank") separate from its wording
 * ("Wajib diisi…"), exactly as [DuplicatePlateException] keeps the colliding plate rather
 * than a rendered sentence. The ViewModel then chooses the field and the copy at the moment
 * it is shown.
 *
 * ## Why these are `IllegalArgumentException` subtypes
 *
 * So a caller catching the supertype keeps working unchanged: a blank field is still a bad
 * argument, and code that does not care *which* field does not have to be rewritten. This is
 * the same choice [BlankRenterFieldException] made, and matching it means the two
 * master-data screens route their blank-field refusals the same way.
 *
 * The message names the field so a log line or a crash report says which one was empty, but
 * it is not shown to the owner — `RStrings` supplies that.
 */
sealed class BlankAccountFieldException(
    val field: String,
    message: String,
) : IllegalArgumentException(message) {

    /** `bankName` — which bank the transfer arrived at. */
    class BlankBankNameException(bankName: String) : BlankAccountFieldException(
        field = "bankName",
        message = "bankName must not be blank, but was '$bankName'",
    )

    /** `number` — the account the transfer landed in. */
    class BlankNumberException(number: String) : BlankAccountFieldException(
        field = "number",
        message = "number must not be blank, but was '$number'",
    )
}
