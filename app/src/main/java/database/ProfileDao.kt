package com.example.boraiptvplayer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    // DEĞİŞİKLİK 1: Kayıt sonrası yeni ID'yi (Long) döndürür
    @Insert
    suspend fun insertProfile(profile: Profile): Long

    @Delete
    suspend fun deleteProfile(profile: Profile)

    @Update
    suspend fun updateProfile(profile: Profile)

    // Canlı takip için (Flow)
    @Query("SELECT * FROM profiles ORDER BY profile_name ASC")
    fun getAllProfiles(): Flow<List<Profile>>

    // DEĞİŞİKLİK 2: Hata veren fonksiyon bu. Eksikti, eklendi.
    // Anlık kontrol için senkron liste döndürür
    @Query("SELECT * FROM profiles")
    fun getAllProfilesSync(): List<Profile>
}