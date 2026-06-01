package com.example

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var btnAudio: ImageButton
    private lateinit var switchSecret: MaterialSwitch

    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<MessageModel>()

    private var endpointId: String? = null
    private var peerName: String? = null
    private var myName: String = "Me"
    private var currentRoute = "LOCAL"

    private var peerPublicKey = ""

    private val incomingFilePayloads = mutableMapOf<Long, Payload>()
    private val completedFilePayloads = mutableMapOf<Long, Payload>()

    // Audio streaming settings
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        endpointId = intent.getStringExtra("ENDPOINT_ID")
        peerName = intent.getStringExtra("PEER_NAME") ?: "Global Room"
        myName = intent.getStringExtra("MY_NAME") ?: "Me"
        currentRoute = intent.getStringExtra("ROUTE") ?: "LOCAL"

        tvPeerName = findViewById(R.id.tvPeerName)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        rvMessages = findViewById(R.id.rvMessages)
        btnAttach = findViewById(R.id.btnAttach)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnAudio = findViewById(R.id.btnAudio)
        switchSecret = findViewById(R.id.switchSecret)

        tvPeerName.text = peerName
        if (currentRoute == "GLOBAL") {
            tvPeerName.text = "Global World Chat"
        }

        adapter = MessageAdapter(messages)
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = adapter

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                val selfDestructSecs = if (switchSecret.isChecked) 10L else 0L
                val msgId = java.util.UUID.randomUUID().toString()
                sendEncryptedText(text, selfDestructSecs, msgId)
                etMessage.text.clear()
            }
        }

        btnAudio.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startAudioStream()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopAudioStream()
                    true
                }
                else -> false
            }
        }

        btnAttach.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            filePickerLauncher.launch(intent)
        }

        btnDisconnect.setOnClickListener {
            if (currentRoute == "LOCAL" && endpointId != null) {
                Nearby.getConnectionsClient(this).disconnectFromEndpoint(endpointId!!)
            }
            finish()
        }

        if (currentRoute == "LOCAL" && endpointId != null) {
            val handshake = MeshPacket(
                senderName = myName,
                destinationId = endpointId!!,
                routeType = "LOCAL",
                payloadType = "HANDSHAKE",
                encryptedData = "",
                iv = "",
                publicKey = MeshCrypto.getMyPublicKeyString(),
                userId = MeshStorage.getUserId()
            )
            val p = Payload.fromBytes(handshake.toJson().toByteArray())
            Nearby.getConnectionsClient(this).sendPayload(endpointId!!, p)
        }

        lifecycleScope.launch {
            NetworkManager.incomingMessages.collect { msg ->
                if (msg != null && currentRoute == "GLOBAL") {
                    runOnUiThread { handleIncomingPacketJson(msg) }
                }
            }
        }
    }

    private fun startAudioStream() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Need Audio Permission", Toast.LENGTH_SHORT).show()
            return
        }
        isRecording = true
        btnAudio.setColorFilter(android.graphics.Color.RED)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfigIn, audioFormat, bufferSize)
                audioRecord?.startRecording()
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val validBytes = buffer.copyOfRange(0, read)
                        val b64 = Base64.encodeToString(validBytes, Base64.DEFAULT)
                        val packet = MeshPacket(
                            senderName = myName,
                            destinationId = endpointId ?: "global",
                            routeType = currentRoute,
                            payloadType = "VOICE",
                            encryptedData = b64,
                            iv = "",
                            publicKey = MeshCrypto.getMyPublicKeyString(),
                            userId = MeshStorage.getUserId()
                        )
                        val json = packet.toJson()
                        if (currentRoute == "LOCAL" && endpointId != null) {
                            Nearby.getConnectionsClient(this@ChatActivity).sendPayload(endpointId!!, Payload.fromBytes(json.toByteArray()))
                        } else {
                            NetworkManager.sendGlobalMessage(json)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        }
    }

    private fun stopAudioStream() {
        isRecording = false
        btnAudio.clearColorFilter()
        // We log one voice message locally for UX
        addMessage(MessageModel(text = null, type = MessageModel.TYPE_AUDIO, senderName = myName, timestamp = getTime(), isMine = true, status = MessageModel.STATUS_DELIVERED))
    }

    private fun sendEncryptedText(text: String, selfDestructSecs: Long, msgId: String) {
        if (peerPublicKey.isEmpty() && currentRoute == "LOCAL") {
            Toast.makeText(this, "Wait for secure handshake", Toast.LENGTH_SHORT).show()
        }
        
        var encData = text
        var ivStr = ""
        if (peerPublicKey.isNotEmpty() && currentRoute == "LOCAL") {
            try {
                val encPair = MeshCrypto.encrypt(text, peerPublicKey)
                encData = encPair.first
                ivStr = encPair.second
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val packet = MeshPacket(
            senderName = myName,
            destinationId = endpointId ?: "global",
            routeType = currentRoute,
            payloadType = "TEXT",
            encryptedData = encData,
            iv = ivStr,
            publicKey = MeshCrypto.getMyPublicKeyString(),
            userId = MeshStorage.getUserId(),
            selfDestruct = selfDestructSecs,
            messageId = msgId
        )

        val json = packet.toJson()

        if (currentRoute == "LOCAL" && endpointId != null) {
            Nearby.getConnectionsClient(this).sendPayload(endpointId!!, Payload.fromBytes(json.toByteArray()))
        } else {
            NetworkManager.sendGlobalMessage(json)
        }

        val msgModel = MessageModel(id = msgId, text = text, type = MessageModel.TYPE_TEXT, senderName = myName, timestamp = getTime(), isMine = true, selfDestruct = selfDestructSecs)
        addMessage(msgModel)
        
        if (selfDestructSecs > 0) {
            scheduleDestruction(msgModel, selfDestructSecs)
        }
    }

    private fun handleIncomingPacketJson(json: String) {
        try {
            val packet = MeshPacket.fromJson(json)
            
            if (packet.publicKey == MeshCrypto.getMyPublicKeyString() && currentRoute == "GLOBAL") return

            if (packet.payloadType == "HANDSHAKE") {
                peerPublicKey = packet.publicKey
                Toast.makeText(this, "Secure ECDH Session Established", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (packet.payloadType == "ACK") {
                val msgIndex = messages.indexOfFirst { it.id == packet.messageId }
                if (msgIndex != -1) {
                    runOnUiThread {
                        messages[msgIndex].status = MessageModel.STATUS_DECRYPTED
                        adapter.notifyItemChanged(msgIndex)
                    }
                }
                return
            }
            
            if (packet.payloadType == "VOICE") {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val audioData = Base64.decode(packet.encryptedData, Base64.DEFAULT)
                        if (audioTrack == null) {
                            audioTrack = AudioTrack.Builder()
                                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                                .setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(channelConfigOut).build())
                                .setBufferSizeInBytes(bufferSize)
                                .setTransferMode(AudioTrack.MODE_STREAM)
                                .build()
                            audioTrack?.play()
                        }
                        audioTrack?.write(audioData, 0, audioData.size)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return
            }

            var decrypted = packet.encryptedData
            if (packet.iv.isNotEmpty() && peerPublicKey.isNotEmpty() && currentRoute == "LOCAL") {
                try {
                    decrypted = MeshCrypto.decrypt(packet.encryptedData, packet.iv, peerPublicKey)
                } catch(e: Exception) {
                    e.printStackTrace()
                    decrypted = "[Decryption Failed]"
                }
            }
            
            val displaySenderName = if (packet.userId.isNotEmpty()) {
                "${packet.senderName} (${packet.userId.take(4)})"
            } else {
                packet.senderName
            }
            
            val msgModel = MessageModel(id = packet.messageId, text = decrypted, type = MessageModel.TYPE_TEXT, senderName = displaySenderName, timestamp = getTime(), isMine = false, selfDestruct = packet.selfDestruct, status = MessageModel.STATUS_DECRYPTED)
            addMessage(msgModel)
            
            if (packet.selfDestruct > 0) {
                scheduleDestruction(msgModel, packet.selfDestruct)
            }
            
            if (packet.payloadType == "TEXT") {
                val ackPacket = MeshPacket(
                    senderName = myName,
                    destinationId = endpointId ?: "global",
                    routeType = currentRoute,
                    payloadType = "ACK",
                    encryptedData = "",
                    iv = "",
                    userId = MeshStorage.getUserId(),
                    messageId = packet.messageId
                )
                val ackJson = ackPacket.toJson()
                if (currentRoute == "LOCAL" && endpointId != null) {
                    Nearby.getConnectionsClient(this).sendPayload(endpointId!!, Payload.fromBytes(ackJson.toByteArray()))
                } else {
                    NetworkManager.sendGlobalMessage(ackJson)
                }
            }
        } catch(e: Exception) {
            addMessage(MessageModel(text = "Error parsing packet", type = MessageModel.TYPE_TEXT, senderName = "System", timestamp = getTime(), isMine = false))
        }
    }

    private fun scheduleDestruction(msg: MessageModel, secs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            val idx = messages.indexOfFirst { it.id == msg.id }
            if (idx != -1) {
                messages[idx].isDestroyed = true
                adapter.notifyItemChanged(idx)
            }
        }, secs * 1000)
    }

    fun handleIncomingPayload(payload: Payload) {
        if (payload.type == Payload.Type.BYTES) {
            val bytes = payload.asBytes() ?: return
            runOnUiThread { handleIncomingPacketJson(String(bytes)) }
        } else if (payload.type == Payload.Type.FILE) {
            incomingFilePayloads[payload.id] = payload
        }
    }

    fun handlePayloadUpdate(update: PayloadTransferUpdate) {
        if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
            val payload = incomingFilePayloads.remove(update.payloadId)
            if (payload != null && payload.type == Payload.Type.FILE) {
                completedFilePayloads[update.payloadId] = payload
                val uri = payload.asFile()?.asUri()
                addMessage(MessageModel(fileUri = uri, type = MessageModel.TYPE_FILE, senderName = peerName ?: "Unknown", timestamp = getTime(), isMine = false))
            }
        }
    }

    private fun addMessage(message: MessageModel) {
        runOnUiThread {
            messages.add(message)
            adapter.notifyItemInserted(messages.size - 1)
            rvMessages.scrollToPosition(messages.size - 1)
        }
    }

    private val filePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val pfd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val filePayload = Payload.fromFile(pfd)
                        if (currentRoute == "LOCAL" && endpointId != null) {
                            Nearby.getConnectionsClient(this).sendPayload(endpointId!!, filePayload)
                            addMessage(MessageModel(fileUri = uri, type = MessageModel.TYPE_FILE, senderName = myName, timestamp = getTime(), isMine = true))
                        } else {
                            Toast.makeText(this, "File sending not supported globally yet", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    companion object {
        const val PICK_FILE_REQUEST = 1001
        var activeInstance: ChatActivity? = null
    }

    override fun onResume() {
        super.onResume()
        activeInstance = this
    }

    override fun onPause() {
        super.onPause()
        activeInstance = null
    }
}
