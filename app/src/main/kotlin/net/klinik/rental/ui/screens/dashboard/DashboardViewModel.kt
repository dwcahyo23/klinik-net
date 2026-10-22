package net.klinik.rental.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import net.klinik.rental.data.repository.ContractArrears
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.domain.calculate.RiskLevel
import net.klinik.rental.domain.calculate.riskOf
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.screens.mobil.VehicleStatus
import net.klinik.rental.ui.strings.RStrings
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Owns the morning summary's state — Story 4.3.
 *
 * ## Why this screen has no arithmetic of its own
 *
 * Because a dashboard that summarises by querying separately is the second-derivation defect
 * this repo keeps finding, and here it would be the worst instance yet: the owner reads four
 * numbers at once and has no way to see which one disagrees with the list they just opened.
 *
 * So the two figures come from the **existing** reads:
 *
 *  - active arrears is the sum over `observeArrearsForAllContracts`, which already filters
 *    zero amounts — so the population is Story 4.1's list by construction, not by a second
 *    filter that could disagree with it
 *  - the fleet split goes through `VehicleStatus.of`, Story 2.3's derivation, rather than a
 *    count column this app does not have
 *
 * The collection rate is the only figure that is genuinely new, and it is one division over
 * two figures that both already exist.
 *
 * ## Why the clock is handed to the repository
 *
 * Because "today" has to be read where the emission happens. A date captured when this
 * ViewModel was built would freeze the morning screen on yesterday's income until the owner
 * left and came back — the same defect Story 4.1's review found in the arrears read.
 */
class DashboardViewModel(
    private val payments: PaymentRepository,
    private val vehicleRepository: VehicleRepository,
    private val strings: RStrings,
    private val clock: Clock,
    /**
     * The contracts, for the chart's daily target and its per-vehicle slices.
     *
     * The donut needs to know **which car** a contract's income belongs to
     * (`dashboard.html:294-320`), and the trend needs each open contract's frozen rate to draw its
     * target line. Both are properties of `contract`, so this is one more reader of the table the
     * other screens already read — joined here rather than through a `@Query` with two JOINs, which
     * is the rule every reader in this app follows.
     *
     * Optional so the existing tests, which build this ViewModel without it, keep compiling — and
     * so a fleet with no contracts still draws both charts, with a zero target.
     */
    contracts: net.klinik.rental.data.repository.ContractRepository? = null,
    /**
     * The deposit history, for the dashboard's "Transaksi terakhir" table.
     *
     * The same repository `SetoranViewModel` reads, over the same cross-contract query — one
     * answer to "what came in recently" rather than a second one assembled here.
     */
    private val history: net.klinik.rental.data.repository.DepositHistoryRepository? = null,
    /** Renter names, so a transaction row can say *who* paid. Joined here, never in SQL. */
    private val renters: net.klinik.rental.data.repository.RenterRepository? = null,
    /** Bank names, so a transaction row can say *where* the money arrived. */
    private val accounts: net.klinik.rental.data.repository.BusinessAccountRepository? = null,
    /**
     * Overrides the arrears source. **For tests only.**
     *
     * A seam because `PaymentRepository` is a `final class` and cannot be stubbed. Defaulted
     * so production never has to know this exists, and named for what it replaces rather than
     * for how it works.
     */
    private val arrearsSourceOverride: (() -> Flow<List<ContractArrears>>)? = null,
    /**
     * Story 5.5's backup repository.
     *
     * Optional so every existing test that builds this ViewModel without one keeps compiling —
     * and so the reminder simply does not appear rather than appearing with an invented date.
     */
    private val backups: net.klinik.rental.data.backup.BackupRepository? = null,
    /** The hour the nudge may appear, supplied by the caller from the owner's setting. */
    private val nudgeHour: Int = net.klinik.rental.data.backup.BackupRepository.DEFAULT_NUDGE_HOUR,
    /**
     * The owner's saved cutoff day. Read from settings by the caller.
     *
     * A **parameter** rather than a second repository read inside this ViewModel: the
     * cutoff period decides every window on this screen, and the caller already holds the
     * value because it wired [PaymentRepository]. One read, one place.
     *
     * A **suspend function** rather than a value, because the day lives in the database
     * and a ViewModel may not read it on its constructor. The value is read once, when the
     * period filter is first used, and cached — the same shape `LaporanViewModel` uses for
     * the same reason.
     *
     * Defaults to the repository's own default so the tests that build this ViewModel
     * without one keep compiling — and so a caller that forgets produces the same period a
     * fresh install would, rather than an exception.
     */
    private val cutoffDay: suspend () -> Int = { PaymentRepository.DEFAULT_CUTOFF_DAY },
) : ViewModel() {

    /**
     * The arrears source, tagged so "not yet read" is a value.
     *
     * A sealed type rather than a nullable list for the reason `TunggakanViewModel` gives: an
     * empty arrears list and a list that has not been read are both empty at runtime, and on
     * this screen the difference is the difference between "nobody owes you anything" and "I
     * have not looked yet".
     */
    private sealed interface Source {
        data object Loading : Source
        data class Loaded(val entries: List<ContractArrears>) : Source
        data object Failed : Source
    }

    /**
     * Today, read from [clock] once per emission rather than captured at construction.
     *
     * Captured at construction, every window below would freeze on the day the ViewModel
     * was built — which is the defect [observeTodaysIncome]'s own KDoc records.
     */
    private val today: LocalDate get() = LocalDate.now(clock)

    /**
     * Income in the **current cutoff period**, with the day the period started —
     * the prototype's second KPI tile (`dashboard.html:178-179`).
     *
     * Both halves ride on one flow because they are one period: the total and its start
     * date are the same period read twice, and a pair of flows could disagree if the owner
     * changed the cutoff day between them.
     */
    private val cutoffIncome: Flow<Pair<Long, LocalDate>> = flow {
        val period = CutoffPeriod.containing(cutoffDay(), today)
        emitAll(
            payments.observeIncomeBetween(period.start, today)
                .map { income -> income to period.start },
        )
    }

    /**
     * The arrears source.
     *
     * The failure is a **value** rather than a side-channel write, for the reason
     * `TunggakanViewModel` gives: a `MutableStateFlow` written beside the flow is not one of
     * the `combine`'s inputs, so the write re-renders nothing and the screen would sit on a
     * skeleton forever.
     */
    private val arrearsSource: Flow<Source> =
        (arrearsSourceOverride?.invoke() ?: payments.observeArrearsForAllContracts(clock))
            .map<List<ContractArrears>, Source> { entries -> Source.Loaded(entries) }
            .onStart { emit(Source.Loading) }
            .catch { emit(Source.Failed) }

    /**
     * The fleet, with its open-contract plates beside it.
     *
     * Two flows rather than one because the status is a **derivation** — a vehicle is
     * `Tersewa` because an open contract names its plate — and the derivation belongs in one
     * place. `MobilViewModel` does the same two, and the plates come from the repository's
     * own `observeRentedPlates` rather than from a second contract query.
     */
    private val fleet: Flow<Pair<List<net.klinik.rental.domain.model.Vehicle>, Set<String>>> =
        combine(
            vehicleRepository.observeVehicles(),
            vehicleRepository.observeRentedPlates(),
        ) { vehicles, rented -> vehicles to rented }

    /**
     * The four reads the dashboard's charts and its transaction table are built from.
     *
     * **The fleet is not among them.** It rides in from [fleet], which already observes it:
     * subscribing to `observeVehicles()` twice on one screen is two queries for one answer, and
     * the second subscription is the one that drifts.
     */
    private data class ChartInputs(
        val byDay: List<net.klinik.rental.data.db.dao.DailyIncome>,
        val byContract: Map<String, Long>,
        val allContracts: List<net.klinik.rental.domain.model.Contract>,
        val ledger: LedgerInputs,
    )

    /**
     * The recent-transactions table's three reads, joined here rather than by a `@Query`.
     *
     * The rule every reader in this app follows: a join is a property of the *screen*, so it
     * happens in the projection where the clock is also read — not in SQL, where a second
     * copy of "which contract belongs to which renter" would live.
     */
    private data class LedgerInputs(
        val rows: List<net.klinik.rental.data.repository.DepositHistoryRow> = emptyList(),
        /** renterId -> name. */
        val renterNames: Map<String, String> = emptyMap(),
        /** accountId -> bank name; a cash deposit has no account and draws a dash. */
        val bankNames: Map<String, String> = emptyMap(),
    )

    private val chartSources: Flow<ChartInputs> = run {
        val historyRepo = history
        val renterRepo = renters
        val accountRepo = accounts
        val ledger: Flow<LedgerInputs> =
            if (historyRepo == null || renterRepo == null || accountRepo == null) {
                // A ViewModel built without them (the older tests) draws the charts and an
                // empty table, rather than inventing names it has no way to read.
                flowOf(LedgerInputs())
            } else {
                combine(
                    historyRepo.observeAll(),
                    renterRepo.observeRenters(),
                    accountRepo.observeAccounts(),
                ) { rows, renterList, accountList ->
                    LedgerInputs(
                        rows = rows,
                        renterNames = renterList.associate { it.id to it.name },
                        bankNames = accountList.associate { it.id to it.bankName },
                    )
                }
            }
        combine(
            payments.observeIncomeByDay(),
            payments.observeIncomeByContract(),
            contracts?.observeContracts() ?: flowOf(emptyList()),
            ledger,
        ) { byDay, byContract, allContracts, ledgerInputs ->
            ChartInputs(byDay, byContract, allContracts, ledgerInputs)
        }
    }

    /**
     * The dashboard's charts and its recent-transactions table, as one flow.
     *
     * ## Why the window is computed here and not in the screen
     *
     * Because "the last 14 days" is a claim about **today**, and today is read from [clock] on
     * every emission — a window frozen when the ViewModel was built would keep showing yesterday's
     * last day after midnight, which is the exact defect `observeTodaysIncome`'s own KDoc was
     * written to fix.
     *
     * ## Why the plates arrive as a parameter rather than being read here
     *
     * Because [fleet] already observes them. Subscribing to `observeVehicles()` twice on one
     * screen is two queries for one answer, so the plates ride in on the same flow that reads the
     * contracts and are joined inside the projection.
     */
    private fun chartProjection(
        byDay: List<net.klinik.rental.data.db.dao.DailyIncome>,
        incomeByContract: Map<String, Long>,
        allContracts: List<net.klinik.rental.domain.model.Contract>,
        vehicles: List<net.klinik.rental.domain.model.Vehicle>,
        ledger: LedgerInputs,
        today: LocalDate,
    ): DashboardCharts {
        // Zero-filled from the day before, so a quiet day is a point at zero rather than a gap the
        // chart would have to infer — and a straight line through that gap would read as a steady
        // Tuesday when nothing arrived.
        val daily = today.minusDays(TREND_DAYS - 1L).datesUntil(today.plusDays(1))
            .map { day -> day to (byDay.firstOrNull { it.date == day }?.total ?: 0L) }
            .toList()

        val dailyRate = allContracts.filter { it.endDate == null }.sumOf { it.dailyRate }
        val plateByVehicle = vehicles.associate { it.id to it.plate }

        val slices = incomeByContract
            .filter { it.value > 0L }
            .map { (contractId, total) ->
                val contract = allContracts.firstOrNull { it.id == contractId }
                (contract?.let { plateByVehicle[it.vehicleId] } ?: contractId) to total
            }
            .sortedByDescending { it.second }

        val weeks = weeklyTotals(daily, COMPARISON_WEEKS)
        val weekTarget = dailyRate * DAYS_PER_WEEK
        // "Di atas target" is a claim about the **latest day**, the same way the prototype's own
        // badge reads (`dashboard.html:250-252`) — not about the fortnight's total, which a good
        // week and a bad one average into a number neither statement is true of.
        val latest = daily.lastOrNull()?.second ?: 0L

        return DashboardCharts(
            trend = daily.map { it.second },
            trendTarget = dailyRate,
            trendLabels = daily.map { strings.formatOldestOpenDay(it.first).orEmpty() },
            weeks = weeks.map { it.total },
            weekTarget = weekTarget,
            slices = slices.map { it.second },
            sliceLabels = slices.map { it.first },
            comparison = weeks.map { row ->
                val range = "${strings.formatOldestOpenDay(row.start)} – " +
                    strings.formatOldestOpenDay(row.end)
                ComparisonRow(
                    label = row.label,
                    rangeText = range,
                    labelText = strings.berandaCompareRow(row.label, range),
                    collectedText = formatRupiah(row.total),
                    targetText = formatRupiah(weekTarget),
                    fraction = if (weekTarget <= 0L) 0f else row.total.toFloat() / weekTarget.toFloat(),
                )
            },
            trendTotalText = formatRupiah(daily.sumOf { it.second }),
            trendMeetsTarget = dailyRate > 0L && latest >= dailyRate,
            trendGapText = formatRupiah((dailyRate - latest).coerceAtLeast(0L)),
            recent = recentRows(ledger, allContracts, plateByVehicle),
        )
    }

    /**
     * The prototype's "Transaksi terakhir" — the eight newest confirmed deposits.
     *
     * ## Why eight, and why "confirmed"
     *
     * `dashboard.html:325` takes the newest eight of the confirmed rows. A cancelled deposit is
     * not income and must not sit in a table headed "what came in" — the same filter the income
     * queries apply, applied here because the history read deliberately returns every row for
     * its audit trail.
     */
    private fun recentRows(
        ledger: LedgerInputs,
        allContracts: List<net.klinik.rental.domain.model.Contract>,
        plateByVehicle: Map<String, String>,
    ): List<RecentTransaction> {
        val contractById = allContracts.associateBy { it.id }
        return ledger.rows
            .asSequence()
            .filter { !it.isCanceled }
            .take(RECENT_ROWS)
            .map { row ->
                val contract = contractById[row.contractId]
                RecentTransaction(
                    dateText = strings.formatOldestOpenDay(row.date).orEmpty(),
                    renterName = contract?.let { ledger.renterNames[it.renterId] }
                        ?: strings.dashboardUnknownName(),
                    plate = contract?.let { plateByVehicle[it.vehicleId] } ?: DASH,
                    bank = row.accountId?.let { ledger.bankNames[it] } ?: CASH,
                    reference = row.referenceNo.orEmpty().ifBlank { DASH },
                    amountText = formatRupiah(row.amount),
                )
            }
            .toList()
    }

    /**
     * The last [weeks] seven-day windows, oldest first.
     *
     * **Derived from the same daily series the trend draws**, never summed from the table again:
     * a second pass over the deposits would be a second answer to "how much came in last week".
     */
    private fun weeklyTotals(
        daily: List<Pair<LocalDate, Long>>,
        weeks: Int,
    ): List<WeekTotal> {
        // The series is zero-filled by the caller, so it always has a last day — the fallback
        // exists so this stays total rather than because it is expected.
        val last = daily.lastOrNull()?.first ?: LocalDate.now(clock)
        return (weeks - 1 downTo 0).map { back ->
            val end = last.minusDays(back * DAYS_PER_WEEK)
            val start = end.minusDays(DAYS_PER_WEEK - 1L)
            WeekTotal(
                label = WEEK_LABELS.getOrElse(back) { "M-$back" },
                start = start,
                end = end,
                total = daily
                    .filter { day -> !day.first.isBefore(start) && !day.first.isAfter(end) }
                    .sumOf { it.second },
            )
        }
    }

    /** One weekly window and what arrived in it. */
    private data class WeekTotal(
        val label: String,
        val start: LocalDate,
        val end: LocalDate,
        val total: Long,
    )

    /**
     * Story 5.5's reminder source, read as its own flow.
     *
     * ## Why it reads the deposit table directly rather than reusing `fleet` or the arrears source
     *
     * Because "has this owner anything to lose" is a question about **deposits**, and neither of
     * those flows carries one. Reading a table the reminder alone cares about is cheaper than
     * widening a source every other screen also pays for.
     *
     * ## Why it re-reads on every emission rather than once
     *
     * Because two of its three inputs are settings the owner can change from another screen, and
     * a reminder computed once at construction would show a date the export has since replaced.
     * `observeAllForHistory` re-emits after every export, which is exactly when the answer
     * changes.
     */

    private val reminderSource: Flow<BackupReminderUi?> = flow {
        val repository = backups
        if (repository == null) {
            // No repository: the screen builds one it did not ask for, which would be a reminder
            // whose date came from somewhere other than the export that set it.
            emit(null)
            return@flow
        }
        repository.observeDepositCount().collect { count ->
            emit(
                backupReminder(
                    lastExport = repository.lastBackupDate(),
                    depositCount = count,
                    today = LocalDate.now(clock),
                    // The hour is read from the clock, **not** from `today` — a `LocalDate` has
                    // no hour, and reading one is a compile error rather than a reminder that
                    // quietly never appears.
                    currentHour = LocalDateTime.now(clock).hour,
                    hour = nudgeHour,
                    strings = strings,
                ),
            )
        }
    }

    /**
     * The period the income tile reads at.
     *
     * The prototype's segmented control (`dashboard.html:29-34`) is the screen's own
     * filter: *Hari ini / Minggu / Cut off / Custom*. Only the **income** tile follows
     * it — the cutoff tile is always the billing period, the arrears total is always
     * everything outstanding, and the rate is always over the whole book.
     */
    private val period = MutableStateFlow(DashboardPeriod.Today)

    /**
     * Picks the period the income tile reads at.
     */
    fun selectPeriod(selected: DashboardPeriod) {
        period.value = selected
    }

    /**
     * The screen state.
     *
     * `flatMapLatest` on the arrears source keeps the failure a value rather than an
     * exception, and the nested `combine` keeps every source typed — the same reason
     * `MobilViewModel` nests rather than reaching for the `vararg` overload.
     */
    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            arrearsSource,
            // The income tile reads the **selected** period, so the read is a function of
            // the filter rather than a fixed one. `flatMapLatest` cancels the old window
            // when the owner picks another, so a slow read cannot land on top of a newer one.
            //
            // The window is computed from the **saved** cutoff day, not the default: the
            // owner's setting decides which days the engine charges for, and a filter that
            // used the default would read a period the app does not bill.
            period.flatMapLatest { selected ->
                val day = cutoffDay()
                payments.observeIncomeBetween(
                    selected.start(today, day),
                    selected.end(today, day),
                )
            },
            period.flatMapLatest { selected ->
                val day = cutoffDay()
                payments.observeTransactionCountBetween(
                    selected.start(today, day),
                    selected.end(today, day),
                )
            },
        ) { source, income, transactions -> Triple(source, income, transactions) },
        fleet,
        reminderSource,
        chartSources,
        cutoffIncome,
    ) { sourceAndIncome, vehiclesAndPlates, reminder, chartInputs, periodIncome ->
        val (source, income, transactions) = sourceAndIncome
        val (vehicles, rentedPlates) = vehiclesAndPlates
        val split = splitFleet(vehicles, rentedPlates)
        val arrears = source.arrearsTotal()
        val charts = if (source is Source.Loading) {
            // A chart drawn from a read that has not landed shows a flat line at zero for the
            // frames before the first emission, which reads as "no income for two weeks".
            DashboardCharts()
        } else {
            chartProjection(
                byDay = chartInputs.byDay,
                incomeByContract = chartInputs.byContract,
                allContracts = chartInputs.allContracts,
                // The plates ride in on **this** flow's own fleet read — no second subscription.
                vehicles = vehicles,
                ledger = chartInputs.ledger,
                today = today,
            )
        }
        DashboardUiState(
            incomeText = formatRupiah(income),
            incomeMeta = strings.dashboardIncomeMeta(transactions),
            arrearsText = formatRupiah(arrears),
            arrearsMeta = strings.dashboardArrearsMeta(source.criticalCount()),
            rateMeta = strings.dashboardRateMeta(split.rented, split.available),
            fleetText = strings.berandaFleetSplit(split.available, split.rented, split.service),
            rateText = strings.berandaRate(collectionRate(income, arrears)),
            // The prototype's cutoff tile is a **different window** from the income tile
            // beside it (`dashboard.html:175-179`): this one is always the billing period the
            // engine is charging for, whatever the period filter says. It is the figure the
            // owner reconciles against an invoice, so it is read from the period, not from
            // today's deposits.
            cutoffIncomeText = formatRupiah(periodIncome.first),
            cutoffSinceText = strings.dashboardCutoffSince(periodIncome.second),
            fleet = split,
            incomeRupiah = income,
            arrearsRupiah = arrears,
            // Loading is the **source's** state, not an empty list: a read that has not
            // happened and a read that found nothing are different facts, and a morning
            // screen that says "Rp 0" before it has looked is the app claiming nobody owes
            // anything.
            isLoading = source is Source.Loading,
            backupReminder = reminder,
            charts = charts,
        )
    }
        // After the projection, so an emission that produces identical figures does not
        // recompose the tiles for no visible change.
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(isLoading = true),
        )

    /**
     * The arrears total, in whole rupiah.
     *
     * The sum over the entries, which already excludes the contracts that owe nothing —
     * so the population is Story 4.1's list by construction. A failed or unfinished read
     * contributes **zero**, because a total nobody can reconcile is worse than a missing
     * one: the tile shows the loading state instead, and the owner is never shown a figure
     * the app could not stand behind.
     */
    private fun Source.arrearsTotal(): Long = when (this) {
        Source.Loading, is Source.Failed -> 0L
        is Source.Loaded -> entries.sumOf { it.amount }
    }

    /**
     * How many contracts are **KRITIS** — the dashboard's arrears meta
     * (`dashboard.html:181`).
     *
     * Read from the **same** entries the total is summed from, not from a second arrears
     * query. A count beside a total that came from elsewhere is a figure that can disagree
     * with the number it describes, and this is the screen where that would be noticed.
     *
     * The tier comes from the domain's own [riskOf] rather than a `days >= 7` written here.
     * That function is what the arrears screen badges with, and a second copy of the
     * threshold is a second thing that can drift from it — the ladder is four levels wide
     * and one literal would only have to be wrong once.
     */
    private fun Source.criticalCount(): Int = when (this) {
        Source.Loading, is Source.Failed -> 0
        is Source.Loaded -> entries.count { riskOf(it.days) == RiskLevel.KRITIS }
    }


    /**
     * Story 5.5: the owner tapped the reminder.
     *
     * Records **that** they asked, and nothing else - no flag the banner reads, no route, no
     * write. It exists so the tap is observable from a test: a dialog appearing proves the
     * button was pressed, but not that the app's own callback chain is intact, and this is
     * the end of that chain.
     */
    fun onBackupReminderTapped() {
        taps += 1
    }

    /** How many times the reminder has been tapped. For a test, and nothing else. */
    val reminderTaps: Int get() = taps

    private var taps = 0

    private fun backupReminder(
        lastExport: LocalDate?,
        depositCount: Int,
        today: LocalDate,
        currentHour: Int,
        hour: Int,
        strings: RStrings,
    ): BackupReminderUi? {
        // Never exported and nothing to lose: a reminder about backing up nothing is noise,
        // and the first-run owner would meet it on the morning they have not yet typed a
        // deposit.
        if (lastExport == today) return null
        if (depositCount == 0) return null
        if (currentHour < hour) return null

        return BackupReminderUi(
            body = strings.berandaBackupReminderBody(),
            lastExportLine = lastExport
                ?.let { strings.berandaBackupReminderLast(strings.formatExceptionDate(it)) },
        )
    }
}

/**
 * The fleet's three counts, through Story 2.3's derivation.
 *
 * The status is **derived** rather than counted from a column, because this app stores no
 * status: a vehicle is `Tersewa` because an open contract names its plate, and `Servis`
 * because the owner set the manual marker. `VehicleStatus.of` is that rule, and it is the
 * same function the fleet list uses — so the morning screen and the fleet screen cannot
 * disagree about which car is off the road.
 */
fun splitFleet(
    vehicles: List<net.klinik.rental.domain.model.Vehicle>,
    rentedPlates: Set<String>,
): FleetSplit {
    var available = 0
    var rented = 0
    var service = 0
    for (vehicle in vehicles) {
        when (
            VehicleStatus.of(
                isServis = vehicle.isServis,
                hasOpenContract = vehicle.plate in rentedPlates,
            )
        ) {
            VehicleStatus.Servis -> service++
            VehicleStatus.Tersewa -> rented++
            VehicleStatus.Tersedia -> available++
        }
    }
    return FleetSplit(available = available, rented = rented, service = service)
}

/**
 * `dashboard.html:198` — the trend's window, in days.
 *
 * Fourteen rather than the seven `LaporanScreen` uses: the dashboard is the wide screen, and a
 * fortnight is what makes a weekly rhythm visible. The reports screen keeps seven because it is
 * summarising a billing period, not a habit.
 */
private const val TREND_DAYS = 14L

/** Four weekly windows — `dashboard.html:256`, `renderBars`'s `length: 4`. */
private const val COMPARISON_WEEKS = 4

private const val DAYS_PER_WEEK = 7L

/** `dashboard.html:267-275` — four bars, oldest (`M-3`) first. */
private val WEEK_LABELS = listOf("M-0", "M-1", "M-2", "M-3")

/** `dashboard.html:325` — the table shows the newest eight confirmed deposits. */
private const val RECENT_ROWS = 8

/** The prototype's own placeholder for a field a joined row could not resolve. */
private const val DASH = "—"

/** A cash deposit's bank column — `payment.accountId` is null for cash (`dashboard.html:333`). */
private const val CASH = "Tunai"
