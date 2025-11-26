package com.example.boraiptvplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.boraiptvplayer.adapter.ChannelAdapter
import com.example.boraiptvplayer.adapter.OnChannelClickListener
import com.example.boraiptvplayer.database.AppDatabase
import com.example.boraiptvplayer.database.Profile
import com.example.boraiptvplayer.network.ChannelWithEpg
import com.example.boraiptvplayer.network.EpgListing
import com.example.boraiptvplayer.network.LiveStream
import com.example.boraiptvplayer.network.RetrofitClient
import com.example.boraiptvplayer.network.VodStream
import com.example.boraiptvplayer.utils.BillingManager
import com.example.boraiptvplayer.utils.ContentCache
import com.example.boraiptvplayer.utils.M3UParser
import com.example.boraiptvplayer.utils.SettingsManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : BaseActivity(), OnChannelClickListener {

    private val db by lazy { AppDatabase.getInstance(this) }
    private val profileDao by lazy { db.profileDao() }
    private val interactionDao by lazy { db.interactionDao() }
    private val favoriteDao by lazy { db.favoriteDao() }

    private lateinit var billingManager: BillingManager

    // UI
    private lateinit var buttonAddProfile: MaterialButton
    private lateinit var btnGlobalSettings: ImageButton
    private lateinit var btnFavorites: ImageButton
    private lateinit var textStatusProfileName: TextView
    private lateinit var textConnectionStatus: TextView
    private lateinit var textExpirationDate: TextView
    private lateinit var progressBar: ProgressBar

    // Kartlar
    private lateinit var cardTv: View
    private lateinit var cardFilms: View
    private lateinit var cardSeries: View

    // Bölümler
    private lateinit var recyclerFavorites: RecyclerView
    private lateinit var titleFavorites: TextView

    private lateinit var recyclerMatches: RecyclerView
    private lateinit var titleMatches: TextView

    private lateinit var recyclerRecommendations: RecyclerView
    private lateinit var titleRecommendations: TextView

    private lateinit var recyclerLatest: RecyclerView
    private lateinit var titleLatest: TextView

    // Adapters
    private lateinit var favAdapter: ChannelAdapter
    private lateinit var matchAdapter: ChannelAdapter
    private lateinit var recAdapter: ChannelAdapter
    private lateinit var latestAdapter: ChannelAdapter

    private var activeProfile: Profile? = null
    private var allProfiles: List<Profile> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBilling()
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclers()
        setupDashboardCards()
        setupClickListeners()

        observeFavorites()
        observeProfiles()
    }

    private fun observeProfiles() {
        lifecycleScope.launch {
            profileDao.getAllProfiles().collectLatest { profiles ->
                allProfiles = profiles

                if (profiles.isEmpty()) {
                    activeProfile = null
                    ContentCache.clear()
                    clearBottomBar()
                    textStatusProfileName.setText(R.string.text_no_profile)
                    recyclerRecommendations.visibility = View.GONE
                    titleRecommendations.visibility = View.GONE
                    createDemoProfileIfNeeded()
                } else {
                    val savedId = SettingsManager.getSelectedProfileId(this@MainActivity)
                    val isSavedProfileValid = profiles.any { it.id == savedId }

                    if (!isSavedProfileValid) {
                        selectProfile(profiles[0])
                    } else {
                        if (activeProfile == null || activeProfile?.id != savedId) {
                            val targetProfile = profiles.find { it.id == savedId }
                            if (targetProfile != null) {
                                selectProfile(targetProfile)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun selectProfile(profile: Profile) {
        SettingsManager.saveSelectedProfileId(this, profile.id)
        activeProfile = profile
        textStatusProfileName.text = profile.profileName
        loadAllContent(profile)

        if (profile.isM3u || profile.serverUrl == "local_demo") {
            textExpirationDate.setText(R.string.status_unlimited)
            textConnectionStatus.setText(R.string.status_connected)
            textConnectionStatus.setTextColor(getColor(R.color.green_success))
        } else {
            lifecycleScope.launch {
                try {
                    val apiService = RetrofitClient.createService(profile.serverUrl)
                    val response = apiService.authenticate(profile.username, profile.password)
                    if (response.isSuccessful && response.body()?.userInfo?.auth == 1) {
                        val expiry = response.body()?.userInfo?.expiryDate
                        textExpirationDate.text = if (expiry != null) formatTimestamp(expiry) else getString(R.string.status_unlimited)
                        textConnectionStatus.setText(R.string.status_connected)
                        textConnectionStatus.setTextColor(getColor(R.color.green_success))
                    } else {
                        textConnectionStatus.setText(R.string.status_login_error)
                        textConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    }
                } catch (e: Exception) {
                    textConnectionStatus.setText(R.string.status_server_error)
                }
            }
        }
    }

    private fun deleteProfile(profile: Profile) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_delete))
            .setMessage(getString(R.string.msg_confirm_delete))
            .setPositiveButton(getString(R.string.btn_yes)) { _, _ ->
                lifecycleScope.launch {
                    if (activeProfile?.id == profile.id) {
                        ContentCache.clear()
                        activeProfile = null
                        clearBottomBar()
                    }
                    profileDao.deleteProfile(profile)
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun initViews() {
        buttonAddProfile = findViewById(R.id.button_add_profile)
        btnGlobalSettings = findViewById(R.id.btn_global_settings)
        btnFavorites = findViewById(R.id.btn_favorites)
        textStatusProfileName = findViewById(R.id.text_status_profile_name)
        textConnectionStatus = findViewById(R.id.text_connection_status)
        textExpirationDate = findViewById(R.id.text_expiration_date)
        progressBar = findViewById(R.id.home_loader)

        cardTv = findViewById(R.id.card_tv)
        cardFilms = findViewById(R.id.card_films)
        cardSeries = findViewById(R.id.card_series)

        recyclerFavorites = findViewById(R.id.recycler_favorites_home)
        titleFavorites = findViewById(R.id.title_favorites)

        recyclerMatches = findViewById(R.id.recycler_matches)
        titleMatches = findViewById(R.id.title_matches)

        recyclerRecommendations = findViewById(R.id.recycler_recommendations)
        titleRecommendations = findViewById(R.id.title_recommendations)

        recyclerLatest = findViewById(R.id.recycler_latest)
        titleLatest = findViewById(R.id.title_latest)
    }

    private fun setupRecyclers() {
        favAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerFavorites.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerFavorites.adapter = favAdapter

        matchAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerMatches.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerMatches.adapter = matchAdapter

        recAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerRecommendations.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerRecommendations.adapter = recAdapter

        latestAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerLatest.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerLatest.adapter = latestAdapter
    }

    private fun setupBilling() {
        billingManager = BillingManager(this,
            { Log.d("Billing", "OK") },
            { showSubscriptionBlocker() }
        )
        billingManager.startConnection()
    }

    private fun showSubscriptionBlocker() {
        if(!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle("Abonelik")
                .setMessage("Devam etmek için abone olun.")
                .setPositiveButton("Abone Ol") { _,_ -> billingManager.launchPurchaseFlow(this) }
                .setCancelable(false)
                .show()
        }
    }

    private fun setupDashboardCards() {
        cardTv.findViewById<TextView>(R.id.card_title).setText(R.string.title_live)
        cardFilms.findViewById<TextView>(R.id.card_title).setText(R.string.title_movies)
        cardSeries.findViewById<TextView>(R.id.card_title).setText(R.string.title_series)

        val iconTv = cardTv.findViewById<ImageView>(R.id.card_icon)
        val iconFilms = cardFilms.findViewById<ImageView>(R.id.card_icon)
        val iconSeries = cardSeries.findViewById<ImageView>(R.id.card_icon)

        iconTv.setImageResource(R.drawable.ic_neon_live)
        iconFilms.setImageResource(R.drawable.ic_neon_movie_reel)
        iconSeries.setImageResource(R.drawable.ic_neon_series)

        iconTv.imageTintList = null
        iconFilms.imageTintList = null
        iconSeries.imageTintList = null
    }

    private fun setupClickListeners() {
        buttonAddProfile.setOnClickListener { showProfileSelectionDialog() }
        btnGlobalSettings.setOnClickListener { showGlobalSettingsDialog() }
        btnFavorites.setOnClickListener {
            if(activeProfile!=null) {
                val intent = Intent(this, FavoritesActivity::class.java)
                intent.putProfileExtras(activeProfile!!)
                startActivity(intent)
            } else {
                showProfileWarning()
            }
        }
        cardTv.setOnClickListener { openChannelList() }
        cardFilms.setOnClickListener {
            if(activeProfile?.isM3u==true) showM3uAlert() else openFilmList()
        }
        cardSeries.setOnClickListener {
            if(activeProfile?.isM3u==true) showM3uAlert() else openSeriesList()
        }
    }

    private fun showM3uAlert() {
        AlertDialog.Builder(this)
            .setMessage("Film/Dizi sadece Xtream'de çalışır.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showGlobalSettingsDialog() {
        val langOptions = arrayOf("Türkçe", "English", "Deutsch", "Français", "Russian", "Arabic")
        val langCodes = arrayOf("tr", "en", "de", "fr", "ru", "ar")
        val audioCode = SettingsManager.getAudioLang(this)
        val subCode = SettingsManager.getSubtitleLang(this)
        val audioName = langCodes.indexOf(audioCode).let { if(it == -1) "Türkçe" else langOptions[it] }
        val subName = langCodes.indexOf(subCode).let { if(it == -1) "Türkçe" else langOptions[it] }
        val menuItems = arrayOf("${getString(R.string.settings_default_audio)}: $audioName", "${getString(R.string.settings_default_subtitle)}: $subName")
        AlertDialog.Builder(this).setTitle(getString(R.string.settings_title)).setItems(menuItems) { _, which ->
            when (which) {
                0 -> showSelectionDialog(getString(R.string.settings_audio_lang), langOptions) { idx -> SettingsManager.setAudioLang(this, langCodes[idx]) }
                1 -> showSelectionDialog(getString(R.string.settings_subtitle_lang), langOptions) { idx -> SettingsManager.setSubtitleLang(this, langCodes[idx]) }
            }
        }.setPositiveButton(getString(R.string.btn_ok), null).show()
    }

    private fun showSelectionDialog(title: String, items: Array<String>, onSelected: (Int) -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setItems(items) { _, which -> onSelected(which); Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show(); showGlobalSettingsDialog() }.show()
    }

    private fun observeFavorites() {
        lifecycleScope.launch {
            favoriteDao.getAllFavorites().collectLatest { favList ->
                // Favoriler az olduğu için main thread sorun olmaz ama yine de IO iyidir
                val mappedFavs = withContext(Dispatchers.Default) {
                    val safeFavList = favList.filter { isSafeContent(it.name) }
                    safeFavList.map { fav ->
                        val stream = LiveStream(fav.streamId, fav.name, fav.image, fav.categoryId)
                        val typeLabel = when(fav.streamType) { "vod"->"Film"; "series"->"Dizi"; else->"TV" }
                        ChannelWithEpg(stream, EpgListing("0", "0", typeLabel, "", "", ""))
                    }
                }

                if (mappedFavs.isNotEmpty()) {
                    favAdapter.submitList(mappedFavs)
                    titleFavorites.visibility = View.VISIBLE
                    recyclerFavorites.visibility = View.VISIBLE
                } else {
                    titleFavorites.visibility = View.GONE
                    recyclerFavorites.visibility = View.GONE
                    favAdapter.submitList(emptyList())
                }
            }
        }
    }

    private fun createDemoProfileIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (profileDao.getAllProfilesSync().isEmpty()) {
                val demoProfile = Profile(profileName = "Demo TV", serverUrl = "local_demo", username = "", password = "", isM3u = true)
                profileDao.insertProfile(demoProfile)
            }
        }
    }

    // --- KRİTİK OPTİMİZASYON BÖLGESİ ---
    private fun loadAllContent(profile: Profile) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // 1. VERİ İNDİRME (IO Thread)
                val (channels, movies, series, epgList) = withContext(Dispatchers.IO) {
                    var ch: List<LiveStream>
                    var mv: List<VodStream>
                    var sr: List<com.example.boraiptvplayer.network.SeriesStream>
                    var ep: List<EpgListing>?

                    if (ContentCache.hasDataFor(profile.id)) {
                        ch = ContentCache.cachedChannels
                        mv = ContentCache.cachedMovies
                        sr = ContentCache.cachedSeries
                        ep = ContentCache.cachedEpg
                    } else {
                        if (profile.isM3u || profile.serverUrl == "local_demo") {
                            if (profile.serverUrl == "local_demo") {
                                ch = listOf(LiveStream(999, "Test Yayını (BBB)", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/800px-Big_buck_bunny_poster_big.jpg", "0", "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
                            } else {
                                val result = M3UParser.parseM3U(profile.serverUrl)
                                ch = result.second
                            }
                            mv = emptyList()
                            sr = emptyList()
                            ep = null
                        } else {
                            val apiService = RetrofitClient.createService(profile.serverUrl)
                            val chDef = async { apiService.getLiveStreams(profile.username, profile.password) }
                            val movDef = async { apiService.getVodStreams(profile.username, profile.password) }
                            val serDef = async { apiService.getSeries(profile.username, profile.password) }
                            val epgDef = async { apiService.getEpgTable(profile.username, profile.password) }

                            ch = chDef.await().body() ?: emptyList()
                            mv = movDef.await().body() ?: emptyList()
                            sr = serDef.await().body() ?: emptyList()
                            ep = epgDef.await().body()?.listings
                        }
                        ContentCache.update(profile.id, ch, mv, sr, ep)
                    }
                    // Veriyi Paketle ve Döndür
                    Quadruple(ch, mv, sr, ep)
                }

                // 2. AĞIR İŞLEME (DEFAULT Thread - CPU Yoğun İşler)
                // UI'ı dondurmadan hesaplamaları burada yapıyoruz
                val (todayMatches, latestItems, recommendedItems) = withContext(Dispatchers.Default) {

                    val safeChannels = channels.filter { isSafeContent(it.name) }
                    val safeMovies = movies.filter { isSafeContent(it.name) }
                    val safeSeries = series.filter { isSafeContent(it.name) }

                    // --- MAÇLAR ---
                    val matches = if (!epgList.isNullOrEmpty()) {
                        val now = System.currentTimeMillis()
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                        val endOfDay = calendar.timeInMillis

                        safeChannels.mapNotNull { channel ->
                            val channelEpgs = epgList.filter { it.epgId == channel.streamId.toString() }
                            val matchEvent = channelEpgs.find { epg ->
                                val start = parseEpgTime(epg.start) ?: 0L
                                val end = parseEpgTime(epg.end) ?: 0L
                                val isTimeValid = (end > now) && (start < endOfDay)
                                if (!isTimeValid) return@find false
                                val title = epg.title.lowercase(Locale.getDefault())
                                val isMatch = title.contains(" vs ") || title.contains(" - ") || title.contains(" v ") || title.contains("karşılaşması") || title.contains("maçı")
                                val isReplay = title.contains("özet") || title.contains("tekrar") || title.contains("highlight")
                                isMatch && !isReplay
                            }
                            if (matchEvent != null) ChannelWithEpg(channel, matchEvent) else null
                        }.take(20)
                    } else emptyList()

                    // --- SON EKLENENLER ---
                    val newMovies = safeMovies.sortedByDescending { it.streamId }.take(10)
                    val newSeries = safeSeries.sortedByDescending { it.seriesId }.take(5)
                    val latest = mutableListOf<ChannelWithEpg>()
                    newMovies.forEach { m -> latest.add(ChannelWithEpg(LiveStream(m.streamId, m.name, m.streamIcon, m.categoryId), EpgListing("0","0","Film","","",""))) }
                    newSeries.forEach { s -> val img = s.cover ?: s.streamIcon; latest.add(ChannelWithEpg(LiveStream(s.seriesId, s.name, img, s.categoryId), EpgListing("0","0","Dizi","","",""))) }

                    // --- ÖNERİLER ---
                    val recs = mutableListOf<ChannelWithEpg>()
                    val totalWatchTime = interactionDao.getTotalUserWatchTime() ?: 0L

                    if (totalWatchTime > 300) {
                        val dominantType = interactionDao.getDominantStreamType()
                        val topCategory = if (dominantType != null) interactionDao.getTopCategoryForType(dominantType) else emptyList()

                        if (topCategory.isNotEmpty()) {
                            val catId = topCategory[0].categoryId
                            when(dominantType) {
                                "vod" -> safeMovies.filter { it.categoryId == catId }.shuffled().take(15).forEach { m -> recs.add(ChannelWithEpg(LiveStream(m.streamId, m.name, m.streamIcon, m.categoryId), EpgListing("0","0","Film","","",""))) }
                                "series" -> safeSeries.filter { it.categoryId == catId }.shuffled().take(15).forEach { s -> recs.add(ChannelWithEpg(LiveStream(s.seriesId, s.name, s.cover ?: s.streamIcon, s.categoryId), EpgListing("0","0","Dizi","","",""))) }
                                else -> safeChannels.filter { it.categoryId == catId }.shuffled().take(15).forEach { c -> recs.add(ChannelWithEpg(c, null)) }
                            }
                        }
                    }

                    if (recs.isEmpty()) {
                        if (safeChannels.isNotEmpty()) recs.addAll(safeChannels.shuffled().take(5).map { ChannelWithEpg(it, null) })
                        if (safeMovies.isNotEmpty()) recs.addAll(safeMovies.shuffled().take(5).map { ChannelWithEpg(LiveStream(it.streamId, it.name, it.streamIcon, it.categoryId), EpgListing("0","0","Film","","","")) })
                        if (safeSeries.isNotEmpty()) recs.addAll(safeSeries.shuffled().take(5).map { ChannelWithEpg(LiveStream(it.seriesId, it.name, it.cover ?: it.streamIcon, it.categoryId), EpgListing("0","0","Dizi","","","")) })
                        recs.shuffle()
                    }

                    val finalRecs = if (recs.isNotEmpty()) {
                        val combined = combineChannelsAndEpg(recs.map { it.channel }, epgList)
                        combined.mapIndexed { index, item ->
                            if (recs[index].epgNow?.title == "Film" || recs[index].epgNow?.title == "Dizi") item.copy(epgNow = recs[index].epgNow) else item
                        }
                    } else emptyList()

                    Triple(matches, latest, finalRecs)
                }

                // 3. UI GÜNCELLEME (Main Thread)
                // Hesaplanan verileri ekrana bas
                if (todayMatches.isNotEmpty()) {
                    matchAdapter.submitList(todayMatches)
                    titleMatches.visibility = View.VISIBLE
                    recyclerMatches.visibility = View.VISIBLE
                } else {
                    titleMatches.visibility = View.GONE
                    recyclerMatches.visibility = View.GONE
                }

                if (latestItems.isNotEmpty()) {
                    latestAdapter.submitList(latestItems)
                    titleLatest.visibility = View.VISIBLE
                    recyclerLatest.visibility = View.VISIBLE
                } else {
                    titleLatest.visibility = View.GONE
                    recyclerLatest.visibility = View.GONE
                }

                if (recommendedItems.isNotEmpty()) {
                    titleRecommendations.setText(R.string.header_recommendations)
                    recAdapter.submitList(recommendedItems)
                    titleRecommendations.visibility = View.VISIBLE
                    recyclerRecommendations.visibility = View.VISIBLE
                } else {
                    titleRecommendations.visibility = View.GONE
                    recyclerRecommendations.visibility = View.GONE
                }

                progressBar.visibility = View.GONE

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                textConnectionStatus.setText(R.string.text_server_error)
            }
        }
    }

    // Basit bir veri taşıyıcı (Quadruple standart kütüphanede yoktur, elle ekledik veya Pair/Triple kullanabiliriz ama bu daha temiz)
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    // --- YARDIMCI FONKSİYONLAR ---
    private fun isSafeContent(name: String?): Boolean {
        if (name == null) return false
        val lowerName = name.lowercase(Locale.getDefault())
        val blockedKeywords = listOf("18+", "+18", "adult", "xxx", "porn", "sex", "erotic")
        return !blockedKeywords.any { lowerName.contains(it) }
    }

    private fun combineChannelsAndEpg(channels: List<LiveStream>, epgData: List<EpgListing>?): List<ChannelWithEpg> {
        val epgMap = epgData?.groupBy { it.epgId }
        val currentTime = System.currentTimeMillis()
        return channels.map { channel ->
            val channelWithEpg = ChannelWithEpg(channel = channel, epgNow = null)
            val channelEpgs = epgMap?.get(channel.streamId.toString())
            if (!channelEpgs.isNullOrEmpty()) {
                val currentEpg = channelEpgs.find { epg ->
                    val start = parseEpgTime(epg.start)
                    val end = parseEpgTime(epg.end)
                    start != null && end != null && currentTime in start..end
                }
                channelWithEpg.epgNow = currentEpg
            }
            channelWithEpg
        }
    }

    private fun parseEpgTime(timeString: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(timeString)?.time
        } catch (e: Exception) { null }
    }

    override fun onChannelClick(item: ChannelWithEpg) {
        val type = item.epgNow?.title
        val id = item.channel.streamId

        if (type == "Dizi") {
            val intent = Intent(this, SeriesDetailActivity::class.java).apply { putProfileExtras(activeProfile!!); putExtra("EXTRA_SERIES_ID", id) }
            startActivity(intent)
        } else if (type == "Film") {
            val intent = Intent(this, FilmDetailActivity::class.java).apply { putProfileExtras(activeProfile!!); putExtra("EXTRA_STREAM_ID", id) }
            startActivity(intent)
        } else {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putProfileExtras(activeProfile!!)
                putExtra("EXTRA_STREAM_ID", id)
                putExtra("EXTRA_STREAM_TYPE", "live")
                if (item.channel.directSource != null) putExtra("EXTRA_DIRECT_URL", item.channel.directSource)
                putExtra("EXTRA_STREAM_NAME", item.channel.name)
                putExtra("EXTRA_STREAM_ICON", item.channel.streamIcon)
            }
            startActivity(intent)
        }
    }

    private fun Intent.putProfileExtras(profile: Profile) {
        putExtra("EXTRA_SERVER_URL", profile.serverUrl)
        putExtra("EXTRA_USERNAME", profile.username)
        putExtra("EXTRA_PASSWORD", profile.password)
        putExtra("EXTRA_IS_M3U", profile.isM3u)
    }

    private fun showProfileSelectionDialog() { if (allProfiles.isEmpty()) { startActivity(Intent(this, AddProfileActivity::class.java)); return }; val profileNames = allProfiles.map { it.profileName }.toTypedArray(); AlertDialog.Builder(this).setTitle(getString(R.string.title_add_profile)).setItems(profileNames) { dialog, which -> selectProfile(allProfiles[which]); dialog.dismiss() }.setPositiveButton(getString(R.string.btn_add_new)) { dialog, _ -> startActivity(Intent(this, AddProfileActivity::class.java)); dialog.dismiss() }.setNeutralButton(getString(R.string.btn_manage)) { dialog, _ -> showManagementDialog() }.show() }
    private fun showManagementDialog() { val profileNames = allProfiles.map { it.profileName }.toTypedArray(); AlertDialog.Builder(this).setTitle(getString(R.string.btn_manage)).setItems(profileNames) { _, which -> showActionDialog(allProfiles[which]) }.setNegativeButton(getString(R.string.btn_cancel), null).show() }
    private fun showActionDialog(profile: Profile) { val options = arrayOf(getString(R.string.btn_edit), getString(R.string.btn_delete)); AlertDialog.Builder(this).setTitle(profile.profileName).setItems(options) { _, which -> when (which) { 0 -> editProfile(profile); 1 -> deleteProfile(profile) } }.show() }
    private fun editProfile(profile: Profile) { val intent = Intent(this, AddProfileActivity::class.java).apply { putExtra("EXTRA_EDIT_ID", profile.id); putExtra("EXTRA_PROFILE_NAME", profile.profileName); putExtra("EXTRA_USERNAME", profile.username); putExtra("EXTRA_PASSWORD", profile.password); putExtra("EXTRA_SERVER_URL", profile.serverUrl) }; startActivity(intent) }
    private fun clearBottomBar() { textConnectionStatus.setText(R.string.text_not_connected); textConnectionStatus.setTextColor(getColor(android.R.color.darker_gray)); textStatusProfileName.setText(R.string.text_no_profile); textExpirationDate.text = "N/A"; recyclerRecommendations.visibility = View.GONE; titleRecommendations.visibility = View.GONE }
    private fun openChannelList() { activeProfile?.let { val intent = Intent(this, LiveCategoryActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun openFilmList() { activeProfile?.let { val intent = Intent(this, FilmsActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun openSeriesList() { activeProfile?.let { val intent = Intent(this, SeriesListActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun showProfileWarning() { Toast.makeText(this, getString(R.string.msg_select_profile), Toast.LENGTH_SHORT).show(); showProfileSelectionDialog() }
    private fun formatTimestamp(timestamp: String): String { return try { val expiryLong = timestamp.toLong() * 1000; val date = Date(expiryLong); val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("tr")); sdf.timeZone = TimeZone.getDefault(); sdf.format(date) } catch (e: Exception) { "Geçersiz Tarih" } }
}