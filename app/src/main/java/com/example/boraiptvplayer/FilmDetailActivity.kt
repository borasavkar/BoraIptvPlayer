package com.example.boraiptvplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.boraiptvplayer.database.AppDatabase
import com.example.boraiptvplayer.database.Favorite
import com.example.boraiptvplayer.network.RetrofitClient
import com.example.boraiptvplayer.network.VodInfoData
import kotlinx.coroutines.launch

class FilmDetailActivity : BaseActivity() {

    // UI Bileşenleri
    private lateinit var imgBackdrop: ImageView
    private lateinit var imgPoster: ImageView
    private lateinit var textTitle: TextView
    private lateinit var textRating: TextView
    private lateinit var textGenre: TextView
    private lateinit var textPlot: TextView
    private lateinit var textCast: TextView
    private lateinit var textDirector: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnTrailer: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnFavorite: ImageButton // <-- Favori Butonu
    private lateinit var progressBar: ProgressBar

    // Veriler
    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null
    private var streamId: Int = -1

    // Oynatma için gerekli detaylar
    private var finalStreamExtension: String = "mp4"

    // Favori İşlemleri İçin
    private val db by lazy { AppDatabase.getInstance(this) }
    private var isFav = false
    private var currentFilmName = ""
    private var currentFilmImage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_film_detail)

        initViews()

        if (getIntentData()) {
            fetchFilmDetails()
        } else {
            Toast.makeText(this, "Film verisi eksik", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initViews() {
        imgBackdrop = findViewById(R.id.img_backdrop)
        imgPoster = findViewById(R.id.img_poster)
        textTitle = findViewById(R.id.text_title)
        textRating = findViewById(R.id.text_rating)
        textGenre = findViewById(R.id.text_genre)
        textPlot = findViewById(R.id.text_plot)
        textCast = findViewById(R.id.text_cast)
        textDirector = findViewById(R.id.text_director)

        btnPlay = findViewById(R.id.btn_play)
        btnTrailer = findViewById(R.id.btn_trailer)
        btnBack = findViewById(R.id.btn_back)
        btnFavorite = findViewById(R.id.btn_favorite) // <-- Favori Butonu ID'si (XML'e eklenmeli)

        progressBar = findViewById(R.id.progress_loader)

        // Tıklama Olayları
        btnBack.setOnClickListener { finish() }
        btnPlay.setOnClickListener { playMovie() }

        // Favori Tıklaması
        btnFavorite.setOnClickListener { toggleFavorite() }
    }

    private fun getIntentData(): Boolean {
        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")
        streamId = intent.getIntExtra("EXTRA_STREAM_ID", -1)
        // Listeden gelen varsayılan uzantıyı al (yedek olarak)
        finalStreamExtension = intent.getStringExtra("EXTRA_EXTENSION") ?: "mp4"

        return !(serverUrl.isNullOrEmpty() || username.isNullOrEmpty() || streamId == -1)
    }

    private fun fetchFilmDetails() {
        progressBar.visibility = View.VISIBLE
        val apiService = RetrofitClient.createService(serverUrl!!)

        lifecycleScope.launch {
            try {
                // 1. Filmin detaylarını API'den çek
                val response = apiService.getVodInfo(username!!, password!!, vodId = streamId)

                if (response.isSuccessful && response.body() != null) {
                    val info = response.body()!!.info
                    val movieData = response.body()!!.movieData

                    // Uzantıyı güncelle (Detaydan gelen daha doğrudur)
                    if (movieData?.extension != null) {
                        finalStreamExtension = movieData.extension
                    }

                    // Favori kaydı için bilgileri sakla
                    if (info != null) {
                        currentFilmName = info.name ?: "Film"
                        currentFilmImage = info.image ?: ""
                    }

                    // UI Güncelle
                    updateUI(info)

                    // 2. Veritabanından "Bu film favori mi?" diye kontrol et
                    checkFavoriteStatus()

                } else {
                    Toast.makeText(this@FilmDetailActivity, "Detaylar alınamadı", Toast.LENGTH_SHORT).show()
                }
                progressBar.visibility = View.GONE
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@FilmDetailActivity, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(info: VodInfoData?) {
        if (info == null) return

        textTitle.text = info.name
        textPlot.text = info.plot ?: "Özet bulunamadı."
        textGenre.text = info.genre
        textRating.text = if (info.rating.isNullOrEmpty()) "N/A" else "${info.rating} / 10"
        textCast.text = info.cast ?: "-"
        textDirector.text = info.director ?: "-"

        // Resimler (Glide)
        if (!info.image.isNullOrEmpty()) {
            Glide.with(this).load(info.image).into(imgPoster)
            Glide.with(this).load(info.image).into(imgBackdrop)
        }

        // Trailer Kontrolü
        if (!info.youtubeTrailer.isNullOrEmpty()) {
            btnTrailer.visibility = View.VISIBLE
            btnTrailer.setOnClickListener {
                openTrailer(info.youtubeTrailer)
            }
        }
    }

    // --- FAVORİ MANTIĞI ---
    private fun checkFavoriteStatus() {
        lifecycleScope.launch {
            // Veritabanına sor: Bu ID'li ve 'vod' türündeki içerik favorilerde var mı?
            isFav = db.favoriteDao().isFavorite(streamId, "vod")
            updateFavIcon()
        }
    }

    private fun toggleFavorite() {
        lifecycleScope.launch {
            if (isFav) {
                // Zaten favoriyse, çıkar
                db.favoriteDao().removeFavorite(streamId, "vod")
                isFav = false
                Toast.makeText(this@FilmDetailActivity, "Favorilerden çıkarıldı", Toast.LENGTH_SHORT).show()
            } else {
                // Favori değilse, ekle
                val fav = Favorite(
                    streamId = streamId,
                    streamType = "vod",
                    name = currentFilmName,
                    image = currentFilmImage,
                    categoryId = "0" // Kategori bilgisi detayda gelmez, önemsiz
                )
                db.favoriteDao().addFavorite(fav)
                isFav = true
                Toast.makeText(this@FilmDetailActivity, "Favorilere eklendi", Toast.LENGTH_SHORT).show()
            }
            updateFavIcon()
        }
    }

    private fun updateFavIcon() {
        // Duruma göre ikon değiştir (Dolu kalp veya Boş kalp)
        val icon = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        btnFavorite.setImageResource(icon)
    }

    // --- OYNATMA İŞLEMLERİ ---
    private fun playMovie() {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("EXTRA_SERVER_URL", serverUrl)
            putExtra("EXTRA_USERNAME", username)
            putExtra("EXTRA_PASSWORD", password)
            putExtra("EXTRA_STREAM_ID", streamId)
            putExtra("EXTRA_STREAM_TYPE", "vod")
            putExtra("EXTRA_EXTENSION", finalStreamExtension)
        }
        startActivity(intent)
    }

    private fun openTrailer(trailerId: String) {
        try {
            // YouTube uygulamasında açmayı dene
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$trailerId"))
            startActivity(intent)
        } catch (e: Exception) {
            // Uygulama yoksa tarayıcıda aç
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.youtube.com/watch?v=$trailerId"))
            startActivity(webIntent)
        }
    }
}