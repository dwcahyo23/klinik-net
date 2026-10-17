package net.klinik.rental.ui.strings

import android.content.Context
import androidx.annotation.StringRes
import net.klinik.rental.R
import net.klinik.rental.ui.format.formatTanggalBulan

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

    // -------------------------------------------------------------- Story 2.5

    /**
     * A saved contract's confirmation. Names the vehicle so it is recognisable —
     * the plate is what the owner sees in the fleet list they just came from.
     */
    fun kontrakSaved(plate: String): String =
        context.getString(R.string.kontrak_saved, plate)

    /**
     * A vehicle that already has an open contract.
     *
     * The exception carries the plate read back from the vehicle row, not the id
     * the form held, so the message names a car that exists in the fleet list. The
     * copy names both the fault and the fix: the car is out, and the way to free it
     * is to end the running contract — which is Story 2.7's screen, and the message
     * says so rather than leaving the owner to work it out.
     */
    fun kontrakErrorVehicleBusy(plate: String): String =
        context.getString(R.string.kontrak_error_vehicle_busy, plate)

    /**
     * A renter who already has an open contract.
     *
     * The renter-side twin of [kontrakErrorVehicleBusy], and deliberately a
     * separate sentence: "that car is already out" and "that person is already
     * renting something" need different actions from the owner, and one wording for
     * both would tell them less than the exception knows.
     */
    fun kontrakErrorRenterBusy(name: String): String =
        context.getString(R.string.kontrak_error_renter_busy, name)

    /**
     * A zero or negative rate.
     *
     * The same wording the fleet screen uses, and no fallback figure is named
     * anywhere in it: the prototype's `parseInt(x) || 250000` produced a plausible
     * number, and telling the owner "we used Rp 250.000" would be inventing a price
     * nobody agreed to. A contract is where that invention would be most expensive,
     * because the frozen rate is what every later charge is computed from.
     */
    fun kontrakErrorRate(): String =
        context.getString(R.string.kontrak_error_rate_zero)

    /** An unpicked renter — a required selection left empty. */
    fun kontrakErrorBlankRenter(): String =
        context.getString(R.string.kontrak_error_blank_renter)

    /** An unpicked vehicle, the same class of fault on the other picker. */
    fun kontrakErrorBlankVehicle(): String =
        context.getString(R.string.kontrak_error_blank_vehicle)

    /** An unpicked start date. */
    fun kontrakErrorBlankDate(): String =
        context.getString(R.string.kontrak_error_blank_date)

    /**
     * A write that failed for a reason this screen does not model.
     *
     * Says nothing about what went wrong because nothing is known: naming a cause
     * here would be a guess shown to the owner as fact. The epic context is explicit
     * that a failed write must fail loudly rather than reset to defaults — data that
     * disappears and looks like a fresh account is worse than a message that admits
     * it does not know the reason.
     */
    fun kontrakErrorUnexpected(): String =
        context.getString(R.string.kontrak_error_unexpected)

    // -------------------------------------------------------------- Story 2.6

    /**
     * Formats a marked day the one way this screen spells a date.
     *
     * ## Why this is on `RStrings` rather than left to the caller
     *
     * The two places that name an exception's date — the confirmation and the
     * duplicate refusal — are reached from the ViewModel, which has no composition
     * and therefore no `stringResource`. So the formatter that produces `14 Okt` has
     * to be callable from here, and putting it here is what keeps it *the same*
     * formatter the sheet's rows use: one path, so the date the owner is told about
     * and the date the owner sees in the list cannot differ.
     *
     * The **month** form, not the weekday form — see `formatTanggalBulan`, and the
     * two exceptions on `14 Jum` and `14 Okt` that a weekday cannot tell apart.
     */
    fun formatExceptionDate(date: java.time.LocalDate): String =
        formatTanggalBulan(date)

    /** A marked day's confirmation. Names the day, which is what the owner just chose. */
    fun kontrakExceptionAdded(date: String): String =
        context.getString(R.string.kontrak_exception_added, date)

    /**
     * And the confirmation for a removal, naming the day that is billable again.
     *
     * Takes a `LocalDate` rather than an already-formatted `String` — unlike the two
     * *refusals* above, which receive the raw date from an exception and format it
     * here — because this one is handed a date that may already be `null`: the row is
     * gone from Room's flow by the time the dialog is confirmed, so the ViewModel
     * reads it *before* the delete. A `null` reads as the sentence without the day,
     * which is still grammatical and does not need a placeholder that would look like
     * a data problem.
     */
    fun kontrakExceptionRemoved(date: java.time.LocalDate?): String =
        context.getString(
            R.string.kontrak_exception_removed,
            date?.let(::formatExceptionDate).orEmpty(),
        )

    /**
     * A date this contract already carries an exception for.
     *
     * The exception carries the *stored* date, read back from the row that holds the
     * `(contractId, date)` pair rather than echoed from the submitted value — the same
     * rule [kontrakErrorVehicleBusy] and [kendaraanErrorDuplicatePlate] follow. After
     * the repository's pre-check or after its translation of the index's refusal, the
     * date in the message always comes from a row that exists in the sheet's list.
     */
    fun kontrakErrorExceptionDuplicate(date: java.time.LocalDate): String =
        context.getString(R.string.kontrak_error_exception_duplicate, formatExceptionDate(date))

    /**
     * A day before the contract began.
     *
     * Names the contract's **own start date**, not the offending day, because the fix
     * is "pick a day on or after the contract began" and the owner cannot act on that
     * without knowing which day that was — and the day they typed is already sitting
     * in the field they typed it into.
     */
    fun kontrakErrorExceptionBeforeStart(startDate: java.time.LocalDate): String =
        context.getString(R.string.kontrak_error_exception_before_start, formatExceptionDate(startDate))

    /**
     * An unpicked day.
     *
     * Not the shared `KnFieldError.Required` wording the two pickers use, because
     * this is not a picker: it is a date the owner has to *choose* from a calendar,
     * and the fix in the sentence is the one that names which calendar.
     */
    fun kontrakErrorExceptionBlankDate(): String =
        context.getString(R.string.kontrak_error_exception_blank_date)

    /**
     * A blank reason.
     *
     * Not the shared required-field wording either, and deliberately: a blank reason
     * is not a field the owner forgot but a *piece of text this row carries* — the
     * acceptance criterion requires the exception to be shown with its reason, and a
     * row with an empty "keterangan" line is indistinguishable from a row whose reason
     * was lost. So the message says what to write in it rather than only that
     * something is absent.
     */
    fun kontrakErrorExceptionBlankReason(): String =
        context.getString(R.string.kontrak_error_exception_blank_reason)

    /**
     * A contract that has ended.
     *
     * One wording for both the add and the remove refusal, because they are one fact:
     * this contract is finished. It has no field of its own to land on — the whole add
     * block is about to go away — so it lands on the sheet's own banner, in the window
     * that is actually on top.
     */
    fun kontrakErrorExceptionEnded(): String =
        context.getString(R.string.kontrak_error_exception_ended)

    /**
     * A write that failed for a reason this screen does not model.
     *
     * Says nothing about what went wrong because nothing is known: naming a cause here
     * would be a guess shown to the owner as fact. It reads "pengecualian" where
     * [kontrakErrorUnexpected] reads "menyimpan", so the owner knows *which* write
     * failed when both screens have banners in their own windows.
     */
    fun kontrakErrorExceptionUnexpected(): String =
        context.getString(R.string.kontrak_error_exception_unexpected)

    // -------------------------------------------------------------- Story 2.7

    /**
     * A contract that was ended.
     *
     * Takes an **already-formatted** date rather than a `LocalDate`, unlike the two
     * exception refusals above — because this one is built from a value the owner just
     * chose and which is already in the sheet's field, not read back out of a row that
     * failed to write. [formatExceptionDate] is still the formatter behind it, called by
     * the ViewModel, so the date the owner is *told* about and the date they *see* in
     * the field come from one place.
     */
    fun kontrakContractEnded(date: String): String =
        context.getString(R.string.kontrak_contract_ended, date)

    /**
     * An end date before the contract began.
     *
     * Names the contract's own **start date**, for the same reason
     * [kontrakErrorExceptionBeforeStart] does: the offending end date is already in the
     * field the owner typed it into, and the fix is not actionable without knowing which
     * day the rental started.
     */
    fun kontrakErrorContractEndBeforeStart(startDate: java.time.LocalDate): String =
        context.getString(
            R.string.kontrak_error_contract_end_before_start,
            formatExceptionDate(startDate),
        )

    /**
     * A date never chosen.
     *
     * Says *pick a date and send again* rather than only that something is absent,
     * because the whole sheet is one field: "required" would name the fault and leave
     * the owner to work out that the calendar icon is the way to answer it.
     */
    fun kontrakErrorContractEndBlankDate(): String =
        context.getString(R.string.kontrak_error_contract_end_blank_date)

    /**
     * A contract that has already ended — refused for a second end, and for any edit
     * of a finished contract.
     *
     * One wording for both because they are one fact, and the same
     * [kontrakErrorExceptionEnded] argument applies in reverse: two messages for one
     * state is how a screen ends up saying less somewhere.
     */
    fun kontrakErrorContractNotOpen(): String =
        context.getString(R.string.kontrak_error_contract_not_open)

    /**
     * An end that failed for a reason this screen does not model.
     *
     * Reads "mengakhiri kontrak" where [kontrakErrorExceptionUnexpected] reads
     * "pengecualian" and [kontrakErrorUnexpected] reads "menyimpan", so the owner knows
     * *which* write failed when three banners exist in three windows.
     */
    fun kontrakErrorContractEndUnexpected(): String =
        context.getString(R.string.kontrak_error_contract_end_unexpected)

    // -------------------------------------------------------------- Story 2.8

    /**
     * The app could not read its own stored data.
     *
     * ## Why this is the only 2.8 message here
     *
     * Every other string this story needs is chrome — nav labels, the retry
     * button — and every other screen already resolves that copy with
     * `stringResource` at the composable, exactly as `Navigation.kt` does for its
     * own back description. Only this one is a *domain* failure: something below
     * the UI raised it, and it belongs with the other domain messages.
     *
     * ## Why the copy refuses to sound like an empty account
     *
     * The epic context's rule is that a failure may never look like a fresh
     * account, and the prototype broke exactly that — `init()` swallowed a parse
     * error and reset to defaults, so the owner saw an empty app, re-entered
     * their fleet, and overwrote history. So this names the failure and the
     * consequence, and it does not offer a blank slate.
     */
    fun dataReadFailed(): String =
        context.getString(R.string.error_read_title)
}