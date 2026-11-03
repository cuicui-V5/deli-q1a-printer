# 得力 Q1A 打印机 Android App 移植与开发指南 (Android Porting Guide)

本文档专为将本项目移植到 Android 平台（开发原生 APK 替代官方“得力趣贴贴”）编写，提供完整的权限配置、BLE 连接、点阵二值化以及 Kotlin 代码实现。

---

## 1. AndroidManifest.xml 权限配置

在 `app/src/main/AndroidManifest.xml` 中添加 BLE 蓝牙权限：

```xml
<!-- 蓝牙基础功能 -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />

<!-- Android 12 (API 31+) 现代蓝牙权限 -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Android 11 及更低版本兼容权限 -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
```

---

## 2. 核心协议常量与指令定义 (Kotlin)

```kotlin
package com.example.deliprinter.core

import java.util.UUID

object DeliProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")

    const val WIDTH_PX = 120      // 15mm 耗材满幅: 15 字节 / 行 (120 点)
    const val HEIGHT_PX = 240     // 30mm 耗材长度: 240 行点阵 (30mm @ 8 dots/mm)
    const val MAX_WIDTH_PX = 120  // 热敏打印头最大点数 (15mm 满幅)

    // 核心控制指令
    val CMD_ENABLE     = byteArrayOf(0x10.toByte(), 0xFF.toByte(), 0xF1.toByte(), 0x03.toByte())
    val CMD_WAKEUP     = ByteArray(12) { 0 }
    val CMD_ALIGN_TOP  = byteArrayOf(0x1F.toByte(), 0x11.toByte(), 0x51.toByte()) // 顶端回抽
    val CMD_FEED_GAP   = byteArrayOf(0x1D.toByte(), 0x0C.toByte())               // 走纸到撕纸刀
    val CMD_EXIT_LABEL = byteArrayOf(0x1F.toByte(), 0x11.toByte(), 0x50.toByte())
    val CMD_FINISH_JOB = byteArrayOf(0x10.toByte(), 0xFF.toByte(), 0xF1.toByte(), 0x45.toByte())

    fun cmdDensity(density: Int): ByteArray {
        val d = density.coerceIn(0, 2).toByte()
        return byteArrayOf(0x10.toByte(), 0xFF.toByte(), 0x10.toByte(), 0x00.toByte(), d)
    }

    fun buildRasterHeader(widthBytes: Int, heightPx: Int): ByteArray {
        return byteArrayOf(
            0x1D.toByte(), 0x76.toByte(), 0x30.toByte(), 0x00.toByte(),
            (widthBytes and 0xFF).toByte(),
            ((widthBytes shr 8) and 0xFF).toByte(),
            (heightPx and 0xFF).toByte(),
            ((heightPx shr 8) and 0xFF).toByte()
        )
    }
}
```

---

## 3. Android Bitmap 转 1-Bit 单色点阵 (Kotlin)

得力 Q1A 打印头在 15mm 纸张下对应每行 120 个点（15 字节），最高位（MSB）在左，1 为黑色（发热燃烧），0 为白色背景：

```kotlin
package com.example.deliprinter.core

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix

object ImageUtils {

    /**
     * 将 Android Bitmap 转换并旋转为打印机所需的 120 宽度的 1-Bit 字节数组 (15x30mm 满幅)
     */
    fun convertBitmapToRasterBytes(source: Bitmap): ByteArray {
        // 如果是横向设计的标签 (例如 240 宽 x 120 高)，旋转 90 度为纵向 (120 宽 x 240 高)
        var bitmap = source
        if (bitmap.width > bitmap.height) {
            val matrix = Matrix().apply { postRotate(90f) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        // 确保宽度与打印纸满幅匹配 (15mm 纸为 120 像素)
        if (bitmap.width > DeliProtocol.MAX_WIDTH_PX) {
            val scale = DeliProtocol.MAX_WIDTH_PX.toFloat() / bitmap.width
            val targetHeight = (bitmap.height * scale).toInt()
            bitmap = Bitmap.createScaledBitmap(bitmap, DeliProtocol.MAX_WIDTH_PX, targetHeight, true)
        }

        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8
        val output = ByteArray(widthBytes * height)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (xb in 0 until widthBytes) {
                var byteVal = 0
                for (bit in 0..7) {
                    val px = xb * 8 + bit
                    if (px < width) {
                        val color = pixels[y * width + px]
                        // 经典加权灰度转换
                        val r = Color.red(color)
                        val g = Color.green(color)
                        val b = Color.blue(color)
                        val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                        
                        // 灰度低于阈值判定为黑点 (1 = 加热)
                        if (gray < 128) {
                            byteVal = byteVal or (1 shl (7 - bit))
                        }
                    }
                }
                output[y * widthBytes + xb] = byteVal.toByte()
            }
        }
        return output
    }
}
```

---

## 4. 完整的 Android 打印通信引擎 (Kotlin 协程实现)

```kotlin
package com.example.deliprinter.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCancellableCoroutine

class DeliPrinterClient(private val context: Context, private val device: BluetoothDevice) {

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    @SuppressLint("MissingPermission")
    suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // 连接成功后，务必请求最大 MTU (协商 240B)
                    g.requestMtu(240)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (cont.isActive) cont.resume(false)
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                // MTU 协商完成后开始发现服务
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = g.getService(DeliProtocol.SERVICE_UUID)
                    writeChar = service?.getCharacteristic(DeliProtocol.WRITE_CHAR_UUID)
                    if (writeChar != null) {
                        writeChar?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        if (cont.isActive) cont.resume(true)
                    } else {
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("未找到 FF02 打印特征值"))
                    }
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    suspend fun writeChunks(data: ByteArray, chunkSize: Int = 200, delayMs: Long = 12) {
        val char = writeChar ?: throw IllegalStateException("未连接")
        var offset = 0
        while (offset < data.size) {
            val length = minOf(chunkSize, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + length)
            char.value = chunk
            gatt?.writeCharacteristic(char)
            offset += length
            delay(delayMs)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun print(bitmap: Bitmap, density: Int = 1) {
        val char = writeChar ?: throw IllegalStateException("未连接")

        // 1. 设置浓度 (0=浅, 1=标准, 2=浓)
        char.value = DeliProtocol.cmdDensity(density)
        gatt?.writeCharacteristic(char)
        delay(150)

        // 2. 启用打印会话
        char.value = DeliProtocol.CMD_ENABLE
        gatt?.writeCharacteristic(char)
        delay(200)

        // 3. 唤醒 MCU
        char.value = DeliProtocol.CMD_WAKEUP
        gatt?.writeCharacteristic(char)
        delay(150)

        // 4. 顶端回抽对齐 (只在第一份标签时执行)
        char.value = DeliProtocol.CMD_ALIGN_TOP
        gatt?.writeCharacteristic(char)
        delay(400) // 等待回抽机械动作稳定

        // 5. 组合连续数据流 (光栅头 + 点阵数据 + 走纸指令 1D 0C)
        val rasterBytes = ImageUtils.convertBitmapToRasterBytes(bitmap)
        val header = DeliProtocol.buildRasterHeader(DeliProtocol.WIDTH_PX / 8, bitmap.height)
        
        // 🚀 核心优化：直接拼入 1D 0C，实现电机一气呵成不卡顿！
        val continuousStream = header + rasterBytes + DeliProtocol.CMD_FEED_GAP
        writeChunks(continuousStream, chunkSize = 200, delayMs = 12)

        // 6. 等待出纸与间隙传感器平稳停刀
        delay(1400)

        // 7. 退出标签模式并结束作业
        char.value = DeliProtocol.CMD_EXIT_LABEL
        gatt?.writeCharacteristic(char)
        delay(200)

        char.value = DeliProtocol.CMD_FINISH_JOB
        gatt?.writeCharacteristic(char)
        delay(400)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
```

---

## 5. 安卓移植核心提示

1. **绝对不要绕回 RFID 验签**：官方 App 原本的闪退与弹窗限制完全是应用层逻辑，直接向硬件发送以上指令即可百分之百自由出纸。
2. **连接生命周期**：热敏标签机在无操作 3 分钟后会自动断电休眠，Android App 中建议在发起打印前检测连接状态，若断开则自动重连。
3. **写入类型**：特征值 `FF02` 务必设置为 `WRITE_TYPE_NO_RESPONSE`，配合 200 字节切片和 12ms 延迟，传输速度最快且完全不丢包。
