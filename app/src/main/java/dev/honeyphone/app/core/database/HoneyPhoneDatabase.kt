package dev.honeyphone.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReviewEntity::class, SignalEntity::class, OutboxEntity::class], version = 2, exportSchema = true)
abstract class HoneyPhoneDatabase : RoomDatabase() {
    abstract fun reviewDao(): ReviewDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reviews ADD COLUMN networkLabel TEXT")
                db.execSQL("ALTER TABLE reviews ADD COLUMN metricsJson TEXT")
            }
        }
        @Volatile private var instance: HoneyPhoneDatabase? = null
        fun get(context: Context): HoneyPhoneDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HoneyPhoneDatabase::class.java, "honeyphone.db").addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
