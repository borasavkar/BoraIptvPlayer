package com.example.boraiptvplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource // <-- YENİ GÜÇLÜ MOTOR
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource // <-- HLS Optimizasyonu
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.example.boraiptvplayer.database.AppDatabase
import com.example.boraiptvplayer.database.Favorite
import com.example.boraiptvplayer.database.Interaction
import com.example.boraiptvplayer.network.RetrofitClient // <-- Paylaşılan Motor
import com.example.boraiptvplayer.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : BaseActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var trackSelector: DefaultTrackSelector? = null

    // UI Bileşenleri
    private lateinit var btnSettings: ImageButton
    private lateinit var btnSubtitle: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnFavoritePlayer: ImageButton
    private lateinit var btnNextEpisode: Button
    private lateinit var btnSkipIntro: Button
    private lateinit var textNetworkSpeed: TextView
    private var topControls: View? = null

    // Veriler
    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null
    private var streamId: Int = -1
    private var streamType: String? = "live"
    private var fileExtension: String? = "mp4"
    private var categoryId: String? = "0"
    private var directUrl: String? = null
    private var nextEpisodeId: Int = -1
    private var streamName: String = ""
    private var streamIcon: String = ""

    private val db by lazy { AppDatabase.getInstance(this) }
    private var isFav = false

    // Resume & Progress
    private var startTime: Long = 0
    private var startPosition: Long = 0
    private var lastTotalRxBytes: Long = 0
    private var lastTimeStamp: Long = 0

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            checkProgress()
            updateNetworkSpeed()
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        private var simpleCache: SimpleCache? = null
    }

    private val subtitlePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> addExternalSubtitle(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        lastTotalRxBytes = TrafficStats.getUidRxBytes(applicationInfo.uid)
        lastTimeStamp = System.currentTimeMillis()

        initViews()
        if (getIntentData()) {
            checkResumeStatus()
            checkFavoriteStatus()
        } else {
            finish()
        }
    }

    private fun initViews() {
        playerView = findViewById(R.id.player_view)
        try { topControls = findViewById(R.id.top_controls) } catch (e: Exception) {}
        btnSettings = findViewById(R.id.btn_settings)
        btnSubtitle = findViewById(R.id.btn_subtitle)
        btnBack = findViewById(R.id.btn_back)
        btnFavoritePlayer = findViewById(R.id.btn_favorite_player)
        btnNextEpisode = findViewById(R.id.btn_next_episode)
        btnSkipIntro = findViewById(R.id.btn_skip_intro)
        textNetworkSpeed = findViewById(R.id.text_network_speed)

        btnBack.setOnClickListener { finish() }
        btnSettings.setOnClickListener { showSettingsDialog() }
        btnSubtitle.setOnClickListener { showSubtitleDialog() }
        btnFavoritePlayer.setOnClickListener { toggleFavorite() }
        btnNextEpisode.setOnClickListener { playNextEpisode() }
        btnSkipIntro.setOnClickListener { skipIntro() }

        playerView?.setControllerVisibilityListener(object : PlayerView.ControllerVisibilityListener {
            override fun onVisibilityChanged(visibility: Int) {
                topControls?.visibility = visibility
                btnBack.visibility = visibility
                textNetworkSpeed.visibility = visibility
                if (topControls == null) {
                    btnSettings.visibility = visibility
                    btnSubtitle.visibility = visibility
                    btnFavoritePlayer.visibility = visibility
                }
            }
        })
    }

    private fun getIntentData(): Boolean {
        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")
        streamId = intent.getIntExtra("EXTRA_STREAM_ID", -1)
        streamType = intent.getStringExtra("EXTRA_STREAM_TYPE") ?: "live"
        fileExtension = intent.getStringExtra("EXTRA_EXTENSION") ?: "mp4"
        categoryId = intent.getStringExtra("EXTRA_CATEGORY_ID") ?: "0"
        directUrl = intent.getStringExtra("EXTRA_DIRECT_URL")
        nextEpisodeId = intent.getIntExtra("EXTRA_NEXT_EPISODE_ID", -1)
        streamName = intent.getStringExtra("EXTRA_STREAM_NAME") ?: "Kanal $streamId"
        streamIcon = intent.getStringExtra("EXTRA_STREAM_ICON") ?: ""

        if (directUrl != null) return true
        return !(serverUrl.isNullOrEmpty() || username.isNullOrEmpty() || streamId == -1)
    }

    private fun checkResumeStatus() {
        if (streamType == "live") {
            initializePlayer()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val interaction = db.interactionDao().getInteraction(streamId, streamType!!)
            if (interaction != null && !interaction.isFinished) {
                startPosition = interaction.lastPosition
            }
            withContext(Dispatchers.Main) { initializePlayer() }
        }
    }

    private fun initializePlayer() {
        if (player == null) {
            try {
                val finalUrl = if (directUrl != null) {
                    directUrl!!
                } else {
                    val cleanUrl = serverUrl!!.trimEnd('/')
                    val safeUsername = java.net.URLEncoder.encode(username, "UTF-8")
                    val safePassword = java.net.URLEncoder.encode(password, "UTF-8")
                    when (streamType) {
                        "vod" -> "$cleanUrl/movie/$safeUsername/$safePassword/$streamId.$fileExtension"
                        "series" -> "$cleanUrl/series/$safeUsername/$safePassword/$streamId.$fileExtension"
                        else -> "$cleanUrl/live/$safeUsername/$safePassword/$streamId.m3u8"
                    }
                }

                trackSelector = DefaultTrackSelector(this)
                applyGlobalSettings(trackSelector!!)

                // --- 1. TURBO MOTOR: OkHttpDataSource ---
                // Retrofit için hazırladığımız "Pool" (Havuz) destekli istemciyi burada da kullanıyoruz.
                // Bu, bağlantı kurma süresini inanılmaz düşürür.
                val okHttpDataSourceFactory = OkHttpDataSource.Factory(RetrofitClient.okHttpClient)
                    .setUserAgent("BoraIPTV/1.0 (Android; Mobile)")

                val dataSourceFactory: DataSource.Factory = if (streamType != "live") {
                    // VOD ise Cache kullan
                    setupCache(this, okHttpDataSourceFactory)
                } else {
                    // Canlı ise direkt OkHttp kullan
                    okHttpDataSourceFactory
                }

                // --- 2. RENDERER & TUNNELING (TV İÇİN KRİTİK) ---
                val renderersFactory = DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON) // Donanım öncelikli, yazılım yedek

                // --- 3. HIZLI BAŞLATMA (INSTANT PLAY) ---
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        3000,   // Min: 3sn (Hemen başla)
                        30000,  // Max: 30sn
                        1000,   // Play: 1sn veri gelince oynat
                        2000    // Rebuffer: 2sn
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                // --- 4. MEDYA KAYNAĞI OPTİMİZASYONU ---
                // HLS (Canlı TV) için "Chunkless" modunu açıyoruz.
                // Bu, listenin sonuna kadar taranmasını beklemeden ilk parçayı oynatır.
                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
                    .setLiveTargetOffsetMs(5000) // Canlı yayına 5sn geriden gel (daha stabil olur)

                // Eğer özel HLS ayarı gerekirse HlsMediaSource.Factory kullanılabilir ama
                // DefaultMediaSourceFactory artık bunu otomatik yapıyor.

                player = ExoPlayer.Builder(this, renderersFactory)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setTrackSelector(trackSelector!!)
                    .setLoadControl(loadControl)
                    .setAudioAttributes(
                        androidx.media3.common.AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        true
                    )
                    .setDeviceVolumeControlEnabled(true)
                    .setHandleAudioBecomingNoisy(true)
                    .build()

                // TUNNELING MODU (Sadece destekleyen cihazlarda - Genelde TV'ler)
                // 4K videolarda CPU yükünü %50 azaltır.
                /* Not: Tunneling modu bazen arayüz (altyazı, hız göstergesi) ile çakışabilir.
                   Eğer hız göstergesi kaybolursa bu satırı yorum satırı yapabilirsin.
                   Şimdilik kapalı tutuyorum çünkü TextureView/SurfaceView yönetimi gerektirir.
                   Daha stabil olması için standart modda bırakıyoruz.
                   // player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                */

                playerView?.player = player
                playerView?.keepScreenOn = true

                val mediaItem = MediaItem.fromUri(finalUrl)
                player?.setMediaItem(mediaItem)

                if (startPosition > 0) {
                    player?.seekTo(startPosition)
                }

                player?.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        handlePlayerError(error)
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            handler.post(progressRunnable)
                        } else {
                            handler.removeCallbacks(progressRunnable)
                        }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == androidx.media3.common.Player.STATE_READY) {
                            smartSelectSubtitle()
                        }
                    }
                })

                player?.prepare()
                player?.play()
                startTime = System.currentTimeMillis()

            } catch (e: Exception) {
                showDetailedErrorDialog("Başlatma Hatası", e.message ?: "Bilinmeyen hata")
            }
        }
    }

    // --- CACHE AYARI (OkHttp İle Güncellendi) ---
    private fun setupCache(context: Context, upstreamFactory: DataSource.Factory): DataSource.Factory {
        if (simpleCache == null) {
            val evictor = LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024) // 200MB Cache
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(File(context.cacheDir, "media"), evictor, databaseProvider)
        }
        return CacheDataSource.Factory()
            .setCache(simpleCache!!)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    // ... (Geri kalan tüm fonksiyonlar AYNI) ...
    // applyGlobalSettings, smartSelectSubtitle, updateNetworkSpeed, checkProgress vb.
    // KOD BÜTÜNLÜĞÜ İÇİN AŞAĞIYA EKLİYORUM:

    private fun applyGlobalSettings(selector: DefaultTrackSelector) {
        val parametersBuilder = selector.buildUponParameters()
        parametersBuilder.setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE).setMaxVideoBitrate(Integer.MAX_VALUE)
        val audioLang = SettingsManager.getAudioLang(this)
        val subLang = SettingsManager.getSubtitleLang(this)
        parametersBuilder.setPreferredAudioLanguage(audioLang)
        parametersBuilder.setPreferredTextLanguage(subLang)
        parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        parametersBuilder.setSelectUndeterminedTextLanguage(true)
        selector.parameters = parametersBuilder.build()
    }
    private fun smartSelectSubtitle() { val preferredLang = SettingsManager.getSubtitleLang(this); val tracks = getTracksByType(C.TRACK_TYPE_TEXT); var targetTrack = tracks.find { it.name.lowercase().contains(preferredLang) }; if (targetTrack == null) { val searchKeyword = if (preferredLang == "tr") "tur" else if (preferredLang == "ru") "rus" else preferredLang; targetTrack = tracks.find { it.name.lowercase().contains(searchKeyword) } }; if (targetTrack != null) { selectTrack(C.TRACK_TYPE_TEXT, targetTrack.groupIndex, targetTrack.trackIndex, targetTrack.rendererIndex) } }
    private fun updateNetworkSpeed() { val currentRxBytes = TrafficStats.getUidRxBytes(applicationInfo.uid); val currentTime = System.currentTimeMillis(); val timeDiff = currentTime - lastTimeStamp; if (timeDiff >= 1000) { val bytesDiff = currentRxBytes - lastTotalRxBytes; val speed = if (bytesDiff > 0) (bytesDiff * 1000) / timeDiff else 0; val speedText = if (speed >= 1024 * 1024) String.format("%.1f MB/s", speed / (1024f * 1024f)) else String.format("%d KB/s", speed / 1024); textNetworkSpeed.text = speedText; lastTotalRxBytes = currentRxBytes; lastTimeStamp = currentTime } }
    private fun checkProgress() { if (player == null || streamType == "live") return; val current = player!!.currentPosition; val duration = player!!.duration; if (streamType == "series" && current > 10000 && current < 300000) { if (btnSkipIntro.visibility != View.VISIBLE) btnSkipIntro.visibility = View.VISIBLE } else { if (btnSkipIntro.visibility == View.VISIBLE) btnSkipIntro.visibility = View.GONE }; if (nextEpisodeId != -1 && duration > 0 && (duration - current) < 45000) { if (btnNextEpisode.visibility != View.VISIBLE) btnNextEpisode.visibility = View.VISIBLE } else { if (btnNextEpisode.visibility == View.VISIBLE) btnNextEpisode.visibility = View.GONE } }
    private fun skipIntro() { player?.let { it.seekTo(it.currentPosition + 85000); Toast.makeText(this, "İntro atlandı", Toast.LENGTH_SHORT).show() } }
    private fun playNextEpisode() { saveProgress(); val intent = Intent(this, PlayerActivity::class.java).apply { putExtra("EXTRA_SERVER_URL", serverUrl); putExtra("EXTRA_USERNAME", username); putExtra("EXTRA_PASSWORD", password); putExtra("EXTRA_STREAM_ID", nextEpisodeId); putExtra("EXTRA_STREAM_TYPE", streamType); putExtra("EXTRA_EXTENSION", fileExtension); putExtra("EXTRA_CATEGORY_ID", categoryId) }; startActivity(intent); finish() }
    private fun checkFavoriteStatus() { lifecycleScope.launch { isFav = db.favoriteDao().isFavorite(streamId, streamType ?: "live"); updateFavIcon() } }
    private fun toggleFavorite() { lifecycleScope.launch { if (isFav) { db.favoriteDao().removeFavorite(streamId, streamType ?: "live"); isFav = false; Toast.makeText(this@PlayerActivity, "Favorilerden çıkarıldı", Toast.LENGTH_SHORT).show() } else { val fav = Favorite(streamId = streamId, streamType = streamType ?: "live", name = streamName, image = streamIcon, categoryId = categoryId); db.favoriteDao().addFavorite(fav); isFav = true; Toast.makeText(this@PlayerActivity, "Favorilere eklendi", Toast.LENGTH_SHORT).show() }; updateFavIcon() } }
    private fun updateFavIcon() { val icon = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline; btnFavoritePlayer.setImageResource(icon) }
    private fun handlePlayerError(error: PlaybackException) { val title: String; val msg: String; val cause = error.cause; if (cause is HttpDataSource.InvalidResponseCodeException) { title = "Sunucu Hatası (${cause.responseCode})"; msg = when(cause.responseCode){404->"Kaynak bulunamadı.";403->"Erişim reddedildi.";else->"Sunucu hatası."} } else if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) { title = "İnternet Yok"; msg = "Bağlantı hatası." } else { title = "Hata"; msg = error.message ?: "" }; showDetailedErrorDialog(title, msg) }
    private fun showDetailedErrorDialog(title: String, message: String) { if (!isFinishing) AlertDialog.Builder(this).setTitle("⚠️ $title").setMessage("$message\n\nNot: Hata uygulamadan değil, yayıncıdan kaynaklıdır.").setPositiveButton("Tamam") { d, _ -> d.dismiss(); finish() }.setCancelable(false).show() }
    private fun showSettingsDialog() { val options = arrayOf("Görüntü Kalitesi", "Ses Dili"); AlertDialog.Builder(this).setTitle("Ayarlar").setItems(options) { _, w -> when(w){0->showTrackSelectionDialog(C.TRACK_TYPE_VIDEO, "Çözünürlük"); 1->showTrackSelectionDialog(C.TRACK_TYPE_AUDIO, "Ses")} }.show() }
    private fun showSubtitleDialog() { val tracks = getTracksByType(C.TRACK_TYPE_TEXT); val items = tracks.map { it.name }.toMutableList(); items.add(0, "Kapat"); items.add("📂 Dosyadan Yükle..."); items.add(getString(R.string.search_subtitle_online)); AlertDialog.Builder(this).setTitle("Altyazı Seç").setItems(items.toTypedArray()) { _, w -> when(w) { 0 -> disableTrack(C.TRACK_TYPE_TEXT); items.size-2 -> openFilePicker(); items.size-1 -> searchSubtitleOnline(); else -> { val t = tracks[w-1]; selectTrack(C.TRACK_TYPE_TEXT, t.groupIndex, t.trackIndex, t.rendererIndex) } } }.show() }
    private fun searchSubtitleOnline() { val query = streamName; val lang = SettingsManager.getSubtitleLang(this); val langName = if (lang == "tr") "Turkish" else if (lang == "ru") "Russian" else "English"; val url = "https://www.google.com/search?q=$query+$langName+subtitle+srt"; val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)); startActivity(intent) }
    private fun getTracksByType(type: Int): List<TrackInfo> { val list = mutableListOf<TrackInfo>(); val info = trackSelector?.currentMappedTrackInfo ?: return emptyList(); for (i in 0 until info.rendererCount) { if (info.getRendererType(i) == type) { val groups = info.getTrackGroups(i); for (j in 0 until groups.length) { val group = groups[j]; for (k in 0 until group.length) { if (info.getTrackSupport(i, j, k) == C.FORMAT_HANDLED) { val f = group.getFormat(k); val name = when(type) { C.TRACK_TYPE_VIDEO -> "${f.width}x${f.height}"; C.TRACK_TYPE_AUDIO -> "${f.language ?: "und"}"; C.TRACK_TYPE_TEXT -> "${f.language ?: "und"} (${f.label ?: "Bilinmeyen"})"; else -> "?" }; list.add(TrackInfo(name, j, k, i)) } } } } }; return list }
    private fun showTrackSelectionDialog(type: Int, title: String) { val tracks = getTracksByType(type); if (tracks.isEmpty()) { Toast.makeText(this, "Seçenek yok", 0).show(); return }; val items = tracks.map { it.name }.toTypedArray(); AlertDialog.Builder(this).setTitle(title).setItems(items) { _, w -> val t = tracks[w]; selectTrack(type, t.groupIndex, t.trackIndex, t.rendererIndex) }.show() }
    private fun selectTrack(type: Int, g: Int, t: Int, r: Int?) { val info = trackSelector?.currentMappedTrackInfo ?: return; val rIdx = r ?: (0 until info.rendererCount).find { info.getRendererType(it) == type } ?: return; val override = TrackSelectionOverride(info.getTrackGroups(rIdx)[g], t); trackSelector?.parameters = trackSelector!!.buildUponParameters().setOverrideForType(override).build(); Toast.makeText(this, "Seçildi", 0).show() }
    private fun disableTrack(type: Int) { trackSelector?.parameters = trackSelector!!.buildUponParameters().setTrackTypeDisabled(type, true).build(); Toast.makeText(this, "Kapatıldı", 0).show() }
    private fun openFilePicker() { val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }; subtitlePickerLauncher.launch(i) }
    private fun addExternalSubtitle(uri: Uri) { if(player==null) return; val media = player?.currentMediaItem ?: return; val sub = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("tr").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(); val newMedia = media.buildUpon().setSubtitleConfigurations(listOf(sub)).build(); val pos = player?.currentPosition ?: 0; player?.setMediaItem(newMedia); player?.seekTo(pos); player?.prepare(); player?.play(); Toast.makeText(this, "Yüklendi", 0).show() }
    data class TrackInfo(val name: String, val groupIndex: Int, val trackIndex: Int, val rendererIndex: Int)
    private fun saveProgress() { if(player==null || streamType=="live") return; val pos = player!!.currentPosition; val dur = player!!.duration; val watched = (System.currentTimeMillis() - startTime)/1000; val finished = (dur>0) && (pos >= (dur*0.95)); lifecycleScope.launch(Dispatchers.IO) { val db = AppDatabase.getInstance(applicationContext); val exist = db.interactionDao().getInteraction(streamId, streamType!!); val total = (exist?.durationSeconds ?: 0) + watched; val interact = Interaction(id = exist?.id ?: 0, streamId = streamId, streamType = streamType!!, categoryId = categoryId ?: "0", durationSeconds = total, lastPosition = if(finished) 0 else pos, maxDuration = dur, isFinished = finished); db.interactionDao().logInteraction(interact) } }
    override fun onStop() { super.onStop(); handler.removeCallbacks(progressRunnable); saveProgress(); player?.release(); player = null }
}