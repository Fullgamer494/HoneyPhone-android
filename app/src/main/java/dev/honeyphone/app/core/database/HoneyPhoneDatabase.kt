package dev.honeyphone.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.honeyphone.app.core.auth.SecureSessionStore

@Database(entities = [ReviewEntity::class, SignalEntity::class, OutboxEntity::class, HoneypotEventEntity::class], version = 5, exportSchema = true)
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
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `honeypot_events` ADD COLUMN `requestDetails` TEXT NOT NULL DEFAULT '{}'")
            }
        }
        @Volatile private var instance: HoneyPhoneDatabase? = null
        @Volatile private var instanceName: String? = null

        fun bindAccount(context: Context, userId: String) = synchronized(this) {
            val appContext=context.applicationContext
            val session=SecureSessionStore(appContext)
            val previous=session.accountId()
            if(previous==userId) return@synchronized
            instance?.close(); instance=null; instanceName=null
            if(previous==null && !session.hasEverBoundAccount()) {
                val legacy=appContext.getDatabasePath("honeyphone.db")
                val target=appContext.getDatabasePath(databaseName(userId))
                val localPrefs=appContext.getSharedPreferences("honeyphone_local", Context.MODE_PRIVATE)
                localPrefs.getString("installation_id", null)?.let { legacyId ->
                    localPrefs.edit().putString("installation_id_$userId", legacyId).remove("installation_id").apply()
                }
                if(legacy.exists() && !target.exists()) {
                    target.parentFile?.mkdirs()
                    legacy.renameTo(target)
                    listOf("-wal","-shm").forEach { suffix ->
                        val old=appContext.getDatabasePath("honeyphone.db$suffix")
                        if(old.exists()) old.renameTo(appContext.getDatabasePath("${databaseName(userId)}$suffix"))
                    }
                }
            }
            session.saveAccountId(userId)
        }

        fun get(context: Context): HoneyPhoneDatabase = synchronized(this) {
            val appContext=context.applicationContext
            val name=SecureSessionStore(appContext).accountId()?.let(::databaseName) ?: "honeyphone.db"
            if(instance==null || instanceName!=name) {
                instance?.close()
                instance=Room.databaseBuilder(appContext, HoneyPhoneDatabase::class.java, name)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
                instanceName=name
            }
            requireNotNull(instance)
        }

        fun deleteAccountData(context: Context, userId: String) = synchronized(this) {
            val appContext=context.applicationContext
            val name=databaseName(userId)
            if(instanceName==name) {
                instance?.close()
                instance=null
                instanceName=null
            }
            appContext.deleteDatabase(name)
            appContext.getSharedPreferences("honeyphone_local", Context.MODE_PRIVATE)
                .edit().remove("installation_id_$userId").apply()
        }

        private fun databaseName(userId: String) = "honeyphone_${userId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.db"
    }
}
