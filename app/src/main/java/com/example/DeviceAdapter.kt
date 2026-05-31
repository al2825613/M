package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val devices: List<Pair<String, String>>, // Pair<EndpointId, EndpointName>
    private val onConnectClick: (String) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device, onConnectClick)
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val btnConnect: Button = itemView.findViewById(R.id.btnConnect)

        fun bind(device: Pair<String, String>, onConnectClick: (String) -> Unit) {
            tvDeviceName.text = device.second
            btnConnect.setOnClickListener {
                onConnectClick(device.first)
            }
        }
    }
}
