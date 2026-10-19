package net.klinik.rental.ui.strings

import android.content.Context
import androidx.annotation.StringRes
import net.klinik.rental.R
import net.klinik.rental.data.backup.BackupJsonReader
import net.klinik.rental.domain.calculate.RiskLevel
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

    /**
     * The context every accessor reads through.
     *
     * Exposed for the one caller that needs more than a string: `PengaturanViewModel.exportTo`
     * writes a backup file and needs a `ContentResolver`. Reaching for a *separate* context
     * field there instead would mean this class's constructor takes two contexts that must be
     * the same object, which is a fact nothing would check.
     *
     * It is `getApplicationContext()` behind the constructor — see the factory below — so a
     * caller holding this cannot leak an Activity.
     */
    val contextRef: Context get() = context.applicationContext

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

    // -------------------------------------------------------------- Story 3.6

    /**
     * `Tandai Kontrak Budi Santoso · B 1235 TF lunas sampai 1 Mar 2026?` — the epic's
     * title, verbatim in shape.
     *
     * Both figures are **already-formatted** strings rather than dates: the ViewModel
     * holds no composition, so it has to produce the same text this dialog renders, and
     * a rupiah or a date spelled in two places is one that can differ in two places.
     */
    fun kontrakSettleTitle(contractLabel: String, date: String): String =
        context.getString(R.string.kontrak_settle_title, contractLabel, date)

    /**
     * `Menghapus Rp 7.000.000 tunggakan (28 hari) dari daftar ini.`
     *
     * Takes an already-formatted amount, for the reason every other money accessor here
     * does.
     */
    fun kontrakSettleImpact(amount: String, days: Int): String =
        context.getString(R.string.kontrak_settle_impact, amount, days)

    /** The figures that *survive*, so the owner can tell a right settlement from a total one. */
    fun kontrakSettleRemaining(amount: String, days: Int): String =
        context.getString(R.string.kontrak_settle_remaining, amount, days)

    /**
     * A settlement date that closes no charge day.
     *
     * Names both likely causes — an earlier settlement, and a Pengecualian Servis — so the
     * owner does not have to guess which one already accounted for those days.
     */
    fun kontrakSettleClosesNothing(): String =
        context.getString(R.string.kontrak_settle_closes_nothing)

    /**
     * A settlement date before the contract began.
     *
     * Names the contract's own **start date**, for the reason
     * [kontrakErrorContractEndBeforeStart] does: the offending date is already in the
     * field the owner picked it in.
     */
    fun kontrakErrorSettleBeforeStart(startDate: java.time.LocalDate): String =
        context.getString(
            R.string.kontrak_error_settle_before_start,
            formatExceptionDate(startDate),
        )

    /**
     * A settlement dated earlier than one the contract already carries.
     *
     * Takes the **existing** date, not the rejected one: the rejected date is still in the
     * field the owner picked it into, and the existing marker is nowhere on screen. So the
     * date this message needs is the one they cannot see.
     *
     * Takes an already-formatted string like the sibling refusals — the ViewModel has no
     * composition, and a date spelled in two places is one that can differ in two places.
     */
    fun kontrakErrorSettleAlreadySettled(existing: String): String =
        context.getString(R.string.kontrak_error_settle_already_settled, existing)

    /** A settlement date never chosen. */
    fun kontrakErrorSettleBlankDate(): String =
        context.getString(R.string.kontrak_error_settle_blank_date)

    /**
     * A settlement that failed for a reason this screen does not model.
     *
     * Reads "menandai kontrak lunas" where [kontrakErrorContractEndUnexpected] reads
     * "mengakhiri kontrak" — four banners now live in four windows, and the owner needs
     * to know which write failed.
     */
    fun kontrakErrorSettleUnexpected(): String =
        context.getString(R.string.kontrak_error_settle_unexpected)

    /**
     * `28 hari ditandai lunas sampai 1 Mar` — the confirmation.
     *
     * Names the day and the count so the message is an audit fact rather than a
     * reassurance: FR-9b leaves no other trace of the action.
     */
    fun kontrakSettled(date: String, days: Int): String =
        context.getString(R.string.kontrak_settled, date, days)

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

    // -------------------------------------------------------------- Story 4.4

    // Only the two **messages** get accessors here. The section's heading, its action and the
    // sheet's three field labels are read with `stringResource` inside the composables that
    // draw them, because those live in a composition and a ViewModel has none — an accessor
    // for each would be a second way to spell a string that a composable never calls, and
    // eight uncalled accessors read as protection while protecting nothing. Every other
    // screen's own copy follows the same rule; only what the ViewModel assembles comes here.

    /**
     * The blank-name refusal.
     *
     * Says what to write **and** what the name is for, for the reason every other refusal here
     * does: "wajib diisi" beside a field the owner believes is already filled is baffling, and
     * this one matters because the value ends up in a message a tenant reads.
     */
    fun pengaturanErrorBlankIdentityName(): String =
        context.getString(R.string.pengaturan_error_blank_identity_name)

    /**
     * The confirmation, naming the identity that was saved.
     *
     * Takes an **already-formatted** name, for the reason every other money-or-name accessor
     * here does: the confirmation the owner reads and the name on the section header must be
     * the same string, or the screen contradicts itself within one glance — the header would
     * still show the old name while the snackbar named the new one.
     */
    fun pengaturanIdentitySaved(name: String): String =
        context.getString(R.string.pengaturan_identity_saved, name)

    // -------------------------------------------------------------- Story 5.1

    /**
     * The export's success message, naming the file that was written.
     *
     * A **message**, so it gets an accessor rather than a `stringResource` at the call site —
     * the rule this file's own KDoc states. It names the file because "your backup is done" and
     * "your backup is done, at this path, under this name" are different assurances, and the
     * second is the one the owner needs when they go looking for the file next month.
     */
    fun pengaturanExportSaved(filename: String): String =
        context.getString(R.string.pengaturan_export_saved, filename)

    /**
     * A failed export, naming the fix.
     *
     * Deliberately distinct from [pengaturanErrorUnexpected]: that one reads as a form problem,
     * and an owner who cannot tell a failed backup from a rejected account will assume their
     * data is safe when it is not.
     */
    fun pengaturanErrorExportFailed(): String =
        context.getString(R.string.pengaturan_error_export_failed)

    // -------------------------------------------------------------- Story 5.2

    /**
     * The preview's first line: `18 mobil · 14 penyewa · 312 setoran`.
     *
     * Assembled in the ViewModel rather than the dialog because it is three numbers and three
     * nouns in one sentence, and a composable that built it would rebuild the pluralisation on
     * every recomposition.
     */
    fun pengaturanRestoreCounts(vehicles: Int, renters: Int, payments: Int): String =
        context.getString(R.string.pengaturan_restore_counts, vehicles, renters, payments)

    /** `Periode terakhir: 24 Mar 2026`. */
    fun pengaturanRestorePeriod(date: String): String =
        context.getString(R.string.pengaturan_restore_period, date)

    /** The sentence for a file with no deposits — not an epoch date. */
    fun pengaturanRestorePeriodEmpty(): String =
        context.getString(R.string.pengaturan_restore_period_empty)

    /** The restore succeeded. */
    fun pengaturanRestoreDone(): String = context.getString(R.string.pengaturan_restore_done)

    /**
     * A refusal, chosen from the reader's own reason.
     *
     * ## Why this is one function with a `when` rather than five accessors
     *
     * Because the caller holds a [RestoreRejected], not a decision — the ViewModel catches the
     * exception and asks "what does this mean to the owner". Five call sites would each have to
     * remember which reason maps to which sentence, and adding a sixth reason would add a sixth
     * call site rather than a compile error in one `when`.
     */
    fun pengaturanRestoreRefused(reason: String, fileVersion: Int?, appVersion: Int): String =
        when (reason) {
            // The version message is the only one that names figures, because it is the only
            // refusal whose cause the owner can see for themselves.
            BackupJsonReader.REASON_VERSION -> context.getString(
                R.string.pengaturan_restore_error_version,
                fileVersion ?: 0,
                appVersion,
            )
            BackupJsonReader.REASON_MISSING_TABLE ->
                context.getString(R.string.pengaturan_restore_error_missing_table)
            BackupJsonReader.REASON_BAD_ROW ->
                context.getString(R.string.pengaturan_restore_error_bad_row)
            // NOT_JSON and NO_VERSION share a sentence: from the owner's side "this is not JSON"
            // and "this JSON has no version" are the same next action — pick another file — and
            // two messages that differ only in a word the owner cannot act on are noise.
            else -> context.getString(R.string.pengaturan_restore_error_not_json)
        }

    /** The file could not be opened at all, which is a different next action from "wrong file". */
    fun pengaturanRestoreUnreadable(): String =
        context.getString(R.string.pengaturan_restore_error_unreadable)

    // -------------------------------------------------------------- Story 5.3

    /** A wrong PIN. */
    fun pinErrorWrong(): String = context.getString(R.string.pin_error_wrong)

    /** The countdown, while input is refused. */
    fun pinErrorLockedOut(seconds: Int): String =
        context.getString(R.string.pin_error_locked_out, seconds)

    /**
     * The check could not run.
     *
     * Deliberately distinct from [pinErrorWrong]: an owner told "wrong PIN" when the read failed
     * will keep retyping a correct code, which is the one behaviour that cannot help.
     */
    fun pinErrorUnavailable(): String = context.getString(R.string.pin_error_unavailable)

    /** A wrong recovery code. */
    fun pinErrorWrongCode(): String = context.getString(R.string.pin_error_wrong_code)

    /** The new PIN is not four digits. */
    fun pinErrorBadNewPin(): String = context.getString(R.string.pin_error_bad_new_pin)

    /** The setup flow's one-time code, shown before the PIN is written. */
    fun pinSetupCodeBody(): String = context.getString(R.string.pin_setup_code_body)

    /** The setup flow's mismatch refusal. */
    fun pinSetupMismatchBody(): String = context.getString(R.string.pin_setup_mismatch_body)

    /** The lock was turned on. */
    fun pinEnabledMessage(): String = context.getString(R.string.pin_enabled_message)

    /** The lock was turned off. */
    fun pinDisabledMessage(): String = context.getString(R.string.pin_disabled_message)

    // -------------------------------------------------------------- Story 5.4

    /** The card's own line: which day is in force. */
    fun pengaturanCutoffCurrent(day: Int): String =
        context.getString(R.string.pengaturan_cutoff_current, day)

    /**
     * How many contracts move — and the plural is three separate strings rather than a count
     * folded into one sentence.
     *
     * Indonesian has one plural form for a counted noun, so `0`/`1`/`n` collapse naturally; but
     * "0 kontrak" and "1 kontrak" read as *complete* claims, and the preview's whole job is to
     * say whether anything moves. Three strings make that unmistakable.
     */
    fun pengaturanCutoffNoContracts(): String =
        context.getString(R.string.pengaturan_cutoff_no_contracts)

    fun pengaturanCutoffOneContract(): String =
        context.getString(R.string.pengaturan_cutoff_one_contract)

    fun pengaturanCutoffManyContracts(count: Int): String =
        context.getString(R.string.pengaturan_cutoff_many, count)

    /** The delta, with its direction **in words**. */
    fun pengaturanCutoffUp(amount: String): String =
        context.getString(R.string.pengaturan_cutoff_up, amount)

    fun pengaturanCutoffDown(amount: String): String =
        context.getString(R.string.pengaturan_cutoff_down, amount)

    fun pengaturanCutoffNoChange(): String =
        context.getString(R.string.pengaturan_cutoff_no_change)

    /** The two period labels, from `CutoffPeriod.label` — never spelled here. */
    fun pengaturanCutoffPeriods(old: String, new: String): String =
        context.getString(R.string.pengaturan_cutoff_periods, old, new)

    /** The refusal for a day outside 1–28, naming the range. */
    fun pengaturanCutoffInvalid(): String =
        context.getString(R.string.pengaturan_cutoff_invalid)

    /** The confirmation, naming the day that was written. */
    fun pengaturanCutoffSaved(day: Int): String =
        context.getString(R.string.pengaturan_cutoff_saved, day)

    // -------------------------------------------------------------- Story 5.5

    /**
     * The reminder's body — one sentence, no urgency.
     *
     * "Belum ada cadangan hari ini" states the fact; "Data Anda bisa hilang kapan saja" would be
     * the app manufacturing fear to make the owner act, and the epic's whole argument against
     * automation is that it pressures rather than informs.
     */
    fun berandaBackupReminderBody(): String =
        context.getString(R.string.beranda_backup_reminder_body)

    /** The last export date, **only when there is one** — the AC says "kalau ada". */
    fun berandaBackupReminderLast(date: String): String =
        context.getString(R.string.beranda_backup_reminder_last, date)

    /** The dialog's explanation: what is at stake, and what the file is for. */
    fun berandaBackupReminderDialogBody(): String =
        context.getString(R.string.beranda_backup_reminder_dialog_body)

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

    // -------------------------------------------------------------- Deposit history

    /**
     * Transfer Bank / Tunai — a row's method, spelled the sheet spells it.
     *
     * The same two strings the deposit sheet's chips use, read through this class rather
     * than resolved twice: a method shown as "Transfer Bank" on the form and "Transfer"
     * on a history row is two spellings of one fact, and the owner is the one who has to
     * notice.
     */
    fun depositoMethodTransfer(): String =
        context.getString(R.string.deposito_method_transfer)

    fun depositoMethodCash(): String =
        context.getString(R.string.deposito_method_cash)

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

    // -------------------------------------------------------------- Story 4.1

    /**
     * `Rp 750.000 · 3 hari` — the arrears row's figure line, the AC's exact shape.
     *
     * Takes two **already-formatted** halves rather than the raw rupiah and the count,
     * because this class is the ViewModel's only route to text and the rupiah is formatted by
     * the one `formatRupiah` call the screen makes. Passing a `Long` here would put a second
     * spelling of a money figure on this class, which is the thing every other money
     * accessor above refuses.
     */
    fun tunggakanRowFigure(amount: String, days: String): String =
        context.getString(R.string.tunggakan_row_figure, amount, days)

    /**
     * `3 hari`.
     *
     * Takes the count rather than a formatted string, because "hari" is the same word for
     * every count including one — a format the whole app can therefore only spell one way.
     */
    fun tunggakanRowDays(days: Int): String =
        context.getString(R.string.tunggakan_row_days, days)

    /**
     * The ladder's own word for a risk level.
     *
     * A `when` over the enum rather than four call sites, for the reason
     * `Destination.icon` documents: one place maps a domain value to its display, so the word
     * beside the badge and the colour of the badge cannot come from two ladders. Exhaustive
     * on purpose — a new `RiskLevel` breaks this rather than inheriting the last branch.
     */
    fun tunggakanRiskLabel(risk: RiskLevel): String = when (risk) {
        RiskLevel.LANCAR -> context.getString(R.string.tunggakan_risk_lancar)
        RiskLevel.RINGAN -> context.getString(R.string.tunggakan_risk_ringan)
        RiskLevel.PERHATIAN -> context.getString(R.string.tunggakan_risk_perhatian)
        RiskLevel.KRITIS -> context.getString(R.string.tunggakan_risk_kritis)
    }

    /**
     * The oldest still-open charge day as `14 Okt`, or `null` when no day is open.
     *
     * Delegates to [formatExceptionDate] rather than spelling `formatTanggalBulan` a second
     * time: this is the same "14 Okt" the exception sheet and the contract end sheet draw, and
     * a date the owner reads three ways on one screen is a date they stop trusting.
     */
    fun formatOldestOpenDay(date: java.time.LocalDate?): String? =
        date?.let(::formatExceptionDate)

    /** The top bar's subtitle: how many people are in the list. */
    fun tunggakanCount(renters: Int): String =
        context.getString(R.string.tunggakan_count, renters)

    /** A calculation that could not be completed. Never drawn as an empty list. */
    fun tunggakanError(): String =
        context.getString(R.string.tunggakan_error)

    /** The empty state's title. */
    fun tunggakanEmptyTitle(): String =
        context.getString(R.string.tunggakan_empty_title)

    /** The empty state's body — what to do, per the epic context's rule. */
    fun tunggakanEmptyBody(): String =
        context.getString(R.string.tunggakan_empty_body)

    /** The detail pane's heading. */
    fun tunggakanDetailTitle(): String =
        context.getString(R.string.tunggakan_detail_title)

    /** The detail pane's prompt when no row is selected. */
    fun tunggakanDetailEmpty(): String =
        context.getString(R.string.tunggakan_detail_empty)

    /** The detail pane's heading row: `Budi Santoso · B 1235 TF`. */
    fun tunggakanDetailLabel(renter: String, plate: String): String =
        context.getString(R.string.tunggakan_detail_label, renter, plate)

    /** The detail pane's "nothing open" line. */
    fun tunggakanDetailOldestNone(): String =
        context.getString(R.string.tunggakan_detail_oldest_none)

    // -------------------------------------------------------------- Story 4.2

    /**
     * The reminder message itself, from four **already-resolved** pieces.
     *
     * A function parameter rather than a `Context` in `ReminderMessage`, so the composition rule
     * stays pure and testable while the wording stays in the string table. The figure is the
     * row's own `figureText`, so the number the owner reads and the number the tenant receives
     * are one rendering.
     */
    fun tunggakanReminderMessage(
        renter: String,
        business: String,
        plate: String,
        figure: String,
    ): String = context.getString(
        R.string.tunggakan_reminder_message,
        renter,
        business,
        plate,
        figure,
    )

    fun tunggakanReminderTitle(): String =
        context.getString(R.string.tunggakan_reminder_title)

    /** The row's verb — a word, not an icon, for the same reason `Akhiri` is spelled out. */
    fun tunggakanReminderAction(): String =
        context.getString(R.string.tunggakan_reminder_action)

    fun tunggakanReminderFieldLabel(): String =
        context.getString(R.string.tunggakan_reminder_field_label)

    fun tunggakanReminderHint(): String =
        context.getString(R.string.tunggakan_reminder_hint)

    fun tunggakanReminderSend(): String =
        context.getString(R.string.tunggakan_reminder_send)

    fun tunggakanReminderCopy(): String =
        context.getString(R.string.tunggakan_reminder_copy)

    fun tunggakanReminderCopied(): String =
        context.getString(R.string.tunggakan_reminder_copied)

    fun tunggakanReminderClose(): String =
        context.getString(R.string.tunggakan_reminder_close)

    /** The empty-text refusal. Says what to do, not only that something is missing. */
    fun tunggakanReminderEmpty(): String =
        context.getString(R.string.tunggakan_reminder_empty)

    /**
     * The phone cannot address anyone — a different fix from an empty message.
     *
     * Distinct copy because the two failures have different repairs: this one is corrected in
     * the renter's own record, not in the field the owner is looking at.
     */
    fun tunggakanReminderNoPhone(): String =
        context.getString(R.string.tunggakan_reminder_no_phone)

    /** The business identity is not configured, so a reminder would be signed by nobody. */
    fun tunggakanReminderNoIdentity(): String =
        context.getString(R.string.tunggakan_reminder_no_identity)

    /** WhatsApp could not be opened; the sentence points at Copy as the way out. */
    fun tunggakanReminderOpenFailed(): String =
        context.getString(R.string.tunggakan_reminder_open_failed)

    // -------------------------------------------------------------- Story 4.3

    /**
     * The fleet split, as "12 · 3 · 1" — available, rented, in service.
     *
     * Three counts in one string rather than three numbers the screen places, for the reason
     * the arrears row's figure line is one string: a figure assembled in two places is one
     * that can be assembled differently.
     */
    fun berandaFleetSplit(available: Int, rented: Int, service: Int): String =
        context.getString(R.string.beranda_fleet_split, available, rented, service)

    /**
     * The collection rate, as a whole percentage — "75%".
     *
     * The number arrives already whole, so this formats rather than rounds: a rate that was
     * rounded here and rounded again on the tile would be two roundings of one figure.
     */
    fun berandaRate(percent: Int): String =
        context.getString(R.string.beranda_rate, percent)

    /**
     * The trend card's subtitle — "14 hari · total Rp 8.400.000".
     *
     * Takes the **formatted** total rather than a `Long`, because the figure is formatted once,
     * by the ViewModel, and this composes the sentence around it. A `Long` here would be a
     * second `formatRupiah` call inside a resource lookup, which is the second spelling this
     * app keeps refusing.
     */
    fun berandaTrendSub(totalText: String): String =
        context.getString(R.string.beranda_chart_trend_sub, totalText)

    /** The trend badge when the latest day met its target. */
    fun berandaTrendMeets(): String =
        context.getString(R.string.beranda_chart_trend_meets)

    /** The trend badge when it fell short — names the gap, so the badge is actionable. */
    fun berandaTrendShort(gapText: String): String =
        context.getString(R.string.beranda_chart_trend_short, gapText)

    /** The weekly row's own heading — the target every bar is measured against. */
    fun berandaCompareTarget(targetText: String): String =
        context.getString(R.string.beranda_chart_compare_target, targetText)

    /**
     * One comparison row's label — `M-1 (1 Okt – 7 Okt)`.
     *
     * A resource rather than string concatenation in the composable, so the parenthesisation is
     * translatable and a locale that brackets differently can say so.
     */
    fun berandaCompareRow(label: String, rangeText: String): String =
        context.getString(R.string.beranda_chart_compare_row, label, rangeText)

    /** A joined transaction row whose renter could not be resolved. */
    fun dashboardUnknownName(): String =
        context.getString(R.string.beranda_recent_unknown_name)

    // -------------------------------------------------------------- Story 4.5

    /**
     * How many renters owe nothing — "3 penyewa lunas".
     *
     * The count arrives whole, so it is formatted rather than assembled: a number built by
     * hand beside a string is a second spelling of the same figure.
     */
    fun laporanSettledRenters(count: Int): String =
        context.getString(R.string.laporan_settled_renters, count)

    /** One ladder level's name, from the domain's own word for it. */
    fun laporanRiskLabel(level: net.klinik.rental.domain.calculate.RiskLevel): String =
        context.getString(
            when (level) {
                net.klinik.rental.domain.calculate.RiskLevel.LANCAR -> R.string.laporan_risk_lancar
                net.klinik.rental.domain.calculate.RiskLevel.RINGAN -> R.string.laporan_risk_ringan
                net.klinik.rental.domain.calculate.RiskLevel.PERHATIAN ->
                    R.string.laporan_risk_perhatian
                net.klinik.rental.domain.calculate.RiskLevel.KRITIS -> R.string.laporan_risk_kritis
            },
        )
}
