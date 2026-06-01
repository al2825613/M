package com.example

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

object MeshStorage {
    private const val PREFS_NAME = "MeshChatPrefs"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_RECENT_PEERS = "recent_peers"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_PRIVATE_KEY = "private_key"
    private const val KEY_PUBLIC_KEY = "public_key"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_USER_ID)) {
            prefs.edit().putString(KEY_USER_ID, UUID.randomUUID().toString()).apply()
        }
    }

    fun getUserId(): String {
        return prefs.getString(KEY_USER_ID, UUID.randomUUID().toString())!!
    }

    fun saveKeyPair(privateKeyBase64: String, publicKeyBase64: String) {
        prefs.edit()
            .putString(KEY_PRIVATE_KEY, privateKeyBase64)
            .putString(KEY_PUBLIC_KEY, publicKeyBase64)
            .apply()
    }

    fun getKeyPair(): Pair<String, String>? {
        val priv = prefs.getString(KEY_PRIVATE_KEY, null)
        val pub = prefs.getString(KEY_PUBLIC_KEY, null)
        if (priv != null && pub != null) {
            return Pair(priv, pub)
        }
        return null
    }

    fun saveNickname(name: String) {
        prefs.edit().putString(KEY_NICKNAME, name).apply()
    }

    fun saveCrash(trace: String) {
        prefs.edit().putString("last_crash", trace).commit()
    }

    fun getCrash(): String? {
        return prefs.getString("last_crash", null)
    }

    fun clearCrash() {
        prefs.edit().remove("last_crash").apply()
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
