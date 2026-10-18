package net.klinik.rental.data.backup

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The export asks for **no storage permission**, and that is checked rather than assumed.
 *
 * ## Why a test is the only thing that can hold this promise
 *
 * The manifest already declares zero permissions and already carries a comment saying the backup
 * goes through the Storage Access Framework. So the acceptance criterion — *"saya tidak perlu izin
 * penyimpanan manual"* — is not work, it is a **property to hold**. A property nobody checks is a
 * property that a well-meaning future feature erases: a feature that wants to enumerate files
 * rather than be handed one would reach for `READ_EXTERNAL_STORAGE`, it would work on the
 * developer's device, and it would be a one-line change nobody would think to check against this
 * criterion.
 *
 * So this test reads the **merged** manifest — the one that actually ships — rather than the
 * source file, because a permission added by a library's manifest would never appear in the
 * source and would still end up in the APK.
 *
 * ## What it deliberately does *not* assert
 *
 * It does not assert the app requests **no** permissions at all. A future feature may legitimately
 * need one — a camera, say — and this test must not become the reason that gets blocked. It asks
 * the narrower question the criterion asks: none of the three permissions that would give the app
 * blanket or direct access to the owner's files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportPermissionTest {

    @Test
    fun `the app requests no storage permission`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toSet()
            .orEmpty()

        val storagePermissions = declared intersect STORAGE_PERMISSIONS

        assertTrue(
            "The backup is handed a Uri by the Storage Access Framework, which needs no permission. " +
                "These were requested: $storagePermissions. A `Uri` from CreateDocument already " +
                "carries the grant; asking for storage on top of it is redundant, and on targetSdk 36 " +
                "READ_EXTERNAL_STORAGE is silently ineffective anyway.",
            storagePermissions.isEmpty(),
        )
    }

    private companion object {
        /**
         * The three that would hand the app access to the owner's files directly.
         *
         * `MANAGE_EXTERNAL_STORAGE` is all-files access and is the one the criterion names; the two
         * legacy ones are what a feature would reach for out of habit. All three are listed so the
         * failure message names what actually turned up rather than only what was expected.
         */
        val STORAGE_PERMISSIONS = setOf(
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
        )
    }
}