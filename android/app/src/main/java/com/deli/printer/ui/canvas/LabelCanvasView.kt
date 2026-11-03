package com.deli.printer.ui.canvas

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.deli.printer.model.*
import kotlin.math.max
import kotlin.math.min

class LabelCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Canvas coordinate space in dots (e.g. 240x120 for 15x30 landscape)
    var canvasDotWidth: Int = 240
        private set
    var canvasDotHeight: Int = 120
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
        color = Color.parseColor("#3B82F6") // Brand blue
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val deleteBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444") // Red
        style = Paint.Style.FILL
    }
    private val deleteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    var onSelectionChanged: ((CanvasElement?) -> Unit)? = null
    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    init {
        try {
            val tf = Typeface.createFromAsset(context.assets, "fonts/fusion-pixel.ttf")
            textPaint.typeface = tf
        } catch (_: Exception) {
            // Fallback to system default if asset is missing
        }
        saveStateToHistory()
    }

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
        if (width <= 0 || height <= 0 || canvasDotWidth <= 0 || canvasDotHeight <= 0) return
        val padding = 40f
        val availW = width - padding * 2
        val availH = height - padding * 2

        displayScale = min(availW / canvasDotWidth, availH / canvasDotHeight).coerceAtLeast(1.0f)
        offsetX = (width - canvasDotWidth * displayScale) / 2f
        offsetY = (height - canvasDotHeight * displayScale) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw Canvas Background & Die-Cut Drop Shadow
        val canvasRect = RectF(
            offsetX, offsetY,
            offsetX + canvasDotWidth * displayScale,
            offsetY + canvasDotHeight * displayScale
        )

        // Shadow / Outer outline
        val shadowPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(canvasRect, 16f, 16f, shadowPaint)

        // White paper background
        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(canvasRect, 16f, 16f, bgPaint)

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

        // Render Selection Handle & Delete Button in view coordinate space
        selectedElement?.let { sel ->
            val bounds = sel.getBounds()
            val left = offsetX + bounds.left * displayScale - 6f
            val top = offsetY + bounds.top * displayScale - 6f
            val right = offsetX + bounds.right * displayScale + 6f
            val bottom = offsetY + bounds.bottom * displayScale + 6f

            canvas.drawRect(left, top, right, bottom, selectPaint)

            // Delete circle button on top-right corner
            canvas.drawCircle(right, top, 18f, deleteBtnPaint)
            canvas.drawText("×", right, top + 8f, deleteTextPaint)
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

                // Check if Delete Button was tapped
                selectedElement?.let { sel ->
                    val b = sel.getBounds()
                    val btnX = offsetX + b.right * displayScale + 6f
                    val btnY = offsetY + b.top * displayScale - 6f
                    val dist = Math.hypot((x - btnX).toDouble(), (y - btnY).toDouble())
                    if (dist <= 30) {
                        removeSelected()
                        return true
                    }
                }

                // Check element selection
                val dotX = (x - offsetX) / displayScale
                val dotY = (y - offsetY) / displayScale

                val clicked = elements.findLast { it.getBounds().contains(dotX, dotY) }
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
                        elements.forEach { it.isSelected = false }
                        selectedElement = null
                        onSelectionChanged?.invoke(null)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
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
                if (isDragging) {
                    isDragging = false
                    saveStateToHistory()
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
