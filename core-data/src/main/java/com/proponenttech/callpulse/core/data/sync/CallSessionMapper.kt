package com.proponenttech.callpulse.core.data.sync

import com.proponenttech.callpulse.core.data.db.CallSessionEntity
import com.proponenttech.callpulse.core.network.dto.CallPayload
import java.time.Instant

object CallSessionMapper {
    fun skipReason(entity: CallSessionEntity, personalNumber: Boolean = false): String? = when {
        entity.phoneE164 == null -> "Private number or short code — upload skipped"
        entity.direction == "unknown" -> "Unknown call direction — upload skipped"
        personalNumber || entity.isPersonal -> "Personal number — upload skipped"
        else -> null
    }

    fun toPayload(entity: CallSessionEntity): CallPayload? {
        if (skipReason(entity) != null) return null
        val school = entity.schoolId ?: return null
        val noAnswer = entity.direction == "missed" || entity.direction == "rejected"
        return CallPayload(
            clientUuid = entity.clientUuid,
            schoolId = school,
            androidCallLogId = entity.androidCallLogId?.toLongOrNull(),
            phoneE164 = requireNotNull(entity.phoneE164),
            contactName = entity.contactName,
            direction = entity.direction,
            answered = !noAnswer && entity.answered,
            startedAt = iso(entity.startedAtMillis),
            endedAt = iso(if (noAnswer) entity.startedAtMillis else
                entity.endedAtMillis ?: (entity.startedAtMillis + entity.durationSec * 1000L)),
            durationSec = if (noAnswer) 0 else entity.durationSec,
        )
    }
    private fun iso(millis: Long) = Instant.ofEpochMilli(millis).toString()
}
