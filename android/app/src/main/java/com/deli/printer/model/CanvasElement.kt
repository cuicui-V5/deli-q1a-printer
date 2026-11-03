package com.deli.printer.model

import android.graphics.Bitmap
import android.graphics.RectF
import java.util.UUID

sealed class CanvasElement {
    val id: String = UUID.randomUUID().toString()
    abstract var x: Float
    abstract var y: Float
    abstract var isSelected: Boolean

    abstract fun getBounds(): RectF
    abstract fun copyElement(): CanvasElement
}

data class TextElement(
    override var x: Float,
    override var y: Float,
    var text: String,
    var textSize: Float = 14f,
    var isBold: Boolean = false,
    var isCentered: Boolean = false,
    override var isSelected: Boolean = false
) : CanvasElement() {
    override fun getBounds(): RectF = RectF(x, y, x + 80f, y + textSize * 1.4f)
    override fun copyElement(): CanvasElement = this.copy()
}

enum class BarcodeType { CODE_128, QR_CODE }

data class BarcodeElement(
    override var x: Float,
    override var y: Float,
    var content: String,
    var type: BarcodeType,
    var width: Float = 60f,
    var height: Float = 30f,
    var bitmap: Bitmap? = null,
    override var isSelected: Boolean = false
) : CanvasElement() {
    override fun getBounds(): RectF = RectF(x, y, x + width, y + height)
    override fun copyElement(): CanvasElement = this.copy(bitmap = this.bitmap)
}

enum class ShapeType { ROUNDED_BORDER, DIVIDER_LINE, RECTANGLE }

data class ShapeElement(
    override var x: Float,
    override var y: Float,
    var width: Float,
    var height: Float,
    var shapeType: ShapeType,
    var strokeWidth: Float = 1.5f,
    var cornerRadius: Float = 6f,
    override var isSelected: Boolean = false
) : CanvasElement() {
    override fun getBounds(): RectF = RectF(x, y, x + width, y + height)
    override fun copyElement(): CanvasElement = this.copy()
}

data class ImageElement(
    override var x: Float,
    override var y: Float,
    var width: Float,
    var height: Float,
    var bitmap: Bitmap,
    override var isSelected: Boolean = false
) : CanvasElement() {
    override fun getBounds(): RectF = RectF(x, y, x + width, y + height)
    override fun copyElement(): CanvasElement = this.copy()
}
