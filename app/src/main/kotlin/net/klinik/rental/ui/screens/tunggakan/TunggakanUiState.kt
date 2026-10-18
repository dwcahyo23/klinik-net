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
 */
data class TunggakanUiState(
    val rows: List<ArrearsRow> = emptyList(),
    val selectedContractId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Nothing is owed, once the read has landed and produced no rows. */
    val isEmpty: Boolean get() = rows.isEmpty()

    /** How many people the list is about — the top bar's subtitle. */
    val renterCount: Int get() = rows.size

    /**
     * The row the detail pane shows, or `null`.
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
    vehiclePlates: Map<String, String>,
    strings: RStrings,
): List<ArrearsRow> {
    // Through `ArrearsEntry`, because the ordering and the zero-filter are the **domain's**
    // rules and this must not restate them. `ContractArrears` deliberately carries more than
    // an `ArrearsEntry` does, so the entry is built for the sort and the row is built from a
    // lookup — a projection rather than a second ordering.
    val byId = associateBy { it.contractId }
    return map { it.toEntry() }
        .inArrearsOrder()
        .mapNotNull { entry ->
            val contract = byId[entry.contractId] ?: return@mapNotNull null
            val risk = riskOf(entry.days)
            ArrearsRow(
                contractId = entry.contractId,
                renterName = renterNames[contract.renterId] ?: contract.renterId,
                vehiclePlate = vehiclePlates[contract.vehicleId] ?: contract.vehicleId,
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