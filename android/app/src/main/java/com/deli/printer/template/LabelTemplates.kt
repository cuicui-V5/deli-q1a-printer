package com.deli.printer.template

import com.deli.printer.model.CanvasElement
import com.deli.printer.model.TextElement

object LabelTemplates {

    /**
     * App initial default state: single line large text "示例文本", centered, NO border.
     */
    fun createDefaultExample(canvasW: Float, canvasH: Float): List<CanvasElement> {
        val textSize = 26f
        return listOf(
            TextElement(
                x = canvasW / 2f,
                y = (canvasH - textSize * 1.3f) / 2f,
                text = "示例文本",
                textSize = textSize,
                isBold = false,
                isCentered = true
            )
        )
    }

    /**
     * Template 1: 银色内六角螺丝 M3x6 (Large font, NO border)
     */
    fun applyScrewTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        val y1 = (canvasH * 0.12f).coerceAtLeast(8f)
        val y2 = (canvasH * 0.48f).coerceAtLeast(40f)
        return listOf(
            TextElement(
                x = canvasW / 2f,
                y = y1,
                text = "银色内六角螺丝",
                textSize = 18f,
                isBold = false,
                isCentered = true
            ),
            TextElement(
                x = canvasW / 2f,
                y = y2,
                text = "M3x6",
                textSize = 28f,
                isBold = true,
                isCentered = true
            )
        )
    }

    /**
     * Template 2: 100Ω电阻 1206 (Large font, NO border)
     */
    fun applyResistorTemplate(canvasW: Float, canvasH: Float): List<CanvasElement> {
        val y1 = (canvasH * 0.12f).coerceAtLeast(8f)
        val y2 = (canvasH * 0.48f).coerceAtLeast(40f)
        return listOf(
            TextElement(
                x = canvasW / 2f,
                y = y1,
                text = "100Ω电阻",
                textSize = 18f,
                isBold = false,
                isCentered = true
            ),
            TextElement(
                x = canvasW / 2f,
                y = y2,
                text = "1206",
                textSize = 28f,
                isBold = true,
                isCentered = true
            )
        )
    }
}
