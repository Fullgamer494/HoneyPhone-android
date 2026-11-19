package dev.honeyphone.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReviewEntity::class, SignalEntity::class, OutboxEntity::class, HoneypotEventEntity::class], version = 4, exportSchema = true)
abstract class HoneyPhoneDatabase : RoomDatabase() {
    abstract fun reviewDao(): ReviewDao
    abstract fun honeypotDao(): HoneypotDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reviews ADD COLUMN networkLabel TEXT")
                db.execSQL("ALTER TABLE reviews ADD COLUMN metricsJson TEXT")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `honeypot_events` (`id` TEXT NOT NULL, `occurredAt` INTEGER NOT NULL, `sourceAddress` TEXT NOT NULL, `method` TEXT NOT NULL, `path` TEXT NOT NULL, `userAgent` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_honeypot_events_occurredAt` ON `honeypot_events` (`occurredAt`)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `honeypot_events` ADD COLUMN `sessionId` TEXT")
                db.execSQL("ALTER TABLE `honeypot_events` ADD COLUMN `responseCode` INTEGER NOT NULL DEFAULT 404")
                db.execSQL("ALTER TABLE `honeypot_events` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'pending'")
            }
        }
        @Volatile private var instance: HoneyPhoneDatabase? = null
        fun get(context: Context): HoneyPhoneDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HoneyPhoneDatabase::class.java, "honeyphone.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
