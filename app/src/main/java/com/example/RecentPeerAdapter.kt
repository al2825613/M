package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecentPeerAdapter(
    private val peers: List<Pair<String, String>>,
    private val onClick: (String, String) -> Unit
) : RecyclerView.Adapter<RecentPeerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvRecentName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_peer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val peer = peers[position]
        holder.tvName.text = peer.second
        holder.itemView.setOnClickListener { onClick(peer.first, peer.second) }
    }

    override fun getItemCount() = peers.size
}
