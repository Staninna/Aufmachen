package com.staninna.aufmachen.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        HeaderEntity::class, ServiceStatsEntity::class, ServiceStateEntity::class, MergeEntity::class,
        TimelineEntity::class, ProofEntity::class, AlertEntity::class, PersonStatsEntity::class,
        PersonStateEntity::class, SyncStateEntity::class,
    ],
    version = 1,
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

        /** Opens the SQLCipher-encrypted database. The passphrase comes from [SecretStore]. */
        fun open(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }

        /** In-memory, unencrypted. Tests only. */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
}
