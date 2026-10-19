package net.klinik.rental.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * `LocalDate` ↔ Material3 `DatePicker`'s epoch millis — the one UTC bridge in this app.
 *
 * ## Why this is worth a file
 *
 * Because the conversion is a **trap**, not boilerplate. `DatePicker` reports
 * `selectedDateMillis` as *UTC midnight* of the picked calendar day, so a `LocalDate` written
 * through the device zone and read back through the device zone is a day early for anyone west of
 * Greenwich and a day late for anyone east of it. Both directions must use `UTC`, and both must be
 * the *same* choice — a write in UTC read back in the local zone is the classic off-by-one, and it
 * looks correct twice a day.
 *
 * ## Why the three pickers still have their own fields
 *
 * The **field** around this differs per screen — a different label, hint, error, test tag, and
 * (in the deposit sheet) a date that cannot be null. Copying the field would copy that difference,
 * which is legitimate. Copying *this* would copy the trap, which is not: three copies of a
 * two-line conversion is three places to fix the day the picker's contract changes, and the
 * KDoc in `DepositSheet` already had to name the other two to keep them in step.
 *
 * Exposed from `ui.format` rather than a `ui.components` file because it has no Compose in it —
 * it is pure `java.time` arithmetic, and the package already owns "the one place a value becomes
 * the shape a widget wants" (see [formatRupiahDigits]).
 */
fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * The read-back — `DatePicker.selectedDateMillis` to the calendar day it names.
 *
 * The inverse of [toPickerMillis], and deliberately in the same zone. See that function on why a
 * local-zone read is the off-by-one.
 *
 * @return the day, or `null` when the picker reports no selection — an untouched picker on a
 *   form whose date is optional.
 */
fun pickerMillisToDate(millis: Long?): LocalDate? =
    millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
