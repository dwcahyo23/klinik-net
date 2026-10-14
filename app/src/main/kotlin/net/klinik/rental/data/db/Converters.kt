package net.klinik.rental.data.db

import androidx.room.TypeConverter
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
}