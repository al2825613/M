package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

object NetworkManager {
    enum class NetworkState { LOCAL_ONLY, GLOBAL_ONLINE }

    private val _networkState = MutableStateFlow(NetworkState.LOCAL_ONLY)
    val networkState: StateFlow<NetworkState> = _networkState

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    private const val GLOBAL_RELAY_URL = "wss://echo.websocket.events" // Echo test relay for P2P sim

    private val _incomingMessages = MutableStateFlow<String?>(null)
    val incomingMessages: StateFlow<String?> = _incomingMessages

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        try {
            val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _networkState.value = NetworkState.GLOBAL_ONLINE
                    connectWebSocket()
                }
                override fun onLost(network: Network) {
                    _networkState.value = NetworkState.LOCAL_ONLY
                    webSocket?.cancel()
                    webSocket = null
                }
            })

            if (BuildHasInternet(connectivityManager)) {
                _networkState.value = NetworkState.GLOBAL_ONLINE
                connectWebSocket()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun BuildHasInternet(cm: ConnectivityManager): Boolean {
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun connectWebSocket() {
        if (webSocket != null) return
        val request = Request.Builder().url(GLOBAL_RELAY_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                _incomingMessages.value = text
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@NetworkManager.webSocket = null
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                this@NetworkManager.webSocket = null
            }
        })
    }

    fun sendGlobalMessage(jsonPacket: String) {
        webSocket?.send(jsonPacket)
    }
}
