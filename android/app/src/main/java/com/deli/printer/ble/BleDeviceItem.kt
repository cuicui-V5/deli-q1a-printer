package com.deli.printer.ble

import android.bluetooth.BluetoothDevice

data class BleDeviceItem(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val isDeli: Boolean
)
