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
import com.example.boraiptvplayer.network.VodStream //

interface OnFilmClickListener {
    fun onFilmClick(film: VodStream)
}

// Hata mesajında bahsettiğin satır burası
class FilmAdapter(private val listener: OnFilmClickListener) :
    ListAdapter<VodStream, FilmAdapter.FilmViewHolder>(FilmDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false) //
        return FilmViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilmViewHolder, position: Int) {
        val film = getItem(position)
        holder.bind(film, listener)
    }

    class FilmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.text_channel_name)
        private val icon: ImageView = itemView.findViewById(R.id.image_channel_icon)
        private val epgText: TextView = itemView.findViewById(R.id.text_epg_now)

        fun bind(film: VodStream, listener: OnFilmClickListener) {
            nameText.text = film.name ?: "İsimsiz Film"
            epgText.visibility = View.GONE // Filmlerde EPG olmaz, gizle

            // Film Afişi (Glide)
            Glide.with(itemView.context)
                .load(film.streamIcon) //
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(icon) //

            itemView.setOnClickListener {
                listener.onFilmClick(film)
            }
        }
    }
}

// DiffUtil
class FilmDiffCallback : DiffUtil.ItemCallback<VodStream>() {
    override fun areItemsTheSame(oldItem: VodStream, newItem: VodStream): Boolean {
        return oldItem.streamId == newItem.streamId //
    }

    override fun areContentsTheSame(oldItem: VodStream, newItem: VodStream): Boolean {
        // Hatanın burada 'newItem' olarak düzeltildiğinden emin oluyoruz
        return oldItem == newItem //
    }
}