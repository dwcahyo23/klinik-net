package net.klinik.rental.ui.screens.laporan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import net.klinik.rental.data.repository.ContractArrears
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.domain.calculate.riskOf
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.strings.RStrings
import java.time.Clock
import java.time.LocalDate

/**
 * Owns the period report's state — Story 4.5.
 *
 * ## Why this screen has no arithmetic of its own
 *
 * Because the acceptance criterion's strongest clause is *"bukan dua fungsi terpisah yang bisa
 * menyimpang"* — the report and the morning summary must not be able to disagree. A report
 * that summarises by querying separately is the second-derivation defect this repo keeps
 * finding, and here it would be the worst instance: the owner compares this figure against
 * Beranda's and neither knows which is right.
 *
 * So every figure comes from the **same** repository functions Beranda reads, with the period's
 * date range as the only difference:
 *
 *  - income is a range over the grouping `observeIncomeByDate` already returns, so the status
 *    filter that excludes a cancelled deposit is the one the app already agreed on
 *  - arrears is the sum over `observeArrearsForAllContracts` called with **this period's**
 *    end date — the same body Beranda runs, with a different time point
 *
 * ## Why the period is chosen, not typed
 *
 * Because `CutoffPeriod` is the app's billing boundary, and the allocation engine already
 * obeys it. Last month's report is the same period function with a different date, so the
 * report's range and the engine's range cannot disagree about which days a month contains.
 *
 * @OptIn the `flatMapLatest` opt-in is declared here rather than silenced: this class
 *   switches periods by re-subscribing, and a compiler warning on the one flow that decides
 *   which month is on screen is exactly the warning worth reading.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LaporanViewModel(
    private val payments: PaymentRepository,
    private val strings: RStrings,
    private val clock: Clock,
    /**
     * The cutoff day, from `settings`. Read through the repository so the report's period
     * boundaries are the ones the allocation engine used.
     */
    private val cutoffDay: suspend () -> Int = { PaymentRepository.DEFAULT_CUTOFF_DAY },
) : ViewModel() {

    /**
     * The period being read.
     *
     * An index rather than a `CutoffPeriod`, because the list is derived from the clock and
     * an index survives the list being recomputed when the day changes — the report stays on
     * the same month when the app is open across a cutoff.
     */
    private val periodIndex = MutableStateFlow(0)

    /**
     * The periods on offer, newest first.
     *
     * Read from the repository's own cutoff day rather than a constant, so the report's
     * boundaries are the ones the engine used — a report of a period the engine never
     * charged would be a report of a month that does not exist.
     */
    private val periods: Flow<List<CutoffPeriod>> = kotlinx.coroutines.flow.flow {
        emit(periodsFor(cutoffDay(), LocalDate.now(clock)))
    }

    /**
     * The selected period, and everything read at it.
     *
     * `flatMapLatest` on the period is what makes the report *re-read* when the owner picks
     * another month — and it is the same shape `TunggakanViewModel` uses, for the same reason:
     * the reads depend on the period, so the period is what the flow is keyed on.
     */
    private val periodData: Flow<PeriodData> = combine(periods, periodIndex) { list, index ->
        list.getOrNull(index) ?: list.first()
    }.flatMapLatest { period ->
        combine(
            payments.observeIncomeBetween(period.start, period.end),
            // The **same body** Beranda runs, called with this period's end date. That is
            // the acceptance criterion's "fungsi yang sama dengan parameter titik waktu
            // berbeda" made mechanical rather than aspirational.
            payments.observeArrearsForAllContracts(clock) { period.end },
            payments.observeIncomeByDay(period.end.minusDays(TREND_DAYS - 1L), period.end),
        ) { income, arrears, trend ->
            PeriodData(
                period = period,
                income = income,
                arrears = arrears,
                trend = trend.map { it.total },
            )
        }
    }

    /** The screen state. */
    val uiState: StateFlow<LaporanUiState> = periodData
        .map<PeriodData, LaporanUiState> { data ->
            // Formatted here rather than inside [PeriodData], because the nested class
            // cannot reach this one's `strings` — and the strings belong to the layer
            // that owns the context, not to a private data holder.
            LaporanUiState(
                periodLabel = data.period.label,
                incomeText = formatRupiah(data.income),
                arrearsText = formatRupiah(data.arrearsTotal),
                settledText = strings.laporanSettledRenters(data.settledRenters),
                trendTotalText = formatRupiah(data.trend.sum()),
                incomeRupiah = data.income,
                arrearsRupiah = data.arrearsTotal,
                settledRenters = data.settledRenters,
                risk = data.riskCounts.asRows { level -> strings.laporanRiskLabel(level) },
                trend = data.trend,
                isLoading = false,
            )
        }
        .onStart { emit(LaporanUiState(isLoading = true)) }
        .catch { emit(LaporanUiState(isLoading = false)) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LaporanUiState(isLoading = true),
        )

    /**
     * The period labels the picker offers, newest first.
     *
     * The chips carry the engine's own `label` — so a period's name and the days the engine
     * charged for it are the same string.
     */
    val periodOptions: StateFlow<List<String>> = periods
        .map { list -> list.map { it.label } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Picks another period. */
    fun selectPeriod(index: Int) {
        periodIndex.value = index
    }

    /** One period's figures, before they are formatted. */
    private data class PeriodData(
        val period: CutoffPeriod,
        val income: Long,
        val arrears: List<ContractArrears>,
        val trend: List<Long>,
    ) {
        /**
         * The arrears total, in whole rupiah.
         *
         * The sum over the entries, which include the contracts owing nothing — so this is
         * every contract's balance, and a contract that owes Rp 0 contributes Rp 0 rather
         * than being absent. The **count** of settled renters depends on that, which is why
         * the list is kept and not just its sum.
         */
        val arrearsTotal: Long get() = arrears.sumOf { it.amount }

        /**
         * How many contracts owe nothing at this period's end.
         *
         * "Lunas" is a fact about a contract, so it is counted from the entries rather than
         * derived from a rupiah difference: a contract that owed Rp 1 and now owes nothing is
         * settled, and a total difference would not see it.
         */
        val settledRenters: Int get() = arrears.count { it.amount == 0L }

        /** How many contracts sit at each rung of the ladder. */
        val riskCounts: RiskCounts
            get() {
                var lancar = 0
                var ringan = 0
                var perhatian = 0
                var kritis = 0
                for (entry in arrears) {
                    when (riskOf(entry.days)) {
                        RiskLevel.LANCAR -> lancar++
                        RiskLevel.RINGAN -> ringan++
                        RiskLevel.PERHATIAN -> perhatian++
                        RiskLevel.KRITIS -> kritis++
                    }
                }
                return RiskCounts(lancar, ringan, perhatian, kritis)
            }
    }

    private companion object {
        /** How many days the trend covers. */
        const val TREND_DAYS = 7L
    }
}
