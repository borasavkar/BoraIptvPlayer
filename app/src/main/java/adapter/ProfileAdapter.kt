package com.example.boraiptvplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.boraiptvplayer.R
import com.example.boraiptvplayer.database.Profile

// Normal Tıklama (Zaten vardı)
interface OnProfileClickListener {
    fun onProfileClick(profile: Profile)
}

// YENİ: Uzun Tıklama Arayüzü
interface OnProfileLongClickListener {
    fun onProfileLongClick(profile: Profile)
}

// Adaptörümüz artık iki listener alacak
class ProfileAdapter(
    private val clickListener: OnProfileClickListener,
    private val longClickListener: OnProfileLongClickListener // <-- YENİ EKLENDİ
) : ListAdapter<Profile, ProfileAdapter.ProfileViewHolder>(ProfileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false) // item_profile.xml'i kullan
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = getItem(position)
        // Bind fonksiyonuna iki listener'ı da yolluyoruz
        holder.bind(profile, clickListener, longClickListener) // <-- GÜNCELLENDİ
    }

    // ViewHolder
    class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileNameText: TextView = itemView.findViewById(R.id.text_profile_name)

        fun bind(
            profile: Profile,
            clickListener: OnProfileClickListener,
            longClickListener: OnProfileLongClickListener // <-- YENİ EKLENDİ
        ) {
            profileNameText.text = profile.profileName

            // Normal Tıklama (Aynı)
            itemView.setOnClickListener {
                clickListener.onProfileClick(profile)
            }

            // YENİ: Uzun Tıklama Olayı
            itemView.setOnLongClickListener {
                longClickListener.onProfileLongClick(profile)
                true // Olayı tükettiğimizi (handled) belirtir
            }
        }
    }
}

// DiffCallback (Aynı)
class ProfileDiffCallback : DiffUtil.ItemCallback<Profile>() {
    override fun areItemsTheSame(oldItem: Profile, newItem: Profile): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Profile, newItem: Profile): Boolean {
        return oldItem == newItem
    }
}