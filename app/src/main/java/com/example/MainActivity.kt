package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

class MainActivity : AppCompatActivity() {

    private val STRATEGY = Strategy.P2P_CLUSTER
    private val SERVICE_ID = "com.meshchat.app.SERVICE_ID"
    
    private lateinit var etNickname: EditText
    private lateinit var btnAdvertise: Button
    private lateinit var btnDiscover: Button
    private lateinit var tvState: TextView
    private lateinit var rvDevices: RecyclerView
    
    private var myNickname: String = "User"
    private var isAdvertising = false
    private var isDiscovering = false
    
    private val discoveredDevices = mutableListOf<Pair<String, String>>()
    private lateinit var deviceAdapter: DeviceAdapter

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            // Permissions granted
        } else {
            Toast.makeText(this, "Permissions are required for P2P connections", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etNickname = findViewById(R.id.etNickname)
        btnAdvertise = findViewById(R.id.btnAdvertise)
        btnDiscover = findViewById(R.id.btnDiscover)
        tvState = findViewById(R.id.tvState)
        rvDevices = findViewById(R.id.rvDevices)

        deviceAdapter = DeviceAdapter(discoveredDevices) { endpointId ->
            connectToEndpoint(endpointId)
        }
        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = deviceAdapter

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
    }

    private fun checkPermissionsAndHops(onSuccess: () -> Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
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

    private fun getNickname(): String {
        val name = etNickname.text.toString().trim()
        return if (name.isNotEmpty()) name else Build.MODEL
    }

    private fun startAdvertising() {
        myNickname = getNickname()
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this)
            .startAdvertising(myNickname, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                isAdvertising = true
                tvState.text = "Advertising as $myNickname..."
                btnAdvertise.text = "Stop Advertising"
            }
            .addOnFailureListener { e ->
                tvState.text = "Advertising Failed: \${e.message}"
            }
    }

    private fun stopAdvertising() {
        Nearby.getConnectionsClient(this).stopAdvertising()
        isAdvertising = false
        tvState.text = getString(R.string.offline)
        btnAdvertise.text = "Start Advertising"
    }

    private fun startDiscovery() {
        discoveredDevices.clear()
        deviceAdapter.notifyDataSetChanged()
        
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                isDiscovering = true
                tvState.text = getString(R.string.searching)
                btnDiscover.text = "Stop Discovery"
            }
            .addOnFailureListener { e ->
                tvState.text = "Discovery Failed: \${e.message}"
            }
    }

    private fun stopDiscovery() {
        Nearby.getConnectionsClient(this).stopDiscovery()
        isDiscovering = false
        tvState.text = getString(R.string.offline)
        btnDiscover.text = "Start Discovery"
    }

    private fun connectToEndpoint(endpointId: String) {
        myNickname = getNickname()
        Nearby.getConnectionsClient(this).requestConnection(myNickname, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                tvState.text = "Requesting connection..."
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Connection request failed", Toast.LENGTH_SHORT).show()
            }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            discoveredDevices.add(Pair(endpointId, info.endpointName))
            deviceAdapter.notifyItemInserted(discoveredDevices.size - 1)
        }

        override fun onEndpointLost(endpointId: String) {
            val index = discoveredDevices.indexOfFirst { it.first == endpointId }
            if (index != -1) {
                discoveredDevices.removeAt(index)
                deviceAdapter.notifyItemRemoved(index)
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Automatically accept connection for P2P mesh logic requirement
            Nearby.getConnectionsClient(this@MainActivity).acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    tvState.text = getString(R.string.connected)
                    stopDiscovery()
                    stopAdvertising()
                    // Launch ChatActivity
                    val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                        putExtra("ENDPOINT_ID", endpointId)
                        // Assuming peerName is available from cached discovery list, or simply call it Peer
                        val peerName = discoveredDevices.find { it.first == endpointId }?.second ?: "Connected Peer"
                        putExtra("PEER_NAME", peerName)
                        putExtra("MY_NAME", myNickname)
                    }
                    startActivity(intent)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    tvState.text = "Connection Rejected"
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    tvState.text = "Connection Error"
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            tvState.text = getString(R.string.offline)
            Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            ChatActivity.receivePayload(payload)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            ChatActivity.receivePayloadUpdate(update)
        }
    }
}
