package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var tvPeerName: TextView
    private lateinit var btnDisconnect: ImageButton
    private lateinit var rvMessages: RecyclerView
    private lateinit var btnAttach: ImageButton
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton

    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<MessageModel>()

    private var endpointId: String? = null
    private var peerName: String? = null
    private var myName: String = "Me"

    private val incomingFilePayloads = mutableMapOf<Long, Payload>()
    private val completedFilePayloads = mutableMapOf<Long, Payload>()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpoint: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val text = payload.asBytes()?.let { String(it) } ?: ""
                addMessage(MessageModel(text = text, type = MessageModel.TYPE_TEXT, senderName = peerName ?: "Unknown", timestamp = getTime(), isMine = false))
            } else if (payload.type == Payload.Type.FILE) {
                incomingFilePayloads[payload.id] = payload
            }
        }

        override fun onPayloadTransferUpdate(endpoint: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payloadId = update.payloadId
                val payload = incomingFilePayloads.remove(payloadId)
                if (payload != null && payload.type == Payload.Type.FILE) {
                    completedFilePayloads[payloadId] = payload
                    val uri = payload.asFile()?.asUri()
                    addMessage(MessageModel(fileUri = uri, type = MessageModel.TYPE_FILE, senderName = peerName ?: "Unknown", timestamp = getTime(), isMine = false))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        endpointId = intent.getStringExtra("ENDPOINT_ID")
        peerName = intent.getStringExtra("PEER_NAME")
        myName = intent.getStringExtra("MY_NAME") ?: "Me"

        tvPeerName = findViewById(R.id.tvPeerName)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        rvMessages = findViewById(R.id.rvMessages)
        btnAttach = findViewById(R.id.btnAttach)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        tvPeerName.text = peerName ?: "Unknown Peer"

        adapter = MessageAdapter(messages)
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = adapter

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty() && endpointId != null) {
                sendText(text)
                etMessage.text.clear()
            }
        }

        btnAttach.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, PICK_FILE_REQUEST)
        }

        btnDisconnect.setOnClickListener {
            endpointId?.let { Nearby.getConnectionsClient(this).disconnectFromEndpoint(it) }
            finish()
        }
    }

    private fun sendText(text: String) {
        val bytesPayload = Payload.fromBytes(text.toByteArray())
        Nearby.getConnectionsClient(this).sendPayload(endpointId!!, bytesPayload)
        addMessage(MessageModel(text = text, type = MessageModel.TYPE_TEXT, senderName = myName, timestamp = getTime(), isMine = true))
    }

    private fun addMessage(message: MessageModel) {
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val pfd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val filePayload = Payload.fromFile(pfd)
                        endpointId?.let { Nearby.getConnectionsClient(this).sendPayload(it, filePayload) }
                        addMessage(MessageModel(fileUri = uri, type = MessageModel.TYPE_FILE, senderName = myName, timestamp = getTime(), isMine = true))
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Here we ideally need to attach payloadCallback to the existing connection manager
        // But Nearby Connections lifecycle might have it in MainActivity.
        // For simplicity in this split, ChatActivity relies on MainActivity passing it, or we use a singleton.
        // To be safe as a monolithic file block requested by user:
        // We will just let MainActivity handle connection events and forward them via a static variable or intent, but a singleton manager is better.
        // Wait, the prompt says Registering PayloadCallback to handle incoming data stream in ChatActivity.
        // This implies MainActivity accepts the connection, then somehow we attach the payload callback here?
        // Actually, Nearby Connections acceptConnection requires PayloadCallback right away. 
        // We will update MainActivity to set ChatActivity's callback statically, or we can just use a static var as a quick hack.
    }

    private fun getTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    companion object {
        const val PICK_FILE_REQUEST = 1001
        var activeChatInstance: ChatActivity? = null
        
        fun receivePayload(payload: Payload) {
            activeChatInstance?.payloadCallback?.onPayloadReceived("", payload)
        }
        
        fun receivePayloadUpdate(update: PayloadTransferUpdate) {
            activeChatInstance?.payloadCallback?.onPayloadTransferUpdate("", update)
        }
    }

    override fun onResume() {
        super.onResume()
        activeChatInstance = this
    }

    override fun onPause() {
        super.onPause()
        activeChatInstance = null
    }
}
