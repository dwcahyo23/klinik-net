package net.klinik.rental.ui.screens.beranda

import net.klinik.rental.ui.components.KnBadgeTone

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
data class BerandaUiState(
    /** `Rp 750.000` — the confirmed deposits dated today, in whole rupiah. */
    val incomeText: String = "",
    /** `Rp 4.250.000` — the arrears across open *and* ended contracts that still owe. */
    val arrearsText: String = "",
    /** `12 · 3 · 1` — tersa, tersewa, servis. See the class KDoc on the fleet split. */
    val fleetText: String = "",
    /** `75%` — the collection rate. Never a dash: a denominator of zero reads 100%. */
    val rateText: String = "",
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
