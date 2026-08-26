package com.proponenttech.callpulse.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * §19 — every schema change ships a real Migration and an exported schema JSON.
 * `fallbackToDestructiveMigration()` is banned past the first internal release: it silently
 * destroys employees' unsynced calls.
 */
@Database(
    entities = [CallSessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CallPulseDatabase : RoomDatabase() {
    abstract fun callSessionDao(): CallSessionDao

    companion object {
        const val NAME = "callpulse.db"
    }
}
