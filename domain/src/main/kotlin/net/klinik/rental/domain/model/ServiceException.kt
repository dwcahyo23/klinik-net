package net.klinik.rental.domain.model

import java.time.LocalDate

/**
 * One day on one contract that is **not** charged.
 *
 * Deliberately not the `SERVICE_EXCEPTION` Room entity — AD-1 keeps `domain/` free
 * of Room, and the entity in `data/db/entity/` maps to this.
 *
 * ## Why [date] is an absolute day and not an offset into a period
 *
 * The tempting encoding is an index or an offset — "skip day 14 of this month's
 * period" — and it is wrong in a way no test would catch until a month rolled: the
 * stored value would silently re-apply to whichever day happened to land at that
 * index next, marking a *different* day than the owner chose and taking a day the
 * renter *should* have paid off the bill.
 *
 * `ChargeSchedule.serviceExceptions` is `Set<LocalDate>` and `chargeDaysIn` filters
 * `it !in schedule.serviceExceptions` over the period's days, so a date in March
 * simply never matches in April. Storing the absolute [LocalDate] makes "an
 * exception does not roll into the next period" a *consequence* of the arithmetic
 * rather than a rule somebody has to remember — and it is what
 * `ServiceExceptionBillingTest` holds in place.
 *
 * ## Why [reason] is free text and not an enum
 *
 * The acceptance criterion requires the exception to be displayed *with its
 * reason* (`epics.md:822`, `dengan keterangan alasannya`), and the thing that was
 * not billed on a given day has no closed vocabulary: a flat tyre, a workshop
 * queue, a car lent to the owner's family. An enum would make the common reasons
 * selectable and make the next one need a schema change.
 *
 * So the reason is owner-visible text, and it is **required** rather than
 * optional: an exception stored with a blank reason produces a list row whose
 * "keterangan" line is empty, which is indistinguishable from a row whose reason
 * was lost. Nothing in this story edits a stored reason, so a blank note would be
 * permanent.
 *
 * [id] and [contractId] are `String` rather than `UUID` because AD-1 restricts
 * `domain/` to `kotlin.*`, `java.time.*` and `kotlinx.coroutines.*`, so
 * `java.util.UUID` is not importable and the ids arrive as strings from the layer
 * that owns persistence. The database keeps them `uuid` columns.
 *
 * ## Why this carries no contract reference and no status
 *
 * [contractId] is the whole link: exceptions are **per Contract, never per
 * Vehicle**, because one vehicle can have several contracts with different
 * exceptions. And "is this contract still open?" is asked through `Contract.isOpen`
 * rather than copied here — a stored copy of the same fact is a second thing to
 * keep in step.
 *
 * @param date the day that is not charged, absolute.
 * @param reason why, in the owner's own words. Never blank.
 */
data class ServiceException(
    /** Stable identity. */
    val id: String,
    /** The `contractId` half of `SERVICE_EXCEPTION`'s foreign key. */
    val contractId: String,
    val date: LocalDate,
    /** Owner-visible text. Never blank — see the class KDoc. */
    val reason: String,
) {
    init {
        // The two ids are a primary key and a foreign key, and the reason is the
        // only thing the owner will read about this row months later. All three are
        // refused here so the caller hears about the field rather than about a
        // constraint three layers down — the same rule `Contract` and `Vehicle`
        // hold.
        require(id.isNotBlank()) { "id must not be blank" }
        require(contractId.isNotBlank()) { "contractId must not be blank" }
        require(reason.isNotBlank()) {
            "reason must not be blank: an exception row with no reason displays an " +
                "empty keterangan, which is indistinguishable from a lost one"
        }
    }
}
