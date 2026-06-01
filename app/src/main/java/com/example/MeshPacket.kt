package com.example

import org.json.JSONObject

data class MeshPacket(
    val senderName: String,
    val destinationId: String,
    val routeType: String, // "LOCAL", "GLOBAL"
    val payloadType: String, // "TEXT", "FILE", "HANDSHAKE", "ACK", "VOICE"
    val encryptedData: String,
    val iv: String,
    val publicKey: String = "",
    val userId: String = "",
    val signature: String = "", // Added signature 
    val selfDestruct: Long = 0L, // time in seconds (e.g. 10), 0 means no self-destruct
    val messageId: String = java.util.UUID.randomUUID().toString() // For ACKs
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("senderName", senderName)
        json.put("destinationId", destinationId)
        json.put("routeType", routeType)
        json.put("payloadType", payloadType)
        json.put("encryptedData", encryptedData)
        json.put("iv", iv)
        json.put("publicKey", publicKey)
        json.put("userId", userId)
        json.put("signature", signature)
        json.put("selfDestruct", selfDestruct)
        json.put("messageId", messageId)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): MeshPacket {
            val json = JSONObject(jsonStr)
            return MeshPacket(
                json.optString("senderName", "Unknown"),
                json.optString("destinationId", ""),
                json.optString("routeType", "LOCAL"),
                json.optString("payloadType", "TEXT"),
                json.optString("encryptedData", ""),
                json.optString("iv", ""),
                json.optString("publicKey", ""),
                json.optString("userId", ""),
                json.optString("signature", ""),
                json.optLong("selfDestruct", 0L),
                json.optString("messageId", "")
            )
        }
    }
}
