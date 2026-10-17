package net.klinik.rental.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import net.klinik.rental.domain.validation.InvalidAmountException
import java.time.LocalDate

/**
 * One row of the `ALLOCATION` table: "this much of that deposit closed this charge day".
 *
 * ## Why this table exists at all
 *
 * AD-3: arrears is `Kewajiban − Σ alokasi` over the period's charge days, read straight off
 * these rows. Nothing re-derives it by replaying payments against a window. That is what makes
 * the Rp 13.000.000 leftover survive the month rollover — credit is the [chargeDate] `null` row
 * itself, not a separate number somebody has to remember to carry forward.
 *
 * The table is therefore not an optimisation. Without it, credit is a figure that is computed
 * again every period and can differ from last month's.
 *
 * ## Why the unique index is a **correctness** constraint, not an index
 *
 * `UNIQUE(paymentId, chargeDate)` stops one deposit writing two rows for the same day. It is the
 * storage half of a guarantee the allocator already makes: `allocatePayments` visits each day
 * once and hands it to at most one row per payment, so a duplicate is impossible *provided*
 * the days are distinct and ascending — and that precondition is asserted at the top of the
 * function rather than trusted from every caller.
 *
 * If this index were dropped, the allocator's own precondition would still hold and every test
 * would still pass; the failure would appear only when two writes raced, as two rows summing to
 * more than the deposit paid. So it is asserted here and, in the migration, under Room's
 * derived name.
 *
 * ## Why [chargeDate] is nullable
 *
 * `null` is the credit row: the money is in the pool but not yet placed on a day. It is the one
 * value in this table that is not a billing fact, and it is why `AD-3`'s "credit survives
 * period rollover" is true rather than hoped for.
 *
 * ## Why `contractId` is stored here when `paymentId` already implies it
 *
 * The domain's `Allocation` deliberately carries no `contractId`, on the argument that the
 * allocator replays one contract at a time and the caller already holds the id. That is right for
 * the *domain* value and wrong for the *table*: arrears is read per contract with a single
 * aggregate query, and joining back to `payment` for every row would make the number that the
 * whole app is built around a two-hop query. This is one of the cases where the stored shape
 * earns a field the calculation does not need.
 */
@Entity(
    tableName = "allocation",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            // RESTRICT: cancelling a deposit deletes its allocations (Story 3.4), and that
            // delete must not be able to take the payment with it by accident.
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // The duplicate-day guard — see the class KDoc. `chargeDate`, not the domain
        // value's `chargeDate`/`date` split: this is the *column*, and the index
        // references columns.
        Index(value = ["paymentId", "chargeDate"], unique = true),
        // The arrears query: "every open day for this contract". Without it the figure the
        // whole app is built around is a scan.
        Index(value = ["contractId", "chargeDate"]),
    ],
)
data class AllocationEntity(
    @PrimaryKey
    val id: String,
    val paymentId: String,
    val contractId: String,
    /** The charge day this money closed, or `null` when it is not placed on one yet. */
    @ColumnInfo(defaultValue = "NULL")
    val chargeDate: LocalDate?,
    /** Rupiah applied. Always greater than zero. */
    val amount: Long,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(paymentId.isNotBlank()) { "paymentId must not be blank" }
        require(contractId.isNotBlank()) { "contractId must not be blank" }
        // A zero row would leave `Σ allocation == amount` intact while recording that a day
        // was closed for no money — and arrears would then be wrong in a way no sum catches.
        InvalidAmountException.requireValid(amount)
    }
}