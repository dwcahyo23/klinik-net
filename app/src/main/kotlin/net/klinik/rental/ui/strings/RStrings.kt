package net.klinik.rental.ui.strings

import android.content.Context
import androidx.annotation.StringRes
import net.klinik.rental.R

/**
 * Indonesian copy for the screens that raise typed domain exceptions.
 *
 * ## Why this class exists rather than `stringResource` at the call site
 *
 * The repository returns a typed exception, never a `Result<_, String>` — the
 * epic context forbids carrying a domain message in a `Result`, because a
 * `String` that is already Indonesian cannot be re-checked or re-worded. So
 * something has to turn each exception into text, and that something cannot be a
 * `@Composable` helper: [KendaraanViewModel] has no composition, and resolving
 * strings there would need a `Context`.
 *
 * This is that something. The copy still lives in `res/values/strings.xml` —
 * which is the rule that actually matters — and every accessor is a named
 * function rather than a string literal, so a screen cannot invent a third
 * wording for one class of fault.
 *
 * It is a thin wrapper, deliberately: no string is assembled here that does not
 * come from a resource, and no mapping is cached, because a cached string
 * outliving a configuration change is how a translated app starts showing the
 * previous locale.
 *
 * @param context used only to resolve resources. Held weakly by nothing here —
 *   the caller owns its lifetime, so this must not outlive the Activity in
 *   practice; the ViewModels receive one from `MainActivity`, which lives
 *   exactly as long as the composition that created it.
 */
class RStrings(private val context: Context) {

    /** A saved vehicle's confirmation. Names the plate so it is recognisable. */
    fun kendaraanSaved(plate: String): String =
        context.getString(R.string.kendaraan_saved, plate)

    /** The fleet count on the top bar: "3 mobil". */
    fun kendaraanCount(count: Int): String =
        context.getString(R.string.kendaraan_count, count)

    /**
     * A plate that is already stored.
     *
     * The exception carries the *stored* plate, not the one the owner typed —
     * after normalisation they are the same string, and naming the stored value
     * means the message points at a row that actually exists.
     */
    fun kendaraanErrorDuplicatePlate(plate: String): String =
        context.getString(R.string.kendaraan_error_duplicate_plate, plate)

    /**
     * A zero or negative rate.
     *
     * No fallback figure is named anywhere in this message: the prototype's
     * `parseInt(x) || 250000` produced a plausible number, and telling the owner
     * "we used Rp 250.000" would be inventing a price nobody agreed to.
     */
    fun kendaraanErrorRateZero(): String =
        context.getString(R.string.kendaraan_error_rate_zero)

    fun kendaraanErrorBlankPlate(): String =
        context.getString(R.string.kendaraan_error_blank_plate)

    fun kendaraanErrorBlankYear(): String =
        context.getString(R.string.kendaraan_error_blank_year)

    /** A vehicle still under contract cannot be deleted. Names the vehicle. */
    fun kendaraanErrorHasContract(plate: String): String =
        context.getString(R.string.kendaraan_error_has_contract, plate)

    fun kendaraanErrorUnexpected(): String =
        context.getString(R.string.kendaraan_error_unexpected)

    // -------------------------------------------------------------- Story 2.4

    /** A saved renter's confirmation. Names the person so it is recognisable. */
    fun penyewaSaved(name: String): String =
        context.getString(R.string.penyewa_saved, name)

    /**
     * A name that is already stored by a *different* renter.
     *
     * The exception carries the *stored* name, not the one the owner typed.
     * Matching here is exact rather than normalised — a plate has one canonical
     * form and a person's name does not — so the two are the same string, and
     * naming the stored value means the message points at a row that actually
     * exists in the list behind the sheet.
     */
    fun penyewaErrorDuplicateName(name: String): String =
        context.getString(R.string.penyewa_error_duplicate_name, name)

    /**
     * A blank or whitespace-only name.
     *
     * The wording is the shared required-field one rather than a renter-specific
     * synonym: a blank name *is* a required field left empty, and two wordings
     * for one class of fault is how a screen ends up saying less somewhere.
     */
    fun penyewaErrorBlankName(): String =
        context.getString(R.string.penyewa_error_blank_name)

    /**
     * A blank phone number, and a blank emergency contact.
     *
     * Both resolve to the same wording, deliberately: they are one class of fault
     * — a required number the owner did not supply — and the difference is which
     * field the message lands on, not what it says. The field is chosen by
     * `BlankRenterFieldException.field`, not by the copy.
     */
    fun penyewaErrorBlankPhone(): String =
        context.getString(R.string.penyewa_error_blank_phone)

    fun penyewaErrorBlankEmergency(): String =
        context.getString(R.string.penyewa_error_blank_emergency)

    /**
     * A write that failed for a reason this screen does not model.
     *
     * Says nothing about what went wrong because nothing is known: naming a
     * cause here would be a guess shown to the owner as fact. The epic context is
     * explicit that a failed write must fail loudly rather than reset to
     * defaults — data that disappears and looks like a fresh account is worse
     * than a message that admits it does not know the reason.
     */
    fun penyewaErrorUnexpected(): String =
        context.getString(R.string.penyewa_error_unexpected)
}