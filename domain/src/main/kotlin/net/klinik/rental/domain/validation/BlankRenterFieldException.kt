package net.klinik.rental.domain.validation

/**
 * Thrown when a required text field is blank.
 *
 * ## Why three typed exceptions rather than one `IllegalArgumentException`
 *
 * `Renter.init` refuses a blank name, phone and emergency contact, and all three
 * refusals arrive at `PenyewaViewModel.saveForm` as an `IllegalArgumentException`
 * — which carries only the message string. The ViewModel cannot tell which field
 * was refused, so it would have to show every message on whichever field it
 * guessed first.
 *
 * One type per field keeps the *fact* ("the phone was blank") separate from its
 * wording ("Wajib diisi…"), exactly as `DuplicatePlateException` keeps the
 * colliding plate rather than a rendered sentence. The ViewModel then chooses the
 * field and the copy at the moment it is shown.
 *
 * ## Why these are `IllegalArgumentException` subtypes
 *
 * So the existing `catch (e: IllegalArgumentException)` in the repository and in
 * any future caller still holds: a blank field is still a bad argument, and code
 * that does not care *which* field keeps working unchanged.
 *
 * The message names the field so a log line or a crash report says which one was
 * empty, but it is not shown to the owner — `RStrings` supplies that.
 */
sealed class BlankRenterFieldException(
    val field: String,
    message: String,
) : IllegalArgumentException(message) {

    /** `name` — required, and the key that reconciles against bank mutations. */
    class BlankNameException(name: String) : BlankRenterFieldException(
        field = "name",
        message = "name must not be blank, but was '$name'",
    )

    /** `phone` — the number the owner reaches the renter on. */
    class BlankPhoneException(phone: String) : BlankRenterFieldException(
        field = "phone",
        message = "phone must not be blank, but was '$phone'",
    )

    /** `emergencyContact` — who to call when the primary number does not answer. */
    class BlankEmergencyContactException(value: String) : BlankRenterFieldException(
        field = "emergencyContact",
        message = "emergencyContact must not be blank, but was '$value'",
    )
}