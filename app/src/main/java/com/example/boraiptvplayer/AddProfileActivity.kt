package com.example.boraiptvplayer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.boraiptvplayer.database.AppDatabase
import com.example.boraiptvplayer.database.Profile
import com.example.boraiptvplayer.network.RetrofitClient
import com.example.boraiptvplayer.utils.SettingsManager // <-- Önemli
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class AddProfileActivity : BaseActivity() {

    // XML'deki bileşenler
    private lateinit var layoutProfileName: TextInputLayout
    private lateinit var editTextProfileName: TextInputEditText
    private lateinit var layoutUsername: TextInputLayout
    private lateinit var editTextUsername: TextInputEditText
    private lateinit var layoutPassword: TextInputLayout
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var layoutServerUrl: TextInputLayout
    private lateinit var editTextServerUrl: TextInputEditText
    private lateinit var buttonSave: Button
    private lateinit var progressBar: ProgressBar

    private val db by lazy { AppDatabase.getInstance(this) }
    private val profileDao by lazy { db.profileDao() }

    private var isEditMode = false
    private var editingProfileId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_profile)

        initViews()
        checkEditMode()

        buttonSave.setOnClickListener {
            validateAndSaveProfile()
        }
    }

    private fun initViews() {
        layoutProfileName = findViewById(R.id.layout_profile_name)
        editTextProfileName = findViewById(R.id.edit_text_profile_name)
        layoutUsername = findViewById(R.id.layout_username)
        editTextUsername = findViewById(R.id.edit_text_username)
        layoutPassword = findViewById(R.id.layout_password)
        editTextPassword = findViewById(R.id.edit_text_password)
        layoutServerUrl = findViewById(R.id.layout_server_url)
        editTextServerUrl = findViewById(R.id.edit_text_server_url)
        buttonSave = findViewById(R.id.button_save)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun checkEditMode() {
        if (intent.hasExtra("EXTRA_EDIT_ID")) {
            isEditMode = true
            editingProfileId = intent.getIntExtra("EXTRA_EDIT_ID", 0)

            editTextProfileName.setText(intent.getStringExtra("EXTRA_PROFILE_NAME"))
            editTextUsername.setText(intent.getStringExtra("EXTRA_USERNAME"))
            editTextPassword.setText(intent.getStringExtra("EXTRA_PASSWORD"))
            editTextServerUrl.setText(intent.getStringExtra("EXTRA_SERVER_URL"))

            buttonSave.text = "Profili Güncelle"
            title = "Profili Düzenle"
        }
    }

    private fun validateAndSaveProfile() {
        val serverUrl = editTextServerUrl.text.toString().trim()

        // İsim Baş Harfi Büyütme
        var profileName = editTextProfileName.text.toString().trim()
        if (profileName.isNotEmpty()) {
            profileName = profileName.substring(0, 1).uppercase() + profileName.substring(1)
        }

        val username = editTextUsername.text.toString().trim()
        val password = editTextPassword.text.toString().trim()

        if (!validateInputs(profileName, username, serverUrl)) return

        showLoading(true)

        lifecycleScope.launch {
            try {
                // 1. API Bağlantısını Test Et
                val apiService = RetrofitClient.createService(serverUrl)
                val response = apiService.authenticate(username, password)

                if (response.isSuccessful && response.body()?.userInfo?.auth == 1) {

                    val profileToSave = Profile(
                        id = if (isEditMode) editingProfileId else 0,
                        profileName = profileName,
                        serverUrl = serverUrl,
                        username = username,
                        password = password
                    )

                    if (isEditMode) {
                        profileDao.updateProfile(profileToSave)
                        // Düzenleneni seçili yap
                        SettingsManager.saveSelectedProfileId(this@AddProfileActivity, profileToSave.id)
                        Toast.makeText(this@AddProfileActivity, "Profil güncellendi", Toast.LENGTH_SHORT).show()
                    } else {
                        // YENİ EKLENENİ SEÇİLİ YAP
                        val newId = profileDao.insertProfile(profileToSave)
                        SettingsManager.saveSelectedProfileId(this@AddProfileActivity, newId.toInt())
                        Toast.makeText(this@AddProfileActivity, "Profil kaydedildi", Toast.LENGTH_SHORT).show()
                    }

                    finish()

                } else {
                    Log.w("BoraIPTV_AddProfile", "API hatası: ${response.message()}")
                    showError("Giriş başarısız: Bilgileri kontrol edin.")
                }
            } catch (e: Exception) {
                Log.e("BoraIPTV_AddProfile", "Ağ hatası: ${e.message}", e)
                showError("Ağ hatası: Sunucuya bağlanılamadı.")
            }
        }
    }

    private fun showError(message: String) {
        showLoading(false)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            buttonSave.visibility = View.INVISIBLE
        } else {
            progressBar.visibility = View.GONE
            buttonSave.visibility = View.VISIBLE
        }
    }

    private fun validateInputs(name: String, user: String, url: String): Boolean {
        layoutProfileName.error = null
        layoutUsername.error = null
        layoutServerUrl.error = null

        if (name.isEmpty()) {
            layoutProfileName.error = "Profil adı boş olamaz"
            return false
        }
        if (user.isEmpty()) {
            layoutUsername.error = "Kullanıcı adı boş olamaz"
            return false
        }
        if (url.isEmpty() || !url.startsWith("http")) {
            layoutServerUrl.error = "Geçerli bir URL girin (http://...)"
            return false
        }
        return true
    }
}