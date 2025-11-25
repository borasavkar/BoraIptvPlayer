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
import com.example.boraiptvplayer.adapter.FilmAdapter
import com.example.boraiptvplayer.adapter.LiveCategoryAdapter
import com.example.boraiptvplayer.adapter.OnCategoryClickListener
import com.example.boraiptvplayer.adapter.OnFilmClickListener
import com.example.boraiptvplayer.network.LiveCategory
import com.example.boraiptvplayer.network.RetrofitClient
import com.example.boraiptvplayer.network.VodStream
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import java.util.Locale

class FilmsActivity : BaseActivity(), OnCategoryClickListener, OnFilmClickListener {

    private lateinit var layoutSearchCategory: TextInputLayout
    private lateinit var inputSearchCategory: TextInputEditText
    private lateinit var layoutSearchFilm: TextInputLayout
    private lateinit var inputSearchFilm: TextInputEditText
    private lateinit var recyclerCategories: RecyclerView
    private lateinit var recyclerFilms: RecyclerView
    private lateinit var progressBar: ProgressBar

    private lateinit var categoryAdapter: LiveCategoryAdapter
    private lateinit var filmAdapter: FilmAdapter

    private var allCategories: List<LiveCategory> = emptyList()
    private var allFilms: List<VodStream> = emptyList()
    private var selectedCategoryId: String? = null
    private var categorySearchJob: Job? = null
    private var filmSearchJob: Job? = null

    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_films)
        initViews()
        if (!getIntentData()) {
            Toast.makeText(this, "Profil hatası", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setupAdapters()
        setupSearchListeners()
        fetchAllData()
    }

    private fun initViews() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        layoutSearchCategory = findViewById(R.id.layout_search_category)
        inputSearchCategory = findViewById(R.id.input_search_category)
        layoutSearchFilm = findViewById(R.id.layout_search_film)
        inputSearchFilm = findViewById(R.id.input_search_film)
        recyclerCategories = findViewById(R.id.recycler_categories)
        recyclerFilms = findViewById(R.id.recycler_films)
        progressBar = findViewById(R.id.progress_bar_loading)
    }

    private fun getIntentData(): Boolean {
        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")
        return !(serverUrl.isNullOrEmpty() || username.isNullOrEmpty() || password.isNullOrEmpty())
    }

    private fun setupAdapters() {
        categoryAdapter = LiveCategoryAdapter(this)
        recyclerCategories.adapter = categoryAdapter
        filmAdapter = FilmAdapter(this)
        recyclerFilms.adapter = filmAdapter
    }

    private fun fetchAllData() {
        progressBar.visibility = View.VISIBLE
        val apiService = RetrofitClient.createService(serverUrl!!)

        lifecycleScope.launch {
            try {
                // SADECE XTREAM KOMUTLARI (Temiz ve Güvenli)
                val catDeferred = async { apiService.getVodCategories(username!!, password!!) }
                val vodDeferred = async { apiService.getVodStreams(username!!, password!!) }

                val catResponse = catDeferred.await()
                val vodResponse = vodDeferred.await()

                if (catResponse.isSuccessful && vodResponse.isSuccessful) {
                    // VodCategory -> LiveCategory dönüşümü (Güvenlik için)
                    val rawCats = catResponse.body() ?: emptyList()
                    allCategories = rawCats.map {
                        LiveCategory(it.categoryId, it.categoryName, 0)
                    }
                    allFilms = vodResponse.body() ?: emptyList()

                    // Sayımları Hesapla
                    withContext(Dispatchers.IO) {
                        val counts = HashMap<String, Int>()
                        val allCat = LiveCategory("0", "Tüm Filmler", 0)
                        val mutableCats = mutableListOf(allCat)
                        mutableCats.addAll(allCategories)
                        allCategories = mutableCats

                        counts["0"] = allFilms.size
                        allFilms.forEach { f ->
                            val cId = f.categoryId ?: "0"
                            counts[cId] = (counts[cId] ?: 0) + 1
                        }

                        withContext(Dispatchers.Main) {
                            categoryAdapter.submitList(allCategories)
                            categoryAdapter.setChannelCounts(counts)
                            filmAdapter.submitList(allFilms)
                        }
                    }
                    progressBar.visibility = View.GONE
                } else {
                    showError("Veri alınamadı.")
                }
            } catch (e: Exception) {
                showError("Sunucu hatası.")
            }
        }
    }

    // --- ARAMA İŞLEMLERİ ---
    private fun setupSearchListeners() {
        inputSearchCategory.addTextChangedListener { performCategorySearch(it.toString(), true) }
        layoutSearchCategory.setEndIconOnClickListener { performCategorySearch(inputSearchCategory.text.toString(), false) }
        inputSearchCategory.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { performCategorySearch(inputSearchCategory.text.toString(), false); true } else false }

        inputSearchFilm.addTextChangedListener { performFilmSearch(it.toString(), true) }
        layoutSearchFilm.setEndIconOnClickListener { performFilmSearch(inputSearchFilm.text.toString(), false) }
        inputSearchFilm.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { performFilmSearch(inputSearchFilm.text.toString(), false); true } else false }
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

    private fun performFilmSearch(txt: String, isAuto: Boolean) {
        filmSearchJob?.cancel()
        filmSearchJob = lifecycleScope.launch {
            if (isAuto) delay(500)
            val q = txt.lowercase(Locale("tr"))
            val res = withContext(Dispatchers.IO) {
                if (q.isNotEmpty()) allFilms.filter { it.name?.lowercase(Locale("tr"))?.contains(q) == true }
                else if (selectedCategoryId != null && selectedCategoryId != "0") allFilms.filter { it.categoryId == selectedCategoryId }
                else allFilms
            }
            filmAdapter.submitList(res)
        }
    }

    // --- TIKLAMALAR ---
    override fun onCategoryClick(c: LiveCategory) {
        selectedCategoryId = c.categoryId
        inputSearchFilm.text?.clear()
        lifecycleScope.launch(Dispatchers.IO) {
            val res = if (c.categoryId == "0") allFilms else allFilms.filter { it.categoryId == c.categoryId }
            withContext(Dispatchers.Main) { filmAdapter.submitList(res) }
        }
    }

    override fun onFilmClick(f: VodStream) {
        // Detay sayfasına git (Sadece Xtream olduğu için güvenli)
        val intent = Intent(this, FilmDetailActivity::class.java).apply {
            putExtra("EXTRA_SERVER_URL", serverUrl)
            putExtra("EXTRA_USERNAME", username)
            putExtra("EXTRA_PASSWORD", password)
            putExtra("EXTRA_STREAM_ID", f.streamId)
            putExtra("EXTRA_STREAM_TYPE", "vod")
            putExtra("EXTRA_EXTENSION", f.fileExtension)
        }
        startActivity(intent)
    }

    private fun showError(msg: String) { progressBar.visibility = View.GONE; Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}