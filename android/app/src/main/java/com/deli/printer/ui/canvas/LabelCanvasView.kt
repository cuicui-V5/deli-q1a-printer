package com.deli.printer.ui.canvas

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.deli.printer.model.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class LabelCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Canvas coordinate space in dots (e.g. 240x96 for 15x30 landscape)
    var canvasDotWidth: Int = 240
        private set
    var canvasDotHeight: Int = 96
        private set

    // Physical display scale factor
    private var displayScale: Float = 1.0f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    private val elements = mutableListOf<CanvasElement>()
    private var selectedElement: CanvasElement? = null

    // Touch Tracking
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var isDragging: Boolean = false

    // Resize Handle Tracking
    private var isResizing: Boolean = false
    private var resizeStartTouchX: Float = 0f
    private var resizeStartTouchY: Float = 0f
    private var resizeStartSize: Float = 14f

    // Undo / Redo History
    private val history = mutableListOf<List<CanvasElement>>()
    private var historyIndex = -1

    // Paint resources
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB") // Brand blue
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val handleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val deleteBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444") // Red
        style = Paint.Style.FILL
    }
    private val resizeBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB") // Blue
        style = Paint.Style.FILL
    }
    private val iconWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    var onSelectionChanged: ((CanvasElement?) -> Unit)? = null
    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
    var onElementSizeChanged: ((Float) -> Unit)? = null
    var onElementModified: (() -> Unit)? = null

    init {
        try {
            val tf = Typeface.createFromAsset(context.assets, "fonts/AlibabaPuHuiTi-Heavy.ttf")
            textPaint.typeface = tf
        } catch (_: Exception) {
            textPaint.typeface = Typeface.DEFAULT_BOLD
        }
        saveStateToHistory()
    }

    fun getAllElements(): List<CanvasElement> = elements.map { it.copyElement() }

    fun setCanvasDimensions(widthDots: Int, heightDots: Int) {
        this.canvasDotWidth = widthDots
        this.canvasDotHeight = heightDots
        selectedElement = null
        calculateScale()
        invalidate()
    }

    fun setElements(newElements: List<CanvasElement>) {
        elements.clear()
        elements.addAll(newElements)
        selectedElement = null
        saveStateToHistory()
        invalidate()
    }

    fun addElement(element: CanvasElement) {
        elements.forEach { it.isSelected = false }
        element.isSelected = true
        elements.add(element)
        selectedElement = element
        saveStateToHistory()
        onSelectionChanged?.invoke(selectedElement)
        invalidate()
    }

    fun getSelectedElement(): CanvasElement? = selectedElement

    fun deselectAll() {
        elements.forEach { it.isSelected = false }
        selectedElement = null
        onSelectionChanged?.invoke(null)
        invalidate()
    }

    fun notifyElementModified() {
        saveStateToHistory()
        invalidate()
    }

    fun removeSelected() {
        val sel = selectedElement ?: return
        elements.remove(sel)
        selectedElement = null
        saveStateToHistory()
        onSelectionChanged?.invoke(null)
        invalidate()
    }

    fun clearCanvas() {
        elements.clear()
        selectedElement = null
        saveStateToHistory()
        onSelectionChanged?.invoke(null)
        invalidate()
    }

    fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            restoreHistoryState()
        }
    }

    fun redo() {
        if (historyIndex < history.size - 1) {
            historyIndex++
            restoreHistoryState()
        }
    }

    private fun saveStateToHistory() {
        val snapshot = elements.map { it.copyElement() }
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(snapshot)
        historyIndex++
        onHistoryChanged?.invoke(historyIndex > 0, historyIndex < history.size - 1)
    }

    private fun restoreHistoryState() {
        elements.clear()
        elements.addAll(history[historyIndex].map { it.copyElement() })
        selectedElement = elements.find { it.isSelected }
        onSelectionChanged?.invoke(selectedElement)
        onHistoryChanged?.invoke(historyIndex > 0, historyIndex < history.size - 1)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateScale()
    }

    private fun calculateScale() {
        val availableW = width.toFloat() - paddingLeft - paddingRight - 32f
        val availableH = height.toFloat() - paddingTop - paddingBottom - 32f

        if (availableW <= 0f || availableH <= 0f) return

        val scaleX = availableW / canvasDotWidth.toFloat()
        val scaleY = availableH / canvasDotHeight.toFloat()
        displayScale = min(scaleX, scaleY)

        val renderedW = canvasDotWidth * displayScale
        val renderedH = canvasDotHeight * displayScale

        offsetX = paddingLeft + (width - paddingLeft - paddingRight - renderedW) / 2f
        offsetY = paddingTop + (height - paddingTop - paddingBottom - renderedH) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val canvasRect = RectF(
            offsetX,
            offsetY,
            offsetX + canvasDotWidth * displayScale,
            offsetY + canvasDotHeight * displayScale
        )

        // Draw outer canvas drop shadow & white paper background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(12f, 0f, 4f, Color.parseColor("#20000000"))
        }
        canvas.drawRoundRect(canvasRect, 12f, 12f, bgPaint)

        // Draw paper border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(canvasRect, 12f, 12f, borderPaint)

        // Apply scale & translation to draw internal elements in true dot coordinates
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(displayScale, displayScale)

        // Clip to canvas bounds
        canvas.clipRect(0f, 0f, canvasDotWidth.toFloat(), canvasDotHeight.toFloat())

        // Render all elements
        elements.forEach { elem ->
            renderElement(canvas, elem)
        }

        canvas.restore()

        // Render Selection Box & Handles in view coordinate space
        selectedElement?.let { sel ->
            val bounds = getElementBounds(sel)
            val left = offsetX + bounds.left * displayScale - 6f
            val top = offsetY + bounds.top * displayScale - 6f
            val right = offsetX + bounds.right * displayScale + 6f
            val bottom = offsetY + bounds.bottom * displayScale + 6f

            // Bounding box
            canvas.drawRoundRect(RectF(left, top, right, bottom), 6f, 6f, selectPaint)

            // 1. Top-Right Delete Handle (Enlarged, easy to tap)
            val delX = right + 2f
            val delY = top - 2f
            canvas.drawCircle(delX, delY, 19f, handleBgPaint)
            canvas.drawCircle(delX, delY, 19f, handleBorderPaint)
            canvas.drawCircle(delX, delY, 15f, deleteBtnPaint)
            // Draw clean '×' icon
            canvas.drawLine(delX - 5f, delY - 5f, delX + 5f, delY + 5f, iconWhitePaint)
            canvas.drawLine(delX + 5f, delY - 5f, delX - 5f, delY + 5f, iconWhitePaint)

            // 2. Bottom-Right Resize Handle (Drag to resize)
            if (sel is TextElement) {
                val resX = right + 2f
                val resY = bottom + 2f
                canvas.drawCircle(resX, resY, 19f, handleBgPaint)
                canvas.drawCircle(resX, resY, 19f, handleBorderPaint)
                canvas.drawCircle(resX, resY, 15f, resizeBtnPaint)
                // Draw diagonal double-headed arrow
                canvas.drawLine(resX - 6f, resY - 6f, resX + 6f, resY + 6f, iconWhitePaint)
                canvas.drawLine(resX + 6f, resY + 2f, resX + 6f, resY + 6f, iconWhitePaint)
                canvas.drawLine(resX + 2f, resY + 6f, resX + 6f, resY + 6f, iconWhitePaint)
                canvas.drawLine(resX - 6f, resY - 2f, resX - 6f, resY - 6f, iconWhitePaint)
                canvas.drawLine(resX - 2f, resY - 6f, resX - 6f, resY - 6f, iconWhitePaint)
            }
        }
    }

    fun getElementBounds(elem: CanvasElement): RectF {
        return when (elem) {
            is TextElement -> {
                textPaint.textSize = elem.textSize
                textPaint.isFakeBoldText = elem.isBold
                val textWidth = textPaint.measureText(elem.text).coerceAtLeast(16f)
                val textHeight = elem.textSize * 1.35f
                val left = if (elem.isCentered) elem.x - textWidth / 2f else elem.x
                val top = elem.y
                RectF(left, top, left + textWidth, top + textHeight)
            }
            is BarcodeElement -> RectF(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height)
            is ShapeElement -> RectF(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height)
            is ImageElement -> RectF(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height)
        }
    }

    private fun renderElement(canvas: Canvas, elem: CanvasElement) {
        when (elem) {
            is TextElement -> {
                textPaint.textSize = elem.textSize
                textPaint.isFakeBoldText = elem.isBold
                textPaint.textAlign = if (elem.isCentered) Paint.Align.CENTER else Paint.Align.LEFT
                canvas.drawText(elem.text, elem.x, elem.y + elem.textSize, textPaint)
            }
            is BarcodeElement -> {
                elem.bitmap?.let { bmp ->
                    val src = Rect(0, 0, bmp.width, bmp.height)
                    val dst = RectF(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height)
                    canvas.drawBitmap(bmp, src, dst, null)
                }
            }
            is ShapeElement -> {
                strokePaint.strokeWidth = elem.strokeWidth
                when (elem.shapeType) {
                    ShapeType.ROUNDED_BORDER -> {
                        val rect = RectF(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height)
                        canvas.drawRoundRect(rect, elem.cornerRadius, elem.cornerRadius, strokePaint)
                    }
                    ShapeType.DIVIDER_LINE -> {
                        canvas.drawLine(elem.x, elem.y, elem.x + elem.width, elem.y, strokePaint)
                    }
                    ShapeType.RECTANGLE -> {
                        canvas.drawRect(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height, strokePaint)
                    }
                }
            }
            is ImageElement -> {
                val src = Rect(0, 0, elem.bitmap.width, elem.bitmap.height)
                val dst = RectF(elem.x, elem.y, elem.x + elem.width, elem.y + elem.height)
                canvas.drawBitmap(elem.bitmap, src, dst, null)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y

                // 1. Check Delete Button tapped (Top-right handle)
                selectedElement?.let { sel ->
                    val b = getElementBounds(sel)
                    val delX = offsetX + b.right * displayScale + 2f
                    val delY = offsetY + b.top * displayScale - 2f
                    if (hypot((x - delX).toDouble(), (y - delY).toDouble()) <= 42.0) {
                        removeSelected()
                        return true
                    }

                    // 2. Check Resize Button tapped (Bottom-right handle)
                    if (sel is TextElement) {
                        val resX = offsetX + b.right * displayScale + 2f
                        val resY = offsetY + b.bottom * displayScale + 2f
                        if (hypot((x - resX).toDouble(), (y - resY).toDouble()) <= 42.0) {
                            isResizing = true
                            resizeStartTouchX = x
                            resizeStartTouchY = y
                            resizeStartSize = sel.textSize
                            return true
                        }
                    }
                }

                // 3. Check element selection & drag start
                val dotX = (x - offsetX) / displayScale
                val dotY = (y - offsetY) / displayScale

                val clicked = elements.findLast { elem ->
                    val b = getElementBounds(elem)
                    RectF(b.left - 8f, b.top - 8f, b.right + 8f, b.bottom + 8f).contains(dotX, dotY)
                }

                if (clicked != null) {
                    elements.forEach { it.isSelected = false }
                    clicked.isSelected = true
                    selectedElement = clicked
                    isDragging = true
                    onSelectionChanged?.invoke(clicked)
                    invalidate()
                    return true
                } else {
                    if (selectedElement != null) {
                        deselectAll()
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isResizing && selectedElement is TextElement) {
                    val sel = selectedElement as TextElement
                    val delta = ((x - resizeStartTouchX) + (y - resizeStartTouchY)) / (2f * displayScale)
                    val newSize = (resizeStartSize + delta * 0.35f).coerceIn(8f, 36f)
                    sel.textSize = newSize
                    onElementSizeChanged?.invoke(newSize)
                    invalidate()
                    return true
                }

                if (isDragging && selectedElement != null) {
                    val dx = (x - lastTouchX) / displayScale
                    val dy = (y - lastTouchY) / displayScale
                    selectedElement?.let { elem ->
                        elem.x += dx
                        elem.y += dy
                    }
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isResizing) {
                    isResizing = false
                    notifyElementModified()
                    onElementModified?.invoke()
                }
                if (isDragging) {
                    isDragging = false
                    notifyElementModified()
                    onElementModified?.invoke()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Renders clean 1:1 offscreen ARGB_8888 Bitmap matching the exact dot dimensions.
     */
    fun exportToBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(canvasDotWidth, canvasDotHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        // Draw elements without selection borders
        elements.forEach { elem ->
            renderElement(canvas, elem)
        }
        return bmp
    }
}
