package com.deli.printer.protocol

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix

/**
 * Bitmap to 1-Bit Monochrome MSB Raster Converter for ESC/POS Thermal Printers.
 */
object BitmapRasterizer {

    /**
     * Converts an Android Bitmap into an ESC/POS GS v 0 byte array.
     *
     * @param source Input Bitmap (ARGB_8888 or RGB_565).
     * @param autoRotateLandscape If true and width > height, rotates 90° CCW (postRotate 270°) to feed along label length.
     * @param dither Apply Floyd-Steinberg error diffusion dithering (recommended for photos/halftones).
     * @return Pair<ByteArray, RasterDimensions> containing the raw bytes, width in bytes, and height in pixels.
     */
    fun convertBitmap(
        source: Bitmap,
        autoRotateLandscape: Boolean = true,
        dither: Boolean = false
    ): RasterResult {
        var bmp = source

        // If landscape (e.g. 240x120), rotate 90 degrees CCW (270° CW)
        // so that the 240 length feeds lengthwise and the 120 width spans the printhead
        if (autoRotateLandscape && bmp.width > bmp.height) {
            val matrix = Matrix().apply { postRotate(270f) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }

        // Ensure width does not exceed maximum hardware printhead width
        if (bmp.width > DeliProtocol.MAX_PRINTHEAD_WIDTH_PX) {
            val scale = DeliProtocol.MAX_PRINTHEAD_WIDTH_PX.toFloat() / bmp.width
            val targetHeight = (bmp.height * scale).toInt()
            bmp = Bitmap.createScaledBitmap(bmp, DeliProtocol.MAX_PRINTHEAD_WIDTH_PX, targetHeight, true)
        }

        val width = bmp.width
        val height = bmp.height
        val widthBytes = (width + 7) / 8
        val output = ByteArray(widthBytes * height)

        if (dither) {
            applyFloydSteinbergDithering(bmp, output, width, height, widthBytes)
        } else {
            applySimpleThreshold(bmp, output, width, height, widthBytes)
        }

        return RasterResult(
            bytes = output,
            widthPx = width,
            heightPx = height,
            widthBytes = widthBytes
        )
    }

    private fun applySimpleThreshold(
        bmp: Bitmap,
        output: ByteArray,
        width: Int,
        height: Int,
        widthBytes: Int
    ) {
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (xb in 0 until widthBytes) {
                var byteVal = 0
                for (bit in 0 until 8) {
                    val px = xb * 8 + bit
                    if (px < width) {
                        val color = pixels[y * width + px]
                        val r = (color shr 16) and 0xFF
                        val g = (color shr 8) and 0xFF
                        val b = color and 0xFF
                        val alpha = (color shr 24) and 0xFF

                        // Standard luminance formula
                        val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                        // Dark dot if luminance < 200 (preserves anti-aliased vector strokes & borders)
                        if (alpha > 50 && lum < 200) {
                            byteVal = byteVal or (1 shl (7 - bit)) // MSB leftmost
                        }
                    }
                }
                output[y * widthBytes + xb] = byteVal.toByte()
            }
        }
    }

    private fun applyFloydSteinbergDithering(
        bmp: Bitmap,
        output: ByteArray,
        width: Int,
        height: Int,
        widthBytes: Int
    ) {
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        // Float grayscale buffer to accumulate error diffusion
        val gray = Array(height) { FloatArray(width) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val alpha = (color shr 24) and 0xFF
                if (alpha < 50) {
                    gray[y][x] = 255f
                } else {
                    val r = (color shr 16) and 0xFF
                    val g = (color shr 8) and 0xFF
                    val b = color and 0xFF
                    gray[y][x] = 0.299f * r + 0.587f * g + 0.114f * b
                }
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldVal = gray[y][x].coerceIn(0f, 255f)
                val newVal = if (oldVal < 128f) 0f else 255f
                val err = oldVal - newVal

                val xb = x / 8
                val bit = x % 8
                if (newVal == 0f) {
                    val idx = y * widthBytes + xb
                    output[idx] = (output[idx].toInt() or (1 shl (7 - bit))).toByte()
                }

                // Diffuse error to neighboring pixels
                if (x + 1 < width) gray[y][x + 1] += err * 7f / 16f
                if (y + 1 < height) {
                    if (x - 1 >= 0) gray[y + 1][x - 1] += err * 3f / 16f
                    gray[y + 1][x] += err * 5f / 16f
                    if (x + 1 < width) gray[y + 1][x + 1] += err * 1f / 16f
                }
            }
        }
    }
}

data class RasterResult(
    val bytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val widthBytes: Int
)
