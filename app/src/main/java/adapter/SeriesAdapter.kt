package com.example.boraiptvplayer.adapter

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
import com.example.boraiptvplayer.network.SeriesStream //

interface OnSeriesClickListener {
    fun onSeriesClick(series: SeriesStream)
}

// Hata mesajında bahsettiğin satır burası
class SeriesAdapter(private val listener: OnSeriesClickListener) :
    ListAdapter<SeriesStream, SeriesAdapter.SeriesViewHolder>(SeriesDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false) //
        return SeriesViewHolder(view)
    }

    override fun onBindViewHolder(holder: SeriesViewHolder, position: Int) {
        val series = getItem(position)
        holder.bind(series, listener)
    }

    class SeriesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.text_channel_name)
        private val icon: ImageView = itemView.findViewById(R.id.image_channel_icon)
        private val epgText: TextView = itemView.findViewById(R.id.text_epg_now)

        fun bind(series: SeriesStream, listener: OnSeriesClickListener) {
            nameText.text = series.name ?: "İsimsiz Dizi"
            epgText.visibility = View.GONE // Dizilerde EPG olmaz, gizle

            // Dizi Afişi (Glide)
            val imageUrl = series.cover ?: series.streamIcon //

            Glide.with(itemView.context)
                .load(imageUrl) //
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(icon) //

            itemView.setOnClickListener {
                listener.onSeriesClick(series)
            }
        }
    }
}

// DiffUtil
class SeriesDiffCallback : DiffUtil.ItemCallback<SeriesStream>() {
    override fun areItemsTheSame(oldItem: SeriesStream, newItem: SeriesStream): Boolean {
        return oldItem.seriesId == newItem.seriesId //
    }

    override fun areContentsTheSame(oldItem: SeriesStream, newItem: SeriesStream): Boolean {
        // Hatanın burada 'newItem' olarak düzeltildiğinden emin oluyoruz
        return oldItem == newItem //
    }
}