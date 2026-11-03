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
            // Rounded Border (native 96x216 hardware printhead)
            ShapeElement(
                x = 4f, y = 4f,
                width = canvasW - 8f, height = canvasH - 8f,
                shapeType = ShapeType.ROUNDED_BORDER,
                strokeWidth = 2f,
                cornerRadius = 6f
            ),
            // Title
            TextElement(
                x = canvasW / 2f, y = 10f,
                text = "🍎 食品赏味保鲜标",
                textSize = 14f, isBold = true, isCentered = true
            ),
            // Divider
            ShapeElement(
                x = 12f, y = 34f,
                width = canvasW - 24f, height = 1.5f,
                shapeType = ShapeType.DIVIDER_LINE
            ),
            // Info 1
            TextElement(
                x = 14f, y = 44f,
                text = "开封: $dateStr",
                textSize = 11f, isBold = false
            ),
            // Info 2
            TextElement(
                x = 14f, y = 66f,
                text = "保质: 7天内冷藏食用",
                textSize = 11f, isBold = true
            )
        )
    }

    fun applyCableTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        return listOf(
            ShapeElement(
                x = 4f, y = 4f,
                width = canvasW - 8f, height = canvasH - 8f,
                shapeType = ShapeType.ROUNDED_BORDER,
                strokeWidth = 2f,
                cornerRadius = 6f
            ),
            TextElement(
                x = 14f, y = 10f,
                text = "🔌 Type-C 快充线",
                textSize = 14f, isBold = true
            ),
            ShapeElement(
                x = 12f, y = 34f,
                width = canvasW - 24f, height = 1.5f,
                shapeType = ShapeType.DIVIDER_LINE
            ),
            TextElement(
                x = 14f, y = 44f,
                text = "规格: 100W PD 快充",
                textSize = 11f
            ),
            TextElement(
                x = 14f, y = 66f,
                text = "位置: 桌面主显示器",
                textSize = 10f
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
                strokeWidth = 2f,
                cornerRadius = 6f
            ),
            TextElement(
                x = canvasW / 2f, y = 10f,
                text = "居家整理收纳",
                textSize = 14f, isBold = true, isCentered = true
            ),
            ShapeElement(
                x = 12f, y = 34f,
                width = canvasW - 24f, height = 1.5f,
                shapeType = ShapeType.DIVIDER_LINE
            ),
            TextElement(
                x = canvasW / 2f, y = 44f,
                text = "物品: 数据线 / 扩展坞",
                textSize = 11f, isCentered = true
            ),
            TextElement(
                x = canvasW / 2f, y = 66f,
                text = "$dateStr 整理于书房",
                textSize = 10f, isCentered = true
            )
        )
    }

    fun applyPriceTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        val barcodeBmp = BarcodeEncoder.generateCode128("199026", 70, 38)
        return listOf(
            ShapeElement(
                x = 4f, y = 4f,
                width = canvasW - 8f, height = canvasH - 8f,
                shapeType = ShapeType.ROUNDED_BORDER,
                strokeWidth = 2f,
                cornerRadius = 6f
            ),
            TextElement(
                x = 14f, y = 10f,
                text = "特惠尝鲜价",
                textSize = 13f, isBold = true
            ),
            TextElement(
                x = 14f, y = 32f,
                text = "¥ 19.90",
                textSize = 22f, isBold = true
            ),
            TextElement(
                x = 14f, y = 68f,
                text = "原价: ¥29.90",
                textSize = 10f
            ),
            BarcodeElement(
                x = canvasW - 84f, y = 30f,
                content = "199026",
                type = BarcodeType.CODE_128,
                width = 70f, height = 38f,
                bitmap = barcodeBmp
            )
        )
    }
}
