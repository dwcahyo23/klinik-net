package net.klinik.rental.ui.backup

import android.content.Context
import android.net.Uri
import java.io.IOException

/**
 * Writes a backup's text to the file the owner chose.
 *
 * ## Why this is in `ui/` and not `data/`
 *
 * Because `android.net.Uri` is a framework type and AD-1 keeps `data/` free of the platform —
 * `BackupRepository` produces a `String` and knows nothing about where a file is, which is what
 * lets its tests run without Robolectric at all. The seam between the two is this one function.
 *
 * ## Why it takes the text and does not build it
 *
 * Because the database read is a `suspend` function in `data/` and this is a blocking write on a
 * `ContentResolver`; composing them here would put a `suspend` function inside a Composable's
 * launcher callback and make "what does the export read" a question about UI code. Split like
 * this, the read is testable against a real Room database and the write is testable against a
 * fake resolver, and neither test needs the other to exist.
 *
 * ## Why no storage permission appears anywhere near this
 *
 * Because a `Uri` handed back by `ActivityResultContracts.CreateDocument` already carries the
 * grant. The app declares **zero** permissions in its manifest, and `ExportPermissionTest` fails
 * the build if that stops being true — asking for `WRITE_EXTERNAL_STORAGE` here would be both
 * unnecessary and, since `targetSdk` is 36, silently ineffective on modern Android.
 */
object BackupFileWriter {

    /**
     * Writes [json] to [uri], truncating whatever was there.
     *
     * `openOutputStream(uri, "wt")` rather than `"w"`: `"w"` maps to `"w"` on most providers and
     * **fails** on a few that require an explicit truncate, and a backup written by appending to
     * a previous backup produces a file that parses as valid JSON up to the boundary and then
     * breaks — the worst possible failure for the one artifact the owner cannot regenerate.
     *
     * @return `true` when the bytes reached the provider, `false` when the write failed. The
     *   caller owns the message: this function knows nothing about the app's wording, and an
     *   exception thrown across the launcher boundary would crash the Activity rather than
     *   raise a banner.
     */
    fun write(context: Context, uri: Uri, json: String): Boolean = try {
        // A null stream means the provider refused the write, and `use` on null quietly returns
        // null — so the result is checked rather than assumed. Treating it as success would show
        // the owner "saved as klinik-net-backup-2026-10-19.json" for a file that does not exist,
        // which is the one outcome worse than saying nothing at all.
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: return false
        stream.use {
            it.write(json.toByteArray(Charsets.UTF_8))
            it.flush()
        }
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        // The grant can be revoked between the picker returning and the write, and it does not
        // always arrive as an IOException.
        false
    }
}