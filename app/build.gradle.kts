plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("kotlin-kapt") // Glide compiler (kapt) için gerekli eklenti
}

android {
    namespace = "com.example.boraiptvplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.boraiptvplayer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // --- Varsayılan AndroidX Kütüphaneleri ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    // OkHttp (İnternetten dosya indirmek için)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- Bora'nın IPTV Projesi Kütüphaneleri ---

    // 1. Video Oynatıcı: Media3 (ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)

    // 2. API İstemcisi: Retrofit
    implementation(libs.squareup.retrofit)

    // 3. JSON İşleyicisi: Moshi
    implementation(libs.squareup.retrofit.converter.moshi)
    implementation(libs.squareup.moshi.kotlin)

    // 4. Lifecycle (ViewModelScope ve lifecycleScope)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // 5. Veritabanı: Room (KSP Kullanarak)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 6. Resim Yükleme: Glide (KAPT Kullanarak)
    implementation("com.github.bumptech.glide:glide:5.0.5")
    kapt("com.github.bumptech.glide:compiler:5.0.5")
    // Google Play Ödeme Sistemi
    implementation("com.android.billingclient:billing-ktx:6.1.0")
    // ExoPlayer OkHttp Eklentisi (Daha hızlı internet bağlantısı için)
    implementation("androidx.media3:media3-datasource-okhttp:1.2.0")
    // Not: Versiyon numarası diğer media3 kütüphaneleriyle aynı olmalı (örn: 1.2.0 veya 1.X.X)
}