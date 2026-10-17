package net.klinik.rental.domain.model

import net.klinik.rental.domain.validation.BlankAccountFieldException

/**
 * A bank account the business receives transfers into.
 *
 * ## Why the number is stored normalised and validated for format only
 *
 * [number] arrives already normalised by
 * [net.klinik.rental.domain.parse.normalizeAccountNumber], so the same account written
 * `1234 5678`, `1234-5678` or `12345678` is one value rather than three. What that
 * function deliberately does **not** do is check the number against a bank — see its KDoc.
 * This model inherits that boundary and must not tighten it: the acceptance criterion is
 * that nothing changes when the bank's records disagree with the owner's, so a "helpful"
 * checksum here would refuse an account the owner's bank accepts.
 *
 * ## Why the bank name is free text
 *
 * The tempting shape is an enum of Indonesian banks. It would be wrong for the same reason
 * the account number is not shape-checked: a closed list needs a schema change for the
 * next bank, and the owner's own spelling — `BCA`, `Bank BCA`, `bca` — is data *they*
 * recognise when scanning their list. The number is the identifier here, not the name, so
 * there is nothing for a canonical spelling to reconcile against.
 *
 * ## Why there is no `isDefault`, no `isActive` and no `status`
 *
 * Story 3.1's acceptance criteria ask for a list the owner adds to and edits. None of them
 * asks for a state on an account, and each of those three fields would be a second source
 * of truth about something the owner did not ask to express. `BusinessAccountTest` asserts
 * their absence by reflection, so the field a later story reaches for is a deliberate
 * addition rather than an accident.
 *
 * [id] is `String` rather than `UUID` because AD-1 restricts `domain/` to `kotlin.*`,
 * `java.time.*` and `kotlinx.coroutines.*`, so `java.util.UUID` is not importable and ids
 * arrive as strings from the layer that owns persistence. The database keeps them `uuid`
 * columns.
 */
data class BusinessAccount(
    /** Stable identity. A `PAYMENT` row will reference this rather than copy the number. */
    val id: String,
    /** The bank, in the owner's own words. Never blank. */
    val bankName: String,
    /** The account number, already normalised. Digits only, never blank. */
    val number: String,
) {
    init {
        // The two required fields are refused with **typed** exceptions rather than
        // `require`, because this constructor is the only place a blank field can be caught:
        // `BusinessAccountRepository.save` takes a `BusinessAccount`, so a blank value cannot
        // reach it — the model has already refused it. A plain `IllegalArgumentException` here
        // would therefore make the repository's typed refusals unreachable dead code, and the
        // ViewModel would have no field to blame.
        //
        // The typed exceptions are `IllegalArgumentException` subtypes, so a caller catching
        // the supertype still holds — the same compatibility argument
        // `BlankRenterFieldException` makes.
        if (bankName.isBlank()) throw BlankAccountFieldException.BlankBankNameException(bankName)
        if (number.isBlank()) throw BlankAccountFieldException.BlankNumberException(number)

        // The id is not owner-visible and has no field to blame, so a plain `require` is right
        // here: a blank id is a programming error rather than something the owner typed.
        require(id.isNotBlank()) { "id must not be blank" }
    }
}
