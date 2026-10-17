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
 * ## Why there is no `status` column
 *
 * Cancellation is Story 3.4's, and the domain KDoc settles the shape before it arrives:
 * *"AD-3 handles a cancellation by deleting that Setoran's allocation rows and recomputing the
 * whole contract from what is left, so a cancelled payment never reaches an allocator — carrying
 * the flag here would invite callers to filter instead."* A filter somebody forgets to apply is
 * precisely how Rp 13.000.000 disappears at the month rollover.
 *
 * A `cancelReason` therefore does not belong here either: it is 3.4's column, written when that
 * story writes its delete.
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
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(contractId.isNotBlank()) { "contractId must not be blank" }
        // The same bound `Payment.init` and `Vehicle.dailyRate` apply, through the same
        // helper — a zero or negative row would leave the allocator's `Σ allocation ==
        // amount` intact while recording that a day was closed for no money.
        InvalidAmountException.requireValid(amount)
        require(method.isNotBlank()) { "method must not be blank" }
    }
}