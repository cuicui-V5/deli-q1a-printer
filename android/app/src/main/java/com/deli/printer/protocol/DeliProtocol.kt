package com.deli.printer.protocol

import java.util.UUID

/**
 * Deli Q1A Label Printer - Protocol Definition (得力 Q1A 打印机底层协议规范)
 * Contains GATT UUIDs, control command frames, and raster image packet builders.
 */
object DeliProtocol {

    // BLE GATT Service & Characteristics
    val SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
    val EVENT_CHAR_UUID: UUID = UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb")

    // Default Device Parameters
    const val DEFAULT_MAC = "5E:55:09:F0:A2:1E"
    const val DPI = 203
    const val DOTS_PER_MM = 8

    // Hardware Printhead Geometry (15mm Paper = 120 dots, 15 bytes/line)
    const val MAX_PRINTHEAD_WIDTH_PX = 120
    const val DEFAULT_WIDTH_PX = 120
    const val DEFAULT_HEIGHT_PX = 240 // 30mm label length
    const val DEFAULT_WIDTH_BYTES = 15

    // Control Commands (LuckPrinter / Lujiang protocol family)
    val CMD_ENABLE     = byteArrayOf(0x10.toByte(), 0xFF.toByte(), 0xF1.toByte(), 0x03.toByte())
    val CMD_WAKEUP     = ByteArray(12) { 0 }
    val CMD_ALIGN_TOP  = byteArrayOf(0x1F.toByte(), 0x11.toByte(), 0x51.toByte()) // 顶端回抽对齐
    val CMD_FEED_GAP   = byteArrayOf(0x1D.toByte(), 0x0C.toByte())               // 间隙定位走纸至切刀
    val CMD_EXIT_LABEL = byteArrayOf(0x1F.toByte(), 0x11.toByte(), 0x50.toByte()) // 退出标签模式
    val CMD_FINISH_JOB = byteArrayOf(0x10.toByte(), 0xFF.toByte(), 0xF1.toByte(), 0x45.toByte()) // 结束断电

    /**
     * Builds heating density command.
     * @param level 0: Light, 1: Normal (recommended), 2: Dark
     */
    fun cmdDensity(level: Int = 1): ByteArray {
        val safeLevel = level.coerceIn(0, 2).toByte()
        return byteArrayOf(0x10.toByte(), 0xFF.toByte(), 0x10.toByte(), 0x00.toByte(), safeLevel)
    }

    /**
     * Constructs ESC/POS 'GS v 0' raster image header.
     * Format: 1D 76 30 00 [xL] [xH] [yL] [yH]
     */
    fun buildRasterHeader(widthBytes: Int, heightPx: Int): ByteArray {
        return byteArrayOf(
            0x1D.toByte(), 0x76.toByte(), 0x30.toByte(), 0x00.toByte(),
            (widthBytes and 0xFF).toByte(),
            ((widthBytes shr 8) and 0xFF).toByte(),
            (heightPx and 0xFF).toByte(),
            ((heightPx shr 8) and 0xFF).toByte()
        )
    }

    /**
     * Assembles continuous print stream pipeline:
     * Raster Header + 1-Bit Bitmap + CMD_FEED_GAP (1D 0C).
     * Prevents stepper motor hesitation or stutter.
     */
    fun buildContinuousPrintStream(bitmapBytes: ByteArray, widthBytes: Int, heightPx: Int): ByteArray {
        val header = buildRasterHeader(widthBytes, heightPx)
        val stream = ByteArray(header.size + bitmapBytes.size + CMD_FEED_GAP.size)
        System.arraycopy(header, 0, stream, 0, header.size)
        System.arraycopy(bitmapBytes, 0, stream, header.size, bitmapBytes.size)
        System.arraycopy(CMD_FEED_GAP, 0, stream, header.size + bitmapBytes.size, CMD_FEED_GAP.size)
        return stream
    }
}
