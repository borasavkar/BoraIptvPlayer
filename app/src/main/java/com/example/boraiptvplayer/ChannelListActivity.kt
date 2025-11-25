package com.example.boraiptvplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.boraiptvplayer.adapter.ChannelAdapter
import com.example.boraiptvplayer.adapter.OnChannelClickListener
import com.example.boraiptvplayer.network.ApiService
import com.example.boraiptvplayer.network.ChannelWithEpg
import com.example.boraiptvplayer.network.EpgListing
import com.example.boraiptvplayer.network.LiveStream
import com.example.boraiptvplayer.network.RetrofitClient
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ChannelListActivity : BaseActivity(), OnChannelClickListener {

    private lateinit var recyclerViewChannels: RecyclerView
    private lateinit var progressBar: ProgressBar

    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null
    private var categoryId: String? = null
    private var searchQuery: String? = null

    private var apiService: ApiService? = null
    private lateinit var channelAdapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_list)

        recyclerViewChannels = findViewById(R.id.recycler_view_channels)
        progressBar = findViewById(R.id.channel_list_progress)

        if (!getIntentData()) {
            Toast.makeText(this, "Profil bilgileri yüklenemedi", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        apiService = RetrofitClient.createService(serverUrl!!)
        setupRecyclerView()
        fetchChannelsAndEpg()
    }

    private fun getIntentData(): Boolean {
        try {
            serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
            username = intent.getStringExtra("EXTRA_USERNAME")
            password = intent.getStringExtra("EXTRA_PASSWORD")
            categoryId = intent.getStringExtra("EXTRA_CATEGORY_ID")
            searchQuery = intent.getStringExtra("EXTRA_SEARCH_QUERY")
            return !(serverUrl.isNullOrEmpty() || username.isNullOrEmpty() || password.isNullOrEmpty())
        } catch (e: Exception) {
            Log.e("BoraIPTV_ChannelList", "Intent verileri alınamadı", e)
            return false
        }
    }

    private fun setupRecyclerView() {
        channelAdapter = ChannelAdapter(this)
        recyclerViewChannels.adapter = channelAdapter
    }

    private fun fetchChannelsAndEpg() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val channelsDeferred = async { apiService?.getLiveStreams(username!!, password!!, categoryId = categoryId) }
                val epgDeferred = async { apiService?.getEpgTable(username!!, password!!) }

                val channelsResponse = channelsDeferred.await()
                val epgResponse = epgDeferred.await()

                var channelList: List<LiveStream>? = if (channelsResponse != null && channelsResponse.isSuccessful) {
                    channelsResponse.body()
                } else {
                    showError("Kanal listesi alınamadı. Hata: ${channelsResponse?.code()}")
                    null
                }

                val epgList: List<EpgListing>? = if (epgResponse != null && epgResponse.isSuccessful) {
                    epgResponse.body()?.listings
                } else null

                if (!channelList.isNullOrEmpty() && !searchQuery.isNullOrEmpty()) {
                    channelList = filterChannelList(channelList, searchQuery!!)
                }

                if (!channelList.isNullOrEmpty()) {
                    val combinedList = combineChannelsAndEpg(channelList, epgList)
                    channelAdapter.submitList(combinedList)
                    showLoading(false)
                } else {
                    if (!searchQuery.isNullOrEmpty()) showError("'$searchQuery' için sonuç bulunamadı.")
                    else showError("Bu kategoride hiç kanal bulunamadı.")
                }

            } catch (e: Exception) {
                showError("Sunucuya bağlanılamadı.")
            }
        }
    }

    private fun filterChannelList(channels: List<LiveStream>, query: String): List<LiveStream> {
        val normalizedQuery = query.normalize()
        return channels.filter {
            it.name?.normalize()?.contains(normalizedQuery, ignoreCase = true) == true
        }
    }

    private fun combineChannelsAndEpg(channels: List<LiveStream>, epgData: List<EpgListing>?): List<ChannelWithEpg> {
        val epgMap = epgData?.groupBy { it.epgId }
        val currentTime = System.currentTimeMillis()
        return channels.map { channel ->
            val channelWithEpg = ChannelWithEpg(channel = channel, epgNow = null)
            val channelEpgs = epgMap?.get(channel.streamId.toString())
            if (!channelEpgs.isNullOrEmpty()) {
                val currentEpg = channelEpgs.find { epg ->
                    val startTime = parseEpgTime(epg.start)
                    val endTime = parseEpgTime(epg.end)
                    startTime != null && endTime != null && currentTime in startTime..endTime
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

    private fun String.normalize(): String {
        val original = arrayOf('ı', 'İ', 'ş', 'Ş', 'ğ', 'Ğ', 'ü', 'Ü', 'ö', 'Ö', 'ç', 'Ç')
        val normalized = arrayOf('i', 'i', 's', 's', 'g', 'g', 'u', 'u', 'o', 'o', 'c', 'c')
        var result = this.lowercase(Locale("tr"))
        original.forEachIndexed { index, char -> result = result.replace(char, normalized[index]) }
        return result
    }

    override fun onChannelClick(channelWithEpg: ChannelWithEpg) {
        val channel = channelWithEpg.channel
        if (serverUrl != null && username != null && password != null) {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("EXTRA_SERVER_URL", serverUrl)
                putExtra("EXTRA_USERNAME", username)
                putExtra("EXTRA_PASSWORD", password)
                putExtra("EXTRA_STREAM_ID", channel.streamId)

                // --- EKLENEN SATIRLAR BURASI ---
                putExtra("EXTRA_STREAM_NAME", channel.name)
                putExtra("EXTRA_STREAM_ICON", channel.streamIcon)
            }
            startActivity(intent)
        } else {
            showError("Profil verileri eksik.")
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        recyclerViewChannels.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        showLoading(false)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}