package net.klinik.rental.ui.backup

import android.content.Context
import android.net.Uri
import java.io.IOException

/**
 * Reads a backup file the owner chose — [BackupFileWriter]'s mirror.
 *
 * ## Why this returns `String?` and not `Result`
 *
 * Because there is exactly one failure that matters here — the bytes could not be read — and the
 * *reason* the owner needs is not "IOException" but "the restore was refused", which the parser
 * supplies when handed nothing. A `Result` would carry a type the UI immediately flattens into
 * the same message.
 *
 * `null` means "could not read", and the caller turns it into the same refusal a malformed file
 * gets. That is deliberate: from the owner's side, "the file could not be opened" and "the file
 * is not a backup" are the same next action — pick a different file.
 *
 * ## Why the whole file is read into memory
 *
 * Because the largest realistic backup is 2,000 deposits with their allocations — a few hundred
 * kilobytes of JSON. Streaming would be the right answer for a file that could be gigabytes and
 * the wrong answer here: the parser needs the whole document anyway, since a truncated read has
 * to be refused rather than partially applied.
 *
 * ## Why a read failure is not an exception
 *
 * The same reason [BackupFileWriter] catches its own: this runs inside an
 * `ActivityResultLauncher` callback, and an exception crossing that boundary takes the Activity
 * down rather than raising a banner. The owner would see a crash where the app should have said
 * "that file could not be read".
 */
object BackupFileReader {

    /** The whole file as text, or `null` when it could not be read. */
    fun read(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
        // A null stream means the provider refused the read, which `use` returns as null rather
        // than throwing — so the result is checked rather than assumed.
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        // The grant can be revoked between the picker returning and the read.
        null
    }
}