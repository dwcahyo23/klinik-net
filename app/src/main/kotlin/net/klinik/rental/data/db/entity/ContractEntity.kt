package net.klinik.rental.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * The minimum `CONTRACT` that makes the vehicle-delete refusal reachable.
 *
 * ## Why this table exists in a story about vehicles
 *
 * Story 2.2's AC asks that deleting a rented vehicle be *refused*. That
 * guarantee has to be enforced by something, and the only thing that knows
 * whether a vehicle is rented is a contract row. Shipping `CONTRACT` as three
 * columns is what makes `DELETE_VEHICLE` a real, asserted behaviour rather than
 * an assertion about a table that does not exist yet.
 *
 * Story 2.5 widens this table with the renter, the frozen daily rate, the work
 * schedule and the settlement date. Nothing here depends on a column it lacks —
 * the columns below are exactly what "does this vehicle have an open contract?"
 * needs to answer.
 *
 * ## Why `onDelete = ForeignKey.RESTRICT`
 *
 * RESTRICT, not CASCADE and not SET NULL. CASCADE would silently delete the
 * contract and every allocation hanging off it, destroying a payment history the
 * owner has already reconciled against a bank statement. SET NULL would leave an
 * allocation pointing at nothing, and arrears is computed from stored allocation
 * rows — so an orphan there is a silently wrong number on another screen.
 *
 * RESTRICT makes SQLite refuse, and `VehicleRepository.delete` turns that refusal
 * into `VehicleHasActiveContractException`. `onUpdate` is CASCADE because a
 * vehicle id never changes; it is stated rather than left to a default so the
 * rule is visible where the relationship is declared.
 *
 * ## Why `endDate` is nullable, and what null means
 *
 * `endDate IS NULL` *is* "active". A contract has no end date until Story 2.7
 * ends it, and a separate boolean `isActive` would be a second source of truth
 * that can disagree with the date it claims to mirror. Status derivation reads
 * this column and nothing else.
 */
@Entity(
    tableName = "contract",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            // See the KDoc: RESTRICT is what makes the refusal real.
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    // Required alongside the foreign key — SQLite needs an index on the child
    // column for the constraint check not to degrade into a table scan, and
    // Room warns when it is missing.
    indices = [Index(value = ["vehicleId"])],
)
data class ContractEntity(
    @PrimaryKey
    val id: String,
    val vehicleId: String,
    val startDate: LocalDate,
    /** `null` means the contract is still running. */
    @ColumnInfo(defaultValue = "NULL")
    val endDate: LocalDate?,
)