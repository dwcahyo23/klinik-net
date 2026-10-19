package net.klinik.rental.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.klinik.rental.data.db.dao.SettingDao
import net.klinik.rental.data.db.entity.SettingEntity

/**
 * What the owner chose about the theme, as stored.
 *
 * [System] is both "never chosen" and "chose to follow the device": the stored key is absent in
 * the first case and holds `"system"` in the second, and nothing in the app behaves differently
 * between them — both resolve against the OS. Collapsing the two here means the reading side has
 * one fewer branch, and the writing side still records an explicit choice so a later change of
 * default does not silently move a tablet the owner had already answered for.
 */
enum class ThemeMode(val stored: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    /** Whether the app draws dark, given the OS's current answer. */
    fun resolvesDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        /** The stored value, or [System] for `null` and for anything a newer version wrote. */
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.stored == value } ?: System
    }
}

/**
 * The one setting that is read on every frame the app draws.
 *
 * ## Why this is a repository and not a `SettingDao` call in the Activity
 *
 * Because both halves of it — reading and writing — have to agree on the key string, and the key
 * is the kind of literal two call sites get wrong once. One class, one constant.
 *
 * A `Flow` rather than a suspend read, so writing the choice updates the Activity that is already
 * running instead of the one that starts next. See [SettingDao.observeValue].
 */
class ThemeRepository(private val dao: SettingDao) {

    fun observeMode(): Flow<ThemeMode> =
        dao.observeValue(KEY).map(ThemeMode::fromStored)

    suspend fun setMode(mode: ThemeMode) {
        dao.putSetting(SettingEntity(key = KEY, value = mode.stored))
    }

    private companion object {
        /** Matches the key Story 5.1's backup exports, and `NEVER_EXPORTED` deliberately omits it. */
        const val KEY = "themeMode"
    }
}
