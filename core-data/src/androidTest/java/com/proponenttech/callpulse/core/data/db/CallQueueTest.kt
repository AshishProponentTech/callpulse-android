package com.proponenttech.callpulse.core.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CallQueueTest {
    @Test fun selectionRescuesUnassignedDraftsAndQueueExcludesFailedAndSkipped() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CallPulseDatabase::class.java).build()
        try {
            val dao = db.callSessionDao()
            val base = CallSessionEntity(
                clientUuid = "unassigned", schoolId = null, phoneE164 = "+919876543210",
                phoneDisplay = null, contactName = null, direction = "outbound", channel = "cellular",
                startedAtMillis = 1000, endedAtMillis = 6000, tzOffsetMinutes = 330,
                durationSec = 5, answered = true, confidence = "high", source = "call_log",
                deviceUuid = "device", androidCallLogId = null, phoneAccountId = null, capturedAtMillis = 7000,
            )
            dao.insert(base)
            dao.insert(base.copy(clientUuid = "assigned", schoolId = "original-school"))
            dao.insert(base.copy(clientUuid = "failed", schoolId = "original-school", syncStatus = SyncStatus.FAILED))
            dao.insert(base.copy(clientUuid = "skipped", schoolId = "original-school", syncStatus = SyncStatus.SKIPPED))
            assertEquals(listOf("assigned"), dao.pendingForSync(100).map { it.clientUuid })
            assertEquals(1, dao.assignSchoolToUnassigned("new-school"))
            assertEquals("original-school", dao.findByClientUuid("assigned")?.schoolId)
            assertEquals("new-school", dao.findByClientUuid("unassigned")?.schoolId)
            assertEquals(1, dao.retryFailed())
            assertEquals(3, dao.pendingForSync(100).size)
            dao.markSynced("assigned", "server-uuid", "lead-uuid", "Student", "new", "linked")
            val uploaded = requireNotNull(dao.findByClientUuid("assigned"))
            assertEquals("server-uuid", uploaded.serverId)
            assertEquals("Student", uploaded.leadName)
            assertEquals("linked", uploaded.reviewState)
            assertEquals(SyncStatus.SYNCED, uploaded.syncStatus)
            dao.markSkipped("unassigned", "Private number")
            assertEquals(listOf("failed"), dao.pendingForSync(100).map { it.clientUuid })
        } finally {
            db.close()
        }
    }
}
