package com.example.boraiptvplayer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Bu, "Temel" Activity sınıfımız olacak.
// Tüm diğer Activity'ler (MainActivity, FilmsActivity vb.) bu sınıftan miras alacak.
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- TAM EKRAN KODU BURADA ---
        // Bu kod, bu sınıftan miras alan HER activity için otomatik olarak çalışacak.

        // 1. Adım: Pencerenin sistem çubuklarının arkasına geçmesine izin ver
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 2. Adım: Sistem çubuklarını (Durum Çubuğu ve Navigasyon Çubuğu) gizle
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        // 3. Adım: Kaydırarak geçici olarak gösterme davranışını ayarla
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}