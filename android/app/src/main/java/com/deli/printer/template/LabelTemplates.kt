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
            // Rounded Border (calibrated safe margin: 14px L/R/Top, 16px Bottom)
            ShapeElement(
                x = 14f, y = 14f,
                width = canvasW - 28f, height = canvasH - 30f,
                shapeType = ShapeType.ROUNDED_BORDER,
                strokeWidth = 2f,
                cornerRadius = 8f
            ),
            // Title
            TextElement(
                x = canvasW / 2f, y = 18f,
                text = "🍎 食品赏味保鲜标",
                textSize = 15f, isBold = true, isCentered = true
            ),
            // Divider
            ShapeElement(
                x = 20f, y = 42f,
                width = canvasW - 40f, height = 1.5f,
                shapeType = ShapeType.DIVIDER_LINE
            ),
            // Info 1
            TextElement(
                x = 20f, y = 52f,
                text = "开封: $dateStr",
                textSize = 12f, isBold = false
            ),
            // Info 2
            TextElement(
                x = 20f, y = 76f,
                text = "保质: 7天内冷藏食用",
                textSize = 12f, isBold = true
            )
        )
    }

    fun applyCableTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        return listOf(
            ShapeElement(
                x = 14f, y = 14f,
                width = canvasW - 28f, height = canvasH - 30f,
                shapeType = ShapeType.ROUNDED_BORDER,
                strokeWidth = 2f,
                cornerRadius = 8f
            ),
            TextElement(
                x = 20f, y = 18f,
                text = "🔌 Type-C 快充线",
                textSize = 15f, isBold = true
            ),
            ShapeElement(
                x = 20f, y = 42f,
                width = canvasW - 40f, height = 1.5f,
                shapeType = ShapeType.DIVIDER_LINE
            ),
            TextElement(
                x = 20f, y = 52f,
                text = "规格: 100W PD 快充",
                textSize = 12f
            ),
            TextElement(
                x = 20f, y = 76f,
                text = "位置: 桌面主显示器",
                textSize = 11f
            )
        )
    }

    fun applyStorageTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return listOf(
            ShapeElement(
                x = 14f, y = 14f,
                width = canvasW - 28f, height = canvasH - 30f,
                shapeType = ShapeType.ROUNDED_BORDER,
                strokeWidth = 2f,
                cornerRadius = 8f
            ),
            TextElement(
                x = canvasW / 2f, y = 18f,
                text = "居家整理收纳",
                textSize = 16f, isBold = true, isCentered = true
            ),
            ShapeElement(
                x = 20f, y = 42f,
                width = canvasW - 40f, height = 1.5f,
                shapeType = ShapeType.DIVIDER_LINE
            ),
            TextElement(
                x = canvasW / 2f, y = 52f,
                text = "物品: 数据线 / 扩展坞",
                textSize = 12f, isCentered = true
            ),
            TextElement(
                x = canvasW / 2f, y = 76f,
                text = "$dateStr 整理于书房",
                textSize = 11f, isCentered = true
            )
        )
    }

    fun applyPriceTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        val barcodeBmp = BarcodeEncoder.generateCode128("199026", 70, 44)
        return listOf(
            ShapeElement(
                x = 14f, y = 14f,
                width = canvasW - 28f, height = canvasH - 30f,
                shapeType = ShapeType.ROUNDED_BORDER,
                strokeWidth = 2f,
                cornerRadius = 8f
            ),
            TextElement(
                x = 20f, y = 18f,
                text = "特惠尝鲜价",
                textSize = 14f, isBold = true
            ),
            TextElement(
                x = 20f, y = 40f,
                text = "¥ 19.90",
                textSize = 24f, isBold = true
            ),
            TextElement(
                x = 20f, y = 76f,
                text = "原价: ¥29.90",
                textSize = 11f
            ),
            BarcodeElement(
                x = canvasW - 90f, y = 36f,
                content = "199026",
                type = BarcodeType.CODE_128,
                width = 72f, height = 44f,
                bitmap = barcodeBmp
            )
        )
    }
}
