package com.example.boraiptvplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesActivity : BaseActivity(), OnChannelClickListener {

    private lateinit var recyclerFavorites: RecyclerView
    private lateinit var adapter: ChannelAdapter
    private val db by lazy { AppDatabase.getInstance(this) }

    // Veriler
    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        recyclerFavorites = findViewById(R.id.recycler_favorites)
        recyclerFavorites.layoutManager = LinearLayoutManager(this)
        adapter = ChannelAdapter(this)
        recyclerFavorites.adapter = adapter

        loadFavorites()
    }

    private fun loadFavorites() {
        lifecycleScope.launch {
            db.favoriteDao().getAllFavorites().collectLatest { favList ->
                // Favorileri ChannelWithEpg formatına çeviriyoruz ki Adapter kullanabilelim
                val list = favList.map { fav ->
                    val stream = LiveStream(
                        streamId = fav.streamId,
                        name = fav.name,
                        streamIcon = fav.image,
                        categoryId = fav.categoryId
                    )
                    // Tür bilgisini EPG başlığına gizliyoruz (Hack)
                    val typeLabel = when(fav.streamType) {
                        "vod" -> "Film"
                        "series" -> "Dizi"
                        else -> "Canlı Yayın"
                    }
                    val dummyEpg = EpgListing("0", "0", typeLabel, "", "", "")
                    ChannelWithEpg(stream, dummyEpg)
                }
                adapter.submitList(list)
            }
        }
    }

    override fun onChannelClick(item: ChannelWithEpg) {
        // Tıklanınca ne olduğunu (Film/Dizi/Canlı) anlayıp oraya git
        val typeLabel = item.epgNow?.title ?: "live"
        val type = when(typeLabel) {
            "Film" -> "vod"
            "Dizi" -> "series"
            else -> "live"
        }

        if (type == "series") {
            val intent = Intent(this, SeriesDetailActivity::class.java).apply {
                putExtra("EXTRA_SERVER_URL", serverUrl)
                putExtra("EXTRA_USERNAME", username)
                putExtra("EXTRA_PASSWORD", password)
                putExtra("EXTRA_SERIES_ID", item.channel.streamId)
            }
            startActivity(intent)
        } else if (type == "vod") {
            val intent = Intent(this, FilmDetailActivity::class.java).apply {
                putExtra("EXTRA_SERVER_URL", serverUrl)
                putExtra("EXTRA_USERNAME", username)
                putExtra("EXTRA_PASSWORD", password)
                putExtra("EXTRA_STREAM_ID", item.channel.streamId)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("EXTRA_SERVER_URL", serverUrl)
                putExtra("EXTRA_USERNAME", username)
                putExtra("EXTRA_PASSWORD", password)
                putExtra("EXTRA_STREAM_ID", item.channel.streamId)
                putExtra("EXTRA_STREAM_TYPE", "live")
            }
            startActivity(intent)
        }
    }
}