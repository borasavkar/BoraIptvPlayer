package com.example.boraiptvplayer.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    // --- GİRİŞ (LOGIN) ---
    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<XtreamResponse>

    // ==========================================
    //               CANLI TV (LIVE)
    // ==========================================

    // 1. Canlı Yayınları Getir
    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null
    ): Response<List<LiveStream>>

    // 2. Canlı Yayın Kategorilerini Getir
    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories" // <-- BURASI "live" OLMALI
    ): Response<List<LiveCategory>>

    // ==========================================
    //               FİLMLER (VOD)
    // ==========================================

    // 3. Filmleri Getir
    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams"
    ): Response<List<VodStream>>

    // 4. Film Kategorilerini Getir (HATANIN KAYNAĞI BURASI OLABİLİR)
    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        // DİKKAT: Burası kesinlikle "get_vod_categories" olmalı
        @Query("action") action: String = "get_vod_categories"
    ): Response<List<VodCategory>>

    // ==========================================
    //               DİZİLER (SERIES)
    // ==========================================

    // 5. Dizileri Getir
    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series"
    ): Response<List<SeriesStream>>

    // 6. Dizi Kategorilerini Getir
    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        // DİKKAT: Burası kesinlikle "get_series_categories" olmalı
        @Query("action") action: String = "get_series_categories"
    ): Response<List<LiveCategory>>

    // 7. Dizi Detaylarını Getir
    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Int
    ): Response<SeriesInfoResponse>

    @GET("player_api.php")
    suspend fun getEpgTable(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_simple_data_table"
    ): Response<EpgResponse>
    // ... Diğer kodların altına ...

    // 8. TEK FİLM DETAYINI GETİR
    @GET("player_api.php")
    suspend fun getVodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_info",
        @Query("vod_id") vodId: Int
    ): Response<VodInfoResponse>
}