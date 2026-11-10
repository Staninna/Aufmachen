package com.staninna.aufmachen.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        HeaderEntity::class, ServiceStatsEntity::class, ServiceStateEntity::class, MergeEntity::class,
        TimelineEntity::class, ProofEntity::class, AlertEntity::class, PersonStatsEntity::class,
        PersonStateEntity::class, SyncStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun headers(): HeaderDao
    abstract fun services(): ServiceDao
    abstract fun timeline(): TimelineDao
    abstract fun proofs(): ProofDao
    abstract fun alerts(): AlertDao
    abstract fun people(): PersonDao
    abstract fun sync(): SyncDao

    companion object {
        const val NAME = "aufmachen.db"

        /** 0.1.x to 0.2: the user's tab choice per service and per person. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE service_state ADD COLUMN placement TEXT")
                db.execSQL("ALTER TABLE person_state ADD COLUMN placement TEXT")
            }
        }

        /** Opens the SQLCipher-encrypted database. The passphrase comes from [SecretStore]. */
        fun open(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        /** In-memory, unencrypted. Tests only. */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
}
