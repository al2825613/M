package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val STRATEGY = Strategy.P2P_CLUSTER
    private val SERVICE_ID = "com.meshchat.app.SERVICE_ID"
    
    private lateinit var etNickname: EditText
    private lateinit var btnAdvertise: Button
    private lateinit var btnDiscover: Button
    private lateinit var btnShowQr: Button
    private lateinit var btnScanQr: Button
    private lateinit var tvStatusBanner: TextView
    private lateinit var rvDevices: RecyclerView
    private lateinit var rvRecentPeers: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView
    
    // Views
    private lateinit var viewDiscover: android.view.View
    private lateinit var viewGlobal: android.view.View

    private var myNickname: String = "User"
    private var isAdvertising = false
    private var isDiscovering = false
    
    private val discoveredDevices = mutableListOf<Pair<String, String>>()
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var recentPeerAdapter: RecentPeerAdapter

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            // Permissions granted
        } else {
            Toast.makeText(this, "Permissions are required for P2P connections", Toast.LENGTH_LONG).show()
        }
    }

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScannedQr(result.contents)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val crash = MeshStorage.getCrash()
            if (crash != null) {
                MeshStorage.clearCrash()
                tvStatusBanner.text = "LAST CRASH: $crash"
                tvStatusBanner.setTextColor(Color.RED)
            }
        } catch (e: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            MeshStorage.init(this) // just in case
            MeshStorage.saveCrash(android.util.Log.getStackTraceString(throwable))
            oldHandler?.uncaughtException(thread, throwable)
        }
        
        setContentView(R.layout.activity_main)

        // Init Core Managers
        MeshStorage.init(this)
        MeshCrypto.init()
        NetworkManager.init(this)

        etNickname = findViewById(R.id.etNickname)
            btnAdvertise = findViewById(R.id.btnAdvertise)
            btnDiscover = findViewById(R.id.btnDiscover)
            btnShowQr = findViewById(R.id.btnShowQr)
            btnScanQr = findViewById(R.id.btnScanQr)
            tvStatusBanner = findViewById(R.id.tvStatusBanner)
            rvDevices = findViewById(R.id.rvDevices)
            rvRecentPeers = findViewById(R.id.rvRecentPeers)
            bottomNavigation = findViewById(R.id.bottomNavigation)
            viewDiscover = findViewById(R.id.viewDiscover)
            viewGlobal = findViewById(R.id.viewGlobal)

            etNickname.setText(MeshStorage.getNickname())

            deviceAdapter = DeviceAdapter(discoveredDevices) { endpointId ->
                connectToEndpoint(endpointId)
            }
            rvDevices.layoutManager = LinearLayoutManager(this)
            rvDevices.adapter = deviceAdapter

            val recentPeers = MeshStorage.getRecentPeers()
            recentPeerAdapter = RecentPeerAdapter(recentPeers) { endpointId, _ ->
                connectToEndpoint(endpointId)
            }
            rvRecentPeers.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            rvRecentPeers.adapter = recentPeerAdapter

            btnAdvertise.setOnClickListener {
                if (!isAdvertising) {
                    checkPermissionsAndHops { startAdvertising() }
                } else {
                    stopAdvertising()
                }
            }

            btnDiscover.setOnClickListener {
                if (!isDiscovering) {
                    checkPermissionsAndHops { startDiscovery() }
                } else {
                    stopDiscovery()
                }
            }

            btnShowQr.setOnClickListener {
                showQrProfile()
            }

            btnScanQr.setOnClickListener {
                val options = ScanOptions()
                options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                options.setPrompt("Scan Peer's MeshChat Profile UI")
                options.setCameraId(0) // Use specific camera
                options.setBeepEnabled(false)
                qrScanLauncher.launch(options)
            }

            bottomNavigation.setOnItemSelectedListener { item ->
                when(item.itemId) {
                    R.id.nav_discover -> {
                        viewDiscover.visibility = android.view.View.VISIBLE
                        viewGlobal.visibility = android.view.View.GONE
                        true
                    }
                    R.id.nav_chats -> {
                        Toast.makeText(this, "Active Chats coming soon", Toast.LENGTH_SHORT).show()
                        false
                    }
                    R.id.nav_global -> {
                        viewDiscover.visibility = android.view.View.GONE
                        viewGlobal.visibility = android.view.View.VISIBLE
                        // Launch a global room as an example
                        val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                            putExtra("ENDPOINT_ID", "")
                            putExtra("PEER_NAME", "Global World")
                            putExtra("MY_NAME", etNickname.text.toString().ifEmpty { "User" })
                            putExtra("ROUTE", "GLOBAL")
                        }
                        startActivity(intent)
                        true
                    }
                    else -> false
                }
            }

            lifecycleScope.launch {
                NetworkManager.networkState.collect { state ->
                    runOnUiThread { updateStatusBanner() }
                }
            }
            
            checkPermissionsAndHops {}
    }

    private fun showQrProfile() {
        saveNickname()
        try {
            val json = JSONObject()
            val userId = MeshStorage.getUserId()
            json.put("userId", userId)
            json.put("nickname", myNickname)
            json.put("publicKey", MeshCrypto.getMyPublicKeyString())
            
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(json.toString(), BarcodeFormat.QR_CODE, 600, 600)
            
            val iv = ImageView(this).apply {
                setImageBitmap(bitmap)
                setPadding(32, 16, 32, 32)
            }
            
            val tvId = TextView(this).apply {
                text = "ID: ${userId.take(8).uppercase()}"
                gravity = android.view.Gravity.CENTER
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 32, 0, 0)
                setTextColor(Color.WHITE)
            }

            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(tvId)
                addView(iv)
            }
            
            AlertDialog.Builder(this)
                .setTitle("Your Secure Profile")
                .setView(layout)
                .setPositiveButton("Close", null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleScannedQr(contents: String) {
        try {
            val json = JSONObject(contents)
            val peerNick = json.optString("nickname")
            val peerPubKey = json.optString("publicKey")
            val peerId = json.optString("userId")
            
            if (peerNick.isNotEmpty() && peerPubKey.isNotEmpty()) {
                val displayNick = if (peerId.isNotEmpty()) "$peerNick (${peerId.take(4)})" else peerNick
                Toast.makeText(this, "Profile trusted: $displayNick", Toast.LENGTH_SHORT).show()
                // Eagerly launch search or advertise to auto-match this profile
                if (!isDiscovering) {
                    checkPermissionsAndHops { startDiscovery() }
                }
            } else {
                Toast.makeText(this, "Invalid MeshChat QR", Toast.LENGTH_SHORT).show()
            }
        } catch(e: Exception) {
            Toast.makeText(this, "Invalid Format", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusBanner() {
        val isGlobal = NetworkManager.networkState.value == NetworkManager.NetworkState.GLOBAL_ONLINE
        if (isGlobal) {
            tvStatusBanner.text = "Status: Global Mesh Active"
            tvStatusBanner.setTextColor(Color.parseColor("#00F0FF")) // Cyan
        } else {
            tvStatusBanner.text = "Status: Local Offline Mesh"
            tvStatusBanner.setTextColor(Color.parseColor("#9D4EDD")) // Purple
        }
        
        var appStr = ""
        if (isAdvertising) appStr += " | Advertising"
        if (isDiscovering) appStr += " | Discovering"
        tvStatusBanner.text = tvStatusBanner.text.toString() + appStr
    }

    private fun checkPermissionsAndHops(onSuccess: () -> Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onSuccess()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun saveNickname() {
        myNickname = etNickname.text.toString().trim()
        if (myNickname.isEmpty()) myNickname = Build.MODEL
        MeshStorage.saveNickname(myNickname)
    }

    private fun startAdvertising() {
        saveNickname()
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this)
            .startAdvertising(myNickname, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                isAdvertising = true
                btnAdvertise.text = "Stop Advertising"
                updateStatusBanner()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,"Adv Failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun stopAdvertising() {
        Nearby.getConnectionsClient(this).stopAdvertising()
        isAdvertising = false
        btnAdvertise.text = "Start Advertising"
        updateStatusBanner()
    }

    private fun startDiscovery() {
        discoveredDevices.clear()
        deviceAdapter.notifyDataSetChanged()
        
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                isDiscovering = true
                btnDiscover.text = "Stop Discovery"
                updateStatusBanner()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,"Disc Failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun stopDiscovery() {
        Nearby.getConnectionsClient(this).stopDiscovery()
        isDiscovering = false
        btnDiscover.text = "Start Discovery"
        updateStatusBanner()
    }

    private fun connectToEndpoint(endpointId: String) {
        saveNickname()
        Nearby.getConnectionsClient(this).requestConnection(myNickname, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
            }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            runOnUiThread {
                val exists = discoveredDevices.any { it.first == endpointId }
                if (!exists) {
                    discoveredDevices.add(Pair(endpointId, info.endpointName))
                    deviceAdapter.notifyItemInserted(discoveredDevices.size - 1)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            runOnUiThread {
                val index = discoveredDevices.indexOfFirst { it.first == endpointId }
                if (index != -1) {
                    discoveredDevices.removeAt(index)
                    deviceAdapter.notifyItemRemoved(index)
                }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Auto accept
            Nearby.getConnectionsClient(this@MainActivity).acceptConnection(endpointId, PayloadCallbackHelper)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            runOnUiThread {
                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        stopDiscovery()
                        stopAdvertising()
                        val peerName = discoveredDevices.find { it.first == endpointId }?.second ?: "Peer"
                        MeshStorage.saveRecentPeer(endpointId, peerName)
                        
                        val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                            putExtra("ENDPOINT_ID", endpointId)
                            putExtra("PEER_NAME", peerName)
                            putExtra("MY_NAME", myNickname)
                            putExtra("ROUTE", "LOCAL")
                        }
                        startActivity(intent)
                    }
                    else -> {
                        Toast.makeText(this@MainActivity, "Connection Rejected/Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
        }
    }
}

object PayloadCallbackHelper : PayloadCallback() {
    override fun onPayloadReceived(endpointId: String, payload: Payload) {
        ChatActivity.activeInstance?.handleIncomingPayload(payload)
    }

    override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
        ChatActivity.activeInstance?.handlePayloadUpdate(update)
    }
}
