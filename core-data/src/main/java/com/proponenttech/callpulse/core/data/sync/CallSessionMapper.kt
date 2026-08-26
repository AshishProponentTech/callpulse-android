package com.proponenttech.callpulse.core.data.sync

import com.proponenttech.callpulse.core.data.db.CallSessionEntity
import com.proponenttech.callpulse.core.network.dto.CallSessionPayload
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Local draft to the section 21 wire shape. */
object CallSessionMapper {

    fun toPayload(entity: CallSessionEntity, deviceUuid: String): CallSessionPayload? {
        // Rule 7: every call row has a project_id. A draft without one cannot be sent; the
        // employee has to pick a project first, and the UI surfaces that.
        val projectId = entity.projectId ?: return null

        return CallSessionPayload(
            clientUuid = entity.clientUuid,
            deviceUuid = entity.deviceUuid ?: deviceUuid,
            projectId = projectId,
            taskId = entity.taskId,
            milestoneId = entity.milestoneId,
            phoneE164 = entity.phoneE164,
            phoneDisplay = entity.phoneDisplay,
            contactName = entity.contactName,
            direction = entity.direction,
            channel = entity.channel,
            subChannel = entity.subChannel,
            startedAt = iso(entity.startedAtMillis),
            endedAt = entity.endedAtMillis?.let { iso(it) },
            tzOffsetMinutes = entity.tzOffsetMinutes,
            durationSec = entity.durationSec,
            ringSec = entity.ringSec,
            answered = entity.answered,
            isPersonal = entity.isPersonal,
            confidence = entity.confidence,
            source = entity.source,
            androidCallLogId = entity.androidCallLogId,
            phoneAccountId = entity.phoneAccountId,
            outcome = entity.outcome,
            notes = entity.notes,
            appVersion = entity.appVersion,
            capturedAt = iso(entity.capturedAtMillis),
            reviewedAt = entity.reviewedAtMillis?.let { iso(it) },
        )
    }

    /** Decision 8 - ISO-8601 with offset. */
    private fun iso(millis: Long): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()),
        )
}
