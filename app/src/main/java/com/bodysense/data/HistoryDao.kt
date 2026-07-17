package com.bodysense.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertHistory(history: HistoryEntity)

    @Query("SELECT * FROM assessment_history ORDER BY dateMillis DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM assessment_history WHERE diseaseId = :diseaseId ORDER BY dateMillis DESC")
    fun getHistoryByDisease(diseaseId: String): Flow<List<HistoryEntity>>

    @Delete
    suspend fun deleteHistory(history: HistoryEntity)

    @Query("DELETE FROM assessment_history")
    suspend fun deleteAllHistory()
}
