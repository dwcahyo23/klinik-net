package net.klinik.rental.ui.screens.laporan

import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.domain.calculate.riskOf
import net.klinik.rental.ui.components.KnBadgeTone
import net.klinik.rental.ui.screens.tunggakan.TunggakanUiState
import java.time.LocalDate

/**
 * The period report's state — Story 4.5.
 *
 * ## Why every figure arrives already formatted
 *
 * Because a rupiah amount has exactly one spelling in this app, and it is produced by
 * `formatRupiah` at the point the number is known. The same rule `BerandaUiState` follows and
 * the same reason: a state carrying a `Long` would push that decision to the composable, and a
 * report formatting a figure differently from the summary the owner just read is the
 * second-derivation defect this story exists to prevent.
 *
 * ## Why the period is a [CutoffPeriod] and not a month
 *
 * Because `CutoffPeriod` is the app's existing answer to "which days am I charging for", and
 * the allocation engine already obeys it. A report that said "October" would mean the 1st to the
 * 31st while the engine charged the 25th to the 24th, and the two totals would disagree for
 * every month without anyone seeing it.
 *
 * @param settledRenters how many contracts owe **nothing** at this period's end. Distinct from
 *   "not in arrears" the other way round: a contract that never existed is not a settled
 *   renter, and a report that counted it would inflate the number the owner is proud of.
 * @param risk the four ladder levels as the screen draws them. See the field's KDoc.
 * @param trend one point per day across the last seven days, zero-filled. See
 *   `PaymentRepository.observeIncomeByDay`.
 */
data class LaporanUiState(
    /** `25 Sep - 24 Okt 2026` — `CutoffPeriod.label`, already spelled. */
    val periodLabel: String = "",
    /** `Rp 1.500.000` — confirmed deposits dated inside this period. */
    val incomeText: String = "",
    /** `Rp 4.250.000` — arrears at this period's end, open and ended contracts alike. */
    val arrearsText: String = "",
    /** `3 penyewa lunas` — see the class KDoc on what counts. */
    val settledText: String = "",
    /** `Rp 750.000` — seven days of income, summed by the report rather than the screen. */
    val trendTotalText: String = "",
    /** The period's own totals, kept as numbers so a test can assert on them. */
    val incomeRupiah: Long = 0L,
    val arrearsRupiah: Long = 0L,
    val settledRenters: Int = 0,
    /** The four ladder levels, counted rather than derived twice. */
    /**
     * The four ladder levels, each with its label, count and colour already resolved.
     *
     * The labels live here rather than in the screen, because resolving them needs a
     * `Context` and the screen is not the layer that owns one. The ViewModel resolves them
     * through `RStrings` — the same place every other string in this app is resolved — and
     * the screen draws text.
     */
    val risk: List<RiskRow> = emptyList(),
    /**
     * Seven daily income totals, oldest first.
     *
     * The shape the Canvas draws directly. Seven points rather than "whatever happened to
     * have deposits", because a line through only the busy days is a different chart.
     */
    val trend: List<Long> = emptyList(),
    val isLoading: Boolean = false,
) {
    /**
     * Whether the period has nothing to show.
     *
     * Not `incomeText.isEmpty()` and not a "no arrears" test: a quiet period is a real fact
     * about a real month, and the screen says so rather than drawing a skeleton.
     */
    val isQuietPeriod: Boolean
        get() = incomeRupiah == 0L && arrearsRupiah == 0L && settledRenters == 0
}

/**
 * How many contracts sit at each rung of the ladder.
 *
 * ## Why four numbers and not a donut
 *
 * Because the epic decided it, and the reason is worth keeping: a donut needs a chart library
 * to be legible, and this repo has declined every dependency for a screen it could have drawn
 * itself. Four coloured numbers are read in one pass, need nothing installed, and cannot go
 * stale in a dependency.
 *
 * ## Why the counts are **carried** rather than derived
 *
 * Because each one comes from `riskOf`, and a count the screen recomputed from its own copy of
 * the ladder is the second answer to the same question. The ViewModel counts through the
 * domain function and the screen draws.
 */
data class RiskCounts(
    val lancar: Int = 0,
    val ringan: Int = 0,
    val perhatian: Int = 0,
    val kritis: Int = 0,
) {
    /** Every contract counted, which is what a total here should add up to. */
    val total: Int get() = lancar + ringan + perhatian + kritis
}

/**
 * The colour each level draws in, on one type rather than at four call sites.
 *
 * ## Why it calls the arrears list's own mapping rather than restating the ladder
 *
 * Because `TunggakanUiState.toneFor` already maps `RiskLevel` to a tone, and it is exhaustive
 * on purpose — a new `RiskLevel` breaks its `when` rather than inheriting the last branch's
 * colour. A second mapping written here over the same ladder would be the second derivation
 * this story exists to prevent, and the one place it would hurt most: the report and the list
 * it is read beside would colour the same level differently, which is exactly the
 * "3 hari beside a green badge" shape UX-DR9 forbids.
 */
internal fun riskLevelOf(count: Int): RiskLevel = riskOf(count)

/**
 * The four ladder levels as the screen draws them: a label, a count and a colour.
 *
 * ## Why the tone comes from the **level**, not from the count beside it
 *
 * Because the tone answers "how bad is this?" and the count answers "how many?" — and three
 * contracts sitting at `KRITIS` is not a *worse* state than one, it is the same state with
 * more of it. Deriving the colour from the count would paint a month with three critical
 * tenants calmer than a month with one, which is the exact inversion UX-DR9 forbids.
 */
data class RiskRow(
    val label: String,
    val count: Int,
    val tone: KnBadgeTone,
)

/**
 * The four rows, in ladder order.
 *
 * A list rather than four separate fields so the screen cannot render three of the four — the
 * criterion asks for four numbers, and "at least three" is not four.
 *
 * The tones come from [TunggakanUiState.toneFor], the arrears list's own mapping over
 * `riskOf`. Reusing it is the point: a second mapping over the same ladder would let the
 * report and the list it is read beside colour the same level differently.
 */
fun RiskCounts.asRows(label: (RiskLevel) -> String): List<RiskRow> =
    listOf(
        RiskLevel.LANCAR to lancar,
        RiskLevel.RINGAN to ringan,
        RiskLevel.PERHATIAN to perhatian,
        RiskLevel.KRITIS to kritis,
    ).map { (level, count) ->
        RiskRow(
            label = label(level),
            count = count,
            tone = TunggakanUiState.toneFor(level),
        )
    }

/**
 * The periods a report can be read at, newest first.
 *
 * ## Why a bounded list and not an open date picker
 *
 * Because the period is the app's own billing boundary, and a free date picker would let the
 * owner cut a range the allocation engine never charged — producing a report of a period that
 * does not exist. Offering "the current one and the ones before it" keeps every figure inside
 * a period the engine has actually billed.
 *
 * A **future** period is included deliberately: the acceptance criterion asks that it not add
 * arrears, which is a statement about behaviour rather than a prohibition on looking.
 */
fun periodsFor(cutoffDay: Int, today: LocalDate, count: Int = 6): List<CutoffPeriod> =
    (0 until count).map { back ->
        CutoffPeriod.containing(cutoffDay, today.minusMonths(back.toLong()))
    }