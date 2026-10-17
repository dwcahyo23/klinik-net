package net.klinik.rental.data.db

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * `LocalDate` <-> ISO `yyyy-MM-dd` TEXT.
 *
 * ## Why the converter is required rather than convenient
 *
 * Room 2.8 ships no built-in `java.time` support, so `ContractEntity`'s
 * `LocalDate` columns need this to compile. It is not an optional convenience:
 * without it the entity cannot have a date column at all.
 *
 * ## Why ISO text and not an integer
 *
 * The epic context is explicit — do not "optimize" to integers later. ISO
 * `yyyy-MM-dd` has the property the date columns actually depend on:
 * **lexicographic order equals chronological order**. `ORDER BY startDate` is
 * then correct without `date()`, the column stays indexable as text, and a value
 * read straight out of the database is legible to the person debugging it.
 *
 * A `days-since-epoch` integer has the same ordering property and loses the
 * second one, so it buys nothing and costs legibility.
 *
 * ## Why the parsers are lenient on input and strict on output
 *
 * [toLocalDate] accepts a real [LocalDate] or an ISO string, so a raw query
 * returning a text column does not need its own conversion. `toString()` on a
 * `LocalDate` *is* `DateTimeFormatter.ISO_LOCAL_DATE`, which is the `yyyy-MM-dd`
 * this column is specified to hold — the output side is exact rather than
 * hand-formatted, so there is one definition of the format.
 *
 * Null passes through in both directions: `CONTRACT.endDate` is nullable and
 * `null` is a meaningful value there, not an absent one.
 */
class Converters {

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    /**
     * `DayOfWeek?` <-> `INTEGER`, for `CONTRACT.restDay`.
     *
     * ## Why an integer and not the enum's name
     *
     * [DayOfWeek.getValue] is 1 (Monday) through 7 (Sunday) — ISO-8601's own
     * numbering, which is what `DayOfWeek.of` parses back. Storing the name
     * instead would make the column a `TEXT` that sorts alphabetically
     * (`FRIDAY < MONDAY`), and `ORDER BY restDay` is a plausible thing for a later
     * screen to want. It would also put the enum's *spelling* in the database,
     * where a rename in Kotlin becomes a silent data migration.
     *
     * ## Why the ordinal is deliberately not used
     *
     * `DayOfWeek.ordinal` is 0 (Monday) through 6 (Sunday) and `getValue` is 1
     * through 7, so the two differ by one. `ordinal` is also documented as
     * unstable across reorderings of the enum, while `getValue` is fixed by
     * ISO-8601. Using the ISO value means the stored integer is a standard number
     * rather than a Kotlin implementation detail.
     *
     * Null passes through in both directions: `null` rest day *is* the seven-day
     * week, and it is a meaningful value rather than an absent one — which is why
     * the column is nullable and why the entity records `DEFAULT NULL` for it.
     */
    @TypeConverter
    fun fromDayOfWeek(value: DayOfWeek?): Int? = value?.value

    @TypeConverter
    fun toDayOfWeek(value: Int?): DayOfWeek? = value?.let(DayOfWeek::of)

    /**
     * `Instant` <-> ISO-8601 TEXT, for `PAYMENT.createdAt`.
     *
     * ## Why this exists at all, when the other date columns use [LocalDate]
     *
     * A deposit has two dates and they are different kinds of fact. [LocalDate] is **when the
     * money arrived relative to the rental period** — a billing fact, and it has to sort and
     * compare against other billing days. `createdAt` is **when the owner typed it in** — an
     * audit fact that is never compared against a charge day and never ordered by.
     *
     * Conflating them would mean typing a deposit in late enough to change which period it
     * falls in, which is a real thing the owner does (Story 3.5's correction case). Keeping
     * them apart is what lets a deposit be dated in the past while still recording when it was
     * actually entered.
     *
     * ISO-8601 instant text rather than epoch millis, for the same reason the date columns use
     * ISO rather than integers: a value read straight out of the database is legible, and
     * `Instant.toString()` *is* the standard format — so there is one definition of it and
     * not a hand-formatted second one.
     */
    @TypeConverter
    fun fromInstant(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun toInstant(value: String?): Instant? = value?.let(Instant::parse)
}