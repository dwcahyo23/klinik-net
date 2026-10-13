package net.klinik.rental.domain.model

import net.klinik.rental.domain.validation.InvalidAmountException
import java.time.LocalDate

/**
 * One deposit the tenant made: a fact with a date and an amount.
 *
 * Deliberately not the `PAYMENT` Room entity — AD-1 keeps `domain/` free of Room, and the
 * entity in `data/` maps to this. Only the three columns arithmetic reads are here;
 * `method`, `referenceNo`, `notes`, `status` and `createdAt` belong to the tenant-facing
 * screen and to Story 3.1, not to a number.
 *
 * [id] is a `String` rather than a `UUID` because AD-1 restricts `domain/` to `kotlin.*`,
 * `java.time.*` and `kotlinx.coroutines.*` — `java.util.UUID` is not importable, so the id
 * arrives as a string from the layer that owns persistence. The database keeps it a `uuid`
 * column; nothing in `domain/` parses it.
 *
 * There is deliberately no `status` or `canceledAt`. AD-3 handles a cancellation by
 * deleting that Setoran's allocation rows and recomputing the whole contract from what is
 * left, so a cancelled payment never reaches an allocator — carrying the flag here would
 * invite callers to filter instead, and a filter somebody forgets to apply is precisely how
 * Rp 13.000.000 disappears at the month rollover.
 */
data class Payment(
    /** Stable identity, also the `paymentId` half of the `ALLOCATION` unique key. */
    val id: String,
    /** The day the money arrived — not the day it closes; that is [Allocation.chargeDate]. */
    val date: LocalDate,
    /** Rupiah received. Always greater than zero. */
    val amount: Long,
) {
    init {
        // Checked on construction so an invalid Setoran cannot enter the queue at all: a
        // Payment is the only source of allocation rows, so an unchecked amount here would
        // otherwise reach the database as a row that silently breaks AD-3's
        // `Σ allocation == amount` rule.
        InvalidAmountException.requireValid(amount)
    }
}