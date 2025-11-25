package com.example.boraiptvplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.boraiptvplayer.R
import com.example.boraiptvplayer.network.LiveCategory

interface OnCategoryClickListener {
    fun onCategoryClick(category: LiveCategory)
}

class LiveCategoryAdapter(private val listener: OnCategoryClickListener) :
    ListAdapter<LiveCategory, LiveCategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    // Kategori ID'sine göre kanal sayısını tutan harita
    private var channelCounts: Map<String, Int> = emptyMap()

    // Bu fonksiyonu Activity'den çağırıp sayıları yükleyeceğiz
    fun setChannelCounts(counts: Map<String, Int>) {
        this.channelCounts = counts
        notifyDataSetChanged() // Listeyi yenile
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        // YENİ TASARIMI KULLAN
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_row, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = getItem(position)
        // Sayıyı haritadan bul, yoksa 0 yaz
        val count = channelCounts[category.categoryId] ?: 0
        holder.bind(category, count, listener)
    }

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.text_category_name)
        private val countText: TextView = itemView.findViewById(R.id.text_count)

        fun bind(category: LiveCategory, count: Int, listener: OnCategoryClickListener) {
            nameText.text = category.categoryName
            countText.text = "($count)"

            itemView.setOnClickListener {
                listener.onCategoryClick(category)
            }
        }
    }
}

class CategoryDiffCallback : DiffUtil.ItemCallback<LiveCategory>() {
    override fun areItemsTheSame(oldItem: LiveCategory, newItem: LiveCategory): Boolean {
        return oldItem.categoryId == newItem.categoryId
    }

    override fun areContentsTheSame(oldItem: LiveCategory, newItem: LiveCategory): Boolean {
        return oldItem == newItem
    }
}