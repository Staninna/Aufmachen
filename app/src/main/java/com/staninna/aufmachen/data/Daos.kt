package com.staninna.aufmachen.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HeaderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<HeaderEntity>): List<Long>

    @Query("SELECT uid FROM headers WHERE source = :source")
    suspend fun uids(source: String): List<Long>

    @Query("SELECT COUNT(*) FROM headers")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM headers")
    suspend fun count(): Int

    /** Keyset paging for the analyzer: rows with id > afterId, ascending. */
    @Query("SELECT * FROM headers WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(afterId: Long, limit: Int): List<HeaderEntity>

    @Query("SELECT * FROM headers WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<HeaderEntity>

    @Query("SELECT * FROM headers WHERE syncedAt >= :since AND initialScan = 0 ORDER BY id")
    suspend fun syncedSince(since: Long): List<HeaderEntity>

    @Query("SELECT * FROM headers WHERE fromDomain IN (:domains) ORDER BY dateEpochDay DESC LIMIT :limit")
    suspend fun latestFromDomains(domains: List<String>, limit: Int): List<HeaderEntity>

    @Query("UPDATE headers SET seen = :seen WHERE source = :source AND uid IN (:uids)")
    suspend fun setSeen(source: String, uids: List<Long>, seen: Boolean)

    @Query("UPDATE headers SET flagged = :v WHERE source = :source AND uid IN (:uids)")
    suspend fun setFlagged(source: String, uids: List<Long>, v: Boolean)

    @Query("UPDATE headers SET important = :v WHERE source = :source AND uid IN (:uids)")
    suspend fun setImportant(source: String, uids: List<Long>, v: Boolean)

    @Query("UPDATE headers SET attach = :v WHERE source = :source AND uid IN (:uids)")
    suspend fun setAttach(source: String, uids: List<Long>, v: Boolean)

    @Query("UPDATE headers SET category = :category WHERE source = :source AND uid IN (:uids)")
    suspend fun setCategory(source: String, uids: List<Long>, category: String)

    @Query("DELETE FROM headers WHERE source = :source")
    suspend fun deleteSource(source: String)
}

@Dao
interface ServiceDao {
    @Query("SELECT * FROM service_stats ORDER BY score DESC, mails DESC")
    fun allStats(): Flow<List<ServiceStatsEntity>>

    @Query("SELECT * FROM service_stats ORDER BY score DESC, mails DESC")
    suspend fun allStatsOnce(): List<ServiceStatsEntity>

    @Query("SELECT * FROM service_stats WHERE domain = :domain")
    fun stats(domain: String): Flow<ServiceStatsEntity?>

    @Query("SELECT * FROM service_stats WHERE domain = :domain")
    suspend fun statsOnce(domain: String): ServiceStatsEntity?

    @Query("DELETE FROM service_stats")
    suspend fun clearStats()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(rows: List<ServiceStatsEntity>)

    @Transaction
    suspend fun replaceStats(rows: List<ServiceStatsEntity>) {
        clearStats()
        rows.chunked(500).forEach { insertStats(it) }
    }

    @Query("SELECT * FROM service_state")
    fun allStates(): Flow<List<ServiceStateEntity>>

    @Query("SELECT * FROM service_state")
    suspend fun allStatesOnce(): List<ServiceStateEntity>

    @Query("SELECT * FROM service_state WHERE domain = :domain")
    fun state(domain: String): Flow<ServiceStateEntity?>

    @Query("SELECT * FROM service_state WHERE domain = :domain")
    suspend fun stateOnce(domain: String): ServiceStateEntity?

    @Upsert
    suspend fun upsertState(state: ServiceStateEntity)

    @Upsert
    suspend fun upsertStates(states: List<ServiceStateEntity>)

    @Query("SELECT * FROM merges")
    fun merges(): Flow<List<MergeEntity>>

    @Query("SELECT * FROM merges")
    suspend fun mergesOnce(): List<MergeEntity>

    @Upsert
    suspend fun upsertMerge(m: MergeEntity)

    @Query("DELETE FROM merges WHERE domain = :domain")
    suspend fun deleteMerge(domain: String)
}

@Dao
interface TimelineDao {
    @Insert
    suspend fun insert(e: TimelineEntity): Long

    @Query("SELECT * FROM timeline WHERE domain IN (:domains) ORDER BY at DESC")
    fun forDomains(domains: List<String>): Flow<List<TimelineEntity>>

    @Query("SELECT * FROM timeline")
    suspend fun all(): List<TimelineEntity>

    @Insert
    suspend fun insertAll(rows: List<TimelineEntity>)
}

@Dao
interface ProofDao {
    @Insert
    suspend fun insert(p: ProofEntity): Long

    @Query("SELECT * FROM proofs WHERE domain IN (:domains) ORDER BY createdAt DESC")
    fun forDomains(domains: List<String>): Flow<List<ProofEntity>>

    @Query("SELECT * FROM proofs")
    suspend fun all(): List<ProofEntity>

    @Insert
    suspend fun insertAll(rows: List<ProofEntity>)
}

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<AlertEntity>): List<Long>

    @Query("SELECT * FROM alerts WHERE dismissed = 0 ORDER BY createdAt DESC")
    fun open(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun all(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts")
    suspend fun allOnce(): List<AlertEntity>

    @Query("SELECT COUNT(*) FROM alerts WHERE dismissed = 0")
    fun openCount(): Flow<Int>

    @Query("UPDATE alerts SET dismissed = 1, dismissReason = :reason WHERE id = :id")
    suspend fun dismiss(id: Long, reason: String)

    @Query("SELECT COUNT(*) FROM alerts WHERE domain = :domain AND dismissed = 0")
    suspend fun openCountFor(domain: String): Int

    @Insert
    suspend fun insertAllPlain(rows: List<AlertEntity>)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM person_stats ORDER BY weight DESC")
    fun all(): Flow<List<PersonStatsEntity>>

    @Query("SELECT * FROM person_stats ORDER BY weight DESC")
    suspend fun allOnce(): List<PersonStatsEntity>

    @Query("DELETE FROM person_stats")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<PersonStatsEntity>)

    @Transaction
    suspend fun replaceAll(rows: List<PersonStatsEntity>) {
        clear()
        rows.chunked(500).forEach { insertAll(it) }
    }

    @Query("SELECT * FROM person_state")
    fun states(): Flow<List<PersonStateEntity>>

    @Query("SELECT * FROM person_state")
    suspend fun statesOnce(): List<PersonStateEntity>

    @Upsert
    suspend fun upsertStates(rows: List<PersonStateEntity>)
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE source = :source")
    suspend fun get(source: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state")
    fun all(): Flow<List<SyncStateEntity>>

    @Upsert
    suspend fun upsert(s: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE source = :source")
    suspend fun delete(source: String)
}
