package com.example.boraiptvplayer.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.boraiptvplayer.R
import com.example.boraiptvplayer.database.Interaction
import com.example.boraiptvplayer.network.Episode

interface OnEpisodeClickListener {
    fun onEpisodeClick(episode: Episode)
}

class EpisodeAdapter(private val listener: OnEpisodeClickListener) :
    ListAdapter<Episode, EpisodeAdapter.EpisodeViewHolder>(EpisodeDiffCallback()) {

    // Hangi bölümlerin izlendiğini tutan liste (StreamID -> Interaction)
    private var watchedMap: Map<Int, Interaction> = emptyMap()

    fun updateWatchedStatus(interactions: List<Interaction>) {
        // StreamID'ye göre haritala
        watchedMap = interactions.associateBy { it.streamId }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position), listener, watchedMap)
    }

    class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.text_channel_name)
        private val infoText: TextView = itemView.findViewById(R.id.text_epg_now)

        fun bind(episode: Episode, listener: OnEpisodeClickListener, watchedMap: Map<Int, Interaction>) {
            nameText.text = episode.title ?: "Bölüm"

            // Stream ID'yi Int'e çevirip kontrol et
            val streamId = episode.id.toIntOrNull() ?: 0
            val interaction = watchedMap[streamId]

            if (interaction != null) {
                if (interaction.isFinished) {
                    infoText.text = "✔ İZLENDİ"
                    infoText.setTextColor(Color.GREEN)
                } else if (interaction.lastPosition > 0) {
                    // Dakikaya çevir
                    val min = interaction.lastPosition / 60000
                    infoText.text = "⏸ $min. dakikada kaldın"
                    infoText.setTextColor(Color.CYAN)
                } else {
                    infoText.text = ""
                }
                infoText.visibility = View.VISIBLE
            } else {
                infoText.visibility = View.GONE
            }

            itemView.setOnClickListener { listener.onEpisodeClick(episode) }
        }
    }
}

class EpisodeDiffCallback : DiffUtil.ItemCallback<Episode>() {
    override fun areItemsTheSame(oldItem: Episode, newItem: Episode): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Episode, newItem: Episode): Boolean = oldItem == newItem
}