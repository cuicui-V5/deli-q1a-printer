package com.deli.printer.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.deli.printer.protocol.BitmapRasterizer
import com.deli.printer.protocol.DeliProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Connecting : BleConnectionState()
    data class Connected(val deviceName: String, val macAddress: String) : BleConnectionState()
    data class Printing(val progressPercent: Int, val status: String) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

@SuppressLint("MissingPermission")
class DeliBleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private var negotiatedMtu = 23 // Default ATT MTU

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val scannedDevices: StateFlow<List<BleDeviceItem>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = dev.name ?: "Unknown Device"
            val mac = dev.address ?: ""
            val isDeli = name.contains("Q1", ignoreCase = true) ||
                         name.contains("DELI", ignoreCase = true) ||
                         mac.equals(DeliProtocol.DEFAULT_MAC, ignoreCase = true)

            val item = BleDeviceItem(
                device = dev,
                name = name,
                address = mac,
                rssi = result.rssi,
                isDeli = isDeli
            )

            val currentList = _scannedDevices.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.address.equals(mac, ignoreCase = true) }
            if (existingIndex >= 0) {
                currentList[existingIndex] = item
            } else {
                currentList.add(item)
            }
            // Sort: Deli devices first, then by signal strength (RSSI)
            currentList.sortWith(compareByDescending<BleDeviceItem> { it.isDeli }.thenByDescending { it.rssi })
            _scannedDevices.value = currentList
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _connectionState.value = BleConnectionState.Error("扫描失败 (错误码 $errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                _connectionState.value = BleConnectionState.Connecting
                // Request MTU 247 for high-throughput packet transfers
                gatt.requestMtu(247)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                cleanupGatt()
                _connectionState.value = BleConnectionState.Disconnected
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
            }
            // Discover GATT Services
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(DeliProtocol.SERVICE_UUID)
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(DeliProtocol.WRITE_CHAR_UUID)
                    notifyCharacteristic = service.getCharacteristic(DeliProtocol.NOTIFY_CHAR_UUID)

                    // Enable notification if characteristic exists
                    notifyCharacteristic?.let {
                        gatt.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }

                    val devName = gatt.device.name ?: "Deli Q1A"
                    val devMac = gatt.device.address
                    _connectionState.value = BleConnectionState.Connected(devName, devMac)
                } else {
                    _connectionState.value = BleConnectionState.Error("未找到打印服务 (0000ff00)")
                    disconnect()
                }
            } else {
                _connectionState.value = BleConnectionState.Error("服务发现失败 (状态 $status)")
                disconnect()
            }
        }
    }

    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        _scannedDevices.value = emptyList()
        _isScanning.value = true
        scanner.startScan(scanCallback)

        // Auto-stop scan after 10 seconds
        mainHandler.postDelayed({
            stopScan()
        }, 10000)
    }

    fun stopScan() {
        if (_isScanning.value) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            _isScanning.value = false
        }
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = BleConnectionState.Connecting
        device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun connectMac(macAddress: String) {
        val adapter = bluetoothAdapter ?: return
        try {
            val device = adapter.getRemoteDevice(macAddress)
            connect(device)
        } catch (e: Exception) {
            _connectionState.value = BleConnectionState.Error("无效的蓝牙地址: $macAddress")
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        cleanupGatt()
        _connectionState.value = BleConnectionState.Disconnected
    }

    private fun cleanupGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
    }

    private suspend fun writeRaw(data: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = writeCharacteristic ?: return false

        char.value = data
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return gatt.writeCharacteristic(char)
    }

    private suspend fun writeChunks(data: ByteArray, delayMs: Long = 12L) {
        val chunkSize = (negotiatedMtu - 3).coerceIn(20, 200)
        var offset = 0
        while (offset < data.size) {
            val end = (offset + chunkSize).coerceAtMost(data.size)
            val chunk = data.copyOfRange(offset, end)
            writeRaw(chunk)
            delay(delayMs)
            offset = end
        }
    }

    /**
     * Executes verified continuous print pipeline.
     */
    suspend fun printBitmap(
        bitmap: Bitmap,
        density: Int = 1,
        copies: Int = 1,
        isLandscape: Boolean = true,
        dither: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (_connectionState.value !is BleConnectionState.Connected) {
            _connectionState.value = BleConnectionState.Error("打印机未连接")
            return@withContext false
        }

        try {
            _connectionState.value = BleConnectionState.Printing(10, "正在生成光栅点阵...")

            // Convert to 1-bit monochrome MSB byte array with auto-rotation
            val raster = BitmapRasterizer.convertBitmap(bitmap, autoRotateLandscape = isLandscape, dither = dither)

            // Assemble continuous pipeline (Header + Bitmap + 1D 0C)
            val continuousStream = DeliProtocol.buildContinuousPrintStream(
                raster.bytes,
                raster.widthBytes,
                raster.heightPx
            )

            // Set Heating Density (0=Light, 1=Normal, 2=Dark)
            writeRaw(DeliProtocol.cmdDensity(density))
            delay(150)

            val safeCopies = copies.coerceIn(1, 20)
            for (copy in 1..safeCopies) {
                _connectionState.value = BleConnectionState.Printing(
                    (copy * 100) / safeCopies,
                    "正在打印第 $copy / $safeCopies 份..."
                )

                // 1. Enable printer session
                writeRaw(DeliProtocol.CMD_ENABLE)
                delay(200)

                // 2. Wakeup null bytes
                writeRaw(DeliProtocol.CMD_WAKEUP)
                delay(150)

                // 3. Align to top of label (on 1st copy)
                if (copy == 1) {
                    writeRaw(DeliProtocol.CMD_ALIGN_TOP)
                    delay(400)
                }

                // 4. Stream continuous pipeline
                writeChunks(continuousStream, delayMs = 12L)

                // Wait for thermal burn and gap advancement
                val feedTimeSec = (raster.heightPx / 180f + 0.4f).coerceAtLeast(1.2f)
                delay((feedTimeSec * 1000).toLong())

                // 5. Exit label mode on last copy
                if (copy == safeCopies) {
                    writeRaw(DeliProtocol.CMD_EXIT_LABEL)
                    delay(200)
                }
            }

            // Finish job and park motor
            writeRaw(DeliProtocol.CMD_FINISH_JOB)
            delay(100)

            val connectedDev = (_connectionState.value as? BleConnectionState.Connected)
                ?: BleConnectionState.Connected("Deli Q1A", DeliProtocol.DEFAULT_MAC)
            _connectionState.value = connectedDev
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _connectionState.value = BleConnectionState.Error("打印异常: ${e.message}")
            false
        }
    }
}
