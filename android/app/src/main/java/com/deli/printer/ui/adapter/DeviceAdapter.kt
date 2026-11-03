package com.deli.printer.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.deli.printer.R
import com.deli.printer.ble.BleDeviceItem

class DeviceAdapter(
    private val onDeviceClick: (BleDeviceItem) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    private val devices = mutableListOf<BleDeviceItem>()

    fun setDevices(newDevices: List<BleDeviceItem>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ble_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = devices[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = devices.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvMac: TextView = itemView.findViewById(R.id.tvDeviceMac)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvDeviceRssi)
        private val tagDeli: TextView = itemView.findViewById(R.id.tagDeli)

        fun bind(item: BleDeviceItem) {
            tvName.text = item.name
            tvMac.text = item.address
            tvRssi.text = "${item.rssi} dBm"
            tagDeli.visibility = if (item.isDeli) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onDeviceClick(item)
            }
        }
    }
}
