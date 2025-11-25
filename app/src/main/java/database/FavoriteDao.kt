package com.example.boraiptvplayer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    // Mevcut kodların arasına ekle:
    @Query("SELECT * FROM favorites ORDER BY id DESC")
    suspend fun getAllFavoritesSync(): List<Favorite>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE streamId = :streamId AND streamType = :type")
    suspend fun removeFavorite(streamId: Int, type: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE streamId = :streamId AND streamType = :type)")
    suspend fun isFavorite(streamId: Int, type: String): Boolean

    @Query("SELECT * FROM favorites ORDER BY id DESC")
    fun getAllFavorites(): Flow<List<Favorite>>
}