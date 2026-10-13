package net.klinik.rental.domain.calculate

import net.klinik.rental.domain.model.Allocation
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.domain.validation.InvalidAmountException
import java.time.LocalDate

/**
 * Replays a contract's payments across its charge days, oldest day first, and returns the
 * rows that say which rupiah closed which day.
 *
 * This is the whole of AD-3. Arrears is not computed here and no credit balance is stored:
 * both are consequences of the returned rows, which is why the Rp 13.000.000 that
 * `verify-fr10.md` case 4 measured vanishing at the month rollover cannot vanish here —
 * it comes back as one row whose [Allocation.chargeDate] is `null`.
 *
 * **The rules, in the order they bite.**
 *
 * 1. *Payments are consumed oldest first*, ties broken by [Payment.id]. Order is
 *    deterministic because the stored rows are compared across recomputes, and a
 *    recompute that shuffled rows would look like a data change.
 * 2. *A payment dated after [asOf] contributes nothing yet.* Gating is by date alone, never
 *    by period: `verify-fr10.md` case 4 is exactly the bug that appears when a payment is
 *    filtered to "inside the current period", and case 7 is the payment that must wait for
 *    the rollover rather than be counted early.
 * 3. *Charge days are filled oldest first, each up to [dailyRate].* A payment dated on a
 *    rest day, on a service exception, before the contract started, or simply late still
 *    closes the **oldest open day** — never the day nearest its own date. There is no
 *    proximity rule here; case 1 pins Rp 500.000 received 20 Mar onto 25 and 26 Feb, and
 *    case 2's whole point is that money arriving on a Sunday is credited rather than
 *    stranded.
 * 4. *Whatever is left over becomes one row with `chargeDate = null` per payment* — and
 *    only for payments that still hold cash, so a payment that fits exactly produces none.
 *    If the day list runs out with several payments unpaid, each gets its own row; emitting
 *    only the first would make the tail vanish, which is this story's failure mode in a
 *    smaller denomination.
 *
 * **Why the leftover is a regenerated row rather than a stored balance.** AD-3 calls these
 * rows facts and forbids recomputing arrears from payments, while also requiring
 * `Σ allocation == amount` for every payment. Those two only collide if the `null` row is
 * permanent. It is not: oldest-first allocation is **prefix-stable** — appending later
 * charge days can never change which day an earlier rupiah closed, because the earlier
 * days were already full or the money already ran out. A longer day list therefore only
 * *fills* the leftover and appends rows; the earlier ones come back identical. The
 * `chargeDate = null` row is the one thing that changes value, which is exactly why it is
 * marked "not yet placed on a day" instead of pretending to name a day.
 *
 * `verify-fr10.md`'s Option B, and its price — "pembatalan harus membuka alokasi lalu
 * menghitung ulang" — is paid by Epic 3 inside its `@Transaction`, not here. Nothing in
 * this function touches a database, and nothing it returns is state.
 *
 * @param payments every deposit on the contract; unfiltered. [asOf] decides which ones
 *   count yet.
 * @param chargeDays the days to charge, ascending and free of gaps — [chargeDaysIn] output.
 * @param dailyRate rupiah owed per charge day, the contract's constant rate.
 * @param asOf today, or rather the date the calculation is being made "as of".
 * @throws InvalidAmountException if [dailyRate] is zero or negative.
 * @throws IllegalStateException if [chargeDays] is not strictly ascending, or if two of the
 *   counted [payments] share an [Payment.id].
 */
fun allocatePayments(
    payments: List<Payment>,
    chargeDays: List<LocalDate>,
    dailyRate: Long,
    asOf: LocalDate,
): List<Allocation> {
    InvalidAmountException.requireValid(dailyRate)

    // The unique key `UNIQUE(paymentId, chargeDate)` on ALLOCATION is a correctness
    // constraint, not an index, and this module is where it is enforced: the day loop below
    // visits each day once and hands it to at most one row per payment, so a duplicate is
    // impossible *provided* the days are distinct and in order. Asserting the precondition
    // keeps that guarantee true for every caller rather than for callers that remembered.
    check(chargeDays.zipWithNext().all { (earlier, later) -> earlier.isBefore(later) }) {
        "chargeDays must be strictly ascending and gap-free; got ${chargeDays.size} days " +
            "where at least one repeats or is out of order"
    }

    val queue = payments
        .filter { !it.date.isAfter(asOf) }
        .sortedWith(compareBy({ it.date }, { it.id }))

    // Two deposits sharing an id would each get a row per day under the same `paymentId`,
    // which is the duplicate UNIQUE(paymentId, chargeDate) exists to forbid — and unlike the
    // day-order check above, no per-payment rule below would notice. Persistence owns the
    // primary key, so this is where "the same deposit twice" has to be said out loud.
    check(queue.map { it.id }.toSet().size == queue.size) {
        "payment ids must be unique; the same deposit appearing twice would write two rows " +
            "for one (paymentId, chargeDate)"
    }

    // Rows are bucketed per payment so the Σ check below is a real recomputation rather than
    // a restatement of the bookkeeping, and so the output is grouped the way the unique key
    // reads: one payment's days in ascending order, its unplaced remainder last.
    val rowsPerPayment: List<MutableList<Allocation>> = List(queue.size) { mutableListOf() }

    var index = 0
    var remaining = if (queue.isEmpty()) 0L else queue[0].amount

    for (day in chargeDays) {
        var unfilled = dailyRate
        while (unfilled > 0L && remaining > 0L) {
            val payment = queue[index]
            // index only advances when a payment is exhausted, so a payment is met at most
            // once per day — that is the other half of the unique-key guarantee.
            val applied = minOf(unfilled, remaining)
            rowsPerPayment[index] += Allocation(payment.id, day, applied)
            unfilled -= applied
            remaining -= applied
            if (remaining == 0L) {
                index++
                if (index < queue.size) remaining = queue[index].amount
            }
        }
        if (index >= queue.size) break
    }

    // Money with no day left to sit on. Not just the payment the day loop stopped on: if the
    // day list runs out while three payments still hold cash, every one of them needs its own
    // row or the tail silently vanishes — which is the Rp 13.000.000 failure again, smaller.
    // Each amount is strictly positive; the loop only reaches here with money in hand.
    for (i in index until queue.size) {
        val unplaced = if (i == index) remaining else queue[i].amount
        if (unplaced > 0L) {
            rowsPerPayment[i] += Allocation(queue[i].id, chargeDate = null, amount = unplaced)
        }
    }

    queue.forEachIndexed { i, payment ->
        val allocated = rowsPerPayment[i].sumOf { it.amount }
        check(allocated == payment.amount) {
            "allocation rows for payment ${payment.id} sum to $allocated, not its amount " +
                "${payment.amount}; Σ allocation == amount is AD-3's one unconditional rule"
        }
    }

    return rowsPerPayment.flatten()
}