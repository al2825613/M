package com.example

import android.net.Uri

data class MessageModel(
    val text: String? = null,
    val fileUri: Uri? = null,
    val type: Int, // 0 for TEXT, 1 for FILE
    val senderName: String,
    val timestamp: String,
    val isMine: Boolean
) {
    companion object {
        const val TYPE_TEXT = 0
        const val TYPE_FILE = 1
    }
}
