package net.klinik.rental.data.repository

import net.klinik.rental.data.db.dao.PaymentDao
import net.klinik.rental.data.db.entity.PaymentEntity
import java.time.LocalDate

/**
 * One deposit, projected for the **history list** — Epic 3's `requestCancel` and
 * `openEditDeposit`, both finished and both unreachable until a row existed to tap.
 *
 * ## Why a second projection rather than reusing [EditableDeposit]
 *
 * Because the two carry deliberately opposite field sets, and merging them would undo a
 * decision Story 3.5 made on purpose:
 *
 *  - [EditableDeposit] carries **no `status` and no `cancelReason`**, so the correction
 *    form cannot grow a cancellation feature. Its KDoc says so.
 *  - This one carries **both**, because a cancelled deposit keeps its row and the owner's
 *    own reason for cancelling it is the whole reason to keep it. A history list that
 *    hid a cancellation would make an audit trail that cannot be read — the exact loss
 *    Story 3.4 refused when it chose a status flag over a delete.
 *
 * So: one projection for the form, one for the list, each holding only what its own
 * surface needs. A shared "deposit" type that carried everything would hand the
 * correction form a cancellation column it must not show.
 *
 * ## Why the read is [PaymentDao.observeForContract] and not a new query
 *
 * That query already returns **every** row, cancelled ones included, and its KDoc gives
 * the reason: the status filter belongs in the allocator, not in a reader, because a
 * filter somebody forgets to apply is how money disappears. Adding a query here would
 * have been a second reader of the same table, and the first thing it would do is
 * reintroduce that filter.
 *
 * @property isCanceled derived rather than stored, so the row and the badge cannot
 *   disagree. `PaymentEntity.STATUS_CANCELED` is the single source; this is the spelling
 *   of it at the row level.
 */
data class DepositHistoryRow(
    val id: String,
    val contractId: String,
    val date: LocalDate,
    /** Rupiah received, **raw** — formatted by the caller, per the dependency direction. */
    val amount: Long,
    /** Decoded, so the UI never parses the column's spelling. See [EditableDeposit]. */
    val method: PaymentMethod,
    /** The account a transfer arrived through, or `null` for cash. */
    val accountId: String?,
    val referenceNo: String?,
    val notes: String?,
    val isCanceled: Boolean,
    /** The owner's own words for cancelling, or `null` when not cancelled. */
    val cancelReason: String?,
) {
    /**
     * Whether either verb may be offered on this row.
     *
     * **Both** verbs, not just the correction one. A cancelled deposit cannot be edited
     * because `correctDeposit` refuses it, and it cannot be cancelled again because
     * `cancelDeposit` refuses that — so a row offering either would render live and
     * ignore the tap. That is the "renders, wired to nothing" defect this repo has
     * shipped three times, and `Screens.Kontrak`'s own KDoc is the rule that forbids it.
     *
     * Derived rather than passed in, so a screen cannot offer a verb the repository
     * would refuse.
     */
    val isEditable: Boolean get() = !isCanceled
}

/**
 * Reads a contract's deposit history.
 *
 * ## Why it is its own class rather than a method on [PaymentRepository]
 *
 * Because it is a **read of the owner's records** and no part of it feeds the
 * allocation arithmetic. The rule that gives `PaymentRepository` its identity — "the one
 * class that computes what a deposit closed" — is not this class's job, and putting a
 * screen-shaped projection beside the allocator is how the two drift apart.
 *
 * No transaction helper either: this reads a flow and writes nothing, so there is
 * nothing to isolate. That is the same reason [RenterRepository] has none.
 */
class DepositHistoryRepository(
    private val paymentDao: PaymentDao,
) {

    /**
     * Every deposit on [contractId], oldest first, cancelled ones included.
     *
     * One-shot, because the caller is a **sheet** that opens for one contract and closes
     * when the owner is done: a subscription that outlived the sheet would recompute for
     * a window nobody is looking at.
     *
     * The ordering comes from the query (`ORDER BY date ASC, id ASC`), which is also
     * how a bank statement reads, rather than from a sort here — a second sort is a
     * second answer to "which came first".
     */
    suspend fun forContract(contractId: String): List<DepositHistoryRow> =
        paymentDao.forContract(contractId).map { it.toHistoryRow() }

    /**
     * Entity -> row, with the two columns [EditableDeposit] deliberately does not carry.
     *
     * [method] decodes to the enum rather than passing the column's string on, so the
     * sheet never parses a persistence spelling — the same rule
     * [EditableDeposit]'s class KDoc gives, for the same reason.
     *
     * An unrecognised `method` string is the one case that cannot throw: this row is a
     * *record of what the owner recorded*, and refusing to list a deposit because its
     * method column says something new would hide money the owner took. It falls back
     * to `TransferBank` and the row stays readable — which is the opposite of what a
     * correction form does, because a correction form that guessed would let the owner
     * save a guess over the truth.
     */
    private fun PaymentEntity.toHistoryRow() = DepositHistoryRow(
        id = id,
        contractId = contractId,
        date = date,
        amount = amount,
        method = runCatching { PaymentMethod.valueOf(method) }
            .getOrDefault(PaymentMethod.TransferBank),
        accountId = accountId,
        referenceNo = referenceNo,
        notes = notes,
        isCanceled = status == PaymentEntity.STATUS_CANCELED,
        cancelReason = cancelReason,
    )
}
