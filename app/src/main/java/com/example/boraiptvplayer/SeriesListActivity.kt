package com.example.boraiptvplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.boraiptvplayer.adapter.LiveCategoryAdapter
import com.example.boraiptvplayer.adapter.OnCategoryClickListener
import com.example.boraiptvplayer.adapter.OnSeriesClickListener
import com.example.boraiptvplayer.adapter.SeriesAdapter
import com.example.boraiptvplayer.network.LiveCategory
import com.example.boraiptvplayer.network.RetrofitClient
import com.example.boraiptvplayer.network.SeriesStream
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import java.util.Locale

class SeriesListActivity : BaseActivity(), OnCategoryClickListener, OnSeriesClickListener {

    private lateinit var layoutSearchCategory: TextInputLayout
    private lateinit var inputSearchCategory: TextInputEditText
    private lateinit var layoutSearchSeries: TextInputLayout
    private lateinit var inputSearchSeries: TextInputEditText
    private lateinit var recyclerCategories: RecyclerView
    private lateinit var recyclerSeries: RecyclerView
    private lateinit var progressBar: ProgressBar

    private lateinit var categoryAdapter: LiveCategoryAdapter
    private lateinit var seriesAdapter: SeriesAdapter

    private var allCategories: List<LiveCategory> = emptyList()
    private var allSeries: List<SeriesStream> = emptyList()
    private var selectedCategoryId: String? = null
    private var categorySearchJob: Job? = null
    private var seriesSearchJob: Job? = null

    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_list)

        initViews()
        if (!getIntentData()) {
            Toast.makeText(this, getString(R.string.status_login_error), Toast.LENGTH_SHORT).show()
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
        layoutSearchSeries = findViewById(R.id.layout_search_series)
        inputSearchSeries = findViewById(R.id.input_search_series)
        recyclerCategories = findViewById(R.id.recycler_categories)
        recyclerSeries = findViewById(R.id.recycler_series)
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
        seriesAdapter = SeriesAdapter(this)
        recyclerSeries.adapter = seriesAdapter
    }

    private fun fetchAllData() {
        progressBar.visibility = View.VISIBLE
        val apiService = RetrofitClient.createService(serverUrl!!)

        lifecycleScope.launch {
            try {
                // Dizileri Çek
                val catDef = async { apiService.getSeriesCategories(username!!, password!!) }
                val serDef = async { apiService.getSeries(username!!, password!!) }

                val catRes = catDef.await()
                val serRes = serDef.await()

                if (catRes.isSuccessful && serRes.isSuccessful) {
                    val rawCats = catRes.body() ?: emptyList()
                    // LiveCategory dönüşümü (Güvenlik için)
                    allCategories = rawCats.map { LiveCategory(it.categoryId, it.categoryName, 0) }
                    allSeries = serRes.body() ?: emptyList()

                    // Sayımları Hesapla
                    withContext(Dispatchers.IO) {
                        val counts = HashMap<String, Int>()
                        val allCat = LiveCategory("0", "Tüm Diziler", 0)
                        val mutableCats = mutableListOf(allCat)
                        mutableCats.addAll(allCategories)
                        allCategories = mutableCats

                        counts["0"] = allSeries.size
                        allSeries.forEach { s ->
                            val cId = s.categoryId ?: "0"
                            counts[cId] = (counts[cId] ?: 0) + 1
                        }

                        withContext(Dispatchers.Main) {
                            categoryAdapter.submitList(allCategories)
                            categoryAdapter.setChannelCounts(counts)
                            seriesAdapter.submitList(allSeries)
                        }
                    }
                    progressBar.visibility = View.GONE
                } else {
                    showError(getString(R.string.error_not_found))
                }
            } catch (e: Exception) {
                showError(getString(R.string.status_server_error))
            }
        }
    }

    private fun setupSearchListeners() {
        inputSearchCategory.addTextChangedListener { performCategorySearch(it.toString(), true) }
        layoutSearchCategory.setEndIconOnClickListener { performCategorySearch(inputSearchCategory.text.toString(), false) }
        inputSearchCategory.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { performCategorySearch(inputSearchCategory.text.toString(), false); true } else false }

        inputSearchSeries.addTextChangedListener { performSeriesSearch(it.toString(), true) }
        layoutSearchSeries.setEndIconOnClickListener { performSeriesSearch(inputSearchSeries.text.toString(), false) }
        inputSearchSeries.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { performSeriesSearch(inputSearchSeries.text.toString(), false); true } else false }
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

    private fun performSeriesSearch(txt: String, isAuto: Boolean) {
        seriesSearchJob?.cancel()
        seriesSearchJob = lifecycleScope.launch {
            if (isAuto) delay(500)
            val q = txt.lowercase(Locale("tr"))
            val res = withContext(Dispatchers.IO) {
                if (q.isNotEmpty()) allSeries.filter { it.name?.lowercase(Locale("tr"))?.contains(q) == true }
                else if (selectedCategoryId != null && selectedCategoryId != "0") allSeries.filter { it.categoryId == selectedCategoryId }
                else allSeries
            }
            seriesAdapter.submitList(res)
        }
    }

    override fun onCategoryClick(c: LiveCategory) {
        selectedCategoryId = c.categoryId
        inputSearchSeries.text?.clear()
        lifecycleScope.launch(Dispatchers.IO) {
            val res = if (c.categoryId == "0") allSeries else allSeries.filter { it.categoryId == c.categoryId }
            withContext(Dispatchers.Main) { seriesAdapter.submitList(res) }
        }
    }

    override fun onSeriesClick(s: SeriesStream) {
        val intent = Intent(this, SeriesDetailActivity::class.java).apply {
            putExtra("EXTRA_SERVER_URL", serverUrl)
            putExtra("EXTRA_USERNAME", username)
            putExtra("EXTRA_PASSWORD", password)
            putExtra("EXTRA_SERIES_ID", s.seriesId)
        }
        startActivity(intent)
    }

    private fun showError(msg: String) {
        progressBar.visibility = View.GONE
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}