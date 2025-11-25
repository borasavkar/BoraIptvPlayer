package com.example.boraiptvplayer.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val streamId: Int,
    val streamType: String, // "live", "vod", "series"
    val name: String,
    val image: String?,
    val categoryId: String?
)