package com.deli.printer.template

import android.content.Context
import android.graphics.*
import com.deli.printer.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TemplateItem(
    val id: String,
    val name: String,
    val isCustom: Boolean,
    val elements: List<CanvasElement>
)

object TemplateManager {

    private const val FILE_NAME = "user_templates.json"

    fun getAllTemplates(context: Context): List<TemplateItem> {
        val list = mutableListOf<TemplateItem>()

        // User Saved Templates (Built-in templates removed per user request)
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val jsonStr = file.readText()
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val name = obj.optString("name", "自定义模版")
                    val elemJson = obj.optString("elements", "[]")
                    val elems = CanvasElementSerializer.fromJson(elemJson)
                    list.add(
                        TemplateItem(
                            id = id,
                            name = name,
                            isCustom = true,
                            elements = elems
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        return list
    }

    fun saveUserTemplate(context: Context, name: String, elements: List<CanvasElement>) {
        val file = File(context.filesDir, FILE_NAME)
        val array = if (file.exists()) {
            try { JSONArray(file.readText()) } catch (_: Exception) { JSONArray() }
        } else {
            JSONArray()
        }

        val newObj = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("name", name)
            put("elements", CanvasElementSerializer.toJson(elements))
        }
        array.put(newObj)
        file.writeText(array.toString())
    }

    fun deleteUserTemplate(context: Context, templateId: String) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        try {
            val array = JSONArray(file.readText())
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") != templateId) {
                    newArray.put(obj)
                }
            }
            file.writeText(newArray.toString())
        } catch (_: Exception) {}
    }

    /**
     * Renders a clean 240x96 bitmap thumbnail for previewing in the template list.
     */
    fun renderThumbnail(context: Context, elements: List<CanvasElement>, widthPx: Int = 240, heightPx: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            try {
                typeface = Typeface.createFromAsset(context.assets, "fonts/AlibabaPuHuiTi-Heavy.ttf")
            } catch (_: Exception) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }

        for (elem in elements) {
            when (elem) {
                is TextElement -> {
                    textPaint.textSize = elem.textSize
                    textPaint.isFakeBoldText = elem.isBold
                    textPaint.textAlign = if (elem.isCentered) Paint.Align.CENTER else Paint.Align.LEFT
                    canvas.drawText(elem.text, elem.x, elem.y + elem.textSize, textPaint)
                }
                is BarcodeElement -> {
                    elem.bitmap?.let { b ->
                        canvas.drawBitmap(b, null, RectF(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height), null)
                    }
                }
                is ShapeElement -> {
                    strokePaint.strokeWidth = elem.strokeWidth
                    when (elem.shapeType) {
                        ShapeType.ROUNDED_BORDER -> canvas.drawRoundRect(RectF(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height), elem.cornerRadius, elem.cornerRadius, strokePaint)
                        ShapeType.DIVIDER_LINE -> canvas.drawLine(elem.x, elem.y, elem.x + elem.width, elem.y, strokePaint)
                        ShapeType.RECTANGLE -> canvas.drawRect(RectF(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height), strokePaint)
                    }
                }
                is ImageElement -> {
                    canvas.drawBitmap(elem.bitmap, null, RectF(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height), null)
                }
            }
        }
        return bmp
    }
}
