package net.klinik.rental.ui.screens.dashboard

import net.klinik.rental.R
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.ui.components.KnBadgeTone
import java.time.LocalDate

/**
 * Which period the dashboard's income tile reads at.
 *
 * `dashboard.html:29-34` — the prototype's segmented control, four stops:
 * *Hari ini / Minggu / Cut off / Custom*.
 *
 * An enum rather than a Boolean because there are four, and a Boolean can only say two.
 * [Custom] is a fourth state rather than a fifth control: it names the *shape* of the
 * range rather than a range of its own, because the range itself is two dates the owner
 * types.
 */
enum class DashboardPeriod {
    /** Today — the deposits dated today. */
    Today,

    /** The last seven days. */
    Week,

    /** The billing period the engine is charging for. */
    Cutoff,

    /** A range the owner typed. */
    Custom,
    ;

    /**
     * This stop's label.
     *
     * A resource rather than a literal, so the same four words the prototype's control
     * shows are the ones this app shows.
     */
    fun labelRes(): Int = when (this) {
        Today -> R.string.dashboard_period_today
        Week -> R.string.dashboard_period_week
        Cutoff -> R.string.dashboard_period_cutoff
        Custom -> R.string.dashboard_period_custom
    }
    ;

    /**
     * The first day of this period, given [today].
     *
     * @param cutoffDay the owner's saved cutoff day, for [Cutoff].
     */
    fun start(today: LocalDate, cutoffDay: Int = PaymentRepository.DEFAULT_CUTOFF_DAY): LocalDate =
        when (this) {
            Today -> today
            // Seven days, inclusive of today: `dashboard.html:152` reads the last seven
            // days, and a window that starts six days back is six days long.
            Week -> today.minusDays(6)
            Cutoff -> CutoffPeriod.containing(cutoffDay, today).start
            // The custom range's own start, which the owner typed. `today` is the
            // fallback because a range that has not been typed yet is not a range.
            Custom -> today
        }

    /**
     * The last day of this period, given [today].
     *
     * @param cutoffDay the owner's saved cutoff day, for [Cutoff].
     */
    fun end(today: LocalDate, cutoffDay: Int = PaymentRepository.DEFAULT_CUTOFF_DAY): LocalDate =
        when (this) {
            Today, Week -> today
            Cutoff -> CutoffPeriod.containing(cutoffDay, today).end
            Custom -> today
        }
}

/**
 * The morning summary's state — Story 4.3.
 *
 * ## Why every figure arrives already formatted
 *
 * Because a rupiah amount has exactly one spelling in this app, and it is produced by
 * `formatRupiah` at the point the number is known. A state that carried a `Long` would push
 * that decision to the composable, and two screens formatting the same figure differently is
 * the second-derivation defect this repo keeps catching. So the ViewModel formats, and the
 * screen draws text.
 *
 * ## Why the four tiles carry no `isPrimary`
 *
 * Because the acceptance criterion is explicit that no KPI leads, and a flag that could make
 * one lead is a flag that eventually will. The four are the same kind of thing, and the screen
 * draws them as the same kind of thing.
 */
data class DashboardUiState(
    /**
     * The period the income tile reads at — `dashboard.html:29-34`.
     *
     * The prototype's dashboard has four: *Hari ini / Minggu / Cut off / Custom*. The
     * screen's own filter, and it changes what the income tile and its meta say while
     * the other three tiles stay on the billing period.
     */
    val period: DashboardPeriod = DashboardPeriod.Today,
    /** `Rp 750.000` — the confirmed deposits dated today, in whole rupiah. */
    val incomeText: String = "",
    /** `Rp 4.250.000` — the arrears across open *and* ended contracts that still owe. */
    val arrearsText: String = "",
    /** `12 · 3 · 1` — tersa, tersewa, servis. See the class KDoc on the fleet split. */
    val fleetText: String = "",
    /** `75%` — the collection rate. Never a dash: a denominator of zero reads 100%. */
    val rateText: String = "",
    /**
     * `3 transaksi` — the meta under the income tile (`dashboard.html:176`).
     *
     * The count is the period's confirmed deposits, so the figure above it can be
     * checked: a rupiah total with no row count is a number the owner cannot reconcile
     * against the history screen.
     */
    val incomeMeta: String = "",
    /**
     * `0 kasus kritis` — the meta under the arrears tile (`dashboard.html:181`).
     */
    val arrearsMeta: String = "",
    /**
     * `0 disewa · 0 tersedia` — the meta under the collection-rate tile
     * (`dashboard.html:183`).
     */
    val rateMeta: String = "",
    /**
     * `Rp 4.250.000` — income in the **cutoff period**, not today
     * (`dashboard.html:178`).
     *
     * A different window from [incomeText]: that one follows the period filter, this one
     * is always the billing period the engine is charging for. The prototype shows both
     * side by side so the owner can see today against the period it belongs to.
     */
    val cutoffIncomeText: String = "",
    /** `sejak 25 Sep` — when the current cutoff period started (`dashboard.html:179`). */
    val cutoffSinceText: String = "",
    /**
     * The fleet's three counts, kept as numbers as well as text.
     *
     * The text is what the tile draws; the numbers are what a test asserts on, because
     * "12 · 3 · 1" is a string and a string can be right by accident. Held here so the screen
     * has one source and the test has one truth.
     */
    val fleet: FleetSplit = FleetSplit(),
    /**
     * The income and arrears the rate was computed from.
     *
     * Kept because a rate is a division, and a division whose operands cannot be seen is a
     * figure nobody can check. A test asserts the rate against these two, not against a
     * hand-written percentage.
     */
    val incomeRupiah: Long = 0L,
    val arrearsRupiah: Long = 0L,
    val isLoading: Boolean = false,
    /**
     * Story 5.5's reminder, or `null` when there is nothing to say.
     *
     * `null` is three separate "no reminder" cases — never exported, exported today, or a
     * database with no deposits — collapsed into one, because the screen draws one thing or
     * nothing and the *reason* for silence is not the owner's business. A reminder that said
     * "you have not backed up today, but you have no data anyway" would be the app talking to
     * itself.
     */
    val backupReminder: BackupReminderUi? = null,
    /** The dashboard's charts — `dashboard.html:60-106`. Empty until the first read lands. */
    val charts: DashboardCharts = DashboardCharts(),
) {
    /**
     * Whether the summary has nothing to show.
     *
     * **Not** `incomeText.isEmpty()` and **not** a "no arrears" test: a morning with no
     * income and no arrears is a real, good morning, and the screen says so rather than
     * showing a skeleton or an invitation to record a deposit.
     */
    val isQuietMorning: Boolean
        get() = incomeRupiah == 0L && arrearsRupiah == 0L
}

/**
 * The reminder's content, already formatted.
 *
 * Built by the ViewModel for the reason every `*Ui` type in this app is built: a sentence
 * assembled in a composable is assembled per recomposition, and the last-export line is
 * optional in a way a `null` check in the UI would have to get right every time.
 *
 * @property lastExportLine `Ekspor terakhir 19 Okt`, or `null` when the owner has never
 *   exported — the AC says *"menyebut tanggal ekspor terakhir, **kalau ada**"*, so a first-run
 *   reminder carries no date rather than a line reading "never".
 */
data class BackupReminderUi(
    val body: String,
    val lastExportLine: String?,
)

/**
 * The dashboard's three charts and its comparison rows — `dashboard.html:60-106,285-287`.
 *
 * ## Why these carry no `null`
 *
 * Because a chart with no data is still a chart: an empty series draws a flat baseline, an empty
 * donut draws its sunken placeholder, and an empty comparison says "no money yet this period" —
 * which is a true statement, not a missing one. A nullable chart would give the screen three ways
 * to draw nothing and the shape that skips the data is the one that gets forgotten.
 *
 * @property trend the last 14 days, oldest first — `renderLine` in `dashboard.html:198`.
 * @property trendTarget the daily rate the active contracts bill; the dashed line above the series.
 * @property trendLabels a label per trend point; the chart draws every other one.
 * @property weeks four weekly totals, oldest first — `renderBars`, `dashboard.html:256`.
 * @property weekTarget the weekly figure each bar is measured against.
 * @property slices the per-vehicle shares, largest first — `renderDonut`, `dashboard.html:291`.
 * @property comparison this period against the one before it — `renderBars`, `dashboard.html:277`.
 */
data class DashboardCharts(
    val trend: List<Long> = emptyList(),
    val trendTarget: Long = 0L,
    val trendLabels: List<String> = emptyList(),
    val weeks: List<Long> = emptyList(),
    val weekTarget: Long = 0L,
    val slices: List<Long> = emptyList(),
    /**
     * The plate each slice belongs to, in the same order as [slices].
     *
     * Carried beside the amounts rather than joined at draw time because a donut with no legend
     * is a shape the owner cannot act on — "a third of your income" is a fact, "Agya, Avanza,
     * Xpander" is the one that tells the owner which car to look at.
     */
    val sliceLabels: List<String> = emptyList(),
    val comparison: List<ComparisonRow> = emptyList(),
    /**
     * `14 hari · total Rp 8.400.000` — the trend card's subtitle (`dashboard.html:249`).
     *
     * Formatted here for the reason every figure in this app is: one formatter, one spelling.
     */
    val trendTotalText: String = "",
    /**
     * Whether the latest day met the daily target — the trend badge's own fact.
     *
     * A Boolean rather than a sentence, so the *tone* is decided by the screen from a fact and the
     * text comes from resources. A ViewModel that chose the colour would be a ViewModel that knew
     * about themes.
     */
    val trendMeetsTarget: Boolean = false,
    /** How far the latest day fell short, formatted. Only drawn when [trendMeetsTarget] is false. */
    val trendGapText: String = "",
    /** "Transaksi terakhir" — the newest confirmed deposits (`dashboard.html:108-117`). */
    val recent: List<RecentTransaction> = emptyList(),
)

/**
 * One row of the dashboard's transaction table.
 *
 * ## Why every field is a `String`
 *
 * Because each is either already-formatted text or a name this app does not own — a plate, a
 * renter's name, a bank's — and a row that carried the domain objects would let the table
 * re-derive a spelling the rest of the app already fixed.
 */
data class RecentTransaction(
    /** `19 Okt` — [net.klinik.rental.ui.format.formatTanggalBulan]'s form. */
    val dateText: String,
    val renterName: String,
    val plate: String,
    val bank: String,
    val reference: String,
    val amountText: String,
)

/**
 * One row of the period comparison — the target, what was collected, and how far short.
 *
 * ## Why the target and the collected are both carried
 *
 * Because a comparison is a **difference**, and a difference whose operands cannot be seen is a
 * number nobody can check — the same reason `DashboardUiState` carries [incomeRupiah] beside
 * [rateText].
 */
data class ComparisonRow(
    /** `M-1` or `M-2`, the prototype's own labels. */
    val label: String,
    val rangeText: String,
    /**
     * `M-1 (1 Okt – 7 Okt)` — the label and its range, already composed.
     *
     * Composed here rather than in the screen because the parenthesis is a *format*, and a format
     * assembled in a composable is one that gets assembled differently the second time. The
     * resource holds it, [net.klinik.rental.ui.strings.RStrings] applies it, and the screen draws
     * the result.
     */
    val labelText: String,
    val collectedText: String,
    val targetText: String,
    /** 0..1, what fraction of the target arrived. Drives the progress bar's colour. */
    val fraction: Float,
)

/**
 * The fleet's three counts, in the order the tile shows them.
 *
 * ## Why the split is three numbers and not one
 *
 * Because the owner asks three different questions: how many cars are on the road, how many
 * are rented, and how many are off it. One "fleet total" would answer none of them.
 *
 * ## Why `Servis` wins a vehicle that is both
 *
 * Because that is Story 2.3's rule and this screen does not re-derive it — the same
 * `VehicleStatus.of` the fleet list uses, with `Servis` checked first because a car in for
 * service *and* still under contract is off the road, which is the fact the owner wants on a
 * morning screen. The open contract still bills either way, so nothing is lost by preferring
 * it here.
 */
data class FleetSplit(
    /** Available — neither in service nor under an open contract. */
    val available: Int = 0,
    /** Rented — under an open contract and not in service. */
    val rented: Int = 0,
    /** In service, by the manual marker. Wins over [rented]. */
    val service: Int = 0,
)

/**
 * The collection rate, as a whole percentage.
 *
 * ## Why 100% when there is nothing to collect
 *
 * Because the rate answers "how much of what was owed has been paid", and a morning with no
 * arrears has nothing outstanding — which is a full collection, not an unmeasured one. Reading
 * it as 0% would tell the owner their tenants are behind when nobody owes anything, and
 * reading it as a dash would leave the tile without a figure at all.
 *
 * ## Why the percentage is whole
 *
 * Because the tile has room for a percentage and not for a decimal, and because a figure
 * like "66.7%" implies a precision the underlying rupiah does not carry — the number is
 * whole, so the ratio of two whole numbers is shown to the nearest whole percent.
 */
fun collectionRate(income: Long, arrears: Long): Int {
    val denominator = income + arrears
    if (denominator == 0L) return 100
    return ((income * 100L) / denominator).toInt()
}
