package com.proponenttech.callpulse.core.data.db

import androidx.room.*

@Entity(tableName = "personal_numbers")
data class PersonalNumberEntity(
    @PrimaryKey val phoneE164: String,
    val createdAtMillis: Long,
)

@Dao
interface PersonalNumberDao {
    @Query("SELECT EXISTS(SELECT 1 FROM personal_numbers WHERE phoneE164 = :phone)")
    suspend fun contains(phone: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(number: PersonalNumberEntity)
}
