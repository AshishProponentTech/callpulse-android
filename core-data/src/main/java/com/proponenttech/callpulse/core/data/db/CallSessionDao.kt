package com.proponenttech.callpulse.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallSessionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CallSessionEntity): Long

    @Update
    suspend fun update(entity: CallSessionEntity)

    @Query("SELECT * FROM call_drafts ORDER BY startedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<CallSessionEntity>>

    @Query("SELECT COUNT(*) FROM call_drafts WHERE reviewedAtMillis IS NULL")
    fun observeUnreviewedCount(): Flow<Int>

    @Query("SELECT * FROM call_drafts WHERE id = :id")
    suspend fun findById(id: Long): CallSessionEntity?

    // ---- §13.3 local dedupe, in priority order --------------------------------------------

    /** 1. The same Android CallLog row is the same call, full stop. */
    @Query("SELECT * FROM call_drafts WHERE androidCallLogId = :callLogId LIMIT 1")
    suspend fun findByCallLogId(callLogId: String): CallSessionEntity?

    @Query("SELECT * FROM call_drafts WHERE clientUuid = :clientUuid LIMIT 1")
    suspend fun findByClientUuid(clientUuid: String): CallSessionEntity?

    /**
     * 2. Same number, same channel, started within a short window. Guards the race between the
     * PhoneState receiver and the periodic reconcile scan both finding the same call.
     */
    @Query(
        """
        SELECT * FROM call_drafts
        WHERE phoneE164 = :phoneE164
          AND channel = :channel
          AND startedAtMillis BETWEEN :fromMillis AND :toMillis
        LIMIT 1
        """,
    )
    suspend fun findNear(
        phoneE164: String,
        channel: String,
        fromMillis: Long,
        toMillis: Long,
    ): CallSessionEntity?

    /**
     * Fills in the project on drafts captured before one was chosen.
     *
     * Deliberately only touches rows where it is NULL. §11.4 says changing the sticky project
     * affects future calls only and historical drafts are never rewritten — filling a blank is
     * not the same thing as overwriting a decision someone already made.
     */
    @Query("UPDATE call_drafts SET projectId = :projectId WHERE projectId IS NULL")
    suspend fun assignProjectToUnassigned(projectId: Long): Int

    // ---- sync queue -------------------------------------------------------------------------

    @Query("SELECT * FROM call_drafts WHERE syncStatus IN ('pending', 'syncing') ORDER BY startedAtMillis ASC LIMIT :limit")
    suspend fun pendingForSync(limit: Int): List<CallSessionEntity>

    @Query("SELECT COUNT(*) FROM call_drafts WHERE syncStatus = 'pending'")
    suspend fun pendingCount(): Int

    @Query("UPDATE call_drafts SET syncStatus = :status, serverId = :serverId, syncError = NULL WHERE clientUuid = :clientUuid")
    suspend fun markSynced(clientUuid: String, status: String, serverId: Long?)

    @Query("UPDATE call_drafts SET syncStatus = 'failed', syncError = :error, syncAttempts = syncAttempts + 1 WHERE clientUuid = :clientUuid")
    suspend fun markFailed(clientUuid: String, error: String)

    @Query("UPDATE call_drafts SET syncStatus = 'pending' WHERE syncStatus = 'syncing'")
    suspend fun resetStuckSyncing()

    /** §15 — local rows are purged after 30 days; they are not deleted merely because they synced. */
    @Query("DELETE FROM call_drafts WHERE syncStatus = 'synced' AND startedAtMillis < :cutoffMillis")
    suspend fun purgeSyncedBefore(cutoffMillis: Long): Int
}
