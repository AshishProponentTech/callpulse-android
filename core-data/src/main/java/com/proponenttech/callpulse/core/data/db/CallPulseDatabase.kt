package com.proponenttech.callpulse.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * §19 — every schema change ships a real Migration and an exported schema JSON.
 * `fallbackToDestructiveMigration()` is banned past the first internal release: it silently
 * destroys employees' unsynced calls.
 */
@Database(
    entities = [CallSessionEntity::class, PersonalNumberEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class CallPulseDatabase : RoomDatabase() {
    abstract fun callSessionDao(): CallSessionDao
    abstract fun personalNumberDao(): PersonalNumberDao

    companion object {
        const val NAME = "callpulse.db"
        val MIGRATION_2_3: Migration = CrmMigration

        /**
         * Makes androidCallLogId unique, so a race between two concurrent scans cannot produce a
         * second draft for the same call.
         *
         * Existing duplicates must go first or the index cannot be created. The row KEPT is the
         * lowest id — the original capture — so any review already attached to it survives and
         * only the accidental copy is dropped.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM call_drafts
                    WHERE androidCallLogId IS NOT NULL
                      AND id NOT IN (
                        SELECT MIN(id) FROM call_drafts
                        WHERE androidCallLogId IS NOT NULL
                        GROUP BY androidCallLogId
                      )
                    """.trimIndent(),
                )
                db.execSQL("DROP INDEX IF EXISTS index_call_drafts_androidCallLogId")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_call_drafts_androidCallLogId " +
                        "ON call_drafts (androidCallLogId)",
                )
            }
        }
    }
}
