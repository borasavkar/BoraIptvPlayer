package com.example.boraiptvplayer.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // --- ULTRA HIZLI BAĞLANTI AYARLARI ---

    // 1. Bağlantı Havuzu: Sayıyı artırdık, süreyi kıstık (Hızlı döngü)
    private val pool = ConnectionPool(20, 2, TimeUnit.MINUTES)

    // 2. Dağıtıcı: Aynı anda 64 indirmeye izin ver (Tarayıcılar gibi çalışsın)
    private val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 32
    }

    val okHttpClient = OkHttpClient.Builder()
        .connectionPool(pool)
        .dispatcher(dispatcher)
        .retryOnConnectionFailure(true)
        // Hızlı pes et ve tekrar dene (Uzun süre beklemesin)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Hız için HTTP/1.1 (En stabil protokol)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    fun createService(baseUrl: String): ApiService {
        val finalBaseUrl = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"

        val retrofit = Retrofit.Builder()
            .baseUrl(finalBaseUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()

        return retrofit.create(ApiService::class.java)
    }
}