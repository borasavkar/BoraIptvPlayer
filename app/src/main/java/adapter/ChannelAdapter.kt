package com.example.boraiptvplayer.adapter

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.boraiptvplayer.R
import com.example.boraiptvplayer.network.ChannelWithEpg
import java.util.Base64

interface OnChannelClickListener {
    fun onChannelClick(channelWithEpg: ChannelWithEpg)
}

class ChannelAdapter(
    private val listener: OnChannelClickListener,
    private val layoutId: Int = R.layout.item_channel
) : ListAdapter<ChannelWithEpg, ChannelAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channelWithEpg = getItem(position)
        holder.bind(channelWithEpg, listener)
    }

    class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val channelNameText: TextView = itemView.findViewById(R.id.text_channel_name)
        private val channelIcon: ImageView = itemView.findViewById(R.id.image_channel_icon)
        private val epgNowText: TextView? = try { itemView.findViewById(R.id.text_epg_now) } catch (e: Exception) { null }

        fun bind(channelWithEpg: ChannelWithEpg, listener: OnChannelClickListener) {
            val channel = channelWithEpg.channel

            // 1. İsim
            channelNameText.text = channel.name?.trim() ?: "İsimsiz"

            // 2. RESİM AYARLARI (BURASI KRİTİK)
            // EPG Başlığına bakarak türü anlıyoruz (MainActivity'de set etmiştik: Film, Dizi, TV)
            val type = channelWithEpg.epgNow?.title ?: "TV"

            if (type == "Film" || type == "Dizi") {
                // FİLM/DİZİ: Posteri tam doldur (Kesilirse kesilsin, doluluk önemli)
                channelIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                channelIcon.setPadding(0, 0, 0, 0) // Boşluk yok
            } else {
                // CANLI TV: Logoyu sığdır (Asla kesilmesin)
                channelIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                // Logolar kenara yapışmasın diye biraz iç boşluk verelim (16px)
                val padding = 16
                channelIcon.setPadding(padding, padding, padding, padding)
            }

            // Glide ile Resmi Yükle
            Glide.with(itemView.context)
                .load(channel.streamIcon)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(android.R.drawable.ic_menu_gallery)
                .into(channelIcon)

            // 3. EPG Bilgisi
            if (epgNowText != null) {
                val epg = channelWithEpg.epgNow
                if (epg != null && (type != "Film" && type != "Dizi")) {
                    // Sadece Canlı Yayınsa EPG göster, Filmse gösterme
                    val epgTitle = try {
                        val decodedBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Base64.getDecoder().decode(epg.title)
                        } else {
                            android.util.Base64.decode(epg.title, android.util.Base64.DEFAULT)
                        }
                        String(decodedBytes, Charsets.UTF_8)
                    } catch (e: Exception) { epg.title }

                    val timeStr = if (epg.start.length > 12) "${epg.start.substring(8, 10)}:${epg.start.substring(10, 12)}" else ""
                    epgNowText.text = if (timeStr.isNotEmpty()) "$timeStr - $epgTitle" else epgTitle
                    epgNowText.visibility = View.VISIBLE
                } else {
                    epgNowText.visibility = View.GONE
                }
            }

            // 4. Tıklama
            itemView.setOnClickListener { listener.onChannelClick(channelWithEpg) }
        }
    }
}

class ChannelDiffCallback : DiffUtil.ItemCallback<ChannelWithEpg>() {
    override fun areItemsTheSame(oldItem: ChannelWithEpg, newItem: ChannelWithEpg): Boolean {
        return oldItem.channel.streamId == newItem.channel.streamId
    }
    override fun areContentsTheSame(oldItem: ChannelWithEpg, newItem: ChannelWithEpg): Boolean {
        return oldItem == newItem
    }
}