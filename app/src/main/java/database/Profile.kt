package com.example.boraiptvplayer.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "profile_name")
    val profileName: String,

    @ColumnInfo(name = "server_url")
    val serverUrl: String, // Xtream için DNS, M3U için Playlist Linki

    @ColumnInfo(name = "username")
    val username: String = "", // M3U ise boş kalabilir

    @ColumnInfo(name = "password")
    val password: String = "", // M3U ise boş kalabilir

    // YENİ EKLENEN: Bu profil M3U mu yoksa Xtream mi?
    @ColumnInfo(name = "is_m3u")
    val isM3u: Boolean = false
)