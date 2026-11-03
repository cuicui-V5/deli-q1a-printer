package com.deli.printer.template

import com.deli.printer.barcode.BarcodeEncoder
import com.deli.printer.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LabelTemplates {

    fun applyFoodTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        return listOf(
            // Rounded Border
            ShapeElement(
                x = 4f, y = 4f,
                width = canvasW - 8f, height = canvasH - 8f,
                shapeType = ShapeType.ROUNDED_BORDER,
                cornerRadius = 6f
            ),
            // Title
            TextElement(
                x = canvasW / 2f, y = 14f,
                text = "🍎 食品赏味保鲜标",
                textSize = 16f, isBold = true, isCentered = true
            ),
            // Divider
            ShapeElement(
                x = 14f, y = 42f,
                width = canvasW - 28f, height = 1f,
                shapeType = ShapeType.DIVIDER_LINE
            ),
            // Info 1
            TextElement(
                x = 18f, y = 54f,
                text = "开封: $dateStr",
                textSize = 12f, isBold = false
            ),
            // Info 2
            TextElement(
                x = 18f, y = 84f,
                text = "保质: 7天内冷藏食用",
                textSize = 13f, isBold = true
            )
        )
    }

    fun applyCableTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        return listOf(
            ShapeElement(
                x = 4f, y = 4f,
                width = canvasW - 8f, height = canvasH - 8f,
                shapeType = ShapeType.ROUNDED_BORDER,
                cornerRadius = 6f
            ),
            TextElement(
                x = 16f, y = 14f,
                text = "🔌 Type-C 快充线",
                textSize = 16f, isBold = true
            ),
            ShapeElement(
                x = 14f, y = 42f,
                width = canvasW - 28f, height = 1f,
                shapeType = ShapeType.DIVIDER_LINE
            ),
            TextElement(
                x = 16f, y = 54f,
                text = "规格: 100W PD 快充",
                textSize = 13f
            ),
            TextElement(
                x = 16f, y = 84f,
                text = "位置: 桌面主显示器",
                textSize = 12f
            )
        )
    }

    fun applyStorageTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return listOf(
            ShapeElement(
                x = 4f, y = 4f,
                width = canvasW - 8f, height = canvasH - 8f,
                shapeType = ShapeType.ROUNDED_BORDER,
                cornerRadius = 6f
            ),
            TextElement(
                x = canvasW / 2f, y = 14f,
                text = "居家整理收纳",
                textSize = 17f, isBold = true, isCentered = true
            ),
            ShapeElement(
                x = 14f, y = 42f,
                width = canvasW - 28f, height = 1f,
                shapeType = ShapeType.DIVIDER_LINE
            ),
            TextElement(
                x = canvasW / 2f, y = 54f,
                text = "物品: 数据线 / 扩展坞",
                textSize = 13f, isCentered = true
            ),
            TextElement(
                x = canvasW / 2f, y = 84f,
                text = "$dateStr 整理于书房",
                textSize = 11f, isCentered = true
            )
        )
    }

    fun applyPriceTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        val barcodeBmp = BarcodeEncoder.generateCode128("199026", 70, 48)
        return listOf(
            ShapeElement(
                x = 4f, y = 4f,
                width = canvasW - 8f, height = canvasH - 8f,
                shapeType = ShapeType.ROUNDED_BORDER,
                cornerRadius = 6f
            ),
            TextElement(
                x = 16f, y = 14f,
                text = "特惠尝鲜价",
                textSize = 15f, isBold = true
            ),
            TextElement(
                x = 16f, y = 42f,
                text = "¥ 19.90",
                textSize = 26f, isBold = true
            ),
            TextElement(
                x = 16f, y = 86f,
                text = "原价: ¥29.90",
                textSize = 11f
            ),
            BarcodeElement(
                x = canvasW - 86f, y = 36f,
                content = "199026",
                type = BarcodeType.CODE_128,
                width = 72f, height = 48f,
                bitmap = barcodeBmp
            )
        )
    }
}
