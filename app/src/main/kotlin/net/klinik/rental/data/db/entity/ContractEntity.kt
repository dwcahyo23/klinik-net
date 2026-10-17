package net.klinik.rental.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import net.klinik.rental.domain.validation.InvalidAmountException
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The `CONTRACT` table: one rental, its frozen price and its schedule.
 *
 * ## Why `onDelete = ForeignKey.RESTRICT` on both parents
 *
 * RESTRICT, not CASCADE and not SET NULL. CASCADE would silently delete the
 * contract and every allocation hanging off it, destroying a payment history the
 * owner has already reconciled against a bank statement. SET NULL would leave an
 * allocation pointing at nothing, and arrears is computed from stored allocation
 * rows — so an orphan there is a silently wrong number on another screen.
 *
 * RESTRICT makes SQLite refuse, and `VehicleRepository.delete` turns that refusal
 * into `VehicleHasActiveContractException`. `onUpdate` is CASCADE because neither
 * id ever changes; it is stated rather than left to a default so the rule is
 * visible where the relationship is declared.
 *
 * ## Why `endDate` is nullable, and what null means
 *
 * `endDate IS NULL` *is* "open". A contract has no end date until Story 2.7 ends
 * it, and a separate boolean `isActive` would be a second source of truth that can
 * disagree with the date it claims to mirror. Status derivation reads this column
 * and nothing else — `VehicleDao.hasOpenContract` asks exactly this question.
 *
 * ## Why the one-active rule is two `UNIQUE` columns and not a partial index
 *
 * AR-12 asks for "a partial unique index for an active contract", and Room 2.8.5
 * cannot express one: `androidx.room.Index` declares only `value`, `orders`, `name`
 * and `unique` — there is no `WHERE` member — and `room-compiler` contains no
 * partial-index support at all. Hand-writing the index in `MIGRATION_2_3` is not a
 * way out either: `SchemaInfoUtilKt.readIndices` reads `PRAGMA index_list`, filters
 * on `origin = 'c'`, and never consults the `partial` column, so a hand-made
 * partial index enters the found set; `TableInfo.equalsCommon` compares the index
 * `Set` for equality; and the generated `onValidateSchema` would therefore refuse
 * the database with `Migration didn't properly handle: contract`.
 *
 * So the substitute keeps AR-12's *intent* — a real schema constraint that two
 * screens cannot bypass — using Room's own vocabulary. [activeVehicleId] and
 * [activeRenterId] are nullable copies of the ids, set while the contract is open
 * and `NULL` once it ends, each carrying a plain `UNIQUE` index. SQLite treats
 * `NULL`s as distinct in a unique index, so unlimited ended contracts are allowed
 * and at most one open contract per vehicle or per renter is. That is exactly
 * partial-index semantics.
 *
 * The obvious objection — a copy can drift from [endDate] — is answered by a
 * construction-time guard rather than by discipline: the `init` block below refuses
 * any row where the active columns disagree with [endDate], the same guard
 * [VehicleEntity] and [RenterEntity] already use so a raw `@Insert` cannot land a bad
 * row.
 *
 * To be exact about what that buys: the guard is Kotlin, not SQL — there is no
 * `CHECK` constraint on this table — so it holds for every write that builds a
 * [ContractEntity], which is every write this app has, but it is not a
 * database-level invariant. A row inserted by hand-written SQL outside Room could
 * still disagree. The column pair plus the guard is what makes the app's own writes
 * safe; a schema-level `CHECK` would be the stronger form and is not what ships.
 * One write path derives the pair, and no row this app can construct disagrees.
 *
 * ## Why every nullable column carries `@ColumnInfo(defaultValue = "NULL")`
 *
 * Room compares *recorded defaults* when it validates a migrated database, so a
 * column declared `@ColumnInfo(defaultValue = "NULL")` and created as a bare `TEXT`
 * by hand-written migration SQL is refused wholesale with "Migration didn't
 * properly handle: contract". That is exactly the defect `MIGRATION_1_2` shipped
 * with and had to be fixed for; `MIGRATION_2_3` writes `DEFAULT NULL` on every one
 * of these columns for the same reason.
 *
 * ## Why `dailyRate` is `Long` and not `Int`
 *
 * Rupiah end to end in whole units (NFR-6), matching [VehicleEntity] and the domain
 * model. An `Int` overflows silently above ~2.1 billion, which a long rental's
 * accumulated figure passes.
 *
 * @property restDay the one day off per week, or `null` for a seven-day week. One
 *   column, not the epic context's `workSchedule` string plus `offDay` integer:
 *   two encodings of one fact can disagree, and `ChargeSchedule.restDay` is the
 *   vocabulary the calculation layer already reads.
 * @property settledThroughDate FR-9b's marker, added by Story 3.6. `null` on every
 *   row written before it and on every contract nobody has settled.
 */
@Entity(
    tableName = "contract",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            // See the KDoc: RESTRICT is what makes the delete refusal real.
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RenterEntity::class,
            parentColumns = ["id"],
            childColumns = ["renterId"],
            // The same shape as the vehicle side, and for the same reason: a
            // renter's contract history is the record of money they paid, so
            // removing the person must not take it with them.
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    // Required alongside each foreign key — SQLite needs an index on the child
    // column for the constraint check not to degrade into a table scan, and Room
    // warns when it is missing.
    //
    // The two unique indexes are the one-active rule itself. They are plain
    // `UNIQUE` indexes over nullable columns rather than partial indexes because
    // Room cannot express a partial index — see the class KDoc.
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["renterId"]),
        Index(value = ["activeVehicleId"], unique = true),
        Index(value = ["activeRenterId"], unique = true),
    ],
)
data class ContractEntity(
    @PrimaryKey
    val id: String,
    val renterId: String,
    val vehicleId: String,
    val startDate: LocalDate,
    /** Rupiah per day, frozen at open time. Always greater than zero (NFR-6). */
    val dailyRate: Long,
    /** The one day off per week; `null` is a seven-day week. */
    @ColumnInfo(defaultValue = "NULL")
    val restDay: DayOfWeek?,
    /**
     * A copy of [vehicleId] while this contract is open, `NULL` once it ends.
     *
     * `UNIQUE`, so at most one open contract per vehicle. SQLite treats `NULL`s as
     * distinct, so any number of ended contracts may carry `NULL` here.
     */
    @ColumnInfo(defaultValue = "NULL")
    val activeVehicleId: String?,
    /** A copy of [renterId] while open, `NULL` once it ends. `UNIQUE`. */
    @ColumnInfo(defaultValue = "NULL")
    val activeRenterId: String?,
    /** `null` means the contract is still running. */
    @ColumnInfo(defaultValue = "NULL")
    val endDate: LocalDate? = null,
    /**
     * FR-9b, Story 3.6: charge days at or before this are treated as **already paid**,
     * `null` meaning no settlement.
     *
     * ## Why this is a field on the contract and not a stored payment
     *
     * The PRD is explicit — *"Tidak ada Setoran yang dibuat, tidak ada alokasi yang
     * disimpan, dan tidak ada jejak audit selain waktu aksi itu dilakukan."* A contract
     * that ran before this app was first opened owes nothing the owner recognises as
     * arrears; they were settled elsewhere and will never be entered here. So the
     * honest representation is a marker the calculator narrows the day list by, not a
     * fake Setoran that would make an unrecoverable fiction look like money that moved.
     *
     * It is deliberately **outside** the [endDate]/active-column guard below. That
     * guard exists so a copy cannot drift from the date it mirrors; this field mirrors
     * nothing, so joining it to the pair would only give the one-active rule a second
     * thing to police.
     *
     * ## Why there is no "un-settle" column or a settled-at timestamp
     *
     * Also the PRD: *"tidak bisa di-undo."* Correction is editing this one column, which
     * is an ordinary contract write rather than a second mechanism — a separate undo
     * path would be exactly the second thing that can disagree with the first.
     */
    @ColumnInfo(defaultValue = "NULL")
    val settledThroughDate: LocalDate? = null,
) {
    init {
        // The unique indexes are only as good as the values they compare, and an
        // `activeVehicleId` that disagreed with `endDate` would either free a slot
        // that is still occupied or occupy one that is not. The `UNIQUE` index
        // cannot detect that: it compares the active column against other rows,
        // never against this row's own end date.
        //
        // So the two active columns are pinned to `endDate` here, which closes it
        // for every row this app constructs the same way `VehicleEntity` closes the
        // un-normalised-plate case — by refusing it at construction rather than by
        // trusting every caller to remember. It is a Kotlin guard and not a SQL
        // `CHECK`, so a hand-written INSERT outside Room is outside its reach; see
        // the class KDoc.
        if (endDate == null) {
            require(activeVehicleId == vehicleId) {
                "an open contract must carry activeVehicleId == vehicleId, " +
                    "but was '$activeVehicleId' against '$vehicleId'"
            }
            require(activeRenterId == renterId) {
                "an open contract must carry activeRenterId == renterId, " +
                    "but was '$activeRenterId' against '$renterId'"
            }
        } else {
            require(activeVehicleId == null) {
                "an ended contract must leave activeVehicleId NULL, " +
                    "but was '$activeVehicleId'"
            }
            require(activeRenterId == null) {
                "an ended contract must leave activeRenterId NULL, " +
                    "but was '$activeRenterId'"
            }
        }

        // Money is rejected at the entity as well as in the domain, so a raw
        // `@Insert` cannot land a zero rate either. Same line `VehicleEntity`
        // holds for the vehicle rate.
        InvalidAmountException.requireValid(dailyRate)

        // The two foreign keys and the primary key. A blank id is a key that
        // collides with every other blank id, and the repository's constraint
        // translation would then report it as a *contract already active* refusal
        // naming a car nobody selected.
        require(id.isNotBlank()) { "id must not be blank" }
        require(renterId.isNotBlank()) { "renterId must not be blank" }
        require(vehicleId.isNotBlank()) { "vehicleId must not be blank" }

        // An end before the start is a date typed into the wrong field, not a
        // short rental. The domain model refuses it too; this is the half that
        // holds for a caller that never built a `Contract`.
        if (endDate != null) {
            require(!endDate.isBefore(startDate)) {
                "endDate $endDate must not be before startDate $startDate"
            }
        }
    }
}
