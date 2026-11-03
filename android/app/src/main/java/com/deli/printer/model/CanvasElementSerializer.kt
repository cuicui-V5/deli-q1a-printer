package com.deli.printer.model

import com.deli.printer.barcode.BarcodeEncoder
import org.json.JSONArray
import org.json.JSONObject

object CanvasElementSerializer {

    fun toJson(elements: List<CanvasElement>): String {
        val array = JSONArray()
        for (elem in elements) {
            val obj = JSONObject()
            when (elem) {
                is TextElement -> {
                    obj.put("type", "text")
                    obj.put("x", elem.x.toDouble())
                    obj.put("y", elem.y.toDouble())
                    obj.put("text", elem.text)
                    obj.put("textSize", elem.textSize.toDouble())
                    obj.put("isBold", elem.isBold)
                    obj.put("isCentered", elem.isCentered)
                }
                is BarcodeElement -> {
                    obj.put("type", "barcode")
                    obj.put("x", elem.x.toDouble())
                    obj.put("y", elem.y.toDouble())
                    obj.put("content", elem.content)
                    obj.put("barcodeType", elem.type.name)
                    obj.put("width", elem.width.toDouble())
                    obj.put("height", elem.height.toDouble())
                }
                is ShapeElement -> {
                    obj.put("type", "shape")
                    obj.put("x", elem.x.toDouble())
                    obj.put("y", elem.y.toDouble())
                    obj.put("width", elem.width.toDouble())
                    obj.put("height", elem.height.toDouble())
                    obj.put("shapeType", elem.shapeType.name)
                    obj.put("strokeWidth", elem.strokeWidth.toDouble())
                    obj.put("cornerRadius", elem.cornerRadius.toDouble())
                }
                else -> {}
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun fromJson(jsonStr: String): List<CanvasElement> {
        val list = mutableListOf<CanvasElement>()
        if (jsonStr.isBlank()) return list
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                when (obj.optString("type")) {
                    "text" -> {
                        list.add(
                            TextElement(
                                x = obj.optDouble("x", 20.0).toFloat(),
                                y = obj.optDouble("y", 20.0).toFloat(),
                                text = obj.optString("text", ""),
                                textSize = obj.optDouble("textSize", 16.0).toFloat(),
                                isBold = obj.optBoolean("isBold", false),
                                isCentered = obj.optBoolean("isCentered", false)
                            )
                        )
                    }
                    "barcode" -> {
                        val content = obj.optString("content", "")
                        val bTypeStr = obj.optString("barcodeType", BarcodeType.CODE_128.name)
                        val bType = try { BarcodeType.valueOf(bTypeStr) } catch (_: Exception) { BarcodeType.CODE_128 }
                        val w = obj.optDouble("width", 60.0).toFloat()
                        val h = obj.optDouble("height", 30.0).toFloat()
                        val bmp = if (bType == BarcodeType.CODE_128) {
                            BarcodeEncoder.generateCode128(content, w.toInt(), h.toInt())
                        } else {
                            BarcodeEncoder.generateQrCode(content, w.toInt())
                        }
                        list.add(
                            BarcodeElement(
                                x = obj.optDouble("x", 20.0).toFloat(),
                                y = obj.optDouble("y", 20.0).toFloat(),
                                content = content,
                                type = bType,
                                width = w,
                                height = h,
                                bitmap = bmp
                            )
                        )
                    }
                    "shape" -> {
                        val sTypeStr = obj.optString("shapeType", ShapeType.ROUNDED_BORDER.name)
                        val sType = try { ShapeType.valueOf(sTypeStr) } catch (_: Exception) { ShapeType.ROUNDED_BORDER }
                        list.add(
                            ShapeElement(
                                x = obj.optDouble("x", 10.0).toFloat(),
                                y = obj.optDouble("y", 10.0).toFloat(),
                                width = obj.optDouble("width", 200.0).toFloat(),
                                height = obj.optDouble("height", 80.0).toFloat(),
                                shapeType = sType,
                                strokeWidth = obj.optDouble("strokeWidth", 2.0).toFloat(),
                                cornerRadius = obj.optDouble("cornerRadius", 6.0).toFloat()
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }
}
