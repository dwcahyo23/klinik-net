package net.klinik.rental.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One bank account the business receives transfers into.
 *
 * ## Why this is its own table and not a row in `settings`
 *
 * The epics file says accounts live in the `settings` table alongside other configuration,
 * and the architecture's ERD defines this as a separate entity with a relation to
 * `PAYMENT`. The ERD wins, because the acceptance criteria are **unimplementable** without
 * row identity: "an account in use cannot be deleted" needs a foreign key for a deposit row
 * to point at, and "editing the number must not change deposits already recorded" needs a
 * *reference* rather than a copy. A `settings(key, value)` pair has no key a `PAYMENT` row
 * could reference, and the architecture's own note warns that `settings.value` is a
 * `String` which must not carry structured data. See `spec-3-1`'s Design Notes for the full
 * reading.
 *
 * ## Why there is no unique index on either column
 *
 * The tempting index is `UNIQUE(number)`, by analogy with `vehicle.plate`. It would be
 * wrong twice over: two accounts at one bank are legitimate (a business account and a
 * payroll account), and the same number held at two banks is the owner's business rather
 * than a data error. The acceptance criterion asks for a *list* the owner adds to, and
 * nothing in it asks for deduplication — so this table has **no indices at all**, and
 * `DatabaseMigrationTest`'s `indexesOf` assertion is what stops a later reader adding one
 * "for consistency with vehicle".
 *
 * ## Why no column is nullable
 *
 * `bankName` and `number` are both required, so the table has no nullable column — and
 * that is why no `@ColumnInfo(defaultValue = "NULL")` appears below. The rule it avoids:
 * Room compares recorded column defaults against the compiled schema and refuses the whole
 * migration when they disagree, so a nullable column here would force a matching
 * `DEFAULT NULL` in `MIGRATION_4_5`. `ServiceExceptionEntity` is in the same position and
 * documents the same trap.
 *
 * @property bankName the bank, in the owner's own words. Free text, not an enum: a closed
 *   list would need a schema change for the next bank, and the owner's spelling is data
 *   they recognise. Never blank.
 * @property number the account number, already normalised by
 *   `net.klinik.rental.domain.parse.normalizeAccountNumber`, so the same account written
 *   three ways is one value. Format-validated only — never checked against a bank.
 */
@Entity(tableName = "business_account")
data class BusinessAccountEntity(
    @PrimaryKey
    val id: String,
    val bankName: String,
    val number: String,
) {
    init {
        // Refused here as well as in the domain model, so a raw `@Insert` cannot land a
        // row the owner cannot identify in their own list — the same two-layer refusal
        // `ContractEntity`, `VehicleEntity` and `ServiceExceptionEntity` hold.
        require(id.isNotBlank()) { "id must not be blank" }
        require(bankName.isNotBlank()) { "bankName must not be blank" }
        require(number.isNotBlank()) { "number must not be blank" }
    }
}
