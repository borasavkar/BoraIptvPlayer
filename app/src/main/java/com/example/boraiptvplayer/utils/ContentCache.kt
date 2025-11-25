package com.example.boraiptvplayer.utils

import com.example.boraiptvplayer.network.EpgListing
import com.example.boraiptvplayer.network.LiveStream
import com.example.boraiptvplayer.network.SeriesStream
import com.example.boraiptvplayer.network.VodStream

object ContentCache {
    private var lastProfileId: Int = -1

    // Verileri tutacak listeler
    var cachedChannels: List<LiveStream> = emptyList()
    var cachedMovies: List<VodStream> = emptyList()
    var cachedSeries: List<SeriesStream> = emptyList()
    var cachedEpg: List<EpgListing>? = null

    // Bu profilin verileri hafızada var mı?
    fun hasDataFor(profileId: Int): Boolean {
        return lastProfileId == profileId && (cachedChannels.isNotEmpty() || cachedMovies.isNotEmpty() || cachedSeries.isNotEmpty())
    }

    // Verileri hafızaya kaydet
    fun update(profileId: Int, channels: List<LiveStream>, movies: List<VodStream>, series: List<SeriesStream>, epg: List<EpgListing>?) {
        lastProfileId = profileId
        cachedChannels = channels
        cachedMovies = movies
        cachedSeries = series
        cachedEpg = epg
    }

    // Hafızayı temizle (Örn: Çıkış yapınca)
    fun clear() {
        lastProfileId = -1
        cachedChannels = emptyList()
        cachedMovies = emptyList()
        cachedSeries = emptyList()
        cachedEpg = null
    }
}