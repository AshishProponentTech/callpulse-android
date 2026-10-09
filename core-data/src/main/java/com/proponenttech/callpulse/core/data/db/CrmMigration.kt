package com.proponenttech.callpulse.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Rebuild because serverId changes from an integer to a CRM UUID.
 * Legacy project/server references cannot be mapped to CRM schools or calls.
 * Preserve every local draft, its client UUID and its sync state.
 * CREATE statements are copied exactly from the exported Room v3 schema.
 */
object CrmMigration : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `call_drafts_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clientUuid` TEXT NOT NULL, `serverId` TEXT, `schoolId` TEXT, `leadId` TEXT, `leadName` TEXT, `leadStatus` TEXT, `reviewState` TEXT NOT NULL, `phoneE164` TEXT, `phoneDisplay` TEXT, `contactName` TEXT, `direction` TEXT NOT NULL, `channel` TEXT NOT NULL, `subChannel` TEXT, `startedAtMillis` INTEGER NOT NULL, `endedAtMillis` INTEGER, `tzOffsetMinutes` INTEGER NOT NULL, `durationSec` INTEGER NOT NULL, `ringSec` INTEGER, `answered` INTEGER NOT NULL, `isPersonal` INTEGER NOT NULL, `confidence` TEXT NOT NULL, `source` TEXT NOT NULL, `deviceUuid` TEXT, `androidCallLogId` TEXT, `phoneAccountId` TEXT, `outcome` TEXT, `notes` TEXT, `appVersion` TEXT, `capturedAtMillis` INTEGER NOT NULL, `reviewedAtMillis` INTEGER, `syncStatus` TEXT NOT NULL, `syncError` TEXT, `syncAttempts` INTEGER NOT NULL)""")
        db.execSQL("""INSERT INTO call_drafts_new (id, clientUuid, serverId, schoolId, leadId, leadName, leadStatus, reviewState, phoneE164, phoneDisplay, contactName, direction, channel, subChannel, startedAtMillis, endedAtMillis, tzOffsetMinutes, durationSec, ringSec, answered, isPersonal, confidence, source, deviceUuid, androidCallLogId, phoneAccountId, outcome, notes, appVersion, capturedAtMillis, reviewedAtMillis, syncStatus, syncError, syncAttempts) SELECT id, clientUuid, NULL, NULL, NULL, NULL, NULL, 'unreviewed', phoneE164, phoneDisplay, contactName, direction, channel, subChannel, startedAtMillis, endedAtMillis, tzOffsetMinutes, durationSec, ringSec, answered, isPersonal, confidence, source, deviceUuid, androidCallLogId, phoneAccountId, outcome, notes, appVersion, capturedAtMillis, reviewedAtMillis, syncStatus, syncError, syncAttempts FROM call_drafts""")
        db.execSQL("DROP TABLE call_drafts")
        db.execSQL("ALTER TABLE call_drafts_new RENAME TO call_drafts")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_call_drafts_clientUuid` ON `call_drafts` (`clientUuid`)""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_call_drafts_androidCallLogId` ON `call_drafts` (`androidCallLogId`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_call_drafts_startedAtMillis` ON `call_drafts` (`startedAtMillis`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_call_drafts_syncStatus` ON `call_drafts` (`syncStatus`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `personal_numbers` (`phoneE164` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`phoneE164`))""")
    }
}
