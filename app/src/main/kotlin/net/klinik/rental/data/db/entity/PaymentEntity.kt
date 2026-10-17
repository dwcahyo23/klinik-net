package net.klinik.rental.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import net.klinik.rental.domain.validation.InvalidAmountException
import java.time.Instant
import java.time.LocalDate

/**
 * One deposit the tenant made, as stored.
 *
 * ## What the domain's `Payment` deliberately does not carry
 *
 * `net.klinik.rental.domain.model.Payment` holds id, date and amount — the three fields the
 * allocator reads. Everything here beyond those three exists for the *owner*, not for the
 * arithmetic: which bank it arrived at, what the bank reference was, what the tenant said about
 * it, and when it was typed in. That split is the reason this is a separate entity rather than
 * the domain model with extra fields, and it is why `method` here is a string while
 * `ChargeSchedule.restDay` is a converted enum.
 *
 * ## Status and cancellation
 *
 * Story 3.4 added [status] and [cancelReason]. A cancelled payment keeps its row for audit
 * purposes ("tidak ada hard delete") while its allocations are deleted and the contract is
 * reallocated from surviving deposits. See the [status] property KDoc for the rationale.
 *
 * ## Why `accountId` is nullable, and why that is not a shortcut
 *
 * `null` means **Tunai** — cash handed over in person. There is no external record to point at,
 * and the epic context is explicit that this is a real state rather than a missing value: a cash
 * deposit is unverifiable by design, and the notes field is the only breadcrumb it has.
 *
 * So the column is `DEFAULT NULL` in the migration SQL and `@ColumnInfo(defaultValue = "NULL")`
 * here. Room compares recorded column defaults against the compiled schema and refuses the whole
 * migration when they disagree, which is why the two must be written in both places.
 */
@Entity(
    tableName = "payment",
    foreignKeys = [
        ForeignKey(
            entity = ContractEntity::class,
            parentColumns = ["id"],
            childColumns = ["contractId"],
            // RESTRICT, for the same reason `service_exception` uses it: a deposit is
            // money the owner took. Deleting the contract must not silently delete the
            // record that it happened.
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BusinessAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            // Also RESTRICT, and this one is the **delete refusal** Story 3.1 deferred: a
            // bank account with deposits on it cannot be deleted, and this foreign key is
            // what enforces it once the referencing rows exist. `SET NULL` would be the
            // wrong choice — it would silently rewrite every deposit that arrived through
            // that account into an unverifiable cash payment.
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // Both foreign keys need their own index, or SQLite degrades the constraint check
        // into a table scan on every write.
        Index(value = ["contractId"]),
        Index(value = ["accountId"]),
    ],
)
data class PaymentEntity(
    @PrimaryKey
    val id: String,
    val contractId: String,
    /** `null` for a cash deposit — see the class KDoc. */
    @ColumnInfo(defaultValue = "NULL")
    val accountId: String?,
    /** When the money arrived, as a billing fact. ISO `yyyy-MM-dd`. */
    val date: LocalDate,
    /** Rupiah received. Always greater than zero. */
    val amount: Long,
    /** `TransferBank` or `Tunai`, stored as the enum's own name. */
    val method: String,
    /**
     * The bank's own reference, typed freely from a mutation screen.
     *
     * Nullable and **unvalidated**: the epic context makes it a reminder for the owner, not
     * something checked against the bank. Anything stricter would refuse the references real
     * banks issue.
     */
    @ColumnInfo(defaultValue = "NULL")
    val referenceNo: String?,
    /** The owner's own note. The only breadcrumb a cash deposit has. */
    @ColumnInfo(defaultValue = "NULL")
    val notes: String?,
    /**
     * When the owner typed it in, as an audit fact.
     *
     * Deliberately distinct from [date]: a deposit may be dated in the past (correction, which
     * the owner does routinely) while still recording when it was actually entered. Conflating
     * them would let a late entry change which billing period the money falls in.
     */
    val createdAt: Instant,
    /**
     * `confirmed` or `canceled` — Story 3.4's addition.
     *
     * ## Why this exists despite `Payment`'s KDoc saying it should not
     *
     * The domain KDoc argues that a status "would invite callers to filter instead, and a
     * filter somebody forgets to apply is precisely how Rp 13.000.000 disappears at the
     * month rollover." That reasoning is kept, and this column is the answer rather than
     * the objection to it:
     *
     *  - **nothing filters on it.** `PaymentDao.forContract` and `observeForContract` keep
     *    returning every row, cancelled ones included — that is what keeps the audit trail
     *    readable. The *allocator* excludes a cancelled deposit by construction, because
     *    cancelling deletes its allocation rows and re-allocates from survivors.
     *  - **so forgetting is now loud.** A caller that wrongly filters pays nothing; a
     *    caller that wrongly *includes* a cancelled deposit would double-count against a
     *    contract whose rows were already rewritten, and `Σ allocation == amount` fails.
     *
     * The AC requires the row to survive ("tidak ada hard delete"), and a row that can
     * neither be deleted nor marked is a row nobody can audit.
     */
    @ColumnInfo(defaultValue = "confirmed")
    val status: String = STATUS_CONFIRMED,
    /**
     * Why it was cancelled — **mandatory when [status] is `canceled`**, which is the AC's
     * *"Pembatalan wajib menyertakan alasan."*
     *
     * Nullable rather than non-null because a confirmed deposit has no reason, and
     * `DEFAULT ''` would make "cancelled with no reason" representable.
     */
    @ColumnInfo(defaultValue = "NULL")
    val cancelReason: String? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(contractId.isNotBlank()) { "contractId must not be blank" }
        // The same bound `Payment.init` and `Vehicle.dailyRate` apply, through the same
        // helper — a zero or negative row would leave the allocator's `Σ allocation ==
        // amount` intact while recording that a day was closed for no money.
        InvalidAmountException.requireValid(amount)
        require(method.isNotBlank()) { "method must not be blank" }
        require(status.isNotBlank()) { "status must not be blank" }
        // The AC's rule, enforced where the row is built rather than only at the call
        // site: a cancelled deposit with no reason is an audit trail with a gap in it,
        // and the whole reason the row survives is so the gap is readable.
        if (status == STATUS_CANCELED) {
            require(!cancelReason.isNullOrBlank()) {
                "a cancelled deposit must carry a reason"
            }
        }
    }

    companion object {
        /** The deposit counts towards arrears. */
        const val STATUS_CONFIRMED = "confirmed"

        /** The deposit is kept for its trail and no longer counts. */
        const val STATUS_CANCELED = "canceled"
    }
}