package com.example.boraiptvplayer.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit // <-- Bu kütüphane sayesinde 'edit { }' kullanabiliyoruz

object SettingsManager {
    private const val PREF_NAME = "BoraPlayerSettings"

    // Anahtarlar
    private const val KEY_DATA_SAVER = "data_saver"
    private const val KEY_AUDIO_LANG = "audio_lang"
    private const val KEY_SUBTITLE_LANG = "subtitle_lang"
    private const val KEY_SELECTED_PROFILE_ID = "selected_profile_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // --- PROFİL HAFIZASI ---
    fun saveSelectedProfileId(context: Context, id: Int) {
        getPrefs(context).edit {
            putInt(KEY_SELECTED_PROFILE_ID, id)
        }
    }

    fun getSelectedProfileId(context: Context): Int {
        return getPrefs(context).getInt(KEY_SELECTED_PROFILE_ID, -1)
    }

    // --- VERİ TASARRUFU (DATA SAVER) ---
    // DÜZELTİLDİ: KTX formatına çevrildi
    fun setDataSaver(context: Context, enabled: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_DATA_SAVER, enabled)
        }
    }

    fun getDataSaver(context: Context): Boolean = getPrefs(context).getBoolean(KEY_DATA_SAVER, true)

    // --- DİL AYARLARI ---
    fun setAudioLang(context: Context, langCode: String) {
        getPrefs(context).edit {
            putString(KEY_AUDIO_LANG, langCode)
        }
    }

    fun setSubtitleLang(context: Context, langCode: String) {
        getPrefs(context).edit {
            putString(KEY_SUBTITLE_LANG, langCode)
        }
    }

    fun getAudioLang(context: Context): String = getPrefs(context).getString(KEY_AUDIO_LANG, "tr") ?: "tr"
    fun getSubtitleLang(context: Context): String = getPrefs(context).getString(KEY_SUBTITLE_LANG, "tr") ?: "tr"
}