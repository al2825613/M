package com.example

import android.net.Uri

data class MessageModel(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String? = null,
    val fileUri: Uri? = null,
    val type: Int, // 0 for TEXT, 1 for FILE, 2 for AUDIO
    val senderName: String,
    val timestamp: String,
    val isMine: Boolean,
    var status: Int = STATUS_SENDING, // 0=Sending, 1=Delivered, 2=Decrypted
    val selfDestruct: Long = 0L,
    var isDestroyed: Boolean = false // Set to true after destruction
) {
    companion object {
        const val TYPE_TEXT = 0
        const val TYPE_FILE = 1
        const val TYPE_AUDIO = 2
        
        const val STATUS_SENDING = 0
        const val STATUS_DELIVERED = 1
        const val STATUS_DECRYPTED = 2
    }
}
