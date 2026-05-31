package com.example

import android.content.Context
import android.content.SharedPreferences

object MeshStorage {
    private const val PREFS_NAME = "MeshChatPrefs"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_RECENT_PEERS = "recent_peers"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveNickname(name: String) {
        prefs.edit().putString(KEY_NICKNAME, name).apply()
    }

    fun getNickname(): String {
        return prefs.getString(KEY_NICKNAME, "User") ?: "User"
    }

    fun saveRecentPeer(endpointId: String, name: String) {
        val peers = prefs.getStringSet(KEY_RECENT_PEERS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val entry = "$endpointId|$name"
        peers.removeAll { it.startsWith("$endpointId|") }
        peers.add(entry)
        prefs.edit().putStringSet(KEY_RECENT_PEERS, peers).apply()
    }

    fun getRecentPeers(): List<Pair<String, String>> {
        val set = prefs.getStringSet(KEY_RECENT_PEERS, emptySet()) ?: emptySet()
        return set.mapNotNull {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        }
    }
}
