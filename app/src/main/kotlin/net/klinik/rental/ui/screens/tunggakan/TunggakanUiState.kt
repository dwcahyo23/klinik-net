package net.klinik.rental.ui.screens.tunggakan

import net.klinik.rental.data.repository.ContractArrears
import net.klinik.rental.domain.calculate.ArrearsEntry
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.domain.calculate.inArrearsOrder
import net.klinik.rental.domain.calculate.riskOf
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.strings.RStrings

/**
 * One row of the arrears list: who owes, how much, and for how many days.
 *
 * ## Why the figures arrive already formatted
 *
 * Because this is the screen the owner reads before contacting a tenant, and money assembled
 * in two places is money that can differ in two places. `formatRupiah` runs once per emission
 * in [TunggakanViewModel], exactly as `ContractRow.rateText` does — so this file contains no
 * `formatRupiah` call and two screens cannot spell the same rupiah two ways.
 *
 * ## Why the tone is carried and is not the source of truth
 *
 * [tone] is **derived** from [risk] by [TunggakanUiState.toneFor] and can never be passed in
 * by a caller. The criterion is explicit that colour follows the day count, and a tone a
 * caller could choose would be a second source free to disagree with the number beside it.
 * Carrying both is not duplication: the number is what the owner reads, the tone is what makes
 * a long list scannable, and neither derives from the other at draw time.
 *
 * @property figureText the AC's exact shape, `Rp 750.000 · 3 hari`. One string rather than two
 *   composables side by side, because the criterion makes them a single fact — a row showing
 *   the rupiah without the day count is a defect, not a partial answer.
 * @property daysLabel the day count **alone**, for the badge and for the detail pane, where
 *   the rupiah already has its own labelled row.
 * @property riskLabel the ladder's own word, rendered because colour is never the only carrier
 *   of meaning (UX-DR9) and a badge the owner cannot read is one they cannot trust.
 * @property oldestDayText the oldest still-open charge day as `14 Okt`, or `null` when no day
 *   is open. This is the figure that explains a number: if Budi paid on Sunday for Monday,
 *   this is the day that tells the owner why.
 * @property isEnded whether the rental has finished. An ended contract with a balance stays in
 *   the list until settled, so this is a marker and never a filter.
 */
data class ArrearsRow(
    val contractId: String,
    val renterName: String,
    val vehiclePlate: String,
    /**
     * The figure in whole rupiah.
     *
     * Carried beside [figureText] because the summary adds rows up, and parsing the formatted
     * string back into a number would be a second money path that can disagree with the first.
     */
    val amount: Long,
    /**
     * The renter's phone, as stored.
     *
     * Carried on the row rather than read when the reminder opens, for the reason the figures
     * are: a second read is a second snapshot, and this one decides **who receives** a message
     * about money. `Renter.phone` is required by the domain model, so it is never absent.
     */
    val renterPhone: String,
    /**
     * The renter's avatar key, or `null` when they have none.
     *
     * Carried on the row for the same reason the phone is: the disc beside the name draws it,
     * and a second read at that point would be a second snapshot of a person whose details
     * decide who gets chased for money.
     *
     * Defaulted to `null` so a row built for a preview or a test needs one fewer argument —
     * and `null` is the honest value there, since neither has an avatar.
     */
    val renterAvatar: String? = null,
    /** `Rp 750.000 · 3 hari` — the AC's format, built once. */
    val figureText: String,
    /** `3 hari`, for the badge and the detail's own labelled row. */
    val daysLabel: String,
    /** The ladder's word for [risk]: Lancar, Ringan, Perhatian or Kritis. */
    val riskLabel: String,
    /** Derived from [risk]. See the class KDoc. */
    val tone: KnBadgeTone,
    val risk: RiskLevel,
    /** `14 Okt`, or `null` when nothing is open. */
    val oldestDayText: String?,
    val isEnded: Boolean,
)

/**
 * The reminder dialog's own state — Story 4.2.
 *
 * ## Why the text is held here rather than derived from the row
 *
 * Because the owner may edit it. The composed sentence is the **starting** value, not the
 * current one, so the state carries what is in the field and [link] is derived from it — the
 * acceptance criterion's *"tautan dibangun ulang dari teks hasil suntingan"*. A link computed
 * once when the dialog opened would send the pre-edit figure, which is the failure the criterion
 * names.
 *
 * ## Why the recipient is carried rather than looked up
 *
 * Because it belongs to the same snapshot as the figures. The row that opened this dialog
 * carried the phone, and a lookup here would be the second read the criterion forbids.
 *
 * @property contractId the row this message is about, so a dismissal can tell whether the row
 *   still exists.
 * @property phone the renter's stored number, already on the row.
 * @property text what is in the field right now — the composed message, possibly edited.
 * @property errorMessage a refusal that has no field of its own — an emptied message, or a
 *   phone that cannot be addressed.
 */
data class ReminderUi(
    val contractId: String,
    val phone: String,
    val text: String,
    val errorMessage: String? = null,
    /** The copy confirmation, cleared when the dialog closes or the text changes. */
    val copiedMessage: String? = null,
) {
    /**
     * The `wa.me` URL for [text], or `null` when the stored phone cannot address anyone.
     *
     * **Derived, not carried.** A stored `link` field would be a second copy of a value that is
     * a function of [phone] and [text], and the copy is what can go stale — the acceptance
     * criterion exists because a link captured at open time sends the pre-edit figure. Deriving
     * it makes "the link follows the text" a property of the type rather than of a handler
     * somebody has to remember.
     */
    val link: String? get() = ReminderMessage.whatsappLink(phone, text)

    /** Whether there is anything to send or copy. */
    val canSend: Boolean get() = text.isNotBlank() && link != null
}

/**
 * Applies an edit to the reminder text.
 *
 * Nothing to rebuild: [ReminderUi.link] is derived from the text, so the two cannot drift and
 * this function cannot throw on an unaddressable phone. The refusal clears on the edit, because
 * the edit **is** the fix — the same rule every field in this app follows.
 */
fun ReminderUi.withText(text: String): ReminderUi = copy(
    text = text,
    errorMessage = null,
    // The confirmation belongs to the text that was copied; an edit makes it stale.
    copiedMessage = null,
)

/**
 * The whole screen in one value.
 *
 * @param rows already in `inArrearsOrder`, so the screen renders in the order the domain
 *   decided. A second sort in the UI is a second answer to "who do I contact first".
 * @param selectedContractId the row the detail pane shows, or `null` for none. Held rather
 *   than derived from [rows]: a contract that stops owing must drop out of the list, and a
 *   pane that silently retargeted itself would show the owner a different person's figure
 *   right after a payment.
 * @param isLoading true before the first emission. Room's first emission is an empty list, so
 *   a screen branching on emptiness would show "Tidak ada tunggakan" on every load — which on
 *   *this* screen is the worst version of the empty-looks-like-new-data failure, because it
 *   tells the owner nobody owes them anything.
 * @param isFailed true when the read failed. **Separate from [isEmpty]** and not derivable
 *   from it: a failed read also produces no rows, so a caller branching on `isEmpty` alone
 *   cannot tell "nobody owes you anything" from "I could not find out" — and this screen has
 *   the strongest reason of any in the app to keep those apart.
 */
data class TunggakanUiState(
    val rows: List<ArrearsRow> = emptyList(),
    val selectedContractId: String? = null,
    val isLoading: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null,
    /** The reminder dialog, or `null` when it is closed. Story 4.2. */
    val reminder: ReminderUi? = null,
    /** The risk chips on the top bar — `arrears.html:29-35`. */
    val riskFilter: RiskFilter = RiskFilter.Semua,
) {
    /**
     * Whether the empty state applies.
     *
     * False while loading **and** while failed, not merely when the list is short — both of
     * those states also produce no rows, and drawing "semua sewa sudah lunas" over either one
     * is the app telling the owner nobody owes them anything on the strength of a read that
     * never happened. The screen's own `when` is ordered for the same reason; this property
     * is here so a future caller cannot get it wrong by reaching for `rows.isEmpty()`.
     */
    val isEmpty: Boolean get() = rows.isEmpty() && !isLoading && !isFailed

    /**
     * How many people the list is about — the top bar's subtitle.
     *
     * Distinct people rather than rows, because one renter can hold two contracts with a
     * balance and "3 penyewa" over two people is a number the owner cannot act on. The
     * subtitle's own copy says "penyewa", so this counts renters.
     *
     * `0` while loading or failed, for the same reason [isEmpty] is false there: the bar
     * must not claim the owner is owed by nobody before the app has looked.
     */
    val renterCount: Int get() = if (isLoading || isFailed) 0 else rows.distinctBy { it.renterName }.size

    /**
     * The rows the list draws: the risk chip applied, then the domain's own order preserved.
     *
     * A filter over [rows] rather than a second sort: `inArrearsOrder` already answered "who do I
     * contact first", and re-ordering here would be a second answer.
     */
    val visibleRows: List<ArrearsRow>
        get() = riskFilter.level?.let { level -> rows.filter { it.risk == level } } ?: rows

    /** The four summary figures — `arrears.html:112-136`. */
    val summary: ArrearsSummary
        get() {
            fun sum(level: RiskLevel): Long = rows.filter { it.risk == level }
                .sumOf { it.amount }
            return ArrearsSummary(
                totalText = formatRupiah(rows.sumOf { it.amount }),
                owingCount = rows.count { it.amount > 0 },
                contractCount = rows.size,
                criticalCount = rows.count { it.risk == RiskLevel.KRITIS },
                criticalText = formatRupiah(sum(RiskLevel.KRITIS)),
                attentionCount = rows.count { it.risk == RiskLevel.PERHATIAN },
                attentionText = formatRupiah(sum(RiskLevel.PERHATIAN)),
                lightCount = rows.count { it.risk == RiskLevel.RINGAN },
                lightText = formatRupiah(sum(RiskLevel.RINGAN)),
            )
        }

    /**
     * The row the detail pane shows.
     *
     * Looked up by id rather than held, so a row that leaves the list takes the selection with
     * it and the pane falls back to its prompt instead of showing a figure for a contract the
     * owner can no longer see listed.
     */
    val selectedRow: ArrearsRow?
        get() = rows.firstOrNull { it.contractId == selectedContractId }

    companion object {
        /**
         * The badge tone for a risk level.
         *
         * ## Why LANCAR is `Success` and not `Neutral`
         *
         * Because the ladder is a *status*, and the semantic tones are reserved for status.
         * `KontrakRow` keeps its schedule and exception badges neutral for exactly that
         * reason: those are facts about a contract, not judgements about one. Drawing "nobody
         * owes anything" in the same neutral as "six-day week" would make one palette carry
         * two different kinds of claim.
         *
         * ## Why the mapping is exhaustive on purpose
         *
         * A new `RiskLevel` must break this `when` rather than silently inherit the last
         * branch's colour — the compiler-enforced property `Destination.icon` already relies
         * on, where adding `Pengaturan` made a `when` non-exhaustive and so the fourth icon
         * could not be forgotten.
         */
        fun toneFor(risk: RiskLevel): KnBadgeTone = when (risk) {
            RiskLevel.LANCAR -> KnBadgeTone.Success
            RiskLevel.RINGAN -> KnBadgeTone.Warning
            RiskLevel.PERHATIAN -> KnBadgeTone.Info
            RiskLevel.KRITIS -> KnBadgeTone.Danger
        }
    }
}

/**
 * The complete entries turned into rows: ordered, formatted, and toned.
 *
 * ## Why the order is applied here and not left to the screen
 *
 * Because [inArrearsOrder] also **filters** — a contract owing nothing is absent rather than
 * drawn at zero. That is the criterion's "a paid tenant never appears as owing", and it is the
 * one behaviour a screen cannot undo by sorting: a row carrying Rp 0 is a row telling the
 * owner someone owes them nothing, which is a different claim from not listing them.
 *
 * ## Why the names fall back to the id
 *
 * Same rule `KontrakViewModel` follows for its own join: the foreign keys make a missing
 * renter or vehicle unreachable, and a row naming an id is a better outcome than a row that
 * shows nothing at all.
 */
internal fun List<ContractArrears>.toRows(
    renterNames: Map<String, String>,
    renterPhones: Map<String, String>,
    renterAvatars: Map<String, String?>,
    vehiclePlates: Map<String, String>,
    strings: RStrings,
): List<ArrearsRow> {
    // Through `ArrearsEntry`, because the ordering and the zero-filter are the **domain's**
    // rules and this must not restate them. `ContractArrears` deliberately carries more than
    // an `ArrearsEntry` does, so the entry is built for the sort and the row is built from a
    // lookup.
    //
    // The lookup cannot miss, and this says so rather than quietly tolerating it: both lists
    // come from the same `entries` on the line above, so a miss would mean the projection had
    // dropped a contract owing money — the one failure this screen exists to prevent. A
    // `mapNotNull` here would swallow it into a shorter list that still renders, which is
    // exactly the shape of defect the epic context calls "empty data that looks like new".
    val byId = entriesById()
    return map { it.toEntry() }
        .inArrearsOrder()
        .map { entry ->
            val contract = requireNotNull(byId[entry.contractId]) {
                "arrears entry ${entry.contractId} was ordered but has no contract behind it; " +
                    "dropping it would hide a tenant who owes money"
            }
            val risk = riskOf(entry.days)
            ArrearsRow(
                contractId = entry.contractId,
                renterName = renterNames[contract.renterId] ?: contract.renterId,
                vehiclePlate = vehiclePlates[contract.vehicleId] ?: contract.vehicleId,
                // Empty rather than a made-up number when the lookup misses: the foreign key
                // makes that unreachable, and an empty phone refuses the link instead of
                // addressing a stranger. See `ReminderMessage.international`.
                renterPhone = renterPhones[contract.renterId].orEmpty(),
                // The avatar, from the **same** lookup the name and phone come from. `null`
                // when the renter has none, and `null` when the lookup misses — the disc
                // falls back to initials either way, so a miss is a blank face rather than
                // a crash.
                renterAvatar = renterAvatars[contract.renterId],
                amount = entry.amount,
                figureText = strings.tunggakanRowFigure(
                    amount = formatRupiah(entry.amount),
                    days = strings.tunggakanRowDays(entry.days),
                ),
                daysLabel = strings.tunggakanRowDays(entry.days),
                riskLabel = strings.tunggakanRiskLabel(risk),
                tone = TunggakanUiState.toneFor(risk),
                risk = risk,
                oldestDayText = strings.formatOldestOpenDay(entry.oldestOpenDay),
                isEnded = contract.isEnded,
            )
        }
}

/**
 * The five risk chips — `arrears.html:29-35`.
 *
 * Each chip names its own `RiskLevel`, or `null` for "all", so the filter and the ladder cannot
 * drift: adding a level to the domain enum makes this `when` non-exhaustive and the compiler says
 * so, rather than a chip silently matching nothing.
 */
enum class RiskFilter(val level: RiskLevel?) {
    Semua(null),
    Kritis(RiskLevel.KRITIS),
    Perhatian(RiskLevel.PERHATIAN),
    Ringan(RiskLevel.RINGAN),
    Lancar(RiskLevel.LANCAR),
}

/**
 * The four figures above the list — `arrears.html:112-136`.
 *
 * Every money value is pre-formatted and every count is derived from the *same* rows the list
 * draws, so a card and the list beneath it are one read. The prototype's first card is accented
 * (`kpi-accent`) because total arrears is the one figure the owner came for; the other three are
 * flat.
 *
 * @property owingCount how many contracts actually owe something, versus every contract on the
 *   list (an ended contract stays until settled, which is why the prototype prints both).
 * @property criticalCount contracts at seven days or more, and their total.
 */
data class ArrearsSummary(
    val totalText: String = "",
    val owingCount: Int = 0,
    val contractCount: Int = 0,
    val criticalCount: Int = 0,
    val criticalText: String = "",
    val attentionCount: Int = 0,
    val attentionText: String = "",
    val lightCount: Int = 0,
    val lightText: String = "",
)

/**
 * The figures alone, in the shape the domain's ordering function takes.
 *
 * `internal` rather than `private` so the tie-break test can sort through the **domain's**
 * rule rather than a copy of it — a test that sorts by hand would be testing its own sort,
 * which is the second-derivation defect this whole story refuses.
 */
internal fun ContractArrears.toEntry() = ArrearsEntry(
    contractId = contractId,
    days = days,
    amount = amount,
    oldestOpenDay = oldestOpenDay,
)

internal fun List<ContractArrears>.entriesById(): Map<String, ContractArrears> =
    associateBy { it.contractId }