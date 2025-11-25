package com.example.boraiptvplayer.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_interactions")
data class Interaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val streamId: Int,
    val streamType: String, // "live", "vod", "series"
    val categoryId: String,

    // --- YENİ EKLENEN SÜTUNLAR ---
    val durationSeconds: Long,      // Toplam izleme
    val lastPosition: Long = 0,     // Kaldığı yer (milisaniye)
    val maxDuration: Long = 0,      // Video uzunluğu
    val isFinished: Boolean = false, // Bitti mi?
    val timestamp: Long = System.currentTimeMillis()
)