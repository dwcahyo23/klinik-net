package net.klinik.rental.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * The `SERVICE_EXCEPTION` table: one day on one contract that is not charged.
 *
 * ## Why `onDelete = ForeignKey.RESTRICT`
 *
 * RESTRICT, not CASCADE and not SET NULL. An ended contract's exception history
 * is *the record of what was not billed* — it is why an invoice for that period is
 * short, and the owner may still be reconciling it against a bank statement months
 * later. CASCADE would delete those rows silently at the moment the owner decided
 * the contract was finished, which is precisely when the record still matters. SET
 * NULL would be worse than both: `contractId` is `NOT NULL` here, and it would have
 * to stop being.
 *
 * So SQLite refuses, and nothing in this story offers a contract delete to trigger
 * it. `ContractRepository` already turns `VehicleRepository`'s identical refusal
 * into a typed exception, and the same shape is what a contract-delete path would
 * use.
 *
 * ## Why the unique index is plain and not partial
 *
 * AR-12's partial-index technique — a nullable copy that is set while active and
 * `NULL` once ended — exists in `ContractEntity` for a reason that does not apply
 * here. That one substitutes for a *conditional* uniqueness ("one open contract per
 * vehicle"). This rule is unconditional: a contract may carry **at most one**
 * exception per date, whether it is running or ended, and `ContractEntity`'s own
 * KDoc records why Room 2.8.5 cannot express a partial index at all
 * (`androidx.room.Index` has no `WHERE` member, and `room-compiler` has no
 * partial-index support). So the index is an ordinary `UNIQUE` over
 * `(contractId, date)` — which is also the whole rule, with nothing to substitute.
 *
 * It is a *schema* constraint rather than a form check for the same reason the
 * plate and renter-name indexes are: two callers can both pass a form validation
 * before either writes, and only the index settles the race.
 *
 * ## Why no column is nullable, and so no `@ColumnInfo(defaultValue)`
 *
 * Every other table in this database carries `@ColumnInfo(defaultValue = "NULL")` on
 * each nullable column, because Room compares *recorded defaults* when it validates
 * a migrated database and a bare `TEXT` is refused wholesale with "Migration
 * didn't properly handle". This table has no nullable column, so there is nothing to
 * declare a default for — and saying so is the point: a nullable `reason` or
 * `date` added later would have to come with the default *and* the `DEFAULT NULL`
 * in `MIGRATION_3_4`, and this comment is where the next reader looks.
 *
 * @property date the absolute day that is not charged, stored as ISO `yyyy-MM-dd`
 *   TEXT by [net.klinik.rental.data.db.Converters]. **Absolute, never an offset or
 *   an index into a period** — that is what makes "an exception does not roll into
 *   the next period" a consequence of `chargeDaysIn`'s arithmetic rather than a rule
 *   somebody has to remember. See `net.klinik.rental.domain.model.ServiceException`.
 * @property reason owner-visible text. Never blank, refused at construction.
 */
@Entity(
    tableName = "service_exception",
    foreignKeys = [
        ForeignKey(
            entity = ContractEntity::class,
            parentColumns = ["id"],
            childColumns = ["contractId"],
            // See the KDoc: an ended contract's exception history is the record of
            // what was not billed, so deleting the contract must not take it.
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // The duplicate rule itself, and it doubles as the foreign key's index —
        // which SQLite needs for the constraint check not to degrade into a table
        // scan, and which Room warns about when it is missing.
        Index(value = ["contractId", "date"], unique = true),
    ],
)
data class ServiceExceptionEntity(
    @PrimaryKey
    val id: String,
    val contractId: String,
    val date: LocalDate,
    /** Owner-visible text. Never blank. */
    val reason: String,
) {
    init {
        // The two keys, plus the one field the owner reads months later. A blank
        // reason is refused here as well as in the domain model, so a raw `@Insert`
        // cannot land the row whose "keterangan" line would then be empty — the
        // same two-layer refusal `ContractEntity` and `VehicleEntity` hold.
        require(id.isNotBlank()) { "id must not be blank" }
        require(contractId.isNotBlank()) { "contractId must not be blank" }
        require(reason.isNotBlank()) { "reason must not be blank" }
    }
}
