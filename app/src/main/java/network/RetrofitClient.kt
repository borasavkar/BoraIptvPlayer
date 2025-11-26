package com.example.boraiptvplayer.network

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.dnsoverhttps.DnsOverHttps
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // --- ULTRA HIZLI BAĞLANTI AYARLARI ---

    // 1. Bağlantı Havuzu: (Senin ayarların korundu)
    private val pool = ConnectionPool(20, 2, TimeUnit.MINUTES)

    // 2. Dağıtıcı: (Senin ayarların korundu)
    private val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 32
    }

    // 3. DNS Hızlandırması için Cache Klasörü (Uygulama context'ine ihtiyacımız olmadığı için geçici bir çözüm)
    // Not: Gerçek bir uygulamada Context injection daha iyidir ama 'Object' yapısında
    // en pratik ve hızlı çözüm, DNS'i statik oluşturmaktır.

    // DNS İstemcisi (Sadece DNS sorguları için kullanılacak hafif client)
    private val bootstrapClient = OkHttpClient.Builder()
        .cache(null)
        .build()

    // --- CLOUDFLARE DNS (1.1.1.1) ENTEGRASYONU ---
    // Bu, operatör engellerini ve yavaşlığını aşar.
    private val dns = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://1.1.1.1/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1")
        )
        .build()

    // --- ANA İSTEMCİ (APP MOTORU) ---
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(pool)
        .dispatcher(dispatcher)
        .dns(dns) // <-- KRİTİK EKLEME: Özel DNS motorunu buraya taktık
        .retryOnConnectionFailure(true)
        .connectTimeout(10, TimeUnit.SECONDS) // Hızlı pes et, kullanıcıyı bekletme
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1)) // Stabilite için HTTP 1.1
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