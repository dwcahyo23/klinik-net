package net.klinik.rental.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.klinik.rental.data.db.dao.VehicleDao
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.VehicleEntity

/**
 * The app's Room database.
 *
 * Over two entities in Story 2.2: `VEHICLE`, and the minimum `CONTRACT` that
 * makes the vehicle-delete refusal enforceable. `RENTER` arrives with Story 2.4.
 *
 * @param version kept at `1` for the life of Epic 2. No migration is declared
 *   because there is no earlier version to migrate from — the first release
 *   ships this schema as-is. A later story that widens `CONTRACT` must add a
 *   `Migration` and bump this, or `fallbackToDestructiveMigration` will make it
 *   too easy to drop the owner's fleet.
 */
@Database(
    entities = [VehicleEntity::class, ContractEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KlinikDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao

    companion object {
        private const val NAME = "klinik.db"

        /**
         * The single application-wide instance.
         *
         * `@Volatile` because `onCreate` and the first screen's first read are
         * on different threads, and a database opened twice under `fallback` is
         * the classic way to end up with two in-memory stores that disagree.
         */
        @Volatile
        private var instance: KlinikDatabase? = null

        fun get(context: Context): KlinikDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): KlinikDatabase =
            Room.databaseBuilder(context, KlinikDatabase::class.java, NAME)
                .build()
    }
}