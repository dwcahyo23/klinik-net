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
import kotlinx.coroutines.launch
import net.klinik.rental.data.repository.ContractArrears
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.domain.calculate.riskOf
import net.klinik.rental.ui.format.CsvWriter
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
    /**
     * The contracts the report lists, with their renters and vehicles.
     *
     * Nullable rather than required, for the reason every other optional collaborator in
     * this app is: the ViewModel's own tests run without a database, and a report that has
     * no contracts to list draws an empty table rather than failing.
     */
    private val contracts: ContractRepository? = null,
    private val renters: RenterRepository? = null,
    private val vehicles: VehicleRepository? = null,
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
     * Which period the report is read at — the prototype's three chips.
     *
     * ## Why one value and not three flows
     *
     * Because the three answers are **one** answer: "which period is on screen". Three
     * separate flows can describe a state that does not exist — the mode says custom while
     * the applied range is last month's — and a report that shows one period's figures
     * under another period's label is the second-derivation defect this screen exists to
     * prevent. One value cannot disagree with itself.
     */
    private val selection = MutableStateFlow(ReportSelection(ReportMode.Current))

    /**
     * The custom range, as typed — the "Dari" and "Sampai" fields.
     *
     * Held as text, as the class KDoc on [CustomRangeUi] explains: a half-typed date is a
     * real state, and parsing on every keystroke would refuse the first character of a year.
     */
    private val customRange = MutableStateFlow(CustomRangeUi(fromText = "", toText = ""))

    /**
     * The export's own state — the progress and the outcome.
     *
     * Its own flow rather than a field on the state, because the export is a **write**: it
     * has to survive the period being switched while the file picker is open, and a value
     * folded into the period's `combine` would be recomputed away by the next emission.
     */
    private val exportState = MutableStateFlow(ExportState())


    /**
     * The periods on offer, newest first.
     *
     * Read from the repository's own cutoff day rather than a constant, so the report's
     * boundaries are the ones the engine used — a report of a period the engine never
     * charged would be a report of a month that does not exist.
     *
     * The cutoff day is **kept** as it is read, because the file name the picker offers
     * needs it and the picker is opened from a callback that cannot suspend.
     */
    private val periods: Flow<List<CutoffPeriod>> = kotlinx.coroutines.flow.flow {
        val day = cutoffDay()
        cutoffDayCache.value = day
        emit(periodsFor(day, LocalDate.now(clock)))
    }

    /**
     * The last cutoff day the report read.
     *
     * A cache rather than a second read, because the file name is built on the UI thread
     * where a blocking database read is not an option — and because the day cannot change
     * while the report is open unless the owner edits it, which re-reads it anyway.
     */
    private val cutoffDayCache = MutableStateFlow(PaymentRepository.DEFAULT_CUTOFF_DAY)

    /**
     * The selected period, and everything read at it.
     *
     * `flatMapLatest` on the period is what makes the report *re-read* when the owner picks
     * another month — and it is the same shape `TunggakanViewModel` uses, for the same reason:
     * the reads depend on the period, so the period is what the flow is keyed on.
     */
    private val periodData: Flow<PeriodData> = combine(
        periods,
        selection,
    ) { list, current ->
        when (current.mode) {
            ReportMode.Current -> list.getOrNull(current.periodIndex) ?: list.first()
            ReportMode.Previous -> list.getOrNull(current.periodIndex + 1) ?: list.last()
            // A custom range that has not been applied yet falls back to the current
            // period, so the screen always has a period to read — the picker's own error
            // says what is missing.
            ReportMode.Custom -> current.appliedRange
                ?.let { CutoffPeriod(it.start, it.endInclusive) }
                ?: list.getOrNull(current.periodIndex) ?: list.first()
        }
    }.flatMapLatest { period ->
        combine(
            payments.observeIncomeBetween(period.start, period.end),
            payments.observeIncomeByDay(period.end.minusDays(TREND_DAYS - 1L), period.end),
            breakdownRows(period),
        ) { income, trend, breakdown ->
            PeriodData(
                period = period,
                income = income,
                arrears = breakdown.arrears,
                trend = trend.map { it.total },
                breakdown = breakdown.rows,
            )
        }
    }

    /**
     * The report's own table: one row per contract, with the days billed, what was received
     * and what is still owed.
     *
     * ## Why the arrears come from the **allocation** and not from `billed − received`
     *
     * Because "owed" is the allocation's answer, and the arrears list already shows it. A
     * table that subtracted the period's own inflows from the period's own bill would answer
     * a *different* question — "did this period cover its own bill" — and would show Rp 0 for
     * a contract carrying a debt from last month. Two numbers under one heading, disagreeing
     * with the list the owner reads beside them, is the second-derivation defect this screen
     * exists to prevent. So the closing balance is the arrears list's figure, and the
     * period's flows sit beside it as what they are: flows.
     *
     * ## Why the days are the engine's day list
     *
     * Because `period length × rate` overstates every contract with a rest day or a service
     * exception, and the report would then disagree with the arrears list about the same
     * rental. The list comes from the same `chargeDaysIn` the allocator charges by.
     */
    private fun breakdownRows(period: CutoffPeriod): Flow<Breakdown> {
        val contractRepo = contracts
        val renterRepo = renters
        val vehicleRepo = vehicles
        if (contractRepo == null || renterRepo == null || vehicleRepo == null) {
            return kotlinx.coroutines.flow.flowOf(Breakdown(emptyList(), emptyList()))
        }
        return combine(
            contractRepo.observeContracts(),
            renterRepo.observeRenters(),
            vehicleRepo.observeVehicles(),
            payments.observeConfirmedPayments(),
            // The **same body** Beranda runs, called with this period's end date.
            payments.observeArrearsForAllContracts(clock) { period.end },
        ) { contractList, renterList, vehicleList, deposits, arrears ->
            val renterNames = renterList.associate { it.id to it.name }
            val vehicleById = vehicleList.associate { it.id to it }
            val arrearsByContract = arrears.associateBy { it.contractId }
            val receivedByContract = deposits
                .filter { !it.date.isBefore(period.start) && !it.date.isAfter(period.end) }
                .groupBy { it.contractId }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
            Breakdown(
                arrears = arrears,
                rows = contractList.map { contract ->
                    val vehicle = vehicleById[contract.vehicleId]
                    val entry = arrearsByContract[contract.id]
                    val received = receivedByContract[contract.id] ?: 0L
                    ReportRow(
                        contractId = contract.id,
                        renterName = renterNames[contract.renterId] ?: contract.renterId,
                        plate = vehicle?.plate ?: contract.vehicleId,
                        brand = vehicle?.brand.orEmpty(),
                        dailyRate = contract.dailyRate,
                        received = received,
                        arrears = entry?.amount ?: 0L,
                        isSettled = (entry?.amount ?: 0L) == 0L,
                    )
                },
            )
        }.map { breakdown ->
            // The engine's own day list, per contract — the one that respects the rest day,
            // the service exceptions and the settlement.
            Breakdown(
                arrears = breakdown.arrears,
                rows = breakdown.rows.map { row ->
                    val days = payments.chargeDaysInPeriod(
                        contractId = row.contractId,
                        period = period,
                        asOf = period.end,
                    )
                    row.copy(daysBilled = days.size, billed = days.size * row.dailyRate)
                },
            )
        }
    }

    /**
     * Each period's own income total — the report's "Riwayat periode".
     *
     * ## Why one subscription for all six periods
     *
     * Because the totals are all sums over the **same** daily buckets, and six separate
     * `observeIncomeBetween` calls would be six subscriptions to one table — six chances to
     * read at six different moments, and a history whose last two rows disagree with each
     * other about a month that changed while the screen was open. One read, six sums.
     */
    private val periodTotals: Flow<Map<CutoffPeriod, Long>> =
        combine(payments.observeIncomeByDay(), periods) { byDay, periodList ->
            val buckets = byDay.associate { it.date to it.total }
            periodList.associate { period ->
                period to period.days().sumOf { day -> buckets[day] ?: 0L }
            }
        }

    /**
     * The screen state.
     *
     * The mode, the custom range and the period history join the period's own figures here,
     * because all four are answers about **which** period is on screen — and a screen that
     * showed a chip saying "custom" beside a period label saying "25 Sep - 24 Okt" would be
     * telling the owner two things at once.
     *
     * ## Why the combines are **nested**, and why they land in one holder
     *
     * Because Kotlin's typed `combine` has no overload for six flows, and the alternative —
     * the `Array<Any>` one — throws away every type in the signature. The steps are pairs and
     * triples, and the last one lands in a named holder rather than a `Triple`, so a field
     * that is renamed is a compile error instead of a silent mismatch in a lambda.
     */
    private val reportInputs: Flow<ReportInputs> = combine(
        combine(periodData, periodTotals) { data, totals -> data to totals },
        selection,
    ) { (data, totals), current ->
        ReportInputs(data = data, totals = totals, selection = current)
    }.let { partial ->
        combine(partial, customRange) { inputs, range -> inputs.copy(range = range) }
    }.let { partial ->
        combine(partial, exportState) { inputs, export -> inputs.copy(export = export) }
    }.let { partial ->
        combine(partial, periods) { inputs, periodList -> inputs.copy(periods = periodList) }
    }

    /** The screen state. */
    val uiState: StateFlow<LaporanUiState> = reportInputs
        .map<ReportInputs, LaporanUiState> { inputs ->
            val data = inputs.data
            val sel = inputs.selection
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
                breakdown = data.breakdown.map { row ->
                    ReportRowUi(
                        renterName = row.renterName,
                        plate = row.plate,
                        brand = row.brand,
                        daysBilled = row.daysBilled,
                        billedText = formatRupiah(row.billed),
                        receivedText = formatRupiah(row.received),
                        arrearsText = formatRupiah(row.arrears),
                        isSettled = row.isSettled,
                        billedRupiah = row.billed,
                        receivedRupiah = row.received,
                        arrearsRupiah = row.arrears,
                    )
                },
                periodHistory = inputs.periods.map { period ->
                    PeriodHistoryUi(
                        label = period.label,
                        totalText = formatRupiah(inputs.totals[period] ?: 0L),
                        // The period the engine is charging for is still running; the ones
                        // before it are closed.
                        statusText = strings.laporanPeriodStatus(
                            running = period.start <= LocalDate.now(clock) &&
                                period.end >= LocalDate.now(clock),
                        ),
                        period = period,
                    )
                },
                // The picker's two fields are only on screen in custom mode — the same state
                // the prototype hides them in, and the same reason: a date field that is not
                // part of the current mode is a field the owner will fill and expect to
                // change nothing.
                customRange = if (sel.mode == ReportMode.Custom) inputs.range else null,
                mode = sel.mode,
                isExporting = inputs.export.isExporting,
                exportMessage = inputs.export.message,
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
        selection.value = selection.value.copy(
            mode = ReportMode.Current,
            periodIndex = index,
        )
    }

    /**
     * Selects one of the three mode chips.
     *
     * Choosing "previous" moves to the period **before** the one on screen, which is why
     * this is not a separate index: the previous period is `periodIndex + 1` in a list that
     * is newest first, and an index that meant "the previous one" would need its own
     * bookkeeping for the same answer.
     */
    fun selectMode(mode: ReportMode) {
        selection.value = selection.value.copy(mode = mode)
    }

    /**
     * Records what the owner typed into the "Dari" field.
     *
     * The text is kept as typed and the report is **not** moved: the range applies when
     * [applyCustomRange] is called, and until then the screen still shows the period the
     * chip named.
     */
    fun setCustomFrom(text: String) {
        customRange.value = customRange.value.copy(fromText = text, error = null)
    }

    /** Records what the owner typed into the "Sampai" field. */
    fun setCustomTo(text: String) {
        customRange.value = customRange.value.copy(toText = text, error = null)
    }

    /**
     * Applies the custom range — the "Terapkan" button.
     *
     * ## Why the parse happens **here** and not on every keystroke
     *
     * Because a half-typed date is a real state, and refusing it on every keystroke would
     * make the field impossible to type into: "2026-1" is not a date, and a field that
     * refuses it will not let the owner reach "2026-10-19". The parse happens once, when
     * the owner says so.
     *
     * ## Why a range that ends before it starts is refused
     *
     * Because the report would read a period that does not exist — and every figure in it
     * would be a zero or a negative, which looks like a business with no money rather than
     * a mistyped range.
     */
    fun applyCustomRange() {
        val range = customRange.value
        val from = parseIsoDate(range.fromText)
        val to = parseIsoDate(range.toText)
        if (from == null || to == null) {
            customRange.value = range.copy(error = strings.laporanCustomRangeInvalid())
            return
        }
        if (to.isBefore(from)) {
            customRange.value = range.copy(error = strings.laporanCustomRangeReversed())
            return
        }
        customRange.value = range.copy(error = null)
        selection.value = selection.value.copy(
            mode = ReportMode.Custom,
            appliedRange = from..to,
        )
    }

    /**
     * Parses an `yyyy-MM-dd` date, or `null` when the text is not one.
     *
     * A local parse rather than `LocalDate.parse`, because that one throws and this is
     * called on whatever the owner typed — the exception would cross a UI callback for a
     * reason the screen can say in one line.
     */
    private fun parseIsoDate(text: String): LocalDate? = try {
        if (text.isBlank()) null else LocalDate.parse(text.trim())
    } catch (_: java.time.format.DateTimeParseException) {
        null
    }

    /**
     * The report's own CSV, as text.
     *
     * ## Why the CSV is built from the **screen's** state
     *
     * Because the file must be the report the owner is looking at. Reading the database
     * again for the export would be a second derivation of the same figures, and the two
     * would disagree the moment a deposit landed between the read and the write.
     *
     * ## Why the header is four rows
     *
     * Because the prototype's is: the title, the period, the date it was printed, and then
     * the column names. A CSV that starts with column names is a table; one that starts with
     * a title is a **document**, and the owner will email it to their accountant.
     */
    fun buildCsv(state: LaporanUiState): String {
        val rows = mutableListOf<List<String?>>()
        rows.add(listOf(strings.laporanCsvTitle()))
        rows.add(listOf(strings.laporanCsvPeriod(), state.periodLabel))
        rows.add(listOf(strings.laporanCsvPrinted(), LocalDate.now(clock).toString()))
        rows.add(emptyList())
        rows.add(strings.laporanTableHeaders())
        state.breakdown.forEach { row ->
            rows.add(
                listOf(
                    row.renterName,
                    row.plate,
                    row.brand,
                    row.daysBilled.toString(),
                    row.billedText,
                    row.receivedText,
                    row.arrearsText,
                    strings.laporanStatus(row.isSettled),
                ),
            )
        }
        rows.add(emptyList())
        rows.add(
            listOf(
                strings.laporanCsvTotal(),
                null,
                null,
                null,
                state.breakdown.sumOf { it.daysBilled }.toString(),
                state.breakdown.sumOf { it.billedRupiah }.toString(),
                state.breakdown.sumOf { it.receivedRupiah }.toString(),
                state.breakdown.sumOf { it.arrearsRupiah }.toString(),
                null,
            ),
        )
        return CsvWriter.BYTE_ORDER_MARK + CsvWriter.build(rows)
    }

    /**
     * The file name the picker offers.
     *
     * The prototype's own shape — `laporan-<from>-sd-<to>.csv` — so a report the owner
     * downloaded from the prototype and one from the app are named the same way.
     */
    fun csvFilename(): String {
        val period = currentPeriod()
        return "laporan-${period.start}-sd-${period.end}.csv"
    }

    /**
     * The period on screen, read from the selection and the six periods.
     *
     * Not `suspend`, because the file name is built on the UI thread where a blocking read
     * is not an option — the cutoff day comes from the cache the periods flow filled.
     */
    private fun currentPeriod(): CutoffPeriod {
        val list = periodsFor(cutoffDayCache.value, LocalDate.now(clock))
        val current = selection.value
        return when (current.mode) {
            ReportMode.Current -> list.getOrNull(current.periodIndex) ?: list.first()
            ReportMode.Previous -> list.getOrNull(current.periodIndex + 1) ?: list.last()
            ReportMode.Custom -> current.appliedRange
                ?.let { CutoffPeriod(it.start, it.endInclusive) }
                ?: list.getOrNull(current.periodIndex) ?: list.first()
        }
    }

    /**
     * Writes the report to the file the owner chose.
     *
     * ## Why the write happens **here** and not in the shell
     *
     * Because the CSV is built from the screen's own state, and the state belongs to the
     * ViewModel. The shell owns the `Uri` — it is the one with a `Context` — and hands it
     * over; the ViewModel owns the text. The same split `BackupFileWriter` documents for the
     * backup.
     */
    fun exportTo(uri: android.net.Uri) {
        if (exportState.value.isExporting) return
        exportState.value = exportState.value.copy(isExporting = true, message = null)
        viewModelScope.launch {
            try {
                val csv = buildCsv(uiState.value)
                val written = net.klinik.rental.ui.file.TextFileWriter.write(
                    strings.contextRef,
                    uri,
                    csv,
                )
                exportState.value = exportState.value.copy(
                    isExporting = false,
                    message = if (written) {
                        strings.laporanExportSaved(csvFilename())
                    } else {
                        strings.laporanExportFailed()
                    },
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Said plainly rather than silently. A file that half-wrote and reported
                // success is worse than one that failed loudly.
                exportState.value = exportState.value.copy(
                    isExporting = false,
                    message = strings.laporanExportFailed(),
                )
            }
        }
    }

    /** One period's figures, before they are formatted. */
    private data class PeriodData(        val period: CutoffPeriod,
        val income: Long,
        val arrears: List<ContractArrears>,
        val trend: List<Long>,
        /** The report's own table, before it is formatted. */
        val breakdown: List<ReportRow>,
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

    /**
     * One contract's row in the report's table, before it is formatted.
     *
     * The figures are **numbers** here and text one layer up, for the reason every other
     * state in this app carries formatted strings: a rupiah amount has exactly one spelling.
     */
    private data class ReportRow(
        val contractId: String,
        val renterName: String,
        val plate: String,
        val brand: String,
        val dailyRate: Long,
        /** The charge days this contract owes in the period. Filled in by the engine's list. */
        val daysBilled: Int = 0,
        /** What those days come to. */
        val billed: Long = 0L,
        /** What arrived in the period. */
        val received: Long,
        /** The closing balance — the arrears list's own figure. */
        val arrears: Long,
        val isSettled: Boolean,
    )

    /** The table's rows and the arrears they were read beside. */
    private data class Breakdown(
        val rows: List<ReportRow>,
        val arrears: List<ContractArrears>,
    )

    /**
     * Everything the report's state is built from, as one holder.
     *
     * A named type rather than a `Triple` of triples, because the alternative is a lambda
     * destructuring a five-deep structure — and the field that gets renamed is the one that
     * silently stops being read.
     */
    private data class ReportInputs(
        val data: PeriodData,
        val totals: Map<CutoffPeriod, Long>,
        val selection: ReportSelection,
        val range: CustomRangeUi = CustomRangeUi(fromText = "", toText = ""),
        val export: ExportState = ExportState(),
        val periods: List<CutoffPeriod> = emptyList(),
    )
}
