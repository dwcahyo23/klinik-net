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

    // -------------------------------------------------------------- Story 3.1

    /**
     * A saved account's confirmation. Names the bank so it is recognisable.
     *
     * The bank rather than the number: the owner knows which bank they just added, and
     * reading a ten-digit number back to them is not a confirmation of anything they can
     * check at a glance.
     */
    fun pengaturanAccountSaved(bankName: String): String =
        context.getString(R.string.pengaturan_account_saved, bankName)

    /** The top bar's subtitle: "2 rekening". */
    fun pengaturanAccountCount(count: Int): String =
        context.getString(R.string.pengaturan_account_count, count)

    /**
     * A blank bank name.
     *
     * Names an example rather than only the field, because the owner is being asked to
     * invent a value and a bare "wajib diisi" leaves them to guess whether the app wants a
     * legal name, a short code, or something else.
     */
    fun pengaturanErrorBlankBank(): String =
        context.getString(R.string.pengaturan_error_blank_bank)

    /**
     * A blank account number.
     *
     * Shorter than the bank's message, deliberately: there is nothing to exemplify — the
     * owner either has the number in front of them or does not — so the message says only
     * what is missing. The format case below is the one that has to explain.
     */
    fun pengaturanErrorBlankNumber(): String =
        context.getString(R.string.pengaturan_error_blank_number)

    /**
     * A number that is not in a storable format.
     *
     * Says which format **is** accepted, because the field visibly contains text and a
     * message that only said "wajib diisi" would read as the app being broken.
     *
     * And it deliberately makes **no claim about the bank**: validation is format only, so
     * a sentence implying the number was verified would be a promise this app cannot keep —
     * and the acceptance criterion is that nothing changes when the bank's records disagree
     * with the owner's. A message that said "nomor tidak dikenali" would be that broken
     * promise in the one place the owner would believe it.
     */
    fun pengaturanErrorNumberFormat(): String =
        context.getString(R.string.pengaturan_error_number_format)

    /**
     * A write that failed for a reason this screen does not model.
     *
     * Says nothing about what went wrong because nothing is known: naming a cause here would
     * be a guess shown to the owner as fact.
     */
    fun pengaturanErrorUnexpected(): String =
        context.getString(R.string.pengaturan_error_unexpected)

    // -------------------------------------------------------------- Story 3.2

    /**
     * A deposit that landed.
     *
     * Takes an **already-formatted** amount, not a `Long`: the caller is the ViewModel,
     * which has no composition and therefore no `stringResource`, and assembling money by
     * hand here would be the second spelling of a rupiah figure on this screen. The
     * acceptance criterion for the numpad says the figure is large and tabular, so the
     * same value has to survive that treatment and this sentence.
     */
    fun depositoSaved(amount: String): String =
        context.getString(R.string.deposito_saved, amount)

    /**
     * A transfer with no account chosen.
     *
     * Lands on the **account field**, so the sheet's `copy(accountError = …)` puts it
     * there — which is why this takes no parameters and returns the bare sentence.
     */
    fun depositoErrorAccountRequired(): String =
        context.getString(R.string.deposito_error_account_required)

    /**
     * The renter has no running contract.
     *
     * The only refusal with no field to blame, so it goes on the sheet's banner rather
     * than on the amount. It names the fix — open or close a contract first — because
     * "there is nothing to bill" on its own leaves the owner with a form that cannot be
     * completed and no idea why.
     */
    fun depositoErrorNoContract(): String =
        context.getString(R.string.deposito_error_no_contract)

    /**
     * An amount that is zero, or digits that are not a figure at all.
     *
     * Says *belum diisi* rather than naming a format, because the case reaching here is
     * the numpad showing `0` — and the numpad's own flash is the immediate answer. This
     * is the sheet-level fallback for a value seeded from outside the pad, where the
     * flash never fired.
     */
    fun depositoErrorAmountZero(): String =
        context.getString(R.string.deposito_error_amount_zero)

    /**
     * A deposit that failed for a reason this screen does not model.
     *
     * Says nothing about what went wrong because nothing is known: naming a cause would
     * be a guess shown to the owner as fact, about money they believe they handed over.
     */
    fun depositoErrorUnexpected(): String =
        context.getString(R.string.deposito_error_unexpected)

    // -------------------------------------------------------------- Story 3.3

    /**
     * `Tunggakan: Rp 750.000 → Rp 250.000`.
     *
     * Takes two **already-formatted** figures. The arrow is the whole point of UX-DR10's
     * format: two numbers side by side make the *change* legible without the owner doing
     * arithmetic, and a sentence assembled per screen is a second place for a rupiah
     * figure to be spelled differently.
     */
    fun depositoPreviewArrears(before: String, after: String): String =
        context.getString(R.string.deposito_preview_arrears, before, after)

    /**
     * The "closes" line, in whichever of its two shapes fits.
     *
     * Two strings rather than one with an optional fragment, because the epic's format has
     * two genuinely different sentences — `Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)`
     * and `Menutup: 3 hari` — and bolting a fragment onto the second would leave a dangling
     * separator when nothing remains to say.
     */
    fun depositoPreviewCloses(daysClosed: Int, daysRemaining: Int, remainingAmount: String): String =
        context.getString(
            R.string.deposito_preview_closes_with_remainder,
            daysClosed,
            daysRemaining,
            remainingAmount,
        )

    /** The no-remainder form. See [depositoPreviewCloses]. */
    fun depositoPreviewClosesOnly(daysClosed: Int): String =
        context.getString(R.string.deposito_preview_closes_only, daysClosed)

    /**
     * `Kredit: Rp 250.000 — dipakai otomatis untuk periode berikutnya`.
     *
     * The explanation is **inline in the sentence**, not a second line. The epic context
     * is explicit that a credit must never appear as a mystery number, and a number on
     * one line with its reason on the next is two things to read where one is enough —
     * on a sheet the owner reads standing up.
     */
    fun depositoPreviewCredit(credit: String): String =
        context.getString(R.string.deposito_preview_credit, credit)

    /**
     * The dialog's title.
     *
     * A question rather than a heading, because the answer is not assumed: the owner may
     * go back with "Cek dulu" rather than confirm.
     */
    fun depositoPreviewTitle(): String =
        context.getString(R.string.deposito_preview_title)

    /**
     * The confirm, and the dismiss.
     *
     * The dismiss reads **"Cek dulu"** — "let me check first" — rather than "Batal"
     * (cancel). Nothing is cancelled: the form is still open with its amount, and the
     * owner has not abandoned anything. A dismiss label that says "cancel" tells them
     * they lost their work, which is the wrong story and the reason they would press
     * confirm instead of looking.
     */
    fun depositoPreviewConfirm(): String =
        context.getString(R.string.deposito_preview_confirm)

    fun depositoPreviewDismiss(): String =
        context.getString(R.string.deposito_preview_dismiss)

    /**
     * A preview that could not be computed.
     *
     * Said plainly and **without offering a save anyway** — the epic context is explicit
     * that a preview showing the wrong thing is worse than no preview, so when the
     * computation fails the right outcome is to say so and leave the form open.
     */
    fun depositoErrorPreviewFailed(): String =
        context.getString(R.string.deposito_error_preview_failed)

    // -------------------------------------------------------------- Story 3.4

    /**
     * `Batalkan setoran Rp 250.000?` — the cancellation dialog's title.
     *
     * Takes an **already-formatted** amount, for the reason every other money accessor
     * here does: the dialog that names a figure and the figure on the row behind it have
     * to be the same figure.
     */
    fun depositoCancelTitle(amount: String): String =
        context.getString(R.string.deposito_cancel_title, amount)

    /**
     * `Tunggakan Pak Budi akan kembali menjadi Rp 750.000.`
     *
     * A sentence naming the renter rather than a second figure with an arrow, because this
     * is the epic's own shape and because a cancellation warns about a consequence while
     * the preview invites a decision — two dialogs that look alike would be two prompts
     * the owner stops reading.
     */
    fun depositoCancelImpact(renterName: String, arrearsAfter: String): String =
        context.getString(R.string.deposito_cancel_impact, renterName, arrearsAfter)

    /**
     * The mandatory reason's refusal.
     *
     * Says *why* the reason is required — the audit trail — rather than only that a field
     * is empty, because the acceptance criterion's own rule is that a cancellation is
     * only meaningful with its explanation.
     */
    fun depositoErrorCancelReasonRequired(): String =
        context.getString(R.string.deposito_error_cancel_reason_required)

    /** A second cancellation of an already-cancelled deposit. */
    fun depositoErrorAlreadyCanceled(): String =
        context.getString(R.string.deposito_error_already_canceled)

    /**
     * `Setoran Rp 250.000 dibatalkan` — the cancellation's confirmation.
     *
     * Distinct from [depositoSaved] because the two announce opposite outcomes of the same
     * gesture, and a snackbar that says "tersimpan" after a cancellation would tell the
     * owner their money was recorded when it was withdrawn from the bill.
     */
    fun depositoCanceled(amount: String): String =
        context.getString(R.string.deposito_cancel_done, amount)

    // -------------------------------------------------------------- Story 3.5

    /**
     * `Setoran Rp 250.000 diperbarui` — the correction's confirmation.
     *
     * Distinct from [depositoSaved] for the reason [depositoCanceled] gives its sibling,
     * and the same rule one level down: after a correction nothing new was *recorded*, so a
     * snackbar reading "tersimpan" would tell the owner a second deposit exists when the
     * one they corrected is the one that changed.
     *
     * Takes an **already-formatted** amount, for the reason every other money accessor on
     * this class does — the ViewModel has no composition, and assembling a rupiah figure
     * here by hand would be the second spelling of it.
     */
    fun depositoCorrected(amount: String): String =
        context.getString(R.string.deposito_corrected, amount)

    /**
     * A correction whose deposit nobody holds.
     *
     * Its own sentence rather than [depositoErrorUnexpected], which reads "Gagal menyimpan
     * setoran" — a failed *save*, about an open that has not saved anything. This is the
     * other half of the rule the cancellation copy above states: the sheet's refusals each
     * name the fault they are refusing, so a correction pointed at a vanished row says so.
     */
    fun depositoErrorNotFound(): String =
        context.getString(R.string.deposito_error_not_found)
}
