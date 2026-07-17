package com.bodysense.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assessment_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val diseaseId: String,
    val riskScore: Float,
    val confidence: Float,
    val diagnosis: String,
    val dateMillis: Long,
    val inputDataJson: String,
    val topContributorsJson: String? = null,
    val explanationText: String? = null
)
