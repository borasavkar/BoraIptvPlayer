package com.example.boraiptvplayer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface InteractionDao {

    // KAYDET
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun logInteraction(interaction: Interaction)

    // KALDIĞI YERİ GETİR
    @Query("SELECT * FROM user_interactions WHERE streamId = :streamId AND streamType = :type LIMIT 1")
    suspend fun getInteraction(streamId: Int, type: String): Interaction?

    // İZLENEN DİZİLERİ GETİR
    @Query("SELECT * FROM user_interactions WHERE streamType = 'series'")
    suspend fun getAllSeriesInteractions(): List<Interaction>

    // AI: EN SEVİLEN KATEGORİ
    @Query("SELECT categoryId, SUM(durationSeconds) as totalScore FROM user_interactions WHERE streamType = :type GROUP BY categoryId ORDER BY totalScore DESC LIMIT 1")
    suspend fun getTopCategoryForType(type: String): List<CategoryScore>

    // AI: BASKIN TÜR
    @Query("SELECT streamType FROM user_interactions GROUP BY streamType ORDER BY SUM(durationSeconds) DESC LIMIT 1")
    suspend fun getDominantStreamType(): String?

    // AI: TOPLAM SÜRE
    @Query("SELECT SUM(durationSeconds) FROM user_interactions")
    suspend fun getTotalUserWatchTime(): Long?
}