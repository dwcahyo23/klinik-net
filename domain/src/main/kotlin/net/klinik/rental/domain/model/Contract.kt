package net.klinik.rental.domain.model

import net.klinik.rental.domain.parse.requirePositiveRate
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One rental: a person, a car, the day it started, and the price it was agreed at.
 *
 * Deliberately not the `CONTRACT` Room entity — AD-1 keeps `domain/` free of Room,
 * and the entity in `data/db/entity/` maps to this.
 *
 * ## Why the rate is copied in rather than read from the vehicle
 *
 * [dailyRate] is the price agreed at open time, frozen onto the contract. Reading
 * the vehicle's rate at billing time instead would silently reprice every running
 * contract the moment the owner raised a price on the fleet screen — a change the
 * owner made about *future* rentals, applied to a rental already agreed. The epic
 * context states the rule outright: "Contract daily rate is copied from the vehicle
 * at open time and stays constant, so a later vehicle rate change cannot silently
 * reprice a running contract; negotiated rates are allowed."
 *
 * So the vehicle is only a *default* for the form. Once the contract exists, the
 * two numbers are independent and this one wins.
 *
 * ## Why `endDate IS NULL` is what "open" means
 *
 * There is no `status` field, and its absence is the point. [Vehicle] and [Renter]
 * both derive their rented-ness from the existence of an open contract, and
 * `VehicleDao.hasOpenContract` asks `endDate IS NULL` directly. A stored status
 * here would be a second source of truth for the same fact, and the two would
 * disagree the first time a contract ended without the flag being written —
 * exactly the drift the prototype's `status` column exhibited.
 *
 * [isOpen] is therefore a *derivation* over [endDate], not a stored flag.
 *
 * Read **at the entry point** of every write, not by the database: a contract that was
 * ended at some point and then offered back to `ContractRepository.open()` would insert a
 * row with both active columns NULL and slip past the two `UNIQUE` indexes, which the
 * indexes are too coarse to detect. `ContractRepository.open` enforces this with a
 * `require(isOpen)` that fires before the write, in the same shape as `requirePositiveRate`.
 * The constructor's own checks (`!endDate.isBefore(startDate)`) are arithmetic, not policy;
 * the `isOpen` rule is policy and lives at the method that owns the consequence.
 *
 * ## Why the schedule is a single nullable [restDay]
 *
 * `null` is a seven-day week; a `DayOfWeek` is a six-day week resting on that day.
 * The epic context's data model describes a `workSchedule` string plus an `offDay`
 * integer, and that is two encodings of one fact: `"7days"` with `offDay = 3` has
 * no defined meaning, and the two can disagree. `ChargeSchedule.restDay` is the
 * vocabulary the calculation layer already reads, and
 * [net.klinik.rental.domain.calculate.chargeDaysIn] filters on `it.dayOfWeek !=
 * schedule.restDay` — so the column stores that field and nothing else.
 *
 * ## What this model deliberately does not carry
 *
 * No service exceptions (Story 2.6 owns the table and its editing), and no
 * arrears, overdue-day count, risk tier or credit — those are allocation-derived
 * and are computed behind the calculation engine, not stored per contract.
 *
 * [id], [renterId] and [vehicleId] are `String` rather than `UUID` because AD-1
 * restricts `domain/` to `kotlin.*`, `java.time.*` and `kotlinx.coroutines.*`, so
 * `java.util.UUID` is not importable and the ids arrive as strings from the layer
 * that owns persistence. The database keeps them `uuid` columns; nothing here
 * parses them.
 *
 * @param startDate the day the rental began. A date in the *past* is accepted:
 *   contracts that began before this app existed are real, and refusing them would
 *   make the owner's own history unrecordable.
 * @param dailyRate whole rupiah per day, greater than zero (NFR-6, AD-2). The
 *   same bound `Payment`, `Allocation` and [Vehicle] use, through the same helper.
 * @param restDay the one day off per week, or `null` for a seven-day contract.
 *   See the class KDoc for why this is one field and not two.
 * @param endDate `null` while the contract is running. Story 2.7 is what sets it;
 *   nothing in this story ends a contract.
 * @param settledThroughDate `null` when nothing has been settled. See the property's
 *   own KDoc for why it is separate from [endDate].
 */
data class Contract(
    /** Stable identity. */
    val id: String,
    /** The person renting. The `renterId` half of `CONTRACT`'s foreign key. */
    val renterId: String,
    /** The car being rented. The `vehicleId` half of the same foreign key. */
    val vehicleId: String,
    val startDate: LocalDate,
    /** Rupiah per day, frozen at open time. Always greater than zero (NFR-6). */
    val dailyRate: Long,
    /** The one day off per week, or `null` for a seven-day week. */
    val restDay: DayOfWeek? = null,
    /** `null` while the contract is still running. */
    val endDate: LocalDate? = null,
    /**
     * FR-9b: charge days at or before this are already paid, `null` for none —
     * Story 3.6.
     *
     * ## Why Epic 3's deferral ends here
     *
     * This model deliberately carried no settlement date while nothing wrote one. Now
     * something does, and the arithmetic needs it: `PaymentRepository.previewAllocation`
     * narrows a contract's charge-day list through
     * [net.klinik.rental.domain.calculate.settledDays] before anything counts it, and a
     * calculation that cannot see the marker cannot honour it. `ChargeSchedule` does **not**
     * read this field — the narrowing happens one layer out, in the repository that owns
     * the read chain. So it moves from "Epic 3 owns it" to a field here.
     *
     * It is **not** folded into [endDate]. They answer different questions — "when
     * did the car come back" and "when were the days paid elsewhere" — and a contract
     * can carry both: one ends mid-period, and the days before that were settled
     * offline. Merging them would make the second action overwrite the first.
     */
    val settledThroughDate: LocalDate? = null,
) {
    init {
        // All three ids are primary keys or foreign keys. A blank one is not a
        // contract pointing at nothing in particular — it is a contract whose
        // foreign key will be refused at the database, or worse, one that joins to
        // whatever else happens to be blank. Refused here so the caller hears
        // about the id rather than about a constraint three layers down.
        require(id.isNotBlank()) { "id must not be blank" }
        require(renterId.isNotBlank()) { "renterId must not be blank" }
        require(vehicleId.isNotBlank()) { "vehicleId must not be blank" }

        // Mirrors `Payment`, `Allocation` and `Vehicle`: money is checked where it
        // enters the domain, never defaulted. The prototype's
        // `dailyRate: parseInt(x) || 250000` is the opposite of this rule, and a
        // contract is where an invented price would be most expensive.
        requirePositiveRate(dailyRate)

        // An end before the start is not a short rental, it is a date typed into
        // the wrong field. Refused rather than normalised: swapping the two would
        // be the app inventing a rental period nobody agreed to, and the billing
        // engine would then charge for days between the two dates.
        //
        // An end *equal* to the start is allowed — a one-day rental is real, and
        // `chargeDaysIn` bills the single day.
        if (endDate != null) {
            require(!endDate.isBefore(startDate)) {
                "endDate $endDate must not be before startDate $startDate"
            }
        }
    }

    /**
     * Whether this contract is still running.
     *
     * A derivation over [endDate], never a stored flag — see the class KDoc. Every
     * screen that asks "is this car rented?" answers it from here or from the
     * matching `endDate IS NULL` query, so there is one rule and not two.
     */
    val isOpen: Boolean get() = endDate == null
}
