package com.example.boraiptvplayer.utils

import com.example.boraiptvplayer.network.LiveCategory
import com.example.boraiptvplayer.network.LiveStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader

object M3UParser {

    private val client = OkHttpClient()

    // M3U Linkini indirip kategorilere ve kanallara ayırır
    fun parseM3U(url: String): Pair<List<LiveCategory>, List<LiveStream>> {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return Pair(emptyList(), emptyList())

            val content = response.body?.string() ?: return Pair(emptyList(), emptyList())

            val channels = mutableListOf<LiveStream>()
            val categories = mutableSetOf<String>()
            val categoryList = mutableListOf<LiveCategory>()

            val reader = BufferedReader(StringReader(content))
            var line = reader.readLine()

            var currentName: String? = null
            var currentLogo: String? = null
            var currentGroup: String? = "Genel"

            while (line != null) {
                line = line.trim()

                if (line.startsWith("#EXTINF")) {
                    // Örnek: #EXTINF:-1 tvg-logo="url" group-title="Spor", Kanal Adı

                    // Kanal Adını Al (Virgülden sonrası)
                    val nameIndex = line.lastIndexOf(',')
                    if (nameIndex != -1) {
                        currentName = line.substring(nameIndex + 1).trim()
                    }

                    // Logoyu Al (Regex ile tvg-logo="..." bul)
                    val logoMatch = Regex("tvg-logo=\"([^\"]*)\"").find(line)
                    if (logoMatch != null && logoMatch.groupValues.size > 1) {
                        currentLogo = logoMatch.groupValues[1]
                    } else {
                        currentLogo = ""
                    }

                    // Grubu (Kategoriyi) Al (Regex ile group-title="..." bul)
                    val groupMatch = Regex("group-title=\"([^\"]*)\"").find(line)
                    if (groupMatch != null && groupMatch.groupValues.size > 1) {
                        currentGroup = groupMatch.groupValues[1]
                    } else {
                        currentGroup = "Genel"
                    }

                    categories.add(currentGroup!!)
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    // Burası URL satırıdır
                    if (currentName != null) {
                        val channel = LiveStream(
                            streamId = line.hashCode(),
                            name = currentName,
                            streamIcon = currentLogo,
                            categoryId = currentGroup,
                            // YENİ: Satırı (URL'yi) direkt buraya kaydediyoruz
                            directSource = line
                        )
                        channels.add(channel)
                    }
                    // Sıfırla
                    currentName = null
                    currentLogo = null
                    currentGroup = "Genel"
                }
                line = reader.readLine()
            }

            // Kategorileri Listeye Çevir (Hepsi için ID = Kategori Adı)
            // "Tüm Kanallar" kategorisini en başa ekleyelim
            categoryList.add(LiveCategory("0", "Tüm Kanallar", 0))

            categories.sorted().forEach { catName ->
                categoryList.add(LiveCategory(catName, catName, 0))
            }

            return Pair(categoryList, channels)

        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(emptyList(), emptyList())
        }
    }
}