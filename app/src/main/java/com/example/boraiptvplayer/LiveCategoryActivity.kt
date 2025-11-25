package com.example.boraiptvplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.boraiptvplayer.adapter.ChannelAdapter
import com.example.boraiptvplayer.adapter.LiveCategoryAdapter
import com.example.boraiptvplayer.adapter.OnCategoryClickListener
import com.example.boraiptvplayer.adapter.OnChannelClickListener
import com.example.boraiptvplayer.network.ChannelWithEpg
import com.example.boraiptvplayer.network.EpgListing
import com.example.boraiptvplayer.network.LiveCategory
import com.example.boraiptvplayer.network.LiveStream
import com.example.boraiptvplayer.network.RetrofitClient
import com.example.boraiptvplayer.utils.M3UParser
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LiveCategoryActivity : BaseActivity(), OnCategoryClickListener, OnChannelClickListener {

    private lateinit var layoutSearchCategory: TextInputLayout
    private lateinit var inputSearchCategory: TextInputEditText
    private lateinit var layoutSearchChannel: TextInputLayout
    private lateinit var inputSearchChannel: TextInputEditText
    private lateinit var recyclerCategories: RecyclerView
    private lateinit var recyclerChannels: RecyclerView
    private lateinit var progressBar: ProgressBar

    private lateinit var categoryAdapter: LiveCategoryAdapter
    private lateinit var channelAdapter: ChannelAdapter

    private var allCategories: List<LiveCategory> = emptyList()
    private var allChannels: List<LiveStream> = emptyList()
    private var allEpgs: List<EpgListing> = emptyList()

    private var selectedCategoryId: String? = null // Seçili kategori ID'si
    private var categorySearchJob: Job? = null
    private var channelSearchJob: Job? = null

    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null
    private var isM3u: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_category)

        // 1. DURUMU GERİ YÜKLE (Ekran döndüyse)
        if (savedInstanceState != null) {
            selectedCategoryId = savedInstanceState.getString("STATE_CATEGORY_ID")
        }

        initViews()
        if (!getIntentData()) {
            Toast.makeText(this, "Hata: Profil bilgisi eksik", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setupAdapters()
        setupSearchListeners()
        fetchAllData()
    }

    // 2. DURUMU KAYDET (Ekran dönmeden hemen önce)
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("STATE_CATEGORY_ID", selectedCategoryId)
    }

    private fun initViews() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        layoutSearchCategory = findViewById(R.id.layout_search_category)
        inputSearchCategory = findViewById(R.id.input_search_category)
        layoutSearchChannel = findViewById(R.id.layout_search_channel)
        inputSearchChannel = findViewById(R.id.input_search_channel)
        recyclerCategories = findViewById(R.id.recycler_categories)
        recyclerChannels = findViewById(R.id.recycler_channels)
        progressBar = findViewById(R.id.progress_bar_loading)
    }

    private fun getIntentData(): Boolean {
        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")
        isM3u = intent.getBooleanExtra("EXTRA_IS_M3U", false)
        return !serverUrl.isNullOrEmpty()
    }

    private fun setupAdapters() {
        categoryAdapter = LiveCategoryAdapter(this)
        recyclerCategories.adapter = categoryAdapter
        channelAdapter = ChannelAdapter(this)
        recyclerChannels.adapter = channelAdapter
    }

    private fun fetchAllData() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                if (isM3u) {
                    val result = withContext(Dispatchers.IO) { M3UParser.parseM3U(serverUrl!!) }
                    allCategories = result.first
                    allChannels = result.second
                    allEpgs = emptyList()
                } else {
                    val apiService = RetrofitClient.createService(serverUrl!!)
                    val catDef = async { apiService.getLiveCategories(username!!, password!!) }
                    val chanDef = async { apiService.getLiveStreams(username!!, password!!) }
                    val epgDef = async { apiService.getEpgTable(username!!, password!!) }

                    val catRes = catDef.await()
                    val chanRes = chanDef.await()
                    val epgRes = epgDef.await()

                    if (catRes.isSuccessful && chanRes.isSuccessful) {
                        allCategories = catRes.body() ?: emptyList()
                        allChannels = chanRes.body() ?: emptyList()
                        allEpgs = epgRes.body()?.listings ?: emptyList()
                    }
                }

                withContext(Dispatchers.IO) {
                    val counts = HashMap<String, Int>()
                    if (!isM3u) {
                        val allCat = LiveCategory("0", "Tüm Kanallar", 0)
                        val mutableCats = mutableListOf(allCat)
                        mutableCats.addAll(allCategories)
                        allCategories = mutableCats
                    }
                    counts["0"] = allChannels.size
                    allChannels.forEach { ch ->
                        val catId = ch.categoryId ?: "0"
                        counts[catId] = (counts[catId] ?: 0) + 1
                    }

                    withContext(Dispatchers.Main) {
                        categoryAdapter.submitList(allCategories)
                        categoryAdapter.setChannelCounts(counts)

                        // 3. FİLTREYİ UYGULA (Eğer hafızada seçili kategori varsa onu göster)
                        if (selectedCategoryId != null && selectedCategoryId != "0") {
                            val filtered = allChannels.filter { it.categoryId == selectedCategoryId }
                            updateChannelList(filtered)
                        } else {
                            updateChannelList(allChannels)
                        }
                    }
                }
                progressBar.visibility = View.GONE
            } catch (e: Exception) {
                showError("Hata: ${e.message}")
            }
        }
    }

    private fun setupSearchListeners() {
        inputSearchCategory.addTextChangedListener { performCategorySearch(it.toString(), true) }
        layoutSearchCategory.setEndIconOnClickListener { performCategorySearch(inputSearchCategory.text.toString(), false) }
        inputSearchCategory.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { performCategorySearch(inputSearchCategory.text.toString(), false); true } else false }

        inputSearchChannel.addTextChangedListener { performChannelSearch(it.toString(), true) }
        layoutSearchChannel.setEndIconOnClickListener { performChannelSearch(inputSearchChannel.text.toString(), false) }
        inputSearchChannel.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { performChannelSearch(inputSearchChannel.text.toString(), false); true } else false }
    }

    private fun performCategorySearch(txt: String, isAuto: Boolean) {
        categorySearchJob?.cancel()
        categorySearchJob = lifecycleScope.launch {
            if (isAuto) delay(300)
            val q = txt.lowercase(Locale("tr"))
            val res = withContext(Dispatchers.IO) { if (q.isEmpty()) allCategories else allCategories.filter { it.categoryName.lowercase(Locale("tr")).contains(q) } }
            categoryAdapter.submitList(res)
        }
    }

    private fun performChannelSearch(txt: String, isAuto: Boolean) {
        channelSearchJob?.cancel()
        channelSearchJob = lifecycleScope.launch {
            if (isAuto) delay(500)
            val q = txt.lowercase(Locale("tr"))
            val res = withContext(Dispatchers.IO) {
                if (q.isNotEmpty()) allChannels.filter { it.name?.lowercase(Locale("tr"))?.contains(q) == true }
                else if (selectedCategoryId != null && selectedCategoryId != "0") allChannels.filter { it.categoryId == selectedCategoryId }
                else allChannels
            }
            updateChannelList(res)
        }
    }

    override fun onCategoryClick(c: LiveCategory) {
        selectedCategoryId = c.categoryId
        inputSearchChannel.text?.clear()
        lifecycleScope.launch(Dispatchers.IO) {
            val res = if (c.categoryId == "0") allChannels else allChannels.filter { it.categoryId == c.categoryId }
            withContext(Dispatchers.Main) { updateChannelList(res) }
        }
    }

    override fun onChannelClick(ch: ChannelWithEpg) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("EXTRA_SERVER_URL", serverUrl)
            putExtra("EXTRA_USERNAME", username)
            putExtra("EXTRA_PASSWORD", password)
            putExtra("EXTRA_STREAM_ID", ch.channel.streamId)
            putExtra("EXTRA_STREAM_TYPE", "live")
            putExtra("EXTRA_CATEGORY_ID", ch.channel.categoryId)

            putExtra("EXTRA_STREAM_NAME", ch.channel.name)
            putExtra("EXTRA_STREAM_ICON", ch.channel.streamIcon)

            if (isM3u && ch.channel.directSource != null) {
                putExtra("EXTRA_DIRECT_URL", ch.channel.directSource)
            }
        })
    }

    private fun updateChannelList(list: List<LiveStream>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val combined = combineChannelsAndEpg(list, allEpgs)
            withContext(Dispatchers.Main) { channelAdapter.submitList(combined) }
        }
    }

    private fun combineChannelsAndEpg(channels: List<LiveStream>, epgList: List<EpgListing>): List<ChannelWithEpg> {
        val epgMap = epgList.groupBy { it.epgId }
        val now = System.currentTimeMillis()
        return channels.map { ch ->
            val chEpg = epgMap[ch.streamId.toString()]?.find { e ->
                val s = parseEpgTime(e.start); val en = parseEpgTime(e.end)
                s != null && en != null && now in s..en
            }
            ChannelWithEpg(ch, chEpg)
        }
    }

    private fun parseEpgTime(t: String): Long? = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(t)?.time
    } catch (e: Exception) { null }

    private fun showError(msg: String) { progressBar.visibility = View.GONE; Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}